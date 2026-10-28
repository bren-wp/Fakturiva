package com.brendigo.fakturiva;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Local, UTF-8 Croatian Excel-friendly payment CSV export. */
public final class PaymentsCsv {
    private PaymentsCsv(){}

    static String cell(String raw){
        String value=raw==null?"":raw.replace("\r"," ").replace("\n"," ");
        // Prevent spreadsheet formula injection via client names and identifiers.
        if(!value.isEmpty() && ("=+-@".indexOf(value.charAt(0))>=0 ||
            value.charAt(0)=='\t'))value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
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
