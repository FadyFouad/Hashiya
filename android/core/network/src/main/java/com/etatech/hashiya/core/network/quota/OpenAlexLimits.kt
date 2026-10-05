package com.etatech.hashiya.core.network.quota

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The `openAlex` section of the remote config: how this device may use the shared OpenAlex budget. */
data class OpenAlexLimits(val dailyDeviceCalls: Int, val maxPagesPerQuery: Int, val baseUrl: HttpUrl?) {
    companion object {
        val Defaults = OpenAlexLimits(dailyDeviceCalls = 60, maxPagesPerQuery = 8, baseUrl = null)

        /** Field by field: a missing, out-of-range, wrong-type or non-https value falls back to its default. */
        fun parse(section: JsonElement?): OpenAlexLimits {
            val fields = section as? JsonObject ?: return Defaults
            return OpenAlexLimits(
                dailyDeviceCalls = wholeNumber(fields["dailyDeviceCalls"], 0..1000) ?: Defaults.dailyDeviceCalls,
                maxPagesPerQuery = wholeNumber(fields["maxPagesPerQuery"], 1..40) ?: Defaults.maxPagesPerQuery,
                baseUrl = httpsUrl(fields["baseUrl"])
            )
        }

        private fun wholeNumber(value: JsonElement?, range: IntRange): Int? {
            val primitive = value as? JsonPrimitive ?: return null
            if (primitive.isString || primitive.booleanOrNull != null) return null
            val number = primitive.doubleOrNull ?: return null
            if (number != Math.rint(number) || number < range.first || number > range.last) return null
            return number.toInt()
        }

        private fun httpsUrl(value: JsonElement?): HttpUrl? {
            val primitive = value as? JsonPrimitive ?: return null
            if (!primitive.isString) return null
            val url = primitive.content.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.host.isNotEmpty() }
        }
    }
}
