package com.example.reports

import android.content.Context
import android.graphics.*
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object UnifiedImageGenerator {

    data class UnifiedDocumentData(
        val title: String,
        val subtitle: String? = null,
        val items: List<Pair<String, String>>, // Label to Value
        val footerText: String? = null,
        val topIconResId: Int? = null,
        val bottomSignatureB64: String? = null
    )

    fun generateUnifiedImage(
        context: Context,
        data: UnifiedDocumentData,
        themeSpec: ExportThemeSpec,
        clinicName: String,
        doctorName: String
    ): File {
        val width = 1080
        val sidePadding = 60
        val fontScale = themeSpec.fontScaleMultiplier

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 56f * fontScale
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeSpec.primaryArgb
            textSize = 34f * fontScale
            textAlign = Paint.Align.RIGHT
        }
        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 30f * fontScale
            textAlign = Paint.Align.RIGHT
        }
        val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 34f * fontScale
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val clinicPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = themeSpec.primaryArgb
            textSize = 38f * fontScale
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        val docPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 26f * fontScale
            textAlign = Paint.Align.RIGHT
        }

        // Layout measurements
        var currentY = 80f
        
        // Header
        currentY += 50f // Clinic Name
        currentY += 40f // Doc name
        currentY += 60f // Spacing
        
        // Title
        currentY += 60f
        if (data.subtitle != null) currentY += 50f
        currentY += 40f // divider
        
        // Items
        val textWidth = width - (sidePadding * 2)
        val alignment = android.text.Layout.Alignment.ALIGN_NORMAL
        
        val valueLayouts = data.items.map { pair ->
            StaticLayout.Builder.obtain(pair.second, 0, pair.second.length, valuePaint, textWidth - 300)
                .setAlignment(alignment)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
                .build()
        }
        
        for (layout in valueLayouts) {
            currentY += maxOf(layout.height.toFloat(), 50f) + 40f
        }
        
        currentY += 60f // footer spacing
        if (data.footerText != null) {
            currentY += 80f
        }
        
        var sigBitmap: Bitmap? = null
        var sigHeight = 0
        if (!data.bottomSignatureB64.isNullOrEmpty()) {
            try {
                val decodedString = android.util.Base64.decode(data.bottomSignatureB64, android.util.Base64.DEFAULT)
                sigBitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                sigHeight = (sigBitmap.height * (textWidth.toFloat() / 2 / sigBitmap.width)).toInt()
                currentY += sigHeight + 40f
            } catch (e: Exception) {}
        }

        val height = currentY.toInt() + 100
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Draw background
        canvas.drawColor(themeSpec.backgroundArgb)
        
        // Top accent band
        val bandPaint = Paint().apply { color = themeSpec.primaryArgb; alpha = 235 }
        canvas.drawRect(0f, 0f, width.toFloat(), 18f, bandPaint)
        
        // Draw Header
        var drawY = 100f
        canvas.drawText(clinicName, width - sidePadding.toFloat(), drawY, clinicPaint)
        drawY += 45f
        canvas.drawText(doctorName, width - sidePadding.toFloat(), drawY, docPaint)
        
        // Draw optional icon
        if (data.topIconResId != null) {
            val icon = BitmapFactory.decodeResource(context.resources, data.topIconResId)
            if (icon != null) {
                val destRect = RectF(sidePadding.toFloat(), 60f, sidePadding.toFloat() + 120f, 60f + 120f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.colorFilter = PorterDuffColorFilter(themeSpec.primaryArgb, PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(icon, null, destRect, paint)
            }
        }

        drawY += 80f
        
        // Draw Title
        canvas.drawText(data.title, width - sidePadding.toFloat(), drawY, titlePaint)
        drawY += 50f
        if (data.subtitle != null) {
            canvas.drawText(data.subtitle, width - sidePadding.toFloat(), drawY, subtitlePaint)
            drawY += 30f
        }
        
        // Divider
        val dividerPaint = Paint().apply { color = themeSpec.primaryArgb; alpha = 150; strokeWidth = 2f }
        canvas.drawLine(sidePadding.toFloat(), drawY, width - sidePadding.toFloat(), drawY, dividerPaint)
        drawY += 60f
        
        // Draw Items
        val rowBgAlt = Paint().apply { color = Color.WHITE; alpha = 12 }
        data.items.forEachIndexed { index, pair ->
            val layout = valueLayouts[index]
            val rowHeight = maxOf(layout.height.toFloat(), 50f)
            
            if (index % 2 == 1) {
                canvas.drawRect(sidePadding.toFloat(), drawY - 40f, width - sidePadding.toFloat(), drawY + rowHeight, rowBgAlt)
            }
            
            // Label on the right
            canvas.drawText(pair.first, width - sidePadding.toFloat(), drawY, labelPaint)
            
            // Value on the left (RTL aligned)
            canvas.save()
            canvas.translate(sidePadding.toFloat(), drawY - 40f)
            layout.draw(canvas)
            canvas.restore()
            
            drawY += rowHeight + 40f
        }
        
        drawY += 20f
        canvas.drawLine(sidePadding.toFloat(), drawY, width - sidePadding.toFloat(), drawY, dividerPaint)
        drawY += 60f
        
        // Footer text
        if (data.footerText != null) {
            val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 24f * fontScale
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(data.footerText, width / 2f, drawY, footPaint)
            drawY += 60f
        }
        
        // Signature
        if (sigBitmap != null) {
            val destRect = RectF(sidePadding.toFloat(), drawY, sidePadding.toFloat() + (textWidth / 2f), drawY + sigHeight)
            canvas.drawBitmap(sigBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))
        }

        // Save
        val sharedDir = File(context.cacheDir, "shared")
        if (!sharedDir.exists()) sharedDir.mkdirs()
        val file = File(sharedDir, "unified_doc_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
        return file
    }
}