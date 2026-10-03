package com.etatech.hashiya.feature.paperdetails

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.designsystem.R as DesignR
import com.etatech.hashiya.core.designsystem.component.CollectionNameDialog
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.NoteFields
import com.etatech.hashiya.core.designsystem.component.NotesHeading
import com.etatech.hashiya.core.designsystem.component.PaperAbstract
import com.etatech.hashiya.core.designsystem.component.PaperHeader
import com.etatech.hashiya.core.designsystem.component.ReadingStatusSelector
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.ControlMaxWidth
import com.etatech.hashiya.core.designsystem.layout.centeredMaxWidth
import com.etatech.hashiya.core.designsystem.layout.horizontalMargin
import com.etatech.hashiya.core.model.Paper

/** [inPane]: shown beside the Library's list, so there is no back button; [onBack] then closes the pane. */
@Composable
internal fun PaperDetailsScreen(
    openAlexId: String,
    onBack: () -> Unit,
    onRemove: (openAlexId: String) -> Unit,
    onReadPdf: (openAlexId: String) -> Unit = {},
    inPane: Boolean = false,
    viewModel: PaperDetailsViewModel = hiltViewModel<PaperDetailsViewModel, PaperDetailsViewModel.Factory>(
        creationCallback = { factory -> factory.create(openAlexId) }
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    val newCollectionDialog by viewModel.newCollectionDialog.collectAsStateWithLifecycle()
    val copied by viewModel.copied.collectAsStateWithLifecycle()
    val pdf by viewModel.pdf.collectAsStateWithLifecycle()
    val openReader by viewModel.openReader.collectAsStateWithLifecycle()
    val notesVersion by viewModel.notesVersion.collectAsStateWithLifecycle()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachPdf(uri)
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Backgrounding the app or leaving the screen writes what was typed without waiting for the pause.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNotes() }
    // Back from the reader, whose Notes sheet may have written these notes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.reloadNotes() }
    LaunchedEffect(openReader) {
        if (openReader) {
            viewModel.onReaderOpened()
            onReadPdf(viewModel.openAlexId)
        }
    }
    LaunchedEffect(exit) {
        when (exit) {
            PaperDetailsExit.Closed -> onBack()
            PaperDetailsExit.Removed -> onRemove(viewModel.openAlexId)
            null -> Unit
        }
    }
    LaunchedEffect(copied) {
        val entry = copied ?: return@LaunchedEffect
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val confirmation = try {
            checkNotNull(clipboard).setPrimaryClip(ClipData.newPlainText("BibTeX", entry.text))
            copyConfirmation(entry.complete, Build.VERSION.SDK_INT)
        } catch (e: Exception) {
            PaperDetailsMessage.CopyFailed
        }
        viewModel.onCopyHandled(confirmation)
    }
    PaperDetailsContent(
        uiState = uiState,
        message = message,
        newCollectionDialog = newCollectionDialog,
        pdf = pdf,
        notesVersion = notesVersion,
        showBack = !inPane,
        actions = PaperDetailsActions(
            onBack = onBack,
            onRemove = viewModel::onRemove,
            onStatusChange = viewModel::onStatusChange,
            onNoteChange = viewModel::onNoteChange,
            onRetrySave = viewModel::onRetrySave,
            onMessageShown = viewModel::onMessageShown,
            onOpenLink = { url -> runCatching { uriHandler.openUri(url) } },
            onCopyBibTeX = viewModel::onCopyBibTeX,
            onToggleCollection = viewModel::onToggleCollection,
            onNewCollection = viewModel::onNewCollection,
            onNewCollectionNameEdited = viewModel::onNewCollectionNameEdited,
            onNewCollectionConfirm = viewModel::onNewCollectionConfirm,
            onNewCollectionDismiss = viewModel::onNewCollectionDismiss,
            onReadPdf = viewModel::onReadPdf,
            onDownloadPdf = viewModel::downloadPdf,
            onCancelPdfDownload = viewModel::cancelPdfDownload,
            onAttachPdf = { pickPdf.launch(arrayOf("application/pdf")) },
            onRemovePdf = viewModel::removePdf
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PaperDetailsContent(
    uiState: PaperDetailsUiState,
    actions: PaperDetailsActions,
    modifier: Modifier = Modifier,
    message: PaperDetailsMessage? = null,
    newCollectionDialog: NewCollectionDialog? = null,
    pdf: PdfRow = PdfRow(PdfRowState.None, null),
    notesVersion: Int = 0,
    showBack: Boolean = true
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.details_notes_save_failed_message)
    val retry = stringResource(R.string.details_retry)
    val statusUpdateFailed = stringResource(R.string.details_status_update_failed)
    val collectionsUpdateFailed = stringResource(R.string.details_collections_update_failed)
    val bibtexCopied = stringResource(R.string.details_bibtex_copied)
    val bibtexIncomplete = stringResource(R.string.details_bibtex_incomplete)
    val copyFailed = stringResource(R.string.details_copy_failed)
    val attachNotPdf = stringResource(R.string.details_pdf_attach_not_pdf)
    val attachTooLarge = stringResource(R.string.details_pdf_too_large)
    val attachFailed = stringResource(R.string.details_pdf_attach_failed)
    LaunchedEffect(message) {
        when (message) {
            PaperDetailsMessage.NotesSaveFailed -> {
                val result = snackbarHostState.showSnackbar(saveFailed, retry, duration = SnackbarDuration.Long)
                actions.onMessageShown()
                if (result == SnackbarResult.ActionPerformed) actions.onRetrySave()
            }

            PaperDetailsMessage.StatusUpdateFailed -> {
                snackbarHostState.showSnackbar(statusUpdateFailed)
                actions.onMessageShown()
            }

            PaperDetailsMessage.CollectionsUpdateFailed -> {
                snackbarHostState.showSnackbar(collectionsUpdateFailed)
                actions.onMessageShown()
            }

            PaperDetailsMessage.BibTeXCopied -> {
                snackbarHostState.showSnackbar(bibtexCopied)
                actions.onMessageShown()
            }

            PaperDetailsMessage.BibTeXIncomplete -> {
                snackbarHostState.showSnackbar(bibtexIncomplete, duration = SnackbarDuration.Long)
                actions.onMessageShown()
            }

            PaperDetailsMessage.CopyFailed -> {
                snackbarHostState.showSnackbar(copyFailed)
                actions.onMessageShown()
            }

            PaperDetailsMessage.PdfAttachNotPdf -> {
                snackbarHostState.showSnackbar(attachNotPdf)
                actions.onMessageShown()
            }

            PaperDetailsMessage.PdfAttachTooLarge -> {
                snackbarHostState.showSnackbar(attachTooLarge)
                actions.onMessageShown()
            }

            PaperDetailsMessage.PdfAttachFailed -> {
                snackbarHostState.showSnackbar(attachFailed)
                actions.onMessageShown()
            }

            null -> Unit
        }
    }

    var checklistOpen by rememberSaveable { mutableStateOf(false) }
    val loaded = uiState as? PaperDetailsUiState.Loaded
    if (checklistOpen && loaded != null) {
        CollectionChecklistSheet(
            collections = loaded.collections,
            memberOf = loaded.memberOf,
            onToggle = actions.onToggleCollection,
            onNew = actions.onNewCollection,
            onDismiss = { checklistOpen = false },
            snackbarHostState = snackbarHostState
        )
    }
    if (newCollectionDialog != null) {
        CollectionNameDialog(
            initialName = null,
            nameTaken = newCollectionDialog.nameTaken,
            onNameEdited = actions.onNewCollectionNameEdited,
            onConfirm = actions.onNewCollectionConfirm,
            onDismiss = actions.onNewCollectionDismiss
        )
    }
    // Replace and Remove ask first (spec §6); Replace then opens the picker.
    var confirming by rememberSaveable { mutableStateOf<PdfAction?>(null) }
    confirming?.let { action ->
        val replace = action == PdfAction.Replace
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (replace) R.string.details_pdf_replace_title else R.string.details_pdf_remove_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    if (replace) actions.onAttachPdf() else actions.onRemovePdf()
                }) {
                    Text(stringResource(if (replace) R.string.details_pdf_replace else R.string.details_pdf_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.details_pdf_cancel)) }
            }
        )
    }
    val onPdfAction: (PdfAction) -> Unit = { action ->
        when (action) {
            PdfAction.Read -> actions.onReadPdf()
            PdfAction.Download, PdfAction.TryAgain -> actions.onDownloadPdf()
            PdfAction.Cancel -> actions.onCancelPdfDownload()
            PdfAction.Attach -> actions.onAttachPdf()
            PdfAction.Replace, PdfAction.Remove -> confirming = action
            PdfAction.OpenInBrowser, PdfAction.OpenLink -> pdf.link?.let(actions.onOpenLink)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = actions.onBack) {
                            Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.details_back))
                        }
                    }
                },
                actions = { if (uiState is PaperDetailsUiState.Loaded) OverflowMenu(actions.onCopyBibTeX, actions.onRemove) }
            )
        },
        // While the checklist is open it shows the snackbar itself, above its scrim.
        snackbarHost = { if (!(checklistOpen && uiState is PaperDetailsUiState.Loaded)) SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (uiState) {
            PaperDetailsUiState.Loading -> LoadingSkeleton(Modifier.padding(padding))

            is PaperDetailsUiState.Loaded -> DetailsBody(
                uiState,
                notesVersion = notesVersion,
                actions = actions,
                pdf = pdf,
                onPdfAction = onPdfAction,
                onOpenCollections = { checklistOpen = true },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun OverflowMenu(onCopyBibTeX: () -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HashiyaIcons.MoreOptions, contentDescription = stringResource(R.string.details_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.details_copy_bibtex)) },
                leadingIcon = { Icon(HashiyaIcons.Copy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCopyBibTeX()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(DesignR.string.designsystem_remove_from_library)) },
                leadingIcon = { Icon(HashiyaIcons.Delete, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                }
            )
        }
    }
}

