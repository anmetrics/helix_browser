package com.helix.browser.ui

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.helix.browser.R
import com.helix.browser.engine.PrivacyManager
import com.helix.browser.utils.Prefs

class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Hook up toolbar from layout
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("clear_cache")?.setOnPreferenceClickListener {
                WebView(requireContext()).clearCache(true)
                Toast.makeText(requireContext(), getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("clear_cookies")?.setOnPreferenceClickListener {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(requireContext(), getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), HistoryActivity::class.java))
                true
            }

            // Clear all browsing data
            findPreference<Preference>("clear_all_data")?.setOnPreferenceClickListener {
                PrivacyManager.clearAllBrowsingData(requireContext())
                Toast.makeText(requireContext(), getString(R.string.all_data_cleared), Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("language_settings")?.setOnPreferenceClickListener {
                startActivityForResult(android.content.Intent(requireContext(), LanguageActivity::class.java), REQUEST_LANGUAGE)
                true
            }

            // Update trackers blocked count summary
            findPreference<Preference>("trackers_blocked")?.apply {
                val count = PrivacyManager.getTrackersBlockedCount(requireContext())
                summary = getString(R.string.trackers_blocked_summary_format, count)
            }

            // Block third-party cookies listener to apply immediately
            findPreference<SwitchPreferenceCompat>("block_third_party_cookies")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val cookieManager = android.webkit.CookieManager.getInstance()
                // Apply to new WebViews going forward
                cookieManager.setAcceptCookie(true)
                true
            }

            // Update language summary
            updateLanguageSummary()
        }

        private fun updateLanguageSummary() {
            findPreference<Preference>("language_settings")?.apply {
                val currentLangCode = Prefs.getLanguage(requireContext())
                summary = getLanguageName(currentLangCode)
            }
        }

        private fun getLanguageName(code: String): String {
            return when (code) {
                "system" -> getString(R.string.system_default)
                "en" -> "English"
                "vi" -> "Tiếng Việt"
                "ja" -> "日本語"
                "ko" -> "한국어"
                "es" -> "Español"
                "fr" -> "Français"
                "zh-rCN" -> "中文"
                "de" -> "Deutsch"
                "pt" -> "Português"
                "ru" -> "Русский"
                "ar" -> "العربية"
                "hi" -> "हिन्दी"
                "th" -> "ไทย"
                "id" -> "Bahasa Indonesia"
                else -> code
            }
        }
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_LANGUAGE && resultCode == android.app.Activity.RESULT_OK) {
                activity?.finish()
            }
        }

        companion object {
            private const val REQUEST_LANGUAGE = 1002
        }
    }
}
