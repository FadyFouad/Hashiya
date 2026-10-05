package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnalyticsSwitchTest {
    private val applied = mutableListOf<Boolean>()
    private val sent = mutableListOf<Pair<String, String>>()
    private val switch = AnalyticsSwitch(apply = { applied += it }, send = { name, value -> sent += name to value })

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
}
