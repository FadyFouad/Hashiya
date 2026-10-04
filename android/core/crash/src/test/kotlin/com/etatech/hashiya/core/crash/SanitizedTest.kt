package com.etatech.hashiya.core.crash

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SanitizedTest {
    @Test
    fun sanitizedDropsMessagesAndCauses() {
        val cause = IllegalStateException("Deep learning in /data/user/0/com.etatech.hashiya/files/pdfs/x.pdf")
        val error = IOException("Couldn't read 'Attention is all you need'", cause)

        val reported = sanitized(error, CrashSite.Restore)

        assertEquals("restore: java.io.IOException", reported.message)
        assertNull(reported.cause)
        assertEquals(error.stackTrace.toList(), reported.stackTrace.toList())
        val everything = reported.toString() + reported.stackTraceToString()
        assertFalse(everything.contains("Attention"))
        assertFalse(everything.contains("/data/user"))
    }

    @Test
    fun anonymousErrorsStillHaveAType() {
        val reported = sanitized(object : RuntimeException("secret") {}, CrashSite.Export)
        assertFalse(reported.message!!.contains("secret"))
    }
}
