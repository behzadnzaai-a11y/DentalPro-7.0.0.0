package com.example.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

/**
 * مدیر بروزرسانی درون‌برنامه‌ای — نسخه 7.0.0 (نوع ۱ اجباری):
 * ۱) چک نسخه از جدول app_updates سرور (Supabase)
 * ۲) پشتیبانی دانلود مستقیم از گوگل‌درایو بدون باز کردن مرورگر
 * ۳) نمایش پیشرفت با حجم کل / دانلود شده / درصد / سرعت
 * ۴) پس از دانلود: بستن اپ و باز کردن نصب‌کننده
 */
object AppUpdateManager {

    enum class UpdateType { MAJOR, MINOR, PATCH }

    data class AppUpdateInfo(
        val versionCode: Long,
        val versionName: String,
        val apkUrl: String,
        val changelog: String,
        val isMandatory: Boolean,
        val updateType: UpdateType
    )

    /** جزئیات پیشرفت دانلود برای نمایش در UI */
    data class DownloadProgress(
        val totalBytes: Long,              // -1 اگر نامشخص
        val downloadedBytes: Long,
        val percent: Float,                // 0..1 (اگر total نامشخص، بر اساس حدس یا 0)
        val speedBytesPerSecond: Long      // سرعت لحظه‌ای
    )

