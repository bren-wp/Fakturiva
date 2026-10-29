package com.brendigo.fakturiva;

import android.test.AndroidTestCase;

/** Payment badges match actual status, outstanding balance and due date. */
@SuppressWarnings("deprecation")
public final class DocumentStatusTest extends AndroidTestCase {
    private Store.Document document(String type,String status,long total,long paid,String due){
        Store.Document d=new Store.Document();
        d.type=type;d.status=status;d.totalCents=total;d.paidCents=paid;
        d.dueDate=due;return d;
    }

    private String label(Store.Document d){
        return DocumentStatus.label(d,"2026-10-29","2026-11-05");
    }

    public void testOutstandingAndDueDateClassification(){
        assertEquals("Plaćen",label(document(Store.INVOICE,Store.PAID,10000,10000,"2026-10-01")));
        assertEquals("Dospio",label(document(Store.INVOICE,Store.ISSUED,10000,0,"2026-10-28")));
        assertEquals("Dospijeva",label(document(Store.INVOICE,Store.ISSUED,10000,0,"2026-11-03")));
        assertEquals("Neplaćen",label(document(Store.INVOICE,Store.ISSUED,10000,0,"2026-12-20")));
        assertEquals("Djelomično plaćen",label(document(Store.INVOICE,Store.ISSUED,10000,3000,"2026-10-01")));
    }

    public void testDraftQuoteAndVoidedStatus(){
        assertEquals("Skica",label(document(Store.INVOICE,Store.DRAFT,0,0,"2026-11-02")));
        assertEquals("Poslana",label(document(Store.QUOTE,Store.SENT,5000,0,"2026-12-01")));
        assertEquals("Storniran",label(document(Store.INVOICE,Store.VOID,10000,0,"2026-10-05")));
    }
}