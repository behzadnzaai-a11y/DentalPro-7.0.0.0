-- ============================================================================
-- DentalPro — اسکریپت کامل و واحد سوپابیس (همه‌چیز در یک فایل)
-- نسخه اپ: 6.3.1
-- ============================================================================
-- اجرا: Supabase Dashboard → SQL Editor → New query → کل این فایل را Paste
-- کنید و Run بزنید. کاملاً idempotent است (اجرای دوباره خطا نمی‌دهد) و برای
-- پروژه تازه یا پروژه‌ای که بخشی از اسکریپت‌ها را قبلاً اجرا کرده، یکسان کار می‌کند.
--
-- محتوا به ترتیب اجرا:
--   بخش A — اسکیمای اولیه: ۲۸ جدول + RLS مالک + ایندکس‌ها + باکت‌های Storage
--            + تابع delete_my_account + فعال‌سازی Realtime
--   بخش B — جدول صف پیامک‌های بانکی unregistered_bank_sms
--            (ثبت‌نشده / ثبت‌شده تک‌سطری / نادیده‌گرفته + RLS مالک + Realtime)
--   بخش C — سیستم اعلان بروزرسانی اپ (app_updates + باکت app_releases + Realtime)
--            نوع آپدیت را خود اپ از ارقام نسخه تشخیص می‌دهد (پایه: 6.3.1):
--              رقم اول (6→7) = آپدیت اصلی → اجباری
--              رقم وسط (3→4) = تغییرات جزیی → اختیاری، اعلان هر بار + تایید کاربر
--              رقم آخر (1→2) = بهبود/رفع باگ → بنر متنی خود-حذف، روزی یک‌بار
--   بخش D — ساختار درختی سرفصل‌های حساب شخصی پرمیس (ستون parent_name)
--   بخش E — هات‌فیکس نهایی: بازسازی policyهای Storage و تابع حذف حساب با
--            cast صحیح uuid (رفع «operator does not exist: uuid = text»)
--
-- نکته: تعریف‌های تکراری (مثل delete_my_account یا policyهای Storage) عمداً
-- چند بار می‌آیند — «آخرین تعریف» (بخش E) نسخهٔ نهایی و معتبر است.
-- ============================================================================


-- ############################################################################
-- بخش A — اسکیمای اولیه (28 جدول + RLS + Storage + Realtime)
-- ############################################################################
-- ============================================================================
-- DentalPro — Supabase initial schema
-- ============================================================================
-- اجرای این فایل: Supabase Dashboard → SQL Editor → New query → کل فایل را
-- Paste کنید و Run بزنید. این اسکریپت idempotent است (اجرای دوباره خطا نمی‌دهد).
--
-- ساختار:
--   ۱) ۲۸ جدول (۲۷ جدول سینک اپ + جدول otp_codes برای ورود پیامکی)
--   ۲) RLS روی همه‌ی جدول‌ها — هر حساب فقط رکوردهایی را می‌بیند که
--      owner_id آن‌ها برابر auth.uid() باشد (جدول users با کلید id)
--   ۳) ایندکس‌ها مطابق ایندکس‌های Room و اسکیمای قبلی Appwrite
--   ۴) دو باکت Storage عمومی: patient_media و clinic_assets
--      (نوشتن/حذف فقط توسط مالک فایل؛ خواندن عمومی مثل قبل)
--   ۵) تابع delete_my_account() برای حذف کامل حساب از داخل اپ
--   ۶) فعال‌سازی Realtime روی همه‌ی جدول‌های سینک
--
-- نام ستون‌ها snake_case است و لایه‌ی Supabase.kt داخل اپ به‌صورت خودکار
-- بین camelCase (Room) و snake_case (Postgres) تبدیل می‌کند.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- ۰) جدول کدهای یک‌بارمصرف پیامکی (فقط Edge Function با service_role به آن
--    دسترسی دارد — هیچ Policyای برای کلاینت تعریف نشده)
-- ---------------------------------------------------------------------------
create table if not exists public.otp_codes (
    phone        text primary key,
    code_hash    text not null,
    expires_at   timestamptz not null,
    attempts     integer not null default 0,
    last_sent_at timestamptz not null default now()
);
alter table public.otp_codes enable row level security;

