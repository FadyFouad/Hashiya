package com.etatech.hashiya.core.data

import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexDataSource
import com.etatech.hashiya.core.network.WorksSearchRequest
import com.etatech.hashiya.core.network.model.NetworkMeta
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse

/** Returns queued responses in order and records every request. */
internal class FakeOpenAlexDataSource : OpenAlexDataSource {
    val requests = mutableListOf<WorksSearchRequest>()
    private val responses = ArrayDeque<Result<NetworkWorksResponse>>()

    fun enqueuePage(vararg ids: String, nextCursor: String?, count: Long = 100) {
        responses += Result.success(
            NetworkWorksResponse(
                meta = NetworkMeta(count = count, nextCursor = nextCursor),
                results = ids.map { NetworkWork(id = "https://openalex.org/$it", displayName = "Paper $it") }
            )
        )
    }

    fun enqueueFailure(failure: NetworkFailure) {
        responses += Result.failure(NetworkException(failure))
    }

    override suspend fun searchWorks(request: WorksSearchRequest): NetworkWorksResponse {
        requests += request
        return responses.removeFirst().getOrThrow()
    }
}
