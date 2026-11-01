package com.brendigo.fakturiva;

import android.test.AndroidTestCase;
import android.test.IsolatedContext;
import java.util.List;
import org.json.JSONObject;

/** Screen 14 regression: persisted category/unit and safe legacy migrations. */
@SuppressWarnings("deprecation")
public final class CatalogScreensTest extends AndroidTestCase {
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
    }
}
