package com.brendigo.fakturiva;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.OutputStream;
import java.util.UUID;

/** A paired Bluetooth Classic SPP transport for ESC/POS thermal printers only. */
public final class BluetoothReceiptPrinter {
    private static final UUID SPP=UUID.fromString(
        "00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothReceiptPrinter(){}
    public interface Callback {
        /** null for successful transport, otherwise a user-friendly error. */
        void onFinished(String error);
    }
    public static void send(Context context,BluetoothDevice device,Store.Document document,
                            int paperDots,Callback callback){
        if(device==null||document==null||callback==null)
            throw new IllegalArgumentException("Printer, document and callback required");
        if(!ThermalReceipt.supports(paperDots))
            throw new IllegalArgumentException("Unsupported thermal paper width");
        // Android 12+ BLUETOOTH_CONNECT permission is explicitly user-approved.
        if(Build.VERSION.SDK_INT>=31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                !=PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Nearby devices permission is required");
        new Thread(()->{
            String error=null;
            try(BluetoothSocket socket=device.createRfcommSocketToServiceRecord(SPP)){
                socket.connect();
                OutputStream out=socket.getOutputStream();
                ThermalReceipt.write(out,document,paperDots);
                out.flush();
            }catch(SecurityException e){
                error="Bluetooth permission is missing";
            }catch(Exception e){
                // The device may have printed partially before a link failure.
                // Never auto-retry an invoice and risk printing duplicates.
                error="Printer connection or printing failed. Check the paper before retrying.";
            }
            final String result=error;
            if(context instanceof android.app.Activity)
                ((android.app.Activity)context).runOnUiThread(()->callback.onFinished(result));
            else callback.onFinished(result);
        },"Fakturiva-ESC-POS").start();
    }
}
