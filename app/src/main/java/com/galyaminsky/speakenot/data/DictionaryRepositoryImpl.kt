package com.galyaminsky.speakenot.data

import com.galyaminsky.speakenot.data.remote.RemoteDictionaryDataSource
import com.galyaminsky.speakenot.data.remote.toDomain
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.DictionaryRepository

class DictionaryRepositoryImpl(private val remote: RemoteDictionaryDataSource) : DictionaryRepository {
    override suspend fun search(query: String) = when (val result = remote.search(query.trim())) {
        is DictionaryResult.Failure -> result
        is DictionaryResult.Success -> DictionaryResult.Success(result.value.mapNotNull { it.toDomain() })
    }

    override suspend fun meanings(ids: List<Long>) = when (val result = remote.meanings(ids.filter { it > 0 }.distinct())) {
        is DictionaryResult.Failure -> result
        is DictionaryResult.Success -> DictionaryResult.Success(result.value.mapNotNull { it.toDomain() })
    }
}
