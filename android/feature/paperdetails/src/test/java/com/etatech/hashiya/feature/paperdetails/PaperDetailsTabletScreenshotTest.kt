package com.etatech.hashiya.feature.paperdetails

import androidx.compose.ui.test.junit4.createComposeRule
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
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

/** Details as a screen on a landscape tablet (opened from Search): a centered column of at most 840dp. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_QUALIFIERS)
class PaperDetailsTabletScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun wide() = composeRule.captureScreenshot("details_wide", variant, arabicText = "ملاحظاتي") {
        PaperDetailsContent(
            uiState = PaperDetailsUiState.Loaded(
                LibraryPaper(SamplePapers.bert, ReadingStatus.Reading),
                PaperNotes(summary = "Pre-training a deep bidirectional Transformer, then fine-tuning it per task."),
                NotesSaveState.Saved
            ),
            actions = PaperDetailsActions()
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
