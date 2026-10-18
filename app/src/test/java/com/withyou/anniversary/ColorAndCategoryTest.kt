package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ColorAndCategoryTest {
    @Test fun legacyIndicesAndAccessibleAutoInk() {
        assertEquals(listOf("#F0DCC8","#E8D5D5","#D9E2D1","#D8DFE9","#E4D9E9"),ColorRules.swatches.take(5).map {it.hex})
        assertEquals(30,ColorRules.swatches.size)
        ColorRules.swatches.forEach {assertTrue("${it.name} contrast",ColorRules.contrast(it.hex,ColorRules.autoInk(it.hex))>=4.5)}
    }
    @Test fun hexAndCustomColors() {
        assertEquals("#A1B2C3",ColorRules.normalize("a1b2c3"))
        assertNull(ColorRules.normalize("#GGGGGG"));assertNull(ColorRules.normalize("#ABC"))
        val r=MemoryDay("test","test",LocalDate.of(2024,1,1),appearance=CardAppearance(background="#000000"))
        assertEquals("#FFFFFF",ColorRules.ink(r))
        assertEquals("#365042",ColorRules.ink(r.copy(appearance=CardAppearance(tone=TextTone.CUSTOM,text="#365042"))))
    }
    @Test fun deletingCategoryPreservesRecordsAndTheirAppearance() {
        val category=Category("cat","旅行")
        val r=MemoryDay("one","旅行",LocalDate.of(2024,2,29),photo="photo.jpg",appearance=CardAppearance(background="#123456"),categoryId="cat")
        val other=r.copy(id="two",categoryId=null)
        val next=AppData(listOf(r,other),categories=listOf(category)).deleteCategory("cat")
        assertTrue(next.categories.isEmpty());assertEquals(listOf(r.copy(categoryId=null),other),next.records)
    }
    @Test fun annualCelebrationFollowsFeb29RuleAndNeverPrecedesStart() {
        val r=MemoryDay("one","相遇",LocalDate.of(2024,2,29))
        assertTrue(ColorRules.isAnnualDay(r,LocalDate.of(2025,2,28)))
        assertFalse(ColorRules.isAnnualDay(r,LocalDate.of(2024,2,28)))
        assertFalse(ColorRules.isAnnualDay(r,LocalDate.of(2025,3,1)))
    }
}
