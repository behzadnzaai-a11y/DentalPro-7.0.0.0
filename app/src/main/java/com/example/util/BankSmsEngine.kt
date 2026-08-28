package com.example.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 1. نرمال‌سازی کامل متون، حروف، اعداد و کاراکترهای نامرئی (SmsNormalizer)
 */
object SmsNormalizer {

    fun toEnglishDigits(text: String): String {
        return text.map { ch ->
            when (ch) {
                '۰', '٠' -> '0'; '۱', '١' -> '1'; '۲', '٢' -> '2'; '۳', '٣' -> '3'; '۴', '٤' -> '4'
                '۵', '٥' -> '5'; '۶', '٦' -> '6'; '۷', '٧' -> '7'; '۸', '٨' -> '8'; '۹', '٩' -> '9'
                else -> ch
            }
        }.joinToString("")
    }

    fun normalizeArabicPersianLetters(text: String): String {
        return text.replace('ك', 'ک').replace('ي', 'ی')
    }

    fun stripInvisibleMarks(text: String): String {
        return text.replace("\u200E", "")
            .replace("\u200F", "")
            .replace("\u200C", " ") // ZWNJ
            .replace("\u00A0", " ")
    }

    fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    fun fullNormalize(text: String): String {
        return normalizeLineEndings(stripInvisibleMarks(normalizeArabicPersianLetters(toEnglishDigits(text))))
    }

    fun cleanAccountNumber(raw: String): String {
        return toEnglishDigits(raw).replace("*", "")
            .replace("-", "")
            .replace(".", "")
            .replace(",", "")
            .replace("/", "")
            .replace(" ", "")
            .trim()
    }
}

/**
 * 2. فیلتر پیامک‌های رمز، کد ورود، کارمزد و اخطار (PasswordSmsFilter)
 */
object PasswordSmsFilter {
    private val REJECT_KEYWORDS = listOf(
        "رمز", "رمز دوم", "کد ورود", "ورود", "هشدار", "اقساط قرارداد",
        "تایید مجوز برداشت", "شناسه تاييد برداشت", "مدت زمان اعتبار تراکنش",
        "جهت انتقال وجه", "با آبونمان", "کد تایید", "رمز پویا", "قبوض",
        "فعالسازی", "اعتبار سنجی", "تایید ورود",
        "آنلاین شدی", "شارژ شدی"
    )

    fun isPasswordOrFeeSms(content: String): Boolean {
        val normalized = SmsNormalizer.fullNormalize(content)
        
        // Fee SMS with validity expiration is rejected
        val hasFee = normalized.contains("کارمزد")
        val hasValidity = normalized.contains("مدت زمان اعتبار")
        if (hasFee && hasValidity) return true

        return REJECT_KEYWORDS.any { normalized.contains(it) }
    }
}

/**
 * 3. ساختار الگوها و الگوریتم امتیازدهی ۵ بخشی (ScoringEngine)
 */
data class PatternCheckCandidate(
    val bankName: String,
    val transactionType: String, // "DEPOSIT" or "WITHDRAWAL"
    val amountTomans: Long,
    val amountRials: Long,
    val accountNumberRaw: String,
    val dateStr: String,
    val timeStr: String,
    val score: Int,
    val rawSms: String
)

object BankSmsEngine {

