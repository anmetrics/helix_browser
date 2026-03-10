package com.helix.browser.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.databinding.ActivityHistoryBinding
import com.helix.browser.ui.adapter.HistoryAdapter
import com.helix.browser.viewmodel.HistoryViewModel
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
            viewModel.allHistory.collectLatest { items ->
                adapter.submitList(items)
                binding.emptyView.visibility =
                    if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_dialog_title)
            .setMessage(R.string.clear_history_dialog_message)
            .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                viewModel.clearAll()
                Toast.makeText(this, getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
