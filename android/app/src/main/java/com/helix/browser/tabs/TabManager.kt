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
    val tabIds: MutableList<String> = mutableListOf(),
    // ARGB color used to render the group header / colored tint in the grid.
    // Defaults to the first palette entry so older persisted groups (which
    // predate this field) still render with a stable, valid color.
    var color: Int = GROUP_COLORS[0]
) {
    companion object {
        // Chrome-like group color palette (ARGB). Kept here so both the model
        // and the UI color picker reference one source of truth.
        val GROUP_COLORS = intArrayOf(
            0xFF7B68EE.toInt(), // purple
            0xFF26A69A.toInt(), // teal
            0xFFEF5350.toInt(), // red
            0xFF42A5F5.toInt(), // blue
            0xFFFFA726.toInt(), // orange
            0xFF66BB6A.toInt(), // green
            0xFFEC407A.toInt(), // pink
            0xFFBDBDBD.toInt()  // grey
        )
    }
}

// Immutable snapshot capturing everything needed to faithfully restore a
// single closed tab via undo: the tab itself, the position it occupied, whether
// it was the foreground tab, and the group it belonged to.
data class ClosedTabSnapshot(
    val tab: BrowserTab,
    val index: Int,
    val wasCurrent: Boolean,
    val groupId: String?,
    val groupName: String?,
    val groupColor: Int?
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

    fun findTab(tabId: String): BrowserTab? = _tabs.firstOrNull { it.id == tabId }

    fun closeTab(tabId: String) {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        val tab = _tabs[index]
        // Don't allow closing pinned tabs without explicit unpin
        if (tab.isPinned) return
        // Save to recently closed (keep max 10) — strip bitmaps so they can be GC'd.
        if (tab.url.isNotEmpty() && !tab.isIncognito) {
            _recentlyClosed.add(0, tab.copy(thumbnail = null, favicon = null))
            if (_recentlyClosed.size > 10) _recentlyClosed.removeAt(_recentlyClosed.size - 1)
        }
        // Drop bitmap references so they can be GC'd, but do NOT eagerly
        // recycle(): notifyChanged() publishes defensive copies that share the
        // same Bitmap instances, and adapters / ImageViews (phone TabsAdapter,
        // tablet DesktopTabAdapter, in-flight Glide loads) may still be bound to
        // them. Synchronously recycling here caused "Canvas: trying to use a
        // recycled bitmap" on the next draw/scroll. Clearing the strong refs is
        // enough for the GC to reclaim the native memory once nothing draws them.
        tab.favicon = null
        tab.thumbnail = null
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

    // Single close path used by every UI close affordance (tap-X, swipe,
    // batch close, context menu). Removes the tab and returns a snapshot the
    // caller can hand to undoClose() from a Snackbar. Returns null when the
    // close was a no-op (unknown id, or a pinned tab which we refuse to close).
    //
    // Unlike closeTab(), this captures the LIVE tab instance (including its
    // bitmaps) in the snapshot so undo can restore the thumbnail/favicon, and
    // it records the original position + group so undo is faithful. The tab is
    // still added to recentlyClosed for the longer-lived reopen machinery.
    fun closeWithUndo(tabId: String): ClosedTabSnapshot? {
        val index = _tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return null
        val tab = _tabs[index]
        if (tab.isPinned) return null

        val wasCurrent = _currentIndex == index
        val groupId = tab.groupId
        val group = groupId?.let { gid -> _tabGroups.find { it.id == gid } }
        val groupName = group?.name
        val groupColor = group?.color

        // Persist a bitmap-free copy to recentlyClosed (parity with closeTab),
        // skipping incognito / blank-url tabs which must never be persisted.
        if (tab.url.isNotEmpty() && !tab.isIncognito) {
            _recentlyClosed.add(0, tab.copy(thumbnail = null, favicon = null))
            if (_recentlyClosed.size > 10) _recentlyClosed.removeAt(_recentlyClosed.size - 1)
        }

        // Snapshot the live instance BEFORE we detach it from its group so the
        // restored tab keeps its bitmaps. Bitmaps are intentionally NOT recycled
        // here so undo can re-show them; they are GC'd if undo never happens.
        val snapshot = ClosedTabSnapshot(
            tab = tab,
            index = index,
            wasCurrent = wasCurrent,
            groupId = groupId,
            groupName = groupName,
            groupColor = groupColor
        )

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
        return snapshot
    }

    // Restore a tab previously removed via closeWithUndo(). Best-effort: if the
    // id is somehow already present (double undo / race) it is a no-op. The tab
    // is reinserted at its original index (clamped) and its group is recreated
    // if it had been the group's last member.
    fun undoClose(snapshot: ClosedTabSnapshot) {
        val tab = snapshot.tab
        if (_tabs.any { it.id == tab.id }) return

        val insertIndex = snapshot.index.coerceIn(0, _tabs.size)
        _tabs.add(insertIndex, tab)

        val gid = snapshot.groupId
        if (gid != null) {
            var group = _tabGroups.find { it.id == gid }
            if (group == null) {
                // The group was dissolved when this tab (its last member) left.
                // Recreate it with the original id/name/color so headers persist.
                group = TabGroup(
                    id = gid,
                    name = snapshot.groupName ?: "",
                    color = snapshot.groupColor ?: TabGroup.GROUP_COLORS[0]
                )
                _tabGroups.add(group)
            }
            if (!group.tabIds.contains(tab.id)) group.tabIds.add(tab.id)
            tab.groupId = group.id
            tab.groupName = group.name
        }

        val currentId = currentTab?.id
        _currentIndex = when {
            snapshot.wasCurrent -> insertIndex
            currentId != null -> _tabs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            else -> _tabs.size - 1
        }
        notifyChanged()
    }

    // Batch close for multi-select mode. Closes each (non-pinned) id through the
    // same single path and returns snapshots ordered by ASCENDING original index
    // so undoCloseAll() can reinsert them left-to-right. Pinned / unknown ids are
    // skipped.
    fun closeAllWithUndo(tabIds: Collection<String>): List<ClosedTabSnapshot> {
        // Remove from the rightmost position first so earlier indices stay valid.
        val ordered = tabIds.distinct()
            .mapNotNull { id -> _tabs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { it to id } }
            .sortedByDescending { it.first }
        val snapshots = ordered.mapNotNull { (_, id) -> closeWithUndo(id) }
        return snapshots.sortedBy { it.index }
    }

    fun undoCloseAll(snapshots: List<ClosedTabSnapshot>) {
        snapshots.sortedBy { it.index }.forEach { undoClose(it) }
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
        // Capture the foreground tab's id BEFORE reordering. After the move the
        // currentTab getter would resolve _tabs[_currentIndex] against a stale
        // index and return the wrong tab, silently switching which page is
        // active. Resolving by id afterwards keeps the same tab current.
        val currentId = currentTab?.id
        tab.isPinned = !tab.isPinned
        // Move pinned tabs to the front
        if (tab.isPinned) {
            val index = _tabs.indexOf(tab)
            _tabs.removeAt(index)
            val insertIndex = _tabs.indexOfLast { it.isPinned } + 1
            _tabs.add(insertIndex.coerceAtLeast(0), tab)
        }
        // Re-resolve the current index from the captured id for both pin and
        // unpin (unpin does not move the tab, but the index must still hold).
        if (currentId != null) {
            _currentIndex = _tabs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        }
        notifyChanged()
    }

    fun muteTab(tabId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        tab.isMuted = !tab.isMuted
        notifyChanged()
    }

    // Toggle the per-tab desktop-site flag and publish a list update so
    // MainActivity can re-apply the WebView user-agent / viewport for that tab.
    // No-op (no spurious emission) if the tab id is unknown.
    fun toggleDesktopMode(tabId: String): Boolean {
        val tab = _tabs.find { it.id == tabId } ?: return false
        tab.isDesktopMode = !tab.isDesktopMode
        notifyChanged()
        return tab.isDesktopMode
    }

    // Explicitly set the per-tab desktop-site flag. Only emits a list update
    // when the value actually changes, to avoid redundant DiffUtil passes when
    // the caller is just syncing an already-correct state.
    fun setDesktopMode(tabId: String, enabled: Boolean) {
        val tab = _tabs.find { it.id == tabId } ?: return
        if (tab.isDesktopMode == enabled) return
        tab.isDesktopMode = enabled
        notifyChanged()
    }

    fun duplicateTab(tabId: String): BrowserTab? {
        val source = _tabs.find { it.id == tabId } ?: return null
        val newTab = BrowserTab(
            title = source.title,
            url = source.url,
            isIncognito = source.isIncognito,
            isDesktopMode = source.isDesktopMode,
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

    fun createTabGroup(
        name: String,
        tabIds: List<String>,
        color: Int = TabGroup.GROUP_COLORS[0]
    ): TabGroup {
        val validIds = tabIds.filter { id -> _tabs.any { it.id == id } }.toMutableList()
        val group = TabGroup(name = name, tabIds = validIds, color = color)
        _tabGroups.add(group)
        validIds.forEach { id ->
            // Detach from any prior group first so a tab never lives in two
            // groups (keeps tabIds and the per-tab groupId consistent).
            val tab = _tabs.find { it.id == id } ?: return@forEach
            tab.groupId?.let { prev ->
                if (prev != group.id) _tabGroups.find { it.id == prev }?.tabIds?.remove(id)
            }
            tab.groupId = group.id
            tab.groupName = name
        }
        _tabGroups.removeAll { it.id != group.id && it.tabIds.isEmpty() }
        notifyChanged()
        return group
    }

    fun addTabToGroup(tabId: String, groupId: String) {
        val tab = _tabs.find { it.id == tabId } ?: return
        val group = _tabGroups.find { it.id == groupId } ?: return
        // Remove from previous group if any
        if (tab.groupId != null && tab.groupId != groupId) {
            _tabGroups.find { it.id == tab.groupId }?.tabIds?.remove(tabId)
        }
        if (!group.tabIds.contains(tabId)) group.tabIds.add(tabId)
        tab.groupId = group.id
        tab.groupName = group.name
        _tabGroups.removeAll { it.id != groupId && it.tabIds.isEmpty() }
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

    fun renameGroup(groupId: String, name: String) {
        val group = _tabGroups.find { it.id == groupId } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || group.name == trimmed) return
        group.name = trimmed
        _tabs.filter { it.groupId == groupId }.forEach { it.groupName = trimmed }
        notifyChanged()
    }

    fun setGroupColor(groupId: String, color: Int) {
        val group = _tabGroups.find { it.id == groupId } ?: return
        if (group.color == color) return
        group.color = color
        notifyChanged()
    }

    fun findGroup(groupId: String): TabGroup? = _tabGroups.find { it.id == groupId }

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
                put("isDesktopMode", tab.isDesktopMode)
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
                put("color", group.color)
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
                    // optString returns "" (not null) for missing keys; we must
                    // post-filter rather than rely on the nullable overload —
                    // passing null as the default emits a NothingType warning.
                    groupId = obj.optString("groupId").takeIf { it.isNotEmpty() && it != "null" },
                    groupName = obj.optString("groupName").takeIf { it.isNotEmpty() && it != "null" },
                    lastAccessTime = obj.optLong("lastAccessTime", System.currentTimeMillis()),
                    isMuted = obj.optBoolean("isMuted", false),
                    isSuspended = obj.optBoolean("isSuspended", false),
                    isDesktopMode = obj.optBoolean("isDesktopMode", false)
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
                        tabIds = tabIds,
                        color = obj.optInt("color", TabGroup.GROUP_COLORS[0])
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
        // Publish DEFENSIVE COPIES, not the live BrowserTab instances. Titles,
        // urls and favicons are mutated in place on the existing tabs (e.g. from
        // the chrome client). If we emitted the same references, DiffUtil's
        // areContentsTheSame() would compare an object to itself and always see
        // "no change", so background tabs would never refresh in the switcher /
        // tab bar. copy() is a shallow copy: the Bitmap fields are shared by
        // reference, which is safe because closeTab() no longer recycles them.
        tabsLiveData.value = _tabs.map { it.copy() }
        currentTabLiveData.value = currentTab?.copy()
    }
}
