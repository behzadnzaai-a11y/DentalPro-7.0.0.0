# Release status: NO-GO

This repository is under remediation and is **not approved for production or clinical use**.

A release build is intentionally blocked unless all of the following are supplied by an authorized release process:

- a non-placeholder `VERSION_CODE` and `VERSION_NAME`;
- `RELEASE_APPROVED=true` after the release checklist is signed off;
- an external `keystore.properties` file and signing keystore;
- passing unit tests, Android lint, migration/backup/integrity tests, and a release build.

Debug builds contain development behavior and must not be distributed as a medical, accounting, backup, or security-complete product. See `docs/RELEASE_CHECKLIST.md` for the gate.
