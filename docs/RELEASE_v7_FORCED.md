# انتشار نسخه 7.0.0 — آپدیت اجباری نوع ۱ (MAJOR)

## خلاصه
- **نسخه قبلی:** 6.3.1 (VERSION_CODE=60301)
- **نسخه جدید:** 7.0.0 (VERSION_CODE=70000)
- **نوع آپدیت:** MAJOR → نوع ۱ → اجباری خودکار
- **منطق اجباری:** در `AppUpdateManager.classifyUpdate()` هر MAJOR اجباری است. علاوه بر آن `is_mandatory=true` و `min_supported_version_code=70000` در جدول سرور ست شده.
- **منبع APK:** گوگل‌درایو — دانلود مستقیم بدون باز کردن مرورگر
- **ویژگی جدید دیالوگ:** نمایش حجم کل، حجم دانلود شده، درصد، سرعت دانلود + بستن خودکار اپ و باز کردن نصب‌کننده

---

## الف) تغییرات کد

### 1. شماره نسخه
**`gradle.properties`:**
```properties
VERSION_CODE=70000
VERSION_NAME=7.0.0
```

**`app/build.gradle.kts`:**
```kotlin
versionCode = providers.gradleProperty("VERSION_CODE").getOrElse("70000").toInt()
versionName = providers.gradleProperty("VERSION_NAME").getOrElse("7.0.0")
```

### 2. منطق دانلود گوگل‌درایو (`AppUpdateManager.kt`)
- `extractDriveFileId(url)` — استخراج fileId از انواع لینک:
  - `/file/d/FILEID/view`
  - `open?id=FILEID`
  - `uc?id=FILEID`
  - `uc?export=download&id=FILEID`
  - `drive.usercontent.google.com/download?id=FILEID`
- `toDirectDriveDownloadUrl()` — تبدیل به `https://drive.google.com/uc?export=download&id=FILEID`
- `resolveDriveConfirmUrlSync()` — حل صفحه اخطار ویروس‌اسکن گوگل‌درایو (استخراج `drive.usercontent.google.com/download?...confirm=TOKEN&uuid=...` از HTML)
- `DownloadProgress` — data class جدید:
  ```kotlin
  data class DownloadProgress(
      val totalBytes: Long,
      val downloadedBytes: Long,
      val percent: Float,
      val speedBytesPerSecond: Long
  )
  ```
- `downloadApk(context, info, onProgress: (DownloadProgress)->Unit)` — دانلود با بافر 128KB، محاسبه سرعت لحظه‌ای هر 0.4 ثانیه، گزارش درصد
- `installApk()` — پس از باز کردن نصب‌کننده، اگر context از نوع Activity باشد `finish()` می‌شود تا اپ بسته شود (طبق درخواست: «سپس اپ بسته و نصب بشه»)

### 3. دیالوگ آپدیت (`AppUpdateDialog.kt`)
- حذف دکمه «بعداً» برای `isMandatory=true` (از قبل بود، تایید شد)
- `onDismissRequest = { if (!isMandatory && !downloading) onDismiss() }` — دیالوگ اجباری قابل بستن نیست
- نمایش:
  - `LinearProgressIndicator` با درصد
  - ردیف «حجم کل: X MB» و «دانلود شده: Y MB»
  - ردیف «سرعت: Z MB/s» + «بدون باز کردن مرورگر»
  - متن توضیحی برای گوگل‌درایو
  - پس از اتمام: نمایش حجم نهایی + راهنمای نصب
- دکمه‌ها:
  - «دانلود و نصب» → در حین دانلود «در حال دانلود... N%» → پس از اتمام «نصب (بستن اپ)»

### 4. جدول سرور — بدون تغییر اسکیما
جدول `app_updates` از مایگریشن `0003_app_updates.sql` قبلاً ساخته شده:
```sql
version_code bigint not null,
version_name text,
apk_url text not null,
changelog text,
is_mandatory boolean default false,
is_active boolean default true,
min_supported_version_code bigint default 0
```
هیچ ستون جدیدی لازم نیست — فقط رکورد جدید درج می‌شود.

---

## ب) کد SQL سرور

فایل آماده: `supabase/migrations/0006_release_v7_forced.sql`

