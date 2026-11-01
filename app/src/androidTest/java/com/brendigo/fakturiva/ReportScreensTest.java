package com.brendigo.fakturiva;

import android.test.AndroidTestCase;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Report screen must calculate all month figures from actual records. */
@SuppressWarnings("deprecation")
public final class ReportScreensTest extends AndroidTestCase{
    private Store.Document invoice(String type,String status,String issue,long total,long paid){
        Store.Document d=new Store.Document();
        d.type=type;d.status=status;d.issueDate=issue;
        d.totalCents=total;d.paidCents=paid;
        return d;
    }
    private Store.Payment payment(String date,long cents){
        Store.Payment p=new Store.Payment();
        p.paidOn=date;p.amountCents=cents;return p;
    }
    public void testMonthlySummaryDoesNotMixInvoicesAndPayments(){
        long[] report=MainActivity.reportMetrics(Arrays.asList(
            invoice(Store.INVOICE,Store.ISSUED,"2026-10-13",12500,0),
            invoice(Store.INVOICE,Store.PAID,"2026-10-14",25000,25000),
            invoice(Store.INVOICE,Store.ISSUED,"2026-10-15",30000,10000),
            invoice(Store.INVOICE,Store.DRAFT,"2026-10-17",99000,0),
            invoice(Store.INVOICE,Store.VOID,"2026-10-18",8000,0),
            invoice(Store.INVOICE,Store.PAID,"2026-11-01",40000,40000),
            invoice(Store.QUOTE,Store.SENT,"2026-10-15",900000,0)
        ),Arrays.asList(
            payment("2026-10-14",25000),
            payment("2026-10-22",10000),
            payment("2026-11-01",40000)
        ),"2026-10");
        assertEquals(67500L,report[0]);
        assertEquals(3L,report[1]);
        assertEquals(1L,report[2]);
        assertEquals(35000L,report[3]);
        assertEquals(32500L,report[4]);
    }

    public void testCsvIsRealTypedDataAndRejectsInvalidMonth()throws Exception{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        ReportCsv.write("2026-10",new long[]{67500,3,1,35000,32500},out);
        String csv=new String(out.toByteArray(),StandardCharsets.UTF_8);
        assertTrue(csv.contains("Fakturirano_EUR_centi"));
        assertTrue(csv.contains("2026-10;67500;3;1;35000;32500"));
        try{
            ReportCsv.write("2026-99",new long[]{1,1,1,1,1},new ByteArrayOutputStream());
            fail("Invalid month");
        }catch(IllegalArgumentException expected){}
    }
}
