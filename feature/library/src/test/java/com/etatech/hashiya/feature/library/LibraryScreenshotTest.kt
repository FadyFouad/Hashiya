package com.etatech.hashiya.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
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
class LibraryScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private fun capture(name: String, state: LibraryUiState, arabicText: String) =
        composeRule.captureScreenshot(name, variant, arabicText) {
            LibraryContent(
                uiState = state,
                selectedPaper = null,
                pendingUndo = null,
                onPaperClick = {},
                onDismissPreview = {},
                onRemove = {},
                onUndo = {},
                onUndoDismissed = {},
                onGoToSearch = {},
                onOpenSettings = {},
                onOpenDoi = {}
            )
        }

    @Test
    fun empty() = capture("library_empty", LibraryUiState.Empty, arabicText = "لا توجد أوراق محفوظة بعد")

    @Test
    fun papers() = capture(
        "library_papers",
        LibraryUiState.Papers(listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.arabicTitled)),
        // "وآخرون" ("et al.") shows on both the attention and bert rows here (each has multiple
        // authors), so it fails the single-match arabicText check. The Arabic-titled paper's own
        // title is paper CONTENT (always Arabic regardless of the UI locale), so it can't prove the
        // app's own res/values-ar strings rendered. Use the top app bar title (library_title,
        // "المكتبة"), a values-ar resource string that appears exactly once on this screen.
        arabicText = "المكتبة"
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
