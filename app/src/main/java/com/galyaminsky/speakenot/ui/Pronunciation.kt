package com.galyaminsky.speakenot.ui

import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.AlwaysOnline
import kotlinx.coroutines.flow.StateFlow

interface SkyengAudioPlayer {
    fun play(url: String, onFailure: () -> Unit): Boolean
    fun release()
}

interface EnglishSpeech {
    val availability: StateFlow<TtsAvailability>
    fun speak(text: String): Boolean
    fun release()
}

enum class TtsAvailability { Initializing, Available, Unavailable }

fun isOfflineEnglishVoice(language: String, needsNetwork: Boolean, installed: Boolean = true): Boolean =
    language == "en" && !needsNetwork && installed

enum class PlaybackSource { Skyeng, TextToSpeech, Unavailable }

class Pronunciation(
    private val audio: SkyengAudioPlayer,
    private val speech: EnglishSpeech,
    private val network: NetworkAvailability = AlwaysOnline,
    private val onFallback: (PlaybackSource) -> Unit = {},
) {
    val availability: StateFlow<TtsAvailability> = speech.availability

    fun play(meaning: Meaning): PlaybackSource {
        if (meaning.text.isBlank()) return PlaybackSource.Unavailable
        val url = meaning.audioUrl?.let(::httpsUrl)
        if (url != null && network.isAvailable()) {
            try {
                if (audio.play(url) {
                    onFallback(if (speech.speak(meaning.text)) PlaybackSource.TextToSpeech else PlaybackSource.Unavailable)
                }) return PlaybackSource.Skyeng
            } catch (_: Exception) { }
        }
        return if (speech.speak(meaning.text)) PlaybackSource.TextToSpeech else PlaybackSource.Unavailable
    }

    fun release() { audio.release(); speech.release() }
}

fun httpsUrl(raw: String): String? {
    val url = when {
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("https://") -> raw
        else -> return null
    }
    return url.takeIf { runCatching { java.net.URI(it).host?.isNotBlank() == true }.getOrDefault(false) }
}
