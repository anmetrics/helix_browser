package com.helix.browser

import android.app.Application
import com.helix.browser.data.AppDatabase
import com.helix.browser.data.BookmarkRepository
import com.helix.browser.data.HistoryRepository
import com.helix.browser.tabs.TabManager

class HelixApp : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val bookmarkRepository by lazy { BookmarkRepository(database.bookmarkDao()) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
    val tabManager by lazy { TabManager() }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: HelixApp
            private set
    }
}
