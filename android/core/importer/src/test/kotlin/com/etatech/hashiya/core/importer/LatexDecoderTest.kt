package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexDecoderTest {
    private fun decoded(raw: String) = LatexDecoder.decode(raw)

    @Test
    fun `symbol accents take the next character or a braced one`() {
        assertEquals("é", decoded("\\'e"))
        assertEquals("é", decoded("\\'{e}"))
        assertEquals("é", decoded("{\\'e}"))
        assertEquals("è", decoded("\\`e"))
        assertEquals("ê", decoded("\\^e"))
        assertEquals("ü", decoded("{\\\"u}"))
        assertEquals("ñ", decoded("\\~n"))
        assertEquals("ā", decoded("\\=a"))
        assertEquals("ż", decoded("\\.z"))
    }

    @Test
    fun `letter accents take a braced or a following character`() {
        assertEquals("ă", decoded("\\u{a}"))
        assertEquals("š", decoded("\\v{s}"))
        assertEquals("ř", decoded("{\\v r}"))
        assertEquals("ő", decoded("\\H{o}"))
        assertEquals("ç", decoded("\\c{c}"))
        assertEquals("ç", decoded("\\c c"))
        assertEquals("ą", decoded("\\k{a}"))
        assertEquals("å", decoded("\\r{a}"))
        assertEquals("ạ", decoded("\\d{a}"))
        assertEquals("a\u0331", decoded("\\b{a}"))
        assertEquals("o\u0361o", decoded("\\t{oo}"))
    }

    @Test
    fun `dotless i and j under an accent become i and j`() {
        assertEquals("ï", decoded("\\\"{\\i}"))
        assertEquals("í", decoded("{\\'\\i}"))
        assertEquals("ĵ", decoded("\\^{\\j}"))
    }

    @Test
    fun `the output is NFC, so accents compose`() {
        assertEquals("\u00E9", decoded("\\'e"))
        assertEquals(1, decoded("\\\"o").length)
    }

    @Test
    fun `named letters swallow one space and an empty group`() {
        assertEquals("Straße", decoded("Stra{\\ss}e"))
        assertEquals("Straße", decoded("Stra\\ss e"))
        assertEquals("Straße", decoded("Stra\\ss{}e"))
        assertEquals("åÅæÆœŒøØłŁıȷ", decoded("{\\aa}{\\AA}{\\ae}{\\AE}{\\oe}{\\OE}{\\o}{\\O}{\\l}{\\L}{\\i}{\\j}"))
    }

    @Test
    fun `escapes give the character`() {
        assertEquals("& % $ # _ { }", decoded("\\& \\% \\$ \\# \\_ \\{ \\}"))
    }

    @Test
    fun `control space and line break give a space`() {
        assertEquals("a b c", decoded("a\\ b\\\\c"))
    }

    @Test
    fun `hyphenation and spacing hints`() {
        assertEquals("hyphenation", decoded("hy\\-phen\\/ation\\@"))
        assertEquals("a b", decoded("a\\,b"))
    }

    @Test
    fun `text symbols`() {
        assertEquals(
            "– — ‘ ’ “ ” … … … ~ ^ \\ _ { } § ¶ © ® ™ † LaTeX TeX",
            decoded(
                "\\textendash{} \\textemdash{} \\textquoteleft{} \\textquoteright{} \\textquotedblleft{} \\textquotedblright{} " +
                    "\\ldots{} \\dots{} \\textellipsis{} \\textasciitilde{} \\textasciicircum{} \\textbackslash{} \\textunderscore{} " +
                    "\\textbraceleft{} \\textbraceright{} \\S{} \\P{} \\copyright{} \\textregistered{} \\texttrademark{} \\dag{} \\LaTeX{} \\TeX"
            )
        )
    }

    @Test
    fun `Greek letters, in or out of math`() {
        assertEquals("α-Stability", decoded("\$\\alpha\$-Stability"))
        assertEquals(
            "αβγδεζηθικλμνξπρστυφχψω",
            decoded(
                "\\alpha\\beta\\gamma\\delta\\epsilon\\zeta\\eta\\theta\\iota\\kappa\\lambda\\mu\\nu\\xi\\pi\\rho\\sigma\\tau" +
                    "\\upsilon\\phi\\chi\\psi\\omega"
            )
        )
        assertEquals("ΓΔΘΛΞΠΣΥΦΨΩ", decoded("\\Gamma\\Delta\\Theta\\Lambda\\Xi\\Pi\\Sigma\\Upsilon\\Phi\\Psi\\Omega"))
    }

    @Test
    fun `other commands are dropped and their argument kept`() {
        assertEquals("the Approach", decoded("the \\emph{Approach}"))
        assertEquals("bold italic", decoded("\\textbf{bold} \\textit{italic}"))
        assertEquals("https://x.org", decoded("\\url{https://x.org}"))
        assertEquals("نص", decoded("\\RL{نص}"))
        assertEquals("text", decoded("\\href{https://x.org}{text}"))
    }

    @Test
    fun `braces, dollars, ties, dashes and quotes`() {
        assertEquals("Deep Residual Learning", decoded("Deep {{Residual Learning}}"))
        assertEquals("O(n^2)", decoded("\$O(n^2)\$"))
        assertEquals("A. Turing", decoded("A.~Turing"))
        assertEquals("a — b – c - d", decoded("a --- b -- c - d"))
        assertEquals("“quoted”", decoded("``quoted''"))
    }

    @Test
    fun `non-Latin text is untouched`() {
        assertEquals("تحليل المشاعر باستخدام BERT", decoded("تحليل المشاعر باستخدام {BERT}"))
    }

    @Test
    fun `a trailing backslash or an unclosed group does not throw`() {
        assertEquals("a", decoded("a\\"))
        assertEquals("é", decoded("\\'{e"))
        assertEquals("", decoded("\\'"))
    }
}
