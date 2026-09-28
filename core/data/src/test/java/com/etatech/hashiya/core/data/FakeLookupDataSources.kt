package com.etatech.hashiya.core.data

import com.etatech.hashiya.core.network.ArxivDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkMeta
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse

internal class FakeOpenAlexLookupDataSource : OpenAlexLookupDataSource {
    val workRequests = mutableListOf<String>()
    val findRequests = mutableListOf<Pair<String, Int>>()
    var works: Map<String, NetworkWork> = emptyMap()
    var found: List<NetworkWork> = emptyList()
    var getFailure: NetworkFailure? = null
    var findFailure: NetworkFailure? = null

    override suspend fun getWork(id: String): NetworkWork? {
        workRequests += id
        getFailure?.let { throw NetworkException(it) }
        return works[id]
    }

    override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse {
        findRequests += filter to perPage
        findFailure?.let { throw NetworkException(it) }
        return NetworkWorksResponse(meta = NetworkMeta(count = found.size.toLong()), results = found)
    }
}

internal class FakeArxivDataSource : ArxivDataSource {
    val requests = mutableListOf<String>()
    var titles: Map<String, String> = emptyMap()
    var failure: NetworkFailure? = null

    override suspend fun title(id: String): String? {
        requests += id
        failure?.let { throw NetworkException(it) }
        return titles[id]
    }
}
