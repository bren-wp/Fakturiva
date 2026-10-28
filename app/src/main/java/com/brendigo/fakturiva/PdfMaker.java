package com.brendigo.fakturiva;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Full-field, multi-page A4 layout using only Android PdfDocument.
 * Every legal/business field wraps rather than being silently truncated.
 * This is not an eInvoice, fiscalized receipt or certificate of compliance.
 */
public final class PdfMaker {
    private static final int W=595,H=842,M=41,RIGHT=W-M,CONTENT_BOTTOM=758;
    private static final int NAVY=Color.rgb(20,38,63), BLUE=Color.rgb(85,102,248);
    private static final int GRAY=Color.rgb(95,111,134), RULE=Color.rgb(224,232,244);
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final PdfDocument document=new PdfDocument();
    private PdfDocument.Page current;
    private Canvas canvas;
    private int y,pageNumber=0;

    private static boolean has(String s){return s!=null&&!s.trim().isEmpty();}
    private static String safe(String s){return s==null?"":s.trim();}
    private String font(boolean bold,int size) {return bold?"sans-serif-medium":"sans-serif";}

    private void useFont(int size,int color,boolean bold){
        paint.setTextSize(size);paint.setColor(color);
        paint.setTypeface(Typeface.create(font(bold,size),bold?Typeface.BOLD:Typeface.NORMAL));
        paint.setStyle(Paint.Style.FILL);
    }
    private void text(String value,float x,float baseline,int size,int color,boolean bold){
        useFont(size,color,bold);
        canvas.drawText(safe(value),x,baseline,paint);
    }
    private void right(String value,float x,float baseline,int size,int color,boolean bold){
        useFont(size,color,bold);
        String s=safe(value);
        canvas.drawText(s,x-paint.measureText(s),baseline,paint);
    }
    private void rule(){
        paint.setColor(RULE);paint.setStrokeWidth(0.7f);
        canvas.drawLine(M,y,RIGHT,y,paint);
        y+=12;
    }
    private void footer(){
        paint.setColor(RULE);paint.setStrokeWidth(0.7f);
        canvas.drawLine(M,H-60,RIGHT,H-60,paint);
        text("Fakturiva · lokalno izrađen PDF · nije eRačun ni dokaz fiskalizacije",M,H-44,8,GRAY,false);
        right("Str. "+pageNumber,RIGHT,H-44,8,GRAY,false);
    }
    private void newPage(){
        if(current!=null){footer();document.finishPage(current);}
        current=document.startPage(new PdfDocument.PageInfo.Builder(W,H,++pageNumber).create());
        canvas=current.getCanvas();canvas.drawColor(Color.WHITE);
        y=43;
        text("Fakturiva",M,y,20,NAVY,true);
        right("Računi i ponude, jednostavno.",RIGHT,y,9,BLUE,false);
        y+=18;rule();y+=6;
    }
    private void requireSpace(int height){
        if(y+height>CONTENT_BOTTOM)newPage();
    }
    private List<String> wrap(String raw,int width,int size,boolean bold){
        useFont(size,NAVY,bold);
        String normalized=safe(raw).replaceAll("\\s+"," ");
        ArrayList<String> lines=new ArrayList<>();
        if(normalized.isEmpty())return lines;
        StringBuilder line=new StringBuilder();
        for(String word:normalized.split(" ")){
            if(word.isEmpty())continue;
            String attempt=line.length()==0?word:line+" "+word;
            if(paint.measureText(attempt)<=width){
                line.setLength(0);line.append(attempt);continue;
            }
            if(line.length()>0){lines.add(line.toString());line.setLength(0);}
            // Long unbroken identifiers must also be preserved in full.
            StringBuilder piece=new StringBuilder();
            for(int i=0;i<word.length();i++){
                String next=piece.toString()+word.charAt(i);
                if(piece.length()>0&&paint.measureText(next)>width){
                    lines.add(piece.toString());piece.setLength(0);
                }
                piece.append(word.charAt(i));
            }
            line.append(piece);
        }
        if(line.length()>0)lines.add(line.toString());
        return lines;
    }
    private void block(String content,int width,int size,int color,boolean bold){
        for(String line:wrap(content,width,size,bold)){
            requireSpace(size+7);
            text(line,M,y,size,color,bold);
            y+=size+4;
        }
    }
    private void field(String name,String value){
        if(!has(value))return;
        block(name+": "+value,RIGHT-M,10,NAVY,false);
        y+=3;
    }
    private void paired(String first,String second){
        List<String> a=wrap(first,240,10,false), b=wrap(second,240,10,false);
        int count=Math.max(a.size(),b.size());
        for(int i=0;i<count;i++){
            requireSpace(16);
            if(i<a.size())text(a.get(i),M,y,10,NAVY,false);
            if(i<b.size())text(b.get(i),M+270,y,10,NAVY,false);
            y+=15;
        }
    }
    private void pairedSection(String[] left,String[] right){
        int lines=Math.max(left.length,right.length);
        for(int n=0;n<lines;n++){
            List<String> leftLines=n<left.length?wrap(left[n],235,10,n==0):new ArrayList<String>();
            List<String> rightLines=n<right.length?wrap(right[n],235,10,n==0):new ArrayList<String>();
            int rows=Math.max(leftLines.size(),rightLines.size());
            for(int i=0;i<rows;i++){
                requireSpace(16);
                if(i<leftLines.size())text(leftLines.get(i),M,y,n==0?12:10,n==0?NAVY:GRAY,n==0);
                if(i<rightLines.size())text(rightLines.get(i),M+270,y,n==0?12:10,n==0?NAVY:GRAY,n==0);
                y+=n==0?17:14;
            }
            if(rows>0)y+=3;
        }
    }
    private static void add(List<String> list,String label,String value){
        if(has(value))list.add(label+": "+value);
    }
    private static String[] company(Store.Document d){
        ArrayList<String> lines=new ArrayList<>();
        lines.add(safe(d.issuerName));
        add(lines,"Adresa",d.issuerAddress);
        add(lines,"Mjesto",join(d.issuerPostalCode,d.issuerCity,d.issuerCountry));
        add(lines,"OIB",d.issuerOib);add(lines,"PDV ID",d.issuerVatId);
        add(lines,"E-mail",d.issuerEmail);add(lines,"Telefon",d.issuerPhone);
        add(lines,"Web",d.issuerWebsite);
        return lines.toArray(new String[0]);
    }
    private static String[] customer(Store.Document d){
        ArrayList<String> lines=new ArrayList<>();
        lines.add(safe(d.clientName));
        add(lines,"Adresa",d.clientAddress);
        add(lines,"OIB",d.clientOib);add(lines,"PDV ID",d.clientVatId);
        add(lines,"E-mail",d.clientEmail);add(lines,"Telefon",d.clientPhone);
        return lines.toArray(new String[0]);
    }
    private static String join(String... words){
        StringBuilder sb=new StringBuilder();
        for(String w:words)if(has(w)){if(sb.length()!=0)sb.append(", ");sb.append(w.trim());}
        return sb.toString();
    }
    private void heading(String title){
        requireSpace(38);
        text(title,M,y,12,BLUE,true);y+=10;rule();
    }
    private void printItems(Store.Document d){
        heading("STAVKE DOKUMENTA");
        requireSpace(34);
        text("OPIS STAVKE",M,y,9,BLUE,true);
        right("KOL.",307,y,9,BLUE,true);
        right("CIJENA",396,y,9,BLUE,true);
        right("PDV",447,y,9,BLUE,true);
        right("IZNOS",RIGHT,y,9,BLUE,true);
        y+=10;rule();
        for(Store.Item item:d.items){
            List<String> nameLines=wrap(item.name,210,10,false);
            if(nameLines.isEmpty())nameLines.add("(bez naziva)");
            boolean first=true;
            for(String name:nameLines){
                requireSpace(18);
                text(name,M,y,10,NAVY,false);
                if(first){
                    right(Money.decimalQuantity(item.qtyMilli),307,y,9,GRAY,false);
                    right(Money.euros(item.unitCents),396,y,9,GRAY,false);
                    right(item.taxRate+"%",447,y,9,GRAY,false);
                    right(Money.euros(Math.addExact(item.net(),item.tax())),RIGHT,y,9,NAVY,true);
                    first=false;
                }
                y+=14;
            }
            y+=6;requireSpace(10);rule();
        }
    }
    private void totals(Store.Document d){
        heading("OBRAČUN I UKUPAN IZNOS");
        Map<Integer,long[]> groups=new TreeMap<>();
        for(Store.Item item:d.items){
            long[] group=groups.get(item.taxRate);
            if(group==null){group=new long[2];groups.put(item.taxRate,group);}
            group[0]=Math.addExact(group[0],item.net());
            group[1]=Math.addExact(group[1],item.tax());
        }
        for(Map.Entry<Integer,long[]> e:groups.entrySet()){
            field("PDV "+e.getKey()+"% · osnovica",Money.euros(e.getValue()[0]));
            field("PDV "+e.getKey()+"% · porez",Money.euros(e.getValue()[1]));
        }
        requireSpace(70);
        right("Osnovica",RIGHT-130,y,10,GRAY,false);right(Money.euros(d.netCents),RIGHT,y,10,NAVY,true);y+=19;
        right("PDV",RIGHT-130,y,10,GRAY,false);right(Money.euros(d.taxCents),RIGHT,y,10,NAVY,true);y+=20;
        right("UKUPNO",RIGHT-130,y,14,NAVY,true);right(Money.euros(d.totalCents),RIGHT,y,14,BLUE,true);y+=21;
        if(Store.INVOICE.equals(d.type)){
            right("Evidentirano plaćeno",RIGHT-130,y,9,GRAY,false);
            right(Money.euros(d.paidCents),RIGHT,y,10,NAVY,false);y+=19;
            right("Preostalo",RIGHT-130,y,10,GRAY,false);
            right(Money.euros(d.outstanding()),RIGHT,y,11,NAVY,true);y+=21;
        }
    }
    private void billing(Store.Document d){
        heading("PLAĆANJE I NAPOMENE");
        field("IBAN",d.issuerIban);
        field("Banka",d.issuerBankName);
        field("BIC / SWIFT",d.issuerSwift);
        field("Način plaćanja",d.issuerPaymentMethod);
        field("Poziv na broj / model",d.issuerPaymentReference);
        field("Registarski podatak",d.issuerRegistry);
        field("Poslovni prostor",d.issuerBusinessUnit);
        field("Oznaka operatera",d.issuerOperator);
        field("Porezni status",d.issuerTaxNote);
        field("Napomena",d.notes);
    }
    private void render(Store.Document d){
        newPage();
        requireSpace(58);
        text(Store.INVOICE.equals(d.type)?"RAČUN":"PONUDA",M,y,19,NAVY,true);
        right(d.number==null?"NACRT #"+d.id:d.number,RIGHT,y,13,BLUE,true);y+=21;
        text(Store.DRAFT.equals(d.status)?"Nacrt — dokument nije potvrđen":
            Store.PAID.equals(d.status)?"Plaćeno":
            Store.SENT.equals(d.status)?"Potvrđena ponuda":"Potvrđeno",M,y,10,GRAY,false);y+=25;
        paired("IZDAVATELJ","KLIJENT");y+=4;
        pairedSection(company(d),customer(d));y+=9;
        requireSpace(20);rule();
        paired("Datum izdavanja: "+safe(d.issueDate),
            "Dospijeće / valjanost: "+safe(d.dueDate));
        if(has(d.supplyDate))field("Datum isporuke / obavljene usluge",d.supplyDate);
        y+=12;
        printItems(d);
        totals(d);
        billing(d);
        if(current!=null){footer();document.finishPage(current);current=null;}
    }
    public static void write(Store db,Store.Document d,OutputStream out)throws IOException{
        PdfMaker maker=new PdfMaker();
        try{
            maker.render(d);
            maker.document.writeTo(out);
        }finally{maker.document.close();}
    }
}
