package com.example.util

/**
 * Enterprise multi-language localization engine (i18n) supporting:
 * - Persian (fa - فارسی)
 * - English (en - English)
 * - Arabic (ar - العربية)
 * - Turkish (tr - Türkçe)
 * - Azerbaijani (az - Azərbaycanca)
 * - Chinese (zh - 中文)
 * - German (de - Deutsch)
 */
object AppLocalization {

    enum class SupportedLanguage(
        val code: String,
        val nativeName: String,
        val englishName: String,
        val flag: String,
        val isRtl: Boolean,
        val defaultCalendar: String, // "SHAMSI", "MILADI", "QAMARI"
        val defaultCurrency: String   // "toman", "usd", "sar", "try", "azn", "cny", "eur"
    ) {
        PERSIAN("fa", "فارسی", "Persian", "🇮🇷", true, "SHAMSI", "toman"),
        ENGLISH("en", "English", "English", "🇺🇸", false, "MILADI", "usd"),
        ARABIC("ar", "العربية", "Arabic", "🇸🇦", true, "QAMARI", "sar"),
        TURKISH("tr", "Türkçe", "Turkish", "🇹🇷", false, "MILADI", "try"),
        AZERBAIJANI("az", "Azərbaycanca", "Azerbaijani", "🇦🇿", false, "MILADI", "azn"),
        CHINESE("zh", "中文", "Chinese", "🇨🇳", false, "MILADI", "cny"),
        GERMAN("de", "Deutsch", "German", "🇩🇪", false, "MILADI", "eur");

        companion object {
            fun fromCode(code: String): SupportedLanguage {
                return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: PERSIAN
            }
        }
    }

