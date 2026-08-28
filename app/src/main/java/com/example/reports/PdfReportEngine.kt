package com.example.reports

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ReportColumn(val title: String, val weight: Float = 1f)
data class ReportSummaryLine(val label: String, val value: String, val emphasize: Boolean = false)

/**
 * Reusable, self-contained PDF builder for every printable document in the
 * app (invoices, statements, treatment plans, and all financial/clinical
 * reports). Draws A4 pages using the native android.graphics.pdf API — no
 * extra library/dependency needed. Right-aligned Persian-first layout with
 * automatic pagination.
 *
 * Usage:
 *   PdfReportBuilder(clinicName, doctorName, "صورت‌حساب بیمار", accentColor = themeColorInt)
 *       .withParagraph("...")
 *       .withTable(columns, rows)
 *       .withSummaryLine("جمع کل", "۱٬۲۰۰٬۰۰۰ تومان", emphasize = true)
 *       .withSignatureLines()
 *       .build(context, "statement")
 */
class PdfReportBuilder(
    private val clinicName: String,
    private val doctorName: String,
    private val reportTitle: String,
    private val subtitle: String? = null,
    private val accentColor: Int = Color.rgb(15, 23, 42),
    private val themeSpec: ExportThemeSpec = ExportThemeSpec.Default
) {
    // Official printable documents use a formal, high-contrast classic navy palette,
    // independent of the app's internal UI neon/dark theme.
    private val formalNavyColor: Int = Color.rgb(15, 23, 42)
    private val formalSlateColor: Int = Color.rgb(30, 41, 59)
    private val fontScale: Float = themeSpec.fontScaleMultiplier
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f

    private var columns: List<ReportColumn>? = null
    private var rows: List<List<String>> = emptyList()
    private val summaryLines = mutableListOf<ReportSummaryLine>()
    private var showSignatureLines = false
    private var bodyParagraph: String? = null

    fun withTable(columns: List<ReportColumn>, rows: List<List<String>>) = apply {
        this.columns = columns
        this.rows = rows
    }

    fun withParagraph(text: String) = apply { bodyParagraph = text }

    fun withSummaryLine(label: String, value: String, emphasize: Boolean = false) = apply {
        summaryLines.add(ReportSummaryLine(label, value, emphasize))
    }

    fun withSignatureLines() = apply { showSignatureLines = true }

    fun build(context: Context, fileNamePrefix: String = "report"): File {
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, isFirstPage = true)

        fun finishAndStartNewPage() {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = drawHeader(canvas, isFirstPage = false)
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin - 30f) {
                finishAndStartNewPage()
            }
        }

        val titlePaint = TextPaint().apply {
            isAntiAlias = true; color = Color.BLACK; textSize = 15f; isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(reportTitle, pageWidth - margin, y, titlePaint)
        y += 20f
        subtitle?.let {
            val sp = TextPaint().apply { isAntiAlias = true; color = Color.DKGRAY; textSize = 10.5f; textAlign = Paint.Align.RIGHT }
            canvas.drawText(it, pageWidth - margin, y, sp)
            y += 18f
        }
        y += 6f

        bodyParagraph?.let { text ->
            val tp = TextPaint().apply { isAntiAlias = true; color = Color.rgb(30, 30, 30); textSize = 10.5f }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, (pageWidth - margin * 2).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(2f, 1.15f)
                .build()
            ensureSpace(layout.height.toFloat() + 12f)
            canvas.save()
            canvas.translate(margin, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 16f
        }

        val cols = columns
        if (cols != null) {
            y = drawTableHeader(canvas, cols, y)
            val rowHeight = 20f
            for (row in rows) {
                if (y + rowHeight > pageHeight - margin - 30f) {
                    finishAndStartNewPage()
                    y = drawTableHeader(canvas, cols, y)
                }
                y = drawTableRow(canvas, cols, row, y, rowHeight)
            }
            y += 12f
        }

        if (summaryLines.isNotEmpty()) {
            ensureSpace(summaryLines.size * 20f + 20f)
            val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 16f
            summaryLines.forEach { line ->
                val labelPaint = TextPaint().apply {
                    isAntiAlias = true
                    color = if (line.emphasize) formalNavyColor else Color.DKGRAY
                    textSize = (if (line.emphasize) 12.5f else 10.5f) * fontScale
                    isFakeBoldText = line.emphasize
                    textAlign = Paint.Align.RIGHT
                }
                val valuePaint = TextPaint(labelPaint).apply {
                    textAlign = Paint.Align.LEFT
                    color = if (line.emphasize) formalNavyColor else Color.rgb(20, 20, 20)
                }
                canvas.drawText(line.label, pageWidth - margin, y, labelPaint)
                canvas.drawText(line.value, margin, y, valuePaint)
                y += 20f
            }
        }

        if (showSignatureLines) {
            ensureSpace(70f)
            y += 34f
            val p = Paint().apply { color = Color.GRAY; strokeWidth = 1f }
            canvas.drawLine(margin, y, margin + 150f, y, p)
            canvas.drawLine(pageWidth - margin - 150f, y, pageWidth - margin, y, p)
            val labelPaint = TextPaint().apply { isAntiAlias = true; color = Color.GRAY; textSize = 9f; textAlign = Paint.Align.CENTER }
            canvas.drawText("امضای بیمار / مراجعه‌کننده", margin + 75f, y + 14f, labelPaint)
            canvas.drawText("مهر و امضای مطب", pageWidth - margin - 75f, y + 14f, labelPaint)
        }

        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        val dir = File(context.cacheDir, "reports")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "${fileNamePrefix}_${UUID.randomUUID().toString().take(8)}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun drawHeader(canvas: Canvas, isFirstPage: Boolean): Float {
        var y = margin + 8f
        val namePaint = TextPaint().apply {
            isAntiAlias = true; color = formalNavyColor; textSize = 14f * fontScale; isFakeBoldText = true; textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(clinicName, pageWidth - margin, y, namePaint)
        y += 16f
        val docPaint = TextPaint().apply { isAntiAlias = true; color = Color.DKGRAY; textSize = 9.5f * fontScale; textAlign = Paint.Align.RIGHT }
        canvas.drawText(doctorName, pageWidth - margin, y, docPaint)
        if (!isFirstPage) {
            val contPaint = TextPaint().apply { isAntiAlias = true; color = Color.GRAY; textSize = 9f * fontScale; textAlign = Paint.Align.LEFT }
            canvas.drawText("(ادامه)", margin, y, contPaint)
        }
        y += 14f
        val linePaint = Paint().apply { color = formalNavyColor; strokeWidth = 2f }
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 20f
        return y
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val p = TextPaint().apply { isAntiAlias = true; color = Color.GRAY; textSize = 8f; textAlign = Paint.Align.CENTER }
        canvas.drawText("صفحه $pageNumber  —  تولید شده توسط دنتال‌پرو", (pageWidth / 2).toFloat(), pageHeight - 18f, p)
    }

    private fun drawTableHeader(canvas: Canvas, cols: List<ReportColumn>, yStart: Float): Float {
        val y = yStart
        val headerBg = Paint().apply {
            color = Color.rgb(241, 245, 249) // Formal Slate Tint #F1F5F9
        }
        canvas.drawRect(margin, y - 13f, pageWidth - margin, y + 6f, headerBg)
        val totalWeight = cols.sumOf { it.weight.toDouble() }.toFloat()
        val usableWidth = pageWidth - margin * 2
        var xRight = pageWidth - margin
        val paint = TextPaint().apply { isAntiAlias = true; color = Color.rgb(15, 23, 42); textSize = 9.5f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        for (col in cols) {
            val colWidth = usableWidth * (col.weight / totalWeight)
            canvas.drawText(col.title, xRight - 4f, y, paint)
            xRight -= colWidth
        }
        val newY = y + 15f
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        canvas.drawLine(margin, newY - 11f, pageWidth - margin, newY - 11f, linePaint)
        return newY
    }

    private fun drawTableRow(canvas: Canvas, cols: List<ReportColumn>, row: List<String>, yStart: Float, rowHeight: Float): Float {
        val totalWeight = cols.sumOf { it.weight.toDouble() }.toFloat()
        val usableWidth = pageWidth - margin * 2
        var xRight = pageWidth - margin
        val paint = TextPaint().apply { isAntiAlias = true; color = Color.rgb(35, 35, 35); textSize = 9f; textAlign = Paint.Align.RIGHT }
        for ((i, col) in cols.withIndex()) {
            val colWidth = usableWidth * (col.weight / totalWeight)
            val text = row.getOrElse(i) { "" }
            canvas.drawText(truncateToWidth(text, paint, colWidth - 8f), xRight - 4f, yStart, paint)
            xRight -= colWidth
        }
        val linePaint = Paint().apply { color = Color.argb(35, 0, 0, 0); strokeWidth = 0.5f }
        canvas.drawLine(margin, yStart + 6f, pageWidth - margin, yStart + 6f, linePaint)
        return yStart + rowHeight
    }

    private fun truncateToWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}
