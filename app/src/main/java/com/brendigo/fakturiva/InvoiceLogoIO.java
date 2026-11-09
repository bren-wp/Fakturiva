package com.brendigo.fakturiva;

import android.content.Context;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;

/** Content URI upload with bounded decode and no media permissions. */
public final class InvoiceLogoIO {
    private InvoiceLogoIO(){}
    public static Bitmap thumbnail(byte[] content){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(content,0,content.length,options);
        if(options.outWidth<1||options.outHeight<1||
           (long)options.outWidth*options.outHeight>24_000_000L)
            throw new IllegalArgumentException("Dimenzije logotipa nisu podržane.");
        int sample=1;
        while(options.outWidth/sample>512||options.outHeight/sample>512)sample*=2;
        BitmapFactory.Options scaled=new BitmapFactory.Options();
        scaled.inSampleSize=sample;
        Bitmap image=BitmapFactory.decodeByteArray(content,0,content.length,scaled);
        if(image==null)throw new IllegalArgumentException("Neispravna datoteka logotipa.");
        return image;
    }
    public static byte[] prepare(Context context,Uri uri)throws Exception{
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalStateException("Datoteka logotipa nije dostupna.");
            byte[] block=new byte[8192];int n;
            while((n=in.read(block))!=-1){
                if(buffer.size()+n>2_000_000)
                    throw new IllegalArgumentException("Logotip mora biti do 2 MB.");
                buffer.write(block,0,n);
            }
        }
        byte[] original=buffer.toByteArray();
        try{
            Bitmap bitmap=thumbnail(original);
            try{
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                Bitmap.CompressFormat format=bitmap.hasAlpha()?
                    Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG;
                if(!bitmap.compress(format,83,output)||output.size()>180000)
                    throw new IllegalArgumentException(
                        "Logo je prevelik. Odaberite jednostavniji PNG ili JPEG.");
                return output.toByteArray();
            }finally{bitmap.recycle();}
        }finally{Arrays.fill(original,(byte)0);}
    }
}
