package com.galyaminsky.speakenot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.galyaminsky.speakenot.domain.DictionaryError
import com.galyaminsky.speakenot.domain.DictionaryRepository
import com.galyaminsky.speakenot.domain.DictionaryResult
import com.galyaminsky.speakenot.domain.HistoryDictionary
import com.galyaminsky.speakenot.domain.HistoryEntry
import com.galyaminsky.speakenot.domain.HistoryRepository
import com.galyaminsky.speakenot.domain.NetworkAvailability
import com.galyaminsky.speakenot.domain.AlwaysOnline
import com.galyaminsky.speakenot.domain.QueryInput
import com.galyaminsky.speakenot.domain.SearchGroup
import com.galyaminsky.speakenot.domain.parseQuery
import com.galyaminsky.speakenot.domain.searchGroups
import com.galyaminsky.speakenot.domain.searchSuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Invalid : SearchStatus
    data object Loading : SearchStatus
    data object Success : SearchStatus
    data object Empty : SearchStatus
    data class Error(val reason: DictionaryError) : SearchStatus
}

data class SearchUiState(
    val query: String = "",
    val input: QueryInput = QueryInput.Empty,
    val status: SearchStatus = SearchStatus.Idle,
    val suggestions: List<String> = emptyList(),
    val groups: List<SearchGroup> = emptyList(),
)

class DictionaryViewModel(private val repository: DictionaryRepository, private val history: HistoryRepository? = null, private val network: NetworkAvailability = AlwaysOnline) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState
    private val mutableHistory = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val recentHistory: StateFlow<List<HistoryEntry>> = mutableHistory
    private val mutableCleared = MutableSharedFlow<List<HistoryEntry>>(extraBufferCapacity = 1)
    val cleared: SharedFlow<List<HistoryEntry>> = mutableCleared
    private var request: Job? = null
    private var generation = 0L

    init {
        history?.let { store ->
            viewModelScope.launch {
                store.observeRecent(HistoryDictionary.ENG).collect { mutableHistory.value = it }
            }
        }
    }

    fun onQueryChanged(query: String) = schedule(query, immediate = false)
    fun searchNow() = schedule(state.value.query, immediate = true)
    fun retry() = searchNow()
    fun selectSuggestion(suggestion: String) = schedule(suggestion, immediate = true)
    fun selectHistory(query: String) = schedule(query, immediate = true)

    fun clearHistory() {
        history?.let { store ->
            viewModelScope.launch {
                val removed = store.clear(HistoryDictionary.ENG)
                if (removed.isNotEmpty()) mutableCleared.emit(removed)
            }
        }
    }

    fun undoClear(entries: List<HistoryEntry>) { history?.let { viewModelScope.launch { it.restore(entries) } } }

    private fun schedule(query: String, immediate: Boolean) {
        generation++
        request?.cancel()
        val current = generation
        val input = parseQuery(query)
        val status = when (input) {
            QueryInput.Empty -> SearchStatus.Idle
            QueryInput.Invalid -> SearchStatus.Invalid
            is QueryInput.Valid -> SearchStatus.Loading
        }
        mutableState.value = SearchUiState(query = query, input = input, status = status)
        if (input !is QueryInput.Valid) return
        request = viewModelScope.launch {
            if (!immediate) delay(400)
            try {
                if (!network.isAvailable()) {
                    if (current == generation) mutableState.value = mutableState.value.copy(status = SearchStatus.Error(DictionaryError.Offline))
                    return@launch
                }
                val result = repository.search(input.text)
                if (current != generation) return@launch
                mutableState.value = when (result) {
                    is DictionaryResult.Failure -> mutableState.value.copy(status = SearchStatus.Error(result.error))
                    is DictionaryResult.Success -> {
                        val groups = searchGroups(input.text, result.value)
                        mutableState.value.copy(
                            status = if (result.value.isEmpty()) SearchStatus.Empty else SearchStatus.Success,
                            suggestions = searchSuggestions(input.text, result.value),
                            groups = groups,
                        )
                    }
                }
                if (result is DictionaryResult.Success && result.value.isNotEmpty() && current == generation) {
                    try { history?.record(HistoryDictionary.ENG, input.text) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* History failure must not hide a successful search. */ }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (current == generation) mutableState.value = mutableState.value.copy(status = SearchStatus.Error(DictionaryError.Unknown))
            }
        }
    }
}
