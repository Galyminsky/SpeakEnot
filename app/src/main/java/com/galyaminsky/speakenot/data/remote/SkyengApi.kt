package com.galyaminsky.speakenot.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface SkyengApi {
    @GET("words/search")
    suspend fun search(@Query("search") query: String, @Query("pageSize") pageSize: Int): Response<List<WordDto>?>

    @GET("meanings")
    suspend fun meanings(@Query("ids") ids: String): Response<List<MeaningDto>?>
}

data class WordDto(val id: Long? = null, val text: String? = null, val meanings: List<MeaningSummaryDto>? = null)
data class MeaningSummaryDto(val id: Long? = null, val translation: TextDto? = null, val transcription: String? = null, val partOfSpeechCode: String? = null)
data class TextDto(val text: String? = null)
data class ExampleDto(val text: String? = null)
data class MeaningDto(
    val id: Long? = null,
    val text: String? = null,
    val translation: TextDto? = null,
    val transcription: String? = null,
    val definition: TextDto? = null,
    val examples: List<ExampleDto>? = null,
    val partOfSpeechCode: String? = null,
    val soundUrl: String? = null,
    val imageUrl: String? = null,
    val images: JsonElement? = null,
)
