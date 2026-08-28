package com.example.data

/**
 * پایگاه‌دانش بالینی تراپی‌های دندانپزشکی.
 * شامل ۲۲ گروه تراپی تخصصی دندانپزشکی با تمامی جزئیات:
 * - مراحل و گام‌های اجرایی هر جلسه (Workflow & Sessions)
 * - احتیاط‌ها و هشدارهای سیستمیک (ADA 2024 / AHA 2021)
 * - پروتکل دارویی کامل (مسکن‌های ترکیبی ADA 2024 / پروفیلاکسی AHA 2021)
 * - هشدارهای وابسته به سوابق بیمار (دیابت، فشارخون، بارداری، وارفارین، آلرژی، بیس‌فسفونات، Pace Maker)
 * - عوارض بالینی و پروتکل فوریت‌های پزشکی/دندانپزشکی (Complications & Emergency Management)
 * - سفارشات ترخیص و مراقبت‌های بعد از درمان (Patient Post-Op Instructions)
 */

data class TherapySessionInfo(
    val titleFa: String,
    val descFa: String
)

data class PatientHistoryFlag(
    val triggerKeywords: List<String>,
    val warningFa: String,
    val severity: FlagSeverity = FlagSeverity.CAUTION
)

enum class FlagSeverity { INFO, CAUTION, CONTRAINDICATION }

data class TherapyProtocol(
    val key: String,
    val matchKeywords: List<String>,
    val nameFa: String,
    val nameEn: String,
    val sessions: List<TherapySessionInfo>,
    val precautions: List<String>,
    val prescriptions: List<String>,
    val modernMaterials: List<String> = emptyList(),
    val patientHistoryFlags: List<PatientHistoryFlag>,
    val complications: List<Pair<String, String>>,
    val postSessionInstructions: List<String>,
    val sources: List<String>
)

object TherapyKnowledgeBase {

    private val customProtocols = mutableMapOf<String, TherapyProtocol>()

    private val commonHistoryFlags = listOf(
        PatientHistoryFlag(
            listOf("دیابت", "قند خون", "diabetes"),
            "بیمار دیابتی: ترجیحاً درمان در ساعات صبح و بعد از وعده غذایی؛ کنترل قند خون ناشتا (BS) و HbA1c (کمتر از ۷٪ ارجح). خطر تاخیر در ترمیم و افزایش عفونت.",
            FlagSeverity.CAUTION
        ),
        PatientHistoryFlag(
            listOf("فشارخون", "فشار خون", "hypertension"),
            "بیمار فشارخون بالا: سنجش فشار قبل از شروع؛ پرهیز از اپی‌نفرین ۱:۵۰۰۰۰؛ حداکثر ۲ کارپول حاوی اپی‌نفرین ۱:۱۰۰۰۰۰؛ پرهیز از تغییر ناگهانی وضعیت یونیت (ارتاستاتیک).",
            FlagSeverity.CAUTION
        ),
        PatientHistoryFlag(
            listOf("وارفارین", "ضدانعقاد", "warfarin", "پلاویکس", "aspirin", "آسپرین"),
            "بیمار تحت درمان با ضدانعقاد: قطع خودسرانه ممنوع؛ چک INR پیش از جراحی (ایمن بین ۲ تا ۳)؛ عدم تجویز NSAIDs به دلیل خطر خونریزی گوارشی و گوارشی؛ استفاده از استامینوفن.",
            FlagSeverity.CONTRAINDICATION
        ),
        PatientHistoryFlag(
            listOf("حساسیت", "آلرژی", "allergy", "پنی سیلین", "پنی‌سیلین"),
            "سابقه حساسیت دارویی: طبق دستورالعمل AHA 2021 کلیندامایسین کاملاً حذف شده است (خطر C. difficile)؛ جایگزین: سفالکسین ۲ گرم، آزیترومایسین ۵۰۰ میلی‌گرم یا داکسی‌سایکلین ۱۰۰ میلی‌گرم.",
            FlagSeverity.CONTRAINDICATION
        ),
        PatientHistoryFlag(
            listOf("قلب", "دریچه", "اندوکاردیت", "cardiac", "endocarditis"),
            "بیمار با خطر اندوکاردیت عفونی (AHA 2021): پروفیلاکسی فقط برای دریچه مصنوعی، سابقه اندوکاردیت یا بیماری مادرزادی سیانوتیک؛ مصرف ۲ گرم آموکسی‌سیلین خوراکی ۳۰ الی ۶۰ دقیقه قبل از درمان.",
            FlagSeverity.CAUTION
        ),
        PatientHistoryFlag(
            listOf("بارداری", "حاملگی", "pregnancy"),
            "بیمار باردار: زمان طلایی سه‌ماهه دوم؛ رادیوگرافی فقط در صورت ضرورت با روپوش سربی؛ لیدوکائین (FDA Cat B) ایمن است؛ پرهیز اکید از NSAIDs در سه‌ماهه سوم (استامینوفن ارجح).",
            FlagSeverity.CAUTION
        ),
        PatientHistoryFlag(
            listOf("بیس فسفونات", "استخوان", "پ پوکی استخوان", "bisphosphonate", "mronj"),
            "مصرف بیس‌فسفونات/دنوسوماب: خطر نکروز استخوان فک (MRONJ) در جراحی و کشیدن دندان؛ اولویت با درمان‌های غیرتهاجمی و حفظ دندان؛ شست‌وشوی مداوم با کلرهگزیدین.",
            FlagSeverity.CONTRAINDICATION
        )
    )