-- ---------------------------------------------------------------------------
-- ۱) جدول پروفایل کاربران — id همان auth.uid() است
-- ---------------------------------------------------------------------------
create table if not exists public.users (
    id          text primary key,
    owner_id    text not null default '',
    name        text not null default '',
    doctor_name text not null default '',
    email       text not null default '',
    phone       text not null default '',
    role        text not null default '',
    sync_id     text not null default '',
    updated_at  bigint not null default 0,
    is_deleted  boolean not null default false
);
create index if not exists idx_users_owner_id on public.users (owner_id);
create index if not exists idx_users_phone on public.users (phone);

-- ---------------------------------------------------------------------------
-- ۲) جدول‌های سینک اپ — sync_id کلید یکتای بین‌دستگاهی است (PK)
-- ---------------------------------------------------------------------------
create table if not exists public.patients (
    patient_code         text not null default '',
    name                 text not null default '',
    phone                text not null default '',
    email                text not null default '',
    address              text not null default '',
    medical_history      text not null default '',
    planned_treatment_ids text not null default '',
    note                 text not null default '',
    referrer             text not null default '',
    photo_file_id        text,
    created_at           bigint not null default 0,
    gender               text not null default '',
    medical_alerts       text not null default '',
    self_declaration_json text not null default '',
    age                  integer not null default 0,
    birth_date           text not null default '',
    preferred_messenger  text not null default '',
    father_name          text not null default '',
    national_id          text not null default '',
    signature_file_id    text,
    assistant_ids        text not null default '',
    owner_id             text not null default '',
    sync_id              text primary key,
    updated_at           bigint not null default 0,
    is_deleted           boolean not null default false
);
create index if not exists idx_patients_owner_id on public.patients (owner_id);
create index if not exists idx_patients_phone on public.patients (phone);

create table if not exists public.treatments (
    patient_id      bigint not null default 0,
    patient_sync_id text not null default '',
    title           text not null default '',
    cost            bigint not null default 0,
    discount        bigint not null default 0,
    selected_teeth  text not null default '',
    date            bigint not null default 0,
    notes           text not null default '',
    status          text not null default 'PLANNED',
    stages          text not null default '',
    completed_stages text not null default '',
    ignored_stages  text not null default '',
    assistant_ids   text not null default '',
    owner_id        text not null default '',
    sync_id         text primary key,
    updated_at      bigint not null default 0,
    is_deleted      boolean not null default false
);
create index if not exists idx_treatments_owner_id on public.treatments (owner_id);
create index if not exists idx_treatments_patient_sync_id on public.treatments (patient_sync_id);
create index if not exists idx_treatments_date on public.treatments (date);

create table if not exists public.payments (
    patient_id          bigint not null default 0,
    patient_sync_id     text not null default '',
    amount              bigint not null default 0,
    type                text not null default 'CASH',
    bank_account_id     bigint,
    bank_account_sync_id text not null default '',
    source_sms_native_id text not null default '',
    cheque_number       text,
    cheque_due_date     bigint,
    date                bigint not null default 0,
    is_cleared          boolean not null default true,
    sayadi_id           text,
    cheque_bank         text,
    cheque_drawer       text,
    cheque_photo_file_id text,
    cheque_status       text not null default 'PENDING',
    transferred_to      text,
    transfer_date       bigint,
    assistant_ids       text not null default '',
    notes               text,
    owner_id            text not null default '',
    sync_id             text primary key,
    updated_at          bigint not null default 0,
    is_deleted          boolean not null default false
);
create index if not exists idx_payments_owner_id on public.payments (owner_id);
create index if not exists idx_payments_patient_sync_id on public.payments (patient_sync_id);
create index if not exists idx_payments_date on public.payments (date);
create index if not exists idx_payments_cheque_due_date on public.payments (cheque_due_date);

