package com.brendigo.fakturiva;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Native screens, independent of external UI dependencies and any remote server. */
public final class MainActivity extends Activity {
    private static final int BG=0xfff6f8fc, WHITE=Color.WHITE, NAVY=0xff14263f,
        TEXT=0xff253449, MUTED=0xff8592a5, BLUE=0xff5566f8,
        TEAL=0xff2fd2c5, LINE=0xffe9edf5, RED=0xffdb4c56;
    private static final int PDF_EXPORT=101,BACKUP_EXPORT=102,BACKUP_IMPORT=103,DEVICE_AUTH_REQUEST=105;
    private static final int BLUETOOTH_PRINT_PERMISSION_REQUEST=108;
    private Store db; private SharedPreferences prefs;
    private String loginUsername="";
    private boolean selfHostedBusy=false;
    private final StringBuilder enteredLocalPin=new StringBuilder();
    private android.window.OnBackInvokedCallback systemBackCallback;
    private LinearLayout root,body; private ScrollView scroller; private FrameLayout contentFrame;
    private FrameLayout adaptiveNavContainer;
    private LinearLayout adaptiveNavBar;
    private int paymentFilter=0;
    private String pendingPaymentsCsvMonth=null,pendingReportCsvMonth=null;
    private final Calendar reportMonth=Calendar.getInstance();
    private String screen="home",filter="Svi",editType=Store.INVOICE;
    private String documentSearchQuery="";
    private boolean dark=false;
    // Debug-only reference captures must not alter the production EN locale.
    private boolean qaReferenceCroatian=false;
    // Cached reference-design typography; independent of network/services.
    private Typeface referenceRegular,referenceBold;
    // 1=enable OS credential lock, 2=unlock, 3=disable.
    private int deviceAuthAction=0;
    private boolean securityFromSettings=false;
    private long currentDocumentId=-1,editClientId=-1,editingDraftId=-1;
    private String draftIssue="",draftDue="",draftSupply="",draftNote="",draftQuoteTitle="";
    private final ArrayList<Store.Item> draftItems=new ArrayList<>();
    private long pendingPdfId=-1;private char[] pendingPassword;
    private long pendingBluetoothDocumentId=-1;
    private int pendingBluetoothPaperDots=ThermalReceipt.PAPER_58_DOTS;
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);

    @Override public void onCreate(Bundle b){super.onCreate(b);
        LocalAccounts.lock();
        prefs=getSharedPreferences("ui",MODE_PRIVATE);
        // Android 13+ gesture back bypasses deprecated Activity.onBackPressed.
        // Use the platform callback so no AndroidX runtime dependency is needed.
        if(android.os.Build.VERSION.SDK_INT>=33){
            systemBackCallback=()->handleBack();
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,systemBackCallback);
        }
        dark=prefs.getBoolean("dark",false);
        if(b!=null){
            pendingPdfId=b.getLong("pendingPdfId",-1);
            pendingPaymentsCsvMonth=b.getString("pendingPaymentsCsvMonth");
            pendingReportCsvMonth=b.getString("pendingReportCsvMonth");
            deviceAuthAction=b.getInt("deviceAuthAction",0);
            pendingBluetoothDocumentId=b.getLong("pendingBluetoothDocumentId",-1);
            pendingBluetoothPaperDots=b.getInt("pendingBluetoothPaperDots",
                ThermalReceipt.PAPER_58_DOTS);
            securityFromSettings=b.getBoolean("securityFromSettings",false);
        }
        if(runScreenshotHarness())return;
        // Never open private financial data before authenticating a local account.
        // The old single-device database is migrated only after first registration.
        splash(true);
    }
    @Override protected void onSaveInstanceState(Bundle outState){
        outState.putLong("pendingPdfId",pendingPdfId);
        outState.putString("pendingPaymentsCsvMonth",pendingPaymentsCsvMonth);
        outState.putString("pendingReportCsvMonth",pendingReportCsvMonth);
        outState.putInt("deviceAuthAction",deviceAuthAction);
        outState.putLong("pendingBluetoothDocumentId",pendingBluetoothDocumentId);
        outState.putInt("pendingBluetoothPaperDots",pendingBluetoothPaperDots);
        outState.putBoolean("securityFromSettings",securityFromSettings);
        // Never persist the backup password, even in the Android Bundle.
        super.onSaveInstanceState(outState);
    }
    @Override protected void onStop(){
        if(db!=null&&("new_doc".equals(screen)||"edit_items".equals(screen)||
            "review".equals(screen)))saveWorkingDraft();
        if("local_login".equals(screen))enteredLocalPin.setLength(0);
        super.onStop();
    }
    /** Allows debug-only emulator screenshots without introducing preview screens in release builds. */
    private boolean runScreenshotHarness(){
        if(!"com.brendigo.fakturiva.SCREENSHOT".equals(getIntent().getAction()))return false;
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)return false;
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT);
        String hardware=android.os.Build.HARDWARE.toLowerCase(java.util.Locale.ROOT);
        String product=android.os.Build.PRODUCT.toLowerCase(java.util.Locale.ROOT);
        // Google APIs API-36 virtual devices may not contain "generic" in their
        // fingerprint. The debug-only harness must still run on ranchu/goldfish
        // hardware and sdk_gphone emulator products, but never on a real phone.
        boolean emulator=fingerprint.contains("generic") ||
            fingerprint.contains("ranchu") ||
            hardware.contains("ranchu") || hardware.contains("goldfish") ||
            product.startsWith("sdk_") || product.contains("emulator");
        if(!emulator){
            android.util.Log.e("FakturivaQA","QA_SCREEN_REJECTED_NOT_EMULATOR");
            return false;
        }
        try{
            db=new Store(this); // Debug-only sample store; never used by release login.
            Class<?> harness=Class.forName("com.brendigo.fakturiva.ScreenshotHarness");
            harness.getMethod("show",MainActivity.class,String.class)
                .invoke(null,this,getIntent().getStringExtra("screen"));
            return true;
        }catch(Exception e){
            android.util.Log.e("Fakturiva","Emulator screenshot harness failed",e);
            return false;
        }
    }
    @Override protected void onDestroy(){
        if(android.os.Build.VERSION.SDK_INT>=33&&systemBackCallback!=null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        if(pendingPassword!=null)Arrays.fill(pendingPassword,'\0');
        if(db!=null)db.close();
        enteredLocalPin.setLength(0);
        super.onDestroy();
    }
    private String selectedCurrency(){return BillingCurrency.canonical(db.setting("currency"));}
    private String selectedLanguage(){
        if(qaReferenceCroatian)return "hr";
        return UiStrings.normalized(
            prefs==null?"en":prefs.getString("ui_language","en"));
    }
    private Locale selectedLocale(){
        return "hr".equals(selectedLanguage())?Locale.forLanguageTag("hr-HR"):Locale.ENGLISH;
    }
    private String ui(String s){return UiStrings.translate(s,selectedLanguage());}
    private String money(long amount){
        return BillingCurrency.format(amount,selectedCurrency(),selectedLocale());
    }
    private String docMoney(Store.Document d,long amount){
        return BillingCurrency.format(amount,d.currencyCode,selectedLocale());
    }
    private String draftCurrency(){
        if(editingDraftId>0){
            try{return db.document(editingDraftId).currencyCode;}
            catch(Exception ignored){}
        }
        return selectedCurrency();
    }
    private String draftMoney(long amount){
        return BillingCurrency.format(amount,draftCurrency(),selectedLocale());
    }
    private int bg(){return dark?0xff0d182a:BG;}
    private int panel(){return dark?0xff14233b:WHITE;}
    private int ink(){return dark?WHITE:TEXT;}
    private int sub(){return dark?0xffa3b2c9:MUTED;}
    private int border(){return dark?0xff2a3c56:LINE;}
    private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outlined(int color){GradientDrawable d=shape(color,17);d.setStroke(dp(1),border());return d;}
    private void background(View v,int color,int radius){v.setBackground(shape(color,radius));}
    private Typeface referenceTypeface(boolean strong){
        if(referenceRegular==null){
            try{
                referenceRegular=Typeface.createFromAsset(getAssets(),"fonts/Inter.ttf");
                referenceBold=Typeface.create(referenceRegular,Typeface.BOLD);
            }catch(RuntimeException ignored){
                // Older Android font parsers still receive fully working text.
                referenceRegular=Typeface.create("sans-serif",Typeface.NORMAL);
                referenceBold=Typeface.create("sans-serif",Typeface.BOLD);
            }
        }
        return strong?referenceBold:referenceRegular;
    }
    private TextView label(String s,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(ui(s));
        t.setTextColor(color);t.setTextSize(size);t.setFontFeatureSettings("kern");
        // Inter belongs to the original design on the highest-deviation
        // dashboard, reports and document-preview screens.
        t.setTypeface(("home".equals(screen)||"detail".equals(screen)||
            "reports".equals(screen))
            ?referenceTypeface(strong)
            :Typeface.create("sans-serif",strong?Typeface.BOLD:Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private void gap(LinearLayout container,int h){View v=new View(this);container.addView(v,lp(1,h));}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private void place(LinearLayout parent,View view){parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
    /**
     * Keep 393dp reference layout unchanged on phones; center all native forms
     * in resizable Android windows on tablets, Chromebooks and Windows hosts.
     * The limit is based on the measured viewport, not a fixed device label.
     */
    private void sizeAdaptiveContent(){
        if(scroller==null||body==null)return;
        int available=scroller.getWidth()-scroller.getPaddingLeft()-
            scroller.getPaddingRight();
        if(available<=0)return;
        int target=Math.min(available,dp(840));
        ScrollView.LayoutParams current=(ScrollView.LayoutParams)body.getLayoutParams();
        if(current.width!=target||current.gravity!=Gravity.CENTER_HORIZONTAL){
            current.width=target;
            current.gravity=Gravity.CENTER_HORIZONTAL;
            body.setLayoutParams(current);
        }
    }
    private void sizeAdaptiveNavigation(){
        if(adaptiveNavContainer==null||adaptiveNavBar==null)return;
        int available=adaptiveNavContainer.getWidth();
        if(available<=0)return;
        int target=Math.min(available,dp(840));
        FrameLayout.LayoutParams current=
            (FrameLayout.LayoutParams)adaptiveNavBar.getLayoutParams();
        if(current.width!=target||current.gravity!=Gravity.CENTER_HORIZONTAL){
            current.width=target;
            current.gravity=Gravity.CENTER_HORIZONTAL;
            adaptiveNavBar.setLayoutParams(current);
        }
    }
    private void title(LinearLayout parent,String s){TextView v=label(s,19,ink(),true);place(parent,v);}
    private void caption(LinearLayout parent,String s){TextView v=label(s,12,sub(),false);place(parent,v);}
    private void section(String s){gap(body,16);title(body,s);gap(body,11);}
    private LinearLayout card(){LinearLayout v=vertical();pad(v,16,16);v.setBackground(outlined(panel()));return v;}
    private TextView action(String text,int color,boolean solid,Runnable click){
        TextView b=label(text,14,solid?WHITE:color,true);b.setGravity(Gravity.CENTER);
        b.setBackground(outlined(panel()));
        if(solid)background(b,color,13);b.setMinimumHeight(dp(50));pad(b,12,10);
        b.setOnClickListener(v->click.run());return b;
    }
    private void button(LinearLayout parent,String text,Runnable act){place(parent,action(text,BLUE,true,act));}
    private void secondary(LinearLayout parent,String text,Runnable act){place(parent,action(text,BLUE,false,act));}
    private void setup(String active,boolean nav){
        screen=active;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(panel());
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=vertical();root.setBackgroundColor(bg());setContentView(root);
        // API 35+ enforces edge-to-edge for targetSdk 35/36. Keep functional
        // navigation and forms away from both gesture and three-button bars.
        // Return unconsumed insets so the IME and children retain their callbacks.
        if(android.os.Build.VERSION.SDK_INT>=35){
            View decor=getWindow().getDecorView();
            decor.setOnApplyWindowInsetsListener((view,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                    android.view.WindowInsets.Type.systemBars()|
                    android.view.WindowInsets.Type.displayCutout());
                root.setPadding(bars.left,bars.top,bars.right,bars.bottom);
                return insets;
            });
            decor.requestApplyInsets();
        }
        contentFrame=new FrameLayout(this);
        root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));
        scroller=new ScrollView(this);scroller.setFillViewport(false);
        scroller.setClipToPadding(false);
        contentFrame.addView(scroller,new FrameLayout.LayoutParams(-1,-1));
        body=vertical();pad(body,17,18);
        ScrollView.LayoutParams bodyParams=new ScrollView.LayoutParams(-1,-2);
        bodyParams.gravity=Gravity.CENTER_HORIZONTAL;
        scroller.addView(body,bodyParams);
        scroller.addOnLayoutChangeListener((view,left,top,right,bottom,
            oldLeft,oldTop,oldRight,oldBottom)->sizeAdaptiveContent());
        adaptiveNavContainer=null;
        adaptiveNavBar=null;
        if(nav)bottomNav();
    }
    /** Keep native back navigation consistent with the current business workflow. */
    private void backFromToolbar(){
        switch(screen){
            case "client_detail":clients();break;
            case "new_doc":
                if(Store.INVOICE.equals(editType))documents(Store.INVOICE);
                else documents(Store.QUOTE);
                break;
            case "edit_items":editDocumentScreen();break;
            case "review":editItemsScreen();break;
            case "detail":
                try{
                    Store.Document d=db.document(currentDocumentId);
                    documents(d.type);
                }catch(Exception e){home();}
                break;
            case "local_accounts":case "self_host":case "privacy":settings();break;
            case "catalog":case "reports":case "payments":case "company":case "guide":
            case "backup":case "about":settings();break;
            default:home();
        }
    }
    private void toolbar(String title,String subtitle,boolean back){
        LinearLayout row=horizontal();
        if(back){
            ImageView b=icon(R.drawable.ic_back,sub());
            b.setPadding(dp(8),dp(8),dp(8),dp(8));
            b.setContentDescription("Natrag");
            row.addView(b,lp(40,48));
            b.setOnClickListener(v->backFromToolbar());
        }
        LinearLayout heading=vertical();TextView t=label(title,23,ink(),true);place(heading,t);
        if(subtitle!=null){TextView st=label(subtitle,11,sub(),false);place(heading,st);}
        row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        // The original preview has TWO purposeful overflow controls:
        // this toolbar button opens settings; the status-row menu opens
        // document-specific PDF actions. Keep both like the source design.
        TextView more=label("•••",15,sub(),true);
        more.setGravity(Gravity.CENTER);
        more.setBackground(shape(panel(),12));
        more.setContentDescription("Otvori postavke");
        row.addView(more,lp(44,44));
        more.setOnClickListener(v->navigate("settings"));
        place(body,row);gap(body,18);
    }
    private ImageView icon(int id,int tint){
        ImageView img=new ImageView(this);img.setImageResource(id);
        img.setColorFilter(tint);img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return img;
    }
    /** Consistent accessible FAB on list screens, overlayed above the bottom navigation. */
    private void floatingAction(String label,Runnable onClick){
        // Reserve scrolling room so the last actual record is never obscured.
        body.setPadding(body.getPaddingLeft(),body.getPaddingTop(),
            body.getPaddingRight(),dp(88));
        ImageView action=icon(R.drawable.ic_add,WHITE);
        action.setContentDescription(label);
        action.setFocusable(true);
        action.setClickable(true);
        action.setPadding(dp(14),dp(14),dp(14),dp(14));
        action.setBackground(shape(BLUE,18));
        action.setElevation(dp(7));
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(
            dp(56),dp(56),Gravity.END|Gravity.BOTTOM);
        pos.setMargins(0,0,dp(18),dp(19));
        contentFrame.addView(action,pos);
        action.setOnClickListener(v->onClick.run());
    }
    private void bottomNav(){
        LinearLayout bar=horizontal();bar.setBackgroundColor(panel());pad(bar,6,7);
        String[] pages={"home","invoices","quotes","clients","settings"};
        String[] names={"Početna","Računi","Ponude","Klijenti","Više"};
        int[] icons={R.drawable.ic_home,R.drawable.ic_invoice,R.drawable.ic_quote,R.drawable.ic_clients,R.drawable.ic_settings};
        String active=screen;
        if("new_doc".equals(screen)||"edit_items".equals(screen)||"review".equals(screen)){
            active=Store.QUOTE.equals(editType)?"quotes":"invoices";
        }else if("catalog".equals(screen)||"reports".equals(screen)||
                  "payments".equals(screen)||"company".equals(screen)||
                  "guide".equals(screen)||"backup".equals(screen)||
                  "about".equals(screen)){
            active="settings";
        }else if("client_detail".equals(screen)){
            active="clients";
        }else if("detail".equals(screen)&&currentDocumentId>0){
            try{
                active=Store.QUOTE.equals(db.document(currentDocumentId).type)?"quotes":"invoices";
            }catch(Exception ignored){active="home";}
        }
        for(int i=0;i<pages.length;i++){final String destination=pages[i];
            boolean selected=active.equals(destination);
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);
            LinearLayout badge=vertical();badge.setGravity(Gravity.CENTER);
            if(selected)background(badge,dark?0xff273D66:0xffECEEFF,12);
            badge.addView(icon(icons[i],selected?BLUE:sub()),lp(23,23));
            LinearLayout.LayoutParams badgeLp=lp(39,35);badgeLp.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(badge,badgeLp);
            TextView name=label(names[i],10,selected?BLUE:sub(),selected);
            name.setGravity(Gravity.CENTER);tab.addView(name,lp(-1,19));
            tab.setContentDescription("Otvori "+names[i]);
            tab.setFocusable(true);tab.setClickable(true);
            bar.addView(tab,new LinearLayout.LayoutParams(0,dp(56),1));
            tab.setOnClickListener(v->navigate(destination));
        }
        adaptiveNavContainer=new FrameLayout(this);
        adaptiveNavContainer.setBackgroundColor(panel());
        adaptiveNavBar=bar;
        FrameLayout.LayoutParams navigationParams=
            new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_HORIZONTAL);
        adaptiveNavContainer.addView(bar,navigationParams);
        adaptiveNavContainer.addOnLayoutChangeListener((view,left,top,right,
            bottom,oldLeft,oldTop,oldRight,oldBottom)->sizeAdaptiveNavigation());
        place(root,adaptiveNavContainer);
    }
    /** Keyboard controls for mouse/keyboard Android runtimes on Windows hosts. */
    @Override public boolean onKeyShortcut(int keyCode,android.view.KeyEvent event){
        if(event!=null&&event.isCtrlPressed()&&!event.isAltPressed()){
            switch(keyCode){
                case android.view.KeyEvent.KEYCODE_1:navigate("home");return true;
                case android.view.KeyEvent.KEYCODE_2:navigate("invoices");return true;
                case android.view.KeyEvent.KEYCODE_3:navigate("quotes");return true;
                case android.view.KeyEvent.KEYCODE_4:navigate("clients");return true;
                case android.view.KeyEvent.KEYCODE_5:navigate("settings");return true;
                case android.view.KeyEvent.KEYCODE_P:
                    if("detail".equals(screen)&&currentDocumentId>0){
                        showPrinterDialog(currentDocumentId);return true;
                    }
                    break;
                default:break;
            }
        }
        return super.onKeyShortcut(keyCode,event);
    }
    /** Never silently delete or overwrite a damaged database. */
    private void damagedDatabase(Exception cause){
        setup("damaged",false);
        gap(body,40);title(body,"Potreban je oporavak podataka");gap(body,16);
        caption(body,"Poslovne podatke nije moguće otvoriti. Pokušajte ih vratiti iz sigurnosne kopije.");
        gap(body,25);
        button(body,"Vrati iz sigurnosne kopije",()->{
            new AlertDialog.Builder(this).setTitle("Oporavak aplikacije")
                .setMessage("Prije vraćanja sačuvat ćemo trenutačne podatke. Odabrana kopija može biti starija.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Pokušaj",(dlg,w)->recoverDamagedDatabase()).show();
        });
    }
    private void recoverDamagedDatabase(){
        try{
            JSONObject snapshot=RecoveryVault.read(this,0);
            db.close();
            File old=getDatabasePath(db.databaseFileName());
            File location=new File(getFilesDir(),"damaged-db-"+System.currentTimeMillis());
            if(!location.mkdirs())throw new IllegalStateException("Nema mjesta za spremanje oštećene baze");
            for(String suffix:new String[]{"","-wal","-shm","-journal"}){
                File original=new File(old.getAbsolutePath()+suffix);
                if(original.exists()&&!original.renameTo(
                    new File(location,db.databaseFileName()+suffix)))
                    throw new IllegalStateException("Nije moguće sigurno arhivirati bazu");
            }
            db=new Store(this,LocalAccounts.activeId());db.restore(snapshot);
            toast("Oporavak je dovršen. Pregledajte dokumente i izvezite novu kopiju.");
            navigate("home");
        }catch(Exception e){error(e);damagedDatabase(e);}
    }
    /** Only the debug emulator's screenshot harness may navigate without a PIN. */
    private boolean runScreenshotModeOnly(){
        return db!=null &&
            (getApplicationInfo().flags &
                android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0 &&
            "com.brendigo.fakturiva.SCREENSHOT".equals(getIntent().getAction());
    }
    private void navigate(String dest){
        if(db==null || (LocalAccounts.activeId()==null && !runScreenshotModeOnly())){
            openExistingLocalAccess();return;
        }
        filter="Svi";
        documentSearchQuery="";
        switch(dest){
            case "home":home();break;
            case "invoices":documents(Store.INVOICE);break;
            case "quotes":documents(Store.QUOTE);break;
            case "clients":clients();break;
            case "catalog":catalog();break;
            case "reports":reports();break;
            case "payments":payments();break;
            case "settings":settings();break;
            case "company":company();break;
            case "guide":guide();break;
            default:home();
        }
    }

    /**
     * Entirely native layered splash gradient: a vertical navy base plus a
     * soft elliptical top-right blue bloom.  No static PNG is drawn at runtime.
     * Geometry is normalized to the original owner's 1179x2556 reference.
     */
    private final class SplashBackdrop extends View{
        private final android.graphics.Paint paint=new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG);
        SplashBackdrop(Context context){super(context);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float width=getWidth(),height=getHeight();
            if(width<=0||height<=0)return;
            // Calibrated from the owner's original 1179x2556 Splash PNG,
            // using only unobstructed background pixels. This remains a
            // native, resolution-independent gradient — never a screenshot.
            paint.setShader(new android.graphics.LinearGradient(0,0,0,height,
                new int[]{0xff122d52,0xff0f2542,0xff0a1c32},
                new float[]{0,.5f,1},android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height,paint);
            paint.setShader(null);

            // Native radial gradient with separate horizontal/vertical radii.
            // The prior circular (.60 x .60) bloom lost up to 47 blue levels
            // in the reference's top-right corner at 393x852dp.
            final float radiusX=width*1.043f;
            final float radiusY=height*.468f;
            final float verticalScale=radiusY/radiusX;
            canvas.save();
            canvas.scale(1f,verticalScale);
            // SRC_OVER is intentional: ADD was unreliable on CI emulators.
            paint.setShader(new android.graphics.RadialGradient(
                width*.964f,height*.097f/verticalScale,radiusX,
                new int[]{0xff225aa4,0xb1225aa4,0x67225aa4,0x33225aa4,0x00225aa4},
                new float[]{0,.25f,.5f,.75f,1f},
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,width,height/verticalScale,paint);
            paint.setShader(null);
            canvas.restore();
        }
    }
    /** Native branded entry view, designed to match the dark Fakturiva splash reference. */
    private void splash(boolean animate){
        screen="splash";
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff0a1c33);
        // Non-immersive fullscreen does not trigger Android's first-run
        // "Viewing full screen" tutorial over the branded launch view.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);
        FrameLayout frame=new FrameLayout(this);
        frame.addView(new SplashBackdrop(this),
            new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        View rings=new View(this){
            final android.graphics.Paint brush=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                brush.setColor(0x13ffffff);brush.setStyle(android.graphics.Paint.Style.STROKE);
                brush.setStrokeWidth(dp(.9f));
                float unit=getWidth();
                // Least-squares circles recovered from 751 original PNG
                // outline pixels at 1179x2556, rather than three landmarks.
                // Right: (1494.2,734.5), r704.4; left:(-346.0,1604.4),r823.6.
                canvas.drawCircle(unit*1.26734f,getHeight()*.28738f,unit*.59745f,brush);
                canvas.drawCircle(-unit*.29350f,getHeight()*.62771f,unit*.69854f,brush);
                brush.setStyle(android.graphics.Paint.Style.FILL);
            }
        };
        frame.addView(rings,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=vertical();center.setGravity(Gravity.CENTER);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.brand_mark);
        LinearLayout.LayoutParams logo=lp(108,108);logo.gravity=Gravity.CENTER_HORIZONTAL;
        center.addView(mark,logo);gap(center,28);
        // Reference white/teal glyph bounds: +2 px X, +11 px Y at 480 dpi.
        // Translation leaves the surrounding vertical rhythm unchanged.
        mark.setTranslationX(dp(.67f));
        mark.setTranslationY(dp(3.67f));
        TextView name=label("Fakturiva",44,WHITE,true);name.setGravity(Gravity.CENTER);
        name.setTextScaleX(.982f);
        // The design uses a heavier headline than Android Roboto Bold.
        // Apply native paint emboldening rather than embedding a mockup.
        name.getPaint().setFakeBoldText(true);
        name.setTranslationY(dp(.67f));
        place(center,name);gap(center,17);
        TextView subtitle=label("Računi i ponude, jednostavno.",13,0xffbdcde1,false);
        subtitle.setTextScaleX(.978f);
        subtitle.setTranslationY(-dp(1f));
        subtitle.setGravity(Gravity.CENTER);place(center,subtitle);
        FrameLayout.LayoutParams middle=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        middle.leftMargin=dp(18);middle.rightMargin=dp(18);frame.addView(center,middle);
        // The reference groups the icon and typography above screen center.
        // Move the real native views, never the source screenshot.
        center.setTranslationY(-dp(40));
        LinearLayout bottom=vertical();bottom.setGravity(Gravity.CENTER);
        FrameLayout progress=new FrameLayout(this);background(progress,0xff36516c,4);
        View completed=new View(this);background(completed,0xff2fd2c5,4);
        FrameLayout.LayoutParams fill=new FrameLayout.LayoutParams(dp(34),dp(4),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        progress.addView(completed,fill);
        LinearLayout.LayoutParams progressLp=lp(85,4);progressLp.gravity=Gravity.CENTER;
        bottom.addView(progress,progressLp);gap(bottom,17);
        TextView text=label("Vaše poslovanje. Jednostavnije.",12,0xff9fb5ce,false);
        // Source footer spans 437 px rather than the old 512 px. Keep the
        // native TextView and real accessibility content instead of a bitmap.
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP,10f);
        text.setTextScaleX(1.055f);
        text.setGravity(Gravity.CENTER);place(bottom,text);
        FrameLayout.LayoutParams down=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        down.bottomMargin=dp(57);frame.addView(bottom,down);
        if(animate){
            frame.setContentDescription("Fakturiva — učitavanje aplikacije");
            frame.setOnClickListener(v->finishSplash());
            frame.postDelayed(this::finishSplash,1150);
        }
    }
    /** Launch navigation respects first-run state and never invents a cloud login. */
    private void finishSplash(){
        if(!"splash".equals(screen)||isFinishing()||isDestroyed())return;
        try{
            if(LocalAccounts.hasAccounts(this))loginPinScreen();
            else welcome();
        }catch(Exception e){
            accountStorageError(e);
        }
    }
    private void welcome(){
        setup("welcome",false);
        root.setBackgroundColor(WHITE);
        body.setBackgroundColor(WHITE);
        pad(body,28,27);
        LinearLayout brand=horizontal();
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.brand_mark);brand.addView(mark,lp(40,40));
        // Reference masthead icon is 120px at 480dpi, not 129px.
        brand.setTranslationX(-dp(1));
        TextView brandName=label("Fakturiva",24,NAVY,true);
        brandName.setTextScaleX(.946f);
        LinearLayout.LayoutParams brandLp=lp(-2,-1);brandLp.leftMargin=dp(9);
        brand.addView(brandName,brandLp);
        place(body,brand);gap(body,22);
        body.addView(new WelcomeIllustration(this),lp(-1,345));gap(body,30);
        TextView eyebrow=label("SVE NA JEDNOM MJESTU",10,0xff6c83ac,true);
        eyebrow.setLetterSpacing(.12f);
        eyebrow.setTranslationX(dp(1));
        place(body,eyebrow);gap(body,11);
        TextView heading=label("Manje papirologije.\nViše vremena za posao.",26,NAVY,true);
        // Narrow Roboto slightly to follow the supplied Inter-based line widths.
        heading.setTextScaleX(.963f);
        heading.getPaint().setFakeBoldText(true);
        heading.setLineSpacing(dp(2),1.0f);
        heading.setTranslationX(dp(1));
        heading.setTranslationY(0);
        place(body,heading);gap(body,14);
        TextView info=label("Izradite profesionalne račune i ponude, pratite naplatu i organizirajte klijente.",12,MUTED,false);
        info.setTextScaleX(.965f);
        info.setTranslationX(dp(1));
        info.setTranslationY(-dp(.67f));
        info.setLineSpacing(dp(4),1.0f);place(body,info);
        gap(body,21);
        LinearLayout dots=horizontal();
        for(int n=0;n<3;n++){
            View dot=new View(this);
            dot.setBackground(shape(n==0?BLUE:0xffdbe2ef,7));
            LinearLayout.LayoutParams p=lp(n==0?23:7,7);p.rightMargin=dp(6);dots.addView(dot,p);
        }
        place(body,dots);gap(body,20);
        // Match the reference CTA's 144px height, inset, rounded corners
        // and central arrow/text alignment. Keep the original real action.
        LinearLayout startButton=horizontal();startButton.setGravity(Gravity.CENTER);
        startButton.setBackground(shape(BLUE,15));
        startButton.setElevation(dp(5));
        if(android.os.Build.VERSION.SDK_INT>=28){
            // Reference has a soft indigo glow, not Android's default black shadow.
            startButton.setOutlineAmbientShadowColor(0x225566f8);
            startButton.setOutlineSpotShadowColor(0x335566f8);
        }
        TextView startArrow=label("›",23,WHITE,true);
        startArrow.setGravity(Gravity.CENTER);
        startButton.addView(startArrow,lp(21,-2));
        TextView startText=label("Započni",15,WHITE,true);
        LinearLayout.LayoutParams startTextLp=lp(-2,-2);
        startTextLp.leftMargin=dp(7);
        startButton.addView(startText,startTextLp);
        startButton.setContentDescription(ui("Započni"));
        startButton.setOnClickListener(v->registrationScreen());
        LinearLayout.LayoutParams startLp=lp(-1,48);
        startLp.leftMargin=dp(1);startLp.rightMargin=dp(1);
        body.addView(startButton,startLp);
        // Restore the split muted/blue login line from the owner's reference.
        // Both visible segments share a single accessible tap target.
        LinearLayout account=horizontal();
        account.setGravity(Gravity.CENTER);
        account.setMinimumHeight(dp(38));
        account.addView(label("Već imate račun? ",10,MUTED,false));
        TextView signIn=label("Prijavite se",10,BLUE,true);
        account.addView(signIn);
        account.setContentDescription(ui("Već imate račun? Prijavite se"));
        account.setOnClickListener(v->openExistingLocalAccess());
        place(body,account);gap(body,15);
    }
    /** Account selection always precedes access to any locally stored invoice. */
    private void openExistingLocalAccess(){
        try{
            if(LocalAccounts.hasAccounts(this))loginPinScreen();
            else registrationScreen();
        }catch(Exception e){accountStorageError(e);}
    }
    private void authPrimaryStyle(TextView button){
        // Preserve a genuine accessible TextView click action while matching
        // the supplied cobalt-to-indigo CTA rather than a flat Android button.
        GradientDrawable gradient=new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{0xff4c67fb,0xff414ef5});
        gradient.setCornerRadius(dp(16));
        button.setBackground(gradient);
        button.setTextSize(17);
        button.setMinimumHeight(dp(55));
        button.setElevation(dp(6));
    }

    /** Local registration modeled on the supplied white/blue reference. */
    private void registrationScreen(){
        if(selfHostedBusy){toast("Prijenos podataka je u tijeku");return;}
        enteredLocalPin.setLength(0);
        setup("local_register",false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        root.setBackgroundColor(WHITE);body.setBackgroundColor(WHITE);
        pad(body,25,60);
        localBrand();gap(body,25);
        place(body,label("Registracija",30,NAVY,true));gap(body,9);
        place(body,label("Kreirajte novi račun i započnite s poslovanjem.",
            14,MUTED,false));
        gap(body,62);
        place(body,label("Korisničko ime",14,0xff53627b,true));gap(body,10);
        EditText username=field("ime@tvrtka.hr","");
        username.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        username.setTextSize(17);username.setMinimumHeight(dp(54));
        place(body,username);
        gap(body,19);
        place(body,label("PIN",14,NAVY,true));gap(body,10);
        EditText pin=field("4–6 znamenki","");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER|
            InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new android.text.InputFilter[]{
            new android.text.InputFilter.LengthFilter(6)});
        pin.setTextSize(18);pin.setMinimumHeight(dp(54));
        place(body,pin);gap(body,10);
        place(body,label("ⓘ  Unesite sigurnosni PIN (4–6 znamenki).",
            12,0xff64738d,false));
        gap(body,48);
        TextView submit=action("♙   Registracija",BLUE,true,()->{
            char[] digits=pin.getText().toString().toCharArray();
            try{
                boolean first=!LocalAccounts.hasAccounts(this);
                String id=LocalAccounts.register(this,username.getText().toString(),digits);
                pin.setText("");
                if(first&&getDatabasePath("fakturiva.db").isFile()){
                    // Explicitly reserve the legacy database for the first
                    // profile; later accounts can never claim its records.
                    prefs.edit().putString("legacy_owner",id).commit();
                }
                openLocalSession(id);
            }catch(Exception e){toast(e.getMessage()==null?
                "Registracija nije uspjela":e.getMessage());}
            finally{Arrays.fill(digits,'\0');}
        });
        authPrimaryStyle(submit);
        submit.setContentDescription("Registracija lokalnog korisničkog računa");
        place(body,submit);gap(body,46);
        TextView login=label("Već imate račun?  Prijavite se",14,BLUE,true);
        login.setGravity(Gravity.CENTER);
        login.setMinimumHeight(dp(48));
        login.setOnClickListener(v->openExistingLocalAccess());
        place(body,login);
        gap(body,64);
        localPrivacyFooter();
    }

    private void localBrand(){
        ImageView image=new ImageView(this);
        image.setImageResource(R.drawable.brand_mark);
        LinearLayout.LayoutParams mark=lp(72,72);
        body.addView(image,mark);
    }

    private void localPrivacyFooter(){
        TextView footer=label("◇  Zaštita vaših poslovnih podataka",12,
            0xff7b8ba5,false);
        footer.setGravity(Gravity.CENTER);
        place(body,footer);gap(body,16);
    }

    private void loginPinScreen(){
        try{
            String[] names=LocalAccounts.names(this);
            if(names.length==0){registrationScreen();return;}
            String last=prefs.getString("local_last_user",names[0]);
            boolean found=false;
            for(String n:names)if(n.equals(last))found=true;
            if(!found)last=names[0];
            loginUsername=last;
            enteredLocalPin.setLength(0);
            setup("local_login",false);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            root.setBackgroundColor(WHITE);body.setBackgroundColor(WHITE);
            pad(body,25,59);
            localBrand();gap(body,26);
            place(body,label("Prijava PIN-om",30,NAVY,true));gap(body,9);
            place(body,label("Unesite svoj sigurnosni PIN za pristup računu.",
                14,MUTED,false));gap(body,44);
            TextView chosen=label("Korisnik: "+loginUsername+(names.length>1?"  ▾":""),
                12,0xff63718a,false);
            chosen.setMinimumHeight(dp(40));
            if(names.length>1){
                chosen.setContentDescription("Odaberite korisnički račun");
                chosen.setOnClickListener(v->
                    new AlertDialog.Builder(this).setTitle("Korisnički račun")
                        .setItems(names,(dialog,which)->{
                            loginUsername=names[which];
                            chosen.setText("Korisnik: "+loginUsername+"  ▾");
                            enteredLocalPin.setLength(0);
                            loginPinScreen();
                        }).show());
            }
            if(names.length>1){
                place(body,chosen);gap(body,18);
            }else{
                // Single-profile login stays visually aligned with the owner's
                // PIN reference; profile switching remains in Settings.
                gap(body,31);
            }
            place(body,label("PIN",14,0xff65748d,true));gap(body,9);
            LinearLayout masked=horizontal();
            masked.setBackground(outlined(WHITE));pad(masked,19,10);
            TextView dots=label("",24,NAVY,true);
            dots.setGravity(Gravity.CENTER_VERTICAL);
            masked.addView(dots,new LinearLayout.LayoutParams(0,dp(44),1));
            TextView lock=label("♙",22,0xff7e8fa7,true);
            masked.addView(lock);
            masked.setContentDescription("Sigurnosni PIN, skriveni unos");
            place(body,masked);gap(body,28);
            int[][] keys={{1,2,3},{4,5,6},{7,8,9},{-1,0,-2}};
            for(int[] row:keys){
                LinearLayout line=horizontal();
                for(int number:row){
                    TextView key=label(number==-1?"⌫":number==-2?"":String.valueOf(number),
                        27,NAVY,true);
                    key.setGravity(Gravity.CENTER);
                    key.setBackground(shape(0xffedf1f9,14));
                    key.setFocusable(number!=-2);
                    key.setClickable(number!=-2);
                    key.setContentDescription(number==-1?"Obriši PIN znamenku":
                        number==-2?"":("Znamenka "+number));
                    if(number==-1)key.setOnClickListener(v->{
                        if(enteredLocalPin.length()>0)
                            enteredLocalPin.deleteCharAt(enteredLocalPin.length()-1);
                        updatePinDots(dots);
                    });
                    else if(number>=0)key.setOnClickListener(v->{
                        if(enteredLocalPin.length()<6)
                            enteredLocalPin.append((char)('0'+number));
                        updatePinDots(dots);
                    });
                    LinearLayout.LayoutParams keyParams=new LinearLayout.LayoutParams(0,dp(53),1);
                    keyParams.leftMargin=dp(3);keyParams.rightMargin=dp(3);
                    line.addView(key,keyParams);
                    if(number==-2)key.setVisibility(View.INVISIBLE);
                }
                place(body,line);gap(body,7);
            }
            gap(body,26);
            TextView submit=action("›   Prijavi se",BLUE,true,()->{
                char[] digits=enteredLocalPin.toString().toCharArray();
                try{
                    String id=LocalAccounts.verify(this,loginUsername,digits);
                    enteredLocalPin.setLength(0);
                    openLocalSession(id);
                }catch(Exception error){
                    enteredLocalPin.setLength(0);
                    updatePinDots(dots);
                    toast(error.getMessage()==null?
                        "Neuspješna provjera sigurnosnog PIN-a":error.getMessage());
                }finally{Arrays.fill(digits,'\0');}
            });
            authPrimaryStyle(submit);
            submit.setContentDescription("Prijavite se s lokalnim sigurnosnim PIN-om");
            place(body,submit);gap(body,37);
            localPrivacyFooter();
            if(names.length>1){
                TextView next=label("Dodajte drugi lokalni račun",12,BLUE,true);
                next.setGravity(Gravity.CENTER);next.setMinimumHeight(dp(44));
                next.setOnClickListener(v->registrationScreen());
                place(body,next);
            }
        }catch(Exception e){accountStorageError(e);}
    }

    private void updatePinDots(TextView dots){
        StringBuilder masked=new StringBuilder();
        for(int i=0;i<enteredLocalPin.length();i++)masked.append("● ");
        dots.setText(masked.toString());
    }

    /** Prevent opening any business table before local authorization. */
    private void openLocalSession(String verifiedId){
        try{
            if(db!=null){db.close();db=null;}
            LocalAccounts.unlock(this,verifiedId);
            db=new Store(this,verifiedId);
            try(android.database.Cursor check=
                db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null)){
                if(!check.moveToFirst()||!"ok".equalsIgnoreCase(check.getString(0)))
                    throw new IllegalStateException("Neuspjela provjera baze");
            }
            // One-time upgrade from the previous single-device data model.
            // Never migrate those documents into a second user's database.
            if(verifiedId.equals(prefs.getString("legacy_owner","")) &&
                !prefs.getBoolean("legacy_imported",false) &&
                getDatabasePath("fakturiva.db").isFile() &&
                db.count("clients")==0 && db.count("documents")==0){
                Store legacy=new Store(this);
                JSONObject snapshot;
                try{snapshot=legacy.backup();}finally{legacy.close();}
                db.restore(snapshot); // validated and crash-safe, never silent merge
                RecoveryVault.migrateLegacyDraft(this);
                if(!prefs.edit().putBoolean("legacy_imported",true).commit())
                    throw new IllegalStateException("Migracija nije potvrđena");
            }
            prefs.edit().putString("local_last_user",LocalAccounts.activeName())
                .putBoolean("welcomed",true).apply();
            if(prefs.getBoolean("device_lock",false))
                securityScreen(true,false);
            else{
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                navigate("home");offerWorkingDraftRecovery();
            }
        }catch(Exception e){
            if(db!=null){db.close();db=null;}
            LocalAccounts.lock();
            accountStorageError(e);
        }
    }

    private void accountStorageError(Exception error){
        android.util.Log.e("FakturivaAuth","Local account storage unavailable",error);
        setup("account_error",false);
        root.setBackgroundColor(WHITE);
        pad(body,25,60);
        place(body,label("Računi nisu dostupni",23,NAVY,true));
        gap(body,12);
        place(body,label("Nije moguće sigurno otvoriti lokalne podatke. "+
            "Podaci neće biti automatski izbrisani ni zamijenjeni. "+
            "Provjerite uređaj i postojeće sigurnosne kopije.",14,MUTED,false));
        gap(body,25);
        button(body,"Ponovno pokušaj",this::openExistingLocalAccess);
    }

    private void localAccountSettings(){
        if(LocalAccounts.activeId()==null){loginPinScreen();return;}
        setup("local_accounts",true);
        toolbar("Korisnički računi","Lokalna pohrana · bez vanjskih servisa",true);
        LinearLayout info=card();
        place(info,label("Prijavljen: "+LocalAccounts.activeName(),16,ink(),true));
        gap(info,9);
        place(info,label("Svaki profil ima zasebnu bazu računa, ponuda i klijenata "+
            "te odvojene šifrirane sigurnosne kopije.",12,sub(),false));
        place(body,info);gap(body,15);
        button(body,"Odjava s uređaja",()->new AlertDialog.Builder(this)
            .setTitle("Odjava").setMessage("Odjaviti lokalni korisnički račun?")
            .setNegativeButton("Odustani",null)
            .setPositiveButton("Odjavi se",(dialog,which)->signOutLocal()).show());
        gap(body,12);
        secondary(body,"Dodaj lokalni korisnički račun",()->registrationScreen());
        gap(body,22);
        secondary(body,"Trajno izbriši ovaj lokalni račun",()->{
            if(selfHostedBusy){toast("Prijenos podataka je u tijeku");return;}
            EditText secret=field("PIN za potvrdu brisanja","");
            secret.setInputType(InputType.TYPE_CLASS_NUMBER|
                InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            new AlertDialog.Builder(this).setTitle("Brisanje lokalnog računa")
                .setMessage("Ovo trajno briše lokalnu bazu i lokalne sigurnosne "+
                    "kopije trenutačnog profila. Prethodno izvezite kopiju. "+
                    "Ne briše udaljeni račun na vašem hostingu.")
                .setView(secret).setNegativeButton("Odustani",null)
                .setPositiveButton("Trajno izbriši",(d,w)->{
                    char[] code=secret.getText().toString().toCharArray();
                    String id=LocalAccounts.activeId();
                    try{
                        if(!id.equals(LocalAccounts.verify(this,
                            LocalAccounts.activeName(),code)))
                            throw new SecurityException("Neispravan profil");
                        if(db!=null){db.close();db=null;}
                        LocalAccounts.delete(this,LocalAccounts.activeName(),code);
                        if(id.equals(prefs.getString("legacy_owner",""))){
                            // Keep legacy migration from repopulating a new account.
                            getApplicationContext().deleteDatabase("fakturiva.db");
                            RecoveryVault.deleteLegacyCopies(this);
                            prefs.edit().remove("legacy_owner").putBoolean(
                                "legacy_imported",true).apply();
                        }
                        loginPinScreen();
                    }catch(Exception e){
                        if(LocalAccounts.activeId()!=null && db==null)
                            db=new Store(this,id);
                        toast(e.getMessage()==null?"Brisanje nije uspjelo":e.getMessage());
                    }finally{Arrays.fill(code,'\0');}
                }).show();
        });
    }

    private void signOutLocal(){
        if(selfHostedBusy){toast("Prijenos podataka je u tijeku");return;}
        enteredLocalPin.setLength(0);
        if(db!=null){db.close();db=null;}
        LocalAccounts.lock();
        loginPinScreen();
    }

    /**
     * An optional HTTPS host is NEVER configured by default. The user's local
     * PIN is never submitted. Strong server credentials are entered per action
     * and never saved in the app or included in Android backup.
     */
    private void selfHostedSettings(){
        if(LocalAccounts.activeId()==null||db==null){loginPinScreen();return;}
        setup("self_host",true);
        toolbar("Vlastiti hosting","Ručna šifrirana sinkronizacija",true);
        LinearLayout notice=card();
        place(notice,label("Potpuno neobavezno",16,ink(),true));gap(notice,8);
        place(notice,label("Svi računi i ponude rade lokalno i bez interneta. "+
            "Za sinkronizaciju sa svojim hostingom postavite vlastitu HTTPS adresu "+
            "i zasebnu lozinku od najmanje 12 znakova. PIN ostaje na uređaju.",12,sub(),false));
        place(body,notice);gap(body,15);
        String id=LocalAccounts.activeId();
        EditText url=field("https://moja-domena.hr/",
            prefs.getString("host_url_"+id,""));
        fieldLabel(body,"Adresa vlastitog hostinga (HTTPS)",url);
        EditText user=field("ime@tvrtka.hr",
            prefs.getString("host_user_"+id,LocalAccounts.activeName()));
        fieldLabel(body,"Poslužiteljsko korisničko ime",user);
        EditText pass=field("Lozinka hostinga (nije PIN)","");
        pass.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if(Build.VERSION.SDK_INT>=26)
            pass.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        fieldLabel(body,"Poslužiteljska lozinka",pass);
        gap(body,17);
        secondary(body,"Registriraj račun na hostingu",()->
            remoteAction(SelfHostedSyncClient.Action.REGISTER,url,user,pass));
        gap(body,10);
        button(body,"Pošalji šifriranu sigurnosnu kopiju",()->
            new AlertDialog.Builder(this).setTitle("Prijenos na vlastiti hosting")
                .setMessage("Poslat će se cijela šifrirana kopija ovog lokalnog "+
                    "profila na odabrani HTTPS hosting. Ako ondje postoji ranija "+
                    "kopija, bit će zamijenjena. Ne prenosite podatke bez ovlaštenja.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Pošalji",(dialog,which)->
                    remoteAction(SelfHostedSyncClient.Action.UPLOAD,url,user,pass))
                .show());
        gap(body,10);
        secondary(body,"Preuzmi i vrati kopiju s hostinga",()->
            new AlertDialog.Builder(this).setTitle("Zamjena lokalnih podataka")
                .setMessage("Ovo zamjenjuje sve račune, ponude i klijente SAMO "+
                    "trenutačnog lokalnog profila. Fakturiva će prije vraćanja "+
                    "napraviti lokalnu šifriranu sigurnosnu kopiju. "+
                    "Provjerite ispravnost odabranog računa i hostinga.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Preuzmi",(dialog,which)->
                    remoteAction(SelfHostedSyncClient.Action.DOWNLOAD,url,user,pass))
                .show());
        gap(body,18);
        secondary(body,"Trajno izbriši račun na hostingu",()->
            new AlertDialog.Builder(this).setTitle("Brisanje na vlastitom hostingu")
                .setMessage("Ovo trajno briše poslužiteljski račun i spremljenu "+
                    "kopiju. Lokalni podaci i dalje ostaju na ovom uređaju.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Trajno izbriši",(dialog,which)->
                    remoteAction(SelfHostedSyncClient.Action.DELETE,url,user,pass))
                .show());
        gap(body,10);
        secondary(body,"Web obrazac za brisanje računa",()->{
            try{
                String api=SelfHostedSyncClient.apiUrl(url.getText().toString());
                String page=api.substring(0,api.length()-"api.php".length())+
                    "delete-account.php";
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(page)));
            }catch(Exception error){toast(error.getMessage());}
        });
        gap(body,16);
        place(body,label("Hoster mora podržavati PHP 8.1+, OpenSSL, HTTPS i "+
            "zapisivanje u storage/data. Nije potreban SQL niti vanjski servis. "+
            "Veći dokumenti mogu zahtijevati povećanje PHP limita prijenosa.",12,sub(),false));
        gap(body,20);
    }

    private void remoteAction(SelfHostedSyncClient.Action action,EditText url,
        EditText username,EditText pass){
        if(selfHostedBusy){toast("Prijenos je već u tijeku");return;}
        if(db==null||LocalAccounts.activeId()==null){
            toast("Prvo se prijavite u lokalni račun");return;
        }
        String host=url.getText().toString().trim();
        String user=username.getText().toString().trim();
        char[] code=pass.getText().toString().toCharArray();
        try{
            String api=SelfHostedSyncClient.apiUrl(host);
            if(code.length<12||code.length>256)
                throw new IllegalArgumentException("Lozinka hostinga mora imati 12–256 znakova");
            String id=LocalAccounts.activeId();
            prefs.edit().putString("host_url_"+id,api)
                .putString("host_user_"+id,user).apply();
            selfHostedBusy=true;
            SelfHostedSyncClient.execute(this,action,api,user,code,db,(error,result)->{
                selfHostedBusy=false;
                if(error!=null)toast(error);
                else{
                    toast(result);
                    if(action==SelfHostedSyncClient.Action.DOWNLOAD)
                        navigate("home");
                    else selfHostedSettings();
                }
            });
            pass.setText("");
            toast("Pokrenut je samo odabrani zahtjev prema vašem hostingu");
        }catch(Exception error){toast(error.getMessage()==null?
            "Neispravne postavke hostinga":error.getMessage());}
        finally{Arrays.fill(code,'\0');}
    }

    /** Required in-app privacy information; the public Play policy URL must
     * be independently published before production distribution. */
    private void privacyInfoScreen(){
        setup("privacy",true);
        toolbar("Privatnost","Fakturiva · Brendigo",true);
        LinearLayout content=card();
        place(content,label("Lokalni profil i pohrana",18,ink(),true));
        gap(content,11);
        place(content,label("Korisničko ime, lokalni PIN i poslovni podaci "+
            "spremaju se na ovom Android uređaju. PIN nije spremljen kao "+
            "čitljiv tekst. Svaki lokalni korisnik ima zasebnu bazu "+
            "i lokalne šifrirane kopije.",13,sub(),false));
        gap(content,14);
        place(content,label("Vlastiti hosting nije obavezan",16,ink(),true));
        gap(content,8);
        place(content,label("Tek ako sami odaberete vlastiti HTTPS hosting "+
            "i pokrenete slanje, šifrirana kopija računa i ponuda prenosi "+
            "se na vašu domenu. Za pristup se koristi zasebna poslužiteljska "+
            "lozinka, nikada lokalni PIN.",13,sub(),false));
        gap(content,14);
        place(content,label("Brisanje i čuvanje",16,ink(),true));
        gap(content,8);
        place(content,label("Lokalni i poslužiteljski računi brišu se odvojeno. "+
            "Za brisanje trebate PIN odnosno lozinku poslužitelja. "+
            "Poslovne dokumente pohranite u skladu sa zakonskim rokovima "+
            "čuvanja, prije brisanja izvezite potrebne kopije.",13,sub(),false));
        place(body,content);gap(body,16);
        button(body,"Upravljaj lokalnim računom",this::localAccountSettings);
        gap(body,10);
        secondary(body,"Upravljaj vlastitim hostingom",this::selfHostedSettings);
        gap(body,16);
        place(body,label("Kontakt za pitanja o aplikaciji: "+
            BrandAttribution.SUPPORT_EMAIL,12,sub(),false));
        gap(body,20);
    }

    /**
     * Screen 03 is real offline protection, never fake cloud authentication.
     * The operating system verifies credentials: we store no PIN or biometrics.
     */
    private void securityScreen(boolean locked,boolean fromSettings){
        securityFromSettings=fromSettings;
        setup(locked?"security_unlock":"security_setup",false);
        // Protect real credentials and financial content. Permit screenshots
        // only for emulator-only debug QA, never for production or regular UI.
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0
            || !"com.brendigo.fakturiva.SCREENSHOT".equals(getIntent().getAction())){
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        root.setBackgroundColor(WHITE);
        body.setBackgroundColor(WHITE);
        pad(body,25,65);
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.brand_mark);
        LinearLayout.LayoutParams markLp=lp(72,72);
        body.addView(mark,markLp);
        gap(body,26);
        place(body,label(locked?"Otključajte Fakturivu":"Zaštitite svoje poslovanje",26,NAVY,true));
        gap(body,9);
        place(body,label(locked?
            "Potvrdite identitet zaključavanjem uređaja.":
            "Odaberite želite li dodatnu zaštitu pristupa Fakturivi.",13,MUTED,false));
        gap(body,82);

        place(body,label("SIGURNOST APLIKACIJE",11,0xff6a81a5,true));
        gap(body,5);
        LinearLayout guard=vertical();
        guard.setBackground(outlined(WHITE));
        pad(guard,15,5);
        place(guard,label("Zaštita pri ponovnom otvaranju",15,NAVY,true));
        gap(guard,7);
        place(guard,label("Koristi PIN, uzorak ili lozinku koju je postavio Android.",12,MUTED,false));
        place(body,guard);
        gap(body,37);
        LinearLayout local=vertical();
        local.setBackground(outlined(WHITE));
        pad(local,15,5);
        place(local,label("Vaša poslovna dokumentacija",14,NAVY,true));
        gap(local,7);
        place(local,label("Zaštitu možete promijeniti u Postavkama.",12,MUTED,false));
        place(body,local);
        gap(body,62);

        android.app.KeyguardManager manager=(android.app.KeyguardManager)
            getSystemService(Context.KEYGUARD_SERVICE);
        boolean secure=manager!=null&&manager.isDeviceSecure();
        if(locked){
            button(body,"Otključaj uređajem",()->confirmDeviceCredential(2));
        }else if(prefs.getBoolean("device_lock",false)){
            button(body,"Isključi zaštitu",()->confirmDeviceCredential(3));
        }else{
            button(body,secure?"Uključi zaštitu uređajem":"Postavi zaštitu uređaja",
                ()->confirmDeviceCredential(1));
        }
        gap(body,50);
        if(!locked){
            place(body,action(fromSettings?"Natrag na postavke":"Nastavi bez dodatne zaštite",
                BLUE,false,()->{
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    if(fromSettings)settings();
                    else{
                        prefs.edit().putBoolean("welcomed",true).apply();
                        navigate("home");
                        offerWorkingDraftRecovery();
                    }
                }));
        }
        gap(body,84);
        TextView note=label("Upravljajte računima i ponudama jednostavno.",11,MUTED,false);
        note.setGravity(Gravity.CENTER);
        place(body,note);
    }
    private void confirmDeviceCredential(int action){
        android.app.KeyguardManager manager=(android.app.KeyguardManager)
            getSystemService(Context.KEYGUARD_SERVICE);
        if(manager==null||!manager.isDeviceSecure()){
            new AlertDialog.Builder(this).setTitle("Uključite zaključavanje uređaja")
                .setMessage("Najprije postavite PIN, lozinku ili uzorak u Android postavkama.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Postavke uređaja",(d,w)->
                    startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)))
                .show();
            return;
        }
        Intent request=manager.createConfirmDeviceCredentialIntent("Fakturiva",
            action==2?"Potvrdite identitet za pristup podacima":
                "Potvrdite identitet za promjenu zaštite");
        if(request==null){toast("Provjera identiteta nije dostupna");return;}
        deviceAuthAction=action;
        try{startActivityForResult(request,DEVICE_AUTH_REQUEST);}
        catch(android.content.ActivityNotFoundException e){
            deviceAuthAction=0;
            toast("Provjera identiteta nije dostupna na uređaju");
        }
    }
    /** Entirely native, static illustration; all actions remain real UI controls. */
    private final class WelcomeIllustration extends View{
        private final android.graphics.Paint p=new android.graphics.Paint(3);
        WelcomeIllustration(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            canvas.save();canvas.scale(getWidth()/340f,getHeight()/340f);
            p.setStyle(android.graphics.Paint.Style.FILL);
            // Sampled from the reference's unobstructed card corners:
            // cool #E5EBFF above, near-white bottom-left, blue haze on
            // bottom-right. These layers remain vector gradients on any dpi.
            p.setShader(new android.graphics.LinearGradient(0,0,0,340,
                new int[]{0xffe5ebff,0xffe6ecff,0xffe9f0ff},
                new float[]{0f,.6f,1f},
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0,0,340,340,37,37,p);
            p.setShader(new android.graphics.RadialGradient(0,340,220,
                new int[]{0xbbfffcff,0x66fffcff,0x00fffcff},
                new float[]{0f,.35f,.70f},
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0,0,340,340,37,37,p);
            p.setShader(new android.graphics.RadialGradient(340,340,140,
                new int[]{0x55faf6fc,0x00faf6fc},null,
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0,0,340,340,37,37,p);
            p.setShader(null);
            p.setColor(0xffd6e4fb);canvas.drawCircle(170,164,120,p);
            // Raise the invoice sheet ~6 design units to match the owner's
            // reference without changing the original illustration bitmap.
            canvas.save();canvas.translate(0,-6);canvas.rotate(-5,170,174);
            p.setColor(0x330f2e72);p.setShadowLayer(19,0,16,0x330f2e72);
            canvas.drawRoundRect(74,63,281,288,20,20,p);p.clearShadowLayer();
            p.setColor(Color.WHITE);canvas.drawRoundRect(74,63,281,288,20,20,p);
            p.setColor(NAVY);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(11);canvas.drawText("FAKTURIVA",90,96,p);
            p.setTextSize(25);canvas.drawText("RAČUN",90,147,p);
            p.setColor(0xffe3eafd);
            canvas.drawRoundRect(90,158,255,165,5,5,p);
            canvas.drawRoundRect(90,177,193,184,5,5,p);
            p.setStrokeWidth(1);canvas.drawLine(90,223,256,223,p);
            p.setColor(0xff8191ad);p.setTextSize(11);p.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Ukupno",90,247,p);
            p.setColor(NAVY);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(16);canvas.drawText("1.250,00 €",192,248,p);
            // Small native verified stamp at the sheet's upper-right edge.
            p.setColor(0xff18c6b7);p.setStrokeWidth(1.2f);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            canvas.drawCircle(254.5f,88,6.1f,p);
            android.graphics.Path seal=new android.graphics.Path();
            seal.moveTo(251f,88);
            seal.lineTo(254.1f,91);
            seal.lineTo(259f,84.5f);
            canvas.drawPath(seal,p);
            p.setStyle(android.graphics.Paint.Style.FILL);
            canvas.restore();
            p.setColor(0xff18c6b7);p.setShadowLayer(12,0,10,0x5518c6b7);
            // The reference badge is a 64-unit rounded square rotated ~6
            // degrees, rather than a large horizontal axis-aligned badge.
            canvas.save();canvas.rotate(6f,284.5f,266f);
            canvas.drawRoundRect(252.5f,234,316.5f,298,20,20,p);
            p.clearShadowLayer();
            p.setColor(Color.WHITE);p.setStrokeWidth(2.6f);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            android.graphics.Path check=new android.graphics.Path();
            check.moveTo(277,262);check.lineTo(284,269);check.lineTo(295,255);
            canvas.drawPath(check,p);p.setStyle(android.graphics.Paint.Style.FILL);
            canvas.restore();
            canvas.restore();
        }
    }
    private void backupAlert(){
        if(db.backupWarning().isEmpty())return;
        LinearLayout warning=card();
        warning.addView(label("Provjerite sigurnosnu kopiju",14,RED,true));
        gap(warning,6);
        warning.addView(label(db.backupWarning(),12,ink(),false));
        gap(warning,9);
        warning.addView(action("Izvezi šifriranu kopiju",BLUE,false,this::backupScreen));
        place(body,warning);gap(body,12);
    }
    private void home(){
        setup("home",true);
        // The reference dashboard uses a 22 dp gutter on 393 dp phones.
        // Keep all dashboard figures sourced from the local SQLite database.
        // Reference Home gutter is 18dp at 393dp; rows remain width-adaptive.
        pad(body,18,16);
        backupAlert();
        LinearLayout bar=horizontal();
        LinearLayout brand=vertical();
        brand.addView(label("Fakturiva",24,ink(),true));
        brand.addView(label("Upravljanje poslovanjem",12,sub(),false));
        bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        ImageView search=icon(R.drawable.ic_search,ink());
        search.setBackground(shape(panel(),12));search.setPadding(dp(9),dp(9),dp(9),dp(9));
        search.setContentDescription("Pregledajte i pretražite račune");
        LinearLayout.LayoutParams searchLp=lp(42,42);searchLp.rightMargin=dp(8);
        bar.addView(search,searchLp);
        search.setOnClickListener(v->documents(Store.INVOICE));
        ImageView alerts=icon(R.drawable.ic_bell,ink());
        alerts.setBackground(shape(panel(),12));alerts.setPadding(dp(9),dp(9),dp(9),dp(9));
        alerts.setContentDescription("Obavijesti o neplaćenim računima");
        bar.addView(alerts,lp(42,42));alerts.setOnClickListener(v->showNotifications());
        // In dark mode, the reference greets lower without shifting its hero.
        // The original 393dp home reference positions the greeting closer
        // to the masthead than the former 41/25dp gaps.
        place(body,bar);gap(body,dark?35:19);

        if(RecoveryVault.hasWorkingDraft(this)){
            LinearLayout resume=card();
            resume.addView(label("Nedovršen račun ili ponuda",14,ink(),true));
            gap(resume,4);
            resume.addView(label("Automatski sačuvano · dodirnite za nastavak",11,sub(),false));
            resume.setOnClickListener(v->loadWorkingDraft());
            place(body,resume);gap(body,12);
        }

        LinearLayout welcome=horizontal();welcome.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words=vertical();
        SimpleDateFormat fullDate=new SimpleDateFormat("EEEE, d. MMMM",Locale.forLanguageTag("hr-HR"));
        words.addView(label(fullDate.format(new java.util.Date())
            .toUpperCase(Locale.forLanguageTag("hr-HR")),11,0xff6A81A5,true));
        gap(words,5);
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        words.addView(label((hour<11?"Dobro jutro":hour<18?"Dobar dan":"Dobra večer")+"! 👋",
            22,ink(),true));
        gap(words,5);
        words.addView(label("Evo pregleda vašeg poslovanja.",12,sub(),false));
        welcome.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        String issuer=db.setting("name").trim();
        String initial=issuer.isEmpty()?"B":issuer.substring(0,1)
            .toUpperCase(Locale.forLanguageTag("hr-HR"));
        TextView avatar=label(initial,23,BLUE,true);
        avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(dark?0xff273962:0xffe6e9ff,16));
        avatar.setContentDescription("Profil tvrtke");
        avatar.setOnClickListener(v->company());
        LinearLayout.LayoutParams avatarLp=lp(48,48);
        avatarLp.leftMargin=dp(10);welcome.addView(avatar,avatarLp);
        place(body,welcome);gap(body,dark?4:19);

        LinearLayout hero=vertical();pad(hero,19,dark?13:16);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            dark?new int[]{0xff19365d,0xff253b91}:new int[]{0xff143354,0xff243584});
        gradient.setCornerRadius(dp(21));hero.setBackground(gradient);
        long[] monthly=db.monthlyReceipts(selectedCurrency());
        LinearLayout heading=horizontal();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("Naplaćeno ovaj mjesec",12,0xffcedbf2,false),
            new LinearLayout.LayoutParams(0,-2,1));
        if(monthly[10]>0){
            double change=100.0*(monthly[11]-monthly[10])/(double)monthly[10];
            if(Double.isFinite(change)){
                TextView delta=label((change>=0?"↗ +":"↘ ")+
                    String.format(selectedLocale(),"%.1f%%",change),
                    11,change>=0?0xff79e7c6:0xffffb6a9,true);
                delta.setGravity(Gravity.CENTER);
                delta.setBackground(shape(0xff314c82,13));pad(delta,7,5);
                heading.addView(delta);
            }
        }
        place(hero,heading);gap(hero,13);
        hero.addView(label(money(monthly[11]),31,WHITE,true));gap(hero,5);
        hero.addView(label("Prema evidentiranim uplatama",11,0xffb8d2e7,false));
        gap(hero,8);
        hero.addView(new RevenueLine(this),lp(-1,43));
        place(body,hero);gap(body,dark?10:16);

        LinearLayout stats=horizontal();
        String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new java.util.Date());
        metric(stats,"Računi",String.valueOf(db.countMonth(Store.INVOICE,month)),"▤",BLUE);
        metric(stats,"Ponude",String.valueOf(db.countMonth(Store.QUOTE,month)),"▣",TEAL);
        place(body,stats);
        // A 48dp-tall arrow pushed the quick-action cards and recent
        // documents below their original y positions. Keep a broad 48dp
        // horizontal target, but a compact 36dp visual row instead.
        gap(body,1);
        LinearLayout quickHeader=horizontal();
        quickHeader.addView(label("Brze radnje",18,ink(),true),
            new LinearLayout.LayoutParams(0,-2,1));
        TextView moreActions=label("›",24,BLUE,true);
        moreActions.setGravity(Gravity.CENTER);
        moreActions.setContentDescription("Otvori sve brze radnje");
        moreActions.setMinimumWidth(dp(48));
        moreActions.setMinimumHeight(dp(36));
        quickHeader.addView(moreActions,lp(48,36));
        moreActions.setOnClickListener(v->showQuickActions());
        // Make the entire title row another comfortable way to open the
        // same fully functional menu, not just the compact right chevron.
        quickHeader.setContentDescription("Otvori sve brze radnje");
        quickHeader.setOnClickListener(v->showQuickActions());
        place(body,quickHeader);
        gap(body,1);
        LinearLayout fast=horizontal();
        quick(fast,"＋","Novi račun",()->newDocument(Store.INVOICE));
        quick(fast,"▣","Nova ponuda",()->newDocument(Store.QUOTE));
        quick(fast,"♧","Novi klijent",this::addClient);
        place(body,fast);

        gap(body,dark?14:19);
        LinearLayout recentHeading=horizontal();
        recentHeading.setGravity(Gravity.CENTER_VERTICAL);
        recentHeading.addView(label("Nedavni dokumenti",18,ink(),true),
            new LinearLayout.LayoutParams(0,-2,1));
        TextView all=label("Prikaži sve  ›",11,BLUE,true);
        all.setContentDescription("Otvori sve račune");
        all.setOnClickListener(v->documents(Store.INVOICE));recentHeading.addView(all);
        place(body,recentHeading);gap(body,11);
        int count=0;
        for(Store.Document document:db.documents(Store.INVOICE)){
            if(count++>=3)break;documentRow(document);
        }
        if(count==0)caption(body,"Još nema računa. Dodajte prvi dokument.");
        gap(body,18);
        if(!db.backupWarning().isEmpty()){
            gap(body,15);
            place(body,label(db.backupWarning(),12,RED,true));
        }
    }
    private void showQuickActions(){
        String[] names={"Novi račun","Nova ponuda","Novi klijent",
            "Artikli i usluge","Izvještaji","Naplata"};
        new AlertDialog.Builder(this).setTitle("Brze radnje")
            .setItems(names,(dialog,which)->{
                switch(which){
                    case 0:newDocument(Store.INVOICE);break;
                    case 1:newDocument(Store.QUOTE);break;
                    case 2:addClient();break;
                    case 3:catalog();break;
                    case 4:reports();break;
                    case 5:payments();break;
                    default:break;
                }
            }).setNegativeButton("Zatvori",null).show();
    }
    private void showNotifications(){
        ArrayList<Store.Document> open=new ArrayList<>();
        for(Store.Document d:db.documents(Store.INVOICE))
            if(Store.ISSUED.equals(d.status)&&d.outstanding()>0)open.add(d);
        if(open.isEmpty()){new AlertDialog.Builder(this).setTitle("Obavijesti")
            .setMessage("Trenutačno nema nenaplaćenih potvrđenih računa.").setPositiveButton("U redu",null).show();return;}
        String[] items=new String[open.size()];
        for(int i=0;i<items.length;i++){Store.Document d=open.get(i);
            items[i]=(d.number==null?"Nacrt":d.number)+" · "+d.clientName+" · "+docMoney(d,d.outstanding());}
        new AlertDialog.Builder(this).setTitle("Nepodmireni računi").setItems(items,(dialog,position)->
            showDocument(open.get(position).id)).setNegativeButton("Zatvori",null).show();
    }
    private int figureIcon(String symbol){
        if("▤".equals(symbol))return R.drawable.ic_invoice;
        if("▣".equals(symbol))return R.drawable.ic_quote;
        if("♧".equals(symbol))return R.drawable.ic_clients;
        return R.drawable.ic_add;
    }
    private void metric(LinearLayout row,String label,String value,String glyph,int accent){
        LinearLayout c=card();pad(c,14,8);
        ImageView image=icon(figureIcon(glyph),accent);
        image.setPadding(dp(8),dp(8),dp(8),dp(8));
        // Reference uses readable pastel icon tiles on both themes.
        image.setBackground(shape(accent==TEAL?0xffdff8f4:0xffe8edff,12));
        c.addView(image,lp(37,37));gap(c,6);
        c.addView(label(label,12,sub(),false));gap(c,3);
        c.addView(label(value,24,ink(),true));c.addView(label("Ovaj mjesec",10,sub(),false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);
        c.setContentDescription(label+": "+value+" ovaj mjesec, otvori popis");
        c.setFocusable(true);
        c.setOnClickListener(v->documents("Računi".equals(label)?Store.INVOICE:Store.QUOTE));
    }
    private void quick(LinearLayout row,String glyph,String label,Runnable action){
        // Compact quick-action cards must leave enough room for the full caption.
        LinearLayout c=card();pad(c,11,7);c.setGravity(Gravity.CENTER);
        boolean quote="▣".equals(glyph),client="♧".equals(glyph);
        int tint=quote?TEAL:client?0xffeb963c:BLUE;
        ImageView image=icon(figureIcon(glyph),tint);
        image.setPadding(dp(10),dp(10),dp(10),dp(10));
        image.setBackground(shape(
            quote?0xffdcf8f4:client?0xfffff0e3:0xffe8edff,12));
        LinearLayout.LayoutParams iconLp=lp(45,45);iconLp.gravity=Gravity.CENTER_HORIZONTAL;c.addView(image,iconLp);
        gap(c,7);TextView text=label(label,11,ink(),true);text.setGravity(Gravity.CENTER);place(c,text);
        // Quick actions can grow with 150% font scaling.
        c.setMinimumHeight(dp(90));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);
        c.setContentDescription(label);c.setFocusable(true);
        c.setOnClickListener(v->action.run());
    }
    private void documents(String type){
        boolean invoice=Store.INVOICE.equals(type);
        setup(invoice?"invoices":"quotes",true);
        // Reference invoice list starts lower with a ~19dp horizontal inset.
        pad(body,19,22);
        LinearLayout header=horizontal();
        LinearLayout heading=vertical();
        heading.addView(label(invoice?"Računi":"Ponude",23,ink(),true));
        gap(heading,5);
        heading.addView(label(invoice?"Svi vaši izdani računi":
            "Profesionalne ponude na jednom mjestu",12,sub(),false));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView searchAction=icon(R.drawable.ic_search,sub());
        searchAction.setPadding(dp(10),dp(10),dp(10),dp(10));
        searchAction.setBackground(shape(panel(),13));
        searchAction.setContentDescription("Pretraži dokumente");
        LinearLayout.LayoutParams searchLp=lp(42,42);
        searchLp.rightMargin=dp(8);header.addView(searchAction,searchLp);
        ImageView filtersAction=icon(R.drawable.ic_filter,sub());
        filtersAction.setPadding(dp(10),dp(10),dp(10),dp(10));
        filtersAction.setBackground(shape(panel(),13));
        filtersAction.setContentDescription("Odaberi prikaz dokumenata");
        header.addView(filtersAction,lp(42,42));
        place(body,header);gap(body,20);

        LinearLayout searchBar=horizontal();
        searchBar.setBackground(outlined(panel()));
        searchBar.setPadding(dp(12),dp(2),dp(12),dp(2));
        searchBar.addView(icon(R.drawable.ic_search,sub()),lp(25,25));
        EditText search=field(invoice?"Pretraži račune...":"Pretraži ponude...",
            documentSearchQuery);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams inputLp=new LinearLayout.LayoutParams(0,dp(42),1);
        inputLp.leftMargin=dp(8);searchBar.addView(search,inputLp);
        ImageView searchFilter=icon(R.drawable.ic_filter,sub());
        searchFilter.setContentDescription("Otvori filtre");
        searchFilter.setPadding(dp(5),dp(5),dp(5),dp(5));
        searchBar.addView(searchFilter,lp(34,34));
        searchAction.setOnClickListener(v->{
            search.requestFocus();
            android.view.inputmethod.InputMethodManager keyboard=
                (android.view.inputmethod.InputMethodManager)
                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.showSoftInput(search,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        String[] states=invoice?
            new String[]{"Svi","Plaćeni","Neplaćeni","Skice"}:
            new String[]{"Svi","Potvrđene","Skice"};
        Runnable chooseFilter=()->new AlertDialog.Builder(this)
            .setTitle("Filtriraj "+(invoice?"račune":"ponude"))
            .setSingleChoiceItems(states,java.util.Arrays.asList(states).indexOf(filter),
                (dialog,which)->{
                    filter=states[which];dialog.dismiss();documents(type);
                })
            .setNegativeButton("Odustani",null).show();
        filtersAction.setOnClickListener(v->chooseFilter.run());
        searchFilter.setOnClickListener(v->chooseFilter.run());
        place(body,searchBar);gap(body,16);

        LinearLayout chips=horizontal();pad(chips,3,3);
        chips.setBackground(shape(dark?0xff1d2a40:0xffe9edf4,15));
        for(String state:states){
            boolean selected=filter.equals(state);
            TextView chip=label(state,11,selected?BLUE:sub(),true);
            chip.setGravity(Gravity.CENTER);
            if(selected)chip.setBackground(shape(panel(),12));
            chip.setContentDescription("Prikaži: "+state);
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(0,dp(36),1);
            item.leftMargin=dp(1);item.rightMargin=dp(1);
            chips.addView(chip,item);
            chip.setOnClickListener(v->{filter=state;documents(type);});
        }
        place(body,chips);gap(body,12);

        List<Store.Document> all=db.documents(type);
        List<Store.Document> inCurrency=new ArrayList<>();
        for(Store.Document document:all)
            if(BillingCurrency.canonical(document.currencyCode).equals(selectedCurrency()))
                inCurrency.add(document);
        if(!invoice){
            // Active = confirmed quote whose validity has not expired.
            long[] overviewValues=activeQuoteTotals(inCurrency,DocumentStatus.today());
            LinearLayout overview=horizontal();
            quoteSummary(overview,"Aktivne ponude",String.valueOf(overviewValues[0]));
            quoteSummary(overview,"Vrijednost ponuda",money(overviewValues[1]));
            place(body,overview);
            gap(body,14);
        }
        LinearLayout totals=horizontal();
        TextView total=label("Ukupno "+(invoice?"računa":"ponuda")+"  "+all.size(),
            11,sub(),false);
        totals.addView(total,new LinearLayout.LayoutParams(0,-2,1));
        if(invoice){
            long unpaid=0;
            for(Store.Document document:inCurrency)
                if(Store.ISSUED.equals(document.status))
                    unpaid=Math.addExact(unpaid,document.outstanding());
            TextView remaining=label("Neplaćeno  "+money(unpaid),11,ink(),true);
            remaining.setSingleLine(true);
            totals.addView(remaining);
        }
        if(invoice){
            place(body,totals);
            gap(body,10);
        }

        LinearLayout group=vertical();
        group.setBackground(outlined(panel()));
        for(Store.Document document:all){
            if(matches(document,filter))documentRowGrouped(group,document);
        }
        place(body,group);
        TextView empty=label("Nema dokumenata za odabranu pretragu.",12,sub(),false);
        empty.setGravity(Gravity.CENTER);
        gap(body,12);place(body,empty);
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence value,int start,int before,int count){
                documentSearchQuery=value.toString();
                filterDocumentRows(group,documentSearchQuery,empty);
            }
            @Override public void afterTextChanged(android.text.Editable value){}
        });
        filterDocumentRows(group,documentSearchQuery,empty);
        body.setFocusableInTouchMode(true);body.requestFocus();
        floatingAction(invoice?"Dodaj novi račun":"Dodaj novu ponudu",()->newDocument(type));
    }

    /** Returns {count, gross euro cents} of non-expired confirmed quotes. */
    static long[] activeQuoteTotals(List<Store.Document> documents,String today){
        long count=0,value=0;
        for(Store.Document quote:documents){
            if(Store.QUOTE.equals(quote.type) && Store.SENT.equals(quote.status)
                && quote.dueDate!=null && quote.dueDate.compareTo(today)>=0){
                count=Math.addExact(count,1);
                value=Math.addExact(value,quote.totalCents);
            }
        }
        return new long[]{count,value};
    }

    /** Compact real-value metric cards for the quote list (screen 09). */
    private void quoteSummary(LinearLayout row,String title,String amount){
        LinearLayout panelView=card();
        pad(panelView,14,10);
        TextView descriptor=label(title,11,sub(),false);
        place(panelView,descriptor);
        gap(panelView,9);
        TextView figure=label(amount,20,ink(),true);
        figure.setSingleLine(true);
        figure.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(panelView,figure);
        panelView.setContentDescription(title+": "+amount);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(72),1);
        params.leftMargin=dp(3);
        params.rightMargin=dp(3);
        row.addView(panelView,params);
    }

    private void documentRowGrouped(LinearLayout target,Store.Document document){
        LinearLayout item=vertical();item.setTag(document);
        View divider=new View(this);divider.setBackgroundColor(border());
        item.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        // Quote rows retain accessible actions but follow the taller source cards.
        LinearLayout row=horizontal();pad(row,10,Store.QUOTE.equals(document.type)?14:12);
        ImageView symbol=icon(Store.INVOICE.equals(document.type)?
            R.drawable.ic_invoice:R.drawable.ic_quote,
            Store.INVOICE.equals(document.type)?BLUE:TEAL);
        symbol.setPadding(dp(9),dp(9),dp(9),dp(9));
        symbol.setBackground(shape(Store.INVOICE.equals(document.type)?
            0xffe8edff:0xffdcf8f4,12));
        row.addView(symbol,lp(38,38));
        LinearLayout description=vertical();
        LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(0,-2,1);
        descLp.leftMargin=dp(8);
        row.addView(description,descLp);
        String number=document.number==null?"Nacrt #"+document.id:document.number;
        TextView documentNumber=label(number,13,ink(),true);
        documentNumber.setSingleLine(true);
        documentNumber.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(description,documentNumber);gap(description,3);
        TextView client=label(document.clientName,11,sub(),false);
        client.setSingleLine(true);
        client.setEllipsize(android.text.TextUtils.TruncateAt.END);
        place(description,client);gap(description,3);
        place(description,label(readableDate(document.issueDate),10,sub(),false));
        // Explicit width is essential: a weighted client description otherwise
        // measures at full width and silently clips the amount on Android 10.
        LinearLayout amount=vertical();amount.setGravity(Gravity.RIGHT);
        TextView total=label(docMoney(document,document.totalCents),12,ink(),true);
        total.setGravity(Gravity.RIGHT);
        total.setSingleLine(true);
        total.setEllipsize(android.text.TextUtils.TruncateAt.END);
        amount.addView(total,new LinearLayout.LayoutParams(-1,-2));
        gap(amount,6);
        String status=DocumentStatus.label(document,DocumentStatus.today(),
            DocumentStatus.inSevenDays());
        String shortStatus="Djelomično plaćen".equals(status)?"Dio plaćen":status;
        TextView badge=label(shortStatus,10,badgeForeground(status),true);
        badge.setGravity(Gravity.CENTER);
        badge.setSingleLine(true);
        badge.setPadding(dp(8),dp(5),dp(8),dp(5));
        badge.setBackground(shape(badgeBackground(status),15));
        LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(-2,-2);
        badgeLp.gravity=Gravity.END;
        amount.addView(badge,badgeLp);
        row.addView(amount,lp(109,-2));
        place(item,row);target.addView(item,new LinearLayout.LayoutParams(-1,-2));
        item.setContentDescription(number+", "+document.clientName+", "+
            docMoney(document,document.totalCents)+", "+status);
        item.setOnClickListener(v->showDocument(document.id));
    }

    private void filterDocumentRows(LinearLayout group,String raw,TextView empty){
        String term=raw.trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
        int count=0;
        for(int index=0;index<group.getChildCount();index++){
            View child=group.getChildAt(index);
            if(!(child.getTag() instanceof Store.Document))continue;
            Store.Document document=(Store.Document)child.getTag();
            String searchable=(document.number+" "+document.clientName+" "+
                document.issueDate+" "+document.dueDate+" "+
                DocumentStatus.label(document,DocumentStatus.today(),
                    DocumentStatus.inSevenDays())).toLowerCase(Locale.forLanguageTag("hr-HR"));
            boolean show=searchable.contains(term);
            child.setVisibility(show?View.VISIBLE:View.GONE);
            if(show){
                ((LinearLayout)child).getChildAt(0)
                    .setVisibility(count==0?View.GONE:View.VISIBLE);
                count++;
            }
        }
        group.setVisibility(count==0?View.GONE:View.VISIBLE);
        empty.setVisibility(count==0?View.VISIBLE:View.GONE);
    }

    private String readableDate(String iso){
        if(iso==null||!iso.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))return "";
        return iso.substring(8,10)+". "+iso.substring(5,7)+". "+
            iso.substring(0,4)+".";
    }

    private int badgeForeground(String status){
        if("Plaćen".equals(status)||"Potvrđena".equals(status))return 0xff078a65;
        if("Dospijeva".equals(status))return 0xffaf6c08;
        if("Neplaćen".equals(status)||"Dospio".equals(status))return 0xffd44759;
        return dark?0xffd0d9ee:0xff61718b;
    }
    private int badgeBackground(String status){
        if("Plaćen".equals(status)||"Potvrđena".equals(status))return 0xffdcf8ec;
        if("Dospijeva".equals(status))return 0xfffff1d8;
        if("Neplaćen".equals(status)||"Dospio".equals(status))return 0xffffe8eb;
        return dark?0xff273953:0xffe9eef7;
    }

    static boolean matches(Store.Document d,String f){
        if("Skice".equals(f))return Store.DRAFT.equals(d.status);
        if("Plaćeni".equals(f))
            return Store.INVOICE.equals(d.type) && !Store.DRAFT.equals(d.status)
                && !Store.VOID.equals(d.status) &&
                (Store.PAID.equals(d.status) || d.outstanding()==0);
        if("Neplaćeni".equals(f))
            return Store.INVOICE.equals(d.type) && !Store.DRAFT.equals(d.status)
                && !Store.VOID.equals(d.status) && d.outstanding()>0;
        if("Potvrđene".equals(f)||"Poslane".equals(f))return Store.SENT.equals(d.status);
        return true;
    }
    private void documentRow(Store.Document document){
        LinearLayout card=vertical();
        card.setBackground(outlined(panel()));
        documentRowGrouped(card,document);
        ((LinearLayout)card.getChildAt(0)).getChildAt(0)
            .setVisibility(View.GONE);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.topMargin=dp(9);
        body.addView(card,params);
    }
    private String status(String s){switch(s){
        case Store.DRAFT:return "Nacrt";case Store.ISSUED:return "Potvrđeno";
        case Store.SENT:return "Potvrđena";case Store.PAID:return "Plaćeno";
        case Store.VOID:return "Stornirano";default:return s;
    }}
    private EditText field(String hint,String value){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(ink());
        e.setHintTextColor(sub());e.setTextSize(14);e.setHint(hint);e.setText(value);
        e.setSelectAllOnFocus(false);e.setBackground(outlined(panel()));pad(e,14,13);
        e.setMinimumHeight(dp(50));return e;
    }
    private void fieldLabel(LinearLayout target,String name,EditText field){
        fieldLabel(target,name,field,12);
    }
    private void fieldLabel(LinearLayout target,String name,EditText field,int precedingGapDp){
        gap(target,precedingGapDp);
        target.addView(label(name,12,ink(),true));
        gap(target,6);
        place(target,field);
    }
    static String quoteDateForDisplay(String iso){
        if(iso==null||!IsoDates.valid(iso))return iso==null?"":iso;
        return Integer.parseInt(iso.substring(8,10))+". "+
            Integer.parseInt(iso.substring(5,7))+". "+iso.substring(0,4)+".";
    }
    static String quoteDateToIso(String display){
        if(display==null)return "";
        String value=display.trim();
        java.util.regex.Matcher match=java.util.regex.Pattern.compile(
            "^(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})\\.?$"
        ).matcher(value);
        if(match.matches()){
            return String.format(Locale.ROOT,"%04d-%02d-%02d",
                Integer.parseInt(match.group(3)),Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(1)));
        }
        return value;
    }
    private void dateInput(EditText edit){dateInput(edit,false);}
    private void dateInput(EditText edit,boolean quoteDate){
        // Real platform DatePicker, with a local vector calendar affordance.
        android.graphics.drawable.Drawable calendar=getDrawable(R.drawable.ic_calendar);
        if(calendar!=null){
            calendar.mutate().setTint(sub());
            calendar.setBounds(0,0,dp(18),dp(18));
            edit.setCompoundDrawables(null,null,calendar,null);
            edit.setCompoundDrawablePadding(dp(9));
        }
        edit.setContentDescription(edit.getHint()+", otvori kalendar");
        edit.setFocusable(false);
        edit.setOnClickListener(v->{Calendar c=Calendar.getInstance();
            try{c.setTime(dateFormat.parse(quoteDateToIso(edit.getText().toString())));}
            catch(Exception ignored){}
            new DatePickerDialog(this,(p,y,m,d)->{
                String iso=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);
                edit.setText(quoteDate?quoteDateForDisplay(iso):iso);
            },
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
        });
    }
    private String later(int days){Calendar c=Calendar.getInstance();c.add(Calendar.DAY_OF_MONTH,days);return dateFormat.format(c.getTime());}
    /** An unfinished invoice or quote must survive process death and device reboot. */
    private boolean saveWorkingDraft(){
        try{
            JSONObject j=new JSONObject();
            j.put("version",1);j.put("type",editType);j.put("savedId",editingDraftId);
            j.put("clientId",editClientId);j.put("issue",draftIssue);
            j.put("due",draftDue);j.put("supply",draftSupply);j.put("notes",draftNote);
            j.put("quoteTitle",draftQuoteTitle);
            JSONArray lines=new JSONArray();
            for(Store.Item i:draftItems){
                JSONObject row=new JSONObject();
                row.put("name",i.name);row.put("qty",i.qtyMilli);
                row.put("price",i.unitCents);row.put("vat",i.taxRate);
                lines.put(row);
            }
            j.put("items",lines);
            RecoveryVault.saveWorkingDraft(this,j);
            return true;
        }catch(Exception e){
            android.util.Log.e("FakturivaRecovery","Working draft could not be saved",e);
            toast("Upozorenje: radni nacrt nije spremljen. Provjerite slobodan prostor i zaštitu uređaja.");
            return false;
        }
    }
    private void loadWorkingDraft(){
        try{
            JSONObject j=RecoveryVault.readWorkingDraft(this);
            if(j==null)return;
            if(j.optInt("version")!=1)throw new IllegalArgumentException("Nepodržan format radnog nacrta");
            String type=j.getString("type");
            if(!Store.INVOICE.equals(type)&&!Store.QUOTE.equals(type))
                throw new IllegalArgumentException("Nepoznata vrsta dokumenta");
            ArrayList<Store.Item> loaded=new ArrayList<>();
            JSONArray lines=j.getJSONArray("items");
            if(lines.length()>300)throw new IllegalArgumentException("Previše stavki nacrta");
            for(int a=0;a<lines.length();a++){
                JSONObject row=lines.getJSONObject(a);Store.Item item=new Store.Item();
                item.name=row.getString("name");item.qtyMilli=row.getLong("qty");
                item.unitCents=row.getLong("price");item.taxRate=row.getInt("vat");
                item.net();item.tax();loaded.add(item);
            }
            editType=type;editingDraftId=j.getLong("savedId");
            editClientId=j.getLong("clientId");
            draftIssue=j.getString("issue");draftDue=j.getString("due");draftSupply=j.optString("supply",draftIssue);
            draftNote=j.optString("notes");
            draftQuoteTitle=j.optString("quoteTitle","");
            draftItems.clear();draftItems.addAll(loaded);
            if(editingDraftId>0){
                Store.Document existing=db.document(editingDraftId);
                if(!Store.DRAFT.equals(existing.status))
                    throw new IllegalStateException("Dokument je u međuvremenu potvrđen. Izaberite drugi nacrt.");
            }
            editDocumentScreen();
        }catch(Exception e){error(e);}
    }
    private void offerWorkingDraftRecovery(){
        if(!RecoveryVault.hasWorkingDraft(this))return;
        new AlertDialog.Builder(this)
            .setTitle("Pronađen je nedovršeni dokument")
            .setMessage("Fakturiva je automatski sačuvala promjene. Nastavite rad bez ponovnog unosa.")
            .setPositiveButton("Nastavi uređivanje",(d,w)->loadWorkingDraft())
            .setNegativeButton("Kasnije",null)
            .show();
    }
    private void watchDraft(EditText input,Runnable onChange){
        input.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){onChange.run();saveWorkingDraft();}
            @Override public void afterTextChanged(android.text.Editable s){}
        });
    }
    private void newDocument(String type){
        if(RecoveryVault.hasWorkingDraft(this)){
            new AlertDialog.Builder(this).setTitle("Nedovršeni dokument")
                .setMessage("Pronađen je automatski spremljen radni nacrt. Pokretanjem novog dokumenta prethodni radni nacrt bit će odbačen. Već spremljeni računi i ponude ostaju netaknuti.")
                .setPositiveButton("Nastavi prethodni",(d,w)->loadWorkingDraft())
                .setNeutralButton("Započni novi",(d,w)->startEmptyDocument(type))
                .setNegativeButton("Odustani",null).show();
        }else startEmptyDocument(type);
    }
    private void startEmptyDocument(String type){
        editType=type;editingDraftId=-1;draftItems.clear();draftIssue=later(0);
        draftDue=later(type.equals(Store.INVOICE)?15:30);
        draftSupply=type.equals(Store.QUOTE)?later(15):draftIssue;
        draftNote="";draftQuoteTitle="";editClientId=-1;
        saveWorkingDraft();editDocumentScreen();
    }
    private void resumeSavedDraft(long id){
        if(RecoveryVault.hasWorkingDraft(this) && editingDraftId!=id){
            new AlertDialog.Builder(this).setTitle("Postoji nedovršeni radni dokument")
                .setMessage("Prije otvaranja spremljenog nacrta nastavite prethodno uređivanje ili potvrdite da ga želite odbaciti.")
                .setPositiveButton("Nastavi radni dokument",(d,w)->loadWorkingDraft())
                .setNeutralButton("Otvori odabrani nacrt",(d,w)->{
                    RecoveryVault.clearWorkingDraft(this);resumeSavedDraft(id);
                })
                .setNegativeButton("Odustani",null).show();
            return;
        }
        try{
            Store.Document d=db.document(id);
            if(!Store.DRAFT.equals(d.status))throw new IllegalStateException("Možete uređivati samo nacrt");
            editingDraftId=d.id;editType=d.type;editClientId=d.clientId;
            draftIssue=d.issueDate;draftDue=d.dueDate;
            draftSupply=d.supplyDate==null||d.supplyDate.isEmpty()?d.issueDate:d.supplyDate;
            draftNote=d.notes;draftQuoteTitle=d.quoteTitle==null?"":d.quoteTitle;
            draftItems.clear();draftItems.addAll(d.items);
            saveWorkingDraft();editDocumentScreen();
        }catch(Exception e){error(e);}
    }
    /** One shared, non-interactive progress indicator for both invoices and quotes. */
    /** Fixed primary action above the bottom navigation: forms stay scrollable. */
    private void stickyPrimary(String text,Runnable click){
        LinearLayout container=vertical();
        container.setBackgroundColor(bg());
        container.setPadding(dp(17),dp(7),dp(17),dp(8));
        container.addView(action(text,BLUE,true,click),lp(-1,51));
        root.addView(container,1,new LinearLayout.LayoutParams(-1,-2));
    }
    private void wizardSteps(int current){
        LinearLayout steps=horizontal();steps.setGravity(Gravity.CENTER_VERTICAL);
        String[] names={"Podaci","Stavke","Pregled"};
        for(int i=0;i<3;i++){
            if(i>0){
                View line=new View(this);line.setBackgroundColor(i<current?TEAL:border());
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(2),0.65f);
                lp.setMargins(dp(4),0,dp(4),dp(21));steps.addView(line,lp);
            }
            LinearLayout cell=vertical();cell.setGravity(Gravity.CENTER);
            final boolean complete=i+1<current;
            TextView dot=label(complete?"✓":String.valueOf(i+1),12,
                complete?0xff15996d:(i+1==current?WHITE:sub()),true);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(shape(complete?
                (dark?0xff2f6558:0xffdcf7ed):
                (i+1==current?BLUE:(dark?0xff253550:0xffe9edff)),18));
            LinearLayout.LayoutParams dotLp=lp(32,32);dotLp.gravity=Gravity.CENTER;
            cell.addView(dot,dotLp);gap(cell,5);
            TextView label=label(names[i],10,i+1==current?BLUE:sub(),i+1==current);
            label.setGravity(Gravity.CENTER);place(cell,label);
            steps.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
        }
        place(body,steps);gap(body,17);
    }
    private void editDocumentScreen(){
        boolean quote=Store.QUOTE.equals(editType);
        setup("new_doc",true);
        // Match invoice reference vertically while retaining quote geometry.
        pad(body,quote?18:15,quote?17:21);
        toolbar(quote?"Nova ponuda":"Novi račun",
            quote?"Sastavite novu poslovnu ponudu":"Izradite novi dokument",true);
        // Quote reference positions the stepper lower, without a bitmap overlay.
        gap(body,quote?14:11);
        wizardSteps(1);
        List<Store.Client> clients=db.clients();

        LinearLayout basics=card();
        place(basics,label(quote?"Nova ponuda":"Osnovni podaci",
            quote?18:16,ink(),true));gap(basics,quote?16:12);
        String typeName=Store.INVOICE.equals(editType)?"računa":"ponude";
        basics.addView(label("Broj "+typeName,12,sub(),true));gap(basics,6);
        basics.addView(label("Currency: "+(editingDraftId>0?
            db.document(editingDraftId).currencyCode:selectedCurrency()),11,sub(),false));
        gap(basics,6);
        TextView sequence=label(editingDraftId>0?
            "Nacrt · broj nakon potvrde":"Dodjeljuje se pri potvrdi",13,ink(),false);
        pad(sequence,14,quote?9:13);sequence.setBackground(outlined(panel()));
        place(basics,sequence);
        if(clients.isEmpty()){
            gap(basics,12);
            basics.addView(label("Nema klijenata. Dodajte klijenta za nastavak.",12,sub(),false));
            gap(basics,12);button(basics,"＋ Dodaj klijenta",this::addClient);
            place(body,basics);return;
        }
        // A new invoice must never silently select the first client.
        String[] names=new String[clients.size()+1];int current=0;
        names[0]="Odaberite klijenta";
        for(int i=0;i<clients.size();i++){
            names[i+1]=clients.get(i).name;
            if(clients.get(i).id==editClientId)current=i+1;
        }
        gap(basics,quote?9:10);basics.addView(label("Klijent",12,sub(),true));gap(basics,6);
        LinearLayout choose=horizontal();choose.setGravity(Gravity.CENTER_VERTICAL);
        choose.setBackground(outlined(panel()));
        choose.setPadding(dp(12),dp(2),dp(12),dp(2));
        Spinner selector=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(
            this,android.R.layout.simple_spinner_dropdown_item,names);
        selector.setAdapter(adapter);selector.setSelection(current);
        // Remove the duplicate platform arrow; use the custom chevron.
        selector.setBackgroundColor(Color.TRANSPARENT);
        choose.addView(selector,new LinearLayout.LayoutParams(0,dp(38),1));
        choose.addView(label("›",23,sub(),false));
        place(basics,choose);
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                editClientId=pos==0?-1:clients.get(pos-1).id;
                saveWorkingDraft();
            }
        });
        // Show dates consistently in Croatian notation on both workflows;
        // the draft still persists validated ISO 8601 dates internally.
        EditText issued=field("Datum izdavanja",
            quoteDateForDisplay(draftIssue));
        issued.setPadding(dp(14),dp(9),dp(14),dp(9));
        issued.setMinimumHeight(dp(quote?43:44));
        dateInput(issued,true);
        fieldLabel(basics,quote?"Datum ponude":"Datum izdavanja",issued,8);
        EditText due=field("Datum dospijeća / valjanosti",
            quoteDateForDisplay(draftDue));
        due.setPadding(dp(14),dp(9),dp(14),dp(9));
        due.setMinimumHeight(dp(quote?43:44));
        dateInput(due,true);
        fieldLabel(basics,quote?"Vrijedi do":"Datum dospijeća",due,8);
        final EditText quoteTitle=quote?field("Npr. Usluge digitalnog marketinga",
            draftQuoteTitle):null;
        if(quote){
            quoteTitle.setPadding(dp(14),dp(9),dp(14),dp(9));
            quoteTitle.setMinimumHeight(dp(43));
            fieldLabel(basics,"Naslov ponude",quoteTitle,8);
            quoteTitle.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(200)
            });
        }else{
            String method=db.setting("payment_method").trim();
            gap(basics,10);basics.addView(label("Način plaćanja",12,sub(),true));gap(basics,6);
            LinearLayout paying=horizontal();paying.setGravity(Gravity.CENTER_VERTICAL);
            paying.setBackground(outlined(panel()));
            paying.setPadding(dp(13),dp(9),dp(13),dp(9));
            paying.addView(label(method.isEmpty()?"Odaberite u profilu tvrtke":method,
                13,ink(),false),new LinearLayout.LayoutParams(0,-2,1));
            paying.addView(label("›",20,sub(),false));
            paying.setContentDescription("Uredi način plaćanja u profilu tvrtke");
            paying.setOnClickListener(v->{saveWorkingDraft();company();});
            place(basics,paying);
        }
        place(body,basics);gap(body,quote?15:13);

        LinearLayout additional=card();
        place(additional,label(quote?"Uvjeti ponude":"Dodatne informacije",18,ink(),true));
        EditText supply=field(quote?"Rok izvedbe":"Datum isporuke ili obavljene usluge",
            quote?quoteDateForDisplay(draftSupply):draftSupply);
        dateInput(supply,quote);
        fieldLabel(additional,quote?"Rok izvedbe":"Datum isporuke / usluge",supply);
        EditText notes=field("Neobavezna napomena",draftNote);
        if(quote){
            notes.setSingleLine(false);
            notes.setMinLines(2);
            notes.setInputType(InputType.TYPE_CLASS_TEXT|
                InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        }
        fieldLabel(additional,"Napomena",notes);
        place(body,additional);gap(body,18);

        watchDraft(issued,()->draftIssue=quoteDateToIso(issued.getText().toString()));
        watchDraft(due,()->draftDue=quoteDateToIso(due.getText().toString()));
        watchDraft(supply,()->draftSupply=quoteDateToIso(supply.getText().toString()));
        watchDraft(notes,()->draftNote=notes.getText().toString());
        if(quote)watchDraft(quoteTitle,()->draftQuoteTitle=quoteTitle.getText().toString());
        stickyPrimary("Nastavi na stavke  ›",()->{
            int chosen=selector.getSelectedItemPosition();
            if(chosen<=0){
                toast("Odaberite klijenta prije nastavka");
                return;
            }
            editClientId=clients.get(chosen-1).id;
            draftIssue=quoteDateToIso(issued.getText().toString());
            draftDue=quoteDateToIso(due.getText().toString());
            draftSupply=quoteDateToIso(supply.getText().toString());
            draftNote=notes.getText().toString();
            if(quote)draftQuoteTitle=quoteTitle.getText().toString().trim();
            try{IsoDates.validate(draftIssue,draftDue);
                if(!IsoDates.valid(draftSupply))throw new IllegalArgumentException(
                    "Neispravan datum isporuke / usluge");
                if(saveWorkingDraft())editItemsScreen();
            }catch(Exception error){error(error);}
        });
    }
    private void editItemsScreen(){
        boolean invoice=Store.INVOICE.equals(editType);
        setup("edit_items",true);
        pad(body,15,invoice?21:14);
        toolbar(invoice?"Stavke računa":"Stavke ponude",
            "2. korak od 3",true);
        if(invoice)gap(body,11);
        wizardSteps(2);
        if(draftItems.isEmpty())caption(body,"Dodajte usluge ili artikle u dokument.");
        for(int index=0;index<draftItems.size();index++){
            Store.Item item=draftItems.get(index);
            final int selected=index;
            // Compact card geometry matches the reference's invoice rows.
            // Deletion remains available in the actual item editor.
            LinearLayout c=card();pad(c,13,9);
            LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);
            TextView itemName=label(item.name,14,ink(),true);
            itemName.setSingleLine(true);
            itemName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(itemName,new LinearLayout.LayoutParams(0,-2,1));
            ImageView edit=icon(R.drawable.ic_edit,sub());
            edit.setPadding(dp(6),dp(6),dp(6),dp(6));
            edit.setContentDescription("Uredi ili ukloni stavku "+item.name);
            header.addView(edit,lp(34,34));
            edit.setOnClickListener(v->editLine(selected));
            place(c,header);gap(c,1);
            place(c,label(Money.decimalQuantity(item.qtyMilli)+" × "+
                draftMoney(item.unitCents)+" · Tax "+Money.rateLabel(item.taxRate),11,sub(),false));
            gap(c,4);
            TextView amount=label(draftMoney(Math.addExact(item.net(),item.tax())),
                16,ink(),true);
            amount.setGravity(Gravity.RIGHT);
            place(c,amount);
            c.setContentDescription(item.name+" — "+
                draftMoney(Math.addExact(item.net(),item.tax()))+
                ". Dodirnite za uređivanje ili uklanjanje.");
            c.setFocusable(true);
            c.setOnClickListener(v->editLine(selected));
            place(body,c);gap(body,9);
        }
        TextView add=label("＋  Dodaj artikl ili uslugu",13,BLUE,true);
        add.setGravity(Gravity.CENTER);add.setContentDescription("Dodaj stavku dokumenta");
        GradientDrawable outline=shape(dark?0xff1b2b4b:0xffeef0ff,15);
        outline.setStroke(dp(1),0xff97a1ff,dp(5),dp(4));
        add.setBackground(outline);add.setMinimumHeight(dp(53));
        add.setOnClickListener(v->addLine());
        place(body,add);gap(body,14);

        long net=0,tax=0;
        Integer commonRate=null;
        boolean mixedRates=false;
        for(Store.Item item:draftItems){
            net=Math.addExact(net,item.net());tax=Math.addExact(tax,item.tax());
            if(commonRate==null)commonRate=item.taxRate;
            else if(commonRate!=item.taxRate)mixedRates=true;
        }
        LinearLayout summary=card();
        title(summary,Store.INVOICE.equals(editType)?"Sažetak računa":"Sažetak ponude");
        gap(summary,14);
        amountRow(summary,"Osnovica",draftMoney(net),false);
        gap(summary,12);
        String taxCaption=commonRate==null||mixedRates?"PDV":
            "Tax ("+Money.rateLabel(commonRate)+")";
        amountRow(summary,taxCaption,draftMoney(tax),false);
        gap(summary,12);
        View line=new View(this);line.setBackgroundColor(border());
        summary.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        gap(summary,15);
        amountRow(summary,"UKUPNO",draftMoney(Math.addExact(net,tax)),true);
        place(body,summary);gap(body,12);
        caption(body,"Iznosi se automatski računaju iz unesenih stavki i poreznih stopa.");
        // Screen 07 has only the native toolbar back arrow. Keep the
        // extra back action on the quote workflow, where parity is separate.
        if(!invoice){
            gap(body,13);
            secondary(body,"‹ Natrag na podatke",this::editDocumentScreen);
        }
        stickyPrimary(Store.INVOICE.equals(editType)?"Pregled računa  ›":"Pregled ponude  ›",()->{
            if(draftItems.isEmpty())toast("Dodajte barem jednu stavku");
            else reviewDraft();
        });
    }
    private void amountRow(LinearLayout target,String title,String value,boolean total){
        LinearLayout row=horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title,total?13:12,total?ink():sub(),total),
            new LinearLayout.LayoutParams(0,-2,1));
        row.addView(label(value,total?21:13,ink(),true));
        place(target,row);
    }
    private void addLine(){editLine(-1);}
    /** Edit/add an invoice line without losing the user's input on validation failure. */
    private void editLine(int index){
        boolean editing=index>=0;
        Store.Item current=editing?draftItems.get(index):null;
        LinearLayout form=vertical();pad(form,16,5);
        EditText name=field("Naziv artikla ili usluge",editing?current.name:"");
        EditText qty=field("Količina",editing?
            Money.decimalQuantity(current.qtyMilli):"1");
        EditText price=field("Unit price ("+draftCurrency()+")",editing?
            String.format(selectedLocale(),"%.2f",current.unitCents/100.0):"0,00");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText tax=field("Tax rate (%)",editing?
            Money.rateLabel(current.taxRate).replace("%",""):"0");
        tax.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fieldLabel(form,"Stavka",name);
        List<Store.Item> catalog=new ArrayList<>();
        for(Store.Item item:db.products())
            if(draftCurrency().equals(BillingCurrency.canonical(item.currencyCode)))
                catalog.add(item);
        if(!catalog.isEmpty()){
            Spinner product=new Spinner(this);
            ArrayList<String> options=new ArrayList<>();
            options.add("Odaberite iz kataloga (neobavezno)");
            for(Store.Item item:catalog)options.add(item.name);
            product.setAdapter(new ArrayAdapter<>(
                this,android.R.layout.simple_spinner_dropdown_item,options));
            product.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                        View view,int pos,long id){
                    if(pos<=0)return;
                    Store.Item item=catalog.get(pos-1);
                    name.setText(item.name);
                    price.setText(String.format(selectedLocale(),
                        "%.2f",item.unitCents/100.0));
                    tax.setText(Money.rateLabel(item.taxRate).replace("%",""));
                }
            });
            gap(form,12);
            form.addView(label("Iz kataloga",12,ink(),true));gap(form,6);
            place(form,product);
        }
        fieldLabel(form,"Količina",qty);
        fieldLabel(form,"Unit price before tax",price);
        fieldLabel(form,"Tax rate (%)",tax);
        AlertDialog.Builder editor=new AlertDialog.Builder(this)
            .setTitle(editing?"Uredi stavku":"Nova stavka")
            .setView(form).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null);
        if(editing)editor.setNeutralButton("Ukloni",null);
        AlertDialog dialog=editor.create();
        dialog.setOnShowListener(ignored->{
            if(editing){
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(button->
                    new AlertDialog.Builder(this)
                        .setTitle("Ukloniti stavku?")
                        .setMessage(current.name+
                            " bit će uklonjena samo iz radnog nacrta.")
                        .setNegativeButton("Odustani",null)
                        .setPositiveButton("Ukloni",(confirmation,which)->{
                            Store.Item removed=draftItems.remove(index);
                            if(!saveWorkingDraft()){
                                draftItems.add(index,removed);
                                return;
                            }
                            dialog.dismiss();
                            editItemsScreen();
                        }).show());
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                try{
                    String description=name.getText().toString().trim();
                    if(description.isEmpty()){
                        name.setError("Unesite naziv stavke");name.requestFocus();return;
                    }
                    Store.Item item=new Store.Item();item.name=description;
                    try{item.qtyMilli=Money.quantity(qty.getText().toString());}
                    catch(Exception e){qty.setError("Provjerite količinu");qty.requestFocus();return;}
                    try{item.unitCents=Money.cents(price.getText().toString());}
                    catch(Exception e){price.setError("Provjerite cijenu");price.requestFocus();return;}
                    if(item.qtyMilli<=0||item.unitCents<0){
                        qty.setError("Količina mora biti veća od nule");
                        qty.requestFocus();return;
                    }
                    try{item.taxRate=Money.parseRate(tax.getText().toString());}
                    catch(Exception e){tax.setError("Enter a valid tax rate");tax.requestFocus();return;}
                    Math.addExact(item.net(),item.tax());
                    if(editing)draftItems.set(index,item);else draftItems.add(item);
                    if(!saveWorkingDraft()){
                        if(editing)draftItems.set(index,current);
                        else draftItems.remove(draftItems.size()-1);
                        return;
                    }
                    dialog.dismiss();editItemsScreen();
                }catch(Exception error){error(error);}
            });
        });
        dialog.show();
    }
    private void reviewDraft(){
        saveWorkingDraft();
        setup("review",true);toolbar("Pregled dokumenta","Provjera i potvrda",true);
        wizardSteps(3);
        LinearLayout summary=card();
        summary.addView(label(editType.equals(Store.INVOICE)?"Račun · nacrt":"Ponuda · nacrt",19,BLUE,true));gap(summary,13);
        String client="";
        for(Store.Client c:db.clients())if(c.id==editClientId)client=c.name;
        summary.addView(label("Klijent: "+client,13,ink(),true));gap(summary,6);
        summary.addView(label("Datum: "+draftIssue+"  •  Do: "+draftDue,11,sub(),false));
        if(Store.QUOTE.equals(editType)&&!draftQuoteTitle.trim().isEmpty()){
            gap(summary,6);
            summary.addView(label("Naslov ponude: "+draftQuoteTitle.trim(),12,ink(),true));
        }
        summary.addView(label(Store.QUOTE.equals(editType)?"Rok izvedbe: "+draftSupply:
            "Isporuka / usluga: "+draftSupply,11,sub(),false));gap(summary,16);
        for(Store.Item i:draftItems){summary.addView(label(i.name+"  ·  "+draftMoney(i.net()+i.tax()),12,ink(),false));gap(summary,8);}
        long net=0,tax=0;for(Store.Item i:draftItems){net+=i.net();tax+=i.tax();}
        gap(summary,15);summary.addView(label("Osnovica: "+draftMoney(net),12,sub(),false));
        summary.addView(label("PDV: "+draftMoney(tax),12,sub(),false));gap(summary,9);
        summary.addView(label("UKUPNO: "+draftMoney(net+tax),24,BLUE,true));place(body,summary);
        gap(body,16);button(body,"Spremi nacrt",()->saveNew(false));
        gap(body,8);secondary(body,"Potvrdi dokument",()->confirmWarning(()->saveNew(true)));
        gap(body,8);secondary(body,"‹ Uredi stavke",this::editItemsScreen);
        gap(body,15);caption(body,"Broj dokumenta dodjeljuje se pri potvrdi.");
    }
    private void saveNew(boolean confirm){
        try{
            long id;
            if(editingDraftId>0){
                id=editingDraftId;
                db.updateDraft(id,editClientId,draftIssue,draftDue,draftSupply,
                    draftNote,draftQuoteTitle,draftItems);
            }else {
                id=db.createDraft(editType,editClientId,draftIssue,draftDue,draftSupply,
                    draftNote,draftQuoteTitle,draftItems);
                editingDraftId=id;saveWorkingDraft();
            }
            if(confirm)db.confirm(id);
            RecoveryVault.clearWorkingDraft(this);
            editingDraftId=-1;draftItems.clear();showDocument(id);
        }catch(Exception e){error(e);}
    }
    private void confirmWarning(Runnable yes){
        new AlertDialog.Builder(this).setTitle("Potvrda dokumenta")
            .setMessage("Dokument će dobiti konačni broj i više se neće moći uređivati kao nacrt. Želite li nastaviti?")
            .setNegativeButton("Odustani",null).setPositiveButton("Potvrdi",(d,w)->yes.run()).show();
    }
    /** Screen 08/11: actual persisted document, not a static design mockup. */
    private void showDocument(long id){
        currentDocumentId=id;
        Store.Document document;
        try{document=db.document(id);}
        catch(Exception e){error(e);navigate("home");return;}
        final Store.Document d=document;
        boolean invoice=Store.INVOICE.equals(d.type);
        boolean draft=Store.DRAFT.equals(d.status);

        setup("detail",true);
        // Keep invoice masthead and actions aligned with the reference.
        // Quote detail remains on its pre-existing independent geometry.
        pad(body,18,invoice?20:13);
        toolbar(invoice?"Pregled računa":"Pregled ponude",
            draft?(invoice?"Provjerite podatke prije izdavanja":
                "Provjerite podatke prije potvrde"):
                (invoice?"Pregled potvrđenog dokumenta":"Ponuda pripremljena za dijeljenje"),true);
        backupAlert();

        // Status, true persisted number and contextual actions match the
        // reference hierarchy. Drafts must never display invented numbers.
        LinearLayout statusBar=horizontal();
        String visibleStatus=invoice&&draft?"Nacrt":
            DocumentStatus.label(d,DocumentStatus.today(),DocumentStatus.inSevenDays());
        TextView statusLabel=label(visibleStatus,11,draft?sub():ink(),true);
        statusLabel.setGravity(Gravity.CENTER);
        statusLabel.setPadding(dp(10),dp(6),dp(10),dp(6));
        statusLabel.setBackground(shape(draft?(dark?0xff273953:0xffedf1f6):
            badgeBackground(DocumentStatus.label(d,DocumentStatus.today(),
                DocumentStatus.inSevenDays())),14));
        statusBar.addView(statusLabel);
        TextView number=label(d.number==null?"Broj nakon potvrde":d.number,
            d.number==null?11:14,ink(),true);
        number.setGravity(Gravity.CENTER);
        statusBar.addView(number,new LinearLayout.LayoutParams(0,-2,1));
        TextView options=label("•••",18,ink(),true);
        options.setGravity(Gravity.CENTER);
        options.setContentDescription("Opcije dokumenta");
        options.setFocusable(true);
        options.setBackground(shape(panel(),12));
        statusBar.addView(options,lp(38,38));
        options.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Dokument")
            .setItems(new String[]{"Pregledaj PDF","Spremi PDF","Podijeli PDF","Print / POS printer"},
                (dialog,which)->{
                    if(which==0)showActualPdfPreview(d);
                    else if(which==1)savePdf(id);
                    else if(which==2)sharePdf(id);
                    else showPrinterDialog(id);
                }).setNegativeButton("Zatvori",null).show());
        place(body,statusBar);gap(body,invoice?6:12);

        place(body,documentPaper(d));
        gap(body,13);

        LinearLayout actions=horizontal();
        TextView save=action(invoice?"↓  Preuzmi PDF":"↓  PDF",
            BLUE,false,()->savePdf(id));
        save.setContentDescription("Spremi PDF dokument");
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(50),1);
        left.rightMargin=dp(6);
        actions.addView(save,left);
        TextView share=action(invoice?"➤  Pošalji":"➤  Pošalji ponudu",
            BLUE,true,()->sharePdf(id));
        share.setContentDescription("Podijeli PDF dokument");
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(50),1);
        right.leftMargin=dp(6);
        actions.addView(share,right);
        place(body,actions);gap(body,10);
        secondary(body,"Print invoice or quote",()->showPrinterDialog(id));
        gap(body,10);

        if(draft){
            place(body,action(invoice?"Potvrdi račun":"Potvrdi ponudu",
                0xff17b99a,true,()->confirmWarning(()->{
                    try{db.confirm(id);showDocument(id);}
                    catch(Exception e){error(e);}
                })));
            gap(body,9);
            secondary(body,"Uredi nacrt",()->resumeSavedDraft(id));
        }else if(invoice && Store.ISSUED.equals(d.status) && d.outstanding()>0){
            place(body,action("Evidentiraj uplatu",0xff17b99a,true,
                ()->payDialog(id)));
        }else if(!invoice && Store.SENT.equals(d.status)){
            place(body,action("Pretvori u nacrt računa",0xff17b99a,true,()->{
                try{
                    long invoiceId=db.quoteToInvoice(id);
                    showDocument(invoiceId);
                    toast("Stvoren je nacrt računa");
                }catch(Exception e){error(e);}
            }));
        }
        gap(body,12);
        caption(body,invoice?
            "Račun možete spremiti kao PDF ili ga podijeliti.":
            "Ponudu možete spremiti kao PDF ili je podijeliti.");
        gap(body,24);
    }

    /** Paper-like native preview with exactly the saved business data. */
    private LinearLayout documentPaper(Store.Document d){
        LinearLayout paper=vertical();
        // Invoice reference requires a slightly taller white paper inset.
        // Quote previews retain their original document geometry.
        pad(paper,18,Store.INVOICE.equals(d.type)?21:20);
        paper.setBackground(outlined(WHITE));
        paper.setMinimumHeight(dp(355));

        LinearLayout masthead=horizontal();
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        masthead.setMinimumHeight(dp(Store.INVOICE.equals(d.type)?29:32));
        ImageView brand=new ImageView(this);
        // Both original paper references use the same small blue document
        // motif; the large square app launcher mark belongs to app branding.
        brand.setImageResource(R.drawable.ic_document_invoice_mark);
        masthead.addView(brand,lp(19,19));
        TextView brandTitle=label("Fakturiva",18,NAVY,true);
        LinearLayout.LayoutParams brandText=new LinearLayout.LayoutParams(0,-2,1);
        brandText.leftMargin=dp(5);
        masthead.addView(brandTitle,brandText);
        LinearLayout docType=vertical();
        docType.setGravity(Gravity.RIGHT);
        TextView kind=label(Store.INVOICE.equals(d.type)?"RAČUN":"PONUDA",
            11,0xff7185a2,false);
        kind.setGravity(Gravity.RIGHT);
        place(docType,kind);
        TextView identifier=label(d.number==null?"NACRT":d.number,11,NAVY,true);
        identifier.setGravity(Gravity.RIGHT);
        place(docType,identifier);
        masthead.addView(docType);
        // Invoices place issuer details closer to the document logo.
        place(paper,masthead);gap(paper,Store.INVOICE.equals(d.type)?1:6);

        String issuer=d.issuerName==null||d.issuerName.trim().isEmpty()?
            "Izdavatelj nije upisan":d.issuerName;
        place(paper,label(issuer,Store.INVOICE.equals(d.type)?10:11,
            0xff647b97,false));
        if(Store.QUOTE.equals(d.type) && d.quoteTitle!=null &&
            !d.quoteTitle.trim().isEmpty()){
            gap(paper,8);
            place(paper,label(d.quoteTitle,14,NAVY,true));
        }
        if(d.issuerAddress!=null&&!d.issuerAddress.isEmpty())
            place(paper,label(d.issuerAddress,Store.INVOICE.equals(d.type)?9:10,
                0xff8798ad,false));
        // Offer paper has a more compact row rhythm in the reference;
        // all variable business text continues to grow naturally.
        gap(paper,Store.QUOTE.equals(d.type)?14:16);
        paperRule(paper);gap(paper,Store.QUOTE.equals(d.type)?14:15);

        LinearLayout contacts=horizontal();
        LinearLayout buyer=vertical();
        buyer.addView(label(Store.QUOTE.equals(d.type)?"ZA KLIJENTA":"KUPAC",
            10,0xff899bb1,true));
        gap(buyer,5);
        buyer.addView(label(d.clientName==null?"":d.clientName,12,NAVY,true));
        if(d.clientAddress!=null&&!d.clientAddress.isEmpty()){
            gap(buyer,5);
            buyer.addView(label(d.clientAddress,10,0xff8798ad,false));
        }
        contacts.addView(buyer,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout dates=vertical();
        boolean offer=Store.QUOTE.equals(d.type);
        dates.addView(label(offer?"VRIJEDI DO":"IZDANO",10,0xff899bb1,true));
        gap(dates,5);
        dates.addView(label(readableDate(offer?d.dueDate:d.issueDate),12,NAVY,true));
        gap(dates,5);
        dates.addView(label(offer?"Izdano: "+readableDate(d.issueDate):
            "Dospijeće: "+readableDate(d.dueDate),10,0xff8798ad,false));
        LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(0,-2,1);
        dateParams.leftMargin=dp(12);
        contacts.addView(dates,dateParams);
        place(paper,contacts);gap(paper,offer?18:19);

        LinearLayout columns=horizontal();
        columns.setBackground(shape(0xffeef1fc,6));
        columns.setPadding(dp(8),dp(10),dp(8),dp(10));
        columns.addView(label("OPIS USLUGE",10,0xff475d7a,true),
            new LinearLayout.LayoutParams(0,-2,1));
        columns.addView(label("IZNOS",10,NAVY,true));
        place(paper,columns);

        for(Store.Item item:d.items){
            LinearLayout line=horizontal();
            line.setPadding(dp(5),dp(offer?10:12),dp(5),dp(offer?8:10));
            TextView description=label(item.name,11,0xff586c87,false);
            description.setMaxLines(2);
            description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            // Keep the reference's clean document row while retaining the
            // exact quantity, unit price and VAT on tap and for accessibility.
            String breakdown=Money.decimalQuantity(item.qtyMilli)+" × "+
                docMoney(d,item.unitCents)+" · Tax "+Money.rateLabel(item.taxRate);
            line.setContentDescription(item.name+", "+breakdown);
            line.setFocusable(true);
            line.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle(item.name).setMessage(breakdown)
                .setPositiveButton("Zatvori",null).show());
            TextView amount=label(docMoney(d,
                Math.addExact(item.net(),item.tax())),11,NAVY,true);
            amount.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams sum=new LinearLayout.LayoutParams(-2,-2);
            sum.leftMargin=dp(10);
            line.addView(amount,sum);
            place(paper,line);
            paperRule(paper);
        }

        gap(paper,offer?12:14);
        paperAmount(paper,"Osnovica",docMoney(d,d.netCents),false);
        gap(paper,9);
        String paperTaxLabel="PDV";
        if(Store.INVOICE.equals(d.type)&&!d.items.isEmpty()){
            int oneRate=d.items.get(0).taxRate;
            boolean onlyOneRate=true;
            for(Store.Item item:d.items)if(item.taxRate!=oneRate){
                onlyOneRate=false;break;
            }
            if(onlyOneRate)paperTaxLabel="Tax ("+Money.rateLabel(oneRate)+")";
        }
        paperAmount(paper,paperTaxLabel,docMoney(d,d.taxCents),false);
        gap(paper,12);
        paperRule(paper);gap(paper,12);
        paperAmount(paper,"UKUPNO",docMoney(d,d.totalCents),true);
        if(Store.INVOICE.equals(d.type) && d.paidCents>0){
            gap(paper,9);
            paperAmount(paper,"Evidentirano plaćeno",docMoney(d,d.paidCents),false);
            gap(paper,7);
            paperAmount(paper,"Preostalo",docMoney(d,d.outstanding()),false);
        }
        // Keep the white paper's footer level stable while aligning issuer,
        // customer and item rows with the invoice reference geometry.
        gap(paper,Store.QUOTE.equals(d.type)?29:28);
        paperRule(paper);gap(paper,12);
        return paper;
    }

    private void paperRule(LinearLayout target){
        View separator=new View(this);
        separator.setBackgroundColor(0xffe7edf5);
        target.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
    }

    private void paperAmount(LinearLayout target,String title,String amount,boolean total){
        LinearLayout row=horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        // The original invoice puts subtotals into a right-aligned column,
        // not at the left edge of the paper. Preserve exact monetary data.
        row.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        TextView caption=label(title,total?12:11,total?NAVY:0xff8699b0,total);
        caption.setMaxLines(2);
        row.addView(caption,lp(92,-2));
        TextView value=label(amount,total?17:11,total?BLUE:NAVY,true);
        value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        // Never ellipsize financial figures: on 280–320dp viewports and
        // scaled fonts let the value wrap onto an additional line.
        value.setSingleLine(false);
        value.setMaxLines(3);
        value.setContentDescription(title+": "+amount);
        row.addView(value,lp(110,-2));
        place(target,row);
    }

    /** Original printable PDF remains available via Android PdfRenderer. */
    private void showActualPdfPreview(Store.Document d){
        try{
            View preview=DocumentPreview.firstPage(this,db,d);
            new AlertDialog.Builder(this)
                .setTitle("Pregled PDF-a")
                .setView(preview)
                .setPositiveButton("Zatvori",null).show();
        }catch(Exception error){error(error);}
    }

    private void payDialog(long id){
        Store.Document d;
        try{d=db.document(id);}catch(Exception e){error(e);return;}
        if(!Store.INVOICE.equals(d.type)||!Store.ISSUED.equals(d.status)||d.outstanding()<=0){
            toast("Za ovaj račun nije moguće unijeti uplatu");return;
        }
        LinearLayout form=vertical();pad(form,17,7);
        form.addView(label("Preostalo za naplatu: "+docMoney(d,d.outstanding()),14,ink(),true));
        EditText amount=field("Iznos uplate",
            String.format(selectedLocale(),"%.2f",d.outstanding()/100.0));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fieldLabel(form,"Amount ("+d.currencyCode+")",amount);
        EditText paidOn=field("Datum uplate",later(0));
        dateInput(paidOn);fieldLabel(form,"Datum uplate",paidOn);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Evidentiraj uplatu").setView(form)
            .setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button->{
                try {
                    long paid=Money.cents(amount.getText().toString());
                    if(paid<=0||paid>d.outstanding())
                        throw new IllegalArgumentException("Uplata mora biti veća od 0 i ne veća od preostalog iznosa");
                    if(!IsoDates.valid(paidOn.getText().toString()))
                        throw new IllegalArgumentException("Datum uplate nije valjan");
                    db.pay(id,paid,paidOn.getText().toString());
                    dialog.dismiss();
                    showDocument(id);
                }catch(Exception e){error(e);amount.requestFocus();}
            }));
        dialog.show();
    }
    /** Screen 12: a genuine searchable client registry with compact grouped rows. */
    private void clients(){
        setup("clients",true);
        // Calibrated client header against the supplied 393dp reference.
        pad(body,18,20);
        List<Store.Client> all=db.clients();

        // A stored client has no independent active/inactive flag. Count clients
        // with actual documents instead of inventing "active" records.
        java.util.HashSet<Long> documented=new java.util.HashSet<>();
        for(String type:new String[]{Store.INVOICE,Store.QUOTE})
            for(Store.Document d:db.documents(type))documented.add(d.clientId);

        LinearLayout header=horizontal();
        LinearLayout heading=vertical();
        heading.addView(label("Klijenti",24,ink(),true));gap(heading,5);
        heading.addView(label("Kontakti i poslovni partneri",12,sub(),false));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView searchButton=icon(R.drawable.ic_search,sub());
        searchButton.setBackground(shape(panel(),13));
        searchButton.setPadding(dp(11),dp(11),dp(11),dp(11));
        searchButton.setContentDescription("Pretraži klijente");
        LinearLayout.LayoutParams searchButtonLp=lp(44,44);
        searchButtonLp.rightMargin=dp(8);
        header.addView(searchButton,searchButtonLp);
        TextView more=label("•••",18,sub(),true);
        more.setGravity(Gravity.CENTER);
        more.setBackground(shape(panel(),13));
        more.setContentDescription("Opcije klijenata");
        header.addView(more,lp(44,44));
        more.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Klijenti")
            .setItems(new String[]{"Dodaj klijenta","Postavke"},
                (dialog,which)->{if(which==0)addClient();else settings();})
            .show());
        place(body,header);gap(body,21);

        LinearLayout searchRow=horizontal();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(outlined(panel()));
        searchRow.setPadding(dp(13),dp(2),dp(12),dp(2));
        searchRow.addView(icon(R.drawable.ic_search,sub()),lp(22,22));
        EditText search=field("Pretraži klijente...","");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(0,dp(44),1);
        searchLp.leftMargin=dp(8);
        searchRow.addView(search,searchLp);
        ImageView filterButton=icon(R.drawable.ic_filter,sub());
        filterButton.setPadding(dp(4),dp(4),dp(4),dp(4));
        filterButton.setContentDescription("Filtriraj klijente");
        searchRow.addView(filterButton,lp(29,29));
        // Move totals closer to search without hiding keyboard controls.
        place(body,searchRow);gap(body,3);

        LinearLayout totals=horizontal();
        totals.setGravity(Gravity.CENTER_VERTICAL);
        totals.addView(label("Ukupno klijenata  ",11,sub(),false));
        totals.addView(label(String.valueOf(all.size()),12,ink(),true));
        View flexible=new View(this);
        totals.addView(flexible,new LinearLayout.LayoutParams(0,dp(1),1));
        totals.addView(label("S dokumentima  ",11,sub(),false));
        totals.addView(label(String.valueOf(documented.size()),12,ink(),true));
        place(body,totals);gap(body,9);

        LinearLayout grouped=vertical();
        grouped.setBackground(outlined(panel()));
        int index=0;
        for(Store.Client client:all){
            if(index++>0){
                View separator=new View(this);
                separator.setBackgroundColor(border());
                grouped.addView(separator,new LinearLayout.LayoutParams(-1,dp(1)));
            }
            LinearLayout row=horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(18),dp(12),dp(18));
            TextView avatar=label(clientInitials(client.name),14,BLUE,true);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(shape(dark?0xff293b63:0xffeaf0ff,12));
            row.addView(avatar,lp(41,41));

            LinearLayout info=vertical();
            LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(0,-2,1);
            infoLp.leftMargin=dp(12);
            row.addView(info,infoLp);
            TextView name=label(client.name,13,ink(),true);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(info,name);gap(info,5);
            String location=clientCity(client.address);
            String secondary=location.isEmpty()?"Klijent":location+" · Klijent";
            TextView subline=label(secondary,11,sub(),false);
            subline.setSingleLine(true);
            subline.setEllipsize(android.text.TextUtils.TruncateAt.END);
            place(info,subline);
            TextView chevron=label("›",25,sub(),false);
            chevron.setGravity(Gravity.CENTER);
            row.addView(chevron,lp(20,39));
            row.setTag(client);
            row.setContentDescription("Otvori klijenta: "+client.name);
            row.setFocusable(true);
            row.setOnClickListener(v->clientDetails(client));
            grouped.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        place(body,grouped);
        TextView empty=label(all.isEmpty()?"Još nemate klijenata.":
            "Nema klijenata za odabranu pretragu.",12,sub(),false);
        empty.setGravity(Gravity.CENTER);
        gap(body,13);place(body,empty);
        TextView tip=label("ⓘ  Dodajte klijente kako biste brže izrađivali dokumente.",
            11,sub(),false);
        gap(body,16);place(body,tip);

        final String[] filter={"Svi"};
        final Runnable apply=()->{
            String term=search.getText().toString().trim()
                .toLowerCase(Locale.forLanguageTag("hr-HR"));
            int visible=0;
            for(int i=0;i<grouped.getChildCount();i++){
                View row=grouped.getChildAt(i);
                if(!(row.getTag() instanceof Store.Client))continue;
                Store.Client candidate=(Store.Client)row.getTag();
                boolean match=clientMatches(candidate,term,filter[0],
                    documented.contains(candidate.id));
                row.setVisibility(match?View.VISIBLE:View.GONE);
                if(match)visible++;
            }
            // Group dividers should separate visible rows, never leave blanks.
            boolean first=true;
            for(int i=0;i<grouped.getChildCount();i++){
                View child=grouped.getChildAt(i);
                if(child.getTag() instanceof Store.Client){
                    if(child.getVisibility()!=View.VISIBLE)continue;
                    first=false;
                }else{
                    boolean nextVisible=false;
                    for(int j=i+1;j<grouped.getChildCount();j++){
                        if(grouped.getChildAt(j).getTag() instanceof Store.Client){
                            nextVisible=grouped.getChildAt(j).getVisibility()==View.VISIBLE;
                            break;
                        }
                    }
                    child.setVisibility(!first && nextVisible?View.VISIBLE:View.GONE);
                }
            }
            grouped.setVisibility(visible==0?View.GONE:View.VISIBLE);
            empty.setVisibility(visible==0?View.VISIBLE:View.GONE);
        };
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                apply.run();
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        searchButton.setOnClickListener(v->{
            search.requestFocus();
            android.view.inputmethod.InputMethodManager keyboard=
                (android.view.inputmethod.InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.showSoftInput(search,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        filterButton.setOnClickListener(v->{
            String[] filters={"Svi","S dokumentima","Bez dokumenata"};
            new AlertDialog.Builder(this)
                .setTitle("Prikaz klijenata")
                .setSingleChoiceItems(filters,java.util.Arrays.asList(filters)
                    .indexOf(filter[0]),(dialog,choice)->{
                        filter[0]=filters[choice];
                        dialog.dismiss();apply.run();
                    }).setNegativeButton("Odustani",null).show();
        });
        apply.run();
        floatingAction("Dodaj klijenta",this::addClient);
    }

    /** Source-of-truth client filter, independent of duplicate client names. */
    static boolean clientMatches(Store.Client c,String search,String view,boolean hasDocs){
        String key=(c.name+" "+c.oib+" "+c.address+" "+c.email+" "+c.phone)
            .toLowerCase(Locale.forLanguageTag("hr-HR"));
        String term=search==null?"":search.trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
        return key.contains(term) &&
            ("Svi".equals(view) || ("S dokumentima".equals(view)&&hasDocs) ||
                ("Bez dokumenata".equals(view)&&!hasDocs));
    }

    /** Two-letter initials, safe for empty strings and short client names. */
    static String clientInitials(String name){
        if(name==null||name.trim().isEmpty())return "K";
        String[] parts=name.trim().split("\\s+");
        StringBuilder result=new StringBuilder();
        for(String part:parts){
            if(part.isEmpty())continue;
            int cp=part.codePointAt(0);
            result.appendCodePoint(Character.toUpperCase(cp));
            if(result.codePointCount(0,result.length())>=2)break;
        }
        return result.toString();
    }

    /** A city can be shown only when present in the stored address. */
    static String clientCity(String address){
        if(address==null||address.trim().isEmpty())return "";
        String[] parts=address.split(",");
        // A street address without a city separator is not a known locality.
        if(parts.length==1)
            return address.matches(".*[0-9].*")?"":address.trim();
        String last=parts[parts.length-1].trim();
        if(parts.length>1&&("Hrvatska".equalsIgnoreCase(last)||
            "Croatia".equalsIgnoreCase(last)))
            return parts[parts.length-2].trim();
        return last;
    }

    /** Screen 13: the real client profile and actual billing history. */
    private void clientDetails(Store.Client c){
        setup("client_detail",true);
        pad(body,18,18);

        LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back=icon(R.drawable.ic_back,sub());
        back.setPadding(dp(8),dp(8),dp(8),dp(8));
        back.setContentDescription("Natrag na klijente");
        back.setOnClickListener(v->clients());
        header.addView(back,lp(42,46));
        LinearLayout headings=vertical();
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-2,1);
        headingParams.leftMargin=dp(5);
        header.addView(headings,headingParams);
        headings.addView(label("Detalji klijenta",23,ink(),true));gap(headings,5);
        headings.addView(label("Profil i povijest poslovanja",11,sub(),false));
        ImageView edit=icon(R.drawable.ic_edit,sub());
        edit.setPadding(dp(11),dp(11),dp(11),dp(11));
        edit.setBackground(shape(panel(),12));
        edit.setContentDescription("Uredi klijenta "+c.name);
        header.addView(edit,lp(44,44));
        edit.setOnClickListener(v->showClientEditor(c));
        place(body,header);gap(body,33);

        LinearLayout identity=vertical();identity.setGravity(Gravity.CENTER);
        TextView avatar=label(clientInitials(c.name),25,BLUE,true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(shape(dark?0xff263759:0xffe8edff,20));
        LinearLayout.LayoutParams logoLp=lp(70,70);
        logoLp.gravity=Gravity.CENTER;
        identity.addView(avatar,logoLp);gap(identity,8);
        TextView name=label(c.name,20,ink(),true);
        name.setGravity(Gravity.CENTER);
        place(identity,name);gap(identity,5);
        String location=clientCity(c.address);
        TextView meta=label("Poslovni partner"+
            (location.isEmpty()?"":" · "+location),11,sub(),false);
        meta.setGravity(Gravity.CENTER);meta.setMaxLines(2);
        place(identity,meta);gap(identity,8);
        // There is no active/inactive field in SQLite; do not fabricate status.
        TextView recorded=label("U evidenciji",11,0xff15996d,true);
        recorded.setGravity(Gravity.CENTER);
        recorded.setBackground(shape(dark?0xff204737:0xffdcf7eb,15));
        pad(recorded,12,6);
        LinearLayout.LayoutParams badgeLp=lp(-2,-2);
        badgeLp.gravity=Gravity.CENTER;
        identity.addView(recorded,badgeLp);
        place(body,identity);gap(body,18);

        long paid=0;
        int yearInvoices=0;
        List<Store.Document> history=new ArrayList<>();
        String currentYear=String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        for(String type:new String[]{Store.INVOICE,Store.QUOTE}){
            for(Store.Document doc:db.documents(type)){
                if(doc.clientId!=c.id)continue;
                history.add(doc);
                if(Store.INVOICE.equals(type)){
                    if(doc.issueDate!=null&&doc.issueDate.startsWith(currentYear) &&
                        !Store.DRAFT.equals(doc.status)&&!Store.VOID.equals(doc.status))
                        yearInvoices++;
                    if(BillingCurrency.canonical(doc.currencyCode).equals(selectedCurrency()))
                        paid=Math.addExact(paid,doc.paidCents);
                }
            }
        }

        LinearLayout statistics=horizontal();
        LinearLayout invoices=clientIndicator("Računi",String.valueOf(yearInvoices),
            "▤","U ovoj godini",BLUE);
        LinearLayout revenue=clientIndicator("Naplaćeno",money(paid),
            "▣","Ukupno",TEAL);
        // Two columns expand vertically for large system font sizes. Fixed
        // 128dp cards previously clipped monetary values on small phones.
        invoices.setMinimumHeight(dp(125));
        revenue.setMinimumHeight(dp(125));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1);
        left.rightMargin=dp(6);
        statistics.addView(invoices,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,-2,1);
        right.leftMargin=dp(6);
        statistics.addView(revenue,right);
        place(body,statistics);gap(body,15);

        LinearLayout data=card();
        place(data,label("Podaci o klijentu",16,ink(),true));gap(data,15);
        clientField(data,"Naziv",c.name);
        clientField(data,"Adresa",c.address);
        clientField(data,"Porezni broj",c.oib);
        clientField(data,"PDV ID",c.vatId);
        clientField(data,"E-pošta",c.email);
        clientField(data,"Telefon",c.phone);
        place(body,data);gap(body,19);

        LinearLayout recent=horizontal();
        recent.addView(label("Posljednji dokumenti",16,ink(),true),
            new LinearLayout.LayoutParams(0,-2,1));
        TextView open=label("›",21,BLUE,true);
        open.setContentDescription("Prikaži dokumente klijenta");
        recent.addView(open);
        open.setOnClickListener(v->{
            String[] numbers=new String[history.size()];
            for(int i=0;i<history.size();i++){
                Store.Document d=history.get(i);
                numbers[i]=(d.number==null?"Nacrt #"+d.id:d.number)+
                    "  ·  "+docMoney(d,d.totalCents);
            }
            if(history.isEmpty()){
                toast("Klijent još nema dokumenata");
                return;
            }
            new AlertDialog.Builder(this).setTitle("Dokumenti klijenta")
                .setItems(numbers,(dialog,index)->showDocument(history.get(index).id))
                .setNegativeButton("Zatvori",null).show();
        });
        place(body,recent);gap(body,11);
        history.sort((a,b)->Long.compare(b.id,a.id));
        int count=0;
        for(Store.Document doc:history){
            if(count++>=3)break;
            documentRow(doc);
        }
        if(history.isEmpty())caption(body,"Još nema dokumenata za ovog klijenta.");
        gap(body,22);
        // A fixed call to action stays visible above bottom navigation.
        stickyPrimary("＋ Izradi račun za klijenta",()->newDocumentForClient(c.id));
    }

    private LinearLayout clientIndicator(String name,String value,String symbol,String qualifier,int color){
        LinearLayout c=card();
        TextView icon=label(symbol,17,color,true);icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(color==TEAL?(dark?0xff1f4743:0xffdcf8f4):
            (dark?0xff293b66:0xffe8edff),11));
        c.addView(icon,lp(36,36));gap(c,7);
        c.addView(label(name,11,sub(),false));gap(c,2);
        c.addView(label(value,21,ink(),true));
        c.addView(label(qualifier,10,sub(),false));
        return c;
    }
    private void clientField(LinearLayout target,String title,String value){
        if(value==null||value.isEmpty())return;
        // Header is the first child. Draw thin dividers only between data rows.
        if(target.getChildCount()>2){
            View rule=new View(this);rule.setBackgroundColor(border());
            target.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
            gap(target,10);
        }
        LinearLayout row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
        TextView key=label(title,11,sub(),false);
        row.addView(key,new LinearLayout.LayoutParams(0,-2,1));
        boolean oib="Porezni broj".equals(title);
        TextView text=label(oib?"••••••••":value,11,ink(),true);
        text.setMaxLines(3);text.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1.7f));
        if(oib){
            text.setContentDescription("Porezni broj je skriven. Dodirnite za prikaz.");
            text.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Porezni broj klijenta").setMessage(value)
                .setPositiveButton("Zatvori",null).show());
        }
        place(target,row);gap(target,10);
    }
    private void newDocumentForClient(long id){
        if(RecoveryVault.hasWorkingDraft(this)){
            new AlertDialog.Builder(this).setTitle("Pronađen je nedovršeni dokument")
                .setMessage("Nastavite automatski spremljeni nacrt ili započnite novi račun za odabranog klijenta.")
                .setPositiveButton("Nastavi nacrt",(d,w)->loadWorkingDraft())
                .setNeutralButton("Novi račun",(d,w)->startEmptyClientInvoice(id))
                .setNegativeButton("Odustani",null).show();
        }else startEmptyClientInvoice(id);
    }
    private void startEmptyClientInvoice(long id){
        startEmptyDocument(Store.INVOICE);
        editClientId=id;
        saveWorkingDraft();
        editDocumentScreen();
    }
    private void addClient(){showClientEditor(null);}
    private void showClientEditor(Store.Client saved){
        boolean editing=saved!=null;
        LinearLayout form=vertical();pad(form,18,6);
        EditText name=field("Naziv tvrtke ili ime i prezime",editing?saved.name:"");
        EditText oib=field("Porezni broj (neobavezno)",editing?saved.oib:"");
        oib.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        EditText vat=field("PDV ID (neobavezno)",editing?saved.vatId:"");
        EditText address=field("Adresa i mjesto",editing?saved.address:"");
        EditText email=field("E-mail",editing?saved.email:"");
        EditText phone=field("Telefon",editing?saved.phone:"");
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        fieldLabel(form,"Naziv klijenta",name);
        fieldLabel(form,"Porezni identifikacijski broj (ako postoji)",oib);
        fieldLabel(form,"PDV identifikacijski broj",vat);
        fieldLabel(form,"Adresa / mjesto / poštanski broj",address);
        fieldLabel(form,"E-mail",email);
        fieldLabel(form,"Telefon",phone);
        ScrollView content=new ScrollView(this);
        content.setFillViewport(false);content.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing?"Uredi klijenta":"Novi klijent").setView(content)
            .setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button->{
                try{
                    String company=name.getText().toString().trim();
                    String id=oib.getText().toString().trim();
                    if(company.isEmpty()){
                        name.setError("Unesite naziv klijenta");name.requestFocus();return;
                    }
                    if(id.length()>40){
                        oib.setError("Porezni broj može imati najviše 40 znakova");oib.requestFocus();return;
                    }
                    long customerId;
                    if(editing){
                        db.updateClient(saved.id,company,id,address.getText().toString(),
                            email.getText().toString(),phone.getText().toString(),vat.getText().toString());
                        customerId=saved.id;
                    }else{
                        customerId=db.saveClientDetailed(company,id,address.getText().toString(),
                            email.getText().toString(),phone.getText().toString(),vat.getText().toString());
                    }
                    dialog.dismiss();
                    if("new_doc".equals(screen)||"edit_items".equals(screen)){
                        editClientId=customerId;
                        saveWorkingDraft();
                        editDocumentScreen();
                    }else if(editing){
                        Store.Client updated=saved;
                        for(Store.Client client:db.clients())
                            if(client.id==customerId){updated=client;break;}
                        clientDetails(updated);
                    }else clients();
                }catch(Exception error){error(error);}
            }));
        dialog.show();
    }
    /** Screen 14: real catalog categories; no fake stock or activity status. */
    private void catalog(){
        setup("catalog",true);
        // Keep category controls at the original vertical rhythm.
        pad(body,18,20);
        List<Store.Item> products=db.products();
        LinearLayout head=horizontal();head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading=vertical();
        heading.addView(label("Artikli i usluge",24,ink(),true));gap(heading,5);
        heading.addView(label("Vaš katalog proizvoda i usluga",12,sub(),false));
        head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView focus=icon(R.drawable.ic_search,sub());
        focus.setContentDescription("Pretraži artikle i usluge");
        focus.setPadding(dp(10),dp(10),dp(10),dp(10));
        focus.setBackground(shape(panel(),13));
        head.addView(focus,lp(44,44));
        TextView more=label("•••",18,sub(),true);
        more.setGravity(Gravity.CENTER);more.setBackground(shape(panel(),13));
        LinearLayout.LayoutParams moreLp=lp(44,44);moreLp.leftMargin=dp(8);
        head.addView(more,moreLp);
        more.setContentDescription("Opcije kataloga");
        more.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Katalog")
            .setItems(new String[]{"Dodaj artikl ili uslugu","Postavke"},
                (dialog,which)->{if(which==0)editProduct(null);else settings();})
            .show());
        place(body,head);gap(body,21);

        LinearLayout searchBar=horizontal();searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(outlined(panel()));pad(searchBar,12,3);
        searchBar.addView(icon(R.drawable.ic_search,sub()),lp(24,24));
        EditText search=field("Pretraži artikle i usluge...","");
        search.setBackgroundColor(Color.TRANSPARENT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(0,dp(45),1);
        searchLp.leftMargin=dp(8);searchBar.addView(search,searchLp);
        ImageView filterIcon=icon(R.drawable.ic_filter,sub());
        filterIcon.setPadding(dp(5),dp(5),dp(5),dp(5));
        filterIcon.setContentDescription("Filtriraj vrstu stavke");
        searchBar.addView(filterIcon,lp(30,30));
        place(body,searchBar);gap(body,10);

        String[] categories={"Sve","Usluge","Proizvodi"};
        final String[] selected={"Sve"};
        LinearLayout tabs=horizontal();
        tabs.setBackground(shape(dark?0xff273852:0xffe9eef5,13));
        pad(tabs,4,4);
        TextView[] buttons=new TextView[categories.length];
        for(int i=0;i<categories.length;i++){
            final String category=categories[i];
            TextView tab=label(category,12,i==0?BLUE:sub(),i==0);
            tab.setGravity(Gravity.CENTER);
            if(i==0)tab.setBackground(shape(panel(),10));
            tabs.addView(tab,new LinearLayout.LayoutParams(0,dp(39),1));
            buttons[i]=tab;
        }
        place(body,tabs);gap(body,15);

        LinearLayout meta=horizontal();meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(label("Ukupno stavki  ",11,sub(),false));
        meta.addView(label(String.valueOf(products.size()),12,ink(),true));
        View space=new View(this);meta.addView(space,new LinearLayout.LayoutParams(0,dp(1),1));
        meta.addView(label("U katalogu  ",11,sub(),false));
        meta.addView(label(String.valueOf(products.size()),12,ink(),true));
        place(body,meta);gap(body,2);

        LinearLayout grouped=vertical();grouped.setBackground(outlined(panel()));
        for(int i=0;i<products.size();i++){
            Store.Item item=products.get(i);
            if(i>0){
                View divider=new View(this);divider.setBackgroundColor(border());
                grouped.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
            }
            LinearLayout row=horizontal();row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12),dp(18),dp(12),dp(18));
            TextView symbol=label("⬡",26,i%3==0?BLUE:(i%3==1?TEAL:0xffeaa044),true);
            symbol.setGravity(Gravity.CENTER);
            symbol.setBackground(shape(dark?0xff273852:
                (i%3==0?0xffeaf0ff:i%3==1?0xffdef8f4:0xfffff1e2),12));
            row.addView(symbol,lp(41,41));
            LinearLayout description=vertical();
            LinearLayout.LayoutParams descriptionLp=new LinearLayout.LayoutParams(0,-2,1);
            descriptionLp.leftMargin=dp(11);descriptionLp.rightMargin=dp(6);
            row.addView(description,descriptionLp);
            TextView name=label(item.name,13,ink(),true);
            name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            description.addView(name);gap(description,5);
            String kind="SERVICE".equals(item.kind)?"Usluga":
                "PRODUCT".equals(item.kind)?"Proizvod":"Nerazvrstano";
            TextView hint=label(kind+" · 1 "+item.unit,11,sub(),false);
            hint.setSingleLine(true);description.addView(hint);
            TextView amount=label(BillingCurrency.format(item.unitCents,
            item.currencyCode,selectedLocale()),12,ink(),true);
            amount.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            row.addView(amount);
            row.setTag(item);
            row.setFocusable(true);row.setContentDescription(item.name+", "+kind+
                ", "+BillingCurrency.format(item.unitCents,item.currencyCode,selectedLocale())+", uredi stavku");
            row.setOnClickListener(v->editProduct(item));
            grouped.addView(row);
        }
        place(body,grouped);
        TextView empty=label(products.isEmpty()?"Katalog je prazan. Dodajte prvu stavku.":
            "Nema stavki za odabrani filtar.",12,sub(),false);
        empty.setGravity(Gravity.CENTER);
        gap(body,12);place(body,empty);
        final Runnable refresh=()->{
            int visible=0;boolean previousVisible=false;
            for(int i=0;i<grouped.getChildCount();i++){
                View row=grouped.getChildAt(i);
                if(row.getTag() instanceof Store.Item){
                    boolean matches=catalogMatches((Store.Item)row.getTag(),
                        search.getText().toString(),selected[0]);
                    row.setVisibility(matches?View.VISIBLE:View.GONE);
                    if(matches)visible++;
                    previousVisible=matches || previousVisible;
                }else{
                    row.setVisibility(View.GONE);
                }
            }
            boolean hasPrevious=false;
            for(int i=0;i<grouped.getChildCount();i++){
                View view=grouped.getChildAt(i);
                if(view.getTag() instanceof Store.Item){
                    if(view.getVisibility()==View.VISIBLE)hasPrevious=true;
                    continue;
                }
                boolean nextVisible=false;
                for(int j=i+1;j<grouped.getChildCount();j++){
                    if(grouped.getChildAt(j).getTag() instanceof Store.Item){
                        nextVisible=grouped.getChildAt(j).getVisibility()==View.VISIBLE;
                        break;
                    }
                }
                view.setVisibility(hasPrevious&&nextVisible?View.VISIBLE:View.GONE);
            }
            grouped.setVisibility(visible>0?View.VISIBLE:View.GONE);
            empty.setVisibility(visible==0?View.VISIBLE:View.GONE);
            for(int i=0;i<buttons.length;i++){
                boolean active=categories[i].equals(selected[0]);
                buttons[i].setTextColor(active?BLUE:sub());
                buttons[i].setTypeface(null,active?android.graphics.Typeface.BOLD:
                    android.graphics.Typeface.NORMAL);
                buttons[i].setBackground(active?shape(panel(),10):null);
            }
        };
        for(int i=0;i<buttons.length;i++){
            final String option=categories[i];
            buttons[i].setOnClickListener(v->{selected[0]=option;refresh.run();});
        }
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                refresh.run();
            }
            @Override public void afterTextChanged(android.text.Editable s){}
        });
        focus.setOnClickListener(v->{
            search.requestFocus();
            android.view.inputmethod.InputMethodManager ime=
                (android.view.inputmethod.InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(ime!=null)ime.showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        filterIcon.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Vrsta stavke")
            .setItems(new String[]{"Sve","Usluge","Proizvodi","Nerazvrstano"},
                (dialog,index)->{
                    selected[0]=new String[]{"Sve","Usluge","Proizvodi","Nerazvrstano"}[index];
                    refresh.run();
                }).show());
        refresh.run();
        floatingAction("Dodaj artikl ili uslugu",()->editProduct(null));
    }

    static boolean catalogMatches(Store.Item item,String search,String category){
        String text=(item.name+" "+item.unit+" "+item.kind)
            .toLowerCase(Locale.forLanguageTag("hr-HR"));
        String term=search==null?"":search.trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
        boolean kind="Sve".equals(category) ||
            ("Usluge".equals(category)&&"SERVICE".equals(item.kind)) ||
            ("Proizvodi".equals(category)&&"PRODUCT".equals(item.kind)) ||
            ("Nerazvrstano".equals(category)&&"UNSPECIFIED".equals(item.kind));
        return kind&&text.contains(term);
    }

    private void addProduct(){editProduct(null);}
    private void editProduct(Store.Item current){
        boolean editing=current!=null;
        LinearLayout form=vertical();pad(form,18,5);
        EditText name=field("Naziv artikla ili usluge",editing?current.name:"");
        EditText price=field("Unit price ("+(editing?
            BillingCurrency.canonical(current.currencyCode):selectedCurrency())+")",editing?
            String.format(selectedLocale(),"%.2f",current.unitCents/100.0):"0,00");
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] kinds={"Odaberite vrstu","Usluga","Proizvod","Nerazvrstano"};
        Spinner kind=new Spinner(this);
        kind.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,kinds));
        if(editing)kind.setSelection("SERVICE".equals(current.kind)?1:
            "PRODUCT".equals(current.kind)?2:3);
        EditText vat=field("Tax rate (%)",editing?
            Money.rateLabel(current.taxRate).replace("%",""):"0");
        vat.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText unit=field("Mjerna jedinica (kom, sat, mj...)",
            editing?current.unit:"kom");
        fieldLabel(form,"Naziv",name);
        gap(form,9);place(form,label("Vrsta",12,ink(),true));place(form,kind);
        fieldLabel(form,"Unit price before tax",price);
        fieldLabel(form,"Mjerna jedinica",unit);
        fieldLabel(form,"Tax rate (%)",vat);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing?"Uredi artikl ili uslugu":"Novi artikl ili usluga")
            .setView(form).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null).create();
        dialog.setOnShowListener(ignore->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button->{
                try{
                    if(kind.getSelectedItemPosition()==0){
                        toast("Odaberite vrstu stavke");return;
                    }
                    String value=new String[]{"","SERVICE","PRODUCT","UNSPECIFIED"}[
                        kind.getSelectedItemPosition()];
                    int rate=Money.parseRate(vat.getText().toString());
                    long cents=Money.cents(price.getText().toString());
                    String normalizedUnit=Store.normalizedUnit(unit.getText().toString().trim());
                    if(editing)
                        db.updateProduct(current.id,name.getText().toString(),cents,rate,
                            value,normalizedUnit);
                    else db.saveProduct(name.getText().toString(),cents,rate,value,normalizedUnit);
                    dialog.dismiss();catalog();
                }catch(Exception e){error(e);}
            }));
        dialog.show();
    }

    /** Screen 15: reports are scoped to the selected accounting month. */
    private void reports(){
        setup("reports",true);
        // The original report uses a shorter vertical rhythm before the chart.
        pad(body,18,16);
        String yearMonth=new SimpleDateFormat("yyyy-MM",Locale.ROOT)
            .format(reportMonth.getTime());
        String month=new SimpleDateFormat("LLLL yyyy.",selectedLocale())
            .format(reportMonth.getTime());
        if(!month.isEmpty())month=Character.toUpperCase(month.charAt(0))+month.substring(1);
        Calendar previous=(Calendar)reportMonth.clone();
        previous.add(Calendar.MONTH,-1);
        String previousKey=new SimpleDateFormat("yyyy-MM",Locale.ROOT)
            .format(previous.getTime());
        Calendar now=Calendar.getInstance();
        boolean currentMonth=reportMonth.get(Calendar.YEAR)==now.get(Calendar.YEAR) &&
            reportMonth.get(Calendar.MONTH)==now.get(Calendar.MONTH);

        List<Store.Document> invoices=db.documents(Store.INVOICE);
        List<Store.Payment> payments=db.payments();
        long[] totals=reportMetrics(invoices,payments,yearMonth,selectedCurrency());

        LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=vertical();
        titles.addView(label("Izvještaji",24,ink(),true));gap(titles,6);
        titles.addView(label("Statistika i analiza poslovanja",12,sub(),false));
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView download=label("↓",24,ink(),true);
        download.setGravity(Gravity.CENTER);
        download.setBackground(shape(panel(),12));
        download.setContentDescription("Izvezi izvještaj u CSV");
        header.addView(download,lp(44,44));
        download.setOnClickListener(v->{
            pendingReportCsvMonth=yearMonth;
            createFile(106,"text/csv","Fakturiva-izvjestaj-"+yearMonth+".csv");
        });
        ImageView options=icon(R.drawable.ic_filter,sub());
        options.setPadding(dp(11),dp(11),dp(11),dp(11));
        options.setBackground(shape(panel(),12));
        options.setContentDescription("Odabir razdoblja");
        LinearLayout.LayoutParams optionsLp=lp(44,44);
        optionsLp.leftMargin=dp(8);
        header.addView(options,optionsLp);
        options.setOnClickListener(v->reportPeriodPicker());
        place(body,header);gap(body,15);

        LinearLayout period=horizontal();period.setGravity(Gravity.CENTER_VERTICAL);
        // On a 393dp reference the month control is compact, NOT the whole
        // remaining row. Constrain it while retaining an ellipsis on mini phones.
        int windowDp=Math.round(getResources().getDisplayMetrics().widthPixels/
            getResources().getDisplayMetrics().density);
        int pickerWidth=Math.max(96,Math.min(155,
            windowDp-36-8-(currentMonth?97:112)-12));
        LinearLayout picker=horizontal();
        picker.setBackground(outlined(panel()));
        picker.setPadding(dp(10),dp(6),dp(10),dp(6));
        picker.setMinimumHeight(dp(40));
        ImageView monthIcon=icon(R.drawable.ic_calendar,sub());
        picker.addView(monthIcon,lp(18,18));
        TextView monthText=label(month,12,ink(),true);
        monthText.setSingleLine(true);
        monthText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams monthNameLp=new LinearLayout.LayoutParams(0,-2,1);
        monthNameLp.leftMargin=dp(7);
        picker.addView(monthText,monthNameLp);
        TextView nextMonth=label("›",18,sub(),true);
        nextMonth.setGravity(Gravity.CENTER);
        picker.addView(nextMonth,lp(11,20));
        LinearLayout.LayoutParams periodLp=lp(pickerWidth,-2);
        periodLp.rightMargin=dp(8);
        period.addView(picker,periodLp);
        picker.setContentDescription("Odaberi mjesec izvještaja: "+month);
        picker.setFocusable(true);
        picker.setOnClickListener(v->reportPeriodPicker());
        period.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        TextView current=label(currentMonth?"Ovaj mjesec":"Na ovaj mjesec",11,BLUE,true);
        current.setGravity(Gravity.CENTER);
        current.setBackground(shape(dark?0xff27385f:0xffe9edff,16));
        pad(current,8,12);
        period.addView(current,lp(currentMonth?97:112,37));
        current.setOnClickListener(v->{
            reportMonth.setTimeInMillis(System.currentTimeMillis());
            reports();
        });
        place(body,period);gap(body,12);

        LinearLayout revenue=card();pad(revenue,18,16);
        revenue.addView(label("Ukupno fakturirano",12,sub(),false));gap(revenue,9);
        revenue.addView(label(money(totals[0]),30,ink(),true));gap(revenue,9);
        long previousCents=db.invoicedCentsMonth(previousKey,selectedCurrency());
        if(previousCents>0){
            double pct=(totals[0]-previousCents)*100d/previousCents;
            String trend=String.format(selectedLocale(),
                "%s%.1f %%",pct>0?"+":"",pct);
            revenue.addView(label(trend+"  u odnosu na prethodni mjesec",11,
                pct>=0?0xff13a884:RED,true));
        }else{
            revenue.addView(label("Bez usporedive osnovice za prethodni mjesec",
                11,sub(),false));
        }
        place(body,revenue);gap(body,11);

        LinearLayout metrics=horizontal();
        reportMetric(metrics,"Izdani računi",String.valueOf(totals[1]),"▤",BLUE,0);
        reportMetric(metrics,"Plaćeni računi",String.valueOf(totals[2]),"✓",TEAL,1);
        place(body,metrics);gap(body,12);

        LinearLayout chart=card();pad(chart,17,17);
        LinearLayout chartHeader=horizontal();
        TextView chartName=label("Kretanje fakturiranja",16,ink(),true);
        chartName.setMaxLines(2);
        chartHeader.addView(chartName,new LinearLayout.LayoutParams(0,-2,1));
        TextView interval=label("Posljednjih 6 mj.",10,sub(),false);
        interval.setMaxLines(2);
        interval.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        chartHeader.addView(interval,lp(92,-2));
        place(chart,chartHeader);gap(chart,19);
        chart.addView(new RevenueBars(this,(Calendar)reportMonth.clone()),lp(-1,185));
        place(body,chart);gap(body,14);

        LinearLayout payout=card();
        place(payout,label("Pregled naplate",16,ink(),true));gap(payout,19);
        clientField(payout,"Naplaćeno u mjesecu",money(totals[3]));
        clientField(payout,"Nenaplaćeno iz izdanih računa",money(totals[4]));
        place(body,payout);gap(body,13);
        secondary(body,"Pregled svih uplata",this::payments);
        gap(body,20);
        // Totals reflect the currently selected reporting month.
    }
    private void reportPeriodPicker(){
        DatePickerDialog dialog=new DatePickerDialog(this,(view,year,month,day)->{
            reportMonth.set(year,month,1);
            reports();
        },reportMonth.get(Calendar.YEAR),reportMonth.get(Calendar.MONTH),1);
        dialog.show();
    }
    /** Invoiced, issued, settled, received this month, outstanding on issued this month. */
    static long[] reportMetrics(List<Store.Document> invoices,
                                List<Store.Payment> payments,String yearMonth){
        return reportMetrics(invoices,payments,yearMonth,"EUR");
    }
    static long[] reportMetrics(List<Store.Document> invoices,
                                List<Store.Payment> payments,String yearMonth,String currency){
        long issued=0,settled=0,invoiced=0,open=0,received=0;
        for(Store.Document d:invoices){
            if(!Store.INVOICE.equals(d.type)||d.issueDate==null||
                !d.issueDate.startsWith(yearMonth)||
                Store.DRAFT.equals(d.status)||Store.VOID.equals(d.status)||
                !BillingCurrency.canonical(d.currencyCode).equals(BillingCurrency.canonical(currency)))continue;
            issued++;
            invoiced=Math.addExact(invoiced,d.totalCents);
            if(d.outstanding()==0)settled++;
            open=Math.addExact(open,d.outstanding());
        }
        for(Store.Payment p:payments){
            if(p.paidOn!=null&&p.paidOn.startsWith(yearMonth)&&
                BillingCurrency.canonical(p.currencyCode).equals(BillingCurrency.canonical(currency)))
                received=Math.addExact(received,p.amountCents);
        }
        return new long[]{invoiced,issued,settled,received,open};
    }

    private void reportMetric(LinearLayout row,String caption,String value,String glyph,int accent,int index){
        LinearLayout c=card();
        TextView symbol=label(glyph,19,accent,true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(accent==TEAL?(dark?0xff20534b:0xffdcf8f4):
            (dark?0xff263a65:0xffe8edff),12));
        c.addView(symbol,lp(36,36));gap(c,6);
        c.addView(label(caption,11,sub(),false));gap(c,2);
        c.addView(label(value,25,ink(),true));
        // WRAP_CONTENT prevents clipping at Android font scale 1.3–1.5.
        c.setMinimumHeight(dp(112));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        if(index==0)lp.rightMargin=dp(6);else lp.leftMargin=dp(6);
        row.addView(c,lp);
    }
    private void payments(){
        setup("payments",true);
        pad(body,18,17);
        LinearLayout top=horizontal();top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading=vertical();
        heading.addView(label("Naplata",24,ink(),true));gap(heading,6);
        heading.addView(label("Evidencija uplata i potraživanja",12,sub(),false));
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView filterAction=icon(R.drawable.ic_filter,sub());
        filterAction.setPadding(dp(10),dp(10),dp(10),dp(10));
        filterAction.setContentDescription("Filtriraj uplate");
        filterAction.setBackground(shape(panel(),12));
        top.addView(filterAction,lp(44,44));
        TextView exportAction=label("↓",24,ink(),true);
        exportAction.setGravity(Gravity.CENTER);
        exportAction.setContentDescription("Izvezi uplate kao CSV");
        exportAction.setBackground(shape(panel(),12));
        LinearLayout.LayoutParams exportLp=lp(44,44);exportLp.leftMargin=dp(8);
        top.addView(exportAction,exportLp);
        exportAction.setOnClickListener(v->{
            pendingPaymentsCsvMonth=paymentFilter==1?
                new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new java.util.Date()):null;
            createFile(104,"text/csv","Fakturiva-uplate-"+
                (pendingPaymentsCsvMonth==null?"sve":pendingPaymentsCsvMonth)+".csv");
        });
        filterAction.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Prikaz naplate")
            .setSingleChoiceItems(new String[]{"Sve uplate","Ovaj mjesec","Za naplatu"},
                paymentFilter,(dialog,which)->{
                    paymentFilter=which;dialog.dismiss();payments();
                }).setNegativeButton("Zatvori",null).show());
        place(body,top);gap(body,21);
        List<Store.Payment> history=db.payments();
        String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new java.util.Date());
        int monthlyCount=0;long monthlyAmount=0;
        for(Store.Payment p:history)if(p.paidOn.startsWith(month)&&
            BillingCurrency.canonical(p.currencyCode).equals(selectedCurrency())){
            monthlyCount++;monthlyAmount=Math.addExact(monthlyAmount,p.amountCents);
        }
        LinearLayout hero=vertical();pad(hero,19,22);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff173b69,0xff102844});
        gradient.setCornerRadius(dp(19));hero.setBackground(gradient);
        hero.addView(label("Ukupno naplaćeno",12,0xffc7d9ec,false));gap(hero,10);
        hero.addView(label(money(db.receivedCents(selectedCurrency())),29,WHITE,true));gap(hero,13);
        hero.addView(label("✓  "+monthlyCount+" evidentiranih uplata ovaj mjesec · "+
            money(monthlyAmount),11,0xff73dfd0,false));
        place(body,hero);gap(body,15);

        LinearLayout filters=horizontal();
        filters.setTag("payment-filters");
        filters.setBackground(shape(dark?0xff263852:0xffe9eef6,15));
        pad(filters,3,3);
        String[] names={"Sve uplate","Ovaj mjesec","Za naplatu"};
        for(int n=0;n<names.length;n++){
            final int selected=n;
            TextView tab=label(names[n],11,paymentFilter==n?BLUE:sub(),paymentFilter==n);
            tab.setGravity(Gravity.CENTER);
            if(paymentFilter==n)tab.setBackground(shape(panel(),12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);
            filters.addView(tab,p);
            tab.setOnClickListener(v->{paymentFilter=selected;payments();});
        }
        place(body,filters);gap(body,8);
        LinearLayout listHeading=horizontal();
        listHeading.addView(label(paymentFilter==2?"Neplaćeni računi":"Nedavne uplate",
            17,ink(),true),new LinearLayout.LayoutParams(0,-2,1));
        TextView viewAll=label("›",21,BLUE,true);
        viewAll.setContentDescription("Prikaži sve uplate");
        listHeading.addView(viewAll);
        viewAll.setOnClickListener(v->{paymentFilter=0;payments();});
        // Source has a compact 10dp gap from section heading to its first
        // list card; the heading's own accessible row supplies that spacing.
        place(body,listHeading);

        LinearLayout card=card();pad(card,0,0);
        if(paymentFilter==2){
            int n=0;
            for(Store.Document d:db.documents(Store.INVOICE)){
                if(!Store.ISSUED.equals(d.status)||d.outstanding()<=0)continue;
                if(n++>0)paymentDivider(card);
                LinearLayout row=horizontal();pad(row,14,15);
                LinearLayout detail=vertical();
                detail.addView(label(d.clientName,13,ink(),true));
                detail.addView(label(d.number+" · "+d.dueDate,11,sub(),false));
                row.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                LinearLayout right=vertical();right.setGravity(Gravity.RIGHT);
                right.addView(label(docMoney(d,d.outstanding()),13,BLUE,true));
                right.addView(label("Za naplatu",10,sub(),false));
                row.addView(right);place(card,row);
                row.setOnClickListener(v->showDocument(d.id));
            }
            if(n==0){pad(card,16,16);place(card,label("Nema nenaplaćenih potvrđenih računa.",12,sub(),false));}
        }else{
            int n=0;
            for(Store.Payment p:history){
                if(paymentFilter==1&&!p.paidOn.startsWith(month))continue;
                if(n++>0)paymentDivider(card);
                LinearLayout row=horizontal();pad(row,12,17);
                TextView check=label("✓",19,TEAL,true);check.setGravity(Gravity.CENTER);
                check.setBackground(shape(dark?0xff22463e:0xffdbf7ea,12));
                row.addView(check,lp(42,42));
                LinearLayout description=vertical();pad(description,10,0);
                TextView clientName=label(p.clientName,13,ink(),true);
                clientName.setMaxLines(3);
                description.addView(clientName);
                TextView documentInfo=label(p.invoiceNumber+" · "+readableDate(p.paidOn),
                    11,sub(),false);
                documentInfo.setMaxLines(2);
                description.addView(documentInfo);
                TextView amount=label("+"+BillingCurrency.format(p.amountCents,p.currencyCode,Locale.ENGLISH),12,TEAL,true);
                amount.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
                amount.setMaxLines(3);
                amount.setContentDescription("Evidentirana uplata: "+BillingCurrency.format(p.amountCents,p.currencyCode,Locale.ENGLISH));
                // Avoid competing horizontal widths on 280/320dp screens or
                // when the user selects large accessibility font scaling.
                float widthDp=getResources().getDisplayMetrics().widthPixels/
                    getResources().getDisplayMetrics().density;
                boolean compactPayment=widthDp<340 ||
                    getResources().getConfiguration().fontScale>=1.35f;
                if(compactPayment){
                    LinearLayout.LayoutParams sumLp=lp(-1,-2);
                    sumLp.topMargin=dp(5);
                    description.addView(amount,sumLp);
                    row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
                }else{
                    row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
                    row.addView(amount);
                }
                place(card,row);
                row.setOnClickListener(v->showDocument(p.documentId));
            }
            if(n==0){pad(card,16,16);place(card,label(
                paymentFilter==1?"Nema evidentiranih uplata ovaj mjesec.":"Nema evidentiranih uplata.",
                12,sub(),false));}
        }
        place(body,card);gap(body,15);
        gap(body,20);
    }
    private void paymentDivider(LinearLayout card){
        View divider=new View(this);
        divider.setBackgroundColor(border());
        card.addView(divider,lp(-1,1));
    }
    private String getPackageVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "Android";}
    }
    private void settings(){
        setup("settings",true);
        pad(body,16,21);
        LinearLayout heading=horizontal();heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles=vertical();
        titles.addView(label("Postavke",24,ink(),true));
        gap(titles,5);
        titles.addView(label("Upravljajte svojom aplikacijom",11,sub(),false));
        heading.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView help=label("?",17,sub(),true);
        help.setGravity(Gravity.CENTER);
        help.setBackground(shape(panel(),12));
        help.setContentDescription("Informacije o aplikaciji");
        help.setOnClickListener(v->aboutSupport());
        heading.addView(help,lp(43,43));
        place(body,heading);gap(body,18);
        LinearLayout brand=horizontal();pad(brand,13,12);
        brand.setBackground(shape(NAVY,19));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.brand_mark);
        brand.addView(mark,lp(57,57));
        LinearLayout brandText=vertical();pad(brandText,11,0);
        brandText.addView(label("Fakturiva",20,WHITE,true));
        brandText.addView(label("Postavke aplikacije",12,0xffb6cbe2,false));
        brand.addView(brandText,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(label("›",23,0xffb6cbe2,true));
        brand.setContentDescription("Fakturiva, informacije o aplikaciji");
        brand.setOnClickListener(v->aboutSupport());
        place(body,brand);gap(body,14);

        LinearLayout options=card();pad(options,0,2);
        place(body,options);
        settingRow(options,"◉","Language",selectedLanguage().equals("en")?"English":"Hrvatski",
            this::languagePicker);
        settingRow(options,"¤","Default currency",selectedCurrency(),
            this::currencyPicker);
        settingRow(options,"▤","Printers and paper",
            printOptions()[Math.max(0,Math.min(printOptions().length-1,
                prefs.getInt("default_paper",0)))],
            this::printerPreferences);
        settingRow(options,"⌁","LAN receipt printer",
            prefs.getString("printer_host","").isEmpty()?
                "Configure a local network printer":
                prefs.getString("printer_host","")+":"+
                prefs.getString("printer_port","9100"),
            this::networkPrinterSettings);
        settingRow(options,"⌁","USB ESC/POS printers",
            "USB Host · 58 / 80 mm · check connected devices",
            this::usbPrinterInfo);
        settingRow(options,"▤","Tvrtka i poslovni podaci","Naziv, adresa i kontakt podaci",this::company);
        settingRow(options,"▣","Numeracija dokumenata",
            "Pregled dodijeljenih brojeva računa i ponuda",this::numberingInfo);
        settingRow(options,"▥","Porezi i stope",
            "Porezni status i napomene u profilu tvrtke",this::company);
        settingRow(options,"▣","Načini plaćanja",
            "IBAN, banka i uvjeti plaćanja",this::company);
        settingRow(options,"✎","Podaci na dokumentima",
            "Profil izdavatelja za nove PDF dokumente",this::company);
        settingRow(options,"?","Pomoć i upute",
            "Računi, ponude i načini rada",this::guide);
        settingRow(options,"☁","Vlastiti hosting (neobavezno)",
            "Šifrirana ručna kopija · HTTPS · bez SQL baze",
            this::selfHostedSettings);
        settingRow(options,"♧","Privatnost i brisanje podataka",
            "Lokalni i udaljeni računi · prava korisnika",
            this::privacyInfoScreen);
        settingRow(options,"♙","Korisnički računi",
            LocalAccounts.activeName()==null?"Lokalni računi":LocalAccounts.activeName(),
            this::localAccountSettings);
        settingRow(options,"◈","Sigurnost",
            prefs.getBoolean("device_lock",false)?"Zaštita uređajem aktivna":"PIN i privatnost",
            ()->securityScreen(false,true));
        settingRow(options,"♢","Izvoz i sigurnosne kopije",
            "Izvoz i obnova podataka",this::backupScreen);
        settingRow(options,"☼","Izgled aplikacije",
            dark?"Tamna tema aktivna":"Svijetla tema aktivna",()->{
                dark=!dark;prefs.edit().putBoolean("dark",dark).apply();settings();
            });
        settingRow(options,"ⓘ","About and support",
            "Powered by Brendigo · "+BrandAttribution.SUPPORT_EMAIL,
            this::aboutSupport);
        gap(body,14);
        place(body,label("POSLOVNI ALATI",11,sub(),true));gap(body,8);
        LinearLayout tools=card();pad(tools,0,2);
        settingRow(tools,"▣","Artikli i usluge","Katalog proizvoda i cijene",this::catalog);
        settingRow(tools,"▥","Izvještaji","Analitika računa i naplate",this::reports);
        place(body,tools);
        gap(body,17);
        caption(body,"Fakturiva · Brendigo");
        gap(body,19);
    }
    private void languagePicker(){
        String[] names=UiStrings.readyNames(),codes=UiStrings.readyCodes();
        int selected=selectedLanguage().equals("hr")?1:0;
        new AlertDialog.Builder(this).setTitle(ui("Language"))
            .setSingleChoiceItems(names,selected,(dialog,which)->{
                prefs.edit().putString("ui_language",codes[which]).apply();
                dialog.dismiss();settings();
            }).setNegativeButton(ui("Cancel"),null).show();
    }
    private void currencyPicker(){
        String[] options=BillingCurrency.options();
        String[] codes=BillingCurrency.codes();
        int selected=0;
        for(int i=0;i<codes.length;i++)if(codes[i].equals(selectedCurrency()))selected=i;
        new AlertDialog.Builder(this).setTitle(ui("Default currency"))
            .setMessage(ui("Applies to new invoices and quotes. Issued documents retain their original currency."))
            .setSingleChoiceItems(options,selected,(dialog,which)->{
                String code=codes[which];
                if(!BillingCurrency.isSupported(code))return;
                db.setting("currency",code);
                dialog.dismiss();settings();
            }).setNegativeButton(ui("Cancel"),null).show();
    }
    private String[] printOptions(){
        String[] system=SystemInvoicePrinter.formats();
        // Keep the original indices 0–5 stable for saved user preferences.
        String[] options=new String[]{ui(system[0]),ui(system[1]),ui(system[2]),ui(system[3]),
            ui("58 mm · Bluetooth ESC/POS"),ui("80 mm · Bluetooth ESC/POS"),
            ui(system[4]),ui(system[5]),
            ui("80 mm · Bluetooth ESC/POS (512 dots)"),
            ui("58 mm · LAN ESC/POS"),
            ui("80 mm · LAN ESC/POS (512 dots)"),
            ui("80 mm · LAN ESC/POS (576 dots)"),
            ui("58 mm · USB ESC/POS"),
            ui("80 mm · USB ESC/POS (512 dots)"),
            ui("80 mm · USB ESC/POS (576 dots)")};
        if(SunmiReceiptPrinter.isSunmiDevice()){
            String[] extended=java.util.Arrays.copyOf(options,options.length+1);
            extended[options.length]=ui("SUNMI ugrađeni pisač (SDK)");
            return extended;
        }
        return options;
    }
    private void printerPreferences(){
        String[] choices=printOptions();
        int preferred=Math.max(0,Math.min(choices.length-1,
            prefs.getInt("default_paper",0)));
        new AlertDialog.Builder(this)
            .setTitle(ui("Printers and paper"))
            .setSingleChoiceItems(choices,preferred,(dialog,which)->{
                prefs.edit().putInt("default_paper",which).apply();
                dialog.dismiss();settings();
            })
            .setNeutralButton(ui("Pair Bluetooth printer"),(dialog,which)->
                startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS")))
            .setNegativeButton(ui("Cancel"),null).show();
    }
    private void showPrinterDialog(long documentId){
        Store.Document document;
        try{document=db.document(documentId);}catch(Exception e){error(e);return;}
        String[] choices=printOptions();
        int preferred=Math.max(0,Math.min(choices.length-1,
            prefs.getInt("default_paper",0)));
        int[] choice={preferred};
        new AlertDialog.Builder(this).setTitle(ui("Print invoice / quote"))
            .setSingleChoiceItems(choices,preferred,(dialog,which)->choice[0]=which)
            .setNegativeButton(ui("Cancel"),null)
            .setPositiveButton(ui("Print"),(dialog,which)->{
                prefs.edit().putInt("default_paper",choice[0]).apply();
                int selected=choice[0];
                if(selected<4||selected==6||selected==7){
                    try{SystemInvoicePrinter.print(this,document,
                        selected<4?selected:selected-2);}
                    catch(Exception e){error(e);}
                }else if(selected==4||selected==5||selected==8){
                    int dots=selected==4?ThermalReceipt.PAPER_58_DOTS:
                        selected==8?ThermalReceipt.PAPER_80_COMPACT_DOTS:
                        ThermalReceipt.PAPER_80_DOTS;
                    requestBluetoothPrint(documentId,dots);
                }else if(selected>=9&&selected<=11){
                    int dots=selected==9?ThermalReceipt.PAPER_58_DOTS:
                        selected==10?ThermalReceipt.PAPER_80_COMPACT_DOTS:
                        ThermalReceipt.PAPER_80_DOTS;
                    editNetworkPrinter(documentId,dots);
                }else if(selected>=12&&selected<=14){
                    int dots=selected==12?ThermalReceipt.PAPER_58_DOTS:
                        selected==13?ThermalReceipt.PAPER_80_COMPACT_DOTS:
                        ThermalReceipt.PAPER_80_DOTS;
                    showUsbPrinters(documentId,dots);
                }else if(selected==15&&SunmiReceiptPrinter.isSunmiDevice()){
                    SunmiReceiptPrinter.send(this,document,
                        error->toast(error==null?
                            ui("Podaci su poslani. Provjerite ispis na papiru."):
                            ui(error)));
                }
            }).show();
    }
    /** Enumerates USB Printer Class devices; no wildcard endpoint probing. */
    private void usbPrinterInfo(){
        try{
            List<android.hardware.usb.UsbDevice> devices=UsbReceiptPrinter.discover(this);
            StringBuilder message=new StringBuilder();
            if(devices.isEmpty()){
                message.append("No USB Printer Class device is connected. ")
                    .append("Connect a compatible receipt printer with a USB OTG cable. ")
                    .append("Other printers may need their manufacturer's Android print service.");
            }else{
                for(android.hardware.usb.UsbDevice device:devices){
                    message.append("USB ").append(device.getVendorId()).append(":")
                        .append(device.getProductId()).append("\n");
                }
                message.append("\nOpen an invoice or quote and select USB ESC/POS to print.");
            }
            new AlertDialog.Builder(this).setTitle("USB receipt printers")
                .setMessage(message.toString()).setPositiveButton("OK",null).show();
        }catch(Exception e){error(e);}
    }
    private void showUsbPrinters(long documentId,int dots){
        try{
            List<android.hardware.usb.UsbDevice> devices=UsbReceiptPrinter.discover(this);
            if(devices.isEmpty()){
                new AlertDialog.Builder(this).setTitle("No compatible USB printer")
                    .setMessage("Connect a USB Printer Class ESC/POS device by USB OTG. " +
                        "For proprietary printer protocols, use its Android print service. " +
                        "Card payment terminals are not supported as printers.")
                    .setPositiveButton("OK",null).show();
                return;
            }
            String[] names=new String[devices.size()];
            for(int i=0;i<names.length;i++){
                android.hardware.usb.UsbDevice device=devices.get(i);
                names[i]="USB VID "+device.getVendorId()+" · PID "+
                    device.getProductId()+" · "+device.getDeviceName();
            }
            Store.Document document=db.document(documentId);
            new AlertDialog.Builder(this).setTitle("Select USB ESC/POS printer")
                .setItems(names,(dialog,index)->{
                    toast("Requesting USB access or sending print data…");
                    UsbReceiptPrinter.send(this,devices.get(index),document,dots,
                        this::toast);
                }).setNegativeButton("Cancel",null).show();
        }catch(Exception e){error(e);}
    }
    private void networkPrinterSettings(){
        editNetworkPrinter(-1,ThermalReceipt.PAPER_58_DOTS);
    }
    private void editNetworkPrinter(long documentId,int dots){
        LinearLayout form=vertical();pad(form,18,9);
        EditText host=field("Private printer IPv4 (192.168.1.50)",
            prefs.getString("printer_host",""));
        host.setSingleLine(true);
        host.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_URI);
        fieldLabel(form,"Printer IPv4",host);
        EditText port=field("Port 9100",prefs.getString("printer_port","9100"));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        fieldLabel(form,"TCP port",port);
        final boolean printing=documentId>0;
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(ui(printing?"Print on LAN ESC/POS":"LAN receipt printer"))
            .setMessage(ui("Only trusted private-network ESC/POS printers with a raw TCP port are supported."))
            .setView(form).setNegativeButton(ui("Cancel"),null)
            .setPositiveButton(ui(printing?"Print":"Save"),null).create();
        dialog.setOnShowListener(ignored->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String address=host.getText().toString().trim();
                String value=port.getText().toString().trim();
                try{
                    NetworkReceiptPrinter.privateIpv4(address);
                    int tcp=NetworkReceiptPrinter.validPort(Integer.parseInt(value));
                    prefs.edit().putString("printer_host",address)
                        .putString("printer_port",Integer.toString(tcp)).apply();
                    dialog.dismiss();
                    if(printing){
                        Store.Document document=db.document(documentId);
                        toast("Sending receipt to LAN printer…");
                        NetworkReceiptPrinter.send(this,address,tcp,document,dots,
                            failure->toast(failure==null?
                                "Print data sent. Check the printer for completion.":
                                failure));
                    }else settings();
                }catch(NumberFormatException e){
                    port.setError(ui("Enter a valid TCP port (1024–65535)."));
                    port.requestFocus();
                }catch(IllegalArgumentException e){
                    host.setError(e.getMessage());
                    host.requestFocus();
                }catch(Exception e){error(e);}
            }));
        dialog.show();
    }
    private void aboutSupport(){
        setup("about",true);
        pad(body,17,18);
        toolbar("About and support","Fakturiva · Brendigo",true);
        LinearLayout info=card();
        place(info,label("Fakturiva",22,ink(),true));
        gap(info,6);
        place(info,label("Version "+getPackageVersion(),12,sub(),false));
        gap(info,14);
        place(info,label(BrandAttribution.FOOTER,13,ink(),true));
        gap(info,8);
        caption(info,"Every generated PDF invoice and thermal receipt has a small Powered by Brendigo attribution at the bottom.");
        gap(info,10);
        caption(info,"To request removal of this attribution, contact our support team at info@plati.com.hr.");
        place(body,info);gap(body,14);
        button(body,"Contact support · "+BrandAttribution.SUPPORT_EMAIL,()->{
            Intent email=new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:"+BrandAttribution.SUPPORT_EMAIL));
            email.putExtra(Intent.EXTRA_SUBJECT,
                "Fakturiva — Powered by Brendigo attribution");
            try{startActivity(email);}
            catch(android.content.ActivityNotFoundException missing){
                toast("No email app is installed. Please write to info@plati.com.hr.");
            }
        });
        gap(body,12);
        caption(body,"This app creates ordinary invoices and quotes. Printing does not process card payments or provide fiscalization.");
        gap(body,12);
        secondary(body,"Privatnost i brisanje računa",this::privacyInfoScreen);
    }
    private void requestBluetoothPrint(long documentId,int dots){
        if(Build.VERSION.SDK_INT>=31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=
                PackageManager.PERMISSION_GRANTED){
            pendingBluetoothDocumentId=documentId;
            pendingBluetoothPaperDots=dots;
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                BLUETOOTH_PRINT_PERMISSION_REQUEST);
            return;
        }
        showPairedBluetoothPrinters(documentId,dots);
    }
    @Override public void onRequestPermissionsResult(int requestCode,
            String[] permissions,int[] grantResults){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode!=BLUETOOTH_PRINT_PERMISSION_REQUEST)return;
        long id=pendingBluetoothDocumentId;
        int paper=pendingBluetoothPaperDots;
        pendingBluetoothDocumentId=-1;
        if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED
            &&id>0)showPairedBluetoothPrinters(id,paper);
        else toast("Nearby devices permission is required for Bluetooth printing.");
    }
    private void showPairedBluetoothPrinters(long documentId,int dots){
        try{
            BluetoothManager manager=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
            if(adapter==null){toast("Bluetooth is unavailable on this device.");return;}
            if(!adapter.isEnabled()){
                new AlertDialog.Builder(this).setTitle(ui("Bluetooth is disabled"))
                    .setMessage(ui("Enable Bluetooth and pair an ESC/POS printer in Android settings."))
                    .setPositiveButton("Bluetooth settings",(dialog,which)->
                        startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS")))
                    .setNegativeButton("Cancel",null).show();
                return;
            }
            ArrayList<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
            devices.sort((a,b)->String.valueOf(a.getName()).compareToIgnoreCase(
                String.valueOf(b.getName())));
            if(devices.isEmpty()){
                new AlertDialog.Builder(this).setTitle(ui("No paired Bluetooth printers"))
                    .setMessage(ui("First pair a compatible Bluetooth Classic ESC/POS receipt printer in Android settings."))
                    .setPositiveButton("Bluetooth settings",(dialog,which)->
                        startActivity(new Intent("android.settings.BLUETOOTH_SETTINGS")))
                    .setNegativeButton("Cancel",null).show();
                return;
            }
            String[] names=new String[devices.size()];
            for(int i=0;i<names.length;i++){
                BluetoothDevice device=devices.get(i);
                String name=device.getName();
                names[i]=name==null||name.trim().isEmpty()?ui("Paired Bluetooth device"):name;
                String address=device.getAddress();
                if(address!=null&&address.length()>=5)
                    names[i]+=" · "+address.substring(address.length()-5);
            }
            Store.Document document=db.document(documentId);
            new AlertDialog.Builder(this).setTitle(ui("Choose ESC/POS printer"))
                .setItems(names,(dialog,index)->{
                    toast("Sending receipt to printer…");
                    try{
                        BluetoothReceiptPrinter.send(this,devices.get(index),document,dots,
                            error->toast(error==null?
                                "Print data sent. Check the printer for completion.":error));
                    }catch(Exception e){error(e);}
                })
                .setNegativeButton("Cancel",null).show();
        }catch(SecurityException e){
            toast("Bluetooth permission is missing.");
        }catch(Exception e){error(e);}
    }
    /** Read-only numbering status; no unsafe editing of confirmed sequences. */
    private void numberingInfo(){
        StringBuilder numbers=new StringBuilder();
        for(String type:new String[]{Store.INVOICE,Store.QUOTE}){
            String latest="";
            for(Store.Document d:db.documents(type)){
                if(d.number!=null&&!d.number.trim().isEmpty()){
                    latest=d.number;
                    break;
                }
            }
            numbers.append(Store.INVOICE.equals(type)?"Računi: ":"Ponude: ")
                .append(latest.isEmpty()?"Još nema potvrđenih dokumenata":latest)
                .append("\n");
        }
        numbers.append("\nNovi se broj dodjeljuje pri potvrdi dokumenta. " +
            "Ne postoji ručno mijenjanje prethodno izdanih brojeva.");
        new AlertDialog.Builder(this).setTitle("Numeracija dokumenata")
            .setMessage(numbers.toString()).setPositiveButton("Zatvori",null).show();
    }
    private void settingRow(LinearLayout target,String symbol,String name,String subtitle,Runnable action){
        if(target.getChildCount()>0){
            View divider=new View(this);divider.setBackgroundColor(border());
            LinearLayout.LayoutParams dividerLp=lp(-1,1);
            dividerLp.setMargins(dp(12),0,dp(12),0);
            target.addView(divider,dividerLp);
        }
        // Nine original settings rows extend close to the navigation bar.
        // Native rows stay fully tappable and content can scroll at large fonts.
        LinearLayout row=horizontal();pad(row,13,11);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=label(symbol,19,BLUE,true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(shape(dark?0xff293a60:0xffecf0ff,12));
        row.addView(mark,lp(38,38));
        LinearLayout info=vertical();pad(info,12,0);
        TextView heading=label(name,13,ink(),true);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(heading);
        gap(info,3);
        TextView summary=label(subtitle,11,sub(),false);
        summary.setSingleLine(true);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(summary);
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(label("›",22,sub(),false));
        row.setContentDescription(ui(name)+". "+ui(subtitle));
        row.setFocusable(true);
        place(target,row);
        row.setOnClickListener(v->action.run());
    }
    private void company(){
        setup("company",true);
        pad(body,17,14);
        LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back=icon(R.drawable.ic_back,sub());
        back.setPadding(dp(8),dp(8),dp(8),dp(8));
        back.setContentDescription("Natrag u postavke");
        back.setOnClickListener(v->settings());
        header.addView(back,lp(38,46));
        LinearLayout headings=vertical();
        headings.addView(label("Profil tvrtke",23,ink(),true));gap(headings,4);
        headings.addView(label("Podaci za dokumente",11,sub(),false));
        header.addView(headings,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=label("•••",18,sub(),true);more.setGravity(Gravity.CENTER);
        more.setBackground(shape(panel(),12));
        more.setContentDescription("Informacije o profilu tvrtke");
        more.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Podaci izdavatelja")
            .setMessage("Promjene će se primijeniti na nove dokumente. " +
                "Podaci na već potvrđenim dokumentima neće se promijeniti.")
            .setPositiveButton("U redu",null).show());
        header.addView(more,lp(43,43));
        place(body,header);gap(body,29);
        LinearLayout hero=horizontal();pad(hero,13,15);
        background(hero,dark?0xff26365a:0xffe9edff,18);
        ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.brand_mark);
        hero.addView(brand,lp(64,64));
        LinearLayout heroText=vertical();pad(heroText,10,0);
        String companyName=db.setting("name").trim();
        heroText.addView(label(companyName.isEmpty()?"Moja tvrtka / obrt":companyName,15,ink(),true));
        gap(heroText,5);
        heroText.addView(label("Invoice and quote issuer",11,sub(),false));
        hero.addView(heroText,new LinearLayout.LayoutParams(0,-2,1));
        // Align the first business form with the reference. Preserve the
        // immutable-issuer disclosure below the editable sections.
        place(body,hero);gap(body,18);
        LinearLayout form=card();
        place(form,label("Company and legal identity",17,ink(),true));gap(form,9);
        String[] keys={"name","oib","vat_id","address","postal_code","city","country","email","phone","website","iban","bank_name","swift","registry","business_unit","operator","payment_method","payment_reference","tax_note",
            "legal_form","trading_name","company_number","register_authority","tax_registration",
            "address_2","region","payment_terms","footer_note","contact_person",
            "bank_account_number","bank_routing_number","bank_sort_code",
            "bank_address","invoice_reference"};
        String[] labels={"Legal business name","Tax ID (optional)","VAT ID (optional)","Street address","Postal code","City","Country","Email","Phone","Website","IBAN (optional)","Bank name","BIC / SWIFT","Register reference (optional)","Business unit","Operator reference","Payment method","Payment reference","Tax or legal note",
            "Legal form (Ltd, Inc, LLC, GmbH, d.o.o., sole trader...)","Trading name (optional)",
            "Company registration number","Registering authority","Tax registration details",
            "Additional address line","State / province / region","Payment terms","Invoice footer note",
            "Contact person","Bank account number (non-IBAN)","Bank routing number / ABA",
            "Sort code / branch code","Bank address","Purchase order / invoice reference"};
        EditText[] fields=new EditText[keys.length];
        // Keep all existing keys and persistence indexes; only reorder the UI.
        int[][] sections={{0,19,20,1,3,24,6,7,8,28},
            {10,11,12,16,17,26,29,30,31,32},
            {2,4,5,9,13,14,15,18,21,22,23,25,27,33}};
        String[] sectionNames={"Company and legal identity","Payment and bank details","Additional invoice details"};
        for(int group=0;group<sections.length;group++){
            LinearLayout card=group==0?form:card();
            if(group>0){
                place(card,label(sectionNames[group],17,ink(),true));
                gap(card,12);
            }
            for(int index:sections[group]){
                fields[index]=field(labels[index],db.setting(keys[index]));
                if(index==7){
                    fields[index].setInputType(InputType.TYPE_CLASS_TEXT|
                        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                }else if(index==1){
                    fields[index].setInputType(InputType.TYPE_CLASS_TEXT|
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    fields[index].setFilters(new android.text.InputFilter[]{
                        new android.text.InputFilter.LengthFilter(40)
                    });
                }else if(index==8){
                    fields[index].setInputType(InputType.TYPE_CLASS_PHONE);
                    fields[index].setTransformationMethod(
                        android.text.method.PasswordTransformationMethod.getInstance());
                }
                // Compact form rows preserve all original fields and their
                // order. Sensitive value toggles share the label's line.
                fields[index].setPadding(dp(14),dp(9),dp(14),dp(9));
                fields[index].setMinimumHeight(dp(47));
                gap(card,index==sections[group][0]?5:10);
                LinearLayout fieldHeading=horizontal();
                fieldHeading.setGravity(Gravity.CENTER_VERTICAL);
                TextView fieldName=label(labels[index],12,ink(),true);
                fieldHeading.addView(fieldName,new LinearLayout.LayoutParams(0,-2,1));
                if(index==1||index==8){
                    final int fieldIndex=index;
                    TextView reveal=label("Prikaži "+(index==1?"porezni broj":"telefon"),11,BLUE,true);
                    reveal.setContentDescription("Prikaži ili sakrij "+
                        (index==1?"porezni broj":"telefon"));
                    reveal.setPadding(dp(6),dp(5),dp(6),dp(5));
                    reveal.setOnClickListener(v->{
                        EditText value=fields[fieldIndex];
                        boolean masked=value.getTransformationMethod() instanceof
                            android.text.method.PasswordTransformationMethod;
                        value.setTransformationMethod(masked?null:
                            android.text.method.PasswordTransformationMethod.getInstance());
                        value.setSelection(value.length());
                        reveal.setText((masked?"Sakrij ":"Prikaži ")+
                            (fieldIndex==1?"porezni broj":"telefon"));
                    });
                    fieldHeading.addView(reveal);
                }
                place(card,fieldHeading);
                gap(card,5);
                place(card,fields[index]);
            }
            if(group==0)place(body,form);
            else place(body,card);
            gap(body,14);
        }
        Runnable saveCompany=()->{
            try{
                String name=fields[0].getText().toString().trim();
                String oib=fields[1].getText().toString().trim();
                String iban=fields[10].getText().toString().trim();
                if(name.isEmpty())throw new IllegalArgumentException("Enter a legal business name");
                if(oib.length()>40)throw new IllegalArgumentException("Porezni broj može imati najviše 40 znakova");
                if(!iban.isEmpty()&&!Iban.valid(iban))throw new IllegalArgumentException("IBAN nije valjan");
                String[] vals=new String[keys.length];
                for(int i=0;i<keys.length;i++)vals[i]=fields[i].getText().toString().trim();
                db.companySettings(keys,vals);
                toast("Company details saved");settings();
            }catch(Exception e){error(e);}
        };
        gap(body,8);
        caption(body,"Promjene vrijede samo za nove dokumente. Potvrđeni računi i ponude čuvaju izvorne podatke izdavatelja.");
        // Keep the primary action visible while scrolling the long form, as in the reference.
        LinearLayout bottomAction=vertical();pad(bottomAction,16,7);
        bottomAction.setBackgroundColor(bg());
        bottomAction.addView(action("✓  Spremi promjene",BLUE,true,saveCompany),lp(-1,52));
        root.addView(bottomAction,1,new LinearLayout.LayoutParams(-1,-2));
        gap(body,16);
    }
    /** Guidance to create an invoice or quote without obsolete integrations. */
    private void guide(){
        setup("guide",true);
        toolbar("Pomoć","Računi, ponude i osnovne radnje",true);
        LinearLayout intro=card();
        title(intro,"Dobro došli u Fakturivu");
        gap(intro,10);
        caption(intro,"Izradite račun ili ponudu, odaberite klijenta i dodajte stavke. Dokument možete spremiti kao PDF i podijeliti.");
        place(body,intro);gap(body,15);
        LinearLayout steps=card();
        title(steps,"Kako započeti");gap(steps,10);
        place(steps,label("1. Unesite podatke izdavatelja.",13,ink(),false));gap(steps,9);
        place(steps,label("2. Dodajte klijenta te artikle ili usluge.",13,ink(),false));gap(steps,9);
        place(steps,label("3. Izradite račun ili ponudu i pregledajte iznose.",13,ink(),false));gap(steps,9);
        place(steps,label("4. Potvrdite dokument i spremite ili podijelite PDF.",13,ink(),false));
        place(body,steps);gap(body,15);
        LinearLayout links=card();pad(links,0,2);
        settingRow(links,"▤","Novi račun","Izradite račun",()->newDocument(Store.INVOICE));
        settingRow(links,"▣","Nova ponuda","Pripremite ponudu",()->newDocument(Store.QUOTE));
        settingRow(links,"♧","Klijenti","Pregledajte i uredite klijente",this::clients);
        settingRow(links,"✎","Profil izdavatelja","Podaci na dokumentima",this::company);
        settingRow(links,"◈","Sigurnosne kopije","Izvoz ili povrat podataka",this::backupScreen);
        place(body,links);gap(body,20);
    }
    private void backupScreen(){
        setup("backup",true);toolbar("Sigurnosna kopija","Šifrirana kopija svih podataka",true);
        LinearLayout info=card();title(info,"Sačuvajte svoje poslovne podatke");
        gap(info,11);caption(info,"Za izvoz odaberite lozinku od najmanje 10 znakova. Sačuvajte je jer bez nje nije moguće vratiti kopiju.");
        gap(info,11);caption(info,"Uvoz u potpunosti zamjenjuje postojeće podatke. Prije uvoza izvezite aktualnu kopiju.");
        place(body,info);gap(body,15);
        LinearLayout local=card();
        title(local,"Automatska zaštita");gap(local,9);
        local.addView(label(db.backupSummary(),12,ink(),false));gap(local,7);
        caption(local,"Automatske kopije pomažu pri oporavku. Prije promjene telefona izvezite zasebnu sigurnosnu kopiju.");
        if(!db.backupWarning().isEmpty()){gap(local,8);local.addView(label(db.backupWarning(),12,RED,true));}
        gap(local,12);
        secondary(local,"Vrati prethodnu kopiju",this::showLocalRecovery);
        place(body,local);gap(body,18);
        button(body,"Izvezi šifriranu kopiju",()->passwordPrompt(false));
        gap(body,10);secondary(body,"Uvezi sigurnosnu kopiju",()->passwordPrompt(true));
        gap(body,15);
        caption(body,"Sačuvajte izvezenu kopiju na sigurnom mjestu kako biste kasnije mogli vratiti podatke.");
    }
    private void showLocalRecovery(){
        try{
            int count=RecoveryVault.count(this);
            if(count==0){toast("Nema dostupnih kopija");return;}
            String[] labels=new String[count];
            for(int i=0;i<count;i++)labels[i]="Kopija "+(i+1)+(i==0?" (najnovija)":"");
            new AlertDialog.Builder(this).setTitle("Odaberite kopiju za oporavak")
                .setItems(labels,(dialog,index)->new AlertDialog.Builder(this)
                    .setTitle("Potvrdite zamjenu podataka")
                    .setMessage("Odabrana kopija zamijenit će trenutačne podatke. Prije potvrde spremite aktualnu sigurnosnu kopiju.")
                    .setNegativeButton("Odustani",null)
                    .setPositiveButton("Vrati",(d,w)->{
                        try{db.restoreLocal(index);toast("Podaci su vraćeni");navigate("home");}
                        catch(Exception e){error(e);}
                    }).show()).show();
        }catch(Exception e){error(e);}
    }
    private void passwordPrompt(boolean importing){
        EditText pass=field("Lozinka (barem 10 znakova)","");
        pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box=vertical();pad(box,18,10);place(box,pass);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(importing?"Vrati sigurnosnu kopiju":"Šifriraj sigurnosnu kopiju")
            .setView(box)
            .setNegativeButton("Odustani",null)
            .setPositiveButton("Nastavi",null).create();
        dialog.setOnShowListener(ignored->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
                char[] password=pass.getText().toString().toCharArray();
                if(password.length<10){
                    Arrays.fill(password,'\0');
                    pass.setError("Lozinka mora imati barem 10 znakova");
                    pass.requestFocus();
                    return;
                }
                clearPass();
                pendingPassword=password;
                dialog.dismiss();
                if(importing){
                    new AlertDialog.Builder(this).setTitle("Zamjena podataka")
                        .setMessage("Uvoz će zamijeniti sve postojeće podatke. Nastaviti?")
                        .setNegativeButton("Odustani",(confirmation,which)->clearPass())
                        .setPositiveButton("Odaberi datoteku",(confirmation,which)->openBackup())
                        .setOnCancelListener(confirmation->clearPass())
                        .show();
                }else createFile(BACKUP_EXPORT,"application/octet-stream",
                    "Fakturiva-backup.fak");
            }));
        dialog.show();
    }
    private void openBackup(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,BACKUP_IMPORT);
    }
    private void createFile(int code,String type,String fileName){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType(type);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,fileName);startActivityForResult(i,code);
    }
    private void savePdf(long id){pendingPdfId=id;Store.Document d=db.document(id);
        createFile(PDF_EXPORT,"application/pdf",(d.number==null?"Nacrt-"+id:d.number)+".pdf");
    }
    private void sharePdf(long id){
        try{
            for(File old:getCacheDir().listFiles()==null?new File[0]:getCacheDir().listFiles()){
                if(old.getName().startsWith("share-") && old.getName().endsWith(".pdf")
                   && System.currentTimeMillis()-old.lastModified()>3L*24*60*60*1000)old.delete();
            }
            String filename="share-"+java.util.UUID.randomUUID().toString()+".pdf";
            File f=new File(getCacheDir(),filename);
            try(FileOutputStream output=new FileOutputStream(f)){PdfMaker.write(db,db.document(id),output);}
            Uri shared=PdfShareProvider.uri(filename);
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("application/pdf");
            share.putExtra(Intent.EXTRA_STREAM,shared);
            share.setClipData(ClipData.newUri(getContentResolver(),"Fakturiva PDF",shared));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Podijeli PDF"));
        }catch(Exception e){error(e);}
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code==DEVICE_AUTH_REQUEST){
            int completedAction=deviceAuthAction;
            deviceAuthAction=0;
            if(result!=RESULT_OK)return;
            if(completedAction==1){
                prefs.edit().putBoolean("device_lock",true).putBoolean("welcomed",true).apply();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                if(securityFromSettings)settings();
                else{navigate("home");offerWorkingDraftRecovery();}
            }else if(completedAction==2){
                if(!"security_unlock".equals(screen))return;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                navigate("home");offerWorkingDraftRecovery();
            }else if(completedAction==3){
                prefs.edit().putBoolean("device_lock",false).apply();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                settings();
            }
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null){
            if(code==BACKUP_EXPORT||code==BACKUP_IMPORT)clearPass();
            if(code==104)pendingPaymentsCsvMonth=null;
            if(code==106)pendingReportCsvMonth=null;
            return;
        }
        Uri uri=data.getData();
        if((code==BACKUP_EXPORT||code==BACKUP_IMPORT)&&pendingPassword==null){
            toast("Lozinka nije dostupna nakon ponovnog pokretanja. Ponovite izvoz ili uvoz.");
            return;
        }
        try{
            if(code==PDF_EXPORT){
                if(pendingPdfId<=0)
                    throw new IllegalStateException("Dokument za izvoz nije poznat. Ponovite izvoz.");
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IllegalStateException("Nije moguće otvoriti datoteku");
                    PdfMaker.write(db,db.document(pendingPdfId),out);
                }
                toast("PDF uspješno spremljen");
            }else if(code==104){
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IllegalStateException("Datoteka nije dostupna");
                    PaymentsCsv.write(PaymentsCsv.forMonth(db.payments(),pendingPaymentsCsvMonth),out);
                }
                toast("CSV evidencija uplata spremljena");
            }else if(code==106){
                if(pendingReportCsvMonth==null)
                    throw new IllegalStateException("Mjesec izvještaja nije poznat. Ponovite izvoz.");
                long[] summary=reportMetrics(db.documents(Store.INVOICE),
                    db.payments(),pendingReportCsvMonth,selectedCurrency());
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IllegalStateException("Datoteka nije dostupna");
                    ReportCsv.write(pendingReportCsvMonth,summary,selectedCurrency(),out);
                }
                toast("Mjesečni izvještaj uspješno spremljen");
            }else if(code==BACKUP_EXPORT){
                byte[] dataBytes=db.backup().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] secure=SecureBackup.encrypt(dataBytes,pendingPassword);
                Arrays.fill(dataBytes,(byte)0);
                try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                    if(out==null)throw new IllegalStateException("Nije moguće spremiti datoteku");
                    out.write(secure);
                }
                Arrays.fill(secure,(byte)0);toast("Šifrirana kopija spremljena");
            }else if(code==BACKUP_IMPORT){
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    if(input==null)throw new IllegalStateException("Nije moguće čitati datoteku");
                    byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1){if(bytes.size()+n>25000000)throw new IllegalArgumentException("Datoteka je prevelika");bytes.write(buffer,0,n);}
                }
                byte[] decrypted=SecureBackup.decrypt(bytes.toByteArray(),pendingPassword);
                try{db.restore(new JSONObject(new String(decrypted,java.nio.charset.StandardCharsets.UTF_8)));}
                finally{Arrays.fill(decrypted,(byte)0);}
                toast("Sigurnosna kopija obnovljena");navigate("home");
            }
        }catch(Exception e){error(e);}
        finally{
            if(code==BACKUP_EXPORT||code==BACKUP_IMPORT)clearPass();
            if(code==104)pendingPaymentsCsvMonth=null;
            if(code==106)pendingReportCsvMonth=null;
        }
    }
    private void clearPass(){if(pendingPassword!=null){Arrays.fill(pendingPassword,'\0');pendingPassword=null;}}
    private void error(Exception e){String s=e.getMessage();toast(s==null?"Došlo je do pogreške":s);}
    private void toast(String s){Toast.makeText(this,ui(s),Toast.LENGTH_LONG).show();}
    private void handleBack(){
        if("local_login".equals(screen)||"local_register".equals(screen)){
            enteredLocalPin.setLength(0);
            if("local_register".equals(screen))welcome();else finish();
        }
        else if("security_unlock".equals(screen))finish();
        else if("security_setup".equals(screen)){
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if(securityFromSettings)settings();else welcome();
        }
        else if("home".equals(screen))finish();
        else backFromToolbar();
    }
    // Compatibility for API 24–32 hardware/system Back keys. On API 33+
    // the registered OnBackInvokedCallback above handles predictive gestures.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed(){
        handleBack();
    }
    /** Native six-month confirmed-invoice chart tied to the chosen month. */
    private final class RevenueBars extends View{
        final android.graphics.Paint paint=new android.graphics.Paint(3);
        private final Calendar ending;
        RevenueBars(Context context,Calendar month){
            super(context);
            ending=(Calendar)month.clone();
            ending.set(Calendar.DAY_OF_MONTH,1);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            long[] values=new long[6];
            Calendar cursor=(Calendar)ending.clone();
            cursor.add(Calendar.MONTH,-5);
            String[] names={"Sij","Velj","Ožu","Tra","Svi","Lip","Srp","Kol","Ruj","Lis","Stu","Pro"};
            String[] labels=new String[6];
            SimpleDateFormat fmt=new SimpleDateFormat("yyyy-MM",Locale.ROOT);
            long max=1;
            for(int i=0;i<6;i++){
                values[i]=db.invoicedCentsMonth(fmt.format(cursor.getTime()),selectedCurrency());
                max=Math.max(max,values[i]);
                labels[i]=names[cursor.get(Calendar.MONTH)];
                cursor.add(Calendar.MONTH,1);
            }
            float width=getWidth(),height=getHeight(),labelH=dp(26),base=height-labelH;
            paint.setColor(border());paint.setStrokeWidth(dp(1));
            canvas.drawLine(0,base,width,base,paint);
            paint.setTextSize(dp(10));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            for(int i=0;i<6;i++){
                float cell=width/6f,barWidth=cell*.72f,x=i*cell+cell*.14f;
                float barHeight=values[i]==0?dp(2):(base-dp(14))*values[i]/(float)max;
                paint.setColor(i==5?TEAL:0xff7385f2);
                canvas.drawRoundRect(x,base-barHeight,x+barWidth,base,dp(7),dp(7),paint);
                paint.setColor(sub());
                canvas.drawText(labels[i],i*cell+cell/2f,height-dp(3),paint);
            }
            paint.setTextAlign(android.graphics.Paint.Align.LEFT);
        }
    }
    private static List<Store.Payment> paymentsForCurrency(List<Store.Payment> all,String code){
        ArrayList<Store.Payment> chosen=new ArrayList<>();
        for(Store.Payment p:all)
            if(BillingCurrency.canonical(p.currencyCode).equals(BillingCurrency.canonical(code)))
                chosen.add(p);
        return chosen;
    }
    private final class RevenueLine extends View{
        private final android.graphics.Paint paint=new android.graphics.Paint(3);
        private final long[] cumulative;
        RevenueLine(android.content.Context context){
            super(context);
            Calendar calendar=Calendar.getInstance();
            String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(calendar.getTime());
            cumulative=ReceiptsChart.cumulative(
                paymentsForCurrency(db.payments(),selectedCurrency()),month,
                calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float width=getWidth(),height=getHeight();
            if(width<=0||height<=0)return;
            long maximum=Math.max(1,cumulative[cumulative.length-1]);
            float[] y=new float[cumulative.length];
            float[] x=new float[cumulative.length];
            for(int n=0;n<cumulative.length;n++){
                x[n]=width*n/(cumulative.length-1);
                y[n]=height*(0.88f-0.73f*cumulative[n]/maximum);
            }
            android.graphics.Path line=new android.graphics.Path();
            line.moveTo(x[0],y[0]);
            for(int n=1;n<x.length;n++){
                float midX=(x[n-1]+x[n])/2;
                float midY=(y[n-1]+y[n])/2;
                line.quadTo(x[n-1],y[n-1],midX,midY);
            }
            line.lineTo(x[x.length-1],y[y.length-1]);
            android.graphics.Path area=new android.graphics.Path(line);
            area.lineTo(width,height);area.lineTo(0,height);area.close();
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(new android.graphics.LinearGradient(0,0,0,height,
                0x6082d9e7,0x0082d9e7,android.graphics.Shader.TileMode.CLAMP));
            canvas.drawPath(area,paint);paint.setShader(null);
            paint.setColor(0xff86dfdf);paint.setStrokeWidth(dp(2.2f));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            canvas.drawPath(line,paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
        }
    }
}
