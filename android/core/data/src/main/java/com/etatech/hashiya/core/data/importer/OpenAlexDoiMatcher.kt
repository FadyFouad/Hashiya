package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.normalizeDoi
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkWork
import javax.inject.Inject
import kotlinx.coroutines.delay

/** DOIs per OpenAlex request. The docs disagree between 50 and 100 values per OR filter; 50 is safe, and below per_page's 100. */
internal const val DOIS_PER_REQUEST = 50

/** Between requests, under the keyless limit of 10 per second. */
internal const val REQUEST_SPACING_MS = 100L

/** What OpenAlex said about each DOI. [notChecked] couldn't be asked: no connection, or a lookup that failed. */
data class DoiMatches(
    val found: Map<String, Paper> = emptyMap(),
    val notFound: Set<String> = emptySet(),
    val notChecked: Set<String> = emptySet()
)

/**
 * Matches DOIs to OpenAlex works (spec §6). Batches of [DOIS_PER_REQUEST] go out as import calls, which the quota routes on its
 * import share; once it has no metered route left (DailyLimit), the rest are looked up one at a time with free single lookups.
 */
internal class OpenAlexDoiMatcher(private val openAlex: OpenAlexLookupDataSource, private val pause: suspend (Long) -> Unit) {
    @Inject
    constructor(openAlex: OpenAlexLookupDataSource) : this(openAlex, { delay(it) })

    suspend fun match(dois: List<String>): DoiMatches {
        val unique = dois.distinct()
        val (filterable, alone) = unique.partition { doi -> doi.none { it == '|' || it == ',' || it.isWhitespace() } }
        val found = LinkedHashMap<String, Paper>()
        val notFound = LinkedHashSet<String>()
        val notChecked = LinkedHashSet<String>()
        val singles = alone.toMutableList()
        var stopped = false
        var singlesOnly = false
        var sent = 0

        suspend fun spaced() {
            if (sent++ > 0) pause(REQUEST_SPACING_MS)
        }

        fun record(batch: List<String>, works: List<NetworkWork>) {
            works.forEach { work ->
                val doi = work.doi?.let(::normalizeDoi)
                if (doi != null && doi in batch && doi !in found) found[doi] = work.asPaper()
            }
            batch.filterNot { it in found }.forEach { notFound += it }
        }

        for (batch in filterable.chunked(DOIS_PER_REQUEST)) {
            when {
                stopped -> notChecked += batch

                singlesOnly -> singles += batch

                else -> {
                    spaced()
                    try {
                        record(batch, openAlex.findWorksForImport("doi:" + batch.joinToString("|"), perPage = DOIS_PER_REQUEST).results)
                    } catch (e: NetworkException) {
                        when (e.failure) {
                            is NetworkFailure.DailyLimit -> {
                                // No metered route left for imports today: finish with free single lookups.
                                singlesOnly = true
                                singles += batch
                            }

                            NetworkFailure.Connectivity -> {
                                stopped = true
                                notChecked += batch
                            }

                            else -> notChecked += batch
                        }
                    }
                }
            }
        }
        for (doi in singles) {
            if (stopped) {
                notChecked += doi
                continue
            }
            spaced()
            try {
                record(listOf(doi), listOfNotNull(openAlex.getWork("doi:$doi")))
            } catch (e: NetworkException) {
                notChecked += doi
                if (e.failure == NetworkFailure.Connectivity) stopped = true
            }
        }
        return DoiMatches(found, notFound, notChecked)
    }
}
