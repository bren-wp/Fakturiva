package com.brendigo.fakturiva;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Human-readable document state based on persisted payments and actual due dates. */
public final class DocumentStatus {
    private DocumentStatus() {}

    public static String today(){
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
            .format(Calendar.getInstance().getTime());
    }

    public static String inSevenDays(){
        Calendar calendar=Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH,7);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(calendar.getTime());
    }

    public static String label(Store.Document document,String today,String upcomingLimit){
        if(Store.VOID.equals(document.status))return "Storniran";
        if(Store.DRAFT.equals(document.status))return "Skica";
        if(Store.QUOTE.equals(document.type)){
            return Store.SENT.equals(document.status)?"Potvrđena":"Potvrđena";
        }
        if(Store.PAID.equals(document.status)||document.outstanding()==0)
            return "Plaćen";
        if(document.paidCents>0)return "Djelomično plaćen";
        String due=document.dueDate;
        if(due!=null&&due.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")){
            if(due.compareTo(today)<0)return "Dospio";
            if(due.compareTo(upcomingLimit)<=0)return "Dospijeva";
        }
        return "Neplaćen";
    }
}