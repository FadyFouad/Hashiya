package com.etatech.hashiya.core.data.pdf

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the startup sweep away from files being written. Anything that writes into the PDF folder and records it (a download,
 * an attach, a restore) runs inside [storing]; the sweep runs inside [sweeping], which waits until no store is running and keeps
 * new ones from starting until it is done.
 */
@Singleton
internal class PdfStoreGate @Inject constructor() {
    private val sweepLock = Mutex()
    private val activeStores = MutableStateFlow(0)

    suspend fun <T> storing(block: suspend () -> T): T {
        sweepLock.withLock { activeStores.update { it + 1 } }
        try {
            return block()
        } finally {
            activeStores.update { it - 1 }
        }
    }

    suspend fun <T> sweeping(block: suspend () -> T): T = sweepLock.withLock {
        activeStores.first { it == 0 }
        block()
    }
}
