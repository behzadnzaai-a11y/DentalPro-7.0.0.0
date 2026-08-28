package com.example.util

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.lang.reflect.Modifier

/**
 * Converts Room entities to/from Supabase row maps using Gson, not
 * kotlin.reflect.full.memberProperties. The reflect-based version required a
 * `kotlin-reflect` dependency; even with that dependency present, blanket
 * reflection has no concept of "this field must never leave the device" —
 * it will happily serialize BankAccount.cvv2 and any *LocalPath field
 * straight to the cloud. This version excludes both explicitly.
 */
object EntityMapper {
    private val gson = Gson()

    // Fields that must NEVER leave the device, regardless of which entity
    // they appear on. Add to this set rather than special-casing entities.
    private val NEVER_SYNC_FIELDS = setOf("cvv2")

    // Local-only fields: meaningful only on this device (a Room primary key, or a
    // cache-only path like PatientDocument.uri that must never be treated as the
    // source of truth), never meaningful across devices.
    // NOTE: "documentUris" is Patient's legacy comma-separated list of local clinical
    // file/radiograph paths (superseded by PatientDocument), added here alongside
    // "uri" for the same reason: it's a raw device path and must never be pushed to
    // the cloud as if it were a synced value.
    private val LOCAL_ONLY_FIELDS = setOf("id", "uri", "documentUris")

    // Any field ending in one of these suffixes is a device-local cache
    // path/URI by naming convention in this codebase (e.g. photoLocalPath,
    // localCachePath) — never a remote Storage reference. These stay local.
    // NOTE: "CachePath" was added alongside "LocalPath"/"LocalUri" because
    // localCachePath (PatientDocument) ends in "CachePath", not "LocalPath" —
    // without it, that field was being synced to the cloud as a raw device path.
    private val LOCAL_PATH_SUFFIXES = listOf("LocalPath", "LocalUri", "CachePath")

    private fun isExcluded(key: String): Boolean =
        key in LOCAL_ONLY_FIELDS || key in NEVER_SYNC_FIELDS ||
            LOCAL_PATH_SUFFIXES.any { key.endsWith(it) }

    /**
     * Serializes [obj] using its real Kotlin property types first (so a Long
     * field is written as `5`, not `5.0`), then walks the resulting JSON tree
     * so each value keeps its correct native type (Long/Double/Boolean/String)
     * instead of collapsing every number to Double the way
     * `gson.fromJson(json, Map::class.java)` would.
     */
    fun toMap(obj: Any): Map<String, Any> {
        val json = gson.toJson(obj)
        val jsonObject = JsonParser.parseString(json).asJsonObject
        val result = mutableMapOf<String, Any>()
        for (key in jsonObject.keySet()) {
            if (isExcluded(key)) continue
            val element = jsonObject.get(key)
            if (element == null || element.isJsonNull) continue
            val value: Any = if (element.isJsonPrimitive) {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isNumber -> {
                        val raw = prim.asString
                        if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                            prim.asDouble
                        } else {
                            prim.asLong
                        }
                    }
                    else -> {
                        val str = prim.asString
                        if ((key.contains("FileId") || key.contains("Photo") || key.contains("Doc")) &&
                            (str.startsWith("/data/") || str.startsWith("/storage/") || str.startsWith("file://") || str.startsWith("content://"))) {
                            // Safety net only — after the fileId-first refactor this should never
                            // fire. If it does, something is still writing a local path into a
                            // synced field; log it so the regression is visible instead of silent.
                            Log.w("EntityMapper", "Dropped local-path value for synced field '$key' instead of uploading it (value starts with: ${str.take(24)})")
                            continue
                        }
                        str
                    }
                }
            } else {
                // Nested arrays/objects (none of the current entities have any,
                // but this keeps toMap from crashing if one is added later).
                element.toString()
            }
            result[key] = value
        }
        return result
    }

    inline fun <reified T : Any> fromMap(map: Map<String, Any>): T {
        val gson = Gson()
        val json = gson.toJson(map)
        return gson.fromJson(json, T::class.java)
    }

    /**
     * آماده‌سازی JSON دریافتی از PostgREST قبل از Gson→Room — برای **همهٔ ۲۷ جدول سینک**.
     *
     * Gson پیش‌فرض‌های Kotlin را اعمال نمی‌کند: فیلد غیرنال که در JSON نیست یا
     * صریحاً null است، در runtime null می‌ماند و insert در Room با NOT NULL
     * شکست می‌خورد — بی‌صدا در pullDocument catch می‌شود. این فقط بیمار نیست:
     *
     *  - `Patient.documentUris` هرگز سینک نمی‌شود → لیست بیمار خالی
     *  - `BankAccount.cvv2` هرگز سینک نمی‌شود → حساب بانکی درج نمی‌شود
     *  - هر String غیرنال دیگر اگر ستون سرور غایب/null باشد، همان شکست
     *
     * کار این تابع برای هر کلاس Room:
     *  1. حذف کلید `id` — PK محلی Room هرگز نباید از دستگاه دیگر (یا ستون uuid سرور) بیاید.
     *  2. String غایب یا JsonNull → `""` (documentUris، cvv2، و هر فیلد متنی دیگر).
     *  3. long/int/boolean **primitive** غایب یا JsonNull → 0 / false تا Gson پرتاب نکند.
     */
    fun preparePulledJson(json: String, clazz: Class<*>): String {
        val obj = JsonParser.parseString(json).asJsonObject
        obj.remove("id")
        for (field in clazz.declaredFields) {
            if (field.name == "id") continue
            if (field.isSynthetic) continue
            if (Modifier.isStatic(field.modifiers)) continue
            if (field.name.contains('$')) continue
            val el = obj.get(field.name)
            val missingOrNull = el == null || el.isJsonNull
            if (!missingOrNull) continue
            when (field.type) {
                String::class.java -> obj.addProperty(field.name, "")
                java.lang.Long.TYPE, java.lang.Integer.TYPE, java.lang.Short.TYPE, java.lang.Byte.TYPE ->
                    obj.addProperty(field.name, 0)
                java.lang.Boolean.TYPE -> obj.addProperty(field.name, false)
                java.lang.Double.TYPE, java.lang.Float.TYPE -> obj.addProperty(field.name, 0.0)
            }
        }
        obj.remove("id")
        return obj.toString()
    }

    /**
     * هنگام به‌روزرسانی ردیف موجود، فیلدهای فقط‌محلی را از کپی فعلی Room روی
     * شیء pull‌شده برمی‌گرداند تا re-pull مقدار `documentUris` / `cvv2` /
     * `photoLocalPath` / `uri` / `localCachePath` را خالی نکند.
     *
     * Gson شیء را با فیلدهای قابل‌نوشتن می‌سازد؛ setAccessible برای valهای
     * Kotlin روی همین نمونه‌ها کافی است.
     */
    fun overlayLocalOnlyFields(pulled: Any, local: Any) {
        if (pulled.javaClass != local.javaClass) return
        for (field in pulled.javaClass.declaredFields) {
            if (field.isSynthetic) continue
            if (Modifier.isStatic(field.modifiers)) continue
            if (field.name.contains('$')) continue
            if (!isExcluded(field.name)) continue
            try {
                field.isAccessible = true
                field.set(pulled, field.get(local))
            } catch (e: Exception) {
                Log.w("EntityMapper", "Could not preserve local-only field ${field.name}: ${e.message}")
            }
        }
    }
}