    private val BANK_NUMBERS_MAP = mapOf(
        "بانک ملت" to listOf("b.mellat", "mellat", "20000", "9820000", "+9820000"),
        "بانک ملی ایران" to listOf("b.melli", "melli", "30000", "9830000", "+9830000"),
        "بانک صادرات ایران" to listOf("b.saderat", "saderat", "2000", "982000", "+982000"),
        "بانک تجارت" to listOf("b.tejarat", "tejarat", "200017", "98200017", "+98200017"),
        "بانک سپه" to listOf("b.sepah", "sepah", "500015", "98500015", "+98500015"),
        "بانک پاسارگاد" to listOf("b.pasargad", "pasargad", "500019000", "98500019000", "+98500019000"),
        "بانک سامان" to listOf("b.saman", "saman", "20008590", "9820008590"),
        "بلو بانک سامان" to listOf("blubank", "blue", "بلو", "30008590"),
        "بانک کشاورزی" to listOf("b.keshavarzi", "keshavarzi", "2000911", "982000911"),
        "بانک پارسیان" to listOf("b.parsian", "parsian", "30008030", "9830008030"),
        "بانک شهر" to listOf("b.shahr", "shahr", "2000490", "982000490"),
        "بانک رفاه کارگران" to listOf("b.refah", "refah", "30009000", "9830009000"),
        "بانک مسکن" to listOf("b.maskan", "maskan", "300025", "98300025"),
        "بانک سینا" to listOf("b.sina", "sina", "200022", "98200022"),
        "بانک دی" to listOf("b.dey", "dey", "300020", "98300020"),
        "بانک اقتصاد نوین" to listOf("b.en", "enbank", "200080", "98200080"),
        "بانک قرض‌الحسنه مهر ایران" to listOf("b.mehr", "mehriran", "30008000", "9830008000"),
        "بانک قرض‌الحسنه رسالت" to listOf("b.resalat", "resalat", "20004000", "9820004000"),
        "بانک آینده" to listOf("b.ayandeh", "ayandeh", "30008300", "9830008300"),
        "بانک گردشگری" to listOf("b.tourism", "gardeshgari", "30008100", "9830008100")
    )

    fun isBankSender(senderAddress: String): Boolean {
        val cleanSender = SmsNormalizer.toEnglishDigits(senderAddress).lowercase()
        return BANK_NUMBERS_MAP.values.any { list ->
            list.any { num -> cleanSender.contains(num.lowercase()) }
        }
    }

    /**
     * برخی گوشی‌ها/اپراتورها فیلد sender را با شماره/کد کوتاه پر می‌کنند نه نام بانک، در حالی
     * که خودِ متن پیامک با نام برند بانک (مثلاً «بلو») شروع می‌شود. برای جلوگیری از رد نادرستِ
     * پیامک‌های واقعی بانکی که فرستنده‌شان با هیچ‌کدام از مقادیرِ BANK_NUMBERS_MAP مطابقت
     * ندارد، این تابع علاوه بر sender، ابتدای متن پیامک را هم بررسی می‌کند.
     */
    fun isBankSenderOrText(senderAddress: String, smsText: String): Boolean {
        if (isBankSender(senderAddress)) return true
        val cleanText = SmsNormalizer.fullNormalize(smsText).lowercase()
        return BANK_NUMBERS_MAP.values.any { list ->
            list.any { num -> num.length >= 3 && cleanText.contains(num.lowercase()) }
        }
    }

    fun detectBankNameBySender(senderAddress: String): String {
        val cleanSender = SmsNormalizer.toEnglishDigits(senderAddress).lowercase()
        for ((bankName, list) in BANK_NUMBERS_MAP) {
            if (list.any { num -> cleanSender.contains(num.lowercase()) }) {
                return bankName
            }
        }
        return "بانک"
    }

    /**
     * مشابه [detectBankNameBySender] اما اگر فرستنده مطابقتی نداشت، ابتدای متن پیامک را هم
     * برای نام برند بانک بررسی می‌کند (نگاه کنید به [isBankSenderOrText]).
     */
    fun detectBankNameBySenderOrText(senderAddress: String, smsText: String): String {
        val bySender = detectBankNameBySender(senderAddress)
        if (bySender != "بانک") return bySender
        val cleanText = SmsNormalizer.fullNormalize(smsText).lowercase()
        for ((bankName, list) in BANK_NUMBERS_MAP) {
            if (list.any { num -> num.length >= 3 && cleanText.contains(num.lowercase()) }) {
                return bankName
            }
        }
        return "بانک"
    }

    /**
     * اجرای موتور امتیازدهی ۵ بخشی (Date, AccountNumber, Amount, Time, Type)
     */
    fun evaluateSms(smsText: String, senderAddress: String = ""): PatternCheckCandidate? {
        if (PasswordSmsFilter.isPasswordOrFeeSms(smsText)) {
            return null
        }
        val text = SmsNormalizer.fullNormalize(smsText)
        var score = 0
        val bankName = if (senderAddress.isNotBlank()) detectBankNameBySenderOrText(senderAddress, smsText) else BankSmsProcessor.parseSms(smsText).bankName ?: "بانک"

        // 1. Date Check
        val dateRegex = Regex("""(13\d{2}|14\d{2}|\d{2})[/\.-](\d{1,2})[/\.-](\d{1,2})""")
        val dateMatch = dateRegex.find(text)
        val dateStr = if (dateMatch != null) {
            score++
            val rawYear = dateMatch.groupValues[1]
            val m = dateMatch.groupValues[2].padStart(2, '0')
            val d = dateMatch.groupValues[3].padStart(2, '0')
            val y = if (rawYear.length == 2) {
                val yrInt = rawYear.toIntOrNull() ?: 4
                if (yrInt in 97..99) "13$rawYear" else "14$rawYear"
            } else rawYear
            "$y/$m/$d"
        } else {
            java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date())
        }

