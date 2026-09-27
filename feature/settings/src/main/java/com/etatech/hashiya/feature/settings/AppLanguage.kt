package com.etatech.hashiya.feature.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

enum class AppLanguage { System, English, Arabic }

/** Wraps AppCompat's per-app language so the ViewModel can be tested without Android. */
interface AppLanguageController {
    fun current(): AppLanguage

    fun set(language: AppLanguage)
}

internal fun appLanguageFromTags(tags: String): AppLanguage = when (tags.substringBefore(',').substringBefore('-')) {
    "en" -> AppLanguage.English
    "ar" -> AppLanguage.Arabic
    else -> AppLanguage.System
}

internal class AppCompatLanguageController @Inject constructor() : AppLanguageController {
    override fun current(): AppLanguage = appLanguageFromTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())

    override fun set(language: AppLanguage) {
        val locales = when (language) {
            AppLanguage.System -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.English -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.Arabic -> LocaleListCompat.forLanguageTags("ar")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppLanguageModule {
    @Binds
    abstract fun bindAppLanguageController(impl: AppCompatLanguageController): AppLanguageController
}
