package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.NetworkFailure

internal fun NetworkFailure.asSearchError(): SearchError = when (this) {
    NetworkFailure.Connectivity -> SearchError.Offline

    is NetworkFailure.Http -> when (code) {
        401, 403 -> if (usedUserKey) SearchError.InvalidUserKey else SearchError.ServiceUnavailable
        429 -> SearchError.RateLimited
        in 500..599 -> SearchError.ServiceUnavailable
        else -> SearchError.Unexpected
    }

    is NetworkFailure.DailyLimit -> SearchError.DailyLimit(resetAtMillis)

    NetworkFailure.MalformedResponse, NetworkFailure.Unknown -> SearchError.Unexpected
}
