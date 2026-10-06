package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class PaperDetailsAnalyticsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)
    private val analytics = FakeAnalytics()

    /** The set of noted papers lives as long as the process, so each test uses papers no other test has edited. */
    private fun freshPaper() = SamplePapers.bert.copy(openAlexId = "https://openalex.org/W${UUID.randomUUID()}")

    private fun TestScope.viewModel(openAlexId: String): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(
            openAlexId,
            library,
            collections,
            FakeCitationRepository(),
            FakeUserPreferencesRepository(),
            FakePdfRepository(),
            backgroundScope,
            analytics
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun twoSavesOfOnePapersNotesAreOneEvent() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val viewModel = viewModel(paper.openAlexId)
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Summary, "First")
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Summary, "Second")
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.NoteEdited), analytics.events)
    }

    @Test
    fun anotherPapersNotesAreASecondEvent() = runTest {
        val first = freshPaper().also { library.save(it) }
        val second = freshPaper().also { library.save(it) }
        val firstModel = viewModel(first.openAlexId)
        val secondModel = viewModel(second.openAlexId)
        advanceUntilIdle()

        firstModel.onNoteChange(NoteSection.Summary, "One")
        advanceUntilIdle()
        secondModel.onNoteChange(NoteSection.Summary, "Two")
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.NoteEdited, AnalyticsEvent.NoteEdited), analytics.events)
    }

    @Test
    fun aFailedNotesSaveSendsNothingAndTheRetryDoes() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val viewModel = viewModel(paper.openAlexId)
        advanceUntilIdle()
        library.failOnSaveNotes = true

        viewModel.onNoteChange(NoteSection.Summary, "Unsaved")
        advanceUntilIdle()
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)

        library.failOnSaveNotes = false
        viewModel.onRetrySave()
        advanceUntilIdle()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.NoteEdited), analytics.events)
    }

    @Test
    fun noteTextNeverReachesAnEvent() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val viewModel = viewModel(paper.openAlexId)
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Summary, "See 10.1038/nature14539")
        advanceUntilIdle()

        val sent = analytics.events.joinToString { "${it.name} ${it.parameters}" }
        assertFalse(sent.contains("10.1038"))
        assertFalse(sent.contains("nature14539"))
        assertFalse(sent.contains(paper.openAlexId))
    }

    @Test
    fun aNewCollectionIsCreatedThenThePaperIsAddedToIt() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val viewModel = viewModel(paper.openAlexId)

        viewModel.onNewCollectionConfirm("Secret thesis 10.1038/nature14539")
        advanceUntilIdle()

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.CollectionCreated, AnalyticsEvent.PaperAddedToCollection),
            analytics.events
        )
        assertFalse(analytics.events.joinToString { it.parameters.toString() }.contains("thesis"))
    }

    @Test
    fun aNameClashSendsNothing() = runTest {
        val paper = freshPaper().also { library.save(it) }
        collections.create("Thesis")
        val viewModel = viewModel(paper.openAlexId)

        viewModel.onNewCollectionConfirm("Thesis")
        advanceUntilIdle()

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun tickingACollectionCountsButUntickingDoesNot() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val id = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel(paper.openAlexId)

        viewModel.onToggleCollection(id, member = true)
        advanceUntilIdle()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperAddedToCollection), analytics.events)

        viewModel.onToggleCollection(id, member = false)
        advanceUntilIdle()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PaperAddedToCollection), analytics.events)
    }

    @Test
    fun aFailedTickSendsNothing() = runTest {
        val paper = freshPaper().also { library.save(it) }
        val id = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel(paper.openAlexId)
        collections.failOnChange = true

        viewModel.onToggleCollection(id, member = true)
        advanceUntilIdle()

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }
}
