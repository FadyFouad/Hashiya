package com.etatech.hashiya.core.network.model

/** OpenAlex ships abstracts as word → positions. Rebuilds the plain text, or null if there is none. */
fun rebuildAbstract(index: Map<String, List<Int>>?): String? {
    if (index.isNullOrEmpty()) return null
    return index
        .flatMap { (word, positions) -> positions.map { position -> position to word } }
        .sortedBy { it.first }
        .joinToString(" ") { it.second }
        .ifBlank { null }
}
