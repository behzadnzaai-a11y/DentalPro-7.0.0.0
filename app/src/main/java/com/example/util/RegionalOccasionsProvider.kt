package com.example.util

data class RegionalOccasion(
    val title: String,
    val category: String,
    val isHoliday: Boolean,
    val shamsiDay: Int? = null,
    val shamsiMonth: Int? = null,
    val qamariDay: Int? = null,
    val qamariMonth: Int? = null,
    val miladiDay: Int? = null,
    val miladiMonth: Int? = null,
    val targetLanguage: String, // "fa", "ar", "az", "zh", "tr", "de", "en"
    val description: String = ""
)

object RegionalOccasionsProvider {

    // Arabic Holidays (Hijri Lunar & Islamic)
    val ARABIC_OCCASIONS = listOf(
        RegionalOccasion(title = "1 شوال: عيد الفطر المبارك", category = "إسلامي", isHoliday = true, qamariDay = 1, qamariMonth = 10, targetLanguage = "ar", description = "عيد الفطر السعيد"),
        RegionalOccasion(title = "2 شوال: ثاني أيام عيد الفطر", category = "إسلامي", isHoliday = true, qamariDay = 2, qamariMonth = 10, targetLanguage = "ar"),
        RegionalOccasion(title = "3 شوال: ثالث أيام عيد الفطر", category = "إسلامي", isHoliday = true, qamariDay = 3, qamariMonth = 10, targetLanguage = "ar"),
        RegionalOccasion(title = "9 ذو الحجة: يوم عرفة", category = "إسلامي", isHoliday = true, qamariDay = 9, qamariMonth = 12, targetLanguage = "ar", description = "وقفة عرفات المباركة"),
        RegionalOccasion(title = "10 ذو الحجة: عيد الأضحى المبارك", category = "إسلامي", isHoliday = true, qamariDay = 10, qamariMonth = 12, targetLanguage = "ar", description = "عيد الأضحى الكبير"),
        RegionalOccasion(title = "11 ذو الحجة: ثاني أيام عيد الأضحى", category = "إسلامي", isHoliday = true, qamariDay = 11, qamariMonth = 12, targetLanguage = "ar"),
        RegionalOccasion(title = "12 ذو الحجة: ثالث أيام عيد الأضحى", category = "إسلامي", isHoliday = true, qamariDay = 12, qamariMonth = 12, targetLanguage = "ar"),
        RegionalOccasion(title = "1 محرم: رأس السنة الهجرية", category = "إسلامي", isHoliday = true, qamariDay = 1, qamariMonth = 1, targetLanguage = "ar", description = "بداية العام الهجري الجديد"),
        RegionalOccasion(title = "10 محرم: يوم عاشوراء", category = "إسلامي", isHoliday = false, qamariDay = 10, qamariMonth = 1, targetLanguage = "ar"),
        RegionalOccasion(title = "12 ربيع الأول: المولد النبوي الشريف", category = "إسلامي", isHoliday = true, qamariDay = 12, qamariMonth = 3, targetLanguage = "ar", description = "ذكرى ميلاد النبي الأكرم ﷺ"),
        RegionalOccasion(title = "27 رجب: ذكرى الإسراء والمعراج", category = "إسلامي", isHoliday = true, qamariDay = 27, qamariMonth = 7, targetLanguage = "ar", description = "معراج النبي ﷺ إلى السماوات العلى"),
        RegionalOccasion(title = "15 شعبان: ليلة النصف من شعبان", category = "إسلامي", isHoliday = false, qamariDay = 15, qamariMonth = 8, targetLanguage = "ar"),
        RegionalOccasion(title = "1 رمضان: أول أيام شهر رمضان المبارك", category = "إسلامي", isHoliday = false, qamariDay = 1, qamariMonth = 9, targetLanguage = "ar")
    )