    fun parseSemver(name: String): Triple<Int, Int, Int>? {
        val cleaned = name.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+")
        val parts = cleaned.split(".")
        if (parts.isEmpty()) return null
        val major = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: return null
        val minor = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    fun classifyUpdate(currentName: String, remoteName: String): UpdateType {
        val cur = parseSemver(currentName) ?: return UpdateType.MINOR
        val rem = parseSemver(remoteName) ?: return UpdateType.MINOR
        return when {
            rem.first > cur.first -> UpdateType.MAJOR
            rem.second > cur.second -> UpdateType.MINOR
            else -> UpdateType.PATCH
        }
    }

    fun currentVersionCode(context: Context): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(pi)
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    fun currentVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    suspend fun checkForUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val row = Supabase.fetchLatestAppUpdate() ?: return@withContext null
            val remoteCode = row.get("version_code")?.takeIf { !it.isJsonNull }?.asLong ?: return@withContext null
            val apkUrl = row.get("apk_url")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
            if (apkUrl.isBlank()) return@withContext null
            val current = currentVersionCode(context)
            if (remoteCode <= current) return@withContext null
            val minSupported = row.get("min_supported_version_code")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
            val remoteName = row.get("version_name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()

            val updateType = classifyUpdate(currentVersionName(context), remoteName)
            val mandatory = updateType == UpdateType.MAJOR ||
                (row.get("is_mandatory")?.takeIf { !it.isJsonNull }?.asBoolean ?: false) ||
                current < minSupported

            AppUpdateInfo(
                versionCode = remoteCode,
                versionName = remoteName,
                apkUrl = apkUrl,
                changelog = row.get("changelog")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
                isMandatory = mandatory,
                updateType = updateType
            )
        } catch (e: Exception) {
            Log.w("AppUpdateManager", "Update check failed: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------
    // Google Drive — استخراج fileId و ساخت لینک مستقیم
    // ------------------------------------------------------------------

    /**
     * استخراج fileId از انواع لینک گوگل‌درایو:
     * - https://drive.google.com/file/d/FILEID/view?usp=sharing
     * - https://drive.google.com/open?id=FILEID
     * - https://drive.google.com/uc?id=FILEID&export=download
     * - https://drive.google.com/uc?export=download&id=FILEID
     * - https://drive.usercontent.google.com/download?id=FILEID&export=download...
     */
    fun extractDriveFileId(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.contains("drive.google.com") && !trimmed.contains("drive.usercontent.google.com") && !trimmed.contains("docs.google.com")) {
            return null
        }
        // Pattern 1: /d/FILEID/
        Regex("""/d/([a-zA-Z0-9_-]{10,})""").find(trimmed)?.let {
            return it.groupValues[1]
        }
        // Pattern 2: ?id=FILEID or &id=FILEID
        Regex("""[?&]id=([a-zA-Z0-9_-]{10,})""").find(trimmed)?.let {
            return it.groupValues[1]
        }
        // Pattern 3: fallback — last segment that looks like id
        return null
    }

    fun isGoogleDriveUrl(url: String): Boolean {
        return url.contains("drive.google.com") || url.contains("drive.usercontent.google.com")
    }

    fun toDirectDriveDownloadUrl(originalUrl: String): String {
        val fileId = extractDriveFileId(originalUrl) ?: return originalUrl
        // لینک مستقیم استاندارد — برای فایل‌های کوچک همین کافی است
        return "https://drive.google.com/uc?export=download&id=$fileId"
    }

    /**
     * برای فایل‌های حجیم گوگل‌درایو صفحه اخطار ویروس‌اسکن نشان می‌دهد.
     * این تابع سعی می‌کند لینک واقعی drive.usercontent.google.com را از HTML استخراج کند.
     * اگر موفق نشود، همان لینک مستقیم اولیه را برمی‌گرداند.
     */
    private fun resolveDriveConfirmUrlSync(client: OkHttpClient, directUrl: String): String {
        try {
            val fileId = extractDriveFileId(directUrl) ?: return directUrl
            val request = Request.Builder()
                .url(directUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                // اگر ریدایرکت به drive.usercontent باشد
                val location = response.header("Location")
                if (!location.isNullOrBlank() && location.contains("drive.usercontent.google.com")) {
                    Log.d("AppUpdateManager", "Drive redirect Location: $location")
                    return location
                }
                // اگر خود response ریدایرکت شده (OkHttp followRedirects=true) و URL نهایی usercontent باشد
                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("drive.usercontent.google.com/download")) {
                    Log.d("AppUpdateManager", "Drive final URL is direct: $finalUrl")
                    return finalUrl
                }
                val body = response.body?.string() ?: return directUrl
                // اگر content-type باینری باشد (یعنی مستقیم دانلود شده)، همان directUrl کافی است
                if (!body.trimStart().startsWith("<", ignoreCase = true) && body.length > 1000) {
                    // به احتمال زیاد باینری نیست چون string کردیم، ولی اگر HTML نباشد
                    // یعنی مستقیم نیست — با این حال ما نمی‌توانیم تشخیص دهیم، پس ادامه به پارس HTML
                }
                // جستجوی لینک drive.usercontent در HTML
                // نمونه: https://drive.usercontent.google.com/download?id=FILEID&export=download&confirm=XXX&uuid=YYY
                val patterns = listOf(
                    Regex("""https://drive\.usercontent\.google\.com/download\?[^"'\s<>]+"""),
                    Regex("""https://drive\.google\.com/uc\?export=download[^"'\s<>]+confirm[^"'\s<>]+"""),
                    Regex("""href="([^"]*export=download[^"]*confirm[^"]*)""""),
                    Regex("""export=download&amp;confirm=([^&]+)&amp;id=([^&"]+)"""),
                    Regex(""""downloadUrl":"([^"]+)"""")
                )
                for (regex in patterns) {
                    val match = regex.find(body)
                    if (match != null) {
                        var url = match.groupValues.getOrNull(1) ?: match.value
                        url = url.replace("&amp;", "&")
                            .replace("\\u003d", "=")
                            .replace("\\u0026", "&")
                            .replace("\\/", "/")
                        if (url.startsWith("/")) {
                            url = "https://drive.google.com$url"
                        }
                        if (url.contains("drive.usercontent.google.com") || url.contains("export=download")) {
                            Log.d("AppUpdateManager", "Drive resolved confirm URL: $url")
                            // اطمینان از وجود id
                            if (!url.contains("id=") && fileId.isNotBlank()) {
                                url += if (url.contains("?")) "&id=$fileId" else "?id=$fileId"
                            }
                            return url
                        }
                    }
                }
                // تلاش دوم: استخراج confirm token و ساخت لینک نهایی
                val confirmRegex = Regex("""confirm=([a-zA-Z0-9_-]+)""")
                val uuidRegex = Regex("""uuid=([a-zA-Z0-9-]+)""")
                val confirmMatch = confirmRegex.find(body)
                val uuidMatch = uuidRegex.find(body)
                if (confirmMatch != null) {
                    val confirm = confirmMatch.groupValues[1]
                    val uuid = uuidMatch?.groupValues?.get(1)
                    var final = "https://drive.usercontent.google.com/download?id=$fileId&export=download&confirm=$confirm"
                    if (uuid != null) final += "&uuid=$uuid"
                    Log.d("AppUpdateManager", "Drive constructed confirm URL: $final")
                    return final
                }
                // اگر چیزی پیدا نشد، همان direct
                return directUrl
            }
        } catch (e: Exception) {
            Log.w("AppUpdateManager", "resolveDriveConfirmUrl failed: ${e.message}")
            return directUrl
        }
    }

    private fun createDriveClient(): OkHttpClient {
        // CookieJar ساده برای نگهداری NID و غیره که گوگل‌درایو نیاز دارد
        val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
        val cookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = (cookieStore[url.host] ?: mutableListOf()).apply {
                    addAll(cookies)
                }
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        }
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun getEffectiveDownloadUrl(originalUrl: String): String {
        if (!isGoogleDriveUrl(originalUrl)) return originalUrl
        val direct = toDirectDriveDownloadUrl(originalUrl)
        // تلاش برای رفع confirm token به صورت sync (در downloadApk دوباره با همین کلاینت انجام می‌شود)
        return direct
    }

    // ------------------------------------------------------------------
    // دانلود APK با گزارش پیشرفت کامل
    // ------------------------------------------------------------------

    /**
     * دانلود فایل APK نسخه جدید در cache/updates/ با گزارش پیشرفت کامل.
     * از لینک گوگل‌درایو هم پشتیبانی می‌کند (بدون باز کردن مرورگر).
     */
    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val outFile = File(dir, "dentalpro_update_v${info.versionCode}.apk")

        val originalUrl = info.apkUrl.trim()
        val isDrive = isGoogleDriveUrl(originalUrl)
        val client: OkHttpClient
        val effectiveUrl: String

        if (isDrive) {
            client = createDriveClient()
            val direct = toDirectDriveDownloadUrl(originalUrl)
            effectiveUrl = resolveDriveConfirmUrlSync(client, direct)
            Log.d("AppUpdateManager", "Drive effective URL: $effectiveUrl")
        } else {
            client = Supabase.http.newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            effectiveUrl = originalUrl
        }

        val request = Request.Builder()
            .url(effectiveUrl)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) DentalPro Updater")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("دانلود ناموفق (HTTP ${response.code}) - لینک را بررسی کنید (دسترسی 'Anyone with link' برای گوگل‌درایو لازم است)")
            }
            val body = response.body ?: throw IllegalStateException("پاسخ خالی از سرور")
            // اگر گوگل‌درایو به جای APK صفحه HTML برگرداند، خطا بده
            val contentType = body.contentType()?.toString() ?: ""
            if (contentType.contains("text/html", ignoreCase = true)) {
                // ممکن است هنوز صفحه تایید باشد — سعی کنیم طول محتوا را چک کنیم
                val preview = try {
                    body.source().peek().readString(1024, Charsets.UTF_8)
                } catch (e: Exception) { "" }
                if (preview.contains("Google Drive", ignoreCase = true) && preview.contains("Virus scan warning", ignoreCase = true) || preview.contains("export=download")) {
                    throw IllegalStateException("لینک گوگل‌درایو نیاز به تایید دارد — لطفاً مطمئن شوید فایل با دسترسی 'Anyone with link - Viewer' به اشتراک گذاشته شده و حجم آن کمتر از محدودیت اسکن نیست، یا از لینک مستقیم drive.usercontent استفاده کنید")
                }
                // اگر HTML بود ولی ما انتظار APK داریم، اگر سایز کوچک باشد خطا
                val contentLen = body.contentLength()
                if (contentLen in 0..50000) {
                    // احتمالاً صفحه خطا
                    throw IllegalStateException("پاسخ دریافتی فایل APK نیست (نوع: $contentType) — لینک دانلود را بررسی کنید")
                }
            }

            val total = body.contentLength() // ممکن است -1 باشد اگر سرور chunked بدهد
            var downloaded = 0L
            val startNs = System.nanoTime()
            var lastNs = startNs
            var lastBytes = 0L
            var lastSpeed = 0L

            body.byteStream().use { input ->
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024) // 128KB buffer برای سرعت بهتر
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val nowNs = System.nanoTime()
                        val elapsedTotalSec = (nowNs - startNs) / 1_000_000_000.0
                        val intervalSec = (nowNs - lastNs) / 1_000_000_000.0

                        // سرعت لحظه‌ای هر 0.4 ثانیه به‌روز شود
                        if (intervalSec >= 0.4) {
                            val intervalBytes = downloaded - lastBytes
                            lastSpeed = if (intervalSec > 0) (intervalBytes / intervalSec).toLong() else 0L
                            lastNs = nowNs
                            lastBytes = downloaded
                        }
                        val avgSpeed = if (elapsedTotalSec > 0) (downloaded / elapsedTotalSec).toLong() else 0L
                        val speedToReport = if (lastSpeed > 0) lastSpeed else avgSpeed
                        val percent = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

                        onProgress(
                            DownloadProgress(
                                totalBytes = total,
                                downloadedBytes = downloaded,
                                percent = percent,
                                speedBytesPerSecond = speedToReport
                            )
                        )
                    }
                }
            }
            // گزارش نهایی ۱۰۰٪
            onProgress(
                DownloadProgress(
                    totalBytes = if (total > 0) total else downloaded,
                    downloadedBytes = downloaded,
                    percent = 1f,
                    speedBytesPerSecond = 0L
                )
            )
        }

        if (!outFile.isFile || outFile.length() == 0L) {
            throw IllegalStateException("فایل دانلودشده معتبر نیست")
        }
        // حداقل ۱ مگابایت برای APK منطقی است — اگر کمتر باشد احتمالاً HTML خطا دانلود شده
        if (outFile.length() < 500_000L) {
            // چک محتوای فایل: اگر با < شروع شود HTML است
            val header = try {
                outFile.inputStream().use { it.readBytes().take(200).toByteArray().toString(Charsets.UTF_8) }
            } catch (e: Exception) { "" }
            if (header.trimStart().startsWith("<")) {
                outFile.delete()
                throw IllegalStateException("فایل دریافتی APK نیست (محتوای HTML) — لینک گوگل‌درایو را با دسترسی عمومی بررسی کنید")
            }
        }

        outFile
    }

    /**
     * نسخه سازگار با API قدیمی (Float 0..1) — برای جاهایی که هنوز callback قدیمی دارند
     */
    suspend fun downloadApk(
        context: Context,
        info: AppUpdateInfo,
        onProgressFloat: (Float) -> Unit
    ): File {
        return downloadApk(context, info) { prog ->
            onProgressFloat(prog.percent)
        }
    }

    fun canInstallPackages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true

    fun openInstallPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("AppUpdateManager", "Cannot open install permission settings: ${e.message}")
        }
    }

    /**
     * باز کردن نصب‌کننده اندروید و بستن اپ فعلی (طبق درخواست: «سپس اپ بسته و نصب بشه»)
     */
    fun installApk(context: Context, apkFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            // تلاش برای بستن اپ فعلی پس از باز کردن نصب‌کننده
            // (فعالیت فعلی finish می‌شود تا کاربر مستقیم به نصب‌کننده برود)
            if (context is Activity) {
                context.finish()
                // برای اطمینان، تسک را هم در اندروید ۵+ می‌بندیم؟ نه، فقط finish کافی است
            } else {
                // اگر context اکتیویتی نیست، سعی می‌کنیم با یک تاخیر کوتاه پروسه را ببندیم
                // تا نصب‌کننده فرصت باز شدن داشته باشد
                try {
                    // هیچ kill اجباری نمی‌کنیم — فقط لاگ
                    Log.d("AppUpdateManager", "Install intent launched, app should go background")
                } catch (e: Exception) {
                    Log.w("AppUpdateManager", "Could not close app: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AppUpdateManager", "installApk failed: ${e.message}", e)
            throw e
        }
    }

    // ------------------------------------------------------------------
    // Helper های فرمت برای UI
    // ------------------------------------------------------------------
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "۰ بایت"
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format("%.1f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format("%.0f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    fun formatBytesFa(bytes: Long, isPersian: Boolean): String {
        if (bytes <= 0) return if (isPersian) "نامشخص" else "Unknown"
        val kb = 1024L
        val mb = kb * 1024L
        val gb = mb * 1024L
        return when {
            bytes >= gb -> String.format("%.2f GB", bytes.toDouble() / gb)
            bytes >= mb -> String.format("%.1f MB", bytes.toDouble() / mb)
            bytes >= kb -> String.format("%.0f KB", bytes.toDouble() / kb)
            else -> "$bytes B"
        }
    }

    fun formatSpeed(bps: Long, isPersian: Boolean): String {
        if (bps <= 0) return if (isPersian) "—" else "—"
        val kb = 1024L
        val mb = kb * 1024L
        return when {
            bps >= mb -> String.format("%.1f MB/s", bps.toDouble() / mb)
            bps >= kb -> String.format("%.0f KB/s", bps.toDouble() / kb)
            else -> "$bps B/s"
        }
    }
}
