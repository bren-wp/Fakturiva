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
    /**
     * A repeatable sample dataset is essential for meaningful screenshot
     * comparison: comparing a six-row reference with a one-row emulator is
     * mostly a DATA mismatch rather than a geometry mismatch. All records
     * below are fictional and created through production Store APIs on a
     * throwaway emulator, never through mocked views or bundled PNGs.
     */
    private static long fixtureClient(List<Store.Client> all,String name){
        for(Store.Client candidate:all)
            if(name.equals(candidate.name))return candidate.id;
        throw new IllegalStateException("Missing seeded client: "+name);
    }
    private static void seed(Store store){
        if(store.clients().isEmpty()){
            store.setting("name","Fakturiva Demo d.o.o.");
            store.setting("oib","12345678903");
            store.setting("address","Primjer adrese 15, Rijeka");
            store.setting("email","primjer@example.invalid");
            store.setting("iban","HR0000000000000000000");
            String[] clients={"Obrt Primjer","Studio Sjever d.o.o.",
                "Adria Digital d.o.o.","Marin Kovač",
                "Terra Projekt j.d.o.o.","Lana Horvat"};
            for(int i=0;i<clients.length;i++)
                store.saveClient(clients[i],"","Primjer ulica "+(i+1)+", Rijeka",
                    "primjer"+i+"@example.invalid");
            String[] products={"Izrada web stranice","SEO optimizacija",
                "Poslovno savjetovanje","Održavanje weba",
                "Dizajn logotipa","Hosting i podrška"};
            long[] prices={60000,25000,7500,10000,18000,12000};
            String[] units={"kom","sat","sat","mjesec","kom","godina"};
            for(int i=0;i<products.length;i++)
                store.saveProduct(products[i],prices[i],25,"SERVICE",units[i]);
        }
        if(store.documents(Store.INVOICE).isEmpty()){
            List<Store.Client> people=store.clients();
            // Historical receipts make the October 27 dashboard reference
            // reproducible, but only inside a throwaway debug emulator.
            Store.Item previous=new Store.Item();
            previous.name="Usluge prethodnog mjeseca";
            previous.qtyMilli=1000;previous.unitCents=597200;
            previous.taxRate=25;
            long september=store.createDraft(Store.INVOICE,
                fixtureClient(people,"Obrt Primjer"),
                "2026-09-20","2026-09-30","Ranije usluge",
                Arrays.asList(previous));
            store.confirm(september);
            store.pay(september,746500,"2026-09-20");
            // Fifteen earlier October records + eight visible reference rows
            // produce 24 invoices, and confirmed October receipts total
            // €8,420 (rather than hardcoding a KPI into the native UI).
            for(int i=0;i<15;i++){
                long gross=i<14?35000:77000;
                Store.Item prior=new Store.Item();
                prior.name="Usluge listopada";
                prior.qtyMilli=1000;
                prior.unitCents=gross*4/5;
                prior.taxRate=25;
                String paidAt=String.format(java.util.Locale.ROOT,
                    "2026-10-%02d",2+i);
                long id=store.createDraft(Store.INVOICE,
                    fixtureClient(people,"Obrt Primjer"),
                    paidAt,"2026-12-20","Raniji račun",
                    Arrays.asList(prior));
                store.confirm(id);
                store.pay(id,gross,paidAt);
            }
            // Oldest first; Store.documents renders actual database rows newest
            // first, as in the visual reference, without fake invoice numbering.
            String[] clientAt={"Obrt Primjer","Studio Sjever d.o.o.",
                "Studio Sjever d.o.o.","Lana Horvat",
                "Terra Projekt j.d.o.o.","Marin Kovač",
                "Adria Digital d.o.o.","Studio Sjever d.o.o."};
            int[] gross={100000,67000,69000,18000,215000,32000,85000,125000};
            String[] dates={"2026-10-02","2026-10-06","2026-10-09",
                "2026-10-15","2026-10-19","2026-10-21",
                "2026-10-24","2026-10-26"};
            boolean[] paid={true,false,false,true,false,true,false,true};
            for(int i=0;i<gross.length;i++){
                Store.Item line=new Store.Item();
                line.name="Poslovna usluga";
                line.qtyMilli=1000;
                line.unitCents=gross[i]*4L/5L;
                line.taxRate=25;
                String due=i==6?"2026-11-12":"2026-12-20";
                long invoice=store.createDraft(Store.INVOICE,
                    fixtureClient(people,clientAt[i]),dates[i],due,"Primjer dokumenta",
                    Arrays.asList(line));
                store.confirm(invoice);
                if(paid[i])store.pay(invoice,gross[i],dates[i]);
            }
        }
        if(store.documents(Store.QUOTE).isEmpty()){
            List<Store.Client> people=store.clients();
            // Six earlier confirmed quotes plus six visible reference quotes
            // yield 12 active offers worth €7,870, computed by Store.
            for(int i=0;i<6;i++){
                long gross=i<5?25000:35000;
                Store.Item line=new Store.Item();
                line.name="Ponuda usluga";
                line.qtyMilli=1000;line.unitCents=gross*4/5;
                line.taxRate=25;
                String issued=String.format(java.util.Locale.ROOT,
                    "2026-10-%02d",1+i);
                long quote=store.createDraft(Store.QUOTE,
                    fixtureClient(people,"Obrt Primjer"),
                    issued,"2026-11-28","2026-11-15",
                    "Ponuda usluga","",Arrays.asList(line));
                store.confirm(quote);
            }
            String[] clientAt={"Terra Projekt j.d.o.o.","Lana Horvat",
                "Marin Kovač","Adria Digital d.o.o.",
                "Studio Sjever d.o.o.","Terra Projekt j.d.o.o."};
            int[] gross={98000,32000,45000,87000,125000,240000};
            String[] dates={"2026-10-16","2026-10-17","2026-10-18",
                "2026-10-21","2026-10-23","2026-10-25"};
            for(int i=0;i<gross.length;i++){
                Store.Item item=new Store.Item();
                item.name="Izrada web stranice";
                item.qtyMilli=1000;
                item.unitCents=gross[i]*4L/5L;
                item.taxRate=25;
                long quote=store.createDraft(Store.QUOTE,
                    fixtureClient(people,clientAt[i]),dates[i],"2026-11-28",
                    "2026-11-15","Primjer ponude",
                    "Usluge digitalnog marketinga",Arrays.asList(item));
                store.confirm(quote);
                if(i==2||i==4)store.acceptQuote(quote);
            }
        }
    }
    @SuppressWarnings("unchecked")
    public static void show(MainActivity a,String screen)throws Exception{
        Store db=(Store)get(a,"db");
        String page=screen==null?"04":screen;
        // Entry references never require financial fixtures; defer one-time
        // seeding to the first business screen to keep splash/login QA fast.
        if(!"01".equals(page)&&!"02".equals(page)&&!"03".equals(page))
            seed(db);
        RecoveryVault.clearWorkingDraft(a);
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
            case "07":{
                call(a,"newDocument",new Class<?>[]{String.class},Store.INVOICE);
                ArrayList<Store.Item> lines=(ArrayList<Store.Item>)get(a,"draftItems");
                Store.Item web=new Store.Item();
                web.name="Izrada web stranice";
                web.unit="kom";web.qtyMilli=1000;
                web.unitCents=60000;web.taxRate=25;
                lines.add(web);
                Store.Item seo=new Store.Item();
                seo.name="SEO optimizacija";
                seo.unit="kom";seo.qtyMilli=1000;
                seo.unitCents=25000;seo.taxRate=25;
                lines.add(seo);
                call(a,"editItemsScreen");
                break;
            }
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
            case "11":{
                // Actual persisted two-line offer, matching the structure and
                // VAT arithmetic of reference 11 without drawing a mockup.
                Store.Item campaign=new Store.Item();
                campaign.name="Vođenje kampanje";
                campaign.unit="kom";campaign.qtyMilli=1000;
                campaign.unitCents=80000;campaign.taxRate=25;
                Store.Item content=new Store.Item();
                content.name="Izrada sadržaja";
                content.unit="kom";content.qtyMilli=1000;
                content.unitCents=40000;content.taxRate=25;
                long quote=db.createDraft(Store.QUOTE,
                    fixtureClient(db.clients(),"Terra Projekt j.d.o.o."),
                    "2026-10-27","2026-11-10","2026-11-15",
                    "Ponuda za usluge","",Arrays.asList(campaign,content));
                db.confirm(quote);
                call(a,"showDocument",new Class<?>[]{long.class},quote);
                break;
            }
            case "12":call(a,"clients");break;
            case "13":call(a,"clientDetails",new Class<?>[]{Store.Client.class},db.clients().get(0));break;
            case "14":call(a,"catalog");break;
            case "15":
                ((java.util.Calendar)get(a,"reportMonth")).set(2026,java.util.Calendar.OCTOBER,1);
                call(a,"reports");
                break;
            case "16":call(a,"payments");break;
            case "17":call(a,"eracunScreen");break;
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
