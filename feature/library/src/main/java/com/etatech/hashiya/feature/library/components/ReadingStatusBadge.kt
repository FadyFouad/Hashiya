package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.readingStatusLabel
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.R

internal const val READING_STATUS_BADGE_TAG = "reading_status_badge"

/**
 * A labelled pill showing a paper's status: To read is outlined, Reading is filled teal, Read is filled with a check.
 * The label always says the status, so it never relies on colour. Tapping it opens a menu to change the status.
 */
@Composable
internal fun ReadingStatusBadge(status: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = readingStatusLabel(status)
    val description = stringResource(R.string.library_status_badge_description, label)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        ReadingStatus.ToRead -> Color.Transparent to colors.onSurfaceVariant
        ReadingStatus.Reading -> colors.primaryContainer to colors.onPrimaryContainer
        ReadingStatus.Read -> colors.surfaceContainerHighest to colors.onSurface
    }
    Box(modifier) {
        Surface(
            onClick = { menuOpen = true },
            shape = RoundedCornerShape(50),
            color = container,
            contentColor = content,
            border = if (status == ReadingStatus.ToRead) BorderStroke(1.dp, colors.outline) else null,
            modifier = Modifier
                .testTag(READING_STATUS_BADGE_TAG)
                .semantics { contentDescription = description }
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == ReadingStatus.Read) {
                    Icon(HashiyaIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            ReadingStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(readingStatusLabel(option)) },
                    onClick = {
                        menuOpen = false
                        if (option != status) onStatusChange(option)
                    },
                    leadingIcon = {
                        if (option == status) {
                            Icon(HashiyaIcons.Check, contentDescription = null)
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    modifier = Modifier.semantics { selected = option == status }
                )
            }
        }
    }
}
