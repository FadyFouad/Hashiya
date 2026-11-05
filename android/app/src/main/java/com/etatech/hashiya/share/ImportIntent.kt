package com.etatech.hashiya.share

import android.content.Intent

/** MIME types other apps use for .bib and .ris files (none is registered with IANA, so all three are in use). */
internal val IMPORT_MIME_TYPES = setOf("application/x-bibtex", "text/x-bibtex", "application/x-research-info-systems")

/**
 * The file another app wants imported, as a URI string, or null when the intent isn't one:
 * - VIEW ("Open with") of a .bib or .ris type, matched by the manifest's filter;
 * - VIEW of a text/plain content: file, which is how file managers such as ColorOS My Files open a .bib or .ris;
 * - SEND of a .bib or .ris type with the file as EXTRA_STREAM;
 * - SEND of any text type with a file and no text, which is how many file managers share a .bib or .ris.
 * The file's content then decides whether it is BibTeX, RIS or neither.
 */
internal fun importUri(action: String?, type: String?, data: String?, stream: String?, hasText: Boolean): String? = when (action) {
    Intent.ACTION_VIEW -> data?.takeIf { type in IMPORT_MIME_TYPES || (type == "text/plain" && it.startsWith("content:")) }
    Intent.ACTION_SEND -> stream?.takeIf { type in IMPORT_MIME_TYPES || (type?.startsWith("text/") == true && !hasText) }
    else -> null
}
