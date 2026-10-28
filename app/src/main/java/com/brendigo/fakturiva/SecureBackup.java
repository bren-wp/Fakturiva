package com.brendigo.fakturiva;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Portable password-encrypted backup: FAK1 + 16B salt + 12B nonce + AES-256-GCM ciphertext. */
public final class SecureBackup {
    private static final byte[] HEADER={'F','A','K','1'};
    private static final int ITERATIONS=210000;
    private SecureBackup(){}
    private static SecretKey key(char[] password,byte[] salt)throws Exception{
        if(password.length<10)throw new IllegalArgumentException("Lozinka mora imati barem 10 znakova");
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try{
            byte[] raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
            try{return new SecretKeySpec(raw,"AES");}finally{Arrays.fill(raw,(byte)0);}
        }finally{spec.clearPassword();}
    }
    public static byte[] encrypt(byte[] plaintext,char[] password)throws Exception{
        byte[] salt=new byte[16], nonce=new byte[12]; SecureRandom rnd=new SecureRandom();rnd.nextBytes(salt);rnd.nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key(password,salt),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(HEADER);
        byte[] ciphertext=cipher.doFinal(plaintext);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(HEADER);out.write(salt);out.write(nonce);out.write(ciphertext);
        return out.toByteArray();
    }
    public static byte[] decrypt(byte[] data,char[] password)throws Exception{
        if(data.length<48||data.length>25000000)throw new IllegalArgumentException("Neispravna ili prevelika sigurnosna kopija");
        for(int i=0;i<HEADER.length;i++)if(data[i]!=HEADER[i])throw new IllegalArgumentException("Neispravan format sigurnosne kopije");
        byte[] salt=Arrays.copyOfRange(data,4,20),nonce=Arrays.copyOfRange(data,20,32);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(password,salt),new GCMParameterSpec(128,nonce));
        c.updateAAD(HEADER);
        return c.doFinal(data,32,data.length-32);
    }
}
