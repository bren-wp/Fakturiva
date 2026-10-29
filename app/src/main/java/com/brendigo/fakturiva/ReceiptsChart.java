package com.brendigo.fakturiva;

import java.util.List;

/** Twelve chart points from real, dated SQLite payment records, never mock data. */
public final class ReceiptsChart {
    private ReceiptsChart() {}

    public static long[] cumulative(List<Store.Payment> payments,String yearMonth,int daysInMonth){
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
        for(int index=1;index<buckets.length;index++)
            buckets[index]=Math.addExact(buckets[index-1],buckets[index]);
        return buckets;
    }
}