    private val translations = mapOf(
        // Navigation & Modules
        "dashboard" to mapOf(
            "fa" to "داشبورد", "en" to "Dashboard", "ar" to "لوحة التحكم",
            "tr" to "Yönetim Paneli", "az" to "İdarəetmə Paneli", "zh" to "仪表板", "de" to "Dashboard"
        ),
        "patients" to mapOf(
            "fa" to "بیماران", "en" to "Patients", "ar" to "المرضى",
            "tr" to "Hastalar", "az" to "Xəstələr", "zh" to "患者档案", "de" to "Patienten"
        ),
        "appointments" to mapOf(
            "fa" to "نوبت‌ها", "en" to "Appointments", "ar" to "المواعيد",
            "tr" to "Randevular", "az" to "Qəbullar", "zh" to "预约日程", "de" to "Termine"
        ),
        "billing" to mapOf(
            "fa" to "امور مالی", "en" to "Financial", "ar" to "المالية",
            "tr" to "Mali İşler", "az" to "Maliyyə", "zh" to "财务结算", "de" to "Finanzen"
        ),
        "vendors" to mapOf(
            "fa" to "فروشندگان", "en" to "Vendors", "ar" to "الموردون",
            "tr" to "Tedarikçiler", "az" to "Təchizatçılar", "zh" to "供应商", "de" to "Lieferanten"
        ),
        "labs" to mapOf(
            "fa" to "لابراتوار", "en" to "Laboratory", "ar" to "المختبر",
            "tr" to "Laboratuvar", "az" to "Laboratoriya", "zh" to "技工所", "de" to "Dentallabor"
        ),
        "maintenance" to mapOf(
            "fa" to "تعمیرات", "en" to "Maintenance", "ar" to "الصيانة",
            "tr" to "Bakım & Onarım", "az" to "Texniki Xidmət", "zh" to "设备维护", "de" to "Wartung"
        ),
        "expenses" to mapOf(
            "fa" to "هزینه‌ها", "en" to "Expenses", "ar" to "المصروفات",
            "tr" to "Giderler", "az" to "Xərclər", "zh" to "支出费用", "de" to "Ausgaben"
        ),
        "personal" to mapOf(
            "fa" to "حساب شخصی", "en" to "Personal Accounts", "ar" to "الحساب الشخصي",
            "tr" to "Kişisel Hesap", "az" to "Şəxsi Hesab", "zh" to "个人账目", "de" to "Persönlich"
        ),
        "reports" to mapOf(
            "fa" to "گزارش‌ها", "en" to "Reports", "ar" to "التقارير",
            "tr" to "Raporlar", "az" to "Hesabatlar", "zh" to "统计报表", "de" to "Berichte"
        ),
        "settings" to mapOf(
            "fa" to "تنظیمات", "en" to "Settings", "ar" to "الإعدادات",
            "tr" to "Ayarlar", "az" to "Tənzimləmələr", "zh" to "系统设置", "de" to "Einstellungen"
        ),
        "bank" to mapOf(
            "fa" to "حساب‌های بانکی", "en" to "Bank Accounts", "ar" to "الحسابات البنكية",
            "tr" to "Banka Hesapları", "az" to "Bank Hesabları", "zh" to "银行账户", "de" to "Bankkonten"
        ),
        "cheques" to mapOf(
            "fa" to "مدیریت چک‌ها", "en" to "Cheques & Notes", "ar" to "إدارة الشيكات",
            "tr" to "Çek Yönetimi", "az" to "Çek İdarəsi", "zh" to "支票管理", "de" to "Schecks"
        ),
        "templates" to mapOf(
            "fa" to "قالب‌ها و تعرفه‌ها", "en" to "Templates & Pricing", "ar" to "النماذج والأسعار",
            "tr" to "Şablonlar ve Fiyatlar", "az" to "Şablonlar və Qiymətlər", "zh" to "模板与价格", "de" to "Vorlagen & Tarife"
        ),
        "more" to mapOf(
            "fa" to "امکانات بیشتر", "en" to "More Modules", "ar" to "المزيد",
            "tr" to "Daha Fazla", "az" to "Əlavə Bölmələr", "zh" to "更多功能", "de" to "Mehr"
        ),

        // Roles & Security
        "role_doctor" to mapOf(
            "fa" to "پزشک (مدیر درمان)", "en" to "Doctor (Lead)", "ar" to "طبيب (المدير الطبي)",
            "tr" to "Hekim (Klinik Yöneticisi)", "az" to "Həkim (Klinika Rəhbəri)", "zh" to "医生（主管）", "de" to "Arzt (Leitung)"
        ),
        "role_assistant" to mapOf(
            "fa" to "منشی / دستیار", "en" to "Secretary / Assistant", "ar" to "سكرتير / مساعد",
            "tr" to "Sekreter / Asistan", "az" to "Katib / Köməkçi", "zh" to "助理 / 秘书", "de" to "Assistenz"
        ),
        "role_accountant" to mapOf(
            "fa" to "حسابدار مطب", "en" to "Accountant", "ar" to "محاسب العيادة",
            "tr" to "Muhasebeci", "az" to "Mühasib", "zh" to "诊所会计", "de" to "Buchhalter"
        ),
        "switch_role" to mapOf(
            "fa" to "تغییر نقش کاربری", "en" to "Switch User Role", "ar" to "تبديل الدور",
            "tr" to "Kullanıcı Rolünü Değiştir", "az" to "İstifadəçi Rolunu Dəyiş", "zh" to "切换用户角色", "de" to "Rolle wechseln"
        ),
        "enter_doctor_pin" to mapOf(
            "fa" to "رمز عبور پزشک (مدیر درمان) را وارد کنید", "en" to "Enter Doctor (Admin) PIN", "ar" to "أدخل رمز PIN للطبيب المدير",
            "tr" to "Yönetici Hekim PIN kodunu giriniz", "az" to "Rəhbər Həkim PIN kodunu daxil edin", "zh" to "请输入主治医生 PIN 码", "de" to "Arzt-PIN eingeben"
        ),
        "doctor_pin_required" to mapOf(
            "fa" to "دسترسی به این بخش نیاز به تایید رمز عبور پزشک دارد", "en" to "Access to this section requires Doctor PIN verification", "ar" to "الوصول لهذا القسم يتطلب التحقق من رمز الطبيب",
            "tr" to "Bu bölüme erişim Hekim PIN onayı gerektirir", "az" to "Bu bölməyə giriş Həkim PIN təsdiqi tələb edir", "zh" to "访问此模块需要主管医生验证", "de" to "Dieser Bereich erfordert die PIN des Arztes"
        ),
        "pin_incorrect" to mapOf(
            "fa" to "رمز عبور وارد شده نادرست است!", "en" to "Incorrect PIN entered!", "ar" to "رمز PIN غير صحيح!",
            "tr" to "Girilen PIN kodu hatalı!", "az" to "Daxil edilən PIN kodu yanlışdır!", "zh" to "输入的 PIN 码错误！", "de" to "Falsche PIN eingegeben!"
        ),
        "role_switched_success" to mapOf(
            "fa" to "نقش با موفقیت تغییر کرد.", "en" to "Role switched successfully.", "ar" to "تم تغيير الدور بنجاح.",
            "tr" to "Rol başarıyla değiştirildi.", "az" to "Rol uğurla dəyişdirildi.", "zh" to "角色切换成功。", "de" to "Rolle erfolgreich gewechselt."
        ),
        "set_new_doctor_pin" to mapOf(
            "fa" to "تنظیم رمز عبور اولیه پزشک", "en" to "Set Initial Doctor PIN", "ar" to "تعيين رمز PIN الأولي للطبيب",
            "tr" to "İlk Hekim PIN Kodunu Belirle", "az" to "İlk Həkim PIN Kodunu Təyin Et", "zh" to "设置初始医生 PIN 码", "de" to "Erste Arzt-PIN festlegen"
        ),
        "define_pin_desc" to mapOf(
            "fa" to "جهت حفاظت از داده‌های محرمانه و جلوگیری از دسترسی بدون اجازه منشی به پنل پزشک، یک رمز ۴ رقمی تعیین کنید.",
            "en" to "Set a 4-digit PIN to secure confidential records and prevent unauthorized assistant access.",
            "ar" to "حدد رمز PIN لحماية السجلات ومنع وصول المساعد دون إذن.",
            "tr" to "Yetkisiz erişimi engellemek için 4 haneli bir PIN belirleyin.",
            "az" to "İcazəsiz girişin qarşısını almaq üçün 4 rəqəmli PIN təyin edin.",
            "zh" to "设置4位数字PIN码以防止未授权访问并保护病历隐私。",
            "de" to "Legen Sie eine 4-stellige PIN fest, um vertrauliche Daten zu schützen."
        ),

        // Actions & Buttons
        "save" to mapOf("fa" to "ذخیره", "en" to "Save", "ar" to "حفظ", "tr" to "Kaydet", "az" to "Yadda saxla", "zh" to "保存", "de" to "Speichern"),
        "cancel" to mapOf("fa" to "انصراف", "en" to "Cancel", "ar" to "إلغاء", "tr" to "İptal", "az" to "Ləğv et", "zh" to "取消", "de" to "Abbrechen"),
        "delete" to mapOf("fa" to "حذف", "en" to "Delete", "ar" to "حذف", "tr" to "Sil", "az" to "Sil", "zh" to "删除", "de" to "Löschen"),
        "edit" to mapOf("fa" to "ویرایش", "en" to "Edit", "ar" to "تعديل", "tr" to "Düzenle", "az" to "Düzəliş et", "zh" to "编辑", "de" to "Bearbeiten"),
        "search" to mapOf("fa" to "جستجو…", "en" to "Search…", "ar" to "بحث…", "tr" to "Ara…", "az" to "Axtar…", "zh" to "搜索…", "de" to "Suchen…"),
        "search_occasions" to mapOf(
            "fa" to "جستجوی مناسبت‌ها و اعیاد…", "en" to "Search occasions & holidays…", "ar" to "البحث في المناسبات والأعياد…",
            "tr" to "Özel gün ve bayramları ara…", "az" to "Özəl gün və bayramları axtar…", "zh" to "搜索节日与纪念日…", "de" to "Feiertage & Anlässe suchen…"
        ),
        "add_new" to mapOf("fa" to "افزودن جدید", "en" to "Add New", "ar" to "إضافة جديد", "tr" to "Yeni Ekle", "az" to "Yenisini əlavə et", "zh" to "新增", "de" to "Hinzufügen"),
        "new_patient" to mapOf("fa" to "بیمار جدید", "en" to "New Patient", "ar" to "مريض جديد", "tr" to "Yeni Hasta", "az" to "Yeni Xəstə", "zh" to "新患者", "de" to "Neuer Patient"),
        "new_appointment" to mapOf("fa" to "نوبت جدید", "en" to "New Appointment", "ar" to "موعد جديد", "tr" to "Yeni Randevu", "az" to "Yeni Qəbul", "zh" to "新预约", "de" to "Neuer Termin"),
        "share" to mapOf("fa" to "اشتراک‌گذاری", "en" to "Share", "ar" to "مشاركة", "tr" to "Paylaş", "az" to "Paylaş", "zh" to "分享", "de" to "Teilen"),
        "whatsapp" to mapOf("fa" to "واتساپ", "en" to "WhatsApp", "ar" to "واتساب", "tr" to "WhatsApp", "az" to "WhatsApp", "zh" to "WhatsApp", "de" to "WhatsApp"),
        "print" to mapOf("fa" to "چاپ", "en" to "Print", "ar" to "طباعة", "tr" to "Yazdır", "az" to "Çap et", "zh" to "打印", "de" to "Drucken"),
        "backup" to mapOf("fa" to "پشتیبان‌گیری", "en" to "Backup", "ar" to "نسخ احتياطي", "tr" to "Yedekle", "az" to "Nüsxələ", "zh" to "备份", "de" to "Sichern"),
        "restore" to mapOf("fa" to "بازیابی", "en" to "Restore", "ar" to "استعادة", "tr" to "Geri Yükle", "az" to "Bərpa et", "zh" to "恢复", "de" to "Wiederherstellen"),
        "confirm" to mapOf("fa" to "تایید", "en" to "Confirm", "ar" to "تأكيد", "tr" to "Onayla", "az" to "Təsdiq et", "zh" to "确认", "de" to "Bestätigen"),
        "close" to mapOf("fa" to "بستن", "en" to "Close", "ar" to "إغلاق", "tr" to "Kapat", "az" to "Bağla", "zh" to "关闭", "de" to "Schließen"),

        // Calendar & Dates
        "calendar_system" to mapOf(
            "fa" to "سیستم تقویم", "en" to "Calendar System", "ar" to "نظام التقويم",
            "tr" to "Takvim Sistemi", "az" to "Təqvim Sistemi", "zh" to "日历系统", "de" to "Kalendersystem"
        ),
        "currency_unit" to mapOf(
            "fa" to "واحد پولی", "en" to "Currency Unit", "ar" to "العملة",
            "tr" to "Para Birimi", "az" to "Valyuta Vahidi", "zh" to "货币单位", "de" to "Währung"
        ),
        "cal_shamsi" to mapOf(
            "fa" to "خورشیدی (هجری شمسی)", "en" to "Solar Hijri (Jalali)", "ar" to "الشمسي (الجلالي)",
            "tr" to "Güneş Hicri (Şemsi)", "az" to "Şəmsi (Cəlali)", "zh" to "太阳历（波斯历）", "de" to "Solar-Hidschri (Jalali)"
        ),
        "cal_miladi" to mapOf(
            "fa" to "میلادی (گرگوری)", "en" to "Gregorian (Western)", "ar" to "الميلادي (الغريغوري)",
            "tr" to "Miladi (Gregoryen)", "az" to "Miladi (Qriqorian)", "zh" to "公历（格里历）", "de" to "Gregorianisch"
        ),
        "cal_qamari" to mapOf(
            "fa" to "هجری قمری (اسلامی)", "en" to "Islamic Lunar (Hijri)", "ar" to "الهجري القمري (الإسلامي)",
            "tr" to "Hicri Kameri (İslami)", "az" to "Hicri Qəməri (İslami)", "zh" to "伊斯兰历（回历）", "de" to "Islamischer Mondkalender"
        ),
        "today" to mapOf("fa" to "امروز", "en" to "Today", "ar" to "اليوم", "tr" to "Bugün", "az" to "Bu gün", "zh" to "今天", "de" to "Heute"),
        "holiday" to mapOf("fa" to "تعطیل رسمی", "en" to "Official Holiday", "ar" to "عطلة رسمية", "tr" to "Resmi Tatil", "az" to "Rəsmi İstirahət", "zh" to "法定节假日", "de" to "Gesetzlicher Feiertag"),
        "occasions_events" to mapOf(
            "fa" to "مناسبت‌ها و تقویم رویدادها", "en" to "Occasions & Events Calendar", "ar" to "المناسبات وتقويم الفعاليات",
            "tr" to "Özel Günler ve Etkinlikler", "az" to "Özəl Günlər və Tədbirlər", "zh" to "节日与活动日程", "de" to "Anlässe & Veranstaltungskalender"
        ),
        "no_occasions_found" to mapOf(
            "fa" to "مناسبتی برای این روز یا عبارت یافت نشد.", "en" to "No occasions found for this query.", "ar" to "لم يتم العثور على مناسبات لهذا اليوم أو البحث.",
            "tr" to "Bu tarih veya arama için özel gün bulunamadı.", "az" to "Bu tarix və ya axtarış üçün qeyd tapılmadı.", "zh" to "未找到相关节日或纪念日。", "de" to "Keine Anlässe für diese Abfrage gefunden."
        ),

        // Financial & Currencies
        "curr_toman" to mapOf("fa" to "تومان", "en" to "Toman", "ar" to "تومان", "tr" to "Tümen", "az" to "Tümən", "zh" to "托曼", "de" to "Toman"),
        "curr_rial" to mapOf("fa" to "ریال", "en" to "Rial", "ar" to "ريال", "tr" to "Riyal", "az" to "Rial", "zh" to "里亚尔", "de" to "Rial"),
        "curr_usd" to mapOf("fa" to "دلار آمریکا ($)", "en" to "USD ($)", "ar" to "دولار أمريكي ($)", "tr" to "ABD Doları ($)", "az" to "ABŞ Dolları ($)", "zh" to "美元 ($)", "de" to "US-Dollar ($)"),
        "curr_eur" to mapOf("fa" to "یورو (€)", "en" to "EUR (€)", "ar" to "يورو (€)", "tr" to "Euro (€)", "az" to "Avro (€)", "zh" to "欧元 (€)", "de" to "Euro (€)"),
        "curr_try" to mapOf("fa" to "لیر ترکیه (₺)", "en" to "Turkish Lira (₺)", "ar" to "ليرة تركية (₺)", "tr" to "Türk Lirası (₺)", "az" to "Türkiyə Lirəsi (₺)", "zh" to "土耳其里拉 (₺)", "de" to "Türkische Lira (₺)"),
        "curr_azn" to mapOf("fa" to "منات آذربایجان (₼)", "en" to "Azerbaijani Manat (₼)", "ar" to "مانات أذربيجاني (₼)", "tr" to "Azerbaycan Manatı (₼)", "az" to "Azərbaycan Manatı (₼)", "zh" to "阿塞拜疆马纳特 (₼)", "de" to "Aserbaidschan-Manat (₼)"),
        "curr_cny" to mapOf("fa" to "یوان چین (¥)", "en" to "Chinese Yuan (¥)", "ar" to "يوان صيني (¥)", "tr" to "Çin Yuanı (¥)", "az" to "Çin Yuanı (¥)", "zh" to "人民币 (¥)", "de" to "Chinesischer Yuan (¥)"),
        "curr_sar" to mapOf("fa" to "ریال سعودی (SAR)", "en" to "Saudi Riyal (SAR)", "ar" to "ريال سعودي (ر.س)", "tr" to "Suudi Riyali (SAR)", "az" to "Səudiyyə Rialı (SAR)", "zh" to "沙特里亚尔 (SAR)", "de" to "Saudi-Riyal (SAR)"),

        // Stats & Dashboard
        "today_appointments" to mapOf(
            "fa" to "نوبت‌های امروز", "en" to "Today's Appointments", "ar" to "مواعيد اليوم",
            "tr" to "Bugünkü Randevular", "az" to "Bugünkü Qəbullar", "zh" to "今日预约", "de" to "Heutige Termine"
        ),
        "total_patients" to mapOf(
            "fa" to "کل بیماران", "en" to "Total Patients", "ar" to "إجمالي المرضى",
            "tr" to "Toplam Hasta", "az" to "Ümumi Xəstələr", "zh" to "患者总数", "de" to "Gesamte Patienten"
        ),
        "pending_payments" to mapOf(
            "fa" to "مطالبات / مانده بدهی", "en" to "Pending Receivables", "ar" to "المستحقات المتبقية",
            "tr" to "Bekleyen Alacaklar", "az" to "Gözlənilən Ödənişlər", "zh" to "待收账款", "de" to "Offene Forderungen"
        ),
        "monthly_income" to mapOf(
            "fa" to "درآمد ماهانه", "en" to "Monthly Income", "ar" to "الدخل الشهري",
            "tr" to "Aylık Gelir", "az" to "Aylıq Gəlir", "zh" to "月度收入", "de" to "Monatseinkommen"
        ),
        "monthly_expense" to mapOf(
            "fa" to "هزینه‌های ماه", "en" to "Monthly Expenses", "ar" to "مصروفات الشهر",
            "tr" to "Aylık Giderler", "az" to "Aylıq Xərclər", "zh" to "月度支出", "de" to "Monatsausgaben"
        ),
        "net_profit" to mapOf(
            "fa" to "سود خالص", "en" to "Net Profit", "ar" to "صافي الأرباح",
            "tr" to "Net Kâr", "az" to "Xalis Gəlir", "zh" to "净利润", "de" to "Nettogewinn"
        ),

        // Quick Clinical
        "quick_actions" to mapOf(
            "fa" to "دسترسی سریع", "en" to "Quick Actions", "ar" to "إجراءات سريعة",
            "tr" to "Hızlı İşlemler", "az" to "Sürətli Əməliyyatlar", "zh" to "快捷操作", "de" to "Schnellaktionen"
        ),
        "clinic_status" to mapOf(
            "fa" to "وضعیت کلینیک", "en" to "Clinic Status", "ar" to "حالة العيادة",
            "tr" to "Klinik Durumu", "az" to "Klinika Vəziyyəti", "zh" to "诊所状态", "de" to "Praxisstatus"
        ),
        "language" to mapOf(
            "fa" to "زبان برنامه", "en" to "App Language", "ar" to "لغة التطبيق",
            "tr" to "Uygulama Dili", "az" to "Tətbiq Dili", "zh" to "应用语言", "de" to "App-Sprache"
        )
    )

