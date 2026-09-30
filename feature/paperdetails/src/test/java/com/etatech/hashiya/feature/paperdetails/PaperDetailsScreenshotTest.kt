package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperNotes
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

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
