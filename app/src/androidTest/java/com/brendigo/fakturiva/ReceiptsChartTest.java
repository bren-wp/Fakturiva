package com.brendigo.fakturiva;

import android.test.AndroidTestCase;
import java.util.Arrays;
import java.util.Collections;

/** Real-payment chart aggregation must never invent sales or shift months. */
@SuppressWarnings("deprecation")
public final class ReceiptsChartTest extends AndroidTestCase {
    private Store.Payment payment(String date,long cents){
        Store.Payment result=new Store.Payment();
        result.paidOn=date;result.amountCents=cents;return result;
    }
    public void testCumulativeReceiptsKeepTotalsAndDoNotLeakMonths(){
        long[] series=ReceiptsChart.cumulative(Arrays.asList(
            payment("2026-10-01",1000),payment("2026-10-22",5000),
            payment("2026-09-25",999999),payment("2026-10-31",1400)),
            "2026-10",31);
        assertEquals(12,series.length);
        assertEquals(1000L,series[0]);
        assertEquals(7400L,series[11]);
        for(int i=1;i<series.length;i++)assertTrue(series[i]>=series[i-1]);
        assertEquals(0L,ReceiptsChart.cumulative(Collections.emptyList(),"2026-10",31)[11]);
    }
    public void testInvalidMonthsAndPaymentsDoNotChangeChart(){
        long[] series=ReceiptsChart.cumulative(Arrays.asList(
            payment("2026-10-00",600),payment("2026-10-35",700),
            payment("2026-10-20",-50),payment("2026-10-2x",300),
            payment("2026-10-29",2500)),"2026-10",31);
        assertEquals(2500L,series[11]);
        try{ReceiptsChart.cumulative(Collections.emptyList(),"2026-19",31);
            fail("Invalid month was accepted");}
        catch(IllegalArgumentException expected){}
    }
}