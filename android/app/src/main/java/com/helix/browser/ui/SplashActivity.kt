package com.helix.browser.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { false }
        val target = Intent(this, MainActivity::class.java).apply {
            data = intent.data
            action = intent.action
            val extras = intent.extras
            if (extras != null) putExtras(extras)
        }
        startActivity(target)
        finish()
    }
}
