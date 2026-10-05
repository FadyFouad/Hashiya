package com.etatech.hashiya.feature.paperdetails

import android.net.Uri
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.testing.FakeAnalytics
import com.etatech.hashiya.core.testing.FakeCitationRepository
import com.etatech.hashiya.core.testing.FakeCollectionsRepository
import com.etatech.hashiya.core.testing.FakeLibraryRepository
import com.etatech.hashiya.core.testing.FakePdfRepository
import com.etatech.hashiya.core.testing.MainDispatcherRule
import com.etatech.hashiya.core.testing.SamplePapers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PaperDetailsAttachPdfTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val library = FakeLibraryRepository()
    private val pdfs = FakePdfRepository()
    private val paper = SamplePapers.bert
    private val id = paper.openAlexId
    private val uri = Uri.parse("content://com.android.providers.downloads.documents/document/42")

    private fun TestScope.viewModel(): PaperDetailsViewModel {
        val viewModel = PaperDetailsViewModel(
            id,
            library,
            FakeCollectionsRepository(library),
            FakeCitationRepository(),
            pdfs,
            backgroundScope,
            FakeAnalytics()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun attachingAPdfPassesTheFileAndSaysNothing() = runTest {
        library.save(paper)
        pdfs.setAttachResult(AttachResult.Done)
        val viewModel = viewModel()

        viewModel.attachPdf(uri)
        advanceUntilIdle()

        assertEquals(listOf(id to uri), pdfs.attaches)
        assertNull(viewModel.message.value)
    }

    @Test
    fun attachingAFileThatIsNotAPdfSaysSo() = runTest {
        library.save(paper)
        pdfs.setAttachResult(AttachResult.NotPdf)
        val viewModel = viewModel()

        viewModel.attachPdf(uri)
        advanceUntilIdle()

        assertEquals(PaperDetailsMessage.PdfAttachNotPdf, viewModel.message.value)
    }

    @Test
    fun attachingATooLargeFileSaysSo() = runTest {
        library.save(paper)
        pdfs.setAttachResult(AttachResult.TooLarge)
        val viewModel = viewModel()

        viewModel.attachPdf(uri)
        advanceUntilIdle()

        assertEquals(PaperDetailsMessage.PdfAttachTooLarge, viewModel.message.value)
    }

    @Test
    fun anUnreadableFileSaysSo() = runTest {
        library.save(paper)
        pdfs.setAttachResult(AttachResult.Unreadable)
        val viewModel = viewModel()

        viewModel.attachPdf(uri)
        advanceUntilIdle()

        assertEquals(PaperDetailsMessage.PdfAttachFailed, viewModel.message.value)
    }
}