        // 2. Time Check
        val timeRegex = Regex("""\b(\d{1,2}):(\d{2})\b""")
        val timeMatch = timeRegex.find(text)
        val timeStr = if (timeMatch != null) {
            score++
            val h = timeMatch.groupValues[1].padStart(2, '0')
            val min = timeMatch.groupValues[2].padStart(2, '0')
            "$h:$min"
        } else {
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        }

        // 3. Amount Check
        val textNoCommas = text.replace(",", "").replace("،", "")

        // رفعِ باگِ «بلوبانک هیچی پیدا نمی‌کنه»: حداقل تعداد رقم قبلاً ۴ (و در fallback، ۵) بود،
        // در حالی که پیامک‌های بلوبانک می‌توانند مبالغ کوچک ریالی مثل «200 ریال» داشته باشند که
        // فقط ۳ رقم است. حداقل به ۳ رقم کاهش یافت تا این مبالغ هم شناسایی شوند؛ رقم بالای صفر
        // (rawAmount != null && rawAmount > 0 پایین‌تر) همچنان جلوی مقادیر بی‌معنی را می‌گیرد.
        val amountRegexList = listOf(
            Regex("""(?:مبلغ|واریز|برداشت|خرید|تراکنش|انتقال وجه|\+|\-)[:\s]*(\d{3,12})"""),
            Regex("""(\d{3,12})\s*(?:ریال|تومان|تومن)""")
        )
        
        var rawAmount: Long? = null
        for (reg in amountRegexList) {
            val match = reg.find(textNoCommas)
            if (match != null) {
                rawAmount = match.groupValues[1].toLongOrNull()
                break
            }
        }
        
        if (rawAmount == null) {
            val allNumbers = Regex("""\b(\d{3,12})\b""").findAll(textNoCommas)
            rawAmount = allNumbers.mapNotNull { it.groupValues[1].toLongOrNull() }.maxOrNull()
        }

        if (rawAmount != null && rawAmount > 0) score += 2 else return null
        
        val isToman = text.contains("تومان") || text.contains("تومن")
        val amountTomans = if (isToman) rawAmount else rawAmount / 10
        val amountRials = if (isToman) rawAmount * 10 else rawAmount

        // 4. Type Check
        val depositWords = listOf("واریز", "افزایش", "بستانکار", "پایا واریز", "ساتنا واریز", "+", "به حساب")
        val withdrawalWords = listOf("برداشت", "کاهش", "بدهکار", "خرید", "انتقال وجه", "-", "از حساب")
        
        var dScore = depositWords.count { text.contains(it) }
        var wScore = withdrawalWords.count { text.contains(it) }
        
        if (text.contains("واریز")) dScore += 2
        if (text.contains("برداشت") || text.contains("خرید")) wScore += 2
        
        val transactionType = if (dScore > wScore) "DEPOSIT" else "WITHDRAWAL"
        score++

        // 5. Account Number Check
        val accRegex = Regex("""(?:حساب|کارت|به|از)[\s\:]*([0-9\*\.\-\/]{4,20})""")
        val accMatch = accRegex.find(text)
        val rawAcc = accMatch?.groupValues?.get(1) ?: ""
        val cleanedAcc = SmsNormalizer.cleanAccountNumber(rawAcc)
        if (cleanedAcc.isNotBlank()) score++

