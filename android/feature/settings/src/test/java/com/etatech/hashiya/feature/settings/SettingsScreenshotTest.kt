package com.etatech.hashiya.feature.settings

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SettingsScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun settings() = composeRule.captureScreenshot("settings", variant, arabicText = "الإعدادات") {
        SettingsContent(
            uiState = SettingsUiState(usingUserKey = true, keyInput = "my-openalex-key", language = AppLanguage.System),
            onBack = {},
            onKeyInputChange = {},
            onSaveKey = {},
            onResetKey = {},
            onLanguageSelected = {}
        )
    }

    @Test
    fun storage() = composeRule.captureScreenshot("settings_storage", variant, arabicText = "التخزين", wholeScreen = true) {
        SettingsContent(
            uiState = SettingsUiState(
                usingUserKey = false,
                language = AppLanguage.System,
                storage = PdfStorage(downloadedBytes = 38_273_024, downloadedCount = 12, attachedBytes = 4_718_592, attachedCount = 3)
            ),
            onBack = {},
            onKeyInputChange = {},
            onSaveKey = {},
            onResetKey = {},
            onLanguageSelected = {}
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }

    @Test
    fun exportDialog() = composeRule.captureScreenshot("settings_export", variant, arabicText = "تضمين ملفات PDF", wholeScreen = true) {
        SettingsContent(
            uiState = SettingsUiState(
                language = AppLanguage.System,
                backup = BackupUiState(
                    summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000),
                    export = ExportState.Choosing(includePdfs = true)
                )
            ),
            onBack = {},
            onKeyInputChange = {},
            onSaveKey = {},
            onResetKey = {},
            onLanguageSelected = {}
        )
    }
}
