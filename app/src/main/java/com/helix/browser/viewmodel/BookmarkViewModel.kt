package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.data.Bookmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as HelixApp).bookmarkRepository

    val allBookmarks = repo.getAllBookmarks()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults = repo.search(_searchQuery.value)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteBookmark(bookmark: Bookmark) = viewModelScope.launch {
        repo.removeBookmark(bookmark)
    }
}
