package com.galyaminsky.speakenot.domain

import kotlinx.coroutines.flow.Flow

enum class FavoriteDictionary { ENG, KZ }

data class FavoriteEntry(val meaningId: Long, val dictionary: FavoriteDictionary, val addedAt: Long)

interface FavoriteRepository {
    fun observe(dictionary: FavoriteDictionary): Flow<List<FavoriteEntry>>
    suspend fun add(dictionary: FavoriteDictionary, meaningId: Long): Boolean
    suspend fun remove(dictionary: FavoriteDictionary, meaningId: Long): FavoriteEntry?
    suspend fun restore(entry: FavoriteEntry): Boolean
}
