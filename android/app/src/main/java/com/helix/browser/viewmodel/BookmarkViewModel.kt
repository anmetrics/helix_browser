package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.data.Bookmark
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class BookmarkViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as HelixApp).bookmarkRepository

    val allBookmarks = repo.getAllBookmarks()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Id of the folder the user is currently inside (null = root). Held in the
     * ViewModel so it survives rotation. [folderStack] records the path of
     * folder rows we descended through so "up" / system-back can pop one level
     * and so the UI can render a breadcrumb / title.
     */
    private val _currentFolderId = MutableStateFlow<Long?>(null)
    val currentFolderId: StateFlow<Long?> = _currentFolderId.asStateFlow()

    private val _folderStack = MutableStateFlow<List<Bookmark>>(emptyList())
    val folderStack: StateFlow<List<Bookmark>> = _folderStack.asStateFlow()

    /** The folder we're inside, or null at root (for the toolbar subtitle). */
    val currentFolder: Bookmark? get() = _folderStack.value.lastOrNull()

    /** True while showing the root level (controls back/up affordance). */
    val isAtRoot: Boolean get() = _folderStack.value.isEmpty()

    /** True while a search is active (controls whether back exits the screen). */
    val isSearching: Boolean get() = _searchQuery.value.isNotBlank()

    // Reactive list the UI renders. When the search box is non-blank we show a
    // flat search across ALL folders (Chrome-style); otherwise we show the
    // children of the current folder. flatMapLatest cancels the previous query
    // as soon as the query/folder changes, so a slow DB result can never
    // overwrite the newest one. Preserves the original debounced reactive search.
    val results: StateFlow<List<Bookmark>> =
        combine(
            _searchQuery.debounce { q -> if (q.isBlank()) 0L else SEARCH_DEBOUNCE_MS },
            _currentFolderId
        ) { query, folderId -> query.trim() to folderId }
            .distinctUntilChanged()
            .flatMapLatest { (trimmed, folderId) ->
                if (trimmed.isBlank()) repo.getChildren(folderId) else repo.search(trimmed)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // --- Folder navigation -------------------------------------------------

    fun openFolder(folder: Bookmark) {
        if (!folder.isFolder) return
        _folderStack.value = _folderStack.value + folder
        _currentFolderId.value = folder.id
    }

    /** Go up one level. Returns false when already at root (let the screen exit). */
    fun navigateUp(): Boolean {
        val stack = _folderStack.value
        if (stack.isEmpty()) return false
        val newStack = stack.dropLast(1)
        _folderStack.value = newStack
        _currentFolderId.value = newStack.lastOrNull()?.id
        return true
    }

    // --- CRUD --------------------------------------------------------------

    fun createFolder(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) repo.addFolder(trimmed, _currentFolderId.value)
    }

    fun renameFolder(folder: Bookmark, newName: String) = viewModelScope.launch {
        repo.renameFolder(folder, newName)
    }

    fun editBookmark(bookmark: Bookmark, newTitle: String, newUrl: String) = viewModelScope.launch {
        repo.updateBookmark(bookmark.withEdits(newTitle, newUrl))
    }

    fun deleteBookmark(bookmark: Bookmark) = viewModelScope.launch {
        if (bookmark.isFolder) {
            // If we're standing inside the folder being deleted, pop up first so
            // the list doesn't keep showing a folder that no longer exists.
            if (_currentFolderId.value == bookmark.id) navigateUp()
            repo.deleteFolder(bookmark)
        } else {
            repo.removeBookmark(bookmark)
        }
    }

    /**
     * Move [item] into [targetParentId]. Invokes [onResult] with false when the
     * move was rejected (folder into itself/descendant) so the UI can warn.
     */
    fun moveToFolder(item: Bookmark, targetParentId: Long?, onResult: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
            val ok = repo.moveToFolder(item, targetParentId)
            onResult(ok)
        }

    /**
     * Folders the user may move [item] into: every folder except [item] itself
     * and its descendants (those would create a cycle). Root is offered by the
     * caller separately. Returned on the caller's coroutine.
     */
    suspend fun foldersForMove(item: Bookmark): List<Bookmark> {
        val all = repo.getAllFolders()
        if (!item.isFolder) return all
        return all.filter { it.id != item.id && !repo.isDescendant(item.id, it.id) }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 180L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
