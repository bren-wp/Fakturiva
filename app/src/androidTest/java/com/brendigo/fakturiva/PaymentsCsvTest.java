package com.brendigo.fakturiva;

import android.test.AndroidTestCase;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Verifies locally exported payment rows are lossless and safe for spreadsheets. */
@SuppressWarnings("deprecation")
public final class PaymentsCsvTest extends AndroidTestCase {
    public void testCsvContainsRealAmountsAndEscapedNames() throws Exception{
        Store.Payment row=new Store.Payment();
        row.invoiceNumber="R-2026-0002";
        row.clientName="Kupac \"Primjer\"; =2+2";
        row.amountCents=123456;
        row.paidOn="2026-10-29";
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        PaymentsCsv.write(Arrays.asList(row),out);
        byte[] raw=out.toByteArray();
        assertEquals((byte)0xef,raw[0]);
        assertEquals((byte)0xbb,raw[1]);
        assertEquals((byte)0xbf,raw[2]);
        String csv=new String(raw,StandardCharsets.UTF_8);
        assertTrue(csv.contains("R-2026-0002"));
        assertTrue(csv.contains("1234,56"));
        assertTrue(csv.contains("Kupac \"\"Primjer\"\"; =2+2"));
        assertTrue(csv.contains("2026-10-29"));
    }
    public void testSpreadsheetFormulaInjectionIsEscaped() {
        assertEquals("\"'=1+1\"",PaymentsCsv.cell("=1+1"));
        assertEquals("\"'@SUM(1)\"",PaymentsCsv.cell("@SUM(1)"));
        assertEquals("\"'  =2+2\"",PaymentsCsv.cell("  =2+2"));
        assertEquals("\"'\t+2+2\"",PaymentsCsv.cell("\t+2+2"));
        assertEquals("\"plain text\"",PaymentsCsv.cell("plain text"));
    }
    public void testSelectedMonthExportContainsOnlyVisiblePayments()throws Exception{
        Store.Payment october=new Store.Payment();
        october.invoiceNumber="R-10";october.clientName="Listopadski kupac";
        october.paidOn="2026-10-15";october.amountCents=12500;
        Store.Payment september=new Store.Payment();
        september.invoiceNumber="R-09";september.clientName="Rujanski kupac";
        september.paidOn="2026-09-28";september.amountCents=3300;
        java.util.List<Store.Payment> payments=Arrays.asList(october,september);
        assertEquals(2,PaymentsCsv.forMonth(payments,null).size());
        assertEquals(1,PaymentsCsv.forMonth(payments,"2026-10").size());
        assertEquals("R-10",PaymentsCsv.forMonth(payments,"2026-10").get(0).invoiceNumber);
        assertEquals(0,PaymentsCsv.forMonth(payments,"2026-11").size());
        try{PaymentsCsv.forMonth(payments,"2026-19");fail("Invalid month");}
        catch(IllegalArgumentException expected){}
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        PaymentsCsv.write(PaymentsCsv.forMonth(payments,"2026-10"),output);
        String csv=new String(output.toByteArray(),StandardCharsets.UTF_8);
        assertTrue(csv.contains("Listopadski kupac"));
        assertFalse(csv.contains("Rujanski kupac"));
    }

}
