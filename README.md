# DentalPro Android

> **Release status: NO-GO.** This codebase is undergoing correctness, privacy, security, backup, and architecture remediation. It is not approved for production or clinical use. See [release status](docs/RELEASE_STATUS.md).

## Backend

The app is backed by **Supabase** (Postgres + GoTrue auth + Storage + Edge Functions).
Server-side setup (one-time) is documented in **[docs/SUPABASE_SETUP.md](docs/SUPABASE_SETUP.md)** —
it boils down to running [`supabase/migrations/0001_dentalpro_init.sql`](supabase/migrations/0001_dentalpro_init.sql)
in the SQL editor, configuring auth providers, and deploying the Edge Functions in
[`supabase/functions/`](supabase/functions/) (`send-otp`, `verify-otp`, `phone-to-email`, `extract-cheque-data`).

Architecture: Room (SQLCipher-encrypted) is the local source of truth; a
`PendingSyncOp` queue pushes/pulls to PostgREST row-by-row with `sync_id` as the
cross-device identity. Every row carries `owner_id` and RLS restricts access to the
owning account. Files live in the `patient_media` / `clinic_assets` storage buckets
under `{uid}/{uuid}.jpg` paths.

The Supabase **anon key** is public-by-design and lives in
`app/src/main/java/com/example/util/Supabase.kt`. The **service_role key must never
enter the app or this repository** — it only exists as an Edge Function secret.

## Development build

Prerequisites:

- JDK 17
- Android SDK (API 36 / Build Tools available through Android Studio)

Use the checked-in, checksum-validated Gradle wrapper:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Do not add API keys, Firebase configuration, signing keys, `keystore.properties`, `local.properties`, or `.env` files to Git.

## Release guard

Release variants are minified and require explicit, externally supplied signing/version configuration plus `RELEASE_APPROVED=true`. Debug signing is never used as a release fallback. The blocking criteria are in [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md).
