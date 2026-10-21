package com.withyou.anniversary

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDate

enum class CheckStatus(val label:String) { DONE("已打卡"), PENDING("未打卡"), OVERDUE("已逾期") }
enum class StatusFace(val label:String) {
    HAPPY("开心"), SMILE("微笑"), HOPEFUL("期待"), CALM("平静"), BLANK("发呆"),
    SLEEPY("困困"), WORRIED("担心"), SAD("委屈"), CRYING("哭哭"), PARTY("庆祝");
    companion object { fun fromKey(key:String?,fallback:StatusFace)=entries.firstOrNull {it.name==key} ?: fallback }
}
data class StatusFaces(val enabled:Boolean=true,val done:StatusFace=StatusFace.HAPPY,
    val pending:StatusFace=StatusFace.CALM,val overdue:StatusFace=StatusFace.CRYING) {
    fun face(status:CheckStatus)=when(status) {CheckStatus.DONE->done;CheckStatus.PENDING->pending;CheckStatus.OVERDUE->overdue}
    fun select(status:CheckStatus,face:StatusFace)=when(status) {
        CheckStatus.DONE->copy(done=face);CheckStatus.PENDING->copy(pending=face);CheckStatus.OVERDUE->copy(overdue=face)
    }
}
val LocalStatusFaces=staticCompositionLocalOf {StatusFaces()}

/** State follows the relevant user's completion; decorative faces never change scheduling. */
fun checkStatus(task:TodoTask,book:TodoBook,user:String,date:LocalDate):CheckStatus? = when {
    TodoRules.complete(book,task,user,date)->CheckStatus.DONE
    !TodoRules.scheduled(task,date)->null
    task.kind==TodoKind.TODO && task.due?.isBefore(date)==true->CheckStatus.OVERDUE
    else->CheckStatus.PENDING
}
