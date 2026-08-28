package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

// =====================================================================================
// PHOTO EDITOR — ادیتور مشترک برش/چرخش/زوم برای همه‌ی ۶ نوع تصویر برنامه.
//
// این فایل جایگزین رفتار قبلی می‌شود که تصویر خام انتخاب‌شده از دوربین/گالری را بدون هیچ
// برش یا تغییر رزولوشنی مستقیماً ذخیره می‌کرد. حالا بین «انتخاب تصویر» و «ذخیره/آپلود»، یک
// مرحله‌ی اجباریِ کادر ادیت (با کتابخانه‌ی CanHub Android-Image-Cropper) قرار می‌گیرد که:
//   ۱) کادر ثابت (دایره‌ای یا مستطیلی) با نسبت ابعاد قفل‌شده‌ی متناسب با رزولوشن مقصد نشان می‌دهد
//   ۲) امکان چرخش و زوم تصویر را می‌دهد
//   ۳) خروجی را دقیقاً با رزولوشن/فرمت/کیفیت تعیین‌شده برای همان نوع تصویر، روی دیسک محلی
//      (همان پوشه‌ی filesDir/images که بقیه‌ی برنامه و SmartImage همیشه از آن می‌خوانند) ذخیره می‌کند
//
// نکته‌ی مهم برای بهزاد: چون این محیط sandbox به Android SDK / Gradle (Maven Google) دسترسی
// شبکه ندارد، امکان کامپایل واقعی این تغییرات اینجا وجود نداشت. نام فیلدهای CropImageOptions
// (cropShape, guidelines, fixAspectRatio, aspectRatioX/Y, outputRequestWidth/Height,
// outputRequestSizeOptions, guidelinesColor, ...) بر اساس API شناخته‌شده و پایدار این کتابخانه
// نوشته شده؛ لطفاً یک‌بار Gradle sync/Build بگیر و اگر نام فیلدی در نسخه‌ی نصب‌شده فرق داشت،
// خطای کامپایلر دقیقاً همان خط را نشان می‌دهد.
// =====================================================================================

/** نوع دارایی تصویری — هرکدام مشخصات کادر/رزولوشن/فرمت خودشان را دارند. */
enum class PhotoAssetKind {
    LOGO,            // ۱) لوگوی کلینیک — دایره‌ای ۲۰۰×۲۰۰
    PATIENT_AVATAR,  // ۲) عکس پروفایل بیمار — دایره‌ای ۱۲۵×۱۲۵
    STAFF_AVATAR,    // ۳) عکس پروفایل پرسنل — دایره‌ای ۱۲۵×۱۲۵
    SIGNATURE,       // ۴) عکس امضا — PNG بدون بک‌گراند، ۹۶۰×۶۳۰
    CLINICAL_PHOTO,  // ۵) عکس قبل/بعد و بالینی — ۹۵۴×۵۸۱، کیفیت ۸۰٪
    DOCUMENT_SCAN,   // ۶) اسکن رادیوگرافی و تصاویر پرونده — ۹۳۵×۴۷۸، کیفیت ۸۰٪
    SAYAD_CHEQUE     // ۷) تصویر چک صیادی — ۱۰۷۵×۵۴۴، کیفیت ۸۰٪
}

/** مشخصات دقیق کادر ادیت + خروجی برای یک نوع تصویر. */
data class PhotoEditSpec(
    val outputWidthPx: Int,
    val outputHeightPx: Int,
    val cropShape: CropImageView.CropShape,
    val guidelines: CropImageView.Guidelines,
    val outputFormat: Bitmap.CompressFormat,
    val quality: Int,
    val transparentBackground: Boolean = false,
    /** برای ۳ مورد آخر (امضا، قبل/بعد، اسکن) نوار پیشرفت آپلود نمایش داده می‌شود. */
    val showsUploadProgress: Boolean = false
) {
    val fileExtension: String
        get() = if (outputFormat == Bitmap.CompressFormat.PNG) "png" else "jpg"
}

