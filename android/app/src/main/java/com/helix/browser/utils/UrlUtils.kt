package com.helix.browser.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager
import com.helix.browser.HelixApp

object UrlUtils {

    fun isUrl(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("ftp://") ||
                (trimmed.contains(".") && !trimmed.contains(" "))
    }

    /**
     * Engine-aware URL formatting. If [input] is a URL it is normalized and returned
     * as-is; otherwise it is turned into a search query using [engine] (which may be a
     * built-in id like "duckduckgo" or a "custom:" template).
     *
     * Before falling back to a plain web search, the FIRST whitespace-separated
     * token is checked against the user's tab-to-search keyword map (see
     * [Prefs.getSearchKeywords]); a match routes the remaining text through the
     * keyword's URL template. Explicit URL/scheme inputs are never intercepted so
     * "https://..." and bare domains keep working unchanged.
     */
    fun formatUrl(input: String, engine: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("file://") || trimmed.startsWith("about:") -> trimmed
            trimmed.startsWith("ftp://") -> trimmed
            isUrl(trimmed) -> "https://$trimmed"
            else -> resolveKeyword(trimmed) ?: buildSearchQuery(trimmed, engine)
        }
    }

    /**
     * If the first token of [input] is a stored tab-to-search keyword, returns the
     * keyword's URL template with the REMAINING text substituted for "%s" (URL-
     * encoded). Returns null when there is no keyword match, when the keyword has
     * no remaining query text after it, or when the map can't be loaded — in every
     * such case the caller falls back to a normal web search so behavior is never
     * worse than before.
     *
     * The keyword lookup is case-insensitive on the keyword token. The remaining
     * text is encoded exactly like [buildSearchQuery] so reserved characters are
     * safe. Resolution uses the application context (incognito-safe: keywords are a
     * user setting, not browsing history) and fails soft to null on any error.
     */
    fun resolveKeyword(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // Split on the FIRST run of whitespace: token = keyword, rest = query.
        val sep = trimmed.indexOfFirst { it.isWhitespace() }
        if (sep <= 0) return null // no trailing query, or empty keyword
        val keyword = trimmed.substring(0, sep)
        val rest = trimmed.substring(sep + 1).trim()
        if (rest.isEmpty()) return null
        val template = try {
            val ctx = HelixApp.instance
            Prefs.getSearchKeywords(ctx)[keyword.lowercase()]
        } catch (e: Exception) {
            null
        } ?: return null
        return applyKeywordTemplate(template, rest)
    }

    /**
     * Substitutes the URL-encoded [query] into a keyword [template]. A "%s" token is
     * replaced; a template without one has the encoded query appended (mirrors the
     * custom-engine contract in [buildSearchQuery]). Returns null when the result is
     * not a safe http(s) URL so a malformed/hostile stored template can never load
     * a "javascript:" / "data:" / "file:" scheme from the omnibox.
     */
    private fun applyKeywordTemplate(template: String, query: String): String? {
        val tpl = template.trim()
        if (tpl.isEmpty()) return null
        val encoded = Uri.encode(query)
        val url = if ("%s" in tpl) tpl.replace("%s", encoded) else "$tpl$encoded"
        val lower = url.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) url else null
    }

    /**
     * Convenience overload that resolves the user's currently selected search engine
     * from preferences via the application context. This keeps existing Context-free
     * call sites honoring the user's chosen engine instead of always using Google.
     */
    fun formatUrl(input: String): String = formatUrl(input, resolveSearchEngine())

    fun formatUrl(context: Context, input: String): String =
        formatUrl(input, Prefs.getSearchEngine(context))

    private fun resolveSearchEngine(): String = try {
        Prefs.getSearchEngine(HelixApp.instance)
    } catch (e: Exception) {
        "google"
    }

    fun buildSearchQuery(query: String, engine: String): String {
        val encoded = Uri.encode(query)
        // Custom engines use the "custom:" prefix followed by a URL template
        // containing %s where the query should be inserted. Example:
        //   custom:https://www.ecosia.org/search?q=%s
        if (engine.startsWith(CUSTOM_PREFIX)) {
            val template = engine.removePrefix(CUSTOM_PREFIX)
            return if ("%s" in template) template.replace("%s", encoded)
                   else "$template$encoded"
        }
        return when (engine.lowercase()) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "yahoo" -> "https://search.yahoo.com/search?p=$encoded"
            "brave" -> "https://search.brave.com/search?q=$encoded"
            "yandex" -> "https://yandex.com/search/?text=$encoded"
            "ecosia" -> "https://www.ecosia.org/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

    const val CUSTOM_PREFIX = "custom:"

    fun getDisplayUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: url
            host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
    }

    fun getFaviconUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host
            if (host.isNullOrBlank()) "" else "https://www.google.com/s2/favicons?domain=$host&sz=64"
        } catch (e: Exception) {
            ""
        }
    }

    fun isSearchQuery(input: String) = !isUrl(input)

    // ---------------------------------------------------------------------------
    // Omnibox answer cards (calculator + unit conversion).
    //
    // These are PURE, side-effect-free, network-free helpers used by the
    // suggestion pipeline to surface an inline answer for math/conversion queries.
    // They NEVER eval arbitrary code: arithmetic is parsed by a hand-written
    // recursive-descent parser over a fixed grammar, and conversions use a small
    // built-in factor table. Any malformed/over-long/non-numeric input returns
    // null so the caller silently falls back to normal suggestions.
    // ---------------------------------------------------------------------------

    /** The computed result of an omnibox answer query. */
    data class AnswerResult(val display: String, val copyValue: String)

    // Hard cap on the raw query length we will even attempt to evaluate. Guards
    // against pathological input (e.g. a pasted page) and bounds parser work.
    private const val MAX_ANSWER_INPUT_LEN = 64

    /**
     * Attempts to interpret [input] as an omnibox answer (arithmetic OR a unit
     * conversion) and returns the formatted result, or null if it is neither.
     * Conversions are tried first because their tokens ("to") never form valid
     * arithmetic. Pure and network-free.
     */
    fun computeAnswer(input: String): AnswerResult? {
        val q = input.trim()
        if (q.isEmpty() || q.length > MAX_ANSWER_INPUT_LEN) return null
        return computeConversion(q) ?: computeArithmetic(q)
    }

    /**
     * Evaluates [input] as a simple arithmetic expression over + - * / % and
     * parentheses with decimals, via a safe recursive-descent parser (NO eval).
     * Returns null for anything that isn't a well-formed numeric expression, for
     * divide-by-zero, and for non-finite results (overflow). A bare number or a
     * single token with no operator is NOT treated as an answer (it would just
     * echo the input), so the omnibox doesn't show a useless "5 = 5" card.
     */
    fun computeArithmetic(input: String): AnswerResult? {
        val expr = input.trim()
        if (expr.isEmpty() || expr.length > MAX_ANSWER_INPUT_LEN) return null
        // Reject anything outside the arithmetic alphabet up front; this also
        // keeps letters/units out of the parser so "10 km" never evaluates.
        if (!expr.all { it.isDigit() || it in "+-*/%(). \t" }) return null
        // Require at least one operator so a bare number isn't shown as an answer.
        if (!expr.any { it in "+-*/%" }) return null
        // A leading sign on the whole expression (e.g. "-3+4") is fine, but a
        // lone "-5" has an operator yet is just a number; suppress those.
        val value = try {
            ArithmeticParser(expr).parse()
        } catch (e: Exception) {
            null
        } ?: return null
        if (!value.isFinite()) return null
        val formatted = formatNumber(value)
        return AnswerResult(display = "$expr = $formatted", copyValue = formatted)
    }

    /**
     * A minimal recursive-descent / Pratt-style parser for the grammar:
     *   expr   := term (('+' | '-') term)*
     *   term   := factor (('*' | '/' | '%') factor)*
     *   factor := ('+' | '-') factor | '(' expr ')' | number
     * Throws on any syntax error or divide/modulo-by-zero (caught by the caller).
     * It only ever produces a Double from the input string — it cannot execute
     * code, call functions, or reference variables.
     */
    private class ArithmeticParser(private val src: String) {
        private var pos = 0

        fun parse(): Double {
            val v = parseExpr()
            skipWs()
            if (pos != src.length) throw IllegalArgumentException("trailing input")
            return v
        }

        private fun parseExpr(): Double {
            var acc = parseTerm()
            while (true) {
                skipWs()
                when (peek()) {
                    '+' -> { pos++; acc += parseTerm() }
                    '-' -> { pos++; acc -= parseTerm() }
                    else -> return acc
                }
            }
        }

        private fun parseTerm(): Double {
            var acc = parseFactor()
            while (true) {
                skipWs()
                when (peek()) {
                    '*' -> { pos++; acc *= parseFactor() }
                    '/' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("divide by zero")
                        acc /= d
                    }
                    '%' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("mod by zero")
                        acc %= d
                    }
                    else -> return acc
                }
            }
        }

        private fun parseFactor(): Double {
            skipWs()
            return when (peek()) {
                '+' -> { pos++; parseFactor() }
                '-' -> { pos++; -parseFactor() }
                '(' -> {
                    pos++
                    val v = parseExpr()
                    skipWs()
                    if (peek() != ')') throw IllegalArgumentException("expected )")
                    pos++
                    v
                }
                else -> parseNumber()
            }
        }

        private fun parseNumber(): Double {
            skipWs()
            val start = pos
            var seenDot = false
            while (pos < src.length) {
                val c = src[pos]
                if (c.isDigit()) {
                    pos++
                } else if (c == '.' && !seenDot) {
                    seenDot = true
                    pos++
                } else {
                    break
                }
            }
            if (pos == start) throw IllegalArgumentException("expected number")
            val token = src.substring(start, pos)
            return token.toDoubleOrNull() ?: throw IllegalArgumentException("bad number")
        }

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else ' '
    }

    // Canonical unit -> (display label, factor to the unit's base) per dimension.
    // value_in_base = value * factor. Conversion within a dimension is
    // out = in * (fromFactor / toFactor). All aliases map to a canonical key.
    private data class UnitDef(val dimension: String, val factorToBase: Double, val label: String)

    private val UNIT_TABLE: Map<String, UnitDef> = buildMap {
        // Length (base: meter)
        put("mm", UnitDef("length", 0.001, "mm"))
        put("cm", UnitDef("length", 0.01, "cm"))
        put("m", UnitDef("length", 1.0, "m"))
        put("km", UnitDef("length", 1000.0, "km"))
        put("in", UnitDef("length", 0.0254, "in"))
        put("inch", UnitDef("length", 0.0254, "in"))
        put("ft", UnitDef("length", 0.3048, "ft"))
        put("foot", UnitDef("length", 0.3048, "ft"))
        put("feet", UnitDef("length", 0.3048, "ft"))
        put("yd", UnitDef("length", 0.9144, "yd"))
        put("yard", UnitDef("length", 0.9144, "yd"))
        put("mi", UnitDef("length", 1609.344, "mi"))
        put("mile", UnitDef("length", 1609.344, "mi"))
        put("miles", UnitDef("length", 1609.344, "mi"))
        // Mass (base: gram)
        put("mg", UnitDef("mass", 0.001, "mg"))
        put("g", UnitDef("mass", 1.0, "g"))
        put("kg", UnitDef("mass", 1000.0, "kg"))
        put("oz", UnitDef("mass", 28.349523125, "oz"))
        put("lb", UnitDef("mass", 453.59237, "lb"))
        put("lbs", UnitDef("mass", 453.59237, "lb"))
        put("pound", UnitDef("mass", 453.59237, "lb"))
        put("pounds", UnitDef("mass", 453.59237, "lb"))
        // Volume (base: liter)
        put("ml", UnitDef("volume", 0.001, "ml"))
        put("l", UnitDef("volume", 1.0, "L"))
        put("liter", UnitDef("volume", 1.0, "L"))
        put("litre", UnitDef("volume", 1.0, "L"))
        put("gal", UnitDef("volume", 3.785411784, "gal"))
        put("gallon", UnitDef("volume", 3.785411784, "gal"))
        // Temperature handled specially below (affine, not linear).
        put("c", UnitDef("temp", 1.0, "°C"))
        put("celsius", UnitDef("temp", 1.0, "°C"))
        put("f", UnitDef("temp", 1.0, "°F"))
        put("fahrenheit", UnitDef("temp", 1.0, "°F"))
        put("k", UnitDef("temp", 1.0, "K"))
        put("kelvin", UnitDef("temp", 1.0, "K"))
    }

    /**
     * Parses a unit conversion like "10 km to mi", "32 f to c", "5kg in lb".
     * Grammar: <number> <fromUnit> ("to" | "in" | ">") <toUnit>. The number and
     * unit may be written together ("5kg"). Returns null when the shape doesn't
     * match, the units are unknown, or the two units are different dimensions.
     * Temperature uses affine formulas; every other dimension uses linear factors.
     */
    fun computeConversion(input: String): AnswerResult? {
        val q = input.trim().lowercase()
        if (q.isEmpty() || q.length > MAX_ANSWER_INPUT_LEN) return null
        // Tokenize on whitespace; then re-split so "5kg" -> "5" "kg".
        val tokens = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size < 3 || tokens.size > 4) return null

        // Locate the connector ("to" / "in" / ">"). Must be present and not first.
        val connectorIdx = tokens.indexOfFirst { it == "to" || it == "in" || it == ">" }
        if (connectorIdx <= 0 || connectorIdx >= tokens.size - 1) return null

        // Left side: either ["5", "kg"] or ["5kg"]. Right side: the unit token.
        val left = tokens.subList(0, connectorIdx)
        val toUnitRaw = tokens.subList(connectorIdx + 1, tokens.size).joinToString("")
        val (amount, fromUnitRaw) = parseAmountAndUnit(left) ?: return null

        val from = UNIT_TABLE[normalizeUnit(fromUnitRaw)] ?: return null
        val to = UNIT_TABLE[normalizeUnit(toUnitRaw)] ?: return null
        if (from.dimension != to.dimension) return null

        val result = if (from.dimension == "temp") {
            convertTemperature(amount, from.label, to.label) ?: return null
        } else {
            amount * (from.factorToBase / to.factorToBase)
        }
        if (!result.isFinite()) return null

        val formattedIn = formatNumber(amount)
        val formattedOut = formatNumber(result)
        return AnswerResult(
            display = "$formattedIn ${from.label} = $formattedOut ${to.label}",
            copyValue = formattedOut
        )
    }

    // Splits the left side of a conversion into (amount, unitToken). Accepts
    // ["5", "kg"] (two tokens) or ["5kg"] (one token where the leading numeric
    // run is the amount). Returns null when no valid leading number is present.
    private fun parseAmountAndUnit(left: List<String>): Pair<Double, String>? {
        if (left.size == 2) {
            val amount = left[0].toDoubleOrNull() ?: return null
            return amount to left[1]
        }
        if (left.size == 1) {
            val t = left[0]
            // Split the leading numeric prefix (digits, one dot, optional sign).
            var i = 0
            if (i < t.length && (t[i] == '-' || t[i] == '+')) i++
            var seenDot = false
            while (i < t.length) {
                val c = t[i]
                if (c.isDigit()) i++
                else if (c == '.' && !seenDot) { seenDot = true; i++ }
                else break
            }
            val numPart = t.substring(0, i)
            val unitPart = t.substring(i)
            val amount = numPart.toDoubleOrNull() ?: return null
            if (unitPart.isEmpty()) return null
            return amount to unitPart
        }
        return null
    }

    private fun normalizeUnit(raw: String): String =
        raw.trim().lowercase().removeSuffix(".")

    // Affine temperature conversion via canonical labels set in UNIT_TABLE.
    private fun convertTemperature(value: Double, fromLabel: String, toLabel: String): Double? {
        // Normalize to Celsius first.
        val celsius = when (fromLabel) {
            "°C" -> value
            "°F" -> (value - 32.0) * 5.0 / 9.0
            "K" -> value - 273.15
            else -> return null
        }
        return when (toLabel) {
            "°C" -> celsius
            "°F" -> celsius * 9.0 / 5.0 + 32.0
            "K" -> celsius + 273.15
            else -> null
        }
    }

    /**
     * Formats a numeric result for display: integral values render without a
     * decimal point ("84"), others are rounded to at most 6 significant-ish
     * fractional digits with trailing zeros stripped ("6.214"). Never uses
     * locale grouping so the copied value is a clean machine-parseable number.
     */
    private fun formatNumber(value: Double): String {
        if (value == 0.0) return "0" // also collapses -0.0
        // Integral within tolerance -> integer form (handles 84.0 -> "84").
        if (kotlin.math.abs(value - Math.rint(value)) < 1e-9 &&
            kotlin.math.abs(value) < 1e15
        ) {
            return value.toLong().toString()
        }
        // Round to 6 fractional digits, then trim trailing zeros and dot.
        val rounded = String.format(java.util.Locale.US, "%.6f", value)
        return rounded.trimEnd('0').trimEnd('.')
    }

    /**
     * Builds the search-engine autocomplete (suggest) endpoint URL for [query]
     * using the user's selected [engine]. Returns null when no suggest endpoint
     * is known for the engine (e.g. custom engines, where we have no contract for
     * a suggest API) so the caller falls back to local history/bookmark matches.
     *
     * All returned endpoints respond with the OpenSearch suggestion JSON shape
     * `["query", ["s1", "s2", ...]]`, which [parseSuggestResponse] decodes.
     * The query is percent-encoded; this method never performs I/O.
     */
    fun buildSuggestUrl(query: String, engine: String): String? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val encoded = Uri.encode(q)
        // Custom engines have no standardized suggest endpoint; fall back to local.
        if (engine.startsWith(CUSTOM_PREFIX)) return null
        return when (engine.lowercase()) {
            // Bing/Yahoo expose the OpenSearch JSON via the Bing osjson API.
            "bing", "yahoo" -> "https://api.bing.com/osjson.aspx?query=$encoded"
            // DuckDuckGo: ?type=list yields the OpenSearch ["q",[...]] shape.
            "duckduckgo" -> "https://duckduckgo.com/ac/?q=$encoded&type=list"
            // Google's Firefox client returns ["q",[...]] (no JSONP wrapper).
            // Brave/Yandex/Ecosia have no public CORS-free suggest endpoint we
            // can rely on, so they reuse Google's suggest for parity.
            "google", "brave", "yandex", "ecosia" ->
                "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
            else -> "https://suggestqueries.google.com/complete/search?client=firefox&q=$encoded"
        }
    }

    /**
     * Parses the OpenSearch suggestion JSON `["query", ["s1", "s2", ...]]` body
     * into the list of suggestion phrases (the second array element). Returns an
     * empty list for any malformed/empty body so a bad response never throws on
     * the caller. Does NOT perform I/O.
     */
    fun parseSuggestResponse(body: String?): List<String> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val root = org.json.JSONArray(body)
            if (root.length() < 2) return emptyList()
            val phrases = root.optJSONArray(1) ?: return emptyList()
            val out = ArrayList<String>(phrases.length())
            for (i in 0 until phrases.length()) {
                val s = phrases.optString(i).trim()
                if (s.isNotEmpty()) out.add(s)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}