```sql
delete from public.app_updates where version_code = 70000;

insert into public.app_updates (
    version_code, version_name, apk_url, changelog,
    is_mandatory, is_active, min_supported_version_code
) values (
    70000,
    '7.0.0',
    'https://drive.google.com/file/d/1YOUR_FILE_ID_HERE/view?usp=sharing',
    '...چنجلاگ ۲۲ آیتم...',
    true, true, 70000
);
```

### مراحل انتشار در گوگل‌درایو:
1. بیلد release بگیرید:
   ```bash
   ./gradlew assembleRelease -PRELEASE_APPROVED=true -PVERSION_CODE=70000 -PVERSION_NAME=7.0.0
   ```
   خروجی: `app/build/outputs/apk/release/app-release.apk` → تغییر نام به `dentalpro-7.0.0.apk`
2. آپلود در گوگل‌درایو
3. کلیک راست → Share → General access = **Anyone with the link** → Viewer
4. لینک را کپی کنید (مثلاً `https://drive.google.com/file/d/1AbC.../view?usp=sharing`)
5. در SQL بالا `apk_url` را جایگزین کنید
6. SQL را در Supabase → SQL Editor اجرا کنید
7. تست: اپ نسخه قدیمی (6.3.1) را باز کنید — باید دیالوگ اجباری نوع ۱ با نوار پیشرفت ظاهر شود

### نکات امنیتی گوگل‌درایو:
- حتماً دسترسی `Anyone with the link` باشد، وگرنه دانلود 403 می‌دهد
- برای فایل‌های >100MB گوگل‌درایو صفحه «Virus scan warning» نشان می‌دهد — کد جدید این را خودکار حل می‌کند (استخراج confirm token)
- اگر باز هم خطا داد، از لینک مستقیم `https://drive.usercontent.google.com/download?id=FILEID&export=download` استفاده کنید

---

## ج) بررسی منطق اجباری بودن

| شرط | نتیجه |
|-----|-------|
| `rem.first > cur.first` (6→7) | `UpdateType.MAJOR` → `isMandatory=true` خودکار |
| `row.is_mandatory = true` | اجباری |
| `current < min_supported_version_code` (هر نسخه <70000) | اجباری |
| `AppUpdateDialog` | `dismissButton` فقط اگر `!isMandatory` نمایش داده می‌شود |
| `onDismissRequest` | برای اجباری غیرقابل بستن |

پس **سه لایه تضمین اجباری** وجود دارد.

---

## د) چنجلاگ کامل 7.0.0 (۲۲ آیتم)

1. ثبت هوشمند دستی چک‌ها — دیالوگ جدید با اسکن بارکد صیادی
2. حذف فیلدهای اضافی در ثبت چک
3. تابع مشترک `commitChequePaymentFromForm`
4. چیدمان کارت‌های داشبورد قابل مرتب‌سازی
5. کارت تنظیمات چیدمان داشبورد
6. لابراتوار — بازگشت رفتار درست انتخاب بیمار
7. لابراتوار — روزهای تحویل قابل ویرایش
8. لابراتوار — حفظ آخرین قیمت واحد
9. صورتحساب طرفحساب — فیلتر بازه زمانی
10. صورتحساب — تعداد فاکتور آخر + اشتراک واتساپ
11. عنوان صورتحساب اصلاح شد
12. فروشندگان — مانده حساب + جزئیات
13. فروشندگان — ثبت پرداخت
14. تعمیرات — نام تکنسین + شماره فاکتور
15. پرسنل فعال — منبع واحد staff_members
16. آواتار حساب کاربری
17. هویت مطب — لوگوی مطب
18. منوی مخفی تنظیمات
19. لینک لوکیشن (بلد/گوگلمپ/نشان)
20. صف ارسال/دریافت
21. دانلود مستقیم از گوگل‌درایو
22. دیالوگ آپدیت با حجم کل/دانلود شده/درصد/سرعت + بستن خودکار

---

## ه) تست دستی بدون بیلد

- چک ساختاری آکولاد: `AppUpdateManager.kt` 99/99، `AppUpdateDialog.kt` 78/78 → OK
- `fetchLatestAppUpdate()` بدون توکن کار می‌کند (policy public read)
- `downloadApk` هم از Supabase Storage و هم از Google Drive پشتیبانی می‌کند
