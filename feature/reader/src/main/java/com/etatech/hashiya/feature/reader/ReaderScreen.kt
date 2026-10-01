package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.designsystem.component.NoteFields
import com.etatech.hashiya.core.designsystem.component.NotesHeading
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.feature.reader.share.PDF_MIME_TYPE
import com.etatech.hashiya.feature.reader.share.sharePdf

@Composable
internal fun ReaderScreen(onBack: () -> Unit, viewModel: ReaderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val notesSaveState by viewModel.notesSaveState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onReplace(uri)
    }
    // Backgrounding the app writes the notes and the page without waiting.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    // Back saves typed notes first (the sheet handles back itself while it is open).
    BackHandler(enabled = !showNotes) { viewModel.onBack() }
    LaunchedEffect(exit) { if (exit != null) onBack() }

    ReaderContent(
        state = state,
        pages = pages,
        notes = notes,
        notesSaveState = notesSaveState,
        showNotes = showNotes,
        message = message,
        actions = ReaderActions(
            onBack = viewModel::onBack,
            onShare = {
                (state as? ReaderState.Ready)?.let { ready -> runCatching { sharePdf(context, ready.file, ready.title) } }
            },
            onOpenNotes = { showNotes = true },
            onCloseNotes = {
                showNotes = false
                viewModel.onNotesClosed()
            },
            onNoteChange = viewModel::onNoteChange,
            onRetrySave = viewModel::onRetrySave,
            onViewport = viewModel::onViewport,
            onPageChanged = viewModel::onPageChanged,
            onReplace = { pickPdf.launch(arrayOf(PDF_MIME_TYPE)) },
            onRemovePdf = viewModel::onRemovePdf,
            onMessageShown = viewModel::onMessageShown
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderContent(
    state: ReaderState,
    pages: Map<Int, Bitmap>,
    notes: PaperNotes?,
    notesSaveState: NotesSaveState,
    showNotes: Boolean,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    message: ReaderMessage? = null,
    pillAlwaysVisible: Boolean = false
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it.textRes) }
    val retry = stringResource(R.string.reader_retry)
    LaunchedEffect(message) {
        if (message == null || messageText == null) return@LaunchedEffect
        val actionLabel = if (message == ReaderMessage.NotesSaveFailed) retry else null
        val result = snackbarHostState.showSnackbar(messageText, actionLabel = actionLabel)
        // Cleared first, so a Retry that fails again shows the message again.
        actions.onMessageShown()
        if (result == SnackbarResult.ActionPerformed) actions.onRetrySave()
    }
    val title = when (state) {
        is ReaderState.Ready -> state.title
        is ReaderState.CantOpen -> state.title
        ReaderState.Loading -> ""
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(onClick = actions.onOpenNotes) {
                        Icon(HashiyaIcons.Notes, contentDescription = stringResource(R.string.reader_notes))
                    }
                    if (state is ReaderState.Ready) {
                        IconButton(onClick = actions.onShare) {
                            Icon(HashiyaIcons.Export, contentDescription = stringResource(R.string.reader_share))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ReaderState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is ReaderState.Ready -> PdfPages(
                    ready = state,
                    pages = pages,
                    onViewport = actions.onViewport,
                    onPageChanged = actions.onPageChanged,
                    pillAlwaysVisible = pillAlwaysVisible
                )

                is ReaderState.CantOpen -> CantOpen(onReplace = actions.onReplace, onRemove = actions.onRemovePdf)
            }
        }
    }
    if (showNotes) {
        NotesSheet(notes, notesSaveState, actions)
    }
}

@Composable
private fun CantOpen(onReplace: () -> Unit, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            HashiyaIcons.Error,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reader_cant_open), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onReplace) { Text(stringResource(R.string.reader_replace_pdf)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.reader_remove_pdf)) }
    }
}

/** The paper's notes over the PDF: half height, expandable, with Details' fields and autosave. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesSheet(notes: PaperNotes?, saveState: NotesSaveState, actions: ReaderActions) {
    ModalBottomSheet(onDismissRequest = actions.onCloseNotes) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NotesHeading(saveState, Modifier.weight(1f))
                IconButton(onClick = actions.onCloseNotes) {
                    Icon(HashiyaIcons.Close, contentDescription = stringResource(R.string.reader_close_notes))
                }
            }
            if (notes == null) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                // The reader never reloads notes, so the fields seed once per opening of the sheet.
                NoteFields(notes, version = 0, onNoteChange = actions.onNoteChange)
            }
        }
    }
}

private val ReaderMessage.textRes: Int
    get() = when (this) {
        ReaderMessage.NotesSaveFailed -> R.string.reader_notes_save_failed
        ReaderMessage.NotPdf -> R.string.reader_not_pdf
        ReaderMessage.TooLarge -> R.string.reader_too_large
        ReaderMessage.AttachFailed -> R.string.reader_attach_failed
    }