    val protocols: List<TherapyProtocol> = listOf(
        // 1. ترمیم کامپوزیت
        TherapyProtocol(
            key = "composite",
            matchKeywords = listOf("ترمیم کامپوزیت", "کامپوزیت", "composite", "پرکردن زیبایی"),
            nameFa = "ترمیم کامپوزیت (Composite Filling)",
            nameEn = "Composite Resin Restoration",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - تراش و بازسازی کامپوزیت", "بی‌حسی موضعی، انتخاب شید رنگی، پاک‌سازی کامل پوسیدگی، اچ اسیدی ۳۷٪ (۱۵ ثانیه مینا، ۱۰ ثانیه عاج)، شست‌وشو و خشک‌کردن مرطوب، اعمال باندینگ و پوآر هوا، کیورینگ نوری، لایه‌گذاری کامپوزیت (انکریمنتال تا ۲mm)، شکل‌دهی آناتومیک، تنظیم اکلوژن و پالیش دیسکی و خمیری.")
            ),
            precautions = listOf("ایزولاسیون کامل با رابردام یا رل پنبه پیش شرط موفقیت باندینگ است.", "در حفره‌های عمیق از لایز تزریقی یا گلاس‌آینومر به‌عنوان بیس/لاینر جهت جلوگیری از حساسیت حرارتی استفاده شود."),
            prescriptions = listOf("در صورت حساسیت خفیف پس از کار: استامینوفن ۵۰۰ میلی‌گرم یا ایبوپروفن ۴۰۰ میلی‌گرم در صورت نیاز."),
            patientHistoryFlags = commonHistoryFlags.filter { it.triggerKeywords.any { k -> k in listOf("حساسیت", "دیابت") } },
            complications = listOf(
                "حساسیت به سرما/گرما بعد از ترمیم" to "ارزیابی بایت (هایپراکلوژن)، در صورت بایت بالا تراش مجدد نقاط تماس؛ استفاده از خمیردندان ضدحساسیت.",
                "افتادن یا شکستن ترمیم" to "بررسی انقباض پلیمریزاسیون، ایزولاسیون مجدد و ترمیم یا تعویض با کامپوزیت پکریبل."
            ),
            postSessionInstructions = listOf("تا ۲ ساعت از خوردن مواد غذایی سفت و رنگی (چای پررنگ، قهوه، پفک) پرهیز شود.", "در صورت احساس بلندی هنگام جویدن حتما جهت تنظیم مجدد مراجعه فرمایید."),
            sources = listOf("Sturdevant's Art & Science of Operative Dentistry", "ADA Clinical Guidelines")
        ),

