package com.brendigo.fakturiva;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.ClipData;
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
    private LinearLayout root,body; private ScrollView scroller;
    private String screen="home",filter="Svi",editType=Store.INVOICE;
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
            if(!prefs.getBoolean("welcomed",false)) welcome();
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
    private int bg(){return dark?0xff101c30:BG;}
    private int panel(){return dark?0xff192a46:WHITE;}
    private int ink(){return dark?WHITE:TEXT;}
    private int sub(){return dark?0xffb2bed4:MUTED;}
    private int border(){return dark?0xff293d59:LINE;}
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
    private void section(String s){gap(body,20);title(body,s);gap(body,12);}
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
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(panel());
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root=vertical();root.setBackgroundColor(bg());setContentView(root);
        scroller=new ScrollView(this);scroller.setFillViewport(false);scroller.setClipToPadding(false);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,0,1));
        body=vertical();pad(body,17,18);scroller.addView(body,new ScrollView.LayoutParams(-1,-2));
        if(nav)bottomNav();
    }
    private void toolbar(String title,String subtitle,boolean back){
        LinearLayout row=horizontal();
        if(back){TextView b=label("‹",31,ink(),false);b.setGravity(Gravity.CENTER);row.addView(b,lp(34,45));b.setOnClickListener(v->navigate("home"));}
        LinearLayout heading=vertical();TextView t=label(title,23,ink(),true);place(heading,t);
        if(subtitle!=null){TextView st=label(subtitle,11,sub(),false);place(heading,st);}
        row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView more=label("⋯",29,ink(),true);more.setGravity(Gravity.CENTER);
        row.addView(more,lp(36,42));more.setOnClickListener(v->navigate("settings"));
        place(body,row);gap(body,18);
    }
    private ImageView icon(int id,int tint){
        ImageView img=new ImageView(this);img.setImageResource(id);
        img.setColorFilter(tint);img.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        return img;
    }
    private void bottomNav(){
        LinearLayout bar=horizontal();bar.setBackgroundColor(panel());pad(bar,6,7);
        String[] pages={"home","invoices","quotes","clients","settings"};
        String[] names={"Početna","Računi","Ponude","Klijenti","Više"};
        int[] icons={R.drawable.ic_home,R.drawable.ic_invoice,R.drawable.ic_quote,R.drawable.ic_clients,R.drawable.ic_more};
        for(int i=0;i<pages.length;i++){final String destination=pages[i];
            boolean selected=screen.equals(destination);
            LinearLayout tab=vertical();tab.setGravity(Gravity.CENTER);
            LinearLayout badge=vertical();badge.setGravity(Gravity.CENTER);
            if(selected)background(badge,dark?0xff273D66:0xffECEEFF,12);
            badge.addView(icon(icons[i],selected?BLUE:sub()),lp(23,23));
            LinearLayout.LayoutParams badgeLp=lp(39,35);badgeLp.gravity=Gravity.CENTER_HORIZONTAL;tab.addView(badge,badgeLp);
            TextView name=label(names[i],10,selected?BLUE:sub(),selected);
            name.setGravity(Gravity.CENTER);tab.addView(name,lp(-1,19));
            bar.addView(tab,new LinearLayout.LayoutParams(0,dp(56),1));tab.setOnClickListener(v->navigate(destination));
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
    private void welcome(){
        setup("welcome",false);gap(body,70);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.brand_mark);
        LinearLayout.LayoutParams il=lp(90,90);il.gravity=Gravity.CENTER_HORIZONTAL;body.addView(icon,il);
        gap(body,20);TextView t=label("Fakturiva",35,ink(),true);t.setGravity(Gravity.CENTER);place(body,t);
        TextView subtitle=label("Računi i ponude, jednostavno.",14,sub(),false);subtitle.setGravity(Gravity.CENTER);place(body,subtitle);
        gap(body,55);
        title(body,"Manje papirologije.\nViše vremena za posao.");gap(body,16);
        caption(body,"Profesionalno upravljanje računima, ponudama i klijentima — sve lokalno na vašem Android uređaju.");
        gap(body,38);
        button(body,"Započni  ›",()->{prefs.edit().putBoolean("welcomed",true).apply();navigate("home");});
        gap(body,20);caption(body,"Bez registracije, oglasa i mrežnih servisa.");
    }
    private void home(){
        setup("home",true);
        LinearLayout bar=horizontal();
        LinearLayout text=vertical();
        text.addView(label("Fakturiva",23,ink(),true));
        text.addView(label("Upravljajte poslovanjem",11,sub(),false));
        bar.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        ImageView search=icon(R.drawable.ic_search,ink());search.setBackground(shape(panel(),12));
        LinearLayout.LayoutParams searchLp=lp(38,38);searchLp.setMargins(0,0,dp(8),0);bar.addView(search,searchLp);
        search.setOnClickListener(v->documents(Store.INVOICE));
        ImageView alerts=icon(R.drawable.ic_bell,ink());alerts.setBackground(shape(panel(),12));
        bar.addView(alerts,lp(38,38));alerts.setOnClickListener(v->showNotifications());
        place(body,bar);gap(body,16);
        if(RecoveryVault.hasWorkingDraft(this)){
            LinearLayout resume=card();
            resume.addView(label("Nedovršen račun ili ponuda",14,ink(),true));
            gap(resume,4);resume.addView(label("Automatski sačuvano · dodirnite za nastavak",11,sub(),false));
            resume.setOnClickListener(v->loadWorkingDraft());
            place(body,resume);gap(body,14);
        }
        java.text.SimpleDateFormat fullDate=new java.text.SimpleDateFormat("EEEE, d. MMMM",Locale.forLanguageTag("hr-HR"));
        TextView date=label(fullDate.format(new java.util.Date()).toUpperCase(Locale.forLanguageTag("hr-HR")),11,0xff6A81A5,true);
        place(body,date);gap(body,7);
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        title(body,(hour<11?"Dobro jutro":hour<18?"Dobar dan":"Dobra večer")+"! 👋");
        gap(body,4);caption(body,"Evo pregleda vašeg poslovanja.");gap(body,17);
        LinearLayout hero=vertical();pad(hero,18,19);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff14263f,0xff254d87});
        gradient.setCornerRadius(dp(20));hero.setBackground(gradient);
        long[] monthly=db.monthlyReceipts();
        hero.addView(label("Naplaćeno ovaj mjesec",12,0xffcedbf2,false));gap(hero,12);
        hero.addView(label(Money.euros(monthly[11]),30,WHITE,true));gap(hero,5);
        hero.addView(label("Prema evidentiranim uplatama",11,0xffa5d7e2,false));
        gap(hero,13);hero.addView(new RevenueLine(this),lp(-1,53));place(body,hero);
        gap(body,12);
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
        section("Nedavni dokumenti");
        int count=0;
        for(Store.Document d:db.documents(Store.INVOICE)){if(count++>=3)break;documentRow(d);}
        if(count==0)caption(body,"Još nema računa. Dodajte prvi dokument.");
        gap(body,18);secondary(body,"Svi računi",()->documents(Store.INVOICE));
        if(!db.backupWarning().isEmpty()){
            gap(body,15);TextView warning=label(db.backupWarning(),12,RED,true);place(body,warning);
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
        LinearLayout c=card();
        ImageView image=icon(figureIcon(glyph),accent);
        image.setPadding(dp(8),dp(8),dp(8),dp(8));
        image.setBackground(shape(accent==TEAL?0xffdff8f4:0xffe8edff,12));
        c.addView(image,lp(42,42));gap(c,11);
        c.addView(label(label,12,sub(),false));gap(c,3);
        c.addView(label(value,25,ink(),true));c.addView(label("Ovaj mjesec",10,sub(),false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);
    }
    private void quick(LinearLayout row,String glyph,String label,Runnable action){
        LinearLayout c=card();c.setGravity(Gravity.CENTER);
        boolean quote="▣".equals(glyph),client="♧".equals(glyph);
        int tint=quote?TEAL:client?0xffeb963c:BLUE;
        ImageView image=icon(figureIcon(glyph),tint);
        image.setPadding(dp(10),dp(10),dp(10),dp(10));
        image.setBackground(shape(quote?0xffdcf8f4:client?0xfffff0e3:0xffe8edff,12));
        LinearLayout.LayoutParams iconLp=lp(45,45);iconLp.gravity=Gravity.CENTER_HORIZONTAL;c.addView(image,iconLp);
        gap(c,7);TextView text=label(label,11,ink(),true);text.setGravity(Gravity.CENTER);place(c,text);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(103),1);
        p.setMargins(dp(3),0,dp(3),0);row.addView(c,p);c.setOnClickListener(v->action.run());
    }
    private void documents(String type){
        setup(Store.INVOICE.equals(type)?"invoices":"quotes",true);
        toolbar(Store.INVOICE.equals(type)?"Računi":"Ponude","Pregled i pretraga dokumenata",false);
        EditText search=field("Pretraži dokumente…","");place(body,search);gap(body,12);
        LinearLayout filters=horizontal();
        String[] states=Store.INVOICE.equals(type)?new String[]{"Svi","Plaćeni","Neplaćeni","Skice"}:new String[]{"Svi","Poslane","Skice"};
        for(String s:states){TextView f=label(s,11,filter.equals(s)?WHITE:sub(),true);f.setGravity(Gravity.CENTER);
            if(filter.equals(s))background(f,BLUE,12);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(35),1);p.setMargins(dp(2),0,dp(2),0);
            filters.addView(f,p);f.setOnClickListener(v->{filter=s;documents(type);});
        }
        place(body,filters);gap(body,12);
        LinearLayout totals=horizontal();
        List<Store.Document> all=db.documents(type);
        TextView totalCount=label("Ukupno: "+all.size(),11,sub(),false);
        totals.addView(totalCount,new LinearLayout.LayoutParams(0,dp(30),1));
        if(Store.INVOICE.equals(type)){
            long unpaid=0;for(Store.Document d:all)if(Store.ISSUED.equals(d.status))unpaid+=d.outstanding();
            totals.addView(label("Neplaćeno: "+Money.euros(unpaid),11,sub(),false));
        }
        place(body,totals);gap(body,7);
        int shown=0;for(Store.Document d:all){
            if(!matches(d,filter))continue;
            documentRow(d);shown++;
        }
        if(shown==0)caption(body,"Nema dokumenata u ovoj kategoriji.");
        gap(body,18);
        button(body,Store.INVOICE.equals(type)?"＋ Novi račun":"＋ Nova ponuda",()->newDocument(type));
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                for(int i=0;i<body.getChildCount();i++){View v=body.getChildAt(i);
                    if(v.getTag() instanceof Store.Document){Store.Document d=(Store.Document)v.getTag();
                        String term=s.toString().toLowerCase(Locale.ROOT);
                        boolean hit=(d.clientName+" "+d.number+" "+d.status).toLowerCase(Locale.ROOT).contains(term);
                        v.setVisibility(hit&&matches(d,filter)?View.VISIBLE:View.GONE);
                    }
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });
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
        r.addView(amounts);place(wrap,r);gap(body,9);place(body,wrap);
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
    private void saveWorkingDraft(){
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
        }catch(Exception e){
            android.util.Log.e("FakturivaRecovery","Working draft could not be saved",e);
            toast("Upozorenje: radni nacrt nije spremljen. Izvezite ili spremite dokument.");
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
    private void editDocumentScreen(){
        setup("new_doc",true);toolbar(Store.INVOICE.equals(editType)?"Novi račun":"Nova ponuda","Korak 1 od 3 · Osnovni podaci",true);
        LinearLayout c=card();title(c,"Osnovni podaci");gap(c,10);
        List<Store.Client> clients=db.clients();
        if(clients.isEmpty()){
            caption(c,"Prije dokumenta dodajte barem jednog klijenta.");
            gap(c,15);button(c,"＋ Dodaj klijenta",this::addClient);
            place(body,c);return;
        }
        String[] names=new String[clients.size()];int current=0;
        for(int i=0;i<clients.size();i++){names[i]=clients.get(i).name;if(clients.get(i).id==editClientId)current=i;}
        c.addView(label("Klijent",12,ink(),true));Spinner selector=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);selector.setAdapter(adapter);
        selector.setSelection(current);place(c,selector);
        selector.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> p){}
            @Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                editClientId=clients.get(pos).id;saveWorkingDraft();
            }
        });
        EditText issued=field("Datum izdavanja",draftIssue);dateInput(issued);fieldLabel(c,"Datum izdavanja",issued);
        EditText due=field("Datum dospijeća / valjanosti",draftDue);dateInput(due);fieldLabel(c,"Dospijeće / vrijedi do",due);
        EditText supply=field("Datum isporuke ili obavljene usluge",draftSupply);dateInput(supply);fieldLabel(c,"Datum isporuke / usluge",supply);
        EditText notes=field("Neobavezna napomena",draftNote);fieldLabel(c,"Napomena",notes);
        watchDraft(issued,()->draftIssue=issued.getText().toString());
        watchDraft(due,()->draftDue=due.getText().toString());
        watchDraft(supply,()->draftSupply=supply.getText().toString());
        watchDraft(notes,()->draftNote=notes.getText().toString());
        place(body,c);gap(body,16);
        button(body,"Nastavi na stavke  ›",()->{
            editClientId=clients.get(selector.getSelectedItemPosition()).id;
            draftIssue=issued.getText().toString();draftDue=due.getText().toString();draftSupply=supply.getText().toString();
            draftNote=notes.getText().toString();saveWorkingDraft();editItemsScreen();
        });
    }
    private void editItemsScreen(){
        setup("edit_items",true);toolbar("Stavke dokumenta","Korak 2 od 3 · Stavke i iznosi",true);
        if(draftItems.isEmpty())caption(body,"Dodajte usluge ili artikle u dokument.");
        int index=0;
        for(Store.Item i:draftItems){
            final int delete=index++;
            LinearLayout c=card();c.addView(label(i.name,14,ink(),true));gap(c,7);
            c.addView(label(Money.decimalQuantity(i.qtyMilli)+" × "+Money.euros(i.unitCents)+" · PDV "+i.taxRate+"%",11,sub(),false));
            gap(c,8);c.addView(label(Money.euros(i.net()+i.tax()),17,BLUE,true));
            gap(c,7);
            TextView remove=label("Ukloni stavku",12,RED,true);place(c,remove);remove.setOnClickListener(v->{draftItems.remove(delete);saveWorkingDraft();editItemsScreen();});
            place(body,c);gap(body,10);
        }
        secondary(body,"＋ Dodaj artikl ili uslugu",this::addLine);
        gap(body,13);
        long net=0,tax=0;for(Store.Item i:draftItems){net+=i.net();tax+=i.tax();}
        LinearLayout summary=card();summary.addView(label("Osnovica: "+Money.euros(net),13,ink(),false));
        summary.addView(label("PDV: "+Money.euros(tax),13,ink(),false));gap(summary,7);
        summary.addView(label("UKUPNO: "+Money.euros(net+tax),20,BLUE,true));
        place(body,summary);gap(body,14);
        button(body,"Pregled dokumenta  ›",()->{
            if(draftItems.isEmpty())toast("Dodajte barem jednu stavku");else reviewDraft();
        });
        gap(body,9);secondary(body,"‹ Natrag na podatke",this::editDocumentScreen);
    }
    private void addLine(){
        LinearLayout form=vertical();pad(form,16,5);
        EditText name=field("Naziv artikla ili usluge","");
        EditText qty=field("Količina","1");
        EditText price=field("Jedinična cijena, EUR","0,00");
        qty.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] rates={"25%","13%","5%","0%"};
        Spinner tax=new Spinner(this);tax.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates));
        fieldLabel(form,"Stavka",name);
        if(!db.products().isEmpty()){
            Spinner product=new Spinner(this);List<Store.Item> products=db.products();
            ArrayList<String> options=new ArrayList<>();options.add("Odaberite iz kataloga (neobavezno)");
            for(Store.Item p:products)options.add(p.name);
            product.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
            product.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                public void onNothingSelected(android.widget.AdapterView<?> parent){}
                public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                    if(pos>0){Store.Item it=products.get(pos-1);name.setText(it.name);
                        price.setText(String.format(Locale.forLanguageTag("hr-HR"),"%.2f",it.unitCents/100.0));
                        for(int j=0;j<rates.length;j++)if(rates[j].equals(it.taxRate+"%"))tax.setSelection(j);
                    }
                }
            });
            gap(form,12);form.addView(label("Iz kataloga",12,ink(),true));gap(form,6);
            place(form,product);
        }
        fieldLabel(form,"Količina",qty);fieldLabel(form,"Jedinična cijena bez PDV-a",price);
        gap(form,11);form.addView(label("Stopa PDV-a (informativni odabir)",12,ink(),true));place(form,tax);
        new AlertDialog.Builder(this).setTitle("Nova stavka").setView(form)
            .setNegativeButton("Odustani",null).setPositiveButton("Dodaj",(dialog,which)->{
                try{Store.Item i=new Store.Item();i.name=name.getText().toString().trim();
                    i.qtyMilli=Money.quantity(qty.getText().toString());
                    i.unitCents=Money.cents(price.getText().toString());
                    i.taxRate=Integer.parseInt(rates[tax.getSelectedItemPosition()].replace("%",""));
                    if(i.name.isEmpty()||i.unitCents<0)throw new IllegalArgumentException("Provjerite naziv i cijenu");
                    draftItems.add(i);saveWorkingDraft();editItemsScreen();
                }catch(Exception e){error(e);}
            }).show();
    }
    private void reviewDraft(){
        saveWorkingDraft();
        setup("review",true);toolbar("Pregled dokumenta","Korak 3 od 3 · Potvrda",true);
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
        currentDocumentId=id;Store.Document d;
        try{d=db.document(id);}catch(Exception e){error(e);navigate("home");return;}
        setup("detail",true);toolbar(Store.INVOICE.equals(d.type)?"Pregled računa":"Pregled ponude","Detalji dokumenta",true);
        LinearLayout card=card();card.addView(label(d.number==null?"Nacrt #"+d.id:d.number,21,BLUE,true));gap(card,4);
        card.addView(label("Status: "+status(d.status),12,sub(),false));gap(card,18);
        card.addView(label(d.issuerName,15,ink(),true));gap(card,4);
        card.addView(label("Klijent: "+d.clientName,13,ink(),false));
        card.addView(label("OIB klijenta: "+d.clientOib,11,sub(),false));
        if(d.clientVatId!=null&&!d.clientVatId.isEmpty())card.addView(label("PDV ID klijenta: "+d.clientVatId,11,sub(),false));
        card.addView(label("Adresa klijenta: "+d.clientAddress,11,sub(),false));gap(card,11);
        card.addView(label("Izdano: "+d.issueDate+"  ·  Dospijeće: "+d.dueDate,11,sub(),false));
        if(d.supplyDate!=null&&!d.supplyDate.isEmpty())card.addView(label("Datum usluge: "+d.supplyDate,11,sub(),false));
        gap(card,15);
        for(Store.Item it:d.items){LinearLayout r=horizontal();
            TextView n=label(it.name+"\n"+Money.decimalQuantity(it.qtyMilli)+" × "+Money.euros(it.unitCents)+" · "+it.taxRate+"%",11,ink(),false);
            r.addView(n,new LinearLayout.LayoutParams(0,-2,1));r.addView(label(Money.euros(it.net()+it.tax()),12,ink(),true));
            place(card,r);gap(card,13);
        }
        card.addView(label("Osnovica: "+Money.euros(d.netCents),12,sub(),false));
        card.addView(label("PDV: "+Money.euros(d.taxCents),12,sub(),false));gap(card,9);
        card.addView(label("UKUPNO: "+Money.euros(d.totalCents),23,BLUE,true));
        if(Store.INVOICE.equals(d.type))card.addView(label("Preostalo: "+Money.euros(d.outstanding()),12,sub(),false));
        place(body,card);gap(body,15);
        button(body,"Preuzmi PDF",()->savePdf(id));gap(body,9);
        secondary(body,"Podijeli PDF",()->sharePdf(id));gap(body,9);
        if(Store.DRAFT.equals(d.status)){
            secondary(body,"✎ Uredi nacrt",()->resumeSavedDraft(id));
            gap(body,9);
            secondary(body,"Potvrdi dokument",()->confirmWarning(()->{
            try{db.confirm(id);showDocument(id);}catch(Exception e){error(e);}
        }));
        }
        if(Store.INVOICE.equals(d.type)&&Store.ISSUED.equals(d.status)&&d.outstanding()>0){
            gap(body,9);secondary(body,"＋ Evidentiraj uplatu",()->payDialog(id));
        }
        if(Store.QUOTE.equals(d.type)&&Store.SENT.equals(d.status)){
            gap(body,9);secondary(body,"Pretvori u račun",()->{
                try{long invoice=db.quoteToInvoice(id);showDocument(invoice);toast("Stvoren je nacrt računa");}catch(Exception e){error(e);}
            });
        }
        gap(body,12);caption(body,"PDF je lokalno generiran; nije strukturirani eRačun niti potvrda fiskalizacije.");
    }
    private void payDialog(long id){
        Store.Document d=db.document(id);
        EditText amount=field("Iznos uplate",String.format(Locale.forLanguageTag("hr-HR"),"%.2f",d.outstanding()/100.0));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout v=vertical();pad(v,18,12);fieldLabel(v,"Iznos u EUR",amount);
        new AlertDialog.Builder(this).setTitle("Evidentiraj uplatu").setView(v).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",(dlg,which)->{
                try{db.pay(id,Money.cents(amount.getText().toString()),later(0));showDocument(id);}
                catch(Exception e){error(e);}
            }).show();
    }
    private void clients(){
        setup("clients",true);toolbar("Klijenti","Kupci i poslovni partneri",false);
        List<Store.Client> list=db.clients();
        caption(body,"Ukupno klijenata: "+list.size());gap(body,13);
        for(Store.Client c:list){LinearLayout card=card();
            LinearLayout r=horizontal();TextView initials=label(c.name.substring(0,Math.min(1,c.name.length())).toUpperCase(),20,BLUE,true);
            initials.setGravity(Gravity.CENTER);background(initials,0xffeaf0ff,11);r.addView(initials,lp(42,42));
            LinearLayout text=vertical();pad(text,12,0);text.addView(label(c.name,14,ink(),true));
            text.addView(label(c.oib.isEmpty()?c.email:"OIB: "+c.oib,11,sub(),false));
            r.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            r.addView(label("›",25,sub(),false));place(card,r);
            place(body,card);gap(body,9);card.setOnClickListener(v->clientDetails(c));
        }
        if(list.isEmpty())caption(body,"Nema klijenata.");gap(body,12);
        button(body,"＋ Dodaj klijenta",this::addClient);
    }
    private void clientDetails(Store.Client c){
        setup("client_detail",true);toolbar("Detalji klijenta","Profil poslovnog partnera",true);
        LinearLayout card=card();title(card,c.name);gap(card,18);
        caption(card,"OIB: "+c.oib);gap(card,10);caption(card,"Adresa: "+c.address);
        gap(card,10);caption(card,"E-mail: "+c.email);gap(card,10);
        caption(card,"Telefon: "+(c.phone==null?"":c.phone));gap(card,10);
        caption(card,"PDV ID: "+(c.vatId==null?"":c.vatId));place(body,card);
        gap(body,11);secondary(body,"✎ Uredi podatke klijenta",()->showClientEditor(c));
        section("Dokumenti klijenta");
        int n=0;for(String t:new String[]{Store.INVOICE,Store.QUOTE})
            for(Store.Document d:db.documents(t))if(d.clientId==c.id){documentRow(d);n++;}
        if(n==0)caption(body,"Još nema dokumenata za ovog klijenta.");
        gap(body,13);button(body,"＋ Novi račun za klijenta",()->{newDocument(Store.INVOICE);editClientId=c.id;editDocumentScreen();});
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
        new AlertDialog.Builder(this).setTitle(editing?"Uredi klijenta":"Novi klijent").setView(content)
            .setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",(dlg,w)->{
                try{
                    String id=oib.getText().toString().trim();
                    if(!id.isEmpty()&&!Oib.valid(id))throw new IllegalArgumentException("OIB nije valjan");
                    if(editing)db.updateClient(saved.id,name.getText().toString(),id,address.getText().toString(),
                        email.getText().toString(),phone.getText().toString(),vat.getText().toString());
                    else db.saveClientDetailed(name.getText().toString(),id,address.getText().toString(),
                        email.getText().toString(),phone.getText().toString(),vat.getText().toString());
                    clients();
                }catch(Exception e){error(e);}
            }).show();
    }
    private void catalog(){
        setup("catalog",true);toolbar("Artikli i usluge","Katalog proizvoda i usluga",true);
        List<Store.Item> list=db.products();caption(body,"Ukupno stavki: "+list.size());gap(body,15);
        for(Store.Item i:list){LinearLayout c=card();c.addView(label(i.name,14,ink(),true));gap(c,6);
            c.addView(label("Jedinična cijena: "+Money.euros(i.unitCents)+"   ·   PDV: "+i.taxRate+"%",12,sub(),false));
            place(body,c);gap(body,9);
        }
        if(list.isEmpty())caption(body,"Katalog je prazan.");gap(body,12);
        button(body,"＋ Novi artikl ili usluga",this::addProduct);
    }
    private void addProduct(){
        LinearLayout form=vertical();pad(form,18,5);
        EditText name=field("Naziv","");
        EditText price=field("Cijena bez PDV-a","0,00");
        price.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] rates={"25%","13%","5%","0%"};
        Spinner rate=new Spinner(this);rate.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,rates));
        fieldLabel(form,"Naziv",name);fieldLabel(form,"Cijena, EUR",price);
        gap(form,12);form.addView(label("Stopa PDV-a",12,ink(),true));place(form,rate);
        new AlertDialog.Builder(this).setTitle("Nova usluga").setView(form).setNegativeButton("Odustani",null)
            .setPositiveButton("Spremi",(dlg,w)->{
                try{db.saveProduct(name.getText().toString(),Money.cents(price.getText().toString()),Integer.parseInt(rates[rate.getSelectedItemPosition()].replace("%","")));catalog();}
                catch(Exception e){error(e);}
            }).show();
    }
    private void reports(){
        setup("reports",true);toolbar("Izvještaji","Statistika poslovanja",true);
        LinearLayout c=card();caption(c,"Izdani računi (bez nacrta)");gap(c,12);
        c.addView(label(Money.euros(db.invoicedCents()),27,BLUE,true));place(body,c);gap(body,13);
        LinearLayout cards=horizontal();metric(cards,"Računi",String.valueOf(db.documents(Store.INVOICE).size()),"▤",BLUE);
        metric(cards,"Ponude",String.valueOf(db.documents(Store.QUOTE).size()),"▣",TEAL);place(body,cards);
        section("Pregled naplate");
        LinearLayout totals=card();totals.addView(label("Naplaćeno: "+Money.euros(db.receivedCents()),15,TEAL,true));
        gap(totals,13);totals.addView(label("Nenaplaćeno: "+Money.euros(Math.max(0,db.invoicedCents()-db.receivedCents())),14,ink(),true));
        place(body,totals);gap(body,12);
        secondary(body,"Pregled uplata",this::payments);
    }
    private void payments(){
        setup("payments",true);toolbar("Naplata","Evidencija uplata i potraživanja",true);
        LinearLayout sum=card();caption(sum,"Ukupno naplaćeno");gap(sum,10);
        sum.addView(label(Money.euros(db.receivedCents()),28,BLUE,true));place(body,sum);
        section("Računi");
        for(Store.Document d:db.documents(Store.INVOICE))
            if(!Store.DRAFT.equals(d.status)){documentRow(d);}
    }
    private String getPackageVersion(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(Exception ignored){return "Android";}
    }
    private void settings(){
        setup("settings",true);toolbar("Postavke","Upravljajte svojom aplikacijom",false);
        LinearLayout brand=card();brand.setBackground(shape(NAVY,17));
        brand.addView(label("Fakturiva",22,WHITE,true));gap(brand,5);
        brand.addView(label("Računi i ponude, jednostavno.",12,0xffc7d8ec,false));
        place(body,brand);gap(body,13);
        settingRow("▤  Tvrtka i poslovni podaci","Identitet izdavatelja",this::company);
        settingRow("♧  Artikli i usluge","Katalog poslovanja",this::catalog);
        settingRow("▣  Izvještaji","Statistika prihoda i naplate",this::reports);
        settingRow("◈  eRačuni i fiskalizacija","Objašnjenje ograničenja",this::eracun);
        settingRow("⇩  Sigurnosna kopija","Zaštita i oporavak podataka",this::backupScreen);
        settingRow("◐  Tamna tema","Prebaci prikaz aplikacije",()->{
            dark=!dark;prefs.edit().putBoolean("dark",dark).apply();settings();
        });
        gap(body,16);caption(body,"Fakturiva "+getPackageVersion()+" · Brendigo · Podaci ostaju na uređaju");
    }
    private void settingRow(String title,String subtitle,Runnable action){
        LinearLayout c=card();LinearLayout row=horizontal();LinearLayout text=vertical();
        text.addView(label(title,14,ink(),true));gap(text,5);text.addView(label(subtitle,11,sub(),false));
        row.addView(text,new LinearLayout.LayoutParams(0,-2,1));row.addView(label("›",22,sub(),true));
        place(c,row);place(body,c);gap(body,9);c.setOnClickListener(v->action.run());
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
        setup("eracun",true);toolbar("eRačuni i fiskalizacija","Trenutačni status integracije",true);
        LinearLayout c=card();c.addView(label("Integracija nije aktivirana",19,BLUE,true));gap(c,15);
        c.addView(label("PDF iz Fakturive nije strukturirani eRačun. Ova verzija nema fiskalizacijski servis, potpisivanje, certifikate ni slanje preko posrednika.",14,ink(),false));
        gap(c,18);c.addView(label("Za zakonski obvezne procese potrebna je zasebna provjerena integracija, internet i vjerodajnice.",12,sub(),false));
        place(body,c);gap(body,15);
        caption(body,"Aplikacija vas ne smije navesti na pogrešan zaključak da je dokument automatski fiskaliziran.");
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
        finally{if(code==BACKUP_EXPORT||code==BACKUP_IMPORT)clearPass();}
    }
    private void clearPass(){if(pendingPassword!=null){Arrays.fill(pendingPassword,'\0');pendingPassword=null;}}
    private void error(Exception e){String s=e.getMessage();toast(s==null?"Došlo je do pogreške":s);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onBackPressed(){
        if("home".equals(screen))super.onBackPressed();
        else if("edit_items".equals(screen))editDocumentScreen();
        else if("review".equals(screen))editItemsScreen();
        else navigate("home");
    }
    private final class RevenueLine extends View{
        private final android.graphics.Paint paint=new android.graphics.Paint(3);
        RevenueLine(android.content.Context ctx){super(ctx);}
        @Override protected void onDraw(android.graphics.Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            android.graphics.Path path=new android.graphics.Path();
            long[] monthly=db.monthlyReceipts();
            long max=1;for(long value:monthly)max=Math.max(max,value);
            for(int i=0;i<monthly.length;i++){
                float x=i*w/(monthly.length-1),y=h-(h*0.85f*monthly[i]/max);
                if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
            }
            paint.setColor(0xff72dadb);paint.setStrokeWidth(dp(2));paint.setStyle(android.graphics.Paint.Style.STROKE);
            canvas.drawPath(path,paint);paint.setStyle(android.graphics.Paint.Style.FILL);
        }
    }
}
