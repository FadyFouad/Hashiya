package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.LookupResult
import com.etatech.hashiya.core.data.repository.PaperLookupRepository
import com.etatech.hashiya.core.model.PaperIdentifier
import kotlinx.coroutines.CompletableDeferred

class FakePaperLookupRepository : PaperLookupRepository {
    val lookups = mutableListOf<PaperIdentifier>()
    val results = mutableMapOf<PaperIdentifier, LookupResult>()
    private var gate: CompletableDeferred<Unit>? = null

    /** Makes every following lookup wait until [releaseLookups], so tests can see the Looking state. */
    fun holdLookups() {
        gate = CompletableDeferred()
    }

    fun releaseLookups() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun lookup(identifier: PaperIdentifier): LookupResult {
        lookups += identifier
        gate?.await()
        return results[identifier] ?: LookupResult.NotFound(arxivTitle = null)
    }
}
