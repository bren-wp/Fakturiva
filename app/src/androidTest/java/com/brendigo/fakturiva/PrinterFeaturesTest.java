package com.brendigo.fakturiva;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Exercises real receipt rasterization without requiring a physical printer. */
public final class PrinterFeaturesTest extends FakturivaTestCase {
    private static Store.Document sample(){
        Store.Document d=new Store.Document();
        d.id=19;d.type=Store.INVOICE;d.status=Store.ISSUED;
        d.number="INV-2026-0019";d.currencyCode="EUR";
        d.issuerName="Obrt Čekić / GmbH";
        d.issuerLegalForm="GmbH";d.issuerCountry="Deutschland";
        d.issuerVatId="DE123456789";
        d.clientName="Željko Šimić";
        d.clientAddress="Ulica 12, Zagreb";
        d.issueDate="2026-11-08";d.dueDate="2026-12-08";
        d.issuerIban="DE89370400440532013000";
        d.issuerBankRoutingNumber="021000021";
        d.issuerPaymentTerms="Net 30";
        Store.Item item=new Store.Item();
        item.name="Usluga – ümlaut, 東京, العربية";
        item.qtyMilli=2000;item.unitCents=12345;item.taxRate=0;
        d.items.add(item);
        d.netCents=24690;d.taxCents=0;d.totalCents=24690;
        return d;
    }
    @Test public void testFormatsAndDocumentContents(){
        assertTrue(ThermalReceipt.supports(384));
        assertTrue(ThermalReceipt.supports(576));
        assertTrue(ThermalReceipt.supports(512));
        assertFalse(ThermalReceipt.supports(448));
        assertEquals(6,SystemInvoicePrinter.formats().length);
        assertTrue(SystemInvoicePrinter.supports(SystemInvoicePrinter.A3));
        assertTrue(SystemInvoicePrinter.supports(SystemInvoicePrinter.A6));
        assertTrue(SystemInvoicePrinter.supports(SystemInvoicePrinter.A4));
        assertTrue(SystemInvoicePrinter.supports(SystemInvoicePrinter.LEGAL));
        assertFalse(SystemInvoicePrinter.supports(-1));
        List<String> rows=ThermalReceipt.compose(sample());
        assertTrue(rows.contains("VAT ID: DE123456789"));
        assertTrue(rows.contains("Bank account: ")==false);
        assertTrue(rows.contains("Customer: Željko Šimić"));
        assertTrue(rows.contains("TOTAL: 246.90 EUR"));
        assertTrue(rows.contains("Payment terms: Net 30"));
        assertTrue(rows.contains("Routing/ABA: 021000021"));
        assertEquals(BrandAttribution.FOOTER,rows.get(rows.size()-1));
        assertEquals("info@plati.com.hr",BrandAttribution.SUPPORT_EMAIL);
    }
    @Test public void testRasterStreamsForBothPaperWidths() throws Exception{
        for(int dots:new int[]{384,512,576}){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            ThermalReceipt.write(out,sample(),dots);
            byte[] bytes=out.toByteArray();
            assertTrue(bytes.length>10000);
            assertEquals(0x1b,bytes[0]&255);
            assertEquals(0x40,bytes[1]&255);
            assertEquals(0x1d,bytes[2]&255);
            assertEquals(0x76,bytes[3]&255);
            assertEquals(0x30,bytes[4]&255);
            assertEquals(dots/8,bytes[6]&255);
            assertEquals(0x1b,bytes[bytes.length-3]&255);
            assertEquals(0x64,bytes[bytes.length-2]&255);
            assertEquals(4,bytes[bytes.length-1]&255);
        }
    }
    @Test public void testPdfPagesUseActualRequestedPaperDimensions()throws Exception{
        int[][] papers={{595,842},{420,595},{612,792},{612,1008},
            {842,1191},{298,420}};
        for(int[] paper:papers){
            java.io.File file=java.io.File.createTempFile(
                "fakturiva-paper-",".pdf",getContext().getCacheDir());
            try{
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){
                    PdfMaker.write(null,sample(),out,paper[0],paper[1]);
                }
                try(android.os.ParcelFileDescriptor fd=android.os.ParcelFileDescriptor.open(
                        file,android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                    android.graphics.pdf.PdfRenderer pdf=new android.graphics.pdf.PdfRenderer(fd)){
                    assertTrue(pdf.getPageCount()>0);
                    try(android.graphics.pdf.PdfRenderer.Page page=pdf.openPage(0)){
                        assertEquals(paper[0],page.getWidth());
                        assertEquals(paper[1],page.getHeight());
                    }
                }
            }finally{file.delete();}
        }
    }
    @Test public void testUnsupportedPaperFailsInsteadOfSendingUnspecifiedCommands(){
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try{
            ThermalReceipt.write(out,sample(),448);
            fail("Unknown width must be blocked");
        }catch(IllegalArgumentException expected){
            assertEquals(0,out.size());
        }catch(IOException e){fail(e.getMessage());}
    }
    @Test public void testPrivateLanIpv4NeverTargetsThePublicInternet(){
        byte[] ip=NetworkReceiptPrinter.privateIpv4("192.168.1.50");
        assertEquals(4,ip.length);
        assertEquals((byte)192,ip[0]);
        assertEquals((byte)50,ip[3]);
        NetworkReceiptPrinter.privateIpv4("10.0.0.45");
        NetworkReceiptPrinter.privateIpv4("172.20.10.4");
        assertEquals(9100,NetworkReceiptPrinter.validPort(9100));
        for(String address:new String[]{"8.8.8.8","1.1.1.1","127.0.0.1",
                "192.168.1.255","192.168.1.0","localhost",
                "printer.example.org","192.168.1.50:9100",
                "192.168.1.50/24","192.168.01.50","172.32.1.9",
                "169.254.1.2","0.0.0.0","255.255.255.255"}){
            try{
                NetworkReceiptPrinter.privateIpv4(address);
                fail("Must block invalid or nonprivate printer: "+address);
            }catch(IllegalArgumentException expected){}
        }
        for(int port:new int[]{0,80,443,1023,65536}){
            try{
                NetworkReceiptPrinter.validPort(port);
                fail("Must block unsafe or out-of-range port");
            }catch(IllegalArgumentException expected){}
        }
    }
    @Test public void testUsbPrinterClassAndEndpointSafety(){
        assertTrue(UsbReceiptPrinter.acceptsInterfaceClass(
            android.hardware.usb.UsbConstants.USB_CLASS_PRINTER));
        assertFalse(UsbReceiptPrinter.acceptsInterfaceClass(
            android.hardware.usb.UsbConstants.USB_CLASS_MASS_STORAGE));
        assertFalse(UsbReceiptPrinter.acceptsInterfaceClass(
            android.hardware.usb.UsbConstants.USB_CLASS_VENDOR_SPEC));
        assertTrue(UsbReceiptPrinter.isBulkOut(
            android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK,
            android.hardware.usb.UsbConstants.USB_DIR_OUT));
        assertFalse(UsbReceiptPrinter.isBulkOut(
            android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_INT,
            android.hardware.usb.UsbConstants.USB_DIR_OUT));
        assertFalse(UsbReceiptPrinter.isBulkOut(
            android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK,
            android.hardware.usb.UsbConstants.USB_DIR_IN));
        assertFalse(UsbReceiptPrinter.supports(null));
    }

    @Test public void testVeryLongReceiptDoesNotSilentlyTruncate()throws Exception{
        Store.Document d=sample();
        for(int i=0;i<850;i++){
            Store.Item row=new Store.Item();
            row.name="Service "+i;row.qtyMilli=1000;row.unitCents=100;row.taxRate=0;
            d.items.add(row);
        }
        try{
            ThermalReceipt.write(new ByteArrayOutputStream(),d,384);
            fail("Must reject excessively long receipts rather than omit line items");
        }catch(IOException expected){
            assertTrue(expected.getMessage().contains("PDF"));
        }
    }
}
