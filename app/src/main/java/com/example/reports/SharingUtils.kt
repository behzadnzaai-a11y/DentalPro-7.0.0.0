package com.example.reports

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

private const val MAX_SHARE_FILE_BYTES = 100L * 1024L * 1024L

/**
 * Stages only the requested file in the narrow FileProvider share directory. This avoids
 * exposing the whole cache directory while retaining short-lived URI grants for recipients.
 */
fun fileProviderUri(context: Context, file: File): Uri {
    require(file.isFile && file.canRead()) { "Share source does not exist or is unreadable" }
    require(file.length() in 1..MAX_SHARE_FILE_BYTES) { "Share source has an invalid size" }

    val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val shareRoot = shareDir.canonicalFile
    val source = file.canonicalFile
    val staged = if (source.parentFile == shareRoot) {
        source
    } else {
        shareDir.listFiles()
            ?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L }
            ?.forEach(File::delete)
        val safeExtension = source.extension
            .lowercase()
            .filter(Char::isLetterOrDigit)
            .take(10)
        val targetName = buildString {
            append("share-")
            append(UUID.randomUUID())
            if (safeExtension.isNotEmpty()) append('.').append(safeExtension)
        }
        File(shareRoot, targetName).also { target ->
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    check(staged.canonicalPath.startsWith("${shareRoot.path}${File.separator}")) {
        "Refusing to share a file outside the staging directory"
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", staged)
}

private fun buildRtlLayout(text: String, paint: android.text.TextPaint, width: Int, alignment: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_NORMAL): android.text.StaticLayout =
    android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
        .build()

/** Opens the system print dialog for an already-generated PDF file. */
fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        Toast.makeText(context, "سرویس چاپ در این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
        return
    }
    printManager.print(jobName, PdfPrintDocumentAdapter(file), PrintAttributes.Builder().build())
}

private class PdfPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

/** Generic share sheet for a PDF file (any app that accepts documents). */
fun sharePdf(context: Context, file: File, chooserTitle: String = "اشتراک‌گذاری سند") {
    val uri = fileProviderUri(context, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

fun normalizePhoneForJid(rawPhone: String): String {
    val digits = rawPhone.filter { it.isDigit() }
    return when {
        digits.startsWith("0098") -> digits.removePrefix("00")
        digits.startsWith("98") -> digits
        digits.startsWith("0") -> "98${digits.removePrefix("0")}"
        else -> digits
    }
}

/** Shares a PDF via Android native system chooser (universal sharing to any app). */
fun sharePdfToWhatsApp(context: Context, file: File, phone: String? = null) {
    sharePdf(context, file, "اشتراک‌گذاری گزارش PDF")
}

/** Shares a rendered summary image (PNG) via Android native system chooser. */
fun shareImageToWhatsApp(context: Context, imageFile: File, phone: String? = null) {
    shareImageNativeChooser(context, imageFile, null, "اشتراک‌گذاری تصویر")
}

/** Shares all rendered PDF pages. URI grants are limited to explicitly staged files. */
fun shareImagesNativeChooser(
    context: Context,
    imageFiles: List<File>,
    chooserTitle: String = "اشتراک‌گذاری تصاویر"
) {
    require(imageFiles.isNotEmpty()) { "No images were generated" }
    val uris = ArrayList(imageFiles.map { fileProviderUri(context, it) })
    val intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/png"
        if (uris.size == 1) {
            putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        clipData = ClipData.newUri(context.contentResolver, "report-page-1", uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری یافت نشد", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens standard sharing or dialer for the given phone number using Android native intents.
 */
fun openWhatsAppChat(context: Context, rawPhone: String) {
    val digits = rawPhone.filter { it.isDigit() }
    val normalized = when {
        digits.startsWith("0098") -> "+98${digits.removePrefix("0098")}"
        digits.startsWith("98") -> "+98${digits.removePrefix("98")}"
        digits.startsWith("0") -> "+98${digits.removePrefix("0")}"
        digits.isNotEmpty() -> "+98$digits"
        else -> ""
    }
    if (normalized.isBlank()) {
        Toast.makeText(context, "شماره تلفن معتبر نیست", Toast.LENGTH_SHORT).show()
        return
    }
    shareTextNativeChooser(context, normalized, "ارسال یا تماس با شماره")
}

/**
 * Generic "share as text" via the system chooser — lists every installed app
 * that accepts plain text (Telegram, Bale, Rubika, email, etc.) without
 * needing a dedicated integration per app.
 */
fun shareTextNativeChooser(context: Context, text: String, chooserTitle: String = "اشتراک‌گذاری") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری یافت نشد", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Generic "share as image" via the system chooser — lists every installed
 * app that accepts images (Telegram, Bale, Rubika, WhatsApp, etc.).
 */
fun shareImageNativeChooser(context: Context, imageFile: File, caption: String? = null, chooserTitle: String = "اشتراک‌گذاری تصویر") {
    val uri = fileProviderUri(context, imageFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        if (!caption.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه‌ای برای اشتراک‌گذاری یافت نشد", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Renders a simple white card image (title + a few text lines, RTL-aware)
 * to a cache file — used for the "share as image" option so the same
 * summary that would be sent as text can also be sent as a picture.
 */
fun generateSimpleInfoCardImage(
    context: Context,
    title: String,
    lines: List<String>,
    signatureB64: String? = null,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1080
    val sidePadding = 60
    val fontScale = themeSpec.fontScaleMultiplier
    
    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 56f * fontScale
        isFakeBoldText = true
    }
    val linePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.onPrimaryContainerArgb
        textSize = 42f * fontScale
    }
    
    val textWidth = width - (sidePadding * 2)
    val alignment = android.text.Layout.Alignment.ALIGN_NORMAL
    
    val titleLayout = android.text.StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
        .setAlignment(alignment)
        .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
        .build()
        
    val lineLayouts = lines.map {
        android.text.StaticLayout.Builder.obtain(it, 0, it.length, linePaint, textWidth)
            .setAlignment(alignment)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
    }
    
    val titleHeight = titleLayout.height
    val linesHeight = lineLayouts.sumOf { it.height + 20 }
    
    var sigBitmap: android.graphics.Bitmap? = null
    var sigHeight = 0
    if (signatureB64 != null && signatureB64.isNotEmpty()) {
        try {
            val decodedString = android.util.Base64.decode(signatureB64, android.util.Base64.DEFAULT)
            sigBitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
            sigHeight = (sigBitmap.height * (textWidth.toFloat() / sigBitmap.width)).toInt()
        } catch (e: Exception) {}
    }
    
    val height = 140 + titleHeight + 60 + linesHeight + 100 + (if (sigBitmap != null) sigHeight + 40 else 0)
    
    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    
    // Background gradient using active ExportThemeSpec
    val bgPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(),
            intArrayOf(themeSpec.backgroundArgb, themeSpec.surfaceArgb),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
    
    // Header background
    val headerPaint = android.graphics.Paint().apply {
        color = themeSpec.primaryContainerArgb
    }
    canvas.drawRect(0f, 0f, width.toFloat(), (140 + titleHeight).toFloat(), headerPaint)
    
    canvas.save()
    canvas.translate(sidePadding.toFloat(), 70f)
    titleLayout.draw(canvas)
    canvas.restore()
    
    canvas.drawLine(sidePadding.toFloat(), (140 + titleHeight).toFloat(), width - sidePadding.toFloat(), (140 + titleHeight).toFloat(), android.graphics.Paint().apply { color = themeSpec.glowArgb; strokeWidth = 4f })
    
    var currentY = 140f + titleHeight + 60f
    lineLayouts.forEach { layout ->
        canvas.save()
        canvas.translate(sidePadding.toFloat(), currentY)
        layout.draw(canvas)
        canvas.restore()
        currentY += layout.height + 20f
    }
    
    val file = File(context.cacheDir, "info_card_${System.currentTimeMillis()}.png")
    java.io.FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

data class RxColumnRowData(
    val title: String,
    val quantity: String,
    val dosage: String,
    val timing: String,
    val instructions: String
)

/**
 * Generates an Iranian Standard Medical Prescription Card PNG matching the table layout:
 * Header: Patient Name, National ID/Phone, Date, Clinic & Doctor
 * Table: | عنوان | تعداد | مقادیر مصرف | زمان مصرف | دستورات متفرقه |
 * Notes: Additional Instructions
 */
fun generateStandardPrescriptionCardImage(
    context: Context,
    patientName: String,
    patientNationalIdOrPhone: String,
    dateStr: String,
    rows: List<RxColumnRowData>,
    notes: String,
    clinicName: String,
    doctorName: String,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1200
    val sidePadding = 50
    val textWidth = width - (sidePadding * 2)
    val fontScale = themeSpec.fontScaleMultiplier

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 46f * fontScale
        isFakeBoldText = true
    }
    val headerPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.onPrimaryContainerArgb
        textSize = 32f * fontScale
    }
    val thPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 30f * fontScale
        isFakeBoldText = true
    }
    val tdPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.onPrimaryContainerArgb
        textSize = 28f * fontScale
    }
    val notesPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.onPrimaryContainerArgb
        textSize = 30f * fontScale
    }

    val gridPaint = android.graphics.Paint().apply {
        color = themeSpec.primaryArgb
        strokeWidth = 2.5f
        style = android.graphics.Paint.Style.STROKE
    }

    val colWidths = floatArrayOf(380f, 130f, 190f, 200f, 200f) // Sum = 1100

    var totalHeight = 280f

    val rowHeights = rows.map { r ->
        val tL = android.text.StaticLayout.Builder.obtain(r.title, 0, r.title.length, tdPaint, colWidths[0].toInt() - 16)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        val qL = android.text.StaticLayout.Builder.obtain(r.quantity, 0, r.quantity.length, tdPaint, colWidths[1].toInt() - 16).build()
        val dL = android.text.StaticLayout.Builder.obtain(r.dosage, 0, r.dosage.length, tdPaint, colWidths[2].toInt() - 16).build()
        val fL = android.text.StaticLayout.Builder.obtain(r.timing, 0, r.timing.length, tdPaint, colWidths[3].toInt() - 16).build()
        val nL = android.text.StaticLayout.Builder.obtain(r.instructions, 0, r.instructions.length, tdPaint, colWidths[4].toInt() - 16)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        
        val maxH = maxOf(tL.height, qL.height, dL.height, fL.height, nL.height) + 30f
        maxOf(maxH, 70f)
    }

    totalHeight += 70f
    totalHeight += rowHeights.sum()
    
    var notesLayout: android.text.StaticLayout? = null
    if (notes.isNotBlank()) {
        notesLayout = android.text.StaticLayout.Builder.obtain(notes, 0, notes.length, notesPaint, textWidth - 40)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        totalHeight += (notesLayout.height + 120f)
    } else {
        totalHeight += 60f
    }
    totalHeight += 120f

    val bmp = android.graphics.Bitmap.createBitmap(width, totalHeight.toInt(), android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    canvas.drawColor(themeSpec.backgroundArgb)

    val outerRect = android.graphics.RectF(20f, 20f, width - 20f, totalHeight - 20f)
    val borderPaint = android.graphics.Paint().apply {
        color = themeSpec.primaryArgb
        strokeWidth = 4f
        style = android.graphics.Paint.Style.STROKE
    }
    canvas.drawRect(outerRect, borderPaint)

    val headerTitle = "نسخه دارویی الکترونیک"
    val alignment = android.text.Layout.Alignment.ALIGN_CENTER
    val titleLayout = android.text.StaticLayout.Builder.obtain(headerTitle, 0, headerTitle.length, titlePaint, textWidth).setAlignment(alignment).build()
    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f)
    titleLayout.draw(canvas)
    canvas.restore()

    var currentY = 110f
    canvas.drawLine(sidePadding.toFloat(), currentY, width - sidePadding.toFloat(), currentY, gridPaint)
    currentY += 20f

    val halfW = (textWidth - 20) / 2
    val rightText = "نام و نام خانوادگی مراجع: $patientName\nمرکز: $clinicName${if(doctorName.isNotBlank()) " — دکتر $doctorName" else ""}"
    val leftText = "شماره پرونده: ${patientNationalIdOrPhone.ifBlank { "-" }}\nتاریخ نسخه: $dateStr"

    val rightLayout = android.text.StaticLayout.Builder.obtain(rightText, 0, rightText.length, headerPaint, halfW)
        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
        .build()
    val leftLayout = android.text.StaticLayout.Builder.obtain(leftText, 0, leftText.length, headerPaint, halfW)
        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
        .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
        .build()

    canvas.save()
    canvas.translate(sidePadding + halfW + 20f, currentY)
    rightLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), currentY)
    leftLayout.draw(canvas)
    canvas.restore()

    currentY += maxOf(rightLayout.height, leftLayout.height) + 20f

    canvas.drawLine(sidePadding.toFloat(), currentY, width - sidePadding.toFloat(), currentY, gridPaint)
    currentY += 20f

    val tableLeft = sidePadding.toFloat()
    val tableRight = width - sidePadding.toFloat()
    val tableTop = currentY

    val headers = arrayOf("عنوان", "تعداد", "مقادیر مصرف", "زمان مصرف", "دستورات متفرقه")
    val headerBg = android.graphics.Paint().apply { color = themeSpec.primaryContainerArgb }
    canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + 60f, headerBg)

    var curX = tableRight
    for (i in 0 until 5) {
        val colW = colWidths[i]
        val cellLeft = curX - colW
        canvas.drawRect(cellLeft, tableTop, curX, tableTop + 60f, gridPaint)
        val hL = android.text.StaticLayout.Builder.obtain(headers[i], 0, headers[i].length, thPaint, colW.toInt() - 10)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        canvas.save()
        canvas.translate(cellLeft + 5f, tableTop + 14f)
        hL.draw(canvas)
        canvas.restore()
        curX -= colW
    }

    currentY += 60f

    rows.forEachIndexed { index, r ->
        val rowH = rowHeights[index]
        val cellValues = arrayOf(r.title, r.quantity, r.dosage, r.timing, r.instructions)
        
        curX = tableRight
        for (i in 0 until 5) {
            val colW = colWidths[i]
            val cellLeft = curX - colW
            canvas.drawRect(cellLeft, currentY, curX, currentY + rowH, gridPaint)
            
            val align = if (i == 0 || i == 4) android.text.Layout.Alignment.ALIGN_NORMAL else android.text.Layout.Alignment.ALIGN_CENTER
            val cL = android.text.StaticLayout.Builder.obtain(cellValues[i], 0, cellValues[i].length, tdPaint, colW.toInt() - 16)
                .setAlignment(align)
                .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build()
            
            canvas.save()
            canvas.translate(cellLeft + 8f, currentY + 14f)
            cL.draw(canvas)
            canvas.restore()
            
            curX -= colW
        }
        currentY += rowH
    }

    currentY += 30f

    if (notesLayout != null) {
        val notesHeader = "دستورات متفرقه و نکات مهم:"
        val nhL = android.text.StaticLayout.Builder.obtain(notesHeader, 0, notesHeader.length, thPaint, textWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .build()
        canvas.save()
        canvas.translate(sidePadding.toFloat(), currentY)
        nhL.draw(canvas)
        canvas.restore()
        currentY += nhL.height + 10f

        val bgNotes = android.graphics.Paint().apply { color = themeSpec.surfaceArgb }
        canvas.drawRect(sidePadding.toFloat(), currentY, width - sidePadding.toFloat(), currentY + notesLayout.height + 20f, bgNotes)
        canvas.drawRect(sidePadding.toFloat(), currentY, width - sidePadding.toFloat(), currentY + notesLayout.height + 20f, gridPaint)

        canvas.save()
        canvas.translate(sidePadding.toFloat() + 20f, currentY + 10f)
        notesLayout.draw(canvas)
        canvas.restore()

        currentY += notesLayout.height + 40f
    }



    val file = File(context.cacheDir, "prescription_card_${System.currentTimeMillis()}.png")
    java.io.FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * Generates a styled Admission Form Card image (with Medical History Positives, Patient details,
 * and Patient's transparent PNG signature at the bottom) matching the app's dark navy/cyan theme.
 */
fun generateAdmissionFormCardImage(
    context: Context,
    patientName: String,
    patientPhone: String,
    patientAge: String,
    patientNationalId: String,
    admissionDateStr: String,
    positiveMedicalHistory: List<String>,
    chiefComplaint: String,
    signatureBase64: String?,
    clinicName: String,
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1080
    val sidePadding = 60
    val textWidth = width - (sidePadding * 2)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 52f
        isFakeBoldText = true
    }

    val subtitlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0F2FE")
        textSize = 34f
    }

    val sectionPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E88E5")
        textSize = 42f
        isFakeBoldText = true
    }

    val bodyPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E293B")
        textSize = 36f
    }

    val alertPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#C2410C")
        textSize = 36f
        isFakeBoldText = true
    }

    val legalPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#475569")
        textSize = 30f
    }

    val alignment = android.text.Layout.Alignment.ALIGN_OPPOSITE

    // Build Layouts
    val titleLayout = android.text.StaticLayout.Builder.obtain("فرم رسمی پذیرش و تاریخچه پزشکی بیمار", 0, "فرم رسمی پذیرش و تاریخچه پزشکی بیمار".length, titlePaint, textWidth).setAlignment(alignment).build()
    val subtitleLayout = android.text.StaticLayout.Builder.obtain("$clinicName | تاریخ پذیرش: $admissionDateStr", 0, "$clinicName | تاریخ پذیرش: $admissionDateStr".length, subtitlePaint, textWidth).setAlignment(alignment).build()

    val patInfoText = "بیمار: $patientName  |  همراه: $patientPhone\nکد ملی: ${if (patientNationalId.isNotBlank()) patientNationalId else "ثبت‌نشده"}  |  سن: ${if (patientAge.isNotBlank()) patientAge else "ثبت‌نشده"}"
    val patInfoLayout = android.text.StaticLayout.Builder.obtain(patInfoText, 0, patInfoText.length, bodyPaint, textWidth).setAlignment(alignment).build()

    val medTitleLayout = android.text.StaticLayout.Builder.obtain("🚨 سوابق پزشکی و هشدارهای سلامتی (موارد مثبت):", 0, "🚨 سوابق پزشکی و هشدارهای سلامتی (موارد مثبت):".length, sectionPaint, textWidth).setAlignment(alignment).build()

    val medContent = if (positiveMedicalHistory.isNotEmpty()) {
        positiveMedicalHistory.joinToString("\n") { "• $it" }
    } else {
        "🟢 فاقد بیماری زمینه‌ای خاص بر اساس اظهارات بیمار"
    }
    val medLayout = android.text.StaticLayout.Builder.obtain(medContent, 0, medContent.length, if (positiveMedicalHistory.isNotEmpty()) alertPaint else bodyPaint, textWidth).setAlignment(alignment).build()

    val ccTitleLayout = android.text.StaticLayout.Builder.obtain("🦷 شکایت اصلی و ملاحظات اولیه:", 0, "🦷 شکایت اصلی و ملاحظات اولیه:".length, sectionPaint, textWidth).setAlignment(alignment).build()
    val ccText = if (chiefComplaint.isNotBlank()) chiefComplaint else "جهت معاینه و چکاپ دوره‌ای دندان‌پزشکی"
    val ccLayout = android.text.StaticLayout.Builder.obtain(ccText, 0, ccText.length, bodyPaint, textWidth).setAlignment(alignment).build()

    val legalText = "اینجانب $patientName صحت اطلاعات سوابق پزشکی فوق را تأیید نموده و موافقت کامل خود را با انجام معاینات و پروتکل‌های درمانی اعلام می‌دارم."
    val legalLayout = android.text.StaticLayout.Builder.obtain(legalText, 0, legalText.length, legalPaint, textWidth).setAlignment(alignment).build()

    // Calculate total height
    var contentHeight = 220 + titleLayout.height + subtitleLayout.height + 40
    contentHeight += 120 + patInfoLayout.height
    contentHeight += 120 + medTitleLayout.height + medLayout.height
    contentHeight += 120 + ccTitleLayout.height + ccLayout.height
    contentHeight += 320 + legalLayout.height // space for legal text + signature

    val bmp = android.graphics.Bitmap.createBitmap(width, contentHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)

    // Background
    canvas.drawColor(android.graphics.Color.parseColor("#F8FAFC"))

    // Header Canvas (App Theme Gradient: using active ExportThemeSpec)
    val headerPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 240f,
            intArrayOf(themeSpec.gradientStartArgb, themeSpec.primaryArgb),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    val headerHeight = 220f + titleLayout.height + subtitleLayout.height
    canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerPaint)

    // Draw Title & Subtitle in Header
    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f)
    titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 50f + titleLayout.height)
    subtitleLayout.draw(canvas)
    canvas.restore()

    var currentY = headerHeight + 50f

    // Patient Info Box
    val infoCardPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }

    val infoBoxHeight = patInfoLayout.height + 40f
    val rectInfo = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + infoBoxHeight)
    canvas.drawRoundRect(rectInfo, 20f, 20f, infoCardPaint)
    canvas.drawRoundRect(rectInfo, 20f, 20f, borderPaint)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    patInfoLayout.draw(canvas)
    canvas.restore()

    currentY += infoBoxHeight + 40f

    // Medical History Section Card
    val medBoxHeight = medTitleLayout.height + medLayout.height + 60f
    val rectMed = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + medBoxHeight)
    val medCardBg = android.graphics.Paint().apply {
        color = if (positiveMedicalHistory.isNotEmpty()) android.graphics.Color.parseColor("#FFF7ED") else android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val medBorderBg = android.graphics.Paint().apply {
        color = if (positiveMedicalHistory.isNotEmpty()) android.graphics.Color.parseColor("#F97316") else android.graphics.Color.parseColor("#CBD5E1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(rectMed, 20f, 20f, medCardBg)
    canvas.drawRoundRect(rectMed, 20f, 20f, medBorderBg)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    medTitleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 30f + medTitleLayout.height)
    medLayout.draw(canvas)
    canvas.restore()

    currentY += medBoxHeight + 40f

    // Chief Complaint Box
    val ccBoxHeight = ccTitleLayout.height + ccLayout.height + 60f
    val rectCc = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + ccBoxHeight)
    canvas.drawRoundRect(rectCc, 20f, 20f, infoCardPaint)
    canvas.drawRoundRect(rectCc, 20f, 20f, borderPaint)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    ccTitleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 30f + ccTitleLayout.height)
    ccLayout.draw(canvas)
    canvas.restore()

    currentY += ccBoxHeight + 40f

    // Legal & Signature Box at Bottom
    val sigBoxHeight = legalLayout.height + 220f
    val rectSig = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + sigBoxHeight)
    val sigBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F0F9FF")
        style = android.graphics.Paint.Style.FILL
    }
    val sigBorder = android.graphics.Paint().apply {
        color = themeSpec.primaryArgb
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(rectSig, 20f, 20f, sigBg)
    canvas.drawRoundRect(rectSig, 20f, 20f, sigBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    legalLayout.draw(canvas)
    canvas.restore()

    // Draw Transparent PNG Signature Bitmap
    val sigBmp = if (!signatureBase64.isNullOrBlank()) {
        try {
            if (signatureBase64.startsWith("/") || signatureBase64.startsWith("file://")) {
                val cleanPath = signatureBase64.removePrefix("file://")
                android.graphics.BitmapFactory.decodeFile(cleanPath)
            } else {
                val pureB64 = if (signatureBase64.contains(",")) signatureBase64.substringAfter(",") else signatureBase64
                val decoded = android.util.Base64.decode(pureB64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            }
        } catch (e: Exception) { null }
    } else null

    val sigY = currentY + legalLayout.height + 30f
    if (sigBmp != null) {
        val destRect = android.graphics.RectF(sidePadding + 40f, sigY, sidePadding + 400f, sigY + 140f)
        val sigPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(sigBmp, null, destRect, sigPaint)
    }

    val stampPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 28f
        isFakeBoldText = true
    }
    canvas.drawText("✓ امضای دیجیتال تأییدشده بیمار", width - sidePadding - 380f, sigY + 80f, stampPaint)

    val file = File(context.cacheDir, "admission_form_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * تولید سند رسمی و چاپی رضایت‌نامه آگاهانه و برائت‌نامه پزشکی قابل ارائه به مراجع قانونی
 * مطابق مواد ۴۹۵ و ۴۹۷ قانون مجازات اسلامی و مواد ۷ و ۱۰ قانون تجارت الکترونیک ج.ا.ایران
 */
fun generateLegalConsentFormDocument(
    context: Context,
    patientName: String,
    patientNationalId: String = "",
    patientFatherName: String = "",
    patientPhone: String = "",
    patientAge: String = "",
    signerRole: String = "خود بیمار",
    doctorName: String = "",
    doctorMedicalNo: String = "",
    clinicName: String = "",
    consentTitle: String = "رضایت‌نامه آگاهانه و برائت‌نامه درمان دندان‌پزشکی",
    legalText: String = "",
    signatureBase64: String? = null,
    timestamp: Long = System.currentTimeMillis(),
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1200
    val sidePadding = 50
    val textWidth = width - (sidePadding * 2)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }

    val subtitlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0F2FE")
        textSize = 28f
    }

    val headerMetaPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F1F5F9")
        textSize = 24f
    }

    val gridLabelPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#475569")
        textSize = 26f
        isFakeBoldText = true
    }

    val gridValPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 28f
        isFakeBoldText = true
    }

    val sectionHeaderPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 34f
        isFakeBoldText = true
    }

    val bodyPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E293B")
        textSize = 30f
    }

    val alignment = android.text.Layout.Alignment.ALIGN_OPPOSITE

    val legalCode = com.example.data.LegalConsentKnowledgeBase.generateLegalVerificationCode(patientNationalId.ifBlank { patientName }, timestamp)
    val dateStr = java.text.SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

    val titleLayout = android.text.StaticLayout.Builder.obtain(
        "فرم رسمی رضایت‌نامه آگاهانه و برائت‌نامه پزشکی", 0, "فرم رسمی رضایت‌نامه آگاهانه و برائت‌نامه پزشکی".length, titlePaint, textWidth
    ).setAlignment(alignment).build()

    val subTitleLayout = android.text.StaticLayout.Builder.obtain(
        "استناد به مواد ۴۹۵ و ۴۹۷ قانون مجازات اسلامی و مواد ۷ و ۱۰ قانون تجارت الکترونیک", 0, "استناد به مواد ۴۹۵ و ۴۹۷ قانون مجازات اسلامی و مواد ۷ و ۱۰ قانون تجارت الکترونیک".length, subtitlePaint, textWidth
    ).setAlignment(alignment).build()

    val consentSubjectLayout = android.text.StaticLayout.Builder.obtain(
        "موضوع رضایت‌نامه: $consentTitle", 0, "موضوع رضایت‌نامه: $consentTitle".length, sectionHeaderPaint, textWidth
    ).setAlignment(alignment).build()

    val effectiveBodyText = if (legalText.isNotBlank()) legalText else "اینجانب با آگاهی کامل از تمامی عوارض، خطرات و هزینه‌های درمان، موافقت و برائت خود را اعلام می‌دارم."
    val bodyLayout = android.text.StaticLayout.Builder.obtain(
        effectiveBodyText, 0, effectiveBodyText.length, bodyPaint, textWidth - 40
    ).setAlignment(alignment).build()

    val legalAckStr = "اینجانب $patientName (کد ملی: ${patientNationalId.ifBlank { "ثبت‌شده" }}) بدین‌وسیله اقرار می‌نمایم که متن فوق را با دقت مطالعه نموده و برائت صریح پزشک معالج را صادر نموده‌ام."
    val ackLayout = android.text.StaticLayout.Builder.obtain(
        legalAckStr, 0, legalAckStr.length, bodyPaint, textWidth - 40
    ).setAlignment(alignment).build()

    var totalHeight = 220 + titleLayout.height + subTitleLayout.height + 40
    totalHeight += 260
    totalHeight += 60 + consentSubjectLayout.height
    totalHeight += 40 + bodyLayout.height + 40
    totalHeight += 30 + ackLayout.height + 40
    totalHeight += 340
    totalHeight += 120

    val bmp = android.graphics.Bitmap.createBitmap(width, totalHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.WHITE)

    // 1. Official Header (Dynamic Theme Gradient)
    val headerPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 240f,
            intArrayOf(themeSpec.gradientStartArgb, themeSpec.primaryArgb),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    val headerH = 200f + titleLayout.height + subTitleLayout.height
    canvas.drawRect(0f, 0f, width.toFloat(), headerH, headerPaint)

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 30f)
    titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f + titleLayout.height)
    subTitleLayout.draw(canvas)
    canvas.restore()

    val headerMetaStr = "کد تاییدیه حقوقی: $legalCode | تاریخ و زمان ثبت: $dateStr"
    canvas.drawText(headerMetaStr, sidePadding.toFloat(), headerH - 25f, headerMetaPaint)

    var currentY = headerH + 30f

    // 2. Patient & Doctor Specifications Grid Card
    val gridBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F8FAFC")
        style = android.graphics.Paint.Style.FILL
    }
    val gridBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CBD5E1")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    val gridRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + 220f)
    canvas.drawRoundRect(gridRect, 16f, 16f, gridBg)
    canvas.drawRoundRect(gridRect, 16f, 16f, gridBorder)

    val rightColX = width - sidePadding - 30f
    canvas.drawText("مشخصات امضاکننده / بیمار:", rightColX, currentY + 40f, gridLabelPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
    val fatherInfo = if (patientFatherName.isNotBlank()) " | نام پدر: $patientFatherName" else ""
    canvas.drawText("نام: $patientName | کد ملی: ${patientNationalId.ifBlank { "-" }}$fatherInfo", rightColX, currentY + 85f, gridValPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })
    canvas.drawText("شماره تماس: ${patientPhone.ifBlank { "-" }} | سن: ${patientAge.ifBlank { "-" }} | سمت: $signerRole", rightColX, currentY + 130f, gridValPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT })

    val leftColX = sidePadding + 30f
    canvas.drawText("مشخصات پزشک معالج و مرکز درمانی:", leftColX, currentY + 40f, gridLabelPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })
    val docStr = if (doctorName.isNotBlank()) doctorName else "پزشک معالج"
    val medStr = if (doctorMedicalNo.isNotBlank()) doctorMedicalNo else "ثبت‌شده"
    val clStr = if (clinicName.isNotBlank()) clinicName else "کلینیک دندان‌پزشکی"
    canvas.drawText("پزشک معالج: $docStr (نظام پزشکی: $medStr)", leftColX, currentY + 85f, gridValPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })
    canvas.drawText("مرکز درمانی: $clStr", leftColX, currentY + 130f, gridValPaint.apply { textAlign = android.graphics.Paint.Align.LEFT })

    currentY += 250f

    // 3. Consent Subject Title
    canvas.save()
    canvas.translate(sidePadding.toFloat(), currentY)
    consentSubjectLayout.draw(canvas)
    canvas.restore()

    currentY += consentSubjectLayout.height + 30f

    // 4. Legal Text Box
    val bodyBoxBg = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    val bodyBoxBorder = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    val bodyBoxH = bodyLayout.height + 40f
    val bodyRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + bodyBoxH)
    canvas.drawRoundRect(bodyRect, 16f, 16f, bodyBoxBg)
    canvas.drawRoundRect(bodyRect, 16f, 16f, bodyBoxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    bodyLayout.draw(canvas)
    canvas.restore()

    currentY += bodyBoxH + 30f

    // 5. Acknowledgment Card
    val ackBoxBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#F0F9FF")
        style = android.graphics.Paint.Style.FILL
    }
    val ackBoxBorder = android.graphics.Paint().apply {
        color = themeSpec.primaryArgb
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3f
    }
    val ackBoxH = ackLayout.height + 40f
    val ackRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + ackBoxH)
    canvas.drawRoundRect(ackRect, 16f, 16f, ackBoxBg)
    canvas.drawRoundRect(ackRect, 16f, 16f, ackBoxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    ackLayout.draw(canvas)
    canvas.restore()

    currentY += ackBoxH + 40f

    // 6. Signature & Fingerprint Verification Grid
    val boxW = (textWidth.toFloat() - 40f) / 3f
    val boxH = 260f

    // Box A (Right): Fingerprint Box (اثر انگشت)
    val fpX1 = (width - sidePadding).toFloat() - boxW
    val fpX2 = (width - sidePadding).toFloat()
    val fpRect = android.graphics.RectF(fpX1, currentY, fpX2, currentY + boxH)
    canvas.drawRoundRect(fpRect, 12f, 12f, gridBg)
    canvas.drawRoundRect(fpRect, 12f, 12f, gridBorder)

    val labelPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 24f
        isFakeBoldText = true
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("اثر انگشت سبابه دست راست", fpRect.centerX(), currentY + 35f, labelPaint)
    canvas.drawText("(بیمار / ولی قانونی)", fpRect.centerX(), currentY + 65f, labelPaint.apply { textSize = 20f; color = android.graphics.Color.GRAY })

    val fpOval = android.graphics.RectF(fpRect.centerX() - 60f, currentY + 80f, fpRect.centerX() + 60f, currentY + 220f)
    val fpDashedPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#94A3B8")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
    canvas.drawOval(fpOval, fpDashedPaint)
    canvas.drawText("محل اثر انگشت", fpRect.centerX(), fpOval.centerY() + 8f, labelPaint.apply { textSize = 18f; color = android.graphics.Color.GRAY })

    // Box B (Center): Digital Signature Box (امضای دیجیتال دوربری‌شده)
    val sigX1 = fpX1 - 20f - boxW
    val sigX2 = fpX1 - 20f
    val sigRect = android.graphics.RectF(sigX1, currentY, sigX2, currentY + boxH)
    canvas.drawRoundRect(sigRect, 12f, 12f, gridBg)
    canvas.drawRoundRect(sigRect, 12f, 12f, gridBorder)

    canvas.drawText("امضای دیجیتال / لمسی", sigRect.centerX(), currentY + 35f, labelPaint.apply { textSize = 24f; color = android.graphics.Color.parseColor("#0F172A") })
    canvas.drawText("تأییدشده - ماده ۷ تجارت الکترونیک", sigRect.centerX(), currentY + 65f, labelPaint.apply { textSize = 17f; color = themeSpec.primaryArgb })

    val sigBmp = if (!signatureBase64.isNullOrBlank()) {
        try {
            if (signatureBase64.startsWith("/") || signatureBase64.startsWith("file://")) {
                val cleanPath = signatureBase64.removePrefix("file://")
                android.graphics.BitmapFactory.decodeFile(cleanPath)
            } else {
                val pureB64 = if (signatureBase64.contains(",")) signatureBase64.substringAfter(",") else signatureBase64
                val decoded = android.util.Base64.decode(pureB64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
            }
        } catch (e: Exception) { null }
    } else null

    if (sigBmp != null) {
        val destSig = android.graphics.RectF(sigRect.left + 15f, currentY + 70f, sigRect.right - 15f, currentY + 235f)
        val sigPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        canvas.drawBitmap(sigBmp, null, destSig, sigPaint)
    } else {
        canvas.drawText("امضا ثبت نشده است", sigRect.centerX(), currentY + 150f, labelPaint.apply { textSize = 20f; color = android.graphics.Color.RED })
    }

    // Box C (Left): Doctor / Clinic Stamp Box
    val docX1 = sidePadding.toFloat()
    val docX2 = docX1 + boxW
    val docRect = android.graphics.RectF(docX1, currentY, docX2, currentY + boxH)
    canvas.drawRoundRect(docRect, 12f, 12f, gridBg)
    canvas.drawRoundRect(docRect, 12f, 12f, gridBorder)

    canvas.drawText("مهر و امضای پزشک معالج", docRect.centerX(), currentY + 35f, labelPaint.apply { textSize = 24f; color = android.graphics.Color.parseColor("#0F172A") })
    canvas.drawText(docStr, docRect.centerX(), currentY + 75f, labelPaint.apply { textSize = 22f; color = android.graphics.Color.parseColor("#0F172A") })
    canvas.drawText("نظام پزشکی: $medStr", docRect.centerX(), currentY + 105f, labelPaint.apply { textSize = 18f; color = android.graphics.Color.GRAY })

    currentY += boxH + 40f

    // 7. Security Footer Banner
    val secBg = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#0F172A")
        style = android.graphics.Paint.Style.FILL
    }
    val secRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + 80f)
    canvas.drawRoundRect(secRect, 8f, 8f, secBg)

    val secTextPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 21f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    canvas.drawText("🔒 سند الکترونیکی غیرقابل‌انکار - رمزنگاری‌شده مطابق مواد ۷ و ۱۰ قانون تجارت الکترونیک ج.ا.ایران", secRect.centerX(), currentY + 35f, secTextPaint)
    canvas.drawText("کد پیگیری حقوقی سند: $legalCode | بایگانی الکترونیکی کلینیک", secRect.centerX(), currentY + 65f, secTextPaint.apply { textSize = 18f; color = android.graphics.Color.parseColor("#94A3B8") })

    val file = File(context.cacheDir, "legal_consent_document_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * 1. تولید تصویر کارت مراقبت‌های پس از درمان (Post-Op Care Instructions)
 */
fun generatePostOpCareInstructionsCardImage(
    context: Context,
    clinicName: String,
    doctorName: String,
    patientName: String,
    patientFatherName: String = "",
    patientNationalId: String = "",
    treatmentTitle: String,
    careInstructions: List<String>,
    emergencyPhone: String = "",
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1200
    val sidePadding = 50
    val textWidth = width - (sidePadding * 2)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }
    val subtitlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0F2FE")
        textSize = 28f
    }
    val sectionPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 34f
        isFakeBoldText = true
    }
    val bodyPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#1E293B")
        textSize = 28f
    }
    val warnPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#991B1B")
        textSize = 26f
        isFakeBoldText = true
    }

    val alignment = android.text.Layout.Alignment.ALIGN_OPPOSITE

    val titleLayout = android.text.StaticLayout.Builder.obtain(
        "راهنمای مراقبت‌های بالینی پس از درمان", 0, "راهنمای مراقبت‌های بالینی پس از درمان".length, titlePaint, textWidth
    ).setAlignment(alignment).build()

    val subTitleLayout = android.text.StaticLayout.Builder.obtain(
        "$clinicName | اقدام درمانی: $treatmentTitle", 0, "$clinicName | اقدام درمانی: $treatmentTitle".length, subtitlePaint, textWidth
    ).setAlignment(alignment).build()

    val fatherStr = if (patientFatherName.isNotBlank()) " | نام پدر: $patientFatherName" else ""
    val patMetaText = "مراجع عزیز: $patientName  |  کد ملی: ${patientNationalId.ifBlank { "-" }}$fatherStr"
    val patMetaLayout = android.text.StaticLayout.Builder.obtain(
        patMetaText, 0, patMetaText.length, sectionPaint, textWidth
    ).setAlignment(alignment).build()

    val instructionsText = careInstructions.joinToString("\n\n") { "• $it" }
    val instrLayout = android.text.StaticLayout.Builder.obtain(
        instructionsText, 0, instructionsText.length, bodyPaint, textWidth - 40
    ).setAlignment(alignment).build()

    val emergText = "⚠️ در صورت بروز درد شدید، خونریزی غیرقابل‌کنترل یا تورم ناگهانی، سریعاً با مطب تماس بگیرید.${if (emergencyPhone.isNotBlank()) "\n📞 تلفن تماس اضطراری: $emergencyPhone" else ""}"
    val emergLayout = android.text.StaticLayout.Builder.obtain(
        emergText, 0, emergText.length, warnPaint, textWidth - 40
    ).setAlignment(alignment).build()

    var contentHeight = 220 + titleLayout.height + subTitleLayout.height + 40
    contentHeight += 80 + patMetaLayout.height
    contentHeight += 60 + instrLayout.height
    contentHeight += 60 + emergLayout.height + 120

    val bmp = android.graphics.Bitmap.createBitmap(width, contentHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#F8FAFC"))

    // Header (Theme Gradient)
    val headerPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 240f,
            intArrayOf(themeSpec.gradientStartArgb, themeSpec.primaryArgb),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    val headerH = 200f + titleLayout.height + subTitleLayout.height
    canvas.drawRect(0f, 0f, width.toFloat(), headerH, headerPaint)

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 30f)
    titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f + titleLayout.height)
    subTitleLayout.draw(canvas)
    canvas.restore()

    var currentY = headerH + 30f

    // Patient Banner
    canvas.save()
    canvas.translate(sidePadding.toFloat(), currentY)
    patMetaLayout.draw(canvas)
    canvas.restore()

    currentY += patMetaLayout.height + 30f

    // Instructions Box
    val boxBg = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL }
    val boxBorder = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#CBD5E1"); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }

    val instrH = instrLayout.height + 40f
    val instrRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + instrH)
    canvas.drawRoundRect(instrRect, 16f, 16f, boxBg)
    canvas.drawRoundRect(instrRect, 16f, 16f, boxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    instrLayout.draw(canvas)
    canvas.restore()

    currentY += instrH + 30f

    // Emergency Box
    val emergBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#FEF2F2"); style = android.graphics.Paint.Style.FILL }
    val emergBorder = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#EF4444"); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }

    val emergH = emergLayout.height + 40f
    val emergRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + emergH)
    canvas.drawRoundRect(emergRect, 16f, 16f, emergBg)
    canvas.drawRoundRect(emergRect, 16f, 16f, emergBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    emergLayout.draw(canvas)
    canvas.restore()

    val file = File(context.cacheDir, "post_op_instructions_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * 2. تولید تصویر یادآوری سررسید قسط (Installment Due Reminder)
 */
fun generateInstallmentDueReminderCardImage(
    context: Context,
    clinicName: String,
    patientName: String,
    patientFatherName: String = "",
    patientNationalId: String = "",
    installmentTitle: String,
    dueDateStr: String,
    amountStr: String,
    bankCardNumber: String = "",
    bankName: String = "",
    accountHolder: String = "",
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1200
    val sidePadding = 50
    val textWidth = width - (sidePadding * 2)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }
    val subtitlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FEF3C7")
        textSize = 28f
    }
    val headerPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 32f
        isFakeBoldText = true
    }
    val valuePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = themeSpec.primaryArgb
        textSize = 38f
        isFakeBoldText = true
    }
    val bodyPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#334155")
        textSize = 28f
    }

    val alignment = android.text.Layout.Alignment.ALIGN_OPPOSITE

    val titleLayout = android.text.StaticLayout.Builder.obtain("یادآوری سررسید قسط دندان‌پزشکی", 0, "یادآوری سررسید قسط دندان‌پزشکی".length, titlePaint, textWidth).setAlignment(alignment).build()
    val subtitleLayout = android.text.StaticLayout.Builder.obtain("$clinicName | اطلاعیه پرداخت سررسید", 0, "$clinicName | اطلاعیه پرداخت سررسید".length, subtitlePaint, textWidth).setAlignment(alignment).build()

    val fatherStr = if (patientFatherName.isNotBlank()) " (فرزند $patientFatherName)" else ""
    val patText = "مراجع گرامی: $patientName$fatherStr\nکد ملی: ${patientNationalId.ifBlank { "-" }}"
    val patLayout = android.text.StaticLayout.Builder.obtain(patText, 0, patText.length, headerPaint, textWidth).setAlignment(alignment).build()

    val detailsText = "عنوان قسط: $installmentTitle\nتاریخ سررسید: $dueDateStr\nمبلغ قسط: $amountStr"
    val detailsLayout = android.text.StaticLayout.Builder.obtain(detailsText, 0, detailsText.length, valuePaint, textWidth - 40).setAlignment(alignment).build()

    val cardInfoText = if (bankCardNumber.isNotBlank()) {
        "💳 شماره کارت جهت واریز:\n$bankCardNumber\n${if (bankName.isNotBlank()) "بانک: $bankName | " else ""}صاحب حساب: ${accountHolder.ifBlank { clinicName }}"
    } else {
        "لطفاً جهت تسویه قسط به پذیرش کلینیک مراجعه فرمایید."
    }
    val cardLayout = android.text.StaticLayout.Builder.obtain(cardInfoText, 0, cardInfoText.length, bodyPaint, textWidth - 40).setAlignment(alignment).build()

    var contentHeight = 220 + titleLayout.height + subtitleLayout.height + 40
    contentHeight += 60 + patLayout.height
    contentHeight += 60 + detailsLayout.height
    contentHeight += 60 + cardLayout.height + 100

    val bmp = android.graphics.Bitmap.createBitmap(width, contentHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#F8FAFC"))

    // Header Background (Dynamic Theme Gradient)
    val bgHeader = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 240f,
            intArrayOf(themeSpec.gradientStartArgb, themeSpec.primaryArgb),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    val headerH = 180f + titleLayout.height + subtitleLayout.height
    canvas.drawRect(0f, 0f, width.toFloat(), headerH, bgHeader)

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 30f)
    titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f + titleLayout.height)
    subtitleLayout.draw(canvas)
    canvas.restore()

    var currentY = headerH + 30f

    canvas.save()
    canvas.translate(sidePadding.toFloat(), currentY)
    patLayout.draw(canvas)
    canvas.restore()

    currentY += patLayout.height + 30f

    // Details Box
    val boxBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F8FAFC"); style = android.graphics.Paint.Style.FILL }
    val boxBorder = android.graphics.Paint().apply { color = themeSpec.primaryArgb; style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }

    val detailsH = detailsLayout.height + 40f
    val detailsRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + detailsH)
    canvas.drawRoundRect(detailsRect, 16f, 16f, boxBg)
    canvas.drawRoundRect(detailsRect, 16f, 16f, boxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    detailsLayout.draw(canvas)
    canvas.restore()

    currentY += detailsH + 30f

    // Card Info Box
    val cardBg = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL }
    val cardBorder = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#CBD5E1"); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3f }

    val cardH = cardLayout.height + 40f
    val cardRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + cardH)
    canvas.drawRoundRect(cardRect, 16f, 16f, cardBg)
    canvas.drawRoundRect(cardRect, 16f, 16f, cardBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    cardLayout.draw(canvas)
    canvas.restore()

    val file = File(context.cacheDir, "installment_due_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * 3. تولید تصویر اخطار تاخیر وصول اقساط (Overdue Delay Notice)
 */
fun generateOverdueInstallmentNoticeCardImage(
    context: Context,
    clinicName: String,
    patientName: String,
    patientFatherName: String = "",
    patientNationalId: String = "",
    installmentTitle: String,
    dueDateStr: String,
    delayDays: Int,
    amountStr: String,
    bankCardNumber: String = "",
    bankName: String = "",
    accountHolder: String = "",
    themeSpec: ExportThemeSpec = ExportThemeSpec.Default
): File {
    val width = 1200
    val sidePadding = 50
    val textWidth = width - (sidePadding * 2)

    val titlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        isFakeBoldText = true
    }
    val subtitlePaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FEE2E2")
        textSize = 28f
    }
    val headerPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#0F172A")
        textSize = 32f
        isFakeBoldText = true
    }
    val warnPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#991B1B")
        textSize = 36f
        isFakeBoldText = true
    }
    val bodyPaint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#334155")
        textSize = 28f
    }

    val alignment = android.text.Layout.Alignment.ALIGN_OPPOSITE

    val titleLayout = android.text.StaticLayout.Builder.obtain("اخطار و یادآوری تاخیر وصول اقساط", 0, "اخطار و یادآوری تاخیر وصول اقساط".length, titlePaint, textWidth).setAlignment(alignment).build()
    val subtitleLayout = android.text.StaticLayout.Builder.obtain("$clinicName | اطلاعیه سررسید گذشته", 0, "$clinicName | اطلاعیه سررسید گذشته".length, subtitlePaint, textWidth).setAlignment(alignment).build()

    val fatherStr = if (patientFatherName.isNotBlank()) " (فرزند $patientFatherName)" else ""
    val patText = "مراجع محترم: $patientName$fatherStr\nکد ملی: ${patientNationalId.ifBlank { "-" }}"
    val patLayout = android.text.StaticLayout.Builder.obtain(patText, 0, patText.length, headerPaint, textWidth).setAlignment(alignment).build()

    val warnText = "🚨 عنوان قسط: $installmentTitle\nتاریخ سررسید: $dueDateStr  |  میزان تاخیر: $delayDays روز\nمبلغ قابل تسویه: $amountStr\nخواهشمند است جهت جلوگیری از معوق شدن حساب، نسبت به واریز اقدام فرمایید."
    val warnLayout = android.text.StaticLayout.Builder.obtain(warnText, 0, warnText.length, warnPaint, textWidth - 40).setAlignment(alignment).build()

    val cardInfoText = if (bankCardNumber.isNotBlank()) {
        "💳 شماره کارت واریز:\n$bankCardNumber\n${if (bankName.isNotBlank()) "بانک: $bankName | " else ""}صاحب حساب: ${accountHolder.ifBlank { clinicName }}"
    } else {
        "لطفاً سریعاً جهت تسویه حساب به بخش حسابداری کلینیک مراجعه فرماپید."
    }
    val cardLayout = android.text.StaticLayout.Builder.obtain(cardInfoText, 0, cardInfoText.length, bodyPaint, textWidth - 40).setAlignment(alignment).build()

    var contentHeight = 220 + titleLayout.height + subtitleLayout.height + 40
    contentHeight += 60 + patLayout.height
    contentHeight += 60 + warnLayout.height
    contentHeight += 60 + cardLayout.height + 100

    val bmp = android.graphics.Bitmap.createBitmap(width, contentHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.drawColor(android.graphics.Color.parseColor("#FEF2F2"))

    // Header Background
    val bgHeader = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, width.toFloat(), 240f,
            intArrayOf(android.graphics.Color.parseColor("#7F1D1D"), android.graphics.Color.parseColor("#DC2626")),
            null, android.graphics.Shader.TileMode.CLAMP)
    }
    val headerH = 180f + titleLayout.height + subtitleLayout.height
    canvas.drawRect(0f, 0f, width.toFloat(), headerH, bgHeader)

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 30f)
    titleLayout.draw(canvas)
    canvas.restore()

    canvas.save()
    canvas.translate(sidePadding.toFloat(), 40f + titleLayout.height)
    subtitleLayout.draw(canvas)
    canvas.restore()

    var currentY = headerH + 30f

    canvas.save()
    canvas.translate(sidePadding.toFloat(), currentY)
    patLayout.draw(canvas)
    canvas.restore()

    currentY += patLayout.height + 30f

    // Warn Box
    val boxBg = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL }
    val boxBorder = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#EF4444"); style = android.graphics.Paint.Style.STROKE; strokeWidth = 3.5f }

    val warnH = warnLayout.height + 40f
    val warnRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + warnH)
    canvas.drawRoundRect(warnRect, 16f, 16f, boxBg)
    canvas.drawRoundRect(warnRect, 16f, 16f, boxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    warnLayout.draw(canvas)
    canvas.restore()

    currentY += warnH + 30f

    // Card Info Box
    val cardH = cardLayout.height + 40f
    val cardRect = android.graphics.RectF(sidePadding.toFloat(), currentY, (width - sidePadding).toFloat(), currentY + cardH)
    canvas.drawRoundRect(cardRect, 16f, 16f, boxBg)
    canvas.drawRoundRect(cardRect, 16f, 16f, boxBorder)

    canvas.save()
    canvas.translate(sidePadding + 20f, currentY + 20f)
    cardLayout.draw(canvas)
    canvas.restore()

    val file = File(context.cacheDir, "overdue_installment_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out) }
    return file
}

/**
 * Opens the default SMS app with the recipient and a pre-filled short text
 * summary — the user reviews and taps send themselves. Deliberately does
 * NOT send silently (no SEND_SMS permission requested), which keeps this
 * safe for a healthcare app and avoids restricted-permission Play policy.
 */
fun sendSmsPrefilled(context: Context, phone: String, message: String) {
    val digits = phone.filter { it.isDigit() }
    val normalizedPhone = when {
        digits.startsWith("0098") -> "+98${digits.removePrefix("0098")}"
        digits.startsWith("98") -> "+98${digits.removePrefix("98")}"
        digits.startsWith("0") -> "+98${digits.removePrefix("0")}"
        digits.isNotEmpty() -> "+98$digits"
        else -> phone
    }

    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$normalizedPhone")).apply {
        putExtra("sms_body", message)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "برنامه پیامک روی این دستگاه یافت نشد", Toast.LENGTH_SHORT).show()
    }
}
