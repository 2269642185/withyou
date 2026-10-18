package com.withyou.anniversary

/** Stable keys are persisted, labels and enum order are free to change. */
enum class CardTemplate(val key: String, val label: String, val description: String, val photoFocused: Boolean = false) {
    CLASSIC("classic", "奶油经典", "柔和留白，记住每一天"),
    POLAROID("polaroid", "拍立得", "把回忆收进一张相片", true),
    PHOTO("photo", "全幅照片", "让珍贵瞬间铺满日常", true),
    MINIMAL("minimal", "极简数字", "让日子成为唯一主角"),
    TICKET("ticket", "纪念票根", "收藏一起出发的凭证"),
    NOTE("note", "手写便笺", "写给平凡日子的小心意");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** App card text can sit on paper even when the record retains an imported photo. */
internal fun MemoryDay.templateColors(): MemoryDay = when(template) {
    CardTemplate.CLASSIC -> this
    CardTemplate.PHOTO -> copy(photo=photo ?: "fallback")
    else -> copy(photo=null)
}
