package com.brendigo.fakturiva;

import java.util.List;
import java.util.Locale;

/**
 * Stateless presentation-domain rules shared by list, client and form screens.
 * Values are derived only from persisted data; no Android UI or preferences.
 * Existing MainActivity entry points delegate here for binary/test compatibility.
 */
final class UiBusinessRules {
    private UiBusinessRules(){}

    static long[] activeQuoteTotals(List<Store.Document> documents,String today){
        long count=0,value=0;
        for(Store.Document quote:documents){
            if(Store.QUOTE.equals(quote.type) &&
                (Store.SENT.equals(quote.status)||Store.ACCEPTED.equals(quote.status))
                && quote.dueDate!=null && quote.dueDate.compareTo(today)>=0){
                count=Math.addExact(count,1);
                value=Math.addExact(value,quote.totalCents);
            }
        }
        return new long[]{count,value};
    }


    static boolean matches(Store.Document d,String f){
        if("Skice".equals(f))return Store.DRAFT.equals(d.status);
        if("Plaćeni".equals(f))
            return Store.INVOICE.equals(d.type) && !Store.DRAFT.equals(d.status)
                && !Store.VOID.equals(d.status) &&
                (Store.PAID.equals(d.status) || d.outstanding()==0);
        if("Neplaćeni".equals(f))
            return Store.INVOICE.equals(d.type) && !Store.DRAFT.equals(d.status)
                && !Store.VOID.equals(d.status) && d.outstanding()>0;
        if("Poslane".equals(f))return Store.QUOTE.equals(d.type) && Store.SENT.equals(d.status);
        if("Prihvaćene".equals(f))return Store.QUOTE.equals(d.type) && Store.ACCEPTED.equals(d.status);
        if("Potvrđene".equals(f))return Store.SENT.equals(d.status);
        return true;
    }


    static String quoteDateForDisplay(String iso){
        if(iso==null||!IsoDates.valid(iso))return iso==null?"":iso;
        return Integer.parseInt(iso.substring(8,10))+". "+
            Integer.parseInt(iso.substring(5,7))+". "+iso.substring(0,4)+".";
    }


    static String quoteDateToIso(String display){
        if(display==null)return "";
        String value=display.trim();
        java.util.regex.Matcher match=java.util.regex.Pattern.compile(
            "^(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})\\.?$"
        ).matcher(value);
        if(match.matches()){
            return String.format(Locale.ROOT,"%04d-%02d-%02d",
                Integer.parseInt(match.group(3)),Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(1)));
        }
        return value;
    }


    static boolean clientMatches(Store.Client c,String search,String view,boolean hasDocs){
        String key=(c.name+" "+c.oib+" "+c.address+" "+c.email+" "+c.phone)
            .toLowerCase(Locale.forLanguageTag("hr-HR"));
        String term=search==null?"":search.trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
        return key.contains(term) &&
            ("Svi".equals(view) || ("S dokumentima".equals(view)&&hasDocs) ||
                ("Bez dokumenata".equals(view)&&!hasDocs));
    }


    static String clientInitials(String name){
        if(name==null||name.trim().isEmpty())return "K";
        String[] parts=name.trim().split("\\s+");
        StringBuilder result=new StringBuilder();
        for(String part:parts){
            if(part.isEmpty())continue;
            int cp=part.codePointAt(0);
            result.appendCodePoint(Character.toUpperCase(cp));
            if(result.codePointCount(0,result.length())>=2)break;
        }
        return result.toString();
    }


    static String clientCity(String address){
        if(address==null||address.trim().isEmpty())return "";
        String[] parts=address.split(",");
        // A street address without a city separator is not a known locality.
        if(parts.length==1)
            return address.matches(".*[0-9].*")?"":address.trim();
        String last=parts[parts.length-1].trim();
        if(parts.length>1&&("Hrvatska".equalsIgnoreCase(last)||
            "Croatia".equalsIgnoreCase(last)))
            return parts[parts.length-2].trim();
        return last;
    }


    static boolean catalogMatches(Store.Item item,String search,String category){
        String text=(item.name+" "+item.unit+" "+item.kind)
            .toLowerCase(Locale.forLanguageTag("hr-HR"));
        String term=search==null?"":search.trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
        boolean kind="Sve".equals(category) ||
            ("Usluge".equals(category)&&"SERVICE".equals(item.kind)) ||
            ("Proizvodi".equals(category)&&"PRODUCT".equals(item.kind)) ||
            ("Nerazvrstano".equals(category)&&"UNSPECIFIED".equals(item.kind));
        return kind&&text.contains(term);
    }

}
