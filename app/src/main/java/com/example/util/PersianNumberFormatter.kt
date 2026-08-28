package com.example.util

/**
 * تبدیل عدد به حروف فارسی و تاریخ شمسیِ رقمی به فرمتِ «روز + نام ماه + سال».
 *
 * چرا این فایل لازم است: قبلاً AI مستقیماً «مبلغ به حروف» و «تاریخ» را هم به‌صورت جدا از روی
 * تصویر می‌خواند — که برای متن دست‌نویسِ کوچک و کم‌کیفیت، دقتش پایین‌تر از خواندنِ خودِ رقم‌هاست.
 * حالا AI فقط رقم را می‌خواند (که قابل‌اعتمادتر است) و این فایل، حروف/فرمت نمایشی را از روی
 * همان رقم به‌صورت قطعی (نه با حدسِ AI) محاسبه می‌کند.
 */
object PersianNumberFormatter {

    private val ones = arrayOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه",
        "ده", "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"
    )
    private val tens = arrayOf(
        "", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"
    )
    private val hundreds = arrayOf(
        "", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد"
    )
    private val scales = arrayOf("", " هزار", " میلیون", " میلیارد", " بیلیون")

    /** یک عدد سه‌رقمی (۰ تا ۹۹۹) را به حروف فارسی برمی‌گرداند. */
    private fun threeDigitsToWords(n: Int): String {
        if (n == 0) return ""
        val parts = mutableListOf<String>()
        val h = n / 100
        val remainder = n % 100
        if (h > 0) parts.add(hundreds[h])
        if (remainder in 1..19) {
            parts.add(ones[remainder])
        } else if (remainder >= 20) {
            val t = remainder / 10
            val o = remainder % 10
            if (o == 0) parts.add(tens[t]) else parts.add("${tens[t]} و ${ones[o]}")
        }
        return parts.joinToString(" و ")
    }

    /** [amount] را به حروف فارسی (مثلاً «بیست و پنج میلیون») برمی‌گرداند. صفر/منفی => رشته‌ی خالی. */
    fun numberToPersianWords(amount: Long): String {
        if (amount <= 0) return ""
        if (amount == 0L) return "صفر"
        var n = amount
        val groups = mutableListOf<Int>()
        while (n > 0) {
            groups.add((n % 1000).toInt())
            n /= 1000
        }
        val parts = mutableListOf<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g != 0) {
                if (i == 1 && g == 1) { // اگر ۱ هزار بود
                    parts.add("هزار")
                } else {
                    parts.add(threeDigitsToWords(g) + scales[i])
                }
            }
        }
        return parts.joinToString(" و ").trim()
    }

    private val jalaliMonthNames = arrayOf(
        "فروردین‌ماه", "اردیبهشت‌ماه", "خردادماه", "تیرماه", "مردادماه", "شهریورماه",
        "مهرماه", "آبان‌ماه", "آذرماه", "دی‌ماه", "بهمن‌ماه", "اسفندماه"
    )

    private val dayNames = arrayOf(
        "", "یکم", "دوم", "سوم", "چهارم", "پنجم", "ششم", "هفتم", "هشتم", "نهم",
        "دهم", "یازدهم", "دوازدهم", "سیزدهم", "چهاردهم", "پانزدهم", "شانزدهم", "هفدهم", "هجدهم", "نوزدهم",
        "بیستم", "بیست و یکم", "بیست و دوم", "بیست و سوم", "بیست و چهارم", "بیست و پنجم", "بیست و ششم", "بیست و هفتم", "بیست و هشتم", "بیست و نهم",
        "سی‌ام", "سی و یکم"
    )

    /** اعداد لاتین/فارسی/عربی را به رقم لاتین معمولی نرمال می‌کند (برای پارس امن ورودی‌های دست‌نویس). */
    private fun normalizeDigits(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(
                when (ch) {
                    '۰', '٠' -> '0'; '۱', '١' -> '1'; '۲', '٢' -> '2'; '۳', '٣' -> '3'; '۴', '٤' -> '4'
                    '۵', '٥' -> '5'; '۶', '٦' -> '6'; '۷', '٧' -> '7'; '۸', '٨' -> '8'; '۹', '٩' -> '9'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    /**
     * [digitsDate] باید شمسی و به‌فرمت YYYY-MM-DD باشد (خروجی خام AI). خروجی مثلاً
     * «سیزدهم خردادماه هزار و چهارصد و پنج» — یا رشته‌ی خالی اگر فرمت نامعتبر بود.
     */
    fun jalaliDigitsToFullDate(digitsDate: String): String {
        val normalized = normalizeDigits(digitsDate.trim())
        val parts = normalized.split("-", "/")
        if (parts.size != 3) return ""
        val y = parts[0].toIntOrNull() ?: return ""
        val m = parts[1].toIntOrNull() ?: return ""
        val d = parts[2].toIntOrNull() ?: return ""
        if (m !in 1..12 || d !in 1..31) return ""
        
        val dayWord = if (d in 1..31) dayNames[d] else ""
        val monthWord = jalaliMonthNames[m - 1]
        val yearWord = numberToPersianWords(y.toLong())
        
        return "$dayWord $monthWord $yearWord"
    }

    /** Formats a numeric amount with standard thousand separators. */
    fun formatAmount(amount: Long): String {
        return String.format(java.util.Locale.US, "%,d", amount)
    }
}
