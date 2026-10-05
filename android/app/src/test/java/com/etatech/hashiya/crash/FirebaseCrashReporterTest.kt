package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashSite
import com.etatech.hashiya.core.crash.ReportedFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseCrashReporterTest {
    private val calls = mutableListOf<String>()
    private val recorded = mutableListOf<Throwable>()
    private var collecting = true

    private val reporter = FirebaseCrashReporter(
        setCollectionEnabled = { calls += "collection:$it" },
        deleteUnsentReports = { calls += "delete" },
        setCustomKey = { key, value -> calls += "key:$key=$value" },
        recordException = { recorded += it },
        isCollectionEnabled = { collecting }
    )

    @Test
    fun switchingOffStopsCollectionThenDeletesUnsentReports() {
        reporter.setEnabled(false)

        assertEquals(listOf("collection:false", "delete"), calls)
    }

    @Test
    fun switchingOnOnlyStartsCollection() {
        reporter.setEnabled(true)

        assertEquals(listOf("collection:true"), calls)
    }

    @Test
    fun keysUseTheirClosedIds() {
        reporter.setKey(CrashKey.LibrarySizeBucket, "1-50")

        assertEquals(listOf("key:librarySizeBucket=1-50"), calls)
    }

    @Test
    fun nonFatalsSendTheSanitizedFailureNeverTheOriginal() {
        val original = IllegalStateException("My secret thesis.pdf")

        reporter.recordNonFatal(original, CrashSite.Restore)

        val sent = recorded.single()
        assertTrue(sent is ReportedFailure)
        assertNotSame(original, sent)
        assertEquals("restore: java.lang.IllegalStateException", sent.message)
        assertEquals(null, sent.cause)
    }

    @Test
    fun nonFatalsAreDroppedWhileCollectionIsOff() {
        collecting = false

        reporter.recordNonFatal(IllegalStateException("My secret thesis.pdf"), CrashSite.Export)

        assertTrue(recorded.isEmpty())
    }
}
