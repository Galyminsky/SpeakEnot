package com.galyaminsky.speakenot.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.galyaminsky.speakenot.domain.NetworkAvailability

class MeaningImageLoader(private val client: OkHttpClient, private val network: NetworkAvailability) {
    suspend fun load(rawUrl: String?): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!network.isAvailable()) return@withContext null
        val url = rawUrl?.let(::httpsUrl) ?: return@withContext null
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) null
                else response.body?.byteStream()?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }
        }.getOrNull()
    }
}
