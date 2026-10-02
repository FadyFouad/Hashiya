package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.isValidCollectionName

const val COLLECTION_NAME_FIELD_TAG = "collection_name_field"

/**
 * Asks for a collection's name. [initialName] null is "New collection" with Create; otherwise "Rename collection" with Save.
 * The button is enabled only for a valid name (1–60 characters after trimming). [nameTaken] shows the clash error under the
 * field until the name is edited ([onNameEdited]). [onConfirm] gets the text as typed; the repository trims it.
 */
@Composable
fun CollectionNameDialog(
    initialName: String?,
    nameTaken: Boolean,
    onNameEdited: () -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName.orEmpty()) }
    val valid = isValidCollectionName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog sizes itself (280–560dp). The platform's default width, with a text field inside, never settles under
        // Robolectric at phone sizes, which hangs the tests that show this dialog.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        // Without the platform's insets, keep a margin in narrow windows.
        modifier = Modifier.padding(horizontal = 16.dp),
        title = { Text(stringResource(if (initialName == null) R.string.collection_new_title else R.string.collection_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onNameEdited()
                },
                label = { Text(stringResource(R.string.collection_name_label)) },
                singleLine = true,
                isError = nameTaken,
                supportingText = if (nameTaken) {
                    { Text(stringResource(R.string.collection_name_taken)) }
                } else {
                    null
                },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(name) }),
                modifier = Modifier.testTag(COLLECTION_NAME_FIELD_TAG)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) {
                Text(stringResource(if (initialName == null) R.string.collection_create else R.string.collection_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.collection_cancel)) }
        }
    )
}