    fun get(key: String, langCode: String): String {
        val entry = translations[key] ?: return key
        return entry[langCode] ?: entry["en"] ?: entry["fa"] ?: key
    }

    fun getWeekdayName(langCode: String, dayIndex: Int): String {
        // dayIndex 0 to 6
        return when (langCode.lowercase()) {
            "fa" -> listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه").getOrElse(dayIndex) { "" }
            "ar" -> listOf("السبت", "الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة").getOrElse(dayIndex) { "" }
            "tr" -> listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar").getOrElse(dayIndex) { "" }
            "az" -> listOf("Bazar ertəsi", "Çərşənbə axşamı", "Çərşənbə", "Cümə axşamı", "Cümə", "Şənbə", "Bazar").getOrElse(dayIndex) { "" }
            "zh" -> listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").getOrElse(dayIndex) { "" }
            "de" -> listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag").getOrElse(dayIndex) { "" }
            else -> listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").getOrElse(dayIndex) { "" }
        }
    }

    fun getMonthName(langCode: String, month: Int, calType: String = "MILADI"): String {
        val m = (month - 1).coerceIn(0, 11)
        if (calType == "SHAMSI") {
            return PersianCalendarHelper.MONTH_NAMES.getOrElse(m) { "" }
        }
        if (calType == "QAMARI") {
            return when (langCode) {
                "ar" -> listOf("محرم", "صفر", "ربيع الأول", "ربيع الثاني", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة").getOrElse(m) { "" }
                else -> PersianCalendarHelper.QAMARI_MONTH_NAMES.getOrElse(m) { "" }
            }
        }
        return when (langCode) {
            "fa" -> GregorianCalendarSystem.MONTH_NAMES_FA.getOrElse(m) { "" }
            "ar" -> listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر").getOrElse(m) { "" }
            "tr" -> listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık").getOrElse(m) { "" }
            "az" -> listOf("Yanvar", "Fevral", "Mart", "Aprel", "May", "İyun", "İyul", "Avqust", "Sentyabr", "Oktyabr", "Noyabr", "Dekabr").getOrElse(m) { "" }
            "zh" -> listOf("一月", "二月", "三月", "四月", "五月", "六月", "七月", "八月", "九月", "十月", "十一月", "十二月").getOrElse(m) { "" }
            "de" -> listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember").getOrElse(m) { "" }
            else -> listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December").getOrElse(m) { "" }
        }
    }
}