create table if not exists public.appointments (
    patient_id           bigint not null default 0,
    patient_sync_id      text not null default '',
    patient_name         text not null default '',
    date                 bigint not null default 0,
    treatment_type       text not null default '',
    planned_treatment_ids text not null default '',
    note                 text not null default '',
    referrer             text not null default '',
    is_completed         boolean not null default false,
    is_archived          boolean not null default false,
    discharge_sent       boolean not null default false,
    discharge_method     text not null default '',
    status               text not null default 'PENDING',
    unit                 text,
    assistant_ids        text not null default '',
    owner_id             text not null default '',
    sync_id              text primary key,
    updated_at           bigint not null default 0,
    is_deleted           boolean not null default false
);
create index if not exists idx_appointments_owner_id on public.appointments (owner_id);
create index if not exists idx_appointments_patient_sync_id on public.appointments (patient_sync_id);
create index if not exists idx_appointments_date on public.appointments (date);

create table if not exists public.treatment_stages (
    treatment_id      bigint not null default 0,
    treatment_sync_id text not null default '',
    stage_index       integer not null default 0,
    title             text not null default '',
    is_completed      boolean not null default false,
    is_ignored        boolean not null default false,
    completed_date    bigint,
    notes             text not null default '',
    owner_id          text not null default '',
    sync_id           text primary key,
    updated_at        bigint not null default 0,
    is_deleted        boolean not null default false
);
create index if not exists idx_treatment_stages_owner_id on public.treatment_stages (owner_id);
create index if not exists idx_treatment_stages_treatment_sync_id on public.treatment_stages (treatment_sync_id);

create table if not exists public.patient_documents (
    patient_id      bigint not null default 0,
    patient_sync_id text not null default '',
    file_id         text,
    doc_type        text not null default 'RADIOGRAPH',
    title           text not null default '',
    created_at      bigint not null default 0,
    notes           text not null default '',
    owner_id        text not null default '',
    sync_id         text primary key,
    updated_at      bigint not null default 0,
    is_deleted      boolean not null default false
);
create index if not exists idx_patient_documents_owner_id on public.patient_documents (owner_id);
create index if not exists idx_patient_documents_patient_sync_id on public.patient_documents (patient_sync_id);
create index if not exists idx_patient_documents_doc_type on public.patient_documents (doc_type);

create table if not exists public.vendors (
    name                text not null default '',
    company             text not null default '',
    phone               text not null default '',
    item_type           text not null default '',
    total_orders        bigint not null default 0,
    total_paid          bigint not null default 0,
    invoice_number      text,
    invoice_details     text,
    invoice_photo_file_id text,
    owner_id            text not null default '',
    sync_id             text primary key,
    updated_at          bigint not null default 0,
    is_deleted          boolean not null default false
);
create index if not exists idx_vendors_owner_id on public.vendors (owner_id);

create table if not exists public.vendor_invoices (
    vendor_id       bigint not null default 0,
    vendor_sync_id  text not null default '',
    invoice_number  text not null default '',
    amount          bigint not null default 0,
    date            bigint not null default 0,
    details         text not null default '',
    document_file_id text,
    is_paid         boolean not null default true,
    owner_id        text not null default '',
    sync_id         text primary key,
    updated_at      bigint not null default 0,
    is_deleted      boolean not null default false
);
create index if not exists idx_vendor_invoices_owner_id on public.vendor_invoices (owner_id);
create index if not exists idx_vendor_invoices_vendor_sync_id on public.vendor_invoices (vendor_sync_id);
create index if not exists idx_vendor_invoices_date on public.vendor_invoices (date);

