package com.brendigo.fakturiva;
/** Croatian OIB check digit (ISO 7064 MOD 11,10). */
public final class Oib {
    private Oib(){}
    public static boolean valid(String raw){
        if(raw==null||!raw.matches("[0-9]{11}"))return false;
        int a=10;
        for(int i=0;i<10;i++){
            a=(a+(raw.charAt(i)-'0'))%10;
            if(a==0)a=10;
            a=(a*2)%11;
        }
        return ((11-a)%10)==raw.charAt(10)-'0';
    }
}