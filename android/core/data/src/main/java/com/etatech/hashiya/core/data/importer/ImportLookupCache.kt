package com.etatech.hashiya.core.data.importer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The analysis phase's lookups, kept for the import phase so it normally sends no requests. In memory only: after process death
 * the import phase asks OpenAlex again for whatever it still needs.
 */
@Singleton
internal class ImportLookupCache @Inject constructor() {
    private val matches = mutableMapOf<Long, DoiMatches>()

    @Synchronized
    operator fun get(jobId: Long): DoiMatches? = matches[jobId]

    @Synchronized
    operator fun set(jobId: Long, value: DoiMatches) {
        matches[jobId] = value
    }

    @Synchronized
    fun remove(jobId: Long) {
        matches.remove(jobId)
    }
}
