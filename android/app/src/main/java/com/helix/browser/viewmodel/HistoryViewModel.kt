package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.data.HistoryItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as HelixApp).historyRepository

    val allHistory = repo.getAllHistory()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Reactive list the UI should render: switches between the full history and a
    // filtered search whenever the query changes. flatMapLatest cancels the
    // previous (now-stale) query as soon as a new keystroke arrives, so we never
    // race a slower DB result onto the screen. The query is debounced and the DAO
    // call runs off the main thread (Room emits on its own dispatcher), keeping
    // typing smooth even with a large history table.
    val results: StateFlow<List<HistoryItem>> = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isBlank()) repo.getAllHistory() else repo.search(trimmed)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: HistoryItem) = viewModelScope.launch {
        repo.deleteItem(item)
    }

    fun clearAll() = viewModelScope.launch {
        repo.clearAll()
    }

    // Time-range "Clear browsing data". sinceMillis is an absolute epoch-millis
    // cutoff; the repository/DAO delete every visit with timestamp >= sinceMillis
    // off the main thread (Room runs suspend queries on its own executor). Pass 0
    // for "All time". onCleared signals completion so the UI can show the
    // confirmation toast only after the delete actually finished, never before.
    fun clearRange(sinceMillis: Long, onCleared: () -> Unit = {}) = viewModelScope.launch {
        repo.clearRange(sinceMillis)
        onCleared()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 180L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
