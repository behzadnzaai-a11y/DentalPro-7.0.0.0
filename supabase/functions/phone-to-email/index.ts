// ============================================================================
// DentalPro — Edge Function: phone-to-email
// ============================================================================
// «ورود با شماره موبایل + رمز عبور»: سوپابیس ورود با موبایل+رمز ندارد؛ اپ ابتدا
// ایمیلِ قطعیِ شماره را امتحان می‌کند (حساب‌های ثبت‌شده با پیامک) و اگر نشد این
// تابع را صدا می‌زند تا ایمیلِ واقعیِ حسابی که با آن شماره ثبت‌نام کرده را بگیرد.
//
// چرا سمت سرور؟ جدول users با RLS قفل است و فقط مالکِ ردیف می‌تواند بخواند؛
// این تابع با service_role دقیقاً «همان یک فیلد email» را برمی‌گرداند — نه نام،
// نه نقش، نه هیچ اطلاعات دیگری (جلوگیری از افشای اطلاعات سایر کاربران).
//
// Deploy: supabase functions deploy phone-to-email
//
// ورودی:  { "phone": "+989XXXXXXXXX" }
// خروجی:  { "email": "user@example.com" }  — رشته‌ی خالی اگر پیدا نشد (200 عمداً)
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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS_HEADERS });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  if (!serviceKey) return json({ error: "SERVER_NOT_CONFIGURED" }, 500);

  let phone: string | null = null;
  try {
    const body = await req.json();
    phone = normalizePhone(String(body?.phone ?? ""));
  } catch (_) {
    return json({ email: "", error: "بدنه‌ی درخواست نامعتبر است" }, 400);
  }
  if (!phone) return json({ email: "", error: "شماره موبایل نامعتبر است" }, 400);

  try {
    const res = await fetch(
      `${supabaseUrl}/rest/v1/users?phone=eq.${encodeURIComponent(phone)}&select=email&limit=1`,
      {
        headers: {
          Authorization: `Bearer ${serviceKey}`,
          apikey: serviceKey,
        },
      },
    );
    if (!res.ok) return json({ email: "", error: "خطای داخلی سرور" }, 500);
    const rows = await res.json();
    const email = Array.isArray(rows) && rows.length > 0 ? String(rows[0].email ?? "") : "";
    return json({ email }, 200);
  } catch (e) {
    return json({ email: "", error: "خطای داخلی سرور" }, 500);
  }
});
