package com.etatech.hashiya.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkFailureTest {
    @Test
    fun dailyLimitExceptionBecomesDailyLimitFailure() {
        val exception = DailyLimitException(resetAtMillis = 42).toNetworkException()

        assertEquals(NetworkFailure.DailyLimit(42), exception.failure)
    }

    @Test
    fun otherIoExceptionsStayConnectivity() {
        assertEquals(NetworkFailure.Connectivity, java.io.IOException("boom").toNetworkException().failure)
    }
}
