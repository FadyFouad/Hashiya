package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.YearFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class YearRangeValidationTest {
    private val currentYear = 2026

    @Test
    fun acceptsValidRange() {
        assertEquals(
            YearRangeValidation.Valid(YearFilter.Between(2015, 2020)),
            validateYearRange("2015", " 2020 ", currentYear)
        )
    }

    @Test
    fun acceptsArabicIndicDigits() {
        assertEquals(
            YearRangeValidation.Valid(YearFilter.Between(2015, 2020)),
            validateYearRange("٢٠١٥", "٢٠٢٠", currentYear)
        )
    }

    @Test
    fun rejectsNonNumbers() {
        assertEquals(YearRangeValidation.NotANumber, validateYearRange("", "2020", currentYear))
        assertEquals(YearRangeValidation.NotANumber, validateYearRange("20x0", "2020", currentYear))
    }

    @Test
    fun rejectsYearsOutsideRange() {
        assertEquals(YearRangeValidation.OutOfRange, validateYearRange("1899", "2020", currentYear))
        assertEquals(YearRangeValidation.OutOfRange, validateYearRange("2020", "2027", currentYear))
    }

    @Test
    fun rejectsReversedRange() {
        assertEquals(YearRangeValidation.FromAfterTo, validateYearRange("2021", "2020", currentYear))
    }
}
