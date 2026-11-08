package com.brendigo.fakturiva;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/** JVM-only regression tests; no physical hardware or Android SDK required. */
public final class UsbPrinterChunkSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        byte[] expected = new byte[4191];
        for (int i = 0; i < expected.length; i++)
            expected[i] = (byte) (i * 17 + 13);
        ByteArrayOutputStream delivered = new ByteArrayOutputStream();
        int[] calls = {0};
        try (EscPosChunkedOutputStream stream =
            new EscPosChunkedOutputStream((bytes, offset, length) -> {
                int accepted = Math.min(7, length);
                delivered.write(bytes, offset, accepted);
                calls[0]++;
                return accepted;
            }, 127)) {
            stream.write(expected, 0, 100);
            for (int i = 100; i < expected.length; i++)
                stream.write(expected[i]);
        }
        check(Arrays.equals(expected, delivered.toByteArray()),
            "Partial bulk writes corrupted or duplicated print data");
        check(calls[0] > 100, "Partial transfer path not exercised");

        int[] attempts = {0};
        EscPosChunkedOutputStream broken = new EscPosChunkedOutputStream(
            (bytes, offset, length) -> { attempts[0]++; return 0; }, 32);
        broken.write(new byte[]{1, 2, 3});
        try { broken.flush(); throw new AssertionError("Zero progress must fail"); }
        catch (IOException expectedFailure) { /* no retry */ }
        broken.flush();
        check(attempts[0] == 1, "Uncertain partial print was automatically resent");
        broken.close();
        try { broken.write(12); throw new AssertionError("Write after close"); }
        catch (IOException expectedFailure) { /* correct */ }

        try {
            new EscPosChunkedOutputStream((b, o, l) -> l, 0);
            throw new AssertionError("Unbounded buffer configuration accepted");
        } catch (IllegalArgumentException expectedFailure) { /* correct */ }
        System.out.println("USB ESC/POS chunk and failure handling: OK");
    }
}
