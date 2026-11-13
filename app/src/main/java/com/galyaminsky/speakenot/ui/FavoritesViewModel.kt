package com.galyaminsky.speakenot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galyaminsky.speakenot.domain.FavoriteDictionary
import com.galyaminsky.speakenot.domain.FavoriteEntry
import com.galyaminsky.speakenot.domain.FavoriteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FavoritesViewModel(private val repository: FavoriteRepository) : ViewModel() {
    private val mutableEntries = MutableStateFlow<List<FavoriteEntry>>(emptyList())
    val entries: StateFlow<List<FavoriteEntry>> = mutableEntries
    private val mutableRemovals = MutableSharedFlow<FavoriteEntry>(extraBufferCapacity = 1)
    val removals: SharedFlow<FavoriteEntry> = mutableRemovals

    init {
        viewModelScope.launch { repository.observe(FavoriteDictionary.ENG).collect { mutableEntries.value = it } }
    }

    fun remove(id: Long) {
        viewModelScope.launch {
            repository.remove(FavoriteDictionary.ENG, id)?.let { mutableRemovals.emit(it) }
        }
    }

    fun undo(entry: FavoriteEntry) { viewModelScope.launch { repository.restore(entry) } }
}
