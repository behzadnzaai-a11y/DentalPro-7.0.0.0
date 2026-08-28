-- ============================================================================
-- DentalPro — Migration 0002: صف پیامک‌های بانکی (unregistered_bank_sms)
-- ============================================================================
-- این جدول تمام تراکنش‌های پیامکی بانکی کشف‌شده روی دستگاه را نگه می‌دارد:
--   status = 'PENDING'  → ثبت‌نشده‌ها (لیست کامل با اکشن‌های «ثبت شود» / «فعلاً نادیده گرفته شود»)
--   status = 'ASSIGNED' → ثبت‌شده‌ها (نمایش تک‌سطری: مبلغ | بابت خلاصه | تاریخ)
--   status = 'IGNORED'  → نادیده‌گرفته‌شده‌ها (لیست مجزا، قابل بازگردانی/ثبت)
-- همهٔ تغییرات به‌صورت رئال‌تایم بین دستگاه‌ها همگام می‌شوند (Realtime + RLS مالک).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ۱) جدول
-- ---------------------------------------------------------------------------
create table if not exists public.unregistered_bank_sms (
    sms_native_id           text   not null default '',  -- کلید یکتای پیامک (dedup بین اسکن/زنده/دستی)
    sender                  text   not null default '',
    content                 text   not null default '',
    bank_name_guess         text   not null default '',
    type                    text   not null default 'DEPOSIT',   -- DEPOSIT / WITHDRAWAL
    amount_tomans           bigint not null default 0,
    account_number_raw      text   not null default '',
    date_str                text   not null default '',
    time_str                text   not null default '',
    score                   integer not null default 5,
    status                  text   not null default 'PENDING',   -- PENDING / ASSIGNED / IGNORED
    assigned_account_id     bigint,                               -- id محلی دستگاه (صرفاً اطلاعاتی)
    assigned_account_sync_id text  not null default '',           -- syncId حساب بانکی (مرجع بین‌دستگاهی)
    registered_for_summary  text   not null default '',           -- خلاصهٔ «بابت» برای نمایش تک‌سطری ثبت‌شده‌ها
    created_at              bigint not null default 0,
    owner_id                text   not null default '',
    sync_id                 text   primary key,
    updated_at              bigint not null default 0,
    is_deleted              boolean not null default false
);

create index if not exists idx_unregistered_bank_sms_owner_id
    on public.unregistered_bank_sms (owner_id);

create index if not exists idx_unregistered_bank_sms_owner_status
    on public.unregistered_bank_sms (owner_id, status);

create index if not exists idx_unregistered_bank_sms_native_id
    on public.unregistered_bank_sms (owner_id, sms_native_id);

-- ---------------------------------------------------------------------------
-- ۲) امنیت سطری (RLS) — هر کاربر فقط ردیف‌های خودش
-- ---------------------------------------------------------------------------
alter table public.unregistered_bank_sms enable row level security;

drop policy if exists "unregistered_bank_sms_owner_rows" on public.unregistered_bank_sms;
create policy "unregistered_bank_sms_owner_rows" on public.unregistered_bank_sms
    for all to authenticated
    using (owner_id = auth.uid()::text)
    with check (owner_id = auth.uid()::text);

-- ---------------------------------------------------------------------------
-- ۳) Realtime — بارگذاری/دریافت زندهٔ تغییرات بین دستگاه‌ها
-- ---------------------------------------------------------------------------
do $$
begin
    execute 'alter table public.unregistered_bank_sms replica identity full';
    execute 'alter publication supabase_realtime add table public.unregistered_bank_sms';
exception
    when others then
        -- اگر جدول قبلاً در publication بوده، ادامه بده
        null;
end $$;

-- ---------------------------------------------------------------------------
-- ۴) به‌روزرسانی تابع حذف کامل حساب — جدول جدید هم پاک شود
-- ---------------------------------------------------------------------------
create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = public, auth, storage, extensions
as $$
declare
    p_uid text := auth.uid()::text;
    t text;
begin
    if p_uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    foreach t in array array[
        'patients','treatments','payments','appointments','treatment_stages',
        'patient_documents','vendors','vendor_invoices','lab_orders',
        'equipment_maintenance','clinic_expenses','expense_payments',
        'personal_transactions','lab_services','installments',
        'treatment_templates','prescriptions','bank_accounts',
        'personal_sub_accounts','fixed_incomes','children_expenses',
        'clinic_settings','app_calendar_events','financial_ledger',
        'audit_events','drug_templates','staff_members',
        'unregistered_bank_sms','users'
    ]
    loop
        execute format('delete from public.%I where owner_id = %L', t, p_uid);
    end loop;

    -- فایل‌های Storage این کاربر (owner در storage.objects همان auth.uid است)
    delete from storage.objects
    where owner = p_uid::uuid and bucket_id in ('patient_media', 'clinic_assets');

    -- حذف خود حساب از auth.users (نشست‌ها هم باطل می‌شوند)
    perform auth.admin_delete_user(p_uid::uuid);
end;
$$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
