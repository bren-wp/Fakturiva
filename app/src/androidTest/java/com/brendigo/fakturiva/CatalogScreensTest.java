package com.brendigo.fakturiva;

import android.test.AndroidTestCase;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.List;
import org.json.JSONObject;

/** Screen 14 regression: persisted category/unit and safe legacy migrations. */
@SuppressWarnings("deprecation")
public final class CatalogScreensTest extends AndroidTestCase {
    /** Mirrors the repository's existing per-test SQLite isolation. */
    private static final class IsolatedContext extends ContextWrapper {
        final File dir;
        IsolatedContext(Context base){
            super(base);
            dir=new File(base.getCacheDir(),"isolated-catalog-"+System.nanoTime());
            if(!dir.mkdirs())throw new IllegalStateException("Cannot create catalog test dir");
        }
        @Override public Context getApplicationContext(){return this;}
        @Override public File getFilesDir(){
            File f=new File(dir,"files");
            if(!f.exists()&&!f.mkdirs())throw new IllegalStateException("files");
            return f;
        }
        @Override public File getDatabasePath(String name){
            File f=new File(dir,"databases/"+name);
            if(!f.getParentFile().exists()&&!f.getParentFile().mkdirs())
                throw new IllegalStateException("database dir");
            return f;
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,
                SQLiteDatabase.CursorFactory factory){
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,
                SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){
            return SQLiteDatabase.openOrCreateDatabase(
                getDatabasePath(name).getAbsolutePath(),factory,handler);
        }
        void clean(){remove(dir);}
        private static void remove(File f){
            if(f.isDirectory()){
                File[] files=f.listFiles();
                if(files!=null)for(File child:files)remove(child);
            }
            if(!f.delete())android.util.Log.w("FakturivaQA","Test cleanup: "+f);
        }
    }

    public void testCatalogCategoriesAndUnitsPersistInBackup()throws Exception {
        IsolatedContext context=new IsolatedContext(getContext());
        Store first=null,restored=null;
        IsolatedContext second=null;
        try{
            first=new Store(context);
            long service=first.saveProduct("SEO optimizacija",25000,25,"SERVICE","sat");
            long product=first.saveProduct("Proizvod A",9000,25,"PRODUCT","kom");
            long legacy=first.saveProduct("Nerazvrstana stavka",4000,25);
            List<Store.Item> rows=first.products();
            Store.Item s=find(rows,service);
            assertEquals("SERVICE",s.kind);
            assertEquals("sat",s.unit);
            assertTrue(MainActivity.catalogMatches(s,"SEO","Usluge"));
            assertFalse(MainActivity.catalogMatches(s,"SEO","Proizvodi"));
            assertTrue(MainActivity.catalogMatches(find(rows,product),"","Proizvodi"));
            assertTrue(MainActivity.catalogMatches(find(rows,legacy),"","Nerazvrstano"));

            first.updateProduct(service,"SEO optimizacija",20000,25,"SERVICE","mj");
            assertEquals("mj",find(first.products(),service).unit);
            JSONObject backup=first.backup();
            assertEquals(5,backup.getInt("schema"));
            second=new IsolatedContext(getContext());
            restored=new Store(second);
            restored.restore(backup);
            assertEquals("SERVICE",find(restored.products(),service).kind);
            assertEquals("mj",find(restored.products(),service).unit);

            JSONObject old=new JSONObject(backup.toString());
            old.put("schema",4);
            org.json.JSONArray catalog=old.getJSONArray("catalog");
            for(int i=0;i<catalog.length();i++){
                catalog.getJSONObject(i).remove("kind");
                catalog.getJSONObject(i).remove("unit");
            }
            restored.restore(old);
            assertEquals("UNSPECIFIED",find(restored.products(),service).kind);
            assertEquals("kom",find(restored.products(),service).unit);
        }finally{
            if(restored!=null)restored.close();
            if(first!=null)first.close();
            if(second!=null)second.clean();
            context.clean();
        }
    }
    private static Store.Item find(List<Store.Item> all,long id){
        for(Store.Item item:all)if(item.id==id)return item;
        throw new AssertionError("Missing "+id);
    }
    public void testInvalidKindsAndUnitsAreRejected(){
        try{Store.normalizedKind("MYSTERY");fail("Unknown kind");}
        catch(IllegalArgumentException expected){}
        try{Store.normalizedUnit("");fail("Empty unit");}
        catch(IllegalArgumentException expected){}
        assertEquals("sat",Store.normalizedUnit("sat"));
        try{Store.normalizedUnit("   ");fail("Whitespace-only unit");}
        catch(IllegalArgumentException expected){}
    }
}
