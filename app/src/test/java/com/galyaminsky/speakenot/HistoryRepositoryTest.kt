package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.HistoryRepositoryImpl
import com.galyaminsky.speakenot.data.FavoriteRepositoryImpl
import com.galyaminsky.speakenot.data.local.FavoriteDao
import com.galyaminsky.speakenot.data.local.EnglishFavoriteEntity
import com.galyaminsky.speakenot.data.local.KazakhFavoriteEntity
import com.galyaminsky.speakenot.data.local.HistoryDao
import com.galyaminsky.speakenot.data.local.HistoryEntity
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.HistoryDictionary
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.MeaningSummary
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.ui.DictionaryViewModel
import com.galyaminsky.speakenot.ui.SearchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryRepositoryTest {
    @Test fun recordsNormalizedQueriesNewestFirstAndShowsOnlyFive() = runTest {
        val repo = HistoryRepositoryImpl(FakeHistoryDao()) { 100 }
        (1..6).forEach { assertTrue(repo.record(HistoryDictionary.ENG, " word$it ")) }
        assertEquals(listOf("word6", "word5", "word4", "word3", "word2"), repo.observeRecent(HistoryDictionary.ENG).first().map { it.query })
        assertFalse(repo.record(HistoryDictionary.ENG, "  "))
        assertFalse(repo.record(HistoryDictionary.ENG, "go \u043F\u0440\u0438\u0432\u0435\u0442"))
    }

    @Test fun repeatedQueryMovesToFrontWithoutDuplicateIgnoringCase() = runTest {
        val dao = FakeHistoryDao()
        val repo = HistoryRepositoryImpl(dao) { 100 }
        repo.record(HistoryDictionary.ENG, "Good Morning")
        repo.record(HistoryDictionary.ENG, "book")
        repo.record(HistoryDictionary.ENG, "  good   morning ")
        assertEquals(listOf("good morning", "book"), repo.observeRecent(HistoryDictionary.ENG).first().map { it.query })
        assertEquals(2, dao.rows.value.size)
    }

    @Test fun dictionariesAreSeparateAndDataSurvivesRepositoryRecreation() = runTest {
        val dao = FakeHistoryDao()
        val first = HistoryRepositoryImpl(dao) { 100 }
        first.record(HistoryDictionary.ENG, "book")
        first.record(HistoryDictionary.KZ, "\u0441\u04D9\u043B\u0435\u043C")
        val second = HistoryRepositoryImpl(dao) { 200 }
        assertEquals(listOf("book"), second.observeRecent(HistoryDictionary.ENG).first().map { it.query })
        assertEquals(listOf("\u0441\u04D9\u043B\u0435\u043C"), second.observeRecent(HistoryDictionary.KZ).first().map { it.query })
        second.clear(HistoryDictionary.ENG)
        assertEquals(listOf("\u0441\u04D9\u043B\u0435\u043C"), first.observeRecent(HistoryDictionary.KZ).first().map { it.query })
    }

    @Test fun clearAndUndoRestoreOrderWithReactiveEmissions() = runTest {
        val repo = HistoryRepositoryImpl(FakeHistoryDao()) { 100 }
        val observed = mutableListOf<List<String>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.observeRecent(HistoryDictionary.ENG).collect { observed += it.map { row -> row.query } }
        }
        repo.record(HistoryDictionary.ENG, "one")
        repo.record(HistoryDictionary.ENG, "two")
        val removed = repo.clear(HistoryDictionary.ENG)
        assertEquals(listOf("two", "one"), removed.map { it.query })
        assertTrue(repo.observeRecent(HistoryDictionary.ENG).first().isEmpty())
        repo.restore(removed)
        assertEquals(listOf("two", "one"), repo.observeRecent(HistoryDictionary.ENG).first().map { it.query })
        assertEquals(listOf(emptyList<String>(), listOf("one"), listOf("two", "one"), emptyList<String>(), listOf("two"), listOf("two", "one")), observed)
    }

    @Test fun failedEmptyAndInvalidSearchesAreNotStoredAndHistoryTapSearchesImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = HistoryRepositoryImpl(FakeHistoryDao()) { 100 }
            val fake = FakeDictionary()
            val vm = DictionaryViewModel(fake, repo)
            vm.onQueryChanged(" ")
            vm.onQueryChanged("go РїСЂРёРІРµС‚")
            advanceUntilIdle()
            assertTrue(fake.queries.isEmpty())
            vm.onQueryChanged("bad")
            advanceUntilIdle()
            assertTrue(vm.state.value.status is SearchStatus.Error)
            vm.onQueryChanged("none")
            advanceUntilIdle()
            assertEquals(SearchStatus.Empty, vm.state.value.status)
            assertTrue(vm.recentHistory.value.isEmpty())
            vm.onQueryChanged("good")
            advanceUntilIdle()
            assertEquals(listOf("good"), vm.recentHistory.value.map { it.query })
            vm.selectHistory("good")
            runCurrent()
            assertEquals(listOf("bad", "none", "good", "good"), fake.queries)
            assertEquals(1, vm.recentHistory.value.size)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun clearingHistoryDoesNotTouchFavoriteData() = runTest {
        val dao = FakeHistoryDao()
        val favoriteDao = FakeFavoriteDao()
        val favorites = FavoriteRepositoryImpl(favoriteDao) { 100 }
        val repo = HistoryRepositoryImpl(dao) { 100 }
        favorites.add(com.galyaminsky.speakenot.domain.FavoriteDictionary.ENG, 42)
        repo.record(HistoryDictionary.ENG, "book")
        val removed = repo.clear(HistoryDictionary.ENG)
        assertEquals(listOf(42L), favorites.observe(com.galyaminsky.speakenot.domain.FavoriteDictionary.ENG).first().map { it.meaningId })
        repo.restore(removed)
        assertEquals(listOf(42L), favorites.observe(com.galyaminsky.speakenot.domain.FavoriteDictionary.ENG).first().map { it.meaningId })
        assertEquals(listOf("book"), repo.observeRecent(HistoryDictionary.ENG).first().map { it.query })
    }

    @Test fun clearRestoresAllRowsIncludingRowsOutsideRecentFive() = runTest {
        val dao = FakeHistoryDao()
        val repo = HistoryRepositoryImpl(dao) { 100 }
        (1..7).forEach { repo.record(HistoryDictionary.ENG, "word$it") }
        assertEquals(5, repo.observeRecent(HistoryDictionary.ENG).first().size)
        val removed = repo.clear(HistoryDictionary.ENG)
        assertEquals(7, removed.size)
        repo.restore(removed)
        assertEquals(7, dao.all("ENG").size)
        assertEquals("word7", repo.observeRecent(HistoryDictionary.ENG).first().first().query)
    }

    private class FakeDictionary : DictionaryRepository {
        val queries = mutableListOf<String>()
        override suspend fun search(query: String): DictionaryResult<List<Word>> {
            queries += query
            return when (query) {
                "bad" -> DictionaryResult.Failure(DictionaryError.Http(503))
                "none" -> DictionaryResult.Success(emptyList())
                else -> DictionaryResult.Success(listOf(Word(1, query, listOf(MeaningSummary(2, "translation", null)))))
            }
        }
        override suspend fun meanings(ids: List<Long>): DictionaryResult<List<Meaning>> = error("unused")
    }

    private class FakeHistoryDao : HistoryDao {
        val rows = MutableStateFlow<List<HistoryEntity>>(emptyList())
        override fun observeRecent(dictionary: String) = rows.map { all -> all.filter { it.dictionary == dictionary }.sortedWith(compareByDescending<HistoryEntity> { it.searchedAt }.thenBy { it.normalizedKey }).take(5) }
        override suspend fun all(dictionary: String) = rows.value.filter { it.dictionary == dictionary }.sortedByDescending { it.searchedAt }
        override suspend fun latest(dictionary: String) = rows.value.filter { it.dictionary == dictionary }.maxOfOrNull { it.searchedAt }
        override suspend fun record(entity: HistoryEntity) {
            rows.value = rows.value.filterNot { it.dictionary == entity.dictionary && it.normalizedKey == entity.normalizedKey } + entity
        }
        override suspend fun restore(entity: HistoryEntity): Long {
            if (rows.value.any { it.dictionary == entity.dictionary && it.normalizedKey == entity.normalizedKey }) return -1
            rows.value = rows.value + entity
            return rows.value.size.toLong()
        }
        override suspend fun clear(dictionary: String) { rows.value = rows.value.filterNot { it.dictionary == dictionary } }
    }

    private class FakeFavoriteDao : FavoriteDao {
        private val eng = MutableStateFlow<List<EnglishFavoriteEntity>>(emptyList())
        private val kz = MutableStateFlow<List<KazakhFavoriteEntity>>(emptyList())
        override fun observeEnglish() = eng
        override fun observeKazakh() = kz
        override suspend fun english(id: Long) = eng.value.find { it.meaningId == id }
        override suspend fun kazakh(id: Long) = kz.value.find { it.meaningId == id }
        override suspend fun latestEnglish() = eng.value.maxOfOrNull { it.addedAt }
        override suspend fun latestKazakh() = kz.value.maxOfOrNull { it.addedAt }
        override suspend fun addEnglish(entity: EnglishFavoriteEntity): Long {
            if (eng.value.any { it.meaningId == entity.meaningId }) return -1
            eng.value = eng.value + entity
            return entity.meaningId
        }
        override suspend fun addKazakh(entity: KazakhFavoriteEntity): Long {
            if (kz.value.any { it.meaningId == entity.meaningId }) return -1
            kz.value = kz.value + entity
            return entity.meaningId
        }
        override suspend fun removeEnglish(id: Long): Int {
            val size = eng.value.size
            eng.value = eng.value.filterNot { it.meaningId == id }
            return size - eng.value.size
        }
        override suspend fun removeKazakh(id: Long): Int {
            val size = kz.value.size
            kz.value = kz.value.filterNot { it.meaningId == id }
            return size - kz.value.size
        }
    }
}

