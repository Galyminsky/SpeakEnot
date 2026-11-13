package com.galyaminsky.speakenot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.Meaning
import com.galyaminsky.speakenot.domain.FavoriteDictionary
import com.galyaminsky.speakenot.domain.FavoriteEntry
import com.galyaminsky.speakenot.domain.FavoriteRepository
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.AlwaysOnline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

sealed interface CardStatus {
    data object Loading : CardStatus
    data class Success(val meaning: Meaning) : CardStatus
    data object InvalidId : CardStatus
    data object NotFound : CardStatus
    data class Error(val reason: DictionaryError) : CardStatus
}

enum class CardSection { Examples, Definition }

data class CardUiState(val status: CardStatus = CardStatus.Loading, val expanded: Set<CardSection> = emptySet(), val favorite: Boolean = false)

class WordCardViewModel(private val repository: DictionaryRepository, private val favorites: FavoriteRepository? = null, private val network: NetworkAvailability = AlwaysOnline) : ViewModel() {
    private val mutableState = MutableStateFlow(CardUiState())
    val state: StateFlow<CardUiState> = mutableState
    private var request: Job? = null
    private var favoriteObservation: Job? = null
    private val mutableRemovals = MutableSharedFlow<FavoriteEntry>(extraBufferCapacity = 1)
    val removals: SharedFlow<FavoriteEntry> = mutableRemovals
    private var generation = 0L
    private var currentId: Long? = null

    fun load(rawId: String?) {
        generation++
        request?.cancel()
        val id = rawId?.toLongOrNull()?.takeIf { it > 0 }
        currentId = id
        mutableState.value = CardUiState(if (id == null) CardStatus.InvalidId else CardStatus.Loading)
        favoriteObservation?.cancel()
        if (id == null) return
        favoriteObservation = favorites?.let { store ->
            viewModelScope.launch {
                store.observe(FavoriteDictionary.ENG).collect { entries ->
                    if (currentId == id) mutableState.value = mutableState.value.copy(favorite = entries.any { it.meaningId == id })
                }
            }
        }
        val current = generation
        request = viewModelScope.launch {
            try {
                if (!network.isAvailable()) {
                    if (current == generation) mutableState.value = mutableState.value.copy(status = CardStatus.Error(DictionaryError.Offline))
                    return@launch
                }
                val result = repository.meanings(listOf(id))
                if (current != generation) return@launch
                mutableState.value = mutableState.value.copy(status = when (result) {
                    is DictionaryResult.Failure -> CardStatus.Error(result.error)
                    is DictionaryResult.Success -> result.value.firstOrNull { it.id == id }
                        ?.let(CardStatus::Success) ?: CardStatus.NotFound
                })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) mutableState.value = mutableState.value.copy(status = CardStatus.Error(DictionaryError.Unknown))
            }
        }
    }

    fun retry() { currentId?.let { load(it.toString()) } }

    fun toggleFavorite() {
        val id = (state.value.status as? CardStatus.Success)?.meaning?.id ?: return
        val store = favorites ?: return
        viewModelScope.launch {
            if (state.value.favorite) {
                store.remove(FavoriteDictionary.ENG, id)?.let { mutableRemovals.emit(it) }
            } else {
                store.add(FavoriteDictionary.ENG, id)
            }
        }
    }

    fun undoRemoval(entry: FavoriteEntry) { favorites?.let { viewModelScope.launch { it.restore(entry) } } }

    fun toggle(section: CardSection) {
        val expanded = mutableState.value.expanded
        mutableState.value = mutableState.value.copy(expanded = if (section in expanded) expanded - section else expanded + section)
    }
}
