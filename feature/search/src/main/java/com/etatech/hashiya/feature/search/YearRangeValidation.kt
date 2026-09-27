package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.YearFilter

internal const val MIN_YEAR = 1900

internal sealed interface YearRangeValidation {
    data class Valid(val range: YearFilter.Between) : YearRangeValidation
    data object NotANumber : YearRangeValidation
    data object OutOfRange : YearRangeValidation
    data object FromAfterTo : YearRangeValidation
}

/** Accepts any Unicode decimal digits, so Arabic-Indic input like "٢٠٢٠" works. */
internal fun validateYearRange(from: String, to: String, currentYear: Int): YearRangeValidation {
    val fromYear = from.trim().toIntOrNull()
    val toYear = to.trim().toIntOrNull()
    return when {
        fromYear == null || toYear == null -> YearRangeValidation.NotANumber
        fromYear !in MIN_YEAR..currentYear || toYear !in MIN_YEAR..currentYear -> YearRangeValidation.OutOfRange
        fromYear > toYear -> YearRangeValidation.FromAfterTo
        else -> YearRangeValidation.Valid(YearFilter.Between(fromYear, toYear))
    }
}
