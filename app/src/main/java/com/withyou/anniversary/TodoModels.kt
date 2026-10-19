package com.withyou.anniversary

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class TodoKind(val label: String) { TODO("一次性待办"), HABIT("习惯打卡"), SHARED("共同事项") }
data class TodoTask(val id: String = UUID.randomUUID().toString(), val owner: String, val title: String,
    val kind: TodoKind = TodoKind.TODO, val notes: String = "", val due: LocalDate? = null,
    val weekdays: Set<Int> = (1..7).toSet(), val shared: Boolean = false, val pairId: String = "",
    val archived: Boolean = false, val deleted: Boolean = false, val revision: Long = 0)
data class TodoCheck(val taskId: String, val user: String, val day: LocalDate, val done: Boolean, val revision: Long = 0, val completedOn: LocalDate? = null)
data class PairInvite(val id: String, val owner: String, val candidate: String, val name: String, val expires: String)
data class TodoOperation(val id: String = UUID.randomUUID().toString(), val taskId: String, val body: String)
data class TodoBook(val uid: String, val pairId: String = "", val partnerId: String = "", val partnerName: String = "伙伴",
    val memories: List<SharedMemory> = emptyList(), val shareJobs: List<ShareJob> = emptyList(),
    val tasks: List<TodoTask> = emptyList(), val checks: List<TodoCheck> = emptyList(),
    val pending: List<TodoOperation> = emptyList(), val invites: List<PairInvite> = emptyList(),
    val taskCursor: Long = 0, val checkCursor: Long = 0, val syncedAt: Long = 0, val conflicts: List<String> = emptyList())

object TodoRules {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    fun today(): LocalDate = LocalDate.now(zone)
    fun checkDay(task: TodoTask, date: LocalDate) = if(task.kind==TodoKind.TODO) LocalDate.of(1970,1,1) else date
    fun scheduled(task: TodoTask, date: LocalDate) = !task.deleted && !task.archived && (task.kind==TodoKind.TODO || date.dayOfWeek.value in task.weekdays)
    fun canCheck(task: TodoTask, uid: String, pairId: String, date: LocalDate): Boolean = scheduled(task,date) &&
        (task.owner==uid || task.kind==TodoKind.SHARED && task.pairId==pairId && pairId.isNotBlank()) &&
        (!task.shared || task.pairId==pairId && pairId.isNotBlank())
    fun validate(task: TodoTask) {
        UUID.fromString(task.id)
        require(task.owner.isNotBlank() && task.title.trim().length in 1..80) { "标题需要 1～80 个字" }
        require(task.notes.length<=1000) { "备注最多 1000 个字" }
        require(task.weekdays.isNotEmpty() && task.weekdays.all {it in 1..7}) { "请至少选择一个打卡日" }
        require(task.kind!=TodoKind.SHARED || task.shared) { "共同事项需要双方可见" }
        require(!task.shared || task.pairId.isNotBlank()) { "先配对，再共享给伙伴" }
    }
    fun complete(book: TodoBook, task: TodoTask, uid: String, date: LocalDate) = book.checks.any {it.taskId==task.id && it.user==uid && it.day==checkDay(task,date) && it.done}
    fun bothComplete(book: TodoBook, task: TodoTask, date: LocalDate) = task.kind==TodoKind.SHARED && book.partnerId.isNotBlank() && task.pairId==book.pairId && complete(book,task,book.uid,date) && complete(book,task,book.partnerId,date)
    data class Progress(val done: Int, val total: Int, val unknown: Int = 0) {
        val fraction get() = if(total==0) 0f else done.toFloat()/total
    }
    fun progress(book: TodoBook, uid: String, date: LocalDate): Progress {
        val candidates=book.tasks.filter { t ->
            scheduled(t,date) && (t.owner==uid || t.kind==TodoKind.SHARED && t.pairId==book.pairId && book.pairId.isNotBlank()) &&
                (!t.shared || t.pairId==book.pairId && book.pairId.isNotBlank()) &&
                (uid==book.uid || t.shared && uid==book.partnerId)
        }
        val unknown=candidates.count {t->t.kind==TodoKind.TODO && book.checks.any {it.taskId==t.id && it.user==uid && it.done && it.completedOn==null}}
        val eligible=candidates.filter {t->t.kind!=TodoKind.TODO || !complete(book,t,uid,date) || book.checks.any {it.taskId==t.id && it.user==uid && it.done && it.completedOn==date}}
        return Progress(eligible.count {complete(book,it,uid,date)},eligible.size,unknown)
    }
    /** Revoked peer data must not survive a membership change or a visibility refresh. */
    fun prune(book: TodoBook, pairId: String, visibleIds: Set<String>): TodoBook {
        val tasks=book.tasks.filter {it.owner==book.uid || it.id in visibleIds && it.pairId==pairId && pairId.isNotBlank()}
        val ids=tasks.map {it.id}.toSet()
        return book.copy(tasks=tasks,checks=book.checks.filter {it.taskId in ids && (it.user==book.uid || pairId==book.pairId && pairId.isNotBlank())})
    }
}
