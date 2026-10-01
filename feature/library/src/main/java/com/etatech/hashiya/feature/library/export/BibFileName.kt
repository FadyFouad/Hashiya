package com.etatech.hashiya.feature.library.export

private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

/** "hashiya-library.bib" for the whole library; otherwise the collection's name with characters files can't hold replaced by "-". */
internal fun bibFileName(collectionName: String?): String {
    if (collectionName == null) return "hashiya-library.bib"
    val safe = collectionName.replace(UNSAFE_FILE_NAME_CHARACTERS, "-").trim()
    return safe.ifEmpty { "collection" } + ".bib"
}
