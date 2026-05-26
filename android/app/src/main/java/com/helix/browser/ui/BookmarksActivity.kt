package com.helix.browser.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.data.BookmarksHtml
import com.helix.browser.databinding.ActivityBookmarksBinding
import com.helix.browser.ui.adapter.BookmarksAdapter
import com.helix.browser.viewmodel.BookmarkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

class BookmarksActivity : BaseActivity() {

    private lateinit var binding: ActivityBookmarksBinding
    private val viewModel: BookmarkViewModel by viewModels()
    private lateinit var adapter: BookmarksAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overridePendingTransitionCompat(R.anim.slide_in_right, R.anim.fade_out)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.inflateMenu(R.menu.menu_bookmarks)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_export -> { startExport(); true }
                R.id.action_import -> { startImport(); true }
                else -> false
            }
        }

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

    override fun finish() {
        super.finish()
        overrideCloseTransitionCompat(R.anim.fade_in, R.anim.slide_out_left)
    }

    // --- HTML bookmark export / import (Netscape format) ---
    //
    // We use the Storage Access Framework instead of WRITE_EXTERNAL_STORAGE
    // so the feature works on every API level we ship (24+) without any
    // runtime permission prompt.

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val app = applicationContext as HelixApp
        lifecycleScope.launch {
            val ok = runCatching {
                val list = app.bookmarkRepository.dao().getAllBookmarksSnapshot()
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { os ->
                        BookmarksHtml.export(os, list)
                    } ?: error("openOutputStream returned null")
                }
                list.size
            }.getOrElse { -1 }
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@BookmarksActivity,
                if (ok >= 0) getString(R.string.bookmarks_exported, ok) else getString(R.string.bookmarks_export_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val app = applicationContext as HelixApp
        lifecycleScope.launch {
            val count = runCatching {
                val parsed = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        BookmarksHtml.import(input)
                    } ?: emptyList()
                }
                var added = 0
                for (b in parsed) {
                    if (!app.bookmarkRepository.isBookmarked(b.url)) {
                        app.bookmarkRepository.addBookmark(b.title, b.url)
                        added++
                    }
                }
                added
            }.getOrElse { -1 }
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@BookmarksActivity,
                if (count >= 0) getString(R.string.bookmarks_imported, count) else getString(R.string.bookmarks_import_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startExport() {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        exportLauncher.launch("helix-bookmarks-$stamp.html")
    }

    private fun startImport() {
        importLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain"))
    }
}
