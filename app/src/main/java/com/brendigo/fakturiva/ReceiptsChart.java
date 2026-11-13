package com.brendigo.fakturiva;

import java.util.List;

/** Twelve chart points from real, dated SQLite payment records, never mock data. */
public final class ReceiptsChart {
    private ReceiptsChart() {}

    /**
     * Actual non-cumulative receipts in twelve chronological, mutually
     * exclusive day ranges. A daily-trend sparkline may rise and fall without
     * inventing revenue or presenting a monotonic cumulative curve as activity.
     */
    public static long[] intervals(List<Store.Payment> payments,String yearMonth,int daysInMonth){

        if(yearMonth==null || !yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])")
                || daysInMonth<28 || daysInMonth>31)
            throw new IllegalArgumentException("Neispravni podaci za graf naplate");
        long[] buckets=new long[12];
        for(Store.Payment payment:payments){
            if(payment.paidOn==null || !payment.paidOn.startsWith(yearMonth+"-"))
                continue;
            if(payment.paidOn.length()!=10 || payment.amountCents<=0)continue;
            int day;
            try{day=Integer.parseInt(payment.paidOn.substring(8,10));}
            catch(NumberFormatException error){continue;}
            if(day<1 || day>daysInMonth)continue;
            int bucket=Math.min(11,(day-1)*12/daysInMonth);
            buckets[bucket]=Math.addExact(buckets[bucket],payment.amountCents);
        }
        return buckets;
    }

    /** Cumulative paid amounts for the same twelve intervals (backwards-compatible). */
    public static long[] cumulative(List<Store.Payment> payments,String yearMonth,int daysInMonth){
        long[] buckets=intervals(payments,yearMonth,daysInMonth);
        for(int index=1;index<buckets.length;index++)
            buckets[index]=Math.addExact(buckets[index-1],buckets[index]);
        return buckets;
    }
}