        // رفعِ باگِ «هیچ اعلانی برای پیامک بانکی نمی‌آید»: آستانه‌ی قبلی (score >= 4) عملاً غیرقابل
        // دستیابی برای بسیاری از پیامک‌های واقعی بانکی بود. امتیاز تضمینی از مبلغ (+۲؛ اجباری،
        // در غیر این صورت پیش‌تر return null شده) و نوع تراکنش (+۱؛ بدون شرط) فقط برابر ۳ است؛
        // امتیاز تاریخ (+۱) و ساعت (+۱) فقط وقتی داده می‌شوند که خودِ متن پیامک شامل یک الگوی
        // تاریخ/ساعت صریح باشد — در حالی که اغلب پیامک‌های بانکی ایرانی («واریز ۵۰۰,۰۰۰ تومان به
        // حساب ...») هیچ تاریخ/ساعتی داخل متن ندارند (این اطلاعات از متادیتای خودِ پیامک گرفته
        // می‌شود، نه بدنه‌ی متن). نتیجه: چنین پیامک‌هایی حداکثر به امتیاز ۳ می‌رسیدند و با
        // آستانه‌ی قبلی (>=4) همیشه silently کنار گذاشته می‌شدند — یعنی evaluateSms برای یک
        // پیامک بانکی کاملاً معتبر null برمی‌گرداند، پس هیچ نوتیفیکیشنی هم ساخته نمی‌شد. آستانه
        // به ۳ کاهش یافت تا سیگنال‌های تضمینی (مبلغ + نوع) به‌تنهایی برای تشخیص کافی باشند؛
        // تاریخ/ساعت/شماره‌حساب همچنان امتیاز اضافه می‌دهند اما دیگر شرط لازم نیستند.
        if (score >= 3 && amountTomans > 0) {
            return PatternCheckCandidate(
                bankName = bankName,
                transactionType = transactionType,
                amountTomans = amountTomans,
                amountRials = amountRials,
                accountNumberRaw = cleanedAcc,
                dateStr = dateStr,
                timeStr = timeStr,
                score = score,
                rawSms = smsText
            )
        }
        return null
    }

    /**
     * 4. اسکن تاریخچه پیامک‌های قبلی گوشی (Historical SMS Backfill)
     * با قابلیت محدودسازی بازهٔ زمانی بین ۱ هفته تا ۳ ماه
     *
     * [typeFilter] فیلتر نوع تراکنش برای اسکن و لیست‌کردن نتایج:
     *   null => همهٔ تراکنش‌ها، "DEPOSIT" => فقط واریزی‌ها، "WITHDRAWAL" => فقط برداشتی‌ها
     */
    suspend fun scanHistoricalSmsBackfill(
        context: Context,
        fromDaysAgo: Int = 90,
        toDaysAgo: Int = 0,
        typeFilter: String? = null,
        onProgress: (scanned: Int, candidateCount: Int) -> Unit
    ): Pair<Int, List<PatternCheckCandidate>> = withContext(Dispatchers.IO) {
        val candidates = mutableListOf<PatternCheckCandidate>()
        var scannedCount = 0

        try {
            val now = System.currentTimeMillis()
            val fromMillis = now - fromDaysAgo.coerceIn(7, 90) * 24L * 60 * 60 * 1000
            val toMillis = now - toDaysAgo.coerceIn(0, 90) * 24L * 60 * 60 * 1000

            val contentResolver: ContentResolver = context.contentResolver
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val cursor = contentResolver.query(
                uri,
                projection,
                "date >= ? AND date <= ?",
                arrayOf(fromMillis.toString(), toMillis.toString()),
                "date DESC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")

                while (c.moveToNext()) {
                    scannedCount++
                    val address = if (addrIdx >= 0) c.getString(addrIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx) ?: "" else ""

                    // 1. Fast Filter by Bank Sender یا نام برند در متن پیامک (نگاه کنید به
                    // isBankSenderOrText — رفعِ باگِ «اسکن تاریخچه هیچی برای بلوبانک پیدا
                    // نمی‌کند»، چون فیلد sender این پیامک‌ها با هیچ مقدار ثبت‌شده مطابقت نداشت)
                    if (isBankSenderOrText(address, body)) {
                        val result = evaluateSms(body, address)
                        if (result != null && (typeFilter == null || result.transactionType == typeFilter)) {
                            candidates.add(result)
                        }
                    }

                    if (scannedCount % 50 == 0) {
                        onProgress(scannedCount, candidates.size)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BankSmsEngine", "Historical SMS backfill error: ${e.message}", e)
        }

        Pair(scannedCount, candidates)
    }
}