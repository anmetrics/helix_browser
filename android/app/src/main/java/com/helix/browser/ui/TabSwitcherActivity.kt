package com.helix.browser.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
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
import com.helix.browser.ui.adapter.TabsAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class TabSwitcherActivity : BaseActivity() {

    private lateinit var binding: ActivityTabSwitcherBinding
    private lateinit var adapter: TabsAdapter
    private lateinit var billingManager: BillingManager
    private var isGridMode = true // Always grid in this design; segment used for filtering

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabSwitcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billingManager = BillingManager.getInstance(this)
        val tabManager = (application as HelixApp).tabManager

        adapter = TabsAdapter(
            onTabClick = { tab ->
                val result = Intent().putExtra("tab_id", tab.id)
                setResult(Activity.RESULT_OK, result)
                finish()
            },
            onTabClose = { tab ->
                tabManager.closeTab(tab.id)
                if (tabManager.tabCount == 0) {
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    refreshList(tabManager.tabs)
                    updateTabCount(tabManager.tabCount)
                    showUndoSnackbar(tab)
                }
            },
            onTabLongPress = { tab, view ->
                showTabContextMenu(tab, view, tabManager)
            },
            activeTabId = tabManager.currentTab?.id
        )

        // Grid layout with 2 columns
        binding.tabsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@TabSwitcherActivity, 2)
            adapter = this@TabSwitcherActivity.adapter
            setHasFixedSize(false)
            itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
                addDuration = 220
                removeDuration = 180
                changeDuration = 200
            }
        }

        // Swipe to dismiss
        val swipeTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val tab = adapter.currentList.getOrNull(position) ?: return

                tabManager.closeTab(tab.id)
                if (tabManager.tabCount == 0) {
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    refreshList(tabManager.tabs)
                    updateTabCount(tabManager.tabCount)
                    showUndoSnackbar(tab)
                }
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

        refreshList(tabManager.tabs)
        updateTabCount(tabManager.tabCount)

        // Scroll to active tab
        val activeIndex = tabManager.tabs.indexOfFirst { it.id == tabManager.currentTab?.id }
        if (activeIndex > 0) {
            binding.tabsRecyclerView.scrollToPosition(activeIndex)
        }

        // New tab button (purple +)
        binding.btnNewTab.setOnClickListener {
            animateClick(it)
            val result = Intent().apply {
                putExtra("new_tab", true)
                putExtra("incognito", false)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        // Segment toggle - tabs/grid view modes
        binding.segmentTabs.setOnClickListener { setGridMode(false) }
        binding.segmentGrid.setOnClickListener { setGridMode(true) }
        // Default to grid as shown in screenshot
        setGridMode(true)

        // More options menu (Material bottom sheet)
        binding.btnMore.setOnClickListener {
            animateClick(it)
            showMoreMenu(tabManager)
        }

        // New incognito
        binding.btnIncognito.setOnClickListener {
            val result = Intent().apply {
                putExtra("new_tab", true)
                putExtra("incognito", true)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        // Close all
        binding.btnCloseAll.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.close_all)
                .setMessage("Close all ${tabManager.tabCount} tabs?")
                .setPositiveButton(R.string.close_all) { _, _ ->
                    tabManager.closeAllTabs()
                    setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Search tabs
        binding.searchTabs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                val filtered = if (query.isEmpty()) {
                    tabManager.tabs
                } else {
                    tabManager.tabs.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.url.contains(query, ignoreCase = true)
                    }
                }
                refreshList(filtered)
            }
        })

        setupBannerAd()

        // Entrance animation
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

    private fun setGridMode(grid: Boolean) {
        isGridMode = grid
        if (grid) {
            binding.segmentGrid.setBackgroundResource(R.drawable.bg_segment_active)
            binding.segmentTabs.setBackgroundResource(R.drawable.bg_segment_inactive)
            binding.btnGridToggle.setColorFilter(getColor(R.color.text_primary))
            binding.tabCountText.setTextColor(getColor(R.color.text_secondary))
            binding.tabsRecyclerView.layoutManager = GridLayoutManager(this, 2)
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

    private fun refreshList(tabs: List<BrowserTab>) {
        adapter.submitList(tabs.toList())
        binding.emptyState.visibility = if (tabs.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateTabCount(count: Int) {
        binding.tabCountText.text = count.toString()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    private fun showUndoSnackbar(tab: BrowserTab) {
        Snackbar.make(binding.tabsRecyclerView, "Tab closed", Snackbar.LENGTH_LONG)
            .setAction("Undo") {
                val tabManager = (application as HelixApp).tabManager
                val newTab = tabManager.addTab(tab.isIncognito, tab.url)
                newTab.title = tab.title
                refreshList(tabManager.tabs)
                updateTabCount(tabManager.tabCount)
            }
            .setActionTextColor(resources.getColor(R.color.accent_purple, theme))
            .setBackgroundTint(resources.getColor(R.color.surface_container_high, theme))
            .setTextColor(resources.getColor(R.color.text_primary, theme))
            .setAnchorView(binding.bannerAdContainer)
            .show()
    }

    private fun showMoreMenu(tabManager: com.helix.browser.tabs.TabManager) {
        val items = mutableListOf<String>()
        items.add("New tab")
        items.add("New incognito tab")
        items.add("Close all tabs")
        if (tabManager.recentlyClosed.isNotEmpty()) {
            items.add("Reopen closed tab")
        }

        MaterialAlertDialogBuilder(this)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "New tab" -> {
                        setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
                        finish()
                    }
                    "New incognito tab" -> {
                        setResult(Activity.RESULT_OK, Intent()
                            .putExtra("new_tab", true)
                            .putExtra("incognito", true))
                        finish()
                    }
                    "Close all tabs" -> {
                        tabManager.closeAllTabs()
                        setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
                        finish()
                    }
                    "Reopen closed tab" -> {
                        val closed = tabManager.recentlyClosed.firstOrNull()
                        if (closed != null) {
                            val newTab = tabManager.addTab(closed.isIncognito, closed.url)
                            newTab.title = closed.title
                            refreshList(tabManager.tabs)
                            updateTabCount(tabManager.tabCount)
                        }
                    }
                }
            }
            .show()
    }

    private fun showTabContextMenu(tab: BrowserTab, anchor: View, tabManager: com.helix.browser.tabs.TabManager) {
        val items = arrayOf(
            "Pin tab",
            "Close other tabs",
            "Close tabs to the right",
            "Share link",
            "Copy link"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(tab.title.ifEmpty { tab.url })
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        tab.isPinned = !tab.isPinned
                        refreshList(tabManager.tabs)
                    }
                    1 -> {
                        tabManager.tabs.toList().forEach {
                            if (it.id != tab.id && !it.isPinned) tabManager.closeTab(it.id)
                        }
                        refreshList(tabManager.tabs)
                        updateTabCount(tabManager.tabCount)
                    }
                    2 -> {
                        val tabs = tabManager.tabs.toList()
                        val index = tabs.indexOfFirst { it.id == tab.id }
                        if (index >= 0 && index < tabs.size - 1) {
                            tabs.subList(index + 1, tabs.size).toList().forEach {
                                if (!it.isPinned) tabManager.closeTab(it.id)
                            }
                            refreshList(tabManager.tabs)
                            updateTabCount(tabManager.tabCount)
                        }
                    }
                    3 -> {
                        if (tab.url.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, tab.url)
                            }
                            startActivity(Intent.createChooser(intent, getString(R.string.share_via)))
                        }
                    }
                    4 -> {
                        if (tab.url.isNotEmpty()) {
                            val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", tab.url))
                            Snackbar.make(binding.tabsRecyclerView, getString(R.string.link_copied), Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
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
