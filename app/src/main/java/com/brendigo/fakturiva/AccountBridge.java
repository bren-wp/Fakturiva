package com.brendigo.fakturiva;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/**
 * First-run bridge between the Android working database and the Fakturiva
 * account hosted at fakturiva.eu. This is NOT a second identity provider.
 *
 * Server registration/login is verified before a local PIN profile is created.
 * Restores never replace an existing local profile and never overwrite an
 * existing remote revision. A local database is an offline working copy.
 */
public final class AccountBridge {
    public static final String HOST = "https://fakturiva.eu/";
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private AccountBridge() {}

    public interface Callback {
        /** Called on the UI thread. The profile ID is non-null on success. */
        void complete(String error, String profileId);
    }

    private static void validate(String username, char[] pass, char[] pin) {
        if (username == null || !username.trim().matches(
                "[A-Za-z0-9][A-Za-z0-9._+@-]{2,79}"))
            throw new IllegalArgumentException("Unesite ispravnu e-adresu ili korisničko ime.");
        if (pass == null || pass.length < 12 || pass.length > 256)
            throw new IllegalArgumentException("Lozinka mora imati 12–256 znakova.");
        if (pin == null || !new String(pin).matches("[0-9]{4,6}"))
            throw new IllegalArgumentException("Lokalni PIN mora imati 4–6 znamenki.");
    }

    private static void ensureNewLocalProfile(Context app, String username) throws Exception {
        for (String existing : LocalAccounts.names(app)) {
            if (existing.equalsIgnoreCase(username.trim()))
                throw new IllegalStateException("Ovaj račun već postoji na uređaju. " +
                    "Prijavite se lokalnim PIN-om i provjerite sinkronizaciju.");
        }
    }

