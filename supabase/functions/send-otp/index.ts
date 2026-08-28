// ============================================================================
// DentalPro — Edge Function: send-otp
// ============================================================================
// ارسال کد یک‌بارمصرف ورود پیامکی از طریق SMS.ir
//
// Deploy (از پوشه‌ی اصلی پروژه):
//   supabase functions deploy send-otp
//   supabase secrets set SMSIR_API_KEY=xxxxxxxx
//   # اختیاری — یکی از دو حالت زیر:
//   supabase secrets set SMSIR_OTP_TEMPLATE_ID=123456        (سرویس Verify)
//   supabase secrets set SMSIR_LINE_NUMBER=3000748xxxx       (ارسالbulk)
//
// ورودی:  { "phone": "+98912xxxxxxx" }
// خروجی:  { "ok": true }  یا خطا
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

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/** فرمت‌های پذیرفتنی: +989xxxxxxxxx / 09xxxxxxxxx / 989xxxxxxxxx / 9xxxxxxxxx */
function normalizePhone(raw: string): string | null {
  const digits = raw.replace(/[^\d+]/g, "");
  let p = digits;
  if (p.startsWith("+")) p = p.slice(1);
  if (p.startsWith("0098")) p = "98" + p.slice(4);
  if (p.startsWith("09")) p = "98" + p.slice(1);
  if (p.startsWith("9") && p.length === 10) p = "98" + p;
  return /^98\d{10}$/.test(p) ? `+${p}` : null;
}

// ---------------------------------------------------------------------------
// ارسال با SMS.ir
// ---------------------------------------------------------------------------
async function sendViaSmsIr(phone: string, code: string): Promise<void> {
  const apiKey = Deno.env.get("SMSIR_API_KEY");
  if (!apiKey) {
    throw new Error("SMS_NOT_CONFIGURED");
  }

  // حالت ۱: سرویس Verify (پیشنهادی) — نیاز به Template فعال در پنل SMS.ir دارد
  const templateId = Deno.env.get("SMSIR_OTP_TEMPLATE_ID");
  if (templateId) {
    const res = await fetch("https://api.sms.ir/v1/send/verify", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": apiKey,
        "Accept": "application/json",
      },
      body: JSON.stringify({
        mobile: phone.replace("+", ""),
        templateId: Number(templateId),
        parameters: [{ name: "CODE", value: code }],
      }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`SMSIR_${res.status}: ${text.slice(0, 200)}`);
    }
    return;
  }

  // حالت ۲: ارسال bulk با شماره‌خط
  const lineNumber = Deno.env.get("SMSIR_LINE_NUMBER");
  if (lineNumber) {
    const template = Deno.env.get("SMSIR_OTP_TEXT") ??
      "دنتال‌پرو\nکد ورود شما: {code}\nاین کد ۵ دقیقه اعتبار دارد.";
    const res = await fetch("https://api.sms.ir/v1/send/bulk", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-api-key": apiKey,
        "Accept": "application/json",
      },
      body: JSON.stringify({
        lineNumber,
        messageText: template.replaceAll("{code}", code),
        mobiles: [phone.replace("+", "")],
      }),
    });
    if (!res.ok) {
      const text = await res.text();
      throw new Error(`SMSIR_${res.status}: ${text.slice(0, 200)}`);
    }
    return;
  }

  throw new Error("SMS_NOT_CONFIGURED");
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  // کلاینت فقط با کلید anon صدا می‌زند؛ برای نوشتن در otp_codes به service_role نیاز داریم
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!serviceKey) return json({ error: "SERVER_NOT_CONFIGURED" }, 500);

  let phone: string | null = null;
  try {
    const body = await req.json();
    phone = normalizePhone(String(body?.phone ?? ""));
  } catch (_) {
    return json({ error: "بدنه‌ی درخواست نامعتبر است" }, 400);
  }
  if (!phone) return json({ error: "شماره موبایل معتبر نیست" }, 400);

  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;

  // محدودیت ارسال: حداکثر یک پیامک در هر ۶۰ ثانیه برای هر شماره
  const checkRes = await fetch(
    `${supabaseUrl}/rest/v1/otp_codes?phone=eq.${encodeURIComponent(phone)}&select=last_sent_at`,
    { headers: { Authorization: `Bearer ${serviceKey}`, apikey: serviceKey } },
  );
  const existing = await checkRes.json().catch(() => []);
  if (Array.isArray(existing) && existing.length > 0) {
    const last = new Date(existing[0].last_sent_at).getTime();
    if (Date.now() - last < 60_000) {
      return json({ error: "برای درخواست کد جدید یک دقیقه صبر کنید" }, 429);
    }
  }

  // تولید کد ۶ رقمی و هش آن
  const code = String(Math.floor(100000 + Math.random() * 900000));
  const codeHash = await sha256Hex(code + ":" + phone);

  const upsertRes = await fetch(
    `${supabaseUrl}/rest/v1/otp_codes?on_conflict=phone`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${serviceKey}`,
        apikey: serviceKey,
        "Content-Type": "application/json",
        Prefer: "resolution=merge-duplicates",
      },
      body: JSON.stringify({
        phone,
        code_hash: codeHash,
        expires_at: new Date(Date.now() + 5 * 60_000).toISOString(),
        attempts: 0,
        last_sent_at: new Date().toISOString(),
      }),
    },
  );
  if (!upsertRes.ok) {
    return json({ error: "خطا در ثبت کد روی سرور" }, 500);
  }

  try {
    await sendViaSmsIr(phone, code);
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    if (msg === "SMS_NOT_CONFIGURED") {
      return json({ error: "سرویس پیامک هنوز پیکربندی نشده است (SMSIR_API_KEY)" }, 503);
    }
    return json({ error: "ارسال پیامک ناموفق بود", detail: msg }, 502);
  }

  return json({ ok: true });
});
