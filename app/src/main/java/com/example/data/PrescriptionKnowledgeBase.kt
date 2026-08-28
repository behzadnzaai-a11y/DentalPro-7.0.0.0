package com.example.data

/**
 * پایگاه‌دانش نسخه‌نویسی پس از جلسات درمانی دندانپزشکی.
 *
 * این پایگاه‌دانش نسخه اولیه‌ای که در فایل «نسخه‌ها.txt» ارسال شده بود را جایگزین می‌کند و
 * بر اساس مهم‌ترین منابع دانشگاهی و معتبر و به‌روز تا سال ۲۰۲۶ بازبینی، اصلاح و تکمیل شده است:
 *  - ADA Center for Evidence-Based Dentistry — Antibiotic Use for Urgent Pulpal/Periapical
 *    Pain & Swelling (Clinical Practice Guideline، همچنان معتبر و مرجع اصلی تا ۲۰۲۶)
 *  - AHA 2021 Scientific Statement on Prevention of Viridans Group Streptococcal Infective
 *    Endocarditis (به‌روزرسانی پروفیلاکسی آندوکاردیت)
 *  - ADA Antibiotic Stewardship / Antibiotic Prophylaxis Chairside Guide
 *  - AAOS/ADA — بازنگری نوامبر ۲۰۲۴: پروفیلاکسی معمول برای بیماران با پروتز مفصلی دیگر
 *    توصیه نمی‌شود مگر با صلاحدید جراح ارتوپد
 *  - AAOMS Third Molar/Office-Based Surgery guidance و ITI Treatment Guide (ایمپلنت)
 *
 * مهم‌ترین اصلاحات نسبت به فایل ارسالی «نسخه‌ها.txt»:
 *  1) کلیندامایسین (Cl) دیگر جایگزین اول آلرژی به پنی‌سیلین نیست. طبق AHA 2021 و ADA،
 *     کلیندامایسین به‌طور کامل از پروفیلاکسی آندوکاردیت حذف شده (خطر بالای C. difficile) و
 *     برای درمان عفونت‌های ادونتوژنیک نیز به‌عنوان آخرین خط (نه خط اول) نگه داشته شده است.
 *     جایگزین‌های ارجح: سفالکسین (Cfx) برای آلرژی خفیف بدون سابقه آنافیلاکسی، و
 *     آزیترومایسین (Azi) برای آلرژی شدید/آنافیلاکسی.
 *  2) در عدم پاسخ به خط اول (آموکسی‌سیلین)، افزودن مترونیدازول یا تعویض با
 *     آموکسی‌سیلین-کلاوولانیک به‌عنوان خط دوم رسمی ADA درج شده است.
 *  3) یادآوری «قطع آنتی‌بیوتیک تا ۲۴ ساعت پس از رفع کامل علائم، صرف‌نظر از دوره ویزیت مجدد»
 *     طبق ADA CPG به تمام ردیف‌های آنتی‌بیوتیک‌دار افزوده شده است.
 *  4) هشدار AAOS نوامبر ۲۰۲۴ درباره عدم نیاز به پروفیلاکسی معمول در بیماران با پروتز مفصلی
 *     (implant / بازسازی استخوان) اضافه شده است.
 *  5) پروفیلاکسی آندوکاردیت (کاشت ایمپلنت/جراحی‌های وسیع در بیماران پرخطر قلبی) نیز مطابق
 *     AHA 2021 اصلاح شد: آموکسی‌سیلین ۲ گرم؛ در آلرژی: سفالکسین ۲ گرم یا آزیترومایسین/
 *     کلاریترومایسین ۵۰۰ میلی‌گرم یا داکسی‌سایکلین ۱۰۰ میلی‌گرم — بدون کلیندامایسین.
 *
 * اختصارات: Ib=ایبوپروفن | Ac=استامینوفن | Am=آموکسی‌سیلین | AmCl=آموکسی‌سیلین-کلاوولانیک |
 * Cfx=سفالکسین | Azi=آزیترومایسین | Mtz=مترونیدازول | Doxy=داکسی‌سیکلین |
 * CHX=کلرهگزیدین ۰٫۱۲-۰٫۲٪ | Dex=دگزامتازون
 */

