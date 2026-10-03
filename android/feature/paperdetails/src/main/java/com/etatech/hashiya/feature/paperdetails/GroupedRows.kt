package com.etatech.hashiya.feature.paperdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.R as DesignR
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons

/** Gap between the rows of a grouped list (Material 3). */
internal val GroupedRowGap = 2.dp

/** The first row of a group rounds its top, the last its bottom; the joins between rows stay tight. */
internal fun groupedRowShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 4.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** A grouped row's surface: surfaceContainer, in the shape its place in the group gives it. */
@Composable
internal fun GroupedRowSurface(shape: Shape, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

/** A row's two lines: a small label ("Collections", "PDF", "DOI") over its value. */
@Composable
internal fun GroupedRowLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

internal const val DOI_ROW_TAG = "doi_row"

/** The paper's DOI as a row: label, the DOI on one line, and an open-in-new mark. The whole row opens the DOI page. */
@Composable
internal fun DoiRow(doi: String, shape: Shape, onOpen: () -> Unit) {
    // A DOI is Latin text: kept left to right, and on the reading side of an Arabic layout.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    GroupedRowSurface(shape) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(shape)
                .clickable(onClickLabel = stringResource(DesignR.string.designsystem_open_doi), role = Role.Button, onClick = onOpen)
                .heightIn(min = 72.dp)
                .padding(16.dp)
                .testTag(DOI_ROW_TAG)
        ) {
            Icon(HashiyaIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                GroupedRowLabel(stringResource(R.string.details_doi))
                Text(
                    doi,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                    textAlign = if (rtl) TextAlign.Right else TextAlign.Left,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Icon(HashiyaIcons.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
