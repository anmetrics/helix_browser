package com.helix.browser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.helix.browser.HelixApp
import com.helix.browser.ui.adapter.Suggestion
import com.helix.browser.ui.adapter.SuggestionType
import com.helix.browser.utils.Prefs
import com.helix.browser.utils.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HelixApp
    private val historyRepo = app.historyRepository
    private val bookmarkRepo = app.bookmarkRepository

    // Browser state
    val currentUrl = MutableLiveData<String>("")
    val currentTitle = MutableLiveData<String>("")
    val loadingProgress = MutableLiveData<Int>(0)
    val isLoading = MutableLiveData<Boolean>(false)
    val isSecure = MutableLiveData<Boolean>(false)
    val isBookmarked = MutableLiveData<Boolean>(false)
    val canGoBack = MutableLiveData<Boolean>(false)
    val canGoForward = MutableLiveData<Boolean>(false)
    val isDesktopMode = MutableLiveData<Boolean>()
    val isIncognito = MutableLiveData<Boolean>(false)

    // Find in page
    val findInPageQuery = MutableLiveData<String>("")
    val showFindInPage = MutableLiveData<Boolean>(false)

    fun onPageStarted(url: String) {
        currentUrl.value = url
        isLoading.value = true
        isSecure.value = url.startsWith("https://")
        // Check bookmark status
        viewModelScope.launch {
            isBookmarked.value = bookmarkRepo.isBookmarked(url)
        }
    }

    fun onPageFinished(url: String, title: String) {
        currentUrl.value = url
        currentTitle.value = title.ifEmpty { url }
        isLoading.value = false
        isSecure.value = url.startsWith("https://")

        // Save history (not incognito, if enabled, and not an internal page).
        // Skip the new-tab page (about:blank) and the self-generated error/
        // interstitial pages, which are loaded via loadDataWithBaseURL and
        // surface here as "about:blank" or a "data:" URL.
        if (isIncognito.value != true &&
            Prefs.isSaveHistoryEnabled(app) &&
            !isInternalPage(url)
        ) {
            viewModelScope.launch {
                historyRepo.addHistory(
                    title = title.ifEmpty { url },
                    url = url
                )
            }
        }
        viewModelScope.launch {
            isBookmarked.value = bookmarkRepo.isBookmarked(url)
        }
    }

    /**
     * Returns true for URLs that should never be recorded in history: the empty
     * URL, the internal new-tab page (about:blank), and the self-generated
     * error/SSL/HTTPS-only interstitial pages (loaded via loadDataWithBaseURL,
     * which report as "about:blank" or a "data:" URL).
     */
    private fun isInternalPage(url: String): Boolean {
        return url.isEmpty() ||
            url == "about:blank" ||
            url.startsWith("about:") ||
            url.startsWith("data:")
    }

    fun onProgressChanged(progress: Int) {
        loadingProgress.value = progress
    }

    fun toggleBookmark(title: String, url: String) {
        viewModelScope.launch {
            val bookmarked = bookmarkRepo.toggleBookmark(title, url)
            isBookmarked.value = bookmarked
        }
    }

    fun updateNavState(canGoBack: Boolean, canGoForward: Boolean) {
        this.canGoBack.value = canGoBack
        this.canGoForward.value = canGoForward
    }

    // Omnibox autocomplete suggestions, merged from a synthetic "search the web"
    // row, local history, local bookmarks, and (debounced, off-main, cancellable)
    // live search-engine suggestions. Emits an immutable list of Suggestion so the
    // adapter can DiffUtil it and render per-type icons/favicons.
    val suggestions = MutableLiveData<List<Suggestion>>()

    // Chrome-style inline autocomplete. Holds the trailing completion suffix the
    // Activity can grey-out behind the user's typed prefix in the address
    // EditText (e.g. typed "git", top history/bookmark host github.com -> emits
    // "hub.com"). Null whenever there is no eligible URL completion: too-short or
    // search-like input, incognito, or no local URL row whose host/URL startsWith
    // the typed text (scheme/www stripped, case-insensitive). Always posted on the
    // main thread, in lockstep with the local-suggestion publish so the field and
    // the rendered rows never disagree.
    val inlineCompletion = MutableLiveData<String?>(null)

    // The in-flight suggestion job. A new keystroke cancels the previous one so an
    // older (slower) response can never clobber the latest query's results.
    private var suggestionJob: Job? = null

    fun fetchSuggestions(query: String) {
        // Cancel any pending debounce / in-flight network for the previous query.
        suggestionJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < MIN_SUGGEST_QUERY_LEN) {
            suggestions.value = emptyList()
            inlineCompletion.value = null
            return
        }

        // Resolve the engine once up front; reused for the search row's URL and
        // for the live-suggest endpoint so the two never disagree. Capture the
        // incognito flag on the main thread so no LiveData is read off-main.
        val engine = try { Prefs.getSearchEngine(app) } catch (_: Exception) { "google" }
        val looksLikeUrl = UrlUtils.isUrl(trimmed)
        val incognito = isIncognito.value == true

        suggestionJob = viewModelScope.launch {
            // Debounce: wait out a burst of fast keystrokes before doing any work.
            // Cancellation (next keystroke) aborts here cheaply, before any I/O.
            delay(SUGGEST_DEBOUNCE_MS)

            // 0) Answer card: detect a local calculator / unit-conversion result and
            // surface it as a distinct top row. Computed PURELY on-device; an answer
            // query is never sent to the network (see the live-suggest skip below).
            val answerRow = makeAnswerRow(trimmed)

            // 1) Local matches first so the list is responsive even fully offline.
            val rawLocal = loadLocalSuggestions(trimmed, incognito)

            // Derive the Chrome-style inline completion from the best (highest-
            // ranked) local URL row whose host/URL startsWith the typed text. Never
            // in incognito (no autocomplete that could reveal private-browsing
            // history) and never for search-like input. The chosen row is annotated
            // so the completion travels with it through DiffUtil; the Activity reads
            // the scalar [inlineCompletion] LiveData. Computed off the captured list,
            // not from LiveData, so nothing is read off-main.
            val match = if (incognito) null else computeInlineMatch(trimmed, rawLocal)
            val local = annotateInlineCompletion(rawLocal, match)
            inlineCompletion.value = match?.suffix

            // Synthetic top row: always offer "search the web for <query>" unless
            // the user is clearly typing a URL (then a search row is just noise).
            // navigateValue is a real, loadable search URL; insertValue is the raw
            // phrase the user can edit.
            val searchRow = if (looksLikeUrl) null else makeSearchRow(trimmed, engine, isQueryRow = true)

            // Publish the local-only result immediately (fail-soft baseline).
            suggestions.value = mergeSuggestions(answerRow, searchRow, emptyList(), local)

            // 2) Live engine suggestions: skip when the input looks like a URL
            // (don't leak full URLs to the suggest endpoint), in incognito (never
            // send keystrokes off-device in private browsing), or when the query
            // is an answer (math/conversion is resolved locally and must never be
            // sent to the network).
            if (looksLikeUrl || incognito || answerRow != null) return@launch

            val remote = fetchEngineSuggestions(trimmed, engine)
            if (remote.isEmpty()) return@launch

            val remoteRows = remote.map { phrase -> makeSearchRow(phrase, engine, isQueryRow = false) }
            // Re-merge with the live suggestions interleaved under the search row
            // and above raw history/bookmark matches.
            suggestions.value = mergeSuggestions(answerRow, searchRow, remoteRows, local)
        }
    }

    // Builds a SEARCH suggestion whose tap loads a real search URL for [engine]
    // and whose insert action fills the omnibox with the raw [phrase].
    // [isQueryRow] true -> the "search for <typed text>" header subtitle;
    // false -> a live engine-suggested phrase.
    private fun makeSearchRow(phrase: String, engine: String, isQueryRow: Boolean): Suggestion {
        val ctx = getApplication<Application>()
        val subtitle = if (isQueryRow) {
            ctx.getString(com.helix.browser.R.string.search_for, phrase)
        } else {
            ctx.getString(com.helix.browser.R.string.search_suggestion_subtitle)
        }
        return Suggestion(
            type = SuggestionType.SEARCH,
            primaryText = phrase,
            secondaryText = subtitle,
            navigateValue = UrlUtils.buildSearchQuery(phrase, engine),
            insertValue = phrase
        )
    }

    // Pulls history + bookmark matches for [query] off the main thread and maps
    // them to HISTORY/BOOKMARK Suggestion rows with a best-effort favicon.
    // In incognito we never derive a REMOTE favicon URL (that would leak the host
    // to the favicon provider); only a locally-stored faviconUrl is used.
    private suspend fun loadLocalSuggestions(query: String, incognito: Boolean): List<Suggestion> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<Suggestion>()
            val allowRemoteFavicons = !incognito

            val bookmarks = try {
                searchBookmarks(query)
            } catch (_: Exception) {
                emptyList()
            }
            val history = try {
                historyRepo.getSuggestions(query)
            } catch (_: Exception) {
                emptyList()
            }

            bookmarks.forEach { b ->
                out.add(
                    Suggestion(
                        type = SuggestionType.BOOKMARK,
                        primaryText = b.title.ifBlank { UrlUtils.getDisplayUrl(b.url) },
                        secondaryText = UrlUtils.getDisplayUrl(b.url),
                        navigateValue = b.url,
                        faviconUrl = faviconFor(b.faviconUrl, b.url, allowRemoteFavicons)
                    )
                )
            }
            history.forEach { h ->
                out.add(
                    Suggestion(
                        type = SuggestionType.HISTORY,
                        primaryText = h.title.ifBlank { UrlUtils.getDisplayUrl(h.url) },
                        secondaryText = UrlUtils.getDisplayUrl(h.url),
                        navigateValue = h.url,
                        faviconUrl = faviconFor(h.faviconUrl, h.url, allowRemoteFavicons)
                    )
                )
            }
            out
        }

    // The chosen inline-autocomplete match: [suffix] is the trailing text to grey-
    // out behind the user's prefix, [rowIndex] is the index in the local list of the
    // row it came from (so the very same row is annotated, no re-matching needed).
    private data class InlineMatch(val suffix: String, val rowIndex: Int)

    // Computes the inline-autocomplete match for [query] from the ranked local
    // matches [local] (bookmarks first, then history — i.e. the order they were
    // produced). Returns null when no eligible URL completion exists.
    //
    // Rules (mirrors Chrome's omnibox inline autocomplete):
    //  - Only URL-bearing rows (HISTORY/BOOKMARK/URL) are candidates; SEARCH rows
    //    and any search-like query (containing whitespace) never autocomplete.
    //  - Comparison strips scheme + leading "www." from BOTH sides and is
    //    case-insensitive, so "git" completes "https://www.github.com/" cleanly.
    //  - A completion is offered when the candidate's HOST startsWith the typed
    //    prefix, or (for deeper typing like "github.com/iss") when the full
    //    scheme/www-stripped URL does. The suffix is taken from the ORIGINAL
    //    (un-normalized) candidate text so casing/path are preserved as stored.
    //  - The prefix must be a strict prefix (suffix non-empty) and itself look
    //    URL-ish (no spaces); an exact full match yields no suffix (null).
    private fun computeInlineMatch(query: String, local: List<Suggestion>): InlineMatch? {
        val typed = query.trim()
        // Whitespace => the user is composing a search phrase, not a URL.
        if (typed.isEmpty() || typed.any { it.isWhitespace() }) return null
        val typedKey = comparableUrlForm(typed)
        if (typedKey.isEmpty()) return null

        for ((index, s) in local.withIndex()) {
            if (s.type == SuggestionType.SEARCH) continue
            val target = s.navigateValue
            if (target.isBlank()) continue

            // Prefer completing against the bare host (the common "git"->github.com
            // case); fall back to the full scheme/www-stripped URL for deeper input
            // such as "github.com/iss".
            val host = hostOf(target)
            if (!host.isNullOrEmpty()) {
                val hostKey = comparableUrlForm(host)
                if (hostKey.length > typedKey.length &&
                    hostKey.startsWith(typedKey, ignoreCase = true)
                ) {
                    return InlineMatch(host.substring(typedKey.length), index)
                }
            }

            val urlKey = comparableUrlForm(target)
            if (urlKey.length > typedKey.length &&
                urlKey.startsWith(typedKey, ignoreCase = true)
            ) {
                val stripped = stripSchemeAndWww(target)
                if (stripped.length > typedKey.length) {
                    return InlineMatch(stripped.substring(typedKey.length), index)
                }
            }
        }
        return null
    }

    // Tags exactly the matched row (by index) with the inline completion so the
    // value travels alongside the row through DiffUtil and stays consistent with
    // the scalar inlineCompletion LiveData. Returns the list unchanged when there
    // is no match.
    private fun annotateInlineCompletion(
        local: List<Suggestion>,
        match: InlineMatch?
    ): List<Suggestion> {
        if (match == null || match.rowIndex !in local.indices) return local
        return local.mapIndexed { i, s ->
            if (i == match.rowIndex) s.copy(inlineCompletion = match.suffix) else s
        }
    }

    // The case-insensitive comparison key for a URL or host: scheme + leading
    // "www." removed and lower-cased. Used only for prefix matching, never shown.
    private fun comparableUrlForm(value: String): String =
        stripSchemeAndWww(value).lowercase()

    // Strips a leading scheme ("https://", "http://", "ftp://", ...) and a single
    // leading "www." from [value], preserving original casing of the remainder so
    // a derived completion suffix keeps the stored casing/path.
    private fun stripSchemeAndWww(value: String): String {
        var v = value.trim()
        val schemeEnd = v.indexOf("://")
        if (schemeEnd in 0..MAX_SCHEME_LEN) {
            v = v.substring(schemeEnd + 3)
        }
        if (v.startsWith("www.", ignoreCase = true)) {
            v = v.substring(4)
        }
        return v
    }

    // Best-effort host extraction; null/blank when [url] has no parseable host
    // (e.g. a bare typed token), so callers fall back to full-URL matching.
    private fun hostOf(url: String): String? =
        try {
            android.net.Uri.parse(url).host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }

    // Resolves the favicon URL for a row: prefer a locally-stored [stored] URL;
    // otherwise fall back to the remote favicon service ONLY when [allowRemote]
    // (i.e. not incognito), so private-browsing rows never ping a 3rd party.
    private fun faviconFor(stored: String?, url: String, allowRemote: Boolean): String? =
        when {
            !stored.isNullOrBlank() -> stored
            allowRemote -> UrlUtils.getFaviconUrl(url).ifBlank { null }
            else -> null
        }

    // Bookmark search uses the repository's reactive Flow; we only need a one-shot
    // snapshot here, so take the first emission. Bounded to a small slice for the
    // omnibox. Runs on the caller's IO context.
    private suspend fun searchBookmarks(query: String): List<com.helix.browser.data.Bookmark> =
        try {
            bookmarkRepo.search(query).first().take(BOOKMARK_SUGGEST_LIMIT)
        } catch (_: Exception) {
            emptyList()
        }

    // Performs the engine suggest request fully off the main thread with hard
    // connect/read timeouts AND an overall coroutine timeout, returning [] on any
    // error/offline condition (fail-soft to local-only). Honors cancellation.
    private suspend fun fetchEngineSuggestions(query: String, engine: String): List<String> {
        val endpoint = UrlUtils.buildSuggestUrl(query, engine) ?: return emptyList()
        return withTimeoutOrNull(SUGGEST_NETWORK_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = SUGGEST_CONNECT_TIMEOUT_MS
                        readTimeout = SUGGEST_READ_TIMEOUT_MS
                        instanceFollowRedirects = true
                        setRequestProperty("Accept", "application/json, text/javascript")
                    }
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext emptyList()
                    // Decode using the charset the server actually declares (suggest
                    // endpoints sometimes return ISO-8859-1/windows-1252); decoding
                    // those bytes as UTF-8 mangles non-ASCII text (e.g. Vietnamese)
                    // into U+FFFD replacement chars. Fall back to UTF-8.
                    val charset = conn.contentType
                        ?.substringAfter("charset=", "")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() }
                        ?: Charsets.UTF_8
                    val body = conn.inputStream.bufferedReader(charset).use(BufferedReader::readText)
                    UrlUtils.parseSuggestResponse(body)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .take(ENGINE_SUGGEST_LIMIT)
                } catch (_: Exception) {
                    emptyList()
                } finally {
                    try { conn?.disconnect() } catch (_: Exception) { /* ignore */ }
                }
            }
        } ?: emptyList()
    }

    // Builds the omnibox ANSWER row (calculator / unit conversion) for [query], or
    // null when the query is neither. The result is computed purely on-device via
    // UrlUtils (no eval, no network). navigateValue carries the plain result text
    // the adapter copies to the clipboard on tap (answer rows never navigate).
    private fun makeAnswerRow(query: String): Suggestion? {
        val answer = try {
            UrlUtils.computeAnswer(query)
        } catch (_: Exception) {
            null
        } ?: return null
        val ctx = getApplication<Application>()
        return Suggestion(
            type = SuggestionType.ANSWER,
            primaryText = answer.display,
            secondaryText = ctx.getString(com.helix.browser.R.string.answer_tap_to_copy),
            // Not a URL: the result text, used as the clipboard payload on tap.
            navigateValue = answer.copyValue,
            insertValue = answer.copyValue
        )
    }

    // Merges the answer row, search row, live engine suggestions, and local matches
    // into one de-duplicated, bounded list. Order: [answer] [search] [engine] [local].
    // De-dup is by navigateValue (case-insensitive) so an engine phrase that also
    // exists in history isn't shown twice. The answer row never participates in the
    // URL de-dup set (its navigateValue is a plain result, not a URL) so it can't
    // accidentally suppress a real suggestion that happens to share the text.
    private fun mergeSuggestions(
        answerRow: Suggestion?,
        searchRow: Suggestion?,
        remote: List<Suggestion>,
        local: List<Suggestion>
    ): List<Suggestion> {
        val out = ArrayList<Suggestion>(MAX_SUGGESTIONS)
        val seen = HashSet<String>()
        answerRow?.let { out.add(it) }
        searchRow?.let {
            out.add(it)
            seen.add(it.navigateValue.lowercase())
        }
        for (s in remote) {
            if (out.size >= MAX_SUGGESTIONS) break
            if (seen.add(s.navigateValue.lowercase())) out.add(s)
        }
        for (s in local) {
            if (out.size >= MAX_SUGGESTIONS) break
            if (seen.add(s.navigateValue.lowercase())) out.add(s)
        }
        return out
    }

    override fun onCleared() {
        super.onCleared()
        suggestionJob?.cancel()
    }

    companion object {
        private const val MIN_SUGGEST_QUERY_LEN = 1
        private const val SUGGEST_DEBOUNCE_MS = 150L
        private const val SUGGEST_NETWORK_TIMEOUT_MS = 2500L
        private const val SUGGEST_CONNECT_TIMEOUT_MS = 2000
        private const val SUGGEST_READ_TIMEOUT_MS = 2000
        private const val ENGINE_SUGGEST_LIMIT = 6
        private const val BOOKMARK_SUGGEST_LIMIT = 3
        private const val MAX_SUGGESTIONS = 10

        // Upper bound on a leading URL scheme length when stripping it for inline-
        // completion comparison; guards against treating a stray "://" deep inside
        // a path/query as a scheme delimiter.
        private const val MAX_SCHEME_LEN = 8
    }
}
