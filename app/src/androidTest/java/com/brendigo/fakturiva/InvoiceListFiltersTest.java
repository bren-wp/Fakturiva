package com.brendigo.fakturiva;

import org.junit.Test;
import static org.junit.Assert.*;



/** UI filter regression tests for the real invoice balances in screen 05. */
@SuppressWarnings("deprecation")
public final class InvoiceListFiltersTest extends FakturivaTestCase {
    private static Store.Document doc(String type,String status,long total,long paid){
        Store.Document d=new Store.Document();
        d.type=type;
        d.status=status;
        d.totalCents=total;
        d.paidCents=paid;
        return d;
    }
    @Test
    public void testFullyPaidInvoiceWithIssuedFlagAppearsPaid(){
        Store.Document d=doc(Store.INVOICE,Store.ISSUED,12500,12500);
        assertTrue(MainActivity.matches(d,"Plaćeni"));
        assertFalse(MainActivity.matches(d,"Neplaćeni"));
    }
    @Test
    public void testPartialPaymentRemainsInUnpaid(){
        Store.Document d=doc(Store.INVOICE,Store.ISSUED,12500,5000);
        assertTrue(MainActivity.matches(d,"Neplaćeni"));
        assertFalse(MainActivity.matches(d,"Plaćeni"));
    }
    @Test
    public void testVoidedAndDraftInvoicesExcludedFromOpenBalance(){
        for(String status:new String[]{Store.VOID,Store.DRAFT}){
            Store.Document d=doc(Store.INVOICE,status,12500,0);
            assertFalse(MainActivity.matches(d,"Neplaćeni"));
            assertFalse(MainActivity.matches(d,"Plaćeni"));
        }
    }
    @Test
    public void testQuotesNeverAppearInInvoicePaymentFilters(){
        Store.Document quote=doc(Store.QUOTE,Store.SENT,12500,0);
        assertTrue(MainActivity.matches(quote,"Poslane"));
        assertFalse(MainActivity.matches(quote,"Prihvaćene"));
        quote.status=Store.ACCEPTED;
        assertFalse(MainActivity.matches(quote,"Poslane"));
        assertTrue(MainActivity.matches(quote,"Prihvaćene"));
        assertFalse(MainActivity.matches(quote,"Neplaćeni"));
        assertFalse(MainActivity.matches(quote,"Plaćeni"));
    }
}
