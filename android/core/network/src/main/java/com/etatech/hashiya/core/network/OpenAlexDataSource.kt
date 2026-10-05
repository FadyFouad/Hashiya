package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import com.etatech.hashiya.core.network.model.RequestRoute
import com.etatech.hashiya.core.network.quota.ROUTE_HEADER
import com.etatech.hashiya.core.network.quota.SearchCache
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response

interface OpenAlexDataSource {
    /** @throws NetworkException on any failure. */
    suspend fun searchWorks(request: WorksSearchRequest): NetworkWorksResponse
}

internal class RetrofitOpenAlexDataSource @Inject constructor(private val api: OpenAlexApi, private val cache: SearchCache) :
    OpenAlexDataSource {
    override suspend fun searchWorks(request: WorksSearchRequest): NetworkWorksResponse = try {
        val query = listOf(
            "search" to request.search,
            "filter" to request.filter,
            "sort" to request.sort,
            "per_page" to request.perPage.toString(),
            "cursor" to request.cursor,
            "select" to SEARCH_FIELDS
        )
        cache.worksPage(query) {
            api.searchWorks(
                search = request.search,
                filter = request.filter,
                sort = request.sort,
                perPage = request.perPage,
                cursor = request.cursor
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toNetworkException()
    }
}

/**
 * A page of `GET /works` for [query] (the parameters sent; null values are left out): from the cache when it holds one
 * that decodes (route [RequestRoute.Cached]), else from [fetch], cached only once its body decodes.
 * @throws HttpException for a non-2xx response, [SerializationException] for a body that doesn't decode.
 */
internal suspend fun SearchCache.worksPage(
    query: List<Pair<String, String?>>,
    fetch: suspend () -> Response<ResponseBody>
): NetworkWorksResponse {
    val key = SearchCache.key("/works", query.mapNotNull { (name, value) -> value?.let { name to it } })
    val cached = withContext(Dispatchers.IO) {
        read(key)?.let { body ->
            try {
                OpenAlexJson.decodeFromString<NetworkWorksResponse>(body)
            } catch (_: SerializationException) {
                null
            }
        }
    }
    if (cached != null) return cached.copy(route = RequestRoute.Cached)

    val response = fetch()
    if (!response.isSuccessful) throw HttpException(response)
    val route = RequestRoute.entries.firstOrNull { it.name == response.headers()[ROUTE_HEADER] }
    return withContext(Dispatchers.IO) {
        val body = response.body()?.use { it.string() }.orEmpty()
        val page = OpenAlexJson.decodeFromString<NetworkWorksResponse>(body)
        write(key, body)
        page.copy(route = route)
    }
}
