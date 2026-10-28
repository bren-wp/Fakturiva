package com.brendigo.fakturiva;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import org.json.JSONObject;

/** Android framework-only instrumented regression tests on isolated storage. */
@SuppressWarnings("deprecation")
public final class InvoiceWorkflowTest extends AndroidTestCase {
    private static final class IsolatedContext extends ContextWrapper {
        final File dir;
        IsolatedContext(Context base) {
            super(base);
            dir=new File(base.getCacheDir(),"isolated-invoice-test-"+System.nanoTime());
            if(!dir.mkdirs())throw new IllegalStateException("Cannot create test directory");
        }
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){
            File f=new File(dir,"files");if(!f.exists()&&!f.mkdirs())throw new IllegalStateException("files");
            return f;
        }
        @Override public File getDatabasePath(String name){
            File f=new File(dir,"databases/"+name);
            if(!f.getParentFile().exists()&&!f.getParentFile().mkdirs())throw new IllegalStateException("database dir");
            return f;
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler errorHandler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory,errorHandler);
        }
        void clean(){
            if(dir.exists())erase(dir);
        }
        private void erase(File target){
            if(target.isDirectory()){
                File[] list=target.listFiles();
                if(list!=null)for(File sub:list)erase(sub);
            }
            if(!target.delete())android.util.Log.w("FakturivaQA","Test file not removed: "+target);
        }
    }

    public void testPersistHistoricalIssuerCustomerAndPayment()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());
        Store db=null,restored=null;
        try{
            db=new Store(context);
            db.setting("name","Izvorni izdavatelj d.o.o.");
            db.setting("oib","12345678903");
            db.setting("address","Izvorna ulica 15, Rijeka");
            db.setting("iban","HR1210010051863000160");
            long idClient=db.saveClientDetailed("Izvorni klijent","12345678903","Kupčeva ulica 12","a@example.invalid","+385100","HR12345678903");
            Store.Item item=new Store.Item();
            item.name="Savjetovanje";item.unitCents=80000;item.qtyMilli=1250;item.taxRate=25;
            long id=db.createDraft(Store.INVOICE,idClient,"2026-10-28","2026-11-15","2026-10-26","Usluga",Arrays.asList(item));
            assertEquals(100000L,db.document(id).netCents);
            assertEquals(25000L,db.document(id).taxCents);
            assertEquals("R-2026-0001",db.confirm(id));
            db.updateClient(idClient,"Izmijenjeni klijent","12345678903","Nova adresa","b@example.invalid","","");
            db.setting("name","Druga tvrtka d.o.o.");
            Store.Document old=db.document(id);
            assertEquals("Izvorni izdavatelj d.o.o.",old.issuerName);
            assertEquals("Izvorni klijent",old.clientName);
            assertEquals("2026-10-26",old.supplyDate);
            db.pay(id,50000,"2026-10-28");
            assertEquals(75000L,db.document(id).outstanding());
            JSONObject export=db.backup();
            IsolatedContext second=new IsolatedContext(getContext());
            try{
                restored=new Store(second);
                restored.restore(export);
                Store.Document reopened=restored.document(id);
                assertEquals("Izvorni klijent",reopened.clientName);
                assertEquals("R-2026-0001",reopened.number);
                assertEquals(75000L,reopened.outstanding());
                ByteArrayOutputStream pdf=new ByteArrayOutputStream();
                PdfMaker.write(restored,reopened,pdf);
                assertTrue("Generated PDF should contain data",pdf.size()>2000);
                assertEquals("%PDF",new String(pdf.toByteArray(),0,4,java.nio.charset.StandardCharsets.US_ASCII));
            }finally{if(restored!=null)restored.close();second.clean();}
        }finally{if(db!=null)db.close();context.clean();}
    }

    public void testRejectedInvalidTransactionKeepsDraft()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());
        Store db=null;
        try{
            db=new Store(context);
            long customer=db.saveClient("Klijent","","Rijeka","");
            Store.Item item=new Store.Item();
            item.name="Usluga";item.qtyMilli=1000;item.unitCents=10000;item.taxRate=25;
            long id=db.createDraft(Store.QUOTE,customer,"2026-10-28","2026-11-28","",Arrays.asList(item));
            try{
                db.updateDraft(id,customer,"2026-02-31","2026-03-05","Neispravno",Arrays.asList(item));
                fail("Expected invalid date rejection");
            }catch(IllegalArgumentException expected){
                assertEquals("2026-10-28",db.document(id).issueDate);
                assertEquals(12500L,db.document(id).totalCents);
            }
            assertEquals(1,db.documents(Store.QUOTE).size());
        }finally{if(db!=null)db.close();context.clean();}
    }
}
