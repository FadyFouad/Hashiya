package com.etatech.hashiya.core.network

import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

sealed interface NetworkFailure {
    data object Connectivity : NetworkFailure

    /** [usedUserKey] tells whether the rejected request carried the user's own key. */
    data class Http(val code: Int, val usedUserKey: Boolean) : NetworkFailure

    data object MalformedResponse : NetworkFailure

    data object Unknown : NetworkFailure

    /** Every route's daily budget is used up until [resetAtMillis] (epoch millis). */
    data class DailyLimit(val resetAtMillis: Long) : NetworkFailure
}

/** Thrown inside the HTTP stack when no route has budget left; becomes [NetworkFailure.DailyLimit]. */
class DailyLimitException(val resetAtMillis: Long) : IOException("dailyLimit")

/** Message is the failure only: never the URL, which carries the API key. */
class NetworkException(val failure: NetworkFailure, cause: Throwable? = null) : Exception(failure.toString(), cause)

internal fun Throwable.toNetworkException(): NetworkException = when (this) {
    is NetworkException -> this

    is HttpException -> NetworkException(
        NetworkFailure.Http(
            code = code(),
            usedUserKey = response()?.raw()?.request?.tag(ApiKeyKind::class.java) == ApiKeyKind.User
        ),
        this
    )

    is SerializationException -> NetworkException(NetworkFailure.MalformedResponse, this)

    is DailyLimitException -> NetworkException(NetworkFailure.DailyLimit(resetAtMillis), this)

    is IOException -> NetworkException(NetworkFailure.Connectivity, this)

    else -> NetworkException(NetworkFailure.Unknown, this)
}
