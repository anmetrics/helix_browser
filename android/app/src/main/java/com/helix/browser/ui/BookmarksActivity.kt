package com.helix.browser.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.helix.browser.HelixApp
import com.helix.browser.R
import com.helix.browser.data.Bookmark
import com.helix.browser.data.BookmarksHtml
import com.helix.browser.databinding.ActivityBookmarksBinding
import com.helix.browser.databinding.DialogEditBookmarkBinding
import com.helix.browser.databinding.DialogFolderNameBinding
import com.helix.browser.ui.adapter.BookmarksAdapter
import com.helix.browser.viewmodel.BookmarkViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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

        // Toolbar nav icon: when inside a folder it walks up one level, only
        // closing the screen once we're back at root.
        binding.toolbar.setNavigationOnClickListener {
            if (!viewModel.navigateUp()) finish()
        }
        binding.toolbar.inflateMenu(R.menu.menu_bookmarks)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_folder -> { showCreateFolderDialog(); true }
                R.id.action_export -> { startExport(); true }
                R.id.action_import -> { startImport(); true }
                else -> false
            }
        }

        // System back: a search collapses first, then folders pop one level,
        // and only at root does back close the Activity.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    viewModel.isSearching -> binding.searchInput.text?.clear()
                    viewModel.navigateUp() -> Unit
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        adapter = BookmarksAdapter(
            onItemClick = { item ->
                if (item.isFolder) {
                    // Clear any active search when descending so we show children.
                    if (viewModel.isSearching) binding.searchInput.text?.clear()
                    viewModel.openFolder(item)
                } else {
                    openBookmark(item)
                }
            },
            onOverflowClick = { anchor, item -> showItemMenu(anchor, item) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@BookmarksActivity)
            adapter = this@BookmarksActivity.adapter
        }

        lifecycleScope.launch {
            viewModel.results.collectLatest { items ->
                adapter.submitList(items)
                renderEmptyState(items.isEmpty())
                updateToolbarTitle()
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

    private fun openBookmark(bookmark: Bookmark) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(bookmark.url)
        }
        startActivity(intent)
        finish()
    }

    // Reflect the current folder in the toolbar so the user knows where they
    // are. At root we show the screen title; inside a folder we show the folder
    // name as the title and "Bookmarks" as a subtitle breadcrumb.
    private fun updateToolbarTitle() {
        val folder = viewModel.currentFolder
        if (folder == null) {
            binding.toolbar.title = getString(R.string.bookmarks)
            binding.toolbar.subtitle = null
        } else {
            binding.toolbar.title = folder.title
            binding.toolbar.subtitle = getString(R.string.bookmarks)
        }
    }

    private fun renderEmptyState(isEmpty: Boolean) {
        binding.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (!isEmpty) return
        binding.emptyView.text = when {
            viewModel.isSearching -> getString(R.string.search_no_results)
            !viewModel.isAtRoot -> getString(R.string.empty_folder)
            else -> getString(R.string.no_bookmarks)
        }
    }

    // --- Per-row overflow menu (Edit / Move / Delete) ---

    private fun showItemMenu(anchor: View, item: Bookmark) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_bookmark_item, menu)
            // Folders aren't edited via the title/url dialog (no URL); they're
            // renamed instead, so swap the Edit label for folders.
            if (item.isFolder) {
                menu.findItem(R.id.action_edit).setTitle(R.string.rename)
            }
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    R.id.action_edit ->
                        if (item.isFolder) showRenameFolderDialog(item) else showEditBookmarkDialog(item)
                    R.id.action_move -> showMovePicker(item)
                    R.id.action_delete -> confirmDelete(item)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun confirmDelete(item: Bookmark) {
        if (item.isFolder) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_folder_title)
                .setMessage(getString(R.string.delete_folder_message, item.title))
                .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteBookmark(item) }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            viewModel.deleteBookmark(item)
        }
    }

    // --- Create / rename folder ---

    private fun showCreateFolderDialog() {
        val dialogBinding = DialogFolderNameBinding.inflate(layoutInflater)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_folder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.create_folder) { _, _ ->
                val name = dialogBinding.folderName.text?.toString().orEmpty()
                if (name.isNotBlank()) viewModel.createFolder(name)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        focusAndShowKeyboard(dialogBinding.folderName)
    }

    private fun showRenameFolderDialog(folder: Bookmark) {
        val dialogBinding = DialogFolderNameBinding.inflate(layoutInflater)
        dialogBinding.folderName.setText(folder.title)
        dialogBinding.folderName.setSelection(folder.title.length)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename_folder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.rename) { _, _ ->
                viewModel.renameFolder(folder, dialogBinding.folderName.text?.toString().orEmpty())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        focusAndShowKeyboard(dialogBinding.folderName)
    }

    // --- Edit bookmark (title + url) ---

    private fun showEditBookmarkDialog(bookmark: Bookmark) {
        val dialogBinding = DialogEditBookmarkBinding.inflate(layoutInflater)
        dialogBinding.editTitle.setText(bookmark.title)
        dialogBinding.editUrl.setText(bookmark.url)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_bookmark)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.editBookmark(
                    bookmark,
                    dialogBinding.editTitle.text?.toString().orEmpty(),
                    dialogBinding.editUrl.text?.toString().orEmpty()
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
        focusAndShowKeyboard(dialogBinding.editTitle)
    }

    // --- Move to folder picker ---

    private fun showMovePicker(item: Bookmark) {
        lifecycleScope.launch {
            val folders = viewModel.foldersForMove(item)
            // First entry is always "root", then each eligible folder.
            val labels = ArrayList<String>(folders.size + 1)
            labels.add(getString(R.string.move_here_root))
            folders.forEach { labels.add(it.title) }
            MaterialAlertDialogBuilder(this@BookmarksActivity)
                .setTitle(R.string.move_to_folder)
                .setItems(labels.toTypedArray()) { _, which ->
                    val target = if (which == 0) null else folders[which - 1].id
                    viewModel.moveToFolder(item, target) { ok ->
                        if (isFinishing || isDestroyed) return@moveToFolder
                        Toast.makeText(
                            this@BookmarksActivity,
                            if (ok) getString(R.string.moved_to_folder)
                            else getString(R.string.move_into_descendant_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun focusAndShowKeyboard(editText: EditText) {
        editText.requestFocus()
        editText.post {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    // --- HTML bookmark export / import (nested Netscape format) ---
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
                list.count { !it.isFolder }
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
                val nodes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input ->
                        BookmarksHtml.importTree(input)
                    } ?: emptyList()
                }
                importNodes(nodes, parentId = null, app)
            }.getOrElse { -1 }
            if (isFinishing || isDestroyed) return@launch
            Toast.makeText(
                this@BookmarksActivity,
                if (count >= 0) getString(R.string.bookmarks_imported, count) else getString(R.string.bookmarks_import_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Recreate the imported tree under [parentId], returning how many bookmarks
    // (not folders) were added. Bookmarks already present (by url) are skipped so
    // re-importing the same file doesn't create duplicates; legacy flat files
    // simply arrive as a list of link nodes at the root.
    private suspend fun importNodes(
        nodes: List<BookmarksHtml.Node>,
        parentId: Long?,
        app: HelixApp
    ): Int {
        var added = 0
        for (node in nodes) {
            when (node) {
                is BookmarksHtml.Node.Folder -> {
                    val folderId = app.bookmarkRepository.addFolder(node.title, parentId)
                    added += importNodes(node.children, folderId, app)
                }
                is BookmarksHtml.Node.Link -> {
                    if (!app.bookmarkRepository.isBookmarked(node.url)) {
                        app.bookmarkRepository.addBookmark(node.title, node.url, parentId = parentId)
                        added++
                    }
                }
            }
        }
        return added
    }

    private fun startExport() {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        exportLauncher.launch("helix-bookmarks-$stamp.html")
    }

    private fun startImport() {
        importLauncher.launch(arrayOf("text/html", "application/xhtml+xml", "text/plain"))
    }
}
