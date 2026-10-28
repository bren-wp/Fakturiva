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
    public static final int VERSION = 1;
    public static final String INVOICE = "INVOICE", QUOTE = "QUOTE";
    public static final String DRAFT = "DRAFT", ISSUED = "ISSUED", SENT = "SENT", PAID = "PAID", VOID = "VOID";
    public Store(Context c) { super(c, DB_NAME, null, VERSION); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE company (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, oib TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE catalog (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>=0), tax_rate INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, number TEXT UNIQUE, client_id INTEGER NOT NULL REFERENCES clients(id), issue_date TEXT NOT NULL, due_date TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, net_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, created_at INTEGER NOT NULL)");
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
        // Intentionally no destructive migration: future schema updates must be additive.
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
    }
    public long saveClient(String name,String oib,String address,String email) {
        if (name.trim().isEmpty()) throw new IllegalArgumentException("Unesite naziv klijenta");
        ContentValues v = new ContentValues();
        v.put("name",name.trim()); v.put("oib",oib.trim());
        v.put("address",address.trim()); v.put("email",email.trim());
        return getWritableDatabase().insertOrThrow("clients",null,v);
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
        return getWritableDatabase().insertOrThrow("catalog",null,v);
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
            long id=db.insertOrThrow("documents",null,doc);
            for(Item i:items) {
                ContentValues v=new ContentValues();
                v.put("document_id",id);v.put("name",i.name.trim());v.put("qty_milli",i.qtyMilli);
                v.put("unit_cents",i.unitCents);v.put("tax_rate",i.taxRate);
                v.put("net_cents",i.net());v.put("tax_cents",i.tax());
                db.insertOrThrow("lines",null,v);
            }
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }
    public Document document(long id) {
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT d.id,d.type,d.number,d.client_id,c.name,c.address,c.oib,d.issue_date,d.due_date,d.notes,d.status,d.net_cents,d.tax_cents,d.total_cents,(SELECT COALESCE(SUM(amount_cents),0) FROM payments WHERE document_id=d.id) FROM documents d JOIN clients c ON c.id=d.client_id WHERE d.id=?",
            new String[]{String.valueOf(id)})) {
            if(!c.moveToFirst()) throw new IllegalArgumentException("Dokument ne postoji");
            Document d=new Document();d.id=c.getLong(0);d.type=c.getString(1);d.number=c.getString(2);
            d.clientId=c.getLong(3);d.clientName=c.getString(4);d.clientAddress=c.getString(5);d.clientOib=c.getString(6);
            d.issueDate=c.getString(7);d.dueDate=c.getString(8);d.notes=c.getString(9);d.status=c.getString(10);
            d.netCents=c.getLong(11);d.taxCents=c.getLong(12);d.totalCents=c.getLong(13);d.paidCents=c.getLong(14);
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
            db.setTransactionSuccessful();
            return code;
        } finally {db.endTransaction();}
    }
    public void pay(long id,long amount,String date) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            Document d=document(id);
            if(!INVOICE.equals(d.type)||DRAFT.equals(d.status)||VOID.equals(d.status))
                throw new IllegalStateException("Moguće je naplatiti samo potvrđen račun");
            if(amount<=0||amount>d.outstanding()) throw new IllegalArgumentException("Uplata premašuje preostali iznos");
            ContentValues v=new ContentValues();v.put("document_id",id);v.put("amount_cents",amount);v.put("paid_on",date);
            db.insertOrThrow("payments",null,v);
            if(amount==d.outstanding()){ContentValues p=new ContentValues();p.put("status",PAID);db.update("documents",p,"id=?",new String[]{String.valueOf(id)});}
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }
    public long quoteToInvoice(long quoteId) {
        Document d=document(quoteId);
        if(!QUOTE.equals(d.type) || DRAFT.equals(d.status) || VOID.equals(d.status))
            throw new IllegalStateException("Potvrdite ponudu prije pretvaranja");
        return createDraft(INVOICE,d.clientId,d.issueDate,d.dueDate,"Prema ponudi "+d.number,d.items);
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
        if(!"fakturiva-backup-v1".equals(root.optString("format")) || root.optInt("schema")!=VERSION)
            throw new IllegalArgumentException("Nepodržana sigurnosna kopija");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
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
            try(Cursor c=db.rawQuery("PRAGMA foreign_key_check",null)){
                if(c.moveToFirst()) throw new IllegalArgumentException("Neispravne veze u sigurnosnoj kopiji");
            }
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
    }
}
