package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorkLocations
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal const val OPENALEX_BASE_URL = "https://api.openalex.org/"

internal const val WORK_FIELDS =
    "id,doi,display_name,publication_year,primary_location,authorships," +
        "cited_by_count,open_access,best_oa_location,abstract_inverted_index,type,biblio"

/** What a keyword search asks for: the work fields plus the primary topic, used only to work out the research area. */
internal const val SEARCH_FIELDS = "$WORK_FIELDS,primary_topic"

/** Only what a PDF download needs when the stored link fails: every place the work is hosted. */
internal const val PDF_LOCATION_FIELDS = "id,locations"

internal interface OpenAlexApi {
    @GET("works")
    suspend fun searchWorks(
        @Query("search") search: String,
        @Query("filter") filter: String?,
        @Query("sort") sort: String?,
        @Query("per_page") perPage: Int,
        @Query("cursor") cursor: String,
        @Query("select") select: String = SEARCH_FIELDS
    ): NetworkWorksResponse

    /** [id] is any id OpenAlex resolves, e.g. "doi:10.1038/nature14539". Retrofit's default encoding encodes "/" and "#". */
    @GET("works/{id}")
    suspend fun getWork(@Path("id") id: String, @Query("select") select: String = WORK_FIELDS): NetworkWork

    @GET("works/{id}")
    suspend fun getWorkLocations(@Path("id") id: String, @Query("select") select: String = PDF_LOCATION_FIELDS): NetworkWorkLocations

    @GET("works")
    suspend fun findWorks(
        @Query("filter") filter: String,
        @Query("per_page") perPage: Int,
        @Query("select") select: String = WORK_FIELDS
    ): NetworkWorksResponse
}
