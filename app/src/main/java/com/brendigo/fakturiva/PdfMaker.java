package com.brendigo.fakturiva;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** A native, offline PDF renderer. This is NOT e-invoicing or fiscalization. */
public final class PdfMaker {
    private static final int NAVY=Color.rgb(20,38,63), BLUE=Color.rgb(85,102,248);
    private static final int W=595,H=842,M=42;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PdfDocument pdf=new PdfDocument();
    private Canvas canvas; private int y, page;
    private void txt(String s,float x,float top,int size,int color,boolean bold){
        p.setColor(color);p.setTextSize(size);p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        canvas.drawText(s==null?"":s,x,top,p);
    }
    private void right(String s,float x,float top,int size,int color,boolean bold){
        p.setTextSize(size);p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        txt(s,x-p.measureText(s),top,size,color,bold);
    }
    private void line(int top){
        p.setColor(Color.rgb(226,231,240));p.setStrokeWidth(1);
        canvas.drawLine(M,top,W-M,top,p);
    }
    private void next(){
        if(page>0) pdf.finishPage(active);
        page++; active=pdf.startPage(new PdfDocument.PageInfo.Builder(W,H,page).create());
        canvas=active.getCanvas();canvas.drawColor(Color.WHITE);y=44;
        txt("Fakturiva",M,y,22,NAVY,true);right("Računi i ponude, jednostavno.",W-M,y,9,BLUE,false);y+=26;line(y);y+=25;
    }
    private PdfDocument.Page active;
    private void room(int h){if(y+h>H-70)next();}
    public static void write(Store db,Store.Document doc,OutputStream output) throws IOException{
        PdfMaker m=new PdfMaker();
        try {m.render(db,doc);m.pdf.writeTo(output);}
        finally {m.pdf.close();}
    }
    private void render(Store db,Store.Document d){
        next();
        txt(Store.INVOICE.equals(d.type)?"RAČUN":"PONUDA",M,y,18,NAVY,true);
        right(d.number==null?"NACRT #"+d.id:d.number,W-M,y,13,BLUE,true);y+=23;
        txt(d.status.equals(Store.DRAFT)?"NACRT — nije potvrđen dokument":"Status: "+status(d.status),M,y,10,Color.DKGRAY,false);y+=27;
        txt("IZDAVATELJ",M,y,10,BLUE,true);txt("KLIJENT",312,y,10,BLUE,true);y+=20;
        txt(cut(d.issuerName,34),M,y,13,NAVY,true);txt(cut(d.clientName,29),312,y,13,NAVY,true);y+=18;
        txt(cut(d.issuerAddress,40),M,y,10,NAVY,false);txt(cut(d.clientAddress,35),312,y,10,NAVY,false);y+=17;
        txt("OIB: "+cut(d.issuerOib,25),M,y,10,NAVY,false);txt("OIB: "+cut(d.clientOib,24),312,y,10,NAVY,false);y+=17;
        txt(cut(d.issuerEmail,42),M,y,10,NAVY,false);y+=25;
        line(y);y+=21;
        txt("Datum dokumenta: "+d.issueDate,M,y,11,NAVY,false);
        txt("Dospijeće / vrijedi do: "+d.dueDate,312,y,11,NAVY,false);y+=30;
        txt("OPIS STAVKE",M,y,10,BLUE,true);
        right("KOL.",342,y,10,BLUE,true);
        right("CIJENA",422,y,10,BLUE,true);
        right("PDV",468,y,10,BLUE,true);
        right("UKUPNO",W-M,y,10,BLUE,true);y+=12;line(y);y+=18;
        for(Store.Item item:d.items){
            room(36);
            txt(cut(item.name,35),M,y,10,NAVY,false);
            right(Money.decimalQuantity(item.qtyMilli),342,y,10,NAVY,false);
            right(Money.euros(item.unitCents),422,y,10,NAVY,false);
            right(item.taxRate+"%",468,y,10,NAVY,false);
            right(Money.euros(item.net()+item.tax()),W-M,y,10,NAVY,true);y+=26;line(y);y+=17;
        }
        room(160);
        right("Osnovica:",448,y,11,NAVY,false);right(Money.euros(d.netCents),W-M,y,11,NAVY,false);y+=22;
        right("PDV:",448,y,11,NAVY,false);right(Money.euros(d.taxCents),W-M,y,11,NAVY,false);y+=25;
        right("UKUPNO:",448,y,13,NAVY,true);right(Money.euros(d.totalCents),W-M,y,13,BLUE,true);y+=26;
        if(Store.INVOICE.equals(d.type)){
            right("Uplaćeno:",448,y,10,NAVY,false);right(Money.euros(d.paidCents),W-M,y,10,NAVY,false);y+=20;
        }
        room(100);y+=16;
        txt("IBAN: "+cut(d.issuerIban,45),M,y,10,NAVY,false);y+=19;
        if(!d.notes.trim().isEmpty()){txt("Napomena: "+cut(d.notes,76),M,y,10,NAVY,false);y+=23;}
        room(88);
        line(y);y+=17;
        txt("Izrađeno lokalno aplikacijom Fakturiva.",M,y,10,Color.DKGRAY,false);y+=16;
        if(Store.INVOICE.equals(d.type)){
            txt("PDF nije strukturirani eRačun niti dokaz fiskalizacije.",M,y,9,Color.rgb(170,74,21),true);
        }
        pdf.finishPage(active);active=null;
    }
    private static String status(String s){switch(s){case Store.PAID:return "Plaćeno";case Store.ISSUED:return "Potvrđeno";case Store.SENT:return "Potvrđena ponuda";default:return s;}}
    private static String cut(String s,int max){return s==null?"":s.length()>max?s.substring(0,max-1)+"…":s;}
}
