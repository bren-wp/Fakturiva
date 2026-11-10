package com.brendigo.fakturiva;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypted server mirror for one authenticated local profile.
 *
 * The remote revision is authoritative. SQLite is only an offline working copy.
 * A mutation is "saved" on the server ONLY after a matching authenticated
 * response and its SHA-256 have been verified. Conflicts are NEVER overwritten.
 * A fresh installation must reauthenticate with the user's server passphrase
 * and explicitly restore; an old device-only PIN cannot reconstruct a key.
 */
public final class CloudMirror {
    private static final String KEY_ALIAS="fakturiva-cloud-credentials-v1";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    // Coordinates pending-marker updates with successful server ACK commits.
    // Network and SQLite work are never performed under this lock.
    private static final Object STATE_LOCK=new Object();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private CloudMirror(){}

    public interface Callback{void completed(String error);}
    private static SharedPreferences state(Context ctx,String id){
        if(!LocalAccounts.validProfileId(id))
            throw new IllegalArgumentException("Invalid profile");
        return ctx.getApplicationContext().getSharedPreferences(
            "cloud_sync_"+id,Context.MODE_PRIVATE);
    }
    private static SecretKey key()throws Exception{
        KeyStore keyStore=KeyStore.getInstance("AndroidKeyStore");keyStore.load(null);
        KeyStore.Entry entry=keyStore.getEntry(KEY_ALIAS,null);
        if(entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator gen=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setRandomizedEncryptionRequired(true).build());
        return gen.generateKey();
    }
    private static String seal(char[] password)throws Exception{
        byte[] plain=new String(password).getBytes(StandardCharsets.UTF_8);
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key()); // Keystore generates IV
            byte[] nonce=cipher.getIV(),encrypted=cipher.doFinal(plain);
            if(nonce==null||nonce.length!=12)throw new SecurityException("Invalid IV");
            byte[] combined=new byte[nonce.length+encrypted.length];
            System.arraycopy(nonce,0,combined,0,nonce.length);
            System.arraycopy(encrypted,0,combined,nonce.length,encrypted.length);
            try{return Base64.encodeToString(combined,Base64.NO_WRAP);}
            finally{Arrays.fill(combined,(byte)0);Arrays.fill(encrypted,(byte)0);}
        }finally{Arrays.fill(plain,(byte)0);}
    }
    private static char[] unseal(String cipherText)throws Exception{
        byte[] combined=Base64.decode(cipherText,Base64.NO_WRAP);
        if(combined.length<29||combined.length>1000)throw new SecurityException(
            "Stored server credential is invalid");
        try{
            byte[] nonce=Arrays.copyOfRange(combined,0,12);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
            byte[] plain=cipher.doFinal(combined,12,combined.length-12);
            try{return new String(plain,StandardCharsets.UTF_8).toCharArray();}
            finally{Arrays.fill(plain,(byte)0);}
        }finally{Arrays.fill(combined,(byte)0);}
    }
    public static boolean enabled(Context ctx,String id){
        return id!=null&&LocalAccounts.validProfileId(id)&&
            state(ctx,id).getBoolean("enabled",false);
    }
    static int expectedRevision(Context ctx,String id){
        return enabled(ctx,id)?state(ctx,id).getInt("revision",-1):-1;
    }
    public static String status(Context ctx,String id){
        if(!enabled(ctx,id))
            return "Server nije povezan. Za obnovu nakon deinstalacije povežite račun.";
        SharedPreferences p=state(ctx,id);
        if(p.getBoolean("conflict",false))
            return "Sukob verzija: prijenos je zaustavljen do ručne provjere.";
        if(p.getBoolean("dirty",false))
            return "Promjene čekaju potvrdu pohrane na serveru.";
        long at=p.getLong("synced_at",0);
        if(at==0)return "Prijenos na server još nije potvrđen.";
        return "Sigurnosna kopija potvrđena na serveru ("+
            new java.text.SimpleDateFormat("dd.MM.yyyy. HH:mm",
                java.util.Locale.forLanguageTag("hr-HR"))
                .format(new java.util.Date(at))+").";
    }
    public static void suspend(Context ctx,String id){
        if(enabled(ctx,id))state(ctx,id).edit().putBoolean("paused",true).commit();
    }
    public static void resume(Context ctx,String id){
        if(enabled(ctx,id))state(ctx,id).edit().putBoolean("paused",false).commit();
    }
    public static void disconnect(Context ctx,String id){
        // Does not delete server data; users can restore it by logging in.
        state(ctx,id).edit().clear().commit();
    }
    /**
     * Refresh only the Keystore-sealed cloud credential after the same
     * account was positively authenticated by the canonical HTTPS server.
     * Never change revisions, dirty markers or stored business data here.
     */
    static void rebindVerifiedPassword(Context ctx,String id,String endpoint,
                                       String username,char[] password) throws Exception {
        SharedPreferences prefs=state(ctx,id);
        if(!prefs.getBoolean("enabled",false))return;
        String url=SelfHostedSyncClient.apiUrl(endpoint);
        if(!url.equals(prefs.getString("api","")) ||
           !username.trim().equalsIgnoreCase(prefs.getString("username","")))
            return; // Custom hosting is a different identity provider.
        if(password==null || password.length<12)throw new SecurityException("Lozinka nedostaje");
        String sealed=seal(password);
        if(!prefs.edit().putString("sealed_password",sealed).commit())
            throw new IllegalStateException("Nova vjerodajnica nije spremljena.");
    }
    /**
     * Called only after the user has explicitly uploaded a verified first
     * snapshot or restored the existing remote data. No silent first-write
     * overwrites, including when the server already has another device.
     */
    public static void enable(Context ctx,String id,String endpoint,
                              String username,char[] password,Callback callback){
        final Context app=ctx.getApplicationContext();
        final char[] copy=password.clone();
        WORK.execute(()->{
            String failure=null;
            try{
                if(copy.length<12||copy.length>256)
                    throw new IllegalArgumentException("Lozinka mora imati 12–256 znakova");
                String url=SelfHostedSyncClient.apiUrl(endpoint);
                JSONObject login=SelfHostedSyncClient.request(url,
                    SelfHostedSyncClient.command("login",username,new String(copy)),null);
                String token=login.getString("token");
                try{
                    JSONObject remote=SelfHostedSyncClient.request(url,
                        new JSONObject().put("action","load"),token);
                    String current=remote.optString("backup_b64","");
                    if(current.isEmpty())throw new IllegalStateException(
                        "Na serveru nema kopije. Najprije spremite postojeće podatke.");
                    byte[] sealed=Base64.decode(current,Base64.DEFAULT);
                    byte[] decrypted;
                    try{decrypted=SecureBackup.decrypt(sealed,copy);}
                    finally{Arrays.fill(sealed,(byte)0);}
                    Arrays.fill(decrypted,(byte)0);
                    // Successful decryption confirms the passphrase belongs
                    // to the backup; there is no client-side SHA guessing.
                    String encrypted=seal(copy);
                    SharedPreferences.Editor editor=state(app,id).edit();
                    editor.putBoolean("enabled",true).putString("api",url)
                        .putString("username",username.trim())
                        .putString("sealed_password",encrypted)
                        .putInt("revision",remote.getInt("revision"))
                        // No synchronization may claim the local SQLite snapshot
                        // is uploaded until its exact bytes are acknowledged.
                        .putBoolean("dirty",true).putBoolean("conflict",false)
                        .putString("pending_change",java.util.UUID.randomUUID().toString())
                        .putBoolean("paused",false)
                        .putLong("synced_at",System.currentTimeMillis());
                    if(!editor.commit())throw new IllegalStateException(
                        "Postavke poslužiteljske sigurnosne kopije nisu spremljene");
                }finally{
                    try{SelfHostedSyncClient.request(url,
                        new JSONObject().put("action","logout"),token);}
                    catch(Exception ignored){}
                }
            }catch(Exception e){failure=e.getMessage();}
            finally{Arrays.fill(copy,'\0');}
            final String result=failure;
            if(callback!=null)MAIN.post(()->callback.completed(result));
        });
    }
    /**
     * Bind a freshly verified server snapshot to a local offline profile.
     * Unlike enable(), this must NOT mark a clean, just-restored snapshot as
     * pending or overwrite it with a different local database.
     */
    static void connectAcknowledged(Context ctx, String id, String endpoint,
                                    String username, char[] password, int revision)
                                    throws Exception {
        if (!LocalAccounts.validProfileId(id) || revision < 1 ||
            password == null || password.length < 12)
            throw new IllegalArgumentException("Neispravna potvrda servera");
        String url = SelfHostedSyncClient.apiUrl(endpoint);
        String encrypted = seal(password);
        SharedPreferences.Editor editor = state(ctx, id).edit();
        editor.putBoolean("enabled", true)
            .putString("api", url)
            .putString("username", username.trim())
            .putString("sealed_password", encrypted)
            .putInt("revision", revision)
            .putBoolean("dirty", false)
            .putBoolean("conflict", false)
            .putBoolean("paused", false)
            .remove("last_error")
            .remove("pending_change")
            .putLong("synced_at", System.currentTimeMillis());
        if (!editor.commit())
            throw new IllegalStateException("Povezivanje s hostingom nije potvrđeno");
    }

    public interface PullCallback {
        /** UI-thread completion. updated=true only after the SQLite transaction
         *  AND revision marker have both been durably acknowledged. */
        void completed(String error, boolean updated);
    }

    /**
     * Explicit, conflict-aware Android pull of a newer web/other-device
     * encrypted snapshot. The user must confirm before calling this method.
     * A dirty offline profile is never overwritten. The local recovery vault
     * is taken by Store.restore BEFORE the replacement SQLite transaction.
     */
    public static void pullNewer(Context ctx,String id,PullCallback callback){
        final Context app=ctx.getApplicationContext();
        WORK.execute(()->{
            String error=null;
            boolean updated=false;
            char[] pass=null;
            Store store=null;
            String token=null,url=null;
            boolean originallyPaused=false,pausedForRestore=false;
            try{
                if(!enabled(app,id) || !id.equals(LocalAccounts.activeId()))
                    throw new SecurityException("Prijavite se u povezani račun.");
                SharedPreferences p=state(app,id);
                if(p.getBoolean("dirty",false))
                    throw new IllegalStateException(
                        "Lokalne promjene nisu prenesene. Prvo provjerite pohranu ili izvezite kopiju.");
                if(p.getInt("revision",-1)<1)
                    throw new SecurityException("Nije potvrđena prethodna server revizija.");
                final String marker=p.getString("pending_change","");
                url=SelfHostedSyncClient.apiUrl(p.getString("api",""));
                pass=unseal(p.getString("sealed_password",""));
                JSONObject login=SelfHostedSyncClient.request(url,
                    SelfHostedSyncClient.command("login",
                        p.getString("username",""),new String(pass)),null);
                token=login.getString("token");
                JSONObject remote=SelfHostedSyncClient.request(url,
                    new JSONObject().put("action","load"),token);
                int actual=remote.getInt("revision");
                int known=p.getInt("revision",-1);
                if(actual<known)
                    throw new SecurityException(
                        "Server ima stariju reviziju; lokalni podatci nisu promijenjeni.");
                if(actual>known){
                    String encoded=remote.getString("backup_b64");
                    if(encoded.isEmpty())
                        throw new SecurityException("Nedostaje šifrirana kopija na serveru.");
                    byte[] ciphertext=Base64.decode(encoded,Base64.DEFAULT);
                    JSONObject snapshot;
                    try{
                        if(!sha(ciphertext).equals(remote.getString("sha256")))
                            throw new SecurityException("Kontrolni zbroj servera nije valjan.");
                        byte[] plain=SecureBackup.decrypt(ciphertext,pass);
                        try{
                            snapshot=new JSONObject(new String(plain,
                                StandardCharsets.UTF_8));
                        }finally{Arrays.fill(plain,(byte)0);}
                    }finally{Arrays.fill(ciphertext,(byte)0);}
                    if(!"fakturiva-backup-v1".equals(snapshot.optString("format")))
                        throw new SecurityException("Neispravna udaljena kopija.");
                    // The calling Activity holds an uncancellable modal while
                    // this runs; additionally reject a pending marker changed
                    // in the meantime. Upload jobs serialize through WORK.
                    synchronized(STATE_LOCK){
                        if(p.getBoolean("dirty",false) ||
                           !marker.equals(p.getString("pending_change","")) ||
                           !id.equals(LocalAccounts.activeId()))
                            throw new IllegalStateException(
                                "Lokalni podatci promijenjeni su tijekom provjere.");
                        originallyPaused=p.getBoolean("paused",false);
                        if(!p.edit().putBoolean("paused",true).commit())
                            throw new IllegalStateException(
                                "Nije moguće zaustaviti prijenos tijekom obnove.");
                        pausedForRestore=true;
                    }
                    store=new Store(app,id);
                    // Store.restore is transactional, validates all financial
                    // totals, and aborts unless a local vault copy is saved.
                    store.restore(snapshot);
                    synchronized(STATE_LOCK){
                        if(!p.edit().putInt("revision",actual)
                            .putBoolean("dirty",false)
                            .putBoolean("conflict",false)
                            .putBoolean("paused",originallyPaused)
                            .remove("pending_change").remove("last_error")
                            .putLong("synced_at",System.currentTimeMillis())
                            .commit())
                            throw new IllegalStateException(
                                "Nova revizija nije potvrđena na uređaju.");
                    }
                    pausedForRestore=false;
                    updated=true;
                }
            }catch(Exception ex){
                error=ex.getMessage()==null?"Obnova sa servera nije uspjela":
                    ex.getMessage();
            }finally{
                if(store!=null)store.close();
                if(pausedForRestore){
                    try{state(app,id).edit()
                        .putBoolean("paused",originallyPaused).commit();}
                    catch(Exception ignored){}
                }
                if(url!=null && token!=null){
                    try{SelfHostedSyncClient.request(url,
                        new JSONObject().put("action","logout"),token);}
                    catch(Exception ignored){}
                }
                if(pass!=null)Arrays.fill(pass,'\0');
            }
            final String result=error;
            final boolean pulled=updated;
            if(callback!=null)MAIN.post(()->callback.completed(result,pulled));
        });
    }

    public static void changed(Context ctx,String id){
        if(!enabled(ctx,id))return;
        SharedPreferences pref=state(ctx,id);
        // durable pending flag written BEFORE scheduling any network work
        synchronized(STATE_LOCK){
            if(!pref.edit().putBoolean("dirty",true)
                .putString("pending_change",java.util.UUID.randomUUID().toString())
                .commit())
                android.util.Log.e("FakturivaCloud","Could not persist unsynced flag");
        }
        if(!pref.getBoolean("paused",false))runPending(ctx,id);
    }
    public static void runPending(Context ctx,String id){
        if(!enabled(ctx,id)||state(ctx,id).getBoolean("paused",false))return;
        final Context app=ctx.getApplicationContext();
        WORK.execute(()->{
            if(!enabled(app,id))return;
            SharedPreferences p=state(app,id);
            if(!p.getBoolean("dirty",false)||p.getBoolean("conflict",false))return;
            if(!id.equals(LocalAccounts.activeId()))return;
            // Capture the revision marker BEFORE reading SQLite and before any
            // network request. Every newer mutation changes it.
            final String capturedChange;
            synchronized(STATE_LOCK){
                capturedChange=p.getString("pending_change","");
            }
            char[] pass=null;
            Store store=null;
            try{
                String url=SelfHostedSyncClient.apiUrl(p.getString("api",""));
                String username=p.getString("username","");
                pass=unseal(p.getString("sealed_password",""));
                store=new Store(app,id);
                byte[] plaintext=store.backup().toString().getBytes(StandardCharsets.UTF_8);
                byte[] cipher;
                try{cipher=SecureBackup.encrypt(plaintext,pass);}
                finally{Arrays.fill(plaintext,(byte)0);}
                try{
                    String localSha=sha(cipher);
                    JSONObject login=SelfHostedSyncClient.request(url,
                        SelfHostedSyncClient.command("login",username,new String(pass)),null);
                    String token=login.getString("token");
                    try{
                        JSONObject remote=SelfHostedSyncClient.request(url,
                            new JSONObject().put("action","load"),token);
                        int savedRevision=p.getInt("revision",-1);
                        int serverRevision=remote.getInt("revision");
                        if(savedRevision!=serverRevision){
                            p.edit().putBoolean("conflict",true).commit();
                            return; // fail closed: NEVER auto-clobber a newer server snapshot
                        }
                        JSONObject saved=SelfHostedSyncClient.request(url,
                            new JSONObject().put("action","save")
                                .put("expected_revision",savedRevision)
                                .put("backup_b64",Base64.encodeToString(cipher,
                                    Base64.NO_WRAP)),token);
                        if(!localSha.equals(saved.getString("sha256")))
                            throw new SecurityException("Server checksum mismatch");
                        int rev=saved.getInt("revision");
                        // Exactly this snapshot was acknowledged.
                        // A mutation committed DURING transmission should keep
                        // dirty=true; do not mark its data as uploaded.
                        boolean moreChanges;
                        synchronized(STATE_LOCK){
                            moreChanges=!capturedChange.equals(
                                p.getString("pending_change",""));
                            if(!p.edit().putInt("revision",rev)
                                .putLong("synced_at",System.currentTimeMillis())
                                .putBoolean("dirty",moreChanges)
                                .remove("last_error").commit())
                                throw new IllegalStateException(
                                    "Potvrda sinkronizacije nije spremljena");
                        }
                        if(moreChanges)runPending(app,id);
                    }finally{
                        try{SelfHostedSyncClient.request(url,
                            new JSONObject().put("action","logout"),token);}
                        catch(Exception ignored){}
                    }
                }finally{Arrays.fill(cipher,(byte)0);}
            }catch(Exception e){
                p.edit().putString("last_error",
                    e.getMessage()==null?"Prijenos nije uspio":e.getMessage()).commit();
            }finally{
                if(store!=null)store.close();
                if(pass!=null)Arrays.fill(pass,'\0');
            }
        });
    }
    private static String sha(byte[] value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result=new StringBuilder(64);
        for(byte b:digest)result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
}