create table if not exists public.lab_orders (
    lab_name        text not null default '',
    patient_name    text not null default '',
    tooth_number    text not null default '',
    prosthesis_type text not null default '',
    cost            bigint not null default 0,
    order_date      bigint not null default 0,
    due_date        bigint not null default 0,
    status          text not null default '',
    shade_brand     text,
    shade_color     text,
    owner_id        text not null default '',
    sync_id         text primary key,
    updated_at      bigint not null default 0,
    is_deleted      boolean not null default false
);
create index if not exists idx_lab_orders_owner_id on public.lab_orders (owner_id);

create table if not exists public.equipment_maintenance (
    equipment_name    text not null default '',
    description       text not null default '',
    cost              bigint not null default 0,
    date              bigint not null default 0,
    next_service_date bigint,
    technician_name   text,
    invoice_number    text,
    owner_id          text not null default '',
    sync_id           text primary key,
    updated_at        bigint not null default 0,
    is_deleted        boolean not null default false
);
create index if not exists idx_equipment_maintenance_owner_id on public.equipment_maintenance (owner_id);

create table if not exists public.clinic_expenses (
    category            text not null default '',
    amount              bigint not null default 0,
    description         text not null default '',
    date                bigint not null default 0,
    invoice_photo_file_id text,
    payment_method      text not null default 'CARD_TRANSFER',
    sub_account_name    text not null default '',
    cheque_number       text,
    lab_order_id        bigint,
    lab_order_sync_id   text not null default '',
    vendor_id           bigint,
    vendor_sync_id      text not null default '',
    maintenance_id      bigint,
    maintenance_sync_id text not null default '',
    owner_id            text not null default '',
    sync_id             text primary key,
    updated_at          bigint not null default 0,
    is_deleted          boolean not null default false
);
create index if not exists idx_clinic_expenses_owner_id on public.clinic_expenses (owner_id);
create index if not exists idx_clinic_expenses_date on public.clinic_expenses (date);

create table if not exists public.expense_payments (
    clinic_expense_id      bigint not null default 0,
    clinic_expense_sync_id text not null default '',
    amount                 bigint not null default 0,
    method                 text not null default 'CARD_TRANSFER',
    bank_account_id        bigint,
    bank_account_sync_id   text not null default '',
    source_sms_native_id   text not null default '',
    cheque_number          text,
    cheque_due_date        bigint,
    cheque_bank            text,
    date                   bigint not null default 0,
    note                   text not null default '',
    owner_id               text not null default '',
    sync_id                text primary key,
    updated_at             bigint not null default 0,
    is_deleted             boolean not null default false
);
create index if not exists idx_expense_payments_owner_id on public.expense_payments (owner_id);
create index if not exists idx_expense_payments_clinic_expense_sync_id on public.expense_payments (clinic_expense_sync_id);
create index if not exists idx_expense_payments_date on public.expense_payments (date);

create table if not exists public.personal_transactions (
    type                 text not null default '',
    sub_account_name     text not null default '',
    bank_account_id      bigint,
    bank_account_sync_id text not null default '',
    source_sms_native_id text not null default '',
    amount               bigint not null default 0,
    description          text not null default '',
    payment_method       text not null default 'CARD_TRANSFER',
    cheque_number        text,
    date                 bigint not null default 0,
    owner_id             text not null default '',
    sync_id              text primary key,
    updated_at           bigint not null default 0,
    is_deleted           boolean not null default false
);
create index if not exists idx_personal_transactions_owner_id on public.personal_transactions (owner_id);
create index if not exists idx_personal_transactions_date on public.personal_transactions (date);

create table if not exists public.lab_services (
    lab_name            text not null default '',
    service_name        text not null default '',
    price               bigint not null default 0,
    notes               text not null default '',
    frame_delivery_days integer not null default 3,
    final_delivery_days integer not null default 7,
    owner_id            text not null default '',
    sync_id             text primary key,
    updated_at          bigint not null default 0,
    is_deleted          boolean not null default false
);
create index if not exists idx_lab_services_owner_id on public.lab_services (owner_id);

