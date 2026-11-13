package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.remote.MeaningDto
import com.galyaminsky.speakenot.data.remote.SkyengApi
import com.galyaminsky.speakenot.data.remote.SkyengDataSource
import com.galyaminsky.speakenot.data.remote.WordDto
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.FavoriteDictionary
import com.galyaminsky.speakenot.domain.FavoriteEntry
import com.galyaminsky.speakenot.domain.FavoriteRepository
import com.galyaminsky.speakenot.domain.HistoryDictionary
import com.galyaminsky.speakenot.domain.HistoryEntry
import com.galyaminsky.speakenot.domain.HistoryRepository
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.ui.CardStatus
import com.galyaminsky.speakenot.ui.DictionaryViewModel
import com.galyaminsky.speakenot.ui.EnglishSpeech
import com.galyaminsky.speakenot.ui.PlaybackSource
import com.galyaminsky.speakenot.ui.Pronunciation
import com.galyaminsky.speakenot.ui.SearchStatus
import com.galyaminsky.speakenot.ui.SkyengAudioPlayer
import com.galyaminsky.speakenot.ui.TtsAvailability
import com.galyaminsky.speakenot.ui.WordCardViewModel
import com.galyaminsky.speakenot.ui.isOfflineEnglishVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineModeTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun offlineSearchKeepsQuerySkipsRepositoryAndWaitsForManualRetryAfterRecovery() = runTest {
        val network = FakeNetwork(false)
        val dictionary = FakeDictionary()
        val history = FakeHistory()
        val vm = DictionaryViewModel(dictionary, history, network)
        vm.onQueryChanged("  good   morning ")
        advanceUntilIdle()
        assertEquals("  good   morning ", vm.state.value.query)
        assertEquals(SearchStatus.Error(DictionaryError.Offline), vm.state.value.status)
        assertEquals(0, dictionary.searchCalls)
        assertEquals(0, history.records)
        network.online = true
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(0, dictionary.searchCalls)
        vm.retry()
        runCurrent()
        assertEquals(listOf("good morning"), dictionary.queries)
        assertEquals(SearchStatus.Success, vm.state.value.status)
        assertEquals(1, history.records)
    }

    @Test fun networkErrorDoesNotRetryAutomaticallyAndKeepsInput() = runTest {
        val dictionary = FakeDictionary()
        dictionary.answer = { DictionaryResult.Failure(DictionaryError.Network) }
        val vm = DictionaryViewModel(dictionary)
        vm.onQueryChanged("book")
        advanceUntilIdle()
        assertEquals(SearchStatus.Error(DictionaryError.Network), vm.state.value.status)
        assertEquals("book", vm.state.value.query)
        dictionary.answer = { DictionaryResult.Success(listOf(word("book"))) }
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, dictionary.searchCalls)
        vm.retry()
        runCurrent()
        assertEquals(2, dictionary.searchCalls)
        assertEquals(SearchStatus.Success, vm.state.value.status)
    }

    @Test fun changingQueryCancelsOldCallAndOfflinePreventsNextOne() = runTest {
        val network = FakeNetwork(true)
        val dictionary = FakeDictionary()
        var cancelled = false
        dictionary.answer = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val vm = DictionaryViewModel(dictionary, network = network)
        vm.onQueryChanged("first")
        advanceTimeBy(400)
        runCurrent()
        network.online = false
        vm.onQueryChanged("second")
        advanceUntilIdle()
        assertTrue(cancelled)
        assertEquals(listOf("first"), dictionary.queries)
        assertEquals("second", vm.state.value.query)
        assertEquals(SearchStatus.Error(DictionaryError.Offline), vm.state.value.status)
    }

    @Test fun dataSourceNeverCallsApiWhileOfflineAndCanCallAfterRecovery() = runTest {
        val network = FakeNetwork(false)
        var calls = 0
        val api = object : SkyengApi {
            override suspend fun search(query: String): Response<List<WordDto>?> { calls++; return Response.success(emptyList()) }
            override suspend fun meanings(ids: String): Response<List<MeaningDto>?> { calls++; return Response.success(emptyList()) }
        }
        val source = SkyengDataSource(api, network)
        assertEquals(DictionaryResult.Failure(DictionaryError.Offline), source.search("book"))
        assertEquals(DictionaryResult.Failure(DictionaryError.Offline), source.meanings(listOf(1)))
        assertEquals(0, calls)
        network.online = true
        assertTrue(source.search("book") is DictionaryResult.Success)
        assertEquals(1, calls)
    }

    @Test fun cardOfflineSkipsApiAndLeavesFavoritesUntouchedUntilManualRetry() = runTest {
        val network = FakeNetwork(false)
        val dictionary = FakeDictionary()
        val favorites = FakeFavorites()
        val vm = WordCardViewModel(dictionary, favorites, network)
        vm.load("7")
        runCurrent()
        assertEquals(CardStatus.Error(DictionaryError.Offline), vm.state.value.status)
        assertEquals(0, dictionary.meaningCalls)
        assertEquals(listOf(7L), favorites.entries.value.map { it.meaningId })
        network.online = true
        runCurrent()
        assertEquals(0, dictionary.meaningCalls)
        vm.retry()
        runCurrent()
        assertEquals(1, dictionary.meaningCalls)
        assertTrue(vm.state.value.status is CardStatus.Success)
        assertEquals(listOf(7L), favorites.entries.value.map { it.meaningId })
    }

    @Test fun offlineTtsUsesOnlyLocalEnglishVoiceAndHandlesUnavailableState() {
        assertTrue(isOfflineEnglishVoice("en", false))
        assertFalse(isOfflineEnglishVoice("en", true))
        assertFalse(isOfflineEnglishVoice("en", false, installed = false))
        assertFalse(isOfflineEnglishVoice("ru", false))
        val network = FakeNetwork(false)
        val audio = FakeAudio()
        val speech = FakeSpeech()
        val pronunciation = Pronunciation(audio, speech, network)
        val meaning = Meaning(7, "book", null, null, null, emptyList(), audioUrl = "https://example.org/sound.mp3")
        speech.availability.value = TtsAvailability.Unavailable
        assertEquals(PlaybackSource.Unavailable, pronunciation.play(meaning))
        assertEquals(0, audio.calls)
        speech.availability.value = TtsAvailability.Available
        assertEquals(PlaybackSource.TextToSpeech, pronunciation.play(meaning))
        assertEquals(listOf("book"), speech.spoken)
        assertEquals(0, audio.calls)
        pronunciation.release()
        assertTrue(audio.released && speech.released)
    }

    private fun word(text: String) = Word(1, text, listOf(MeaningSummary(7, "translation", null)))

    private class FakeNetwork(var online: Boolean) : NetworkAvailability { override fun isAvailable() = online }

    private class FakeDictionary : DictionaryRepository {
        val queries = mutableListOf<String>()
        var searchCalls = 0
        var meaningCalls = 0
        var answer: suspend (String) -> DictionaryResult<List<Word>> = { DictionaryResult.Success(listOf(Word(1, it, listOf(MeaningSummary(7, "translation", null))))) }
        override suspend fun search(query: String): DictionaryResult<List<Word>> { searchCalls++; queries += query; return answer(query) }
        override suspend fun meanings(ids: List<Long>): DictionaryResult<List<Meaning>> {
            meaningCalls++
            return DictionaryResult.Success(listOf(Meaning(ids.single(), "book", "translation", null, null, emptyList())))
        }
    }

    private class FakeHistory : HistoryRepository {
        var records = 0
        private val entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
        override fun observeRecent(dictionary: HistoryDictionary) = entries
        override suspend fun record(dictionary: HistoryDictionary, query: String): Boolean { records++; return true }
        override suspend fun clear(dictionary: HistoryDictionary) = emptyList<HistoryEntry>()
        override suspend fun restore(entries: List<HistoryEntry>) = Unit
    }

    private class FakeFavorites : FavoriteRepository {
        val entries = MutableStateFlow(listOf(FavoriteEntry(7, FavoriteDictionary.ENG, 1)))
        override fun observe(dictionary: FavoriteDictionary) = entries
        override suspend fun add(dictionary: FavoriteDictionary, meaningId: Long) = error("unexpected favorite write")
        override suspend fun remove(dictionary: FavoriteDictionary, meaningId: Long): FavoriteEntry? = error("unexpected favorite write")
        override suspend fun restore(entry: FavoriteEntry): Boolean = error("unexpected favorite write")
    }

    private class FakeAudio : SkyengAudioPlayer {
        var calls = 0
        var released = false
        override fun play(url: String, onFailure: () -> Unit): Boolean { calls++; return true }
        override fun release() { released = true }
    }

    private class FakeSpeech : EnglishSpeech {
        override val availability = MutableStateFlow(TtsAvailability.Initializing)
        val spoken = mutableListOf<String>()
        var released = false
        override fun speak(text: String): Boolean {
            if (availability.value != TtsAvailability.Available) return false
            spoken += text
            return true
        }
        override fun release() { released = true }
    }
}
