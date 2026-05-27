package com.example.util

import android.content.Context
import android.content.res.Configuration
import com.example.data.model.AppLanguage
import java.util.Locale

object LocaleHelper {
    fun localizedContext(base: Context, languageCode: String): Context {
        val language = AppLanguage.fromCode(languageCode)
        val localeTag = language.localeTag
        if (localeTag == null) {
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(localeTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return base.createConfigurationContext(configuration)
    }
}
