package com.etatech.hashiya.feature.library

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeReviewPrompt
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** From 840dp a paper opens in the detail pane beside the list; narrower, it opens Details as its own screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class LibraryTwoPaneTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val repository = FakeLibraryRepository()
    private val opened = mutableListOf<String>()
    private var selectHandled = 0

    @Before
    fun savePapers() = runBlocking {
        repository.save(SamplePapers.attention)
        repository.save(SamplePapers.bert)
    }

    private fun show(window: TestWindow, selectRequest: String? = null) = show(mutableStateOf(window), selectRequest)

    private fun show(window: State<TestWindow>, selectRequest: String? = null) {
        val viewModel = LibraryViewModel(
            SavedStateHandle(),
            repository,
            FakeCollectionsRepository(repository),
            FakeCitationRepository(),
            FakeUserPreferencesRepository(),
            FakePdfRepository(),
            NoOpAnalytics,
            FakeReviewPrompt()
        )
        composeRule.setContentInWindow(window) {
            LibraryScreen(
                onGoToSearch = {},
                onAddPaper = {},
                onOpenSettings = {},
                onOpenPaper = { opened += it },
                // Stands in for Details, which this module doesn't depend on.
                detailPane = { openAlexId, _, onRemove ->
                    Text("Details of $openAlexId")
                    Button(onClick = { onRemove(openAlexId) }) { Text("Remove it") }
                },
                selectRequest = selectRequest,
                onSelectRequestHandled = { selectHandled++ },
                viewModel = viewModel
            )
        }
    }

    @Test
    fun expandedOpensThePaperInTheDetailPane() {
        show(TestWindow.Expanded)
        composeRule.onNodeWithText("No paper selected").assertIsDisplayed()

        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()

        composeRule.onNodeWithText("Details of ${SamplePapers.bert.openAlexId}").assertIsDisplayed()
        composeRule.onNode(hasText(SamplePapers.bert.title, substring = true) and isSelected()).assertExists()
        composeRule.onNode(hasText(SamplePapers.attention.title, substring = true) and isSelected()).assertDoesNotExist()
        assertEquals(emptyList<String>(), opened)
    }

    @Test
    fun removingFromThePaneRemovesWithUndoAndClosesIt() {
        show(TestWindow.Expanded)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()
        composeRule.onNodeWithText("Remove it").performClick()

        composeRule.onNodeWithText("Removed from library").assertIsDisplayed()
        composeRule.onNodeWithText("No paper selected").assertIsDisplayed()
        composeRule.onNodeWithText(SamplePapers.bert.title).assertDoesNotExist()
    }

    @Test
    fun mediumOpensDetailsAsAScreen() {
        show(TestWindow.Medium)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()

        assertEquals(listOf(SamplePapers.bert.openAlexId), opened)
        composeRule.onNodeWithText("No paper selected").assertDoesNotExist()
    }

    @Test
    fun narrowingTheWindowKeepsThePaperOpenAsAScreen() {
        val window = mutableStateOf(TestWindow.Expanded)
        show(window)
        composeRule.onNodeWithText(SamplePapers.bert.title).performClick()

        window.value = TestWindow.Medium
        composeRule.waitForIdle()

        assertEquals(listOf(SamplePapers.bert.openAlexId), opened)
        // Widening again doesn't reopen it here: the app moves the Details screen back into the pane instead.
        window.value = TestWindow.Expanded
        composeRule.onNodeWithText("No paper selected").assertIsDisplayed()
    }

    @Test
    fun aSelectRequestShowsThePaperInThePane() {
        show(TestWindow.Expanded, selectRequest = SamplePapers.attention.openAlexId)
        composeRule.onNodeWithText("Details of ${SamplePapers.attention.openAlexId}").assertIsDisplayed()
        assertEquals(1, selectHandled)
        assertEquals(emptyList<String>(), opened)
    }
}
