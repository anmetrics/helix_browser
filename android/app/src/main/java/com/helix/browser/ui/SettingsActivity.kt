package com.helix.browser.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.helix.browser.R
import com.helix.browser.engine.PrivacyManager
import com.helix.browser.utils.Prefs

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Close button
        findViewById<android.view.View>(R.id.btnClose).setOnClickListener { finish() }

        setupSwitches()
        setupClickItems()
        updateDynamicLabels()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
    }

    private fun setupSwitches() {
        // JavaScript
        bindSwitch(R.id.switchJavascript, "javascript_enabled", true)
        // Desktop mode
        bindSwitch(R.id.switchDesktopMode, "desktop_mode", false)
        // Block ads
        bindSwitch(R.id.switchBlockAds, "block_ads", true)
        // Block trackers
        bindSwitch(R.id.switchBlockTrackers, "block_trackers", true)
        // Block third-party cookies
        val cookieSwitch = bindSwitch(R.id.switchBlockCookies, "block_third_party_cookies", true)
        cookieSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_third_party_cookies", isChecked).apply()
            CookieManager.getInstance().setAcceptCookie(true)
        }
        // Do Not Track
        bindSwitch(R.id.switchDoNotTrack, "do_not_track", true)
        // HTTPS upgrade
        bindSwitch(R.id.switchHttpsUpgrade, "https_upgrade", true)
        // Anti-fingerprinting
        bindSwitch(R.id.switchAntiFingerprinting, "anti_fingerprinting", false)
        // Block popups
        bindSwitch(R.id.switchBlockPopups, "block_popups", true)
        // Restore tabs
        bindSwitch(R.id.switchRestoreTabs, "restore_tabs", true)
        // Suspend inactive tabs
        bindSwitch(R.id.switchSuspendTabs, "suspend_inactive_tabs", false)
    }

    private fun bindSwitch(viewId: Int, prefKey: String, defaultValue: Boolean): MaterialSwitch {
        val switch = findViewById<MaterialSwitch>(viewId)
        switch.isChecked = prefs.getBoolean(prefKey, defaultValue)
        switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(prefKey, isChecked).apply()
        }
        return switch
    }

    private fun setupClickItems() {
        // Search engine
        findViewById<android.view.View>(R.id.settSearchEngine).setOnClickListener {
            showSearchEngineDialog()
        }

        // Homepage
        findViewById<android.view.View>(R.id.settHomepage).setOnClickListener {
            showHomepageDialog()
        }

        // Language
        findViewById<android.view.View>(R.id.settLanguage).setOnClickListener {
            startActivityForResult(Intent(this, LanguageActivity::class.java), REQUEST_LANGUAGE)
        }

        // Clear cache
        findViewById<android.view.View>(R.id.settClearCache).setOnClickListener {
            WebView(this).clearCache(true)
            Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
        }

        // Clear cookies
        findViewById<android.view.View>(R.id.settClearCookies).setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            Toast.makeText(this, getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show()
        }

        // Clear history
        findViewById<android.view.View>(R.id.settClearHistory).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Clear all data
        findViewById<android.view.View>(R.id.settClearAll).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_all_data)
                .setMessage(R.string.clear_all_data_summary)
                .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                    PrivacyManager.clearAllBrowsingData(this)
                    Toast.makeText(this, getString(R.string.all_data_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun updateDynamicLabels() {
        // Search engine label
        val engineName = when (prefs.getString("search_engine", "google")) {
            "google" -> "Google"
            "bing" -> "Bing"
            "duckduckgo" -> "DuckDuckGo"
            "yahoo" -> "Yahoo"
            "yandex" -> "Yandex"
            "ecosia" -> "Ecosia"
            else -> "Google"
        }
        findViewById<TextView>(R.id.tvSearchEngine).text = engineName

        // Homepage
        val homepage = prefs.getString("homepage", "https://www.google.com") ?: "https://www.google.com"
        findViewById<TextView>(R.id.tvHomepage).text = homepage

        // Language
        val langCode = Prefs.getLanguage(this)
        findViewById<TextView>(R.id.tvLanguage).text = getLanguageName(langCode)

        // Trackers blocked
        val count = PrivacyManager.getTrackersBlockedCount(this)
        findViewById<TextView>(R.id.tvTrackersBlocked).text = getString(R.string.trackers_blocked_summary_format, count)
    }

    private fun showSearchEngineDialog() {
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Yahoo", "Yandex", "Ecosia")
        val values = arrayOf("google", "bing", "duckduckgo", "yahoo", "yandex", "ecosia")
        val current = prefs.getString("search_engine", "google")
        val checkedIndex = values.indexOf(current)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_engine_pref)
            .setSingleChoiceItems(engines, checkedIndex) { dialog, which ->
                prefs.edit().putString("search_engine", values[which]).apply()
                findViewById<TextView>(R.id.tvSearchEngine).text = engines[which]
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showHomepageDialog() {
        val currentHomepage = prefs.getString("homepage", "https://www.google.com") ?: "https://www.google.com"
        val input = android.widget.EditText(this).apply {
            setText(currentHomepage)
            setSelection(text.length)
            setPadding(60, 40, 60, 40)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(getColor(R.color.text_primary))
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.homepage_pref)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newHomepage = input.text.toString().trim()
                if (newHomepage.isNotEmpty()) {
                    prefs.edit().putString("homepage", newHomepage).apply()
                    findViewById<TextView>(R.id.tvHomepage).text = newHomepage
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getLanguageName(code: String): String {
        return when (code) {
            "system" -> getString(R.string.system_default)
            "en" -> "English"
            "vi" -> "Tieng Viet"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "es" -> "Espanol"
            "fr" -> "Francais"
            "zh-rCN" -> "Chinese"
            "de" -> "Deutsch"
            "pt" -> "Portugues"
            "ru" -> "Russian"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "th" -> "Thai"
            "id" -> "Indonesian"
            else -> code
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_LANGUAGE && resultCode == RESULT_OK) {
            recreate()
        }
    }

    companion object {
        private const val REQUEST_LANGUAGE = 1002
    }
}