object Prefs {
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_HOMEPAGE = "homepage"
    private const val KEY_JAVASCRIPT = "javascript_enabled"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_BLOCK_ADS = "block_ads"
    private const val KEY_BLOCK_POPUPS = "block_popups"
    private const val KEY_SAVE_HISTORY = "save_history"
    private const val KEY_LANGUAGE = "app_language"
    private const val DEFAULT_HOMEPAGE = "https://www.google.com"

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun getSearchEngine(context: Context) = prefs(context).getString(KEY_SEARCH_ENGINE, "google") ?: "google"
    fun setSearchEngine(context: Context, engine: String) = prefs(context).edit().putString(KEY_SEARCH_ENGINE, engine).apply()

    fun getHomepage(context: Context) = prefs(context).getString(KEY_HOMEPAGE, DEFAULT_HOMEPAGE) ?: DEFAULT_HOMEPAGE
    fun setHomepage(context: Context, url: String) = prefs(context).edit().putString(KEY_HOMEPAGE, url).apply()

    fun isJavaScriptEnabled(context: Context) = prefs(context).getBoolean(KEY_JAVASCRIPT, true)
    fun isDarkMode(context: Context) = prefs(context).getBoolean(KEY_DARK_MODE, false)
    fun isDesktopMode(context: Context) = prefs(context).getBoolean(KEY_DESKTOP_MODE, false)
    // Ad blocking is ON by default on a fresh install so the network-level
    // request/popup interception matches the Settings switch default (true) and
    // the cosmetic/YouTube scripts in PrivacyManager.getPrivacyScripts (also
    // true). A false default here previously left network ad-blocking off while
    // the UI claimed it was enabled.
    fun isAdBlockEnabled(context: Context) = prefs(context).getBoolean(KEY_BLOCK_ADS, true)
    fun isBlockPopupsEnabled(context: Context) = prefs(context).getBoolean(KEY_BLOCK_POPUPS, true)

