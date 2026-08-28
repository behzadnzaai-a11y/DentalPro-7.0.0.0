package com.example.reports

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.PersonalSubAccount
import com.example.data.TreatmentTemplate
import java.io.File
import java.io.FileOutputStream

// -----------------------------------------------------------------------
// CSV سبک، سازگار با اکسل ویندوز/تبلت — هر فیلد داخل گیومه، ممیزها escape
// می‌شوند، و یک UTF-8 BOM اول فایل قرار می‌گیرد تا اکسل حروف فارسی را
// درست بخواند و هنگام باز/ذخیره دوباره در ویندوز بهم نریزد.
// -----------------------------------------------------------------------

private fun csvEscape(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

/** Robust CSV line parser handling quoted fields (with embedded commas/newlines already split by caller). */
private fun parseCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { result.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    result.add(sb.toString())
    return result
}

/** Splits full CSV text into logical rows, respecting quoted newlines. */
private fun splitCsvRows(text: String): List<String> {
    val rows = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '"') inQuotes = !inQuotes
        if ((c == '\n') && !inQuotes) {
            rows.add(sb.toString())
            sb.clear()
        } else if (c != '\r') {
            sb.append(c)
        }
        i++
    }
    if (sb.isNotBlank()) rows.add(sb.toString())
    return rows
}

private fun writeAndShareCsv(context: Context, fileName: String, csvBody: String) {
    try {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            out.write(0xEF); out.write(0xBB); out.write(0xBF) // UTF-8 BOM برای اکسل
            out.write(csvBody.toByteArray(Charsets.UTF_8))
        }
        val uri = fileProviderUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "خروجی CSV").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "خطا در ایجاد یا اشتراک‌گذاری فایل CSV: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

private fun readCsvFromUri(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            var bytes = input.readBytes()
            // حذف UTF-8 BOM در صورت وجود (فایل‌های ذخیره‌شده از اکسل ویندوز معمولاً BOM دارند)
            if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                bytes = bytes.copyOfRange(3, bytes.size)
            }
            String(bytes, Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }
}

// ---------------- Treatment Templates ----------------

fun exportTemplatesToCsv(context: Context, templates: List<TreatmentTemplate>) {
    val header = listOf("title", "titleFa", "defaultCost", "defaultNotes", "stages", "isStarred").joinToString(",") { csvEscape(it) }
    val rows = templates.joinToString("\n") { t ->
        listOf(t.title, t.titleFa, t.defaultCost.toString(), t.defaultNotes, t.stages, t.isStarred.toString())
            .joinToString(",") { csvEscape(it) }
    }
    writeAndShareCsv(context, "الگوهای_درمان.csv", "$header\n$rows")
}

fun readTemplatesFromCsvUri(context: Context, uri: Uri): List<TreatmentTemplate>? {
    val text = readCsvFromUri(context, uri) ?: return null
    val rows = splitCsvRows(text)
    if (rows.size < 2) return null
    val header = parseCsvLine(rows[0]).map { it.trim().lowercase() }
    val titleIdx = header.indexOf("title")
    val titleFaIdx = header.indexOf("titlefa")
    val costIdx = header.indexOf("defaultcost")
    val notesIdx = header.indexOf("defaultnotes")
    val stagesIdx = header.indexOf("stages")
    val starredIdx = header.indexOf("isstarred")
    if (titleIdx == -1) return null
    return rows.drop(1).mapNotNull { row ->
        val cols = parseCsvLine(row)
        val title = cols.getOrNull(titleIdx)?.trim().orEmpty()
        if (title.isBlank()) return@mapNotNull null
        TreatmentTemplate(
            title = title,
            titleFa = if (titleFaIdx >= 0) cols.getOrNull(titleFaIdx)?.trim().orEmpty() else "",
            defaultCost = cols.getOrNull(costIdx)?.trim()?.toLongOrNull() ?: 0L,
            defaultNotes = if (notesIdx >= 0) cols.getOrNull(notesIdx).orEmpty() else "",
            stages = if (stagesIdx >= 0) cols.getOrNull(stagesIdx).orEmpty() else "",
            isStarred = if (starredIdx >= 0) (cols.getOrNull(starredIdx)?.trim()?.equals("true", ignoreCase = true) ?: true) else true
        )
    }
}

// ---------------- Personal Sub-Accounts ----------------

fun exportSubAccountsToCsv(context: Context, subAccounts: List<PersonalSubAccount>) {
    val header = listOf("name", "category", "ledgerSide").joinToString(",") { csvEscape(it) }
    val rows = subAccounts.joinToString("\n") { s ->
        listOf(s.name, s.category, s.ledgerSide).joinToString(",") { csvEscape(it) }
    }
    writeAndShareCsv(context, "سرفصل_های_شخصی.csv", "$header\n$rows")
}

fun readSubAccountsFromCsvUri(context: Context, uri: Uri): List<PersonalSubAccount>? {
    val text = readCsvFromUri(context, uri) ?: return null
    val rows = splitCsvRows(text)
    if (rows.size < 2) return null
    val header = parseCsvLine(rows[0]).map { it.trim().lowercase() }
    val nameIdx = header.indexOf("name")
    val categoryIdx = header.indexOf("category")
    val ledgerSideIdx = header.indexOfFirst { it == "ledgerside" || it == "ledger_side" || it == "ledger" }
    if (nameIdx == -1) return null
    return rows.drop(1).mapNotNull { row ->
        val cols = parseCsvLine(row)
        val name = cols.getOrNull(nameIdx)?.trim().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        val ledger = if (ledgerSideIdx >= 0) cols.getOrNull(ledgerSideIdx)?.trim().takeUnless { it.isNullOrBlank() } ?: "NONE" else "NONE"
        PersonalSubAccount(
            name = name,
            category = if (categoryIdx >= 0) cols.getOrNull(categoryIdx)?.trim().takeUnless { it.isNullOrBlank() } ?: "EXPENSE" else "EXPENSE",
            ledgerSide = ledger
        )
    }
}
