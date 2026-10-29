package com.brendigo.fakturiva;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
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
        TEAL=0xff14b9a4, LINE=0xffe9edf5, RED=0xffdb4c56;
    private static final int PDF_EXPORT=101,BACKUP_EXPORT=102,BACKUP_IMPORT=103;
    private Store db; private SharedPreferences prefs;
    private LinearLayout root,body; private ScrollView scroller; private FrameLayout contentFrame;
    private int paymentFilter=0;
    private String pendingPaymentsCsvMonth=null;
    private final Calendar reportMonth=Calendar.getInstance();
    private String screen="home",filter="Svi",editType=Store.INVOICE;
    private String documentSearchQuery="";
    private boolean dark=false;
    private long currentDocumentId=-1,editClientId=-1,editingDraftId=-1;
    private String draftIssue="",draftDue="",draftSupply="",draftNote="";
    private final ArrayList<Store.Item> draftItems=new ArrayList<>();
    private long pendingPdfId=-1;private char[] pendingPassword;
    private final SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT);

    @Override public void onCreate(Bundle b){super.onCreate(b);
        db=new Store(this);prefs=getSharedPreferences("ui",MODE_PRIVATE);
        dark=prefs.getBoolean("dark",false);
        if(runScreenshotHarness())return;
        try{
            try(android.database.Cursor check=db.getReadableDatabase().rawQuery("PRAGMA quick_check(1)",null)){
                if(!check.moveToFirst()||!"ok".equalsIgnoreCase(check.getString(0)))
                    throw new IllegalStateException("Neuspjela provjera baze");
            }
            if(!prefs.getBoolean("welcomed",false)) splash(true);
            else { navigate("home");offerWorkingDraftRecovery(); }
        }catch(Exception openError){damagedDatabase(openError);}
    }
    @Override protected void onStop(){
        if("new_doc".equals(screen)||"edit_items".equals(screen)||"review".equals(screen))
            saveWorkingDraft();
        super.onStop();
    }
    /** Allows debug-only emulator screenshots without introducing preview screens in release builds. */
    private boolean runScreenshotHarness(){
        if(!"com.brendigo.fakturiva.SCREENSHOT".equals(getIntent().getAction()))return false;
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)return false;
        String fingerprint=android.os.Build.FINGERPRINT.toLowerCase(java.util.Locale.ROOT);
        if(!fingerprint.contains("generic")&&!fingerprint.contains("ranchu"))return false;
        try{
            Class<?> harness=Class.forName("com.brendigo.fakturiva.ScreenshotHarness");
            harness.getMethod("show",MainActivity.class,String.class)
                .invoke(null,this,getIntent().getStringExtra("screen"));
            return true;
        }catch(Exception e){
            android.util.Log.e("Fakturiva","Emulator screenshot harness failed",e);
            return false;
        }
    }
    @Override protected void onDestroy(){if(pendingPassword!=null)Arrays.fill(pendingPassword,'\0');db.close();super.onDestroy();}
    private int bg(){return dark?0xff0d1728:BG;}
    private int panel(){return dark?0xff14233b:WHITE;}
    private int ink(){return dark?WHITE:TEXT;}
    private int sub(){return dark?0xff9aaac2:MUTED;}
    private int border(){return dark?0xff283b56:LINE;}
    private int dp(float value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outlined(int color){GradientDrawable d=shape(color,17);d.setStroke(dp(1),border());return d;}
    private void background(View v,int color,int radius){v.setBackground(shape(color,radius));}
    private TextView label(String s,int size,int color,boolean strong){TextView t=new TextView(this);t.setText(s);
        t.setTextColor(color);t.setTextSize(size);t.setFontFeatureSettings("kern");
        t.setTypeface(Typeface.create("sans-serif",strong?Typeface.BOLD:Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private void gap(LinearLayout container,int h){View v=new View(this);container.addView(v,lp(1,h));}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private void place(LinearLayout parent,View view){parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
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
        contentFrame=new FrameLayout(this);
        root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));
        scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.setClipToPadding(false);
        contentFrame.addView(scroller,new FrameLayout.LayoutParams(-1,-1));
        body=vertical();pad(body,17,18);scroller.addView(body,new ScrollView.LayoutParams(-1,-2));
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
            case "catalog":case "reports":case "payments":case "company":case "eracun":
            case "backup":settings();break;
            default:home();
        }
    }
    private void toolbar(String title,String subtitle,boolean back){
        LinearLayout row=horizontal();
        if(back){
            TextView b=label("‹",31,ink(),false);
            b.setGravity(Gravity.CENTER);
            b.setContentDescription("Natrag");
            row.addView(b,lp(40,48));
            b.setOnClickListener(v->backFromToolbar());
        }
        LinearLayout heading=vertical();TextView t=label(title,23,ink(),true);place(heading,t);
        if(subtitle!=null){TextView st=label(subtitle,11,sub(),false);place(heading,st);}
        row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
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
        for(int i=0;i<pages.length;i++){final String destination=pages[i];
            boolean selected=screen.equals(destination);
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);
            LinearLayout badge=vertical();badge.setGravity(Gravity.CENTER);
            if(selected)background(badge,dark?0xff273D66:0xffECEEFF,12);
            badge.addView(icon(icons[i],selected?BLUE:sub()),lp(23,23));
            LinearLayout.LayoutParams badgeLp=lp(39,35);badgeLp.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(badge,badgeLp);
            TextView name=label(names[i],10,selected?BLUE:sub(),selected);
            name.setGravity(Gravity.CENTER);tab.addView(name,lp(-1,19));
            tab.setContentDescription("Otvori "+names[i]);
            bar.addView(tab,new LinearLayout.LayoutParams(0,dp(56),1));
            tab.setOnClickListener(v->navigate(destination));
        }
        place(root,bar);
    }
    /** Never silently delete or overwrite a damaged database. */
    private void damagedDatabase(Exception cause){
        setup("damaged",false);
        gap(body,40);title(body,"Potreban je oporavak podataka");gap(body,16);
        caption(body,"Baza se nije mogla sigurno otvoriti. Izvorna datoteka neće biti obrisana. Ako postoji lokalna kopija, možete je pokušati vratiti.");
        gap(body,25);
        button(body,"Pokušaj oporavak iz lokalne kopije",()->{
            new AlertDialog.Builder(this).setTitle("Oporavak aplikacije")
                .setMessage("Prije vraćanja sačuvat ćemo postojeću bazu u zasebnu datoteku. Lokalna kopija može biti starija od posljednjih izmjena.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Pokušaj",(dlg,w)->recoverDamagedDatabase()).show();
        });
    }
    private void recoverDamagedDatabase(){
        try{
            JSONObject snapshot=RecoveryVault.read(this,0);
            db.close();
            File old=getDatabasePath("fakturiva.db");
            File location=new File(getFilesDir(),"damaged-db-"+System.currentTimeMillis());
            if(!location.mkdirs())throw new IllegalStateException("Nema mjesta za spremanje oštećene baze");
            for(String suffix:new String[]{"","-wal","-shm","-journal"}){
                File original=new File(old.getAbsolutePath()+suffix);
                if(original.exists()&&!original.renameTo(new File(location,"fakturiva.db"+suffix)))
                    throw new IllegalStateException("Nije moguće sigurno arhivirati bazu");
            }
            db=new Store(this);db.restore(snapshot);
            toast("Oporavak je dovršen. Pregledajte dokumente i izvezite novu kopiju.");
            navigate("home");
        }catch(Exception e){error(e);damagedDatabase(e);}
    }
    private void navigate(String dest){
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
            case "eracun":eracun();break;
            default:home();
        }
    }

    /** Native branded entry view, designed to match the dark Fakturiva splash reference. */
    private void splash(boolean animate){
        screen="splash";
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff0a1c33);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN);
        FrameLayout frame=new FrameLayout(this);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff173a64,0xff102b4b,0xff09192d});
        frame.setBackground(bg);
        setContentView(frame);
        View rings=new View(this){
            final android.graphics.Paint brush=new android.graphics.Paint(3);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                brush.setColor(0x1affffff);brush.setStyle(android.graphics.Paint.Style.STROKE);
                brush.setStrokeWidth(dp(0.7f));
                float unit=getWidth();
                canvas.drawCircle(unit*1.20f,getHeight()*.32f,unit*.65f,brush);
                canvas.drawCircle(-unit*.45f,getHeight()*.64f,unit*.88f,brush);
                brush.setStyle(android.graphics.Paint.Style.FILL);
            }
        };
        frame.addView(rings,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout center=vertical();center.setGravity(Gravity.CENTER);
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.brand_mark);
        LinearLayout.LayoutParams logo=lp(104,104);logo.gravity=Gravity.CENTER_HORIZONTAL;
        center.addView(mark,logo);gap(center,28);
        TextView name=label("Fakturiva",42,WHITE,true);name.setGravity(Gravity.CENTER);
        place(center,name);gap(center,13);
        TextView subtitle=label("Računi i ponude, jednostavno.",14,0xffbdcde1,false);
        subtitle.setGravity(Gravity.CENTER);place(center,subtitle);
        FrameLayout.LayoutParams middle=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        middle.leftMargin=dp(18);middle.rightMargin=dp(18);frame.addView(center,middle);
        LinearLayout bottom=vertical();bottom.setGravity(Gravity.CENTER);
        FrameLayout progress=new FrameLayout(this);background(progress,0xff36516c,4);
        View completed=new View(this);background(completed,0xff2fd2c5,4);
        FrameLayout.LayoutParams fill=new FrameLayout.LayoutParams(dp(33),dp(4),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        progress.addView(completed,fill);
        LinearLayout.LayoutParams progressLp=lp(82,4);progressLp.gravity=Gravity.CENTER;
        bottom.addView(progress,progressLp);gap(bottom,21);
        TextView text=label("Vaše poslovanje. Jednostavnije.",12,0xff9fb5ce,false);
        text.setGravity(Gravity.CENTER);place(bottom,text);
        FrameLayout.LayoutParams down=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        down.bottomMargin=dp(53);frame.addView(bottom,down);
        if(animate){
            frame.setOnClickListener(v->welcome());
            frame.postDelayed(()->{if("splash".equals(screen)&&!isFinishing())welcome();},850);
        }
    }
    private void welcome(){
        setup("welcome",false);
        root.setBackgroundColor(WHITE);
        body.setBackgroundColor(WHITE);
        pad(body,25,20);
        LinearLayout brand=horizontal();
        ImageView mark=new ImageView(this);
        mark.setImageResource(R.drawable.brand_mark);brand.addView(mark,lp(43,43));
        TextView brandName=label("Fakturiva",24,NAVY,true);
        LinearLayout.LayoutParams brandLp=lp(-2,-1);brandLp.leftMargin=dp(10);
        brand.addView(brandName,brandLp);
        place(body,brand);gap(body,20);
        body.addView(new WelcomeIllustration(this),lp(-1,330));gap(body,24);
        place(body,label("SVE NA JEDNOM MJESTU",10,0xff6c83ac,true));gap(body,11);
        TextView heading=label("Manje papirologije.\nViše vremena za posao.",26,NAVY,true);
        heading.setLineSpacing(dp(3),1.0f);
        place(body,heading);gap(body,11);
        TextView info=label("Izradite profesionalne račune i ponude, pratite naplatu i organizirajte klijente.",13,MUTED,false);
        info.setLineSpacing(dp(3),1.0f);place(body,info);
        gap(body,14);
        LinearLayout dots=horizontal();
        for(int n=0;n<3;n++){
            View dot=new View(this);
            dot.setBackground(shape(n==0?BLUE:0xffdbe2ef,7));
            LinearLayout.LayoutParams p=lp(n==0?23:7,7);p.rightMargin=dp(6);dots.addView(dot,p);
        }
        place(body,dots);gap(body,23);
        button(body,"›  Započni",()->{
            prefs.edit().putBoolean("welcomed",true).apply();navigate("home");
        });
        gap(body,11);
        TextView note=label("Bez registracije. Vaši podaci ostaju na uređaju.",11,MUTED,false);
        note.setGravity(Gravity.CENTER);place(body,note);gap(body,23);
    }
    /** Entirely native, static illustration; all actions remain real UI controls. */
    private final class WelcomeIllustration extends View{
        private final android.graphics.Paint p=new android.graphics.Paint(3);
        WelcomeIllustration(Context ctx){super(ctx);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            canvas.save();canvas.scale(getWidth()/340f,getHeight()/340f);
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setShader(new android.graphics.LinearGradient(0,0,310,340,
                new int[]{0xffe7edff,0xfff1f3ff,0xfff8f8fd},null,
                android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0,0,340,340,37,37,p);p.setShader(null);
            p.setColor(0xffdae5fa);canvas.drawCircle(170,164,115,p);
            canvas.save();canvas.rotate(-5,170,174);
            p.setColor(0x330f2e72);p.setShadowLayer(19,0,16,0x330f2e72);
            canvas.drawRoundRect(72,63,270,288,20,20,p);p.clearShadowLayer();
            p.setColor(Color.WHITE);canvas.drawRoundRect(72,63,270,288,20,20,p);
            p.setColor(NAVY);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(11);canvas.drawText("FAKTURIVA",90,96,p);
            p.setTextSize(25);canvas.drawText("RAČUN",90,156,p);
            p.setColor(0xffe3eafd);
            canvas.drawRoundRect(90,175,244,182,5,5,p);
            canvas.drawRoundRect(90,193,184,200,5,5,p);
            p.setStrokeWidth(1);canvas.drawLine(90,240,245,240,p);
            p.setColor(0xff8191ad);p.setTextSize(11);p.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Ukupno",90,260,p);
            p.setColor(NAVY);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(16);canvas.drawText("1.250,00 €",192,261,p);
            canvas.restore();
            p.setColor(0xff15beb1);p.setShadowLayer(12,0,10,0x5515beb1);
            canvas.drawRoundRect(246,243,315,308,23,23,p);p.clearShadowLayer();
            p.setColor(Color.WHITE);p.setStrokeWidth(4.8f);
            p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
            p.setStyle(android.graphics.Paint.Style.STROKE);
            android.graphics.Path check=new android.graphics.Path();
            check.moveTo(264,272);check.lineTo(275,283);check.lineTo(296,258);
            canvas.drawPath(check,p);p.setStyle(android.graphics.Paint.Style.FILL);
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
        backupAlert();
        LinearLayout bar=horizontal();
        LinearLayout brand=vertical();
        brand.addView(label("Fakturiva",23,ink(),true));
        brand.addView(label("Upravljanje poslovanjem",11,sub(),false));
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
        place(body,bar);gap(body,21);

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
        place(body,welcome);gap(body,15);

        LinearLayout hero=vertical();pad(hero,19,19);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff143354,0xff243584});
        gradient.setCornerRadius(dp(21));hero.setBackground(gradient);
        long[] monthly=db.monthlyReceipts();
        LinearLayout heading=horizontal();heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(label("Naplaćeno ovaj mjesec",12,0xffcedbf2,false),
            new LinearLayout.LayoutParams(0,-2,1));
        if(monthly[10]>0){
            double change=100.0*(monthly[11]-monthly[10])/(double)monthly[10];
            if(Double.isFinite(change)){
                TextView delta=label((change>=0?"↗ +":"↘ ")+
                    String.format(Locale.forLanguageTag("hr-HR"),"%.1f%%",change),
                    11,change>=0?0xff79e7c6:0xffffb6a9,true);
                delta.setGravity(Gravity.CENTER);
                delta.setBackground(shape(0xff314c82,13));pad(delta,7,5);
                heading.addView(delta);
            }
        }
        place(hero,heading);gap(hero,13);
        hero.addView(label(Money.euros(monthly[11]),30,WHITE,true));gap(hero,4);
        hero.addView(label("Prema evidentiranim uplatama",11,0xffb8d2e7,false));
        gap(hero,8);
        hero.addView(new RevenueLine(this),lp(-1,57));
        place(body,hero);gap(body,13);

        LinearLayout stats=horizontal();
        String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new java.util.Date());
        metric(stats,"Računi",String.valueOf(db.countMonth(Store.INVOICE,month)),"▤",BLUE);
        metric(stats,"Ponude",String.valueOf(db.countMonth(Store.QUOTE,month)),"▣",TEAL);
        place(body,stats);
        section("Brze radnje");
        LinearLayout fast=horizontal();
        quick(fast,"＋","Novi račun",()->newDocument(Store.INVOICE));
        quick(fast,"▣","Nova ponuda",()->newDocument(Store.QUOTE));
        quick(fast,"♧","Novi klijent",this::addClient);
        place(body,fast);

        gap(body,17);
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
    private void showNotifications(){
        ArrayList<Store.Document> open=new ArrayList<>();
        for(Store.Document d:db.documents(Store.INVOICE))
            if(Store.ISSUED.equals(d.status)&&d.outstanding()>0)open.add(d);
        if(open.isEmpty()){new AlertDialog.Builder(this).setTitle("Obavijesti")
            .setMessage("Trenutačno nema nenaplaćenih potvrđenih računa.").setPositiveButton("U redu",null).show();return;}
        String[] items=new String[open.size()];
        for(int i=0;i<items.length;i++){Store.Document d=open.get(i);
            items[i]=(d.number==null?"Nacrt":d.number)+" · "+d.clientName+" · "+Money.euros(d.outstanding());}
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
        LinearLayout c=card();pad(c,14,13);
        ImageView image=icon(figureIcon(glyph),accent);
        image.setPadding(dp(8),dp(8),dp(8),dp(8));
        image.setBackground(shape(accent==TEAL?0xffdff8f4:0xffe8edff,12));
        c.addView(image,lp(38,38));gap(c,7);
        c.addView(label(label,12,sub(),false));gap(c,3);
        c.addView(label(value,24,ink(),true));c.addView(label("Ovaj mjesec",10,sub(),false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);
    }
    private void quick(LinearLayout row,String glyph,String label,Runnable action){
        // Compact quick-action cards must leave enough room for the full caption.
        LinearLayout c=card();pad(c,11,8);c.setGravity(Gravity.CENTER);
        boolean quote="▣".equals(glyph),client="♧".equals(glyph);
        int tint=quote?TEAL:client?0xffeb963c:BLUE;
        ImageView image=icon(figureIcon(glyph),tint);
        image.setPadding(dp(10),dp(10),dp(10),dp(10));
        image.setBackground(shape(quote?0xffdcf8f4:client?0xfffff0e3:0xffe8edff,12));
        LinearLayout.LayoutParams iconLp=lp(45,45);iconLp.gravity=Gravity.CENTER_HORIZONTAL;c.addView(image,iconLp);
        gap(c,7);TextView text=label(label,11,ink(),true);text.setGravity(Gravity.CENTER);place(c,text);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(93),1);
        p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);c.setOnClickListener(v->action.run());
    }
    private void documents(String type){
        boolean invoice=Store.INVOICE.equals(type);
        setup(invoice?"invoices":"quotes",true);
        LinearLayout header=horizontal();
        LinearLayout heading=vertical();
        heading.addView(label(invoice?"Računi":"Ponude",23,ink(),true));
        gap(heading,5);
        heading.addView(label(invoice?"Svi vaši izdani računi":"Sve vaše ponude",
            12,sub(),false));
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
        place(body,header);gap(body,18);

        LinearLayout searchBar=horizontal();
        searchBar.setBackground(outlined(panel()));
        searchBar.setPadding(dp(12),dp(4),dp(12),dp(4));
        searchBar.addView(icon(R.drawable.ic_search,sub()),lp(25,25));
        EditText search=field(invoice?"Pretraži račune...":"Pretraži ponude...",
            documentSearchQuery);
        search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams inputLp=new LinearLayout.LayoutParams(0,dp(45),1);
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
            new String[]{"Svi","Poslane","Skice"};
        Runnable chooseFilter=()->new AlertDialog.Builder(this)
            .setTitle("Filtriraj "+(invoice?"račune":"ponude"))
            .setSingleChoiceItems(states,java.util.Arrays.asList(states).indexOf(filter),
                (dialog,which)->{
                    filter=states[which];dialog.dismiss();documents(type);
                })
            .setNegativeButton("Odustani",null).show();
        filtersAction.setOnClickListener(v->chooseFilter.run());
        searchFilter.setOnClickListener(v->chooseFilter.run());
        place(body,searchBar);gap(body,15);

        LinearLayout chips=horizontal();pad(chips,4,4);
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
        place(body,chips);gap(body,14);

        List<Store.Document> all=db.documents(type);
        LinearLayout totals=horizontal();
        TextView total=label("Ukupno "+(invoice?"računa":"ponuda")+"  "+all.size(),
            11,sub(),false);
        totals.addView(total,new LinearLayout.LayoutParams(0,-2,1));
        if(invoice){
            long unpaid=0;
            for(Store.Document document:all)
                if(Store.ISSUED.equals(document.status))
                    unpaid=Math.addExact(unpaid,document.outstanding());
            TextView remaining=label("Neplaćeno  "+Money.euros(unpaid),11,ink(),true);
            remaining.setSingleLine(true);
            totals.addView(remaining);
        }
        place(body,totals);gap(body,12);

        LinearLayout group=vertical();
        group.setBackground(outlined(panel()));
        List<Store.Document> matching=new ArrayList<>();
        for(Store.Document document:all){
            if(!matches(document,filter))continue;
            matching.add(document);
            documentRowGrouped(group,document);
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

    private void documentRowGrouped(LinearLayout target,Store.Document document){
        LinearLayout item=vertical();item.setTag(document);
        View divider=new View(this);divider.setBackgroundColor(border());
        item.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        LinearLayout row=horizontal();pad(row,12,15);
        ImageView symbol=icon(Store.INVOICE.equals(document.type)?
            R.drawable.ic_invoice:R.drawable.ic_quote,
            Store.INVOICE.equals(document.type)?BLUE:TEAL);
        symbol.setPadding(dp(9),dp(9),dp(9),dp(9));
        symbol.setBackground(shape(Store.INVOICE.equals(document.type)?
            0xffe8edff:0xffdcf8f4,12));
        row.addView(symbol,lp(42,42));
        LinearLayout description=vertical();
        LinearLayout.LayoutParams descLp=new LinearLayout.LayoutParams(0,-2,1);
        descLp.leftMargin=dp(10);
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
        LinearLayout amount=vertical();amount.setGravity(Gravity.RIGHT);
        amount.addView(label(Money.euros(document.totalCents),12,ink(),true));
        gap(amount,6);
        String status=DocumentStatus.label(document,DocumentStatus.today(),
            DocumentStatus.inSevenDays());
        TextView badge=label(status,10,badgeForeground(status),true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9),dp(5),dp(9),dp(5));
        badge.setBackground(shape(badgeBackground(status),15));
        amount.addView(badge);
        row.addView(amount);
        place(item,row);target.addView(item,new LinearLayout.LayoutParams(-1,-2));
        item.setContentDescription(number+", "+document.clientName+", "+
            Money.euros(document.totalCents)+", "+status);
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
        if("Plaćen".equals(status)||"Poslana".equals(status))return 0xff078a65;
        if("Dospijeva".equals(status))return 0xffaf6c08;
        if("Neplaćen".equals(status)||"Dospio".equals(status))return 0xffd44759;
        return dark?0xffd0d9ee:0xff61718b;
    }
    private int badgeBackground(String status){
        if("Plaćen".equals(status)||"Poslana".equals(status))return 0xffdcf8ec;
        if("Dospijeva".equals(status))return 0xfffff1d8;
        if("Neplaćen".equals(status)||"Dospio".equals(status))return 0xffffe8eb;
        return dark?0xff273953:0xffe9eef7;
    }

    private boolean matches(Store.Document d,String f){
        if("Skice".equals(f))return Store.DRAFT.equals(d.status);
        if("Plaćeni".equals(f))return Store.PAID.equals(d.status);
        if("Neplaćeni".equals(f))return Store.ISSUED.equals(d.status);
        if("Poslane".equals(f))return Store.SENT.equals(d.status);
        return true;
    }
    private void documentRow(Store.Document d){
        LinearLayout wrap=card();wrap.setTag(d);
        LinearLayout r=horizontal();
        TextView icon=label(Store.INVOICE.equals(d.type)?"▤":"▣",24,Store.INVOICE.equals(d.type)?BLUE:TEAL,true);
        r.addView(icon,lp(40,43));
        LinearLayout name=vertical();name.addView(label(d.number==null?"Nacrt #"+d.id:d.number,13,ink(),true));
        name.addView(label(d.clientName+"  •  "+d.issueDate,11,sub(),false));
        r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout amounts=vertical();amounts.setGravity(Gravity.END);
        amounts.addView(label(Money.euros(d.totalCents),13,ink(),true));
        amounts.addView(label(status(d.status),10,Store.PAID.equals(d.status)?TEAL:BLUE,false));
        r.addView(amounts);place(wrap,r);
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
        rowParams.topMargin=dp(9);
        body.addView(wrap,rowParams);
        wrap.setOnClickListener(v->showDocument(d.id));
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
        gap(target,12);target.addView(label(name,12,ink(),true));gap(target,6);place(target,field);
    }
    private void dateInput(EditText edit){
        edit.setFocusable(false);
        edit.setOnClickListener(v->{Calendar c=Calendar.getInstance();
            try{c.setTime(dateFormat.parse(edit.getText().toString()));}catch(Exception ignored){}
            new DatePickerDialog(this,(p,y,m,d)->edit.setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),
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
            draftNote=j.optString("notes");draftItems.clear();draftItems.addAll(loaded);
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
        draftDue=later(type.equals(Store.INVOICE)?15:30);draftSupply=draftIssue;draftNote="";editClientId=-1;
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
            draftIssue=d.issueDate;draftDue=d.dueDate;draftSupply=d.supplyDate==null||d.supplyDate.isEmpty()?d.issueDate:d.supplyDate;draftNote=d.notes;
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
        setup("new_doc",true);
        toolbar(Store.INVOICE.equals(editType)?"Novi račun":"Nova ponuda",
            "Izradite novi dokument",true);
        wizardSteps(1);
        List<Store.Client> clients=db.clients();

        LinearLayout basics=card();
        title(basics,"Osnovni podaci");gap(basics,12);
        String typeName=Store.INVOICE.equals(editType)?"računa":"ponude";
        basics.addView(label("Broj "+typeName,12,sub(),true));gap(basics,6);
        TextView sequence=label(editingDraftId>0?
            "Nacrt · broj nakon potvrde":"Dodjeljuje se pri potvrdi",13,ink(),false);
        pad(sequence,14,13);sequence.setBackground(outlined(panel()));
        place(basics,sequence);
        if(clients.isEmpty()){
            gap(basics,12);
            basics.addView(label("Nema klijenata. Dodajte klijenta za nastavak.",12,sub(),false));
            gap(basics,12);button(basics,"＋ Dodaj klijenta",this::addClient);
            place(body,basics);return;
        }
        String[] names=new String[clients.size()];int current=0;
        for(int i=0;i<clients.size();i++){
            names[i]=clients.get(i).name;
            if(clients.get(i).id==editClientId)current=i;
        }
        gap(basics,13);basics.addView(label("Klijent",12,sub(),true));gap(basics,6);
        LinearLayout choose=horizontal();choose.setGravity(Gravity.CENTER_VERTICAL);
        choose.setBackground(outlined(panel()));choose.setPadding(dp(12),dp(5),dp(12),dp(5));
        Spinner selector=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(
            this,android.R.layout.simple_spinner_dropdown_item,names);
        selector.setAdapter(adapter);selector.setSelection(current);
        choose.addView(selector,new LinearLayout.LayoutParams(0,dp(42),1));
        choose.addView(label("›",23,sub(),false));
        place(basics,choose);
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                editClientId=clients.get(pos).id;saveWorkingDraft();
            }
        });
        EditText issued=field("Datum izdavanja",draftIssue);
        dateInput(issued);fieldLabel(basics,"Datum izdavanja",issued);
        EditText due=field("Datum dospijeća / valjanosti",draftDue);
        dateInput(due);fieldLabel(basics,"Dospijeće / vrijedi do",due);
        String method=db.setting("payment_method").trim();
        gap(basics,13);basics.addView(label("Način plaćanja",12,sub(),true));gap(basics,6);
        LinearLayout paying=horizontal();paying.setGravity(Gravity.CENTER_VERTICAL);
        paying.setBackground(outlined(panel()));paying.setPadding(dp(13),dp(14),dp(13),dp(14));
        paying.addView(label(method.isEmpty()?"Odaberite u profilu tvrtke":method,13,ink(),false),
            new LinearLayout.LayoutParams(0,-2,1));
        paying.addView(label("›",20,sub(),false));
        paying.setContentDescription("Uredi način plaćanja u profilu tvrtke");
        paying.setOnClickListener(v->{
            saveWorkingDraft();company();
        });
        place(basics,paying);
        place(body,basics);gap(body,13);

        LinearLayout additional=card();
        title(additional,"Dodatne informacije");
        EditText supply=field("Datum isporuke ili obavljene usluge",draftSupply);
        dateInput(supply);
        fieldLabel(additional,"Datum isporuke / usluge",supply);
        EditText notes=field("Neobavezna napomena",draftNote);
        fieldLabel(additional,"Napomena",notes);
        place(body,additional);gap(body,18);

        watchDraft(issued,()->draftIssue=issued.getText().toString());
        watchDraft(due,()->draftDue=due.getText().toString());
        watchDraft(supply,()->draftSupply=supply.getText().toString());
        watchDraft(notes,()->draftNote=notes.getText().toString());
        stickyPrimary("Nastavi na stavke  ›",()->{
            editClientId=clients.get(selector.getSelectedItemPosition()).id;
            draftIssue=issued.getText().toString();
            draftDue=due.getText().toString();
            draftSupply=supply.getText().toString();
            draftNote=notes.getText().toString();
            try{IsoDates.validate(draftIssue,draftDue);
                if(!IsoDates.valid(draftSupply))throw new IllegalArgumentException(
                    "Neispravan datum isporuke / usluge");
                if(saveWorkingDraft())editItemsScreen();
            }catch(Exception error){error(error);}
        });
    }
    private void editItemsScreen(){
        setup("edit_items",true);
        toolbar(Store.INVOICE.equals(editType)?"Stavke računa":"Stavke ponude",
            "2. korak od 3",true);
        wizardSteps(2);
        if(draftItems.isEmpty())caption(body,"Dodajte usluge ili artikle u dokument.");
        for(int index=0;index<draftItems.size();index++){
            Store.Item item=draftItems.get(index);
            final int selected=index;
            LinearLayout c=card();
            LinearLayout header=horizontal();header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(label(item.name,14,ink(),true),
                new LinearLayout.LayoutParams(0,-2,1));
            TextView edit=label("✎",23,sub(),false);
            edit.setGravity(Gravity.CENTER);
            edit.setContentDescription("Uredi stavku "+item.name);
            header.addView(edit,lp(42,42));
            edit.setOnClickListener(v->editLine(selected));
            place(c,header);gap(c,3);
            c.addView(label(Money.decimalQuantity(item.qtyMilli)+" × "+
                Money.euros(item.unitCents)+" · PDV "+item.taxRate+"%",11,sub(),false));
            gap(c,10);
            LinearLayout bottom=horizontal();
            bottom.addView(label(Money.euros(Math.addExact(item.net(),item.tax())),
                16,ink(),true),new LinearLayout.LayoutParams(0,-2,1));
            TextView delete=label("Ukloni",11,RED,true);
            delete.setContentDescription("Ukloni stavku "+item.name);
            bottom.addView(delete);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Ukloniti stavku?")
                .setMessage(item.name+" bit će uklonjena samo iz radnog nacrta.")
                .setNegativeButton("Odustani",null)
                .setPositiveButton("Ukloni",(dialog,which)->{
                    Store.Item removed=draftItems.remove(selected);
                    if(!saveWorkingDraft()){
                        draftItems.add(selected,removed);
                        return;
                    }
                    editItemsScreen();
                }).show());
            place(c,bottom);place(body,c);gap(body,9);
        }
        TextView add=label("＋  Dodaj artikl ili uslugu",13,BLUE,true);
        add.setGravity(Gravity.CENTER);add.setContentDescription("Dodaj stavku dokumenta");
        GradientDrawable outline=shape(dark?0xff1b2b4b:0xffeef0ff,15);
        outline.setStroke(dp(1),0xff97a1ff,dp(5),dp(4));
        add.setBackground(outline);add.setMinimumHeight(dp(53));
        add.setOnClickListener(v->addLine());
        place(body,add);gap(body,14);

        long net=0,tax=0;
        for(Store.Item item:draftItems){
            net=Math.addExact(net,item.net());tax=Math.addExact(tax,item.tax());
        }
        LinearLayout summary=card();
        title(summary,Store.INVOICE.equals(editType)?"Sažetak računa":"Sažetak ponude");
        gap(summary,14);
        amountRow(summary,"Osnovica",Money.euros(net),false);
        gap(summary,12);
        amountRow(summary,"PDV",Money.euros(tax),false);
        gap(summary,12);
        View line=new View(this);line.setBackgroundColor(border());
        summary.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        gap(summary,15);
        amountRow(summary,"UKUPNO",Money.euros(Math.addExact(net,tax)),true);
        place(body,summary);gap(body,12);
        caption(body,"Iznosi se automatski računaju iz unesenih stavki i poreznih stopa.");
        gap(body,13);
        secondary(body,"‹ Natrag na podatke",this::editDocumentScreen);
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
        EditText price=field("Jedinična cijena, EUR",editing?
            String.format(Locale.forLanguageTag("hr-HR"),"%.2f",current.unitCents/100.0):"0,00");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] rates={"25%","13%","5%","0%"};
        Spinner tax=new Spinner(this);
        tax.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates));
        if(editing)for(int i=0;i<rates.length;i++)
            if(rates[i].equals(current.taxRate+"%"))tax.setSelection(i);
        fieldLabel(form,"Stavka",name);
        List<Store.Item> catalog=db.products();
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
                    price.setText(String.format(Locale.forLanguageTag("hr-HR"),
                        "%.2f",item.unitCents/100.0));
                    for(int j=0;j<rates.length;j++)
                        if(rates[j].equals(item.taxRate+"%"))tax.setSelection(j);
                }
            });
            gap(form,12);
            form.addView(label("Iz kataloga",12,ink(),true));gap(form,6);
            place(form,product);
        }
        fieldLabel(form,"Količina",qty);
        fieldLabel(form,"Jedinična cijena bez PDV-a",price);
        gap(form,11);form.addView(label("Stopa PDV-a",12,ink(),true));
        place(form,tax);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing?"Uredi stavku":"Nova stavka")
            .setView(form).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null).create();
        dialog.setOnShowListener(ignored->
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
                    item.taxRate=Integer.parseInt(rates[
                        tax.getSelectedItemPosition()].replace("%",""));
                    Math.addExact(item.net(),item.tax());
                    if(editing)draftItems.set(index,item);else draftItems.add(item);
                    if(!saveWorkingDraft()){
                        if(editing)draftItems.set(index,current);
                        else draftItems.remove(draftItems.size()-1);
                        return;
                    }
                    dialog.dismiss();editItemsScreen();
                }catch(Exception error){error(error);}
            }));
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
        summary.addView(label("Isporuka / usluga: "+draftSupply,11,sub(),false));gap(summary,16);
        for(Store.Item i:draftItems){summary.addView(label(i.name+"  ·  "+Money.euros(i.net()+i.tax()),12,ink(),false));gap(summary,8);}
        long net=0,tax=0;for(Store.Item i:draftItems){net+=i.net();tax+=i.tax();}
        gap(summary,15);summary.addView(label("Osnovica: "+Money.euros(net),12,sub(),false));
        summary.addView(label("PDV: "+Money.euros(tax),12,sub(),false));gap(summary,9);
        summary.addView(label("UKUPNO: "+Money.euros(net+tax),24,BLUE,true));place(body,summary);
        gap(body,16);button(body,"Spremi nacrt",()->saveNew(false));
        gap(body,8);secondary(body,"Potvrdi dokument",()->confirmWarning(()->saveNew(true)));
        gap(body,8);secondary(body,"‹ Uredi stavke",this::editItemsScreen);
        gap(body,15);caption(body,"Potvrda dodjeljuje broj dokumenta. Fiskalizacija i slanje strukturiranih eRačuna nisu podržani.");
    }
    private void saveNew(boolean confirm){
        try{
            long id;
            if(editingDraftId>0){
                id=editingDraftId;
                db.updateDraft(id,editClientId,draftIssue,draftDue,draftSupply,draftNote,draftItems);
            }else {
                id=db.createDraft(editType,editClientId,draftIssue,draftDue,draftSupply,draftNote,draftItems);
                editingDraftId=id;saveWorkingDraft();
            }
            if(confirm)db.confirm(id);
            RecoveryVault.clearWorkingDraft(this);
            editingDraftId=-1;draftItems.clear();showDocument(id);
        }catch(Exception e){error(e);}
    }
    private void confirmWarning(Runnable yes){
        new AlertDialog.Builder(this).setTitle("Potvrda dokumenta")
            .setMessage("Dodijelit će se konačni broj dokumenta. Ova aplikacija ne provodi fiskalizaciju niti šalje eRačun. Potvrdite samo ako to odgovara vašem poslovnom procesu.")
            .setNegativeButton("Odustani",null).setPositiveButton("Potvrdi",(d,w)->yes.run()).show();
    }
    private void showDocument(long id){
        currentDocumentId=id;
        Store.Document d;
        try{d=db.document(id);}catch(Exception e){error(e);navigate("home");return;}
        setup("detail",true);
        toolbar(Store.INVOICE.equals(d.type)?"Pregled računa":"Pregled ponude","Provjerite podatke dokumenta",true);
        backupAlert();

        LinearLayout identity=card();
        LinearLayout header=horizontal();
        ImageView glyph=icon(Store.INVOICE.equals(d.type)?R.drawable.ic_invoice:R.drawable.ic_quote,BLUE);
        glyph.setPadding(dp(12),dp(12),dp(12),dp(12));
        background(glyph,dark?0xff293c63:0xffe9edff,13);
        header.addView(glyph,lp(48,48));
        LinearLayout identityText=vertical();pad(identityText,12,0);
        identityText.addView(label(d.number==null?"Nacrt #"+d.id:d.number,19,ink(),true));
        identityText.addView(label(d.clientName,12,sub(),false));
        header.addView(identityText,new LinearLayout.LayoutParams(0,-2,1));
        place(identity,header);gap(identity,12);
        TextView state=label(status(d.status),12,Store.PAID.equals(d.status)?TEAL:
            Store.DRAFT.equals(d.status)?sub():BLUE,true);
        place(identity,state);
        gap(identity,12);
        LinearLayout amounts=horizontal();
        LinearLayout value=vertical();
        value.addView(label("UKUPNO",10,sub(),true));
        value.addView(label(Money.euros(d.totalCents),25,BLUE,true));
        amounts.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        if(Store.INVOICE.equals(d.type)){
            LinearLayout due=vertical();due.setGravity(Gravity.RIGHT);
            due.addView(label("PREOSTALO",10,sub(),true));
            due.addView(label(Money.euros(d.outstanding()),16,ink(),true));
            amounts.addView(due);
        }
        place(identity,amounts);place(body,identity);
        gap(body,14);

        // Rendered from the same native PDF used by Save/Share.
        LinearLayout previewCard=card();
        LinearLayout previewHeader=horizontal();
        previewHeader.addView(label("Pregled PDF dokumenta",14,ink(),true),
            new LinearLayout.LayoutParams(0,-2,1));
        previewHeader.addView(label("A4",11,sub(),true));
        place(previewCard,previewHeader);gap(previewCard,12);
        try{
            View preview=DocumentPreview.firstPage(this,db,d);
            previewCard.addView(preview);
            gap(previewCard,7);
            previewCard.addView(label("Prikazana je prva stranica; puni dokument otvara se izvozom PDF-a.",10,sub(),false));
        }catch(Exception e){
            previewCard.addView(label("Pregled nije dostupan. Podaci dokumenta su i dalje sačuvani.",12,sub(),false));
        }
        place(body,previewCard);gap(body,13);
        button(body,"Spremi PDF",()->savePdf(id));gap(body,9);
        secondary(body,"Podijeli PDF",()->sharePdf(id));

        section("Podaci dokumenta");
        LinearLayout details=card();
        details.addView(label("Izdavatelj: "+d.issuerName,12,ink(),true));gap(details,6);
        details.addView(label("Klijent: "+d.clientName,12,ink(),false));
        if(d.clientOib!=null&&!d.clientOib.isEmpty())details.addView(label("OIB: "+d.clientOib,11,sub(),false));
        if(d.clientVatId!=null&&!d.clientVatId.isEmpty())details.addView(label("PDV ID: "+d.clientVatId,11,sub(),false));
        if(d.clientAddress!=null&&!d.clientAddress.isEmpty())details.addView(label("Adresa: "+d.clientAddress,11,sub(),false));
        gap(details,8);
        details.addView(label("Datum izdavanja: "+d.issueDate,12,ink(),false));
        details.addView(label("Rok dospijeća: "+d.dueDate,12,ink(),false));
        if(d.supplyDate!=null&&!d.supplyDate.isEmpty())
            details.addView(label("Datum usluge: "+d.supplyDate,12,ink(),false));
        place(body,details);

        section("Stavke i obračun");
        LinearLayout list=card();
        for(Store.Item item:d.items){
            LinearLayout line=horizontal();
            LinearLayout desc=vertical();
            desc.addView(label(item.name,13,ink(),true));
            desc.addView(label(Money.decimalQuantity(item.qtyMilli)+" × "+Money.euros(item.unitCents)+
                " · PDV "+item.taxRate+"%",11,sub(),false));
            line.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
            line.addView(label(Money.euros(Math.addExact(item.net(),item.tax())),12,ink(),true));
            place(list,line);gap(list,11);
        }
        list.addView(label("Osnovica: "+Money.euros(d.netCents),12,sub(),false));
        list.addView(label("PDV: "+Money.euros(d.taxCents),12,sub(),false));
        gap(list,8);list.addView(label("Ukupno: "+Money.euros(d.totalCents),19,BLUE,true));
        place(body,list);gap(body,14);

        if(Store.DRAFT.equals(d.status)){
            secondary(body,"Uredi nacrt",()->resumeSavedDraft(id));gap(body,9);
            secondary(body,"Potvrdi dokument",()->confirmWarning(()->{
                try{db.confirm(id);showDocument(id);}catch(Exception e){error(e);}
            }));
        }
        if(Store.INVOICE.equals(d.type)&&Store.ISSUED.equals(d.status)&&d.outstanding()>0){
            gap(body,9);secondary(body,"Evidentiraj uplatu",()->payDialog(id));
        }
        if(Store.QUOTE.equals(d.type)&&Store.SENT.equals(d.status)){
            gap(body,9);secondary(body,"Pretvori u račun",()->{
                try{long invoice=db.quoteToInvoice(id);showDocument(invoice);
                    toast("Stvoren je nacrt računa");}catch(Exception e){error(e);}
            });
        }
        gap(body,18);
        caption(body,"PDF nije strukturirani eRačun niti potvrda fiskalizacije.");
    }
    private void payDialog(long id){
        Store.Document d;
        try{d=db.document(id);}catch(Exception e){error(e);return;}
        if(!Store.INVOICE.equals(d.type)||!Store.ISSUED.equals(d.status)||d.outstanding()<=0){
            toast("Za ovaj račun nije moguće unijeti uplatu");return;
        }
        LinearLayout form=vertical();pad(form,17,7);
        form.addView(label("Preostalo za naplatu: "+Money.euros(d.outstanding()),14,ink(),true));
        EditText amount=field("Iznos uplate",
            String.format(Locale.forLanguageTag("hr-HR"),"%.2f",d.outstanding()/100.0));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        fieldLabel(form,"Iznos u EUR",amount);
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
    private void clients(){
        setup("clients",true);
        toolbar("Klijenti","Kupci i poslovni partneri",false);
        EditText search=field("Pretraži klijente…","");
        place(body,search);gap(body,14);
        List<Store.Client> clients=db.clients();
        place(body,label("Ukupno klijenata: "+clients.size(),12,sub(),false));
        gap(body,11);
        LinearLayout results=vertical();place(body,results);
        for(Store.Client client:clients){
            LinearLayout card=card();
            LinearLayout line=horizontal();
            String initial=client.name.substring(0,Math.min(1,client.name.length()))
                .toUpperCase(Locale.forLanguageTag("hr-HR"));
            TextView icon=label(initial,19,BLUE,true);
            icon.setGravity(Gravity.CENTER);
            background(icon,dark?0xff293b63:0xffeaf0ff,11);
            line.addView(icon,lp(43,43));
            LinearLayout details=vertical();pad(details,12,0);
            details.addView(label(client.name,13,ink(),true));gap(details,3);
            String secondary=!client.oib.isEmpty()?"OIB: "+client.oib:
                (!client.address.isEmpty()?client.address:client.email);
            details.addView(label(secondary,11,sub(),false));
            line.addView(details,new LinearLayout.LayoutParams(0,-2,1));
            line.addView(label("›",25,sub(),false));
            place(card,line);
            LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(-1,-2);
            itemLp.bottomMargin=dp(9);
            results.addView(card,itemLp);
            card.setTag((client.name+" "+client.oib+" "+client.address+" "+
                client.email+" "+client.phone).toLowerCase(Locale.forLanguageTag("hr-HR")));
            card.setOnClickListener(v->clientDetails(client));
        }
        TextView empty=label(clients.isEmpty()?"Još nemate klijenata.":"Nema podudarnih klijenata.",
            12,sub(),false);
        place(results,empty);empty.setVisibility(clients.isEmpty()?View.VISIBLE:View.GONE);
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence value,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence value,int start,int before,int count){
                String query=value.toString().trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
                int visible=0;
                for(int i=0;i<results.getChildCount();i++){
                    View row=results.getChildAt(i);
                    if(!(row.getTag() instanceof String))continue;
                    boolean match=((String)row.getTag()).contains(query);
                    row.setVisibility(match?View.VISIBLE:View.GONE);
                    if(match)visible++;
                }
                empty.setVisibility(visible==0?View.VISIBLE:View.GONE);
            }
            @Override public void afterTextChanged(android.text.Editable edit){}
        });
        floatingAction("Dodaj klijenta",this::addClient);
    }
    private void clientDetails(Store.Client c){
        setup("client_detail",true);
        toolbar("Detalji klijenta","Profil i povijest poslovanja",true);
        String initials="";
        for(String part:c.name.trim().split("\\s+")){
            if(!part.isEmpty()&&initials.length()<2)initials+=part.substring(0,1).toUpperCase(Locale.forLanguageTag("hr-HR"));
        }
        LinearLayout identity=vertical();identity.setGravity(Gravity.CENTER);
        TextView logo=label(initials.isEmpty()?"K":initials,25,BLUE,true);
        logo.setGravity(Gravity.CENTER);logo.setBackground(shape(dark?0xff263759:0xffe8edff,21));
        LinearLayout.LayoutParams logoLp=lp(70,70);logoLp.gravity=Gravity.CENTER;
        identity.addView(logo,logoLp);gap(identity,13);
        TextView companyName=label(c.name,20,ink(),true);companyName.setGravity(Gravity.CENTER);
        place(identity,companyName);gap(identity,5);
        TextView caption=label("Poslovni partner · "+(c.address.isEmpty()?"Hrvatska":c.address),11,sub(),false);
        caption.setGravity(Gravity.CENTER);caption.setMaxLines(2);place(identity,caption);gap(identity,12);
        TextView active=label("Aktivan",11,0xff15996d,true);
        active.setGravity(Gravity.CENTER);
        active.setBackground(shape(dark?0xff204737:0xffdcf7eb,15));pad(active,11,6);
        LinearLayout.LayoutParams activeLp=lp(-2,-2);activeLp.gravity=Gravity.CENTER;
        identity.addView(active,activeLp);
        place(body,identity);gap(body,23);

        long paid=0;int invoiceCount=0;
        List<Store.Document> history=new ArrayList<>();
        for(String t:new String[]{Store.INVOICE,Store.QUOTE}){
            for(Store.Document d:db.documents(t)){
                if(d.clientId!=c.id)continue;
                history.add(d);
                if(Store.INVOICE.equals(t)){
                    invoiceCount++;
                    paid=Math.addExact(paid,d.paidCents);
                }
            }
        }
        LinearLayout indicators=horizontal();
        LinearLayout invoices=clientIndicator("Računi",String.valueOf(invoiceCount),"▤","Ukupno",BLUE);
        LinearLayout receipt=clientIndicator("Naplaćeno",Money.euros(paid),"✓","Ukupno",TEAL);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(132),1);
        left.setMargins(0,0,dp(6),0);indicators.addView(invoices,left);
        LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(132),1);
        right.setMargins(dp(6),0,0,0);indicators.addView(receipt,right);
        place(body,indicators);
        section("Podaci o klijentu");
        LinearLayout details=card();
        clientField(details,"Naziv",c.name);
        clientField(details,"Adresa",c.address);
        clientField(details,"OIB",c.oib);
        clientField(details,"PDV ID",c.vatId);
        clientField(details,"E-pošta",c.email);
        clientField(details,"Telefon",c.phone);
        place(body,details);
        section("Posljednji dokumenti");
        history.sort((a,b)->Long.compare(b.id,a.id));
        int count=0;
        for(Store.Document document:history){
            if(count++>=3)break;
            documentRow(document);
        }
        if(count==0)caption(body,"Još nema dokumenata za ovog klijenta.");
        gap(body,12);
        secondary(body,"Uredi podatke klijenta",()->showClientEditor(c));
        gap(body,8);
        button(body,"＋ Izradi račun za klijenta",()->newDocumentForClient(c.id));
        gap(body,18);
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
    private void clientField(LinearLayout target,String label,String value){
        if(value==null||value.isEmpty())return;
        LinearLayout row=horizontal();
        TextView name=label(label,11,sub(),false);
        row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView current=label(value,11,ink(),true);
        current.setMaxLines(3);
        current.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(current,new LinearLayout.LayoutParams(0,-2,1.7f));
        place(target,row);gap(target,12);
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
        EditText oib=field("OIB",editing?saved.oib:"");
        EditText vat=field("PDV ID (neobavezno)",editing?saved.vatId:"");
        EditText address=field("Adresa i mjesto",editing?saved.address:"");
        EditText email=field("E-mail",editing?saved.email:"");
        EditText phone=field("Telefon",editing?saved.phone:"");
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        fieldLabel(form,"Naziv klijenta",name);
        fieldLabel(form,"OIB (11 znamenaka)",oib);
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
                    if(!id.isEmpty()&&!Oib.valid(id)){
                        oib.setError("OIB nije valjan");oib.requestFocus();return;
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
    private void catalog(){
        setup("catalog",true);
        toolbar("Artikli i usluge","Katalog proizvoda i usluga",true);
        List<Store.Item> products=db.products();
        EditText search=field("Pretražite artikle ili usluge…","");
        place(body,search);gap(body,13);
        LinearLayout meta=horizontal();
        meta.addView(label("Ukupno stavki: "+products.size(),12,sub(),false),
            new LinearLayout.LayoutParams(0,-2,1));
        place(body,meta);gap(body,9);

        LinearLayout rows=vertical();place(body,rows);
        for(Store.Item item:products){
            LinearLayout c=card();
            LinearLayout row=horizontal();
            TextView symbol=label("▤",16,BLUE,true);symbol.setGravity(Gravity.CENTER);
            symbol.setBackground(shape(dark?0xff253a5f:0xffeaf0ff,12));
            row.addView(symbol,lp(42,42));
            LinearLayout text=vertical();pad(text,11,0);
            text.addView(label(item.name,13,ink(),true));
            text.addView(label("Cijena bez PDV-a · "+item.taxRate+"% PDV",11,sub(),false));
            row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(label(Money.euros(item.unitCents),12,ink(),true));
            place(c,row);
            LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(-1,-2);
            itemLp.bottomMargin=dp(9);
            rows.addView(c,itemLp);
            c.setTag(item.name.toLowerCase(Locale.forLanguageTag("hr-HR")));
            c.setOnClickListener(v->editProduct(item));
        }
        if(products.isEmpty())caption(body,"Katalog je prazan. Dodajte prvi artikl ili uslugu.");
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){
                String filter=text.toString().trim().toLowerCase(Locale.forLanguageTag("hr-HR"));
                for(int i=0;i<rows.getChildCount();i++){
                    View item=rows.getChildAt(i);
                    Object tag=item.getTag();
                    if(tag instanceof String)item.setVisibility(((String)tag).contains(filter)?View.VISIBLE:View.GONE);
                }
            }
            @Override public void afterTextChanged(android.text.Editable edit){}
        });
        floatingAction("Dodaj artikl ili uslugu",()->editProduct(null));
    }
    private void addProduct(){editProduct(null);}
    private void editProduct(Store.Item current){
        boolean editing=current!=null;
        LinearLayout form=vertical();pad(form,18,5);
        EditText name=field("Naziv artikla ili usluge",editing?current.name:"");
        EditText price=field("Jedinična cijena (EUR)",editing?String.format(Locale.forLanguageTag("hr-HR"),"%.2f",current.unitCents/100.0):"0,00");
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] rates={"25%","13%","5%","0%"};
        Spinner vat=new Spinner(this);
        vat.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates));
        if(editing)for(int i=0;i<rates.length;i++)if(rates[i].equals(current.taxRate+"%"))vat.setSelection(i);
        fieldLabel(form,"Naziv",name);fieldLabel(form,"Cijena bez PDV-a",price);
        gap(form,9);form.addView(label("Stopa PDV-a",12,ink(),true));place(form,vat);
        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle(editing?"Uredi artikl ili uslugu":"Novi artikl ili usluga")
            .setView(form).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",null).create();
        dialog.setOnShowListener(ignore->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setOnClickListener(button->{
                try {
                    int tax=Integer.parseInt(rates[vat.getSelectedItemPosition()].replace("%",""));
                    long cents=Money.cents(price.getText().toString());
                    if(editing)db.updateProduct(current.id,name.getText().toString(),cents,tax);
                    else db.saveProduct(name.getText().toString(),cents,tax);
                    dialog.dismiss();catalog();
                }catch(Exception e){error(e);}
            }));
        dialog.show();
    }
    private void reports(){
        setup("reports",true);
        toolbar("Izvještaji","Statistika i analiza poslovanja",true);

        String month=new SimpleDateFormat("MMMM yyyy.",Locale.forLanguageTag("hr-HR")).format(reportMonth.getTime());
        String yearMonth=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(reportMonth.getTime());
        Calendar now=Calendar.getInstance();
        boolean currentMonth=reportMonth.get(Calendar.YEAR)==now.get(Calendar.YEAR)&&
            reportMonth.get(Calendar.MONTH)==now.get(Calendar.MONTH);
        LinearLayout period=horizontal();
        TextView picker=label("▣  "+month+"  ›",13,ink(),true);
        pad(picker,13,11);picker.setBackground(outlined(panel()));
        period.addView(picker,new LinearLayout.LayoutParams(0,-2,1));
        picker.setOnClickListener(v->{
            DatePickerDialog dialog=new DatePickerDialog(this,(view,year,m,day)->{
                reportMonth.set(year,m,1);reports();
            },reportMonth.get(Calendar.YEAR),reportMonth.get(Calendar.MONTH),1);
            dialog.show();
        });
        TextView current=label(currentMonth?"Ovaj mjesec":"Odabrani",11,BLUE,true);
        current.setGravity(Gravity.CENTER);current.setBackground(shape(dark?0xff27385f:0xffe9edff,16));
        LinearLayout.LayoutParams selected=lp(102,40);selected.setMargins(dp(10),0,0,0);
        period.addView(current,selected);
        current.setOnClickListener(v->{reportMonth.setTimeInMillis(System.currentTimeMillis());reports();});
        place(body,period);gap(body,14);

        LinearLayout revenue=card();pad(revenue,18,18);
        revenue.addView(label("Ukupno fakturirano",12,sub(),false));gap(revenue,10);
        revenue.addView(label(Money.euros(db.invoicedCentsMonth(yearMonth)),29,ink(),true));gap(revenue,8);
        revenue.addView(label("Potvrđeni računi · bez nacrta",11,sub(),false));
        place(body,revenue);gap(body,14);

        int invoiceCount=0,paid=0;
        for(Store.Document d:db.documents(Store.INVOICE)){
            if(Store.DRAFT.equals(d.status)||!d.issueDate.startsWith(yearMonth))continue;
            invoiceCount++;
            if(Store.PAID.equals(d.status))paid++;
        }
        LinearLayout metrics=horizontal();
        reportMetric(metrics,"Izdani računi",String.valueOf(invoiceCount),"▤",BLUE,0);
        reportMetric(metrics,"Plaćeni računi",String.valueOf(paid),"✓",TEAL,1);
        place(body,metrics);gap(body,14);

        LinearLayout chart=card();pad(chart,18,17);
        LinearLayout heading=horizontal();
        heading.addView(label("Kretanje naplate",16,ink(),true),new LinearLayout.LayoutParams(0,-2,1));
        heading.addView(label("Posljednjih 6 mj.",10,sub(),false));
        place(chart,heading);gap(chart,12);
        chart.addView(new RevenueBars(this),lp(-1,194));place(body,chart);gap(body,14);

        LinearLayout payout=card();
        title(payout,"Pregled naplate");gap(payout,18);
        clientField(payout,"Naplaćeno",Money.euros(db.receivedCents()));
        clientField(payout,"Nenaplaćeno",Money.euros(db.outstandingCents()));
        place(body,payout);gap(body,12);
        secondary(body,"Pregled svih uplata",this::payments);
        gap(body,18);
    }
    private void reportMetric(LinearLayout row,String caption,String value,String glyph,int accent,int index){
        LinearLayout c=card();
        TextView symbol=label(glyph,19,accent,true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(accent==TEAL?(dark?0xff20534b:0xffdcf8f4):
            (dark?0xff263a65:0xffe8edff),12));
        c.addView(symbol,lp(39,39));gap(c,9);
        c.addView(label(caption,11,sub(),false));gap(c,3);
        c.addView(label(value,25,ink(),true));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(114),1);
        if(index==0)lp.rightMargin=dp(6);else lp.leftMargin=dp(6);
        row.addView(c,lp);
    }
    private void payments(){
        setup("payments",true);
        toolbar("Naplata","Evidencija uplata i potraživanja",true);
        List<Store.Payment> history=db.payments();
        String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(new java.util.Date());
        int monthlyCount=0;long monthlyAmount=0;
        for(Store.Payment p:history)if(p.paidOn.startsWith(month)){
            monthlyCount++;monthlyAmount=Math.addExact(monthlyAmount,p.amountCents);
        }
        LinearLayout hero=vertical();pad(hero,19,18);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xff173b69,0xff102844});
        gradient.setCornerRadius(dp(19));hero.setBackground(gradient);
        hero.addView(label("Ukupno naplaćeno",12,0xffc7d9ec,false));gap(hero,10);
        hero.addView(label(Money.euros(db.receivedCents()),29,WHITE,true));gap(hero,13);
        hero.addView(label("✓  "+monthlyCount+" uplata ovaj mjesec · "+Money.euros(monthlyAmount),
            11,0xff73dfd0,false));
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
        place(body,filters);gap(body,17);
        LinearLayout heading=horizontal();
        heading.addView(label(paymentFilter==2?"Neplaćeni računi":"Nedavne uplate",17,ink(),true),
            new LinearLayout.LayoutParams(0,-2,1));
        if(paymentFilter!=2){
            TextView export=label("Izvoz CSV",11,BLUE,true);
            heading.addView(export);
            export.setOnClickListener(v->{
                pendingPaymentsCsvMonth=paymentFilter==1?month:null;
                String suffix=pendingPaymentsCsvMonth==null?"sve":pendingPaymentsCsvMonth;
                createFile(104,"text/csv","Fakturiva-uplate-"+suffix+".csv");
            });
        }
        place(body,heading);gap(body,11);

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
                right.addView(label(Money.euros(d.outstanding()),13,BLUE,true));
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
                description.addView(label(p.clientName,13,ink(),true));
                description.addView(label(p.invoiceNumber+" · "+p.paidOn,11,sub(),false));
                row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
                row.addView(label("+"+Money.euros(p.amountCents),12,TEAL,true));
                place(card,row);
                row.setOnClickListener(v->showDocument(p.documentId));
            }
            if(n==0){pad(card,16,16);place(card,label(
                paymentFilter==1?"Nema evidentiranih uplata ovaj mjesec.":"Nema evidentiranih uplata.",
                12,sub(),false));}
        }
        place(body,card);gap(body,15);
        caption(body,"Naplate se prikazuju prema stvarno evidentiranim uplatama.");
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
        toolbar("Postavke","Upravljajte svojom aplikacijom",false);
        LinearLayout brand=horizontal();pad(brand,15,15);
        brand.setBackground(shape(NAVY,19));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.brand_mark);
        brand.addView(mark,lp(57,57));
        LinearLayout brandText=vertical();pad(brandText,11,0);
        brandText.addView(label("Fakturiva",20,WHITE,true));
        brandText.addView(label("Postavke aplikacije",12,0xffb6cbe2,false));
        brand.addView(brandText,new LinearLayout.LayoutParams(0,-2,1));
        brand.addView(label("›",23,0xffb6cbe2,true));
        place(body,brand);gap(body,14);

        LinearLayout options=card();pad(options,0,2);
        place(body,options);
        settingRow(options,"▤","Tvrtka i poslovni podaci","Naziv, adresa, OIB, IBAN",this::company);
        settingRow(options,"▣","Artikli i usluge","Katalog proizvoda i cijene",this::catalog);
        settingRow(options,"▥","Izvještaji","Analitika računa i naplate",this::reports);
        settingRow(options,"⇄","eRačuni i fiskalizacija","Trenutačno stanje integracije",this::eracun);
        settingRow(options,"♢","Izvoz i sigurnosne kopije","Zaštita i oporavak dokumenata",this::backupScreen);
        settingRow(options,"☼","Izgled aplikacije",dark?"Tamna tema aktivna":"Svijetla tema aktivna",()->{
            dark=!dark;prefs.edit().putBoolean("dark",dark).apply();settings();
        });
        gap(body,17);
        caption(body,"Fakturiva "+getPackageVersion()+" · Brendigo");
        gap(body,19);
    }
    private void settingRow(LinearLayout target,String symbol,String name,String subtitle,Runnable action){
        if(target.getChildCount()>0){
            View divider=new View(this);divider.setBackgroundColor(border());
            LinearLayout.LayoutParams dividerLp=lp(-1,1);dividerLp.setMargins(dp(12),0,dp(12),0);
            target.addView(divider,dividerLp);
        }
        LinearLayout row=horizontal();pad(row,13,13);
        TextView mark=label(symbol,19,BLUE,true);
        mark.setGravity(Gravity.CENTER);mark.setBackground(shape(dark?0xff293a60:0xffecf0ff,12));
        row.addView(mark,lp(38,38));
        LinearLayout info=vertical();pad(info,12,0);
        info.addView(label(name,13,ink(),true));gap(info,3);
        info.addView(label(subtitle,11,sub(),false));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(label("›",22,sub(),false));
        place(target,row);
        row.setOnClickListener(v->action.run());
    }
    private void company(){
        setup("company",true);toolbar("Profil tvrtke","Podaci za dokumente",true);
        LinearLayout hero=horizontal();pad(hero,15,15);
        background(hero,dark?0xff26365a:0xffe9edff,18);
        ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.brand_mark);
        hero.addView(brand,lp(60,60));
        LinearLayout heroText=vertical();pad(heroText,10,0);
        String companyName=db.setting("name").trim();
        heroText.addView(label(companyName.isEmpty()?"Moja tvrtka / obrt":companyName,15,ink(),true));
        gap(heroText,5);
        heroText.addView(label("Profil izdavatelja računa",11,sub(),false));
        hero.addView(heroText,new LinearLayout.LayoutParams(0,-2,1));
        place(body,hero);gap(body,14);
        caption(body,"Podaci se prikazuju na novim računima i ponudama. Potvrđeni dokumenti trajno čuvaju podatke iz trenutka potvrde.");
        gap(body,14);
        LinearLayout form=card();
        title(form,"Osnovni podaci");
        String[] keys={"name","oib","vat_id","address","postal_code","city","country","email","phone","website","iban","bank_name","swift","registry","business_unit","operator","payment_method","payment_reference","tax_note"};
        String[] labels={"Naziv tvrtke / obrta ili ime i prezime","OIB (11 znamenaka)","PDV identifikacijski broj (ako se primjenjuje)","Ulica i kućni broj / adresa","Poštanski broj","Grad / mjesto","Država","E-mail","Telefon","Web stranica","IBAN","Naziv banke","BIC / SWIFT","Registarski podatak (npr. MBS)","Oznaka poslovnog prostora","Oznaka operatera","Način plaćanja","Poziv na broj / model plaćanja","Porezni status ili zakonska napomena"};
        EditText[] fields=new EditText[keys.length];
        for(int i=0;i<keys.length;i++){
            if(i==3||i==10||i==13||i==18){gap(form,14);}
            if(i==3)title(form,"Sjedište i kontakt");
            if(i==10)title(form,"Plaćanje i banka");
            if(i==13)title(form,"Poslovni i porezni podaci");
            fields[i]=field(labels[i],db.setting(keys[i]));
            fieldLabel(form,labels[i],fields[i]);
            if(i==7)fields[i].setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        }
        place(body,form);gap(body,16);
        Runnable saveCompany=()->{
            try{
                String name=fields[0].getText().toString().trim();
                String oib=fields[1].getText().toString().trim();
                String iban=fields[10].getText().toString().trim();
                if(name.isEmpty())throw new IllegalArgumentException("Unesite naziv tvrtke ili obrta");
                if(!oib.isEmpty()&&!Oib.valid(oib))throw new IllegalArgumentException("OIB nije valjan");
                if(!iban.isEmpty()&&!Iban.valid(iban))throw new IllegalArgumentException("IBAN nije valjan");
                String[] vals=new String[keys.length];
                for(int i=0;i<keys.length;i++)vals[i]=fields[i].getText().toString().trim();
                db.companySettings(keys,vals);
                toast("Podaci tvrtke uspješno spremljeni");settings();
            }catch(Exception e){error(e);}
        };
        // Keep the primary action visible while scrolling the long form, as in the reference.
        LinearLayout bottomAction=vertical();pad(bottomAction,16,7);
        bottomAction.setBackgroundColor(bg());
        bottomAction.addView(action("✓  Spremi promjene",BLUE,true,saveCompany),lp(-1,52));
        root.addView(bottomAction,1,new LinearLayout.LayoutParams(-1,-2));
        gap(body,12);caption(body,"Fiskalizacija nije podržana u ovoj verziji. Podaci o poslovnom prostoru i operateru služe samo za pripremu profila.");
        gap(body,16);
    }
    private void eracun(){
        setup("eracun",true);
        toolbar("eRačuni","Integracije i status usluge",true);
        LinearLayout hero=vertical();hero.setGravity(Gravity.CENTER);pad(hero,16,22);
        hero.setBackground(shape(dark?0xff243252:0xffecefff,21));
        TextView symbol=label("⇄",31,BLUE,true);
        symbol.setGravity(Gravity.CENTER);symbol.setBackground(shape(panel(),17));
        LinearLayout.LayoutParams iconLp=lp(68,68);iconLp.gravity=Gravity.CENTER;
        hero.addView(symbol,iconLp);gap(hero,15);
        TextView heading=label("eRačuni i fiskalizacija",19,ink(),true);
        heading.setGravity(Gravity.CENTER);place(hero,heading);gap(hero,8);
        TextView intro=label("Elektronička razmjena i fiskalizacija zahtijevaju provjerenu integraciju.",12,sub(),false);
        intro.setGravity(Gravity.CENTER);place(hero,intro);gap(hero,13);
        TextView state=label("Povezivanje nije dostupno",11,0xffb77312,true);
        state.setGravity(Gravity.CENTER);state.setBackground(shape(dark?0xff584125:0xfffff0d3,13));
        pad(state,12,6);
        LinearLayout.LayoutParams stateLp=lp(-2,-2);stateLp.gravity=Gravity.CENTER;
        hero.addView(state,stateLp);
        place(body,hero);gap(body,15);

        LinearLayout phases=card();title(phases,"Status integracije");gap(phases,12);
        boolean hasCompany=!db.setting("name").trim().isEmpty()&&
            Oib.valid(db.setting("oib"))&&!db.setting("address").trim().isEmpty();
        integrationStep(phases,hasCompany?"✓":"1","Profil tvrtke",
            hasCompany?"Osnovni podaci uneseni":"Unesite naziv, OIB i adresu",hasCompany,
            this::company);
        integrationStep(phases,"2","Informacijski posrednik",
            "Pružatelj nije povezan",false,()->showIntegrationRequirements());
        integrationStep(phases,"3","Ovlaštenja i testiranje",
            "Nisu dostupni u ovoj verziji",false,()->showIntegrationRequirements());
        place(body,phases);gap(body,15);

        LinearLayout info=card();info.setBackground(shape(dark?0xff24314d:0xffedf1ff,17));
        info.addView(label("Važno za eRačun",14,ink(),true));gap(info,8);
        info.addView(label("PDF datoteka nije strukturirani eRačun ni potvrda fiskalizacije. Za razmjenu i slanje potrebna je stvarna autorizirana integracija.",12,sub(),false));
        place(body,info);gap(body,14);
        button(body,"Informacije o integraciji",this::showIntegrationRequirements);
        gap(body,15);
    }
    private void integrationStep(LinearLayout parent,String number,String heading,
            String description,boolean ok,Runnable action){
        LinearLayout row=horizontal();pad(row,1,12);
        TextView badge=label(number,17,ok?TEAL:sub(),true);
        badge.setGravity(Gravity.CENTER);badge.setBackground(shape(ok?
            (dark?0xff20483f:0xffddf8ec):(dark?0xff27354e:0xffeff1f8),14));
        row.addView(badge,lp(39,39));
        LinearLayout words=vertical();pad(words,12,0);
        words.addView(label(heading,13,ink(),true));
        words.addView(label(description,11,sub(),false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(label("›",23,sub(),false));
        place(parent,row);
        row.setOnClickListener(v->action.run());
        gap(parent,5);
    }
    private void showIntegrationRequirements(){
        new AlertDialog.Builder(this).setTitle("eRačuni i fiskalizacija")
            .setMessage("Fakturiva trenutačno nema mrežnu vezu, integriranog informacijskog posrednika, certifikate ili fiskalizacijski sustav. Dostupni su samo lokalni dokumenti i PDF izvoz. Ne možete slati eRačune ni fiskalizirati račune iz ove verzije.")
            .setPositiveButton("U redu",null).show();
    }
    
    private void backupScreen(){
        setup("backup",true);toolbar("Sigurnosna kopija","Šifrirana kopija svih podataka",true);
        LinearLayout info=card();title(info,"Sačuvajte svoje poslovne podatke");
        gap(info,11);caption(info,"Sigurnosna kopija šifrira se AES-256-GCM algoritmom. Lozinka mora imati barem 10 znakova. Ne postoji mogućnost oporavka ako je izgubite.");
        gap(info,11);caption(info,"Uvoz u potpunosti zamjenjuje postojeće podatke. Prije uvoza izvezite aktualnu kopiju.");
        place(body,info);gap(body,15);
        LinearLayout local=card();
        title(local,"Automatska zaštita");gap(local,9);
        local.addView(label(db.backupSummary(),12,ink(),false));gap(local,7);
        caption(local,"Posljednje tri lokalne kopije šifrirane su ključem uređaja. One ne preživljavaju brisanje aplikacije ili gubitak telefona.");
        if(!db.backupWarning().isEmpty()){gap(local,8);local.addView(label(db.backupWarning(),12,RED,true));}
        gap(local,12);
        secondary(local,"Vrati prethodno lokalno stanje",this::showLocalRecovery);
        place(body,local);gap(body,18);
        button(body,"Izvezi šifriranu kopiju",()->passwordPrompt(false));
        gap(body,10);secondary(body,"Uvezi sigurnosnu kopiju",()->passwordPrompt(true));
        gap(body,15);
        caption(body,"Za zaštitu od krađe, kvara ili gubitka telefona kopirajte izvezenu datoteku na sigurno mjesto izvan uređaja. Bez te kopije ne možemo zajamčiti povrat dokumenata.");
    }
    private void showLocalRecovery(){
        try{
            int count=RecoveryVault.count(this);
            if(count==0){toast("Nema dostupnih lokalnih kopija");return;}
            String[] labels=new String[count];
            for(int i=0;i<count;i++)labels[i]="Lokalna kopija "+(i+1)+(i==0?" (najnovija)":"");
            new AlertDialog.Builder(this).setTitle("Odaberite kopiju za oporavak")
                .setItems(labels,(dialog,index)->new AlertDialog.Builder(this)
                    .setTitle("Potvrdite zamjenu podataka")
                    .setMessage("Oporavak mijenja trenutačno stanje. Postojeće stanje se prije promjene dodatno pohranjuje. Provjerite datume i izvezite lozinkom zaštićenu kopiju na drugi uređaj.")
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
        new AlertDialog.Builder(this).setTitle(importing?"Vrati sigurnosnu kopiju":"Šifriraj sigurnosnu kopiju")
            .setView(box).setNegativeButton("Odustani",null).setPositiveButton("Nastavi",(dialog,which)->{
                char[] pwd=pass.getText().toString().toCharArray();
                if(pwd.length<10){Arrays.fill(pwd,'\0');toast("Lozinka mora imati barem 10 znakova");return;}
                if(pendingPassword!=null)Arrays.fill(pendingPassword,'\0');
                pendingPassword=pwd;
                if(importing)new AlertDialog.Builder(this).setTitle("Zamjena podataka")
                    .setMessage("Uvoz će zamijeniti sve postojeće podatke. Nastaviti?")
                    .setNegativeButton("Odustani",(dlg,w)->clearPass())
                    .setPositiveButton("Odaberi datoteku",(dlg,w)->openBackup()).show();
                else createFile(BACKUP_EXPORT,"application/octet-stream","Fakturiva-backup.fak");
            }).show();
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
        if(result!=RESULT_OK||data==null||data.getData()==null){
            if(code==BACKUP_EXPORT||code==BACKUP_IMPORT)clearPass();
            if(code==104)pendingPaymentsCsvMonth=null;
            return;
        }
        Uri uri=data.getData();
        try{
            if(code==PDF_EXPORT){
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
        }
    }
    private void clearPass(){if(pendingPassword!=null){Arrays.fill(pendingPassword,'\0');pendingPassword=null;}}
    private void error(Exception e){String s=e.getMessage();toast(s==null?"Došlo je do pogreške":s);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){
        if("home".equals(screen))super.onBackPressed();
        else backFromToolbar();
    }
    private final class RevenueBars extends View{
        final android.graphics.Paint p=new android.graphics.Paint(3);
        RevenueBars(Context ctx){super(ctx);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            long[] totals=db.monthlyReceipts();
            long top=1;
            for(int i=6;i<12;i++)top=Math.max(top,totals[i]);
            float w=getWidth(),h=getHeight(),labelH=dp(22),plotH=h-labelH;
            p.setColor(border());p.setStrokeWidth(dp(1));
            canvas.drawLine(0,plotH,w,plotH,p);
            java.util.Calendar now=java.util.Calendar.getInstance();
            now.add(java.util.Calendar.MONTH,-5);
            String[] months={"Sij","Velj","Ožu","Tra","Svi","Lip","Srp","Kol","Ruj","Lis","Stu","Pro"};
            p.setTextSize(dp(10));p.setTextAlign(android.graphics.Paint.Align.CENTER);
            for(int i=0;i<6;i++){
                float slot=w/6f;
                float x=i*slot+slot*.14f,barW=slot*.72f;
                float fraction=(float)totals[i+6]/top;
                float barH=Math.max(dp(3),plotH*.86f*fraction);
                p.setStyle(android.graphics.Paint.Style.FILL);
                p.setColor(i==5?TEAL:0xff6879ec);
                canvas.drawRoundRect(x,plotH-barH,x+barW,plotH,dp(7),dp(7),p);
                p.setColor(sub());
                canvas.drawText(months[now.get(java.util.Calendar.MONTH)],x+barW/2,h-dp(3),p);
                now.add(java.util.Calendar.MONTH,1);
            }
            p.setTextAlign(android.graphics.Paint.Align.LEFT);
        }
    }
    private final class RevenueLine extends View{
        private final android.graphics.Paint paint=new android.graphics.Paint(3);
        private final long[] cumulative;
        RevenueLine(android.content.Context context){
            super(context);
            Calendar calendar=Calendar.getInstance();
            String month=new SimpleDateFormat("yyyy-MM",Locale.ROOT).format(calendar.getTime());
            cumulative=ReceiptsChart.cumulative(
                db.payments(),month,calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
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
