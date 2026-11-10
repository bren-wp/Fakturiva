package com.brendigo.fakturiva;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * User-supplied HTTPS endpoint. All financial backup bytes are encrypted with
 * SecureBackup on the Android device before being sent.
 *
 * No session/password is persisted. Every operation explicitly logs in,
 * performs exactly one user-requested action, then logs out.
 */
public final class SelfHostedSyncClient {
    public enum Action { REGISTER, UPLOAD, DOWNLOAD, DELETE }
    public interface Callback {
        /** Called on UI thread; null error indicates success. */
        void finished(String error, String result);
    }
    private SelfHostedSyncClient(){}

    public static String apiUrl(String supplied) throws Exception {
        if(supplied==null||supplied.length()>512)
            throw new IllegalArgumentException("Unesite adresu HTTPS hostinga");
        URI uri=new URI(supplied.trim());
        if(!"https".equalsIgnoreCase(uri.getScheme())||
            uri.getHost()==null||uri.getHost().isEmpty()||
            uri.getUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null)
            throw new IllegalArgumentException("Potreban je HTTPS URL bez korisničkih podataka");
        String path=uri.getRawPath();
        if(path==null||path.isEmpty())path="/";
        if(path.endsWith("/"))path+="api.php";
        else if(!path.endsWith("/api.php"))path+="/api.php";
        URI target=new URI("https",uri.getRawAuthority(),path,null,null);
        return target.toASCIIString();
    }

    static JSONObject request(String url,JSONObject data,String token) throws Exception {
        URL target=new URL(url);
        HttpsURLConnection connection=(HttpsURLConnection)target.openConnection();
        connection.setInstanceFollowRedirects(false); // Never forward a passphrase.
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(35000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("Cache-Control","no-store");
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        connection.setDoOutput(true);
        byte[] body=data.toString().getBytes(StandardCharsets.UTF_8);
        if(body.length>36000000)
            throw new IllegalArgumentException("Preslika je prevelika za odabrani hosting");
        connection.setFixedLengthStreamingMode(body.length);
        try{
            try(OutputStream stream=connection.getOutputStream()){
                stream.write(body);
            }finally{Arrays.fill(body,(byte)0);}
            int status=connection.getResponseCode();
            if(status>=300&&status<400)
                throw new IllegalStateException("Preusmjeravanje hostinga nije dopušteno");
            InputStream input=status>=400?connection.getErrorStream():
                connection.getInputStream();
            if(input==null)
                throw new IllegalStateException("Poslužitelj nije vratio odgovor");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream stream=input){
                byte[] block=new byte[8192];int n;
                while((n=stream.read(block))!=-1){
                    if(buffer.size()+n>36000000)
                        throw new IllegalStateException("Prevelik odgovor poslužitelja");
                    buffer.write(block,0,n);
                }
            }
            JSONObject response=new JSONObject(buffer.toString("UTF-8"));
            if(status>=400||!response.optBoolean("ok",false))
                throw new IllegalStateException("Hosting: "+
                    response.optString("error","Zahtjev nije uspio"));
            return response.getJSONObject("data");
        }finally{connection.disconnect();}
    }

    static JSONObject command(String action,String username,String password){
        try {
            return new JSONObject().put("action",action).put("username",username)
                .put("passphrase",password);
        }catch(Exception impossible){throw new IllegalStateException(impossible);}
    }

    private static void sameProfile(String expected,Store source){
        if(expected==null||source==null||
            !expected.equals(LocalAccounts.activeId())||
            !expected.equals(source.profileId()))
            throw new SecurityException("Lokalni račun se promijenio; prijenos je otkazan");
    }

