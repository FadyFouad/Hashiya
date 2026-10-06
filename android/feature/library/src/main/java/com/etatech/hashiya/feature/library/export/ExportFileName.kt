package com.etatech.hashiya.feature.library.export

import com.etatech.hashiya.core.model.CitationStyle

private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

/**
 * "hashiya-library" for the whole library; otherwise the collection's name with characters files can't hold replaced by "-".
 * BibTeX is ".bib"; the other styles are " – APA.rtf" and " – IEEE.rtf".
 */
internal fun exportFileName(collectionName: String?, style: CitationStyle): String {
    val base = if (collectionName == null) {
        "hashiya-library"
    } else {
        collectionName.replace(UNSAFE_FILE_NAME_CHARACTERS, "-").trim().ifEmpty { "collection" }
    }
    return base + when (style) {
        CitationStyle.Bibtex -> ".bib"
        CitationStyle.Apa -> " – APA.rtf"
        CitationStyle.Ieee -> " – IEEE.rtf"
    }
}
