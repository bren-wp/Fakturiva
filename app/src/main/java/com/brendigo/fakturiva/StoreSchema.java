package com.brendigo.fakturiva;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

/**
 * Versioned, additive SQLite schema and migrations.
 * SQL is moved verbatim from Store: no table, index or row is dropped.
 * SQLiteOpenHelper still wraps upgrades in the platform transaction.
 */
final class StoreSchema {
    private StoreSchema(){}

    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE company (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, oib TEXT NOT NULL DEFAULT '', address TEXT NOT NULL DEFAULT '', email TEXT NOT NULL DEFAULT '', phone TEXT NOT NULL DEFAULT '', vat_id TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE catalog (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price_cents INTEGER NOT NULL CHECK(price_cents>=0), tax_rate INTEGER NOT NULL, kind TEXT NOT NULL DEFAULT 'UNSPECIFIED', unit TEXT NOT NULL DEFAULT 'kom', currency_code TEXT NOT NULL DEFAULT 'EUR')");
        db.execSQL("CREATE TABLE documents (id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT NOT NULL, number TEXT UNIQUE, client_id INTEGER NOT NULL REFERENCES clients(id), issue_date TEXT NOT NULL, due_date TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '', status TEXT NOT NULL, net_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL, total_cents INTEGER NOT NULL, created_at INTEGER NOT NULL, client_name TEXT NOT NULL DEFAULT '', client_address TEXT NOT NULL DEFAULT '', client_oib TEXT NOT NULL DEFAULT '', issuer_name TEXT NOT NULL DEFAULT '', issuer_address TEXT NOT NULL DEFAULT '', issuer_oib TEXT NOT NULL DEFAULT '', issuer_email TEXT NOT NULL DEFAULT '', issuer_iban TEXT NOT NULL DEFAULT '', issuer_tax_note TEXT NOT NULL DEFAULT '', client_email TEXT NOT NULL DEFAULT '', client_phone TEXT NOT NULL DEFAULT '', client_vat_id TEXT NOT NULL DEFAULT '', issuer_phone TEXT NOT NULL DEFAULT '', issuer_vat_id TEXT NOT NULL DEFAULT '', issuer_postal_code TEXT NOT NULL DEFAULT '', issuer_city TEXT NOT NULL DEFAULT '', issuer_country TEXT NOT NULL DEFAULT '', issuer_website TEXT NOT NULL DEFAULT '', issuer_bank_name TEXT NOT NULL DEFAULT '', issuer_swift TEXT NOT NULL DEFAULT '', issuer_registry TEXT NOT NULL DEFAULT '', issuer_business_unit TEXT NOT NULL DEFAULT '', issuer_operator TEXT NOT NULL DEFAULT '', issuer_payment_method TEXT NOT NULL DEFAULT '', issuer_payment_reference TEXT NOT NULL DEFAULT '', supply_date TEXT NOT NULL DEFAULT '', quote_title TEXT NOT NULL DEFAULT '', currency_code TEXT NOT NULL DEFAULT 'EUR', issuer_legal_form TEXT NOT NULL DEFAULT '', issuer_trading_name TEXT NOT NULL DEFAULT '', issuer_company_number TEXT NOT NULL DEFAULT '', issuer_register_authority TEXT NOT NULL DEFAULT '', issuer_tax_registration TEXT NOT NULL DEFAULT '', issuer_address_2 TEXT NOT NULL DEFAULT '', issuer_region TEXT NOT NULL DEFAULT '', issuer_payment_terms TEXT NOT NULL DEFAULT '', issuer_footer_note TEXT NOT NULL DEFAULT '', issuer_contact_person TEXT NOT NULL DEFAULT '', issuer_bank_account_number TEXT NOT NULL DEFAULT '', issuer_bank_routing_number TEXT NOT NULL DEFAULT '', issuer_bank_sort_code TEXT NOT NULL DEFAULT '', issuer_bank_address TEXT NOT NULL DEFAULT '', issuer_invoice_reference TEXT NOT NULL DEFAULT '', issuer_invoice_theme TEXT NOT NULL DEFAULT '', issuer_invoice_accent TEXT NOT NULL DEFAULT '', issuer_logo_sha256 TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE lines (id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL REFERENCES documents(id), name TEXT NOT NULL, qty_milli INTEGER NOT NULL CHECK(qty_milli>0), unit_cents INTEGER NOT NULL CHECK(unit_cents>=0), tax_rate INTEGER NOT NULL, net_cents INTEGER NOT NULL, tax_cents INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT, document_id INTEGER NOT NULL REFERENCES documents(id), amount_cents INTEGER NOT NULL CHECK(amount_cents>0), paid_on TEXT NOT NULL, channel TEXT NOT NULL DEFAULT 'UNKNOWN', business_unit TEXT NOT NULL DEFAULT 'PP1')");
        db.execSQL("CREATE TABLE sequences (type TEXT NOT NULL, year INTEGER NOT NULL, last_num INTEGER NOT NULL, PRIMARY KEY(type,year))");
        db.execSQL("CREATE TABLE branding_assets (sha256 TEXT PRIMARY KEY, image_base64 TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_documents_type ON documents(type,created_at DESC)");
        db.execSQL("CREATE INDEX idx_documents_client ON documents(client_id)");
        db.execSQL("CREATE INDEX idx_payments_doc ON payments(document_id)");
        for (String key : new String[]{"name","oib","address","email","iban","phone","tax_note","vat_id","postal_code","city","country","website","bank_name","swift","registry","business_unit","operator","payment_method","payment_reference","currency","legal_form","trading_name","company_number","register_authority","tax_registration","address_2","region","payment_terms","footer_note","contact_person","bank_account_number","bank_routing_number","bank_sort_code","bank_address","invoice_reference","invoice_theme","invoice_accent","logo_sha256"}) {
            ContentValues v = new ContentValues(); v.put("key",key); v.put("value","");
            db.insertOrThrow("company",null,v);
        }
    }

