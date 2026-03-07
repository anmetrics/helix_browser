package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.data.Bookmark
import com.helix.browser.data.HistoryItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.helix.browser.utils.Prefs

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HelixApp
    private val historyRepo = app.historyRepository
    private val bookmarkRepo = app.bookmarkRepository

    // Browser state
    val currentUrl = MutableLiveData<String>("")
    val currentTitle = MutableLiveData<String>("New Tab")
    val loadingProgress = MutableLiveData<Int>(0)
    val isLoading = MutableLiveData<Boolean>(false)
    val isSecure = MutableLiveData<Boolean>(false)
    val isBookmarked = MutableLiveData<Boolean>(false)
    val canGoBack = MutableLiveData<Boolean>(false)
    val canGoForward = MutableLiveData<Boolean>(false)
    val isDesktopMode = MutableLiveData<Boolean>(false)
    val isIncognito = MutableLiveData<Boolean>(false)

    // Find in page
    val findInPageQuery = MutableLiveData<String>("")
    val showFindInPage = MutableLiveData<Boolean>(false)

    fun onPageStarted(url: String) {
        currentUrl.value = url
        isLoading.value = true
        isSecure.value = url.startsWith("https://")
        // Check bookmark status
        viewModelScope.launch {
            isBookmarked.value = bookmarkRepo.isBookmarked(url)
        }
    }

    fun onPageFinished(url: String, title: String) {
        currentUrl.value = url
        currentTitle.value = title.ifEmpty { url }
        isLoading.value = false
        isSecure.value = url.startsWith("https://")

        // Save history (not incognito and if enabled)
        if (isIncognito.value != true && Prefs.isSaveHistoryEnabled(app)) {
            viewModelScope.launch {
                historyRepo.addHistory(
                    title = title.ifEmpty { url },
                    url = url
                )
            }
        }
        viewModelScope.launch {
            isBookmarked.value = bookmarkRepo.isBookmarked(url)
        }
    }

    fun onProgressChanged(progress: Int) {
        loadingProgress.value = progress
    }

    fun toggleBookmark(title: String, url: String) {
        viewModelScope.launch {
            val bookmarked = bookmarkRepo.toggleBookmark(title, url)
            isBookmarked.value = bookmarked
        }
    }

    fun updateNavState(canGoBack: Boolean, canGoForward: Boolean) {
        this.canGoBack.value = canGoBack
        this.canGoForward.value = canGoForward
    }
}
