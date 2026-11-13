package com.galyaminsky.speakenot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel, snackbarHostState: SnackbarHostState, onOpen: (Long) -> Unit) {
    val entries by viewModel.entries.collectAsState()
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.removals.collect { removed ->
            if (snackbarHostState.showSnackbar("Удалено из избранного", "Отменить") == SnackbarResult.ActionPerformed) {
                viewModel.undo(removed)
            }
        }
    }
    Column {
        Text("Избранное ENG")
        if (entries.isEmpty()) Text("Пока ничего не сохранено")
        LazyColumn {
            items(entries, key = { it.meaningId }) { entry ->
                Button(onClick = { onOpen(entry.meaningId) }) { Text("Значение #${entry.meaningId}") }
                Button(onClick = { viewModel.remove(entry.meaningId) }) { Text("Удалить") }
            }
        }
    }
}
