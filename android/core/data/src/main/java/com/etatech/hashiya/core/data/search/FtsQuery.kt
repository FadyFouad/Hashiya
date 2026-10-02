package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.searchableText

private val NOT_LETTER_OR_DIGIT = Regex("""[^\p{L}\p{N}]+""")

/**
 * The FTS MATCH expression for what the user typed, or null when no word is left ("no search").
 * Each word becomes a quoted prefix term and every word must match: "Deep lear" → `"deep*" "lear*"`.
 * Only letters and digits survive, so FTS syntax (quotes, `*`, `-`, parentheses, `column:`, operators) never reaches MATCH.
 * FTS4 reads a prefix only inside the quotes (`"transf*"`); `"transf"*` would match the exact word.
 */
internal fun ftsMatch(query: String): String? = searchableText(query)
    .split(NOT_LETTER_OR_DIGIT)
    .filter { it.isNotEmpty() }
    .takeIf { it.isNotEmpty() }
    ?.joinToString(" ") { "\"$it*\"" }
