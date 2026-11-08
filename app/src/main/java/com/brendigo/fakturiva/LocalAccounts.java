package com.brendigo.fakturiva;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local-only profiles. Neither usernames nor PINs are sent to a server.
 *
 * The account registry is sealed by a non-exportable Android Keystore AES key.
 * PIN verifiers are salted PBKDF2 values (no plaintext PIN storage), and
 * attempts are throttled and persisted across app restarts. A 4-digit PIN is
 * intrinsically weak; the Keystore and Android device screen lock remain
 * important defenses against offline extraction.
 */
public final class LocalAccounts {
    private static final String KEY_ALIAS = "fakturiva-local-accounts-v1";
    private static final byte[] HEADER = {'F','A','C','1'};
    private static final Pattern USERNAME =
        Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+@-]{2,79}");
    private static final Pattern PIN = Pattern.compile("[0-9]{4,6}");
    private static final int KDF_ROUNDS = 210000;
    private static final int MAX_REGISTRY = 1024 * 1024;
    private static volatile String sessionId = null;
    private static volatile String sessionName = null;

    private LocalAccounts() {}

    private static File file(Context context) {
        return new File(context.getNoBackupFilesDir(), "fakturiva-accounts.fv");
    }

    public static String activeId() { return sessionId; }
    public static String activeName() { return sessionName; }

    /** Clear in-memory authorization; it is intentionally not restored after process death. */
    public static void lock() { sessionId = null; sessionName = null; }

    public static boolean validProfileId(String id) {
        return id != null && id.matches("[a-f0-9]{32}");
    }

    private static String normalized(String username) {
        if (username == null) throw new IllegalArgumentException("Unesite korisničko ime");
        String name = username.trim();
        if (!USERNAME.matcher(name).matches())
            throw new IllegalArgumentException("Korisničko ime: 3–80 znakova (slova, brojke, . _ + @ -)");
        return name.toLowerCase(Locale.ROOT);
    }

    private static void checkPin(char[] pin) {
        if (pin == null || !PIN.matcher(new String(pin)).matches())
            throw new IllegalArgumentException("PIN mora imati 4–6 znamenki");
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry existing = store.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry) existing).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static byte[] passwordHash(char[] pin, byte[] salt) throws Exception {
        // PBKDF2-HMAC-SHA1 is supported on the app's minimum Android API 24.
        PBEKeySpec spec = new PBEKeySpec(pin, salt, KDF_ROUNDS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] unb64(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }

    private static JSONObject read(Context context) throws Exception {
        AtomicFile atomic = new AtomicFile(file(context));
        File target = file(context);
        if (!target.exists() && !new File(target.getPath() + ".bak").exists())
            return new JSONObject().put("version", 1).put("accounts", new JSONArray());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (FileInputStream in = atomic.openRead()) {
            byte[] block = new byte[4096];
            int n;
            while ((n = in.read(block)) != -1) {
                if (bytes.size() + n > MAX_REGISTRY)
                    throw new IllegalStateException("Prevelika datoteka lokalnih računa");
                bytes.write(block, 0, n);
            }
        }
        byte[] sealed = bytes.toByteArray();
        if (sealed.length < 4+12+16)
            throw new IllegalStateException("Oštećena pohrana korisničkih računa");
        for (int i=0; i<4; i++)
            if (sealed[i] != HEADER[i])
                throw new IllegalStateException("Nepoznata verzija lokalnih računa");
        byte[] iv = Arrays.copyOfRange(sealed,4,16);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        cipher.updateAAD(HEADER);
        byte[] plain = cipher.doFinal(sealed,16,sealed.length-16);
        try {
            JSONObject registry = new JSONObject(new String(plain,StandardCharsets.UTF_8));
            if (registry.optInt("version") != 1 || registry.optJSONArray("accounts")==null)
                throw new IllegalStateException("Nepodržana verzija korisničkih računa");
            return registry;
        } finally {
            Arrays.fill(plain,(byte)0);
            Arrays.fill(sealed,(byte)0);
        }
    }

    private static void write(Context context, JSONObject registry) throws Exception {
        byte[] raw = registry.toString().getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_REGISTRY - 128)
            throw new IllegalStateException("Previše lokalnih računa");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        cipher.updateAAD(HEADER);
        byte[] encrypted = cipher.doFinal(raw);
        AtomicFile atomic = new AtomicFile(file(context));
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(HEADER);
            out.write(iv);
            out.write(encrypted);
            atomic.finishWrite(out);
            out = null;
        } catch (Exception failure) {
            if (out != null) atomic.failWrite(out);
            throw failure;
        } finally {
            Arrays.fill(raw,(byte)0);
            Arrays.fill(encrypted,(byte)0);
        }
    }

    public static synchronized boolean hasAccounts(Context context) throws Exception {
        return read(context).getJSONArray("accounts").length() > 0;
    }

    public static synchronized String[] names(Context context) throws Exception {
        JSONArray accounts = read(context).getJSONArray("accounts");
        ArrayList<String> result = new ArrayList<>();
        for (int i=0; i<accounts.length(); i++)
            result.add(accounts.getJSONObject(i).getString("username"));
        return result.toArray(new String[0]);
    }

    private static JSONObject find(JSONArray accounts, String normalized) throws Exception {
        for (int i=0; i<accounts.length(); i++) {
            JSONObject entry = accounts.getJSONObject(i);
            if (normalized.equals(entry.getString("normalized"))) return entry;
        }
        return null;
    }

    public static synchronized String register(Context context, String username, char[] pin)
            throws Exception {
        String normalized = normalized(username);
        checkPin(pin);
        JSONObject registry = read(context);
        JSONArray accounts = registry.getJSONArray("accounts");
        if (accounts.length() >= 25)
            throw new IllegalStateException("Najviše 25 lokalnih korisničkih profila");
        if (find(accounts,normalized) != null)
            throw new IllegalArgumentException("Korisničko ime već postoji na ovom uređaju");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = passwordHash(pin,salt);
        String id = java.util.UUID.randomUUID().toString().replace("-","");
        JSONObject entry = new JSONObject()
            .put("id",id).put("username",username.trim())
            .put("normalized",normalized)
            .put("salt",b64(salt)).put("verifier",b64(hash))
            .put("attempts",0).put("blockedUntil",0L);
        Arrays.fill(hash,(byte)0);
        accounts.put(entry);
        write(context,registry);
        return id;
    }

    /**
     * Returns a profile id only after successful PIN verification.
     * Invalid credentials never activate another user's database.
     */
    public static synchronized String verify(Context context, String username, char[] pin)
            throws Exception {
        String normal = normalized(username);
        JSONObject registry = read(context);
        JSONObject entry = find(registry.getJSONArray("accounts"),normal);
        if (entry == null)
            throw new SecurityException("Korisničko ime ili PIN nisu ispravni");
        long now = System.currentTimeMillis();
        long blockedUntil = entry.optLong("blockedUntil",0L);
        if (blockedUntil > now) {
            long seconds = (blockedUntil-now+999)/1000;
            throw new SecurityException("Previše pokušaja. Pokušajte ponovno za " +
                seconds + " sekundi.");
        }
        // Work is bounded even for an invalid PIN; no plaintext PINs are stored.
        boolean matched = false;
        if (pin != null && PIN.matcher(new String(pin)).matches()) {
            byte[] actual = passwordHash(pin,unb64(entry.getString("salt")));
            byte[] expected = unb64(entry.getString("verifier"));
            matched = MessageDigest.isEqual(actual,expected);
            Arrays.fill(actual,(byte)0);
            Arrays.fill(expected,(byte)0);
        }
        if (!matched) {
            int attempts = Math.min(40,entry.optInt("attempts",0)+1);
            entry.put("attempts",attempts);
            if (attempts >= 5)
                entry.put("blockedUntil",now + Math.min(3600_000L,
                    30_000L * (1L << Math.min(7,attempts-5))));
            write(context,registry);
            throw new SecurityException("Korisničko ime ili PIN nisu ispravni");
        }
        entry.put("attempts",0).put("blockedUntil",0L);
        write(context,registry);
        return entry.getString("id");
    }

    /** Must be called only with an id returned by register() or verify(). */
    static synchronized void unlock(Context context, String verifiedId) throws Exception {
        if (!validProfileId(verifiedId))
            throw new SecurityException("Neispravan identifikator profila");
        JSONArray accounts = read(context).getJSONArray("accounts");
        for (int i=0;i<accounts.length();i++) {
            JSONObject entry=accounts.getJSONObject(i);
            if (verifiedId.equals(entry.getString("id"))) {
                sessionId=verifiedId;
                sessionName=entry.getString("username");
                return;
            }
        }
        throw new SecurityException("Korisnički račun više ne postoji");
    }

    /** Delete an authenticated local profile. Caller must close its SQLite DB first. */
    public static synchronized void delete(Context context,String username,char[] pin)
            throws Exception {
        String verified = verify(context,username,pin);
        if (!verified.equals(sessionId))
            throw new SecurityException("Ovaj korisnički račun nije trenutno otvoren");
        // Remove the registry entry only after its database and recovery files.
        String dbName = Store.databaseName(verified);
        if (!context.deleteDatabase(dbName))
            throw new IllegalStateException("Nije moguće ukloniti pohranjene podatke");
        RecoveryVault.deleteProfile(context,verified);
        JSONObject registry=read(context);
        JSONArray accounts=registry.getJSONArray("accounts"),remaining=new JSONArray();
        for(int i=0;i<accounts.length();i++) {
            JSONObject entry=accounts.getJSONObject(i);
            if (!verified.equals(entry.getString("id")))remaining.put(entry);
        }
        registry.put("accounts",remaining);
        write(context,registry);
        lock();
    }
}
