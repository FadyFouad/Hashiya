package com.etatech.hashiya.core.citation

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderingTest {
    private val citation = StyledCitation(listOf(Run("A & B <x> \"q\". "), Run("Journal", italic = true), Run(", 1.")))

    @Test
    fun plainJoinsTheRuns() = assertEquals("A & B <x> \"q\". Journal, 1.", Rendering.plain(citation))

    @Test
    fun htmlEscapesAndItalicises() = assertEquals("A &amp; B &lt;x&gt; &quot;q&quot;. <i>Journal</i>, 1.", Rendering.html(citation))

    @Test
    fun rtfEscapesControlCharactersAndWritesUnicode() {
        val rtf = Rendering.rtf(listOf(StyledCitation(listOf(Run("a\\b{c}"), Run("ع😀", italic = true)))), hangingIndent = false)
        assertEquals(
            "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n" +
                "{\\pard a\\\\b\\{c\\}{\\i \\u1593?\\u-10179?\\u-8704?}\\par}\n" +
                "}",
            rtf
        )
    }

    @Test
    fun rtfHangingIndentIsOnlyForApa() {
        val one = listOf(StyledCitation(listOf(Run("x"))))
        assertEquals(true, Rendering.rtf(one, hangingIndent = true).contains("{\\pard\\fi-720\\li720 x\\par}"))
        assertEquals(true, Rendering.rtf(one, hangingIndent = false).contains("{\\pard x\\par}"))
    }

    @Test
    fun anEmptyListIsAValidEmptyDocument() = assertEquals(
        "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}",
        Rendering.rtf(emptyList(), hangingIndent = true)
    )
}
