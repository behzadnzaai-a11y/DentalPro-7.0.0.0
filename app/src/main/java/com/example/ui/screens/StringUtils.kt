package com.example.ui.screens

fun String.toEnglishLong(): Long {
    if (this.isBlank()) return 0L
    val persianToEnglish = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    val converted = this.map { persianToEnglish[it] ?: it }.joinToString("")
    val clean = converted.replace(Regex("[^0-9]"), "")
    return clean.toLongOrNull() ?: 0L
}

fun String.toEnglishLongOrNull(): Long? {
    if (this.isBlank()) return null
    val persianToEnglish = mapOf(
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    val converted = this.map { persianToEnglish[it] ?: it }.joinToString("")
    val clean = converted.replace(Regex("[^0-9]"), "")
    return clean.toLongOrNull()
}

fun parseTeethCount(toothNumStr: String): Int {
    if (toothNumStr.isBlank()) return 1
    val tokens = toothNumStr.split(Regex("[/,\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
    var count = 0
    for (token in tokens) {
        if (token.contains("-")) {
            val parts = token.split("-").mapNotNull { it.trim().toEnglishLongOrNull()?.toInt() }
            if (parts.size == 2 && parts[1] >= parts[0]) {
                count += (parts[1] - parts[0] + 1)
            } else {
                count += 1
            }
        } else if (token.any { it.isDigit() || "۰۱۲۳۴۵۶۷۸۹".contains(it) }) {
            count += 1
        }
    }
    return if (count > 0) count else 1
}

fun honorificPatientName(name: String, gender: String = ""): String {
    if (name.isBlank()) return name
    val clean = name.trim()
    if (clean.startsWith("آقای") || clean.startsWith("خانم") || clean.startsWith("Mr.") || clean.startsWith("Ms.")) {
        return clean
    }
    // Fallback if legacy record without prefix:
    val prefix = if (gender == "زن" || gender.lowercase() == "female" || gender == "خانم" || gender == "Ms.") "خانم " else "آقای "
    return prefix + clean
}


/**
 * قالب‌بندی ورودی عددی تاریخ شمسی با جداساز ثابت «/» — سال اول (YYYY/MM/DD)
 * مناسب «تاریخ / سال تولد»: فقط ارقام پذیرفته می‌شود و «/» پس از سال (۴ رقم) و ماه (۲ رقم)
 * خودکار درج می‌گردد. ورودِ فقط سال (مثلاً ۱۳۷۵) همچنان معتبر است.
 */
fun formatJalaliYearFirstInput(raw: String): String {
    val digits = raw.replace(Regex("[^0-9]"), "").take(8)
    val sb = StringBuilder()
    digits.forEachIndexed { index, c ->
        if (index == 4 || index == 6) sb.append('/')
        sb.append(c)
    }
    return sb.toString()
}

/**
 * قالب‌بندی ورودی عددی تاریخ شمسی با جداساز ثابت «/» — روز/ماه/سال (DD/MM/YYYY)
 * مناسب فرم نوبت: در بخش سال، پیشوند «14» خودکار وارد می‌شود؛ کاربر فقط دو رقم آخرِ سال را
 * می‌زند (مثلاً زدن «۰۵» سال ۱۴۰۵ را کامل می‌کند).
 */
fun formatJalaliDayFirstInput(raw: String): String {
    val digits = raw.replace(Regex("[^0-9]"), "").take(8)
    if (digits.isEmpty()) return ""
    val day = digits.take(2)
    val month = digits.drop(2).take(2)
    val yearDigits = digits.drop(4)
    // اگر سال از قبل کامل/نیمه‌کامل است (مثلاً هنگام ویرایش تاریخِ انتخاب‌شده از تقویم) دست‌نخورده
    // می‌ماند؛ اما تایپِ از صفر فقط دو رقم آخر را می‌گیرد و «14» خودکار جلو می‌نشیند.
    val yearStr = when {
        yearDigits.isEmpty() -> ""
        yearDigits.length >= 3 -> yearDigits.take(4)
        else -> "14" + yearDigits.take(2)
    }
    val sb = StringBuilder(day)
    if (digits.length > 2) sb.append('/').append(month)
    if (yearStr.isNotEmpty()) sb.append('/').append(yearStr)
    return sb.toString()
}

/**
 * تبدیل متن تاریخ روز/ماه/سال (با جداساز «/») به سه‌تایی (روز، ماه، سال) شمسی.
 * سالِ دو رقمی به‌صورت خودکار 14xx تفسیر می‌شود. در صورت نامعتبر بودن، null برمی‌گردد.
 */
fun parseJalaliDayFirstInput(text: String): Triple<Int, Int, Int>? {
    val digits = text.replace(Regex("[^0-9]"), "")
    if (digits.length < 6) return null
    val day = digits.take(2).toIntOrNull() ?: return null
    val month = digits.substring(2, 4).toIntOrNull() ?: return null
    val yearDigits = digits.substring(4)
    val year = when (yearDigits.length) {
        4 -> yearDigits.toIntOrNull()
        2 -> ("14" + yearDigits).toIntOrNull()
        else -> null
    } ?: return null
    if (day !in 1..31 || month !in 1..12 || year !in 1300..1499) return null
    return Triple(day, month, year)
}
