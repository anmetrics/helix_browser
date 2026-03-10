package com.helix.browser.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.billing.BillingManager
import com.helix.browser.databinding.ActivityTabSwitcherBinding
import com.helix.browser.ui.adapter.TabsAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class TabSwitcherActivity : BaseActivity() {

    private lateinit var binding: ActivityTabSwitcherBinding
    private lateinit var adapter: TabsAdapter
    private lateinit var billingManager: BillingManager

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
                }
            },
            activeTabId = tabManager.currentTab?.id
        )

        binding.tabsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TabSwitcherActivity)
            adapter = this@TabSwitcherActivity.adapter
            // Smooth scroll deceleration
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

        // Toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // New tab button
        binding.btnNewTab.setOnClickListener {
            val result = Intent().apply {
                putExtra("new_tab", true)
                putExtra("incognito", false)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
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

        // --- Banner Ad / Premium ---
        setupBannerAd()

        // Entrance animation for the whole recycler
        binding.tabsRecyclerView.alpha = 0f
        binding.tabsRecyclerView.translationY = 40f
        binding.tabsRecyclerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateTabCount(count: Int) {
        binding.tabCountText.text = if (count == 1) "1 tab" else "$count tabs"
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
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
