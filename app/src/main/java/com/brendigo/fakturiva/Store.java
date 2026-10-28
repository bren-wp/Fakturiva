package com.brendigo.fakturiva;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.DatabaseUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

/** Single-device storage. All document state transitions are transactional. */
public final class Store extends SQLiteOpenHelper {
    private static final String DB_NAME = "fakturiva.db";
    public static final int VERSION = 2;
    public static final String INVOICE = "INVOICE", QUOTE = "QUOTE";
    public static final String DRAFT = "DRAFT", ISSUED = "ISSUED", SENT = "SENT", PAID = "PAID", VOID = "VOID";
    private final Context appContext;
    private volatile String lastBackupWarning="";
    public Store(Context c) { super(c, DB_NAME, null, VERSION);appContext=c.getApplicationContext(); }
    private void snapshotAfterWrite(){
        try{RecoveryVault.snapshot(appContext,this);lastBackupWarning="";}
        catch(Exception e){
            lastBackupWarning="Lokalna kopija nije ažurirana. Odmah izvezite sigurnosnu kopiju.";
            android.util.Log.e("FakturivaRecovery","Cannot write local recovery snapshot",e);
        }
    }
    public String backupWarning(){return lastBackupWarning;}
    public String backupSummary(){return RecoveryVault.summary(appContext);}
    public void restoreLocal(int index) throws Exception{
        JSONObject restored=RecoveryVault.read(appContext,index);
        restore(restored);
    }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE company (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, oib TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE catalog (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>=0), tax_rate INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, number TEXT UNIQUE, client_id INTEGER NOT NULL REFERENCES clients(id), issue_date TEXT NOT NULL, due_date TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, net_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, created_at INTEGER NOT NULL, client_name TEXT NOT NULL DEFAULT '', client_address TEXT NOT NULL DEFAULT '', client_oib TEXT NOT NULL DEFAULT '', issuer_name TEXT NOT NULL DEFAULT '', issuer_address TEXT NOT NULL DEFAULT '', issuer_oib TEXT NOT NULL DEFAULT '', issuer_email TEXT NOT NULL DEFAULT '', issuer_iban TEXT NOT NULL DEFAULT '', issuer_tax_note TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE lines (id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL REFERENCES documents(id), name TEXT NOT NULL, qty_milli INTEGER NOT NULL CHECK(qty_milli>0), unit_cents INTEGER NOT NULL CHECK(unit_cents>=0), tax_rate INTEGER NOT NULL, net_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL REFERENCES documents(id), amount_cents INTEGER NOT NULL CHECK(amount_cents>0), paid_on TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sequences (type TEXT NOT NULL, year INTEGER NOT NULL, last_num INTEGER NOT NULL, PRIMARY KEY(type,year))");
        db.execSQL("CREATE INDEX idx_documents_type ON documents(type,created_at DESC)");
        db.execSQL("CREATE INDEX idx_documents_client ON documents(client_id)");
        db.execSQL("CREATE INDEX idx_payments_doc ON payments(document_id)");
        for (String key : new String[]{"name","oib","address","email","iban","phone","tax_note"}) {
            ContentValues v = new ContentValues(); v.put("key",key); v.put("value","");
            db.insertOrThrow("company",null,v);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV==1 && newV>=2){
            for(String column:new String[]{"client_name","client_address","client_oib","issuer_name","issuer_address","issuer_oib","issuer_email","issuer_iban","issuer_tax_note"})
                db.execSQL("ALTER TABLE documents ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE documents SET client_name=(SELECT name FROM clients WHERE clients.id=documents.client_id),client_address=(SELECT address FROM clients WHERE clients.id=documents.client_id),client_oib=(SELECT oib FROM clients WHERE clients.id=documents.client_id)");
            for(String key:new String[]{"name","address","oib","email","iban","tax_note"})
                db.execSQL("UPDATE documents SET issuer_"+key+"=(SELECT value FROM company WHERE company.key='"+key+"')");
            return;
        }
        throw new IllegalStateException("Nepodržana migracija baze: "+oldV+" -> "+newV);
    }
    public static final class Client {
        public long id; public String name, oib, address, email;
    }
    public static final class Item {
        public long id; public String name; public long qtyMilli = 1000, unitCents;
        public int taxRate=25;
        public long net() { return Money.subtotal(qtyMilli, unitCents); }
        public long tax() { return Money.tax(net(),taxRate); }
    }
    public static final class Document {
        public long id,clientId,netCents,taxCents,totalCents;
        public String type,number,clientName,clientAddress,clientOib,issueDate,dueDate,notes,status;
        public String issuerName,issuerAddress,issuerOib,issuerEmail,issuerIban,issuerTaxNote;
        public List<Item> items = new ArrayList<>();
        public long paidCents;
        public long outstanding() { return Math.max(0, totalCents - paidCents); }
    }
    public String setting(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM company WHERE key=?",new String[]{key})) {
            return c.moveToFirst()?c.getString(0):"";
        }
    }
    public void setting(String key,String value) {
        ContentValues v = new ContentValues(); v.put("key",key); v.put("value",value.trim());
        getWritableDatabase().insertWithOnConflict("company",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        snapshotAfterWrite();
    }
    /** Update the company profile atomically instead of saving each field separately. */
    public void companySettings(String[] keys,String[] values){
        if(keys.length!=values.length)throw new IllegalArgumentException("Neispravna polja");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            for(int i=0;i<keys.length;i++){
                if(!java.util.Arrays.asList("name","oib","address","email","iban","phone","tax_note").contains(keys[i]))
                    throw new IllegalArgumentException("Nepoznata postavka");
                ContentValues v=new ContentValues();v.put("key",keys[i]);v.put("value",values[i].trim());
                db.insertWithOnConflict("company",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        snapshotAfterWrite();
    }
    public long saveClient(String name,String oib,String address,String email) {
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Unesite naziv klijenta");
        ContentValues v = new ContentValues();
        v.put("name",name.trim()); v.put("oib",oib.trim());
        v.put("address",address.trim()); v.put("email",email.trim());
        long id=getWritableDatabase().insertOrThrow("clients",null,v);
        snapshotAfterWrite();return id;
    }
    public List<Client> clients() {
        ArrayList<Client> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,oib,address,email FROM clients ORDER BY name COLLATE NOCASE",null)) {
            while(c.moveToNext()) { Client x=new Client();
                x.id=c.getLong(0);x.name=c.getString(1);x.oib=c.getString(2);x.address=c.getString(3);x.email=c.getString(4);out.add(x);
            }
        }
        return out;
    }
    public long saveProduct(String name,long priceCents,int taxRate) {
        if(name.trim().isEmpty() || priceCents<0) throw new IllegalArgumentException("Neispravna usluga");
        Money.tax(0,taxRate);
        ContentValues v=new ContentValues();
        v.put("name",name.trim());v.put("price_cents",priceCents);v.put("tax_rate",taxRate);
        long id=getWritableDatabase().insertOrThrow("catalog",null,v);
        snapshotAfterWrite();return id;
    }
    public List<Item> products() {
        ArrayList<Item> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,price_cents,tax_rate FROM catalog ORDER BY name COLLATE NOCASE",null)) {
            while(c.moveToNext()) { Item x=new Item();x.id=c.getLong(0);x.name=c.getString(1);x.unitCents=c.getLong(2);x.taxRate=c.getInt(3);out.add(x); }
        }
        return out;
    }
    public long createDraft(String type,long clientId,String issueDate,String dueDate,String notes,List<Item> items) {
        if(!type.equals(INVOICE)&&!type.equals(QUOTE)) throw new IllegalArgumentException("Neispravna vrsta dokumenta");
        if(items.isEmpty()) throw new IllegalArgumentException("Dodajte barem jednu stavku");
        if(issueDate.isEmpty() || dueDate.isEmpty()) throw new IllegalArgumentException("Unesite datume");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        boolean saved=false;
        try {
            long net=0,tax=0;
            for(Item i:items) {
                if(i.name.trim().isEmpty()) throw new IllegalArgumentException("Unesite naziv stavke");
                net=Math.addExact(net,i.net()); tax=Math.addExact(tax,i.tax());
            }
            ContentValues doc=new ContentValues();
            doc.put("type",type);doc.put("client_id",clientId);
            doc.put("issue_date",issueDate);doc.put("due_date",dueDate);
            doc.put("notes",notes);doc.put("status",DRAFT);
            doc.put("net_cents",net);doc.put("tax_cents",tax);doc.put("total_cents",Math.addExact(net,tax));
            doc.put("created_at",System.currentTimeMillis());
            try(Cursor client=db.rawQuery("SELECT name,address,oib FROM clients WHERE id=?",new String[]{String.valueOf(clientId)})){
                if(!client.moveToFirst())throw new IllegalArgumentException("Odabrani klijent ne postoji");
                doc.put("client_name",client.getString(0));doc.put("client_address",client.getString(1));doc.put("client_oib",client.getString(2));
            }
            for(String key:new String[]{"name","address","oib","email","iban","tax_note"})doc.put("issuer_"+key,setting(key));
            long id=db.insertOrThrow("documents",null,doc);
            for(Item i:items) {
                ContentValues v=new ContentValues();
                v.put("document_id",id);v.put("name",i.name.trim());v.put("qty_milli",i.qtyMilli);
                v.put("unit_cents",i.unitCents);v.put("tax_rate",i.taxRate);
                v.put("net_cents",i.net());v.put("tax_cents",i.tax());
                db.insertOrThrow("lines",null,v);
            }
            db.setTransactionSuccessful(); saved=true; return id;
        } finally {
            db.endTransaction();
            if(saved)snapshotAfterWrite();
        }
    }
    /** Edit only a draft. Confirmed invoices and quotes stay immutable. */
    public void updateDraft(long id,long clientId,String issueDate,String dueDate,String notes,List<Item> items){
        if(items.isEmpty())throw new IllegalArgumentException("Dodajte barem jednu stavku");
        if(!issueDate.matches("\\d{4}-\\d{2}-\\d{2}")||!dueDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            throw new IllegalArgumentException("Datum mora biti u formatu YYYY-MM-DD");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        boolean saved=false;
        try{
            try(Cursor c=db.rawQuery("SELECT status FROM documents WHERE id=?",new String[]{String.valueOf(id)})){
                if(!c.moveToFirst()||!DRAFT.equals(c.getString(0)))
                    throw new IllegalStateException("Moguće je uređivati samo nacrt");
            }
            long net=0,tax=0;
            for(Item i:items){
                if(i.name.trim().isEmpty())throw new IllegalArgumentException("Naziv stavke je obavezan");
                net=Math.addExact(net,i.net());tax=Math.addExact(tax,i.tax());
            }
            ContentValues changes=new ContentValues();
            changes.put("client_id",clientId);
            changes.put("issue_date",issueDate);changes.put("due_date",dueDate);changes.put("notes",notes);
            changes.put("net_cents",net);changes.put("tax_cents",tax);changes.put("total_cents",Math.addExact(net,tax));
            try(Cursor c=db.rawQuery("SELECT name,address,oib FROM clients WHERE id=?",new String[]{String.valueOf(clientId)})){
                if(!c.moveToFirst())throw new IllegalArgumentException("Odabrani klijent ne postoji");
                changes.put("client_name",c.getString(0));changes.put("client_address",c.getString(1));changes.put("client_oib",c.getString(2));
            }
            if(db.update("documents",changes,"id=? AND status='DRAFT'",new String[]{String.valueOf(id)})!=1)
                throw new IllegalStateException("Neuspjelo spremanje nacrta");
            db.delete("lines","document_id=?",new String[]{String.valueOf(id)});
            for(Item i:items){
                ContentValues line=new ContentValues();
                line.put("document_id",id);line.put("name",i.name.trim());line.put("qty_milli",i.qtyMilli);
                line.put("unit_cents",i.unitCents);line.put("tax_rate",i.taxRate);
                line.put("net_cents",i.net());line.put("tax_cents",i.tax());
                db.insertOrThrow("lines",null,line);
            }
            db.setTransactionSuccessful();saved=true;
        }finally{db.endTransaction();if(saved)snapshotAfterWrite();}
    }
    public Document document(long id) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT d.id,d.type,d.number,d.client_id,d.client_name,d.client_address,d.client_oib,d.issue_date,d.due_date,d.notes,d.status,d.net_cents,d.tax_cents,d.total_cents,(SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE document_id=d.id),d.issuer_name,d.issuer_address,d.issuer_oib,d.issuer_email,d.issuer_iban,d.issuer_tax_note FROM documents d WHERE d.id=?",
            new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Dokument ne postoji");
            Document d=new Document();d.id=c.getLong(0);d.type=c.getString(1);d.number=c.getString(2);
            d.clientId=c.getLong(3);d.clientName=c.getString(4);d.clientAddress=c.getString(5);d.clientOib=c.getString(6);
            d.issueDate=c.getString(7);d.dueDate=c.getString(8);d.notes=c.getString(9);d.status=c.getString(10);
            d.netCents=c.getLong(11);d.taxCents=c.getLong(12);d.totalCents=c.getLong(13);d.paidCents=c.getLong(14);
            d.issuerName=c.getString(15);d.issuerAddress=c.getString(16);d.issuerOib=c.getString(17);
            d.issuerEmail=c.getString(18);d.issuerIban=c.getString(19);d.issuerTaxNote=c.getString(20);
            try(Cursor l=getReadableDatabase().rawQuery("SELECT id,name,qty_milli,unit_cents,tax_rate FROM lines WHERE document_id=? ORDER BY id",new String[]{String.valueOf(id)})){
                while(l.moveToNext()) { Item i=new Item();i.id=l.getLong(0);i.name=l.getString(1);i.qtyMilli=l.getLong(2);i.unitCents=l.getLong(3);i.taxRate=l.getInt(4);d.items.add(i); }
            }
            return d;
        }
    }
    public List<Document> documents(String type) {
        ArrayList<Document> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM documents WHERE type=? ORDER BY created_at DESC,id DESC",new String[]{type})){
            while(c.moveToNext()) out.add(document(c.getLong(0)));
        }
        return out;
    }
    public String confirm(long id) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        boolean saved=false;
        try {
            String type, issueDate,status;
            try(Cursor c=db.rawQuery("SELECT type,issue_date,status FROM documents WHERE id=?",new String[]{String.valueOf(id)})){
                if(!c.moveToFirst()) throw new IllegalArgumentException("Dokument ne postoji");
                type=c.getString(0);issueDate=c.getString(1);status=c.getString(2);
            }
            if(!DRAFT.equals(status)) throw new IllegalStateException("Dokument je već potvrđen");
            if(setting("name").trim().isEmpty()) throw new IllegalStateException("Unesite podatke svoje tvrtke u Postavkama");
            int year=Integer.parseInt(issueDate.substring(0,4));
            int num=1;
            try(Cursor c=db.rawQuery("SELECT last_num FROM sequences WHERE type=? AND year=?",new String[]{type,String.valueOf(year)})){
                if(c.moveToFirst()) num=c.getInt(0)+1;
            }
            ContentValues seq=new ContentValues();seq.put("type",type);seq.put("year",year);seq.put("last_num",num);
            db.insertWithOnConflict("sequences",null,seq,SQLiteDatabase.CONFLICT_REPLACE);
            String code=(INVOICE.equals(type)?"R":"P")+"-"+year+"-"+String.format(java.util.Locale.ROOT,"%04d",num);
            ContentValues v=new ContentValues();v.put("number",code);v.put("status",INVOICE.equals(type)?ISSUED:SENT);
            if(db.update("documents",v,"id=? AND status=?",new String[]{String.valueOf(id),DRAFT})!=1)
                throw new IllegalStateException("Dokument se promijenio");
            db.setTransactionSuccessful();saved=true;
            return code;
        } finally {db.endTransaction();if(saved)snapshotAfterWrite();}
    }
    public void pay(long id,long amount,String date) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        boolean saved=false;
        try {
            Document d=document(id);
            if(!INVOICE.equals(d.type)||DRAFT.equals(d.status)||VOID.equals(d.status))
                throw new IllegalStateException("Moguće je naplatiti samo potvrđen račun");
            if(amount<=0||amount>d.outstanding()) throw new IllegalArgumentException("Uplata premašuje preostali iznos");
            ContentValues v=new ContentValues();v.put("document_id",id);v.put("amount_cents",amount);v.put("paid_on",date);
            db.insertOrThrow("payments",null,v);
            if(amount==d.outstanding()){ContentValues p=new ContentValues();p.put("status",PAID);db.update("documents",p,"id=?",new String[]{String.valueOf(id)});}
            db.setTransactionSuccessful();saved=true;
        } finally{db.endTransaction();if(saved)snapshotAfterWrite();}
    }
    public long quoteToInvoice(long quoteId) {
        Document d=document(quoteId);
        if(!QUOTE.equals(d.type) || DRAFT.equals(d.status) || VOID.equals(d.status))
            throw new IllegalStateException("Potvrdite ponudu prije pretvaranja");
        return createDraft(INVOICE,d.clientId,d.issueDate,d.dueDate,"Prema ponudi "+d.number,d.items);
    }
    public long countMonth(String type,String yearMonth){
        if(!INVOICE.equals(type)&&!QUOTE.equals(type))throw new IllegalArgumentException("Nepoznati dokument");
        return DatabaseUtils.longForQuery(getReadableDatabase(),
            "SELECT COUNT(*) FROM documents WHERE type=? AND substr(issue_date,1,7)=?",
            new String[]{type,yearMonth});
    }
    public long count(String table) {
        if(!table.equals("clients")&&!table.equals("catalog")&&!table.equals("documents")) return 0;
        return DatabaseUtils.longForQuery(getReadableDatabase(),"SELECT COUNT(*) FROM "+table,null);
    }
    public long invoicedCents() {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
            "SELECT COALESCE(SUM(total_cents),0) FROM documents WHERE type='INVOICE' AND status IN ('ISSUED','PAID')",null);
    }
    public long receivedCents() {
        return DatabaseUtils.longForQuery(getReadableDatabase(),
            "SELECT COALESCE(SUM(amount_cents),0) FROM payments",null);
    }
    /** Twelve chronological monthly receipt totals, with zeroes for empty months. */
    public long[] monthlyReceipts(){
        long[] values=new long[12];
        java.util.Calendar month=java.util.Calendar.getInstance();
        month.set(java.util.Calendar.DAY_OF_MONTH,1);
        month.add(java.util.Calendar.MONTH,-11);
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM",java.util.Locale.ROOT);
        for(int i=0;i<values.length;i++){
            values[i]=DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE substr(paid_on,1,7)=?",
                new String[]{fmt.format(month.getTime())});
            month.add(java.util.Calendar.MONTH,1);
        }
        return values;
    }
    public JSONObject backup() throws JSONException {
        JSONObject root=new JSONObject();root.put("format","fakturiva-backup-v1");root.put("schema",VERSION);
        for(String table:new String[]{"company","clients","catalog","documents","lines","payments","sequences"}){
            JSONArray rows=new JSONArray();
            try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM "+table,null)){
                while(c.moveToNext()){JSONObject row=new JSONObject();
                    for(int i=0;i<c.getColumnCount();i++){
                        if(c.isNull(i)) row.put(c.getColumnName(i),JSONObject.NULL);
                        else if(c.getType(i)==Cursor.FIELD_TYPE_INTEGER) row.put(c.getColumnName(i),c.getLong(i));
                        else row.put(c.getColumnName(i),c.getString(i));
                    }
                    rows.put(row);
                }
            }
            root.put(table,rows);
        }
        return root;
    }
    public void restore(JSONObject root) throws JSONException {
        if(!"fakturiva-backup-v1".equals(root.optString("format")) || root.optInt("schema")<1 || root.optInt("schema")>VERSION)
            throw new IllegalArgumentException("Nepodržana sigurnosna kopija");
        for(String table:new String[]{"company","clients","catalog","documents","lines","payments","sequences"})
            if(root.optJSONArray(table)==null)throw new IllegalArgumentException("Nedostaje tablica u kopiji: "+table);
        SQLiteDatabase db=getWritableDatabase();
        // Preserve a snapshot of the CURRENT database before replacing any records.
        snapshotAfterWrite();
        db.beginTransaction();boolean saved=false;
        try {
            for(String table:new String[]{"payments","lines","documents","sequences","catalog","clients","company"}) db.delete(table,null,null);
            for(String table:new String[]{"company","clients","catalog","documents","lines","payments","sequences"}){
                JSONArray arr=root.getJSONArray(table);
                if(arr.length()>100000) throw new IllegalArgumentException("Sigurnosna kopija je prevelika");
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);ContentValues v=new ContentValues();
                    java.util.Iterator<String> keys=o.keys();
                    while(keys.hasNext()){String k=keys.next();Object val=o.get(k);
                        if(val==JSONObject.NULL) v.putNull(k);
                        else if(val instanceof Number) v.put(k,((Number)val).longValue());
                        else v.put(k,String.valueOf(val));
                    }
                    db.insertOrThrow(table,null,v);
                }
            }
            if(root.optInt("schema")==1){
                db.execSQL("UPDATE documents SET client_name=(SELECT name FROM clients WHERE clients.id=documents.client_id),client_address=(SELECT address FROM clients WHERE clients.id=documents.client_id),client_oib=(SELECT oib FROM clients WHERE clients.id=documents.client_id)");
                for(String key:new String[]{"name","address","oib","email","iban","tax_note"})
                    db.execSQL("UPDATE documents SET issuer_"+key+"=(SELECT value FROM company WHERE company.key='"+key+"')");
            }
            try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)){
                if(c.moveToFirst()) throw new IllegalArgumentException("Neispravne veze u sigurnosnoj kopiji");
            }
            db.setTransactionSuccessful();saved=true;
        } finally{db.endTransaction();if(saved)snapshotAfterWrite();}
    }
}
