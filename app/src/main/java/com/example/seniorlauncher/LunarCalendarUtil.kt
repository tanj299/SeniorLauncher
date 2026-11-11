package com.example.seniorlauncher

import android.icu.util.ChineseCalendar

object LunarCalendarUtil {
    private val LUNAR_MONTHS = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "腊")
    private val LUNAR_DAYS = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun getTodayLunarDate(): String {
        val calendar = ChineseCalendar()
        val month = calendar.get(ChineseCalendar.MONTH)
        val day = calendar.get(ChineseCalendar.DAY_OF_MONTH)
        val isLeapMonth = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1

        val monthName = LUNAR_MONTHS.getOrNull(month) ?: "${month + 1}"
        val dayName = LUNAR_DAYS.getOrNull(day - 1) ?: "$day"
        val prefix = if (isLeapMonth) "闰" else ""
        return "农历$prefix${monthName}月$dayName"
    }
}
