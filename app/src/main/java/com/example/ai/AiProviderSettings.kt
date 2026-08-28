package com.example.ai

import android.content.Context
import com.example.security.SecureSecretStore

/**
 * تنظیمات مرکزیِ دو ارائه‌دهنده‌ی AI قابل‌انتخاب — طبق درخواست: «پنجره تنظیمات ای‌آی دو تا
 * ای‌پی‌آی‌کی مجزا با تیک فعال، فقط یکی». هر دو کلید رمزنگاری‌شده روی گوشی ذخیره می‌شوند
 * (SecureSecretStore)؛ کدام‌یک واقعاً برای استخراج چک استفاده می‌شود را [KEY_ACTIVE_PROVIDER]
 * تعیین می‌کند.
 */
object AiProviderSettings {
    const val KEY_GEMINI_API_KEY = "gemini_api_key"
    const val KEY_GEMINI_MODEL = "gemini_model"
    const val KEY_GROQ_API_KEY = "groq_api_key"
    const val KEY_ACTIVE_PROVIDER = "active_ai_provider" // مقدار: "gemini" یا "groq"

    /** مدل‌های Gemini قابل‌انتخاب از منوی مخفی — اولی پیش‌فرض است. */
    val GEMINI_MODELS = listOf("gemini-3.5-flash", "gemini-3.6-flash", "gemini-3.7-flash", "gemini-pro-preview")
    const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash"

    /** مدل ثابتِ Groq — طبق درخواست، فقط همین یک مدل. */
    const val GROQ_MODEL = "qwen/qwen3.6-27b"

    enum class Provider { GEMINI, GROQ }

    fun getActiveProvider(context: Context): Provider {
        val raw = SecureSecretStore.get(context, KEY_ACTIVE_PROVIDER)
        return if (raw == "groq") Provider.GROQ else Provider.GEMINI
    }

    fun setActiveProvider(context: Context, provider: Provider) {
        SecureSecretStore.put(context, KEY_ACTIVE_PROVIDER, if (provider == Provider.GROQ) "groq" else "gemini")
    }

    fun getGeminiModel(context: Context): String =
        SecureSecretStore.get(context, KEY_GEMINI_MODEL)?.takeIf { it in GEMINI_MODELS } ?: DEFAULT_GEMINI_MODEL

    fun setGeminiModel(context: Context, model: String) {
        SecureSecretStore.put(context, KEY_GEMINI_MODEL, model)
    }

    fun getGeminiApiKey(context: Context): String? = SecureSecretStore.get(context, KEY_GEMINI_API_KEY)
    
    fun getGeminiApiKeysList(context: Context): List<String> {
        val raw = getGeminiApiKey(context) ?: return emptyList()
        val keys = raw.split(Regex("[\\n\\r,;]+")).map { it.trim() }.filter { it.isNotBlank() }
        return keys.shuffled().take(3)
    }

    fun getGroqApiKey(context: Context): String? = SecureSecretStore.get(context, KEY_GROQ_API_KEY)
    
    fun getGroqApiKeysList(context: Context): List<String> {
        val raw = getGroqApiKey(context) ?: return emptyList()
        val keys = raw.split(Regex("[\\n\\r,;]+")).map { it.trim() }.filter { it.isNotBlank() }
        return keys.shuffled().take(3)
    }

    /** آیا ارائه‌دهنده‌ی فعال، کلیدش هم تنظیم شده — یعنی واقعاً آماده‌ی استفاده است. */
    fun isReady(context: Context): Boolean = when (getActiveProvider(context)) {
        Provider.GEMINI -> !getGeminiApiKey(context).isNullOrBlank()
        Provider.GROQ -> !getGroqApiKey(context).isNullOrBlank()
    }
}
