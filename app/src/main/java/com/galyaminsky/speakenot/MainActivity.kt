package com.galyaminsky.speakenot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.galyaminsky.speakenot.di.AppContainer
import com.galyaminsky.speakenot.ui.SpeakEnotApp
import com.galyaminsky.speakenot.ui.theme.SpeakEnotTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SpeakEnotTheme { SpeakEnotApp(container.dictionaryRepository, container.favoriteRepository, container.historyRepository, container.networkAvailability, container.imageLoader) } }
    }
}
