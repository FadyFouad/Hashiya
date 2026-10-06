package com.etatech.hashiya.core.model

/** How a citation or a reference list is written; [id] is what the preference and analytics store. */
enum class CitationStyle(val id: String) {
    Apa("apa"),
    Ieee("ieee"),
    Bibtex("bibtex");

    companion object {
        /** APA when nothing, or something unknown, is stored. */
        fun fromId(id: String?): CitationStyle = entries.firstOrNull { it.id == id } ?: Apa
    }
}