    public static void execute(Activity activity,Action action,String endpoint,
        String username,char[] passphrase,Store profileStore,Callback callback) {
        if(activity==null||callback==null||action==null)
            throw new IllegalArgumentException("Nedostaju podaci za hosting");
        final char[] secret=passphrase==null?new char[0]:passphrase.clone();
        final String expectedProfile=LocalAccounts.activeId();
        new Thread(()->{
            String token=null;
            String url=null;
            String error=null;
            String outcome=null;
            try{
                sameProfile(expectedProfile,profileStore);
                url=apiUrl(endpoint);
                if(secret.length<12)
                    throw new IllegalArgumentException(
                        "Poslužiteljska lozinka mora imati najmanje 12 znakova");
                String password=new String(secret);
                if(action==Action.REGISTER){
                    JSONObject registered=request(url,command("register",username,password),null);
                    token=registered.getString("token");
                    sameProfile(expectedProfile,profileStore);
                    byte[] raw=profileStore.backup().toString()
                        .getBytes(StandardCharsets.UTF_8);
                    byte[] encrypted;
                    try{encrypted=SecureBackup.encrypt(raw,secret);}
                    finally{Arrays.fill(raw,(byte)0);}
                    try{
                        JSONObject save=new JSONObject().put("action","save")
                            .put("expected_revision",0)
                            .put("backup_b64",android.util.Base64.encodeToString(
                                encrypted,android.util.Base64.NO_WRAP));
                        JSONObject ack=request(url,save,token);
                        outcome="Račun registriran i sigurnosna kopija potvrđena "+
                            "na serveru, revizija "+ack.getInt("revision")+".";
                    }finally{Arrays.fill(encrypted,(byte)0);}
                }else{
                    JSONObject login=request(url,command("login",username,password),null);
                    token=login.getString("token");
                    sameProfile(expectedProfile,profileStore);
                    if(action==Action.UPLOAD){
                        JSONObject existing=request(url,new JSONObject().put("action","load"),token);
                        int revision=existing.getInt("revision");
                        // A new local install must restore an existing server
                        // account before any upload. Explicitly associated
                        // profiles must match the latest acknowledged revision.
                        int acknowledged=CloudMirror.expectedRevision(activity,
                            expectedProfile);
                        if(revision!=0 && acknowledged!=revision)
                            throw new IllegalStateException(
                                "Na serveru već postoje podatci. Najprije odaberite "+
                                "'Preuzmi i vrati' te pregledajte postojeće račune; "+
                                "pohrana neće prebrisati udaljene podatke.");
                        // A server's revision is never guessed. Conditional write
                        // prevents a concurrent device silently overwriting data.
                        byte[] raw=profileStore.backup().toString()
                            .getBytes(StandardCharsets.UTF_8);
                        byte[] encrypted;
                        try{encrypted=SecureBackup.encrypt(raw,secret);}
                        finally{Arrays.fill(raw,(byte)0);}
                        try{
                            JSONObject save=new JSONObject()
                                .put("action","save")
                                .put("expected_revision",revision)
                                .put("backup_b64",android.util.Base64.encodeToString(
                                    encrypted,android.util.Base64.NO_WRAP));
                            JSONObject written=request(url,save,token);
                            outcome="Šifrirana preslika poslana. Verzija: "+
                                written.getInt("revision")+".";
                        }finally{Arrays.fill(encrypted,(byte)0);}
                    }else if(action==Action.DOWNLOAD){
                        JSONObject loaded=request(url,new JSONObject().put("action","load"),token);
                        String encoded=loaded.optString("backup_b64","");
                        if(encoded.isEmpty())
                            throw new IllegalStateException(
                                "Na hostingu još nema spremljene preslike");
                        byte[] sealed=android.util.Base64.decode(encoded,
                            android.util.Base64.DEFAULT);
                        byte[] raw;
                        try{raw=SecureBackup.decrypt(sealed,secret);}
                        finally{Arrays.fill(sealed,(byte)0);}
                        try{
                            JSONObject snapshot=new JSONObject(
                                new String(raw,StandardCharsets.UTF_8));
                            // SQLite performs its own verified transaction and
                            // creates a local encrypted recovery snapshot first.
                            sameProfile(expectedProfile,profileStore);
                            profileStore.restore(snapshot);
                        }finally{Arrays.fill(raw,(byte)0);}
                        outcome="Šifrirana kopija je provjerena i vraćena u ovaj profil.";
                    }else if(action==Action.DELETE){
                        JSONObject response=new JSONObject().put("action","delete")
                            .put("passphrase",password);
                        request(url,response,token);
                        token=null;
                        outcome="Poslužiteljski račun i njegovi podaci su obrisani. "+
                            "Lokalni račun je zadržan.";
                    }
                }
            }catch(Exception failure){
                error=failure.getMessage();
                if(error==null||error.isEmpty())error="Zahtjev prema hostingu nije uspio";
            }finally{
                if(url!=null&&token!=null){
                    try{request(url,new JSONObject().put("action","logout"),token);}
                    catch(Exception ignored){/* remote token expires independently */ }
                }
                Arrays.fill(secret,'\0');
            }
            final String message=error;
            final String success=outcome;
            new Handler(Looper.getMainLooper()).post(()->{
                if(!activity.isFinishing()&&!activity.isDestroyed())
                    callback.finished(message,success);
            });
        },"FakturivaSelfHostedSync").start();
    }
}
