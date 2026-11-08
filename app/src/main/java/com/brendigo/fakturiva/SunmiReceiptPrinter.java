package com.brendigo.fakturiva;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import com.sunmi.peripheral.printer.InnerPrinterCallback;
import com.sunmi.peripheral.printer.InnerPrinterException;
import com.sunmi.peripheral.printer.InnerPrinterManager;
import com.sunmi.peripheral.printer.SunmiPrinterService;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Native SUNMI built-in printer SDK (not a card-payment SDK).
 * Only runs after an explicit user choice on a SUNMI device.
 *
 * A successful SDK call only means that the print service accepted the bytes;
 * it does not prove successful paper output. No automatic retry or drawer kick.
 */
public final class SunmiReceiptPrinter {
    private static final int BINDER_CHUNK = 8192;
    private static final long CONNECT_TIMEOUT_MS = 10_000L;

    private SunmiReceiptPrinter() {}

    public interface Callback {
        /** null means bytes were submitted to the service; not physical print confirmation. */
        void onFinished(String error);
    }

    public static boolean isSunmiDevice() {
        return android.os.Build.MANUFACTURER != null
            && android.os.Build.MANUFACTURER.toLowerCase(Locale.ROOT).contains("sunmi");
    }

    public static void send(Activity activity, Store.Document document, Callback callback) {
        if (activity == null || document == null || callback == null)
            throw new IllegalArgumentException("Activity, document and callback required");
        if (!isSunmiDevice()) {
            callback.onFinished("The built-in SUNMI printer is unavailable on this device.");
            return;
        }
        final android.content.Context app = activity.getApplicationContext();
        final AtomicBoolean finished = new AtomicBoolean(false);
        final AtomicBoolean connected = new AtomicBoolean(false);
        final AtomicBoolean intentionalUnbind = new AtomicBoolean(false);
        final Handler ui = new Handler(Looper.getMainLooper());
        final InnerPrinterManager manager = InnerPrinterManager.getInstance();

        final InnerPrinterCallback binding = new InnerPrinterCallback() {
            private void result(String message) {
                if (finished.compareAndSet(false, true))
                    ui.post(() -> callback.onFinished(message));
            }
            private void unbind() {
                intentionalUnbind.set(true);
                try { manager.unBindService(app, this); }
                catch (InnerPrinterException | RuntimeException ignored) {}
            }
            @Override protected void onConnected(SunmiPrinterService service) {
                connected.set(true);
                if (finished.get()) { unbind(); return; }
                new Thread(() -> {
                    String error = null;
                    try {
                        if (!manager.hasPrinter(service))
                            throw new IOException("No integrated thermal printer is available.");
                        int paper = service.getPrinterPaper();
                        int dots;
                        if (paper == 1) dots = ThermalReceipt.PAPER_58_DOTS;
                        else if (paper == 2) dots = ThermalReceipt.PAPER_80_DOTS;
                        else throw new IOException("Unknown SUNMI paper width; printing blocked.");
                        try (OutputStream out = new BufferedOutputStream(
                                new ServiceOutputStream(service), BINDER_CHUNK)) {
                            ThermalReceipt.write(out, document, dots);
                            out.flush();
                        }
                    } catch (Exception e) {
                        error = "SUNMI printing failed. Check for partial output before retrying.";
                    } finally {
                        unbind();
                    }
                    result(error);
                }, "Fakturiva-SUNMI-Printers").start();
            }
            @Override protected void onDisconnected() {
                if (!intentionalUnbind.get())
                    result("SUNMI print service disconnected. Check for partial output.");
            }
        };
        try {
            boolean started = manager.bindService(app, binding);
            if (!started) {
                callback.onFinished("The SUNMI printer service is not available.");
                return;
            }
            // An unresponsive service must not leave the user in a silent state.
            ui.postDelayed(() -> {
                if (!connected.get() && finished.compareAndSet(false, true)) {
                    intentionalUnbind.set(true);
                    try { manager.unBindService(app, binding); }
                    catch (InnerPrinterException | RuntimeException ignored) {}
                    callback.onFinished("SUNMI printer connection timed out.");
                }
            }, CONNECT_TIMEOUT_MS);
        } catch (InnerPrinterException | RuntimeException error) {
            callback.onFinished("Could not connect to the SUNMI printer service.");
        }
    }

    /** Chunk requests under Binder's per-transaction limit; preserves FIFO send order. */
    private static final class ServiceOutputStream extends OutputStream {
        private final SunmiPrinterService service;
        ServiceOutputStream(SunmiPrinterService service) {
            if (service == null) throw new IllegalArgumentException("Service required");
            this.service = service;
        }
        @Override public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null || offset < 0 || length < 0
                    || offset > bytes.length - length)
                throw new IndexOutOfBoundsException("Invalid SUNMI print buffer");
            while (length > 0) {
                int len = Math.min(length, BINDER_CHUNK);
                byte[] chunk = new byte[len];
                System.arraycopy(bytes, offset, chunk, 0, len);
                try { service.sendRAWData(chunk, null); }
                catch (RemoteException e) {
                    throw new IOException("SUNMI printer service rejected data", e);
                }
                offset += len;
                length -= len;
            }
        }
    }
}
