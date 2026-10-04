package com.etatech.hashiya.feature.settings

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.etatech.hashiya.core.data.backup.BackupSummary
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// PHONE_QUALIFIERS gives a full-height phone viewport; Robolectric's tiny default window would
// place the last language row below the fold, where a real performClick() touch cannot land.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: SettingsUiState) = composeRule.setContent {
        HashiyaTheme {
            SettingsContent(
                uiState = state,
                onBack = { events += "back" },
                onKeyInputChange = { events += "input:$it" },
                onSaveKey = { events += "save" },
                onResetKey = { events += "reset" },
                onLanguageSelected = { events += "language:$it" },
                onDeleteDownloadedPdfs = { events += "deletePdfs" },
                onCrashReportsChange = { events += "crashReports:$it" }
            )
        }
    }

    @Test
    fun builtInKeyStatusAndResetDisabled() {
        show(SettingsUiState(usingUserKey = false))

        composeRule.onNodeWithText("Using built-in key").assertIsDisplayed()
        composeRule.onNodeWithText("Reset to built-in").assertIsNotEnabled()
    }

    @Test
    fun saveAndResetInvokeCallbacks() {
        show(SettingsUiState(usingUserKey = true, keyInput = "abc"))

        composeRule.onNodeWithText("Using your key").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Reset to built-in").performClick()
        assertEquals(listOf("save", "reset"), events)
    }

    @Test
    fun choosingArabicInvokesCallback() {
        show(SettingsUiState())

        composeRule.onNodeWithText("العربية").performClick()
        assertEquals(listOf("language:Arabic"), events)
    }

    @Test
    fun backInvokesCallback() {
        show(SettingsUiState())

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun storageShowsBothKindsAndDeletingAsksFirst() {
        show(
            SettingsUiState(
                storage = PdfStorage(downloadedBytes = 3_145_728, downloadedCount = 2, attachedBytes = 1_048_576, attachedCount = 1)
            )
        )

        composeRule.onNodeWithText("Storage").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Downloaded PDFs", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Attached PDFs", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("Delete downloaded PDFs").performScrollTo().performClick()
        composeRule.onNodeWithText("Delete 2 downloaded PDFs? You can download them again. Attached PDFs are kept.").assertIsDisplayed()
        assertEquals(emptyList<String>(), events)

        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf("deletePdfs"), events)
    }

    @Test
    fun crashReportsSwitchIsOnAndTurnsOff() {
        show(SettingsUiState(crashReportsEnabled = true))

        composeRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Send crash reports").performScrollTo().assertIsOn()
        composeRule.onNodeWithText("Send crash reports").performClick()
        assertEquals(listOf("crashReports:false"), events)
    }

    @Test
    fun crashReportsSwitchIsOffAndTurnsOn() {
        show(SettingsUiState(crashReportsEnabled = false))

        composeRule.onNodeWithText("Send crash reports").performScrollTo().assertIsOff()
        composeRule.onNodeWithText("Send crash reports").performClick()
        assertEquals(listOf("crashReports:true"), events)
    }

    @Test
    fun privacyFooterAndPolicyLinkAreShown() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            CompositionLocalProvider(
                LocalUriHandler provides object : UriHandler {
                    override fun openUri(uri: String) {
                        opened += uri
                    }
                }
            ) {
                HashiyaTheme {
                    SettingsContent(uiState = SettingsUiState(), onBack = {
                    }, onKeyInputChange = {}, onSaveKey = {}, onResetKey = {}, onLanguageSelected = {})
                }
            }
        }

        composeRule.onNodeWithText(
            "Crash details and app errors help fix bugs. They never include your papers, notes or searches."
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(listOf("https://fadyfouad.github.io/Hashiya-Privacy-Policy/"), opened)
    }

    private fun showWithUriHandler(handler: UriHandler, locale: Locale? = null) = composeRule.setContent {
        val configuration = LocalConfiguration.current
        val effective = if (locale == null) {
            configuration
        } else {
            Configuration(configuration).apply { setLocale(locale) }
        }
        CompositionLocalProvider(LocalUriHandler provides handler, LocalConfiguration provides effective) {
            HashiyaTheme {
                SettingsContent(uiState = SettingsUiState(), onBack = {
                }, onKeyInputChange = {}, onSaveKey = {}, onResetKey = {}, onLanguageSelected = {})
            }
        }
    }

    @Test
    fun arabicOpensTheArabicSectionOfThePolicy() {
        val opened = mutableListOf<String>()
        showWithUriHandler(
            object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            },
            locale = Locale("ar")
        )

        // Only the configuration's locale is swapped, so the label still comes from the English resources.
        composeRule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(listOf("https://fadyfouad.github.io/Hashiya-Privacy-Policy/#ar"), opened)
    }

    @Test
    fun policyLinkWithNoAppToOpenItDoesNotCrash() {
        var attempts = 0
        showWithUriHandler(
            object : UriHandler {
                override fun openUri(uri: String) {
                    attempts++
                    throw IllegalArgumentException("Can't open $uri")
                }
            }
        )

        composeRule.onNodeWithText("Privacy policy").performScrollTo().performClick()
        assertEquals(1, attempts)
        composeRule.onNodeWithText("Privacy policy").assertIsDisplayed()
    }

    @Test
    fun noDownloadedPdfsHidesTheDeleteButton() {
        show(SettingsUiState(storage = PdfStorage(downloadedBytes = 0, downloadedCount = 0, attachedBytes = 1_048_576, attachedCount = 1)))

        composeRule.onNodeWithText("Attached PDFs", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Delete downloaded PDFs").assertDoesNotExist()
    }

    @Test
    fun storageIsHiddenUntilLoaded() {
        show(SettingsUiState(storage = null))

        composeRule.onNodeWithText("Storage").assertDoesNotExist()
    }

    @Test
    fun exportOpensTheDialogWithCounts() {
        var state by mutableStateOf(
            SettingsUiState(
                backup = BackupUiState(summary = BackupSummary(papers = 182, collections = 6, pdfCount = 41, pdfBytes = 238_000_000))
            )
        )
        composeRule.setContent {
            HashiyaTheme {
                SettingsContent(
                    uiState = state,
                    onBack = {},
                    onKeyInputChange = {},
                    onSaveKey = {},
                    onResetKey = {},
                    onLanguageSelected = {},
                    onExportClick = { state = state.copy(backup = state.backup.copy(export = ExportState.Choosing(includePdfs = false))) }
                )
            }
        }
        composeRule.onNodeWithText("Export library").performScrollTo().performClick()
        composeRule.onNodeWithText("182 papers · 6 collections").assertIsDisplayed()
        composeRule.onNodeWithText("Include PDFs").assertIsDisplayed()
    }

    @Test
    fun exportIsDisabledForAnEmptyLibrary() {
        show(SettingsUiState(backup = BackupUiState(summary = BackupSummary(0, 0, 0, 0))))
        composeRule.onNodeWithText("Export library").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun savingShowsProgress() {
        show(SettingsUiState(backup = BackupUiState(summary = BackupSummary(1, 0, 0, 0), export = ExportState.Saving)))
        composeRule.onNodeWithText("Saving…").performScrollTo().assertIsDisplayed()
    }
}
