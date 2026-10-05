package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsSwitchTest {
    private val applied = mutableListOf<Boolean>()
    private val sent = mutableListOf<Pair<String, String>>()
    private val logged = mutableListOf<AnalyticsEvent>()
    private val switch = AnalyticsSwitch(
        apply = { applied += it },
        send = { name, value -> sent += name to value },
        logEvent = { logged += it }
    )

    @Test
    fun startsOffAndSendsNothing() {
        assertFalse(switch.isOn)
        switch.setProperty(AnalyticsProperty.Language, Language.Ar)
        assertEquals(emptyList<Pair<String, String>>(), sent)
    }

    @Test
    fun turningOnSendsTheLatestValuesAndLaterOnesDirectly() {
        switch.setProperty(AnalyticsProperty.Language, Language.En)
        switch.setProperty(AnalyticsProperty.Language, Language.Ar)
        switch.setEnabled(true)
        switch.setProperty(AnalyticsProperty.HasOwnKey, YesNo.Yes)
        assertEquals(listOf(true), applied)
        assertEquals(listOf("language" to "ar", "has_own_key" to "yes"), sent)
    }

    @Test
    fun offThenOnSendsTheCachedValuesAgain() {
        switch.setEnabled(true)
        switch.setProperty(AnalyticsProperty.LibrarySizeBucket, LibrarySize.UpTo500)
        switch.setEnabled(false)
        switch.setProperty(AnalyticsProperty.Language, Language.En)
        sent.clear()
        switch.setEnabled(true)
        assertEquals(setOf("library_size_bucket" to "51-500", "language" to "en"), sent.toSet())
    }

    @Test
    fun eventsBeforeTheFirstDecisionAreSentWhenItTurnsOn() {
        switch.log(AnalyticsEvent.ScreenView(Screen.Library))
        switch.log(AnalyticsEvent.PaperRemoved)
        assertEquals(emptyList<AnalyticsEvent>(), logged)

        switch.setEnabled(true)
        switch.log(AnalyticsEvent.ScreenView(Screen.Search))

        assertEquals(
            listOf(AnalyticsEvent.ScreenView(Screen.Library), AnalyticsEvent.PaperRemoved, AnalyticsEvent.ScreenView(Screen.Search)),
            logged
        )
    }

    @Test
    fun eventsBeforeTheFirstDecisionAreDroppedWhenItTurnsOff() {
        switch.log(AnalyticsEvent.ScreenView(Screen.Library))

        switch.setEnabled(false)
        switch.setEnabled(true)

        assertEquals(emptyList<AnalyticsEvent>(), logged)
    }

    @Test
    fun onlyTheFirstTwentyEventsAreKeptBeforeTheFirstDecision() {
        repeat(25) { switch.log(AnalyticsEvent.SearchMore(it + 2)) }

        switch.setEnabled(true)

        assertEquals((2..21).map { AnalyticsEvent.SearchMore(it) }, logged)
    }

    @Test
    fun afterTheFirstDecisionEventsAreSentOnlyWhileOn() {
        switch.setEnabled(true)
        switch.setEnabled(false)
        switch.log(AnalyticsEvent.PaperRemoved)
        switch.setEnabled(true)
        switch.log(AnalyticsEvent.ScreenView(Screen.Settings))

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(Screen.Settings)), logged)
    }
}
