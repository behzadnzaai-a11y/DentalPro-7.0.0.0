package com.example.ui.screens

data class CanalAnatomySpec(
    val name: String, 
    val minLength: Double, 
    val maxLength: Double, 
    val avgLength: Double, 
    val refPoint: String, 
    val mafFile: String, 
    val isDefault: Boolean = true,
    val probabilityNote: String? = null
)

object EndoAnatomyManager {
    var selectedRegion: String = "ایران - شمال‌غرب (پیش‌فرض)"

    fun getAvailableRegions(): List<String> {
        return listOf(
            "ایران - شمال‌غرب (پیش‌فرض)",
            "ایران - مرکز",
            "جهانی (Standard)"
        )
    }

    fun getProbabilityNote(defaultNote: String?): String? {
        if (defaultNote == null) return null
        if (selectedRegion.contains("جهانی")) {
             return defaultNote.replace("در ایران", "در سطح جهانی")
        }
        return defaultNote
    }

    private val overrides = mutableMapOf<String, String>()

    fun saveOverrideForTooth(region: String, tooth: Int, canals: List<CanalAnatomySpec>) {
        overrides["${region}_$tooth"] = canals.joinToString(";") { "${it.name}:${it.avgLength}:${it.mafFile}" }
    }

    fun getOverridesJsonString(): String {
        val json = org.json.JSONObject()
        overrides.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    fun canalSpecsForTooth(toothNumber: Int, treatmentTitle: String = ""): List<CanalAnatomySpec> {
        val isPrimaryTreatment = treatmentTitle.contains("شیری") || treatmentTitle.contains("پالپوتومی") || treatmentTitle.contains("pediatric", ignoreCase = true)
        var effectiveTooth = toothNumber
        if (isPrimaryTreatment && toothNumber in 11..48) {
            if (toothNumber in 11..15) effectiveTooth = toothNumber + 40
            else if (toothNumber in 21..25) effectiveTooth = toothNumber + 40
            else if (toothNumber in 31..35) effectiveTooth = toothNumber + 40
            else if (toothNumber in 41..45) effectiveTooth = toothNumber + 40
        }
        val list = when (effectiveTooth) {
            // ===== فک بالا - دائمی (Maxillary Permanent) =====
            11, 21 -> listOf(CanalAnatomySpec("C", 21.0, 26.0, 23.5, "Incisal Edge", "#40", true, "همیشه حاضر"))
            12, 22 -> listOf(
                CanalAnatomySpec("C", 20.0, 25.0, 22.5, "Incisal Edge", "#35", true, "همیشه حاضر"),
                CanalAnatomySpec("P", 20.0, 25.0, 22.5, "Incisal Edge", "#35", false, "فراوانی ۱ الی ۳ درصد موارد در ایران")
            )
            13, 23 -> listOf(
                CanalAnatomySpec("C", 23.0, 30.0, 26.5, "Cusp Tip", "#45", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 23.0, 30.0, 26.5, "Cusp Tip", "#45", false, "فراوانی کانال دوم ۱ الی ۲ درصد موارد در ایران")
            )
            14, 24 -> listOf(
                CanalAnatomySpec("B", 19.0, 23.0, 21.0, "Buccal Cusp", "#25", true, "همیشه حاضر"),
                CanalAnatomySpec("P", 19.0, 23.0, 21.0, "Palatal Cusp", "#25", false, "فراوانی ۸۵ درصد موارد در ایران"),
                CanalAnatomySpec("C3", 18.0, 22.0, 20.0, "Cusp Tip", "#25", false, "فراوانی ۲ الی ۳ درصد موارد در ایران")
            )
            15, 25 -> listOf(
                CanalAnatomySpec("C", 19.0, 24.0, 21.5, "Cusp Tip", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("P", 19.0, 24.0, 21.5, "Palatal Cusp", "#30", false, "فراوانی ۲۵ درصد موارد در ایران")
            )
            16, 26 -> listOf(
                CanalAnatomySpec("MB1", 19.0, 22.0, 20.5, "MB Cusp", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("MB2", 18.0, 21.0, 19.5, "MB Cusp", "#25", false, "فراوانی ۶۵ الی ۸۰ درصد موارد در ایران"),
                CanalAnatomySpec("DB", 18.0, 21.0, 19.5, "DB Cusp", "#25", true, "همیشه حاضر"),
                CanalAnatomySpec("P", 20.0, 24.0, 22.0, "Palatal Cusp", "#40", true, "همیشه حاضر (طولانی‌ترین کانال)")
            )
            17, 27 -> listOf(
                CanalAnatomySpec("MB1", 18.0, 22.0, 20.0, "MB Cusp", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("MB2", 17.0, 21.0, 19.0, "MB Cusp", "#25", false, "فراوانی ۳۰ الی ۴۰ درصد موارد در ایران"),
                CanalAnatomySpec("DB", 18.0, 22.0, 20.0, "DB Cusp", "#25", true, "همیشه حاضر"),
                CanalAnatomySpec("P", 18.0, 22.0, 20.0, "Palatal Cusp", "#40", true, "همیشه حاضر")
            )
            18, 28 -> listOf(
                CanalAnatomySpec("MB", 19.0, 22.0, 19.0, "MB Cusp", "#30", true),
                CanalAnatomySpec("DB", 19.0, 22.0, 19.0, "DB Cusp", "#30", true),
                CanalAnatomySpec("P", 19.0, 22.0, 19.5, "Palatal Cusp", "#30", true)
            )

            // ===== فک پایین - دائمی (Mandibular Permanent) =====
            31, 41 -> listOf(
                CanalAnatomySpec("B", 19.0, 23.0, 21.0, "Incisal Edge", "#35", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 19.0, 23.0, 21.0, "Incisal Edge", "#35", false, "فراوانی ۲۰ الی ۳۵ درصد موارد در ایران")
            )
            32, 42 -> listOf(
                CanalAnatomySpec("B", 20.0, 24.0, 22.0, "Incisal Edge", "#35", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 20.0, 24.0, 22.0, "Incisal Edge", "#35", false, "فراوانی ۱۵ الی ۳۰ درصد موارد در ایران")
            )
            33, 43 -> listOf(
                CanalAnatomySpec("C", 22.0, 28.0, 25.0, "Cusp Tip", "#45", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 22.0, 28.0, 25.0, "Cusp Tip", "#45", false, "فراوانی ۲ الی ۵ درصد موارد در ایران")
            )
            34, 44 -> listOf(
                CanalAnatomySpec("B", 20.0, 24.0, 22.0, "Buccal Cusp", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 20.0, 24.0, 22.0, "Lingual Cusp", "#30", false, "فراوانی ۲۵ الی ۳۰ درصد موارد در ایران")
            )
            35, 45 -> listOf(
                CanalAnatomySpec("C", 20.0, 25.0, 22.5, "Buccal Cusp", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("L", 20.0, 25.0, 22.5, "Lingual Cusp", "#30", false, "فراوانی ۱۰ الی ۱۵ درصد موارد در ایران")
            )
            36, 46 -> listOf(
                CanalAnatomySpec("MB", 20.0, 23.0, 21.5, "MB Cusp", "#30", true, "همیشه حاضر (بخشی از ریشه مزیال)"),
                CanalAnatomySpec("ML", 20.0, 23.0, 21.5, "ML Cusp", "#25", true, "همیشه حاضر"),
                CanalAnatomySpec("D", 19.0, 23.0, 21.0, "Distal Cusp", "#40", true, "همیشه حاضر"),
                CanalAnatomySpec("DB", 18.0, 22.0, 20.0, "Distal Cusp", "#25", false, "فراوانی ۲۵ الی ۳۵ درصد موارد در ایران (چهارمین کانال)")
            )
            37, 47 -> listOf(
                CanalAnatomySpec("MB", 18.0, 22.0, 20.0, "MB Cusp", "#30", true, "همیشه حاضر"),
                CanalAnatomySpec("ML", 18.0, 22.0, 20.0, "ML Cusp", "#25", true, "همیشه حاضر"),
                CanalAnatomySpec("D", 18.0, 22.0, 20.0, "Distal Cusp", "#40", true, "همیشه حاضر"),
                CanalAnatomySpec("C-Shape", 18.0, 22.0, 20.0, "Distal Cusp", "#35", false, "فراوانی ۵ الی ۸ درصد موارد در ایران"),
                CanalAnatomySpec("DB", 17.0, 21.0, 19.0, "Distal Cusp", "#25", false, "فراوانی ۱۰ الی ۲۰ درصد موارد در ایران")
            )
            38, 48 -> listOf(
                CanalAnatomySpec("MB", 17.0, 22.0, 19.5, "MB Cusp", "#30", true),
                CanalAnatomySpec("ML", 17.0, 22.0, 19.5, "ML Cusp", "#25", true),
                CanalAnatomySpec("D", 17.0, 22.0, 19.0, "Distal Cusp", "#40", true)
            )

            // ===== دندان‌های شیری (Primary) =====
            51, 61 -> listOf(CanalAnatomySpec("C", 14.0, 20.0, 16.0, "Incisal Edge", "#30", true))
            52, 62 -> listOf(CanalAnatomySpec("C", 13.0, 19.0, 15.0, "Incisal Edge", "#30", true))
            53, 63 -> listOf(CanalAnatomySpec("C", 15.0, 21.0, 18.0, "Cusp Tip", "#35", true))
            54, 64 -> listOf(
                CanalAnatomySpec("MB", 13.0, 18.0, 15.0, "MB Cusp", "#25", true),
                CanalAnatomySpec("DB", 13.0, 18.0, 15.0, "DB Cusp", "#25", true),
                CanalAnatomySpec("P", 13.0, 18.0, 16.0, "Palatal Cusp", "#30", true)
            )
            55, 65 -> listOf(
                CanalAnatomySpec("MB", 14.0, 19.0, 16.0, "MB Cusp", "#25", true),
                CanalAnatomySpec("DB", 14.0, 19.0, 16.0, "DB Cusp", "#25", true),
                CanalAnatomySpec("P", 14.0, 19.0, 17.0, "Palatal Cusp", "#30", true)
            )
            71, 81 -> listOf(CanalAnatomySpec("C", 12.0, 18.0, 14.0, "Incisal Edge", "#25", true))
            72, 82 -> listOf(CanalAnatomySpec("C", 12.5, 18.5, 14.5, "Incisal Edge", "#25", true))
            73, 83 -> listOf(CanalAnatomySpec("C", 14.0, 20.0, 17.0, "Cusp Tip", "#30", true))
            74, 84 -> listOf(
                CanalAnatomySpec("M", 13.0, 18.0, 15.0, "Mesial Cusp", "#25", true), 
                CanalAnatomySpec("D", 13.0, 18.0, 15.0, "Distal Cusp", "#25", true)
            )
            75, 85 -> listOf(
                CanalAnatomySpec("M", 14.0, 19.0, 16.5, "Mesial Cusp", "#25", true), 
                CanalAnatomySpec("D", 14.0, 19.0, 16.5, "Distal Cusp", "#25", true)
            )

            else -> listOf(CanalAnatomySpec("C", 18.0, 25.0, 21.0, "Cusp Tip", "#30", true))
        }
        return list.map { 
            it.copy(probabilityNote = getProbabilityNote(it.probabilityNote))
        }
    }
}
