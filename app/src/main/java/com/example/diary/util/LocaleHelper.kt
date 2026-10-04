package com.example.diary.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    private val systemLocale: Locale = Locale.getDefault()

    @Volatile
    var language: String = SYSTEM
        private set

    fun prime(language: String) {
        this.language = language
        Locale.setDefault(resolve(language))
    }

    fun resolve(language: String = this.language): Locale = when (language) {
        ZH -> Locale.SIMPLIFIED_CHINESE
        EN -> Locale.ENGLISH
        else -> systemLocale
    }

    fun wrap(base: Context): Context {
        if (language == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(resolve()))
        return base.createConfigurationContext(config)
    }
}