create table if not exists public.installments (
    patient_id       bigint not null default 0,
    patient_sync_id  text not null default '',
    treatment_id     bigint,
    treatment_sync_id text not null default '',
    title            text not null default '',
    amount           bigint not null default 0,
    due_date         bigint not null default 0,
    is_paid          boolean not null default false,
    paid_date        bigint,
    note             text not null default '',
    owner_id         text not null default '',
    sync_id          text primary key,
    updated_at       bigint not null default 0,
    is_deleted       boolean not null default false
);
create index if not exists idx_installments_owner_id on public.installments (owner_id);
create index if not exists idx_installments_patient_sync_id on public.installments (patient_sync_id);
create index if not exists idx_installments_due_date on public.installments (due_date);

create table if not exists public.treatment_templates (
    title         text not null default '',
    title_fa      text not null default '',
    default_cost  bigint not null default 0,
    default_notes text not null default '',
    stages        text not null default '',
    is_starred    boolean not null default true,
    sort_order    integer not null default 0,
    category      text not null default '',
    owner_id      text not null default '',
    sync_id       text primary key,
    updated_at    bigint not null default 0,
    is_deleted    boolean not null default false
);
create index if not exists idx_treatment_templates_owner_id on public.treatment_templates (owner_id);

create table if not exists public.prescriptions (
    patient_id       bigint not null default 0,
    patient_sync_id  text not null default '',
    treatment_id     bigint not null default 0,
    treatment_sync_id text not null default '',
    treatment_title  text not null default '',
    stage_name       text not null default '',
    analgesic        text not null default '',
    antibiotic       text not null default '',
    mouthwash        text not null default '',
    other_meds       text not null default '',
    notes            text not null default '',
    public_note      text not null default '',
    duration_days    integer not null default 5,
    doctor_approved  boolean not null default false,
    date             bigint not null default 0,
    sent_sms         boolean not null default false,
    sent_whatsapp    boolean not null default false,
    owner_id         text not null default '',
    sync_id          text primary key,
    updated_at       bigint not null default 0,
    is_deleted       boolean not null default false
);
create index if not exists idx_prescriptions_owner_id on public.prescriptions (owner_id);
create index if not exists idx_prescriptions_patient_sync_id on public.prescriptions (patient_sync_id);
create index if not exists idx_prescriptions_treatment_sync_id on public.prescriptions (treatment_sync_id);

create table if not exists public.bank_accounts (
    bank_name      text not null default '',
    card_holder    text not null default '',
    card_number    text not null default '',
    account_number text not null default '',
    balance        bigint not null default 0,
    sms_keywords   text not null default '',
    -- توجه: cvv2 هرگز سینک نمی‌شود و در سرور وجود ندارد (EntityMapper حذفش می‌کند)
    expire_month   text not null default '',
    expire_year    text not null default '',
    iban           text not null default '',
    owner_id       text not null default '',
    sync_id        text primary key,
    updated_at     bigint not null default 0,
    is_deleted     boolean not null default false
);
create index if not exists idx_bank_accounts_owner_id on public.bank_accounts (owner_id);

create table if not exists public.personal_sub_accounts (
    name            text not null default '',
    category        text not null default 'EXPENSE',
    is_quick_select boolean not null default false,
    ledger_side     text not null default 'NONE',
    owner_id        text not null default '',
    sync_id         text primary key,
    updated_at      bigint not null default 0,
    is_deleted      boolean not null default false
);
create index if not exists idx_personal_sub_accounts_owner_id on public.personal_sub_accounts (owner_id);

create table if not exists public.fixed_incomes (
    title                text not null default '',
    amount               bigint not null default 0,
    day_of_month         integer not null default 1,
    bank_account_id      bigint,
    bank_account_sync_id text not null default '',
    source_sms_native_id text not null default '',
    bank_account_name    text not null default '',
    sub_account_name     text not null default '',
    last_processed_month text not null default '',
    is_active            boolean not null default true,
    owner_id             text not null default '',
    sync_id              text primary key,
    updated_at           bigint not null default 0,
    is_deleted           boolean not null default false
);
create index if not exists idx_fixed_incomes_owner_id on public.fixed_incomes (owner_id);

