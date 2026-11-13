package com.brendigo.fakturiva;

import org.junit.Test;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

/** Real Android JSON and crypto guard tests, without touching customer data. */
public final class SyncIntegrityTest {
    @Test public void checksCiphertextAndMonotonicAcknowledgement() throws Exception {
        byte[] encrypted="FAK1-isolated-unit-test-ciphertext".getBytes(
            StandardCharsets.UTF_8);
        String digest=SyncIntegrity.sha256(encrypted);
        assertEquals(64,digest.length());
        JSONObject ack=new JSONObject().put("revision",7).put("sha256",digest);
        assertEquals(7,SyncIntegrity.requireAcknowledgement(ack,6,encrypted));
        SyncIntegrity.verifyRemote(ack,encrypted);

        try {
            SyncIntegrity.requireAcknowledgement(ack,5,encrypted);
            fail("A skipped remote revision must not be accepted");
        } catch(SecurityException expected) { /* fail closed */ }
        try {
            SyncIntegrity.requireAcknowledgement(
                new JSONObject().put("revision",7).put("sha256","bad"),6,encrypted);
            fail("An unverified remote ACK must not mark local changes uploaded");
        } catch(SecurityException expected) { /* fail closed */ }
        try {
            SyncIntegrity.verifyRemote(ack,"FAK1-unrelated-copy".getBytes(
                StandardCharsets.UTF_8));
            fail("Encrypted download checksum mismatch must fail before restore");
        } catch(SecurityException expected) { /* fail closed */ }
    }
    @Test public void emptyOrNullRemoteIsNeverTrusted() throws Exception {
        try {
            SyncIntegrity.verifyRemote(new JSONObject(),new byte[0]);
            fail("Empty snapshot must be rejected");
        } catch(SecurityException expected) { /* safe */ }
        try {
            SyncIntegrity.requireAcknowledgement(null,1,new byte[]{1});
            fail("Missing acknowledgement must fail");
        } catch(SecurityException expected) { /* safe */ }
    }
}
