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
    public static long tax(long netCents, int rate) {
        if (rate != 0 && rate != 5 && rate != 13 && rate != 25)
            throw new IllegalArgumentException("Neispravna stopa PDV-a");
        return BigDecimal.valueOf(netCents).multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
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
