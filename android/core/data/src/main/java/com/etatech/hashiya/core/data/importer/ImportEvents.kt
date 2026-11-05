package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.ImportFileFormat
import com.etatech.hashiya.core.analytics.ImportResult
import com.etatech.hashiya.core.analytics.LibrarySize
import com.etatech.hashiya.core.analytics.MatchedShare
import com.etatech.hashiya.core.importer.ImportFormat
import com.etatech.hashiya.core.model.ImportJob

/** The `import` event for a job that ended with [result] (spec §8.4): buckets and shares only, never names or titles. */
internal fun importEvent(job: ImportJob, result: ImportResult, format: ImportFormat?): AnalyticsEvent.Import = AnalyticsEvent.Import(
    format = when (format) {
        ImportFormat.BibTeX -> ImportFileFormat.Bibtex
        ImportFormat.Ris -> ImportFileFormat.Ris
        null -> null
    },
    result = result,
    entries = LibrarySize.of(job.counts.found),
    matched = MatchedShare.of(job.counts.matched, job.counts.toImport),
    toCollection = job.collectionId != null
)
