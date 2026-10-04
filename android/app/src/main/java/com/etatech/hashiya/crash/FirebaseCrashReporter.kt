package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.CrashSite
import com.etatech.hashiya.core.crash.sanitized
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Release builds' reporter. Everything it sends goes through [sanitized]; keys come from closed lists. */
class FirebaseCrashReporter internal constructor(
    private val setCollectionEnabled: (Boolean) -> Unit,
    private val deleteUnsentReports: () -> Unit,
    private val setCustomKey: (String, String) -> Unit,
    private val recordException: (Throwable) -> Unit,
    private val isCollectionEnabled: () -> Boolean
) : CrashReporter {
    constructor(crashlytics: FirebaseCrashlytics) : this(
        crashlytics::setCrashlyticsCollectionEnabled,
        crashlytics::deleteUnsentReports,
        crashlytics::setCustomKey,
        crashlytics::recordException,
        crashlytics::isCrashlyticsCollectionEnabled
    )

    override fun setEnabled(enabled: Boolean) {
        setCollectionEnabled(enabled)
        if (!enabled) deleteUnsentReports()
    }

    override fun setKey(key: CrashKey, value: String) = setCustomKey(key.id, value)

    // Crashlytics keeps a non-fatal recorded while collection is off and sends it once collection is on again.
    override fun recordNonFatal(error: Throwable, site: CrashSite) {
        if (isCollectionEnabled()) recordException(sanitized(error, site))
    }
}
