package com.etatech.hashiya.core.bibtex

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexTextTest {
    @Test
    fun escapesEverySpecialCharacter() {
        assertEquals("R\\&D 50\\% \\$5 \\#1 a\\_b \\{x\\}", escapeLatex("R&D 50% $5 #1 a_b {x}"))
        assertEquals("a\\textasciitilde{}b\\textasciicircum{}c", escapeLatex("a~b^c"))
        assertEquals("C:\\textbackslash{}dir", escapeLatex("C:\\dir"))
    }

    @Test
    fun keepsUnicode() {
        assertEquals("Jörg Müller · تعلم", escapeLatex("Jörg Müller · تعلم"))
    }

    @Test
    fun collapsesWhitespace() {
        assertEquals("Deep learning for graphs", cleanWhitespace("  Deep\nlearning \t for   graphs "))
    }

    @Test
    fun protectsWordsWithInnerCapitals() {
        assertEquals("{BERT:} Pre-training of Deep Models", protectCapitals("BERT: Pre-training of Deep Models"))
        assertEquals("{ImageNet} and {COVID-19} on an {iPhone}", protectCapitals("ImageNet and COVID-19 on an iPhone"))
        assertEquals("The deep A", protectCapitals("The deep A"))
        assertEquals("تعلم {GPU}", protectCapitals("تعلم GPU"))
    }
}
