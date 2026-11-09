package com.brendigo.fakturiva;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Supported monetary units with two decimal places. The existing database
 * stores exact 1/100 units; currencies with 0 or 3 fraction digits must not
 * be offered until the line-item arithmetic and migrations support them.
 * Changing the user's default never changes a saved document's currency.
 */
public final class BillingCurrency {
    public static final String DEFAULT="EUR";
    private static final String[] CODES={
        "EUR","USD","GBP","CHF","CAD","AUD","NZD","SGD","HKD",
        "CNY","INR","BRL","MXN","ZAR","PLN","CZK","SEK","NOK",
        "DKK","RON","HUF","BGN","TRY","ILS","THB","MYR",
        "IDR","PHP","TWD","KRW"
    };
    private BillingCurrency(){}
    private static final Map<String,String> SUPPORTED=new LinkedHashMap<>();
    static {
        for(String code:CODES){
            try {
                Currency c=Currency.getInstance(code);
                if(c.getDefaultFractionDigits()==2)SUPPORTED.put(code,c.getDisplayName(Locale.ENGLISH));
            }catch(IllegalArgumentException ignored){}
        }
        if(!SUPPORTED.containsKey(DEFAULT))throw new IllegalStateException("EUR unavailable");
    }
    public static String canonical(String code){
        if(code==null)return DEFAULT;
        String upper=code.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED.containsKey(upper)?upper:DEFAULT;
    }
    public static boolean isSupported(String code){
        return code!=null&&SUPPORTED.containsKey(code.trim().toUpperCase(Locale.ROOT));
    }
    public static String[] options(){
        String[] options=new String[SUPPORTED.size()];
        int i=0;
        for(Map.Entry<String,String> item:SUPPORTED.entrySet())
            options[i++]=item.getKey()+" — "+item.getValue();
        return options;
    }
    public static String[] codes(){return SUPPORTED.keySet().toArray(new String[0]);}
    public static String format(long units,String currency,Locale locale){
        String code=canonical(currency);
        NumberFormat number=NumberFormat.getNumberInstance(locale==null?Locale.ENGLISH:locale);
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        // Croatian reference screens use the recognized euro sign, not the
        // ISO code. Keep codes for other currencies and locales so financial
        // documents remain unambiguous across international installations.
        String suffix=("EUR".equals(code) && locale!=null &&
            "hr".equals(locale.getLanguage()))?"€":code;
        return number.format(BigDecimal.valueOf(units,2))+" "+suffix;
    }
}
