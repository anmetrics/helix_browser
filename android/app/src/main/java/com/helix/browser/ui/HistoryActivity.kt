package com.helix.browser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.databinding.ActivityHistoryBinding
import com.helix.browser.ui.adapter.HistoryAdapter
import com.helix.browser.viewmodel.HistoryViewModel
import com.helix.browser.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overridePendingTransitionCompat(R.anim.slide_in_right, R.anim.fade_out)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == com.helix.browser.R.id.action_clear_history) {
                showClearHistoryDialog()
                true
            } else false
        }

        adapter = HistoryAdapter(
            onItemClick = { item ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = Uri.parse(item.url)
                }
                startActivity(intent)
                finish()
            },
            onDeleteClick = { item -> viewModel.deleteItem(item) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        lifecycleScope.launch {
            viewModel.results.collectLatest { items ->
                adapter.submitList(items)
                renderEmptyState(items.isEmpty())
            }
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun finish() {
        super.finish()
        overrideCloseTransitionCompat(R.anim.fade_in, R.anim.slide_out_left)
    }

    // Show the rich empty state, switching copy between "no history at all" and
    // "no matches for the current search" so the user understands why the list
    // is blank while typing.
    private fun renderEmptyState(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
        if (!isEmpty) return
        val searching = viewModel.searchQuery.value.isNotBlank()
        binding.emptyTitle.text =
            if (searching) getString(R.string.search_no_results) else getString(R.string.no_history)
        binding.emptySubtitle.text =
            if (searching) getString(R.string.search_no_results_subtitle)
            else getString(R.string.no_history_subtitle)
    }

    // Time-range "Clear browsing data" chooser. Each entry maps to an absolute
    // epoch-millis cutoff (computed lazily at confirm time, not dialog-build
    // time, so a slow tap can't use a stale "now"). "All time" uses 0, which the
    // repository treats as "delete everything". The actual delete runs off the
    // main thread; the confirmation toast is shown only after it completes via
    // the clearRange callback, and is posted through the lifecycle scope so it
    // never fires against a destroyed Activity.
    private fun showClearHistoryDialog() {
        val labels = arrayOf(
            getString(R.string.clear_range_last_hour),
            getString(R.string.clear_range_last_24_hours),
            getString(R.string.clear_range_last_7_days),
            getString(R.string.clear_range_all_time)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history_dialog_title)
            .setItems(labels) { _, which ->
                val now = System.currentTimeMillis()
                val sinceMillis = when (which) {
                    0 -> now - ONE_HOUR_MS
                    1 -> now - ONE_DAY_MS
                    2 -> now - SEVEN_DAYS_MS
                    else -> 0L
                }
                viewModel.clearRange(sinceMillis) {
                    lifecycleScope.launch {
                        Toast.makeText(
                            this@HistoryActivity,
                            getString(R.string.history_cleared),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
        private const val ONE_DAY_MS = 24L * ONE_HOUR_MS
        private const val SEVEN_DAYS_MS = 7L * ONE_DAY_MS
    }
}
