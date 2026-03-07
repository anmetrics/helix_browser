package com.helix.browser.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Base activity.
 *
 * Strategy: do NOT use full edge-to-edge (setDecorFitsSystemWindows=false)
 * because that breaks WebView keyboard on Android 11+.
 *
 * Instead, we color-match the status bar and nav bar to our toolbar background
 * so it looks seamless. The system handles all safe-area padding automatically.
 */
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use the official AndroidX enableEdgeToEdge API.
        // This is the ONLY reliable way to force Android 10+ to completely drop
        // all shadow scrims and overlays from the status bar and bottom gesture bar.
        // We set both to fully TRANSPARENT, so they will literally just show
        // the app's true root background color (#1A1835) with zero alterations.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
    }
}
