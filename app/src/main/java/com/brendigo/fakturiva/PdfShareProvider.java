package com.brendigo.fakturiva;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only access to one app-owned PDF, via a time-limited URI grant. */
public final class PdfShareProvider extends ContentProvider {
    public static final String AUTHORITY="com.brendigo.fakturiva.pdf";
    public static final Uri URI=Uri.parse("content://"+AUTHORITY+"/share.pdf");
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri){validate(uri);return "application/pdf";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException{
        validate(uri);
        if(!"r".equals(mode))throw new FileNotFoundException("Samo čitanje");
        File f=new File(getContext().getCacheDir(),"share.pdf");
        return ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);
    }
    private void validate(Uri uri) {
        if(!AUTHORITY.equals(uri.getAuthority())||!"/share.pdf".equals(uri.getPath()))
            throw new IllegalArgumentException("Nepoznat dokument");
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        validate(uri);
        String[] cols=projection==null?new String[]{"_display_name","_size"}:projection;
        MatrixCursor out=new MatrixCursor(cols);Object[] row=new Object[cols.length];
        for(int i=0;i<cols.length;i++){
            if("_display_name".equals(cols[i]))row[i]="Fakturiva.pdf";
            else if("_size".equals(cols[i]))row[i]=new File(getContext().getCacheDir(),"share.pdf").length();
        }
        out.addRow(row);return out;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException();}
}