    private static String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest) result.append(String.format(Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    /**
     * Confirm a web password for a profile already present on this device.
     * A successful server login does NOT bypass the device's local PIN;
     * unlocking the offline database remains the responsibility of LocalAccounts.
     * Offline users can still select the independent PIN-only login action.
     */
    public static void verifyCredentials(Activity activity, String name,
                                         char[] password, Callback callback) {
        final String username = name == null ? "" : name.trim();
        final char[] secret = password == null ? new char[0] : password.clone();
        new Thread(() -> {
            String error = null, token = null, url = null;
            try {
                if (!username.matches("[A-Za-z0-9][A-Za-z0-9._+@-]{2,79}") ||
                    secret.length < 12 || secret.length > 256)
                    throw new IllegalArgumentException("Neispravna web prijava.");
                url = SelfHostedSyncClient.apiUrl(HOST);
                JSONObject auth = SelfHostedSyncClient.command("login", username,
                    new String(secret));
                token = SelfHostedSyncClient.request(url, auth, null)
                    .getString("token");
            } catch(Exception failure) {
                error = failure.getMessage() == null ?
                    "Web prijava nije uspjela." : failure.getMessage();
            } finally {
                if (url != null && token != null) {
                    try { SelfHostedSyncClient.request(url,
                        new JSONObject().put("action","logout"),token); }
                    catch(Exception ignored) { /* token expires independently */ }
                }
                Arrays.fill(secret, '\0');
            }
            final String result = error;
            UI.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed())
                    callback.complete(result, null);
            });
        }, "FakturivaVerifyWebPassword").start();
    }

    /** Starts registration on the server before creating the offline cache. */
    public static void register(Activity activity, String username, char[] passphrase,
                                char[] pin, Callback callback) {
        execute(activity, username, passphrase, pin, true, callback);
    }

    /** Restores a verified encrypted remote backup to a new local PIN profile. */
    public static void restoreLogin(Activity activity, String username, char[] passphrase,
                                    char[] pin, Callback callback) {
        execute(activity, username, passphrase, pin, false, callback);
    }

    private static void execute(Activity activity, String name, char[] password,
                                char[] localPin, boolean create, Callback callback) {
        final Context app = activity.getApplicationContext();
        final char[] secret = password == null ? new char[0] : password.clone();
        final char[] digits = localPin == null ? new char[0] : localPin.clone();
        final String username = name == null ? "" : name.trim();
        new Thread(() -> {
            String error = null, id = null, token = null, url = null;
            Store store = null;
            try {
                validate(username, secret, digits);
                // Existing on-device records are never silently overwritten by
                // a remote sign-in. Use the existing local PIN and explicit sync.
                ensureNewLocalProfile(app, username);
                url = SelfHostedSyncClient.apiUrl(HOST);
                JSONObject auth = SelfHostedSyncClient.command(
                    create ? "register" : "login", username, new String(secret));
                JSONObject signedIn = SelfHostedSyncClient.request(url, auth, null);
                token = signedIn.getString("token");
                int remoteRevision = signedIn.getInt("revision");
                JSONObject snapshot = null;
                if (!create) {
                    JSONObject remote = SelfHostedSyncClient.request(url,
                        new JSONObject().put("action", "load"), token);
                    remoteRevision = remote.getInt("revision");
                    String encoded = remote.optString("backup_b64", "");
                    if (!encoded.isEmpty()) {
                        byte[] sealed = Base64.decode(encoded, Base64.DEFAULT);
                        try {
                            if (!sha256(sealed).equals(remote.getString("sha256")))
                                throw new SecurityException("Kontrolni zbroj servera nije ispravan.");
                            byte[] plain = SecureBackup.decrypt(sealed, secret);
                            try {
                                snapshot = new JSONObject(
                                    new String(plain, StandardCharsets.UTF_8));
                            } finally {
                                Arrays.fill(plain, (byte) 0);
                            }
                        } finally {
                            Arrays.fill(sealed, (byte) 0);
                        }
                        if (!"fakturiva-backup-v1".equals(snapshot.optString("format")))
                            throw new SecurityException("Neispravna poslovna kopija.");
                    } else if (remoteRevision != 0) {
                        throw new SecurityException("Na serveru nedostaje očekivana kopija.");
                    }
                }
                if (snapshot == null) {
                    // A new account (or an interrupted first upload) needs an
                    // initial encrypted backup, but must NOT acquire a durable
                    // local PIN profile until the server acknowledges it.
                    // Stage an isolated EMPTY database that has no registry
                    // entry, never touch an existing user's SQLite file.
                    if (remoteRevision != 0)
                        throw new SecurityException("Nedostaje kopija postojeće verzije.");
                    String stagedId = java.util.UUID.randomUUID().toString()
                        .replace("-", "");
                    Store staged = null;
                    JSONObject firstBackup;
                    try {
                        staged = new Store(app, stagedId);
                        firstBackup = staged.backup();
                    } finally {
                        if (staged != null) staged.close();
                        app.deleteDatabase(Store.databaseName(stagedId));
                    }
                    byte[] raw = firstBackup.toString().getBytes(StandardCharsets.UTF_8);
                    byte[] encrypted;
                    try { encrypted = SecureBackup.encrypt(raw, secret); }
                    finally { Arrays.fill(raw, (byte) 0); }
                    try {
                        JSONObject upload = new JSONObject().put("action", "save")
                            .put("expected_revision", 0)
                            .put("backup_b64", Base64.encodeToString(
                                encrypted, Base64.NO_WRAP));
                        JSONObject ack = SelfHostedSyncClient.request(url, upload, token);
                        if (!sha256(encrypted).equals(ack.getString("sha256")))
                            throw new SecurityException(
                                "Potvrda šifrirane kopije nije ispravna.");
                        remoteRevision = ack.getInt("revision");
                        if (remoteRevision != 1)
                            throw new SecurityException(
                                "Neispravna prva revizija sigurnosne kopije.");
                    } finally { Arrays.fill(encrypted, (byte) 0); }
                }
                // Network failure, server conflict and decryption rejection
                // leave NO newly registered local account behind. The web
                // account can be recovered by ordinary email/password login.
                ensureNewLocalProfile(app, username);
                id = LocalAccounts.register(app, username, digits);
                store = new Store(app, id);
                if (snapshot != null) {
                    // Store.restore validates schema/relations/amounts and
                    // preserves a recovery snapshot before replacement.
                    store.restore(snapshot);
                }
                CloudMirror.connectAcknowledged(app, id, url, username,
                    secret, remoteRevision);
            } catch (Exception failure) {
                error = failure.getMessage();
                if (error == null || error.isEmpty())
                    error = "Povezivanje s računom Fakturiva nije uspjelo.";
            } finally {
                if (store != null) store.close();
                if (url != null && token != null) {
                    try {
                        SelfHostedSyncClient.request(url,
                            new JSONObject().put("action", "logout"), token);
                    } catch (Exception ignored) {
                        // Server token expires independently after 24 hours.
                    }
                }
                Arrays.fill(secret, '\0');
                Arrays.fill(digits, '\0');
            }
            final String resultError = error, resultId = error == null ? id : null;
            UI.post(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed())
                    callback.complete(resultError, resultId);
            });
        }, create ? "FakturivaWebRegistration" : "FakturivaWebRestore").start();
    }
}
