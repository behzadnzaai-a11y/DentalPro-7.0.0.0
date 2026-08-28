-- ============================================================================
-- DentalPro — Migration 0003: اعلان بروزرسانی اپلیکیشن از طریق سرور (app_updates)
-- ============================================================================
-- برای انتشار نسخه جدید کافی است یک ردیف در این جدول درج کنید (از داشبورد Supabase
-- یا SQL Editor). اپ هنگام اجرا آخرین ردیف فعال را می‌خواند؛ اگر version_code آن از
-- نسخه نصب‌شده بزرگ‌تر باشد، اعلان «بروزرسانی جدید» نمایش داده می‌شود و کاربر با یک
-- کلیک، فایل APK را دانلود و نصب می‌کند.
--
-- نمونه انتشار نسخه:
--   insert into public.app_updates (version_code, version_name, apk_url, changelog, is_mandatory)
--   values (
--       10,                                    -- باید از versionCode نسخه فعلی بزرگ‌تر باشد
--       '9.1.0',
--       'https://geifrkzmiutpkbwejdms.supabase.co/storage/v1/object/public/app_releases/dentalpro-9.1.0.apk',
--       '- ثبت تراکنش پیامک بانکی با پاپ‌آپ جدید' || chr(10) || '- سه لیست ثبت‌شده/ثبت‌نشده/نادیده‌گرفته',
--       false                                  -- true = بروزرسانی اجباری (دکمه «بعداً» مخفی می‌شود)
--   );
--
-- برای غیرفعال‌کردن یک اعلان:  update public.app_updates set is_active = false where version_code = 10;
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ۱) جدول
-- ---------------------------------------------------------------------------
create table if not exists public.app_updates (
    id            bigint generated always as identity primary key,
    version_code  bigint  not null,                 -- versionCode اندروید (مبنای مقایسه)
    version_name  text    not null default '',      -- مثل «9.1.0» (فقط نمایشی)
    apk_url       text    not null,                 -- لینک مستقیم دانلود APK (باکت عمومی یا هر URL مستقیم)
    changelog     text    not null default '',      -- تغییرات این نسخه (چندخطی)
    is_mandatory  boolean not null default false,   -- بروزرسانی اجباری؟
    is_active     boolean not null default true,    -- امکان خاموش‌کردن اعلان بدون حذف ردیف
    min_supported_version_code bigint not null default 0, -- نسخه‌های قدیمی‌تر از این، اجباری تلقی می‌شوند
    created_at    timestamptz not null default now()
);

create index if not exists idx_app_updates_active_version
    on public.app_updates (is_active, version_code desc);

-- ---------------------------------------------------------------------------
-- ۲) امنیت سطری — خواندن برای همه کلاینت‌ها (حتی قبل از لاگین)، نوشتن فقط ادمین
--    (درج/ویرایش فقط از داشبورد Supabase یا با service_role انجام می‌شود؛
--     هیچ policy نوشتنی برای کلاینت‌ها تعریف نشده است.)
-- ---------------------------------------------------------------------------
alter table public.app_updates enable row level security;

drop policy if exists "app_updates_public_read" on public.app_updates;
create policy "app_updates_public_read" on public.app_updates
    for select to anon, authenticated
    using (is_active = true);

-- ---------------------------------------------------------------------------
-- ۳) باکت عمومی فایل‌های انتشار (برای آپلود APK از داشبورد → Storage → app_releases)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('app_releases', 'App Releases', true)
on conflict (id) do update set public = true, name = 'App Releases';

drop policy if exists "app_releases_read" on storage.objects;
create policy "app_releases_read" on storage.objects
    for select to public
    using (bucket_id = 'app_releases');

-- آپلود فایل انتشار فقط توسط کاربر احرازهویت‌شده (عملاً ادمین از داشبورد آپلود می‌کند)
drop policy if exists "app_releases_insert" on storage.objects;
create policy "app_releases_insert" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'app_releases' and owner = auth.uid());

-- ---------------------------------------------------------------------------
-- ۴) Realtime (اختیاری) — اگر اپ باز باشد، اعلان نسخه جدید بدون ری‌استارت هم می‌رسد
-- ---------------------------------------------------------------------------
do $$
begin
    execute 'alter table public.app_updates replica identity full';
    execute 'alter publication supabase_realtime add table public.app_updates';
exception
    when others then
        null;
end $$;
