package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkLocation
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Looks up single works by identifier. */
interface OpenAlexLookupDataSource {
    /**
     * The work OpenAlex resolves [id] to (e.g. "doi:10.1038/nature14539"), or null when OpenAlex has none.
     * Callers pass only well-formed ids, so HTTP 400 also means "none".
     * @throws NetworkException on any other failure.
     */
    suspend fun getWork(id: String): NetworkWork?

    /** @throws NetworkException on any failure. */
    suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse
}

/** Where else a saved paper's PDF may be, for when its stored link no longer gives it. */
interface OpenAlexPdfLinksDataSource {
    /**
     * Every location OpenAlex lists for the work [openAlexId] (e.g. "W2626778328"), in OpenAlex's order, or none when OpenAlex
     * has no such work.
     * @throws NetworkException on any other failure.
     */
    suspend fun pdfLocations(openAlexId: String): List<NetworkLocation>
}

private val NOT_FOUND_CODES = setOf(400, 404)

internal class RetrofitOpenAlexLookupDataSource @Inject constructor(private val api: OpenAlexApi) :
    OpenAlexLookupDataSource,
    OpenAlexPdfLinksDataSource {
    override suspend fun getWork(id: String): NetworkWork? = try {
        api.getWork(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val networkException = e.toNetworkException()
        val code = (networkException.failure as? NetworkFailure.Http)?.code
        if (code in NOT_FOUND_CODES) null else throw networkException
    }

    override suspend fun pdfLocations(openAlexId: String): List<NetworkLocation> = try {
        api.getWorkLocations(openAlexId).locations
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val networkException = e.toNetworkException()
        val code = (networkException.failure as? NetworkFailure.Http)?.code
        if (code in NOT_FOUND_CODES) emptyList() else throw networkException
    }

    override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = try {
        api.findWorks(filter = filter, perPage = perPage)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        throw e.toNetworkException()
    }
}