create table if not exists public.children_expenses (
    child_name text not null default '',
    title      text not null default '',
    amount     bigint not null default 0,
    date       bigint not null default 0,
    owner_id   text not null default '',
    sync_id    text primary key,
    updated_at  bigint not null default 0,
    is_deleted  boolean not null default false
);
create index if not exists idx_children_expenses_owner_id on public.children_expenses (owner_id);

create table if not exists public.clinic_settings (
    clinic_name                text not null default '',
    clinic_phone               text not null default '',
    clinic_address             text not null default '',
    doctor_name                text not null default '',
    doctor_license_no          text not null default '',
    children_names             text not null default '',
    staff_names                text not null default '',
    show_summary_card          boolean not null default true,
    show_quick_actions         boolean not null default true,
    show_charts                boolean not null default true,
    sms_sample_template        text not null default '',
    theme_name                 text not null default 'NEON_CARBON',
    is_dark_mode               boolean not null default true,
    clinical_font_size_pt      integer not null default 11,
    global_font_scale_index    integer not null default 9,
    appointment_template       text not null default '',
    invoice_template           text not null default '',
    treatment_plan_template    text not null default '',
    installment_template       text not null default '',
    overdue_installment_template text not null default '',
    post_op_template           text not null default '',
    prescription_template      text not null default '',
    endo_anatomy_overrides     text not null default '',
    working_days               text not null default '',
    logo_file_id               text,
    assistant_permissions_mask  integer not null default -1,
    onboarding_completed       boolean not null default false,
    doctor_pin_hash            text,
    doctor_pin_salt            text,
    assistant_pin_hash         text,
    assistant_pin_salt         text,
    owner_id                   text not null default '',
    sync_id                    text primary key,
    updated_at                 bigint not null default 0,
    is_deleted                 boolean not null default false
);
create index if not exists idx_clinic_settings_owner_id on public.clinic_settings (owner_id);

create table if not exists public.app_calendar_events (
    category          text not null default '',
    shamsi_day        integer,
    shamsi_month      integer,
    shamsi_year       integer,
    qamari_day        integer,
    qamari_month      integer,
    miladi_day        integer,
    miladi_month      integer,
    title             text not null default '',
    is_holiday_in_iran boolean not null default false,
    description       text not null default '',
    personal_type     text not null default '',
    owner_id          text not null default '',
    sync_id           text primary key,
    updated_at        bigint not null default 0,
    is_deleted        boolean not null default false
);
create index if not exists idx_app_calendar_events_owner_id on public.app_calendar_events (owner_id);

create table if not exists public.financial_ledger (
    source_type           text not null default '',
    source_id             bigint not null default 0,
    patient_id            bigint,
    patient_sync_id       text not null default '',
    bank_account_id       bigint,
    bank_account_sync_id  text not null default '',
    source_sms_native_id  text not null default '',
    amount_delta          bigint not null default 0,
    status                text not null default 'POSTED',
    occurred_at           bigint not null default 0,
    description           text not null default '',
    created_at            bigint not null default 0,
    owner_id              text not null default '',
    sync_id               text primary key,
    updated_at            bigint not null default 0,
    is_deleted            boolean not null default false
);
create index if not exists idx_financial_ledger_owner_id on public.financial_ledger (owner_id);
create unique index if not exists idx_financial_ledger_source on public.financial_ledger (source_type, source_id);
create index if not exists idx_financial_ledger_occurred_at on public.financial_ledger (occurred_at);

create table if not exists public.audit_events (
    actor_role  text not null default '',
    action      text not null default '',
    entity_type text not null default '',
    entity_id   bigint,
    result      text not null default 'ALLOWED',
    details     text not null default '',
    occurred_at bigint not null default 0,
    owner_id    text not null default '',
    sync_id     text primary key,
    updated_at  bigint not null default 0,
    is_deleted  boolean not null default false
);
create index if not exists idx_audit_events_owner_id on public.audit_events (owner_id);
create index if not exists idx_audit_events_occurred_at on public.audit_events (occurred_at);

