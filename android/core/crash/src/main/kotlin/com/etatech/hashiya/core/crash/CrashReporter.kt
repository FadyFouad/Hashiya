package com.etatech.hashiya.core.crash

/**
 * Reports crashes' context and non-fatal failures. Only `:app` knows the service behind it; everything else depends on this.
 * Keys and sites are closed lists, so free text — titles, searches, notes, paths — can't be attached by accident.
 */
interface CrashReporter {
    /** Starts or stops collection. Stopping also drops reports not yet sent. */
    fun setEnabled(enabled: Boolean)

    fun setKey(key: CrashKey, value: String)

    /** Reports [error] without its message or causes (see [sanitized]). */
    fun recordNonFatal(error: Throwable, site: CrashSite)
}

/** Debug builds and tests: reports nothing. */
object NoOpCrashReporter : CrashReporter {
    override fun setEnabled(enabled: Boolean) = Unit

    override fun setKey(key: CrashKey, value: String) = Unit

    override fun recordNonFatal(error: Throwable, site: CrashSite) = Unit
}

enum class CrashKey(val id: String) {
    Screen("screen"),
    Language("language"),
    LibrarySizeBucket("librarySizeBucket"),
    BackupInProgress("backupInProgress")
}

enum class CrashSite(val id: String) {
    Migration("migration"),
    DatabaseOpen("databaseOpen"),
    Restore("restore"),
    Export("export"),
    PdfStore("pdfStore"),
    UnexpectedUiError("unexpectedUiError")
}
