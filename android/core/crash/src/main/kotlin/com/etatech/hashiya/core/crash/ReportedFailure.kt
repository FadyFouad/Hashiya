package com.etatech.hashiya.core.crash

/** What a non-fatal report sends: the site and the error's type, with its stack — never its message or causes. */
class ReportedFailure internal constructor(site: CrashSite, type: String, trace: Array<StackTraceElement>) :
    Exception("${site.id}: $type") {
    init {
        stackTrace = trace
    }

    // Keeps the reporter from capturing this constructor's frames instead of the original ones.
    override fun fillInStackTrace(): Throwable = this
}

/** Messages and causes can hold titles, searches or file paths, so only the class name and stack survive. */
fun sanitized(error: Throwable, site: CrashSite): ReportedFailure {
    val type = error::class.java.name
    return ReportedFailure(site, type, error.stackTrace)
}
