package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TodoRulesTest {
    private val date=LocalDate.of(2026,10,19)
    private fun task(kind:TodoKind=TodoKind.HABIT)=TodoTask(owner="a",title="读书",kind=kind)
    @Test fun repeatAndDailyCompletionAreIndependent() {
        val task=task().copy(weekdays=setOf(1,3,5))
        assertTrue(TodoRules.scheduled(task,date));assertFalse(TodoRules.scheduled(task,date.plusDays(1)))
        val book=TodoBook("a",tasks=listOf(task),checks=listOf(TodoCheck(task.id,"a",date,true)))
        assertTrue(TodoRules.complete(book,task,"a",date));assertFalse(TodoRules.complete(book,task,"a",date.plusDays(1)))
    }
    @Test fun oneTimeCompletionSurvivesDayChanges() {
        val task=task(TodoKind.TODO)
        val book=TodoBook("a",checks=listOf(TodoCheck(task.id,"a",TodoRules.checkDay(task,date),true)))
        assertTrue(TodoRules.complete(book,task,"a",date.plusDays(50)))
    }
    @Test fun sharedNeedsTwoSeparateCompletionsAndCurrentPair() {
        val task=task(TodoKind.SHARED).copy(shared=true,pairId="pair")
        val first=TodoBook("a",pairId="pair",partnerId="b",checks=listOf(TodoCheck(task.id,"a",date,true)))
        assertFalse(TodoRules.bothComplete(first,task,date))
        assertTrue(TodoRules.bothComplete(first.copy(checks=first.checks+TodoCheck(task.id,"b",date,true)),task,date))
        assertFalse(TodoRules.canCheck(task,"b","new-pair",date))
        assertFalse(TodoRules.canCheck(task.copy(kind=TodoKind.HABIT),"b","pair",date))
    }
    @Test fun unlinkPrunesPartnerCacheButKeepsOwnData() {
        val mine=task().copy(shared=true,pairId="old")
        val other=task().copy(owner="b",shared=true,pairId="old")
        val book=TodoBook("a",pairId="old",partnerId="b",tasks=listOf(mine,other),checks=listOf(TodoCheck(mine.id,"a",date,true),TodoCheck(mine.id,"b",date,true),TodoCheck(other.id,"b",date,true)))
        val pruned=TodoRules.prune(book,"",emptySet())
        assertEquals(listOf(mine),pruned.tasks);assertEquals(1,pruned.checks.size);assertEquals("a",pruned.checks.single().user)
    }
    @Test fun invalidSharedTaskAndEmptyScheduleRejected() {
        for(bad in listOf(task().copy(title=""),task().copy(weekdays=emptySet()),task(TodoKind.SHARED))) {
            try {TodoRules.validate(bad);fail("invalid task accepted")} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun progressExcludesPastOneTimeCompletionRestDaysAndHiddenPeerPlans() {
        val old=task(TodoKind.TODO);val current=task(TodoKind.TODO);val pending=task(TodoKind.TODO).copy(due=date.minusDays(4))
        val rest=task().copy(weekdays=setOf(2));val hidden=task().copy(owner="b")
        val book=TodoBook("a",pairId="p",partnerId="b",tasks=listOf(old,current,pending,rest,hidden),checks=listOf(
            TodoCheck(old.id,"a",TodoRules.checkDay(old,date),true,1,date.minusDays(1)),TodoCheck(current.id,"a",TodoRules.checkDay(current,date),true,1,date)))
        assertEquals(TodoRules.Progress(1,2),TodoRules.progress(book,"a",date))
        assertEquals(TodoRules.Progress(0,0),TodoRules.progress(book,"b",date))
        assertEquals(TodoRules.Progress(0,1),TodoRules.progress(book,"a",date.plusDays(2)))
    }
    @Test fun sharedProgressCountsEachParticipantOnceAndCancellationUpdatesIt() {
        val shared=task(TodoKind.SHARED).copy(shared=true,pairId="p")
        val mine=task().copy(shared=true,pairId="p");val peer=mine.copy(id=java.util.UUID.randomUUID().toString(),owner="b")
        val book=TodoBook("a",pairId="p",partnerId="b",tasks=listOf(shared,mine,peer),checks=listOf(TodoCheck(shared.id,"a",date,true)))
        assertEquals(TodoRules.Progress(1,2),TodoRules.progress(book,"a",date))
        assertEquals(TodoRules.Progress(0,2),TodoRules.progress(book,"b",date))
        assertEquals(TodoRules.Progress(0,2),TodoRules.progress(book.copy(checks=emptyList()),"a",date))
        assertEquals(TodoRules.Progress(0,0),TodoRules.progress(book.copy(pairId="new"),"a",date))
    }
    @Test fun legacyCompletionDateIsUnknownUntilFirstRefresh() {
        val task=task(TodoKind.TODO)
        val check=TodoCheck(task.id,"a",TodoRules.checkDay(task,date),true)
        val book=TodoBook("a",tasks=listOf(task),checks=listOf(check))
        assertEquals(TodoRules.Progress(0,0,1),TodoRules.progress(book,"a",date))
        assertEquals(TodoRules.Progress(1,1),TodoRules.progress(book.copy(checks=listOf(check.copy(completedOn=date))),"a",date))
        assertEquals(TodoRules.Progress(0,0),TodoRules.progress(book.copy(checks=listOf(check.copy(completedOn=date.minusDays(1)))),"a",date))
    }
}
