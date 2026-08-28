package com.example.util

import java.util.Calendar

data class JalaliDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int    // 1..31
) {
    override fun toString(): String = "$year/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}"
}

data class QamariDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int,
    val monthName: String
) {
    override fun toString(): String = "$day $monthName $year (قمری)"
}

data class MiladiDate(
    val year: Int,
    val month: Int, // 1..12
    val day: Int,
    val monthNameFa: String,
    val monthNameEn: String
) {
    override fun toString(): String = "$day $monthNameFa $year (میلادی)"
}

data class ThreeCalendarsDate(
    val jalali: JalaliDate,
    val qamari: QamariDate,
    val miladi: MiladiDate
)

data class CalendarOccasion(
    val titleFa: String,
    val isHoliday: Boolean,
    val isPersonal: Boolean = false
)

object PersianCalendarHelper {

    val MONTH_NAMES = listOf(
        "فروردین", "اردیبهشت", "خرداد",
        "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر",
        "دی", "بهمن", "اسفند"
    )

    val WEEKDAY_NAMES = listOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
    )

    val QAMARI_MONTH_NAMES = listOf(
        "محرم", "صفر", "ربیع‌الاول", "ربیع‌الثانی",
        "جمادی‌الاول", "جمادی‌الثانی", "رجب", "شعبان",
        "رمضان", "شوال", "ذی‌القعده", "ذی‌الحجه"
    )

    val MILADI_MONTH_NAMES_FA = listOf(
        "ژانویه", "فوریه", "مارس", "آوریل", "می", "ژوئن",
        "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر"
    )

    val MILADI_MONTH_NAMES_EN = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun isJalaliLeapYear(year: Int): Boolean {
        val remainder = year % 33
        return remainder == 1 || remainder == 5 || remainder == 9 || remainder == 13 || remainder == 17 || remainder == 22 || remainder == 26 || remainder == 30
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        return when (month) {
            in 1..6 -> 31
            in 7..11 -> 30
            12 -> if (isJalaliLeapYear(year)) 30 else 29
            else -> 30
        }
    }

    fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

        var gy = gYear - 1600
        var gm = gMonth - 1
        var gd = gDay - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) {
            gDayNo += gDaysInMonth[i]
        }
        if (gm > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        var jm = 0
        while (jm < 11 && jDayNo >= jDaysInMonth[jm]) {
            jDayNo -= jDaysInMonth[jm]
            jm++
        }
        val jd = jDayNo + 1

        return JalaliDate(jy, jm + 1, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        var jY = jy - 979
        var jM = jm - 1
        var jD = jd - 1

        var jDayNo = 365 * jY + (jY / 33) * 8 + (jY % 33 + 3) / 4
        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        for (i in 0 until jM) {
            jDayNo += jDaysInMonth[i]
        }
        jDayNo += jD

        var gDayNo = jDayNo + 79

        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097

        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524

            if (gDayNo >= 365) {
                gDayNo++
            } else {
                leap = false
            }
        }

        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461

        if (gDayNo >= 366) {
            leap = false
            gDayNo--
            gy += gDayNo / 365
            gDayNo %= 365
        }

        val gDaysInMonth = intArrayOf(31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gDayNo >= gDaysInMonth[gm]) {
            gDayNo -= gDaysInMonth[gm]
            gm++
        }
        val gd = gDayNo + 1

        return Triple(gy, gm + 1, gd)
    }

    fun timestampToJalali(timestamp: Long): JalaliDate {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun jalaliToTimestamp(jy: Int, jm: Int, jd: Int, hour: Int = 0, minute: Int = 0): Long {
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getDayOfWeekIndex(jy: Int, jm: Int, jd: Int): Int {
        val (gy, gm, gd) = jalaliToGregorian(jy, jm, jd)
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            Calendar.FRIDAY -> 6
            else -> 0
        }
    }

    /**
     * Converts a Jalali date in 1405 SH to all 3 calendars (Jalali, Qamari, Miladi)
     * strictly matching the 1405 PDF specifications.
     */
    fun get3CalendarsFor1405(jm: Int, jd: Int): ThreeCalendarsDate {
        val jalali = JalaliDate(1405, jm, jd)

        val dayOfYear = if (jm <= 6) {
            (jm - 1) * 31 + jd
        } else if (jm <= 11) {
            186 + (jm - 7) * 30 + jd
        } else {
            336 + jd
        }

        // Miladi conversion (2026/2027) based on 1 Farvardin 1405 = 21 March 2026
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 21, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOfYear - 1)
        }
        val gYear = cal.get(Calendar.YEAR)
        val gMonth = cal.get(Calendar.MONTH) + 1
        val gDay = cal.get(Calendar.DAY_OF_MONTH)
        val miladiMonthFa = MILADI_MONTH_NAMES_FA.getOrElse(gMonth - 1) { "" }
        val miladiMonthEn = MILADI_MONTH_NAMES_EN.getOrElse(gMonth - 1) { "" }
        val miladi = MiladiDate(gYear, gMonth, gDay, miladiMonthFa, miladiMonthEn)

        // Qamari conversion (1447/1448) strictly matching the 1405 PDF
        val (qYear, qMonth, qDay) = when (dayOfYear) {
            in 1..29 -> Triple(1447, 10, dayOfYear)               // 1..29 Shawwal 1447
            in 30..59 -> Triple(1447, 11, dayOfYear - 29)          // 1..30 Dhu al-Qadah 1447
            in 60..88 -> Triple(1447, 12, dayOfYear - 59)          // 1..29 Dhu al-Hijjah 1447
            in 89..117 -> Triple(1448, 1, dayOfYear - 88)          // 1..29 Muharram 1448
            in 118..146 -> Triple(1448, 2, dayOfYear - 117)        // 1..29 Safar 1448
            in 147..176 -> Triple(1448, 3, dayOfYear - 146)        // 1..30 Rabi' al-Awwal 1448
            in 177..205 -> Triple(1448, 4, dayOfYear - 176)        // 1..29 Rabi' al-Thani 1448
            in 206..235 -> Triple(1448, 5, dayOfYear - 205)        // 1..30 Jumada al-Awwal 1448
            in 236..264 -> Triple(1448, 6, dayOfYear - 235)        // 1..29 Jumada al-Thani 1448
            in 265..295 -> Triple(1448, 7, if (dayOfYear == 295) 30 else (dayOfYear - 264)) // 1..30 Rajab 1448
            in 296..324 -> Triple(1448, 8, dayOfYear - 295)        // 1..29 Sha'ban 1448
            in 325..354 -> Triple(1448, 9, dayOfYear - 324)        // 1..30 Ramadan 1448
            else -> Triple(1448, 10, dayOfYear - 354)              // 1..11 Shawwal 1448
        }
        val qamariMonthName = QAMARI_MONTH_NAMES.getOrElse(qMonth - 1) { "" }
        val qamari = QamariDate(qYear, qMonth, qDay, qamariMonthName)

        return ThreeCalendarsDate(jalali, qamari, miladi)
    }

    fun getOccasions(jy: Int, jm: Int, jd: Int): List<CalendarOccasion> {
        val result = mutableListOf<CalendarOccasion>()
        val isFriday = getDayOfWeekIndex(jy, jm, jd) == 6
        if (isFriday) {
            result.add(CalendarOccasion("جمعه (تعطیل هفتگی)", true))
        }
        return result
    }

    fun isHoliday(jy: Int, jm: Int, jd: Int): Boolean {
        return getDayOfWeekIndex(jy, jm, jd) == 6
    }

    fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isWorkingDay(jy: Int, jm: Int, jd: Int, workingDaysConfig: String = "0,1,2,3,4,5"): Boolean {
        if (isHoliday(jy, jm, jd)) return false
        val dayOfWeek = getDayOfWeekIndex(jy, jm, jd)
        val activeDays = workingDaysConfig.split(",").mapNotNull { it.trim().toIntOrNull() }
        return dayOfWeek in activeDays
    }
}
