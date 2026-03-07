package com.helix.browser.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Base activity that enables edge-to-edge rendering on all screens,
 * so no toolbar/navbar overlaps the Android system bars.
 *
 * Each layout should use:
 *  - `android:fitsSystemWindows="true"` on AppBarLayout → handles status bar padding automatically
 *  - For custom bottom bars, use a navBarSpace View + WindowInsetsCompat (see MainActivity)
 */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw content behind status bar & nav bar, let each layout handle its own insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
