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

/** What a crash report sends for one level of the cause chain: the type and stack, never the message or suppressed errors. */
class ReportedCrash internal constructor(type: String, trace: Array<StackTraceElement>, cause: ReportedCrash?) :
    Exception("crash: $type", cause) {
    init {
        stackTrace = trace
    }

    // Keeps the reporter from capturing this constructor's frames instead of the original ones.
    override fun fillInStackTrace(): Throwable = this
}

/** Deep enough for wrapped errors; a longer chain is cut, and a chain that loops back ends where it repeats. */
private const val MAX_CRASH_CAUSES = 8

/**
 * The uncaught [error] as a crash report may carry it: each level of the cause chain keeps its class name and stack,
 * and loses its message and suppressed errors, which can hold titles, searches, links or file paths.
 */
fun sanitizedFatal(error: Throwable): Throwable {
    val levels = mutableListOf<Throwable>()
    var level: Throwable? = error
    while (level != null && levels.size < MAX_CRASH_CAUSES && levels.none { it === level }) {
        levels += level
        level = level.cause
    }
    return levels.foldRight(null as ReportedCrash?) { original, cause ->
        ReportedCrash(original::class.java.name, original.stackTrace, cause)
    }!!
}
