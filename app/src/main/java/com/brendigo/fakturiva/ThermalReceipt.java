package com.brendigo.fakturiva;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Raster ESC/POS print stream for 58/80 mm Bluetooth Classic SPP printers.
 * All user text is rendered to pixels, so it cannot inject printer commands.
 * Raster mode requires a compatible ESC/POS printer; this is NOT a card POS
 * terminal protocol, Bluetooth LE profile, or fiscalization integration.
 */
public final class ThermalReceipt {
    public static final int PAPER_58_DOTS=384, PAPER_80_DOTS=576,
        PAPER_80_COMPACT_DOTS=512;
    private static final int MAX_ROWS=800;
    private ThermalReceipt(){}

    public static boolean supports(int dots){
        // 80 mm printers commonly expose either 512 or 576 printable dots.
        return dots==PAPER_58_DOTS || dots==PAPER_80_DOTS ||
            dots==PAPER_80_COMPACT_DOTS;
    }
    private static void optional(List<String> rows,String label,String value){
        if(value!=null && !value.trim().isEmpty())
            rows.add(label+": "+value.trim());
    }
    static List<String> compose(Store.Document d){
        if(d==null)throw new IllegalArgumentException("Missing document");
        ArrayList<String> rows=new ArrayList<>();
        rows.add("FAKTURIVA");
        rows.add(Store.QUOTE.equals(d.type)?"QUOTE":"INVOICE");
        rows.add(d.number==null?"DRAFT — NOT ISSUED":d.number);
        optional(rows,"Legal name",d.issuerName);
        optional(rows,"Legal form",d.issuerLegalForm);
        optional(rows,"Trading name",d.issuerTradingName);
        optional(rows,"Company number",d.issuerCompanyNumber);
        optional(rows,"Register",d.issuerRegisterAuthority);
        optional(rows,"Tax ID",d.issuerOib);
        optional(rows,"VAT ID",d.issuerVatId);
        optional(rows,"Tax registration",d.issuerTaxRegistration);
        optional(rows,"Address",d.issuerAddress);
        optional(rows,"Address 2",d.issuerAddress2);
        optional(rows,"Postal code",d.issuerPostalCode);
        optional(rows,"City",d.issuerCity);
        optional(rows,"Region",d.issuerRegion);
        optional(rows,"Country",d.issuerCountry);
        optional(rows,"Contact",d.issuerContactPerson);
        optional(rows,"Phone",d.issuerPhone);
        optional(rows,"Email",d.issuerEmail);
        optional(rows,"Website",d.issuerWebsite);
        rows.add("------------------------------");
        optional(rows,"Customer",d.clientName);
        optional(rows,"Customer address",d.clientAddress);
        optional(rows,"Customer tax ID",d.clientOib);
        optional(rows,"Customer VAT ID",d.clientVatId);
        optional(rows,"Customer email",d.clientEmail);
        optional(rows,"Customer phone",d.clientPhone);
        optional(rows,"Quote title",d.quoteTitle);
        optional(rows,"Issued",d.issueDate);
        optional(rows,"Due / valid until",d.dueDate);
        optional(rows,"Delivery / service",d.supplyDate);
        rows.add("------------------------------");
        if(d.items!=null){
            for(Store.Item item:d.items){
                if(item==null)continue;
                optional(rows,"Item",item.name);
                rows.add("Qty "+Money.decimalQuantity(item.qtyMilli)+" x "+
                    amount(item.unitCents,d.currencyCode));
                rows.add("Tax "+Money.rateLabel(item.taxRate)+" | Line "+
                    amount(Math.addExact(item.net(),item.tax()),d.currencyCode));
            }
        }
        rows.add("------------------------------");
        rows.add("Subtotal: "+amount(d.netCents,d.currencyCode));
        rows.add("Tax: "+amount(d.taxCents,d.currencyCode));
        rows.add("TOTAL: "+amount(d.totalCents,d.currencyCode));
        if(Store.INVOICE.equals(d.type)){
            rows.add("Paid: "+amount(d.paidCents,d.currencyCode));
            rows.add("Outstanding: "+amount(d.outstanding(),d.currencyCode));
        }
        rows.add("------------------------------");
        optional(rows,"Bank",d.issuerBankName);
        optional(rows,"IBAN",d.issuerIban);
        optional(rows,"Bank account",d.issuerBankAccountNumber);
        optional(rows,"BIC/SWIFT",d.issuerSwift);
        optional(rows,"Routing/ABA",d.issuerBankRoutingNumber);
        optional(rows,"Sort/branch",d.issuerBankSortCode);
        optional(rows,"Bank address",d.issuerBankAddress);
        optional(rows,"Payment method",d.issuerPaymentMethod);
        optional(rows,"Payment reference",d.issuerPaymentReference);
        optional(rows,"PO reference",d.issuerInvoiceReference);
        optional(rows,"Payment terms",d.issuerPaymentTerms);
        optional(rows,"Tax/legal note",d.issuerTaxNote);
        optional(rows,"Footer note",d.issuerFooterNote);
        optional(rows,"Notes",d.notes);
        optional(rows,"Business unit",d.issuerBusinessUnit);
        optional(rows,"Operator",d.issuerOperator);
        rows.add("Fakturiva");
        rows.add(BrandAttribution.FOOTER);
        return rows;
    }
    private static String amount(long cents,String currency){
        return BillingCurrency.format(cents,currency,Locale.ENGLISH);
    }
    private static void rasterLine(OutputStream out,String raw,int widthDots,
                                   boolean smallFooter) throws IOException{
        String text=raw==null?"":raw.replace('\r',' ').replace('\n',' ');
        // Keep each raster allocation bounded; never omit a long text field.
        int offset=0;
        do{
            int next=offset;
            int codepoints=0;
            while(next<text.length()&&codepoints<160){
                next+=Character.charCount(text.codePointAt(next));
                codepoints++;
            }
            String part=text.substring(offset,next);
            TextPaint paint=new TextPaint(3);
            paint.setColor(Color.BLACK);
            paint.setTextSize(smallFooter?
                (widthDots==PAPER_58_DOTS?16f:18f):
                (widthDots==PAPER_58_DOTS?24f:27f));
            int margin=widthDots==PAPER_58_DOTS?10:14;
            int contentWidth=widthDots-margin*2;
            StaticLayout layout=StaticLayout.Builder.obtain(part,0,part.length(),
                paint,contentWidth)
                .setAlignment(smallFooter?
                    Layout.Alignment.ALIGN_CENTER:Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
            int height=Math.max(28,layout.getHeight()+12);
            if(height>2048)throw new IOException("Thermal line too tall; export PDF instead");
            Bitmap bitmap=Bitmap.createBitmap(widthDots,height,Bitmap.Config.ARGB_8888);
            try{
                bitmap.eraseColor(Color.WHITE);
                Canvas canvas=new Canvas(bitmap);
                canvas.translate(margin,5);
                layout.draw(canvas);
                int[] pixels=new int[widthDots*height];
                bitmap.getPixels(pixels,0,widthDots,0,0,widthDots,height);
                int bytesPerRow=widthDots/8;
                // GS v 0 raster format, mode 0, packed MSB-first.
                out.write(new byte[]{0x1d,0x76,0x30,0,
                    (byte)(bytesPerRow&255),(byte)(bytesPerRow>>8),
                    (byte)(height&255),(byte)(height>>8)});
                byte[] row=new byte[bytesPerRow];
                for(int y=0;y<height;y++){
                    java.util.Arrays.fill(row,(byte)0);
                    for(int x=0;x<widthDots;x++){
                        int color=pixels[y*widthDots+x];
                        int lum=(Color.red(color)*299+Color.green(color)*587+
                            Color.blue(color)*114)/1000;
                        if(lum<170)row[x>>3]|=(byte)(0x80>>(x&7));
                    }
                    out.write(row);
                }
            }finally{bitmap.recycle();}
            offset=next;
        }while(offset<text.length());
    }
    public static void write(OutputStream out,Store.Document d,int widthDots)
            throws IOException{
        if(out==null)throw new IllegalArgumentException("Output required");
        if(!supports(widthDots))
            throw new IllegalArgumentException("Supported raster widths: 384, 512, 576 dots");
        List<String> rows=compose(d);
        if(rows.size()>MAX_ROWS)throw new IOException("Receipt too long; print PDF instead");
        out.write(new byte[]{0x1b,0x40}); // ESC @ initialize; no drawer kick
        for(String line:rows)
            rasterLine(out,line,widthDots,BrandAttribution.FOOTER.equals(line));
        out.write(new byte[]{0x1b,0x64,0x04}); // feed; no auto cut
        out.flush();
    }
}
