package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.readingStatusLabel
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.feature.library.LibraryFilter
import com.etatech.hashiya.feature.library.R
import java.text.NumberFormat

/** Single-select All · To read · Reading · Read, each with how many papers match the search. Scrolls sideways. */
@Composable
internal fun StatusFilterChips(filter: LibraryFilter, onSelect: (ReadingStatus?) -> Unit, modifier: Modifier = Modifier) {
    // The same number formatting as Search's result count, so Arabic shows Arabic-Indic digits.
    val numbers = NumberFormat.getInstance(LocalConfiguration.current.locales[0])
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatusChip(stringResource(R.string.library_filter_all), numbers.format(filter.total), filter.status == null) { onSelect(null) }
        ReadingStatus.entries.forEach { status ->
            StatusChip(readingStatusLabel(status), numbers.format(filter.counts[status] ?: 0), filter.status == status) {
                onSelect(status)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, count: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(R.string.library_filter_count, label, count)) },
        // The check marks the selection without relying on colour.
        leadingIcon = if (selected) {
            { Icon(HashiyaIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
        } else {
            null
        }
    )
}
