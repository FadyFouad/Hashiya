package com.etatech.hashiya.core.model

sealed interface SearchError {
    data object Offline : SearchError
    data object InvalidUserKey : SearchError
    data object RateLimited : SearchError
    data object ServiceUnavailable : SearchError
    data object Unexpected : SearchError
}