/** یک نسخه دارویی ساختاریافته برای یک جلسه درمانی مشخص — قابل ویرایش توسط پزشک پیش از تایید. */
data class DrugPrescription(
    val analgesic: String = "",   // مسکن (دوز)
    val antibiotic: String = "",  // آنتی‌بیوتیک (دوز)
    val mouthwash: String = "",   // دهان‌شویه
    val otherMeds: String = "",   // سایر داروها
    val notes: String = "",       // نکات مهم (موارد منع مصرف و تداخلات - داخلی مطب)
    val publicNote: String = "لطفاً داروها را سر ساعت و طبق دستور با آب کافی میل کنید. در صورت بروز هرگونه حساسیت پوستی یا گوارشی، با مطب تماس بگیرید.", // دستورات متفرقه و نکات مهم (خطاب به بیمار)
    val durationDays: Int = 5     // حداقل طول درمان
) {
    fun isBlank(): Boolean = analgesic.isBlank() && antibiotic.isBlank() && mouthwash.isBlank() && otherMeds.isBlank() && notes.isBlank()
}

/** یک ردیف پایگاه‌دانش: کلیدواژه‌های تشخیص درمان + نسخه جلسه ابتدایی/جراحی و نسخه جلسات پیگیری. */
private data class RxProfile(
    val key: String,
    val matchKeywords: List<String>,
    val initial: DrugPrescription,
    val followUp: DrugPrescription = DrugPrescription(
        notes = "معمولاً نیازی به داروی جدید نیست؛ در صورت ناراحتی خفیف: استامینوفن ۵۰۰ میلی‌گرم هر ۶ ساعت در صورت نیاز.",
        publicNote = "در صورت احساس ناآرامی یا درد خفیف، می‌توانید طبق دستور پزشک مسکن استفاده کنید."
    )
)

/** کلیدواژه‌هایی که نشان‌دهنده جلسه پیگیری/کشیدن بخیه/تحویل نهایی هستند (نسخه سبک‌تر). */
private val followUpStageKeywords = listOf(
    "کشیدن بخیه", "کشیدن بخیه‌ها", "بخیه‌ها", "پیگیری", "کنترل التیام", "تحویل نهایی",
    "چسباندن نهایی", "سمانتاسیون", "سمنتاسیون", "تست فریم", "تست بیسکویتی", "معاینه",
    "جلسه ۲", "جلسه ۳", "جلسه ۴", "جلسه ۵", "تکمیل درمان", "اُبچوریشن مجدد"
)

object PrescriptionKnowledgeBase {

