package com.brendigo.fakturiva;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/** End-to-end tax ledger checks on the actual SQLite schema and Android PDFs. */
public final class CroatianTaxReportsTest extends FakturivaTestCase {
    private static String sampleOib(){
        String stem="0000000000";
        int n=10;
        for(int i=0;i<10;i++){
            n=(n+(stem.charAt(i)-'0'))%10;
            if(n==0)n=10;
            n=(n*2)%11;
        }
        int digit=11-n;if(digit==10)digit=0;
        return stem+digit;
    }
    @Test public void cashBasisPdfAndTaxTotals() throws Exception{
        Context context=getContext();
        String unique=UUID.randomUUID().toString().replace("-","");
        String id=LocalAccounts.register(context,"tax_"+unique.substring(0,16),
            "123456".toCharArray());
        LocalAccounts.unlock(context,id);
        Store store=new Store(context,id);
        try{
            store.setting("country","HR");
            store.setting("name","Primjer obrta");
            store.setting("oib",sampleOib());
            store.setting("hr_tax_owner","Probni obveznik");
            store.setting("hr_tax_residence","Primjer adresa 1");
            store.setting("hr_tax_place","Poslovna jedinica 1");
            store.setting("hr_tax_activity","Računalno programiranje");
            store.setting("hr_tax_nkd","62.10");
            store.setting("business_unit","PP1");
            store.setting("hr_tax_model",CroatianTaxReports.PAUSAL);
            assertTrue(CroatianTaxReports.validOib(sampleOib()));
            SQLiteDatabase sql=store.getWritableDatabase();
            sql.execSQL("INSERT INTO clients(id,name) VALUES(501,'Test klijent')");
            sql.execSQL("INSERT INTO documents(id,type,number,client_id,issue_date,due_date,"+
                "status,net_cents,tax_cents,total_cents,created_at,currency_code,"+
                "issuer_business_unit) VALUES(501,'INVOICE','R-QA-501',501,"+
                "'2026-12-01','2027-01-30','PAID',35000,0,35000,1,'EUR','PP1')");
            sql.execSQL("INSERT INTO lines(document_id,name,qty_milli,unit_cents,"+
                "tax_rate,net_cents,tax_cents) VALUES(501,'Test',1000,35000,0,35000,0)");
            sql.execSQL("INSERT INTO payments(document_id,amount_cents,paid_on,"+
                "channel,business_unit) VALUES(501,10000,'2026-12-30','CASH','PP1')");
            sql.execSQL("INSERT INTO payments(document_id,amount_cents,paid_on,"+
                "channel,business_unit) VALUES(501,20000,'2026-12-31','BANK','PP1')");
            sql.execSQL("INSERT INTO payments(document_id,amount_cents,paid_on,"+
                "channel,business_unit) VALUES(501,5000,'2027-01-04','CARD','PP1')");
            CroatianTaxReports.Book year=CroatianTaxReports.book(store,
                "2026-01-01","2026-12-31","SVE");
            assertEquals(2,year.rows.size());
            assertEquals(10000,year.cash);
            assertEquals(20000,year.noncash);
            assertEquals(30000,year.total);
            assertEquals(20000,CroatianTaxReports.book(store,
                "2026-12-31","2026-12-31","SVE").total);
            CroatianTaxReports.Posd posd=CroatianTaxReports.posd(store,2026,
                "2026-01-01","2026-12-31","0,00");
            assertEquals(12,posd.months);
            assertEquals(20340,posd.assessedTax);
            assertEquals(169500,posd.assessedIncome);
            assertEquals(20340,posd.difference);
            ByteArrayOutputStream kpr=new ByteArrayOutputStream();
            CroatianTaxPdf.writeKpr(year,CroatianTaxReports.business(store),kpr);
            assertTrue(kpr.size()>1000);
            assertEquals("%PDF",new String(kpr.toByteArray(),0,4,"US-ASCII"));
            ByteArrayOutputStream pdf=new ByteArrayOutputStream();
            CroatianTaxPdf.writePosd(posd,pdf);
            assertTrue(pdf.size()>1000);
            assertEquals("%PDF",new String(pdf.toByteArray(),0,4,"US-ASCII"));
            String xml=new String(CroatianTaxReports.workingXml(posd),"UTF-8");
            assertTrue(xml.contains("NIJE-ZA-UVOZ-U-EPOREZNU"));
            assertTrue(xml.contains("<UkupniPrimiciEUR>300.00</UkupniPrimiciEUR>"));
            assertFalse(xml.contains("123456"));
            store.setting("hr_tax_special","yes");
            CroatianTaxReports.Posd special=CroatianTaxReports.posd(store,2026,
                "2026-01-01","2026-12-31","0,00");
            assertEquals(15255,special.reduction);
            assertEquals(5085,special.taxAfterReduction);
            store.setting("hr_tax_model",CroatianTaxReports.DOHODAK);
            CroatianTaxReports.Book cashOnly=CroatianTaxReports.book(store,
                "2026-01-01","2026-12-31","SVE");
            assertEquals(10000,cashOnly.total);
            try{
                CroatianTaxReports.posd(store,2026,
                    "2026-01-01","2026-12-31","0,00");
                fail("Non-pauschal entity must not receive PO-SD");
            }catch(IllegalArgumentException expected){/* correct */}
            store.setting("hr_tax_model",CroatianTaxReports.PAUSAL);
            sql.execSQL("UPDATE payments SET channel='UNKNOWN' "+
                "WHERE paid_on='2026-12-30'");
            try{
                CroatianTaxReports.book(store,"2026-01-01","2026-12-31","SVE");
                fail("Unclassified cash receipts cannot be silently included");
            }catch(IllegalStateException expected){/* correct */}
        }finally{
            store.close();
            LocalAccounts.delete(context,"tax_"+unique.substring(0,16),
                "123456".toCharArray());
            LocalAccounts.lock();
        }
    }
    @Test public void proratingAndDatesRejectIncompleteYears(){
        assertEquals(12,CroatianTaxReports.activityMonths(2026,
            "2026-01-01","2026-12-31"));
        assertEquals(11,CroatianTaxReports.activityMonths(2026,
            "2026-01-12","2026-12-31"));
        assertEquals(1,CroatianTaxReports.activityMonths(2026,
            "2026-11-20","2026-11-30"));
        assertFalse(CroatianTaxReports.validOib("00000000000"));
        try{
            CroatianTaxReports.checkedDate("2026-02-30");
            fail("Impossible dates must fail");
        }catch(IllegalArgumentException expected){/* valid */}
    }
}
