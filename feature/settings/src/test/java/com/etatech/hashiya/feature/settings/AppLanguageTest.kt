package com.etatech.hashiya.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun emptyTagsMeanSystem() = assertEquals(AppLanguage.System, appLanguageFromTags(""))

    @Test
    fun englishWithRegion() = assertEquals(AppLanguage.English, appLanguageFromTags("en-US"))

    @Test
    fun arabic() = assertEquals(AppLanguage.Arabic, appLanguageFromTags("ar"))

    @Test
    fun unsupportedLanguageFallsBackToSystem() = assertEquals(AppLanguage.System, appLanguageFromTags("fr-FR,en"))
}
