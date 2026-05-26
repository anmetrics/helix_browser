package com.helix.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrivacyManagerTest {

    // Tracker detection lives in PrivacyManager.isTracker; that path uses
    // android.net.Uri.parse so it requires instrumentation/Robolectric to
    // exercise. Here we cover the truly pure helpers.

    @Test fun `upgrades http to https`() {
        assertEquals("https://example.com/x", PrivacyManager.upgradeToHttps("http://example.com/x"))
    }

    @Test fun `leaves https untouched`() {
        assertNull(PrivacyManager.upgradeToHttps("https://example.com"))
    }

    @Test fun `leaves unknown scheme untouched`() {
        assertNull(PrivacyManager.upgradeToHttps("ftp://example.com"))
    }

    @Test fun `only replaces first occurrence`() {
        // The scheme upgrade must not touch a literal http:// appearing inside
        // a query parameter — only the leading scheme is rewritten.
        val result = PrivacyManager.upgradeToHttps("http://x.test/?u=http://y.test")
        assertEquals("https://x.test/?u=http://y.test", result)
    }
}
