package com.brendigo.fakturiva;

import org.junit.Test;
import static org.junit.Assert.*;


import java.util.Arrays;
import java.util.Collections;

/** Active quote cards are computed from local business records, never fixture totals. */
@SuppressWarnings("deprecation")
public final class QuoteOverviewTest extends FakturivaTestCase {
    private Store.Document quote(String type,String status,String validUntil,long cents) {
        Store.Document d=new Store.Document();
        d.type=type; d.status=status; d.dueDate=validUntil;
        d.totalCents=cents; return d;
    }

    @Test
    public void testOnlyValidConfirmedQuotesCountAndSum(){
        long[] totals=MainActivity.activeQuoteTotals(Arrays.asList(
            quote(Store.QUOTE,Store.SENT,"2026-11-15",240000),
            quote(Store.QUOTE,Store.SENT,"2026-11-01",125000),
            quote(Store.QUOTE,Store.SENT,"2026-10-31",87000),
            quote(Store.QUOTE,Store.ACCEPTED,"2026-11-15",40000),
            quote(Store.QUOTE,Store.DRAFT,"2026-12-20",999000),
            quote(Store.QUOTE,Store.VOID,"2026-11-15",900000),
            quote(Store.INVOICE,Store.ISSUED,"2026-11-15",300000)
        ),"2026-11-01");
        assertEquals(3L,totals[0]);
        assertEquals(405000L,totals[1]);
    }

    @Test
    public void testNoQuotesProducesZeroValues(){
        long[] totals=MainActivity.activeQuoteTotals(Collections.emptyList(),"2026-11-01");
        assertEquals(0L,totals[0]);
        assertEquals(0L,totals[1]);
    }
}
