package com.helix.browser.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.helix.browser.databinding.ActivityDownloadsBinding
import com.helix.browser.ui.adapter.DownloadsAdapter
import com.helix.browser.R

class DownloadsActivity : BaseActivity() {

    private lateinit var binding: ActivityDownloadsBinding
    private lateinit var adapter: DownloadsAdapter
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = DownloadsAdapter { downloadId ->
            openDownloadedFile(downloadId)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@DownloadsActivity)
            adapter = this@DownloadsActivity.adapter
        }

        loadDownloads()
    }

    private fun loadDownloads() {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        val items = mutableListOf<DownloadItem>()
        val cursor: Cursor = dm.query(query)
        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val uri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                items.add(DownloadItem(id, title ?: getString(R.string.downloads), status, uri, total, downloaded))
            } while (cursor.moveToNext())
        }
        cursor.close()
        val reversed = items.reversed()
        adapter.submitList(reversed)
        binding.emptyView.visibility = if (reversed.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openDownloadedFile(downloadId: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val intent = dm.getMimeTypeForDownloadedFile(downloadId)?.let { mime ->
            val uri = dm.getUriForDownloadedFile(downloadId)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        intent?.let { startActivity(it) }
    }
}

data class DownloadItem(
    val id: Long,
    val title: String,
    val status: Int,
    val localUri: String?,
    val totalBytes: Long,
    val downloadedBytes: Long
)
