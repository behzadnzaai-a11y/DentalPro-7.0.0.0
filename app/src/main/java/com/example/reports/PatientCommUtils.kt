package com.example.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.example.ui.screens.honorificPatientName
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** One line item shown on a statement or treatment-plan card. */
data class SummaryLineItem(val title: String, val subtitle: String, val amountText: String)

/**
 * Renders a branded, shareable PNG card — used when sending a patient
 * statement or treatment plan over WhatsApp (image beats a wall of text
 * inside a chat). Colors come from the currently active neon theme so the
 * image matches the app's look.
 */
fun buildSummaryImage(
    context: Context,
    cardTitle: String,
    clinicName: String,
    doctorName: String,
    patientName: String,
    items: List<SummaryLineItem>,
    footerLines: List<Pair<String, String>>, // label to value, last one is emphasized total
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default,
    fileNamePrefix: String,
    gender: String = "مرد"
): File {
    val fontScale = themeSpec.fontScaleMultiplier
    val width = 1000
    val margin = 56
    val rowHeight = (74 * fontScale).toInt()
    val headerHeight = 260
    val footerHeight = 60 + (footerLines.size * 54 * fontScale).toInt()
    val height = headerHeight + items.size * rowHeight + footerHeight + 80

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(themeSpec.backgroundArgb)

    // Top accent gradient band
    val bandPaint = Paint().apply { color = themeSpec.primaryArgb; alpha = 235 }
    canvas.drawRect(0f, 0f, width.toFloat(), 14f, bandPaint)

    var y = 70f
    val clinicPaint = TextPaint().apply {
        isAntiAlias = true; color = themeSpec.primaryArgb; textSize = 34f * fontScale; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(clinicName, width - margin.toFloat(), y, clinicPaint)
    y += 40f
    val docPaint = TextPaint().apply { isAntiAlias = true; color = Color.LTGRAY; textSize = 20f * fontScale; textAlign = Paint.Align.RIGHT }
    canvas.drawText(doctorName, width - margin.toFloat(), y, docPaint)
    y += 52f

    val titlePaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 30f * fontScale; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    canvas.drawText(cardTitle, width - margin.toFloat(), y, titlePaint)
    y += 40f
    val patientPaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 24f * fontScale; textAlign = Paint.Align.RIGHT }
    val formattedPatient = honorificPatientName(patientName, gender)
    canvas.drawText("مراجع عزیز: $formattedPatient", width - margin.toFloat(), y, patientPaint)
    y += 30f

    val dividerPaint = Paint().apply { color = themeSpec.primaryArgb; alpha = 150; strokeWidth = 2f }
    canvas.drawLine(margin.toFloat(), y, width - margin.toFloat(), y, dividerPaint)
    y += 46f

    val itemTitlePaint = TextPaint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 24f * fontScale; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
    val itemSubPaint = TextPaint().apply { isAntiAlias = true; color = Color.LTGRAY; textSize = 18f * fontScale; textAlign = Paint.Align.RIGHT }
    val itemAmountPaint = TextPaint().apply { isAntiAlias = true; color = themeSpec.primaryArgb; textSize = 24f * fontScale; isFakeBoldText = true; textAlign = Paint.Align.LEFT }
    val rowBgAlt = Paint().apply { color = Color.WHITE; alpha = 12 }

    items.forEachIndexed { index, item ->
        if (index % 2 == 1) {
            canvas.drawRect(margin.toFloat(), y - 44f, width - margin.toFloat(), y + 22f, rowBgAlt)
        }
        canvas.drawText(item.title, width - margin.toFloat(), y, itemTitlePaint)
        canvas.drawText(item.amountText, margin.toFloat(), y, itemAmountPaint)
        if (item.subtitle.isNotBlank()) {
            canvas.drawText(item.subtitle, width - margin.toFloat(), y + 26f, itemSubPaint)
        }
        y += rowHeight
    }

    y += 16f
    canvas.drawLine(margin.toFloat(), y, width - margin.toFloat(), y, dividerPaint)
    y += 46f

    footerLines.forEachIndexed { index, (label, value) ->
        val isLast = index == footerLines.lastIndex
        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            color = if (isLast) themeSpec.primaryArgb else Color.LTGRAY
            textSize = (if (isLast) 30f else 22f) * fontScale
            isFakeBoldText = isLast
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(labelPaint).apply { textAlign = Paint.Align.LEFT; color = if (isLast) themeSpec.primaryArgb else Color.WHITE }
        canvas.drawText(label, width - margin.toFloat(), y, labelPaint)
        canvas.drawText(value, margin.toFloat(), y, valuePaint)
        y += if (isLast) 54f else 44f
    }

    val brandPaint = TextPaint().apply { isAntiAlias = true; color = Color.GRAY; textSize = 16f; textAlign = Paint.Align.CENTER }
    canvas.drawText("تولید شده توسط دنتال‌پرو", width / 2f, height - 24f, brandPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "${fileNamePrefix}_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

/** Short SMS-safe Persian summary of a patient's account balance. */
fun buildStatementSmsText(
    clinicName: String,
    patientName: String,
    currency: String,
    totalCostText: String,
    totalPaidText: String,
    balanceText: String,
    isDebtor: Boolean,
    gender: String = "مرد"
): String {
    val formattedPatient = honorificPatientName(patientName, gender)
    val balanceLine = if (isDebtor) "مانده بدهی: $balanceText $currency" else "مانده بستانکاری: $balanceText $currency"
    return "$clinicName\nصورت‌حساب مراجع عزیز: $formattedPatient\nجمع درمان: $totalCostText $currency\nجمع پرداختی: $totalPaidText $currency\n$balanceLine"
}

/** Short SMS-safe Persian summary of a proposed treatment plan and price. */
fun buildTreatmentPlanSmsText(
    clinicName: String,
    patientName: String,
    currency: String,
    treatmentTitles: List<String>,
    totalPriceText: String,
    gender: String = "مرد"
): String {
    val formattedPatient = honorificPatientName(patientName, gender)
    val itemsText = if (treatmentTitles.size <= 4) {
        treatmentTitles.joinToString("، ")
    } else {
        treatmentTitles.take(4).joinToString("، ") + " و ${treatmentTitles.size - 4} مورد دیگر"
    }
    return "$clinicName\nطرح درمان پیشنهادی برای مراجع عزیز: $formattedPatient\n$itemsText\nهزینه کل تخمینی: $totalPriceText $currency"
}

/**
 * Renders an exact, high-quality PNG image card of the official lab invoice view
 * for direct sharing to WhatsApp.
 */
fun buildOfficialLabInvoiceImage(
    context: Context,
    clinicName: String,
    doctorName: String,
    labName: String,
    patientName: String,
    invoiceNo: String,
    prosthesisType: String,
    toothNumber: String,
    teethCount: Int,
    unitPriceText: String,
    totalCostText: String,
    orderDateText: String,
    frameDateText: String,
    finalDateText: String,
    paymentMethodText: String,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default,
    gender: String = "مرد"
): File {
    val formattedPatient = honorificPatientName(patientName, gender)
    val accentColor = themeSpec.primaryArgb
    val backgroundColor = themeSpec.backgroundArgb
    val width = 1200
    val margin = 50f
    val height = 1350

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    // Top accent band
    val bandPaint = Paint().apply { color = accentColor; isAntiAlias = true }
    canvas.drawRect(0f, 0f, width.toFloat(), 18f, bandPaint)

    var y = 80f
    val headerTitlePaint = TextPaint().apply {
        isAntiAlias = true; color = accentColor; textSize = 38f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
    }
    canvas.drawText(clinicName, width - margin, y, headerTitlePaint)

    val docPaint = TextPaint().apply {
        isAntiAlias = true; color = Color.LTGRAY; textSize = 26f; textAlign = Paint.Align.RIGHT
    }
    y += 42f
    canvas.drawText(doctorName, width - margin, y, docPaint)

    val invNoPaint = TextPaint().apply {
        isAntiAlias = true; color = Color.WHITE; textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.LEFT
    }
    canvas.drawText("شماره فاکتور: $invoiceNo", margin, y - 10f, invNoPaint)

    val badgePaint = Paint().apply { color = accentColor; alpha = 60; isAntiAlias = true }
    val badgeRect = RectF(margin, y + 10f, margin + 240f, y + 55f)
    canvas.drawRoundRect(badgeRect, 12f, 12f, badgePaint)
    val badgeTextPaint = TextPaint().apply {
        isAntiAlias = true; color = accentColor; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
    }
    canvas.drawText("فاکتور سفارش لابراتوار", margin + 120f, y + 42f, badgeTextPaint)

    y += 80f
    val dividerPaint = Paint().apply { color = Color.GRAY; alpha = 120; strokeWidth = 2.5f }
    canvas.drawLine(margin, y, width - margin, y, dividerPaint)

    // Main Card Box
    y += 40f
    var cy = y + 40f

    val labelPaint = TextPaint().apply {
        isAntiAlias = true; color = Color.LTGRAY; textSize = 26f; textAlign = Paint.Align.RIGHT
    }
    val valuePaint = TextPaint().apply {
        isAntiAlias = true; color = Color.WHITE; textSize = 28f; isFakeBoldText = true; textAlign = Paint.Align.LEFT
    }

    val innerMargin = margin + 30f
    val innerWidth = width - (innerMargin * 2)
    val labelWidth = 400f

    fun drawRow(label: String, value: String, isPrimary: Boolean = false) {
        val vPaint = if (isPrimary) TextPaint(valuePaint).apply { color = accentColor; textSize = 30f } else valuePaint

        val labelLayout = StaticLayout.Builder.obtain(label, 0, label.length, labelPaint, labelWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()

        canvas.save()
        canvas.translate(width - innerMargin - labelWidth, cy)
        labelLayout.draw(canvas)
        canvas.restore()

        val valueLayout = StaticLayout.Builder.obtain(value, 0, value.length, vPaint, (innerWidth - labelWidth - 20f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()

        canvas.save()
        canvas.translate(innerMargin, cy)
        valueLayout.draw(canvas)
        canvas.restore()

        cy += maxOf(labelLayout.height, valueLayout.height) + 26f
    }

    drawRow("لابراتوار سازنده:", labName, isPrimary = true)
    drawRow("مراجع / بیمار:", formattedPatient)
    drawRow("نوع پروتز / سفارش:", prosthesisType, isPrimary = true)
    drawRow("شماره دندان(ها):", toothNumber)
    drawRow("تعداد واحدهای ساخت:", "$teethCount واحد")
    drawRow("مبلغ هر واحد:", unitPriceText)
    drawRow("مبلغ کل سفارش:", totalCostText, isPrimary = true)
    drawRow("تاریخ ثبت سفارش:", orderDateText)
    if (frameDateText.isNotBlank()) drawRow("تاریخ امتحان فریم:", frameDateText)
    drawRow("تاریخ تحویل نهایی:", finalDateText)
    drawRow("تسویه مالی:", paymentMethodText)

    val cardHeight = cy - y + 20f
    val cardPaint = Paint().apply { color = Color.WHITE; alpha = 12; isAntiAlias = true }
    val cardRect = RectF(margin, y, width - margin, y + cardHeight)
    canvas.drawRoundRect(cardRect, 16f, 16f, cardPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "lab_invoice_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

fun generateInstallmentDueReminderCardImage(
    context: Context,
    clinicName: String,
    patientName: String,
    patientFatherName: String = "",
    patientNationalId: String = "",
    installmentTitle: String,
    dueDateStr: String,
    amountStr: String,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val accentColor = themeSpec.primaryArgb
    val backgroundColor = themeSpec.backgroundArgb
    val onSurface = android.graphics.Color.WHITE
    val width = 1000
    val height = 750
    val margin = 50f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    val bandPaint = Paint().apply { color = accentColor; isAntiAlias = true }
    canvas.drawRect(0f, 0f, width.toFloat(), 16f, bandPaint)

    var y = 75f
    val titlePaint = TextPaint().apply { color = accentColor; textSize = 36f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    canvas.drawText("یادآوری سررسید قسط — $clinicName", width / 2f, y, titlePaint)

    y += 55f
    val subPaint = TextPaint().apply { color = onSurface; textSize = 28f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    val detailsExtra = listOfNotNull(
        patientFatherName.takeIf { it.isNotBlank() }?.let { "فرزند: $it" },
        patientNationalId.takeIf { it.isNotBlank() }?.let { "کدملی: $it" }
    ).joinToString(" | ")
    val patDisplay = if (detailsExtra.isNotBlank()) "مراجع محترم: $patientName ($detailsExtra)" else "مراجع محترم: $patientName"
    canvas.drawText(patDisplay, width / 2f, y, subPaint)

    y += 60f
    val boxPaint = Paint().apply { color = accentColor; alpha = 35; isAntiAlias = true }
    canvas.drawRoundRect(RectF(margin, y, width - margin, y + 360f), 16f, 16f, boxPaint)

    var rowY = y + 70f
    val rowLabelPaint = TextPaint().apply { color = Color.LTGRAY; textSize = 26f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    val rowValPaint = TextPaint().apply { color = onSurface; textSize = 28f; isFakeBoldText = true; textAlign = Paint.Align.LEFT; isAntiAlias = true }
    val rowValAccent = TextPaint(rowValPaint).apply { color = accentColor }

    fun drawInfoRow(label: String, value: String, accent: Boolean = false) {
        canvas.drawText(label, width - margin - 30f, rowY, rowLabelPaint)
        canvas.drawText(value, margin + 30f, rowY, if (accent) rowValAccent else rowValPaint)
        rowY += 65f
    }

    drawInfoRow("عنوان قسط / درمان:", installmentTitle)
    drawInfoRow("تاریخ سررسید:", dueDateStr)
    drawInfoRow("مبلغ قابل پرداخت:", amountStr, accent = true)
    drawInfoRow("وضعیت:", "در انتظار پرداخت")

    y += 400f
    val footerPaint = TextPaint().apply { color = Color.GRAY; textSize = 22f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    canvas.drawText("با تشکر از همکاری و حسن توجه شما", width / 2f, y, footerPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "installment_reminder_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

fun generateOverdueInstallmentNoticeCardImage(
    context: Context,
    clinicName: String,
    patientName: String,
    patientFatherName: String = "",
    patientNationalId: String = "",
    installmentTitle: String,
    dueDateStr: String,
    delayDays: Long,
    amountStr: String,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val errorColor = android.graphics.Color.parseColor("#EF4444")
    val backgroundColor = themeSpec.backgroundArgb
    val onSurface = android.graphics.Color.WHITE
    val width = 1000
    val height = 780
    val margin = 50f

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    val bandPaint = Paint().apply { color = errorColor; isAntiAlias = true }
    canvas.drawRect(0f, 0f, width.toFloat(), 16f, bandPaint)

    var y = 75f
    val titlePaint = TextPaint().apply { color = errorColor; textSize = 36f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    canvas.drawText("اخطار تاخیر در پرداخت قسط — $clinicName", width / 2f, y, titlePaint)

    y += 55f
    val subPaint = TextPaint().apply { color = onSurface; textSize = 28f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    val detailsExtra = listOfNotNull(
        patientFatherName.takeIf { it.isNotBlank() }?.let { "فرزند: $it" },
        patientNationalId.takeIf { it.isNotBlank() }?.let { "کدملی: $it" }
    ).joinToString(" | ")
    val patDisplay = if (detailsExtra.isNotBlank()) "مراجع محترم: $patientName ($detailsExtra)" else "مراجع محترم: $patientName"
    canvas.drawText(patDisplay, width / 2f, y, subPaint)

    y += 60f
    val boxPaint = Paint().apply { color = errorColor; alpha = 30; isAntiAlias = true }
    canvas.drawRoundRect(RectF(margin, y, width - margin, y + 420f), 16f, 16f, boxPaint)

    var rowY = y + 70f
    val rowLabelPaint = TextPaint().apply { color = Color.LTGRAY; textSize = 26f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    val rowValPaint = TextPaint().apply { color = onSurface; textSize = 28f; isFakeBoldText = true; textAlign = Paint.Align.LEFT; isAntiAlias = true }
    val rowValError = TextPaint(rowValPaint).apply { color = errorColor }

    fun drawInfoRow(label: String, value: String, isError: Boolean = false) {
        canvas.drawText(label, width - margin - 30f, rowY, rowLabelPaint)
        canvas.drawText(value, margin + 30f, rowY, if (isError) rowValError else rowValPaint)
        rowY += 65f
    }

    drawInfoRow("عنوان قسط / درمان:", installmentTitle)
    drawInfoRow("تاریخ سررسید:", dueDateStr)
    drawInfoRow("میزان تاخیر:", "$delayDays روز تاخیر", isError = true)
    drawInfoRow("مبلغ معوقه:", amountStr, isError = true)
    drawInfoRow("وضعیت:", "معوقه و نیازمند تسویه فوری")

    y += 460f
    val footerPaint = TextPaint().apply { color = Color.GRAY; textSize = 22f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    canvas.drawText("خواهشمند است جهت تسویه حساب با پذیرش مطب تماس حاصل فرمایید", width / 2f, y, footerPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "overdue_notice_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

fun buildVendorStatementImage(
    context: Context,
    clinicName: String,
    partyName: String,
    expenses: List<com.example.data.ClinicExpense>,
    payments: List<com.example.data.ExpensePayment>,
    totalDebt: Long,
    totalPaid: Long,
    remaining: Long,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val accentColor = themeSpec.primaryArgb
    val backgroundColor = themeSpec.backgroundArgb
    val onSurface = android.graphics.Color.WHITE
    val width = 1200
    val margin = 50f

    val allItems = (expenses.map { Pair(it.date, it) } + payments.map { Pair(it.date, it) }).sortedBy { it.first }
    val baseRowHeight = 65f
    val itemsHeight = allItems.size * baseRowHeight + 140f
    val height = (580 + itemsHeight).toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(backgroundColor)

    val bandPaint = Paint().apply { color = accentColor; isAntiAlias = true }
    canvas.drawRect(0f, 0f, width.toFloat(), 18f, bandPaint)

    var y = 90f
    val titlePaint = TextPaint().apply { color = accentColor; textSize = 40f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    // عنوان استاندارد درخواستی: «صورتحساب (نام طرف حساب)»
    canvas.drawText("صورتحساب ($partyName)", width / 2f, y, titlePaint)

    y += 55f
    val subtitlePaint = TextPaint().apply { color = onSurface; textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.CENTER; isAntiAlias = true }
    canvas.drawText("$clinicName — جمع فاکتورها: ${com.example.ui.screens.formatAmount(totalDebt)} | پرداختی: ${com.example.ui.screens.formatAmount(totalPaid)}", width / 2f, y, subtitlePaint)

    y += 70f
    val headerBgPaint = Paint().apply { color = accentColor; alpha = 50; isAntiAlias = true }
    canvas.drawRoundRect(RectF(margin, y, width - margin, y + 65f), 14f, 14f, headerBgPaint)

    val headerTextPaint = TextPaint().apply { color = onSurface; textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    canvas.drawText("تاریخ", width - margin - 30f, y + 44f, headerTextPaint)
    canvas.drawText("شرح سفارش / پرداخت", width - margin - 240f, y + 44f, headerTextPaint)
    val headerLeftPaint = TextPaint(headerTextPaint).apply { textAlign = Paint.Align.LEFT }
    canvas.drawText("مبلغ (تومان)", margin + 30f, y + 44f, headerLeftPaint)

    y += 90f
    val itemPaint = TextPaint().apply { color = onSurface; textSize = 24f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    val itemLeftPaint = TextPaint(itemPaint).apply { textAlign = Paint.Align.LEFT; isFakeBoldText = true; textSize = 26f }

    allItems.forEach { (date, item) ->
        val jDate = com.example.util.PersianCalendarHelper.timestampToJalali(date)
        val dateText = String.format("%04d/%02d/%02d", jDate.year, jDate.month, jDate.day)
        canvas.drawText(dateText, width - margin - 30f, y + 10f, itemPaint)

        val desc = if (item is com.example.data.ClinicExpense) {
            item.description.ifBlank { "هزینه / خرید خریدار" }.trim()
        } else if (item is com.example.data.ExpensePayment) {
            if (item.note.isNotBlank()) "پرداختی: ${item.note.trim()}" else "پرداختی (${item.method})"
        } else ""

        val descWidth = (width - margin - 240f - (margin + 240f)).toInt()
        val descLayout = StaticLayout.Builder.obtain(desc, 0, desc.length, itemPaint, descWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()

        canvas.save()
        canvas.translate(margin + 240f, y - 16f)
        descLayout.draw(canvas)
        canvas.restore()

        if (item is com.example.data.ClinicExpense) {
            itemLeftPaint.color = Color.parseColor("#EF4444")
            canvas.drawText(com.example.ui.screens.formatAmount(item.amount), margin + 30f, y + 10f, itemLeftPaint)
        } else if (item is com.example.data.ExpensePayment) {
            itemLeftPaint.color = Color.parseColor("#10B981")
            canvas.drawText(com.example.ui.screens.formatAmount(item.amount), margin + 30f, y + 10f, itemLeftPaint)
        }
        y += maxOf(descLayout.height.toFloat(), 45f) + 20f
    }

    y += 30f
    val dividerPaint = Paint().apply { color = Color.GRAY; strokeWidth = 2.5f }
    canvas.drawLine(margin, y, width - margin, y, dividerPaint)

    y += 50f
    val summaryLabelPaint = TextPaint().apply { color = onSurface; textSize = 26f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
    canvas.drawText("جمع فاکتورها / هزینه‌ها: ${com.example.ui.screens.formatAmount(totalDebt)} تومان", width - margin - 20f, y, summaryLabelPaint)
    canvas.drawText("جمع کل پرداختی‌ها: ${com.example.ui.screens.formatAmount(totalPaid)} تومان", width - margin - 20f, y + 44f, summaryLabelPaint)

    val remainingColor = if (remaining > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
    val summaryLeftPaint = TextPaint(summaryLabelPaint).apply { textAlign = Paint.Align.LEFT; color = remainingColor; textSize = 30f }
    canvas.drawText("مانده بدهی نهایی: ${com.example.ui.screens.formatAmount(kotlin.math.abs(remaining))} تومان", margin + 20f, y + 44f, summaryLeftPaint)

    val dir = File(context.cacheDir, "reports")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "vendor_statement_${UUID.randomUUID().toString().take(8)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}

