package com.galyaminsky.speakenot.data

import com.galyaminsky.speakenot.data.local.HistoryDao
import com.galyaminsky.speakenot.data.local.HistoryEntity
import com.galyaminsky.speakenot.domain.HistoryDictionary
import com.galyaminsky.speakenot.domain.HistoryEntry
import com.galyaminsky.speakenot.domain.HistoryRepository
import com.galyaminsky.speakenot.domain.QueryInput
import com.galyaminsky.speakenot.domain.parseQuery
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepositoryImpl(private val dao: HistoryDao, private val clock: () -> Long = System::currentTimeMillis) : HistoryRepository {
    private val mutex = Mutex()

    override fun observeRecent(dictionary: HistoryDictionary): Flow<List<HistoryEntry>> =
        dao.observeRecent(dictionary.name).map { rows -> rows.map { HistoryEntry(dictionary, it.query, it.searchedAt) } }

    override suspend fun record(dictionary: HistoryDictionary, query: String): Boolean = mutex.withLock {
        val input = parseQuery(query) as? QueryInput.Valid ?: return@withLock false
        val previous = dao.latest(dictionary.name) ?: Long.MIN_VALUE
        val time = maxOf(clock(), if (previous == Long.MAX_VALUE) previous else previous + 1)
        dao.record(HistoryEntity(dictionary.name, input.text.lowercase(Locale.ROOT), input.text, time))
        true
    }

    override suspend fun clear(dictionary: HistoryDictionary): List<HistoryEntry> = mutex.withLock {
        val rows = dao.all(dictionary.name)
        dao.clear(dictionary.name)
        rows.map { HistoryEntry(dictionary, it.query, it.searchedAt) }
    }

    override suspend fun restore(entries: List<HistoryEntry>) = mutex.withLock {
        entries.forEach { entry ->
            dao.restore(HistoryEntity(entry.dictionary.name, entry.query.lowercase(Locale.ROOT), entry.query, entry.searchedAt))
        }
    }
}
