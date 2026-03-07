package com.helix.browser.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.helix.browser.HelixApp
import com.helix.browser.databinding.ActivityTabSwitcherBinding
import com.helix.browser.tabs.BrowserTab
import com.helix.browser.ui.adapter.TabsAdapter

class TabSwitcherActivity : BaseActivity() {

    private lateinit var binding: ActivityTabSwitcherBinding
    private lateinit var adapter: TabsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabSwitcherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply nav bar inset to bottom spacer
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navBarSpace.layoutParams.height = bars.bottom
            binding.navBarSpace.requestLayout()
            insets
        }

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
                    // Return with new_tab request
                    val result = Intent().putExtra("new_tab", true)
                    setResult(Activity.RESULT_OK, result)
                    finish()
                } else {
                    adapter.submitList(tabManager.tabs.toMutableList())
                    binding.tabCountText.text = "${tabManager.tabCount} tab"
                }
            }
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
    }
}
