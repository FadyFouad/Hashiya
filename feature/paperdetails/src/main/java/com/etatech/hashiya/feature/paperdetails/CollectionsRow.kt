package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.PaperCollection

internal const val COLLECTIONS_ROW_TAG = "collections_row"

/** "Collections" and the names of the ones this paper is in, as plain chips. The whole row is one button that opens the checklist. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollectionsRow(collections: List<PaperCollection>, memberOf: Set<Long>, onClick: () -> Unit) {
    val names = collections.filter { it.id in memberOf }.map { it.name }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(COLLECTIONS_ROW_TAG)
    ) {
        Icon(HashiyaIcons.Collection, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(stringResource(R.string.details_collections), style = MaterialTheme.typography.labelMedium)
            if (names.isEmpty()) {
                Text(
                    stringResource(R.string.details_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { name ->
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        Icon(HashiyaIcons.ArrowDropDown, contentDescription = null)
    }
}
