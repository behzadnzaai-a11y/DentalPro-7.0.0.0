package com.example.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp

/**
 * ورودی هوشمند تاریخ تولد به فرمت xxxx/xx/xx (سال/ماه/روز) با پشتیبانی از:
 * ۱. تایپ مستقیم ارقام به صورت ۸ رقمی پیوسته (مثلاً ۱۳۶۵۱۱۰۸ -> ۱۳۶۵/۱۱/۰۸) بدون مسدود شدن.
 * ۲. پشتیبانی از کیبوردهای فارسی و عربی (تبدیل خودکار ارقام ۰-۹ و ٠-٩).
 * ۳. پشتیبانی از جداکننده‌های مختلف (/, -, ., _, فاصله، ؍، ٫، \).
 * ۴. اعتبارسنجی مقادیر ماه (۱ تا ۱۲) و روز (۱ تا ۳۱).
 * ۵. تکمیل صفرهای ماه و روز هنگام خروج از فیلد (Blur).
 * ۶. هماهنگی دوطرفه با فیلد سن بیمار.
 */
@Composable
fun BirthDateInput(
    birthDateStr: String,
    onBirthDateChange: (String) -> Unit,
    isPersian: Boolean,
    modifier: Modifier = Modifier
) {
    var textValue by remember { mutableStateOf(birthDateStr) }
    var lastEmitted by remember { mutableStateOf(birthDateStr) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // هماهنگی با تغییرات بیرونی مقدار (مثلاً وقتی سن بیمار تغییر می‌کند)
    LaunchedEffect(birthDateStr) {
        if (birthDateStr != lastEmitted) {
            textValue = birthDateStr
            lastEmitted = birthDateStr
            errorMessage = null
        }
    }

    fun normalizeCharacters(raw: String): String {
        val sb = StringBuilder()
        for (c in raw) {
            when (c) {
                '۰', '٠' -> sb.append('0')
                '۱', '١' -> sb.append('1')
                '۲', '٢' -> sb.append('2')
                '۳', '٣' -> sb.append('3')
                '۴', '٤' -> sb.append('4')
                '۵', '٥' -> sb.append('5')
                '۶', '٦' -> sb.append('6')
                '۷', '٧' -> sb.append('7')
                '۸', '٨' -> sb.append('8')
                '۹', '٩' -> sb.append('9')
                '/', '-', '.', '_', ' ', '؍', '٫', '\\' -> sb.append('/')
                else -> if (c.isDigit() || c == '/') sb.append(c)
            }
        }
        return sb.toString()
    }

    fun processInput(raw: String) {
        val normalized = normalizeCharacters(raw)

        if (normalized.isBlank()) {
            textValue = ""
            errorMessage = null
            lastEmitted = ""
            onBirthDateChange("")
            return
        }

        val formatted: String
        val hasSlash = normalized.contains('/')

        if (!hasSlash) {
            // کاربر ارقام را بدون اسلش تایپ کرده است (مثلاً 13651108) -> قالب‌بندی خودکار تا ۸ رقم
            val digits = normalized.filter { it.isDigit() }.take(8)
            formatted = when {
                digits.length <= 4 -> digits
                digits.length <= 6 -> "${digits.take(4)}/${digits.drop(4)}"
                else -> "${digits.take(4)}/${digits.substring(4, 6)}/${digits.drop(6)}"
            }
        } else {
            // اسلش در ورودی وجود دارد (تایپ دستی یا باقی‌مانده از ویرایش قبلی)
            val segments = normalized.split('/')
            val first = segments[0]
            val y = first.take(4)
            val mOverflow = if (first.length > 4) first.drop(4) else ""

            val second = mOverflow + (segments.getOrNull(1) ?: "")
            val m = second.take(2)
            val dOverflow = if (second.length > 2) second.drop(2) else ""

            val third = dOverflow + (segments.getOrNull(2) ?: "")
            val d = third.take(2)

            val parts = mutableListOf<String>()
            if (y.isNotEmpty()) parts.add(y)
            if (segments.size > 1 || mOverflow.isNotEmpty() || dOverflow.isNotEmpty()) {
                parts.add(m)
            }
            if (segments.size > 2 || dOverflow.isNotEmpty()) {
                parts.add(d)
            }

            val joined = parts.joinToString("/")
            formatted = if (normalized.endsWith('/') && parts.size <= 2 && (parts.isEmpty() || parts.last().isNotEmpty())) {
                "$joined/"
            } else {
                joined
            }
        }

        // اعتبارسنجی ماه و روز
        var validationError: String? = null
        val parts = formatted.split('/')
        if (parts.size > 1 && parts[1].length == 2) {
            val mVal = parts[1].toIntOrNull() ?: 0
            if (mVal == 0) {
                validationError = if (isPersian) "ماه نامعتبر است" else "Invalid month"
            } else if (mVal > 12) {
                validationError = if (isPersian) "ماه نمی‌تواند بیشتر از ۱۲ باشد" else "Month cannot exceed 12"
            }
        }

        if (parts.size > 2 && parts[2].length == 2) {
            val dVal = parts[2].toIntOrNull() ?: 0
            if (dVal == 0) {
                validationError = if (isPersian) "روز نامعتبر است" else "Invalid day"
            } else if (dVal > 31) {
                validationError = if (isPersian) "روز نمی‌تواند بیشتر از ۳۱ باشد" else "Day cannot exceed 31"
            }
        }

        textValue = formatted
        errorMessage = validationError
        lastEmitted = formatted
        onBirthDateChange(formatted)
    }

    fun handleBlur() {
        if (textValue.isBlank()) return
        val parts = textValue.split('/').map { it.trim() }
        val year = parts.getOrNull(0) ?: ""
        if (year.isEmpty()) return

        val monthRaw = parts.getOrNull(1) ?: ""
        val dayRaw = parts.getOrNull(2) ?: ""

        val padded = when {
            monthRaw.isNotEmpty() && dayRaw.isNotEmpty() -> {
                val m = monthRaw.padStart(2, '0').take(2)
                val d = dayRaw.padStart(2, '0').take(2)
                "$year/$m/$d"
            }
            monthRaw.isNotEmpty() -> {
                val m = monthRaw.padStart(2, '0').take(2)
                "$year/$m"
            }
            else -> year
        }

        if (padded != textValue) {
            textValue = padded
            lastEmitted = padded
            onBirthDateChange(padded)
        }
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { processInput(it) },
        label = { Text(if (isPersian) "تاریخ تولد (xxxx/xx/xx)" else "Birth Date (xxxx/xx/xx)", fontSize = 11.sp) },
        placeholder = { Text("xxxx/xx/xx (مثال: 13651108)", fontSize = 11.sp) },
        supportingText = {
            if (errorMessage != null) {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
            } else {
                Text(
                    text = if (isPersian) "سال الزامی (xxxx)، ماه (حداکثر 12) و روز (حداکثر 31) اختیاری" else "Year required (xxxx), Month (max 12) & Day (max 31) optional",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        isError = errorMessage != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.onFocusEvent { state ->
            if (!state.isFocused) handleBlur()
        }
    )
}
