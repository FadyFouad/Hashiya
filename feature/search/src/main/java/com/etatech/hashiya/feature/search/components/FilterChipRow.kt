package com.etatech.hashiya.feature.search.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.feature.search.R

private val YEAR_PRESETS = listOf(2024, 2020, 2015)

@Composable
internal fun FilterChipRow(
    sort: SearchSort,
    years: YearFilter,
    openAccessOnly: Boolean,
    currentYear: Int,
    onSortChange: (SearchSort) -> Unit,
    onYearFilterChange: (YearFilter) -> Unit,
    onOpenAccessToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuOpen by rememberSaveable { mutableStateOf(false) }
    var yearMenuOpen by rememberSaveable { mutableStateOf(false) }
    var yearDialogOpen by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box {
            FilterChip(
                selected = sort != SearchSort.Relevance,
                onClick = { sortMenuOpen = true },
                label = { Text(sortLabel(sort)) },
                trailingIcon = { Icon(HashiyaIcons.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                SearchSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(sortLabel(option)) },
                        onClick = {
                            sortMenuOpen = false
                            onSortChange(option)
                        }
                    )
                }
            }
        }
        Box {
            FilterChip(
                selected = years != YearFilter.AnyTime,
                onClick = { yearMenuOpen = true },
                label = { Text(yearLabel(years)) },
                trailingIcon = { Icon(HashiyaIcons.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) }
            )
            DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                (listOf<YearFilter>(YearFilter.AnyTime) + YEAR_PRESETS.map { YearFilter.Since(it) }).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(yearLabel(option)) },
                        onClick = {
                            yearMenuOpen = false
                            onYearFilterChange(option)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_year_custom)) },
                    onClick = {
                        yearMenuOpen = false
                        yearDialogOpen = true
                    }
                )
            }
        }
        FilterChip(
            selected = openAccessOnly,
            onClick = onOpenAccessToggle,
            label = { Text(stringResource(R.string.search_open_access)) },
            leadingIcon = if (openAccessOnly) {
                { Icon(HashiyaIcons.Check, contentDescription = null, Modifier.size(18.dp)) }
            } else {
                null
            }
        )
    }

    if (yearDialogOpen) {
        YearRangeDialog(
            initial = years as? YearFilter.Between,
            currentYear = currentYear,
            onConfirm = {
                yearDialogOpen = false
                onYearFilterChange(it)
            },
            onDismiss = { yearDialogOpen = false }
        )
    }
}

@Composable
private fun sortLabel(sort: SearchSort): String = stringResource(
    when (sort) {
        SearchSort.Relevance -> R.string.search_sort_relevance
        SearchSort.MostCited -> R.string.search_sort_most_cited
        SearchSort.Newest -> R.string.search_sort_newest
    }
)

@Composable
private fun yearLabel(years: YearFilter): String = when (years) {
    YearFilter.AnyTime -> stringResource(R.string.search_year_any)
    is YearFilter.Since -> stringResource(R.string.search_year_since, years.year)
    is YearFilter.Between -> stringResource(R.string.search_year_between, years.from, years.to)
}
