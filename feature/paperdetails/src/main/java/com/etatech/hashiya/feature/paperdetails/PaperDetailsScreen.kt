package com.etatech.hashiya.feature.paperdetails

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.etatech.hashiya.core.designsystem.R as DesignR
import com.etatech.hashiya.core.designsystem.component.CollectionNameDialog
import com.etatech.hashiya.core.designsystem.component.LoadingSkeleton
import com.etatech.hashiya.core.designsystem.component.PaperAbstract
import com.etatech.hashiya.core.designsystem.component.PaperHeader
import com.etatech.hashiya.core.designsystem.component.ReadingStatusSelector
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.Paper

internal const val NOTE_FIELD_TAG_PREFIX = "note_field_"

@Composable
internal fun PaperDetailsScreen(
    onBack: () -> Unit,
    onRemove: (openAlexId: String) -> Unit,
    onReadPdf: (openAlexId: String) -> Unit = {},
    viewModel: PaperDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exit by viewModel.exit.collectAsStateWithLifecycle()
    val newCollectionDialog by viewModel.newCollectionDialog.collectAsStateWithLifecycle()
    val copied by viewModel.copied.collectAsStateWithLifecycle()
    val pdf by viewModel.pdf.collectAsStateWithLifecycle()
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.attachPdf(uri)
    }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    // Backgrounding the app or leaving the screen writes what was typed without waiting for the pause.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNotes() }
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
            onReadPdf = { onReadPdf(viewModel.openAlexId) },
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
    pdf: PdfRow = PdfRow(PdfRowState.None, null)
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
                    IconButton(onClick = actions.onBack) {
                        Icon(HashiyaIcons.Back, contentDescription = stringResource(R.string.details_back))
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
                actions,
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
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        PaperHeader(paper)
        Spacer(Modifier.height(16.dp))
        ReadingStatusSelector(state.paper.status, actions.onStatusChange)
        Spacer(Modifier.height(8.dp))
        CollectionsRow(state.collections, state.memberOf, onClick = onOpenCollections)
        PdfRowView(pdf, onPdfAction)
        PaperLinks(paper, actions.onOpenLink)
        Spacer(Modifier.height(16.dp))
        PaperAbstract(paper)
        Spacer(Modifier.height(24.dp))
        NotesHeading(state.saveState)
        NoteSection.entries.forEach { section ->
            key(section) {
                Spacer(Modifier.height(12.dp))
                NoteField(section, state.notes[section], onTextChange = { text -> actions.onNoteChange(section, text) })
            }
        }
    }
}

@Composable
private fun PaperLinks(paper: Paper, onOpenLink: (String) -> Unit) {
    val doi = paper.doi ?: return
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LinkButton(stringResource(DesignR.string.designsystem_open_doi), Modifier.weight(1f), onClick = {
            onOpenLink("https://doi.org/$doi")
        })
    }
}

@Composable
private fun LinkButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(HashiyaIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun NotesHeading(saveState: NotesSaveState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.details_notes_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val status = when (saveState) {
            NotesSaveState.Idle -> null
            NotesSaveState.Saving -> R.string.details_notes_saving
            NotesSaveState.Saved -> R.string.details_notes_saved
            NotesSaveState.Failed -> R.string.details_notes_save_failed
        }
        if (status != null) {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.labelMedium,
                color = if (saveState ==
                    NotesSaveState.Failed
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

@Composable
private fun NoteField(section: NoteSection, text: String, onTextChange: (String) -> Unit) {
    // The field owns what is on screen, so typing never waits for the ViewModel's state to come back, which can drop
    // characters and reset the keyboard's composition. [text] only seeds it: the ViewModel reads notes once.
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(text)) }
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val changed = new.text != value.text
            value = new
            if (changed) onTextChange(new.text)
        },
        label = { Text(stringResource(section.labelRes)) },
        placeholder = { Text(stringResource(section.hintRes)) },
        // Arabic notes lay out right to left in an English UI, and English notes left to right in an Arabic one.
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NOTE_FIELD_TAG_PREFIX + section.name)
    )
}

@get:StringRes
private val NoteSection.labelRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.note_summary
        NoteSection.ResearchQuestion -> R.string.note_research_question
        NoteSection.Method -> R.string.note_method
        NoteSection.KeyFindings -> R.string.note_key_findings
        NoteSection.Limitations -> R.string.note_limitations
        NoteSection.Thoughts -> R.string.note_thoughts
    }

@get:StringRes
private val NoteSection.hintRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.note_summary_hint
        NoteSection.ResearchQuestion -> R.string.note_research_question_hint
        NoteSection.Method -> R.string.note_method_hint
        NoteSection.KeyFindings -> R.string.note_key_findings_hint
        NoteSection.Limitations -> R.string.note_limitations_hint
        NoteSection.Thoughts -> R.string.note_thoughts_hint
    }
