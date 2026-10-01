package com.etatech.hashiya.feature.paperdetails

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.etatech.hashiya.core.data.repository.DownloadFailure
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class PaperDetailsPdfContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val link = "https://arxiv.org/pdf/1706.03762"
    private val events = mutableListOf<String>()
    private val actions = PaperDetailsActions(
        onOpenLink = { events += "open:$it" },
        onReadPdf = { events += "read" },
        onDownloadPdf = { events += "download" },
        onCancelPdfDownload = { events += "cancel" },
        onAttachPdf = { events += "attach" },
        onRemovePdf = { events += "remove" }
    )

    private fun show(row: PdfRow) = composeRule.setContent {
        HashiyaTheme {
            PaperDetailsContent(
                uiState = PaperDetailsUiState.Loaded(
                    LibraryPaper(SamplePapers.attention, ReadingStatus.Reading),
                    PaperNotes(),
                    NotesSaveState.Idle
                ),
                actions = actions,
                pdf = row
            )
        }
    }

    private fun stored(source: PdfSource = PdfSource.Downloaded) =
        PdfRow(PdfRowState.Stored(PaperPdf(source, sizeBytes = 2_400_000, addedAt = 1_000)), link)

    @Test
    fun anAvailablePdfDownloadsOnTap() {
        show(PdfRow(PdfRowState.Available, link))

        composeRule.onNodeWithText("PDF available to download").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download PDF").performScrollTo().performClick()

        assertEquals(listOf("download"), events)
    }

    @Test
    fun anAvailablePdfCanBeAttachedFromTheMenu() {
        show(PdfRow(PdfRowState.Available, link))

        composeRule.onNodeWithTag(PDF_MENU_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Attach PDF").performClick()

        assertEquals(listOf("attach"), events)
    }

    @Test
    fun noLinkOffersAttach() {
        show(PdfRow(PdfRowState.None, null))

        composeRule.onNodeWithText("No PDF").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Attach PDF").performScrollTo().performClick()

        assertEquals(listOf("attach"), events)
    }

    @Test
    fun aDownloadCanBeCancelled() {
        show(PdfRow(PdfRowState.Downloading(bytes = 500_000, totalBytes = 2_000_000), link))

        composeRule.onNodeWithText("Cancel").performScrollTo().performClick()

        assertEquals(listOf("cancel"), events)
    }

    @Test
    fun aStoredPdfShowsItsSizeAndSourceAndOpensTheReader() {
        show(stored())

        composeRule.onNodeWithText("PDF · 2.4 MB · Downloaded").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(PDF_ROW_TAG).performScrollTo().performClick()

        assertEquals(listOf("read"), events)
    }

    @Test
    fun anAttachedPdfSaysAttached() {
        show(stored(PdfSource.Attached))

        composeRule.onNodeWithText("PDF · 2.4 MB · Attached").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun removeAsksFirst() {
        show(stored())

        composeRule.onNodeWithTag(PDF_MENU_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Remove PDF").performClick()
        composeRule.onNodeWithText("Remove this PDF?").assertIsDisplayed()
        assertEquals(emptyList<String>(), events)

        composeRule.onNodeWithText("Remove PDF").performClick()

        assertEquals(listOf("remove"), events)
    }

    @Test
    fun replaceAsksFirstThenOpensThePicker() {
        show(stored())

        composeRule.onNodeWithTag(PDF_MENU_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Replace PDF").performClick()
        composeRule.onNodeWithText("Replace this PDF?").assertIsDisplayed()
        composeRule.onNodeWithText("Replace PDF").performClick()

        assertEquals(listOf("attach"), events)
    }

    @Test
    fun cancellingTheConfirmationDoesNothing() {
        show(stored())

        composeRule.onNodeWithTag(PDF_MENU_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Remove PDF").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Remove this PDF?").assertDoesNotExist()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun theStoredMenuOpensTheLink() {
        show(stored())

        composeRule.onNodeWithTag(PDF_MENU_TAG).performScrollTo().performClick()
        composeRule.onNodeWithText("Open link in browser").performClick()

        assertEquals(listOf("open:$link"), events)
    }

    @Test
    fun aNotPdfFailureExplainsAndOffersEveryWayOut() {
        show(PdfRow(PdfRowState.Failed(DownloadFailure.NotPdf), link))

        composeRule.onNodeWithText("Couldn't get the PDF").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("This link opens a web page, not a PDF.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performScrollTo().performClick()
        composeRule.onNodeWithText("Open in browser").performScrollTo().performClick()
        composeRule.onNodeWithText("Attach PDF").performScrollTo().performClick()

        assertEquals(listOf("download", "open:$link", "attach"), events)
    }

    @Test
    fun eachFailureSaysWhy() {
        val reasons = mapOf(
            DownloadFailure.Offline to "You're offline.",
            DownloadFailure.TooLarge to "The PDF is larger than 100 MB.",
            DownloadFailure.Http to "The server didn't send the PDF."
        )
        var row by mutableStateOf(PdfRow(PdfRowState.Failed(DownloadFailure.Offline), link))
        composeRule.setContent {
            HashiyaTheme {
                PaperDetailsContent(
                    uiState = PaperDetailsUiState.Loaded(
                        LibraryPaper(SamplePapers.attention, ReadingStatus.Reading),
                        PaperNotes(),
                        NotesSaveState.Idle
                    ),
                    actions = actions,
                    pdf = row
                )
            }
        }
        reasons.forEach { (reason, text) ->
            row = PdfRow(PdfRowState.Failed(reason), link)
            composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun attachErrorsShowASnackbar() {
        composeRule.setContent {
            HashiyaTheme {
                PaperDetailsContent(
                    uiState = PaperDetailsUiState.Loaded(
                        LibraryPaper(SamplePapers.attention, ReadingStatus.Reading),
                        PaperNotes(),
                        NotesSaveState.Idle
                    ),
                    actions = actions,
                    message = PaperDetailsMessage.PdfAttachNotPdf
                )
            }
        }

        composeRule.onNodeWithText("That file isn't a PDF.").assertIsDisplayed()
    }
}
