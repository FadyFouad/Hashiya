package com.etatech.hashiya.core.model

sealed interface SearchError {
    data object Offline : SearchError
    data object InvalidUserKey : SearchError
    data object RateLimited : SearchError
    data object ServiceUnavailable : SearchError
    data object Unexpected : SearchError

    /** Every route's daily budget is used up; search is available again at [resetAtMillis] (epoch millis). */
    data class DailyLimit(val resetAtMillis: Long) : SearchError
}
