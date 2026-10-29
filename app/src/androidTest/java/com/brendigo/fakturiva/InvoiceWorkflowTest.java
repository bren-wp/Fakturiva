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
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(),factory,errorHandler);
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
    public void testInvalidBackupRollsBackWithoutLosingInvoices()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());Store db=null;
        try{
            db=new Store(context);
            db.setting("name","Testni izdavatelj");
            long customer=db.saveClient("Kupac","","Rijeka","");
            Store.Item item=new Store.Item();
            item.name="Izrada dokumentacije";item.qtyMilli=1000;item.unitCents=10000;item.taxRate=25;
            long id=db.createDraft(Store.QUOTE,customer,"2026-10-28","2026-11-28","",Arrays.asList(item));
            assertEquals("P-2026-0001",db.confirm(id));
            JSONObject corrupted=new JSONObject(db.backup().toString());
            corrupted.getJSONArray("documents").getJSONObject(0).put("total_cents",999999L);
            try{
                db.restore(corrupted);
                fail("Corrupt invoice totals must not replace existing records");
            }catch(IllegalArgumentException expected){
                assertEquals(12500L,db.document(id).totalCents);
                assertEquals("P-2026-0001",db.document(id).number);
                assertEquals(1,db.documents(Store.QUOTE).size());
            }
            // A later valid restoration still remains possible.
            JSONObject valid=db.backup();
            db.restore(valid);
            assertEquals(12500L,db.document(id).totalCents);
        }finally{if(db!=null)db.close();context.clean();}
    }

    public void testInterruptedAtomicRecoveryAndDatabaseReopen()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());Store db=null;
        try{
            db=new Store(context);
            db.setting("name","Izdavatelj");
            long customer=db.saveClient("Kupac","","Rijeka","");
            Store.Item item=new Store.Item();item.name="Usluga";item.unitCents=4200;item.qtyMilli=1000;item.taxRate=25;
            long id=db.createDraft(Store.QUOTE,customer,"2026-10-28","2026-11-28","",Arrays.asList(item));
            assertEquals("P-2026-0001",db.confirm(id));
            db.close();db=null;
            db=new Store(context);
            assertEquals("P-2026-0001",db.document(id).number);
            File primary=new File(context.getFilesDir(),"recovery/recovery-0.fv");
            File fallback=new File(primary.getPath()+".bak");
            if(fallback.exists()&&!fallback.delete())fail("Cannot reset test backup");
            assertTrue("Local recovery snapshot present",primary.isFile());
            assertTrue("Backup renamed for interrupted-write test",primary.renameTo(fallback));
            JSONObject recovered=RecoveryVault.read(context,0);
            assertEquals(1,recovered.getJSONArray("documents").length());
            assertTrue("Backup must be restored to primary name",primary.isFile());
        }finally{if(db!=null)db.close();context.clean();}
    }

    public void testNumberingOverpaymentAndDuplicateConfirmation()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());Store db=null;
        try{
            db=new Store(context);
            db.setting("name","Izdavatelj");db.setting("oib","12345678903");
            db.setting("address","Rijeka");
            long client=db.saveClient("Kupac","","Rijeka","");
            Store.Item item=new Store.Item();item.name="Usluga";item.qtyMilli=1000;item.unitCents=10000;item.taxRate=25;
            long first=db.createDraft(Store.INVOICE,client,"2026-10-28","2026-11-28","",Arrays.asList(item));
            assertEquals("R-2026-0001",db.confirm(first));
            try{db.confirm(first);fail("Double confirmation");}catch(IllegalStateException expected){}
            long second=db.createDraft(Store.INVOICE,client,"2026-10-28","2026-11-28","",Arrays.asList(item));
            assertEquals("R-2026-0002",db.confirm(second));
            db.pay(first,5000,"2026-10-29");
            try{db.pay(first,10000,"2026-10-30");fail("Overpayment");}
            catch(IllegalArgumentException expected){}
            assertEquals(7500L,db.document(first).outstanding());
            assertEquals(1,db.payments().size());
            db.pay(first,7500,"2026-10-31");
            assertEquals(0L,db.document(first).outstanding());
            assertEquals(Store.PAID,db.document(first).status);
        }finally{if(db!=null)db.close();context.clean();}
    }

    public void testLongPdfHasMultiplePages()throws Exception{
        IsolatedContext context=new IsolatedContext(getContext());Store db=null;
        try{
            db=new Store(context);
            db.setting("name","Izdavatelj");
            long client=db.saveClient("Klijent s vrlo vrlo dugim poslovnim nazivom","","Primjer adrese i grada","");
            java.util.ArrayList<Store.Item> items=new java.util.ArrayList<>();
            for(int i=0;i<65;i++){
                Store.Item item=new Store.Item();
                item.name="Detaljan opis poslovne usluge broj "+i+" i dodatnih parametara dokumenta";
                item.qtyMilli=1000;item.unitCents=100;item.taxRate=25;items.add(item);
            }
            long id=db.createDraft(Store.QUOTE,client,"2026-10-28","2026-11-28","",items);
            File file=new File(context.dir,"multi-page.pdf");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){
                PdfMaker.write(db,db.document(id),out);
            }
            try(android.os.ParcelFileDescriptor parcel=android.os.ParcelFileDescriptor.open(file,
                android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(parcel)){
                assertTrue("Line items must continue to another A4 page",renderer.getPageCount()>=2);
            }
        }finally{if(db!=null)db.close();context.clean();}
    }
    public void testCatalogEditingDoesNotRewriteIssuedDocument() throws Exception {
        IsolatedContext context=new IsolatedContext(getContext());
        Store db=null;
        try {
            db=new Store(context);
            db.setting("name","Testni izdavatelj");
            db.setting("oib","12345678903");
            db.setting("address","Rijeka");
            long customer=db.saveClient("Klijent","","Rijeka","");
            long productId=db.saveProduct("Stara usluga",12000,25);
            Store.Item item=db.products().get(0);
            long documentId=db.createDraft(Store.INVOICE,customer,
                "2026-10-28","2026-11-28","2026-10-28","",
                java.util.Arrays.asList(item));
            db.confirm(documentId);
            db.updateProduct(productId,"Nova usluga",19000,13);
            assertEquals("Stara usluga",db.document(documentId).items.get(0).name);
            assertEquals(12000L,db.document(documentId).items.get(0).unitCents);
            assertEquals(25,db.document(documentId).items.get(0).taxRate);
            assertEquals("Nova usluga",db.products().get(0).name);
            assertEquals(19000L,db.products().get(0).unitCents);
            assertEquals(13,db.products().get(0).taxRate);
        } finally {
            if(db!=null)db.close();
            context.clean();
        }
    }

    public void testReportsAreScopedToActualIssuingMonth() throws Exception {
        IsolatedContext context=new IsolatedContext(getContext()); Store db=null;
        try {
            db=new Store(context);
            db.setting("name","Izdavatelj");
            db.setting("oib","12345678903");
            db.setting("address","Rijeka");
            long client=db.saveClient("Kupac","","Rijeka","");
            Store.Item item=new Store.Item();item.name="Usluga";
            item.qtyMilli=1000;item.unitCents=10000;item.taxRate=25;
            long october=db.createDraft(Store.INVOICE,client,"2026-10-28","2026-11-28","",Arrays.asList(item));
            db.confirm(october);
            long september=db.createDraft(Store.INVOICE,client,"2026-09-28","2026-10-28","",Arrays.asList(item));
            db.confirm(september);
            assertEquals(12500L,db.invoicedCentsMonth("2026-10"));
            assertEquals(12500L,db.invoicedCentsMonth("2026-09"));
            assertEquals(0L,db.invoicedCentsMonth("2026-08"));
            assertEquals(25000L,db.invoicedCents());
        } finally {
            if(db!=null)db.close();
            context.clean();
        }
    }

}
