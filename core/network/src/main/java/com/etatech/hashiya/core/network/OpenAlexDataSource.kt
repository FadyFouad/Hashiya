package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface OpenAlexDataSource {
    /** @throws NetworkException on any failure. */
    suspend fun searchWorks(request: WorksSearchRequest): NetworkWorksResponse
}

internal class RetrofitOpenAlexDataSource @Inject constructor(private val api: OpenAlexApi) : OpenAlexDataSource {
    override suspend fun searchWorks(request: WorksSearchRequest): NetworkWorksResponse = try {
        api.searchWorks(
            search = request.search,
            filter = request.filter,
            sort = request.sort,
            perPage = request.perPage,
            cursor = request.cursor
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toNetworkException()
    }
}
