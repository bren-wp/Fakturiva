package com.brendigo.fakturiva;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit, user-selected USB printer-class ESC/POS transport.
 *
 * No mass USB probing, vendor-specific USB endpoint guessing, cash-drawer commands,
 * background printing or automatic retry. The user must own/approve USB permission.
 * Sending bytes is not proof that a paper receipt was physically printed.
 */
public final class UsbReceiptPrinter {
    private static final int TIMEOUT_MS = 5000;
    private static final int BULK_CHUNK = 4096;
    private static final String PERMISSION_ACTION =
        "com.brendigo.fakturiva.action.USB_RECEIPT_PRINTER_PERMISSION";

    private UsbReceiptPrinter() {}

    public interface Callback {
        /** null if data was sent to the USB endpoint; not a hardware print confirmation. */
        void onFinished(String error);
    }

    static boolean acceptsInterfaceClass(int classId) {
        return classId == UsbConstants.USB_CLASS_PRINTER;
    }

    static boolean isBulkOut(int type, int direction) {
        return type == UsbConstants.USB_ENDPOINT_XFER_BULK
            && direction == UsbConstants.USB_DIR_OUT;
    }

    private static UsbEndpoint outEndpoint(UsbInterface intf) {
        if (!acceptsInterfaceClass(intf.getInterfaceClass())) return null;
        for (int i = 0; i < intf.getEndpointCount(); i++) {
            UsbEndpoint endpoint = intf.getEndpoint(i);
            if (isBulkOut(endpoint.getType(), endpoint.getDirection())) return endpoint;
        }
        return null;
    }

    private static UsbInterface printerInterface(UsbDevice device) {
        if (device == null) return null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface candidate = device.getInterface(i);
            if (outEndpoint(candidate) != null) return candidate;
        }
        return null;
    }

    /** Only USB Printer Class devices exposing a bulk OUT endpoint are shown. */
    public static boolean supports(UsbDevice device) {
        return printerInterface(device) != null;
    }

    public static List<UsbDevice> available(UsbManager manager) {
        List<UsbDevice> devices = new ArrayList<>();
        if (manager != null) {
            for (UsbDevice device : manager.getDeviceList().values()) {
                if (supports(device)) devices.add(device);
            }
        }
        return devices;
    }

    public static void send(Activity activity, UsbDevice device, Store.Document document,
                            int paperDots, Callback callback) {
        if (activity == null || document == null || callback == null
                || !ThermalReceipt.supports(paperDots)) {
            throw new IllegalArgumentException("Activity, invoice, width and callback required");
        }
        if (!supports(device)) {
            callback.onFinished("The selected USB device has no printer-class bulk OUT endpoint.");
            return;
        }
        UsbManager manager = (UsbManager) activity.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            callback.onFinished("USB host service is unavailable.");
            return;
        }
        if (manager.hasPermission(device)) {
            sendWithPermission(activity, manager, device, document, paperDots, callback);
            return;
        }

        Intent permissionIntent = new Intent(PERMISSION_ACTION)
            .setPackage(activity.getPackageName());
        IntentFilter filter = new IntentFilter(PERMISSION_ACTION);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!PERMISSION_ACTION.equals(intent.getAction())) return;
                try { activity.unregisterReceiver(this); }
                catch (IllegalArgumentException ignored) {}
                // Never trust the broadcast extra alone. Re-check OS permission and
                // original selected device before touching any USB endpoint.
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        && manager.hasPermission(device) && supports(device)) {
                    sendWithPermission(activity, manager, device, document, paperDots, callback);
                } else {
                    callback.onFinished("USB printer access was denied or the printer was removed.");
                }
            }
        };
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerLegacyReceiver(activity, receiver, filter);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            // UsbManager adds EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED to this result.
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pending = PendingIntent.getBroadcast(
                activity, 0, permissionIntent, flags);
            manager.requestPermission(device, pending);
        } catch (Exception failure) {
            try { activity.unregisterReceiver(receiver); }
            catch (IllegalArgumentException ignored) {}
            callback.onFinished("Could not request permission for the selected USB printer.");
        }
    }

    /** Before API 33 Android has no registerReceiver(receiver, filter, flags) overload. */
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerLegacyReceiver(Activity activity,
                                               BroadcastReceiver receiver, IntentFilter filter) {
        activity.registerReceiver(receiver, filter);
    }

    private static void sendWithPermission(Activity activity, UsbManager manager,
                                           UsbDevice device, Store.Document document,
                                           int paperDots, Callback callback) {
        new Thread(() -> {
            String error = null;
            UsbDeviceConnection connection = null;
            UsbInterface intf = printerInterface(device);
            boolean claimed = false;
            try {
                if (intf == null || !manager.hasPermission(device)) {
                    throw new IOException("Printer disconnected or permission revoked");
                }
                connection = manager.openDevice(device);
                if (connection == null) throw new IOException("Could not open USB printer");
                claimed = connection.claimInterface(intf, true);
                if (!claimed) throw new IOException("Could not claim USB printer interface");
                UsbEndpoint endpoint = outEndpoint(intf);
                try (OutputStream out = new BufferedOutputStream(
                        new BulkOutputStream(connection, endpoint), 8192)) {
                    ThermalReceipt.write(out, document, paperDots);
                    out.flush();
                }
            } catch (Exception e) {
                // A failed transfer may still have printed part of the document.
                error = "USB print interrupted. Check for a partial receipt before retrying.";
            } finally {
                if (connection != null) {
                    if (claimed) {
                        try { connection.releaseInterface(intf); }
                        catch (Exception ignored) {}
                    }
                    connection.close();
                }
            }
            final String result = error;
            activity.runOnUiThread(() -> callback.onFinished(result));
        }, "Fakturiva-USB-ESC-POS").start();
    }

    private static final class BulkOutputStream extends OutputStream {
        private final UsbDeviceConnection connection;
        private final UsbEndpoint endpoint;

        BulkOutputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
            if (connection == null || endpoint == null)
                throw new IllegalArgumentException("USB connection and OUT endpoint required");
            this.connection = connection;
            this.endpoint = endpoint;
        }
        @Override public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (data == null || offset < 0 || length < 0
                    || offset > data.length - length) {
                throw new IndexOutOfBoundsException("Invalid USB print buffer");
            }
            while (length > 0) {
                int size = Math.min(length, BULK_CHUNK);
                int sent = connection.bulkTransfer(endpoint, data, offset, size, TIMEOUT_MS);
                if (sent <= 0 || sent > size) throw new IOException("USB printer write failed");
                offset += sent;
                length -= sent;
            }
        }
    }
}
