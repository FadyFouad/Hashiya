package com.etatech.hashiya.core.network.quota

import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var clock = Instant.parse("2026-10-05T10:00:00Z").toEpochMilli()

    private fun advance(seconds: Long) {
        clock += seconds * 1000
    }

    private fun cache(maxBytes: Long = 5_000_000) = SearchCache(directory(), maxBytes) { clock }

    private fun directory() = File(folder.root, "OpenAlexSearch")

    @Test
    fun returnsWhatWasStored() {
        val cache = cache()
        cache.write("a", "page")
        assertEquals("page", cache.read("a"))
        assertNull(cache.read("b"))
    }

    @Test
    fun entriesExpireAfter24Hours() {
        val cache = cache()
        cache.write("a", "page")
        advance(86_399)
        assertNotNull(cache.read("a"))
        advance(2)
        assertNull(cache.read("a"))
    }

    @Test
    fun removesTheLeastRecentlyUsedOverTheSizeLimit() {
        val cache = cache(maxBytes = 250)
        val body = "x".repeat(100)
        cache.write("old", body)
        advance(10)
        cache.write("used", body)
        advance(10)
        cache.read("old") // now the most recently used
        advance(10)
        cache.write("new", body)
        assertNotNull(cache.read("old"))
        assertNull(cache.read("used"))
        assertNotNull(cache.read("new"))
    }

    @Test
    fun survivesANewInstance() {
        cache().write("a", "page")
        assertEquals("page", cache().read("a"))
    }

    @Test
    fun aCorruptEntryIsAMissAndIsRemoved() {
        val cache = cache()
        cache.write("a", "page")
        val file = directory().listFiles()!!.single()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(cache.read("a"))
        assertFalse(file.exists())
    }

    @Test
    fun anUnusableDirectoryIsAMissNotAnError() {
        val blocker = File(folder.root, "blocker").apply { writeText("not a directory") }
        val cache = SearchCache(File(blocker, "inside")) { clock }
        cache.write("a", "page")
        assertNull(cache.read("a"))
    }

    @Test
    fun theKeyIgnoresTheApiKeyAndParameterOrder() {
        val a = SearchCache.key("/works", listOf("search" to "bert", "cursor" to "*", "api_key" to "one"))
        val b = SearchCache.key("/works", listOf("api_key" to "two", "cursor" to "*", "search" to "bert"))
        val c = SearchCache.key("/works", listOf("search" to "bert", "cursor" to "*"))
        assertEquals(a, b)
        assertEquals(a, c)
        assertFalse(a.contains("one"))
    }

    @Test
    fun theKeyDiffersForCursorFilterAndPath() {
        val base = SearchCache.key("/works", listOf("search" to "bert", "cursor" to "*"))
        assertNotEquals(base, SearchCache.key("/works", listOf("search" to "bert", "cursor" to "abc")))
        assertNotEquals(
            base,
            SearchCache.key("/works", listOf("search" to "bert", "cursor" to "*", "filter" to "is_oa:true"))
        )
        assertNotEquals(base, SearchCache.key("/authors", listOf("search" to "bert", "cursor" to "*")))
    }
}
