package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.data.repository.DownloadFailure
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
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
class PaperDetailsScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    // ViT has no abstract, so the notes start high enough to be on screen.
    private val notes = PaperNotes(
        summary = "Images split into 16x16 patches go straight into a standard Transformer.",
        researchQuestion = "Can attention alone match CNNs on image classification?"
    )

    @Test
    fun withNotes() = composeRule.captureScreenshot("details_notes", variant, arabicText = "ملاحظاتي") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Saved),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun empty() = composeRule.captureScreenshot("details_empty", variant, arabicText = "الخلاصة") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.untitled, ReadingStatus.ToRead),
                PaperNotes(),
                NotesSaveState.Idle
            ),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun saveFailed() = composeRule.captureScreenshot("details_save_failed", variant, arabicText = "تعذّر الحفظ") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Failed),
            actions = PaperDetailsActions()
        )
    }

    private val thesis = PaperCollection(1, "Thesis", 2)
    private val chapter = PaperCollection(2, "الفصل الثاني", 1)

    @Test
    fun inCollections() = composeRule.captureScreenshot("details_collections", variant, arabicText = "المجموعات") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.vit, ReadingStatus.Reading),
                notes,
                NotesSaveState.Idle,
                listOf(chapter, thesis),
                setOf(chapter.id, thesis.id)
            ),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun checklist() = composeRule.captureScreenshot(
        "details_checklist",
        variant,
        arabicText = "مجموعة جديدة",
        wholeScreen = true,
        beforeCapture = { onNodeWithTag(COLLECTIONS_ROW_TAG).performClick() }
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.vit, ReadingStatus.Reading),
                notes,
                NotesSaveState.Idle,
                listOf(chapter, thesis),
                setOf(thesis.id)
            ),
            actions = PaperDetailsActions()
        )
    }

    @Test
    fun emptyChecklist() = composeRule.captureScreenshot(
        "details_checklist_empty",
        variant,
        arabicText = "اجمع الأوراق لفصل أو مقرر أو مشروع.",
        wholeScreen = true,
        beforeCapture = { onNodeWithTag(COLLECTIONS_ROW_TAG).performClick() }
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(LibraryPaper(SamplePapers.vit, ReadingStatus.Reading), notes, NotesSaveState.Idle),
            actions = PaperDetailsActions()
        )
    }

    private val link = "https://arxiv.org/pdf/1706.03762"
    private val attention = LibraryPaper(SamplePapers.attention, ReadingStatus.Reading)

    @Test
    fun pdfAvailable() = composeRule.captureScreenshot(
        "details_pdf_available",
        variant,
        arabicText = "ملف PDF متاح للتنزيل"
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(attention, PaperNotes(), NotesSaveState.Idle),
            actions = PaperDetailsActions(),
            pdf = PdfRow(PdfRowState.Available, link)
        )
    }

    @Test
    fun pdfDownloading() = composeRule.captureScreenshot("details_pdf_downloading", variant, arabicText = "إلغاء") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(attention, PaperNotes(), NotesSaveState.Idle),
            actions = PaperDetailsActions(),
            pdf = PdfRow(PdfRowState.Downloading(bytes = 1_200_000, totalBytes = 2_400_000), link)
        )
    }

    // The stored line's own word: "ملف PDF" alone would also match the row's label.
    @Test
    fun pdfStored() = composeRule.captureScreenshot("details_pdf_stored", variant, arabicText = "مُنزَّل") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(attention, PaperNotes(), NotesSaveState.Idle),
            actions = PaperDetailsActions(),
            pdf = PdfRow(PdfRowState.Stored(PaperPdf(PdfSource.Downloaded, sizeBytes = 2_400_000, addedAt = 1_000)), link)
        )
    }

    @Test
    fun pdfNotAPdf() = composeRule.captureScreenshot(
        "details_pdf_not_pdf",
        variant,
        arabicText = "يفتح هذا الرابط صفحة ويب وليس ملف PDF."
    ) {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(attention, PaperNotes(), NotesSaveState.Idle),
            actions = PaperDetailsActions(),
            pdf = PdfRow(PdfRowState.Failed(DownloadFailure.NotPdf), link)
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
