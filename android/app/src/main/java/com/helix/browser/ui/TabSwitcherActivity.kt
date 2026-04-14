package com.helix.browser.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
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
    private val recentlyClosed = mutableListOf<Pair<Int, BrowserTab>>()

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
                val position = tabManager.tabs.indexOfFirst { it.id == tab.id }
                tabManager.closeTab(tab.id)
                if (tabManager.tabCount == 0) {
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    adapter.submitList(tabManager.tabs.toMutableList())
                    updateTabCount(tabManager.tabCount)
                    showUndoSnackbar(tab)
                }
            },
            activeTabId = tabManager.currentTab?.id
        )

        // Grid layout with 2 columns
        binding.tabsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@TabSwitcherActivity, 2)
            adapter = this@TabSwitcherActivity.adapter
            setHasFixedSize(false)
        }

        // Swipe to dismiss
        val swipeTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val tab = tabManager.tabs.getOrNull(position) ?: return

                tabManager.closeTab(tab.id)
                if (tabManager.tabCount == 0) {
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    adapter.submitList(tabManager.tabs.toMutableList())
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

        adapter.submitList(tabManager.tabs.toMutableList())
        updateTabCount(tabManager.tabCount)

        // Scroll to active tab
        val activeIndex = tabManager.tabs.indexOfFirst { it.id == tabManager.currentTab?.id }
        if (activeIndex > 0) {
            binding.tabsRecyclerView.scrollToPosition(activeIndex)
        }

        // New tab button
        binding.btnNewTab.setOnClickListener {
            val result = Intent().apply {
                putExtra("new_tab", true)
                putExtra("incognito", false)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        // More options menu
        binding.btnMore.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, getString(R.string.close_all))
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        tabManager.closeAllTabs()
                        setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
                        finish()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
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
            tabManager.closeAllTabs()
            setResult(Activity.RESULT_OK, Intent().putExtra("new_tab", true))
            finish()
        }

        // Search tabs
        binding.searchTabs.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    adapter.submitList(tabManager.tabs.toMutableList())
                } else {
                    val filtered = tabManager.tabs.filter {
                        it.title.contains(query, ignoreCase = true) ||
                                it.url.contains(query, ignoreCase = true)
                    }
                    adapter.submitList(filtered.toMutableList())
                }
            }
        })

        // --- Banner Ad / Premium ---
        setupBannerAd()

        // Entrance animation for the whole content
        binding.tabsRecyclerView.alpha = 0f
        binding.tabsRecyclerView.translationY = 60f
        binding.tabsRecyclerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setStartDelay(100)
            .setInterpolator(android.view.animation.OvershootInterpolator(0.8f))
            .start()
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
                // Re-add the tab
                val tabManager = (application as HelixApp).tabManager
                // We can't fully restore but we can create a new tab with same URL
                val newTab = tabManager.addTab(tab.isIncognito, tab.url)
                newTab.title = tab.title
                adapter.submitList(tabManager.tabs.toMutableList())
                updateTabCount(tabManager.tabCount)
            }
            .setActionTextColor(resources.getColor(R.color.accent_purple, theme))
            .setBackgroundTint(resources.getColor(R.color.surface_container_high, theme))
            .setTextColor(resources.getColor(R.color.text_primary, theme))
            .show()
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
            binding.bannerAdContainer.visibility = View.GONE
        }
    }
}
