package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetAppearanceTest {
    private val record=MemoryDay("one","相遇",LocalDate.of(2024,2,29),photo="retained.jpg")
    @Test fun followUsesPhotoAndTextRegionInsteadOfRetainedFilePresence() {
        for(template in CardTemplate.entries) {
            val r=record.copy(template=template)
            val small=WidgetAppearance.resolve(r,WidgetChoice(r.id),0)
            val large=WidgetAppearance.resolve(r,null,2)
            if(template in listOf(CardTemplate.CLASSIC,CardTemplate.PHOTO,CardTemplate.POLAROID)) {
                assertEquals(r.photo,small.photoName)
                assertEquals("#FFFFFF",small.ink)
                assertTrue(small.photoBehindText)
            } else {
                assertNull(small.photoName);assertFalse(small.photoBehindText)
                assertEquals("#49392D",small.ink)
            }
            if(template==CardTemplate.POLAROID) {
                assertTrue(large.photoPanel);assertFalse(large.photoBehindText)
                assertEquals("#49392D",large.ink)
            }
        }
    }
    @Test fun independentAppearanceIsUnaffectedByRecordEditsAndMissingRecordIsNeutral() {
        val choice=WidgetChoice(record.id,false,"#394052","#FFFFFF")
        val before=WidgetAppearance.resolve(record,choice,2)
        val after=WidgetAppearance.resolve(record.copy(template=CardTemplate.NOTE,photo="new.jpg",appearance=CardAppearance(background="#FFFFFF",title="#000000")),choice,2)
        assertEquals(before,after);assertNull(before.photoName)
        assertEquals("#FFFFFF",before.title);assertEquals("#394052",before.background)
        assertEquals(CardTemplate.CLASSIC,before.template)
        assertNull(WidgetAppearance.resolve(null,WidgetChoice("deleted"),2).photoName)
    }
    @Test fun photoFallbackAndSizeAdaptationKeepReadableDefaults() {
        val photo=record.copy(photo=null,template=CardTemplate.PHOTO)
        assertTrue(WidgetAppearance.resolve(photo,null,0).photoBehindText)
        assertEquals("#FFFFFF",WidgetAppearance.resolve(photo,null,2).ink)
        val r=record.copy(template=CardTemplate.POLAROID)
        val g=WidgetGeometry.defaults(2)
        assertTrue(g.contentTop(WidgetAppearance.resolve(r,null,2))>60)
        assertEquals(0,WidgetGeometry.kind(110f,250f))
        assertEquals(1,WidgetGeometry.kind(280f,150f))
        assertEquals(2,WidgetGeometry.kind(280f,280f))
    }
}
