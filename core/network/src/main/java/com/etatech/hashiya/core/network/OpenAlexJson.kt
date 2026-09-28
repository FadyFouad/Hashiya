package com.etatech.hashiya.core.network

import kotlinx.serialization.json.Json

/** Tolerant decoder: OpenAlex adds fields often and returns null for many numeric fields. */
internal val OpenAlexJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
