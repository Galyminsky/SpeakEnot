package com.galyaminsky.speakenot.domain

import kotlinx.coroutines.flow.Flow

/** Future contract for IDs of viewed meanings. No Skyeng content or persistent implementation. */
interface RecentMeaningCache {
    val capacity: Int get() = 50
    fun observeRecentIds(): Flow<List<Long>>
    suspend fun markViewed(meaningId: Long)
}
