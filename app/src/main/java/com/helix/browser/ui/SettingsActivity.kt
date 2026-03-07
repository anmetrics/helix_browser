package com.helix.browser.ui

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.helix.browser.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setSupportActionBar(null)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Cài đặt"
        }

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
        }
    }
}