create table if not exists public.drug_templates (
    category              text not null default 'OTHER',
    title                 text not null default '',
    dosage                text not null default '',
    timing                text not null default '',
    instructions          text not null default '',
    default_duration_days integer not null default 5,
    owner_id              text not null default '',
    sync_id               text primary key,
    updated_at            bigint not null default 0,
    is_deleted            boolean not null default false
);
create index if not exists idx_drug_templates_owner_id on public.drug_templates (owner_id);

create table if not exists public.staff_members (
    full_name      text not null default '',
    role           text not null default 'ASSISTANT',
    phone          text not null default '',
    photo_file_id  text,
    owner_id       text not null default '',
    sync_id        text primary key,
    updated_at     bigint not null default 0,
    is_deleted     boolean not null default false
);
create index if not exists idx_staff_members_owner_id on public.staff_members (owner_id);

-- ---------------------------------------------------------------------------
-- ۳) Row Level Security — هر کاربر فقط داده‌های خودش
--    (users با کلید id؛ بقیه با owner_id)
-- ---------------------------------------------------------------------------
alter table public.users                 enable row level security;
alter table public.patients              enable row level security;
alter table public.treatments            enable row level security;
alter table public.payments              enable row level security;
alter table public.appointments          enable row level security;
alter table public.treatment_stages      enable row level security;
alter table public.patient_documents     enable row level security;
alter table public.vendors               enable row level security;
alter table public.vendor_invoices       enable row level security;
alter table public.lab_orders            enable row level security;
alter table public.equipment_maintenance enable row level security;
alter table public.clinic_expenses       enable row level security;
alter table public.expense_payments      enable row level security;
alter table public.personal_transactions enable row level security;
alter table public.lab_services          enable row level security;
alter table public.installments          enable row level security;
alter table public.treatment_templates   enable row level security;
alter table public.prescriptions         enable row level security;
alter table public.bank_accounts         enable row level security;
alter table public.personal_sub_accounts enable row level security;
alter table public.fixed_incomes         enable row level security;
alter table public.children_expenses     enable row level security;
alter table public.clinic_settings       enable row level security;
alter table public.app_calendar_events   enable row level security;
alter table public.financial_ledger      enable row level security;
alter table public.audit_events          enable row level security;
alter table public.drug_templates        enable row level security;
alter table public.staff_members         enable row level security;

drop policy if exists "users_own_rows" on public.users;
create policy "users_own_rows" on public.users
    for all to authenticated
    using (id = auth.uid()::text)
    with check (id = auth.uid()::text);

-- برای همه‌ی جدول‌های داده، یک Policy واحد «فقط مالک»
do $$
declare
    t text;
begin
    foreach t in array array[
        'patients','treatments','payments','appointments','treatment_stages',
        'patient_documents','vendors','vendor_invoices','lab_orders',
        'equipment_maintenance','clinic_expenses','expense_payments',
        'personal_transactions','lab_services','installments',
        'treatment_templates','prescriptions','bank_accounts',
        'personal_sub_accounts','fixed_incomes','children_expenses',
        'clinic_settings','app_calendar_events','financial_ledger',
        'audit_events','drug_templates','staff_members'
    ]
    loop
        execute format('drop policy if exists "%s_owner_rows" on public.%I', t, t);
        execute format(
            'create policy "%s_owner_rows" on public.%I for all to authenticated
             using (owner_id = auth.uid()::text)
             with check (owner_id = auth.uid()::text)', t, t);
    end loop;
end $$;

-- ---------------------------------------------------------------------------
-- ۴) باکت‌های Storage (عمومی برای خواندن — مثل Appwrite قبلی با Role.any()؛
--    نوشتن/حذف فقط توسط مالکِ فایل)
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public)
values ('patient_media', 'Patient Media', true)
on conflict (id) do update set public = true, name = 'Patient Media';

