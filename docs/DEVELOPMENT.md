# Development & verification

Fakturiva uses native Android framework components (Java, SQLite, PdfDocument, AndroidKeyStore, Documents Provider, no third-party runtime libraries). minSdk 24, targetSdk 35.

Open this repository in Android Studio or build with JDK 17 and Gradle 8.9:

```shell
gradle :app:assembleDebug :app:bundleRelease :app:lintDebug
javac -d build/smoke app/src/main/java/com/brendigo/fakturiva/{Money,SecureBackup}.java quality/NativeCoreSmoke.java
java -cp build/smoke com.brendigo.fakturiva.NativeCoreSmoke
```

## Signing & releases

The preview APK is debug-signed and intended only for development. The release AAB is unsigned unless an established, secure signing key is provided; it is NOT a ready-to-upload Google Play bundle. Never commit a signing key to Git. Production releases require a maintained upload key, reproducible signing configuration and verified closed testing.

## Storage and recovery

SQLite transactions preserve consistency for edits to saved documents. Confirmed documents are immutable within the available UI; drafts can be edited. Every successful change attempts to create an encrypted in-app snapshot, rotating across three copies. Failures show a warning. Snapshots are device-bound; uninstalling the application or losing the phone removes them. To protect against device loss, export an encrypted password-protected backup off-device and verify restore on a separate test device before relying on it.

Local recovery first preserves a damaged database file separately. Restore operates transactionally and preserves another pre-restore snapshot. This is defense-in-depth, not a zero-data-loss guarantee. Test storage pressure, process death, encryption key invalidation, migration from v1 to v2 and restore from old backups before production.

## Functional scope and compliance

PDF rendering is offline. There is NO Fiscalization/eRačun exchange, online signing, eInvoice XML protocol, government API integration or verified tax compliance. VAT rates provided in the UI are illustrative and must be reviewed for each business/tax case. No internet permission. Do not publish as a compliant invoicing product until these obligations and appropriate legal contexts have been implemented and audited.

## Visual acceptance

UI reference ZIP provided by the owner contains 20 screens (393×852 dp reference/1179×2556 PNG). Pixel-by-pixel equivalence is not yet verified on Android emulator and different screen densities. A device screenshot comparison and accessibility review are required before marking this task complete.
