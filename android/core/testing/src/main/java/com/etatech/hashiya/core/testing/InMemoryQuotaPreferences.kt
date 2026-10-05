package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.network.quota.OpenAlexLimits
import com.etatech.hashiya.core.network.quota.QuotaPreferences

class InMemoryQuotaPreferences : QuotaPreferences {
    override var limits: OpenAlexLimits = OpenAlexLimits.Defaults
    override var sharedCallsDay: Long = 0
    override var sharedCalls: Int = 0
    override var sharedUsedUpUntil: Long = 0
    override var keylessUsedUpUntil: Long = 0
}
