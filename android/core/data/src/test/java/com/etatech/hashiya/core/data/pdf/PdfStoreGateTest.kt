package com.etatech.hashiya.core.data.pdf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfStoreGateTest {
    @Test
    fun sweepWaitsForRunningStores() = runTest {
        val gate = PdfStoreGate()
        val events = mutableListOf<String>()
        val release = CompletableDeferred<Unit>()
        val store = launch {
            gate.storing {
                events += "store start"
                release.await()
                events += "store end"
            }
        }
        yield()
        val sweep = async { gate.sweeping { events += "sweep" } }
        yield()
        release.complete(Unit)
        store.join()
        sweep.await()
        assertEquals(listOf("store start", "store end", "sweep"), events)
    }
}
