# Production release checklist

All items are blocking.

- [ ] CI unit, integration, migration, backup round-trip, authorization, lint and release builds pass.
- [ ] Accounting reconciliation and cheque-state tests pass with no balance drift.
- [ ] Encrypted backup is restored on a clean device and record/file checksums match.
- [ ] Cloud backup list/download/delete/recovery is tested against the production service.
- [ ] Security review covers database/files at rest, lock timeout, role enforcement and audit trail.
- [ ] Clinical prescription decision support is independently validated or clearly disabled/non-diagnostic.
- [ ] PHI is redacted from lock-screen notifications and widgets.
- [ ] Accessibility and localization checks pass for supported locales and font scales.
- [ ] Privacy policy, data retention/deletion flow and support contacts are finalized.
- [ ] Version/signing values are injected by the authorized release environment.
- [ ] `RELEASE_APPROVED=true` is set only after sign-off.
