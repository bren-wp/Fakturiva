package com.brendigo.fakturiva;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Exports exactly the selected month's factual accounting snapshot.
 * Amounts are integer euro cents to avoid locale-dependent parsing.
 * Never labels invoiced turnover as received revenue.
 */
public final class ReportCsv {
    private ReportCsv(){}

    public static void write(String yearMonth,long[] values,OutputStream output)
            throws IOException {
        write(yearMonth,values,"EUR",output);
    }
    public static void write(String yearMonth,long[] values,String currency,OutputStream output)
            throws IOException {
        if(yearMonth==null||!yearMonth.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw new IllegalArgumentException("Neispravan mjesec");
        if(values==null||values.length!=5)
            throw new IllegalArgumentException("Neispravan sažetak izvještaja");
        for(long amount:values)if(amount<0)
            throw new IllegalArgumentException("Negativan iznos u izvještaju");
        String code=BillingCurrency.canonical(currency);
        String csv="Mjesec;Fakturirano_"+code+"_centi;Izdani_racuni;Placeni_racuni;"+
            "Evidentirane_uplate_"+code+"_centi;Nenaplaceno_izdanih_racuna_"+code+"_centi\r\n"+
            yearMonth+";"+values[0]+";"+values[1]+";"+values[2]+";"+
            values[3]+";"+values[4]+"\r\n";
        output.write(csv.getBytes(StandardCharsets.UTF_8));
    }
}
