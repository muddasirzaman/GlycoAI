package com.sugarsaathi.app

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    // Reads the saved language directly, before Compose starts
    fun savedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
        return prefs.getString("lang", "en") ?: "en"
    }

    fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .edit()
            .putString("lang", language)
            .apply()
    }
}