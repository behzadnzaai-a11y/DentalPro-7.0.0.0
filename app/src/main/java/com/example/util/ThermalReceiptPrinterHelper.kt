package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * ماژول فرمت‌دهی و پشتیبانی از فیش‌پرینترهای حرارتی ۸۰ میلی‌متری (Thermal Printers)
 * جهت چاپ سریع رسید پرداختی‌ها، کارت نوبت بیمار و فاکتورهای کوچک
 */
object ThermalReceiptPrinterHelper {

    /**
     * فرمت‌دهی متنی استاندارد رسید حرارتی ۸۰ میلی‌متری (۴۲ کاراکتر در هر سطر)
     */
    fun formatPaymentReceipt(
        clinicName: String,
        doctorName: String,
        patientName: String,
        dateStr: String,
        receiptNo: String,
        paymentType: String,
        amountText: String,
        remainingDebtText: String = "۰"
    ): String {
        val divider = "=========================================="
        val dotDivider = "------------------------------------------"
        val sb = StringBuilder()

        sb.appendLine("            $clinicName            ")
        if (doctorName.isNotBlank()) {
            sb.appendLine("         دکتر $doctorName         ")
        }
        sb.appendLine(divider)
        sb.appendLine("نوع سند: رسید دریافت وجه مالی")
        sb.appendLine("شماره رسید: $receiptNo")
        sb.appendLine("تاریخ: $dateStr")
        sb.appendLine(dotDivider)
        sb.appendLine("نام مراجع: $patientName")
        sb.appendLine("روش پرداخت: $paymentType")
        sb.appendLine(dotDivider)
        sb.appendLine("مبلغ دریافتی: $amountText تومان")
        if (remainingDebtText != "۰" && remainingDebtText.isNotBlank()) {
            sb.appendLine("مانده بدهی: $remainingDebtText تومان")
        }
        sb.appendLine(divider)
        sb.appendLine("  با تشکر از مراجعه شما - دنتال‌پرو  ")
        sb.appendLine("      سیستم مدیریت کلینیک دندان‌پزشکی      ")

        return sb.toString()
    }

    /**
     * ذخیره‌سازی فایل متن رسید و اشتراک‌گذاری جهت ارسال به برنامه‌های فیش‌پرینتر حرارتی بلوتوثی
     */
    fun shareThermalReceiptText(context: Context, receiptText: String): File {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, "thermal_receipt_${System.currentTimeMillis()}.txt")
        FileOutputStream(file).use { out ->
            out.write(receiptText.toByteArray(Charsets.UTF_8))
        }
        return file
    }
}
