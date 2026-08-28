package com.example.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * نتایج استخراج و پارس هوشمند پیامک‌های تراکنش بانکی ایران
 */
data class ParsedBankSms(
    val bankName: String,
    val transactionType: String, // "DEPOSIT" (واریز) or "WITHDRAWAL" (برداشت)
    val amountTomans: Long,
    val amountRials: Long,
    val amountWords: String,
    val accountNumber: String?,
    val cardNumber: String?,
    val balanceTomans: Long?,
    val dateStr: String?,
    val rawText: String,
    val isValid: Boolean,
    val errorMessage: String? = null
)

/**
 * پردازشگر و موتور هوشمند تشخیص الگوی پیامک‌های تراکنش تمام بانک‌های ایران
 */
object BankSmsProcessor {

    private val BANK_PATTERNS = listOf(
        "ملت" to "بانک ملت",
        "ملی" to "بانک ملی ایران",
        "صادرات" to "بانک صادرات ایران",
        "تجارت" to "بانک تجارت",
        "سپه" to "بانک سپه",
        "پاسارگاد" to "بانک پاسارگاد",
        "سامان" to "بانک سامان",
        "بلو" to "بلو بانک سامان",
        "کشاورزی" to "بانک کشاورزی",
        "پارسیان" to "بانک پارسیان",
        "شهر" to "بانک شهر",
        "رفاه" to "بانک رفاه کارگران",
        "مسکن" to "بانک مسکن",
        "سینا" to "بانک سینا",
        "دی" to "بانک دی",
        "اقتصاد نوین" to "بانک اقتصاد نوین",
        "مهر ایران" to "بانک قرض‌الحسنه مهر ایران",
        "رسالت" to "بانک قرض‌الحسنه رسالت",
        "آینده" to "بانک آینده",
        "گردشگری" to "بانک گردشگری"
    )

    fun cleanDigits(input: String): String {
        return input.map { ch ->
            when (ch) {
                '۰', '٠' -> '0'; '۱', '١' -> '1'; '۲', '٢' -> '2'; '۳', '٣' -> '3'; '۴', '٤' -> '4'
                '۵', '٥' -> '5'; '۶', '٦' -> '6'; '۷', '٧' -> '7'; '۸', '٨' -> '8'; '۹', '٩' -> '9'
                else -> ch
            }
        }.joinToString("")
    }

    /**
     * تحلیل، استخراج و پارس هوشمند متن پیامک تراکنش بانکی
     */
    fun parseSms(smsText: String): ParsedBankSms {
        if (smsText.isBlank()) {
            return emptyResult(smsText, "متن پیامک خالی است")
        }

        val text = cleanDigits(smsText)
        val normalizedText = text.replace(",", "").replace("،", "").replace(":", " ")

        // 1. Detect Bank Name
        var bankName = "بانک"
        for ((keyword, officialName) in BANK_PATTERNS) {
            if (smsText.contains(keyword, ignoreCase = true)) {
                bankName = officialName
                break
            }
        }

        // 2. Detect Transaction Type (DEPOSIT vs WITHDRAWAL)
        val hasDepositKeyword = normalizedText.contains("واریز") ||
                normalizedText.contains("افزایش") ||
                normalizedText.contains("بستانکار") ||
                normalizedText.contains("پایا واریز") ||
                normalizedText.contains("ساتنا واریز")

        val hasWithdrawalKeyword = normalizedText.contains("برداشت") ||
                normalizedText.contains("کاهش") ||
                normalizedText.contains("بدهکار") ||
                normalizedText.contains("خرید")

        val isDeposit: Boolean
        val isWithdrawal: Boolean
        when {
            hasDepositKeyword && !hasWithdrawalKeyword -> { isDeposit = true; isWithdrawal = false }
            hasWithdrawalKeyword && !hasDepositKeyword -> { isDeposit = false; isWithdrawal = true }
            hasDepositKeyword && hasWithdrawalKeyword -> { isDeposit = true; isWithdrawal = false }
            else -> {
                isDeposit = normalizedText.contains("+")
                isWithdrawal = !isDeposit && normalizedText.contains("-")
            }
        }

        val transactionType = if (isWithdrawal) "WITHDRAWAL" else "DEPOSIT"

        // 3. Extract Amount
        val isToman = normalizedText.contains("تومان") || normalizedText.contains("تومن")
        
        // Regex for amounts after words like مبلغ، واریز، برداشت، +، -
        val amountRegex = Regex("""(?:مبلغ|واریز|برداشت|خرید|تراکنش|\+|\-)[:\s]*(\d{4,15})""")
        val amountMatch = amountRegex.find(normalizedText)
        
        var rawAmount = amountMatch?.groupValues?.get(1)?.toLongOrNull()

        if (rawAmount == null) {
            // Fallback: search for first 5+ digit number in SMS
            val fallbackMatch = Regex("""\b(\d{5,15})\b""").find(normalizedText)
            rawAmount = fallbackMatch?.groupValues?.get(1)?.toLongOrNull()
        }

        if (rawAmount == null || rawAmount <= 0) {
            return emptyResult(smsText, "مبلغ تراکنش در متن پیامک یافت نشد")
        }

        val amountTomans = if (isToman) rawAmount else rawAmount / 10
        val amountRials = if (isToman) rawAmount * 10 else rawAmount
        val amountWords = PersianNumberFormatter.numberToPersianWords(amountTomans)

        // 4. Extract Account / Card Number
        val accRegex = Regex("""(?:حساب|کارت|به|از)\s*([0-9\*\-]{4,20})""")
        val accMatch = accRegex.find(normalizedText)
        val accountNumber = accMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

        // 5. Extract Balance
        val balanceRegex = Regex("""(?:موجودی|موجود)\s*(\d{4,15})""")
        val balanceMatch = balanceRegex.find(normalizedText)
        val rawBalance = balanceMatch?.groupValues?.get(1)?.toLongOrNull()
        val balanceTomans = rawBalance?.let { if (isToman) it else it / 10 }

        // 6. Extract Date & Time
        val dateRegex = Regex("""(14\d{2}[/\.-]\d{1,2}[/\.-]\d{1,2})""")
        val dateMatch = dateRegex.find(normalizedText)
        val dateStr = dateMatch?.groupValues?.get(1) ?: SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())

        return ParsedBankSms(
            bankName = bankName,
            transactionType = transactionType,
            amountTomans = amountTomans,
            amountRials = amountRials,
            amountWords = amountWords,
            accountNumber = accountNumber,
            cardNumber = null,
            balanceTomans = balanceTomans,
            dateStr = dateStr,
            rawText = smsText,
            isValid = true
        )
    }

    private fun emptyResult(rawText: String, error: String) = ParsedBankSms(
        bankName = "نامشخص",
        transactionType = "DEPOSIT",
        amountTomans = 0L,
        amountRials = 0L,
        amountWords = "",
        accountNumber = null,
        cardNumber = null,
        balanceTomans = null,
        dateStr = null,
        rawText = rawText,
        isValid = false,
        errorMessage = error
    )
}
