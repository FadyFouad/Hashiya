package com.etatech.hashiya.core.network

/** One page request to `GET /works`. Null [filter]/[sort] are left out of the URL. */
data class WorksSearchRequest(val search: String, val filter: String?, val sort: String?, val cursor: String, val perPage: Int)
