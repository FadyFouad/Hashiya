package com.etatech.hashiya.feature.settings

class FakeAppLanguageController(var language: AppLanguage = AppLanguage.System) : AppLanguageController {
    override fun current(): AppLanguage = language

    override fun set(language: AppLanguage) {
        this.language = language
    }
}
