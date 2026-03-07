package com.helix.browser.tabs

import androidx.lifecycle.MutableLiveData

class TabManager {

    private val _tabs = mutableListOf<BrowserTab>()
    val tabs: List<BrowserTab> get() = _tabs.toList()

    val tabsLiveData = MutableLiveData<List<BrowserTab>>()
    val currentTabLiveData = MutableLiveData<BrowserTab?>()

    private var _currentIndex = -1
    val currentIndex get() = _currentIndex
    val currentTab get() = if (_currentIndex >= 0 && _currentIndex < _tabs.size) _tabs[_currentIndex] else null
    val tabCount get() = _tabs.size

    fun addTab(isIncognito: Boolean = false, url: String = ""): BrowserTab {
        val tab = BrowserTab(isIncognito = isIncognito, url = url)
        _tabs.add(tab)
        _currentIndex = _tabs.size - 1
        notifyChanged()
        return tab
    }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        _tabs.removeAt(index)
        if (_tabs.isEmpty()) {
            _currentIndex = -1
        } else if (_currentIndex >= _tabs.size) {
            _currentIndex = _tabs.size - 1
        } else if (_currentIndex > index) {
            _currentIndex--
        }
        notifyChanged()
    }

    fun switchToTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            _currentIndex = index
            currentTabLiveData.value = _tabs[_currentIndex]
        }
        notifyChanged()
    }

    fun switchToIndex(index: Int) {
        if (index in 0 until _tabs.size) {
            _currentIndex = index
            currentTabLiveData.value = _tabs[_currentIndex]
        }
        notifyChanged()
    }

    fun updateCurrentTab(title: String? = null, url: String? = null) {
        val tab = currentTab ?: return
        title?.let { tab.title = it }
        url?.let { tab.url = it }
        notifyChanged()
    }

    fun closeAllTabs() {
        _tabs.clear()
        _currentIndex = -1
        notifyChanged()
    }

    fun closeAllIncognito() {
        val nonIncognito = _tabs.filter { !it.isIncognito }
        _tabs.clear()
        _tabs.addAll(nonIncognito)
        if (_currentIndex >= _tabs.size) _currentIndex = _tabs.size - 1
        notifyChanged()
    }

    private fun notifyChanged() {
        tabsLiveData.value = _tabs.toList()
        currentTabLiveData.value = currentTab
    }
}
