package com.etatech.hashiya.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkWorksResponse(val meta: NetworkMeta, val results: List<NetworkWork> = emptyList())

@Serializable
data class NetworkMeta(val count: Long = 0, @SerialName("next_cursor") val nextCursor: String? = null)

@Serializable
data class NetworkWork(
    val id: String,
    val doi: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("publication_year") val publicationYear: Int? = null,
    @SerialName("primary_location") val primaryLocation: NetworkLocation? = null,
    val authorships: List<NetworkAuthorship> = emptyList(),
    @SerialName("cited_by_count") val citedByCount: Int = 0,
    @SerialName("open_access") val openAccess: NetworkOpenAccess? = null,
    @SerialName("best_oa_location") val bestOaLocation: NetworkLocation? = null,
    @SerialName("abstract_inverted_index") val abstractInvertedIndex: Map<String, List<Int>>? = null,
    /** OpenAlex's work type, e.g. "article", "preprint", "book-chapter". */
    val type: String? = null,
    val biblio: NetworkBiblio? = null
)

@Serializable
data class NetworkBiblio(
    val volume: String? = null,
    val issue: String? = null,
    @SerialName("first_page") val firstPage: String? = null,
    @SerialName("last_page") val lastPage: String? = null
)

@Serializable
data class NetworkLocation(val source: NetworkSource? = null, @SerialName("pdf_url") val pdfUrl: String? = null)

@Serializable
data class NetworkSource(
    @SerialName("display_name") val displayName: String? = null,
    /** e.g. "journal", "conference", "repository". */
    val type: String? = null,
    @SerialName("host_organization_name") val hostOrganizationName: String? = null
)

@Serializable
data class NetworkAuthorship(val author: NetworkAuthor)

@Serializable
data class NetworkAuthor(val id: String? = null, @SerialName("display_name") val displayName: String? = null)

@Serializable
data class NetworkOpenAccess(@SerialName("is_oa") val isOa: Boolean = false)