object PhotoEditSpecs {
    val LOGO = PhotoEditSpec(
        outputWidthPx = 200, outputHeightPx = 200,
        cropShape = CropImageView.CropShape.OVAL,
        guidelines = CropImageView.Guidelines.OFF,
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 92
    )
    val PATIENT_AVATAR = PhotoEditSpec(
        outputWidthPx = 125, outputHeightPx = 125,
        cropShape = CropImageView.CropShape.OVAL,
        guidelines = CropImageView.Guidelines.OFF,
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 92
    )
    val STAFF_AVATAR = PhotoEditSpec(
        outputWidthPx = 125, outputHeightPx = 125,
        cropShape = CropImageView.CropShape.OVAL,
        guidelines = CropImageView.Guidelines.OFF,
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 92
    )
    val SIGNATURE = PhotoEditSpec(
        outputWidthPx = 960, outputHeightPx = 630,
        cropShape = CropImageView.CropShape.RECTANGLE,
        guidelines = CropImageView.Guidelines.OFF,
        outputFormat = Bitmap.CompressFormat.PNG, quality = 100,
        transparentBackground = true,
        showsUploadProgress = true
    )
    val CLINICAL_PHOTO = PhotoEditSpec(
        outputWidthPx = 954, outputHeightPx = 581,
        cropShape = CropImageView.CropShape.RECTANGLE,
        guidelines = CropImageView.Guidelines.ON, // گرید کم‌رنگ ۹ خانه‌ای
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 80,
        showsUploadProgress = true
    )
    val DOCUMENT_SCAN = PhotoEditSpec(
        outputWidthPx = 935, outputHeightPx = 478,
        cropShape = CropImageView.CropShape.RECTANGLE,
        guidelines = CropImageView.Guidelines.ON, // گرید کم‌رنگ ۹ خانه‌ای
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 80,
        showsUploadProgress = true
    )
    val SAYAD_CHEQUE = PhotoEditSpec(
        outputWidthPx = 1075, outputHeightPx = 544,
        cropShape = CropImageView.CropShape.RECTANGLE,
        guidelines = CropImageView.Guidelines.ON,
        outputFormat = Bitmap.CompressFormat.JPEG, quality = 80,
        showsUploadProgress = true
    )

    fun forKind(kind: PhotoAssetKind): PhotoEditSpec = when (kind) {
        PhotoAssetKind.LOGO -> LOGO
        PhotoAssetKind.PATIENT_AVATAR -> PATIENT_AVATAR
        PhotoAssetKind.STAFF_AVATAR -> STAFF_AVATAR
        PhotoAssetKind.SIGNATURE -> SIGNATURE
        PhotoAssetKind.CLINICAL_PHOTO -> CLINICAL_PHOTO
        PhotoAssetKind.DOCUMENT_SCAN -> DOCUMENT_SCAN
        PhotoAssetKind.SAYAD_CHEQUE -> SAYAD_CHEQUE
    }
}

/**
 * پردازش خودکارِ یک تصویر بدون بازکردنِ ادیتورِ تعاملی — برای جریان‌های دسته‌ای (مثل آپلود
 * هم‌زمانِ چند عکس چک) که باز کردنِ کادر برش برای تک‌تکِ آن‌ها، کار را کند و خسته‌کننده می‌کند.
 * تصویر را به نسبتِ ابعاد هدف (center-crop، بدون کِش رفتنِ تصویر) برش می‌دهد، به رزولوشنِ دقیق
 * spec تغییر اندازه می‌دهد، و در همان پوشه‌ی دائمیِ محلی (filesDir/images) ذخیره می‌کند.
 */
