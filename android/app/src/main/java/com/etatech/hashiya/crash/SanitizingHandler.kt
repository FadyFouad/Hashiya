package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.sanitizedFatal

/**
 * Passes an uncaught crash on to [next] — Crashlytics' handler in release builds — with only its types and stacks
 * (see [sanitizedFatal]): Crashlytics would otherwise send every level's message, which can hold titles, searches or paths.
 */
fun sanitizingHandler(next: Thread.UncaughtExceptionHandler?) =
    Thread.UncaughtExceptionHandler { thread, error -> next?.uncaughtException(thread, sanitizedFatal(error)) }
