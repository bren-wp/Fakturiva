# Fakturiva — security, dead-code and cross-platform verification matrix
**Date:** 14 November 2026 · **Target:** PR #39 (follow-up to merged PR #38) · **Status:** ongoing / not a production certification.

## Verified evidence

- PR #38 merged into `main` at `7de2d9addeae0745645327e706a0ac77b34e40e8`. Existing release `v0.18.6-preview.1` contains debug/unsigned Android artifacts; it is **not** proof of an owner-signed Play update or hosted deployment.
- Baseline source archive for `v0.18.6-preview.1`: local Python unittest discovery **58 tests successful**, initial static inventory **135 source files**, zero matches for its initial limited critical rules and zero Java private-method candidates. Those are *heuristic results*, not a comprehensive penetration test.
- On an isolated local PHP fixture, added `document-publish-atomicity-smoke.php` verified HTTP 413 from the *real* `publish()` path at the document/line capacity: unchanged encrypted account bytes, Android `load` response, current revision and encrypted revision history. No production data was read or changed.
- The updated static analyzer now searches for forbidden runtime **JSON** account records (not only .php source files), dangerous WebView2 host objects/messages/devtools and Android file URL access. False negatives and framework reflection remain possible.
- GitHub Actions on earlier PR #39 commit `6cf71ad5` reported green for PHP, Android verification, Windows build and read-only public HTTPS probe. Every newer commit needs its **own** green CI; cancelled or in-progress jobs are never counted.
- Native Android screen comparison on merged `main` SHA `7de2d9ad`, using the owner's private 20-reference ZIP: mean RGB MAE **8.273/255**, exact 1:1 **0/20**. See `docs/V0.18.6_20_SCREEN_PIXEL_AUDIT_2026-11-14.md`. No image generation or substitution.

## Security-control matrix

| Area | Inspected implementation | Current verification / risk |
|---|---|---|
| Login & passwords | PHP Argon2id/bcrypt verifier, Android password-protected local profile; no active PIN login | Synthetic login tests; no production account assertion |
| Sessions & tokens | Strict HttpOnly/Secure/SameSite web session and authenticated bearer token | PHP smoke tests; deployment/session persistence not independently checked |
| CSRF / XSS | CSRF protection on web actions, escaping and restrictive CSP | Added escaped print-preview regression; real-browser CSP test remains |
| Tenant isolation | Encrypted account records keyed by authenticated ID | Synthetic multi-tenant tests; independent production cross-account verification remains |
| Encrypted storage | FAK1 AES-256-GCM backed up through revisioned CAS; encrypted account records | Isolated crypto/rollback tests; external backups and production key permissions need operations review |
| Concurrent changes | HTTP 409 CAS, explicit Android conflict handling and recovery vault | Synthetic tests; multi-physical-device failure injection not yet done |
| Android privacy | Disable OS backup and cleartext; local database / permissions | Static + emulator tests; device-specific restore not fully certified |
| Windows host | Exact HTTPS origin navigation, safe initial controls, cross-origin download guard | Policy smoke test + Windows build gate; interactive WebView2 runtime/printing remains to be tested |
| Web print | Authenticated snapshot, HTML-escaped details, integer-cent totals and integrity gate | PHP regression + CI pending on exact head; browser 'Save to PDF' is **not** server-generated/fiscalized PDF |
| Release security | APK/AAB artifacts, checksums, GitHub Actions | No validated owner keystore or Google Play release signature |

## Dead-code & maintainability review

The scanner uses conservative textual Java private-method candidates only; a zero count **does not prove zero dead code**. Dynamic Android lifecycle calls, handlers, layouts, JNI/reflection, PHP routes and JavaScript event attributes can make apparently unreferenced code live. **No code was deleted solely from scanner output.**

Hotspots to refactor with regression tests: `MainActivity.java` (~5,668 lines), `Store.java` (~734), `web/lib.php` (~500) and `CloudMirror.java` (~495). Existing compatibility redirects `web/login-pin.php` and `web/identify.php` deliberately remain, because they redirect old bookmarks to password login without accepting PINs. Their removal must be assessed against live links before deletion.

## Production Android ↔ web ↔ Windows E2E: not yet proven

Public `GET /api.php?action=health` tests only reachability/TLS and must never be confused with authentication, deployment SHA or an end-to-end business flow. This session did **not** have an explicitly provisioned staging user, production test credentials or permission to create/change real business records on a live account.

Mandatory safe acceptance sequence: (1) provision a clearly isolated synthetic test account on authorized staging or production; (2) prove current deployed SHA, TLS, session/logout and cross-account denials; (3) create invoice on running Android APK; (4) verify same ID, currency, totals and revision in web and a *running* Windows WebView2 instance; (5) make an authorized web edit and confirm Android pull; (6) force concurrent offline 409 and prove original data, RecoveryVault and encrypted history remain; (7) interrupt network and retest without duplicate documents; (8) test PDF print and download UI in real Windows session; (9) destroy *only* the dedicated test account after explicitly approved verification.

Without these steps, mark production cross-platform E2E **UNVERIFIED**, not failed or passed.

## Visual/functional exit criteria

- Re-run all 20 real Android screenshots at the same commit, viewport, fonts and density; compare exact pixel mismatch, SSIM and RGB MAE against the original **private** reference, plus full responsive/accessibility matrix. Current exact parity: **0/20**.
- Prioritize Dark theme, Home, Quote preview, Client details, Invoices and Settings, followed by the remaining screens. Avoid hardcoded screenshots, fake values, or overlaying references.
- Test *every* user action and state transition on Android, web and Windows; Android UI instrumentation and build green do not prove complete end-user functionality.
- Review PHP HTML print preview in real browsers; it is read-only, prints through browser and does not issue electronic/fiscal documents. Legal e-invoice and fiscalization integrations need independent verification.
- PR #39 stays **Draft** until the full latest-head CI matrix is green, critical review findings are closed, and a release is expressly justified. Do not overwrite or republish the existing immutable preview tag.

**Never migrate/reset live accounts or offline data as an audit shortcut.**
