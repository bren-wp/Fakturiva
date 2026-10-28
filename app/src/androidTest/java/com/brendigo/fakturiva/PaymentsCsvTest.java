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
        assertEquals("\"' - if prefixed spaces are not formula\"",PaymentsCsv.cell("' - if prefixed spaces are not formula"));
    }
}
