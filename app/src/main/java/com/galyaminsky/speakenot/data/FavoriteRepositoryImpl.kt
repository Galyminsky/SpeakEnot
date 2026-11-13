package com.galyaminsky.speakenot.data

import com.galyaminsky.speakenot.data.local.EnglishFavoriteEntity
import com.galyaminsky.speakenot.data.local.FavoriteDao
import com.galyaminsky.speakenot.data.local.KazakhFavoriteEntity
import com.galyaminsky.speakenot.domain.FavoriteDictionary
import com.galyaminsky.speakenot.domain.FavoriteEntry
import com.galyaminsky.speakenot.domain.FavoriteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoriteRepositoryImpl(private val dao: FavoriteDao, private val clock: () -> Long = System::currentTimeMillis) : FavoriteRepository {
    private val mutex = Mutex()

    override fun observe(dictionary: FavoriteDictionary): Flow<List<FavoriteEntry>> = when (dictionary) {
        FavoriteDictionary.ENG -> dao.observeEnglish().map { rows -> rows.map { FavoriteEntry(it.meaningId, dictionary, it.addedAt) } }
        FavoriteDictionary.KZ -> dao.observeKazakh().map { rows -> rows.map { FavoriteEntry(it.meaningId, dictionary, it.addedAt) } }
    }

    override suspend fun add(dictionary: FavoriteDictionary, meaningId: Long): Boolean = mutex.withLock {
        if (meaningId <= 0) return@withLock false
        val previous = when (dictionary) {
            FavoriteDictionary.ENG -> dao.latestEnglish()
            FavoriteDictionary.KZ -> dao.latestKazakh()
        } ?: Long.MIN_VALUE
        val addedAt = maxOf(clock(), if (previous == Long.MAX_VALUE) previous else previous + 1)
        when (dictionary) {
            FavoriteDictionary.ENG -> dao.addEnglish(EnglishFavoriteEntity(meaningId, "Skyeng", addedAt))
            FavoriteDictionary.KZ -> dao.addKazakh(KazakhFavoriteEntity(meaningId, "Skyeng", addedAt))
        } != -1L
    }

    override suspend fun remove(dictionary: FavoriteDictionary, meaningId: Long): FavoriteEntry? = mutex.withLock {
        val row = when (dictionary) {
            FavoriteDictionary.ENG -> dao.english(meaningId)?.let { FavoriteEntry(it.meaningId, dictionary, it.addedAt) }
            FavoriteDictionary.KZ -> dao.kazakh(meaningId)?.let { FavoriteEntry(it.meaningId, dictionary, it.addedAt) }
        } ?: return@withLock null
        val deleted = when (dictionary) {
            FavoriteDictionary.ENG -> dao.removeEnglish(meaningId)
            FavoriteDictionary.KZ -> dao.removeKazakh(meaningId)
        }
        row.takeIf { deleted > 0 }
    }

    override suspend fun restore(entry: FavoriteEntry): Boolean = mutex.withLock {
        when (entry.dictionary) {
            FavoriteDictionary.ENG -> dao.addEnglish(EnglishFavoriteEntity(entry.meaningId, "Skyeng", entry.addedAt))
            FavoriteDictionary.KZ -> dao.addKazakh(KazakhFavoriteEntity(entry.meaningId, "Skyeng", entry.addedAt))
        } != -1L
    }
}
