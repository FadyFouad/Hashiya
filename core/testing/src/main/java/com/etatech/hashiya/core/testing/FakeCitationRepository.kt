package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CitationResult
import kotlinx.coroutines.CompletableDeferred

class FakeCitationRepository : CitationRepository {
    /** What every result reports as [CitationResult.complete]. */
    var complete = true

    /** When set, [entry] and [export] throw it. */
    var failure: Exception? = null

    var exportText = "@misc{paper2020,\n}\n"

    /** openAlexId → the entry [entry] returns; ids not here are "not saved". */
    var entries: Map<String, String> = emptyMap()

    /** The collection id of every [export] call, in order. */
    val exports = mutableListOf<Long?>()

    /** When set, [export] waits for it, so a test can see the export running. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun entry(openAlexId: String): CitationResult? {
        failure?.let { throw it }
        return entries[openAlexId]?.let { CitationResult(it, complete) }
    }

    override suspend fun export(collectionId: Long?): CitationResult {
        exports += collectionId
        gate?.await()
        failure?.let { throw it }
        return CitationResult(exportText, complete)
    }
}
