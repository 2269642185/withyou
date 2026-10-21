package com.withyou.anniversary

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CheckTimes {
    fun date(check:TodoCheck):LocalDate?=check.completedAt?.atZone(TodoRules.zone)?.toLocalDate()
        ?: check.completedOn ?: check.day.takeUnless {it==LocalDate.of(1970,1,1)}
    fun short(check:TodoCheck,today:LocalDate):String {
        val date=date(check) ?: return "具体时间未记录"
        val day=when(date) {today->"今天";today.minusDays(1)->"昨天";else->if(date.year==today.year) "${date.monthValue}月${date.dayOfMonth}日" else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"}
        return day+(check.completedAt?.atZone(TodoRules.zone)?.format(DateTimeFormatter.ofPattern(" HH:mm")) ?: " · 具体时间未记录")
    }
    fun detail(check:TodoCheck):String {
        return check.completedAt?.atZone(TodoRules.zone)?.format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm:ss"))
            ?: (date(check)?.let {"${it.year}年${it.monthValue}月${it.dayOfMonth}日 · "}.orEmpty()+"具体时间未记录")
    }
    fun displayCheck(task:TodoTask,book:TodoBook,date:LocalDate)=book.checks.find {
        it.taskId==task.id && it.user==(if(task.kind==TodoKind.SHARED) book.uid else task.owner) && it.day==TodoRules.checkDay(task,date) && it.done
    }
    fun collected(task:TodoTask,book:TodoBook,today:LocalDate)=task.kind==TodoKind.TODO && displayCheck(task,book,today)?.let {date(it)?.isBefore(today)}==true
    fun sortTime(check:TodoCheck):Instant=check.completedAt ?: date(check)?.atStartOfDay(TodoRules.zone)?.toInstant() ?: Instant.MIN
    fun uploadable(day:String,recorded:String,serverDay:String):Boolean = runCatching {
        val d=LocalDate.parse(day);val server=LocalDate.parse(serverDay)
        day=="1970-01-01" || d==server || recorded.isNotBlank() && Instant.parse(recorded).atZone(TodoRules.zone).toLocalDate()==d && d<server
    }.getOrDefault(false)
}
