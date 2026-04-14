package com.helix.browser.tabs

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TabGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val tabIds: MutableList<String> = mutableListOf()
)

class TabManager {

    private val _tabs = mutableListOf<BrowserTab>()
    val tabs: List<BrowserTab> get() = _tabs.toList()

    val tabsLiveData = MutableLiveData<List<BrowserTab>>()
    val currentTabLiveData = MutableLiveData<BrowserTab?>()

    private var _currentIndex = -1
    val currentIndex get() = _currentIndex
    val currentTab get() = if (_currentIndex >= 0 && _currentIndex < _tabs.size) _tabs[_currentIndex] else null
    val tabCount get() = _tabs.size

    private val _tabGroups = mutableListOf<TabGroup>()
    val tabGroups: List<TabGroup> get() = _tabGroups.toList()

    private val _recentlyClosed = mutableListOf<BrowserTab>()
    val recentlyClosed: List<BrowserTab> get() = _recentlyClosed.toList()

    companion object {
        private const val PREFS_NAME = "helix_tabs"
        private const val KEY_TABS = "saved_tabs"
        private const val KEY_GROUPS = "saved_groups"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val SUSPEND_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
    }

    fun addTab(isIncognito: Boolean = false, url: String = ""): BrowserTab {
        val tab = BrowserTab(
            isIncognito = isIncognito,
            url = url,
            lastAccessTime = System.currentTimeMillis()
        )
        _tabs.add(tab)
        _currentIndex = _tabs.size - 1
        notifyChanged()
        return tab
    }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tab = _tabs[index]
        // Don't allow closing pinned tabs without explicit unpin
        if (tab.isPinned) return
        // Save to recently closed (keep max 10)
        if (tab.url.isNotEmpty() && !tab.isIncognito) {
            _recentlyClosed.add(0, tab.copy(thumbnail = null, favicon = null))
            if (_recentlyClosed.size > 10) _recentlyClosed.removeAt(_recentlyClosed.size - 1)
        }
        // Remove from any group
        _tabGroups.forEach { it.tabIds.remove(tabId) }
        _tabGroups.removeAll { it.tabIds.isEmpty() }
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
            _tabs[_currentIndex].lastAccessTime = System.currentTimeMillis()
            _tabs[_currentIndex].isSuspended = false
            currentTabLiveData.value = _tabs[_currentIndex]
        }
        notifyChanged()
    }

    fun switchToIndex(index: Int) {
        if (index in 0 until _tabs.size) {
            _currentIndex = index
            _tabs[_currentIndex].lastAccessTime = System.currentTimeMillis()
            _tabs[_currentIndex].isSuspended = false
            currentTabLiveData.value = _tabs[_currentIndex]
        }
        notifyChanged()
    }

    fun updateCurrentTab(title: String? = null, url: String? = null) {
        val tab = currentTab ?: return
        title?.let { tab.title = it }
        url?.let { tab.url = it }
        tab.lastAccessTime = System.currentTimeMillis()
        notifyChanged()
    }

    fun closeAllTabs() {
        _tabs.clear()
        _tabGroups.clear()
        _currentIndex = -1
        notifyChanged()
    }

    fun closeAllIncognito() {
        val incognitoIds = _tabs.filter { it.isIncognito }.map { it.id }.toSet()
        _tabGroups.forEach { group -> group.tabIds.removeAll { it in incognitoIds } }
        _tabGroups.removeAll { it.tabIds.isEmpty() }
        val nonIncognito = _tabs.filter { !it.isIncognito }
        _tabs.clear()
        _tabs.addAll(nonIncognito)
        if (_currentIndex >= _tabs.size) _currentIndex = _tabs.size - 1
        notifyChanged()
    }

    // --- Enhanced Tab Operations ---

    fun pinTab(tabId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        tab.isPinned = !tab.isPinned
        // Move pinned tabs to the front
        if (tab.isPinned) {
            val index = _tabs.indexOf(tab)
            _tabs.removeAt(index)
            val insertIndex = _tabs.indexOfLast { it.isPinned } + 1
            _tabs.add(insertIndex.coerceAtLeast(0), tab)
            _currentIndex = _tabs.indexOfFirst { it.id == currentTab?.id }
        }
        notifyChanged()
    }

    fun muteTab(tabId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        tab.isMuted = !tab.isMuted
        notifyChanged()
    }

    fun duplicateTab(tabId: String): BrowserTab? {
        val source = _tabs.find { it.id == tabId } ?: return null
        val newTab = BrowserTab(
            title = source.title,
            url = source.url,
            isIncognito = source.isIncognito,
            lastAccessTime = System.currentTimeMillis()
        )
        val sourceIndex = _tabs.indexOf(source)
        _tabs.add(sourceIndex + 1, newTab)
        _currentIndex = sourceIndex + 1
        notifyChanged()
        return newTab
    }

    fun closeOtherTabs(exceptId: String) {
        val keep = _tabs.find { it.id == exceptId } ?: return
        val pinnedTabs = _tabs.filter { it.isPinned && it.id != exceptId }
        // Remove group references for closed tabs
        val keepIds = (pinnedTabs.map { it.id } + exceptId).toSet()
        _tabGroups.forEach { group -> group.tabIds.retainAll(keepIds) }
        _tabGroups.removeAll { it.tabIds.isEmpty() }
        _tabs.clear()
        _tabs.addAll(pinnedTabs)
        _tabs.add(keep)
        _currentIndex = _tabs.indexOf(keep)
        notifyChanged()
    }

    fun closeTabsToRight(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0 || index >= _tabs.size - 1) return
        val toRemove = _tabs.subList(index + 1, _tabs.size)
            .filter { !it.isPinned }
            .map { it.id }
            .toSet()
        _tabGroups.forEach { group -> group.tabIds.removeAll { it in toRemove } }
        _tabGroups.removeAll { it.tabIds.isEmpty() }
        _tabs.removeAll { it.id in toRemove }
        if (_currentIndex >= _tabs.size) _currentIndex = _tabs.size - 1
        notifyChanged()
    }

    fun createTabGroup(name: String, tabIds: List<String>): TabGroup {
        val validIds = tabIds.filter { id -> _tabs.any { it.id == id } }.toMutableList()
        val group = TabGroup(name = name, tabIds = validIds)
        _tabGroups.add(group)
        validIds.forEach { id ->
            _tabs.find { it.id == id }?.apply {
                groupId = group.id
                groupName = name
            }
        }
        notifyChanged()
        return group
    }

    fun addTabToGroup(tabId: String, groupId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        val group = _tabGroups.find { it.id == groupId } ?: return
        // Remove from previous group if any
        if (tab.groupId != null) {
            _tabGroups.find { it.id == tab.groupId }?.tabIds?.remove(tabId)
        }
        group.tabIds.add(tabId)
        tab.groupId = group.id
        tab.groupName = group.name
        notifyChanged()
    }

    fun removeTabFromGroup(tabId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        val groupId = tab.groupId ?: return
        _tabGroups.find { it.id == groupId }?.tabIds?.remove(tabId)
        _tabGroups.removeAll { it.tabIds.isEmpty() }
        tab.groupId = null
        tab.groupName = null
        notifyChanged()
    }

    fun searchTabs(query: String): List<BrowserTab> {
        if (query.isBlank()) return _tabs.toList()
        val lowerQuery = query.lowercase()
        return _tabs.filter { tab ->
            tab.title.lowercase().contains(lowerQuery) ||
                tab.url.lowercase().contains(lowerQuery)
        }
    }

    // --- Persistence ---

    fun saveTabs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Save tabs as JSON array
        val tabsArray = JSONArray()
        for (tab in _tabs) {
            if (tab.isIncognito) continue // Don't persist incognito tabs
            val obj = JSONObject().apply {
                put("id", tab.id)
                put("title", tab.title)
                put("url", tab.url)
                put("isPinned", tab.isPinned)
                put("groupId", tab.groupId ?: JSONObject.NULL)
                put("groupName", tab.groupName ?: JSONObject.NULL)
                put("lastAccessTime", tab.lastAccessTime)
                put("isMuted", tab.isMuted)
                put("isSuspended", tab.isSuspended)
            }
            tabsArray.put(obj)
        }
        editor.putString(KEY_TABS, tabsArray.toString())

        // Save groups
        val groupsArray = JSONArray()
        for (group in _tabGroups) {
            val obj = JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("tabIds", JSONArray(group.tabIds))
            }
            groupsArray.put(obj)
        }
        editor.putString(KEY_GROUPS, groupsArray.toString())
        editor.putInt(KEY_CURRENT_INDEX, _currentIndex)
        editor.apply()
    }

    fun restoreTabs(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabsJson = prefs.getString(KEY_TABS, null) ?: return false
        val groupsJson = prefs.getString(KEY_GROUPS, null)

        return try {
            val tabsArray = JSONArray(tabsJson)
            if (tabsArray.length() == 0) return false

            _tabs.clear()
            for (i in 0 until tabsArray.length()) {
                val obj = tabsArray.getJSONObject(i)
                val url = obj.getString("url")
                val title = obj.getString("title")
                val tab = BrowserTab(
                    id = obj.getString("id"),
                    title = if (url.isEmpty() || url == "about:blank") "" else title,
                    url = url,
                    isPinned = obj.optBoolean("isPinned", false),
                    groupId = obj.optString("groupId", null).takeIf { it != "null" },
                    groupName = obj.optString("groupName", null).takeIf { it != "null" },
                    lastAccessTime = obj.optLong("lastAccessTime", System.currentTimeMillis()),
                    isMuted = obj.optBoolean("isMuted", false),
                    isSuspended = obj.optBoolean("isSuspended", false)
                )
                _tabs.add(tab)
            }

            // Restore groups
            _tabGroups.clear()
            if (groupsJson != null) {
                val groupsArray = JSONArray(groupsJson)
                for (i in 0 until groupsArray.length()) {
                    val obj = groupsArray.getJSONObject(i)
                    val tabIds = mutableListOf<String>()
                    val idsArray = obj.getJSONArray("tabIds")
                    for (j in 0 until idsArray.length()) {
                        tabIds.add(idsArray.getString(j))
                    }
                    _tabGroups.add(TabGroup(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        tabIds = tabIds
                    ))
                }
            }

            _currentIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
                .coerceIn(0, (_tabs.size - 1).coerceAtLeast(0))

            notifyChanged()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun suspendInactiveTabs() {
        val now = System.currentTimeMillis()
        var changed = false
        for (i in _tabs.indices) {
            if (i == _currentIndex) continue // Don't suspend current tab
            val tab = _tabs[i]
            if (!tab.isSuspended && !tab.isPinned &&
                (now - tab.lastAccessTime) > SUSPEND_TIMEOUT_MS
            ) {
                tab.isSuspended = true
                changed = true
            }
        }
        if (changed) notifyChanged()
    }

    private fun notifyChanged() {
        tabsLiveData.value = _tabs.toList()
        currentTabLiveData.value = currentTab
    }
}
