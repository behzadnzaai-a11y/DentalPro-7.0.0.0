# راهنمای راه‌اندازی سوپابیس — دنتال‌پرو

این سند مرحله‌به‌مرحله‌ی فعال‌سازی بک‌اند جدید را توضیح می‌دهد. کل مهاجرت از Appwrite
به سوپابیس داخل کد انجام شده است؛ فقط باید تنظیمات سمت سرور (یک‌بار) انجام شود.

> **اطلاعات پروژه‌ی شما (از قبل موجود):**
> - Project URL: `https://osralxmrhcdvdwdcvlvg.supabase.co`
> - کلید anon (public): داخل `app/src/main/java/com/example/util/Supabase.kt` قرار دارد.
> - کلید service_role: **فقط** به‌صورت Secret در Edge Functionها استفاده می‌شود و
>   هیچ‌جا در اپ یا Git قرار ندارد. اگر نشت کرده فکر می‌کنید، از
>   Dashboard → Settings → API آن را Reset کنید.

---

## گام ۱ — اجرای اسکریپت SQL (الزامی، ۲ دقیقه)

1. وارد [supabase.com](https://supabase.com) شوید و پروژه را باز کنید.
2. از منوی چپ: **SQL Editor → New query**.
3. کل محتوای فایل واحد [`supabase/dentalpro_all_in_one.sql`](../supabase/dentalpro_all_in_one.sql)
   را کپی/پیست کنید و **Run** بزنید — همه‌چیز (اسکیمای اولیه + صف پیامک بانکی +
   سیستم بروزرسانی + ساختار درختی حساب‌ها + هات‌فیکس‌ها) در همین یک فایل است و
   اجرای دوباره‌اش هم امن است.

> فایل‌های جداگانه در `supabase/migrations/` (0001 تا 0005) فقط برای مرجع/تاریخچه
> نگه داشته شده‌اند؛ نیازی به اجرای تک‌تک آن‌ها نیست.
> برای انتشار نسخه جدید اپ از قالب‌های
> [`supabase/app_update_release_templates.sql`](../supabase/app_update_release_templates.sql)
> استفاده کنید.

این اسکریپت می‌سازد:
- **۲۸ جدول** (۲۷ جدول سینک اپ + جدول کدهای پیامکی `otp_codes`)
- **RLS روی همه‌ی جدول‌ها**: هر حساب فقط داده‌های خودش را می‌بیند
  (`owner_id = auth.uid()`) — ریشه‌ی باگ نشت داده بین حساب‌ها در نسخه‌ی قبلی خشک می‌شود.
- **دو باکت فایل**: `patient_media` و `clinic_assets` (خواندن عمومی، نوشتن فقط مالک)
- **تابع `delete_my_account()`**: حذف کامل حساب از داخل اپ (دیتا + فایل‌ها + خود حساب)
- **Realtime** روی همه‌ی جدول‌ها (برای آپدیت زنده بین دستگاه‌ها در آینده)

## گام ۲ — تنظیمات احراز هویت (Dashboard → Authentication)

### ۲-۱) آدرس‌های بازگشت (URL Configuration)
در **Authentication → URL Configuration**:
- **Site URL** را بگذارید: `https://dentalpro.app`
- در **Redirect URLs** این‌ها را اضافه کنید:
  - `dental://app`
  - `https://dentalpro.app/**`

### ۲-۲) ورود با گوگل (Providers → Google)
1. **Enable** کنید.
2. مقادیر حساب گوگل خود را وارد کنید:
   - **Client ID**: `347554951220-i40d72rf9e39sr4jt9lu3bh9un2tbu1c.apps.googleusercontent.com`
   - **Client Secret**: `GOCSPX-....` (مقدار کامل که دارید)
3. در [Google Cloud Console](https://console.cloud.google.com/apis/credentials) همان
   پروژه، مطمئن شوید:
   - Client نوع **Web** همان Client ID بالا است.
   - **Authorized redirect URI** آن روی
     `https://osralxmrhcdvdwdcvlvg.supabase.co/auth/v1/callback`
     تنظیم شده است.

> ورود گوگل با جریان **PKCE + مرورگر (Custom Tab)** پیاده شده — بدون نیاز به Google
> Play Services روی گوشی کاربر (مناسب ایران). بعد از لاگین، مرورگر با
> `dental://app?code=...` به اپ برمی‌گردد و نشست ساخته می‌شود.

### ۲-۳) تایید ایمیل (Email Provider)
- **پیشنهاد برای شروع**: در **Authentication → Providers → Email** گزینه‌ی
  **Confirm email** را **خاموش** کنید تا ثبت‌نام مستقیم وارد اپ شود.
- اگر روشن بماند هم مشکلی نیست: بعد از ثبت‌نام صفحه‌ی «در انتظار تایید ایمیل»
  نمایش داده می‌شود و کاربر بعد از کلیک روی لینک ایمیل، با دکمه‌ی
  «بررسی مجدد وضعیت» وارد می‌شود.
- **هشدار**: سرویس ایمیل پیش‌فرض سوپابیس محدودیت ارسال دارد (۳ ایمیل/ساعت در
  پلن رایگان). برای پرترافیک، Custom SMTP تنظیم کنید.

### ۲-۴) ورود با موبایل (SMS.ir)
ورود پیامکی از طریق **Edge Function**های `send-otp` و `verify-otp` انجام می‌شود
(سوپابیس خودش SMS.ir ندارد). وقتی کلید SMS.ir را گرفتید:

```bash
# نصب CLI (یک‌بار)
npm i -g supabase

# ورود
supabase login

# اتصال به پروژه (از Dashboard → Settings → General → Reference ID)
supabase link --project-ref osralxmrhcdvdwdcvlvg

# Deploy توابع (از پوشه‌ی ریشه‌ی پروژه)
supabase functions deploy send-otp
supabase functions deploy verify-otp
supabase functions deploy phone-to-email
supabase functions deploy extract-cheque-data

# تنظیم کلید SMS.ir به‌صورت Secret (هرگز داخل کد/گیت نگذارید)
supabase secrets set SMSIR_API_KEY=کلید-شما

# یکی از این دو حالت (در پنل SMS.ir):
# حالت ۱ — سرویس Verify (پیشنهادی): یک قالب بسازید با پارامتر CODE و:
supabase secrets set SMSIR_OTP_TEMPLATE_ID=شناسه-قالب
# حالت ۲ — ارسال با شماره خط:
supabase secrets set SMSIR_LINE_NUMBER=3000748xxxx
```

تا وقتی این توابع Deploy نشده باشند:
- ورود پیامکی پیام «توابع سرور هنوز Deploy نشده‌اند» می‌دهد.
- ورود با **موبایل + رمز** (برای حساب‌های ثبت‌نام‌شده با ایمیل) کار نمی‌کند؛
  ورود با ایمیل کاملاً مستقل و فعال است.

## گام ۳ — استخراج چک با هوش مصنوعی (Gemini روی سرور)

اپ **دیگر هیچ کلید API نگه نمی‌دارد و ارسال نمی‌کند**. کلید Gemini/Groq به‌صورت
Secret روی سرور سوپابیس ذخیره می‌شود و تابع `extract-cheque-data` با **IP سرور**
(خارج از تحریم) فراخوانی می‌کند:

```bash
supabase functions deploy extract-cheque-data

# کلید Gemini خودتان (از aistudio.google.com/apikey) — هرگز داخل اپ/گیت نگذارید:
supabase secrets set GEMINI_API_KEY=AIza...

# اختیاری: چند کلید برای اطمینان (با کاما جدا می‌شوند) و/یا گروک:
# supabase secrets set GEMINI_API_KEYS=کلید۱,کلید۲
# supabase secrets set GROQ_API_KEY=gsk_...

# تست سریع:
curl -X POST "https://osralxmrhcdvdwdcvlvg.supabase.co/functions/v1/extract-cheque-data" \
  -H "Authorization: Bearer کلید-anon" -H "Content-Type: application/json" \
  -d '{"imageBase64":"<base64-کوتاه>","prompt":"test","provider":"gemini"}'
```

انتخاب «ارائه‌دهنده» و «مدل» همچنان از داخل اپ (تنظیمات) قابل تغییر است؛ فقط
کلیدها سمت سرورند. اگر کلید ست نشده باشد، اپ پیام «کلید Gemini روی سرور تنظیم
نشده است» می‌دهد.

## گام ۴ — بیلد و تست

```bash
./gradlew testDebugUnitTest assembleDebug
```

پس از نصب روی گوشی:
1. **ثبت‌نام** با ایمیل/رمز (یا گوگل) → تکمیل پروفایل → ورود.
2. در **تنظیمات → عیب‌یابی ابری**، دوئو را ببینید: هر ۵ مورد باید سبز باشد
   (شبکه، پینگ سرور، نشست، RLS جدول‌ها، باکت فایل).
3. داده‌ای که ثبت می‌کنید باید در Dashboard → Table Editor ظاهر شود و در دستگاه
   دوم با همان حساب، بعد از لاگین پایین بیاید.

---

## معماری سینک (خلاصه برای مراجعه)

| موضوع | Appwrite (قبل) | سوپابیس (حالا) |
|---|---|---|
| احراز هویت | SDK Appwrite (Custom Tab + CallbackActivity) | REST مستقیم GoTrue + PKCE |
| داده | Databases/Collections | PostgREST + Postgres (جدول‌های snake_case) |
| ایزوله‌سازی | Permission سطح سند (باز!) | **RLS**: `owner_id = auth.uid()` |
| فایل | باکت `patient_media` با fileId | باکت‌های `patient_media`/`clinic_assets` با مسیر `{uid}/{uuid}.jpg` |
| صف آفلاین | PendingSyncOp (Room) | همان — دست‌نخورده |
| تعیین هویت بین‌دستگاهی | `syncId` (UUID) | همان `sync_id` — حالا PRIMARY KEY واقعی |
| حذف حساب | پیمایش ۲۷ کالکشن از کلاینت | RPC اتمیک `delete_my_account()` |
| ورود موبایل | تابع Appwrite + SMS داخلی | Edge Function + SMS.ir |
| استخراج چک | تابع Appwrite | Edge Function `extract-cheque-data` |
| تبدیل نام فیلدها | camelCase | camelCase (Room) ↔ snake_case (Postgres) در `Supabase.kt` |

نکته‌های امنیتی پیاده‌شده:
- `cvv2` کارت‌بانکی، مسیرهای محلی (`photoLocalPath`, `uri`, `localCachePath`,
  `documentUris`) هرگز سینک نمی‌شوند (EntityMapper — مثل قبل).
- کلید service_role فقط در Secretهای Edge Function است؛ اپ فقط کلید anon دارد که
  با RLS محافظت می‌شود.
- نشست‌ها در SharedPreferences خصوصی اپ ذخیره و با refresh token به‌صورت خودکار تمدید
  می‌شوند.

## اشتراک داده بین پزشک و منشی (نکته)

در این نسخه، RLS «هر حساب فقط داده‌های خودش» است. اگر بخواهید دو حساب (پزشک +
منشی) روی **یک مطب مشترک** کار کنند، در SQL می‌توان Policyها را به حالت اشتراکی
تغییر داد؛ این قابلیت به‌صورت اختیاری در نسخه‌های بعد اضافه می‌شود.
