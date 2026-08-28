package com.example.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Converts every PDF page to a separate PNG without truncating long reports. */
object PdfRendererHelper {

    fun renderPdfToImageCards(
        context: Context,
        pdfFile: File,
        themeSpec: ExportThemeSpec = ExportThemeSpec.Default
    ): List<File> {
        require(pdfFile.isFile && pdfFile.canRead()) { "PDF is missing or unreadable" }
        val outDir = File(context.cacheDir, "reports_images").apply { mkdirs() }
        val operationId = UUID.randomUUID().toString().take(8)
        val result = mutableListOf<File>()

        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount > 0) { "PDF contains no pages" }
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            // Keep one page in memory at a time. Two-times rendering remains readable
                            // while avoiding the unbounded stitched bitmap used previously.
                            val width = (page.width * 2).coerceAtLeast(1000)
                            val height = (page.height * 2).coerceAtLeast(1400)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val outFile = File(
                                    outDir,
                                    "report_${operationId}_page_${(index + 1).toString().padStart(3, '0')}.png"
                                )
                                FileOutputStream(outFile).use { output ->
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                                        "Could not encode PDF page ${index + 1}"
                                    }
                                }
                                result += outFile
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            return result
        } catch (error: Exception) {
            result.forEach(File::delete)
            throw error
        }
    }
}
