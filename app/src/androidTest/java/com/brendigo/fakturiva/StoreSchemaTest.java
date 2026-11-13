package com.brendigo.fakturiva;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Real SQLite schema/migration tests; operate exclusively on in-memory fixture
 * databases and never open, delete or rekey actual user profile databases.
 */
public final class StoreSchemaTest {
    private static boolean hasColumn(SQLiteDatabase db,String table,String column){
        try(Cursor cursor=db.rawQuery("PRAGMA table_info("+table+")",null)){
            while(cursor.moveToNext())
                if(column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name"))))
                    return true;
        }
        return false;
    }

    @Test public void freshVersionEightHasAllRequiredTablesAndSettings(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            db.execSQL("PRAGMA foreign_keys=ON");
            StoreSchema.create(db);
            for(String table:new String[]{"company","clients","catalog",
                    "documents","lines","payments","sequences","branding_assets"}){
                try(Cursor row=db.rawQuery("SELECT name FROM sqlite_master "+
                        "WHERE type='table' AND name=?",new String[]{table})){
                    assertTrue("Missing table: "+table,row.moveToFirst());
                }
            }
            assertTrue(hasColumn(db,"documents","issuer_logo_sha256"));
            assertTrue(hasColumn(db,"payments","business_unit"));
            try(Cursor row=db.rawQuery(
                    "SELECT value FROM company WHERE key='invoice_theme'",null)){
                assertTrue(row.moveToFirst());
                assertEquals("",row.getString(0));
            }
        }finally{db.close();}
    }

    @Test public void previousVersionSevenUpgradesWithoutDeletingExistingRows(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            db.execSQL("CREATE TABLE company (key TEXT PRIMARY KEY,value TEXT NOT NULL)");
            db.execSQL("CREATE TABLE documents (id INTEGER PRIMARY KEY, issue_date TEXT)");
            db.execSQL("INSERT INTO company(key,value) VALUES('name','Existing company')");
            db.execSQL("INSERT INTO documents(id,issue_date) VALUES(31,'2025-12-31')");
            StoreSchema.upgrade(db,7,Store.VERSION);
            assertTrue(hasColumn(db,"documents","issuer_invoice_theme"));
            assertTrue(hasColumn(db,"documents","issuer_invoice_accent"));
            assertTrue(hasColumn(db,"documents","issuer_logo_sha256"));
            try(Cursor row=db.rawQuery("SELECT id,issue_date FROM documents",null)){
                assertTrue(row.moveToFirst());
                assertEquals(31,row.getLong(0));
                assertEquals("2025-12-31",row.getString(1));
            }
            try(Cursor row=db.rawQuery("SELECT value FROM company WHERE key='name'",null)){
                assertTrue(row.moveToFirst());
                assertEquals("Existing company",row.getString(0));
            }
            try(Cursor row=db.rawQuery("SELECT name FROM sqlite_master "+
                    "WHERE name='branding_assets'",null)){
                assertTrue(row.moveToFirst());
            }
        }finally{db.close();}
    }

    @Test public void invalidMigrationVersionFailsClosed(){
        SQLiteDatabase db=SQLiteDatabase.create(null);
        try{
            try{
                StoreSchema.upgrade(db,0,Store.VERSION);
                fail("Unknown historical version must never be silently reset");
            }catch(IllegalStateException expected){/* no destructive fallback */}
        }finally{db.close();}
    }
}