insert into storage.buckets (id, name, public)
values ('clinic_assets', 'Clinic Assets', true)
on conflict (id) do update set public = true, name = 'Clinic Assets';

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
-- ۵) تابع حذف کامل حساب — از داخل اپ با POST /rest/v1/rpc/delete_my_account
--    همه‌ی رکوردها + فایل‌های Storage + خودِ حساب auth حذف می‌شوند.
--    SECURITY DEFINER یعنی با نقش postgres اجرا می‌شود و از RLS عبور می‌کند،
--    اما فقط و فقط ردیف‌های خودِ کاربرِ احراز‌هویت‌شده را پاک می‌کند.
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
        'audit_events','drug_templates','staff_members','users'
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

-- ---------------------------------------------------------------------------
-- ۶) Realtime — برای به‌روزرسانی زنده بین دستگاه‌ها در آینده
-- ---------------------------------------------------------------------------
do $$
declare
    t text;
begin
    foreach t in array array[
        'patients','treatments','payments','appointments','treatment_stages',
        'patient_documents','vendors','vendor_invoices','lab_orders',
        'equipment_maintenance','clinic_expenses','expense_payments',
        'personal_transactions','lab_services','installments',
        'treatment_templates','prescriptions','bank_accounts',
        'personal_sub_accounts','fixed_incomes','children_expenses',
        'clinic_settings','app_calendar_events','financial_ledger',
        'audit_events','drug_templates','staff_members'
    ]
    loop
        execute format('alter table public.%I replica identity full;', t);
        execute format('alter publication supabase_realtime add table public.%I;', t);
    end loop;
exception
    when others then
        -- اگر جدولی قبلاً در publication بوده، ادامه بده
        null;
end $$;

-- ############################################################################
-- بخش B — صف پیامک‌های بانکی (unregistered_bank_sms)
-- ############################################################################
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

-- ############################################################################
-- بخش C — سیستم اعلان بروزرسانی اپ (app_updates + app_releases)
-- ############################################################################
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

-- ############################################################################
-- بخش D — ساختار درختی سرفصل‌های حساب شخصی (پرمیس)
-- ############################################################################
-- ============================================================================
-- DentalPro — Migration 0004: ساختار درختی سرفصل‌های حساب شخصی (پرمیس)
-- ============================================================================
-- ستون parent_name به جدول personal_sub_accounts اضافه می‌شود تا ساختار
-- «سرفصل ▸ زیرشاخه» دیتابیس پرمیس (۱۶۵ حساب: هزینه‌ها/درآمدها/دارایی‌ها/
-- بدهی‌ها/سرمایه/بدهکاران/بستانکاران) بین دستگاه‌ها سینک شود.
-- خودِ حساب‌ها روی هر دستگاه هنگام اولین اجرا seed شده و از طریق صف سینک
-- به سرور (با owner_id همان کاربر) ارسال می‌شوند — نیازی به seed سمت سرور نیست.
-- ============================================================================

alter table public.personal_sub_accounts
    add column if not exists parent_name text not null default '';

create index if not exists idx_personal_sub_accounts_parent
    on public.personal_sub_accounts (owner_id, parent_name);

-- ############################################################################
-- بخش E — هات‌فیکس نهایی castهای uuid/text (نسخه نهایی policyها و تابع حذف حساب)
-- ############################################################################
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

-- ============================================================================
-- پایان اسکریپت — پس از اجرا:
--   ▸ select count(*) from information_schema.tables where table_schema='public';
--     باید ۳۰ جدول ببینید (۲۸ اولیه + unregistered_bank_sms + app_updates)
--   ▸ برای انتشار نسخه جدید اپ از قالب‌های supabase/app_update_release_templates.sql
--     استفاده کنید (APK در باکت app_releases + یک ردیف در app_updates)
-- ============================================================================
