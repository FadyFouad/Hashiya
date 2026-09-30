package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection

internal const val CHECKLIST_ROW_TAG_PREFIX = "checklist_row_"

/**
 * One checkbox per collection, applied at once, then "New collection". Stays open while toggling.
 * The boxes show [memberOf], the stored state, so a change that fails un-ticks by itself. The sheet covers the screen's
 * snackbar, so it shows [snackbarHostState]'s messages ("Couldn't update collections") itself while open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionChecklistSheet(
    collections: List<PaperCollection>,
    memberOf: Set<Long>,
    onToggle: (Long, Boolean) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            if (collections.isEmpty()) {
                Text(
                    stringResource(R.string.details_collections_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            collections.forEach { collection ->
                val checked = collection.id in memberOf
                ListItem(
                    headlineContent = { Text(collection.name) },
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    modifier = Modifier
                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(collection.id, it) })
                        .testTag(CHECKLIST_ROW_TAG_PREFIX + collection.id)
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.details_new_collection)) },
                leadingContent = { Icon(HashiyaIcons.Add, contentDescription = null) },
                modifier = Modifier.clickable(role = Role.Button, onClick = onNew)
            )
            SnackbarHost(snackbarHostState)
        }
    }
}
