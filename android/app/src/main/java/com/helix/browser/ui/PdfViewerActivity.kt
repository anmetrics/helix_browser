package com.helix.browser.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.helix.browser.R
import com.helix.browser.databinding.ActivityPdfViewerBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// In-app PDF viewer, the inline-PDF equivalent of what Chrome does when you
// open a .pdf link. Renders with the platform android.graphics.pdf.PdfRenderer
// (available since API 21, well under our minSdk 24) so there is no third-party
// dependency and no native code.
//
// Threading / safety contract:
//  - The file is opened (ParcelFileDescriptor, MODE_READ_ONLY) and the renderer
//    is created on Dispatchers.IO; opening can do disk + Binder work and must
//    never touch the main thread.
//  - PdfRenderer permits exactly ONE page open at a time and is NOT thread-safe.
//    Every renderer access (openPage / close / size reads) is funnelled through
//    a single dedicated background executor and guarded by `rendererLock`, so we
//    never open two pages concurrently and never race onDestroy's close().
//  - Bitmaps are rendered lazily, one visible page at a time, into a bounded
//    LruCache. The cache recycles bitmaps as they are evicted, and onDestroy
//    evicts everything, so memory stays bounded even for a 1000-page document.
//  - Every external call (open, render) is wrapped; any failure flips the screen
//    to the error state instead of crashing.
//
// Launch contract (other units rely on these exact extras):
//   EXTRA_PDF_URI   = "helix.pdf_uri"   (required, file:// or content:// string)
//   EXTRA_PDF_TITLE = "helix.pdf_title" (optional toolbar title)
class PdfViewerActivity : BaseActivity() {

    private lateinit var binding: ActivityPdfViewerBinding

    // All renderer/PFD access is confined to this single-thread executor and
    // additionally guarded by rendererLock for the onDestroy close() race.
    private var renderExecutor: ExecutorService? = null
    private val rendererLock = Any()

    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    private var adapter: PdfPageAdapter? = null

    // Bounded LRU of rendered pages. Sized from the runtime memory budget; cap is
    // applied per-bitmap too (see renderPage) so a single huge page can't OOM.
    private lateinit var bitmapCache: LruCache<Int, Bitmap>

    // Page sizes captured once at load time so the RecyclerView can lay out the
    // correct per-page height without keeping any PdfRenderer.Page open.
    private val pageSizes = ArrayList<PageSize>()

    @Volatile
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        overridePendingTransitionCompat(R.anim.slide_in_right, R.anim.fade_out)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val title = intent?.getStringExtra(EXTRA_PDF_TITLE)?.takeIf { it.isNotBlank() }
        binding.toolbar.title = title ?: getString(R.string.pdf_viewer_title)

