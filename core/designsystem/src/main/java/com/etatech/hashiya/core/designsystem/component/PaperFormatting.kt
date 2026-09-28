package com.etatech.hashiya.core.designsystem.component

import android.icu.text.CompactDecimalFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import java.text.NumberFormat
import java.util.Locale

data class AuthorSummary(val names: String, val remaining: Int)

fun summarizeAuthors(authors: List<Author>, max: Int = 3): AuthorSummary = AuthorSummary(
    names = authors.take(max).joinToString(", ") { it.name },
    remaining = (authors.size - max).coerceAtLeast(0)
)

/** "128K" in English; each locale's own short form elsewhere. */
fun compactCount(value: Int, locale: Locale): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(value.toLong())

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
internal fun fullCount(value: Int): String = NumberFormat.getInstance(currentLocale()).format(value)

@Composable
fun paperTitle(paper: Paper): String = paper.title.ifBlank { stringResource(R.string.designsystem_untitled) }

@Composable
internal fun authorsLine(authors: List<Author>): String {
    val summary = summarizeAuthors(authors)
    return if (summary.remaining == 0) {
        summary.names
    } else {
        stringResource(R.string.designsystem_authors_more, summary.names, summary.remaining)
    }
}
