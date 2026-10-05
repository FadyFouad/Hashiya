package com.etatech.hashiya.feature.library

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Keyboard and mouse: Ctrl+F, the right-click menu, and Back closing the detail pane. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class LibraryInputTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repository = FakeLibraryRepository()
    private var findHandled = 0

    @Before
    fun savePapers() = runBlocking {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
    }

    private fun show(window: TestWindow, findRequested: Boolean = false) {
        val viewModel = LibraryViewModel(
            SavedStateHandle(),
            repository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            FakePdfRepository(),
            NoOpAnalytics
        )
        composeRule.setContentInWindow(window) {
            LibraryScreen(
                onGoToSearch = {},
                onAddPaper = {},
                onOpenSettings = {},
                onOpenPaper = {},
                detailPane = { openAlexId, _, _ -> Text("Details of $openAlexId") },
                findRequested = findRequested,
                onFindHandled = { findHandled++ },
                viewModel = viewModel
            )
        }
    }

    @Test
    fun ctrlFFocusesTheSearchField() {
        show(TestWindow.Compact, findRequested = true)
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals(1, findHandled)
    }

    @Test
    fun rightClickSetsTheStatus() {
        show(TestWindow.Compact)
        composeRule.onNodeWithText(SamplePapers.bert.title).performMouseInput { rightClick() }
        composeRule.onNodeWithText("Read").performClick()
        composeRule.waitForIdle()

        val status = runBlocking { repository.observePaper(SamplePapers.bert.openAlexId).first()?.status }
        assertEquals(ReadingStatus.Read, status)
    }

    @Test
    fun rightClickRemovesWithUndo() {
        show(TestWindow.Compact)
        composeRule.onNodeWithText(SamplePapers.bert.title).performMouseInput { rightClick() }
        composeRule.onNodeWithText("Remove from library").performClick()

        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.onNodeWithText(SamplePapers.bert.title).assertDoesNotExist()
    }

    @Test
    fun backClosesTheDetailPaneFirst() {
        show(TestWindow.Expanded)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        composeRule.onNodeWithText("Details of ${SamplePapers.bert.openAlexId}").assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("No paper selected").assertIsDisplayed()
    }
}
