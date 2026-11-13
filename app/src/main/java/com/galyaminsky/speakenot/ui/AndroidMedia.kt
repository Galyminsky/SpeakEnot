package com.galyaminsky.speakenot.ui

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.os.Looper
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidSkyengAudioPlayer : SkyengAudioPlayer {
    private var player: MediaPlayer? = null

    override fun play(url: String, onFailure: () -> Unit): Boolean {
        release()
        return try {
            val next = MediaPlayer()
            player = next
            next.setOnPreparedListener { it.start() }
            next.setOnCompletionListener { release() }
            next.setOnErrorListener { _, _, _ -> release(); onFailure(); true }
            next.setDataSource(url)
            next.prepareAsync()
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    override fun release() {
        player?.release()
        player = null
    }
}

class AndroidEnglishSpeech(context: Context) : EnglishSpeech {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private val mutableAvailability = MutableStateFlow(TtsAvailability.Initializing)
    override val availability: StateFlow<TtsAvailability> = mutableAvailability
    private var pending: String? = null
    private var closed = false

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (!closed && status == TextToSpeech.SUCCESS) {
                    val offlineVoice = runCatching {
                        engine?.voices?.firstOrNull { voice ->
                            isOfflineEnglishVoice(
                                voice.locale.language,
                                voice.isNetworkConnectionRequired,
                                voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true,
                            )
                        }
                    }.getOrNull()
                    val selected = offlineVoice != null && runCatching { engine?.setVoice(offlineVoice) == TextToSpeech.SUCCESS }.getOrDefault(false)
                    mutableAvailability.value = if (selected) TtsAvailability.Available else TtsAvailability.Unavailable
                    if (selected) pending?.let { engine?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "word") }
                } else {
                    mutableAvailability.value = TtsAvailability.Unavailable
                }
                pending = null
            }
        }
    }

    override fun speak(text: String): Boolean {
        if (closed || availability.value == TtsAvailability.Unavailable || text.isBlank()) return false
        if (availability.value == TtsAvailability.Initializing) { pending = text; return true }
        return engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word") == TextToSpeech.SUCCESS
    }

    override fun release() {
        closed = true
        pending = null
        engine?.stop()
        engine?.shutdown()
        engine = null
    }
}
