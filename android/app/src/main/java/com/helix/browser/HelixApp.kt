package com.helix.browser

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.helix.browser.data.AppDatabase
import com.helix.browser.data.BookmarkRepository
import com.helix.browser.data.HistoryRepository
import com.helix.browser.billing.BillingManager
import com.helix.browser.tabs.TabManager

class HelixApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val bookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
    val tabManager by lazy { TabManager() }
    val billingManager by lazy { BillingManager.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Helix has a single dark brand palette and no light design. Lock the
        // app to night mode so the DayNight theme always resolves to the dark
        // Material defaults; otherwise, on a device set to Light mode the
        // auto-generated Material colors (dialogs, menus, switches) flip light
        // and clash with the hardcoded-dark custom colors.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Install crash handler FIRST so any later init failure is captured.
        HelixCrashHandler.install(this)
        // Expose web content to desktop Chrome (chrome://inspect) in debug builds
        // only. Never enable renderer inspection in release — it would let a
        // connected machine read/drive any page the user loads.
        if (BuildConfig.DEBUG) {
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
        // Init billing early so subscription status is ready
        billingManager
    }

    companion object {
        lateinit var instance: HelixApp
            private set
    }
}
