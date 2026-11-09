package com.brendigo.fakturiva;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Croatian cash-basis tax ledger. Does NOT equate issued invoices to receipts:
 * only actual payment entries affect KPR/PO-SD. Amounts are integer EUR cents.
 *
 * Scope: individual income-tax trades (paušal or dohodak), reporting years
 * 2025 and 2026. Company-tax entities must not receive a fabricated PO-SD.
 */
public final class CroatianTaxReports {
    private CroatianTaxReports() {}
    public static final String PAUSAL="PAUSAL", DOHODAK="DOHODAK", OTHER="OTHER";
    public static final long MAX_PAUSAL_CENTS=6_000_000L;
    private static final long[] LIMITS={1_130_000L,1_530_000L,1_990_000L,
        3_060_000L,4_000_000L,5_000_000L,6_000_000L};
    private static final long[] ANNUAL_INCOME={169_500L,229_500L,298_500L,
        459_000L,600_000L,750_000L,900_000L};
    private static final long[] ANNUAL_TAX={20_340L,27_540L,35_820L,
        55_080L,72_000L,90_000L,108_000L};

    public static final class Row {
        public final String date,source,description,unit;
        public final long id,cash,noncash;
        public Row(String date,String source,String description,String unit,
                   long id,long cash,long noncash){
            this.date=date;this.source=source;this.description=description;
            this.unit=unit;this.id=id;this.cash=cash;this.noncash=noncash;
        }
        public long total(){return Math.addExact(cash,noncash);}
    }
    public static final class Book {
        public final String start,end,model,unit;
        public final List<Row> rows;
        public final long cash,noncash,total;
        Book(String start,String end,String model,String unit,List<Row> rows,
             long cash,long noncash){
            this.start=start;this.end=end;this.model=model;this.unit=unit;
            this.rows=Collections.unmodifiableList(rows);
            this.cash=cash;this.noncash=noncash;this.total=Math.addExact(cash,noncash);
        }
    }
    public static final class Business {
        public final String name,owner,oib,address,nkd,activity,place,unit;
        public final boolean special;
        Business(String name,String owner,String oib,String address,
                 String nkd,String activity,String place,String unit,boolean special){
            this.name=name;this.owner=owner;this.oib=oib;this.address=address;
            this.nkd=nkd;this.activity=activity;this.place=place;
            this.unit=unit;this.special=special;
        }
    }
    public static final class Posd {
        public final int year,months,bracket;
        public final String from,to;
        public final Business business;
        public final Book book;
        public final long assessedIncome,assessedTax,reduction,taxAfterReduction;
        public final long alreadyPaid,difference,monthlyTax,annualizedReceipts;
        Posd(int year,int months,int bracket,String from,String to,Business business,
             Book book,long assessedIncome,long assessedTax,long reduction,
             long paid,long annualized){
            this.year=year;this.months=months;this.bracket=bracket;
            this.from=from;this.to=to;this.business=business;this.book=book;
            this.assessedIncome=assessedIncome;this.assessedTax=assessedTax;
            this.reduction=reduction;this.alreadyPaid=paid;
            this.taxAfterReduction=assessedTax-reduction;
            this.difference=this.taxAfterReduction-paid;
            this.monthlyTax=roundedDiv(this.taxAfterReduction,months);
            this.annualizedReceipts=annualized;
        }
    }
    public static String text(Store db,String key){
        String v=db.setting(key);return v==null?"":v.trim();
    }
    public static void requireCroatia(Store db){
        String country=text(db,"country");
        if(!country.equalsIgnoreCase("HR") &&
           !country.equalsIgnoreCase("HRVATSKA") &&
           !country.equalsIgnoreCase("CROATIA"))
            throw new IllegalArgumentException(
                "Za hrvatske obrasce upišite HR ili Hrvatska u profilu izdavatelja.");
    }
    public static String taxModel(Store db){
        String m=text(db,"hr_tax_model");
        return PAUSAL.equals(m)||DOHODAK.equals(m)?m:OTHER;
    }
    public static String checkedDate(String value){
        if(!IsoDates.valid(value) || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Datum mora biti u obliku GGGG-MM-DD");
        return value;
    }
    public static long cents(String value){
        if(value==null||value.trim().isEmpty())throw new IllegalArgumentException(
            "Unesite iznos uplaćenog poreza za odabranu godinu (može biti 0,00)");
        String normalized=value.trim().replace(" ","").replace("€","");
        // Croatian thousands separator '.' and decimal ','; also accept
        // a single dot as an English-style decimal separator.
        if(normalized.indexOf(',')>=0)
            normalized=normalized.replace(".","").replace(',','.');
        else if(normalized.indexOf('.')!=normalized.lastIndexOf('.'))
            throw new IllegalArgumentException("Nejasan decimalni zapis: koristite 1.234,56");
        if(!normalized.matches("[0-9]{1,12}(\\.[0-9]{1,2})?"))
            throw new IllegalArgumentException("Iznos mora biti broj s najviše 2 decimale");
        java.math.BigDecimal money=new java.math.BigDecimal(normalized);
        return money.movePointRight(2).longValueExact();
    }
    static long roundedDiv(long numerator,long denominator){
        if(denominator<=0 || numerator<0)throw new IllegalArgumentException("Dijeljenje nije moguće");
        return Math.addExact(numerator,denominator/2)/denominator;
    }
    public static String euros(long cents){
        java.math.BigDecimal value=java.math.BigDecimal.valueOf(cents,2);
        java.text.NumberFormat fmt=java.text.NumberFormat.getNumberInstance(
            java.util.Locale.forLanguageTag("hr-HR"));
        fmt.setMinimumFractionDigits(2);fmt.setMaximumFractionDigits(2);
        return fmt.format(value)+" €";
    }
    public static boolean validOib(String oib){
        if(oib==null||!oib.matches("[0-9]{11}"))return false;
        int result=10;
        for(int i=0;i<10;i++){
            result=(result+(oib.charAt(i)-'0'))%10;
            if(result==0)result=10;
            result=(result*2)%11;
        }
        int check=11-result;
        if(check==10)check=0;
        return check==oib.charAt(10)-'0';
    }
    public static Business business(Store db){
        String name=text(db,"name"),owner=text(db,"hr_tax_owner");
        String oib=text(db,"oib"),address=text(db,"hr_tax_residence");
        String activity=text(db,"hr_tax_activity"),place=text(db,"hr_tax_place");
        String nkd=text(db,"hr_tax_nkd"),unit=text(db,"business_unit");
        if(unit.isEmpty())unit="PP1";
        boolean special="yes".equals(text(db,"hr_tax_special"));
        return new Business(name,owner,oib,address,nkd,activity,place,unit,special);
    }
    public static void requireBusiness(Store db){
        requireCroatia(db);
        if(OTHER.equals(taxModel(db)))throw new IllegalArgumentException(
            "KPR nije univerzalna evidencija za sve d.o.o./j.d.o.o. i porezne modele. " +
            "Odaberite stvarni porezni status obveznika u HR izvještajima.");
        Business b=business(db);
        if(b.name.isEmpty()||b.owner.isEmpty()||b.activity.isEmpty()||
           b.address.isEmpty()||b.place.isEmpty()||b.nkd.isEmpty())
            throw new IllegalArgumentException(
                "Dopunite naziv djelatnosti, NKD, nositelja i adrese u HR poreznim podacima.");
        if(!validOib(b.oib))throw new IllegalArgumentException(
            "OIB u profilu tvrtke nije valjan.");
    }
    public static Book book(Store db,String from,String to,String unit){
        requireBusiness(db);
        checkedDate(from);checkedDate(to);
        if(from.compareTo(to)>0)
            throw new IllegalArgumentException("Datum od mora biti prije datuma do.");
        String model=taxModel(db);
        ArrayList<Row> rows=new ArrayList<>();
        long cash=0,noncash=0;
        for(Store.Payment p:db.payments()){
            if(p.paidOn==null||p.paidOn.compareTo(from)<0||p.paidOn.compareTo(to)>0)
                continue;
            if(unit!=null&&!unit.isEmpty()&&!"SVE".equals(unit) &&
               !unit.equals(p.businessUnit))continue;
            if(!"EUR".equals(p.currencyCode))
                throw new IllegalStateException(
                    "U odabranom razdoblju postoji primitak u valuti "+p.currencyCode+
                    ". Potvrdite EUR preračun prema datumu naplate prije izvještavanja.");
            if(p.channel==null||"UNKNOWN".equals(p.channel))
                throw new IllegalStateException(
                    "Uplata #"+p.id+" uz račun "+p.invoiceNumber+
                    " nema potvrđen način plaćanja. Razvrstajte je u pregledu uplata.");
            long c=("CASH".equals(p.channel)||"CHEQUE".equals(p.channel))?
                p.amountCents:0;
            long n=("BANK".equals(p.channel)||"CARD".equals(p.channel))?
                p.amountCents:0;
            if(c==0&&n==0)throw new IllegalStateException(
                "Nepodržana vrsta uplate #"+p.id);
            // Non-pauschal income tax KPR is a cash/cheque record; bank entries
            // belong in accounting records other than this KPR.
            if(DOHODAK.equals(model)&&n>0)continue;
            String source=p.invoiceNumber==null?"":p.invoiceNumber;
            rows.add(new Row(p.paidOn,source,
                "Naplata računa "+source+" · uplata "+p.id,
                p.businessUnit==null?"PP1":p.businessUnit,p.id,c,n));
            cash=Math.addExact(cash,c);noncash=Math.addExact(noncash,n);
        }
        rows.sort(Comparator.comparing((Row r)->r.date).thenComparingLong(r->r.id));
        return new Book(from,to,model,unit,rows,cash,noncash);
    }
    public static int activityMonths(int year,String from,String to){
        checkedDate(from);checkedDate(to);
        if(!from.startsWith(year+"-")||!to.startsWith(year+"-")||
            from.compareTo(to)>0)
            throw new IllegalArgumentException("Razdoblje djelatnosti mora biti u istoj poreznoj godini.");
        int m1=Integer.parseInt(from.substring(5,7));
        int m2=Integer.parseInt(to.substring(5,7));
        int d1=Integer.parseInt(from.substring(8,10));
        int count=m2-m1+1;
        // Rule in the 2025+ PO-SD instructions: every complete calendar month
        // plus the final month regardless of number of days.
        if(d1>1&&count>1)count--;
        return count;
    }
    public static Posd posd(Store db,int year,String activeFrom,String activeTo,
                            String paidTaxText){
        requireBusiness(db);
        if(!PAUSAL.equals(taxModel(db)))
            throw new IllegalArgumentException("PO-SD odnosi se na odgovarajućeg paušalnog obveznika.");
        if(year<2025||year>2026)
            throw new IllegalArgumentException(
                "Porezni razredi verificirani su za 2025. i 2026. godinu. " +
                "Za drugu godinu potrebna je provjera aktualnog propisa.");
        int months=activityMonths(year,activeFrom,activeTo);
        if(months<1||months>12)throw new IllegalArgumentException("Broj mjeseci izvan raspona");
        Book receipts=book(db,year+"-01-01",year+"-12-31","SVE");
        long annualized=roundedDiv(Math.multiplyExact(receipts.total,12),months);
        if(annualized>MAX_PAUSAL_CENTS)
            throw new IllegalStateException(
                "Preračunati godišnji primici premašuju 60.000,00 €. " +
                "Provjerite porezni status i PO-SD s računovođom.");
        int bracket=0;
        while(bracket<LIMITS.length-1&&annualized>LIMITS[bracket])bracket++;
        long baseIncome=roundedDiv(Math.multiplyExact(ANNUAL_INCOME[bracket],months),12);
        long tax=roundedDiv(Math.multiplyExact(ANNUAL_TAX[bracket],months),12);
        Business business=business(db);
        long reduction=business.special?roundedDiv(Math.multiplyExact(tax,75),100):0;
        long paid=cents(paidTaxText);
        return new Posd(year,months,bracket+1,activeFrom,activeTo,
            business,receipts,baseIncome,tax,reduction,paid,annualized);
    }
    public static byte[] workingXml(Posd p){
        // This is a well-formed review/export file, NOT the Croatian ePorezna
        // import format. Until the currently applicable XSD is imported,
        // validated and tested against ePorezna this must stay visibly labelled.
        StringBuilder xml=new StringBuilder(1600);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<FakturivaPOSD xmlns=\"urn:brendigo:fakturiva:posd:working:1\" ")
           .append("status=\"RADNI-PODACI-NIJE-ZA-UVOZ-U-EPOREZNU\" ")
           .append("godina=\"").append(p.year).append("\">\n");
        xmlText(xml,"OIB",p.business.oib);
        xmlText(xml,"PorezniObveznik",p.business.owner);
        xmlText(xml,"Prebivaliste",p.business.address);
        xmlText(xml,"NazivDjelatnosti",p.business.name);
        xmlText(xml,"VrstaDjelatnosti",p.business.activity);
        xmlText(xml,"NKD",p.business.nkd);
        xmlText(xml,"AdresaDjelatnosti",p.business.place);
        xmlText(xml,"RazdobljeOd",p.from);xmlText(xml,"RazdobljeDo",p.to);
        xmlText(xml,"GotovinaEUR",decimal(p.book.cash));
        xmlText(xml,"BezgotovinskiEUR",decimal(p.book.noncash));
        xmlText(xml,"UkupniPrimiciEUR",decimal(p.book.total));
        xmlText(xml,"BrojMjeseci",String.valueOf(p.months));
        xmlText(xml,"GodisnjiDohodakEUR",decimal(p.assessedIncome));
        xmlText(xml,"GodisnjiPorezEUR",decimal(p.assessedTax));
        xmlText(xml,"UmanjenjeEUR",decimal(p.reduction));
        xmlText(xml,"PorezNakonUmanjenjaEUR",decimal(p.taxAfterReduction));
        xmlText(xml,"UplaceniPorezEUR",decimal(p.alreadyPaid));
        xmlText(xml,"RazlikaEUR",decimal(p.difference));
        xml.append("</FakturivaPOSD>\n");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static String decimal(long cents){
        return java.math.BigDecimal.valueOf(cents,2).toPlainString();
    }
    private static void xmlText(StringBuilder xml,String key,String value){
        xml.append("  <").append(key).append('>');
        for(int i=0;i<value.length();i++){
            char ch=value.charAt(i);
            if(ch=='&')xml.append("&amp;");
            else if(ch=='<')xml.append("&lt;");
            else if(ch=='>')xml.append("&gt;");
            else if(ch=='"')xml.append("&quot;");
            else if(ch=='\'')xml.append("&apos;");
            else if(ch>=32||ch=='\t'||ch=='\n'||ch=='\r')xml.append(ch);
        }
        xml.append("</").append(key).append(">\n");
    }
}