        // 2. ترمیم آمالگام
        TherapyProtocol(
            key = "amalgam",
            matchKeywords = listOf("ترمیم آمالگام", "آمالگام", "amalgam"),
            nameFa = "ترمیم آمالگام (Amalgam Restoration)",
            nameEn = "Dental Amalgam Restoration",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - آماده‌سازی و کندانس آمالگام", "بی‌حسی موضعی، تراش پوسیدگی با ایجاد گیر مکانیکی و زاویه ۹۰ درجه در حواشی، قرار دادن والینیک/وارنیش زینک اکساید، مخلوط‌سازی کپسول آمالگام، کندانس لایه‌به‌لایه‌ای عمیق، کاروینگ آناتومیک شیارها و برنیشینگ حواشی، چک بایت با کاغذ کاربن.")
            ),
            precautions = listOf("کوری و برنیشینگ دقیق برای جلوگیری از گپ حاشیه‌ای الزام‌آور است.", "عدم اعمال فشار سنگین تا ۲۴ ساعت جهت گیرش کامل آلیاژ."),
            prescriptions = listOf("استامینوفن ۵۰۰ میلی‌گرم یا ایبوپروفن ۴۰۰ میلی‌گرم در صورت درد احتمالی."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "شکستن دیواره دندان" to "در تخریب‌های وسیع خلفی، ترجیح با پوشش کاسپ (Onlay/Crown) یا ترمیم با پین درون‌عاجی است."
            ),
            postSessionInstructions = listOf("تا ۲ ساعت از خوردن غذا پرهیز کنید.", "تا ۲۴ ساعت با سمت ترمیم‌شده غذاهای بسیار سفت نجوید."),
            sources = listOf("Sturdevant's Operative Dentistry")
        ),

        // 3. ونیر کامپوزیت
        TherapyProtocol(
            key = "composite_veneer",
            matchKeywords = listOf("ونیر کامپوزیت", "کامپوزیت ونیر", "composite veneer"),
            nameFa = "ونیر کامپوزیت زیبایی (Composite Veneer)",
            nameEn = "Direct Composite Veneer",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - فرم‌دهی و لایه‌گذاری زیبایی", "آنالیز لبخند، انتخاب شیدهای انامل، دنپین و اوپکر، اماده‌سازی بسیار سطحی مینا (بدون تراش عاج)، اچ و باندینگ، فرم‌دهی لایه‌ای لبخند، کیور نوری و فرم‌دهی اولیه.",
                ),
                TherapySessionInfo("جلسه ۲ - پرداخت و پالیش نهایی", "بررسی اکلوژن در حرکات سانتریک و لترال، پالیش چندمرحله‌ای با دیسک‌ها، مولت‌های لاستیکی و خمیر الماسه جهت دستیابی به درخشش طبیعی.")
            ),
            precautions = listOf("کنترل اکلوژن دقیق به ویژه در بیماران با سابقه بروکسیزم (دندان‌قروچه)."),
            prescriptions = listOf("در صورت لزوم تجویز نایت گارد (محافظ شبانه) جهت ماندگاری ونیرها."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "پریدگی یا لبه‌پریدگی کامپوزیت" to "ترمیم موضعی با اسیداچ، باندینگ و افزودن کامپوزیت نانوهایبرید."
            ),
            postSessionInstructions = listOf("استفاده روزانه از نخ دندان و مسواک نرم.", "پرهیز از شکستن آجیل با دندان جلو و تخمه‌شکستن."),
            sources = listOf("Journal of Esthetic and Restorative Dentistry")
        ),

        // 4. لمینت سرامیکی
        TherapyProtocol(
            key = "laminate",
            matchKeywords = listOf("لمینت", "laminate", "porcelain veneer", "لمینت سرامیکی"),
            nameFa = "لمینت سرامیکی (Porcelain Laminate Veneer)",
            nameEn = "Porcelain Laminate Veneer",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - آنالیز، تراش و قالب‌گیری", "طراحی لبخند، تراش محافظه‌کارانه مینا (0.3 - 0.7mm)، نخ پس‌زنی لثه، قالب‌گیری دقیق با سیلیکون افزایشی (A-Silicone) و ساخت لمینت موقت."),
                TherapySessionInfo("جلسه ۲ - امتحان فریم سرامیکی", "امتحان لمینت‌ها در دهان، بررسی انطباق حواشی، کنتاکت‌ها، رنگ و فرم زیبایی با خمیر Try-in."),
                TherapySessionInfo("جلسه ۳ - سمنتاسیون رزینی نهایی", "ایزولاسیون با رابردام، اچ سرامیک با هیدروفلوریک اسید، سیلان‌زدن، اچ و باندینگ مینا، سمنتاسیون با سمان رزینی لایت‌کیور و پاک‌سازی زوائد.")
            ),
            precautions = listOf("حفظ مینای دندان برای باندینگ پایدار به لمینت ضروری است."),
            prescriptions = listOf("دهان‌شویه کلرهگزیدین ۰.۱۲٪ روزی ۲ بار به مدت ۵ روز بعد از چسباندن."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "جدا شدن لمینت" to "بررسی علل اکلوژالی، اچ مجدد دندان و سرامیک و سمنتاسیون مجدد."
            ),
            postSessionInstructions = listOf("پرهیز از وارد کردن نیروهای برشی سنگین به دندان‌های جلو.", "مراجعه منظم ۶ ماهه جهت بررسی حواشی لمینت."),
            sources = listOf("Rosentiel Contemporary Fixed Prosthodontics")
        ),

        // 5. اینله / آنله
        TherapyProtocol(
            key = "inlay_onlay",
            matchKeywords = listOf("اینله", "آنله", "inlay", "onlay"),
            nameFa = "اینله و آنله سرامیکی/کامپوزیتی (Inlay / Onlay)",
            nameEn = "Porcelain/Composite Inlay & Onlay",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - تراش و قالب‌گیری", "برداشت پوسیدگی، بازسازی دیواره‌ها، آماده‌سازی حفره با واگرایی ۶-۱۰ درجه دیواره‌ها، قالب‌گیری دقیق و پانسمان موقت."),
                TherapySessionInfo("جلسه ۲ - چسباندن نهایی (Sementation)", "برداشت پانسمان، امتحان قطعه سرامیکی/کامپوزیتی، ایزولاسیون و سمنتاسیون رزینی، تنظیم بایت و پالیش حواشی.")
            ),
            precautions = listOf("شعاع زوایای داخلی باید گرد باشد تا از تمرکز استرس جلوگیری شود."),
            prescriptions = listOf("مسکن در صورت حساسیت خفیف بافتی."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("حساسیت پس از سمنتاسیون" to "چک بایت دقیق و ارزیابی گپ حاشیه‌ای."),
            postSessionInstructions = listOf("تا ۲ ساعت بعد از سمنتاسیون غذا نجوید."),
            sources = listOf("Sturdevant's Operative Dentistry")
        ),

        // 6. بلیچینگ مطبی
        TherapyProtocol(
            key = "bleaching",
            matchKeywords = listOf("بلیچینگ", "سفید کردن", "bleaching", "whitening"),
            nameFa = "بلیچینگ مطبی (In-Office Bleaching)",
            nameEn = "In-Office Tooth Whitening",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - سفیدکردن تخصصی مطبی", "جرم‌گیری و بروساژ سطحی، نصب رتراکتور لب و محافظ لثه (Gingival Barrier) و کیور نوری آن، بکارگیری ژل هیدروژن پراکسید ۳۵-۴۰٪ روی دندان‌ها در ۳ پارت ۱۵ دقیقه‌ای، شست‌وشو و اعمال ژل فلوراید/ضدحساسیت.")
            ),
            precautions = listOf("پوشش کامل لثه‌ها با رزین محافظ جهت جلوگیری از سوختگی شیمیایی بافت نرم الزام‌آور است."),
            prescriptions = listOf("خمیردندان ضدحساسیت (حتما حاوی نیترات پتاسیم) از یک هفته قبل و بعد از بلیچینگ.", "ایبوپروفن ۴۰۰ میلی‌گرم ۱ ساعت قبل از بلیچینگ در بیماران حساس."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("حساسیت شدید دندانی به سرما و هوا" to "استفاده از ژل ضدحساسیت Potassium Nitrate / Fluoride و پرهیز از نوشیدنی‌های سرد."),
            postSessionInstructions = listOf("رعایت رژیم سفید (White Diet) تا ۴۸ ساعت: پرهیز اکید از چای، قهوه، نوشابه، رب، زعفران و سیگار."),
            sources = listOf("JADA Tooth Bleaching Guidelines")
        ),

        // 7. عصب‌کشی و درمان ریشه (RCT)
        TherapyProtocol(
            key = "rct",
            matchKeywords = listOf("عصب", "ریشه", "rct", "endo", "پالپ", "root canal"),
            nameFa = "عصب‌کشی / درمان ریشه (Root Canal Treatment)",
            nameEn = "Root Canal Treatment (RCT)",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - آماده‌سازی و تخلیه کانال", "بی‌حسی، رابردم، حفره دسترسی، تعیین طول کارکردی (Working Length) با اپکس‌لوکیتور و گرافی، آماده‌سازی روتاری، شست‌وشو با NaOCl ۲.۵٪ و EDTA ۱۷٪، پانسمان کلسیم هیدروکساید در صورت عفونت."),
                TherapySessionInfo("جلسه ۲ - پرکردن و انسداد کانال (Obturation)", "شست‌وشوی نهایی، خشک‌کردن با پیپر پوینت، پرکردن کانال با گوتاپرکا و سیلر رزینی (MTA/Bioceramic)، گرافی تایید کیفی پرکردگی و پانسمان/ترمیم تاج.")
            ),
            precautions = listOf("شست‌وشوی مداوم با هیپوکلریت سدیم و عدم خروج آن از اپکس.", "ایزولاسیون با رابردم جهت جلوگیری از بلع فایل و آلودگی بزاقی."),
            prescriptions = listOf(
                "تسکین درد (طبق ADA 2024): ایبوپروفن ۴۰۰-۶۰۰ میلی‌گرم + استامینوفن ۵۰۰ میلی‌گرم هر ۶ ساعت ترکیبی.",
                "آنتی‌بیوتیک: طبق ADA 2024 در پالپیت یا پریودنتیت اپیکال بدون علائم سیستمیک (تب، تورم منتشر) آنتی‌بیوتیک ممنوع است! تنها در صورت تورم منتشر یا تب: آموکسی‌سیلین ۵۰۰ میلی‌گرم هر ۸ ساعت به مدت ۵ روز."
            ),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "حادثه هیپوکلریت (NaOCl Accident)" to "شست‌وشوی فوری با نرمال سالین، بی‌حسی موضعی جهت تسکین درد، کمپرس سرد اولیه سپس گرم، تجویز مسکن و آنتی‌بیوتیک و پیگیری روزانه.",
                "شکستن فایل در کانال" to "اطلاع‌رسانی صریح به بیمار، تلاش برای بای‌پس (By-pass) فایل با K-File سایز ۱۰ و ۱۵ یا خارج‌سازی با اولتراسونیک.",
                "درد شدید بین‌جلسه‌ای (Flare-up)" to "بازکردن پانسمان، شست‌وشوی مجدد با سالین، تجدید پانسمان کلسیم هیدروکساید و اصلاح اکلوژن."
            ),
            postSessionInstructions = listOf("احساس درد هنگام جویدن تا چند روز طبیعی است و با مسکن تجویز شده کنترل می‌شود.", "تا زمان ترمیم نهایی تاج از جویدن لقمه‌های سفت رو دندان عصب‌کشی‌شده پرهیز کنید."),
            sources = listOf("Cohen's Pathways of the Pulp", "AAE Endodontic Guidelines", "ADA 2024 Dental Pain Management")
        ),

        // 8. درمان مجدد ریشه (Re-RCT)
        TherapyProtocol(
            key = "re_rct",
            matchKeywords = listOf("درمان مجدد", "re-rct", "re_rct", "عصب کشی مجدد"),
            nameFa = "درمان مجدد ریشه (Re-Root Canal Treatment)",
            nameEn = "Endodontic Retreatment",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - تخلیه گوتا و مواد قدیمی", "بی‌حسی، ایزولاسیون، برداشت ترمیم قدیمی/پست، خارج‌سازی گوتاپرکا و سیلر با فایل روتاری مخصوص Retreatment و حلال (حلال ارنج یا کلروفرم)، دستیابی به طول کار کامل و پانسمان کلسیم هیدروکساید."),
                TherapySessionInfo("جلسه ۲ - شست‌وشو و پرکردن مجدد", "پاک‌سازی مجدد کانال‌ها، فعال‌سازی اولتراسونیک شست‌وشودهنده‌ها، پرکردن کامل با گوتا و سیلر بیوسرامیک و گرافی تایید.")
            ),
            precautions = listOf("خطر سوراخ‌شدگی (Perforation) یا پله‌افتادن (Ledge) در کانال‌های دستکاری‌شده قدیمی."),
            prescriptions = listOf("ترکیب ایبوپروفن ۶۰۰ میلی‌گرم + استامینوفن ۵۰۰ میلی‌گرم جهت کنترل درد شدید احتمالی."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("پرفوراسیون ریشه" to "ترمیم فوری موضع سوراخ‌شدگی با MTA یا آلونیکس/بیوسرامیک Putty."),
            postSessionInstructions = listOf("مراقبت دقیق و پرهیز از فشار سنگین بر دندان تا جابجایی ترمیم نهایی."),
            sources = listOf("AAE Guide to Clinical Endodontic Retreatment")
        ),

        // 9. کاشت ایمپلنت تخصصی
        TherapyProtocol(
            key = "implant",
            matchKeywords = listOf("ایمپلنت", "کاشت", "implant", "فیکسچر"),
            nameFa = "کاشت ایمپلنت تخصصی (Dental Implant Surgery)",
            nameEn = "Dental Implant Surgery & Prosthetics",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - جراحی کاشت فیکسچر (Stage 1)", "بی‌حسی، کنار زدن فلپ موکوپریوستئال، دریلینگ مرحله‌ای استخوان فک بر اساس گاید/CBCT با اسپری سالین سرد، رزوه فیکسچر با تورک مناسب (۳۵-۴۵ Ncm)، بستن هیلینگ اسکرو یا کاوراسکرو و بخیه زدن."),
                TherapySessionInfo("جلسه ۲ - کشیدن بخیه و کنترل التیام", "ارزیابی بافت نرم، کنترل التیام لثه و کشیدن بخیه‌ها بعد از ۷ الی ۱۰ روز."),
                TherapySessionInfo("جلسه ۳ - جراحی مرحله دوم و نصب هیلینگ کپ (Stage 2)", "پس از ۳ تا ۶ ماه استئواینتگراسیون: آنکاور کردن ایمپلنت، نصب هیلینگ اباتمنت جهت فرم‌دهی لثه به مدت ۲ هفته."),
                TherapySessionInfo("جلسه ۴ - قالب‌گیری روکش ایمپلنت", "قالب‌گیری به روش Open-Tray یا Closed-Tray یا اسکن دیجیتال با اسکن‌بادام."),
                TherapySessionInfo("جلسه ۵ - تحویل روکش ایمپلنت", "تست اباتمنت، تنظیم کنتاکت‌ها و اکلوژن روکش، بستن پیچ اباتمنت با تورک‌متر (۲۵-۳۵ Ncm) و سمنتاسیون یا پیچ‌کردن روکش (Screw-Retained).")
            ),
            precautions = listOf("ارزیابی تراکم استخوان، فاصله از عصب آلوئولار سفلی و سینوس ماگزیلا با CBCT پیش از جراحی الزام‌آور است."),
            prescriptions = listOf(
                "آموکسی‌سیلین ۵۰۰ میلی‌گرم هر ۸ ساعت به مدت ۷ روز (یا آزیترومایسین ۵۰۰ میلی‌گرم روز اول، سپس ۲۵۰ میلی‌گرم روزانه در آلرژی).",
                "مسکن: ایبوپروفن ۶۰۰ میلی‌گرم هر ۶ ساعت.",
                "دهان‌شویه کلرهگزیدین ۰.۱۲٪ روزی ۲ بار به مدت ۲ هفته."
            ),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "عدم استئواینتگراسیون و لقی ایمپلنت (Spinning Implant)" to "خارج کردن فیکسچر لق، کورتاژ کامل ساکت استخوانی، پیوند استخوان و صبر ۳ الی ۶ ماهه جهت کاشت مجدد.",
                "پری‌ایمپلنتیت (Peri-Implantitis)" to "پاک‌سازی سطح ایمپلنت با ایرفلو/اولتراسونیک، بکارگیری متراکسین/مواد بازسازی استخوان."
            ),
            postSessionInstructions = listOf("استفاده از کمپرس یخ در ۲۴ ساعت اول (۱۰ دقیقه گذاشتن، ۱۰ دقیقه برداشتن) جهت کاهش تورم.", "پرهیز از کشیدن سیگار و قلیان به مدت حداقل ۲ هفته (عامل اصلی شکست ایمپلنت)."),
            sources = listOf("ITI Treatment Guide", "Misch's Contemporary Implant Dentistry")
        ),

        // 10. پیوند استخوان و سینوس لیفت
        TherapyProtocol(
            key = "bone_graft",
            matchKeywords = listOf("پیوند استخوان", "سینوس لیفت", "bone graft", "sinus lift", "پودر استخوان"),
            nameFa = "پیوند استخوان و سینوس لیفت (Bone Graft & Sinus Lift)",
            nameEn = "Bone Grafting & Sinus Floor Elevation",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - جراحی پیوند و لبه سینوس", "بی‌حسی، کنار زدن فلپ، ایجاد پنجره استخوانی (Lateral Window) یا روش جراحی بسته (Osteotome)، بالا بردن غشای شنایدر سینوس بدون پارگی، قرار دادن پودر استخوان (آلوگرافت/گزنوگرافت) و ممبران کلاژن و بخیه محکم."),
                TherapySessionInfo("جلسه ۲ - کشیدن بخیه و پیگیری", "کنترل التیام و کشیدن بخیه‌ها پس از ۱۰ الی ۱۴ روز.")
            ),
            precautions = listOf("بررسی سلامتی سینوس ماگزیلا و عدم وجود سینوزیت حاد پیش از جراحی."),
            prescriptions = listOf(
                "آموکسی‌سیلین + اسید کلاولانیک (کواپلاو/فارمنتین) ۶۲۵/۸۷۵ میلی‌گرم هر ۱۲ ساعت به مدت ۷ روز.",
                "اسپری ضد احتقان بینی (فنیل‌افرین/سدیم کلراید) روزی ۳ بار به مدت ۵ روز.",
                "ایبوپروفن ۶۰۰ میلی‌گرم هر ۶ ساعت."
            ),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("پارگی غشای سینوس (Schneiderian Membrane Perforation)" to "پوشاندن موضع پارگی با ممبران کلاژنی قابل جذب کلاپلاگ/کلاژن سد یا بخیه با نخ ۶-۰ مونوپلاست."),
            postSessionInstructions = listOf("پرهیز اکید از فوت کردن، فین کردن شدید، عطسه با دهان بسته و استفاده از نی به مدت ۲ هفته جهت جلوگیری از ایجاد فشار معکوس در سینوس."),
            sources = listOf("Peterson's Principles of Oral and Maxillofacial Surgery")
        ),

        // 11. جراحی افزایش طول تاج
        TherapyProtocol(
            key = "crown_lengthening",
            matchKeywords = listOf("افزایش طول تاج", "crown lengthening", "clp", "جراحی لثه تاج"),
            nameFa = "جراحی افزایش طول تاج (Crown Lengthening Surgery - CLP)",
            nameEn = "Crown Lengthening Surgery",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - جراحی فلپ و استئوتومی", "بی‌حسی موضعی، اینسیژن معکوس شیاری، کنار زدن فلپ، استئوتومی و استئوپلاستی استخوان دور دندان جهت بازسازی عرض بیولوژیک (حداقل ۳mm فاصله استخوان تا حاشیه ترمیم)، شست‌وشو و بخیه جابجا شده به سمت اپیکال (APF)."),
                TherapySessionInfo("جلسه ۲ - کشیدن بخیه و معاینه", "بررسی التیام بافت نرم و کشیدن بخیه‌ها پس از ۷ تا ۱۰ روز.")
            ),
            precautions = listOf("رعایت دقیق عرض بیولوژیک (Biological Width) برای جلوگیری از التهاب مزمن و تحلیل استخوان."),
            prescriptions = listOf("ایبوپروفن ۴۰۰ میلی‌گرم هر ۶ ساعت.", "دهان‌شویه کلرهگزیدین ۰.۱۲٪ روزی ۲ بار."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("حساسیت ریشه‌ای دندان‌های مجاور" to "استفاده از ژل‌های فلورایدتراپی و خمیردندان‌های ضدحساسیت."),
            postSessionInstructions = listOf("تا ۲۴ ساعت غذاهای نرم و خنک میل شود.", "شست‌وشوی ملایم دهان با کلرهگزیدین بدون مسواک زدن مستقیم روی منطقه جراحی."),
            sources = listOf("Carranza's Clinical Periodontology")
        ),

        // 12. کشیدن دندان و جراحی دندان عقل
        TherapyProtocol(
            key = "extraction",
            matchKeywords = listOf("کشیدن", "جراحی دندان", "عقل", "extraction", "exodontia", "نهفته"),
            nameFa = "کشیدن دندان / جراحی دندان عقل (Tooth Extraction / Wisdom Surgery)",
            nameEn = "Tooth Extraction & Impacted Wisdom Surgery",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - کشیدن / جراحی و خروج دندان", "بی‌حسی عمیق، سندسموتومی، لوکسیشن با الواتور، اکسپاند ساکت با فورسپس/سکشن‌کردن تاج و ریشه در جراحی دندان نهفته، کورتاژ ساکت، فشار با گاز استریل و بخیه زدن در صورت جراحی.")
            ),
            precautions = listOf("ارزیابی مسیر عصب آلوئولار سفلی و سینوس ماگزیلا با رادیوگرافی پانوپاتیک/CBCT."),
            prescriptions = listOf(
                "ایبوپروفن ۴۰۰-۶۰۰ میلی‌گرم + استامینوفن ۵۰۰ میلی‌گرم هر ۶ ساعت جهت کنترل درد.",
                "در جراحی‌های وسیع یا نهفته: آموکسی‌سیلین ۵۰۰ میلی‌گرم هر ۸ ساعت به مدت ۵ روز."
            ),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf(
                "حفره خشک (Dry Socket / Alveolar Osteitis)" to "شست‌وشوی ملایم ساکت با سالین گرم، قرار دادن پانسمان آلووژل (Alvogyl) یا گاز آغشته به اوژنول داخل ساکت.",
                "خونریزی مداوم post-op" to "فشار مستقیم گاز مرطوب به مدت ۴۵ دقیقه، بکارگیری اسید ترانگزامیک یا ترومبین موضعی و بخیه criss-cross."
            ),
            postSessionInstructions = listOf("فشار دادن محکم گاز استریل روی محل کشیده شده به مدت ۱ الی ۱.۵ ساعت بدون تف کردن بزاق.", "پرهیز از مصرف غذاهای داغ، سیگار، قلیان و استفاده از نی تا ۴۸ ساعت."),
            sources = listOf("Peterson's Principles of Oral and Maxillofacial Surgery")
        ),

        // 13. روکش تک واحدی
        TherapyProtocol(
            key = "crown",
            matchKeywords = listOf("روکش", "پروتز ثابت", "crown", "زیرکونیا", "pfm"),
            nameFa = "روکش تک واحدی (PFM / Zirconia Crown)",
            nameEn = "Single Unit Crown Restoration",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - تراش دندان و قالب‌گیری", "بی‌حسی، تراش آناتومیک دندان (۱.۵-۲mm تاج و خط تراش شولدر/چمبر)، نخ پس‌زنی لثه (Retraction Cord)، قالب‌گیری با سیلیکون افزایشی و ساخت روکش موقت آکریلی."),
                TherapySessionInfo("جلسه ۲ - تست فریم یا تست بیسکویتی", "بررسی انطباق فریم زیرکونیا/فلزی روی خط تراش، کنتاکت‌های مجاور و فضای ساخت سرامیک."),
                TherapySessionInfo("جلسه ۳ - تحویل و سمنتاسیون نهایی", "چک نهایی اکلوژن، سمنتاسیون با گلاس‌آینومر یا سمان رزینی، پاک‌سازی کامل زوائد سمان بین‌دندانی.")
            ),
            precautions = listOf("پاک‌سازی کامل زوائد سمان زیر لثه جهت جلوگیری از التهاب و تحلیل استخوان."),
            prescriptions = listOf("استامینوفن در صورت التهاب خفیف لثه پس از تراش."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("گیر کردن نخ دندان یا لقی روکش" to "بررسی انطباق حواشی و سمنتاسیون مجدد با سمان مقاوم‌تر."),
            postSessionInstructions = listOf("استفاده روزانه از نخ دندان عبوری حول روکش جهت حفظ سلامت لثه."),
            sources = listOf("Rosentiel Contemporary Fixed Prosthodontics")
        ),

        // 14. بریج دندانی
        TherapyProtocol(
            key = "bridge",
            matchKeywords = listOf("بریج", "bridge", "پروتز متصل"),
            nameFa = "بریج دندانی (Dental Bridge)",
            nameEn = "Fixed Partial Denture (Bridge)",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - تراش پایه‌ها و قالب‌گیری", "تراش دندان‌های پایه (Abutment)، موازی‌سازی مسیر نشست بریج (Path of Insertion)، نخ پس‌زنی، قالب‌گیری و نصب بریج موقت."),
                TherapySessionInfo("جلسه ۲ - تست فریم و بیسکویت", "تست فریم متصل، ارزیابی انطباق پنتیک (Pontic) با لثه و چک بایت."),
                TherapySessionInfo("جلسه ۳ - چسباندن نهایی", "سمنتاسیون دائم با گلاس آینومر سلف کانس یا رزینی و آموزش استفاده از نخ دندان مخصوص (SuperFloss).")
            ),
            precautions = listOf("ارزیابی نسبت ریشه به تاج (Crown-to-Root Ratio) دندان‌های پایه طبق قانون آن (Ante's Law)."),
            prescriptions = listOf("دهان‌شویه کلرهگزیدین جهت تمیزی زیر پنتیک."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("گیر غذایی زیر پنتیک بریج" to "طراحی پنتیک به فرم modified ridge lap و آموزش نخ دندان سوپرفلوس."),
            postSessionInstructions = listOf("تمیز کردن زیر پنتیک بریج با نخ دندان سوپرفلوس یا واترپیک (Interdental Cleaner)."),
            sources = listOf("Rosentiel Fixed Prosthodontics")
        ),

        // 15. دندان مصنوعی و پروتز متحرک
        TherapyProtocol(
            key = "denture",
            matchKeywords = listOf("دست دندان", "دندان مصنوعی", "denture", "پروتز متحرک", "پارسیال"),
            nameFa = "دندان مصنوعی و پروتز متحرک (Complete / Partial Denture)",
            nameEn = "Complete & Removable Partial Denture",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - قالب‌گیری اولیه", "معاینه مخاط دهان، انتخاب تری آناتومیک و قالب‌گیری اولیه با آلژینات."),
                TherapySessionInfo("جلسه ۲ - قالب‌گیری نهایی (Final Impression)", "ساخت تری اختصاصی، بوردر مولدینگ با کمپوند و قالب‌گیری نهایی با واش ژل."),
                TherapySessionInfo("جلسه ۳ - ثبت بایت و روابط فکی (Jaw Relation)", "تعیین ارتفاع عمودی اکلوژن (VDO)، خط لبخند و انتخاب رنگ و سایز دندان‌ها."),
                TherapySessionInfo("جلسه ۴ - امتحان واکس (Try-in)", "ارزیابی چیدمان دندان‌ها در موم، تکلم، زیبایی و چک بایت."),
                TherapySessionInfo("جلسه ۵ - تحویل پروتز (Delivery)", "تحویل دست‌دندان، اصلاح نقاط فشار موضعی با خمیر PIP و آموزش نگهداری."),
                TherapySessionInfo("جلسه ۶ - تنظیم زخم‌های اولیه (Post-Check)", "بررسی زخم‌های احتمالی مخاطی و برطرف کردن نقاط فشار آکریل.")
            ),
            precautions = listOf("کنترل مفصل گیجگاهی-فکی (TMJ) و سلامت بافت مخاطی زیر پروتز."),
            prescriptions = listOf("قطره نیستانتین دهانی در صورت وجود کاندیدیازیس زیر دست‌دندان."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("زخم شدن مخاط تحت فشار پروتز" to "شناسایی نقطه فشار با خمیر PIP و تراش آکریل اضافی پروتز در آن نقطه."),
            postSessionInstructions = listOf("خارج کردن پروتز از دهان هنگام خواب و نگهداری در ظرف آب تمیز.", "شست‌وشوی روزانه پروتز با مسواک مخصوص و صابون ملایم."),
            sources = listOf("Boucher's Prosthodontic Treatment for Edentulous Patients")
        ),

        // 16. ارتودنسی ثابت
        TherapyProtocol(
            key = "fixed_ortho",
            matchKeywords = listOf("ارتودنسی ثابت", "براکت", "ortho", "orthodontic", "سیم کشی"),
            nameFa = "ارتودنسی ثابت (Fixed Orthodontic Treatment)",
            nameEn = "Fixed Orthodontic Appliance Treatment",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - ثبت مدارک تشخیصی", "قالب‌گیری تشخیصی، عکس‌های فتوگرافی لبخند و رخ، آنالیز رادیوگرافی پانوپاتیک و سفالومتری."),
                TherapySessionInfo("جلسه ۲ - نصب براکت‌ها (Bonding)", "اچ، باندینگ و چسباندن براکت‌ها روی دندان‌ها، قرار دادن سیم اولیه (NiTi) و الاستیک‌ها."),
                TherapySessionInfo("جلسات ماهانه - تنظیمات و تعویض سیم", "ارزیابی حرکت دندان‌ها، تعویض سیم به سایزهای بالاتر و بکارگیری کش‌های بین‌فکی."),
                TherapySessionInfo("جلسه پایانی - ده‌باندینگ و ریتینر", "برداشتن براکت‌ها، پالیش سمان، قالب‌گیری و تحویل ریتینر ثابت و متحرک.")
            ),
            precautions = listOf("ارزیابی بهداشت دهان بیمار پیش از شروع؛ خطر دمینرالیزاسیون و پوسیدگی دور براکت‌ها."),
            prescriptions = listOf("موم ارتودنسی جهت پرهیز از زخم شدن مخاط گونه توسط سیم‌ها."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("زخم شدن گونه و لب" to "استفاده از موم ارتودنسی روی براکت آزاردهنده."),
            postSessionInstructions = listOf("استفاده از مسواک ارتودنسی و مسواک بین‌دندانی بعد از هر وعده غذایی.", "پرهیز اکید از خوردن غذاهای سفت و چسبنده (گز، سوهان، ته دیگ)."),
            sources = listOf("Proffit's Contemporary Orthodontics")
        ),

        // 17. ارتودنسی شفاف / الاینر
        TherapyProtocol(
            key = "aligners",
            matchKeywords = listOf("الاینر", "ارتودنسی شفاف", "clear aligners", "invisalign", "پلاک شفاف"),
            nameFa = "ارتودنسی شفاف / الاینر (Clear Aligners)",
            nameEn = "Clear Aligner Orthodontic Therapy",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - اسکن ۳ بعدی و ClinCheck", "اسکن دیجیتال داخل دهانی، ارسال فایل به نرم‌افزار طراحی، بررسی سیمولیشن ۳ بعدی حرکت دندان‌ها."),
                TherapySessionInfo("جلسه ۲ - نصب اتچمنت‌ها و تحویل پلاک‌ها", "باندینگ اتچمنت‌های کامپوزیتی، استریپینگ بین‌دندانی (IPR) در صورت نیاز و تحویل سری اول الاینرها."),
                TherapySessionInfo("پایش دوره‌ای - تحویل پلاک‌های بعدی", "ارزیابی انطباق پلاک‌ها و تحویل سری‌های جدید هر ۲ الی ۴ هفته.")
            ),
            precautions = listOf("همکاری کامل بیمار در استفاده ۲۲ ساعت در شبانه‌روز شرط اصلی موفقیت درمان است."),
            prescriptions = listOf("قرص‌های ضدعفونی‌کننده پلاک شفاف."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("عدم نشستن کامل الاینر (Tracking Error)" to "استفاده از چوی (Chewies) جهت فشردن الاینر و نشستن کامل آن."),
            postSessionInstructions = listOf("خارج کردن پلاک‌ها فقط هنگام غذا خوردن و مسواک زدن (حداقل ۲۲ ساعت استفاده در روز)."),
            sources = listOf("Journal of Clinical Orthodontics")
        ),

        // 18. جرم‌گیری و بروساژ
        TherapyProtocol(
            key = "scaling",
            matchKeywords = listOf("جرم گیری", "بروساژ", "scaling", "polishing", "جرم‌گیری"),
            nameFa = "جرم‌گیری و بروساژ (Scaling & Polishing)",
            nameEn = "Dental Scaling & Prophylaxis",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - پاک‌سازی و کورتاژ سبک", "ارزیابی عمق پاکت‌ها، برداشت جرم‌های فوق و زیر لثه‌ای با دستگاه اولتراسونیک (کویتورون)، کورتاژ دستی، بروساژ با خمیر مخصوص و فلورایدتراپی.")
            ),
            precautions = listOf("استفاده از اسپری آب کافی حین کار با اولتراسونیک جهت پرهیز از آسیب حرارتی به پالپ."),
            prescriptions = listOf("دهان‌شویه کلرهگزیدین ۰.۱۲٪ روزی ۲ بار به مدت ۱ هفته."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("حساسیت موقت دندان‌ها به سرما" to "استفاده از خمیردندان ضدحساسیت و ژل فلوراید."),
            postSessionInstructions = listOf("مسواک زدن صحیح با تکنیک بس (Bass) و استفاده روزانه از نخ دندان."),
            sources = listOf("Carranza's Clinical Periodontology")
        ),

        // 19. جرم‌گیری عمیق (SRP)
        TherapyProtocol(
            key = "srp",
            matchKeywords = listOf("srp", "جرم گیری عمیق", "تسطیح ریشه", "root planing"),
            nameFa = "جرم‌گیری عمیق و تسطیح سطح ریشه (Scaling & Root Planing - SRP)",
            nameEn = "Scaling & Root Planing (SRP)",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - SRP کوادرانت ۱ و ۲", "بی‌حسی موضعی، پاک‌سازی عمیق زیر لثه، تسطیح سطح ریشه با قلم‌های کورت گرسی (Gracey Curettes) و شست‌وشو با کلرهگزیدین."),
                TherapySessionInfo("جلسه ۲ - SRP کوادرانت ۳ و ۴", "بی‌حسی و پاک‌سازی دو کوادرانت مقابل."),
                TherapySessionInfo("جلسه ۳ - ارزیابی مجدد (Re-evaluation)", "اندازه‌گیری مجدد عمق پاکت‌های پریودنتال پس از ۴ الی ۶ هفته.")
            ),
            precautions = listOf("کنترل بیماران با سابقه پریودنتیت پیشرفته و دیابت کنترل‌نشده."),
            prescriptions = listOf("ژل‌های موضعی کلرهگزیدین یا کلیندمایسین/مترونیدازول موضعي داخل پاکت."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("تحلیل لثه و نمایان شدن ریشه‌ها" to "توضیح به بیمار که این امر نتیجه کاهش تورم لثه و نشانه بهبودی است."),
            postSessionInstructions = listOf("شست‌وشوی مرتب دهان با دهان‌شویه آب‌نمک رقیق یا کلرهگزیدین."),
            sources = listOf("Carranza's Clinical Periodontology")
        ),

        // 20. پالپوتومی و عصب‌کشی دندان شیری
        TherapyProtocol(
            key = "pediatric_endo",
            matchKeywords = listOf("پالپوتومی", "دندان شیری", "pediatric", "pulpotomy", "اطفال"),
            nameFa = "عصب‌کشی دندان شیری / پالپوتومی (Pediatric Pulpotomy)",
            nameEn = "Pediatric Pulpotomy & Pulpectomy",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - پالپوتومی و ترمیم دندان شیری", "بی‌حسی موضعی با تکنیک بدون درد، ایزولاسیون، برداشت پوسیدگی و پالپ تاجی، کنترل خونریزی کانال‌ها با پنبه استریل، قرار دادن ماده فرموکرزول/MTA/ZOE روی کَف حفره و پرکردن تاج با ZOE/گلاس‌آینومر.")
            ),
            precautions = listOf("کنترل همکاری کودک و بکارگیری تکنیک‌های مدیریت رفتار (Tell-Show-Do)."),
            prescriptions = listOf("شربت ایبوپروفن یا استامینوفن اطفال دوز بر اساس وزن (10-15 mg/kg)."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("درد یا ابسه دندان شیری پس از پالپوتومی" to "در صورت بروز فیستول یا درد مداوم، تبدیل به پالپکتومی یا کشیدن دندان شیری و نصب اسپیس مینتینر."),
            postSessionInstructions = listOf("مراقبت از لب و گونه کودک جهت پرهیز از گازگرفتگی ناشی از بی‌حسی تا ۲ ساعت."),
            sources = listOf("McDonald and Avery's Dentistry for the Child and Adolescent")
        ),

        // 21. روکش استیل دندان شیری (SSC)
        TherapyProtocol(
            key = "ssc",
            matchKeywords = listOf("ssc", "روکش استیل", "روکش اطفال", "ss crown"),
            nameFa = "روکش استیل دندان شیری (Stainless Steel Crown - SSC)",
            nameEn = "Stainless Steel Crown (SSC)",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - آماده‌سازی و سمنتاسیون روکش", "بی‌حسی، کاهش ارتفاع اکلوزال دندان شیری (1-1.5mm)، تراش مختصر سطوح پروگزیمال، انتخاب سایز مناسب روکش استیل، کانتورینگ حواشی با انبر و سمنتاسیون با گلاس آینومر.")
            ),
            precautions = listOf("تطابق کامل حاشیه روکش با لثه جهت پرهیز از گیر غذایی و التهاب لثه."),
            prescriptions = listOf("شربت استامینوفن در صورت حس فشار اولیه روی لثه کودک."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("افتادن روکش شیری" to "تمیز کردن داخل روکش و سمنتاسیون مجدد."),
            postSessionInstructions = listOf("پرهیز از خوردن شکلات‌های بسیار چسبنده که ممکن است روکش را خارج کند."),
            sources = listOf("AAPD Pediatric Dentistry Guidelines")
        ),

        // 22. فیشور سیلانت
        TherapyProtocol(
            key = "fissure_sealant",
            matchKeywords = listOf("فیشور سیلانت", "شیارپوش", "sealant", "fissure"),
            nameFa = "فیشور سیلانت و پیشگیری (Fissure Sealant)",
            nameEn = "Dental Fissure Sealant",
            sessions = listOf(
                TherapySessionInfo("جلسه ۱ - بکارگیری شیارپوش پیشگیرانه", "تمیز کردن شیارهای دندان با بروساژ بدون خمیر، اچ اسیدی ۳۷٪ شیارها (۲۰ ثانیه)، شست‌وشوی کامل و خشک‌کردن با هوای بدون روغن، اعمال رزین فیشور سیلانت داخل شیارها، کیور نوری و تنظیم اکلوژن.")
            ),
            precautions = listOf("ایزولاسیون و خشک بودن کامل شیار پیش شرط نفوذ و پایداری سیلانت است."),
            prescriptions = listOf("نیاز به داروی مسکن ندارد."),
            patientHistoryFlags = commonHistoryFlags,
            complications = listOf("افتادن سیلانت" to "اچ مجدد و بکارگیری مجدد رزین سیلانت."),
            postSessionInstructions = listOf("معاینه ۶ ماهه جهت ارزیابی پایداری سیلانت روی دندان‌های آسیاب کودک."),
            sources = listOf("AAPD Pediatric Preventive Dentistry Guidelines")
        )
    )

    fun updateProtocol(updated: TherapyProtocol) {
        customProtocols[updated.key] = updated
    }

    fun findProtocol(therapyTitle: String): TherapyProtocol? {
        val t = therapyTitle.lowercase().trim()
        if (t.isBlank()) return null

        // Check edited/custom protocols first
        customProtocols.values.firstOrNull { protocol ->
            protocol.matchKeywords.any { kw -> t.contains(kw.lowercase()) } ||
            t.contains(protocol.nameFa.lowercase()) || protocol.nameFa.lowercase().contains(t)
        }?.let { return it }

        return protocols.firstOrNull { protocol ->
            protocol.matchKeywords.any { kw -> t.contains(kw.lowercase()) }
        } ?: protocols.firstOrNull { protocol ->
            val pName = protocol.nameFa.lowercase()
            t.contains(pName) || pName.contains(t)
        }
    }

    fun findProtocolsForSummary(treatmentTitle: String): List<TherapyProtocol> {
        val raw = treatmentTitle.trim()
        if (raw.isBlank()) return emptyList()

        val parts = raw.split(Regex("""[\+,\&\;\-\n\r]+""")).map { it.trim() }.filter { it.length >= 2 }
        val matchedList = mutableListOf<TherapyProtocol>()

        for (part in parts) {
            val matched = findProtocol(part)
            if (matched != null && !matchedList.contains(matched)) {
                matchedList.add(matched)
            }
        }

        val rawLower = raw.lowercase()
        protocols.forEach { proto ->
            if (!matchedList.contains(proto)) {
                if (proto.matchKeywords.any { kw -> rawLower.contains(kw.lowercase()) }) {
                    matchedList.add(proto)
                }
            }
        }

        return matchedList
    }

    fun matchingHistoryFlags(protocol: TherapyProtocol, patientMedicalHistory: String, patientMedicalAlerts: String): List<PatientHistoryFlag> {
        val combined = (patientMedicalHistory + " " + patientMedicalAlerts).lowercase()
        if (combined.isBlank()) return emptyList()
        return protocol.patientHistoryFlags.filter { flag -> flag.triggerKeywords.any { kw -> combined.contains(kw.lowercase()) } }
    }

    fun generateDischargeInstructions(
        treatmentTitle: String,
        patientName: String,
        clinicName: String,
        isPersian: Boolean
    ): String {
        val matchedProtocols = findProtocolsForSummary(treatmentTitle)
        val allInstructions = mutableListOf<String>()

        if (matchedProtocols.isNotEmpty()) {
            matchedProtocols.forEach { proto ->
                proto.postSessionInstructions.forEach { instr ->
                    if (!allInstructions.contains(instr)) {
                        allInstructions.add(instr)
                    }
                }
            }
        }

        if (allInstructions.isEmpty()) {
            allInstructions.add(
                if (isPersian) "رعایت کامل بهداشت دهان و دندان، مسواک زدن ملایم و مصرف به موقع داروهای تجویزی."
                else "Maintain oral hygiene, brush gently, and take prescribed medications as directed."
            )
        }

        val bulletPoints = allInstructions.joinToString("\n") { "• $it" }

        return if (isPersian) {
            "با سلام و احترام؛\nمراجع عزیز $patientName؛\nدستورالعمل و توصیه‌های مراقبت‌های بعد از درمان ($treatmentTitle) در مطب/کلینیک $clinicName:\n\n$bulletPoints\n\nدر صورت بروز هرگونه سوال، مسکن یا عارضه غیرعادی حتماً با مطب تماس بگیرید.\nبا آرزوی سلامتی و تندرستی شما."
        } else {
            "Dear $patientName,\nPost-care instructions for your dental procedure ($treatmentTitle) at $clinicName:\n\n$bulletPoints\n\nIf you experience unusual pain or have any questions, please contact our clinic.\nBest regards."
        }
    }
}
