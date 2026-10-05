package com.etatech.hashiya.crash

import android.os.Process
import com.etatech.hashiya.core.crash.sanitizedFatal
import kotlin.system.exitProcess

/**
 * Passes an uncaught crash on to [next] — Crashlytics' handler in release builds — with only its types and stacks
 * (see [sanitizedFatal]): Crashlytics would otherwise send every level's message, which can hold titles, searches or paths.
 *
 * The process must end whatever happens here, as it does without this handler: if sanitizing fails (an out-of-memory
 * crash, say), there is no [next], or [next] throws, the process is ended with [terminate] — and the unsanitized crash is
 * never handed on.
 */
fun sanitizingHandler(
    next: Thread.UncaughtExceptionHandler?,
    sanitize: (Throwable) -> Throwable = ::sanitizedFatal,
    terminate: () -> Unit = ::killProcess
) = Thread.UncaughtExceptionHandler { thread, error ->
    val sanitized = try {
        sanitize(error)
    } catch (_: Throwable) {
        null
    }
    if (sanitized == null || next == null) {
        terminate()
    } else {
        try {
            // Crashlytics' handler ends the process itself, after recording.
            next.uncaughtException(thread, sanitized)
        } catch (_: Throwable) {
            terminate()
        }
    }
}

/** How Android's own last-resort handler ends a crashed app. */
private fun killProcess() {
    Process.killProcess(Process.myPid())
    exitProcess(10)
}
