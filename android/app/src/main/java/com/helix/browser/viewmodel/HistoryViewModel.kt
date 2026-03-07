package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.data.HistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as HelixApp).historyRepository

    val allHistory = repo.getAllHistory()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteItem(item: HistoryItem) = viewModelScope.launch {
        repo.deleteItem(item)
    }

    fun clearAll() = viewModelScope.launch {
        repo.clearAll()
    }
}
