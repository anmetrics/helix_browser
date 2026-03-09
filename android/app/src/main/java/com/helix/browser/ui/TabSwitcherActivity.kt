package com.helix.browser.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.billing.BillingManager
import com.helix.browser.databinding.ActivityTabSwitcherBinding
import com.helix.browser.ui.adapter.TabsAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
                tabManager.closeTab(tab.id)
                if (tabManager.tabCount == 0) {
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    adapter.submitList(tabManager.tabs.toMutableList())
                    binding.tabCountText.text = "${tabManager.tabCount} tab"
                }
            },
            activeTabId = tabManager.currentTab?.id
        )

        binding.tabsRecyclerView.apply {
            layoutManager = GridLayoutManager(this@TabSwitcherActivity, 2)
            adapter = this@TabSwitcherActivity.adapter
        }

        adapter.submitList(tabManager.tabs.toMutableList())
        binding.tabCountText.text = "${tabManager.tabCount} tab"

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

        // Animate RecyclerView items
        val layoutAnim = AnimationUtils.loadLayoutAnimation(
            this, R.anim.layout_animation_fall_down
        )
        binding.tabsRecyclerView.layoutAnimation = layoutAnim
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down)
    }

    private fun setupBannerAd() {
        // Observe premium status
        lifecycleScope.launch {
            billingManager.isPremium.collectLatest { isPremium ->
                binding.bannerAdContainer.visibility = if (isPremium) View.GONE else View.VISIBLE
            }
        }

        // Update price text
        binding.btnSubscribe.text = billingManager.getFormattedPrice()

        // Subscribe button
        binding.btnSubscribe.setOnClickListener {
            billingManager.launchSubscription(this)
        }

        // Dismiss button (temporarily hides for this session)
        binding.btnDismissAd.setOnClickListener {
            binding.bannerAdContainer.visibility = View.GONE
        }
    }
}
