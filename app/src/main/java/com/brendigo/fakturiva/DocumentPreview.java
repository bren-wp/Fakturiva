package com.brendigo.fakturiva;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Previews the actual PDF generated for the selected document, not a mockup.
 * Android SDK PdfRenderer is available on the app's minimum supported API.
 */
public final class DocumentPreview {
    private DocumentPreview() {}

    public static View firstPage(Activity activity, Store store, Store.Document document) throws Exception {
        File cache=File.createTempFile("fakturiva-preview-", ".pdf",activity.getCacheDir());
        Bitmap bitmap;
        try {
            try(FileOutputStream out=new FileOutputStream(cache)) {
                PdfMaker.write(store,document,out);
                out.getFD().sync();
            }
            try(ParcelFileDescriptor file=ParcelFileDescriptor.open(cache,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(file);
                PdfRenderer.Page page=renderer.openPage(0)) {
                int maxWidth=Math.min(950,Math.max(350,activity.getResources().getDisplayMetrics().widthPixels-40));
                int height=Math.max(1,(int)((long)maxWidth*page.getHeight()/page.getWidth()));
                bitmap=Bitmap.createBitmap(maxWidth,height,Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
        }finally{
            if(!cache.delete())cache.deleteOnExit();
        }
        ImageView image=new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.WHITE);
        image.setContentDescription("Stvarni pregled prve stranice PDF dokumenta");
        image.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        return image;
    }
}
