package com.galyaminsky.speakenot.domain

import kotlinx.coroutines.flow.Flow

enum class HistoryDictionary { ENG, KZ }

data class HistoryEntry(val dictionary: HistoryDictionary, val query: String, val searchedAt: Long)

interface HistoryRepository {
    fun observeRecent(dictionary: HistoryDictionary): Flow<List<HistoryEntry>>
    suspend fun record(dictionary: HistoryDictionary, query: String): Boolean
    suspend fun clear(dictionary: HistoryDictionary): List<HistoryEntry>
    suspend fun restore(entries: List<HistoryEntry>)
}
