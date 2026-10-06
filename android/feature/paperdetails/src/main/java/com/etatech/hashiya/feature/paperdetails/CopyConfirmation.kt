package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.CitationStyle

private const val SYSTEM_CLIPBOARD_CONFIRMATION_SDK = 33

/** What to show after copying: Android 13 and later confirm copies themselves, so only older versions get "copied". */
internal fun copyConfirmation(style: CitationStyle, complete: Boolean, sdkInt: Int): PaperDetailsMessage? = when {
    !complete -> PaperDetailsMessage.CitationIncomplete
    sdkInt >= SYSTEM_CLIPBOARD_CONFIRMATION_SDK -> null
    style == CitationStyle.Apa -> PaperDetailsMessage.ApaCopied
    style == CitationStyle.Ieee -> PaperDetailsMessage.IeeeCopied
    else -> PaperDetailsMessage.BibTeXCopied
}

/** The remembered style first, then the others in the menu's usual order. */
internal fun orderedStyles(remembered: CitationStyle): List<CitationStyle> =
    listOf(remembered) + listOf(CitationStyle.Apa, CitationStyle.Ieee, CitationStyle.Bibtex).filter { it != remembered }
