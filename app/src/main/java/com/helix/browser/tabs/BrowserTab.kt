package com.helix.browser.tabs

import android.graphics.Bitmap
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "",
    var favicon: Bitmap? = null,
    var isIncognito: Boolean = false,
    var thumbnail: Bitmap? = null
)
