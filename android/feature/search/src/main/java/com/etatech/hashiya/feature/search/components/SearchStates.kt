package com.etatech.hashiya.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.ErrorState
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.feature.search.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdleState(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    val suggestions = listOf(
        stringResource(R.string.search_suggestion_llm),
        stringResource(R.string.search_suggestion_crispr),
        stringResource(R.string.search_suggestion_climate)
    )
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = HashiyaIcons.Search,
            title = stringResource(R.string.search_idle_title),
            message = stringResource(R.string.search_idle_message)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            suggestions.forEach { suggestion ->
                AssistChip(onClick = { onSuggestion(suggestion) }, label = { Text(suggestion) })
            }
        }
    }
}

@Composable
internal fun NoResultsState(showClearFilters: Boolean, onClearFilters: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = HashiyaIcons.SearchOff,
        title = stringResource(R.string.search_empty_title),
        message = stringResource(R.string.search_empty_message),
        actionLabel = if (showClearFilters) stringResource(R.string.search_clear_filters) else null,
        onAction = onClearFilters,
        modifier = modifier
    )
}

@Composable
internal fun SearchErrorState(error: SearchError, onRetry: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val (title, message) = when (error) {
        SearchError.Offline -> R.string.search_error_offline_title to R.string.search_error_offline_message
        SearchError.InvalidUserKey -> R.string.search_error_key_title to R.string.search_error_key_message
        SearchError.RateLimited -> R.string.search_error_rate_title to R.string.search_error_rate_message
        SearchError.ServiceUnavailable -> R.string.search_error_unavailable_title to R.string.search_error_unavailable_message
        SearchError.Unexpected -> R.string.search_error_unexpected_title to R.string.search_error_unexpected_message
    }
    val opensSettings = error == SearchError.InvalidUserKey
    ErrorState(
        title = stringResource(title),
        message = stringResource(message),
        actionLabel = stringResource(if (opensSettings) R.string.search_open_settings else R.string.search_retry),
        onAction = if (opensSettings) onOpenSettings else onRetry,
        modifier = modifier
    )
}
