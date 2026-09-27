package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.NetworkFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchErrorMappingTest {
    @Test
    fun connectivityIsOffline() {
        assertEquals(SearchError.Offline, NetworkFailure.Connectivity.asSearchError())
    }

    @Test
    fun rejectedUserKeyIsInvalidUserKey() {
        assertEquals(SearchError.InvalidUserKey, NetworkFailure.Http(401, usedUserKey = true).asSearchError())
        assertEquals(SearchError.InvalidUserKey, NetworkFailure.Http(403, usedUserKey = true).asSearchError())
    }

    @Test
    fun rejectedBuiltInKeyIsServiceUnavailable() {
        assertEquals(SearchError.ServiceUnavailable, NetworkFailure.Http(401, usedUserKey = false).asSearchError())
    }

    @Test
    fun tooManyRequestsIsRateLimited() {
        assertEquals(SearchError.RateLimited, NetworkFailure.Http(429, usedUserKey = true).asSearchError())
    }

    @Test
    fun serverErrorsAreServiceUnavailable() {
        assertEquals(SearchError.ServiceUnavailable, NetworkFailure.Http(503, usedUserKey = false).asSearchError())
    }

    @Test
    fun everythingElseIsUnexpected() {
        assertEquals(SearchError.Unexpected, NetworkFailure.Http(400, usedUserKey = false).asSearchError())
        assertEquals(SearchError.Unexpected, NetworkFailure.MalformedResponse.asSearchError())
        assertEquals(SearchError.Unexpected, NetworkFailure.Unknown.asSearchError())
    }
}