    // Azerbaijan Holidays (Miladi + Qamari)
    val AZERBAIJAN_OCCASIONS = listOf(
        RegionalOccasion(title = "Yeni il bayramı", category = "Dövlət", isHoliday = true, miladiDay = 1, miladiMonth = 1, targetLanguage = "az"),
        RegionalOccasion(title = "Yeni il bayramı (2-ci gün)", category = "Dövlət", isHoliday = true, miladiDay = 2, miladiMonth = 1, targetLanguage = "az"),
        RegionalOccasion(title = "Ümumxalq Hüzn Günü (20 Yanvar)", category = "Milli Anım", isHoliday = true, miladiDay = 20, miladiMonth = 1, targetLanguage = "az"),
        RegionalOccasion(title = "Beynəlxalq Qadınlar Günü", category = "Beynəlxalq", isHoliday = true, miladiDay = 8, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Novruz bayramı", category = "Milli", isHoliday = true, miladiDay = 20, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Novruz bayramı (Tətil)", category = "Milli", isHoliday = true, miladiDay = 21, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Novruz bayramı (Tətil)", category = "Milli", isHoliday = true, miladiDay = 22, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Novruz bayramı (Tətil)", category = "Milli", isHoliday = true, miladiDay = 23, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Novruz bayramı (Tətil)", category = "Milli", isHoliday = true, miladiDay = 24, miladiMonth = 3, targetLanguage = "az"),
        RegionalOccasion(title = "Faşizm üzərində Qələbə Günü", category = "Dövlət", isHoliday = true, miladiDay = 9, miladiMonth = 5, targetLanguage = "az"),
        RegionalOccasion(title = "Müstəqillik Günü (28 May)", category = "Dövlət", isHoliday = true, miladiDay = 28, miladiMonth = 5, targetLanguage = "az"),
        RegionalOccasion(title = "Milli Qurtuluş Günü", category = "Dövlət", isHoliday = true, miladiDay = 15, miladiMonth = 6, targetLanguage = "az"),
        RegionalOccasion(title = "Azərbaycan Respublikasının Silahlı Qüvvələri Günü", category = "Dövlət", isHoliday = true, miladiDay = 26, miladiMonth = 6, targetLanguage = "az"),
        RegionalOccasion(title = "Zəfər Günü (8 Noyabr)", category = "Dövlət", isHoliday = true, miladiDay = 8, miladiMonth = 11, targetLanguage = "az"),
        RegionalOccasion(title = "Dövlət Bayrağı Günü (9 Noyabr)", category = "Dövlət", isHoliday = true, miladiDay = 9, miladiMonth = 11, targetLanguage = "az"),
        RegionalOccasion(title = "Dünya Azərbaycanlılarının Həmrəyliyi Günü", category = "Milli", isHoliday = true, miladiDay = 31, miladiMonth = 12, targetLanguage = "az"),
        // Qamari
        RegionalOccasion(title = "Ramazan Bayramı (1 Şəvval)", category = "Dini", isHoliday = true, qamariDay = 1, qamariMonth = 10, targetLanguage = "az"),
        RegionalOccasion(title = "Ramazan Bayramı (2-ci gün)", category = "Dini", isHoliday = true, qamariDay = 2, qamariMonth = 10, targetLanguage = "az"),
        RegionalOccasion(title = "Qurban Bayramı (10 Zilhiccə)", category = "Dini", isHoliday = true, qamariDay = 10, qamariMonth = 12, targetLanguage = "az"),
        RegionalOccasion(title = "Qurban Bayramı (2-ci gün)", category = "Dini", isHoliday = true, qamariDay = 11, qamariMonth = 12, targetLanguage = "az")
    )

    // China Holidays (Miladi + Chinese Calendar Events)
    val CHINA_OCCASIONS = listOf(
        RegionalOccasion(title = "元旦 (New Year's Day)", category = "法定节假日", isHoliday = true, miladiDay = 1, miladiMonth = 1, targetLanguage = "zh"),
        RegionalOccasion(title = "清明节 (Tomb Sweeping Day)", category = "传统节日", isHoliday = true, miladiDay = 4, miladiMonth = 4, targetLanguage = "zh"),
        RegionalOccasion(title = "劳动节 (International Workers' Day)", category = "法定节假日", isHoliday = true, miladiDay = 1, miladiMonth = 5, targetLanguage = "zh"),
        RegionalOccasion(title = "青年节 (Youth Day)", category = "纪念日", isHoliday = false, miladiDay = 4, miladiMonth = 5, targetLanguage = "zh"),
        RegionalOccasion(title = "儿童节 (Children's Day)", category = "纪念日", isHoliday = false, miladiDay = 1, miladiMonth = 6, targetLanguage = "zh"),
        RegionalOccasion(title = "建军节 (Army Day)", category = "纪念日", isHoliday = false, miladiDay = 1, miladiMonth = 8, targetLanguage = "zh"),
        RegionalOccasion(title = "教师节 (Teachers' Day)", category = "纪念日", isHoliday = false, miladiDay = 10, miladiMonth = 9, targetLanguage = "zh"),
        RegionalOccasion(title = "国庆节 (National Day)", category = "法定节假日", isHoliday = true, miladiDay = 1, miladiMonth = 10, targetLanguage = "zh"),
        RegionalOccasion(title = "国庆节假期", category = "法定节假日", isHoliday = true, miladiDay = 2, miladiMonth = 10, targetLanguage = "zh"),
        RegionalOccasion(title = "国庆节假期", category = "法定节假日", isHoliday = true, miladiDay = 3, miladiMonth = 10, targetLanguage = "zh"),
        // Lunar key references translated for common solar dates / representations
        RegionalOccasion(title = "春节 (Chinese Lunar New Year)", category = "法定节假日", isHoliday = true, miladiDay = 17, miladiMonth = 2, targetLanguage = "zh"),
        RegionalOccasion(title = "元宵节 (Lantern Festival)", category = "传统节日", isHoliday = false, miladiDay = 3, miladiMonth = 3, targetLanguage = "zh"),
        RegionalOccasion(title = "端午节 (Dragon Boat Festival)", category = "法定节假日", isHoliday = true, miladiDay = 19, miladiMonth = 6, targetLanguage = "zh"),
        RegionalOccasion(title = "中秋节 (Mid-Autumn Festival)", category = "法定节假日", isHoliday = true, miladiDay = 25, miladiMonth = 9, targetLanguage = "zh")
    )

    // Turkey Holidays (Miladi + Qamari)
    val TURKEY_OCCASIONS = listOf(
        RegionalOccasion(title = "Yılbaşı (1 Ocak)", category = "Resmi Tatil", isHoliday = true, miladiDay = 1, miladiMonth = 1, targetLanguage = "tr"),
        RegionalOccasion(title = "Ulusal Egemenlik ve Çocuk Bayramı (23 Nisan)", category = "Milli Bayram", isHoliday = true, miladiDay = 23, miladiMonth = 4, targetLanguage = "tr"),
        RegionalOccasion(title = "Emek ve Dayanışma Günü (1 Mayıs)", category = "Resmi Tatil", isHoliday = true, miladiDay = 1, miladiMonth = 5, targetLanguage = "tr"),
        RegionalOccasion(title = "Atatürk'ü Anma, Gençlik ve Spor Bayramı (19 Mayıs)", category = "Milli Bayram", isHoliday = true, miladiDay = 19, miladiMonth = 5, targetLanguage = "tr"),
        RegionalOccasion(title = "Demokrasi ve Milli Birlik Günü (15 Temmuz)", category = "Milli Gün", isHoliday = true, miladiDay = 15, miladiMonth = 7, targetLanguage = "tr"),
        RegionalOccasion(title = "Zafer Bayramı (30 Ağustos)", category = "Milli Bayram", isHoliday = true, miladiDay = 30, miladiMonth = 8, targetLanguage = "tr"),
        RegionalOccasion(title = "Cumhuriyet Bayramı (29 Ekim)", category = "Milli Bayram", isHoliday = true, miladiDay = 29, miladiMonth = 10, targetLanguage = "tr"),
        // Qamari
        RegionalOccasion(title = "Ramazan Bayramı (1 Şevval)", category = "Dini Bayram", isHoliday = true, qamariDay = 1, qamariMonth = 10, targetLanguage = "tr"),
        RegionalOccasion(title = "Ramazan Bayramı (2. Gün)", category = "Dini Bayram", isHoliday = true, qamariDay = 2, qamariMonth = 10, targetLanguage = "tr"),
        RegionalOccasion(title = "Ramazan Bayramı (3. Gün)", category = "Dini Bayram", isHoliday = true, qamariDay = 3, qamariMonth = 10, targetLanguage = "tr"),
        RegionalOccasion(title = "Kurban Bayramı (10 Zilhicce)", category = "Dini Bayram", isHoliday = true, qamariDay = 10, qamariMonth = 12, targetLanguage = "tr"),
        RegionalOccasion(title = "Kurban Bayramı (2. Gün)", category = "Dini Bayram", isHoliday = true, qamariDay = 11, qamariMonth = 12, targetLanguage = "tr"),
        RegionalOccasion(title = "Kurban Bayramı (3. Gün)", category = "Dini Bayram", isHoliday = true, qamariDay = 12, qamariMonth = 12, targetLanguage = "tr"),
        RegionalOccasion(title = "Kurban Bayramı (4. Gün)", category = "Dini Bayram", isHoliday = true, qamariDay = 13, qamariMonth = 12, targetLanguage = "tr"),
        RegionalOccasion(title = "Hicri Yılbaşı (1 Muharrem)", category = "Dini Gün", isHoliday = false, qamariDay = 1, qamariMonth = 1, targetLanguage = "tr"),
        RegionalOccasion(title = "Mevlid Kandili (12 Rebiülevvel)", category = "Dini Gün", isHoliday = false, qamariDay = 12, qamariMonth = 3, targetLanguage = "tr"),
        RegionalOccasion(title = "Miraç Kandili (27 Recep)", category = "Dini Gün", isHoliday = false, qamariDay = 27, qamariMonth = 7, targetLanguage = "tr")
    )

    // Europe / Germany / International (Miladi)
    val EUROPE_OCCASIONS = listOf(
        RegionalOccasion(title = "Neujahr (New Year's Day)", category = "Gesetzlicher Feiertag", isHoliday = true, miladiDay = 1, miladiMonth = 1, targetLanguage = "de"),
        RegionalOccasion(title = "Karfreitag (Good Friday)", category = "Kirchlicher Feiertag", isHoliday = true, miladiDay = 3, miladiMonth = 4, targetLanguage = "de"),
        RegionalOccasion(title = "Ostermontag (Easter Monday)", category = "Kirchlicher Feiertag", isHoliday = true, miladiDay = 6, miladiMonth = 4, targetLanguage = "de"),
        RegionalOccasion(title = "Tag der Arbeit (Labour Day)", category = "Gesetzlicher Feiertag", isHoliday = true, miladiDay = 1, miladiMonth = 5, targetLanguage = "de"),
        RegionalOccasion(title = "Europatag (Europe Day)", category = "Gedenktag", isHoliday = false, miladiDay = 9, miladiMonth = 5, targetLanguage = "de"),
        RegionalOccasion(title = "Christi Himmelfahrt (Ascension Day)", category = "Kirchlicher Feiertag", isHoliday = true, miladiDay = 14, miladiMonth = 5, targetLanguage = "de"),
        RegionalOccasion(title = "Pfingstmontag (Whit Monday)", category = "Kirchlicher Feiertag", isHoliday = true, miladiDay = 25, miladiMonth = 5, targetLanguage = "de"),
        RegionalOccasion(title = "Tag der Deutschen Einheit (German Unity Day)", category = "Nationalfeiertag", isHoliday = true, miladiDay = 3, miladiMonth = 10, targetLanguage = "de"),
        RegionalOccasion(title = "Reformationstag / Halloween", category = "Feiertag", isHoliday = false, miladiDay = 31, miladiMonth = 10, targetLanguage = "de"),
        RegionalOccasion(title = "Allerheiligen (All Saints' Day)", category = "Feiertag", isHoliday = false, miladiDay = 1, miladiMonth = 11, targetLanguage = "de"),
        RegionalOccasion(title = "Heiliger Abend (Christmas Eve)", category = "Brauchtum", isHoliday = false, miladiDay = 24, miladiMonth = 12, targetLanguage = "de"),
        RegionalOccasion(title = "1. Weihnachtstag (Christmas Day)", category = "Gesetzlicher Feiertag", isHoliday = true, miladiDay = 25, miladiMonth = 12, targetLanguage = "de"),
        RegionalOccasion(title = "2. Weihnachtstag (Boxing Day)", category = "Gesetzlicher Feiertag", isHoliday = true, miladiDay = 26, miladiMonth = 12, targetLanguage = "de"),
        RegionalOccasion(title = "Silvester (New Year's Eve)", category = "Brauchtum", isHoliday = false, miladiDay = 31, miladiMonth = 12, targetLanguage = "de")
    )

    // English / Global Occasions
    val ENGLISH_OCCASIONS = listOf(
        RegionalOccasion(title = "New Year's Day", category = "Public Holiday", isHoliday = true, miladiDay = 1, miladiMonth = 1, targetLanguage = "en"),
        RegionalOccasion(title = "Martin Luther King Jr. Day", category = "Federal Holiday", isHoliday = true, miladiDay = 19, miladiMonth = 1, targetLanguage = "en"),
        RegionalOccasion(title = "Valentine's Day", category = "Observance", isHoliday = false, miladiDay = 14, miladiMonth = 2, targetLanguage = "en"),
        RegionalOccasion(title = "Good Friday", category = "Public Holiday", isHoliday = true, miladiDay = 3, miladiMonth = 4, targetLanguage = "en"),
        RegionalOccasion(title = "Easter Monday", category = "Public Holiday", isHoliday = true, miladiDay = 6, miladiMonth = 4, targetLanguage = "en"),
        RegionalOccasion(title = "International Workers' Day", category = "Public Holiday", isHoliday = true, miladiDay = 1, miladiMonth = 5, targetLanguage = "en"),
        RegionalOccasion(title = "Independence Day (4th of July)", category = "Federal Holiday", isHoliday = true, miladiDay = 4, miladiMonth = 7, targetLanguage = "en"),
        RegionalOccasion(title = "Labor Day", category = "Federal Holiday", isHoliday = true, miladiDay = 7, miladiMonth = 9, targetLanguage = "en"),
        RegionalOccasion(title = "Halloween", category = "Observance", isHoliday = false, miladiDay = 31, miladiMonth = 10, targetLanguage = "en"),
        RegionalOccasion(title = "Veterans Day", category = "Federal Holiday", isHoliday = true, miladiDay = 11, miladiMonth = 11, targetLanguage = "en"),
        RegionalOccasion(title = "Thanksgiving Day", category = "Federal Holiday", isHoliday = true, miladiDay = 26, miladiMonth = 11, targetLanguage = "en"),
        RegionalOccasion(title = "Christmas Eve", category = "Observance", isHoliday = false, miladiDay = 24, miladiMonth = 12, targetLanguage = "en"),
        RegionalOccasion(title = "Christmas Day", category = "Federal Holiday", isHoliday = true, miladiDay = 25, miladiMonth = 12, targetLanguage = "en"),
        RegionalOccasion(title = "Boxing Day", category = "Public Holiday", isHoliday = true, miladiDay = 26, miladiMonth = 12, targetLanguage = "en"),
        RegionalOccasion(title = "New Year's Eve", category = "Observance", isHoliday = false, miladiDay = 31, miladiMonth = 12, targetLanguage = "en")
    )

    fun getOccasionsForLanguage(langCode: String): List<RegionalOccasion> {
        return when (langCode.lowercase()) {
            "ar" -> ARABIC_OCCASIONS
            "az" -> AZERBAIJAN_OCCASIONS
            "zh" -> CHINA_OCCASIONS
            "tr" -> TURKEY_OCCASIONS
            "de" -> EUROPE_OCCASIONS
            "en" -> ENGLISH_OCCASIONS
            else -> emptyList() // For "fa", it uses the full comprehensive Iranian DB events
        }
    }

    fun getDayOccasionsForLanguage(
        langCode: String,
        gd: Int,
        gm: Int,
        gy: Int,
        qd: Int?,
        qm: Int?
    ): List<RegionalOccasion> {
        val list = getOccasionsForLanguage(langCode)
        return list.filter { event ->
            var matched = false
            if (event.miladiDay == gd && event.miladiMonth == gm) {
                matched = true
            }
            if (qd != null && qm != null && event.qamariDay == qd && event.qamariMonth == qm) {
                matched = true
            }
            matched
        }
    }

    fun searchOccasions(langCode: String, query: String): List<RegionalOccasion> {
        if (query.isBlank()) return getOccasionsForLanguage(langCode)
        val clean = query.trim().lowercase()
        return getOccasionsForLanguage(langCode).filter {
            it.title.lowercase().contains(clean) ||
            it.category.lowercase().contains(clean) ||
            it.description.lowercase().contains(clean)
        }
    }
}