suspend fun autoProcessImageForBatch(context: Context, uri: Uri, spec: PhotoEditSpec, kind: PhotoAssetKind): String? {
    return try {
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val targetRatio = spec.outputWidthPx.toFloat() / spec.outputHeightPx.toFloat()
        val srcRatio = decoded.width.toFloat() / decoded.height.toFloat()
        val cropped = if (srcRatio > targetRatio) {
            // منبع نسبت به هدف عریض‌تر است: از عرض بزنیم
            val newWidth = (decoded.height * targetRatio).toInt().coerceIn(1, decoded.width)
            val x = (decoded.width - newWidth) / 2
            Bitmap.createBitmap(decoded, x, 0, newWidth, decoded.height)
        } else {
            // منبع نسبت به هدف بلندتر است: از ارتفاع بزنیم
            val newHeight = (decoded.width / targetRatio).toInt().coerceIn(1, decoded.height)
            val y = (decoded.height - newHeight) / 2
            Bitmap.createBitmap(decoded, 0, y, decoded.width, newHeight)
        }
        val resized = Bitmap.createScaledBitmap(cropped, spec.outputWidthPx, spec.outputHeightPx, true)
        saveEditedBitmapToLocalFilePublic(context, resized, spec, kind)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** نسخه‌ی عمومیِ ذخیره‌سازی — تابع اصلی private است، این یک wrapper برای استفاده در جریان‌های دسته‌ای است. */
private fun saveEditedBitmapToLocalFilePublic(context: Context, bitmap: Bitmap, spec: PhotoEditSpec, kind: PhotoAssetKind): String =
    saveEditedBitmapToLocalFile(context, bitmap, spec, kind)

/**
 * [onEdited] را با مسیر فایل نهایی (محلی، در filesDir/images، آماده‌ی آپلود) صدا می‌زند.
 * خروجیِ برگشتی، تابعی است که با گرفتن Uri منبع (از دوربین یا گالری) ادیتور را باز می‌کند.
 */
@Composable
fun rememberPhotoEditLauncher(
    kind: PhotoAssetKind,
    onEdited: (String) -> Unit
): (Uri) -> Unit {
    val context = LocalContext.current
    val spec = remember(kind) { PhotoEditSpecs.forKind(kind) }
    var pendingSourceUri by remember { mutableStateOf<Uri?>(null) }

    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        val sourceUri = pendingSourceUri
        pendingSourceUri = null
        val croppedUri = if (result.isSuccessful) result.uriContent else null
        try {
            if (croppedUri != null) {
                val decoded = context.contentResolver.openInputStream(croppedUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
                if (decoded != null) {
                    val processed = if (spec.transparentBackground) {
                        removeLightBackground(decoded)
                    } else {
                        decoded
                    }
                    val savedPath = saveEditedBitmapToLocalFile(context, processed, spec, kind)
                    onEdited(savedPath)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // فایل خروجی موقتِ خودِ کتابخانه‌ی کراپ را پاک می‌کنیم (دیگر لازم نیست).
            deleteIfLocalFileUri(croppedUri)
            // فایل خامِ گرفته‌شده از دوربین (پیش از برش) را هم پاک می‌کنیم؛ فقط اگر داخل
            // cache/camera خودمان باشد (خروجی گالری را دست نمی‌زنیم، مالک آن اپ گالری است).
            try {
                sourceUri?.let { src ->
                    if (src.scheme == "file" || src.scheme == "content") {
                        val path = src.path
                        if (path != null && path.contains("${context.cacheDir.absolutePath}/camera")) {
                            File(path).takeIf { it.exists() }?.delete()
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    return { sourceUri ->
        pendingSourceUri = sourceUri
        val options = CropImageContractOptions(
            uri = sourceUri,
            cropImageOptions = CropImageOptions().apply {
                cropShape = spec.cropShape
                guidelines = spec.guidelines
                fixAspectRatio = true
                aspectRatioX = spec.outputWidthPx
                aspectRatioY = spec.outputHeightPx
                outputRequestWidth = spec.outputWidthPx
                outputRequestHeight = spec.outputHeightPx
                outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_EXACT
                outputCompressFormat = spec.outputFormat
                outputCompressQuality = spec.quality
                allowRotation = true
                allowFlipping = true
                autoZoomEnabled = true
                multiTouchEnabled = true
                guidelinesColor = android.graphics.Color.argb(90, 255, 255, 255)
            }
        )
        cropLauncher.launch(options)
    }
}

/**
 * مشابه rememberSafeCameraLauncher، با این تفاوت که به‌جای مسیر فایل، خودِ Uri دوربین
 * (FileProvider) را برمی‌گرداند — چون ادیتور کراپ به Uri نیاز دارد، نه رشته‌ی مسیر.
 */
@Composable
fun rememberEditableCameraLauncher(
    context: Context,
    onImageCaptured: (Uri) -> Unit
): () -> Unit {
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var launchAfterPermission by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingUri != null) {
            onImageCaptured(pendingUri!!)
        }
        pendingUri = null
    }

    fun launchCameraNow() {
        val (uri, _) = getTempCameraUri(context)
        pendingUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && launchAfterPermission) {
            launchCameraNow()
        }
        launchAfterPermission = false
    }

    return {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraNow()
        } else {
            launchAfterPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

/**
 * پس‌زمینه‌ی روشن (کاغذ سفید زیر امضا) را شفاف می‌کند تا فقط خط قلم/جوهر باقی بماند —
 * دقیقاً مطابق درخواست: خروجی PNG بدون بک‌گراند. لبه‌ی پیکسل‌های نزدیک به آستانه به‌صورت
 * محو (feather) شفاف می‌شوند تا لبه‌ی خط دندانه‌دار نشود. رنگ اصلی خط دست‌نخورده می‌ماند.
 */
private fun removeLightBackground(source: Bitmap, lightThreshold: Int = 225, featherRange: Int = 40): Bitmap {
    val w = source.width
    val h = source.height
    val result = source.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(w * h)
    result.getPixels(pixels, 0, w, 0, 0, w, h)
    for (i in pixels.indices) {
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        val luminance = (r * 299 + g * 587 + b * 114) / 1000
        val alpha = when {
            luminance >= lightThreshold -> 0
            luminance <= lightThreshold - featherRange -> 255
            else -> (255f * (lightThreshold - luminance) / featherRange).toInt().coerceIn(0, 255)
        }
        pixels[i] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
    result.setPixels(pixels, 0, w, 0, 0, w, h)
    return result
}

/** خروجی نهایی را در همان پوشه‌ی دائمی محلی برنامه (filesDir/images) ذخیره می‌کند. */
private fun saveEditedBitmapToLocalFile(context: Context, bitmap: Bitmap, spec: PhotoEditSpec, kind: PhotoAssetKind): String {
    val dir = File(context.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "${kind.name.lowercase()}_${UUID.randomUUID()}.${spec.fileExtension}")
    FileOutputStream(file).use { out ->
        bitmap.compress(spec.outputFormat, spec.quality, out)
    }
    return file.absolutePath
}

private fun deleteIfLocalFileUri(uri: Uri?) {
    if (uri == null) return
    try {
        if (uri.scheme == "file") {
            uri.path?.let { File(it) }?.takeIf { it.exists() }?.delete()
        }
    } catch (_: Exception) {
    }
}

/**
 * پس از اطمینان از موفقیت آپلود (fileId واقعی از سرور برگشته)، نسخه‌ی موقتِ محلیِ
 * پیش از آپلود را از دیسک پاک می‌کند — دقیقاً طبق درخواست «ذخیره در کش، پس از اطمینان از
 * آپلود حذف». امن است چون SyncEngine.uploadFile به‌صورت مستقل یک کپی دیگر از محتوای آپلودشده
 * را در filesDir/images با نامِ fileId نگه می‌دارد؛ یعنی SmartImage/دانلود مجدد هیچ‌وقت این
 * فایل موقت را کم نخواهد داشت.
 */
fun deleteLocalStagingFileQuietly(path: String?) {
    if (path.isNullOrBlank()) return
    try {
        val f = File(path)
        if (f.exists()) f.delete()
    } catch (_: Exception) {
    }
}

/** دیکد سریع و امنِ یک مسیر فایل محلی برای پیش‌نمایش (مسیر نامعتبر یا حذف‌شده => null، نه کرش). */
fun decodeLocalThumbnail(path: String?): Bitmap? {
    if (path.isNullOrBlank()) return null
    return try {
        val f = File(path)
        if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath) else null
    } catch (e: Exception) {
        null
    }
}

/** نوار پیشرفتِ ساده برای ۳ نوع سند نهایی (امضا، قبل/بعد، اسکن) هنگام ذخیره/آپلود. */
@Composable
fun DocumentUploadProgressBar(isUploading: Boolean, isPersian: Boolean) {
    if (isUploading) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPersian) "در حال ذخیره و آپلود تصویر…" else "Saving & uploading photo…",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
