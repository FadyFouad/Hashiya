package com.etatech.hashiya.core.network

import kotlinx.coroutines.flow.StateFlow

internal const val API_KEY_PARAM = "api_key"

/** The user's own OpenAlex key, or null to use the built-in one. Implemented in `core/data`. */
interface UserApiKeySource {
    val userKey: StateFlow<String?>
}

/** Tags a request that carries a key with which kind it is, so a rejection can be blamed on the user's key or not. */
internal enum class ApiKeyKind { User, BuiltIn }
