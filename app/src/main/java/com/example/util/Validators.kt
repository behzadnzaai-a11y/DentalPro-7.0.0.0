package com.example.util

private fun normalizeDigits(input: String): String {
    val map = mapOf(
        '۰' to '0','۱' to '1','۲' to '2','۳' to '3','۴' to '4',
        '۵' to '5','۶' to '6','۷' to '7','۸' to '8','۹' to '9',
        '٠' to '0','١' to '1','٢' to '2','٣' to '3','٤' to '4',
        '٥' to '5','٦' to '6','٧' to '7','٨' to '8','٩' to '9'
    )
    return input.map { map[it] ?: it }.joinToString("")
}

fun isValidAmount(raw: String): Boolean {
    if (raw.isBlank()) return true
    val normalized = normalizeDigits(raw).replace(Regex("[^0-9]"), "")
    return normalized.isNotEmpty() && (normalized.toLongOrNull() ?: -1L) >= 0
}

fun isValidPositiveAmount(raw: String): Boolean {
    if (raw.isBlank()) return false
    val normalized = normalizeDigits(raw).replace(Regex("[^0-9]"), "")
    return normalized.isNotEmpty() && (normalized.toLongOrNull() ?: 0L) > 0
}
