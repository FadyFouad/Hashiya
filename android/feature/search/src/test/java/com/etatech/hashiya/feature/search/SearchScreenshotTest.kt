package com.etatech.hashiya.feature.search

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.captureScreenshot
import java.time.Instant
import java.time.ZoneId
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
class SearchScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private val searching = SearchUiState(
        text = "transformer attention",
        sort = SearchSort.MostCited,
        years = YearFilter.Since(2015),
        isIdle = false,
        totalCount = 48210
    )

    private fun loadStates(refresh: LoadState) = LoadStates(
        refresh = refresh,
        prepend = LoadState.NotLoading(endOfPaginationReached = true),
        append = LoadState.NotLoading(endOfPaginationReached = true)
    )

    private fun capture(
        name: String,
        uiState: SearchUiState,
        data: PagingData<Paper>,
        arabicText: String,
        savedIds: Set<String> = emptySet()
    ) = composeRule.captureScreenshot(name, variant, arabicText) {
        SearchContent(
            uiState = uiState,
            papers = flowOf(data).collectAsLazyPagingItems(),
            savedIds = savedIds,
            selectedItem = null,
            message = null,
            actions = SearchActions(),
            currentYear = 2026,
            resetZone = ZoneId.of("Asia/Riyadh")
        )
    }

    @Test
    fun idle() = capture("search_idle", SearchUiState(), PagingData.empty(), arabicText = "ابحث في OpenAlex")

    @Test
    fun loading() = capture(
        "search_loading",
        searching,
        PagingData.empty(loadStates(LoadState.Loading)),
        arabicText = "الأكثر استشهادًا"
    )

    @Test
    fun results() = capture(
        "search_results",
        searching,
        // Explicit load states, as a real Pager dispatches them: without them the first page counts as still loading.
        PagingData.from(
            listOf(SamplePapers.attention, SamplePapers.bert, SamplePapers.vit),
            loadStates(LoadState.NotLoading(endOfPaginationReached = false))
        ),
        arabicText = "في المكتبة",
        savedIds = setOf(SamplePapers.attention.openAlexId)
    )

    @Test
    fun empty() = capture(
        "search_empty",
        searching.copy(openAccessOnly = true),
        PagingData.empty(loadStates(LoadState.NotLoading(endOfPaginationReached = false))),
        arabicText = "لا توجد أوراق مطابقة"
    )

    @Test
    fun offline() = capture(
        "search_offline",
        searching,
        PagingData.empty(loadStates(LoadState.Error(SearchException(SearchError.Offline)))),
        arabicText = "تعذّر الوصول إلى OpenAlex"
    )

    @Test
    fun dailyLimit() = capture(
        "search_daily_limit",
        searching,
        PagingData.empty(loadStates(LoadState.Error(SearchException(SearchError.DailyLimit(RESET_AT))))),
        arabicText = "تم بلوغ الحد اليومي للبحث"
    )

    @Test
    fun appendDailyLimit() = capture(
        "search_append_daily_limit",
        searching,
        PagingData.from(
            listOf(SamplePapers.attention, SamplePapers.bert),
            loadStates(LoadState.NotLoading(endOfPaginationReached = false))
                .copy(append = LoadState.Error(SearchException(SearchError.DailyLimit(RESET_AT))))
        ),
        arabicText = "فتح الإعدادات"
    )

    companion object {
        private val RESET_AT = Instant.parse("2026-10-06T00:00:00Z").toEpochMilli()

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
