package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CheckTimesTest {
    private val today=LocalDate.of(2026,10,22)
    private val task=TodoTask(owner="a",title="阅读")
    private fun check(stamp:String?)=TodoCheck(task.id,"a",LocalDate.of(1970,1,1),true,completedAt=stamp?.let(Instant::parse))
    @Test fun briefAndDetailedTimesUseCompletionInstantAcrossMidnight() {
        val c=check("2026-10-21T16:19:32Z")
        assertEquals("今天 00:19",CheckTimes.short(c,today))
        assertEquals("2026年10月22日 00:19:32",CheckTimes.detail(c))
        assertEquals("昨天 00:19",CheckTimes.short(c,today.plusDays(1)))
        assertEquals("10月22日 00:19",CheckTimes.short(c,today.plusDays(2)))
        assertEquals("2026年10月22日 00:19",CheckTimes.short(c,today.plusYears(1)))
    }
    @Test fun oldDataNeverInventsPreciseTimeOrShowsEpochSentinel() {
        assertEquals("具体时间未记录",CheckTimes.short(check(null),today))
        val dated=check(null).copy(completedOn=today.minusDays(1))
        assertEquals("昨天 · 具体时间未记录",CheckTimes.short(dated,today))
        assertEquals("2026年10月21日 · 具体时间未记录",CheckTimes.detail(dated))
    }
    @Test fun collectionFollowsCalendarDayAndUndoRestoresItem() {
        val c=check("2026-10-21T15:59:59Z")
        val book=TodoBook("a",checks=listOf(c))
        assertFalse(CheckTimes.collected(task,book,today.minusDays(1)))
        assertTrue(CheckTimes.collected(task,book,today))
        assertFalse(CheckTimes.collected(task,book.copy(checks=listOf(c.copy(done=false))),today))
        assertFalse(CheckTimes.collected(task,book.copy(checks=listOf(check(null))),today))
    }
    @Test fun dailyAndSharedTasksNeverDisappearBecauseOfYesterday() {
        listOf(TodoKind.HABIT,TodoKind.SHARED).forEach {kind ->
            val t=task.copy(kind=kind,shared=kind==TodoKind.SHARED,pairId="pair")
            val b=TodoBook("a",pairId="pair",partnerId="b",checks=listOf(check("2026-10-21T08:00:00Z").copy(day=today.minusDays(1))))
            assertFalse(CheckTimes.collected(t,b,today))
            assertFalse(TodoRules.complete(b,t,"a",today))
        }
        val b=TodoBook("a",checks=listOf(check("2026-10-21T08:00:00Z").copy(user="b")))
        assertFalse(CheckTimes.collected(task,b,today))
        assertTrue(CheckTimes.collected(task.copy(owner="b"),b,today))
    }
    @Test fun offlineDayRequiresMatchingRecordedTimeAndNeverFutureDay() {
        assertTrue(CheckTimes.uploadable("2026-10-21","2026-10-21T08:00:00Z","2026-10-22"))
        assertFalse(CheckTimes.uploadable("2026-10-21","","2026-10-22"))
        assertFalse(CheckTimes.uploadable("2026-10-21","2026-10-22T08:00:00Z","2026-10-22"))
        assertFalse(CheckTimes.uploadable("2026-10-23","2026-10-23T08:00:00Z","2026-10-22"))
        assertFalse(CheckTimes.uploadable("invalid","invalid","invalid"))
    }
}
