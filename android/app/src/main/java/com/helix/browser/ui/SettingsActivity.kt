package com.helix.browser.ui

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.helix.browser.R
import com.helix.browser.engine.PrivacyManager

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
                Toast.makeText(requireContext(), "Đã xoá cache", Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("clear_cookies")?.setOnPreferenceClickListener {
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                Toast.makeText(requireContext(), "Đã xoá cookies", Toast.LENGTH_SHORT).show()
                true
            }

            findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
                startActivity(android.content.Intent(requireContext(), HistoryActivity::class.java))
                true
            }

            // Clear all browsing data
            findPreference<Preference>("clear_all_data")?.setOnPreferenceClickListener {
                PrivacyManager.clearAllBrowsingData(requireContext())
                Toast.makeText(requireContext(), "Đã xoá tất cả dữ liệu duyệt web", Toast.LENGTH_SHORT).show()
                true
            }

            // Update trackers blocked count summary
            findPreference<Preference>("trackers_blocked")?.apply {
                val count = PrivacyManager.getTrackersBlockedCount(requireContext())
                summary = "$count trình theo dõi đã bị chặn"
            }

            // Block third-party cookies listener to apply immediately
            findPreference<SwitchPreferenceCompat>("block_third_party_cookies")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val cookieManager = android.webkit.CookieManager.getInstance()
                // Apply to new WebViews going forward
                cookieManager.setAcceptCookie(true)
                true
            }
        }
    }
}
