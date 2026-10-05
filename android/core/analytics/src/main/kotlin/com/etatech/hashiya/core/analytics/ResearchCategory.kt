package com.etatech.hashiya.core.analytics

/** A work's primary topic as OpenAlex ids ("https://openalex.org/subfields/1702", …/fields/17, …/domains/3). Never names. */
data class TopicIds(val subfield: String?, val field: String?, val domain: String?) {
    internal val isEmpty get() = subfield == null && field == null && domain == null
}

/** The research area of a keyword search, worked out on the device from the results' topics (spec §4.1). */
enum class ResearchCategory(val id: String) {
    Ai("ai"),
    ComputerVision("computer_vision"),
    Theory("theory"),
    Networks("networks"),
    Systems("systems"),
    Software("software"),
    Hci("hci"),
    InformationSystems("information_systems"),
    Graphics("graphics"),
    SignalProcessing("signal_processing"),
    CsOther("cs_other"),
    Mathematics("mathematics"),
    Engineering("engineering"),
    PhysicalSciences("physical_sciences"),
    LifeSciences("life_sciences"),
    SocialSciences("social_sciences"),
    HealthSciences("health_sciences"),
    Unknown("unknown");

    companion object {
        private val subfields = mapOf(
            1702 to Ai, 1707 to ComputerVision, 1703 to Theory, 1705 to Networks, 1708 to Systems,
            1712 to Software, 1709 to Hci, 1710 to InformationSystems, 1704 to Graphics, 1711 to SignalProcessing
        )
        private val fields = mapOf(17 to CsOther, 26 to Mathematics, 22 to Engineering)
        private val domains = mapOf(3 to PhysicalSciences, 1 to LifeSciences, 2 to SocialSciences, 4 to HealthSciences)

        /** One work: its subfield, else its field, else its domain; anything unrecognised is [Unknown]. */
        fun of(ids: TopicIds): ResearchCategory = number(ids.subfield, "subfields")?.let(subfields::get)
            ?: number(ids.field, "fields")?.let(fields::get)
            ?: number(ids.domain, "domains")?.let(domains::get)
            ?: Unknown

        /**
         * A search: of the first 10 results that have a topic, the most common category if at least 3 were mapped and it has
         * at least 40% of them with no tie; otherwise [Unknown].
         */
        fun classify(topics: List<TopicIds>): ResearchCategory {
            val mapped = topics.asSequence().filterNot { it.isEmpty }.take(10).map(::of).toList()
            if (mapped.size < 3) return Unknown
            val ranked = mapped.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            val top = ranked.first()
            if (ranked.getOrNull(1)?.value == top.value || top.value * 5 < mapped.size * 2) return Unknown
            return top.key
        }

        private fun number(id: String?, kind: String): Int? {
            val prefix = "https://openalex.org/$kind/"
            return id?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.toIntOrNull()
        }
    }
}
