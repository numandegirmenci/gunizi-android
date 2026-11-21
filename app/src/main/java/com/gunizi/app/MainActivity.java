package com.gunizi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Turkish, one-time setup; two modes and an honest AI status. */
public final class MainActivity extends Activity {
    private static final int FOLDER=101, MIC=102, NOTIFY=103;
    private static final int BG=0xff0b1020,PANEL=0xff151e31,WHITE=0xfff5f8ff,
        MUTED=0xffa3b2c6,CYAN=0xff23d3d9,PURPLE=0xff8964ff;
    private Prefs prefs;
    private LinearLayout layout;
    private TextView status,detail,folderText,modeDetail;
    private Button serviceButton;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){
        @Override public void run(){refreshStatus();handler.postDelayed(this,3000);}
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);build();
    }
    @Override protected void onResume(){super.onResume();handler.post(refresh);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String str,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(str);t.setTextSize(size);
        t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private void space(LinearLayout l,int v){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(v)));}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;
    }
    private LinearLayout card(String heading,String sub){
        LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(17),dp(16),dp(17),dp(16));p.setBackground(bg(PANEL));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(13);
        layout.addView(p,params);
        p.addView(text(heading,18,WHITE,true));
        if(sub!=null){space(p,5);p.addView(text(sub,13,MUTED,false));}
        space(p,12);return p;
    }
    private Button button(String title,int color){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextColor(color==CYAN?BG:WHITE);
        b.setBackground(bg(color));b.setTextSize(14);b.setMinHeight(dp(48));return b;
    }
    private void build(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20),dp(24),dp(20),dp(35));
        sc.addView(layout);setContentView(sc);
        layout.addView(text("◉  Günİzi",30,WHITE,true));
        layout.addView(text("Gününü kaydet, istediğinde hatırla.",14,MUTED,false));
        space(layout,20);

        LinearLayout st=card("Kayıt durumu",null);
        status=text("Hazır",21,CYAN,true);st.addView(status);
        space(st,7);detail=text("Kayıt hizmeti başlatılmadı.",13,MUTED,false);st.addView(detail);
        space(st,12);
        serviceButton=button("Kayıt hizmetini etkinleştir",CYAN);st.addView(serviceButton);
        serviceButton.setOnClickListener(v->{if(RecorderService.running)stop();else start();});

        LinearLayout mo=card("Çalışma biçimi","Birini seç. Mesai modunda saat ve günler geçerli.");
        RadioGroup options=new RadioGroup(this);options.setOrientation(RadioGroup.VERTICAL);
        RadioButton work=new RadioButton(this),continuous=new RadioButton(this);
        work.setText("Mesai Kaydı  ·  Seçilen saatlerde");
        continuous.setText("Sürekli Kayıt  ·  Gecko gibi RAM geçmişi");
        for(RadioButton r:new RadioButton[]{work,continuous}){
            r.setId(View.generateViewId());r.setTextColor(WHITE);r.setTextSize(14);
            r.setButtonTintList(ColorStateList.valueOf(CYAN));options.addView(r);
        }
        options.check(Prefs.MODE_WORK.equals(prefs.mode())?work.getId():continuous.getId());
        options.setOnCheckedChangeListener((group,id)->{
            prefs.mode(id==work.getId()?Prefs.MODE_WORK:Prefs.MODE_CONTINUOUS);
            if(RecorderService.running)send(RecorderService.ACTION_REFRESH);
            refreshStatus();
        });
        mo.addView(options);space(mo,8);
        modeDetail=text("",12,MUTED,false);mo.addView(modeDetail);

        LinearLayout schedule=card("Mesai planı","Saatleri ve günleri yalnızca bir kez ayarla.");
        LinearLayout hours=new LinearLayout(this);hours.setOrientation(LinearLayout.HORIZONTAL);
        Button begin=button("Başla: "+prefs.timeString(true),0xff293752);
        Button end=button("Bitir: "+prefs.timeString(false),0xff293752);
        hours.addView(begin,new LinearLayout.LayoutParams(0,dp(50),1f));
        hours.addView(end,new LinearLayout.LayoutParams(0,dp(50),1f));
        schedule.addView(hours);
        begin.setOnClickListener(v->pickTime(true,begin));
        end.setOnClickListener(v->pickTime(false,end));
        space(schedule,11);
        schedule.addView(text("Çalışma günleri",14,WHITE,true));
        String[] names={"Pzt","Sal","Çar","Per","Cum","Cmt","Paz"};
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout line=null;
        for(int i=0;i<7;i++){
            if(i%4==0){line=new LinearLayout(this);rows.addView(line);}
            int day=i;CheckBox cb=new CheckBox(this);cb.setText(names[i]);cb.setTextColor(WHITE);
            cb.setButtonTintList(ColorStateList.valueOf(CYAN));cb.setTextSize(12);
            cb.setChecked((prefs.days()&(1<<i))!=0);
            cb.setOnCheckedChangeListener((v,checked)->{
                int mask=prefs.days();
                prefs.days(checked?mask|(1<<day):mask&~(1<<day));
                if(RecorderService.running)send(RecorderService.ACTION_REFRESH);
            });
            line.addView(cb,new LinearLayout.LayoutParams(0,dp(46),1f));
        }
        schedule.addView(rows);

        LinearLayout folder=card("Kayıt klasörü","Dosyalar yalnızca seçtiğin klasöre aktarılır.");
        folderText=text(prefs.folder()==null?"Henüz seçilmedi":"Klasör izni var ✓",14,CYAN,true);
        folder.addView(folderText);space(folder,10);
        Button choose=button("Klasör seç",0xff293752);folder.addView(choose);
        choose.setOnClickListener(v->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,FOLDER);
        });
        space(folder,10);
        Button restore=button("Şifreli yedekleri kurtar",0xff293752);
        folder.addView(restore);
        restore.setOnClickListener(v->{
            if(RecorderService.running)send(RecorderService.ACTION_RECOVER);
            else message("Kurtarmak için hizmeti başlat.");
        });

        LinearLayout ram=card("Gecko tarzı RAM kaydı","Son "+RecorderService.ramMinutes()+" dakikalık ses RAM'de tutulur.");
        Button save=button("RAM geçmişini WAV olarak kaydet",PURPLE);ram.addView(save);
        save.setOnClickListener(v->{
            if(!RecorderService.running || !Prefs.MODE_CONTINUOUS.equals(prefs.mode())){
                message("Önce Sürekli Kayıt modunu aç.");return;
            }
            send(RecorderService.ACTION_SAVE_LAST);message("Kaydetme istendi.");
        });

        LinearLayout battery=card("Pil ve performans","Gerçek pil tüketimi henüz ölçülmedi.");
        battery.addView(text("Mesai Kaydı: 08.00–17.30 örneğinde 9,5 saat. Sürekli Kayıt: 24 saat. Sürekli mod 2,53 kat daha uzun kayıt süresi anlamına gelir; pil tüketimi aynı oranda artmak zorunda değildir.",13,MUTED,false));
        LinearLayout ai=card("Yerel yapay zeka ve günlük rapor",
            "Tüm ses dosyasını cihazda yazıya çevirir, zamanlı özet ve tam döküm oluşturur.");
        ai.addView(text("Türkçe modeli bir kez indir. Mesai kayıtların şarjdayken otomatik özetlenir.",13,MUTED,false));
        space(ai,10);
        Button summary=button("Yerel yapay zeka / Günlük rapor",PURPLE);
        ai.addView(summary);
        summary.setOnClickListener(v->startActivity(new Intent(this,SummaryActivity.class)));

        LinearLayout permissions=card("İzinler ve arka plan",
            "Uygulamanın istediği izinleri sen kontrol edersin.");
        TextView permissionLabel=text("",13,MUTED,false);
        permissions.addView(permissionLabel);
        permissionLabel.setText(
            "Mikrofon: "+(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED?"izinli":"izin gerekli")+
            "\nBildirim: "+(Build.VERSION.SDK_INT<33||
              checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"izinli":"izin gerekli")+
            "\nKlasör: "+(prefs.folder()!=null?"seçildi":"seçilmedi"));
        space(permissions,10);
        Button grantMic=button("Mikrofon iznini ver",0xff293752);
        permissions.addView(grantMic);
        grantMic.setOnClickListener(v->{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);
            else message("Mikrofon izni zaten verilmiş.");
        });
        Button grantNotifications=button("Bildirim iznini ver",0xff293752);
        permissions.addView(grantNotifications);
        grantNotifications.setOnClickListener(v->{
            if(Build.VERSION.SDK_INT>=33&&
               checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);
            else message("Bildirim izni zaten verilmiş.");
        });
        Button batterySettingsButton=button("Samsung pil ve arka plan ayarları (isteğe bağlı)",0xff293752);
        permissions.addView(batterySettingsButton);
        batterySettingsButton.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName())));}
        });
        layout.addView(text("Android 14+ mikrofon kısıtı: Hizmeti uygulama açıkken başlatmalısın. Sistem servisi durdurursa otomatik kayıt kesilebilir.",12,MUTED,false));
        refreshStatus();
    }
    private void pickTime(boolean first,Button btn){
        new TimePickerDialog(this,(v,h,m)->{
            if(first)prefs.start(h,m);else prefs.end(h,m);
            btn.setText((first?"Başla: ":"Bitir: ")+prefs.timeString(first));
            if(!prefs.validRange())message("Başlangıç bitişten önce olmalı.");
            if(RecorderService.running)send(RecorderService.ACTION_REFRESH);
            refreshStatus();
        },first?prefs.startHour():prefs.endHour(),first?prefs.startMinute():prefs.endMinute(),true).show();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=FOLDER || result!=RESULT_OK || data==null || data.getData()==null)return;
        try{
            Uri uri=data.getData();
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.folder(uri);folderText.setText("Klasör izni var ✓");message("Klasör kaydedildi.");
        }catch(Exception e){message("Klasör erişim hatası: "+e.getMessage());}
    }
    private void start(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;
        }
        if(prefs.folder()==null){message("Önce kayıt klasörünü seç.");return;}
        if(Prefs.MODE_WORK.equals(prefs.mode())&&(!prefs.validRange()||prefs.days()==0)){
            message("Geçerli gün ve saat belirle.");return;
        }
        prefs.setSetupDone(true);
        try{startForegroundService(new Intent(this,RecorderService.class).setAction(RecorderService.ACTION_START));}
        catch(Exception e){message("Başlatma hatası: "+e.getMessage());}
        handler.postDelayed(this::refreshStatus,1000);
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
        super.onRequestPermissionsResult(request,names,results);
        if(request==MIC&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)
            start();
        else if(request==NOTIFY)
            message(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED?
                "Bildirimlere izin verildi.":"Bildirim izni verilmedi; kayıt bildirimi görünmeyebilir.");
        else message("Mikrofon izni gerekli.");
    }
    private void stop(){send(RecorderService.ACTION_STOP);handler.postDelayed(this::refreshStatus,700);}
    private void send(String action){
        try{startService(new Intent(this,RecorderService.class).setAction(action));}
        catch(Exception e){message("İşlem hatası: "+e.getMessage());}
    }
    private void refreshStatus(){
        if(status==null)return;
        status.setText(RecorderService.running?RecorderService.stateText:"Kayıt hizmeti kapalı");
        detail.setText(RecorderService.lastEvent);
        if(serviceButton!=null)serviceButton.setText(RecorderService.running?
            "Kayıt hizmetini durdur":"Kayıt hizmetini etkinleştir");
        if(modeDetail!=null)modeDetail.setText(Prefs.MODE_WORK.equals(prefs.mode())?
            "Mesai: "+prefs.timeString(true)+"–"+prefs.timeString(false)+" · Otomatik kaydetme":
            RecorderService.ramMinutes()+" dakikalık döngüsel RAM · Elle WAV kaydetme");
    }
    private void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
