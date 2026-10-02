package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.ReadingStatus

/** The one place reading statuses get their names: badges, menus, chips and the preview selector all use it. */
@Composable
fun readingStatusLabel(status: ReadingStatus): String = stringResource(
    when (status) {
        ReadingStatus.ToRead -> R.string.status_to_read
        ReadingStatus.Reading -> R.string.status_reading
        ReadingStatus.Read -> R.string.status_read
    }
)

/** Single-choice To read · Reading · Read. The selected segment is filled and checked, so it never relies on colour alone. */
@Composable
fun ReadingStatusSelector(status: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit, modifier: Modifier = Modifier) {
    val options = ReadingStatus.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == status,
                onClick = { if (option != status) onStatusChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(readingStatusLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
