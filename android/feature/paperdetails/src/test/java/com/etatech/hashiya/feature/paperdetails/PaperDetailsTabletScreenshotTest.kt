package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.designsystem.component.PaperCard
import com.etatech.hashiya.core.designsystem.layout.ListDetailPanes
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.TABLET_QUALIFIERS
import com.etatech.hashiya.core.testing.captureScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Details as the Library's detail pane on a landscape tablet, in the PDF row's three states. The list beside it is a
 * stand-in (this module doesn't depend on the Library): what matters is the pane's width and the grouped rows.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_QUALIFIERS)
class PaperDetailsTabletScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val link = "https://arxiv.org/pdf/1810.04805"
    private val notes = PaperNotes(summary = "Pre-training a deep bidirectional Transformer, then fine-tuning it per task.")

    private fun capture(name: String, arabicText: String, pdf: PdfRow) = composeRule.captureScreenshot(name, variant, arabicText) {
        ListDetailPanes(
            list = {
                Column {
                    listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.vit).forEach { paper ->
                        PaperCard(paper, inLibrary = true, onClick = {}, onSave = {}, selected = paper == SamplePapers.bert)
                    }
                }
            },
            detail = {
                PaperDetailsContent(
                    uiState = PaperDetailsUiState.Loaded(
                        LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                        notes,
                        NotesSaveState.Saved
                    ),
                    actions = PaperDetailsActions(),
                    pdf = pdf,
                    showBack = false
                )
            }
        )
    }

    @Test
    fun pdfAvailable() = capture("details_pane_pdf_available", "متاح للتنزيل", PdfRow(PdfRowState.Available, link))

    @Test
    fun pdfDownloaded() = capture(
        "details_pane_pdf_downloaded",
        "مُنزَّل",
        PdfRow(PdfRowState.Stored(PaperPdf(PdfSource.Downloaded, sizeBytes = 192_000, addedAt = 1_000)), link)
    )

    @Test
    fun pdfNone() = capture("details_pane_pdf_none", "لا يوجد ملف PDF", PdfRow(PdfRowState.None, null))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
