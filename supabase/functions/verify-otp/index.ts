// ============================================================================
// DentalPro — Edge Function: verify-otp
// ============================================================================
// بررسی کد یک‌بارمصرف پیامکی و صدور توکن ورود (magic-link) برای اپ
//
// جریان:
//   ۱) اپ این تابع را با { phone, code } صدا می‌زند
//   ۲) کد با هشِ ذخیره‌شده مقایسه می‌شود (حداکثر ۵ تلاش، انقضا ۵ دقیقه)
//   ۳) اگر حسابی با این شماره وجود ندارد، با ایمیلِ قطعیِ 98xxxxxxxxxx@phone.dentalpro.app
//      ساخته می‌شود (ایمیل تاییدشده + شماره تاییدشده)
//   ۴) با admin generateLink یک لینک magic می‌سازیم و «token» آن را برمی‌گردانیم
//   ۵) اپ با POST /auth/v1/verify { token, type: "magiclink" } نشست واقعی می‌گیرد
//
// ورودی:  { "phone": "+98912xxxxxxx", "code": "123456" }
// خروجی:  { "ok": true, "token": "..." , "redirect_to": "dental://app" }
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

function normalizePhone(raw: string): string | null {
  let p = raw.replace(/[^\d+]/g, "");
  if (p.startsWith("+")) p = p.slice(1);
  if (p.startsWith("0098")) p = "98" + p.slice(4);
  if (p.startsWith("09")) p = "98" + p.slice(1);
  if (p.startsWith("9") && p.length === 10) p = "98" + p;
  return /^98\d{10}$/.test(p) ? `+${p}` : null;
}

async function sha256Hex(input: string): Promise<string> {
  const data = new TextEncoder().encode(input);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

const PHONE_EMAIL_DOMAIN = "phone.dentalpro.app";

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  if (!serviceKey) return json({ error: "SERVER_NOT_CONFIGURED" }, 500);

  let phone: string | null = null;
  let code = "";
  try {
    const body = await req.json();
    phone = normalizePhone(String(body?.phone ?? ""));
    code = String(body?.code ?? "").trim();
  } catch (_) {
    return json({ error: "بدنه‌ی درخواست نامعتبر است" }, 400);
  }
  if (!phone) return json({ error: "شماره موبایل معتبر نیست" }, 400);
  if (!/^\d{4,8}$/.test(code)) return json({ error: "کد تایید نامعتبر است" }, 400);

  const authed = (extra: Record<string, string> = {}) => ({
    Authorization: `Bearer ${serviceKey}`,
    apikey: serviceKey,
    ...extra,
  });

  // ۱) رکورد کد را بخوان
  const rowRes = await fetch(
    `${supabaseUrl}/rest/v1/otp_codes?phone=eq.${encodeURIComponent(phone)}&select=*`,
    { headers: authed() },
  );
  const rows = await rowRes.json().catch(() => []);
  if (!Array.isArray(rows) || rows.length === 0) {
    return json({ error: "کدی برای این شماره ثبت نشده است" }, 400);
  }
  const row = rows[0];

  if (new Date(row.expires_at).getTime() < Date.now()) {
    return json({ error: "کد منقضی شده است. دوباره درخواست بدهید" }, 400);
  }
  if (Number(row.attempts) >= 5) {
    return json({ error: "تعداد تلاش‌ها بیش از حد مجاز است. کد جدید بگیرید" }, 429);
  }

  // ۲) مقایسه‌ی هش
  const codeHash = await sha256Hex(code + ":" + phone);
  if (codeHash !== row.code_hash) {
    await fetch(
      `${supabaseUrl}/rest/v1/otp_codes?phone=eq.${encodeURIComponent(phone)}`,
      {
        method: "PATCH",
        headers: authed({ "Content-Type": "application/json", Prefer: "return=minimal" }),
        body: JSON.stringify({ attempts: Number(row.attempts) + 1 }),
      },
    );
    return json({ error: "کد تایید نادرست است" }, 400);
  }

  // ۳) کد مصرف شد — بلافاصله حذف تا دوباره استفاده نشود
  await fetch(
    `${supabaseUrl}/rest/v1/otp_codes?phone=eq.${encodeURIComponent(phone)}`,
    { method: "DELETE", headers: authed() },
  );

  // ۴) حساب با ایمیلِ قطعیِ این شماره — بساز (اگر از قبل هست، همان استفاده می‌شود).
  //    ایمیلِ قطعی یعنی همیشه می‌توان بدون جستجو با همان ایمیل magic-link ساخت.
  const email = `${phone.slice(1)}@${PHONE_EMAIL_DOMAIN}`;

  const createRes = await fetch(`${supabaseUrl}/auth/v1/admin/users`, {
    method: "POST",
    headers: authed({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      email,
      email_confirm: true,
      phone,
      phone_confirm: true,
      user_metadata: { phone, name: phone },
    }),
  });
  if (!createRes.ok && createRes.status !== 422) {
    // 422 = «از قبل ثبت شده» که کاملاً طبیعی است؛ بقیه‌ی خطاها جدی‌اند
    const text = await createRes.text();
    return json({ error: "خطا در ایجاد حساب کاربری", detail: text.slice(0, 200) }, 502);
  }

  // ۵) صدور توکن magic-link برای این حساب — اپ با /auth/v1/verify نشست می‌گیرد
  const linkRes = await fetch(`${supabaseUrl}/auth/v1/admin/generate_link`, {
    method: "POST",
    headers: authed({ "Content-Type": "application/json" }),
    body: JSON.stringify({
      type: "magiclink",
      email,
    }),
  });
  if (!linkRes.ok) {
    const text = await linkRes.text();
    return json({ error: "خطا در صدور توکن ورود", detail: text.slice(0, 200) }, 502);
  }
  const link = await linkRes.json();
  const actionLink: string = link.properties?.action_link ?? "";
  const token = new URL(actionLink).searchParams.get("token") ?? "";
  const redirectTo = new URL(actionLink).searchParams.get("redirect_to") ?? "dental://app";
  if (!token) return json({ error: "توکن ورود از پاسخ سرور استخراج نشد" }, 502);

  return json({ ok: true, token, redirect_to: redirectTo });
});
