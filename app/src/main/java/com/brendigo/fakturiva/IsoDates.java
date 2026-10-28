package com.brendigo.fakturiva;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
/** Strict ISO-8601 dates supported on Android API 24+. */
public final class IsoDates {
    private IsoDates(){}
    public static void validate(String issued,String due){
        if(!valid(issued)||!valid(due))throw new IllegalArgumentException("Datum nije valjan (GGGG-MM-DD)");
        if(due.compareTo(issued)<0)throw new IllegalArgumentException("Dospijeće ne može biti prije datuma dokumenta");
    }
    public static boolean valid(String value){
        if(value==null||!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return false;
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);f.setLenient(false);
        ParsePosition p=new ParsePosition(0);
        return f.parse(value,p)!=null&&p.getIndex()==value.length();
    }
}