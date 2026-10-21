package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StatusFacesTest {
    private val date=LocalDate.of(2026,10,21)
    @Test fun deadlineCompletionAndDailyResetAreIndependentOfFaces() {
        val task=TodoTask(owner="self",title="阅读",due=date.minusDays(1))
        val book=TodoBook("self")
        assertEquals(CheckStatus.OVERDUE,checkStatus(task,book,"self",date))
        assertEquals(CheckStatus.PENDING,checkStatus(task.copy(due=date),book,"self",date))
        assertEquals(CheckStatus.PENDING,checkStatus(task.copy(due=date.plusDays(1)),book,"self",date))
        val done=book.copy(checks=listOf(TodoCheck(task.id,"self",TodoRules.checkDay(task,date),true)))
        assertEquals(CheckStatus.DONE,checkStatus(task,done,"self",date.plusDays(2)))
        val habit=task.copy(kind=TodoKind.HABIT)
        val dayDone=book.copy(checks=listOf(TodoCheck(task.id,"self",date,true)))
        assertEquals(CheckStatus.DONE,checkStatus(habit,dayDone,"self",date))
        assertEquals(CheckStatus.PENDING,checkStatus(habit,dayDone,"self",date.plusDays(1)))
        assertNull(checkStatus(habit.copy(weekdays=setOf(1)),book,"self",date))
        assertEquals(CheckStatus.PENDING,checkStatus(habit,dayDone,"peer",date))
    }
    @Test fun choosingOrDisablingFacesDoesNotChangeMeaningAndUnknownKeysFallBack() {
        val defaults=StatusFaces()
        assertEquals(StatusFace.CRYING,defaults.face(CheckStatus.OVERDUE))
        val custom=defaults.select(CheckStatus.OVERDUE,StatusFace.PARTY).copy(enabled=false)
        assertEquals(StatusFace.PARTY,custom.face(CheckStatus.OVERDUE))
        assertEquals(defaults.done,custom.done);assertEquals(defaults.pending,custom.pending)
        assertEquals(StatusFace.CRYING,StatusFace.fromKey("future-face",StatusFace.CRYING))
    }
}
