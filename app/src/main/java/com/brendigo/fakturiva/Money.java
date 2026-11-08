package com.brendigo.fakturiva;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Monetary operations in integer euro cents; quantity is in thousandths. */
public final class Money {
    private Money() {}
    public static long cents(String input) {
        String s = input.trim().replace("€", "").replace(" ", "").replace("\u00a0", "");
        if (s.contains(",")) s = s.replace(".", "").replace(',', '.');
        return new BigDecimal(s).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    public static long quantity(String input) {
        String s = input.trim().replace(',', '.');
        long q = new BigDecimal(s).movePointRight(3).setScale(0, RoundingMode.HALF_UP).longValueExact();
        if (q <= 0 || q > 1000000000L) throw new IllegalArgumentException("Neispravna količina");
        return q;
    }
    public static long subtotal(long qtyMilli, long priceCents) {
        if (qtyMilli <= 0 || priceCents < 0) throw new IllegalArgumentException("Neispravna stavka");
        return BigDecimal.valueOf(qtyMilli).multiply(BigDecimal.valueOf(priceCents))
            .divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValueExact();
    }
    /**
     * Legacy rate codes 0–100 represent whole percentage points. Extended
     * codes 10001–20000 represent 0.01%–100% in basis points, offset by
     * 10000 so every existing historical value remains unambiguous.
     */
    public static int parseRate(String input) {
        if(input==null)throw new IllegalArgumentException("Enter a tax rate");
        String raw=input.trim().replace("%","").replace(',','.');
        BigDecimal rate=new BigDecimal(raw);
        if(rate.signum()<0||rate.compareTo(BigDecimal.valueOf(100))>0||
            rate.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("Tax rate must be from 0 to 100% with up to two decimals");
        if(rate.stripTrailingZeros().scale()<=0)return rate.intValueExact();
        return Math.addExact(10000,rate.movePointRight(2).intValueExact());
    }
    public static String rateLabel(int rateCode) {
        int bp=rateCode>=10000?rateCode-10000:Math.multiplyExact(rateCode,100);
        if(bp<0||bp>10000)throw new IllegalArgumentException("Invalid tax rate");
        return BigDecimal.valueOf(bp,2).stripTrailingZeros().toPlainString()+"%";
    }
    public static long tax(long netCents, int rateCode) {
        int bp=rateCode>=10000?rateCode-10000:Math.multiplyExact(rateCode,100);
        if(bp<0||bp>10000)throw new IllegalArgumentException("Invalid tax rate");
        return BigDecimal.valueOf(netCents).multiply(BigDecimal.valueOf(bp))
            .divide(BigDecimal.valueOf(10000),0,RoundingMode.HALF_UP).longValueExact();
    }
    public static String euros(long cents) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.forLanguageTag("hr-HR"));
        f.setMinimumFractionDigits(2); f.setMaximumFractionDigits(2);
        return f.format(BigDecimal.valueOf(cents, 2)) + " €";
    }
    public static String decimalQuantity(long milli) {
        return BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
