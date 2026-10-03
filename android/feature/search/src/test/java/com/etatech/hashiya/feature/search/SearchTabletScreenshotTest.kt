package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.TABLET_QUALIFIERS
import com.etatech.hashiya.core.testing.captureScreenshot
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Search on a landscape tablet: the results with the picked paper's preview beside them. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_QUALIFIERS)
class SearchTabletScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun twoPane() = composeRule.captureScreenshot(
        "search_two_pane",
        variant,
        arabicText = "حفظ في المكتبة",
        beforeCapture = { onNodeWithText(SamplePapers.bert.abstract.orEmpty().take(40), substring = true).assertExists() }
    ) {
        SearchContent(
            uiState = SearchUiState(text = "transformer attention", sort = SearchSort.MostCited, isIdle = false, totalCount = 48210),
            papers = flowOf(
                PagingData.from(
                    listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.vit),
                    LoadStates(
                        refresh = LoadState.NotLoading(endOfPaginationReached = false),
                        prepend = LoadState.NotLoading(endOfPaginationReached = true),
                        append = LoadState.NotLoading(endOfPaginationReached = true)
                    )
                )
            ).collectAsLazyPagingItems(),
            savedIds = setOf(SamplePapers.attention.openAlexId),
            selectedItem = PaperItem(SamplePapers.bert, inLibrary = false),
            message = null,
            actions = SearchActions(),
            currentYear = 2026
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