    fun isSaveHistoryEnabled(context: Context) = prefs(context).getBoolean(KEY_SAVE_HISTORY, true)
    fun setSaveHistoryEnabled(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply()

    fun getLanguage(context: Context) = prefs(context).getString(KEY_LANGUAGE, "system") ?: "system"
    fun setLanguage(context: Context, lang: String) = prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()

    // --- Tab-to-search keywords ---------------------------------------------
    //
    // Maps a short keyword (the first omnibox token) to a URL template containing
    // "%s" where the rest of the typed text is inserted (e.g. "yt" ->
    // "https://www.youtube.com/results?search_query=%s"). Stored as a single JSON
    // object string under [KEY_SEARCH_KEYWORDS] so a future Settings UI can read,
    // edit, and persist the full map through [getSearchKeywords]/[setSearchKeywords].
    //
    // Prefs key: "search_keywords" (JSON object: { "<keyword>": "<template>" }).
    // Keywords are stored/looked-up lower-cased; templates must be http(s).
    private const val KEY_SEARCH_KEYWORDS = "search_keywords"

    // Seeded on first read so a fresh install has useful keywords out of the box.
    // Templates intentionally use "%s"; UrlUtils.resolveKeyword URL-encodes the
    // query before substitution and validates the resulting scheme.
    private val DEFAULT_SEARCH_KEYWORDS: Map<String, String> = linkedMapOf(
        "yt" to "https://www.youtube.com/results?search_query=%s",
        "w" to "https://en.wikipedia.org/w/index.php?search=%s",
        "gh" to "https://github.com/search?q=%s",
        "maps" to "https://www.google.com/maps/search/%s"
    )

    /**
     * Returns the tab-to-search keyword map (lower-cased keys -> http(s) URL
     * templates). On first access the [DEFAULT_SEARCH_KEYWORDS] are persisted so
     * the stored value and a future Settings UI agree. Any parse failure fails
     * soft to the defaults rather than throwing.
     */
    fun getSearchKeywords(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_SEARCH_KEYWORDS, null)
        if (raw.isNullOrBlank()) {
            setSearchKeywords(context, DEFAULT_SEARCH_KEYWORDS)
            return DEFAULT_SEARCH_KEYWORDS
        }
        return try {
            val obj = org.json.JSONObject(raw)
            val out = LinkedHashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) out[k.lowercase()] = v
            }
            if (out.isEmpty()) DEFAULT_SEARCH_KEYWORDS else out
        } catch (e: Exception) {
            DEFAULT_SEARCH_KEYWORDS
        }
    }

    /**
     * Persists the keyword map as a JSON object string. Keys are normalized to
     * lower case; blank keys/templates are dropped. Safe to call from the main
     * thread (a tiny SharedPreferences write).
     */
    fun setSearchKeywords(context: Context, keywords: Map<String, String>) {
        val obj = org.json.JSONObject()
        for ((k, v) in keywords) {
            val key = k.trim().lowercase()
            val tpl = v.trim()
            if (key.isNotEmpty() && tpl.isNotEmpty()) obj.put(key, tpl)
        }
        prefs(context).edit().putString(KEY_SEARCH_KEYWORDS, obj.toString()).apply()
    }

    // Per-origin site permissions (geolocation, camera, mic, notifications).
    // Values: "allow" | "deny" | absent => prompt user.
    private fun sitePermKey(permission: String, origin: String) = "site_perm:$permission:$origin"
    fun getSitePermission(context: Context, permission: String, origin: String): String? =
        prefs(context).getString(sitePermKey(permission, origin), null)
    fun setSitePermission(context: Context, permission: String, origin: String, decision: String) =
        prefs(context).edit().putString(sitePermKey(permission, origin), decision).apply()
    fun clearSitePermission(context: Context, permission: String, origin: String) =
        prefs(context).edit().remove(sitePermKey(permission, origin)).apply()
}
