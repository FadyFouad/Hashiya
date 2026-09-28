package com.etatech.hashiya.core.model

import java.text.Normalizer
import java.util.Locale

private val COMBINING_MARKS = Regex("""\p{M}+""")

// Tatweel (kashida), the Arabic line-stretching character.
private const val TATWEEL = "ـ"

// أ إ آ ٱ: alef with hamza above, hamza below, madda, and alef wasla.
private val ALEF_VARIANTS = Regex("[أإآٱ]")
private const val ALEF = "ا"
private const val ALEF_MAKSURA = 'ى'
private const val YAA = 'ي'

/** Lowercased text with accents and marks removed, Arabic letter variants unified and digits in ASCII, for full-text search. */
fun searchableText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
    .replace(COMBINING_MARKS, "")
    .replace(TATWEEL, "")
    .replace(ALEF_VARIANTS, ALEF)
    .replace(ALEF_MAKSURA, YAA)
    .withAsciiDigits()
    .lowercase(Locale.ROOT)

// Every decimal digit (Arabic-Indic ١٩, Persian ۱۹, ...) as its ASCII digit, so "١٩" and "19" find each other.
private fun String.withAsciiDigits(): String = buildString(length) {
    for (char in this@withAsciiDigits) append(if (char.isDigit()) '0' + char.digitToInt() else char)
}

// Arabic diacritics (tashkeel, Quranic marks): U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06DC, U+06DF–U+06E4, U+06E7–U+06E8, U+06EA–U+06ED.
private val ARABIC_MARKS = Regex("[ؐ-ًؚ-ٰٟۖ-ۜ۟-ۤۧ-۪ۨ-ۭ]")

/** [text] without Arabic diacritics (tashkeel, Quranic marks) and tatweel; everything else is kept as typed. */
fun withoutArabicMarks(text: String): String = text
    .replace(ARABIC_MARKS, "")
    .replace(TATWEEL, "")
