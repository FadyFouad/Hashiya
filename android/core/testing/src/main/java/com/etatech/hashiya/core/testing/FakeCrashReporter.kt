package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.CrashSite

/** Records every call, so tests can check exactly what would be reported. Safe to call from any thread. */
class FakeCrashReporter : CrashReporter {
    private val lock = Any()
    private val _enabledCalls = mutableListOf<Boolean>()
    private val _keys = mutableMapOf<CrashKey, String>()
    private val _keyHistory = mutableListOf<Pair<CrashKey, String>>()
    private val _nonFatals = mutableListOf<Pair<Throwable, CrashSite>>()

    val enabledCalls: List<Boolean> get() = synchronized(lock) { _enabledCalls.toList() }
    val keys: Map<CrashKey, String> get() = synchronized(lock) { _keys.toMap() }
    val keyHistory: List<Pair<CrashKey, String>> get() = synchronized(lock) { _keyHistory.toList() }
    val nonFatals: List<Pair<Throwable, CrashSite>> get() = synchronized(lock) { _nonFatals.toList() }

    override fun setEnabled(enabled: Boolean) {
        synchronized(lock) { _enabledCalls += enabled }
    }

    override fun setKey(key: CrashKey, value: String) {
        synchronized(lock) {
            _keys[key] = value
            _keyHistory += key to value
        }
    }

    override fun recordNonFatal(error: Throwable, site: CrashSite) {
        synchronized(lock) { _nonFatals += error to site }
    }
}
