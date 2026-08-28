package com.example.util

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.ULocale

interface CalendarSystem {
    val typeName: String
    fun getToday(): Triple<Int, Int, Int>
    fun getMonthName(month: Int): String
    fun getDaysInMonth(year: Int, month: Int): Int
    fun getDayOfWeekIndex(year: Int, month: Int, day: Int): Int
    fun getWeekdayNames(): List<String>
    fun toTimestamp(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long
    fun fromTimestamp(timestamp: Long): Triple<Int, Int, Int>
}

object JalaliCalendarSystem : CalendarSystem {
    override val typeName = "SHAMSI"
    override fun getToday(): Triple<Int, Int, Int> {
        val j = PersianCalendarHelper.timestampToJalali(System.currentTimeMillis())
        return Triple(j.year, j.month, j.day)
    }
    override fun getMonthName(month: Int) = PersianCalendarHelper.MONTH_NAMES.getOrElse(month - 1) { "" }
    override fun getDaysInMonth(year: Int, month: Int) = PersianCalendarHelper.getDaysInMonth(year, month)
    override fun getDayOfWeekIndex(year: Int, month: Int, day: Int) = PersianCalendarHelper.getDayOfWeekIndex(year, month, day)
    override fun getWeekdayNames() = PersianCalendarHelper.WEEKDAY_NAMES
    override fun toTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int) = PersianCalendarHelper.jalaliToTimestamp(year, month, day, hour, minute)
    override fun fromTimestamp(timestamp: Long): Triple<Int, Int, Int> {
        val j = PersianCalendarHelper.timestampToJalali(timestamp)
        return Triple(j.year, j.month, j.day)
    }
}

object GregorianCalendarSystem : CalendarSystem {
    override val typeName = "MILADI"
    
    val MONTH_NAMES_FA = listOf("ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
    val WEEKDAY_NAMES_FA = listOf("یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه", "شنبه")
    
    override fun getToday(): Triple<Int, Int, Int> {
        val c = java.util.Calendar.getInstance()
        return Triple(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
    override fun getMonthName(month: Int) = MONTH_NAMES_FA.getOrElse(month - 1) { "" }
    override fun getDaysInMonth(year: Int, month: Int): Int {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.YEAR, year)
        c.set(java.util.Calendar.MONTH, month - 1)
        c.set(java.util.Calendar.DAY_OF_MONTH, 1)
        return c.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    }
    override fun getDayOfWeekIndex(year: Int, month: Int, day: Int): Int {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.YEAR, year)
        c.set(java.util.Calendar.MONTH, month - 1)
        c.set(java.util.Calendar.DAY_OF_MONTH, day)
        val dow = c.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
        // Jalali weekday index: 0=Saturday, 1=Sunday, 2=Monday, ..., 6=Friday
        // Gregorian: 0=Sunday, 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday
        // Let's just return Gregorian index here, and WEEKDAY_NAMES_FA handles it.
        return dow
    }
    override fun getWeekdayNames() = WEEKDAY_NAMES_FA
    override fun toTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.YEAR, year)
        c.set(java.util.Calendar.MONTH, month - 1)
        c.set(java.util.Calendar.DAY_OF_MONTH, day)
        c.set(java.util.Calendar.HOUR_OF_DAY, hour)
        c.set(java.util.Calendar.MINUTE, minute)
        c.set(java.util.Calendar.SECOND, 0)
        c.set(java.util.Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
    override fun fromTimestamp(timestamp: Long): Triple<Int, Int, Int> {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        return Triple(c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

object IslamicCalendarSystem : CalendarSystem {
    override val typeName = "QAMARI"
    override fun getToday(): Triple<Int, Int, Int> {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val c = IslamicCalendar(ULocale("@calendar=islamic-umalqura"))
            return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        } else {
            return JalaliCalendarSystem.getToday()
        }
    }
    override fun getMonthName(month: Int) = PersianCalendarHelper.QAMARI_MONTH_NAMES.getOrElse(month - 1) { "" }
    override fun getDaysInMonth(year: Int, month: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val c = IslamicCalendar(ULocale("@calendar=islamic-umalqura"))
            c.set(Calendar.YEAR, year)
            c.set(Calendar.MONTH, month - 1)
            c.set(Calendar.DAY_OF_MONTH, 1)
            return c.getActualMaximum(Calendar.DAY_OF_MONTH)
        }
        return 30
    }
    override fun getDayOfWeekIndex(year: Int, month: Int, day: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val c = IslamicCalendar(ULocale("@calendar=islamic-umalqura"))
            c.set(Calendar.YEAR, year)
            c.set(Calendar.MONTH, month - 1)
            c.set(Calendar.DAY_OF_MONTH, day)
            val dow = c.get(Calendar.DAY_OF_WEEK)
            // Islamic Calendar standard Android Calendar: 1=Sunday, 2=Monday, ..., 7=Saturday
            // We want Jalali indices: 0=Saturday ... 6=Friday
            val jDow = if (dow == Calendar.SATURDAY) 0 else dow
            return jDow
        }
        return JalaliCalendarSystem.getDayOfWeekIndex(year, month, day)
    }
    override fun getWeekdayNames() = PersianCalendarHelper.WEEKDAY_NAMES
    override fun toTimestamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val c = IslamicCalendar(ULocale("@calendar=islamic-umalqura"))
            c.set(Calendar.YEAR, year)
            c.set(Calendar.MONTH, month - 1)
            c.set(Calendar.DAY_OF_MONTH, day)
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, minute)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        return JalaliCalendarSystem.toTimestamp(year, month, day, hour, minute)
    }
    override fun fromTimestamp(timestamp: Long): Triple<Int, Int, Int> {
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            val c = IslamicCalendar(ULocale("@calendar=islamic-umalqura")).apply { timeInMillis = timestamp }
            return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
        return JalaliCalendarSystem.fromTimestamp(timestamp)
    }
}
