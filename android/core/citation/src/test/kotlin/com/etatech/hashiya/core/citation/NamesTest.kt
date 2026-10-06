package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class NamesTest {
    @Test
    fun familyIsTheLastWordAndInitialsTheRest() {
        assertEquals(PersonName("Vaswani", "A."), personName("Ashish Vaswani"))
        assertEquals(PersonName("Gomez", "A. N."), personName("Aidan N. Gomez"))
        assertEquals(PersonName("Kaiser", "Ł."), personName("  Łukasz   Kaiser "))
    }

    @Test
    fun hyphenatedGivenNamesKeepTheHyphen() = assertEquals(PersonName("Sartre", "J.-P."), personName("Jean-Paul Sartre"))

    @Test
    fun oneWordAndArabicNamesStayWhole() {
        assertEquals(PersonName("OpenAI", null), personName("OpenAI"))
        assertEquals(PersonName("محمد عبد الله", null), personName("محمد عبد الله"))
    }
}
