package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CitationRepository
import com.etatech.hashiya.core.data.repository.CitationResult
import com.etatech.hashiya.core.model.CitationStyle
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

    /** The style of every [entry] and [export] call, in order. */
    val styles = mutableListOf<CitationStyle>()

    /** When set, [export] waits for it, so a test can see the export running. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun entry(openAlexId: String, style: CitationStyle): CitationResult? {
        styles += style
        failure?.let { throw it }
        return entries[openAlexId]?.let {
            CitationResult(
                it,
                html = if (style ==
                    CitationStyle.Bibtex
                ) {
                    null
                } else {
                    "<i>$it</i>"
                },
                complete = complete
            )
        }
    }

    override suspend fun export(collectionId: Long?, style: CitationStyle): CitationResult {
        styles += style
        exports += collectionId
        gate?.await()
        failure?.let { throw it }
        return CitationResult(
            exportText,
            rtf = if (style == CitationStyle.Bibtex) null else "{\\rtf1 $exportText}",
            complete = complete
        )
    }
}
