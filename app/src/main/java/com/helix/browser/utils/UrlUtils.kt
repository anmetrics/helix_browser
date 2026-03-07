package com.helix.browser.utils

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.preference.PreferenceManager

object UrlUtils {

    fun isUrl(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.startsWith("http://") ||
                trimmed.startsWith("https://") ||
                trimmed.startsWith("ftp://") ||
                (trimmed.contains(".") && !trimmed.contains(" "))
    }

    fun formatUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("file://") || trimmed.startsWith("about:") -> trimmed
            isUrl(trimmed) -> "https://$trimmed"
            else -> null ?: buildSearchQuery(trimmed, "google")
        }
    }

    fun buildSearchQuery(query: String, engine: String): String {
        val encoded = Uri.encode(query)
        return when (engine.lowercase()) {
            "bing" -> "https://www.bing.com/search?q=$encoded"
            "duckduckgo" -> "https://duckduckgo.com/?q=$encoded"
            "yahoo" -> "https://search.yahoo.com/search?p=$encoded"
            "brave" -> "https://search.brave.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }

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
            "https://www.google.com/s2/favicons?domain=${uri.host}&sz=64"
        } catch (e: Exception) {
            ""
        }
    }

    fun isSearchQuery(input: String) = !isUrl(input)
}

object Prefs {
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_HOMEPAGE = "homepage"
    private const val KEY_JAVASCRIPT = "javascript_enabled"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_DESKTOP_MODE = "desktop_mode"
    private const val KEY_BLOCK_ADS = "block_ads"
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
    fun isAdBlockEnabled(context: Context) = prefs(context).getBoolean(KEY_BLOCK_ADS, false)
}
