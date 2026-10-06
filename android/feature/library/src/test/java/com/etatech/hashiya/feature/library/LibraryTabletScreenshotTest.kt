package com.etatech.hashiya.feature.library

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeReviewPrompt
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.ScreenshotVariant
import com.etatech.hashiya.core.testing.ScreenshotVariantRule
import com.etatech.hashiya.core.testing.TABLET_QUALIFIERS
import com.etatech.hashiya.core.testing.captureScreenshot
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Library on a landscape tablet: the list with the detail pane beside it, before a paper is picked. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TABLET_QUALIFIERS)
class LibraryTabletScreenshotTest(private val variant: ScreenshotVariant) {
    @get:Rule(order = 0)
    val variantRule = ScreenshotVariantRule(variant)

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    @Test
    fun twoPane() {
        val repository = FakeLibraryRepository()
        runBlocking {
            repository.save(SamplePapers.attention)
            repository.save(SamplePapers.bert)
            repository.save(SamplePapers.vit)
            repository.setStatus(SamplePapers.bert.openAlexId, ReadingStatus.Reading)
        }
        val viewModel = LibraryViewModel(
            SavedStateHandle(),
            repository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            FakePdfRepository(),
            NoOpAnalytics,
            FakeReviewPrompt()
        )
        composeRule.captureScreenshot(
            "library_two_pane",
            variant,
            arabicText = "لم تختر ورقة",
            beforeCapture = { onNodeWithText(SamplePapers.vit.title).assertExists() }
        ) {
            LibraryScreen(
                onGoToSearch = {},
                onAddPaper = {},
                onOpenSettings = {},
                onOpenPaper = {},
                detailPane = { _, _, _ -> },
                viewModel = viewModel
            )
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun parameters() = ScreenshotVariant.parameters()
    }
}
