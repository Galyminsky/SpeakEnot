package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.remote.ExampleDto
import com.galyaminsky.speakenot.data.remote.MeaningDto
import com.galyaminsky.speakenot.data.remote.TextDto
import com.galyaminsky.speakenot.data.remote.toDomain
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.domain.partOfSpeechLabel
import com.galyaminsky.speakenot.ui.AppRoute
import com.galyaminsky.speakenot.ui.CardSection
import com.galyaminsky.speakenot.ui.CardStatus
import com.galyaminsky.speakenot.ui.DictionaryViewModel
import com.galyaminsky.speakenot.ui.EnglishSpeech
import com.galyaminsky.speakenot.ui.TtsAvailability
import com.galyaminsky.speakenot.ui.PlaybackSource
import com.galyaminsky.speakenot.ui.Pronunciation
import com.galyaminsky.speakenot.ui.SearchStatus
import com.galyaminsky.speakenot.ui.SkyengAudioPlayer
import com.galyaminsky.speakenot.ui.WordCardViewModel
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WordCardTest {
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun mapsCompleteAndPartialMeaningWithoutInventingMissingContent() {
        val full = MeaningDto(
            id = 7, text = " book ", translation = TextDto(" книга "), transcription = " bʊk ",
            definition = TextDto(" a written work "), examples = listOf(ExampleDto(" A book. "), ExampleDto(" ")),
            partOfSpeechCode = "n", soundUrl = "//audio.example/book.mp3",
            images = JsonParser.parseString("[\"//image.example/book.jpg\"]"),
        ).toDomain()!!
        assertEquals("book", full.text)
        assertEquals("книга", full.translation)
        assertEquals("bʊk", full.transcription)
        assertEquals("n", full.partOfSpeech)
        assertEquals("Существительное", partOfSpeechLabel(full.partOfSpeech))
        assertEquals("//audio.example/book.mp3", full.audioUrl)
        assertEquals("//image.example/book.jpg", full.imageUrl)
        assertEquals(listOf("A book."), full.examples)
        val partial = MeaningDto(id = 8).toDomain()!!
        assertEquals("", partial.text)
        assertNull(partial.translation)
        assertNull(partial.imageUrl)
        assertNull(partial.audioUrl)
        assertTrue(partial.examples.isEmpty())
    }

    @Test fun loadsOnlyRequestedMeaningAndRejectsInvalidId() = runTest {
        val fake = FakeRepository()
        fake.answer = { DictionaryResult.Success(listOf(meaning(9), meaning(7))) }
        val vm = WordCardViewModel(fake)
        vm.load("7")
        advanceUntilIdle()
        assertEquals(listOf(listOf(7L)), fake.ids)
        assertEquals(7L, (vm.state.value.status as CardStatus.Success).meaning.id)
        vm.load("0")
        assertEquals(CardStatus.InvalidId, vm.state.value.status)
        vm.load("bad")
        assertEquals(CardStatus.InvalidId, vm.state.value.status)
        assertEquals(1, fake.ids.size)
    }

    @Test fun emptyWrongIdAndApiErrorsAreHandledAndRetryUsesSameId() = runTest {
        val fake = FakeRepository()
        var calls = 0
        fake.answer = {
            calls++
            when (calls) {
                1 -> DictionaryResult.Success(emptyList())
                2 -> DictionaryResult.Success(listOf(meaning(99)))
                3 -> DictionaryResult.Failure(DictionaryError.Http(503))
                4 -> DictionaryResult.Failure(DictionaryError.Timeout)
                else -> DictionaryResult.Success(listOf(meaning(7)))
            }
        }
        val vm = WordCardViewModel(fake)
        vm.load("7")
        advanceUntilIdle()
        assertEquals(CardStatus.NotFound, vm.state.value.status)
        vm.retry(); advanceUntilIdle()
        assertEquals(CardStatus.NotFound, vm.state.value.status)
        vm.retry(); advanceUntilIdle()
        assertEquals(CardStatus.Error(DictionaryError.Http(503)), vm.state.value.status)
        vm.retry(); advanceUntilIdle()
        assertEquals(CardStatus.Error(DictionaryError.Timeout), vm.state.value.status)
        vm.retry(); advanceUntilIdle()
        assertEquals(7L, (vm.state.value.status as CardStatus.Success).meaning.id)
        assertEquals(List(5) { listOf(7L) }, fake.ids)
    }

    @Test fun cancelledAndLateRequestsCannotReplaceNewCard() = runTest {
        val old = CompletableDeferred<DictionaryResult<List<Meaning>>>()
        val fake = FakeRepository()
        fake.answer = { ids ->
            if (ids == listOf(1L)) withContext(NonCancellable) { old.await() }
            else DictionaryResult.Success(listOf(meaning(2)))
        }
        val vm = WordCardViewModel(fake)
        vm.load("1")
        testScheduler.runCurrent()
        vm.load("2")
        testScheduler.runCurrent()
        old.complete(DictionaryResult.Success(listOf(meaning(1))))
        advanceUntilIdle()
        assertEquals(2L, (vm.state.value.status as CardStatus.Success).meaning.id)
    }

    @Test fun loadingCancellationStopsRepositoryCall() = runTest {
        val fake = FakeRepository()
        var cancelled = false
        fake.answer = {
            try { awaitCancellation() } finally { cancelled = true }
        }
        val vm = WordCardViewModel(fake)
        vm.load("1")
        testScheduler.runCurrent()
        vm.load("invalid")
        testScheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(CardStatus.InvalidId, vm.state.value.status)
    }

    @Test fun expansionSectionsAreIndependent() {
        val vm = WordCardViewModel(FakeRepository())
        vm.toggle(CardSection.Examples)
        assertEquals(setOf(CardSection.Examples), vm.state.value.expanded)
        vm.toggle(CardSection.Definition)
        assertEquals(setOf(CardSection.Examples, CardSection.Definition), vm.state.value.expanded)
        vm.toggle(CardSection.Examples)
        assertEquals(setOf(CardSection.Definition), vm.state.value.expanded)
    }

    @Test fun pronunciationPrefersSkyengFallsBackToTtsAndReleasesEngines() {
        val audio = FakeAudio()
        val speech = FakeSpeech()
        val player = Pronunciation(audio, speech)
        val word = meaning(1).copy(text = "book", audioUrl = "//example.org/book.mp3")
        assertEquals(PlaybackSource.Skyeng, player.play(word))
        assertEquals("https://example.org/book.mp3", audio.lastUrl)
        assertTrue(speech.spoken.isEmpty())
        audio.fail?.invoke()
        assertEquals(listOf("book"), speech.spoken)
        audio.accept = false
        assertEquals(PlaybackSource.TextToSpeech, player.play(word))
        assertEquals(PlaybackSource.TextToSpeech, player.play(word.copy(audioUrl = null)))
        speech.accept = false
        assertEquals(PlaybackSource.Unavailable, player.play(word.copy(audioUrl = null)))
        player.release()
        assertTrue(audio.released && speech.released)
    }

    @Test fun returningToSearchKeepsExistingSearchViewModelState() = runTest {
        val fake = FakeRepository()
        fake.searchAnswer = DictionaryResult.Success(listOf(Word(2, "book", listOf(MeaningSummary(7, "книга", null)))))
        fake.answer = { DictionaryResult.Success(listOf(meaning(7))) }
        val search = DictionaryViewModel(fake)
        search.onQueryChanged("book")
        advanceUntilIdle()
        assertEquals(SearchStatus.Success, search.state.value.status)
        val card = WordCardViewModel(fake)
        assertEquals("word/7", AppRoute.meaning(7))
        assertFalse(AppRoute.showBottomBar(AppRoute.Word))
        card.load("7")
        advanceUntilIdle()
        assertEquals("book", search.state.value.query)
        assertEquals(7L, search.state.value.groups.single().items.single().meaning.id)
        assertEquals(1, fake.searchCalls)
    }

    private fun meaning(id: Long) = Meaning(id, "book", "книга", null, null, emptyList())

    private class FakeRepository : DictionaryRepository {
        val ids = mutableListOf<List<Long>>()
        var searchCalls = 0
        var answer: suspend (List<Long>) -> DictionaryResult<List<Meaning>> = { DictionaryResult.Success(emptyList()) }
        var searchAnswer: DictionaryResult<List<Word>> = DictionaryResult.Success(emptyList())
        override suspend fun meanings(ids: List<Long>): DictionaryResult<List<Meaning>> { this.ids += ids; return answer(ids) }
        override suspend fun search(query: String): DictionaryResult<List<Word>> { searchCalls++; return searchAnswer }
    }

    private class FakeAudio : SkyengAudioPlayer {
        var accept = true
        var lastUrl: String? = null
        var fail: (() -> Unit)? = null
        var released = false
        override fun play(url: String, onFailure: () -> Unit): Boolean { lastUrl = url; fail = onFailure; return accept }
        override fun release() { released = true }
    }

    private class FakeSpeech : EnglishSpeech {
        override val availability = MutableStateFlow(TtsAvailability.Available)
        val spoken = mutableListOf<String>()
        var accept = true
        var released = false
        override fun speak(text: String): Boolean { spoken += text; return accept }
        override fun release() { released = true }
    }
}
