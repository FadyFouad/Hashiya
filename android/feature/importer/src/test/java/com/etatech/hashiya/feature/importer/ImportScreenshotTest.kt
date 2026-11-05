package com.etatech.hashiya.feature.importer

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
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
class ImportScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val job = ImportJob(
        id = 1,
        fileName = "My thesis.bib",
        state = ImportState.Ready,
        counts = ImportCounts(found = 126, matched = 98, local = 21, inLibrary = 4, repeated = 2, unreadable = 1, notChecked = 3),
        collectionName = "My thesis"
    )

    private fun capture(name: String, state: ImportUiState, arabicText: String) =
        composeRule.captureScreenshot(name, variant, arabicText) { ImportContent(state, ImportActions()) }

    // Each arabicText is a values-ar string shown exactly once on its screen.
    @Test
    fun start() = capture("import_start", ImportUiState.Start, arabicText = "الاستيراد من BibTeX أو RIS")

    @Test
    fun analyzing() =
        capture("import_analyzing", ImportUiState.Analyzing(job.copy(state = ImportState.Analyzing)), arabicText = "في OpenAlex")

    @Test
    fun ready() = capture("import_ready", ImportUiState.Ready(job, addToCollection = true), arabicText = "مطابقة في OpenAlex")

    @Test
    fun importing() = capture(
        "import_importing",
        ImportUiState.Importing(job.copy(state = ImportState.Importing, processed = 50)),
        arabicText = "يمكنك مغادرة التطبيق"
    )

    @Test
    fun done() = capture(
        "import_done",
        ImportUiState.Done(job.copy(state = ImportState.Done, imported = 119, collectionId = 4)),
        arabicText = "عرض في المكتبة"
    )

    @Test
    fun failed() = capture("import_failed", ImportUiState.Failed(ImportFailure.Unsupported), arabicText = "ليس بصيغة BibTeX")

    @Test
    fun cancelled() = capture(
        "import_cancelled",
        ImportUiState.Cancelled(job.copy(state = ImportState.Cancelled, imported = 50)),
        arabicText = "أُلغي الاستيراد"
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
