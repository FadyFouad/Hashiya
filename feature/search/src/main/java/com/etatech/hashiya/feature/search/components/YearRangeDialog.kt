package com.etatech.hashiya.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.feature.search.MIN_YEAR
import com.etatech.hashiya.feature.search.R
import com.etatech.hashiya.feature.search.YearRangeValidation
import com.etatech.hashiya.feature.search.validateYearRange

@Composable
internal fun YearRangeDialog(
    initial: YearFilter.Between?,
    currentYear: Int,
    onConfirm: (YearFilter.Between) -> Unit,
    onDismiss: () -> Unit
) {
    var from by rememberSaveable { mutableStateOf(initial?.from?.toString().orEmpty()) }
    var to by rememberSaveable { mutableStateOf(initial?.to?.toString().orEmpty()) }
    val validation = validateYearRange(from, to, currentYear)
    val bothFilled = from.isNotBlank() && to.isNotBlank()
    val error = when {
        !bothFilled -> null
        validation is YearRangeValidation.NotANumber -> stringResource(R.string.search_year_error_number)
        validation is YearRangeValidation.OutOfRange -> stringResource(R.string.search_year_error_range, MIN_YEAR, currentYear)
        validation is YearRangeValidation.FromAfterTo -> stringResource(R.string.search_year_error_order)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_year_dialog_title)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text(stringResource(R.string.search_year_from)) },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        label = { Text(stringResource(R.string.search_year_to)) },
                        singleLine = true,
                        isError = error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { (validation as? YearRangeValidation.Valid)?.let { onConfirm(it.range) } },
                enabled = validation is YearRangeValidation.Valid
            ) { Text(stringResource(R.string.search_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.search_cancel)) }
        }
    )
}
