package com.etatech.hashiya.core.analytics

/** A value from a closed list: the only kind of value a crash key or analytics property may take. Sealed, so no caller can make one. */
sealed interface ClosedValue {
    val id: String
}

enum class LibrarySize(override val id: String) : ClosedValue {
    Zero("0"),
    UpTo50("1-50"),
    UpTo500("51-500"),
    UpTo5000("501-5000"),
    Over5000("5000+");

    companion object {
        /** The library's size, coarse enough to say nothing about a person. */
        fun of(papers: Int): LibrarySize = when {
            papers <= 0 -> Zero
            papers <= 50 -> UpTo50
            papers <= 500 -> UpTo500
            papers <= 5000 -> UpTo5000
            else -> Over5000
        }
    }
}

enum class Language(override val id: String) : ClosedValue {
    En("en"),
    Ar("ar"),
    System("system");

    companion object {
        /** From `AppCompatDelegate` language tags ("ar", "en-GB,ar", ""): the first tag's language. */
        fun of(tags: String): Language = when (tags.substringBefore(',').substringBefore('-')) {
            "en" -> En
            "ar" -> Ar
            else -> System
        }
    }
}

enum class YesNo(override val id: String) : ClosedValue {
    Yes("yes"),
    No("no");

    companion object {
        fun of(value: Boolean) = if (value) Yes else No
    }
}

enum class Screen(override val id: String) : ClosedValue {
    Library("library"),
    Search("search"),
    Details("details"),
    Reader("reader"),
    Settings("settings"),
    Restore("restore"),
    Export("export"),
    Import("import")
}

enum class AnalyticsProperty(val id: String) {
    LibrarySizeBucket("library_size_bucket"),
    Language("language"),
    HasOwnKey("has_own_key")
}

enum class SearchKind(val id: String) { Keyword("keyword"), Doi("doi"), Arxiv("arxiv"), Link("link") }

enum class SearchRoute(val id: String) { User("user"), Shared("shared"), Keyless("keyless"), Cached("cached") }

enum class LimitKind(val id: String) { Daily("daily"), PageCap("page_cap") }

enum class SaveSource(val id: String) { Search("search"), Lookup("lookup"), Share("share") }

enum class ExportFormat(val id: String) { Bibtex("bibtex"), Apa("apa"), Ieee("ieee"), Backup("backup") }

enum class PdfOrigin(val id: String) { Downloaded("downloaded"), Attached("attached") }

enum class ResultsBucket(val id: String) {
    Zero("0"),
    UpTo25("1-25"),
    UpTo200("26-200"),
    Over200("200+");

    companion object {
        fun of(count: Long): ResultsBucket = when {
            count <= 0 -> Zero
            count <= 25 -> UpTo25
            count <= 200 -> UpTo200
            else -> Over200
        }
    }
}

enum class ImportFileFormat(val id: String) { Bibtex("bibtex"), Ris("ris") }

/** How an import ended: [Ok] when it saved what it found; otherwise why not. */
enum class ImportResult(val id: String) {
    Ok("ok"),
    Cancelled("cancelled"),
    Unsupported("unsupported"),
    TooLarge("too_large"),
    Unreadable("unreadable"),
    NothingNew("nothing_new")
}

/** Matched papers over papers to import: none, under half, half or more, all. */
enum class MatchedShare(val id: String) {
    None("none"),
    Some("some"),
    Most("most"),
    All("all");

    companion object {
        fun of(matched: Int, toImport: Int): MatchedShare = when {
            toImport <= 0 || matched <= 0 -> None
            matched >= toImport -> All
            matched * 2 >= toImport -> Most
            else -> Some
        }
    }
}