        bitmapCache = object : LruCache<Int, Bitmap>(computeCacheSizeBytes()) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount

            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Bitmap,
                newValue: Bitmap?
            ) {
                // Recycle on eviction unless it's still the value we just put.
                if (oldValue != newValue && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setHasFixedSize(false)

        val rawUri = intent?.getStringExtra(EXTRA_PDF_URI)
        val uri = validateUri(rawUri)
        if (uri == null) {
            showError()
            return
        }

        load(uri)
    }

    // Accepts only file:// and content:// URIs. Anything else (http, javascript,
    // data, missing scheme, malformed) is rejected so we never hand an arbitrary
    // or remote source to PdfRenderer.
    private fun validateUri(raw: String?): Uri? {
        if (raw.isNullOrBlank()) return null
        val uri = try {
            Uri.parse(raw)
        } catch (e: Exception) {
            return null
        }
        return when (uri.scheme?.lowercase()) {
            ContentResolver.SCHEME_FILE, ContentResolver.SCHEME_CONTENT -> uri
            else -> null
        }
    }

    private fun load(uri: Uri) {
        showLoading()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { openRenderer(uri) }
            if (destroyed) return@launch
            if (!ok) {
                showError()
                return@launch
            }
            adapter = PdfPageAdapter()
            binding.recyclerView.adapter = adapter
            showContent()
        }
    }

    // Opens the document and captures every page's size. Runs on IO. Returns
    // false (never throws) on any failure; partial state is torn down so a retry
    // or onDestroy is safe.
    private fun openRenderer(uri: Uri): Boolean {
        synchronized(rendererLock) {
            if (destroyed) return false
            try {
                val descriptor = when (uri.scheme?.lowercase()) {
                    ContentResolver.SCHEME_FILE -> {
                        val path = uri.path ?: return false
                        val file = File(path)
                        if (!file.exists() || !file.canRead()) return false
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                    ContentResolver.SCHEME_CONTENT ->
                        contentResolver.openFileDescriptor(uri, "r")
                    else -> null
                } ?: return false

                pfd = descriptor
                val r = PdfRenderer(descriptor)
                renderer = r

                val count = r.pageCount
                if (count <= 0) {
                    // Empty/invalid document: tear down so we don't leak the PFD.
                    closeRendererLocked()
                    return false
                }
                pageSizes.clear()
                pageSizes.ensureCapacity(count)
                for (i in 0 until count) {
                    // Open/close each page just to read its intrinsic size.
                    r.openPage(i).use { page ->
                        pageSizes.add(PageSize(page.width, page.height))
                    }
                }
                return true
            } catch (e: Exception) {
                // Could be a corrupt/encrypted PDF, a revoked content URI, an
                // unreadable file, etc. Fail soft: tear down and report error.
                closeRendererLocked()
                return false
            }
        }
    }

    // Renders a single page to a bitmap sized to the RecyclerView width, capped
    // so one oversized page can't blow the heap. Returns null on any failure.
    private fun renderPage(position: Int, targetWidthPx: Int): Bitmap? {
        if (targetWidthPx <= 0) return null
        synchronized(rendererLock) {
            val r = renderer ?: return null
            if (destroyed) return null
            if (position < 0 || position >= pageSizes.size) return null
            val size = pageSizes[position]
            if (size.width <= 0 || size.height <= 0) return null

            // Scale to the view width, but never exceed MAX_BITMAP_DIMENSION_PX on
            // either axis (protects against pathologically large pages).
            var widthPx = minOf(targetWidthPx, MAX_BITMAP_DIMENSION_PX)
            var heightPx = (widthPx.toLong() * size.height / size.width).toInt()
            if (heightPx <= 0) return null
            if (heightPx > MAX_BITMAP_DIMENSION_PX) {
                heightPx = MAX_BITMAP_DIMENSION_PX
                widthPx = (heightPx.toLong() * size.width / size.height).toInt()
                if (widthPx <= 0) return null
            }

            return try {
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                // PDFs assume a white page; otherwise transparent areas render black.
                bitmap.eraseColor(Color.WHITE)
                r.openPage(position).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                bitmap
            } catch (oom: OutOfMemoryError) {
                // Drop caches and skip this page rather than crash the process.
                bitmapCache.evictAll()
                null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
    }

    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
    }

    private fun computeCacheSizeBytes(): Int {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        // Use a small slice of the heap; clamp so tiny/huge heaps both behave.
        val eighth = maxKb / 8
        val clampedKb = eighth.coerceIn(MIN_CACHE_KB, MAX_CACHE_KB)
        return clampedKb * 1024
    }

    override fun finish() {
        super.finish()
        overrideCloseTransitionCompat(R.anim.fade_in, R.anim.slide_out_left)
    }

    override fun onDestroy() {
        destroyed = true
        // Detach the adapter first so no in-flight bind tries to render after we
        // start tearing down the renderer.
        binding.recyclerView.adapter = null
        adapter = null

        // Recycle every cached bitmap (entryRemoved handles the actual recycle).
        bitmapCache.evictAll()

        // Close renderer + PFD under the same lock that guards rendering, so an
        // executor task that is mid-render finishes before we close.
        synchronized(rendererLock) {
            closeRendererLocked()
        }

        renderExecutor?.shutdownNow()
        renderExecutor = null
        super.onDestroy()
    }

    // Must be called while holding rendererLock.
    private fun closeRendererLocked() {
        try {
            renderer?.close()
        } catch (e: Exception) {
            // ignore: already closed / never opened
        } finally {
            renderer = null
        }
        try {
            pfd?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            pfd = null
        }
    }

    private fun executor(): ExecutorService {
        val existing = renderExecutor
        if (existing != null && !existing.isShutdown) return existing
        val created = Executors.newSingleThreadExecutor()
        renderExecutor = created
        return created
    }

    private data class PageSize(val width: Int, val height: Int)

    // Inner adapter: one ImageView per page. The view's height is set from the
    // page aspect ratio so scrolling is stable before the bitmap arrives; the
    // bitmap is produced off-thread and only applied if the holder hasn't been
    // recycled to a different page in the meantime.
    private inner class PdfPageAdapter : RecyclerView.Adapter<PdfPageViewHolder>() {

        override fun getItemCount(): Int = pageSizes.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
            // Build the per-page view in code so this unit owns no extra layout
            // file: an ImageView wrapped in a FrameLayout for margin/centering.
            val context = parent.context
            val container = FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val image = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    val margin = (PAGE_MARGIN_DP * context.resources.displayMetrics.density).toInt()
                    setMargins(margin, margin, margin, margin)
                }
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = context.getString(R.string.pdf_page_content_description)
            }
            container.addView(image)
            return PdfPageViewHolder(container, image)
        }

        override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun onViewRecycled(holder: PdfPageViewHolder) {
            holder.unbind()
        }
    }

    private inner class PdfPageViewHolder(
        itemView: View,
        private val image: ImageView
    ) : RecyclerView.ViewHolder(itemView) {
        private var boundPosition: Int = RecyclerView.NO_POSITION

        fun bind(position: Int) {
            boundPosition = position
            image.setImageBitmap(null)

            val size = pageSizes.getOrNull(position)
            // Decide the target width from the RecyclerView's current width, minus
            // the ImageView's horizontal margins, so the rendered bitmap width and
            // the reserved placeholder height stay consistent (no scroll jump).
            val rvWidth = binding.recyclerView.width
                .let { if (it > 0) it else itemView.resources.displayMetrics.widthPixels }
            val marginPx = (PAGE_MARGIN_DP * itemView.resources.displayMetrics.density).toInt()
            val targetWidth = (rvWidth - marginPx * 2).coerceAtLeast(1)

            // Reserve the right height up front (stable scrolling, no jump on load).
            if (size != null && size.width > 0) {
                val h = (targetWidth.toLong() * size.height / size.width).toInt().coerceAtLeast(1)
                setImageHeight(h)
            }

            // Cache hit: apply synchronously.
            val cached = bitmapCache.get(position)
            if (cached != null && !cached.isRecycled) {
                if (boundPosition == position) image.setImageBitmap(cached)
                return
            }

            if (destroyed) return
            val ex = try {
                executor()
            } catch (e: Exception) {
                return
            }
            try {
                ex.execute {
                    if (destroyed) return@execute
                    val bmp = try {
                        renderPage(position, targetWidth)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (e: Exception) {
                        null
                    } ?: return@execute
                    // Hand the bitmap to the cache; entryRemoved recycles any prior.
                    image.post {
                        if (destroyed) {
                            if (!bmp.isRecycled) bmp.recycle()
                            return@post
                        }
                        // Only cache once; if a concurrent put won, reuse theirs.
                        val existing = bitmapCache.get(position)
                        val toShow: Bitmap
                        if (existing != null && !existing.isRecycled) {
                            if (!bmp.isRecycled) bmp.recycle()
                            toShow = existing
                        } else {
                            bitmapCache.put(position, bmp)
                            toShow = bmp
                        }
                        if (boundPosition == position && !toShow.isRecycled) {
                            image.setImageBitmap(toShow)
                        }
                    }
                }
            } catch (e: Exception) {
                // Executor rejected (shutting down). Nothing to render.
            }
        }

        fun unbind() {
            boundPosition = RecyclerView.NO_POSITION
            // Don't recycle here: the cache owns the bitmap lifecycle.
            image.setImageBitmap(null)
        }

        private fun setImageHeight(heightPx: Int) {
            val lp = image.layoutParams ?: FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                heightPx
            )
            lp.height = heightPx
            image.layoutParams = lp
        }
    }

    companion object {
        // Launch-contract extra keys. Other units (the launcher) depend on these.
        const val EXTRA_PDF_URI = "helix.pdf_uri"
        const val EXTRA_PDF_TITLE = "helix.pdf_title"

        // Hard cap on a single rendered bitmap edge, regardless of page size, to
        // keep one page well under the 100MB+ that an unbounded huge page needs.
        private const val MAX_BITMAP_DIMENSION_PX = 2048

        // Bitmap cache budget, in KB, clamped from a slice of the heap.
        private const val MIN_CACHE_KB = 8 * 1024      // 8 MB floor
        private const val MAX_CACHE_KB = 64 * 1024     // 64 MB ceiling

        // Gap around each page so pages read as separate sheets.
        private const val PAGE_MARGIN_DP = 6
    }
}