    private val profiles: List<RxProfile> = listOf(
        RxProfile(
            key = "scaling",
            matchKeywords = listOf("جرم‌گیری و بروساژ", "جرم گیری", "scaling", "پروفیلاکسی دهانی"),
            initial = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت حساسیت یا درد لثه - ۳",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - حداقل ۳۰ دقیقه فاصله با خمیردندان - ۷",
                notes = "Ac: در نارسایی کبدی و الکلیسم ممنوع. CHX: حداقل ۳۰ دقیقه فاصله با مسواک/خمیردندان تا رنگ‌گرفتگی دندان کاهش یابد؛ مصرف طولانی‌مدت (بیش از ۲ هفته) توصیه نمی‌شود."
            )
        ),
        RxProfile(
            key = "srp",
            matchKeywords = listOf("جرم‌گیری عمیق", "تسطیح سطح ریشه", "srp", "root planing"),
            initial = DrugPrescription(
                analgesic = "قرص ایبوپروفن ۴۰۰ - هر ۸ ساعت ۱ عدد - همراه غذا - در صورت درد لثه - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب کامل - ۵",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۲ هفته - ۱۴",
                notes = "Ib: نارسایی کلیوی، زخم گوارشی فعال، سه‌ماهه سوم بارداری ممنوع؛ با وارفارین/متوترکسات تداخل خونریزی‌دهنده دارد (Ac ارجح است). Am: در حساسیت به پنی‌سیلین ممنوع. کلیندامایسین دیگر جایگزین خط اول توصیه نمی‌شود (ریسک کلستریدیوم دیفیسیل). آنتی‌بیوتیک را حداکثر ۲۴ ساعت پس از رفع کامل علائم قطع کنید."
            ),
            followUp = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت نیاز - ۳",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - ادامه طبق برنامه - ۷",
                notes = "کوادرانت مقابل — پروتکل دارویی مشابه جلسه اول در صورت وجود همان اندیکاسیون."
            )
        ),
        RxProfile(
            key = "rct_noninfected",
            matchKeywords = listOf("عصب‌کشی قدامی (", "anterior rct", "عصب کشی قدامی غیرعفونی", "posterior rct", "عصب‌کشی خلفی (", "عصب کشی خلفی غیرعفونی", "پالپکتومی", "درمان ریشه غیرعفونی"),
            initial = DrugPrescription(
                analgesic = "کپسول ژلوفن (ایبوپروفن) ۴۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - در صورت احساس درد - ۵",
                notes = "آنتی‌بیوتیک معمولاً اندیکاسیون ندارد (طبق ADA CPG آنتی‌بیوتیک فقط در آبسه با علائم سیستمیک یا عدم امکان کنترل موضعی عفونت توصیه می‌شود). در بیمار تحت وارفارین، Ac بر Ib ارجح است."
            )
        ),
        RxProfile(
            key = "rct_infected",
            matchKeywords = listOf("عصب‌کشی قدامی عفونی", "ant. infected rct", "عصب‌کشی خلفی عفونی", "post. infected rct", "عصب کشی عفونی", "پالپیت برگشت‌ناپذیر", "آبسه پری‌اپیکال", "re-rct", "درمان مجدد ریشه"),
            initial = DrugPrescription(
                analgesic = "کپسول ژلوفن (ایبوپروفن) ۴۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - همراه استامینوفن ۵۰۰ در درد شدید - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب کامل - ۵",
                notes = "کلیندامایسین دیگر خط اول آلرژی پنی‌سیلین محسوب نمی‌شود (طبق AHA 2021 و ADA؛ ریسک بالای کولیت پسودوممبران/C. difficile) و فقط در صورت عدم دسترسی به گزینه‌های فوق و با احتیاط قابل استفاده است. Am/AmCl: INR را در بیماران وارفارینی افزایش می‌دهد (پایش لازم). آنتی‌بیوتیک را حداکثر ۲۴ ساعت پس از رفع کامل علائم قطع کنید، صرف‌نظر از زمان ویزیت مجدد (طبق ADA بازبینی طی ۳ روز)."
            ),
            followUp = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت ناراحتی باقی‌مانده - ۳",
                notes = "در صورت تکمیل موفق درمان و رفع کامل علائم، ادامه آنتی‌بیوتیک ضرورتی ندارد."
            )
        ),
        RxProfile(
            key = "extraction",
            matchKeywords = listOf("کشیدن دندان", "جراحی دندان عقل", "extraction", "wisdom", "دندان نهفته", "exodontia"),
            initial = DrugPrescription(
                analgesic = "کپسول ژلوفن (ایبوپروفن) ۴۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - پس از جراحی - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب کامل - ۵",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - شروع از روز بعد جراحی - ۱۰",
                otherMeds = "قرص دگزامتازون ۰٫۷۵ - هر ۱۲ ساعت ۱ عدد - بعد از غذا - جهت کاهش تورم - ۲",
                notes = "Ib: طبق موارد منع فوق. Dex: در دیابت کنترل‌نشده قند خون را بالا می‌برد؛ با احتیاط تجویز شود. کلیندامایسین جایگزین خط اول آلرژی نیست. حفره خشک (Dry Socket) در صورت درد شدید ۲-۳ روز پس از کشیدن باید بررسی شود."
            ),
            followUp = DrugPrescription(notes = "کشیدن بخیه — معمولاً نیازی به داروی جدید نیست؛ ادامه کلرهگزیدین در صورت نیاز.")
        ),
        RxProfile(
            key = "implant",
            matchKeywords = listOf("کاشت ایمپلنت", "implant", "فیکسچر"),
            initial = DrugPrescription(
                analgesic = "قرص ایبوپروفن ۶۰۰ - هر ۸ ساعت ۱ عدد - همراه غذا - پس از صرف غذا - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب کامل - ۷",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۲ هفته - ۱۴",
                otherMeds = "قرص دگزامتازون ۰٫۷۵ - هر ۱۲ ساعت ۱ عدد - بعد از غذا - جهت کاهش تورم - ۲",
                notes = "پروفیلاکسی آندوکاردیت فقط برای بیماران پرخطر قلبی طبق AHA 2021 (دریچه مصنوعی، سابقه اندوکاردیت، بیماری مادرزادی سیانوتیک) — بدون کلیندامایسین. طبق بازنگری AAOS نوامبر ۲۰۲۴ (هم‌راستا با ADA)، پروفیلاکسی معمول آنتی‌بیوتیکی برای بیماران دارای پروتز مفصلی دیگر توصیه نمی‌شود مگر با صلاحدید صریح جراح ارتوپد. NSAID و وارفارین تداخل خونریزی‌دهنده دارند."
            ),
            followUp = DrugPrescription(notes = "نصب هیلینگ کپ / کشیدن بخیه — معمولاً نیاز نیست؛ در صورت ناراحتی: استامینوفن (Ac) ۵۰۰ میلی‌گرم.")
        ),
        RxProfile(
            key = "bone_graft",
            matchKeywords = listOf("پیوند استخوان", "سینوس لیفت", "bone graft", "sinus lift"),
            initial = DrugPrescription(
                analgesic = "قرص ایبوپروفن ۶۰۰ - هر ۶ ساعت ۱ عدد - همراه غذا - پس از صرف غذا - ۵",
                antibiotic = "قرص آمی‌کلاو (آموکسی‌سیلین-کلاوولانیک) ۶۲۵ - هر ۱۲ ساعت ۱ عدد - همراه غذا - با یک لیوان آب - ۷",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۲ هفته - ۱۴",
                otherMeds = "قرص دگزامتازون ۰٫۷۵ - هر ۱۲ ساعت ۱ عدد - بعد از غذا - جهت کاهش تورم - ۲",
                notes = "کلیندامایسین جایگزین اول آلرژی نیست. پرهیز اکید از فوت‌کردن، فین‌کردن شدید و استفاده از نی تا ۲ هفته جهت جلوگیری از پارگی غشای سینوس/جابجایی گرافت."
            ),
            followUp = DrugPrescription(notes = "کشیدن بخیه — قطع مسکن؛ ادامه آنتی‌بیوتیک تنها طبق صلاحدید پزشک تا تکمیل دوره تجویزشده.")
        ),
        RxProfile(
            key = "gum_flap",
            matchKeywords = listOf("جراحی فلپ لثه", "فلپ لثه", "gingival flap", "flap surgery"),
            initial = DrugPrescription(
                analgesic = "کپسول ژلوفن ۴۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - در صورت درد - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب - ۵",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۲ هفته - ۱۴",
                otherMeds = "قرص دگزامتازون ۰٫۷۵ - هر ۱۲ ساعت ۱ عدد - بعد از غذا - جهت کاهش تورم - ۲",
                notes = "توجه ویژه به بیماران دیابتی (تاخیر ترمیم). کلیندامایسین جایگزین خط اول نیست."
            ),
            followUp = DrugPrescription(notes = "کشیدن بخیه — ادامه کلرهگزیدین طبق برنامه.")
        ),
        RxProfile(
            key = "crown_lengthening",
            matchKeywords = listOf("افزایش طول تاج", "crown lengthening", "clp"),
            initial = DrugPrescription(
                analgesic = "کپسول ژلوفن ۴۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - در صورت درد - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب - ۵",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۱ هفته - ۷",
                notes = "در بیماران کلیوی، NSAID (Ib) ممنوع است — جایگزینی با استامینوفن."
            ),
            followUp = DrugPrescription(notes = "کشیدن بخیه — ادامه کلرهگزیدین در صورت نیاز.")
        ),
        RxProfile(
            key = "soft_tissue_graft",
            matchKeywords = listOf("پیوند بافت نرم لثه", "soft tissue graft", "گرافت لثه", "پیوند لثه"),
            initial = DrugPrescription(
                analgesic = "قرص ایبوپروفن ۴۰۰ - هر ۶ ساعت ۱ عدد - همراه غذا - در صورت درد - ۵",
                antibiotic = "کپسول آموکسی‌سیلین ۵۰۰ - هر ۸ ساعت ۱ کپسول - بعد از غذا - با یک لیوان آب - ۵",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از مسواک - به مدت ۲ هفته - ۱۴",
                notes = "ناحیه کام (محل دهنده) نباید تحریک مکانیکی شود. تداخل NSAID با داروهای قلبی-عروقی (مانند مهارکننده‌های ACE) در نظر گرفته شود."
            ),
            followUp = DrugPrescription(notes = "کشیدن بخیه — ادامه کلرهگزیدین در صورت نیاز.")
        ),
        RxProfile(
            key = "crown_bridge",
            matchKeywords = listOf("روکش", "بریج", "crown", "bridge", "زیرکونیا", "pfm", "لمینت"),
            initial = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت حساسیت یا درد - ۳",
                notes = "در صورت حساسیت پس از سمانتاسیون، بایت بررسی و در صورت لزوم تراش نقاط تماس انجام شود."
            ),
            followUp = DrugPrescription(notes = "تست فریم/بیسکویت/چسباندن نهایی — معمولاً دارویی لازم نیست.")
        ),
        RxProfile(
            key = "denture",
            matchKeywords = listOf("پروتز متحرک", "دنچر", "denture"),
            initial = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت درد - ۳",
                mouthwash = "دهان‌شویه کلرهگزیدین ۰٫۱۲٪ - هر ۱۲ ساعت ۱۰ سی‌سی - بعد از غذا - جهت ضدعفونی دهان - ۷",
                notes = "از مصرف بی‌رویه ژل بنزوکائین در سالمندان (خطر متهموگلوبینمی) خودداری شود؛ در صورت زخم مقاوم، تنظیم مجدد پروتز اولویت دارد نه صرفاً مسکن."
            )
        ),
        RxProfile(
            key = "pediatric",
            matchKeywords = listOf("اطفال", "پالپوتومی", "ssc", "روکش استیل", "کشیدن دندان شیری", "pediatric"),
            initial = DrugPrescription(
                analgesic = "سوسپانسیون ایبوپروفن کودکان - هر ۶ ساعت ۵ سی‌سی - بعد از غذا - بر اساس وزن کودک - ۳",
                antibiotic = "سوسپانسیون آموکسی‌سیلین ۱۲۵ - هر ۸ ساعت ۵ سی‌سی - بعد از غذا - طبق دستور پزشک - ۵",
                notes = "دوز دقیقاً بر اساس وزن کودک محاسبه شود. آسپرین در کودکان به دلیل خطر سندرم ری اکیداً ممنوع است."
            )
        ),
        RxProfile(
            key = "orthodontics",
            matchKeywords = listOf("ارتودنسی", "براکت", "الاینر", "orthodontic", "aligner"),
            initial = DrugPrescription(
                analgesic = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - در صورت درد - ۳",
                notes = "برخی مطالعات نشان می‌دهند NSAID (ایبوپروفن) ممکن است سرعت حرکت دندانی ارتودنسی را اندکی کاهش دهد؛ به همین دلیل استامینوفن برای کنترل درد ناشی از فعال‌سازی/تحویل الاینر ارجح است."
            )
        )
    )

    /** آیا متن این جلسه، جلسه پیگیری/کشیدن بخیه/تحویل نهایی (نسخه سبک‌تر) است؟ */
    private fun isFollowUpStage(stageText: String): Boolean {
        val t = stageText.lowercase()
        return followUpStageKeywords.any { kw -> t.contains(kw.lowercase()) }
    }

    /** یافتن پروفایل دارویی مناسب بر اساس عنوان درمان و متن جلسه تکمیل‌شده (بدون در نظر گرفتن سوابق بیمار). */
    fun suggest(treatmentTitle: String, stageText: String): DrugPrescription? {
        val titleLower = treatmentTitle.lowercase().trim()
        val stageLower = stageText.lowercase().trim()
        val combined = "$titleLower $stageLower"
        if (combined.isBlank()) return null

        val matched = profiles.firstOrNull { profile ->
            profile.matchKeywords.any { kw -> combined.contains(kw.lowercase()) }
        } ?: return null

        return if (isFollowUpStage(stageText)) matched.followUp else matched.initial
    }

    // ================================================================================
    // بررسی هشدارها، بیماری‌های زمینه‌ای و داروهای مصرفی بیمار پیش از نمایش نسخه پیشنهادی
    // ================================================================================

    /** یک قاعده بررسی سابقه/دارو بیمار در برابر نسخه پیشنهادی. */
    private data class PatientRxRule(
        val triggerKeywords: List<String>,
        val severity: FlagSeverity,
        val footnote: String,
        // در صورت true بودن severeAllergyMode، این قاعده فقط زمانی فعال می‌شود که علاوه‌بر تریگر، آنافیلاکسی هم ذکر شده باشد
        val adjust: (DrugPrescription) -> DrugPrescription
    )

    private const val AC_ONLY = "قرص استامینوفن ۵۰۰ - هر ۶ ساعت ۱ عدد - بعد از غذا - به‌جای NSAID با توجه به سابقه بیمار - ۵"
    private const val CFX_ALT = "کپسول سفالکسین ۵۰۰ - هر ۶ ساعت ۱ کپسول - بعد از غذا - به‌جای آموکسی‌سیلین با توجه به سابقه آلرژی - ۵"
    private const val AZI_ALT = "قرص آزیترومایسین ۵۰۰ - روزانه ۱ عدد - قبل از غذا - با توجه به آلرژی شدید/آنافیلاکسی - ۵"

    private fun containsNsaid(text: String) = text.contains("ایبوپروفن") || text.contains("(Ib)") || text.contains(" Ib ")
    private fun containsAmoxicillin(text: String) = text.contains("آموکسی‌سیلین") || text.contains("(Am)") || text.contains("(AmCl)") || text.contains("کلاوولانیک")
    private fun containsMetronidazole(text: String) = text.contains("مترونیدازول") || text.contains("(Mtz)")
    private fun containsDexamethasone(text: String) = text.contains("دگزامتازون") || text.contains("(Dex)")
    private fun containsAcetaminophen(text: String) = text.contains("استامینوفن") || text.contains("(Ac)")
    private fun containsDoxy(text: String) = text.contains("داکسی") || text.contains("(Doxy)")

    private val patientRules: List<PatientRxRule> = listOf(
        // ضدانعقاد / آسپرین / وارفارین — تداخل خونریزی‌دهنده با NSAID و افزایش INR با آنتی‌بیوتیک‌های بتالاکتام/مترونیدازول
        PatientRxRule(
            triggerKeywords = listOf("وارفارین", "ضدانعقاد", "آسپرین", "پلاویکس", "کلوپیدوگرل", "هپارین", "warfarin"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه مصرف داروی ضدانعقاد/ضدپلاکت دارد؛ ایبوپروفن به دلیل خطر خونریزی حذف و با استامینوفن جایگزین شد. در صورت تجویز آموکسی‌سیلین/مترونیدازول، INR باید پایش شود (این داروها INR را افزایش می‌دهند).",
            adjust = { rx ->
                if (containsNsaid(rx.analgesic)) rx.copy(analgesic = AC_ONLY) else rx
            }
        ),
        // نارسایی کلیوی
        PatientRxRule(
            triggerKeywords = listOf("نارسایی کلیوی", "بیماری کلیوی", "دیالیز", "کلیه"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه نارسایی/بیماری کلیوی دارد؛ ایبوپروفن (NSAID) به دلیل خطر بدتر شدن عملکرد کلیه حذف و با استامینوفن جایگزین شد. دوز آنتی‌بیوتیک ممکن است نیاز به تنظیم بر اساس GFR داشته باشد.",
            adjust = { rx ->
                if (containsNsaid(rx.analgesic)) rx.copy(analgesic = AC_ONLY) else rx
            }
        ),
        // زخم گوارشی فعال
        PatientRxRule(
            triggerKeywords = listOf("زخم معده", "زخم گوارشی", "رفلاکس شدید", "خونریزی گوارشی"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه زخم گوارشی فعال دارد؛ ایبوپروفن به دلیل خطر تشدید خونریزی گوارشی حذف و با استامینوفن جایگزین شد.",
            adjust = { rx ->
                if (containsNsaid(rx.analgesic)) rx.copy(analgesic = AC_ONLY) else rx
            }
        ),
        // بارداری
        PatientRxRule(
            triggerKeywords = listOf("بارداری", "حاملگی", "باردار"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار باردار است؛ ایبوپروفن (به‌ویژه در سه‌ماهه سوم) حذف و با استامینوفن جایگزین شد. در صورت نیاز به آنتی‌بیوتیک، داکسی‌سایکلین به دلیل تداخل با تکامل استخوان/دندان جنین ممنوع است؛ آموکسی‌سیلین (دسته B) گزینه ایمن‌تر است.",
            adjust = { rx ->
                var out = rx
                if (containsNsaid(out.analgesic)) out = out.copy(analgesic = AC_ONLY)
                if (containsDoxy(out.antibiotic)) out = out.copy(antibiotic = AZI_ALT + " (به‌جای داکسی‌سایکلین، ممنوع در بارداری)")
                out
            }
        ),
        // شیردهی — احتیاط با برخی آنتی‌بیوتیک‌ها/مسکن‌های قوی، نه ممنوعیت کامل
        PatientRxRule(
            triggerKeywords = listOf("شیردهی", "شیرده"),
            severity = FlagSeverity.CAUTION,
            footnote = "بیمار در دوران شیردهی است؛ داکسی‌سایکلین و مترونیدازول با احتیاط بیشتر تجویز شوند (در صورت امکان جایگزینی با آموکسی‌سیلین ارجح است).",
            adjust = { rx -> rx }
        ),
        // آلرژی به پنی‌سیلین (بدون آنافیلاکسی)
        PatientRxRule(
            triggerKeywords = listOf("آلرژی پنی‌سیلین", "حساسیت به پنی‌سیلین", "آلرژی پنی سیلین", "حساسیت پنی سیلین"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه آلرژی به پنی‌سیلین دارد؛ آموکسی‌سیلین/آموکسی‌سیلین-کلاوولانیک حذف و با سفالکسین جایگزین شد (کلیندامایسین به دلیل ریسک C. difficile توصیه نمی‌شود).",
            adjust = { rx ->
                if (containsAmoxicillin(rx.antibiotic)) rx.copy(antibiotic = CFX_ALT) else rx
            }
        ),
        // آلرژی شدید/آنافیلاکسی به پنی‌سیلین یا بتالاکتام‌ها (اولویت بالاتر از قاعده قبلی)
        PatientRxRule(
            triggerKeywords = listOf("آنافیلاکسی", "آلرژی شدید پنی‌سیلین", "آلرژی شدید بتالاکتام", "شوک آنافیلاکتیک"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه آنافیلاکسی/آلرژی شدید به پنی‌سیلین یا سایر بتالاکتام‌ها دارد؛ آموکسی‌سیلین، آموکسی‌سیلین-کلاوولانیک و سفالکسین (به دلیل احتمال واکنش متقاطع) حذف و با آزیترومایسین جایگزین شد.",
            adjust = { rx ->
                if (containsAmoxicillin(rx.antibiotic) || rx.antibiotic.contains("سفالکسین") || rx.antibiotic.contains("(Cfx)")) rx.copy(antibiotic = AZI_ALT) else rx
            }
        ),
        // دیابت — احتیاط با دگزامتازون (افزایش قند خون)
        PatientRxRule(
            triggerKeywords = listOf("دیابت", "قند خون بالا", "دیابتی"),
            severity = FlagSeverity.CAUTION,
            footnote = "بیمار سابقه دیابت دارد؛ در صورت وجود دگزامتازون در نسخه، با احتیاط و با پایش قند خون تجویز شود (ترجیحاً کوتاه‌ترین دوره ممکن).",
            adjust = { rx -> rx }
        ),
        // نارسایی کبدی / الکلیسم — احتیاط با استامینوفن
        PatientRxRule(
            triggerKeywords = listOf("نارسایی کبدی", "بیماری کبدی", "الکلیسم", "هپاتیت فعال"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه نارسایی کبدی/الکلیسم دارد؛ استامینوفن باید در پایین‌ترین دوز موثر و کوتاه‌مدت مصرف شود یا در صورت شدت بیماری کبدی از مصرف آن پرهیز و با پزشک معالج بیمار مشورت شود.",
            adjust = { rx -> rx }
        ),
        // مصرف بیس‌فسفونات/دنوسوماب — ریسک MRONJ در جراحی‌های استخوانی
        PatientRxRule(
            triggerKeywords = listOf("بیس فسفونات", "بیس‌فسفونات", "دنوسوماب", "زولدرونیک", "آلندرونات"),
            severity = FlagSeverity.CONTRAINDICATION,
            footnote = "بیمار سابقه مصرف بیس‌فسفونات/دنوسوماب دارد؛ ریسک استئونکروز فک (MRONJ) در جراحی‌های استخوانی (کشیدن دندان، ایمپلنت، پیوند استخوان) وجود دارد — پیش از هرگونه جراحی با پزشک تجویزکننده مشورت شود.",
            adjust = { rx -> rx }
        ),
        // بیماری قلبی پرخطر / دریچه مصنوعی / سابقه اندوکاردیت
        PatientRxRule(
            triggerKeywords = listOf("دریچه مصنوعی", "سابقه اندوکاردیت", "بیماری مادرزادی قلب", "پیوند قلب"),
            severity = FlagSeverity.CAUTION,
            footnote = "بیمار در گروه پرخطر قلبی برای آندوکاردیت عفونی است؛ پروفیلاکسی طبق دستورالعمل AHA 2021 (آموکسی‌سیلین ۲ گرم، بدون کلیندامایسین) رعایت شود.",
            adjust = { rx -> rx }
        ),
        // پروتز مفصلی — طبق بازنگری AAOS 2024 پروفیلاکسی معمول توصیه نمی‌شود
        PatientRxRule(
            triggerKeywords = listOf("پروتز مفصل", "پروتز زانو", "پروتز لگن", "مفصل مصنوعی"),
            severity = FlagSeverity.INFO,
            footnote = "بیمار سابقه پروتز مفصلی دارد؛ طبق بازنگری AAOS نوامبر ۲۰۲۴ (هم‌راستا با ADA)، پروفیلاکسی آنتی‌بیوتیکی معمول توصیه نمی‌شود مگر با صلاحدید صریح جراح ارتوپد.",
            adjust = { rx -> rx }
        )
    )

    /**
     * نسخه پیشنهادی را بر اساس سوابق پزشکی، هشدارها و داروهای مصرفی بیمار بررسی و در صورت
     * وجود تداخل یا منع مصرف اصلاح می‌کند؛ تمام موارد احتیاطی لحاظ‌شده را به‌صورت پانوشت
     * («⚠ موارد احتیاطی لحاظ‌شده بر اساس سوابق بیمار») به بخش نکات نسخه اضافه می‌کند.
     */
    fun adjustForPatient(base: DrugPrescription, medicalHistory: String, medicalAlerts: String): DrugPrescription {
        val combinedHistory = "$medicalHistory $medicalAlerts".lowercase()
        if (combinedHistory.isBlank()) return base

        var result = base
        val appliedFootnotes = mutableListOf<String>()

        for (rule in patientRules) {
            val triggered = rule.triggerKeywords.any { kw -> combinedHistory.contains(kw.lowercase()) }
            if (triggered) {
                result = rule.adjust(result)
                appliedFootnotes.add("• [${severityLabel(rule.severity)}] ${rule.footnote}")
            }
        }

        if (appliedFootnotes.isEmpty()) return result

        val footnoteBlock = buildString {
            append("\n\n⚠ موارد احتیاطی لحاظ‌شده بر اساس سوابق بیمار (بررسی خودکار تداخل و منع مصرف):\n")
            append(appliedFootnotes.joinToString("\n"))
            append("\n\nاین بررسی خودکار جایگزین قضاوت بالینی پزشک نیست و پزشک پیش از تایید نهایی باید نسخه را بازبینی کند.")
        }

        return result.copy(notes = (result.notes.trim() + footnoteBlock).trim())
    }

    private fun severityLabel(severity: FlagSeverity): String = when (severity) {
        FlagSeverity.CONTRAINDICATION -> "منع مصرف"
        FlagSeverity.CAUTION -> "احتیاط"
        FlagSeverity.INFO -> "اطلاع"
    }
}
