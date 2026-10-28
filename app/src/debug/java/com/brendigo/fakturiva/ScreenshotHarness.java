package com.brendigo.fakturiva;

import android.app.Activity;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * QA-only: executes on an Android emulator in the debuggable variant.
 * Not packaged into release builds. Produces genuine screenshots of native screens,
 * with clearly fictional sample data instead of misleading mockups.
 */
public final class ScreenshotHarness {
    private ScreenshotHarness(){}
    private static Object get(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private static void call(MainActivity a,String name,Class<?>[] types,Object... params)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,params);
    }
    private static void call(MainActivity a,String name)throws Exception{call(a,name,new Class<?>[0]);}
    private static void seed(Store store){
        if(store.clients().isEmpty()){
            store.setting("name","Fakturiva Demo d.o.o.");
            store.setting("oib","12345678903");
            store.setting("address","Primjer adrese 15, Rijeka");
            store.setting("email","primjer@example.invalid");
            store.setting("iban","HR0000000000000000000");
            store.saveClient("Studio Primjer d.o.o.","12345678903","Primjer ulica 3, Rijeka","studio@example.invalid");
            store.saveClient("Obrt Primjer","12345678903","Trg Primjera 8, Rijeka","obrt@example.invalid");
            store.saveProduct("Dizajn web stranice",100000,25);
            store.saveProduct("Tehnička podrška",5000,25);
        }
        if(store.documents(Store.INVOICE).isEmpty()){
            long client=store.clients().get(0).id;
            Store.Item item=new Store.Item();item.name="Dizajn web stranice";item.unitCents=100000;item.qtyMilli=1000;item.taxRate=25;
            long id=store.createDraft(Store.INVOICE,client,"2026-10-28","2026-11-12","Primjer dokumenta",Arrays.asList(item));
            store.confirm(id);
            store.pay(id,125000,"2026-10-28");
            Store.Item smaller=new Store.Item();smaller.name="Tehnička podrška";smaller.qtyMilli=1000;smaller.unitCents=5000;smaller.taxRate=25;
            long second=store.createDraft(Store.INVOICE,store.clients().get(1).id,"2026-10-28","2026-11-12","Primjer",Arrays.asList(smaller));
            store.confirm(second);
        }
        if(store.documents(Store.QUOTE).isEmpty()){
            Store.Item item=new Store.Item();item.name="Izrada web stranice";item.qtyMilli=1000;item.unitCents=80000;item.taxRate=25;
            long id=store.createDraft(Store.QUOTE,store.clients().get(0).id,"2026-10-28","2026-11-28","Primjer ponude",Arrays.asList(item));
            store.confirm(id);
        }
    }
    @SuppressWarnings("unchecked")
    public static void show(MainActivity a,String screen)throws Exception{
        Store db=(Store)get(a,"db");
        seed(db);
        RecoveryVault.clearWorkingDraft(a);
        String page=screen==null?"04":screen;
        switch(page){
            case "01":case "02":case "03":call(a,"welcome");break;
            case "04":call(a,"home");break;
            case "05":call(a,"documents",new Class<?>[]{String.class},Store.INVOICE);break;
            case "06":call(a,"newDocument",new Class<?>[]{String.class},Store.INVOICE);break;
            case "07":
                call(a,"newDocument",new Class<?>[]{String.class},Store.INVOICE);
                ArrayList<Store.Item> lines=(ArrayList<Store.Item>)get(a,"draftItems");
                Store.Item item=new Store.Item();item.name="Dizajn web stranice";item.qtyMilli=1000;item.unitCents=100000;item.taxRate=25;
                lines.add(item);
                call(a,"editItemsScreen");break;
            case "08":call(a,"showDocument",new Class<?>[]{long.class},db.documents(Store.INVOICE).get(0).id);break;
            case "09":call(a,"documents",new Class<?>[]{String.class},Store.QUOTE);break;
            case "10":call(a,"newDocument",new Class<?>[]{String.class},Store.QUOTE);break;
            case "11":call(a,"showDocument",new Class<?>[]{long.class},db.documents(Store.QUOTE).get(0).id);break;
            case "12":call(a,"clients");break;
            case "13":call(a,"clientDetails",new Class<?>[]{Store.Client.class},db.clients().get(0));break;
            case "14":call(a,"catalog");break;
            case "15":call(a,"reports");break;
            case "16":call(a,"payments");break;
            case "17":call(a,"eracun");break;
            case "18":call(a,"settings");break;
            case "19":call(a,"company");break;
            case "20":set(a,"dark",true);call(a,"home");break;
            default:throw new IllegalArgumentException("Unknown preview page: "+page);
        }
    }
}
