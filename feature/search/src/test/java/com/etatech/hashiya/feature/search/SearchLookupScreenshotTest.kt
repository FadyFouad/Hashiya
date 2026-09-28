package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE_QUALIFIERS)
class SearchLookupScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private fun capture(
        name: String,
        text: String,
        arabicText: String,
        lookupState: LookupUiState? = null,
        note: SearchNote? = null,
        data: PagingData<Paper> = PagingData.empty()
    ) = composeRule.captureScreenshot(name, variant, arabicText) {
        SearchContent(
            uiState = SearchUiState(text = text, isIdle = lookupState != null || text.isBlank(), totalCount = null),
            papers = flowOf(data).collectAsLazyPagingItems(),
            savedIds = emptySet(),
            selectedItem = null,
            message = null,
            actions = SearchActions(),
            lookupState = lookupState,
            note = note,
            currentYear = 2026
        )
    }

    @Test
    fun looking() = capture(
        "search_lookup_looking",
        text = "10.1038/nature14539",
        arabicText = "جارٍ البحث عن DOI",
        lookupState = LookupUiState.Looking(PaperIdentifier.Doi("10.1038/nature14539"))
    )

    @Test
    fun found() = capture(
        "search_lookup_found",
        text = "arXiv:1706.03762",
        arabicText = "حفظ في المكتبة",
        lookupState = LookupUiState.Found(SamplePapers.attention)
    )

    @Test
    fun notFound() = capture(
        "search_lookup_not_found",
        text = "1810.04805",
        arabicText = "لم يتم العثور على ورقة",
        lookupState = LookupUiState.NotFound(PaperIdentifier.Arxiv("1810.04805"), bertTitle)
    )

    @Test
    fun error() = capture(
        "search_lookup_error",
        text = "10.1038/nature14539",
        arabicText = "تعذّر الوصول إلى OpenAlex",
        lookupState = LookupUiState.Failed(SearchError.Offline)
    )

    @Test
    fun shareNote() = capture(
        "search_share_note",
        text = "Deep learning",
        arabicText = "لا يوجد DOI",
        note = SearchNote.NoIdInShare,
        data = PagingData.from(
            listOf(SamplePapers.bert, SamplePapers.vit),
            LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true)
            )
        )
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
