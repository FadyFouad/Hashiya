package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.NotedPapers
import com.etatech.hashiya.core.analytics.PdfOrigin
import com.etatech.hashiya.core.data.di.ApplicationScope
import com.etatech.hashiya.core.data.notes.NotesEditor
import com.etatech.hashiya.core.data.repository.AttachResult
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.PdfRepository
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.feature.reader.pdf.PdfPageSource
import com.etatech.hashiya.feature.reader.pdf.PdfPageSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The route's argument: ReaderRoute.openAlexId. */
internal const val ARG_OPEN_ALEX_ID = "openAlexId"

internal const val LAST_PAGE_SAVE_DEBOUNCE_MS = 1_000L

/** Pages kept rendered on each side of the visible ones. */
internal const val RENDER_MARGIN = 2

/** Zoom beyond this scales the bitmap instead of rendering a larger one. */
internal const val MAX_RENDER_SCALE = 2.5f

/** At most 12 MP (48 MB) per rendered page, whatever its shape. */
internal const val MAX_PAGE_PIXELS = 12_000_000

/** The width to render a page at: screen width times the zoom for visible pages (up to [MAX_RENDER_SCALE]), within the budget. */
internal fun renderWidth(viewportWidth: Int, zoom: Float, aspectRatio: Float, visible: Boolean): Int {
    val scale = if (visible) zoom.coerceIn(1f, MAX_RENDER_SCALE) else 1f
    val wanted = (viewportWidth * scale).roundToInt()
    val largest = sqrt(MAX_PAGE_PIXELS / aspectRatio.coerceAtLeast(0.01f)).toInt()
    return wanted.coerceAtMost(largest).coerceAtLeast(1)
}

