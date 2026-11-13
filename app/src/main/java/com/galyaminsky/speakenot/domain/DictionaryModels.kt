package com.galyaminsky.speakenot.domain

data class Word(val id: Long, val text: String, val meanings: List<MeaningSummary>)
data class MeaningSummary(val id: Long, val translation: String?, val transcription: String?, val partOfSpeech: String? = null)
data class Meaning(
    val id: Long,
    val text: String,
    val translation: String?,
    val transcription: String?,
    val definition: String?,
    val examples: List<String>,
    val partOfSpeech: String? = null,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
)

sealed interface DictionaryError {
    data object Offline : DictionaryError
    data class Http(val code: Int) : DictionaryError
    data object EmptyResponse : DictionaryError
    data object Timeout : DictionaryError
    data object Network : DictionaryError
    data object Unknown : DictionaryError
}

sealed interface DictionaryResult<out T> {
    data class Success<T>(val value: T) : DictionaryResult<T>
    data class Failure(val error: DictionaryError) : DictionaryResult<Nothing>
}

interface DictionaryRepository {
    suspend fun search(query: String): DictionaryResult<List<Word>>
    suspend fun meanings(ids: List<Long>): DictionaryResult<List<Meaning>>
}
