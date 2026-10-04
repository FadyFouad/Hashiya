package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.CrashSite

/** Records every call, so tests can check exactly what would be reported. */
class FakeCrashReporter : CrashReporter {
    val enabledCalls = mutableListOf<Boolean>()
    val keys = mutableMapOf<CrashKey, String>()
    val keyHistory = mutableListOf<Pair<CrashKey, String>>()
    val nonFatals = mutableListOf<Pair<Throwable, CrashSite>>()

    override fun setEnabled(enabled: Boolean) {
        synchronized(enabledCalls) {
            enabledCalls += enabled
        }
    }

    override fun setKey(key: CrashKey, value: String) {
        synchronized(keys) {
            keys[key] = value
        }
        synchronized(keyHistory) {
            keyHistory += key to value
        }
    }

    override fun recordNonFatal(error: Throwable, site: CrashSite) {
        synchronized(nonFatals) {
            nonFatals += error to site
        }
    }
}
