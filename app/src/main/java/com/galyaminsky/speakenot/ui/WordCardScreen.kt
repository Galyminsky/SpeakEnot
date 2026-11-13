package com.galyaminsky.speakenot.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.galyaminsky.speakenot.R
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.partOfSpeechLabel
import com.galyaminsky.speakenot.domain.NetworkAvailability

@Composable
fun WordCardScreen(meaningId: String?, viewModel: WordCardViewModel, imageLoader: MeaningImageLoader, network: NetworkAvailability, snackbarHostState: SnackbarHostState, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val playback = remember { mutableStateOf<PlaybackSource?>(null) }
    val pronunciation = remember(context, network) { Pronunciation(AndroidSkyengAudioPlayer(), AndroidEnglishSpeech(context), network) { playback.value = it } }
    val ttsAvailability by pronunciation.availability.collectAsState()
    DisposableEffect(pronunciation) { onDispose { pronunciation.release() } }
    LaunchedEffect(meaningId) { viewModel.load(meaningId) }
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.removals.collect { removed ->
            if (snackbarHostState.showSnackbar("Удалено из избранного", "Отменить") == SnackbarResult.ActionPerformed) {
                viewModel.undoRemoval(removed)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Button(onClick = onBack) { Text("Назад") }
        when (val status = state.status) {
            CardStatus.Loading -> Text("Загрузка…")
            CardStatus.InvalidId -> Text("Некорректный ID значения")
            CardStatus.NotFound -> {
                Text("Значение не найдено")
                Button(onClick = viewModel::retry) { Text("Повторить") }
            }
            is CardStatus.Error -> {
                Text(dictionaryErrorMessage(status.reason))
                Button(onClick = viewModel::retry) { Text("Повторить") }
            }
            is CardStatus.Success -> MeaningContent(status.meaning, state.expanded, state.favorite, imageLoader, ttsAvailability, playback.value,
                onPlay = { playback.value = pronunciation.play(status.meaning) }, viewModel = viewModel, context = context)
        }
    }
}

@Composable
private fun MeaningContent(
    meaning: Meaning,
    expanded: Set<CardSection>,
    favorite: Boolean,
    imageLoader: MeaningImageLoader,
    ttsAvailability: TtsAvailability,
    playback: PlaybackSource?,
    onPlay: () -> Unit,
    viewModel: WordCardViewModel,
    context: Context,
) {
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, meaning.imageUrl) {
        value = imageLoader.load(meaning.imageUrl)
    }
    if (image != null) {
        Image(bitmap = image!!, contentDescription = "Изображение слова", contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(240.dp))
    } else {
        // The project currently has no raccoon art; use its existing launcher resource until supplied.
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = "Изображение отсутствует",
            modifier = Modifier.fillMaxWidth().height(240.dp))
    }
    Text(meaning.text.ifBlank { "Слово не указано" })
    Text(meaning.transcription ?: "Транскрипция отсутствует")
    Text(partOfSpeechLabel(meaning.partOfSpeech))
    Text(meaning.translation ?: "Перевод отсутствует")
    Button(onClick = viewModel::toggleFavorite) { Text(if (favorite) "Убрать закладку" else "В закладки") }
    when (ttsAvailability) {
        TtsAvailability.Initializing -> Text("Проверка офлайн-произношения…")
        TtsAvailability.Unavailable -> Text("Английский офлайн-голос недоступен")
        TtsAvailability.Available -> Unit
    }
    if (playback == PlaybackSource.Unavailable) Text("Произношение недоступно")
    Button(onClick = onPlay, enabled = meaning.text.isNotBlank()) { Text("Произнести") }
    Button(onClick = { copy(context, meaning.text) }, enabled = meaning.text.isNotBlank()) { Text("Копировать слово") }
    Button(onClick = { copy(context, meaning.translation.orEmpty()) }, enabled = !meaning.translation.isNullOrBlank()) { Text("Копировать перевод") }
    Spacer(Modifier.height(8.dp))
    Text("Пример")
    Text(meaning.examples.firstOrNull() ?: "Примеры отсутствуют")
    Button(onClick = { viewModel.toggle(CardSection.Examples) }) { Text("Другие примеры") }
    if (CardSection.Examples in expanded) meaning.examples.drop(1).forEach { Text(it) }
    Button(onClick = { viewModel.toggle(CardSection.Definition) }) { Text("Определение") }
    if (CardSection.Definition in expanded) Text(meaning.definition ?: "Определение отсутствует")
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("SpeakEnot", text))
}
