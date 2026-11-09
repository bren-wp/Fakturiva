package com.brendigo.fakturiva;

import android.graphics.Color;

/** Appearance only: never changes immutable invoice numbers, VAT, or sums. */
public final class InvoiceTheme {
    private InvoiceTheme(){}
    public static final String CLASSIC="CLASSIC",MINIMAL="MINIMAL",
        MODERN="MODERN",FORMAL="FORMAL";
    public static final String[] IDS={CLASSIC,MINIMAL,MODERN,FORMAL};
    public static final String[] NAMES={"Fakturiva Classic","Minimal",
        "Modern","Poslovni Formal"};
    public static String validated(String value){
        if(value==null||value.isEmpty())return CLASSIC;
        for(String id:IDS)if(id.equals(value))return id;
        throw new IllegalArgumentException("Nepoznat izgled računa");
    }
    public static int defaultColor(String kind){
        switch(validated(kind)){
            case MINIMAL:return Color.rgb(31,41,55);
            case MODERN:return Color.rgb(3,110,104);
            case FORMAL:return Color.rgb(17,51,94);
            default:return Color.rgb(85,102,248);
        }
    }
    public static int accent(String hex,String kind){
        if(hex==null||hex.isEmpty())return defaultColor(kind);
        if(!hex.matches("#[0-9A-Fa-f]{6}"))
            throw new IllegalArgumentException("Boja mora biti #RRGGBB.");
        int color=Color.parseColor(hex);
        // Contrast against white paper, not simply perceived brightness.
        if((1.05/(luminance(color)+.05))<4.5)
            throw new IllegalArgumentException(
                "Odabrana boja nema dovoljan kontrast na bijelom papiru.");
        return color;
    }
    private static double linear(int x){
        double v=x/255.0;return v<=.04045?v/12.92:
            Math.pow((v+.055)/1.055,2.4);
    }
    private static double luminance(int c){
        return .2126*linear(Color.red(c))+.7152*linear(Color.green(c))
            +.0722*linear(Color.blue(c));
    }
}
