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
            store.saveProduct("Dizajn web stranice",100000,25,"SERVICE","kom");
            store.saveProduct("Tehnička podrška",5000,25,"SERVICE","sat");
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
            long id=store.createDraft(Store.QUOTE,store.clients().get(0).id,
                "2026-10-28","2026-11-28","2026-11-15",
                "Primjer ponude","Usluge digitalnog marketinga",Arrays.asList(item));
            store.confirm(id);
        }
    }
    @SuppressWarnings("unchecked")
    public static void show(MainActivity a,String screen)throws Exception{
        Store db=(Store)get(a,"db");
        seed(db);
        RecoveryVault.clearWorkingDraft(a);
        String page=screen==null?"04":screen;
        // All twenty owner's original PNGs are in Croatian. Force the
        // screenshot harness language only for this debug emulator capture;
        // never write a user preference or change production locale.
        set(a,"qaReferenceCroatian",true);
        switch(page){
            case "01":call(a,"splash",new Class<?>[]{boolean.class},false);break;
            case "02":call(a,"welcome");break;
            case "03":
                // Capture the REAL email/password form from the owner's
                // reference, not the unrelated device-security prompt.
                call(a,"loginServerScreen");
                // Only the emulator debug harness can disable screenshot
                // blocking; production credential screens retain FLAG_SECURE.
                a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                break;
            case "04":call(a,"home");break;
            case "05":call(a,"documents",new Class<?>[]{String.class},Store.INVOICE);break;
            case "06":call(a,"newDocument",new Class<?>[]{String.class},Store.INVOICE);break;
            case "07":
                call(a,"newDocument",new Class<?>[]{String.class},Store.INVOICE);
                ArrayList<Store.Item> lines=(ArrayList<Store.Item>)get(a,"draftItems");
                Store.Item item=new Store.Item();item.name="Dizajn web stranice";item.qtyMilli=1000;item.unitCents=100000;item.taxRate=25;
                lines.add(item);
                call(a,"editItemsScreen");break;
            case "08":{
                // Screen 08's reference is an invoice *draft* with two lines.
                // Use a realistic debug-only draft, never a fabricated issued
                // number or a hardcoded screenshot in the release application.
                Store.Item site=new Store.Item();
                site.name="Izrada web stranice";
                site.qtyMilli=1000;site.unitCents=60000;site.taxRate=25;
                Store.Item seo=new Store.Item();
                seo.name="SEO optimizacija";
                seo.qtyMilli=1000;seo.unitCents=25000;seo.taxRate=25;
                long invoiceDraft=db.createDraft(Store.INVOICE,db.clients().get(0).id,
                    "2026-10-27","2026-11-10","Pregled prije potvrde",
                    Arrays.asList(site,seo));
                call(a,"showDocument",new Class<?>[]{long.class},invoiceDraft);
                break;
            }
            case "09":call(a,"documents",new Class<?>[]{String.class},Store.QUOTE);break;
            case "10":
                call(a,"newDocument",new Class<?>[]{String.class},Store.QUOTE);
                // Stable, fictitious values matching the dated design reference,
                // restricted to this debug-only emulator harness.
                set(a,"draftIssue","2026-10-27");
                set(a,"draftDue","2026-11-10");
                set(a,"draftSupply","2026-11-15");
                set(a,"draftQuoteTitle","Usluge digitalnog marketinga");
                call(a,"editDocumentScreen");
                break;
            case "11":call(a,"showDocument",new Class<?>[]{long.class},db.documents(Store.QUOTE).get(0).id);break;
            case "12":call(a,"clients");break;
            case "13":call(a,"clientDetails",new Class<?>[]{Store.Client.class},db.clients().get(0));break;
            case "14":call(a,"catalog");break;
            case "15":
                ((java.util.Calendar)get(a,"reportMonth")).set(2026,java.util.Calendar.OCTOBER,1);
                call(a,"reports");
                break;
            case "16":call(a,"payments");break;
            case "17":call(a,"guide");break;
            case "18":call(a,"settings");break;
            case "19":call(a,"company");break;
            case "20":set(a,"dark",true);call(a,"home");break;
            default:throw new IllegalArgumentException("Unknown preview page: "+page);
        }
        // Calling setContentView is not the same as a framebuffer draw.
        // On Android 15/16 the system SplashScreen may still cover the Activity
        // for several frames, even though the layout methods have finished.
        // Emit the readiness file only AFTER the requested view has actually
        // entered the draw pipeline, and allow the system splash to clear.
        final android.view.View decor=a.getWindow().getDecorView();
        final android.view.ViewTreeObserver tree=decor.getViewTreeObserver();
        tree.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            private boolean scheduled=false;
            @Override public boolean onPreDraw(){
                if(!scheduled){
                    scheduled=true;
                    decor.getViewTreeObserver().removeOnPreDrawListener(this);
                    decor.postDelayed(()->{
                        // Ignore a now-destroyed Activity; never signal that
                        // a stale screen belongs to the next screenshot.
                        if(a.isFinishing()||a.isDestroyed()||!decor.isShown())return;
                        try(java.io.FileOutputStream signal=a.openFileOutput(
                                "qa-screen-ready.txt",
                                android.content.Context.MODE_PRIVATE)){
                            signal.write(page.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
                            signal.flush();
                            Log.i("FakturivaQA","QA_SCREEN_READY "+page);
                        }catch(java.io.IOException e){
                            Log.e("FakturivaQA","QA readiness marker failed "+page,e);
                        }
                    },1000);
                }
                return true;
            }
        });
    }
}
