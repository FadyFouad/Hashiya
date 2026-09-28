package com.etatech.hashiya.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchableTextTest {
    private fun same(vararg variants: String) {
        val expected = searchableText(variants.first())
        variants.forEach { assertEquals("searchableText(\"$it\")", expected, searchableText(it)) }
    }

    @Test
    fun ignoresCaseAndLatinAccents() {
        assertEquals("schrodinger", searchableText("Schrödinger"))
        same("Schrödinger", "schrodinger", "SCHRÖDINGER", "Schrödinger")
        assertEquals("cafe naive", searchableText("Café Naïve"))
    }

    @Test
    fun removesArabicTashkeel() {
        assertEquals("التعلم", searchableText("التَّعلُّم"))
        same("التعلم", "التَّعلُّم", "اَلتَّعَلُّمُ")
    }

    @Test
    fun removesTatweel() {
        assertEquals("العربية", searchableText("العـــربية"))
    }

    @Test
    fun unifiesAlefForms() {
        assertEquals("احمد", searchableText("أحمد"))
        assertEquals("اسلام", searchableText("إسلام"))
        assertEquals("اية", searchableText("آية"))
        assertEquals("الكتاب", searchableText("ٱلكتاب"))
    }

    @Test
    fun unifiesAlefMaksuraWithYaa() {
        assertEquals("مستشفي", searchableText("مستشفى"))
        same("مستشفى", "مستشفي")
    }

    @Test
    fun handlesMixedArabicAndEnglish() {
        assertEquals("تعلم الالة machine learning", searchableText("تعلُّم الآلة Machine LEARNING"))
    }

    @Test
    fun keepsEmptyAndPunctuationOnlyInputHarmless() {
        assertEquals("", searchableText(""))
        assertEquals("-- !? ()", searchableText("-- !? ()"))
    }

    /** On a Turkish phone, "TITLE".lowercase() would give "tıtle" and never match text indexed elsewhere. */
    @Test
    fun lowercasingIgnoresTheDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("title", searchableText("TITLE"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
