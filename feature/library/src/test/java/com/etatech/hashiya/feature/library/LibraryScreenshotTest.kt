package com.etatech.hashiya.feature.library

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import com.etatech.hashiya.feature.library.components.READING_STATUS_BADGE_TAG
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

    /** One paper of each status, so every badge style shows. */
    private val library = LibraryUiState.Papers(
        listOf(
            LibraryPaper(SamplePapers.attention, ReadingStatus.Reading),
            LibraryPaper(SamplePapers.bert, ReadingStatus.Read),
            LibraryPaper(SamplePapers.arabicTitled, ReadingStatus.ToRead)
        ),
        LibraryFilter(counts = mapOf(ReadingStatus.ToRead to 1, ReadingStatus.Reading to 1, ReadingStatus.Read to 1))
    )

    private fun capture(
        name: String,
        state: LibraryUiState,
        arabicText: String,
        wholeScreen: Boolean = false,
        beforeCapture: ComposeContentTestRule.() -> Unit = {}
    ) = composeRule.captureScreenshot(name, variant, arabicText, wholeScreen, beforeCapture) {
        LibraryContent(uiState = state, pendingUndo = null, actions = LibraryActions())
    }

    @Test
    fun empty() = capture("library_empty", LibraryUiState.Empty, arabicText = "لا توجد أوراق محفوظة بعد")

    // The top app bar title (library_title) is a values-ar string that appears exactly once on these screens;
    // status labels appear on both a chip and a badge, and paper titles are content, not app strings.
    @Test
    fun papers() = capture("library_papers", library, arabicText = "المكتبة")

    @Test
    fun filteredSearch() = capture(
        "library_search",
        LibraryUiState.Papers(
            listOf(LibraryPaper(SamplePapers.attention, ReadingStatus.Reading)),
            LibraryFilter(
                query = "transformer",
                status = ReadingStatus.Reading,
                counts = mapOf(ReadingStatus.ToRead to 2, ReadingStatus.Reading to 1, ReadingStatus.Read to 0)
            )
        ),
        arabicText = "الكل"
    )

    @Test
    fun noMatches() = capture(
        "library_no_matches",
        LibraryUiState.NoMatches(LibraryFilter(query = "zebra", status = ReadingStatus.Read)),
        arabicText = "لا توجد أوراق مطابقة"
    )

    @Test
    fun statusMenu() = capture("library_status_menu", library, arabicText = "المكتبة", wholeScreen = true) {
        onAllNodesWithTag(READING_STATUS_BADGE_TAG).onFirst().performClick()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
