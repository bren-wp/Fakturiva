package com.brendigo.fakturiva;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * Synthetic-only cross-language FAK1 contract test.
 * PHP generates encrypted bytes; this real Android crypto class decrypts and
 * re-encrypts them. No real account, password or plaintext is written to disk.
 */
public final class CrossRuntimeFak1Smoke {
    public static void main(String[] args) throws Exception {
        String fixturePassword=System.getenv("FAKTURIVA_FIXTURE_PASS");
        if(fixturePassword==null||fixturePassword.length()<12)
            throw new IllegalStateException("Missing synthetic fixture password");
        char[] pass=fixturePassword.toCharArray();
        byte[] plain=null, encrypted=null, incoming=null;
        try {
            String encoded=new String(System.in.readAllBytes(),StandardCharsets.UTF_8).trim();
            incoming=Base64.getDecoder().decode(encoded);
            plain=SecureBackup.decrypt(incoming,pass);
            String text=new String(plain,StandardCharsets.UTF_8);
            if(!text.contains("\"format\":\"fakturiva-backup-v1\"")||
               !text.contains("\"schema\":8")||
               !text.contains("\"R-2026-WEB-E2E\""))
                throw new AssertionError("PHP snapshot is not valid Android schema 8");
            encrypted=SecureBackup.encrypt(plain,pass);
            if(Arrays.equals(incoming,encrypted))
                throw new AssertionError("Re-encryption must use fresh salt and nonce");
            System.out.print(Base64.getEncoder().encodeToString(encrypted));
        }finally{
            Arrays.fill(pass,'\0');
            if(plain!=null)Arrays.fill(plain,(byte)0);
            if(encrypted!=null)Arrays.fill(encrypted,(byte)0);
            if(incoming!=null)Arrays.fill(incoming,(byte)0);
        }
    }
}
