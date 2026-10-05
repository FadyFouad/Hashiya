package com.etatech.hashiya.feature.reader

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.PdfOrigin
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import com.etatech.hashiya.feature.reader.pdf.PdfPageSource
import com.etatech.hashiya.feature.reader.pdf.PdfPageSourceFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Robolectric for android.graphics.Bitmap and android.net.Uri. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val pdfs = FakePdfRepository()
    private val analytics = FakeAnalytics()
    private val paper = SamplePapers.attention
    private val id = paper.openAlexId
    private val storedPdf = PaperPdf(PdfSource.Downloaded, sizeBytes = 2_400_000, addedAt = 0, lastPage = 7)

    /** Every source the factory opened, in order. */
    private val opened = mutableListOf<FakePdfPageSource>()

    private suspend fun savedWithPdf() {
        library.save(paper)
        pdfs.setPdf(id, storedPdf)
    }

    /** The application scope is the test's backgroundScope: it outlives viewModelScope, like the real one. */
    private fun TestScope.viewModel(
        paperId: String = id,
        open: suspend (File) -> PdfPageSource = { FakePdfPageSource(pageCount = 300).also(opened::add) }
    ) = ReaderViewModel(
        SavedStateHandle(mapOf(ARG_OPEN_ALEX_ID to paperId)),
        pdfs,
        library,
        PdfPageSourceFactory { file -> open(file) },
        backgroundScope,
        analytics
    )

    private fun ReaderViewModel.ready(): ReaderState.Ready =
        state.value as? ReaderState.Ready ?: error("Expected Ready but was ${state.value}")

    @Test
    fun opensOnTheStoredLastPage() = runTest {
        savedWithPdf()

        val viewModel = viewModel()
        advanceUntilIdle()

        val ready = viewModel.ready()
        assertEquals(paper.title, ready.title)
        assertEquals(300, ready.pageCount)
        assertEquals(7, ready.startPage)
        assertEquals(800f / 600f, ready.pageAspectRatios.first(), 0.001f)
    }

    @Test
    fun aFileThatCantBeOpenedShowsCantOpen() = runTest {
        savedWithPdf()

        val viewModel = viewModel(open = { throw IOException("password-protected") })
        advanceUntilIdle()

        assertEquals(ReaderState.CantOpen(paper.title), viewModel.state.value)
    }

    @Test
    fun onlyPagesNearTheViewportAreRendered() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onViewport(firstVisible = 10, lastVisible = 11, widthPx = 100, zoom = 1f)
        advanceUntilIdle()
        val first = viewModel.pages.value
        // The visible pages and two on each side, visible ones first.
        assertEquals((8..13).toSet(), first.keys)
        assertEquals(listOf(10, 11, 8, 9, 12, 13), opened.single().rendered.map { it.first })

        viewModel.onViewport(firstVisible = 50, lastVisible = 51, widthPx = 100, zoom = 1f)
        advanceUntilIdle()

        assertEquals((48..53).toSet(), viewModel.pages.value.keys)
        // Pages that left the window are released, so memory stays flat on a long PDF.
        assertTrue(first.values.all { it.isRecycled })
        assertEquals(12, opened.single().rendered.size)
    }

    @Test
    fun theWindowStopsAtTheFirstAndLastPage() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onViewport(firstVisible = 0, lastVisible = 0, widthPx = 100, zoom = 1f)
        advanceUntilIdle()
        assertEquals((0..2).toSet(), viewModel.pages.value.keys)

        viewModel.onViewport(firstVisible = 299, lastVisible = 299, widthPx = 100, zoom = 1f)
        advanceUntilIdle()
        assertEquals((297..299).toSet(), viewModel.pages.value.keys)
    }

    @Test
    fun zoomRendersTheVisiblePagesSharperAndTheirNeighboursAtScreenWidth() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onViewport(firstVisible = 10, lastVisible = 10, widthPx = 100, zoom = 1f)
        advanceUntilIdle()

        viewModel.onViewport(firstVisible = 10, lastVisible = 10, widthPx = 100, zoom = 2f)
        advanceUntilIdle()

        assertEquals(200, viewModel.pages.value.getValue(10).width)
        assertEquals(100, viewModel.pages.value.getValue(9).width)
        // Only the visible page renders again; its neighbours already have the right width.
        assertEquals(listOf(10 to 200), opened.single().rendered.drop(5))
    }

    @Test
    fun theLastPageIsSavedOneSecondAfterScrollingStops() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onPageChanged(8)
        viewModel.onPageChanged(9)
        advanceTimeBy(LAST_PAGE_SAVE_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(emptyList<Pair<String, Int>>(), pdfs.lastPages)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(id to 9), pdfs.lastPages)
    }

    @Test
    fun theStoredPageIsNotWrittenAgain() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()

        // The list reports the page it opened on.
        viewModel.onPageChanged(7)
        advanceUntilIdle()

        assertEquals(emptyList<Pair<String, Int>>(), pdfs.lastPages)
    }

    @Test
    fun stoppingSavesTheCurrentPageAtOnce() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPageChanged(12)

        viewModel.onStop()
        // runCurrent, not advanceUntilIdle: the application scope here is backgroundScope, whose work advanceUntilIdle skips.
        runCurrent()

        assertEquals(listOf(id to 12), pdfs.lastPages)
    }

    @Test
    fun notesTypedInTheSheetAreSavedAfterThePause() = runTest {
        savedWithPdf()
        library.saveNotes(id, PaperNotes(summary = "Transformers"))
        library.notesSaves.clear()
        val viewModel = viewModel()
        advanceUntilIdle()
        assertEquals(PaperNotes(summary = "Transformers"), viewModel.notes.value)

        viewModel.onNoteChange(NoteSection.Method, "Self-attention only")
        advanceUntilIdle()

        assertEquals(listOf(id to PaperNotes(summary = "Transformers", method = "Self-attention only")), library.notesSaves)
    }

    @Test
    fun backSavesTypedNotesBeforeLeaving() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Thoughts, "Cite in chapter 2")

        viewModel.onBack()
        runCurrent()

        assertEquals(listOf(id to PaperNotes(thoughts = "Cite in chapter 2")), library.notesSaves)
        assertEquals(ReaderExit.Back, viewModel.exit.value)
    }

    @Test
    fun aFailedNotesSaveKeepsTheReaderOpen() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        library.failOnSaveNotes = true
        viewModel.onNoteChange(NoteSection.Thoughts, "Not saved")

        viewModel.onBack()
        runCurrent()

        assertNull(viewModel.exit.value)
        assertEquals(ReaderMessage.NotesSaveFailed, viewModel.message.value)
    }

    @Test
    fun aPdfOpenedByReplaceAfterCantOpenIsCountedAsAttached() = runTest {
        savedWithPdf()
        var attempts = 0
        val viewModel = viewModel(open = { if (attempts++ == 0) throw IOException("damaged") else FakePdfPageSource(3) })
        advanceUntilIdle()
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
        pdfs.setAttachResult(AttachResult.Done)

        viewModel.onReplace(Uri.parse("content://downloads/paper.pdf"))
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PdfOpened(PdfOrigin.Attached)), analytics.events)
    }

    @Test
    fun replacingAPdfThatAlreadyOpenedAddsNoSecondEvent() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        pdfs.setAttachResult(AttachResult.Done)

        viewModel.onReplace(Uri.parse("content://downloads/paper.pdf"))
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PdfOpened(PdfOrigin.Downloaded)), analytics.events)
    }

    @Test
    fun replacingAnUnreadableFileOpensTheNewOne() = runTest {
        savedWithPdf()
        var attempts = 0
        val viewModel = viewModel(open = { if (attempts++ == 0) throw IOException("damaged") else FakePdfPageSource(3) })
        advanceUntilIdle()
        pdfs.setAttachResult(AttachResult.Done)

        viewModel.onReplace(Uri.parse("content://downloads/paper.pdf"))
        advanceUntilIdle()

        assertEquals(3, viewModel.ready().pageCount)
        assertEquals(0, viewModel.ready().startPage)
    }

    @Test
    fun replacingWithAFileThatIsntAPdfSaysSo() = runTest {
        savedWithPdf()
        val viewModel = viewModel(open = { throw IOException("damaged") })
        advanceUntilIdle()
        pdfs.setAttachResult(AttachResult.NotPdf)

        viewModel.onReplace(Uri.parse("content://downloads/page.html"))
        advanceUntilIdle()

        assertEquals(ReaderMessage.NotPdf, viewModel.message.value)
        assertEquals(ReaderState.CantOpen(paper.title), viewModel.state.value)
    }

    @Test
    fun removingThePdfClosesTheReader() = runTest {
        savedWithPdf()
        val viewModel = viewModel(open = { throw IOException("damaged") })
        advanceUntilIdle()

        viewModel.onRemovePdf()
        advanceUntilIdle()

        assertEquals(listOf(id), pdfs.removals)
        assertEquals(ReaderExit.Closed, viewModel.exit.value)
    }

    @Test
    fun removingThePdfSavesTypedNotesFirst() = runTest {
        savedWithPdf()
        val viewModel = viewModel(open = { throw IOException("damaged") })
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Thoughts, "Find a clean copy")

        viewModel.onRemovePdf()
        runCurrent()

        assertEquals(listOf(id to PaperNotes(thoughts = "Find a clean copy")), library.notesSaves)
        assertEquals(listOf(id), pdfs.removals)
        assertEquals(ReaderExit.Closed, viewModel.exit.value)
    }

    @Test
    fun aPdfRemovedElsewhereClosesTheReader() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()

        pdfs.setPdf(id, null)
        advanceUntilIdle()

        assertEquals(ReaderExit.Closed, viewModel.exit.value)
    }

    @Test
    fun aPaperWithoutAPdfClosesAtOnce() = runTest {
        library.save(paper)

        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(ReaderExit.Closed, viewModel.exit.value)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun clearingTheViewModelClosesTheFileAndReleasesItsPages() = runTest {
        savedWithPdf()
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onViewport(firstVisible = 0, lastVisible = 0, widthPx = 100, zoom = 1f)
        advanceUntilIdle()
        val pages = viewModel.pages.value

        ViewModelStore().apply { put("reader", viewModel) }.clear()

        assertTrue(opened.single().closed)
        assertTrue(pages.values.all { it.isRecycled })
        assertFalse(pages.isEmpty())
    }

    @Test
    fun readingTheFileSendsPdfOpenedWithItsSourceOnce() = runTest {
        savedWithPdf()

        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onPageChanged(3)
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PdfOpened(PdfOrigin.Downloaded)), analytics.events)
    }

    @Test
    fun anAttachedFileIsOpenedAsAttached() = runTest {
        library.save(paper)
        pdfs.setPdf(id, storedPdf.copy(source = PdfSource.Attached))

        viewModel()
        advanceUntilIdle()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.PdfOpened(PdfOrigin.Attached)), analytics.events)
    }

    @Test
    fun aFileThatCantBeOpenedSendsNothing() = runTest {
        savedWithPdf()

        viewModel(open = { throw IOException("password-protected") })
        advanceUntilIdle()

        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun notesTypedInTheSheetAreOneEventWithNoText() = runTest {
        val noted = paper.copy(openAlexId = "https://openalex.org/W${UUID.randomUUID()}")
        library.save(noted)
        pdfs.setPdf(noted.openAlexId, storedPdf)
        val viewModel = viewModel(paperId = noted.openAlexId)
        advanceUntilIdle()

        viewModel.onNoteChange(NoteSection.Summary, "See 10.1038/nature14539")
        advanceUntilIdle()
        viewModel.onNoteChange(NoteSection.Summary, "See 10.1038/nature14539 again")
        advanceUntilIdle()

        assertEquals(
            listOf<AnalyticsEvent>(AnalyticsEvent.PdfOpened(PdfOrigin.Downloaded), AnalyticsEvent.NoteEdited),
            analytics.events
        )
        assertFalse(analytics.events.joinToString { it.parameters.toString() }.contains("nature14539"))
    }
}
