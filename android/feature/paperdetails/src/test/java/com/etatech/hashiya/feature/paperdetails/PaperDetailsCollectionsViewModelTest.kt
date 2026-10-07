package com.etatech.hashiya.feature.paperdetails

import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.FakeUserPreferencesRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PaperDetailsCollectionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val collections = FakeCollectionsRepository(library)
    private val citations = FakeCitationRepository()
    private val preferences = FakeUserPreferencesRepository()
    private val analytics = FakeAnalytics()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId

    private fun TestScope.viewModel(): PaperDetailsViewModel {
        val viewModel =
            PaperDetailsViewModel(id, library, collections, citations, preferences, FakePdfRepository(), backgroundScope, analytics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    private fun PaperDetailsViewModel.loaded() = uiState.value as PaperDetailsUiState.Loaded

    @Test
    fun loadedStateCarriesCollectionsAndMembership() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val b = (collections.create("B") as CollectionResult.Done).id
        collections.setMembership(b, id, true)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(PaperCollection(a, "A", 0), PaperCollection(b, "B", 1)), viewModel.loaded().collections)
        assertEquals(setOf(b), viewModel.loaded().memberOf)
    }

    @Test
    fun aNewCollectionStillGetsThePaperWhenDetailsClosesFirst() = runTest {
        library.save(paper)
        val viewModel = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        collections.membershipGate = gate

        viewModel.onNewCollectionConfirm("Thesis")
        runCurrent()
        // The collection exists but the paper isn't in it yet, and the person leaves Details.
        viewModel.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        val thesis = collections.observeCollections().first().single { it.name == "Thesis" }
        assertEquals(setOf(thesis.id), collections.observeCollectionIds(id).first())
    }

    @Test
    fun aTickStillAppliesWhenDetailsClosesFirst() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel()
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        collections.membershipGate = gate

        viewModel.onToggleCollection(a, member = true)
        runCurrent()
        viewModel.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(setOf(a), collections.observeCollectionIds(id).first())
    }

    @Test
    fun togglingChangesMembership() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel()

        viewModel.onToggleCollection(a, member = true)
        advanceUntilIdle()
        assertEquals(setOf(a), viewModel.loaded().memberOf)

        viewModel.onToggleCollection(a, member = false)
        advanceUntilIdle()
        assertEquals(emptySet<Long>(), viewModel.loaded().memberOf)
    }

    @Test
    fun aFailedToggleKeepsTheStoredStateAndSaysSo() = runTest {
        library.save(paper)
        val a = (collections.create("A") as CollectionResult.Done).id
        val viewModel = viewModel()
        collections.failOnChange = true

        viewModel.onToggleCollection(a, member = true)
        advanceUntilIdle()

        assertEquals(emptySet<Long>(), viewModel.loaded().memberOf)
        assertEquals(PaperDetailsMessage.CollectionsUpdateFailed, viewModel.message.value)
    }

    @Test
    fun newCollectionIsCreatedWithThePaperInIt() = runTest {
        library.save(paper)
        collections.create("Thesis")
        val viewModel = viewModel()

        viewModel.onNewCollection()
        assertEquals(NewCollectionDialog(), viewModel.newCollectionDialog.value)
        viewModel.onNewCollectionConfirm("thesis")
        advanceUntilIdle()
        assertEquals(NewCollectionDialog(nameTaken = true), viewModel.newCollectionDialog.value)
        viewModel.onNewCollectionNameEdited()
        assertEquals(NewCollectionDialog(), viewModel.newCollectionDialog.value)

        viewModel.onNewCollectionConfirm("Chapter 2")
        advanceUntilIdle()
        assertNull(viewModel.newCollectionDialog.value)
        val chapter = viewModel.loaded().collections.first { it.name == "Chapter 2" }
        assertEquals(setOf(chapter.id), viewModel.loaded().memberOf)
    }

    @Test
    fun aFailedNewCollectionClosesTheDialogAndSaysSo() = runTest {
        library.save(paper)
        val viewModel = viewModel()
        collections.failOnChange = true

        viewModel.onNewCollection()
        viewModel.onNewCollectionConfirm("Thesis")
        advanceUntilIdle()

        assertNull(viewModel.newCollectionDialog.value)
        assertEquals(emptyList<PaperCollection>(), viewModel.loaded().collections)
        assertEquals(PaperDetailsMessage.CollectionsUpdateFailed, viewModel.message.value)
    }

    @Test
    fun copyCitationHandsTheEntryAndRemembersTheStyle() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "<the entries text>")
        val viewModel = viewModel()

        viewModel.onCopyCitation(CitationStyle.Ieee)
        advanceUntilIdle()
        assertEquals(
            CopiedCitation(CitationStyle.Ieee, "<the entries text>", "<i><the entries text></i>", complete = true),
            viewModel.copied.value
        )
        assertEquals(CitationStyle.Ieee, preferences.citationStyle.first())
        assertEquals(listOf(CitationStyle.Ieee), citations.styles)

        viewModel.onCopyHandled(PaperDetailsMessage.IeeeCopied)
        assertNull(viewModel.copied.value)
        assertEquals(PaperDetailsMessage.IeeeCopied, viewModel.message.value)
    }

    @Test
    fun copyingBibTeXHasNoRichText() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "@inproceedings{devlin2019bert,\n}\n")
        val viewModel = viewModel()

        viewModel.onCopyCitation(CitationStyle.Bibtex)
        advanceUntilIdle()

        assertEquals(
            CopiedCitation(CitationStyle.Bibtex, "@inproceedings{devlin2019bert,\n}\n", null, complete = true),
            viewModel.copied.value
        )
        assertEquals(CitationStyle.Bibtex, preferences.citationStyle.first())
    }

    @Test
    fun theCitationIsCopiedEvenWhenTheStyleCannotBeRemembered() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "<the entries text>")
        preferences.failOnSetCitationStyle = IOException("datastore")
        val viewModel = viewModel()

        viewModel.onCopyCitation(CitationStyle.Ieee)
        advanceUntilIdle()

        assertEquals(
            CopiedCitation(CitationStyle.Ieee, "<the entries text>", "<i><the entries text></i>", complete = true),
            viewModel.copied.value
        )
        assertNull(viewModel.message.value)
        assertEquals(CitationStyle.Apa, preferences.citationStyle.first())
    }

    @Test
    fun anIncompleteEntryIsStillCopied() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "@misc{k,\n}\n")
        citations.complete = false
        val viewModel = viewModel()

        viewModel.onCopyCitation(CitationStyle.Bibtex)
        advanceUntilIdle()

        assertEquals(CopiedCitation(CitationStyle.Bibtex, "@misc{k,\n}\n", null, complete = false), viewModel.copied.value)
    }

    @Test
    fun copyWithNothingToConfirmShowsNoMessage() = runTest {
        library.save(paper)
        citations.entries = mapOf(id to "@misc{k,\n}\n")
        val viewModel = viewModel()
        viewModel.onCopyCitation(CitationStyle.Bibtex)
        advanceUntilIdle()

        viewModel.onCopyHandled(null)
        assertNull(viewModel.copied.value)
        assertNull(viewModel.message.value)
    }

    @Test
    fun aFailedCopySaysSo() = runTest {
        library.save(paper)
        citations.failure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onCopyCitation(CitationStyle.Bibtex)
        advanceUntilIdle()

        assertNull(viewModel.copied.value)
        assertEquals(PaperDetailsMessage.CopyFailed, viewModel.message.value)
    }
}
