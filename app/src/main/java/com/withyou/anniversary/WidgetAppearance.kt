package com.withyou.anniversary

/** One policy for the launcher and configuration preview. Independent instances stay plain. */
data class WidgetAppearance(
    val template: CardTemplate,
    val background: String,
    val ink: String,
    val title: String,
    val days: String,
    val auxiliary: String,
    val photoName: String?,
    val photoBehindText: Boolean,
    val photoPanel: Boolean,
    val overlay: Float,
) {
    companion object {
        fun resolve(record: MemoryDay?, choice: WidgetChoice?, kind: Int): WidgetAppearance {
            val follow=record!=null && choice?.followColors!=false
            val template=if(follow) record!!.template else CardTemplate.CLASSIC
            val panel=template==CardTemplate.POLAROID && kind!=0
            val behind=template==CardTemplate.PHOTO || (template==CardTemplate.POLAROID && kind==0) || (template==CardTemplate.CLASSIC && follow && record!!.photo!=null)
            val styled=if(follow) record!!.copy(photo=if(behind) record.photo ?: "fallback" else null)
                else MemoryDay("widget","",java.time.LocalDate.of(2000,1,1),appearance=CardAppearance(background=choice?.background ?: "#F0DCC8",tone=if(choice?.text==null) TextTone.AUTO else TextTone.CUSTOM,text=choice?.text))
            val ink=ColorRules.ink(styled)
            val a=styled.appearance
            val overlay=if(a.tone==TextTone.AUTO) maxOf(if(template==CardTemplate.CLASSIC) .67f else .72f,a.overlay) else maxOf(.25f,a.overlay)
            return WidgetAppearance(template,ColorRules.background(styled),ink,a.title ?: ink,a.days ?: ink,a.auxiliary ?: ink,
                if(follow && (behind || panel)) record!!.photo else null,behind,panel,overlay)
        }
    }
}

/** Dimensions in dp, independent of a launcher's arbitrary grid cell labels. */
data class WidgetGeometry(val width: Float,val height: Float,val kind: Int) {
    val left: Float get()=if(kind==0) 12f else 18f
    fun contentLeft(style: WidgetAppearance)=if(style.photoPanel && kind==1) width*.38f+14f else left
    fun contentTop(style: WidgetAppearance)=if(style.photoPanel && kind==2) height*.28f+20f else if(style.template==CardTemplate.NOTE) (if(kind==0) 14f else 22f) else 12f
    companion object {
        fun kind(width: Float,height: Float)=if(width>=240 && height>=250) 2 else if(width>=240 && height>=130) 1 else 0
        fun defaults(kind: Int)=when(kind) {0 ->WidgetGeometry(140f,140f,0);2 ->WidgetGeometry(280f,280f,2);else ->WidgetGeometry(280f,150f,1)}
    }
}
