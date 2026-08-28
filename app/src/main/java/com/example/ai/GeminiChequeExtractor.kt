package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import com.example.util.PersianNumberFormatter

data class ExtractedChequeData(
    val amount: Long?,
    val amountText: String?,
    val dueDateDigits: String?,
    val dueDateDisplay: String?,
    val bankName: String?,
    val branchName: String?,
    val branchCode: String?,
    val drawerName: String?,
    val drawerNationalId: String?,
    val chequeNumber: String?,
    val sayadiId: String?,
    val confidence: String?,
    val error: String? = null
)

object GeminiChequeExtractor {
    private val prompt = """
        تصویر یک چک بانکی ایرانی (چک صیادی) است. این اطلاعات را دقیقاً از روی تصویربخوان و فقط در قالب JSON زیر برگردان — هیچ متن اضافه‌ای قبل یا بعد از JSON ننویس:
        
        {
          "amount": <مبلغ به عدد، فقط رقم، بدون کاما و بدون واحد؛ اگر مبلغ به رقم روی چک نوشته نشده یا ناخوانا بود، null>,
          "dueDateDigits": <تاریخ سررسید به شمسی، رقمی فقط، دقیقاً به فرمت YYYY-MM-DD؛ اگر ناخوانا بود، null>,
          "bankName": <نام بانک صادرکننده‌ی دسته‌چک؛ اگر ناخوانا بود، null>,
          "branchName": <نام شعبه صادرکننده در صورت درج شدن روی چک؛ اگر نبود null>,
          "branchCode": <کد شعبه در صورت درج شدن روی چک؛ اگر نبود null>,
          "drawerName": <نام کامل صاحب حساب/امضاکننده‌ی چک؛ اگر ناخوانا بود، null>,
          "drawerNationalId": <شناسه ملی/کد ملی صاحب حساب در صورت درج شدن؛ اگر نبود null>,
          "chequeNumber": <شماره سریال چک (معمولا عددی چند رقمی)؛ اگر ناخوانا بود، null>,
          "sayadiId": <شناسه‌ی ۱۶ رقمی صیادی، معمولاً کنار یا زیر بارکد QR چاپ شده؛ اگر ناخوانا بود، null>,
          "confidence": <"high" اگر همه‌ی فیلدهای مهم واضح خوانده شدند، "medium" اگر بعضی مبهم بودند، "low" اگر تصویر کلاً نامناسب/تار بود>
        }

        اگر تصویر اصلاً یک چک نیست، همه‌ی فیلدها را null بگذار و confidence را "low" کن.
    """.trimIndent()

    suspend fun extract(context: Context, bitmap: Bitmap): ExtractedChequeData = withContext(Dispatchers.IO) {
        if (!AiProviderSettings.isReady(context)) {
            return@withContext emptyResult(
                error = "کلید API تنظیم نشده — از منوی مخفی، «تنظیمات API های هوش مصنوعی» را باز کن و یک کلید فعال کن"
            )
        }

        val scaledBitmap = try {
            val maxDim = 1024
            if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true
                )
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }

        val imageBase64 = try {
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            return@withContext emptyResult(error = "خطا در آماده‌سازی تصویر")
        }

        val provider = AiProviderSettings.getActiveProvider(context)
        val model = when (provider) {
            AiProviderSettings.Provider.GEMINI -> AiProviderSettings.getGeminiModel(context)
            AiProviderSettings.Provider.GROQ -> AiProviderSettings.GROQ_MODEL
        }
        val providerString = if (provider == AiProviderSettings.Provider.GROQ) "groq" else "gemini"

        val rawJsonResult = try {
            // کلید API دیگر از اپ ارسال نمی‌شود — کلید روی سرور (Edge Function Secret)
            // نگهداری می‌شود و فراخوانی Gemini/Groq با IP سرور انجام می‌گیرد (دور زدن تحریم).
            val payload = JSONObject().apply {
                put("imageBase64", imageBase64)
                put("prompt", prompt)
                put("provider", providerString)
                put("model", model)
            }

            // Edge Function سوپابیس — اجرای API روی IP سرور
            try {
                val fnBody = com.google.gson.JsonParser.parseString(payload.toString()).asJsonObject
                val responseText = com.example.util.Supabase.invokeEdgeFunction("extract-cheque-data", fnBody)
                Result.success(responseText)
            } catch (fnError: Exception) {
                Result.failure(Exception(fnError.message ?: "خطای سرور ابری"))
            }
        } catch (e: Exception) {
            return@withContext emptyResult(error = "خطا در ارتباط با سرور: ${e.message}")
        }

        if (rawJsonResult.isFailure) {
            return@withContext emptyResult(error = rawJsonResult.exceptionOrNull()?.message ?: "خطای نامشخص در سرور")
        }

        val text = rawJsonResult.getOrNull()
        if (text.isNullOrBlank()) {
            return@withContext emptyResult(error = "پاسخی از سرور دریافت نشد")
        }

        try {
            val parsed = JSONObject(text)
            
            if (parsed.has("error")) {
                return@withContext emptyResult(error = parsed.optString("error"))
            }

            val amount = if (parsed.isNull("amount")) null else parsed.optLong("amount").takeIf { it > 0 }
            val dueDateDigits = parsed.optStringOrNull("dueDateDigits") ?: parsed.optStringOrNull("dueDate")

            val amountText = amount?.let { PersianNumberFormatter.numberToPersianWords(it) }
            val dueDateDisplay = dueDateDigits?.let { PersianNumberFormatter.jalaliDigitsToFullDate(it) }?.takeIf { it.isNotBlank() }

            ExtractedChequeData(
                amount = amount,
                amountText = amountText,
                dueDateDigits = dueDateDigits,
                dueDateDisplay = dueDateDisplay,
                bankName = parsed.optStringOrNull("bankName"),
                branchName = parsed.optStringOrNull("branchName"),
                branchCode = parsed.optStringOrNull("branchCode"),
                drawerName = parsed.optStringOrNull("drawerName"),
                drawerNationalId = parsed.optStringOrNull("drawerNationalId"),
                chequeNumber = parsed.optStringOrNull("chequeNumber"),
                sayadiId = parsed.optStringOrNull("sayadiId"),
                confidence = parsed.optStringOrNull("confidence")
            )
        } catch (e: Exception) {
            emptyResult(error = "پاسخ سرور قابل‌پردازش نبود")
        }
    }

    private fun emptyResult(error: String) = ExtractedChequeData(
        amount = null, amountText = null, dueDateDigits = null, dueDateDisplay = null,
        bankName = null, branchName = null, branchCode = null, drawerName = null,
        drawerNationalId = null, chequeNumber = null, sayadiId = null, confidence = null,
        error = error
    )
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
