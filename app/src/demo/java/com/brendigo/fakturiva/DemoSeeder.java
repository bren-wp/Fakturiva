package com.brendigo.fakturiva;

import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Only compiled into com.brendigo.fakturiva.demo; NEVER production. */
public final class DemoSeeder {
    public static final String USERNAME="demo@fakturiva.test";
    public static final String PIN="123456";
    private DemoSeeder(){}
    public static void seed(Context context) throws Exception{
        if(LocalAccounts.hasAccounts(context))return;
        char[] secret=PIN.toCharArray();
        Store db=null;
        try{
            String id=LocalAccounts.register(context,USERNAME,secret);
            LocalAccounts.unlock(context,id);
            db=new Store(context,id);
            db.companySettings(
                new String[]{"name","country","legal_form","address","city","currency","email"},
                new String[]{"Fakturiva DEMO","OTHER","OTHER",
                    "Primjer adrese 1","Demograd","EUR","demo@fakturiva.test"});
            long c1=db.saveClient("Ogledni kupac d.o.o.","",
                "Primjer ulica 2","kupac@example.test");
            long c2=db.saveClient("Ogledni klijent","",
                "Primjer cesta 3","drugi@example.test");
            Store.Item service=new Store.Item();
            service.name="Izrada web stranice";service.qtyMilli=1000;
            service.unitCents=120000;service.taxRate=0;
            Store.Item service2=new Store.Item();
            service2.name="Tehnička podrška";service2.qtyMilli=2000;
            service2.unitCents=3500;service2.taxRate=0;
            long invoice=db.createDraft(Store.INVOICE,c1,
                "2026-06-05","2026-06-20","2026-06-05",
                "Ogledni dokument bez stvarne fiskalizacije",
                Arrays.asList(service,service2));
            db.confirm(invoice);
            db.pay(invoice,127000,"2026-06-07","BANK");
            db.createDraft(Store.INVOICE,c2,"2026-07-01",
                "2026-07-20","2026-07-01","Ogledni nacrt",List.of(service2));
            long quote=db.createDraft(Store.QUOTE,c2,
                "2026-07-10","2026-07-31","2026-07-10",
                "Ogledna ponuda","Izrada web aplikacije",
                List.of(service,service2));
            db.confirm(quote);
        }finally{
            if(db!=null)db.close();
            LocalAccounts.lock();
            Arrays.fill(secret,'\0');
        }
    }
}
