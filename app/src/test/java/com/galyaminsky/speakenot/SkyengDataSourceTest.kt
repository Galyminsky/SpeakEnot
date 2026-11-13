package com.galyaminsky.speakenot

import com.galyaminsky.speakenot.data.remote.SkyengApi
import com.galyaminsky.speakenot.data.remote.SkyengDataSource
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryResult
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SkyengDataSourceTest {
    @Test fun parsesSearchAndMeaningUrlsAndEmptyList() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[{\"id\":1,\"text\":\"go\",\"meanings\":[{\"id\":7}]}]"))
            server.enqueue(MockResponse().setBody("[]"))
            val source = SkyengDataSource(api(server))
            assertEquals(1, (source.search("go") as DictionaryResult.Success).value.size)
            assertEquals("/words/search?search=go", server.takeRequest().path)
            assertEquals(emptyList<Any>(), (source.meanings(listOf(7, 8)) as DictionaryResult.Success).value)
            assertEquals("/meanings?ids=7%2C8", server.takeRequest().path)
        }
    }

    @Test fun handlesHttpErrorAndMissingBody() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            server.enqueue(MockResponse().setResponseCode(204))
            val source = SkyengDataSource(api(server))
            assertEquals(DictionaryResult.Failure(DictionaryError.Http(503)), source.search("go"))
            assertEquals(DictionaryResult.Failure(DictionaryError.EmptyResponse), source.search("go"))
        }
    }

    @Test fun mapsTimeoutAndRethrowsCancellation() = runBlocking {
        val timeoutApi = object : SkyengApi {
            override suspend fun search(query: String): Response<List<com.galyaminsky.speakenot.data.remote.WordDto>?> = throw SocketTimeoutException()
            override suspend fun meanings(ids: String): Response<List<com.galyaminsky.speakenot.data.remote.MeaningDto>?> = throw CancellationException()
        }
        val source = SkyengDataSource(timeoutApi)
        assertEquals(DictionaryResult.Failure(DictionaryError.Timeout), source.search("go"))
        try {
            source.meanings(listOf(1))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    private fun api(server: MockWebServer): SkyengApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(SkyengApi::class.java)
}
