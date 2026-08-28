// ============================================================================
// DentalPro — Edge Function: extract-cheque-data (استخراج داده چک با هوش مصنوعی)
// ============================================================================
// اجرای Gemini/Groq با IP سرور سوپابیس — دور زدن فیلترینگ و تحریم‌ها.
//
// «پروکسی کلید» حذف شد: کلاینت دیگر هیچ کلید API ارسال نمی‌کند. کلید(ها) به‌صورت
// Secret سمت سرور نگهداری می‌شوند و فقط تصویر + پرامپت از اپ می‌آید.
//
// Deploy:
//   supabase functions deploy extract-cheque-data
//
// Secrets (کلید روی سرور — هرگز داخل اپ یا Git):
//   supabase secrets set GEMINI_API_KEY=AIza...            (یا چند کلید با کاما:
//                                                           GEMINI_API_KEYS=k1,k2)
//   supabase secrets set GROQ_API_KEY=gsk_...              (اختیاری — یا GROQ_API_KEYS)
//
// ورودی:  { imageBase64, prompt, provider?: "gemini"|"groq", model?: string }
// خروجی:  JSON استخراج‌شده از روی عکس چک
// ============================================================================

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS_HEADERS, "Content-Type": "application/json" },
  });
}

/** یک Secret را می‌خواند و به لیست کلیدها (جداشده با کاما) تبدیل می‌کند. */
function readKeys(single?: string, multi?: string): string[] {
  const raw = multi?.trim() || single?.trim() || "";
  return raw.split(",").map((k) => k.trim()).filter((k) => k.length > 0);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  let imageBase64: string, prompt: string, provider: string, model: string;
  try {
    const body = await req.json();
    imageBase64 = String(body?.imageBase64 ?? "").trim();
    prompt = String(body?.prompt ?? "").trim();
    provider = String(body?.provider ?? "gemini").toLowerCase();
    model = String(body?.model ?? "").trim();
  } catch (_) {
    return json({ error: "بدنه‌ی درخواست نامعتبر است" }, 400);
  }

  if (!imageBase64 || imageBase64.length < 100) {
    return json({ error: "تصویر ارسال نشده یا نامعتبر است" }, 400);
  }
  if (!prompt) return json({ error: "پرامپت ارسال نشده است" }, 400);

  // کلیدها فقط از Secretهای سرور خوانده می‌شوند — نه از کلاینت
  const geminiKeys = readKeys(Deno.env.get("GEMINI_API_KEY"), Deno.env.get("GEMINI_API_KEYS"));
  const groqKeys = readKeys(Deno.env.get("GROQ_API_KEY"), Deno.env.get("GROQ_API_KEYS"));

  let apiKeys: string[];
  if (provider === "gemini") {
    apiKeys = geminiKeys.length > 0 ? geminiKeys : groqKeys; // اگر فقط گروک تنظیم شده بود
    if (apiKeys.length === 0) {
      return json({ error: "کلید Gemini روی سرور تنظیم نشده است (GEMINI_API_KEY)" }, 503);
    }
    if (!model) model = "gemini-2.0-flash";
  } else if (provider === "groq") {
    apiKeys = groqKeys.length > 0 ? groqKeys : geminiKeys;
    if (apiKeys.length === 0) {
      return json({ error: "کلید Groq روی سرور تنظیم نشده است (GROQ_API_KEY)" }, 503);
    }
    if (!model) model = "llama-3.2-11b-vision-preview";
  } else {
    return json({ error: `ارائه‌دهنده‌ی ناشناخته: ${provider}` }, 400);
  }

  let lastErrorMsg = "نامشخص";
  let lastStatusCode = 502;

  for (const apiKey of apiKeys) {
    try {
      if (provider === "gemini") {
        const response = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              contents: [{
                parts: [
                  { text: prompt },
                  { inline_data: { mime_type: "image/jpeg", data: imageBase64 } },
                ],
              }],
              generationConfig: {
                temperature: 0.1,
                responseMimeType: "application/json",
              },
            }),
          },
        );

        if (!response.ok) {
          const errText = await response.text();
          lastStatusCode = response.status;
          lastErrorMsg = `Gemini (${response.status}): ${errText}`;
          continue; // سعی با کلید بعدی
        }

        const data = await response.json();
        const textOut = data?.candidates?.[0]?.content?.parts?.[0]?.text;
        if (!textOut) {
          lastErrorMsg = "پاسخ Gemini خالی بود";
          continue;
        }

        let parsed;
        try {
          parsed = JSON.parse(textOut);
        } catch (_) {
          lastErrorMsg = "پاسخ Gemini فرمت JSON معتبری نداشت";
          continue;
        }
        return json(parsed, 200);
      } else {
        const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${apiKey}`,
          },
          body: JSON.stringify({
            model,
            temperature: 0.1,
            response_format: { type: "json_object" },
            messages: [{
              role: "user",
              content: [
                { type: "text", text: prompt },
                { type: "image_url", image_url: { url: `data:image/jpeg;base64,${imageBase64}` } },
              ],
            }],
          }),
        });

        if (!response.ok) {
          const errText = await response.text();
          lastStatusCode = response.status;
          lastErrorMsg = `Groq (${response.status}): ${errText}`;
          continue;
        }

        const data = await response.json();
        const textOut = data?.choices?.[0]?.message?.content;
        if (!textOut) {
          lastErrorMsg = "پاسخ Groq خالی بود";
          continue;
        }

        let parsed;
        try {
          parsed = JSON.parse(textOut);
        } catch (_) {
          lastErrorMsg = "پاسخ Groq فرمت JSON معتبری نداشت";
          continue;
        }
        return json(parsed, 200);
      }
    } catch (e) {
      lastErrorMsg = e instanceof Error ? e.message : String(e);
      lastStatusCode = 500;
      // سعی با کلید بعدی
    }
  }

  // اگر به اینجا رسید، یعنی هیچ‌کدام از کلیدها موفق نشدند
  return json({ error: `ارتباط ناموفق با سرویس تشخیص. آخرین خطا: ${lastErrorMsg}` }, lastStatusCode);
});
