package com.brendigo.fakturiva;

import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Version and ciphertext integrity checks shared by manual and automatic
 * synchronization. A server ACK is never proof until both match the upload.
 * Does not decrypt, merge, log or mutate any user records.
 */
final class SyncIntegrity {
    private SyncIntegrity(){}

    static String sha256(byte[] data)throws Exception {
        if(data==null)throw new IllegalArgumentException("Ciphertext is missing");
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder result=new StringBuilder(64);
        for(byte value:digest)
            result.append(String.format(Locale.ROOT,"%02x",value&255));
        return result.toString();
    }

    static void verifyRemote(JSONObject remote,byte[] ciphertext)throws Exception {
        if(remote==null || ciphertext==null || ciphertext.length==0)
            throw new SecurityException("Udaljena šifrirana preslika nije dostupna");
        String expected=sha256(ciphertext);
        String actual=remote.optString("sha256","");
        if(!expected.equals(actual))
            throw new SecurityException("Kontrolni zbroj udaljene preslike nije valjan");
    }

    static int requireAcknowledgement(JSONObject saved,int previousRevision,
                                      byte[] uploaded)throws Exception {
        if(saved==null || previousRevision<0)
            throw new SecurityException("Nedostaje valjana potvrda revizije");
        int revision=saved.getInt("revision");
        if(revision!=Math.addExact(previousRevision,1))
            throw new SecurityException("Poslužitelj nije potvrdio očekivanu reviziju");
        verifyRemote(saved,uploaded);
        return revision;
    }
}
