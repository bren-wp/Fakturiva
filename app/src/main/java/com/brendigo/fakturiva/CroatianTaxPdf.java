package com.brendigo.fakturiva;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Real, selectable-text Android PDFs. Table headings and column order follow
 * the user's KPR and PO-SD 2026 references; template source PDFs and personal
 * identifiers are NOT bundled into the app.
 */
public final class CroatianTaxPdf {
    private CroatianTaxPdf(){}
    private static final int BLACK=Color.BLACK, GRAY=0xffd9d9d9;
    private static final float LEFT=24;
    private static final class Draw {
        final Canvas c;final float w,h;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
        Draw(Canvas c,float width,float height){
            this.c=c;this.w=width;this.h=height;
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        }
        void text(String value,float x,float baseline,int size,boolean bold){
            p.setColor(BLACK);p.setStyle(Paint.Style.FILL);
            p.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);
            c.drawText(value==null?"":value,x,baseline,p);
        }
        void centered(String value,float baseline,int size,boolean bold){
            p.setTypeface(Typeface.create("sans-serif",
                bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(size);
            float tw=p.measureText(value);
            text(value,(w-tw)/2f,baseline,size,bold);
        }
        void fill(float x,float y,float width,float height,int color){
            p.setColor(color);p.setStyle(Paint.Style.FILL);
            c.drawRect(x,y,x+width,y+height,p);
        }
        void line(float x1,float y1,float x2,float y2){
            p.setColor(BLACK);p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(.7f);c.drawLine(x1,y1,x2,y2,p);
            p.setStyle(Paint.Style.FILL);
        }
        void box(float x,float y,float width,float height,boolean gray){
            if(gray)fill(x,y,width,height,GRAY);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.75f);
            p.setColor(BLACK);c.drawRect(x,y,x+width,y+height,p);
            p.setStyle(Paint.Style.FILL);
        }
        String crop(String value,float maxWidth,int fontSize,boolean bold){
            if(value==null)return "";
            p.setTypeface(Typeface.create("sans-serif",
                bold?Typeface.BOLD:Typeface.NORMAL));
            p.setTextSize(fontSize);
            if(p.measureText(value)<=maxWidth)return value;
            String ellipsis="…";
            int count=p.breakText(value,true,
                Math.max(1,maxWidth-p.measureText(ellipsis)),null);
            return value.substring(0,Math.max(0,count))+ellipsis;
        }
        void headerCell(String[] lines,float x,float top,float width,float height){
            box(x,top,width,height,true);
            for(int i=0;i<lines.length;i++){
                text(crop(lines[i],width-7,8,true),x+4,
                    top+12+i*10,8,true);
            }
        }
        void inside(String value,float x,float top,float width,int fontSize,boolean bold){
            text(crop(value,width-8,fontSize,bold),x+4,top+15,fontSize,bold);
        }
        void cell(String value,float x,float top,float width,float height,
                  int size,boolean bold,boolean shade){
            box(x,top,width,height,shade);
            inside(value,x,top,width,size,bold);
        }
        float section(String title,float y,float x,float width){
            cell(title,x,y,width,22,10,true,true);return y+22;
        }
        void foot(String title,int page,int pages){
            line(LEFT,h-39,w-LEFT,h-39);
            text(title,LEFT,h-25,8,false);
            String counter="Stranica "+page+" / "+pages;
            p.setTextSize(8);p.setTypeface(Typeface.DEFAULT);
            text(counter,w-LEFT-p.measureText(counter),h-25,8,false);
        }
        void wrapped(String text,float x,float y,float maxWidth,
                     int fontSize,int maxLines){
            if(text==null)return;
            String remaining=text.trim();
            p.setTextSize(fontSize);p.setTypeface(Typeface.DEFAULT);
            int line=0;
            while(!remaining.isEmpty()&&line<maxLines){
                int len=p.breakText(remaining,true,maxWidth,null);
                if(len<=0)break;
                if(len<remaining.length()){
                    int space=remaining.lastIndexOf(' ',len);
                    if(space>0)len=space;
                }
                String part=remaining.substring(0,len).trim();
                text(part,x,y+line*(fontSize+4),fontSize,false);
                remaining=remaining.substring(len).trim();
                line++;
            }
        }
    }
    public static void writeKpr(CroatianTaxReports.Book book,
                                CroatianTaxReports.Business company,
                                OutputStream output)throws IOException{
        // One receipt per row, not one invoice per issued date. A page with zero
        // receipts still renders correct headers and zero totals.
        PdfDocument pdf=new PdfDocument();
        int perPage=16;
        int pages=Math.max(1,(book.rows.size()+perPage-1)/perPage);
        try{
            for(int page=0;page<pages;page++){
                PdfDocument.Page sheet=pdf.startPage(
                    new PdfDocument.PageInfo.Builder(595,842,page+1).create());
                Draw d=new Draw(sheet.getCanvas(),595,842);
                d.text("KNJIGA PROMETA",24,45,18,true);
                d.text("Razdoblje: "+book.start+" - "+book.end,24,66,9,false);
                d.text("I. OPĆI PODACI O POREZNOM OBVEZNIKU",24,92,11,true);
                float x=24,w=547,y=100;
                y=d.section("I. OPĆI PODACI O POREZNOM OBVEZNIKU",y,x,w);
                d.cell("1. NAZIV DJELATNOSTI: "+company.name,x,y,330,27,9,false,false);
                d.cell("ŠIFRA DJELATNOSTI: "+company.nkd,x+330,y,w-330,27,9,false,false);
                y+=27;
                d.cell("2. IME I PREZIME: "+company.owner,x,y,w,26,9,false,false);y+=26;
                d.cell("3. ADRESA: "+company.address,x,y,w,26,9,false,false);y+=26;
                d.cell("4. OIB: "+company.oib,x,y,w,26,9,false,false);y+=26;
                y=d.section("II. PODACI O POSLOVNOJ JEDINICI",y,x,w);
                d.cell("1. NAZIV: "+("SVE".equals(book.unit)?"SVE JEDINICE":company.unit),
                    x,y,w,25,9,false,false);y+=25;
                d.cell("2. ADRESA: "+company.place,x,y,w,25,9,false,false);y+=25;
                d.text("PRIMICI PREMA DATUMU STVARNE NAPLATE",x,y+16,9,true);y+=22;
                final float[] col={34,74,73,148,72,73,73};
                final String[][] heads={
                    {"R.BR."},{"NADNEVAK"},{"BROJ","TEMELJNICE"},
                    {"OPIS ISPRAVA O","PRIMICIMA U GOTOVINI"},
                    {"NAPLAĆENO","U GOTOVINI"},{"NAPLAĆENO","BEZGOTOVINSKI"},
                    {"UKUPNO","NAPLAĆENO"}
                };
                float cx=x;
                for(int j=0;j<heads.length;j++){
                    d.headerCell(heads[j],cx,y,col[j],35);cx+=col[j];
                }
                y+=35;
                for(int i=page*perPage;i<Math.min(book.rows.size(),(page+1)*perPage);i++){
                    CroatianTaxReports.Row r=book.rows.get(i);
                    String[] cols={String.valueOf(i+1),r.date,r.source,
                        r.description,CroatianTaxReports.euros(r.cash),
                        CroatianTaxReports.euros(r.noncash),
                        CroatianTaxReports.euros(r.total())};
                    cx=x;
                    for(int j=0;j<cols.length;j++){
                        d.cell(cols[j],cx,y,col[j],22,j<=3?8:8,false,false);cx+=col[j];
                    }
                    y+=22;
                }
                if(page==pages-1){
                    cx=x;
                    String[] vals={"UKUPNO:","","","",
                        CroatianTaxReports.euros(book.cash),
                        CroatianTaxReports.euros(book.noncash),
                        CroatianTaxReports.euros(book.total)};
                    for(int j=0;j<vals.length;j++){
                        d.cell(vals[j],cx,y,col[j],27,8,j==0||j>3,true);cx+=col[j];
                    }
                    y+=29;
                }
                d.wrapped("Paušalni obveznik: gotovinski i bezgotovinski primici. "+
                    "Ostali obveznici: KPR obuhvaća samo odgovarajuće gotovinske primitke.",
                    x,Math.min(y+18,776),w,8,2);
                d.foot("Fakturiva · Izvještaj iz evidentiranih uplata · provjeriti s knjigama",
                    page+1,pages);
                pdf.finishPage(sheet);
            }
            pdf.writeTo(output);
        }finally{pdf.close();}
    }

