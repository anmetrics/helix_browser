package com.helix.browser.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.databinding.ActivityDownloadsBinding
import com.helix.browser.ui.adapter.DownloadsAdapter
import com.helix.browser.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Real download manager screen.
//
// Threading contract: DownloadManager.query() is a cross-process (Binder +
// ContentProvider) + disk-backed call and MUST NOT run on the main thread.
// All querying and every DownloadManager mutation (remove/cancel/enqueue)
// happens on Dispatchers.IO inside a lifecycle-scoped coroutine; only
// adapter.submitList() / visibility toggles touch the UI thread.
//
// Polling: a single coroutine loops only while at least one download is
// active (pending/running/paused). It stops as soon as nothing is active and
// is cancelled in onPause, so a backgrounded screen never polls.
class DownloadsActivity : BaseActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadsAdapter
    private var pollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overridePendingTransitionCompat(R.anim.slide_in_right, R.anim.fade_out)

        binding.toolbar.setNavigationOnClickListener { finish() }
        // No menu XML for this screen; add the "Clear completed" action
        // programmatically so we never touch shared resources.
        binding.toolbar.menu.add(
            Menu.NONE, MENU_CLEAR_COMPLETED, Menu.NONE,
            getString(R.string.download_clear_completed)
        ).apply {
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_CLEAR_COMPLETED) {
                clearCompleted()
                true
            } else {
                false
            }
        }

        adapter = DownloadsAdapter(
            onOpen = { id -> openDownloadedFile(id) },
            onCancel = { id -> removeDownloads(longArrayOf(id), R.string.download_cancelled) },
            onRetry = { item -> retryDownload(item) },
            onRemove = { item -> removeDownloads(longArrayOf(item.id), R.string.download_removed) },
            onShare = { id -> shareDownloadedFile(id) }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DownloadsActivity)
            adapter = this@DownloadsActivity.adapter
        }
    }

    override fun onResume() {
        super.onResume()
        startPolling()
    }

    override fun onPause() {
        super.onPause()
        stopPolling()
    }

    override fun finish() {
        super.finish()
        overrideCloseTransitionCompat(R.anim.fade_in, R.anim.slide_out_left)
    }

    // Launches the single poll loop. Refreshes once immediately, then keeps
    // polling at REFRESH_INTERVAL_MS only while a download is still active.
    private fun startPolling() {
        stopPolling()
        pollJob = lifecycleScope.launch {
            try {
                while (isActive) {
                    val items = queryDownloads()
                    renderList(items)
                    val hasActive = items.any { it.isActive() }
                    if (!hasActive) break
                    delay(REFRESH_INTERVAL_MS)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                // Querying the provider can throw (e.g. provider restart). Never
                // crash the screen; surface nothing is far worse than failing soft.
                renderList(emptyList())
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun renderList(items: List<DownloadItem>) {
        adapter.submitList(items)
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    // Runs entirely on Dispatchers.IO: opens, walks and closes the cursor off
    // the main thread, returning a fully-materialised list.
    private suspend fun queryDownloads(): List<DownloadItem> = withContext(Dispatchers.IO) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val items = mutableListOf<DownloadItem>()
        val cursor: Cursor? = dm.query(DownloadManager.Query())
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val idIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val titleIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val statusIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val localUriIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                val totalIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val soFarIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val srcUriIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
                do {
                    val id = c.getLong(idIdx)
                    val title = c.getString(titleIdx)
                    val status = c.getInt(statusIdx)
                    val localUri = c.getString(localUriIdx)
                    val total = c.getLong(totalIdx)
                    val downloaded = c.getLong(soFarIdx)
                    val sourceUri = c.getString(srcUriIdx)
                    items.add(
                        DownloadItem(
                            id = id,
                            title = title ?: fallbackTitle(),
                            status = status,
                            localUri = localUri,
                            totalBytes = total,
                            downloadedBytes = downloaded,
                            sourceUri = sourceUri
                        )
                    )
                } while (c.moveToNext())
            }
        }
        // DownloadManager returns oldest-first; show newest at top.
        items.asReversed().toList()
    }

    private fun fallbackTitle(): String = getString(R.string.downloads)

    // Cancel == remove for in-flight downloads (DownloadManager has no pause/
    // resume of an arbitrary id without re-enqueue), and is also used for the
    // explicit "Remove" action on terminal entries.
    private fun removeDownloads(ids: LongArray, toastRes: Int) {
        if (ids.isEmpty()) return
        lifecycleScope.launch {
            val removed = withContext(Dispatchers.IO) {
                try {
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    dm.remove(*ids)
                } catch (e: Exception) {
                    0
                }
            }
            if (removed > 0) {
                Toast.makeText(this@DownloadsActivity, getString(toastRes), Toast.LENGTH_SHORT).show()
            }
            // Refresh immediately so the row disappears without waiting a tick.
            startPolling()
        }
    }

    // Re-enqueues a failed download from its original source URI, then removes
    // the failed entry. DownloadManager cannot restart a failed id in place.
    private fun retryDownload(item: DownloadItem) {
        val source = item.sourceUri
        if (source.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.download_retry_failed), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = Uri.parse(source)
                    val fileName = item.title.ifBlank { uri.lastPathSegment ?: fallbackTitle() }
                    val request = DownloadManager.Request(uri).apply {
                        setTitle(fileName)
                        setDescription(getString(R.string.downloading_via_helix))
                        setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                        )
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        allowScanningByMediaScanner()
                    }
                    dm.enqueue(request)
                    // Drop the stale failed row.
                    dm.remove(item.id)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            val msgRes = if (ok) R.string.download_retrying else R.string.download_retry_failed
            Toast.makeText(this@DownloadsActivity, getString(msgRes), Toast.LENGTH_SHORT).show()
            startPolling()
        }
    }

    private fun clearCompleted() {
        lifecycleScope.launch {
            val ids = withContext(Dispatchers.IO) {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val terminal = mutableListOf<Long>()
                try {
                    val query = DownloadManager.Query().setFilterByStatus(
                        DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
                    )
                    dm.query(query)?.use { c ->
                        if (c.moveToFirst()) {
                            val idIdx = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                            do {
                                terminal.add(c.getLong(idIdx))
                            } while (c.moveToNext())
                        }
                    }
                } catch (e: Exception) {
                    // fall through with whatever we collected
                }
                terminal.toLongArray()
            }
            if (ids.isEmpty()) {
                Toast.makeText(
                    this@DownloadsActivity,
                    getString(R.string.download_nothing_to_clear),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            removeDownloads(ids, R.string.download_completed_cleared)
        }
    }

    private fun openDownloadedFile(downloadId: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val mime = dm.getMimeTypeForDownloadedFile(downloadId)
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            Toast.makeText(this, getString(R.string.download_open_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.download_no_app_to_open), Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDownloadedFile(downloadId: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = dm.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            Toast.makeText(this, getString(R.string.download_open_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val mime = dm.getMimeTypeForDownloadedFile(downloadId) ?: "*/*"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.download_no_app_to_open), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        // Poll cadence while a download is active. Off-main, so this is cheap.
        private const val REFRESH_INTERVAL_MS = 1000L
        private const val MENU_CLEAR_COMPLETED = 1
    }
}

data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val localUri: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val sourceUri: String? = null
) {
    fun isActive(): Boolean =
        status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_PAUSED
}