private data class Viewport(val firstVisible: Int, val lastVisible: Int, val widthPx: Int, val zoom: Float)

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pdfRepository: PdfRepository,
    private val libraryRepository: LibraryRepository,
    private val pageSourceFactory: PdfPageSourceFactory,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val analytics: Analytics
) : ViewModel() {
    val openAlexId: String = checkNotNull(savedStateHandle[ARG_OPEN_ALEX_ID]) { "ReaderRoute needs an openAlexId" }

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Rendered pages by index: only the visible ones and [RENDER_MARGIN] on each side. */
    val pages: StateFlow<Map<Int, Bitmap>> = _pages.asStateFlow()

    private val _message = MutableStateFlow<ReaderMessage?>(null)
    val message: StateFlow<ReaderMessage?> = _message.asStateFlow()

    private val _exit = MutableStateFlow<ReaderExit?>(null)
    val exit: StateFlow<ReaderExit?> = _exit.asStateFlow()

    private val notesEditor = NotesEditor(
        openAlexId,
        libraryRepository,
        viewModelScope,
        applicationScope,
        onSaveFailed = { _message.value = ReaderMessage.NotesSaveFailed },
        onSaved = { if (NotedPapers.firstEdit(openAlexId)) analytics.log(AnalyticsEvent.NoteEdited) }
    )
    val notes: StateFlow<PaperNotes?> = notesEditor.notes
    val notesSaveState: StateFlow<NotesSaveState> = notesEditor.saveState

    private var source: PdfPageSource? = null
    private var title = ""
    private val viewport = MutableStateFlow<Viewport?>(null)
    private val currentPage = MutableStateFlow<Int?>(null)

    /** The page last read from or written to the database. */
    private var savedPage: Int? = null

    private var openCounted = false

    init {
        viewModelScope.launch { load() }
        // collectLatest: a new viewport cancels rendering for the old one.
        viewModelScope.launch { viewport.filterNotNull().collectLatest { renderAround(it) } }
        viewModelScope.launch {
            currentPage.filterNotNull().distinctUntilChanged().debounce(LAST_PAGE_SAVE_DEBOUNCE_MS).collect { savePage(it) }
        }
    }

    /** The list's visible pages, its width in pixels and the zoom once a gesture ends. */
    fun onViewport(firstVisible: Int, lastVisible: Int, widthPx: Int, zoom: Float) {
        if (widthPx > 0 && lastVisible >= firstVisible) viewport.value = Viewport(firstVisible, lastVisible, widthPx, zoom)
    }

    fun onPageChanged(page: Int) {
        currentPage.value = page
    }

    fun onNoteChange(section: NoteSection, text: String) = notesEditor.onNoteChange(section, text)

    fun onRetrySave() = notesEditor.retry()

    /** The Notes sheet closed: write what was typed without waiting for the pause. */
    fun onNotesClosed() = notesEditor.flush()

    /** The app went to the background: write the notes and the page now. */
    fun onStop() {
        notesEditor.flush()
        flushPage()
    }

    /** Saves typed notes first, so Details reads them on return; if that fails, the reader stays with Retry. */
    fun onBack() {
        viewModelScope.launch {
            if (notesEditor.saveNow()) {
                flushPage()
                _exit.compareAndSet(null, ReaderExit.Back)
            }
        }
    }

    /** Replace PDF on the can't-open screen. */
    fun onReplace(uri: Uri) {
        viewModelScope.launch {
            val result = try {
                pdfRepository.attach(openAlexId, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AttachResult.Unreadable
            }
            when (result) {
                AttachResult.Done -> reopen()
                AttachResult.NotPdf -> _message.value = ReaderMessage.NotPdf
                AttachResult.TooLarge -> _message.value = ReaderMessage.TooLarge
                AttachResult.Unreadable -> _message.value = ReaderMessage.AttachFailed
            }
        }
    }

    /**
     * Remove PDF on the can't-open screen; the reader closes either way. Typed notes are saved first, as on Back, because removing
     * closes the reader at once and Details reads the notes again as soon as it is back on screen.
     */
    fun onRemovePdf() {
        viewModelScope.launch {
            if (!notesEditor.saveNow()) return@launch
            try {
                pdfRepository.remove(openAlexId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The row on Details still shows the PDF, with its own Remove.
            }
            _exit.compareAndSet(null, ReaderExit.Closed)
        }
    }

    fun onMessageShown() {
        _message.value = null
    }

    override fun onCleared() {
        onStop()
        closeSource()
    }

    private suspend fun load() {
        title = libraryRepository.observePaper(openAlexId).first()?.paper?.title.orEmpty()
        val pdf = pdfRepository.observePdf(openAlexId).first()
        val file = pdfRepository.pdfFile(openAlexId)
        if (pdf == null || file == null) {
            _exit.compareAndSet(null, ReaderExit.Closed)
            return
        }
        savedPage = pdf.lastPage
        open(file, pdf.lastPage)
        countOpened(if (pdf.source == PdfSource.Attached) PdfOrigin.Attached else PdfOrigin.Downloaded)
        // Removed from Details, Settings or the paper's removal: there is nothing left to read.
        viewModelScope.launch {
            pdfRepository.observePdf(openAlexId).first { it == null }
            _exit.compareAndSet(null, ReaderExit.Closed)
        }
    }

    private suspend fun open(file: File, startPage: Int) {
        val opened = try {
            pageSourceFactory.open(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (opened == null) {
            _state.value = ReaderState.CantOpen(title)
            return
        }
        source = opened
        val ratios = List(opened.pageCount) { index ->
            val size = opened.pageSize(index)
            size.height.toFloat() / size.width.coerceAtLeast(1)
        }
        _state.value = ReaderState.Ready(title, opened.pageCount, ratios, startPage.coerceIn(0, opened.pageCount - 1), file)
    }

    private suspend fun reopen() {
        closeSource()
        val file = pdfRepository.pdfFile(openAlexId) ?: return
        savedPage = 0
        currentPage.value = null
        _state.value = ReaderState.Loading
        open(file, startPage = 0)
        countOpened(PdfOrigin.Attached)
    }

    /** The first PDF that opens counts, once per view model, whether it opened at the start or after Replace. */
    private fun countOpened(origin: PdfOrigin) {
        if (openCounted || _state.value !is ReaderState.Ready) return
        openCounted = true
        analytics.log(AnalyticsEvent.PdfOpened(origin))
    }

    private suspend fun renderAround(viewport: Viewport) {
        val source = source ?: return
        val ready = _state.value as? ReaderState.Ready ?: return
        val visible = viewport.firstVisible..viewport.lastVisible
        val first = (viewport.firstVisible - RENDER_MARGIN).coerceAtLeast(0)
        val last = (viewport.lastVisible + RENDER_MARGIN).coerceAtMost(source.pageCount - 1)
        val window = first..last
        // Pages at least three places from the screen are never drawn, so recycling them is safe.
        val evicted = _pages.value.filterKeys { it !in window }
        if (evicted.isNotEmpty()) {
            _pages.update { it - evicted.keys }
            evicted.values.forEach(Bitmap::recycle)
        }
        val order = visible.filter { it in window } + window.filter { it !in visible }
        for (index in order) {
            val width = renderWidth(viewport.widthPx, viewport.zoom, ready.pageAspectRatios[index], index in visible)
            if (_pages.value[index]?.width == width) continue
            val bitmap = try {
                source.render(index, width)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue
            }
            // The replaced bitmap may still be on screen this frame, so it is left to the garbage collector, not recycled.
            _pages.update { it + (index to bitmap) }
        }
    }

    private suspend fun savePage(page: Int) {
        if (page == savedPage) return
        try {
            pdfRepository.setLastPage(openAlexId, page)
            savedPage = page
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next scroll or leaving the reader tries again.
        }
    }

    /** Writes the current page now, on the application scope, so leaving can't cancel it. */
    private fun flushPage() {
        val page = currentPage.value ?: return
        if (page == savedPage) return
        savedPage = page
        applicationScope.launch { runCatching { pdfRepository.setLastPage(openAlexId, page) } }
    }

    private fun closeSource() {
        val old = source ?: return
        source = null
        viewport.value = null
        val bitmaps = _pages.value
        _pages.value = emptyMap()
        bitmaps.values.forEach(Bitmap::recycle)
        old.close()
    }
}
