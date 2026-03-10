package com.helix.browser.tabs

import android.graphics.Bitmap
import java.util.UUID

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "",
    var url: String = "",
    var favicon: Bitmap? = null,
    var isIncognito: Boolean = false,
    var thumbnail: Bitmap? = null,
    var isPinned: Boolean = false,
    var groupId: String? = null,
    var groupName: String? = null,
    var lastAccessTime: Long = System.currentTimeMillis(),
    var isMuted: Boolean = false,
    var isSuspended: Boolean = false
)
