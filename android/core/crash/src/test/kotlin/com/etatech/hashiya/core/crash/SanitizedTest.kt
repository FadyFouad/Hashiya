package com.etatech.hashiya.core.crash

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fails the test if anything reads its text: reporting must never ask an error for its message. */
private class TellTaleException : RuntimeException("Deep learning") {
    override val message: String get() = throw AssertionError("message read")

    override fun getLocalizedMessage(): String = throw AssertionError("localizedMessage read")

    override fun toString(): String = throw AssertionError("toString called")
}

class SanitizedTest {
    @Test
    fun sanitizedDropsMessagesAndCauses() {
        val deeper = IllegalArgumentException("content://docs/My thesis.hashiya")
        val cause = IllegalStateException("Deep learning in /data/user/0/com.etatech.hashiya/files/pdfs/x.pdf", deeper)
        val error = IOException("Couldn't read 'Attention is all you need'", cause)

        val reported = sanitized(error, CrashSite.Restore)

        assertEquals("restore: java.io.IOException", reported.message)
        assertNull(reported.cause)
        assertEquals(error.stackTrace.toList(), reported.stackTrace.toList())
        val everything = reported.toString() + reported.stackTraceToString()
        assertFalse(everything.contains("Attention"))
        assertFalse(everything.contains("/data/user"))
        assertFalse(everything.contains("thesis"))
    }

    @Test
    fun sanitizedDropsSuppressedErrors() {
        val error = IOException("Couldn't read")
        error.addSuppressed(IllegalStateException("Closing 'Attention is all you need'"))

        val reported = sanitized(error, CrashSite.Export)

        assertEquals(0, reported.suppressed.size)
        assertFalse(reported.stackTraceToString().contains("Attention"))
    }

    @Test
    fun sanitizedNeverAsksTheErrorForItsText() {
        val error = TellTaleException()

        val reported = sanitized(error, CrashSite.PdfStore)

        assertEquals("pdfStore: ${TellTaleException::class.java.name}", reported.message)
    }

    @Test
    fun anonymousErrorsStillHaveAType() {
        val error = object : RuntimeException("secret") {}

        val reported = sanitized(error, CrashSite.Export)

        assertEquals("export: ${error::class.java.name}", reported.message)
        assertTrue(error::class.java.name.startsWith("com.etatech.hashiya.core.crash.SanitizedTest"))
        assertFalse(reported.message!!.contains("secret"))
    }
}
