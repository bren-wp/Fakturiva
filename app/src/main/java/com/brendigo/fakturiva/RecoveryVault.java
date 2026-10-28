package com.brendigo.fakturiva;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Extra device-local, encrypted recovery snapshots. Does NOT protect against
 * loss/uninstall of the device; portable password backups must be exported separately.
 * No raw data is stored in cache or external storage.
 */
public final class RecoveryVault {
    private static final String ALIAS="fakturiva-local-recovery-v1";
    private static final byte[] MAGIC={'F','R','V','2'};
    private static final int MAX_BYTES=25_000_000;
    private static final int COPIES=3;
    private static final String PREFIX="recovery-";
    private RecoveryVault(){}

    private static File directory(Context ctx){
        File dir=new File(ctx.getFilesDir(),"recovery");
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Nije moguće stvoriti mapu za oporavak");
        return dir;
    }
    private static File file(File dir,int index){return new File(dir,PREFIX+index+".fv");}
    private static SecretKey secretKey() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        KeyStore.Entry entry=store.getEntry(ALIAS,null);
        if(entry instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(
            ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build();
        generator.init(spec);
        return generator.generateKey();
    }
    private static byte[] gzip(byte[] plain) throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(GZIPOutputStream g=new GZIPOutputStream(out)){g.write(plain);}
        return out.toByteArray();
    }
    private static byte[] gunzip(byte[] compressed) throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(GZIPInputStream in=new GZIPInputStream(new ByteArrayInputStream(compressed))){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(n==0)continue;
                if(out.size()+n>MAX_BYTES)throw new IllegalArgumentException("Prevelika kopija");
                out.write(buffer,0,n);
            }
        }
        return out.toByteArray();
    }
    private static byte[] seal(byte[] compressed) throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,secretKey());
        byte[] nonce=cipher.getIV();
        cipher.updateAAD(MAGIC);
        byte[] ciphertext=cipher.doFinal(compressed);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(MAGIC);out.write(nonce);out.write(ciphertext);
        return out.toByteArray();
    }
    private static byte[] open(byte[] sealed) throws Exception{
        if(sealed.length<4+12+16||sealed.length>MAX_BYTES)throw new IllegalArgumentException("Neispravna kopija");
        for(int i=0;i<MAGIC.length;i++)if(sealed[i]!=MAGIC[i])throw new IllegalArgumentException("Neispravan format");
        byte[] nonce=Arrays.copyOfRange(sealed,4,16);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,secretKey(),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(MAGIC);
        return cipher.doFinal(sealed,16,sealed.length-16);
    }
    /** Copy-on-write: a power loss never removes the previous generation. */
    private static void copyAtomically(File source,File destination) throws Exception{
        if(!source.isFile())return;
        if(source.length()>MAX_BYTES)throw new IllegalArgumentException("Prevelika lokalna kopija");
        AtomicFile atomic=new AtomicFile(destination);
        FileOutputStream out=null;
        try(FileInputStream in=new FileInputStream(source)){
            out=atomic.startWrite();
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1)out.write(block,0,n);
            atomic.finishWrite(out);out=null;
        }catch(Exception e){
            if(out!=null)atomic.failWrite(out);
            throw e;
        }
    }
    /** Always called after the SQLite commit, never inside a transaction. */
    public static synchronized void snapshot(Context ctx,Store store) throws Exception{
        byte[] plain=store.backup().toString().getBytes(StandardCharsets.UTF_8);
        if(plain.length>MAX_BYTES)throw new IllegalStateException("Baza je prevelika za lokalnu kopiju");
        byte[] compressed;
        try{compressed=gzip(plain);}finally{Arrays.fill(plain,(byte)0);}
        byte[] sealed;
        try{sealed=seal(compressed);}finally{Arrays.fill(compressed,(byte)0);}
        File dir=directory(ctx),tmp=new File(dir,"recovery-tmp.fv");
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(sealed);out.flush();out.getFD().sync();
        }finally{Arrays.fill(sealed,(byte)0);}
        // Preserve recovery-0 until the new snapshot is DURABLY committed.
        // Older generations are atomically copied, never destructively renamed.
        for(int i=COPIES-1;i>0;i--)copyAtomically(file(dir,i-1),file(dir,i));
        copyAtomically(tmp,file(dir,0));
        if(!tmp.delete())Log.w("FakturivaRecovery","Temporary recovery copy will be cleaned later");
    }
    public static synchronized int count(Context ctx){
        File dir=directory(ctx);int n=0;
        for(int i=0;i<COPIES;i++){
            File current=file(dir,i);
            if(current.isFile()||new File(current.getPath()+".bak").isFile())n++;
        }
        return n;
    }
    public static synchronized JSONObject read(Context ctx,int index) throws Exception{
        if(index<0||index>=COPIES)throw new IllegalArgumentException("Nepoznata kopija");
        File file=file(directory(ctx),index);
        AtomicFile atomic=new AtomicFile(file);
        // AtomicFile may have to recover a .bak after power loss; never reject it
        // just because the primary filename temporarily does not exist.
        if(!file.isFile()&&!new File(file.getPath()+".bak").isFile())
            throw new IllegalArgumentException("Kopija ne postoji");
        byte[] sealed;
        try(FileInputStream in=atomic.openRead()){
            ByteArrayOutputStream data=new ByteArrayOutputStream();
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1){
                if(data.size()+n>MAX_BYTES)throw new IllegalArgumentException("Prevelika kopija");
                data.write(block,0,n);
            }
            sealed=data.toByteArray();
        }
        byte[] compressed=open(sealed),plain;
        Arrays.fill(sealed,(byte)0);
        try{plain=gunzip(compressed);}finally{Arrays.fill(compressed,(byte)0);}
        try{return new JSONObject(new String(plain,StandardCharsets.UTF_8));}
        finally{Arrays.fill(plain,(byte)0);}
    }
    private static File workingFile(Context ctx){
        return new File(directory(ctx),"working-draft.fv");
    }
    public static synchronized boolean hasWorkingDraft(Context ctx){
        File file=workingFile(ctx);
        return file.isFile()||new File(file.getPath()+".bak").isFile();
    }
    /** Crash-safe, device-key encrypted draft in progress (not a numbered invoice). */
    public static synchronized void saveWorkingDraft(Context ctx,JSONObject data)throws Exception{
        byte[] raw=data.toString().getBytes(StandardCharsets.UTF_8);
        if(raw.length>1024*1024)throw new IllegalArgumentException("Prevelik radni nacrt");
        byte[] encrypted;
        try{encrypted=seal(raw);}finally{Arrays.fill(raw,(byte)0);}
        AtomicFile atomic=new AtomicFile(workingFile(ctx));FileOutputStream out=null;
        try{
            out=atomic.startWrite();out.write(encrypted);
            atomic.finishWrite(out);out=null;
        }catch(Exception e){
            if(out!=null)atomic.failWrite(out);
            throw e;
        }finally{Arrays.fill(encrypted,(byte)0);}
    }
    public static synchronized JSONObject readWorkingDraft(Context ctx)throws Exception{
        AtomicFile atomic=new AtomicFile(workingFile(ctx));
        if(!hasWorkingDraft(ctx))return null;
        byte[] encrypted;
        try(FileInputStream in=atomic.openRead()){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))!=-1){
                if(bytes.size()+n>1024*1024+128)throw new IllegalArgumentException("Prevelika datoteka nacrta");
                bytes.write(buffer,0,n);
            }
            encrypted=bytes.toByteArray();
        }
        byte[] raw;
        try{raw=open(encrypted);}finally{Arrays.fill(encrypted,(byte)0);}
        try{return new JSONObject(new String(raw,StandardCharsets.UTF_8));}
        finally{Arrays.fill(raw,(byte)0);}
    }
    public static synchronized void clearWorkingDraft(Context ctx){
        new AtomicFile(workingFile(ctx)).delete();
    }

    public static synchronized String summary(Context ctx){
        File f=file(directory(ctx),0);
        if(!f.isFile()){
            File previous=new File(f.getPath()+".bak");
            if(!previous.isFile())return "Lokalna kopija još nije stvorena";
            f=previous;
        }
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("dd.MM.yyyy. HH:mm",java.util.Locale.forLanguageTag("hr-HR"));
        return "Posljednja lokalna kopija: "+fmt.format(new java.util.Date(f.lastModified()));
    }
}
