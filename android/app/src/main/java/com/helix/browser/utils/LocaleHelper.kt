package com.helix.browser.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    fun onAttach(context: Context): Context {
        val lang = Prefs.getLanguage(context)
        return setLocale(context, lang)
    }

    fun setLocale(context: Context, language: String): Context {
        Prefs.setLanguage(context, language)
        return updateResources(context, language)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = if (language == "system") {
            Resources.getSystem().configuration.locales.get(0)
        } else {
            val parts = language.split("-")
            if (parts.size > 1) {
                Locale(parts[0], parts[1].removePrefix("r"))
            } else {
                Locale(language)
            }
        }
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            config.setLocales(localeList)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
    }
}
