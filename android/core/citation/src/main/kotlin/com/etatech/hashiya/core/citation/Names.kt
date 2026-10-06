@file:Suppress("ktlint")

package com.etatech.hashiya.core.citation

/** A person's family name and initials; [initials] is null when the name is kept whole. */
internal data class PersonName(val family: String, val initials: String?)

private val WHITESPACE = Regex("\\s+")

/** "Aidan N. Gomez" → Gomez, A. N. One-word names (organisations) and Arabic-script names are kept whole. */
internal fun personName(name: String): PersonName {
    val trimmed = name.trim().replace(WHITESPACE, " ")
    val parts = trimmed.split(' ')
    if (parts.size < 2 || trimmed.any { it in '؀'..'ۿ' }) return PersonName(trimmed, null)
    val initials = parts.dropLast(1).joinToString(" ") { given ->
        given.split('-').filter { it.isNotEmpty() }.joinToString("-") { "${it.first().uppercaseChar()}." }
    }
    return PersonName(parts.last(), initials)
}