// A scrolling Column, not a LazyColumn: text fields in a lazy list lose focus when they scroll out of composition.
@Composable
private fun DetailsBody(
    state: PaperDetailsUiState.Loaded,
    notesVersion: Int,
    actions: PaperDetailsActions,
    pdf: PdfRow,
    onPdfAction: (PdfAction) -> Unit,
    onOpenCollections: () -> Unit,
    modifier: Modifier = Modifier
) {
    val paper = state.paper.paper
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .centeredMaxWidth()
            .padding(start = horizontalMargin(), end = horizontalMargin(), bottom = 24.dp)
    ) {
        PaperHeader(paper)
        Spacer(Modifier.height(16.dp))
        ReadingStatusSelector(state.paper.status, actions.onStatusChange)
        Spacer(Modifier.height(16.dp))
        // One Material 3 grouped list: Collections, PDF and, when the paper has one, its DOI.
        val doi = paper.doi
        val rows = if (doi != null) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(GroupedRowGap)) {
            CollectionsRow(state.collections, state.memberOf, groupedRowShape(0, rows), onClick = onOpenCollections)
            PdfRowView(pdf, groupedRowShape(1, rows), onPdfAction)
            if (doi != null) DoiRow(doi, groupedRowShape(2, rows)) { actions.onOpenLink("https://doi.org/$doi") }
        }
        Spacer(Modifier.height(16.dp))
        PaperAbstract(paper)
        Spacer(Modifier.height(24.dp))
        NotesHeading(state.saveState)
        NoteFields(state.notes, notesVersion, actions.onNoteChange)
    }
}
