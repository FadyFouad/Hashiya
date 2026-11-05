package com.etatech.hashiya.core.importer

import com.etatech.hashiya.core.model.searchableText

/** A title reduced for comparison, with the year: the identity of an entry without a DOI. */
data class TitleKey(val title: String, val year: Int?)

/**
 * [title] through searchableText (no case, accents, tashkeel or letter variants), with every run of characters that are not letters
 * or digits as one space. Null when nothing is left.
 */
fun titleKey(title: String, year: Int?): TitleKey? {
    val words = StringBuilder()
    var pendingSpace = false
    for (c in searchableText(title)) {
        if (c.isLetterOrDigit()) {
            if (pendingSpace && words.isNotEmpty()) words.append(' ')
            pendingSpace = false
            words.append(c)
        } else {
            pendingSpace = true
        }
    }
    return words.takeIf { it.isNotEmpty() }?.let { TitleKey(it.toString(), year) }
}

/** The entry's identity for duplicates: its DOI, or its title key when it has none. */
private sealed interface Identity {
    data class ByDoi(val doi: String) : Identity

    data class ByTitle(val key: TitleKey) : Identity
}

private fun ImportedEntry.identity(): Identity? = doi?.let(Identity::ByDoi) ?: titleKey(title, year)?.let(Identity::ByTitle)

/** What is already saved. [importedDois] and [importedTitleKeys] are the papers this import saved before it was interrupted. */
data class ExistingLibrary(
    val dois: Set<String> = emptySet(),
    val titleKeys: Set<TitleKey> = emptySet(),
    val importedDois: Set<String> = emptySet(),
    val importedTitleKeys: Set<TitleKey> = emptySet()
)

sealed interface PlannedEntry {
    val entry: ImportedEntry

    /** To save: looked up on OpenAlex when it has a DOI, else built from the file. */
    data class ToImport(override val entry: ImportedEntry) : PlannedEntry

    /** Already saved before this import: skipped. */
    data class InLibrary(override val entry: ImportedEntry) : PlannedEntry

    /** Saved by this import before it was interrupted: counts as imported. */
    data class AlreadyImported(override val entry: ImportedEntry) : PlannedEntry

    /** The same paper as an earlier entry in the file: skipped. */
    data class Repeated(override val entry: ImportedEntry) : PlannedEntry
}

object ImportPlanner {
    /** One plan per entry, in file order (spec §5). */
    fun plan(entries: List<ImportedEntry>, existing: ExistingLibrary): List<PlannedEntry> {
        val seen = mutableSetOf<Identity>()
        return entries.map { entry ->
            when (val identity = entry.identity()) {
                null -> PlannedEntry.ToImport(entry)

                else -> when {
                    !seen.add(identity) -> PlannedEntry.Repeated(entry)
                    identity.isIn(existing.importedDois, existing.importedTitleKeys) -> PlannedEntry.AlreadyImported(entry)
                    identity.isIn(existing.dois, existing.titleKeys) -> PlannedEntry.InLibrary(entry)
                    else -> PlannedEntry.ToImport(entry)
                }
            }
        }
    }

    private fun Identity.isIn(dois: Set<String>, titleKeys: Set<TitleKey>): Boolean = when (this) {
        is Identity.ByDoi -> doi in dois
        is Identity.ByTitle -> key in titleKeys
    }
}
