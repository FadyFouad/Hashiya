package com.etatech.hashiya.core.network.quota

import android.content.Context
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What the OpenAlex quota remembers between launches: the config's limits and the counters. Times are epoch millis. */
interface QuotaPreferences {
    var limits: OpenAlexLimits
    var sharedCallsDay: Long
    var sharedCalls: Int
    var sharedUsedUpUntil: Long
    var keylessUsedUpUntil: Long
}

/** A private preferences file; callers serialize access, so reads and writes here are plain and synchronous. */
class SharedPreferencesQuotaPreferences(context: Context) : QuotaPreferences {
    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override var limits: OpenAlexLimits
        get() = OpenAlexLimits(
            dailyDeviceCalls = preferences.getInt(DAILY_CALLS, OpenAlexLimits.Defaults.dailyDeviceCalls),
            maxPagesPerQuery = preferences.getInt(MAX_PAGES, OpenAlexLimits.Defaults.maxPagesPerQuery),
            baseUrl = preferences.getString(BASE_URL, null)?.toHttpUrlOrNull()
        )
        set(value) {
            preferences.edit()
                .putInt(DAILY_CALLS, value.dailyDeviceCalls)
                .putInt(MAX_PAGES, value.maxPagesPerQuery)
                .apply { if (value.baseUrl == null) remove(BASE_URL) else putString(BASE_URL, value.baseUrl.toString()) }
                .apply()
        }

    override var sharedCallsDay: Long
        get() = preferences.getLong(SHARED_CALLS_DAY, 0)
        set(value) = preferences.edit().putLong(SHARED_CALLS_DAY, value).apply()

    override var sharedCalls: Int
        get() = preferences.getInt(SHARED_CALLS, 0)
        set(value) = preferences.edit().putInt(SHARED_CALLS, value).apply()

    override var sharedUsedUpUntil: Long
        get() = preferences.getLong(SHARED_USED_UP_UNTIL, 0)
        set(value) = preferences.edit().putLong(SHARED_USED_UP_UNTIL, value).apply()

    override var keylessUsedUpUntil: Long
        get() = preferences.getLong(KEYLESS_USED_UP_UNTIL, 0)
        set(value) = preferences.edit().putLong(KEYLESS_USED_UP_UNTIL, value).apply()

    private companion object {
        const val FILE = "openalex_quota"
        const val DAILY_CALLS = "limits.dailyDeviceCalls"
        const val MAX_PAGES = "limits.maxPagesPerQuery"
        const val BASE_URL = "limits.baseUrl"
        const val SHARED_CALLS_DAY = "sharedCallsDay"
        const val SHARED_CALLS = "sharedCalls"
        const val SHARED_USED_UP_UNTIL = "sharedUsedUpUntil"
        const val KEYLESS_USED_UP_UNTIL = "keylessUsedUpUntil"
    }
}
