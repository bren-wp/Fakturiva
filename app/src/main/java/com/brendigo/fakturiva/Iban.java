package com.brendigo.fakturiva;

import java.util.Locale;

/** Checks IBAN structure and ISO 13616 mod-97 checksum, without any network services. */
public final class Iban {
    private Iban(){}
    public static String normalize(String value) {
        return value==null?"":value.replaceAll("\\s+","").toUpperCase(Locale.ROOT);
    }
    public static boolean valid(String value){
        String iban=normalize(value);
        if(iban.length()<15||iban.length()>34||!iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+"))return false;
        // Croatian IBAN has exactly 21 characters; other IBANs may be valid for foreign businesses.
        if(iban.startsWith("HR")&&iban.length()!=21)return false;
        int remainder=0;
        String shifted=iban.substring(4)+iban.substring(0,4);
        for(int i=0;i<shifted.length();i++){
            char c=shifted.charAt(i);
            String digits=Character.isDigit(c)?String.valueOf(c):String.valueOf(c-'A'+10);
            for(int j=0;j<digits.length();j++)remainder=(remainder*10+digits.charAt(j)-'0')%97;
        }
        return remainder==1;
    }
}