    static void upgrade(SQLiteDatabase db,int oldV,int newV) {
        if(oldV<1||oldV>8||newV!=Store.VERSION)throw new IllegalStateException("Nepodržana migracija baze: "+oldV+" -> "+newV);
        if(oldV==1) {
            for(String column:new String[]{"client_name","client_address","client_oib","issuer_name","issuer_address","issuer_oib","issuer_email","issuer_iban","issuer_tax_note"})
                db.execSQL("ALTER TABLE documents ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE documents SET client_name=(SELECT name FROM clients WHERE clients.id=documents.client_id),client_address=(SELECT address FROM clients WHERE clients.id=documents.client_id),client_oib=(SELECT oib FROM clients WHERE clients.id=documents.client_id)");
            for(String key:new String[]{"name","address","oib","email","iban","tax_note"})
                db.execSQL("UPDATE documents SET issuer_"+key+"=(SELECT value FROM company WHERE company.key='"+key+"')");
        }
        if(oldV<=2) {
            for(String column:new String[]{"phone","vat_id"})
                db.execSQL("ALTER TABLE clients ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");
            for(String column:new String[]{"client_email","client_phone","client_vat_id","issuer_phone","issuer_vat_id","issuer_postal_code","issuer_city","issuer_country","issuer_website","issuer_bank_name","issuer_swift","issuer_registry","issuer_business_unit","issuer_operator","issuer_payment_method","issuer_payment_reference","supply_date"})
                db.execSQL("ALTER TABLE documents ADD COLUMN "+column+" TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE documents SET client_email=(SELECT email FROM clients WHERE id=documents.client_id),client_phone=(SELECT phone FROM clients WHERE id=documents.client_id),client_vat_id=(SELECT vat_id FROM clients WHERE id=documents.client_id)");
            for(String key:new String[]{"vat_id","postal_code","city","country","website","bank_name","swift","registry","business_unit","operator","payment_method","payment_reference"})
                db.execSQL("INSERT OR IGNORE INTO company(key,value) VALUES('"+key+"','')");
            db.execSQL("UPDATE documents SET issuer_phone=(SELECT value FROM company WHERE key='phone')");
            db.execSQL("UPDATE documents SET supply_date=issue_date");
        }
        if(oldV<=3){
            db.execSQL("ALTER TABLE documents ADD COLUMN quote_title TEXT NOT NULL DEFAULT ''");
        }
        if(oldV<=4){
            db.execSQL("ALTER TABLE catalog ADD COLUMN kind TEXT NOT NULL DEFAULT 'UNSPECIFIED'");
            db.execSQL("ALTER TABLE catalog ADD COLUMN unit TEXT NOT NULL DEFAULT 'kom'");
        }
        if(oldV<=5){
            db.execSQL("ALTER TABLE documents ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'EUR'");
            db.execSQL("ALTER TABLE catalog ADD COLUMN currency_code TEXT NOT NULL DEFAULT 'EUR'");
            for(String key:new String[]{"legal_form","trading_name","company_number",
                "register_authority","tax_registration","address_2","region",
                "payment_terms","footer_note","contact_person","bank_account_number","bank_routing_number","bank_sort_code","bank_address","invoice_reference"})
                db.execSQL("ALTER TABLE documents ADD COLUMN issuer_"+key+" TEXT NOT NULL DEFAULT ''");
            for(String key:new String[]{"currency","legal_form","trading_name","company_number",
                "register_authority","tax_registration","address_2","region",
                "payment_terms","footer_note","contact_person","bank_account_number","bank_routing_number","bank_sort_code","bank_address","invoice_reference"})
                db.execSQL("INSERT OR IGNORE INTO company(key,value) VALUES('"+key+"','')");
        }
        if(oldV<=6){
            db.execSQL("ALTER TABLE payments ADD COLUMN channel TEXT NOT NULL DEFAULT 'UNKNOWN'");
            db.execSQL("ALTER TABLE payments ADD COLUMN business_unit TEXT NOT NULL DEFAULT 'PP1'");
        }
        if(oldV<=7){
            db.execSQL("ALTER TABLE documents ADD COLUMN issuer_invoice_theme TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE documents ADD COLUMN issuer_invoice_accent TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE documents ADD COLUMN issuer_logo_sha256 TEXT NOT NULL DEFAULT ''");
            db.execSQL("CREATE TABLE branding_assets (sha256 TEXT PRIMARY KEY, image_base64 TEXT NOT NULL)");
            for(String key:new String[]{"invoice_theme","invoice_accent","logo_sha256"})
                db.execSQL("INSERT OR IGNORE INTO company(key,value) VALUES('"+key+"','')");
        }
    }
}
