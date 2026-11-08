package com.brendigo.fakturiva;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Android print services handle USB/Wi-Fi/network/other printers. The user
 * chooses the destination in the system dialog; Fakturiva does not scan
 * networks, collect printer credentials or send data to a remote server.
 */
public final class SystemInvoicePrinter {
    public static final int A4=0, LETTER=1, A5=2, LEGAL=3, A3=4, A6=5;
    private SystemInvoicePrinter(){}

    public static String[] formats(){
        return new String[]{"A4 · Android printer","US Letter · Android printer",
            "A5 · Android printer","US Legal · Android printer",
            "A3 · Android printer","A6 · Android printer"};
    }
    public static boolean supports(int format){return format>=A4&&format<=A6;}
    static int[] pdfSize(int format){
        switch(format){
            case LETTER:return new int[]{612,792};
            case A5:return new int[]{420,595};
            case LEGAL:return new int[]{612,1008};
            case A3:return new int[]{842,1191};
            case A6:return new int[]{298,420};
            case A4:return new int[]{595,842};
            default:throw new IllegalArgumentException("Unknown paper size");
        }
    }
    private static PrintAttributes.MediaSize media(int format){
        switch(format){
            case LETTER:return PrintAttributes.MediaSize.NA_LETTER;
            case A5:return PrintAttributes.MediaSize.ISO_A5;
            case LEGAL:return PrintAttributes.MediaSize.NA_LEGAL;
            case A3:return PrintAttributes.MediaSize.ISO_A3;
            case A6:return PrintAttributes.MediaSize.ISO_A6;
            case A4:
            default:return PrintAttributes.MediaSize.ISO_A4;
        }
    }
    public static void print(Activity activity,Store.Document d,int paper){
        if(activity==null||d==null||!supports(paper))
            throw new IllegalArgumentException("Invalid document or paper format");
        PrintManager manager=(PrintManager)activity.getSystemService(Context.PRINT_SERVICE);
        if(manager==null)throw new IllegalStateException("Android print service unavailable");
        String fileName="Fakturiva-"+(d.number==null?"draft-"+d.id:d.number)+".pdf";
        int[] pdfDimensions=pdfSize(paper);
        PrintDocumentAdapter adapter=new PrintDocumentAdapter(){
            @Override public void onLayout(PrintAttributes oldAttributes,
                PrintAttributes newAttributes,CancellationSignal cancellation,
                LayoutResultCallback callback,Bundle extras){
                if(cancellation.isCanceled()){callback.onLayoutCancelled();return;}
                callback.onLayoutFinished(new PrintDocumentInfo.Builder(fileName)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build(),true);
            }
            @Override public void onWrite(PageRange[] pages,ParcelFileDescriptor destination,
                CancellationSignal cancellation,WriteResultCallback callback){
                if(cancellation.isCanceled()){callback.onWriteCancelled();return;}
                new Thread(()->{
                    try(OutputStream out=new FileOutputStream(destination.getFileDescriptor())){
                        // PdfMaker uses only the saved document snapshot.
                        PdfMaker.write(null,d,out,pdfDimensions[0],pdfDimensions[1]);
                        out.flush();
                    }catch(Exception e){
                        callback.onWriteFailed("The invoice PDF could not be prepared");
                        try{destination.close();}catch(Exception ignored){}
                        return;
                    }
                    try{
                        // Notify the Android spooler only once the output
                        // stream is flushed AND closed.
                        if(cancellation.isCanceled())callback.onWriteCancelled();
                        else callback.onWriteFinished(new PageRange[]{PageRange.ALL_PAGES});
                    }finally{
                        try{destination.close();}catch(Exception ignored){}
                    }
                },"Fakturiva-SystemPrint").start();
            }
        };
        PrintAttributes attributes=new PrintAttributes.Builder()
            .setMediaSize(media(paper))
            .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build();
        manager.print("Fakturiva "+(d.number==null?"Draft":d.number),adapter,attributes);
    }
}
