package com.etatech.hashiya.analytics

import android.os.Bundle
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.AnalyticsSwitch
import com.etatech.hashiya.core.analytics.ClosedValue
import com.google.firebase.analytics.FirebaseAnalytics

/** Release builds' usage statistics: only the closed events and properties of `:core:analytics`. Nothing while off. */
class FirebaseAnalyticsTracker internal constructor(
    private val logEvent: (String, Map<String, String>) -> Unit,
    setUserProperty: (String, String) -> Unit,
    private val setCollectionEnabled: (Boolean) -> Unit,
    private val resetAnalyticsData: () -> Unit,
    private val denyAdsConsent: () -> Unit
) : Analytics {
    constructor(analytics: FirebaseAnalytics) : this(
        logEvent = { name, parameters ->
            analytics.logEvent(name, Bundle().apply { parameters.forEach { (key, value) -> putString(key, value) } })
        },
        setUserProperty = analytics::setUserProperty,
        setCollectionEnabled = analytics::setAnalyticsCollectionEnabled,
        resetAnalyticsData = analytics::resetAnalyticsData,
        denyAdsConsent = {
            analytics.setConsent(
                mapOf(
                    FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                    FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                    FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
                )
            )
        }
    )

    private val switch = AnalyticsSwitch(
        apply = { enabled ->
            denyAdsConsent()
            setCollectionEnabled(enabled)
            if (!enabled) resetAnalyticsData()
        },
        send = setUserProperty
    )

    override fun log(event: AnalyticsEvent) {
        if (switch.isOn) logEvent(event.name, event.parameters)
    }

    override fun setProperty(property: AnalyticsProperty, value: ClosedValue) = switch.setProperty(property, value)

    override fun setEnabled(enabled: Boolean) = switch.setEnabled(enabled)
}
