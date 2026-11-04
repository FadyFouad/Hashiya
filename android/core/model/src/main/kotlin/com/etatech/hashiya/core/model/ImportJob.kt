package com.etatech.hashiya.core.model

enum class ImportState {
    /** Reading the file and matching DOIs with OpenAlex; nothing is saved yet. */
    Analyzing,

    /** The preview: waiting for the user to confirm. */
    Ready,

    Importing,
    Done,
    Failed,
    Cancelled
}

enum class ImportFailure {
    /** Neither BibTeX nor RIS. */
    Unsupported,

    /** Over the 10 MB limit. */
    TooLarge,

    /** The file couldn't be read, or saving failed. */
    Unreadable,

    /** Every entry is already in the library or repeated in the file. */
    NothingToImport
}

/** What an import found and will do. [toImport] papers are saved: [matched] from OpenAlex and [local] from the file. */
data class ImportCounts(
    /** Entries read from the file. */
    val found: Int = 0,
    val matched: Int = 0,
    /** Saved from the file's own fields: no DOI, a DOI OpenAlex doesn't know, or one that couldn't be checked. */
    val local: Int = 0,
    val inLibrary: Int = 0,
    /** The same paper again later in the file. */
    val repeated: Int = 0,
    /** Entries that couldn't be read (malformed, or with nothing to import). */
    val unreadable: Int = 0,
    /** Part of [local]: DOIs that couldn't be looked up (offline, OpenAlex's limit, or the per-import cap). */
    val notChecked: Int = 0
) {
    val toImport: Int get() = matched + local
}

data class ImportJob(
    val id: Long,
    val fileName: String,
    val state: ImportState,
    val counts: ImportCounts = ImportCounts(),
    /** Entries handled so far in the import phase, out of [ImportCounts.toImport]. */
    val processed: Int = 0,
    /** Papers saved by this job. */
    val imported: Int = 0,
    val addToCollection: Boolean = true,
    /** The new collection's name: the file name without its extension, made unique when the import starts. */
    val collectionName: String = "",
    /** The collection the papers went into, once created. */
    val collectionId: Long? = null,
    val failure: ImportFailure? = null
) {
    val isRunning: Boolean get() = state == ImportState.Analyzing || state == ImportState.Importing
}
