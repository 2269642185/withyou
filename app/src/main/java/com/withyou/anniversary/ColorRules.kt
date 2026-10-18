package com.withyou.anniversary

import kotlin.math.pow

enum class TextTone(val label: String) { AUTO("自动适配"), DARK("深色"), LIGHT("浅色"), CUSTOM("自定义") }
data class CardAppearance(
    val background: String? = null,
    val tone: TextTone = TextTone.AUTO,
    val text: String? = null,
    val title: String? = null,
    val days: String? = null,
    val auxiliary: String? = null,
    val overlay: Float = .56f,
)
data class ColorSwatch(val name: String, val hex: String, val group: String)
object ColorRules {
    // The first five entries preserve the original serialized indices and appearance.
    val swatches = listOf(
        ColorSwatch("浅杏", "#F0DCC8", "奶油"), ColorSwatch("藕粉", "#E8D5D5", "花瓣"),
        ColorSwatch("鼠尾草", "#D9E2D1", "自然"), ColorSwatch("雾蓝", "#D8DFE9", "天空"),
        ColorSwatch("浅藤", "#E4D9E9", "暮色"),
        ColorSwatch("暖白", "#FFF7E9", "奶油"), ColorSwatch("燕麦", "#EADCC6", "奶油"),
        ColorSwatch("香草", "#F4E6B9", "奶油"), ColorSwatch("奶茶", "#D9BA9C", "奶油"), ColorSwatch("可可", "#73584A", "奶油"),
        ColorSwatch("樱花", "#F5DEE2", "花瓣"), ColorSwatch("蜜桃", "#F3CEBC", "花瓣"),
        ColorSwatch("玫瑰", "#CC9599", "花瓣"), ColorSwatch("莓果", "#8C4E61", "花瓣"), ColorSwatch("珊瑚", "#EBA79A", "花瓣"),
        ColorSwatch("苔绿", "#A8B497", "自然"), ColorSwatch("薄荷", "#DAEDE2", "自然"),
        ColorSwatch("橄榄", "#7A8765", "自然"), ColorSwatch("松林", "#405E51", "自然"), ColorSwatch("青柠", "#E4E7B9", "自然"),
        ColorSwatch("晴空", "#D6EBF4", "天空"), ColorSwatch("海盐", "#BAD9DE", "天空"),
        ColorSwatch("雨蓝", "#9DAEC6", "天空"), ColorSwatch("海湾", "#43677C", "天空"), ColorSwatch("云白", "#EBEFF1", "天空"),
        ColorSwatch("暮紫", "#B6A2BF", "暮色"), ColorSwatch("落日", "#EAC6A5", "暮色"),
        ColorSwatch("夜空", "#394052", "暮色"), ColorSwatch("梅影", "#645267", "暮色"), ColorSwatch("月灰", "#CBC7C5", "暮色"),
    )
    val groups = listOf("奶油", "花瓣", "自然", "天空", "暮色")
    val textChoices = listOf("#49392D", "#FFFFFF", "#684D58", "#35564B", "#344E6C", "#6B4D25")
    fun normalize(value: String): String? = value.trim().removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }?.let { "#${it.uppercase(java.util.Locale.ROOT)}" }
    fun argb(hex: String): Int = (0xFF000000L or hex.removePrefix("#").toLong(16)).toInt()
    private fun luminance(hex: String): Double {
        val v = argb(hex)
        fun channel(shift: Int): Double { val c = ((v shr shift) and 255) / 255.0; return if(c <= .04045) c/12.92 else ((c+.055)/1.055).pow(2.4) }
        return channel(16)*.2126+channel(8)*.7152+channel(0)*.0722
    }
    fun contrast(a: String, b: String): Double { val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05) }
    fun background(record: MemoryDay) = record.appearance.background ?: swatches[record.color.coerceIn(swatches.indices)].hex
    fun autoInk(background: String) = if(contrast(background,"#49392D") >= 4.5) "#49392D" else if(contrast(background,"#FFFFFF") >= contrast(background,"#000000")) "#FFFFFF" else "#000000"
    fun ink(record: MemoryDay): String = with(record.appearance) {
        when(tone) { TextTone.AUTO -> if(record.photo != null) "#FFFFFF" else autoInk(background(record)); TextTone.DARK -> "#49392D"; TextTone.LIGHT -> "#FFFFFF"; TextTone.CUSTOM -> text ?: "#49392D" }
    }
    fun isAnnualDay(record: MemoryDay, today: java.time.LocalDate) = today >= record.date && DateRules.nextAnniversary(record.date,today) == today
}
