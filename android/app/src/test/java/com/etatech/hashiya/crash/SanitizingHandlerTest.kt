package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.ReportedCrash
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SanitizingHandlerTest {
    private val received = mutableListOf<Pair<Thread, Throwable>>()
    private val next = Thread.UncaughtExceptionHandler { thread, error -> received += thread to error }

    @Test
    fun theNextHandlerGetsTheCrashWithoutItsText() {
        val error = IOException("Couldn't open /data/user/0/com.etatech.hashiya/files/pdfs/x.pdf", IllegalStateException("Attention"))
        val thread = Thread.currentThread()

        sanitizingHandler(next).uncaughtException(thread, error)

        val (onThread, sent) = received.single()
        assertSame(thread, onThread)
        assertTrue(sent is ReportedCrash)
        assertEquals("crash: java.io.IOException", sent.message)
        assertEquals(error.stackTrace.toList(), sent.stackTrace.toList())
        assertEquals("crash: java.lang.IllegalStateException", sent.cause!!.message)
        val trace = sent.stackTraceToString()
        assertFalse(trace.contains("/data/user"))
        assertFalse(trace.contains("Attention"))
    }

    @Test
    fun withNoNextHandlerNothingHappens() {
        sanitizingHandler(null).uncaughtException(Thread.currentThread(), RuntimeException("secret"))

        assertTrue(received.isEmpty())
    }
}
