package com.brendigo.fakturiva;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * USB Host / USB Printer Class (interface class 7) -> raw ESC/POS raster.
 *
 * This is NOT an acquirer POS terminal API and deliberately refuses unknown
 * vendor-specific USB interfaces to avoid sending invoice data to a payment
 * terminal, storage device or another unrelated peripheral.
 */
public final class UsbReceiptPrinter {
    private static final String ACTION_PERMISSION =
        "com.brendigo.fakturiva.action.USB_PRINTER_PERMISSION";
    private static final String EXTRA_JOB = "job";
    private static final int TIMEOUT_MS = 5000;
    private static final AtomicInteger IDS = new AtomicInteger(1000);
    private static final ConcurrentHashMap<Integer, Job> PENDING =
        new ConcurrentHashMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(
        runnable -> {
            Thread thread = new Thread(runnable, "FakturivaUsbPrint");
            thread.setDaemon(true);
            return thread;
        });

    private UsbReceiptPrinter() {}

    private static final class Port {
        final UsbInterface intf;
        final UsbEndpoint endpoint;
        Port(UsbInterface intf, UsbEndpoint endpoint) {
            this.intf = intf; this.endpoint = endpoint;
        }
    }

    /** Package-visible selectors allow tests to verify safety without hardware. */
    static boolean acceptsInterfaceClass(int deviceClass) {
        return deviceClass == UsbConstants.USB_CLASS_PRINTER;
    }

    static boolean isBulkOut(int type, int direction) {
        return type == UsbConstants.USB_ENDPOINT_XFER_BULK
            && direction == UsbConstants.USB_DIR_OUT;
    }

    static boolean supports(UsbDevice device) {
        return printerPort(device) != null;
    }

    private static Port printerPort(UsbDevice device) {
        if (device == null) return null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (!acceptsInterfaceClass(intf.getInterfaceClass()))
                continue;
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint ep = intf.getEndpoint(j);
                if (isBulkOut(ep.getType(), ep.getDirection()))
                    return new Port(intf, ep);
            }
        }
        return null;
    }

    /** USB enumeration is local and does not request access to a device. */
    public static List<UsbDevice> discover(Context context) {
        UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (manager == null) return Collections.emptyList();
        ArrayList<UsbDevice> printers = new ArrayList<>();
        for (UsbDevice device : manager.getDeviceList().values()) {
            if (printerPort(device) != null) printers.add(device);
        }
        printers.sort(Comparator.comparingInt(UsbDevice::getVendorId)
            .thenComparingInt(UsbDevice::getProductId));
        return printers;
    }

    private static final class Job {
        final WeakReference<Activity> activity;
        final int deviceId;
        final Store.Document document;
        final int dots;
        final Consumer<String> result;
        Job(Activity activity, UsbDevice device, Store.Document document,
            int dots, Consumer<String> result) {
            this.activity = new WeakReference<>(activity);
            this.deviceId = device.getDeviceId();
            this.document = document;
            this.dots = dots;
            this.result = result;
        }
        void notifyResult(String message) {
            Activity current = activity.get();
            if (current != null && !current.isFinishing() && !current.isDestroyed())
                current.runOnUiThread(() -> result.accept(message));
        }
    }

    /**
     * User-initiated only. USB permission is requested from the OS for the
     * selected printer; no data is sent until permission is granted.
     */
    public static void send(Activity activity, UsbDevice device,
        Store.Document document, int dots, Consumer<String> result) {
        if (activity == null || document == null || result == null)
            throw new IllegalArgumentException("Missing print arguments");
        if (printerPort(device) == null) {
            result.accept("This USB device does not expose a USB Printer Class bulk endpoint.");
            return;
        }
        if (!ThermalReceipt.supports(dots)) {
            result.accept("Unsupported thermal paper width.");
            return;
        }
        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            result.accept("USB Host is unavailable on this Android device.");
            return;
        }
        Job job = new Job(activity, device, document, dots, result);
        try {
            if (manager.hasPermission(device)) {
                transfer(activity.getApplicationContext(), device, job);
                return;
            }
            // Limit pending hardware prompts; avoid retaining unbounded documents.
            if (PENDING.size() >= 2) {
                result.accept("Finish the existing USB printer permission request first.");
                return;
            }
            int id = IDS.incrementAndGet();
            Intent intent = new Intent(activity, PermissionReceiver.class)
                .setAction(ACTION_PERMISSION)
                .setData(Uri.parse("fakturiva-usb-permission://job/" + id))
                .putExtra(EXTRA_JOB, id);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            // UsbManager adds the permission result as fill-in extras.
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                activity, id, intent, flags);
            PENDING.put(id, job);
            try {
                manager.requestPermission(device, pendingIntent);
            } catch (RuntimeException failure) {
                PENDING.remove(id);
                throw failure;
            }
        } catch (SecurityException failure) {
            job.notifyResult("USB permission was denied. Nothing was printed.");
        } catch (RuntimeException failure) {
            job.notifyResult("Could not request access to the USB printer.");
        }
    }

    /** Explicit, non-exported receiver: never trust a device ID in a broadcast. */
    public static final class PermissionReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !ACTION_PERMISSION.equals(intent.getAction())) return;
            int id = intent.getIntExtra(EXTRA_JOB, -1);
            Job job = PENDING.remove(id);
            if (job == null) return; // Stale result after restart or duplicate
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            UsbManager manager =
                (UsbManager) context.getSystemService(Context.USB_SERVICE);
            boolean granted = intent.getBooleanExtra(
                UsbManager.EXTRA_PERMISSION_GRANTED, false);
            if (!granted || device == null || device.getDeviceId() != job.deviceId ||
                manager == null || !manager.hasPermission(device) ||
                printerPort(device) == null) {
                job.notifyResult("USB printer permission not granted; nothing was sent.");
                return;
            }
            transfer(context.getApplicationContext(), device, job);
        }
    }

    private static void transfer(Context app, UsbDevice device, Job job) {
        EXECUTOR.execute(() -> {
            UsbManager manager = (UsbManager) app.getSystemService(Context.USB_SERVICE);
            Port port = printerPort(device);
            if (manager == null || port == null) {
                job.notifyResult("USB printer disconnected before printing.");
                return;
            }
            UsbDeviceConnection connection = null;
            boolean claimed = false;
            try {
                connection = manager.openDevice(device);
                if (connection == null)
                    throw new IOException("Cannot open USB printer; check permission and cable");
                claimed = connection.claimInterface(port.intf, true);
                if (!claimed) throw new IOException("USB printer interface is busy");
                final UsbDeviceConnection usb = connection;
                try (EscPosChunkedOutputStream stream =
                    new EscPosChunkedOutputStream((data, offset, length) ->
                        usb.bulkTransfer(port.endpoint, data, offset, length, TIMEOUT_MS),
                        8192)) {
                    ThermalReceipt.write(stream, job.document, job.dots);
                }
                job.notifyResult("Print data sent over USB. Check the printer for completion.");
            } catch (Exception error) {
                job.notifyResult("USB print failed; a partial receipt may exist. " +
                    "Check the printer before manually retrying.");
            } finally {
                if (connection != null) {
                    if (claimed) {
                        try { connection.releaseInterface(port.intf); }
                        catch (RuntimeException ignored) { /* disconnected */ }
                    }
                    connection.close();
                }
            }
        });
    }
}
