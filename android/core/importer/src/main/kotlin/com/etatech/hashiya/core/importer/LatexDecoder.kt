package com.etatech.hashiya.core.importer

import java.text.Normalizer

/** Turns the LaTeX in a BibTeX value into plain text (spec §4.2). Whitespace is left for [collapse]. */
internal object LatexDecoder {
    /** Combining marks for the accent commands, placed after the accented character. */
    private val ACCENTS = mapOf(
        '\'' to '́',
        '`' to '̀',
        '^' to '̂',
        '"' to '̈',
        '~' to '̃',
        '=' to '̄',
        '.' to '̇',
        'u' to '̆',
        'v' to '̌',
        'H' to '̋',
        'c' to '̧',
        'k' to '̨',
        'r' to '̊',
        'd' to '̣',
        'b' to '̱',
        't' to '͡'
    )

    /** Control words that stand for text. */
    private val WORDS = mapOf(
        "ss" to "ß", "aa" to "å", "AA" to "Å", "ae" to "æ", "AE" to "Æ", "oe" to "œ", "OE" to "Œ",
        "o" to "ø", "O" to "Ø", "l" to "ł", "L" to "Ł", "i" to "ı", "j" to "ȷ",
        "textendash" to "–", "textemdash" to "—", "textquoteleft" to "‘", "textquoteright" to "’",
        "textquotedblleft" to "“", "textquotedblright" to "”", "ldots" to "…", "dots" to "…", "textellipsis" to "…",
        "textasciitilde" to "~", "textasciicircum" to "^", "textbackslash" to "\\", "textunderscore" to "_",
        "textbraceleft" to "{", "textbraceright" to "}", "S" to "§", "P" to "¶", "copyright" to "©",
        "textregistered" to "®", "texttrademark" to "™", "dag" to "†", "LaTeX" to "LaTeX", "TeX" to "TeX",
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ",
        "eta" to "η", "theta" to "θ", "iota" to "ι", "kappa" to "κ", "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ", "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω", "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
        "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω"
    )

    fun decode(raw: String): String {
        val out = StringBuilder(raw.length)
        decodeRange(raw, 0, raw.length, out)
        return Normalizer.normalize(out, Normalizer.Form.NFC)
    }

    private fun decodeRange(s: String, start: Int, end: Int, out: StringBuilder) {
        var i = start
        while (i < end) {
            val c = s[i]
            when {
                c == '\\' -> i = command(s, i, end, out)

                c == '{' || c == '}' || c == '$' -> i++

                c == '~' -> {
                    out.append(' ')
                    i++
                }

                s.startsWith("---", i) && i + 3 <= end -> {
                    out.append('—')
                    i += 3
                }

                s.startsWith("--", i) && i + 2 <= end -> {
                    out.append('–')
                    i += 2
                }

                s.startsWith("``", i) && i + 2 <= end -> {
                    out.append('“')
                    i += 2
                }

                s.startsWith("''", i) && i + 2 <= end -> {
                    out.append('”')
                    i += 2
                }

                else -> {
                    out.append(c)
                    i++
                }
            }
        }
    }

    /** Decodes the command at [at] (a backslash) and returns the index after it. */
    private fun command(s: String, at: Int, end: Int, out: StringBuilder): Int {
        val next = at + 1
        if (next >= end) return end
        val n = s[next]
        if (!n.isAsciiLetter()) {
            ACCENTS[n]?.let { mark -> return accent(s, next + 1, end, mark, out) }
            when (n) {
                '-', '/', '@' -> Unit
                ' ', '\\', ',' -> out.append(' ')
                else -> out.append(n)
            }
            return next + 1
        }
        var j = next
        while (j < end && s[j].isAsciiLetter()) j++
        val name = s.substring(next, j)
        val mark = if (name.length == 1) ACCENTS[name[0]] else null
        if (mark != null) return accent(s, j, end, mark, out)
        WORDS[name]?.let { text ->
            out.append(text)
            return swallowAfterWord(s, j, end)
        }
        if (name == "href") return skipGroup(s, skipSpaces(s, j, end), end)
        // Any other command: drop its name, keep what follows (its braced argument is decoded as ordinary text).
        return swallowAfterWord(s, j, end)
    }

    /** A control word swallows one following space, or an empty group right after it. */
    private fun swallowAfterWord(s: String, at: Int, end: Int): Int = when {
        at + 1 < end && s[at] == '{' && s[at + 1] == '}' -> at + 2
        at < end && s[at] == ' ' -> at + 1
        else -> at
    }

    private fun accent(s: String, at: Int, end: Int, mark: Char, out: StringBuilder): Int {
        val (arg, after) = argument(s, at, end)
        if (arg.isNotEmpty()) {
            out.append(arg[0]).append(mark).append(arg, 1, arg.length)
        }
        return after
    }

    /** An accent's argument: a braced group or one character, with `\i` and `\j` as plain i and j. */
    private fun argument(s: String, at: Int, end: Int): Pair<String, Int> {
        val i = skipSpaces(s, at, end)
        if (i >= end) return "" to end
        if (s[i] == '{') {
            val close = matchingBrace(s, i, end)
            val inner = s.substring(i + 1, close).trim()
            val text = dotless(inner) ?: StringBuilder().also { decodeRange(inner, 0, inner.length, it) }.toString()
            return text to minOf(close + 1, end)
        }
        if (s[i] == '\\') {
            var j = i + 1
            while (j < end && s[j].isAsciiLetter()) j++
            dotless(s.substring(i, j))?.let { return it to j }
            val text = StringBuilder()
            val after = command(s, i, end, text)
            return text.toString() to after
        }
        return s[i].toString() to i + 1
    }

    private fun dotless(text: String): String? = when (text) {
        "\\i" -> "i"
        "\\j" -> "j"
        else -> null
    }

    private fun skipSpaces(s: String, at: Int, end: Int): Int {
        var i = at
        while (i < end && (s[i] == ' ' || s[i] == '\t')) i++
        return i
    }

    private fun skipGroup(s: String, at: Int, end: Int): Int = if (at < end &&
        s[at] == '{'
    ) {
        minOf(matchingBrace(s, at, end) + 1, end)
    } else {
        at
    }

    /** The index of the brace closing the one at [open], or [end] when it never closes. */
    private fun matchingBrace(s: String, open: Int, end: Int): Int {
        var depth = 0
        for (i in open until end) {
            when (s[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return i
            }
        }
        return end
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
}
