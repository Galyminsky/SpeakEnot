package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.FavoriteRepositoryImpl
import com.galyaminsky.speakenot.data.local.EnglishFavoriteEntity
import com.galyaminsky.speakenot.data.local.FavoriteDao
import com.galyaminsky.speakenot.data.local.KazakhFavoriteEntity
import com.galyaminsky.speakenot.domain.FavoriteDictionary
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.Word
import com.galyaminsky.speakenot.ui.FavoritesViewModel
import com.galyaminsky.speakenot.ui.WordCardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteRepositoryTest {
    @Test fun addsMeaningIdOnlyAndRejectsDuplicatesAndInvalidIds() = runTest {
        val dao = FakeDao()
        val repo = FavoriteRepositoryImpl(dao) { 100 }
        assertTrue(repo.add(FavoriteDictionary.ENG, 42))
        assertFalse(repo.add(FavoriteDictionary.ENG, 42))
        assertFalse(repo.add(FavoriteDictionary.ENG, 0))
        assertEquals(listOf(42L), repo.observe(FavoriteDictionary.ENG).first().map { it.meaningId })
        assertEquals("Skyeng", dao.eng.value.single().dictionary)
        assertEquals(setOf("meaningId", "dictionary", "addedAt"), EnglishFavoriteEntity::class.java.declaredFields.filterNot { it.isSynthetic || it.name.startsWith("$") }.map { it.name }.toSet())
        assertEquals(setOf("meaningId", "dictionary", "addedAt"), KazakhFavoriteEntity::class.java.declaredFields.filterNot { it.isSynthetic || it.name.startsWith("$") }.map { it.name }.toSet())
    }

    @Test fun newestFirstEvenWhenClockDoesNotAdvance() = runTest {
        val repo = FavoriteRepositoryImpl(FakeDao()) { 100 }
        repo.add(FavoriteDictionary.ENG, 9)
        repo.add(FavoriteDictionary.ENG, 2)
        repo.add(FavoriteDictionary.ENG, 5)
        assertEquals(listOf(5L, 2L, 9L), repo.observe(FavoriteDictionary.ENG).first().map { it.meaningId })
    }

    @Test fun deletionAndUndoRestoreOriginalPositionAndPreventDuplicateRestore() = runTest {
        val repo = FavoriteRepositoryImpl(FakeDao()) { 100 }
        repo.add(FavoriteDictionary.ENG, 1)
        repo.add(FavoriteDictionary.ENG, 2)
        val removed = repo.remove(FavoriteDictionary.ENG, 1)!!
        assertEquals(listOf(2L), repo.observe(FavoriteDictionary.ENG).first().map { it.meaningId })
        assertNull(repo.remove(FavoriteDictionary.ENG, 1))
        assertTrue(repo.restore(removed))
        assertFalse(repo.restore(removed))
        assertEquals(listOf(2L, 1L), repo.observe(FavoriteDictionary.ENG).first().map { it.meaningId })
    }

    @Test fun englishAndKazakhAreIsolatedAndRepositoryRecreationKeepsData() = runTest {
        val dao = FakeDao()
        val first = FavoriteRepositoryImpl(dao) { 100 }
        first.add(FavoriteDictionary.ENG, 7)
        first.add(FavoriteDictionary.KZ, 7)
        first.add(FavoriteDictionary.KZ, 8)
        val recreated = FavoriteRepositoryImpl(dao) { 200 }
        assertEquals(listOf(7L), recreated.observe(FavoriteDictionary.ENG).first().map { it.meaningId })
        assertEquals(listOf(8L, 7L), recreated.observe(FavoriteDictionary.KZ).first().map { it.meaningId })
        recreated.remove(FavoriteDictionary.ENG, 7)
        assertEquals(listOf(8L, 7L), first.observe(FavoriteDictionary.KZ).first().map { it.meaningId })
    }

    @Test fun flowEmitsAfterAddRemoveAndUndo() = runTest {
        val repo = FavoriteRepositoryImpl(FakeDao()) { 100 }
        val observed = mutableListOf<List<Long>>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            repo.observe(FavoriteDictionary.ENG).collect { observed += it.map { entry -> entry.meaningId } }
        }
        repo.add(FavoriteDictionary.ENG, 1)
        val removed = repo.remove(FavoriteDictionary.ENG, 1)!!
        repo.restore(removed)
        assertEquals(listOf(emptyList<Long>(), listOf(1L), emptyList<Long>(), listOf(1L)), observed)
        job.cancel()
    }

    @Test fun cardBookmarkAndListReactToRemovalAndUndo() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val favorites = FavoriteRepositoryImpl(FakeDao()) { 100 }
            val dictionary = object : DictionaryRepository {
                override suspend fun search(query: String): DictionaryResult<List<Word>> = error("unused")
                override suspend fun meanings(ids: List<Long>) = DictionaryResult.Success(listOf(Meaning(7, "book", "книга", null, null, emptyList())))
            }
            val card = WordCardViewModel(dictionary, favorites)
            val list = FavoritesViewModel(favorites)
            val removed = mutableListOf<com.galyaminsky.speakenot.domain.FavoriteEntry>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { card.removals.collect { removed += it } }
            card.load("7")
            testScheduler.runCurrent()
            card.toggleFavorite()
            testScheduler.runCurrent()
            assertTrue(card.state.value.favorite)
            assertEquals(listOf(7L), list.entries.value.map { it.meaningId })
            card.toggleFavorite()
            testScheduler.runCurrent()
            assertFalse(card.state.value.favorite)
            assertTrue(list.entries.value.isEmpty())
            assertEquals(7L, removed.single().meaningId)
            card.undoRemoval(removed.single())
            testScheduler.runCurrent()
            assertTrue(card.state.value.favorite)
            assertEquals(listOf(7L), list.entries.value.map { it.meaningId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeDao : FavoriteDao {
        val eng = MutableStateFlow<List<EnglishFavoriteEntity>>(emptyList())
        val kz = MutableStateFlow<List<KazakhFavoriteEntity>>(emptyList())
        override fun observeEnglish() = eng
        override fun observeKazakh() = kz
        override suspend fun english(id: Long) = eng.value.find { it.meaningId == id }
        override suspend fun kazakh(id: Long) = kz.value.find { it.meaningId == id }
        override suspend fun latestEnglish() = eng.value.maxOfOrNull { it.addedAt }
        override suspend fun latestKazakh() = kz.value.maxOfOrNull { it.addedAt }
        override suspend fun addEnglish(entity: EnglishFavoriteEntity): Long {
            if (eng.value.any { it.meaningId == entity.meaningId }) return -1
            eng.value = (eng.value + entity).sortedWith(compareByDescending<EnglishFavoriteEntity> { it.addedAt }.thenByDescending { it.meaningId })
            return entity.meaningId
        }
        override suspend fun addKazakh(entity: KazakhFavoriteEntity): Long {
            if (kz.value.any { it.meaningId == entity.meaningId }) return -1
            kz.value = (kz.value + entity).sortedWith(compareByDescending<KazakhFavoriteEntity> { it.addedAt }.thenByDescending { it.meaningId })
            return entity.meaningId
        }
        override suspend fun removeEnglish(id: Long): Int {
            if (eng.value.none { it.meaningId == id }) return 0
            eng.value = eng.value.filterNot { it.meaningId == id }
            return 1
        }
        override suspend fun removeKazakh(id: Long): Int {
            if (kz.value.none { it.meaningId == id }) return 0
            kz.value = kz.value.filterNot { it.meaningId == id }
            return 1
        }
    }
}
