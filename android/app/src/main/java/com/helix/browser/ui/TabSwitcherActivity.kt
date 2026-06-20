package com.helix.browser.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.billing.BillingManager
import com.helix.browser.databinding.ActivityTabSwitcherBinding
import com.helix.browser.tabs.BrowserTab
import com.helix.browser.tabs.ClosedTabSnapshot
import com.helix.browser.tabs.TabGroup
import com.helix.browser.tabs.TabManager
import com.helix.browser.ui.adapter.TabListItem
import com.helix.browser.ui.adapter.TabsAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class TabSwitcherActivity : BaseActivity() {

    private lateinit var binding: ActivityTabSwitcherBinding
    private lateinit var adapter: TabsAdapter
    private lateinit var billingManager: BillingManager
    private lateinit var tabManager: TabManager
    private var isGridMode = true
    private val spanCount get() = 2

    // Current search query (kept so grouping rebuilds preserve the filter).
    private var searchQuery: String = ""

    // Multi-select state.
    private var selectionMode = false
    private val selectedIds = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabSwitcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billingManager = BillingManager.getInstance(this)
        tabManager = (application as HelixApp).tabManager

        adapter = TabsAdapter(
            onTabClick = { tab ->
                val result = Intent().putExtra("tab_id", tab.id)
                setResult(Activity.RESULT_OK, result)
                finish()
            },
            onTabClose = { tab -> closeWithUndo(tab.id) },
            onTabLongPress = { tab, view ->
                if (selectionMode) {
                    toggleSelection(tab.id)
                } else {
                    showTabContextMenu(tab, view)
                }
            },
            onSelectionToggle = { tab -> toggleSelection(tab.id) },
            onHeaderClick = { header -> showGroupOptions(header) },
            activeTabId = tabManager.currentTab?.id
        )

        val layoutManager = GridLayoutManager(this, spanCount)
        layoutManager.spanSizeLookup = adapter.spanSizeLookup(spanCount)
        binding.tabsRecyclerView.apply {
            this.layoutManager = layoutManager
            adapter = this@TabSwitcherActivity.adapter
            setHasFixedSize(false)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 220
                removeDuration = 180
                changeDuration = 200
            }
        }

        setupSwipeToClose()

        rebuild()
        updateTabCount(tabManager.tabCount)

        val activeIndex = adapter.currentList.indexOfFirst {
            it is TabListItem.TabItem && it.tab.id == tabManager.currentTab?.id
        }
        if (activeIndex > 0) binding.tabsRecyclerView.scrollToPosition(activeIndex)

        binding.btnNewTab.setOnClickListener {
            animateClick(it)
            setResult(Activity.RESULT_OK, Intent()
                .putExtra("new_tab", true)
                .putExtra("incognito", false))
            finish()
        }

        binding.segmentTabs.setOnClickListener { setGridMode(false) }
        binding.segmentGrid.setOnClickListener { setGridMode(true) }
        setGridMode(true)

        binding.btnMore.setOnClickListener {
            animateClick(it)
            showMoreMenu()
        }

        binding.btnIncognito.setOnClickListener {
            setResult(Activity.RESULT_OK, Intent()
                .putExtra("new_tab", true)
                .putExtra("incognito", true))
            finish()
        }

        binding.btnCloseAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.close_all)
                .setMessage(
                    resources.getQuantityString(
                        R.plurals.close_all_tabs_confirm,
                        tabManager.tabCount,
                        tabManager.tabCount
                    )
                )
                .setPositiveButton(R.string.close_all) { _, _ ->
                    tabManager.closeAllTabs()
                    setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.searchTabs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                rebuild()
            }
        })

        setupSelectionBar()
        setupBannerAd()

        binding.tabsRecyclerView.alpha = 0f
        binding.tabsRecyclerView.translationY = 60f
        binding.tabsRecyclerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(80)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.6f))
            .start()
    }

    override fun onBackPressed() {
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ---- List construction ----

    // Flatten TabManager state into the ordered row list: each non-empty group
    // becomes a header followed by its member tabs (in their _tabs order), then
    // all ungrouped tabs. Honors the active search filter.
    private fun buildItems(): List<TabListItem> {
        val tabs = tabManager.tabs
        val filtered: List<BrowserTab> = if (searchQuery.isEmpty()) {
            tabs
        } else {
            tabs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.url.contains(searchQuery, ignoreCase = true)
            }
        }
        val filteredIds = filtered.mapTo(HashSet()) { it.id }

        val items = mutableListOf<TabListItem>()
        // Group sections, in TabManager's group declaration order.
        for (group in tabManager.tabGroups) {
            val members = filtered.filter { it.groupId == group.id }
            if (members.isEmpty()) continue
            items.add(
                TabListItem.Header(
                    groupId = group.id,
                    name = group.name,
                    color = group.color,
                    count = group.tabIds.count { it in filteredIds }
                )
            )
            members.forEach { items.add(TabListItem.TabItem(it.copy(), group.color)) }
        }
        // Ungrouped tabs (no group, or group no longer exists).
        val knownGroupIds = tabManager.tabGroups.mapTo(HashSet()) { it.id }
        filtered.filter { it.groupId == null || it.groupId !in knownGroupIds }
            .forEach { items.add(TabListItem.TabItem(it.copy(), null)) }
        return items
    }

    // Single entry point to refresh the visible list + empty state from the
    // current TabManager + search/selection state.
    private fun rebuild() {
        val items = buildItems()
        adapter.activeTabId = tabManager.currentTab?.id
        adapter.submitList(items)
        binding.emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---- Close-with-undo (single path) ----

    private fun closeWithUndo(tabId: String) {
        val snapshot = tabManager.closeWithUndo(tabId) ?: return
        if (tabManager.tabCount == 0) {
            setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
            finish()
            return
        }
        rebuild()
        updateTabCount(tabManager.tabCount)
        showUndoSnackbar(listOf(snapshot))
    }

    private fun showUndoSnackbar(snapshots: List<ClosedTabSnapshot>) {
        if (snapshots.isEmpty()) return
        val message = if (snapshots.size == 1) {
            getString(R.string.tab_closed)
        } else {
            getString(R.string.tabs_closed_count, snapshots.size)
        }
        Snackbar.make(binding.tabsRecyclerView, message, Snackbar.LENGTH_LONG)
            .setAction(getString(R.string.undo)) {
                tabManager.undoCloseAll(snapshots)
                rebuild()
                updateTabCount(tabManager.tabCount)
            }
            .setActionTextColor(resources.getColor(R.color.accent_purple, theme))
            .setBackgroundTint(resources.getColor(R.color.surface_container_high, theme))
            .setTextColor(resources.getColor(R.color.text_primary, theme))
            .setAnchorView(binding.bannerAdContainer)
            .show()
    }

    // ---- Selection mode ----

    private fun setupSelectionBar() {
        binding.btnSelectionClose.setOnClickListener { exitSelectionMode() }
        binding.btnSelectionShare.setOnClickListener { shareSelected() }
        binding.btnSelectionGroup.setOnClickListener { groupSelected() }
        binding.btnSelectionCloseTabs.setOnClickListener { closeSelected() }
    }

    private fun enterSelectionMode(initialId: String) {
        if (selectionMode) return
        selectionMode = true
        selectedIds.clear()
        selectedIds.add(initialId)
        adapter.selectionMode = true
        adapter.setSelected(selectedIds)
        binding.selectionBar.visibility = View.VISIBLE
        updateSelectionUi()
    }

    private fun exitSelectionMode() {
        if (!selectionMode) return
        selectionMode = false
        selectedIds.clear()
        adapter.selectionMode = false
        adapter.setSelected(emptySet())
        binding.selectionBar.visibility = View.GONE
    }

    private fun toggleSelection(tabId: String) {
        if (!selectionMode) {
            enterSelectionMode(tabId)
            return
        }
        if (!selectedIds.remove(tabId)) selectedIds.add(tabId)
        if (selectedIds.isEmpty()) {
            exitSelectionMode()
            return
        }
        adapter.setSelected(selectedIds)
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        binding.selectionCount.text =
            getString(R.string.tabs_selected_count, selectedIds.size)
    }

    private fun shareSelected() {
        val urls = tabManager.tabs
            .filter { it.id in selectedIds && it.url.isNotEmpty() }
            .map { it.url }
        if (urls.isEmpty()) {
            exitSelectionMode()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, urls.joinToString("\n"))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
        exitSelectionMode()
    }

    private fun groupSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        showAddToGroupMenu(ids) { exitSelectionMode() }
    }

    private fun closeSelected() {
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        val snapshots = tabManager.closeAllWithUndo(ids)
        exitSelectionMode()
        if (tabManager.tabCount == 0) {
            setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
            finish()
            return
        }
        rebuild()
        updateTabCount(tabManager.tabCount)
        showUndoSnackbar(snapshots)
    }

    // ---- Grouping UI ----

    // Show "New group…" plus every existing group. onDone is invoked after the
    // grouping completes (used by selection mode to dismiss the action bar).
    private fun showAddToGroupMenu(tabIds: List<String>, onDone: () -> Unit) {
        if (tabIds.isEmpty()) return
        val groups = tabManager.tabGroups
        val labels = mutableListOf(getString(R.string.new_group))
        groups.forEach { labels.add(it.name) }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_to_group)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    showNewGroupDialog(tabIds, onDone)
                } else {
                    val group = groups.getOrNull(which - 1)
                    if (group != null) {
                        tabIds.forEach { tabManager.addTabToGroup(it, group.id) }
                        rebuild()
                    }
                    onDone()
                }
            }
            .setOnCancelListener { onDone() }
            .show()
    }

    // Name + color picker dialog for creating a new group.
    private fun showNewGroupDialog(tabIds: List<String>, onDone: () -> Unit) {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(0))
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.group_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(getString(R.string.default_group_name))
            setSelection(text.length)
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(64))
        }
        container.addView(nameInput)

        val selectedColor = intArrayOf(TabGroup.GROUP_COLORS[0])
        container.addView(buildColorPicker(selectedColor, dp(0)))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_group)
            .setView(container)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = nameInput.text?.toString()?.trim()
                    ?.ifEmpty { getString(R.string.default_group_name) }
                    ?: getString(R.string.default_group_name)
                tabManager.createTabGroup(name, tabIds, selectedColor[0])
                rebuild()
                onDone()
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onDone() }
            .setOnCancelListener { onDone() }
            .show()
    }

    // Horizontal row of color swatches; mutates target[0] on tap and shows a
    // ring on the selected swatch. topMarginPx spaces it below preceding views.
    private fun buildColorPicker(target: IntArray, topMarginPx: Int): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = if (topMarginPx > 0) topMarginPx else dp(16)
            layoutParams = lp
        }
        val swatches = mutableListOf<FrameLayout>()
        TabGroup.GROUP_COLORS.forEach { color ->
            val swatch = FrameLayout(this).apply {
                val lp = LinearLayout.LayoutParams(dp(32), dp(32))
                lp.marginEnd = dp(8)
                layoutParams = lp
                isClickable = true
                isFocusable = true
                contentDescription = getString(R.string.group_color)
            }
            fun render(isSel: Boolean) {
                swatch.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (isSel) setStroke(dp(3), getColor(R.color.text_primary))
                }
            }
            render(color == target[0])
            swatch.setOnClickListener {
                target[0] = color
                swatches.forEachIndexed { idx, s ->
                    val c = TabGroup.GROUP_COLORS[idx]
                    s.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (c == color) setStroke(dp(3), getColor(R.color.text_primary))
                    }
                }
            }
            swatches.add(swatch)
            row.addView(swatch)
        }
        return row
    }

    // Header tap -> rename / recolor / ungroup all.
    private fun showGroupOptions(header: TabListItem.Header) {
        val items = arrayOf(
            getString(R.string.rename_group),
            getString(R.string.change_group_color),
            getString(R.string.ungroup)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(header.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showRenameGroupDialog(header.groupId, header.name)
                    1 -> showRecolorGroupDialog(header.groupId)
                    2 -> {
                        val ids = tabManager.tabs.filter { it.groupId == header.groupId }.map { it.id }
                        ids.forEach { tabManager.removeTabFromGroup(it) }
                        rebuild()
                    }
                }
            }
            .show()
    }

    private fun showRenameGroupDialog(groupId: String, current: String) {
        val density = resources.displayMetrics.density
        val input = EditText(this).apply {
            setText(current)
            setSelection(text.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(android.text.InputFilter.LengthFilter(64))
        }
        val pad = (24 * density).toInt()
        val wrap = FrameLayout(this).apply { setPadding(pad, (8 * density).toInt(), pad, 0) }
        wrap.addView(input)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_group)
            .setView(wrap)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString()?.trim()
                if (!name.isNullOrEmpty()) {
                    tabManager.renameGroup(groupId, name)
                    rebuild()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRecolorGroupDialog(groupId: String) {
        val current = tabManager.findGroup(groupId)?.color ?: TabGroup.GROUP_COLORS[0]
        val selectedColor = intArrayOf(current)
        val density = resources.displayMetrics.density
        val pad = (24 * density).toInt()
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, 0)
        }
        wrap.addView(buildColorPicker(selectedColor, 0))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.change_group_color)
            .setView(wrap)
            .setPositiveButton(R.string.save) { _, _ ->
                tabManager.setGroupColor(groupId, selectedColor[0])
                rebuild()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---- Swipe-to-close ----

    private fun setupSwipeToClose() {
        val swipeTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            // Headers and selection mode are not swipeable.
            override fun getSwipeDirs(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                if (selectionMode) return 0
                if (vh.itemViewType == TabsAdapter.VIEW_TYPE_HEADER) return 0
                return super.getSwipeDirs(rv, vh)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = adapter.currentList.getOrNull(position) as? TabListItem.TabItem ?: return
                closeWithUndo(item.tab.id)
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float,
                actionState: Int, isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val width = itemView.width.toFloat()
                val alpha = 1f - (abs(dX) / width).coerceIn(0f, 1f)
                val scale = 1f - (abs(dX) / width * 0.15f).coerceIn(0f, 0.15f)
                itemView.alpha = alpha
                itemView.scaleX = scale
                itemView.scaleY = scale
                itemView.translationX = dX
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.35f
        })
        swipeTouchHelper.attachToRecyclerView(binding.tabsRecyclerView)
    }

    private fun setGridMode(grid: Boolean) {
        isGridMode = grid
        if (grid) {
            binding.segmentGrid.setBackgroundResource(R.drawable.bg_segment_active)
            binding.segmentTabs.setBackgroundResource(R.drawable.bg_segment_inactive)
            binding.btnGridToggle.setColorFilter(getColor(R.color.text_primary))
            binding.tabCountText.setTextColor(getColor(R.color.text_secondary))
            val lm = GridLayoutManager(this, spanCount)
            lm.spanSizeLookup = adapter.spanSizeLookup(spanCount)
            binding.tabsRecyclerView.layoutManager = lm
        } else {
            binding.segmentGrid.setBackgroundResource(R.drawable.bg_segment_inactive)
            binding.segmentTabs.setBackgroundResource(R.drawable.bg_segment_active)
            binding.btnGridToggle.setColorFilter(getColor(R.color.text_secondary))
            binding.tabCountText.setTextColor(getColor(R.color.text_primary))
            binding.tabsRecyclerView.layoutManager = LinearLayoutManager(this)
        }
        adapter.resetAnimations()
        adapter.notifyDataSetChanged()
    }

    private fun updateTabCount(count: Int) {
        binding.tabCountText.text = count.toString()
    }

    override fun finish() {
        super.finish()
        overridePendingTransitionCompat(R.anim.fade_in, R.anim.slide_down)
    }

    private fun showMoreMenu() {
        val entries = mutableListOf<Pair<String, () -> Unit>>()

        entries.add(getString(R.string.new_tab) to {
            setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
            finish()
        })
        entries.add(getString(R.string.new_incognito_tab) to {
            setResult(Activity.RESULT_OK, Intent()
                .putExtra("new_tab", true)
                .putExtra("incognito", true))
            finish()
        })
        entries.add(getString(R.string.select_tabs) to {
            tabManager.tabs.firstOrNull()?.let { enterSelectionMode(it.id) }
        })
        entries.add(getString(R.string.close_all_tabs) to {
            tabManager.closeAllTabs()
            setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
            finish()
        })
        if (tabManager.recentlyClosed.isNotEmpty()) {
            entries.add(getString(R.string.reopen_closed_tab) to {
                val closed = tabManager.recentlyClosed.firstOrNull()
                if (closed != null) {
                    val newTab = tabManager.addTab(closed.isIncognito, closed.url)
                    newTab.title = closed.title
                    rebuild()
                    updateTabCount(tabManager.tabCount)
                }
            })
        }
        if (billingManager.isPremium.value) {
            entries.add(getString(R.string.premium_active) to {})
        } else {
            entries.add(getString(R.string.premium_remove_ads) to {
                billingManager.launchSubscription(this)
            })
        }

        MaterialAlertDialogBuilder(this)
            .setItems(entries.map { it.first }.toTypedArray()) { _, which ->
                entries.getOrNull(which)?.second?.invoke()
            }
            .show()
    }

    private fun showTabContextMenu(tab: BrowserTab, anchor: View) {
        val isGrouped = tab.groupId != null
        val entries = mutableListOf<Pair<String, () -> Unit>>()

        entries.add(getString(if (tab.isPinned) R.string.unpin_tab else R.string.pin_tab) to {
            tabManager.pinTab(tab.id)
            rebuild()
        })
        entries.add(getString(R.string.select_tabs) to {
            enterSelectionMode(tab.id)
        })
        entries.add(getString(R.string.add_to_group) to {
            showAddToGroupMenu(listOf(tab.id)) {}
        })
        if (isGrouped) {
            entries.add(getString(R.string.remove_from_group) to {
                tabManager.removeTabFromGroup(tab.id)
                rebuild()
            })
        }
        entries.add(getString(R.string.close_other_tabs) to {
            tabManager.closeOtherTabs(tab.id)
            rebuild()
            updateTabCount(tabManager.tabCount)
        })
        entries.add(getString(R.string.close_tabs_to_right) to {
            tabManager.closeTabsToRight(tab.id)
            rebuild()
            updateTabCount(tabManager.tabCount)
        })
        entries.add(getString(R.string.share_link) to {
            if (tab.url.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, tab.url)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
            }
        })
        entries.add(getString(R.string.copy_link) to {
            if (tab.url.isNotEmpty()) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", tab.url))
                Snackbar.make(binding.tabsRecyclerView, getString(R.string.link_copied), Snackbar.LENGTH_SHORT).show()
            }
        })

        MaterialAlertDialogBuilder(this)
            .setTitle(tab.title.ifEmpty { tab.url.ifEmpty { getString(R.string.new_tab) } })
            .setItems(entries.map { it.first }.toTypedArray()) { _, which ->
                entries.getOrNull(which)?.second?.invoke()
            }
            .show()
    }

    private fun animateClick(view: View) {
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(120)
                .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }.start()
    }

    private fun setupBannerAd() {
        lifecycleScope.launch {
            billingManager.isPremium.collectLatest { isPremium ->
                binding.bannerAdContainer.visibility = if (isPremium) View.GONE else View.VISIBLE
            }
        }

        binding.btnSubscribe.text = billingManager.getFormattedPrice()
        binding.btnSubscribe.setOnClickListener {
            billingManager.launchSubscription(this)
        }
        binding.btnDismissAd.setOnClickListener {
            binding.bannerAdContainer.animate()
                .alpha(0f)
                .translationY(50f)
                .setDuration(200)
                .withEndAction { binding.bannerAdContainer.visibility = View.GONE }
                .start()
        }
    }
}
