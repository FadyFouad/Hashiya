package com.etatech.hashiya.core.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchCategoryTest {
    private fun topic(subfield: Int? = null, field: Int? = null, domain: Int? = null) = TopicIds(
        subfield?.let { "https://openalex.org/subfields/$it" },
        field?.let { "https://openalex.org/fields/$it" },
        domain?.let { "https://openalex.org/domains/$it" }
    )

    @Test
    fun everyComputerScienceSubfieldMaps() {
        val expected = mapOf(
            1702 to ResearchCategory.Ai, 1707 to ResearchCategory.ComputerVision, 1703 to ResearchCategory.Theory,
            1705 to ResearchCategory.Networks, 1708 to ResearchCategory.Systems, 1712 to ResearchCategory.Software,
            1709 to ResearchCategory.Hci, 1710 to ResearchCategory.InformationSystems, 1704 to ResearchCategory.Graphics,
            1711 to ResearchCategory.SignalProcessing, 1706 to ResearchCategory.CsOther
        )
        for ((subfield, category) in expected) assertEquals(category, ResearchCategory.of(topic(subfield, 17, 3)))
    }

    @Test
    fun fieldsAndDomainsMap() {
        assertEquals(ResearchCategory.Mathematics, ResearchCategory.of(topic(2601, 26, 3)))
        assertEquals(ResearchCategory.Engineering, ResearchCategory.of(topic(2201, 22, 3)))
        assertEquals(ResearchCategory.PhysicalSciences, ResearchCategory.of(topic(3101, 31, 3)))
        assertEquals(ResearchCategory.LifeSciences, ResearchCategory.of(topic(1301, 13, 1)))
        assertEquals(ResearchCategory.SocialSciences, ResearchCategory.of(topic(3301, 33, 2)))
        assertEquals(ResearchCategory.HealthSciences, ResearchCategory.of(topic(2701, 27, 4)))
    }

    @Test
    fun malformedOrUnknownIdsAreUnknownOrFallThrough() {
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds("https://openalex.org/subfields/abc", null, null)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds("1702", null, null)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.of(TopicIds(null, null, "https://openalex.org/domains/9")))
        assertEquals(ResearchCategory.Mathematics, ResearchCategory.of(TopicIds("x", "https://openalex.org/fields/26", null)))
    }

    @Test
    fun theVote() {
        val ai = topic(1702, 17, 3)
        val vision = topic(1707, 17, 3)
        val theory = topic(1703, 17, 3)
        val none = TopicIds(null, null, null)
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(ai, ai, vision, theory, ai)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, ai, vision, vision, theory)))
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(ai, ai, vision, theory, topic(1705, 17, 3))))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, vision, theory)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(listOf(ai, ai)))
        assertEquals(ResearchCategory.Unknown, ResearchCategory.classify(emptyList()))
        assertEquals(ResearchCategory.Ai, ResearchCategory.classify(listOf(none, none, ai, ai, ai)))
        assertEquals(ResearchCategory.ComputerVision, ResearchCategory.classify(List(10) { vision } + List(15) { ai }))
    }
}
