package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.feature.library.LibraryHeader
import com.etatech.hashiya.feature.library.R

/** "All papers", then each collection with its count and a Rename/Delete menu, then "New collection". Every choice closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionSelectorSheet(
    header: LibraryHeader,
    onSelect: (Long?) -> Unit,
    onNew: () -> Unit,
    onRename: (PaperCollection) -> Unit,
    onDelete: (PaperCollection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.navigationBarsPadding()) {
            item {
                SelectorRow(
                    name = stringResource(R.string.library_all_papers),
                    count = header.libraryCount,
                    selected = header.selected == null,
                    onClick = {
                        onSelect(null)
                        onDismiss()
                    }
                )
            }
            items(header.collections, key = { it.id }) { collection ->
                SelectorRow(
                    name = collection.name,
                    count = collection.paperCount,
                    selected = header.selected?.id == collection.id,
                    onClick = {
                        onSelect(collection.id)
                        onDismiss()
                    },
                    menu = {
                        CollectionMenu(
                            name = collection.name,
                            onRename = {
                                onRename(collection)
                                onDismiss()
                            },
                            onDelete = {
                                onDelete(collection)
                                onDismiss()
                            }
                        )
                    }
                )
            }
            item {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_new_collection)) },
                    leadingContent = { Icon(HashiyaIcons.Add, contentDescription = null) },
                    modifier = Modifier.clickable(role = Role.Button) {
                        onNew()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun SelectorRow(
    name: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    menu: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(pluralStringResource(R.plurals.library_paper_count, count, count)) },
        leadingContent = { Icon(if (selected) HashiyaIcons.Check else HashiyaIcons.Collection, contentDescription = null) },
        trailingContent = menu,
        modifier = Modifier
            .semantics { this.selected = selected }
            .clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun CollectionMenu(name: String, onRename: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(HashiyaIcons.MoreOptions, contentDescription = stringResource(R.string.library_collection_options, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_rename)) },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}
