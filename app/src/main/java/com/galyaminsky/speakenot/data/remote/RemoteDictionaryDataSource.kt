package com.galyaminsky.speakenot.data.remote

import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.AlwaysOnline
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import retrofit2.Response

interface RemoteDictionaryDataSource {
    suspend fun search(query: String): DictionaryResult<List<WordDto>>
    suspend fun meanings(ids: List<Long>): DictionaryResult<List<MeaningDto>>
}

class SkyengDataSource(private val api: SkyengApi, private val network: NetworkAvailability = AlwaysOnline) : RemoteDictionaryDataSource {
    override suspend fun search(query: String) = request { api.search(query) }
    override suspend fun meanings(ids: List<Long>) = request { api.meanings(ids.joinToString(",")) }

    private suspend fun <T> request(call: suspend () -> Response<List<T>?>): DictionaryResult<List<T>> = try {
        if (!network.isAvailable()) DictionaryResult.Failure(DictionaryError.Offline)
        else {
            val response = call()
            when {
                !response.isSuccessful -> DictionaryResult.Failure(DictionaryError.Http(response.code()))
                response.body() == null -> DictionaryResult.Failure(DictionaryError.EmptyResponse)
                else -> DictionaryResult.Success(response.body().orEmpty())
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SocketTimeoutException) {
        DictionaryResult.Failure(DictionaryError.Timeout)
    } catch (_: IOException) {
        DictionaryResult.Failure(DictionaryError.Network)
    } catch (_: Exception) {
        DictionaryResult.Failure(DictionaryError.Unknown)
    }
}
