package com.brendigo.fakturiva;

import org.junit.Test;
import static org.junit.Assert.*;

import android.content.Context;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.UUID;

/** Real Android Keystore + SQLite account-isolation smoke tests. */
public final class LocalAccountsTest extends FakturivaTestCase {
    @Test public void accountsMustNeverShareSqliteOrRecoveryDrafts() throws Exception {
        Context ctx=getContext();
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,10);
        String first="qa_"+suffix+"_a@example.test",second="qa_"+suffix+"_b@example.test";
        char[] pinA="Brendigo-local-password-A-2026".toCharArray(),pinB="Brendigo-local-password-B-2026".toCharArray();
        String idA=LocalAccounts.register(ctx,first,pinA);
        String idB=LocalAccounts.register(ctx,second,pinB);
        assertNotEquals(idA,idB);
        assertNotEquals(Store.databaseName(idA),Store.databaseName(idB));
        try {
            try {
                LocalAccounts.verify(ctx,first,"Wrong-password-2026".toCharArray());
                fail("Wrong password must never authorize a profile");
            }catch(SecurityException expected){/* no access */}
            LocalAccounts.unlock(ctx,LocalAccounts.verify(ctx,first,pinA));
            Store a=new Store(ctx,idA);
            try {
                a.saveClient("Korisnik A","12345678903",
                    "Test adresa A","a@example.invalid");
                assertEquals(1,a.count("clients"));
                RecoveryVault.saveWorkingDraft(ctx,
                    new JSONObject().put("version",1).put("onlyA",true));
                assertTrue(RecoveryVault.hasWorkingDraft(ctx));
            } finally {a.close();}
            LocalAccounts.lock();
            LocalAccounts.unlock(ctx,LocalAccounts.verify(ctx,second,pinB));
            Store b=new Store(ctx,idB);
            try {
                assertEquals(0,b.count("clients"));
                assertFalse("User B must not see user A's draft",
                    RecoveryVault.hasWorkingDraft(ctx));
                b.saveClient("Korisnik B","12345678903",
                    "Test adresa B","b@example.invalid");
                assertEquals(1,b.count("clients"));
            }finally{b.close();}
            LocalAccounts.lock();
            LocalAccounts.unlock(ctx,LocalAccounts.verify(ctx,first,pinA));
            Store restoredA=new Store(ctx,idA);
            try{
                assertEquals("Korisnik A",restoredA.clients().get(0).name);
                assertTrue(RecoveryVault.hasWorkingDraft(ctx));
            }finally{restoredA.close();}
            char[] rotated="Brendigo-new-passphrase-A-2026".toCharArray();
            String migrated=LocalAccounts.rekeyAfterVerifiedServerLogin(ctx,first,rotated);
            assertEquals("Credential migration must preserve SQLite identity",idA,migrated);
            try {
                LocalAccounts.verify(ctx,first,pinA);
                fail("Old local password was accepted after rekey.");
            }catch(SecurityException expected){/* no access */}
            LocalAccounts.delete(ctx,first,rotated);
            Arrays.fill(rotated,'\0');
            assertFalse(ctx.getDatabasePath(Store.databaseName(idA)).exists());
            LocalAccounts.unlock(ctx,LocalAccounts.verify(ctx,second,pinB));
            Store stillB=new Store(ctx,idB);
            try{assertEquals("Korisnik B",stillB.clients().get(0).name);}
            finally{stillB.close();}
            LocalAccounts.delete(ctx,second,pinB);
        } finally {
            Arrays.fill(pinA,'\0');
            Arrays.fill(pinB,'\0');
            LocalAccounts.lock();
        }
    }
}