    public static void writePosd(CroatianTaxReports.Posd p,OutputStream output)
            throws IOException{
        PdfDocument pdf=new PdfDocument();
        try{
            PdfDocument.Page page=pdf.startPage(
                new PdfDocument.PageInfo.Builder(842,596,1).create());
            Draw d=new Draw(page.getCanvas(),842,596);
            d.centered("OBRAZAC ZA PO-SD",28,13,true);
            d.centered("IZVJEŠĆE O PAUŠALNOM DOHOTKU OD SAMOSTALNE DJELATNOSTI",49,12,true);
            d.centered("OD 1. 1. DO 31. 12. "+p.year+". GODINE",66,10,true);
            float x=16,w=810,y=83;
            y=d.section("I. PODACI O POREZNOM OBVEZNIKU",y,x,w);
            d.cell("OIB: "+p.business.oib,x,y,170,39,10,false,false);
            d.cell("IME I PREZIME: "+p.business.owner,x+170,y,320,39,10,false,false);
            d.cell("ADRESA: "+p.business.address,x+490,y,320,39,9,false,false);
            y+=39;
            y=d.section("II. PODACI O DJELATNOSTI",y,x,w);
            d.cell("1. NAZIV I VRSTA: "+p.business.name+" · "+p.business.activity,x,y,w,32,9,false,false);y+=32;
            d.cell("2. ADRESA DJELATNOSTI: "+p.business.place,x,y,w,31,9,false,false);y+=31;
            d.cell("3. VUKOVAR / POTPOMOGNUTO PODRUČJE I. SKUPINE / OTOK I. SKUPINE: "+
                (p.business.special?"DA":"NE"),x,y,w,28,9,false,false);y+=28;
            d.cell("4. RAZDOBLJE OBAVLJANJA DJELATNOSTI: "+p.from+" - "+p.to,
                x,y,w,30,9,false,false);y+=30;
            y=d.section("III. PODACI O OSTVARENIM PRIMICIMA OD POJEDINAČNE DJELATNOSTI",y,x,w);
            d.cell("NAPLAĆENI U GOTOVINI",x,y,270,31,9,true,true);
            d.cell("NAPLAĆENI BEZGOTOVINSKI",x+270,y,270,31,9,true,true);
            d.cell("UKUPNO NAPLAĆENI PRIMICI",x+540,y,270,31,9,true,true);y+=31;
            d.cell(CroatianTaxReports.euros(p.book.cash),x,y,270,29,10,false,false);
            d.cell(CroatianTaxReports.euros(p.book.noncash),x+270,y,270,29,10,false,false);
            d.cell(CroatianTaxReports.euros(p.book.total),x+540,y,270,29,10,true,false);y+=29;
            d.cell("IV. GODIŠNJI PAUŠALNI DOHODAK OD POJEDINAČNE DJELATNOSTI",
                x,y,570,32,9,true,true);
            d.cell(CroatianTaxReports.euros(p.assessedIncome),x+570,y,152,32,9,true,false);
            d.cell(p.months+" mj.",x+722,y,88,32,9,true,false);y+=32;
            d.cell("V. ZAJEDNIČKA DJELATNOST (nije podržana automatski)",x,y,570,32,9,true,true);
            d.cell("0,00 €",x+570,y,152,32,9,false,false);
            d.cell("0 mj.",x+722,y,88,32,9,false,false);y+=32;
            d.cell("VI. UKUPNI GODIŠNJI PAUŠALNI DOHODAK",x,y,570,32,9,true,true);
            d.cell(CroatianTaxReports.euros(p.assessedIncome),x+570,y,152,32,9,true,false);
            d.cell(p.months+" mj.",x+722,y,88,32,9,true,false);
            d.foot("Fakturiva · Radni izračun iz KPR-a, ne potvrda Porezne uprave",1,3);
            pdf.finishPage(page);

            PdfDocument.Page second=pdf.startPage(
                new PdfDocument.PageInfo.Builder(842,596,2).create());
            d=new Draw(second.getCanvas(),842,596);
            d.centered("PO-SD "+p.year+" · NASTAVAK",31,12,true);
            x=16;w=810;y=55;
            y=d.section("VII. OBRAČUN PAUŠALNOG POREZA NA DOHODAK",y,x,w);
            String[][] fields={
                {"1.","IZNOS OBVEZE PAUŠALNOG POREZA NA DOHODAK",
                    CroatianTaxReports.euros(p.assessedTax)},
                {"2.","UMANJENJE ZA VUKOVAR / POTPOMOGNUTA PODRUČJA / OTOKE",
                    CroatianTaxReports.euros(p.reduction)},
                {"3.","OBVEZA PAUŠALNOG POREZA NAKON UMANJENJA",
                    CroatianTaxReports.euros(p.taxAfterReduction)},
                {"4.","UKUPNO UPLAĆENI PAUŠALNI POREZ NA DOHODAK",
                    CroatianTaxReports.euros(p.alreadyPaid)},
                {"5.","RAZLIKA ZA UPLATU / POVRAT",
                    CroatianTaxReports.euros(p.difference)},
                {"6.","IZNOS MJESEČNOG PAUŠALNOG POREZA NA DOHODAK",
                    CroatianTaxReports.euros(p.monthlyTax)}
            };
            for(String[] field:fields){
                d.cell(field[0],x,y,35,35,9,true,false);
                d.cell(field[1],x+35,y,621,35,10,false,false);
                d.cell(field[2],x+656,y,154,35,9,true,false);
                y+=35;
            }
            y+=20;
            d.text("Godišnji primitci (preračunati): "+
                CroatianTaxReports.euros(p.annualizedReceipts),x,y+8,10,false);
            d.text("Paušalni razred: "+p.bracket+" / 7 · porezna godina "+p.year,
                x,y+25,9,false);
            y+=44;
            d.text("NADNEVAK: ____________________",x,y+18,10,false);
            d.text("POTPIS POREZNOG OBVEZNIKA: ____________________",x+420,y+18,10,false);
            y+=34;
            d.wrapped("Napomena: obračun vrijedi samo za pojedinačnu paušalnu djelatnost. "+
                "Uplaćeni porez korisnik potvrđuje prema poreznoj knjigovodstvenoj kartici.",
                x,y,760,8,3);
            d.wrapped("U slučaju zajedničke djelatnosti, prekida poslovanja, "+
                "poreznih iznimaka ili nenaplaćenih/nerazvrstanih uplata pregledajte "+
                "podatke s ovlaštenim računovođom.",
                x,y+31,760,8,3);
            d.foot("Fakturiva · 2025/2026 porezni razredi · provjeriti prije predaje",2,3);
            pdf.finishPage(second);
            PdfDocument.Page third=pdf.startPage(
                new PdfDocument.PageInfo.Builder(842,596,3).create());
            d=new Draw(third.getCanvas(),842,596);
            d.centered("PO-SD · DODATNE NAPOMENE I GODIŠNJI POREZNI RAZREDI",32,12,true);
            x=16;w=810;y=62;
            y=d.section("UKUPNI PRIMICI I GODIŠNJI PAUŠALNI POREZ (2025./2026.)",y,x,w);
            d.cell("UKUPNI PRIMICI (EUR)",x,y,405,28,10,true,true);
            d.cell("GODIŠNJI PAUŠALNI POREZ (EUR)",x+405,y,405,28,10,true,true);y+=28;
            String[][] brackets={
                {"0,00 - 11.300,00","203,40"},
                {"11.300,01 - 15.300,00","275,40"},
                {"15.300,01 - 19.900,00","358,20"},
                {"19.900,01 - 30.600,00","550,80"},
                {"30.600,01 - 40.000,00","720,00"},
                {"40.000,01 - 50.000,00","900,00"},
                {"50.000,01 - 60.000,00","1.080,00"}
            };
            for(String[] bracket:brackets){
                d.cell(bracket[0],x,y,405,29,9,false,false);
                d.cell(bracket[1],x+405,y,405,29,9,false,false);
                y+=29;
            }
            y+=27;
            d.wrapped("1. Za kraće porezno razdoblje razred se utvrđuje iz "+
                "godišnje preračunatih primitaka, a obveza razmjerno broju mjeseci.",
                x,y,w,10,3);y+=42;
            d.wrapped("2. U broj mjeseci uračunavaju se puni kalendarski mjeseci "+
                "i posljednji mjesec bez obzira na dan prestanka djelatnosti.",
                x,y,w,10,3);y+=42;
            d.wrapped("3. Za Vukovar, potpomognuta područja I. skupine i "+
                "otoke I. skupine može se primijeniti propisano umanjenje od 75 %.",
                x,y,w,10,3);
            d.foot("Podatci iz fakturiranih i evidentiranih naplata · informativna priprema",3,3);
            pdf.finishPage(third);
            pdf.writeTo(output);
        }finally{pdf.close();}
    }
}
