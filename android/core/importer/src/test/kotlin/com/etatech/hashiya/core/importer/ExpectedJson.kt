package com.etatech.hashiya.core.importer

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** testdata/import at the repository root (beside android/), found by walking up from the working directory. */
internal val importTestData: File by lazy {
    var dir: File? = File("").absoluteFile
    while (dir != null && !File(dir, "testdata/import").isDirectory) dir = dir.parentFile
    File(requireNotNull(dir) { "No testdata/import above ${File("").absolutePath}" }, "testdata/import")
}

internal fun fixtureBytes(name: String): ByteArray = File(importTestData, name).readBytes()

/** The expected parse result for [name], as stored in testdata/import/expected, shared with the iOS tests. */
internal fun expectedJson(name: String): JsonElement =
    kotlinx.serialization.json.Json.parseToJsonElement(File(importTestData, "expected/$name.json").readText())

/** The same JSON shape as the expected files: every field present, nulls included. Null parse results become JsonNull. */
internal fun ImportParseResult?.toJson(): JsonElement {
    if (this == null) return JsonNull
    return buildJsonObject {
        put("format", if (format == ImportFormat.BibTeX) "bibtex" else "ris")
        put("entries", JsonArray(entries.map { it.toJson() }))
        put(
            "issues",
            buildJsonArray {
                issues.forEach { issue ->
                    add(
                        buildJsonObject {
                            put("line", issue.line)
                            put("kind", issue.kind.name.replaceFirstChar { it.lowercase() })
                        }
                    )
                }
            }
        )
    }
}

private fun ImportedEntry.toJson(): JsonObject = buildJsonObject {
    put("line", line)
    put("entryType", entryType)
    put("citeKey", citeKey)
    put("title", title)
    put("authors", JsonArray(authors.map(::JsonPrimitive)))
    put("year", year)
    put("venue", venue)
    put("doi", doi)
    put("url", url)
    put("abstract", abstract)
    put("volume", volume)
    put("issue", issue)
    put("firstPage", firstPage)
    put("lastPage", lastPage)
    put("publisher", publisher)
    put("workType", workType)
    put("sourceType", sourceType)
}
