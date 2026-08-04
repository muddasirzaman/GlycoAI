package com.sugarsaathi.app

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

object LocaleHelper {

    fun wrap(context: Context, language: String): Context {
        val locale = Locale.forLanguageTag(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        return context.createConfigurationContext(config)
    }

    fun savedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
        return prefs.getString("lang", "en") ?: "en"
    }

    fun hasChosenLanguage(context: Context): Boolean {
        return context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .contains("lang")
    }

    fun saveLanguage(context: Context, language: String) {
        context.getSharedPreferences("locale_pref", Context.MODE_PRIVATE)
            .edit {
                putString("lang", language)
            }
    }
}