package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DateRulesTest {
    private fun d(value: String) = LocalDate.parse(value)
    @Test fun todayAndYesterday() {
        val today = d("2026-10-16")
        assertEquals(DayCount(1, "就是今天", true), DateRules.count(today,today,CountMode.INCLUSIVE))
        assertEquals(DayCount(0, "就是今天", true), DateRules.count(today,today,CountMode.ELAPSED))
        assertEquals(2,DateRules.count(today.minusDays(1),today,CountMode.INCLUSIVE).days)
        assertEquals(1,DateRules.count(today.minusDays(1),today,CountMode.ELAPSED).days)
    }
    @Test fun futureUsesActualDistanceForBothModes() {
        CountMode.entries.forEach { assertEquals(DayCount(1,"还有",false),DateRules.count(d("2027-01-01"),d("2026-12-31"),it)) }
    }
    @Test fun crossYearAndLeapDay() {
        assertEquals(2,DateRules.count(d("2023-12-31"),d("2024-01-01"),CountMode.INCLUSIVE).days)
        assertEquals(3,DateRules.count(d("2024-02-28"),d("2024-03-01"),CountMode.INCLUSIVE).days)
        assertEquals(2,DateRules.count(d("2025-02-28"),d("2025-03-01"),CountMode.INCLUSIVE).days)
    }
    @Test fun feb29AnniversaryClampsAndResumesLeapYear() {
        val start=d("2020-02-29")
        assertEquals(d("2025-02-28"),DateRules.nextAnniversary(start,d("2025-02-27")))
        assertEquals(0,DateRules.untilAnniversary(start,d("2025-02-28")))
        assertEquals(d("2028-02-29"),DateRules.nextAnniversary(start,d("2027-03-01")))
        assertEquals(d("2024-02-29"),DateRules.nextAnniversary(start,d("2024-02-28")))
    }
    @Test fun futureStartNeverReturnsAnniversaryBeforeStart() {
        assertEquals(d("2029-02-28"),DateRules.nextAnniversary(d("2029-02-28"),d("2026-10-16")))
    }
    @Test fun anniversaryAndCumulativeAreIndependent() {
        assertEquals(366,DateRules.count(d("2025-10-16"),d("2026-10-16"),CountMode.INCLUSIVE).days)
        assertEquals(0,DateRules.untilAnniversary(d("2025-10-16"),d("2026-10-16")))
    }
    @Test fun editedDateAlwaysRecomputes() {
        val today=d("2026-10-16")
        assertEquals(366,DateRules.count(d("2025-10-16"),today,CountMode.INCLUSIVE).days)
        assertEquals(3,DateRules.count(d("2026-10-19"),today,CountMode.INCLUSIVE).days)
    }
}
