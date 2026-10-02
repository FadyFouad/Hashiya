package com.etatech.hashiya.feature.paperdetails

private const val SYSTEM_CLIPBOARD_CONFIRMATION_SDK = 33

/** What to show after copying: Android 13 and later confirm copies themselves, so only older versions get "BibTeX copied". */
internal fun copyConfirmation(complete: Boolean, sdkInt: Int): PaperDetailsMessage? = when {
    !complete -> PaperDetailsMessage.BibTeXIncomplete
    sdkInt < SYSTEM_CLIPBOARD_CONFIRMATION_SDK -> PaperDetailsMessage.BibTeXCopied
    else -> null
}
