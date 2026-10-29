package com.brendigo.fakturiva;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

/** Local, UTF-8 Croatian Excel-friendly payment CSV export. */
public final class PaymentsCsv {
    private PaymentsCsv(){}

    static String cell(String raw){
        String value=raw==null?"":raw.replace("\r"," ").replace("\n"," ");
        // Prevent spreadsheet formula injection via client names and identifiers.
        // Some spreadsheet importers ignore leading whitespace before a formula.
        int position=0;
        while(position<value.length() && Character.isWhitespace(value.charAt(position)))
            position++;
        if(position<value.length() && "=+-@".indexOf(value.charAt(position))>=0)
            value="'"+value;
        else if(!value.isEmpty() && value.charAt(0)=='\t')
            value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    /** A null month includes all transactions; other values select the shown month. */
    public static List<Store.Payment> forMonth(List<Store.Payment> payments,String yearMonth){
        if(yearMonth!=null && !yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Neispravan mjesec izvoza");
        List<Store.Payment> result=new ArrayList<>();
        for(Store.Payment payment:payments){
            if(yearMonth==null || (payment.paidOn!=null && payment.paidOn.startsWith(yearMonth)))
                result.add(payment);
        }
        return result;
    }
    public static void write(List<Store.Payment> payments,OutputStream output) throws IOException{
        // UTF-8 BOM is intentional for desktop Excel's Croatian locale.
        output.write(new byte[]{(byte)0xef,(byte)0xbb,(byte)0xbf});
        Writer out=new OutputStreamWriter(output,StandardCharsets.UTF_8);
        out.write("Broj računa;Klijent;Datum uplate;Iznos EUR\r\n");
        for(Store.Payment p:payments){
            out.write(cell(p.invoiceNumber)+";"+cell(p.clientName)+";"+
                cell(p.paidOn)+";"+cell(java.math.BigDecimal.valueOf(p.amountCents,2).toPlainString().replace('.',','))+"\r\n");
        }
        out.flush();
    }
}
