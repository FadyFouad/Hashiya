package com.etatech.hashiya.core.model

/** A paper identifier recognized in text typed or shared by the user. */
sealed interface PaperIdentifier {
    /** A DOI in the canonical form returned by [normalizeDoi], e.g. "10.1038/nature14539". */
    data class Doi(val value: String) : PaperIdentifier

    /** An arXiv ID without version or subject class, e.g. "1706.03762", "hep-th/9901001", "math/0309136". */
    data class Arxiv(val id: String) : PaperIdentifier
}

private const val MAX_SHARED_TEXT = 2_000

// New style, optional version: YYMM.NNNN from 0704 (when it began) to 1412, YYMM.NNNNN from 1501 on.
private const val MONTH = "(?:0[1-9]|1[0-2])"
private const val NEW_ARXIV_4 = """(?:07(?:0[4-9]|1[0-2])|(?:0[89]|1[0-4])$MONTH)\.\d{4}"""
private const val NEW_ARXIV_5 = """(?:1[5-9]|[2-9]\d)$MONTH\.\d{5}"""
private const val NEW_ARXIV = """(?:$NEW_ARXIV_4|$NEW_ARXIV_5)(?:v\d+)?"""

// Old style: archive[.SUBJECT]/YYMMNNN, optional version.
private const val OLD_ARXIV = """[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/\d{7}(?:v\d+)?"""
private const val ANY_ARXIV = "(?:$NEW_ARXIV|$OLD_ARXIV)"

private val ARXIV_URL = Regex(
    """(?:https?://)?(?:www\.|export\.)?arxiv\.org/(?:abs|pdf)/($ANY_ARXIV)(?:\.pdf)?/?""",
    RegexOption.IGNORE_CASE
)
private val ARXIV_PREFIXED = Regex("""arxiv:($ANY_ARXIV)""", RegexOption.IGNORE_CASE)
private val BARE_ARXIV = Regex(ANY_ARXIV, RegexOption.IGNORE_CASE)
private val ARXIV_DOI = Regex("""10\.48550/arxiv\.($ANY_ARXIV)""", RegexOption.IGNORE_CASE)
private val BARE_DOI = Regex("""10\.\d{4,9}/\S+""")
private val DOI_IN_PATH = Regex("""10\.\d{4,9}/.+""")
private val SPACE_AFTER_PREFIX = Regex("""(?i)\b(arxiv:|doi:)\s+""")
private val WHITESPACE = Regex("""\s+""")
private val URL_START = Regex("""(?i)(https?://|www\.|(?:export\.)?arxiv\.org/|(?:dx\.)?doi\.org/)""")
private val DOI_HOSTS = setOf("doi.org", "dx.doi.org", "www.doi.org")
private const val TRAILING_JUNK = ".,;:!?\"'>]"
private const val LEADING_JUNK = "([\"'<"

/** Strict: the whole trimmed [text] must be an identifier or a supported link. Used for the Search box. */
fun parsePaperIdentifier(text: String): PaperIdentifier? {
    val input = SPACE_AFTER_PREFIX.replace(text.trim(), "$1")
    if (input.isEmpty() || input.any { it.isWhitespace() }) return null
    return parseToken(input)
}

/** Lenient: the first supported link in [text], otherwise the first bare identifier. Used for shared text. */
fun extractPaperIdentifier(text: String): PaperIdentifier? {
    val tokens = SPACE_AFTER_PREFIX.replace(text.take(MAX_SHARED_TEXT), "$1")
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
    return tokens.firstNotNullOfOrNull { token -> if (isUrl(token)) parseToken(token) else null }
        ?: tokens.firstNotNullOfOrNull { token -> if (isUrl(token)) null else parseToken(token) }
}

private fun parseToken(rawToken: String): PaperIdentifier? {
    val token = trimTrailingJunk(rawToken.trimStart { it in LEADING_JUNK })
    if (token.isEmpty()) return null
    if (isUrl(token)) {
        val urlStart = URL_START.find(token)
        val urlToken = if (urlStart != null) trimTrailingJunk(token.substring(urlStart.range.first)) else token
        return parseUrl(urlToken)
    }
    ARXIV_PREFIXED.matchEntire(token)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    BARE_ARXIV.matchEntire(token)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.value)) }
    val doi = if (token.startsWith("doi:", ignoreCase = true)) token.substring(4) else token
    return if (BARE_DOI.matches(doi)) doiIdentifier(doi) else null
}

private fun isUrl(token: String): Boolean {
    val value = token.trimStart { it in LEADING_JUNK }.lowercase()
    return "://" in value || value.startsWith("www.") || value.startsWith("arxiv.org/") ||
        value.startsWith("doi.org/") || value.startsWith("dx.doi.org/")
}

private fun parseUrl(url: String): PaperIdentifier? {
    val withoutQuery = url.substringBefore('#').substringBefore('?')
    ARXIV_URL.matchEntire(withoutQuery)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    val withoutScheme = withoutQuery.substringAfter("://")
    val host = withoutScheme.substringBefore('/').lowercase()
    val path = percentDecode(withoutScheme.substringAfter('/', missingDelimiterValue = ""))
    val doiPart = if (host in DOI_HOSTS) path else DOI_IN_PATH.find(path)?.value
    return doiPart?.let(::doiIdentifier)
}

private fun doiIdentifier(raw: String): PaperIdentifier? {
    val doi = normalizeDoi(trimTrailingJunk(raw.trimEnd('/'))) ?: return null
    if (!BARE_DOI.matches(doi)) return null
    ARXIV_DOI.matchEntire(doi)?.let { return PaperIdentifier.Arxiv(canonicalArxiv(it.groupValues[1])) }
    return PaperIdentifier.Doi(doi)
}

/** Removes the version, a ".pdf" suffix and an old-style subject class ("math.GT/0309136" → "math/0309136"). */
private fun canonicalArxiv(raw: String): String = raw.lowercase()
    .removeSuffix(".pdf")
    .replace(Regex("""v\d+$"""), "")
    .replace(Regex("""^([a-z]+(?:-[a-z]+)?)\.[a-z]{2}/"""), "$1/")

/** Drops trailing punctuation; a trailing ")" only when the parentheses are unbalanced. */
private fun trimTrailingJunk(value: String): String {
    var result = value
    while (result.isNotEmpty()) {
        val last = result.last()
        val unbalancedParen = last == ')' && result.count { it == '(' } < result.count { it == ')' }
        if (last in TRAILING_JUNK || unbalancedParen) result = result.dropLast(1) else break
    }
    return result
}

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = mutableListOf<Byte>()
    var i = 0
    while (i < value.length) {
        val hex = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes += hex.toByte()
            i += 3
        } else {
            bytes += value[i].toString().encodeToByteArray().toList()
            i += 1
        }
    }
    return bytes.toByteArray().decodeToString()
}
