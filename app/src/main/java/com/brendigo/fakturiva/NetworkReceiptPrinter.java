package com.brendigo.fakturiva;

import android.app.Activity;
import android.content.Context;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Opt-in ESC/POS printing to a local-network printer's raw TCP socket.
 * Does not discover, scan, or contact printers automatically; never accepts
 * public Internet, hostname, loopback, broadcast or multicast destinations.
 */
public final class NetworkReceiptPrinter {
    private static final int CONNECT_TIMEOUT_MS=5000;
    private NetworkReceiptPrinter(){}

    public interface Callback {
        /** null if the bytes were delivered to the socket; paper output is not confirmed. */
        void onFinished(String error);
    }

    /**
     * Accepts IPv4 RFC1918 addresses only. Validating literal octets avoids
     * DNS rebinding and accidentally sending invoice data to a public server.
     */
    static byte[] privateIpv4(String candidate){
        if(candidate==null)throw new IllegalArgumentException("Printer IPv4 address is required");
        String host=candidate.trim();
        String[] parts=host.split("\\.",-1);
        if(parts.length!=4)throw new IllegalArgumentException(
            "Use a private IPv4 address, for example 192.168.1.50");
        int[] octet=new int[4];
        for(int i=0;i<4;i++){
            if(!parts[i].matches("(0|[1-9][0-9]{0,2})"))
                throw new IllegalArgumentException("Invalid printer IPv4 address");
            octet[i]=Integer.parseInt(parts[i]);
            if(octet[i]>255)throw new IllegalArgumentException("Invalid printer IPv4 address");
        }
        boolean local=octet[0]==10 ||
            (octet[0]==172 && octet[1]>=16 && octet[1]<=31) ||
            (octet[0]==192 && octet[1]==168);
        if(!local || octet[3]==0 || octet[3]==255)
            throw new IllegalArgumentException(
                "For privacy, only private LAN printer IPv4 addresses are allowed");
        return new byte[]{(byte)octet[0],(byte)octet[1],
            (byte)octet[2],(byte)octet[3]};
    }
    static int validPort(int port){
        if(port<1024||port>65535)
            throw new IllegalArgumentException("Printer TCP port must be 1024–65535");
        return port;
    }

    public static void send(Context context,String host,int port,
            Store.Document invoice,int widthDots,Callback callback){
        if(context==null||invoice==null||callback==null)
            throw new IllegalArgumentException("Context, invoice and callback required");
        if(!ThermalReceipt.supports(widthDots))
            throw new IllegalArgumentException("Unsupported thermal printer width");
        final byte[] ip=privateIpv4(host);
        final int checkedPort=validPort(port);
        new Thread(()->{
            String error=null;
            try(Socket socket=new Socket()){
                socket.connect(new InetSocketAddress(
                    InetAddress.getByAddress(ip),checkedPort),CONNECT_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                try(OutputStream out=socket.getOutputStream()){
                    ThermalReceipt.write(out,invoice,widthDots);
                    out.flush();
                }
            }catch(Exception e){
                // The printer might already have printed some data.
                // Never retry automatically and risk a duplicate invoice.
                error="Network printer failed or disconnected. Check for a partial print before retrying.";
            }
            final String result=error;
            if(context instanceof Activity)
                ((Activity)context).runOnUiThread(()->callback.onFinished(result));
            else callback.onFinished(result);
        },"Fakturiva-LAN-ESC-POS").start();
    }
}
