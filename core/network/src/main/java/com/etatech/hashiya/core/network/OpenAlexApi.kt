package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal const val OPENALEX_BASE_URL = "https://api.openalex.org/"

internal const val WORK_FIELDS =
    "id,doi,display_name,publication_year,primary_location,authorships," +
        "cited_by_count,open_access,best_oa_location,abstract_inverted_index"

internal interface OpenAlexApi {
    @GET("works")
    suspend fun searchWorks(
        @Query("search") search: String,
        @Query("filter") filter: String?,
        @Query("sort") sort: String?,
        @Query("per_page") perPage: Int,
        @Query("cursor") cursor: String,
        @Query("select") select: String = WORK_FIELDS
    ): NetworkWorksResponse

    /** [id] is any id OpenAlex resolves, e.g. "doi:10.1038/nature14539". Retrofit's default encoding encodes "/" and "#". */
    @GET("works/{id}")
    suspend fun getWork(@Path("id") id: String, @Query("select") select: String = WORK_FIELDS): NetworkWork

    @GET("works")
    suspend fun findWorks(
        @Query("filter") filter: String,
        @Query("per_page") perPage: Int,
        @Query("select") select: String = WORK_FIELDS
    ): NetworkWorksResponse
}
