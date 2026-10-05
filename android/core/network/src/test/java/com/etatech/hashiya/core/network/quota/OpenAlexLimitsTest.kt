package com.etatech.hashiya.core.network.quota

import com.etatech.hashiya.core.network.OpenAlexJson
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexLimitsTest {
    private fun parse(json: String) = OpenAlexLimits.parse(OpenAlexJson.parseToJsonElement(json))

    @Test
    fun defaults() = assertEquals(OpenAlexLimits(60, 8, null), OpenAlexLimits.Defaults)

    @Test
    fun readsEveryValidField() = assertEquals(
        OpenAlexLimits(0, 40, "https://proxy.example/openalex".toHttpUrl()),
        parse("""{"dailyDeviceCalls":0,"maxPagesPerQuery":40,"baseUrl":"https://proxy.example/openalex"}""")
    )

    @Test
    fun anInvalidCallCountFallsBackAlone() {
        for (value in listOf("-1", "1001", "\"60\"", "12.5", "true", "null")) {
            val limits = parse("""{"dailyDeviceCalls":$value,"maxPagesPerQuery":3}""")
            assertEquals(value, 60, limits.dailyDeviceCalls)
            assertEquals(value, 3, limits.maxPagesPerQuery)
        }
    }

    @Test
    fun anInvalidPageLimitFallsBack() {
        for (value in listOf("0", "41", "\"8\"")) {
            assertEquals(8, parse("""{"maxPagesPerQuery":$value}""").maxPagesPerQuery)
        }
    }

    @Test
    fun aBaseUrlThatIsNotHttpsIsIgnored() {
        for (value in listOf("\"http://proxy.example\"", "\"proxy.example\"", "\"https://\"", "5", "null")) {
            assertNull(value, parse("""{"baseUrl":$value}""").baseUrl)
        }
    }

    @Test
    fun aSectionThatIsNotAnObjectMeansAllDefaults() {
        for (json in listOf("[]", "5", "\"x\"", "null")) assertEquals(OpenAlexLimits.Defaults, parse(json))
    }
}
