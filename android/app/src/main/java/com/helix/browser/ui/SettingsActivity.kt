package com.helix.browser.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.TextView
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.helix.browser.R
import com.helix.browser.engine.HelixWebView
import com.helix.browser.engine.PrivacyManager
import com.helix.browser.utils.Prefs

class SettingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        overridePendingTransitionCompat(R.anim.slide_in_right, R.anim.fade_out)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Close button
        findViewById<android.view.View>(R.id.btnClose).setOnClickListener { finish() }

        setupSwitches()
        setupClickItems()
        setupAccessibilitySection()
        updateDynamicLabels()
    }

    override fun finish() {
        super.finish()
        overrideCloseTransitionCompat(R.anim.fade_in, R.anim.slide_out_left)
    }

    private fun setupSwitches() {
        // JavaScript
        bindSwitch(R.id.switchJavascript, "javascript_enabled", true)
        // Desktop site. Default is device-aware (tablets browse desktop by
        // default) so the switch reflects the effective state MainActivity
        // derives from this same pref + isTablet fallback. The persisted value,
        // once written, is what actually drives browsing: MainActivity seeds
        // viewModel.isDesktopMode from Prefs.isDesktopMode and re-applies it to
        // the live WebView pool on resume, so toggling this here takes effect.
        bindSwitch(R.id.switchDesktopMode, "desktop_mode", resources.getBoolean(R.bool.is_tablet))
        // Block ads
        bindSwitch(R.id.switchBlockAds, "block_ads", true)
        // Block trackers
        bindSwitch(R.id.switchBlockTrackers, "block_trackers", true)
        // Block third-party cookies. First-party cookies remain accepted
        // either way; this switch controls only the cross-site policy applied
        // per WebView in PrivacyManager.applyThirdPartyCookiePolicy().
        val cookieSwitch = bindSwitch(R.id.switchBlockCookies, "block_third_party_cookies", true)
        cookieSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("block_third_party_cookies", isChecked).apply()
            // Keep first-party cookies on; third-party policy is re-applied
            // when each tab's WebView is rebound on resume.
            CookieManager.getInstance().setAcceptCookie(true)
            PrivacyManager.applyThirdPartyCookiePolicy(this)
        }
        // Do Not Track
        bindSwitch(R.id.switchDoNotTrack, "do_not_track", true)
        // HTTPS upgrade
        bindSwitch(R.id.switchHttpsUpgrade, "https_upgrade", true)
        // HTTPS-Only mode (strict)
        bindSwitch(R.id.switchHttpsOnly, "https_only_mode", false)
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
            languageLauncher.launch(Intent(this, LanguageActivity::class.java))
        }

        // Site permissions
        findViewById<android.view.View>(R.id.settSitePermissions).setOnClickListener {
            startActivity(Intent(this, SitePermissionsActivity::class.java))
        }

        // Clear cache. Confirm first for parity with Clear all data so an
        // accidental tap cannot silently wipe cached site data.
        findViewById<android.view.View>(R.id.settClearCache).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(R.string.clear_cache_summary)
                .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                    // WebView.clearCache(true) clears the app-wide HTTP cache.
                    // It must be invoked on a WebView instance; this throwaway
                    // instance is created on the UI thread (cheap) and not
                    // retained, so it is eligible for GC immediately after.
                    WebView(this).clearCache(true)
                    Toast.makeText(this, getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Clear cookies. Confirm first: this signs the user out of every site.
        findViewById<android.view.View>(R.id.settClearCookies).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cookies)
                .setMessage(R.string.clear_cookies_summary)
                .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.removeAllCookies(null)
                    // Persist the removal so it survives process death rather
                    // than relying on the next implicit flush.
                    cookieManager.flush()
                    Toast.makeText(this, getString(R.string.cookies_cleared), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
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
        val rawEngine = prefs.getString("search_engine", "google") ?: "google"
        val engineName = when {
            rawEngine.startsWith(com.helix.browser.utils.UrlUtils.CUSTOM_PREFIX) ->
                getString(R.string.custom_search_engine)
            rawEngine == "google" -> "Google"
            rawEngine == "bing" -> "Bing"
            rawEngine == "duckduckgo" -> "DuckDuckGo"
            rawEngine == "yahoo" -> "Yahoo"
            rawEngine == "brave" -> "Brave"
            rawEngine == "yandex" -> "Yandex"
            rawEngine == "ecosia" -> "Ecosia"
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
        val engines = arrayOf("Google", "Bing", "DuckDuckGo", "Yahoo", "Brave", "Yandex", "Ecosia", getString(R.string.custom_search_engine))
        val values = arrayOf("google", "bing", "duckduckgo", "yahoo", "brave", "yandex", "ecosia", "__custom__")
        val current = prefs.getString("search_engine", "google") ?: "google"
        val checkedIndex = if (current.startsWith(com.helix.browser.utils.UrlUtils.CUSTOM_PREFIX))
            values.size - 1 else values.indexOf(current).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_engine_pref)
            .setSingleChoiceItems(engines, checkedIndex) { dialog, which ->
                if (values[which] == "__custom__") {
                    dialog.dismiss()
                    showCustomSearchEngineDialog()
                } else {
                    prefs.edit().putString("search_engine", values[which]).apply()
                    findViewById<TextView>(R.id.tvSearchEngine).text = engines[which]
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomSearchEngineDialog() {
        val current = prefs.getString("search_engine", "") ?: ""
        val existing = if (current.startsWith(com.helix.browser.utils.UrlUtils.CUSTOM_PREFIX))
            current.removePrefix(com.helix.browser.utils.UrlUtils.CUSTOM_PREFIX) else ""
        val input = android.widget.EditText(this).apply {
            setText(existing)
            hint = "https://example.com/search?q=%s"
            setSelection(text.length)
            setPadding(60, 40, 60, 40)
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(getColor(R.color.text_primary))
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_search_engine_title)
            .setMessage(R.string.custom_search_engine_hint)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val template = input.text.toString().trim()
                if (template.startsWith("http://") || template.startsWith("https://")) {
                    val value = com.helix.browser.utils.UrlUtils.CUSTOM_PREFIX + template
                    prefs.edit().putString("search_engine", value).apply()
                    findViewById<TextView>(R.id.tvSearchEngine).text =
                        getString(R.string.custom_search_engine)
                } else {
                    Toast.makeText(this, R.string.custom_search_engine_invalid, Toast.LENGTH_SHORT).show()
                }
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

    // --- Accessibility section ------------------------------------------------
    //
    // Mirrors Chrome's "Accessibility" settings: a "Force enable zoom" toggle and
    // a "Minimum font size" chooser. The hand-built activity_settings layout is
    // owned by another unit, so this section is constructed programmatically and
    // appended to the existing scroll container, reusing the same visual idiom
    // (section header + icon + title/subtitle rows, MaterialSwitch, and the
    // single-choice dialog used elsewhere). Both prefs are read/written through
    // the shared HelixWebView pref-key constants and re-applied to open tabs by
    // MainActivity.onResume() -> applySettingsFromPrefs() when Settings closes.
    private fun setupAccessibilitySection() {
        val container = findScrollContent() ?: return

        // Append a divider + section header to match the other groups.
        container.addView(buildDivider())
        container.addView(buildSectionHeader(getString(R.string.accessibility_category)))

        // Force-enable-zoom row (switch).
        container.addView(
            buildSwitchRow(
                iconRes = R.drawable.ic_text_zoom,
                title = getString(R.string.force_enable_zoom_title),
                summary = getString(R.string.force_enable_zoom_summary),
                prefKey = HelixWebView.PREF_FORCE_ZOOM,
                defaultValue = false
            )
        )

        // Minimum font size row (single-choice dialog). The value label updates
        // in place after the dialog commits a choice.
        val minFontSubtitle = TextView(this).apply {
            setTextAppearanceCompat()
        }
        val minFontRow = buildClickRow(
            iconRes = R.drawable.ic_text_zoom,
            title = getString(R.string.min_font_size_title),
            subtitleView = minFontSubtitle,
            showChevron = true
        )
        minFontSubtitle.text = minFontSizeLabel(currentMinFontSize())
        minFontRow.setOnClickListener { showMinFontSizeDialog(minFontSubtitle) }
        container.addView(minFontRow)
    }

    // The font-size choices presented to the user, paired with the persisted
    // CSS-px values from HelixWebView. Order defines the dialog item order.
    private val minFontValues = intArrayOf(
        HelixWebView.MIN_FONT_OFF,
        HelixWebView.MIN_FONT_SMALL,
        HelixWebView.MIN_FONT_MEDIUM,
        HelixWebView.MIN_FONT_LARGE,
        HelixWebView.MIN_FONT_HUGE
    )

    private fun minFontLabels(): Array<String> = arrayOf(
        getString(R.string.min_font_off),
        getString(R.string.min_font_small),
        getString(R.string.min_font_medium),
        getString(R.string.min_font_large),
        getString(R.string.min_font_huge)
    )

    private fun currentMinFontSize(): Int =
        prefs.getInt(HelixWebView.PREF_MIN_FONT_SIZE, HelixWebView.MIN_FONT_OFF)

    private fun minFontSizeLabel(value: Int): String {
        val idx = minFontValues.indexOf(value)
        val labels = minFontLabels()
        // Unknown/legacy values fall back to the "Off" label rather than crashing.
        return if (idx in labels.indices) labels[idx] else labels[0]
    }

    private fun showMinFontSizeDialog(subtitle: TextView) {
        val labels = minFontLabels()
        val current = currentMinFontSize()
        val checked = minFontValues.indexOf(current).let { if (it < 0) 0 else it }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.min_font_size_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val value = minFontValues[which]
                prefs.edit().putInt(HelixWebView.PREF_MIN_FONT_SIZE, value).apply()
                subtitle.text = labels[which]
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // Locate the vertical content LinearLayout inside the settings ScrollView
    // without depending on fragile child indices. Returns null (callers no-op)
    // if the expected hierarchy is ever changed by the layout's owning unit.
    private fun findScrollContent(): LinearLayout? {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val scroll = findFirstOfType(root, ScrollView::class.java) ?: return null
        return (0 until scroll.childCount)
            .map { scroll.getChildAt(it) }
            .firstOrNull { it is LinearLayout } as? LinearLayout
    }

    private fun <T : View> findFirstOfType(root: View?, type: Class<T>): T? {
        if (root == null) return null
        if (type.isInstance(root)) return type.cast(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstOfType(root.getChildAt(i), type)
                if (found != null) return found
            }
        }
        return null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun buildDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
        ).apply {
            marginStart = dp(20)
            marginEnd = dp(20)
            topMargin = dp(4)
        }
        setBackgroundColor(getColor(R.color.divider_color))
    }

    private fun buildSectionHeader(text: String): TextView = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setPadding(dp(20), dp(20), dp(20), dp(8))
        setTextColor(getColor(R.color.accent_purple_light))
        textSize = 12f
        letterSpacing = 0.05f
        isAllCaps = true
        setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD))
        this.text = text
    }

    // Replicates SettingsTitleStyle / SettingsSubtitleStyle for a programmatic
    // title TextView (singleLine, ellipsized).
    private fun TextView.setTitleAppearance() {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setTextColor(getColor(R.color.text_primary))
        textSize = 15f
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun TextView.setTextAppearanceCompat() {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) }
        setTextColor(getColor(R.color.text_tertiary))
        textSize = 12f
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    // Builds the leading icon + title/subtitle text block shared by every row.
    // The caller adds the trailing control (switch or chevron).
    private fun buildRowScaffold(
        iconRes: Int,
        title: String,
        subtitleView: TextView
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            minimumHeight = dp(60)
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundResource(R.drawable.bg_settings_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(iconRes)
            imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.info_blue))
        }
        row.addView(icon)

        val textContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(12)
            }
            orientation = LinearLayout.VERTICAL
        }
        val titleView = TextView(this).apply {
            setTitleAppearance()
            this.text = title
        }
        textContainer.addView(titleView)
        textContainer.addView(subtitleView)
        row.addView(textContainer)
        return row
    }

    private fun buildClickRow(
        iconRes: Int,
        title: String,
        subtitleView: TextView,
        showChevron: Boolean
    ): LinearLayout {
        val row = buildRowScaffold(iconRes, title, subtitleView)
        applyRowClickableBackground(row)
        if (showChevron) {
            val chevron = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
                setImageResource(R.drawable.ic_arrow_forward)
                imageTintList =
                    android.content.res.ColorStateList.valueOf(getColor(R.color.text_tertiary))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(chevron)
        }
        return row
    }

    private fun buildSwitchRow(
        iconRes: Int,
        title: String,
        summary: String,
        prefKey: String,
        defaultValue: Boolean
    ): LinearLayout {
        val subtitle = TextView(this).apply {
            setTextAppearanceCompat()
            text = summary
        }
        val row = buildRowScaffold(iconRes, title, subtitle)
        applyRowClickableBackground(row)
        val switch = MaterialSwitch(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isChecked = prefs.getBoolean(prefKey, defaultValue)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(prefKey, isChecked).apply()
            }
        }
        row.addView(switch)
        // Tapping the row toggles the switch, matching platform list-row behavior.
        row.setOnClickListener { switch.toggle() }
        return row
    }

    // Applies the themed selectable-item-background so rows ripple like the XML
    // rows (which use ?attr/selectableItemBackground via SettingsItemStyle).
    private fun applyRowClickableBackground(row: View) {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val ta = obtainStyledAttributes(attrs)
        try {
            row.background = ta.getDrawable(0)
        } finally {
            ta.recycle()
        }
        row.isClickable = true
        row.isFocusable = true
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

    private val languageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) recreate()
    }
}
