package com.etatech.hashiya.analytics

import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.AnalyticsProperty
import com.etatech.hashiya.core.analytics.Language
import com.etatech.hashiya.core.analytics.Screen
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseAnalyticsTrackerTest {
    private val calls = mutableListOf<String>()
    private val tracker = FirebaseAnalyticsTracker(
        logEvent = { name, params -> calls += "log:$name:$params" },
        setUserProperty = { name, value -> calls += "property:$name=$value" },
        setCollectionEnabled = { calls += "collection:$it" },
        resetAnalyticsData = { calls += "reset" },
        denyAdsConsent = { calls += "consent" }
    )

    @Test
    fun nothingIsLoggedOrSentWhileOff() {
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))
        tracker.setProperty(AnalyticsProperty.Language, Language.Ar)

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun turningOnSetsConsentEnablesAndSendsCachedProperties() {
        tracker.setProperty(AnalyticsProperty.Language, Language.Ar)
        tracker.setEnabled(true)
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))

        assertEquals(listOf("consent", "collection:true", "property:language=ar", "log:screen_view:{screen=library}"), calls)
    }

    @Test
    fun turningOffDisablesAndResets() {
        tracker.setEnabled(true)
        calls.clear()

        tracker.setEnabled(false)

        assertEquals(listOf("consent", "collection:false", "reset"), calls)
    }

    /** The first screen view can come before the launch reads the Settings switch; it's held until then. */
    @Test
    fun eventsLoggedBeforeTheLaunchDecisionAreSentAfterTurningOn() {
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))
        tracker.setProperty(AnalyticsProperty.Language, Language.Ar)

        tracker.setEnabled(true)

        assertEquals(listOf("consent", "collection:true", "property:language=ar", "log:screen_view:{screen=library}"), calls)
    }

    @Test
    fun eventsLoggedBeforeTheLaunchDecisionAreDroppedWhenItIsOff() {
        tracker.log(AnalyticsEvent.ScreenView(Screen.Library))

        tracker.setEnabled(false)
        tracker.setEnabled(true)

        assertEquals(listOf("consent", "collection:false", "reset", "consent", "collection:true"), calls)
    }
}
