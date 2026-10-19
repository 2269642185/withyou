package com.withyou.anniversary

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

enum class CountMode(val label: String) { INCLUSIVE("开始当天算第 1 天"), ELAPSED("实际间隔天数") }
enum class AppTheme(val label: String) { SYSTEM("跟随系统"), LIGHT("暖白浅色"), DARK("温柔深色") }
data class MemoryDay(
    val id: String,
    val title: String,
    val date: LocalDate,
    val nickname: String = "",
    val mode: CountMode = CountMode.INCLUSIVE,
    val color: Int = 0,
    val photo: String? = null,
    val pinned: Boolean = false,
    val appearance: CardAppearance = CardAppearance(),
    val categoryId: String? = null,
    val template: CardTemplate = CardTemplate.CLASSIC,
)
data class Category(val id: String, val name: String, val icon: String = "heart", val color: Int = 0)
data class Preferences(val theme: AppTheme = AppTheme.SYSTEM, val defaultMode: CountMode = CountMode.INCLUSIVE, val animations: Boolean = true)
data class AppData(val records: List<MemoryDay> = emptyList(), val preferences: Preferences = Preferences(), val categories: List<Category> = emptyList()) {
    fun ordered() = records.sortedWith(compareByDescending<MemoryDay> { it.pinned }.thenBy { it.date }.thenBy { it.id })
    fun deleteCategory(id: String) = copy(categories = categories.filterNot { it.id == id }, records = records.map { if (it.categoryId == id) it.copy(categoryId = null) else it })
}
data class DayCount(val days: Long, val caption: String, val today: Boolean)
object DateRules {
    fun count(start: LocalDate, today: LocalDate, mode: CountMode): DayCount {
        val delta = ChronoUnit.DAYS.between(start, today)
        return when {
            delta < 0 -> DayCount(-delta, "还有", false)
            delta == 0L -> DayCount(if (mode == CountMode.INCLUSIVE) 1 else 0, "就是今天", true)
            else -> DayCount(delta + if (mode == CountMode.INCLUSIVE) 1 else 0, "已经", false)
        }
    }
    // MonthDay.atYear explicitly clamps February 29 to February 28 in common years.
    fun nextAnniversary(start: LocalDate, today: LocalDate): LocalDate {
        if (today < start) return start
        val thisYear = MonthDay.from(start).atYear(today.year)
        return if (thisYear < today) MonthDay.from(start).atYear(today.year + 1) else thisYear
    }
    fun untilAnniversary(start: LocalDate, today: LocalDate) = ChronoUnit.DAYS.between(today, nextAnniversary(start, today))
}
