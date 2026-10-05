package com.etatech.hashiya.feature.search.components

import android.text.format.DateFormat
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.component.EmptyState
import com.etatech.hashiya.core.designsystem.component.ErrorState
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.feature.search.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
internal fun SearchErrorState(
    error: SearchError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    resetZone: TimeZone = TimeZone.getDefault()
) {
    val (title, message) = when (error) {
        SearchError.Offline -> stringResource(R.string.search_error_offline_title) to stringResource(R.string.search_error_offline_message)

        SearchError.InvalidUserKey -> stringResource(R.string.search_error_key_title) to stringResource(R.string.search_error_key_message)

        SearchError.RateLimited -> stringResource(R.string.search_error_rate_title) to stringResource(R.string.search_error_rate_message)

        SearchError.ServiceUnavailable ->
            stringResource(R.string.search_error_unavailable_title) to stringResource(R.string.search_error_unavailable_message)

        SearchError.Unexpected ->
            stringResource(R.string.search_error_unexpected_title) to stringResource(R.string.search_error_unexpected_message)

        is SearchError.DailyLimit ->
            stringResource(R.string.search_error_daily_limit_title) to dailyLimitMessage(error, resetZone)
    }
    val opensSettings = error == SearchError.InvalidUserKey || error is SearchError.DailyLimit
    ErrorState(
        title = title,
        message = message,
        actionLabel = stringResource(if (opensSettings) R.string.search_open_settings else R.string.search_retry),
        onAction = if (opensSettings) onOpenSettings else onRetry,
        modifier = modifier
    )
}

/**
 * "Search will be available again at 3:00 AM. …": the reset time in the app's locale. The Arabic string wraps the time
 * in U+2068 … U+2069 itself, so it keeps its order inside the sentence.
 */
@Composable
internal fun dailyLimitMessage(error: SearchError.DailyLimit, zone: TimeZone): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val time = formatResetTime(error.resetAtMillis, LocalConfiguration.current.locales[0], zone, is24Hour)
    return stringResource(R.string.search_error_daily_limit_message, time)
}

/** java.text rather than java.time, which needs API 26 (minSdk is 24). */
internal fun formatResetTime(resetAtMillis: Long, locale: Locale, zone: TimeZone, is24Hour: Boolean): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm")
    return SimpleDateFormat(pattern, locale).apply {
        timeZone = zone
        // The locale's own digits (Arabic-Indic in Arabic), as the result count uses.
        numberFormat = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    }.format(Date(resetAtMillis))
}
