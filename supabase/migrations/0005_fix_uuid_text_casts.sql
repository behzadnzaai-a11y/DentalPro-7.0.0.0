-- ============================================================================
-- DentalPro — Hotfix 0005: رفع خطای «operator does not exist: uuid = text»
-- ============================================================================
-- علت خطا: ستون owner در جدول storage.objects از نوع uuid است، اما در policyها
-- و تابع delete_my_account با مقدار text مقایسه شده بود (auth.uid()::text / p_uid).
-- این فایل همه policyهای Storage و تابع حذف حساب را با cast صحیح بازسازی می‌کند.
-- اجرای این فایل به‌تنهایی کافی است (idempotent — اجرای دوباره امن است).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ۱) Policyهای باکت‌های patient_media / clinic_assets
-- ---------------------------------------------------------------------------
drop policy if exists "dental_media_read" on storage.objects;
create policy "dental_media_read" on storage.objects
    for select to public
    using (bucket_id in ('patient_media', 'clinic_assets'));

drop policy if exists "dental_media_insert" on storage.objects;
create policy "dental_media_insert" on storage.objects
    for insert to authenticated
    with check (
        bucket_id in ('patient_media', 'clinic_assets')
        and owner = auth.uid()
    );

drop policy if exists "dental_media_update" on storage.objects;
create policy "dental_media_update" on storage.objects
    for update to authenticated
    using (bucket_id in ('patient_media', 'clinic_assets') and owner = auth.uid())
    with check (bucket_id in ('patient_media', 'clinic_assets') and owner = auth.uid());

drop policy if exists "dental_media_delete" on storage.objects;
create policy "dental_media_delete" on storage.objects
    for delete to authenticated
    using (bucket_id in ('patient_media', 'clinic_assets') and owner = auth.uid());

-- ---------------------------------------------------------------------------
-- ۲) Policyهای باکت app_releases (فایل‌های انتشار نسخه)
-- ---------------------------------------------------------------------------
drop policy if exists "app_releases_read" on storage.objects;
create policy "app_releases_read" on storage.objects
    for select to public
    using (bucket_id = 'app_releases');

drop policy if exists "app_releases_insert" on storage.objects;
create policy "app_releases_insert" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'app_releases' and owner = auth.uid());

-- ---------------------------------------------------------------------------
-- ۳) تابع حذف کامل حساب — cast صحیح uuid برای storage.objects.owner
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

    -- فایل‌های Storage این کاربر (owner در storage.objects از نوع uuid است)
    delete from storage.objects
    where owner = p_uid::uuid and bucket_id in ('patient_media', 'clinic_assets');

    -- حذف خود حساب از auth.users (نشست‌ها هم باطل می‌شوند)
    perform auth.admin_delete_user(p_uid::uuid);
end;
$$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
