package com.etatech.hashiya.core.designsystem.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.NotesSaveState
import com.etatech.hashiya.core.model.PaperNotes

/** Each note field's test tag is this plus the section's name, e.g. "note_field_Summary". */
const val NOTE_FIELD_TAG_PREFIX = "note_field_"

/** "My notes" and, beside it, Saving…, Saved or Couldn't save. */
@Composable
fun NotesHeading(saveState: NotesSaveState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.designsystem_notes_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        val status = when (saveState) {
            NotesSaveState.Idle -> null
            NotesSaveState.Saving -> R.string.designsystem_notes_saving
            NotesSaveState.Saved -> R.string.designsystem_notes_saved
            NotesSaveState.Failed -> R.string.designsystem_notes_save_failed
        }
        if (status != null) {
            Text(
                stringResource(status),
                style = MaterialTheme.typography.labelMedium,
                color = if (saveState == NotesSaveState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

/** Every note section in order, each seeded once from [notes]. A new [version] seeds them again (after a reload). */
@Composable
fun NoteFields(notes: PaperNotes, version: Int, onNoteChange: (NoteSection, String) -> Unit) {
    NoteSection.entries.forEach { section ->
        key(section, version) {
            Spacer(Modifier.height(12.dp))
            NoteField(section, notes[section], onTextChange = { text -> onNoteChange(section, text) })
        }
    }
}

/**
 * One note section. The field owns what is on screen, so typing never waits for the ViewModel's state to come back, which can
 * drop characters and reset the keyboard's composition. [text] only seeds it: notes are read once.
 */
@Composable
fun NoteField(section: NoteSection, text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
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
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOTE_FIELD_TAG_PREFIX + section.name)
    )
}

@get:StringRes
private val NoteSection.labelRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.designsystem_note_summary
        NoteSection.ResearchQuestion -> R.string.designsystem_note_research_question
        NoteSection.Method -> R.string.designsystem_note_method
        NoteSection.KeyFindings -> R.string.designsystem_note_key_findings
        NoteSection.Limitations -> R.string.designsystem_note_limitations
        NoteSection.Thoughts -> R.string.designsystem_note_thoughts
    }

@get:StringRes
private val NoteSection.hintRes: Int
    get() = when (this) {
        NoteSection.Summary -> R.string.designsystem_note_summary_hint
        NoteSection.ResearchQuestion -> R.string.designsystem_note_research_question_hint
        NoteSection.Method -> R.string.designsystem_note_method_hint
        NoteSection.KeyFindings -> R.string.designsystem_note_key_findings_hint
        NoteSection.Limitations -> R.string.designsystem_note_limitations_hint
        NoteSection.Thoughts -> R.string.designsystem_note_thoughts_hint
    }
