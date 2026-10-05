package com.etatech.hashiya.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    val biblio: NetworkBiblio? = null,
    @Serializable(with = LenientTopicSerializer::class)
    @SerialName("primary_topic") val primaryTopic: NetworkTopic? = null
)

/** A work's primary topic: only the ids of its subfield, field and domain are kept; names are ignored. */
@Serializable
data class NetworkTopic(val subfield: NetworkTopicRef? = null, val field: NetworkTopicRef? = null, val domain: NetworkTopicRef? = null)

@Serializable
data class NetworkTopicRef(val id: String? = null)

/** Reads a topic's ids and returns null for any shape it does not understand, so the topic can never fail a work or a page. */
internal object LenientTopicSerializer : KSerializer<NetworkTopic> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("NetworkTopic")

    override fun deserialize(decoder: Decoder): NetworkTopic {
        val json = decoder as? JsonDecoder ?: throw SerializationException("Topics are read from JSON only")
        return readTopic(json.decodeJsonElement()) ?: NetworkTopic()
    }

    override fun serialize(encoder: Encoder, value: NetworkTopic) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("Topics are written as JSON only")
        json.encodeJsonElement(
            buildJsonObject {
                value.subfield?.id?.let { put("subfield", buildJsonObject { put("id", it) }) }
                value.field?.id?.let { put("field", buildJsonObject { put("id", it) }) }
                value.domain?.id?.let { put("domain", buildJsonObject { put("id", it) }) }
            }
        )
    }

    private fun readTopic(element: JsonElement): NetworkTopic? {
        val topic = element as? JsonObject ?: return null
        return NetworkTopic(readRef(topic["subfield"]), readRef(topic["field"]), readRef(topic["domain"]))
    }

    private fun readRef(element: JsonElement?): NetworkTopicRef? {
        val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.takeIf { it.isString }?.content
        return id?.let(::NetworkTopicRef)
    }
}

@Serializable
data class NetworkBiblio(
    val volume: String? = null,
    val issue: String? = null,
    @SerialName("first_page") val firstPage: String? = null,
    @SerialName("last_page") val lastPage: String? = null
)

@Serializable
data class NetworkLocation(
    val source: NetworkSource? = null,
    @SerialName("pdf_url") val pdfUrl: String? = null,
    @SerialName("is_oa") val isOa: Boolean = false
)

/** A work with only its locations: every place OpenAlex knows it is hosted, open or not. */
@Serializable
data class NetworkWorkLocations(val id: String, val locations: List<NetworkLocation> = emptyList())

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
