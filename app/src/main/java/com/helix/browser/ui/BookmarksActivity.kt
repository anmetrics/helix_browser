package com.helix.browser.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.databinding.ActivityBookmarksBinding
import com.helix.browser.ui.adapter.BookmarksAdapter
import com.helix.browser.viewmodel.BookmarkViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest

class BookmarksActivity : BaseActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val viewModel: BookmarkViewModel by viewModels()
    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = BookmarksAdapter(
            onItemClick = { bookmark ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse(bookmark.url)
                }
                startActivity(intent)
                finish()
            },
            onDeleteClick = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BookmarksActivity)
            adapter = this@BookmarksActivity.adapter
        }

        lifecycleScope.launch {
            viewModel.allBookmarks.collectLatest { bookmarks ->
                adapter.submitList(bookmarks)
                binding.emptyView.visibility =
                    if (bookmarks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
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
}
