package com.galyaminsky.speakenot.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.FavoriteRepository
import com.galyaminsky.speakenot.domain.HistoryRepository
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.QueryInput

object AppRoute {
    const val Home = "home"
    const val Search = "search"
    const val Favorites = "favorites"
    const val Word = "word/{meaningId}"

    fun meaning(id: Long): String = "word/$id"

    fun showBottomBar(route: String?): Boolean = route != Word
}

@Composable
fun SpeakEnotApp(repository: DictionaryRepository, favorites: FavoriteRepository, history: HistoryRepository, network: NetworkAvailability, imageLoader: MeaningImageLoader) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, bottomBar = {
        if (AppRoute.showBottomBar(route)) {
            NavigationBar {
                listOf(AppRoute.Home to "Главная", AppRoute.Search to "Поиск", AppRoute.Favorites to "Избранное").forEach { (destination, title) ->
                    NavigationBarItem(
                        selected = route == destination,
                        onClick = {
                            navController.navigate(destination) {
                                launchSingleTop = true
                                popUpTo(AppRoute.Home) { saveState = true }
                                restoreState = true
                            }
                        },
                        icon = { Text(title.take(1)) },
                        label = { Text(title) },
                    )
                }
            }
        }
    }) { padding ->
        NavHost(navController, startDestination = AppRoute.Home, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(AppRoute.Home) { Text("Главная") }
            composable(AppRoute.Search) {
                val factory = remember(repository, history, network) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = DictionaryViewModel(repository, history, network) as T
                    }
                }
                SearchScreen(viewModel(factory = factory), snackbarHostState, onMeaningSelected = { navController.navigate(AppRoute.meaning(it)) })
            }
            composable(AppRoute.Favorites) {
                val factory = remember(favorites) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = FavoritesViewModel(favorites) as T
                    }
                }
                FavoritesScreen(viewModel(factory = factory), snackbarHostState) { navController.navigate(AppRoute.meaning(it)) }
            }
            composable(AppRoute.Word) { cardEntry ->
                val factory = remember(repository, favorites, network) {
                    object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T = WordCardViewModel(repository, favorites, network) as T
                    }
                }
                WordCardScreen(
                    meaningId = cardEntry.arguments?.getString("meaningId"),
                    viewModel = viewModel(factory = factory),
                    imageLoader = imageLoader,
                    network = network,
                    snackbarHostState = snackbarHostState,
                    onBack = { if (!navController.popBackStack()) navController.navigate(AppRoute.Search) },
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(viewModel: DictionaryViewModel, snackbarHostState: SnackbarHostState, onMeaningSelected: (Long) -> Unit) {
    val state by viewModel.state.collectAsState()
    val recent by viewModel.recentHistory.collectAsState()
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.cleared.collect { removed ->
            if (snackbarHostState.showSnackbar("История очищена", "Отменить") == SnackbarResult.ActionPerformed) {
                viewModel.undoClear(removed)
            }
        }
    }
    Column {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChanged,
            label = { Text("Слово или словосочетание") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.searchNow() }),
            modifier = Modifier.fillMaxWidth(),
        )
        val language = (state.input as? QueryInput.Valid)?.language
        if (language != null) Text("Язык: $language")
        LazyColumn {
            if (recent.isNotEmpty()) {
                item {
                    Text("Недавние запросы")
                    Button(onClick = viewModel::clearHistory) { Text("Очистить историю") }
                }
                items(recent, key = { "history:${it.query}" }) { entry ->
                    Button(onClick = { viewModel.selectHistory(entry.query) }) { Text(entry.query) }
                }
            }
            if (state.status == SearchStatus.Success && state.suggestions.isNotEmpty()) {
                item { Text("Подсказки") }
                items(state.suggestions) { suggestion ->
                    Button(onClick = { viewModel.selectSuggestion(suggestion) }) { Text(suggestion) }
                }
            }
            when (val status = state.status) {
                SearchStatus.Idle -> item { Text("Введите запрос") }
                SearchStatus.Invalid -> item { Text("Используйте только один язык: английский или русский") }
                SearchStatus.Loading -> item { Text("Загрузка…") }
                SearchStatus.Empty -> item {
                    Text("Ничего не найдено")
                    Button(onClick = viewModel::retry) { Text("Повторить") }
                }
                is SearchStatus.Error -> item {
                    Text(dictionaryErrorMessage(status.reason))
                    Button(onClick = viewModel::retry) { Text("Повторить") }
                }
                SearchStatus.Success -> {
                    state.groups.forEach { group ->
                        item { Text(group.partOfSpeech ?: "Другая часть речи") }
                        items(group.items) { result ->
                            Button(onClick = { onMeaningSelected(result.meaning.id) }) {
                                Text("${result.word} — ${result.meaning.translation.orEmpty()}")
                            }
                        }
                    }
                }
            }
        }
    }
}
