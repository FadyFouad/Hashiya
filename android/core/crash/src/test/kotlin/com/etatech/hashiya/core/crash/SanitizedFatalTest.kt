package com.etatech.hashiya.core.crash

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SanitizedFatalTest {
    private fun chainOf(error: Throwable): List<Throwable> = generateSequence(error) { it.cause }.take(20).toList()

    @Test
    fun keepsEachLevelsTypeAndFramesButNoText() {
        val deepest = IllegalArgumentException("content://docs/My thesis.hashiya")
        val middle = IllegalStateException("Deep learning in /data/user/0/com.etatech.hashiya/files/pdfs/x.pdf", deepest)
        val error = IOException("Couldn't open 'Attention is all you need'", middle)

        val reported = sanitizedFatal(error)

        val original = chainOf(error)
        val sent = chainOf(reported)
        assertEquals(3, sent.size)
        sent.zip(original).forEach { (level, from) ->
            assertEquals("crash: ${from::class.java.name}", level.message)
            assertEquals(from.stackTrace.toList(), level.stackTrace.toList())
        }
        val everything = sent.joinToString { it.toString() } + reported.stackTraceToString()
        listOf("Attention", "Deep learning", "/data/user", "thesis", "content://").forEach {
            assertFalse(it, everything.contains(it))
        }
    }

    @Test
    fun dropsSuppressedErrorsAtEveryLevel() {
        val cause = IllegalStateException("inner")
        cause.addSuppressed(IOException("Closing 'Attention is all you need'"))
        val error = RuntimeException("outer", cause)
        error.addSuppressed(IOException("Closing /data/user/0/x.pdf"))

        val reported = sanitizedFatal(error)

        chainOf(reported).forEach { assertEquals(0, it.suppressed.size) }
        val trace = reported.stackTraceToString()
        assertFalse(trace.contains("Attention"))
        assertFalse(trace.contains("/data/user"))
    }

    @Test
    fun neverAsksAnyLevelForItsText() {
        val error = RuntimeException("outer", TellTale())

        val reported = sanitizedFatal(error)

        assertEquals("crash: ${TellTale::class.java.name}", reported.cause!!.message)
    }

    @Test
    fun aCauseCycleEnds() {
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second", first)
        first.initCause(second)

        val reported = sanitizedFatal(first)

        val sent = chainOf(reported)
        assertEquals(2, sent.size)
        assertNull(sent.last().cause)
    }

    @Test
    fun aVeryLongChainIsCut() {
        var error: Throwable = IllegalStateException("bottom")
        repeat(30) { error = RuntimeException("level $it", error) }

        val sent = chainOf(sanitizedFatal(error))

        assertEquals(8, sent.size)
        assertNull(sent.last().cause)
    }

    private class TellTale : RuntimeException("Deep learning") {
        override val message: String get() = throw AssertionError("message read")

        override fun getLocalizedMessage(): String = throw AssertionError("localizedMessage read")

        override fun toString(): String = throw AssertionError("toString called")
    }
}
