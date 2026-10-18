package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test

class CardTemplateTest {
    @Test fun serializedKeysAreUniqueAndUnknownKeysUseClassic() {
        assertEquals(6,CardTemplate.entries.size)
        assertEquals(6,CardTemplate.entries.map {it.key}.toSet().size)
        CardTemplate.entries.forEach {assertEquals(it,CardTemplate.fromKey(it.key))}
        assertEquals(CardTemplate.CLASSIC,CardTemplate.fromKey(null))
        assertEquals(CardTemplate.CLASSIC,CardTemplate.fromKey("not-installed"))
    }

    @Test fun retainedPhotosDoNotForceWhiteTextOnPaperLayouts() {
        val record=MemoryDay("test","test",java.time.LocalDate.of(2024,1,1),photo="retained.jpg")
        listOf(CardTemplate.POLAROID,CardTemplate.TICKET,CardTemplate.NOTE,CardTemplate.MINIMAL).forEach {
            val paper=record.copy(template=it).templateColors()
            assertEquals("#49392D",ColorRules.ink(paper))
            assertEquals("retained.jpg",record.photo)
        }
        assertEquals("#FFFFFF",ColorRules.ink(record.copy(template=CardTemplate.PHOTO,photo=null).templateColors()))
    }
}
