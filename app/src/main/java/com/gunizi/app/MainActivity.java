package com.gunizi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/**
 * Compact Turkish interface. Four independent tabs, never one endless settings column.
 * The microphone never starts automatically in On-Demand mode.
 */
public final class MainActivity extends Activity {
    private static final int FOLDER=101,MIC=102,NOTIFY=103;
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca,PURPLE=0xff8360ed,EDGE=0xff263348;
    private Prefs prefs;
    private LinearLayout page,nav;
    private TextView status,substatus,folderLabel;
    private Button action,saveHistoryButton,scheduleActionButton;
    private TextView scheduleState;
    private String pendingMicAction;
    private HistoryGaugeView historyGauge;
    private int tab=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,1000);}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);prefs=new Prefs(this);AppUpdateWorker.schedule(this);show(0);
    }
    @Override protected void onResume(){super.onResume();handler.post(heartbeat);}
    @Override protected void onPause(){handler.removeCallbacks(heartbeat);super.onPause();}
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    private TextView label(String content,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
    }
    private GradientDrawable drawable(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout card(String title){
        LinearLayout box=column();box.setPadding(dp(16),dp(14),dp(16),dp(15));box.setBackground(drawable(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);page.addView(box,p);
        if(title!=null){box.addView(label(title,16,WHITE,true));gap(box,10);}
        return box;
    }
    private void muted(LinearLayout box,String text){box.addView(label(text,13,MUTED,false));}
    private Button button(String value,boolean accent){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(accent?BG:WHITE);b.setBackground(drawable(accent?CYAN:EDGE));
        b.setMinHeight(dp(48));b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setMaxLines(2);b.setGravity(Gravity.CENTER);return b;
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private void pair(LinearLayout parent,Button left,Button right){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(49),1);
        a.rightMargin=dp(8);
        line.addView(left,a);line.addView(right,new LinearLayout.LayoutParams(0,dp(49),1));
        parent.addView(line);
    }
    private LinearLayout accordion(String title,String hint,boolean expanded){
        LinearLayout wrapper=card(null);
        Button header=button((expanded?"▴ ":"▾ ")+title,false);
        header.setContentDescription(title+" bölümünü aç veya kapat");
        wrapper.addView(header,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView caption=label(hint,12,MUTED,false);
        LinearLayout.LayoutParams capLp=new LinearLayout.LayoutParams(-1,-2);
        capLp.topMargin=dp(5);wrapper.addView(caption,capLp);
        LinearLayout body=column();
        body.setVisibility(expanded?View.VISIBLE:View.GONE);
        wrapper.addView(body);
        header.setOnClickListener(v->{
            boolean open=body.getVisibility()!=View.VISIBLE;
            body.setVisibility(open?View.VISIBLE:View.GONE);
            header.setText((open?"▴ ":"▾ ")+title);
        });
        return body;
    }
    private void show(int selected){
        tab=selected;
        LinearLayout shell=column();shell.setBackgroundColor(BG);
        shell.setPadding(dp(13),dp(13),dp(13),dp(7));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=label("◉ Günİzi",24,WHITE,true);header.addView(logo,new LinearLayout.LayoutParams(0,-2,1f));
        TextView caption=label("ÖZEL • ÇEVRİMDIŞI",10,CYAN,true);
        header.addView(caption);shell.addView(header);gap(shell,13);
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);
        page=column();page.setPadding(dp(1),dp(3),dp(1),dp(8));sc.addView(page);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));
        gap(shell,7);
        nav=row();
        String[] tabs={"◉ Kayıt","◷ Mesai","✦ Yapay Zeka","⚙ Ayarlar"};
        for(int i=0;i<tabs.length;i++){
            final int target=i;
            Button b=button(tabs[i],false);b.setTextSize(12);
            b.setTextColor(tab==i?CYAN:MUTED);
            b.setBackground(drawable(tab==i?0xff243d47:PANEL));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),1);
            p.rightMargin=i<tabs.length-1?dp(5):0;nav.addView(b,p);
            b.setOnClickListener(v->show(target));
        }
        shell.addView(nav);
        setContentView(shell);
        if(tab==0)recordTab();
        else if(tab==1)workTab();
        else if(tab==2)reportTab();
        else settingsTab();
        refresh();
    }
    private void statusCard(LinearLayout box){
        status=label("Hazır",23,CYAN,true);box.addView(status);
        gap(box,5);substatus=label("",13,MUTED,false);box.addView(substatus);
    }
    private void recordTab(){
        page.addView(label("Geçmiş kaydı",22,WHITE,true));
        gap(page,3);
        page.addView(label("İstediğin an son dakikaları kaydet • Ses yalnızca dinleme açıkken birikir.",12,MUTED,false));
        gap(page,11);

        LinearLayout live=card("Canlı geçmiş");
        historyGauge=new HistoryGaugeView(this);
        live.addView(historyGauge,new LinearLayout.LayoutParams(-1,dp(136)));
        status=label("Dinleme kapalı",22,CYAN,true);
        live.addView(status);gap(live,4);
        substatus=label("Dinlemeyi açınca sayaç ilerler.",13,MUTED,false);
        live.addView(substatus);gap(live,12);
        action=button("Dinlemeyi aç",true);
        live.addView(action);
        action.setOnClickListener(v->{
            if(RecorderService.historyScheduledArmed){
                toast("Önce zamanlı geçmiş planını kapat.");return;
            }
            if(RecorderService.historyActive){
                new AlertDialog.Builder(this)
                    .setTitle("Geçmiş dinlemeyi durdur")
                    .setMessage("Henüz kaydetmediğin geçici geçmiş temizlenecek. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç",null)
                    .setPositiveButton("Durdur",(d,w)->{
                        send(RecorderService.ACTION_STOP);
                        handler.postDelayed(()->show(0),750);
                    }).show();
            }else{
                if(RecorderService.running){
                    toast("Önce Mesai kaydını durdur. Aynı anda iki mod açılamaz.");return;
                }
                startHistory();
            }
        });

        LinearLayout choose=card("Son geçmişi kaydet");
        muted(choose,"Süreyi seç; mikrofon açıldığından beri biriken geçmişten kaydet.");
        gap(choose,9);
        int[] minutes={1,5,15,60,120,240,360};
        String[] titles={"1 dk","5 dk","15 dk","1 saat","2 saat","4 saat","6 saat"};
        HorizontalScrollView durations=new HorizontalScrollView(this);
        durations.setHorizontalScrollBarEnabled(false);
        LinearLayout durationRow=row();
        durations.addView(durationRow);
        choose.addView(durations,new LinearLayout.LayoutParams(-1,dp(48)));
        for(int i=0;i<minutes.length;i++){
            final int duration=minutes[i];
            Button chip=button(titles[i],false);
            chip.setTextSize(12);
            chip.setTextColor(prefs.durationMinutes()==duration?BG:WHITE);
            chip.setBackground(drawable(prefs.durationMinutes()==duration?CYAN:EDGE));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(72),dp(44));
            lp.rightMargin=dp(6);durationRow.addView(chip,lp);
            chip.setOnClickListener(v->{prefs.durationMinutes(duration);show(0);});
        }
        gap(choose,8);
        saveHistoryButton=button("Son "+formatDuration(prefs.durationMinutes())+" kaydet",true);
        choose.addView(saveHistoryButton);
        saveHistoryButton.setOnClickListener(v->{
            if(!RecorderService.historyActive){toast("Önce dinlemeyi aç.");return;}
            if(RecorderService.liveHistorySeconds()<1){toast("Henüz geçmiş birikmedi.");return;}
            if(prefs.folder()==null){toast("Önce bir kayıt klasörü seç.");return;}
            send(RecorderService.ACTION_SAVE_HISTORY);
            toast("Biriken geçmiş kaydediliyor. Dinleme devam eder.");
        });
        gap(choose,5);
        muted(choose,"Dinleme devam eder; kaydedilmeyen geçmiş, durduğunda silinir.");
        timedHistoryCard();
        if(prefs.folder()==null){
            LinearLayout folder=card("Kayıt klasörü seçilmedi");
            muted(folder,"Ses kaydedebilmek için Ayarlar'dan klasör seç.");
            Button pick=button("Klasör seç",false);
            folder.addView(pick);pick.setOnClickListener(v->show(3));
        }
    }
    private static String formatDuration(int m){
        return m<60?m+" dakikayı":m/60+" saati";
    }
    private void workTab(){
        page.addView(label("Mesai kaydı",22,WHITE,true));gap(page,9);
        LinearLayout box=card("Mesai zamanlaması");
        muted(box,"Belirlediğin günlerde ve saatlerde otomatik kayıt planı. "
            +"Android gereği kayıt hizmetini uygulama açıkken etkinleştirmen gerekir.");
        gap(box,11);statusCard(box);gap(box,12);
        LinearLayout times=row();
        Button start=button("Başla "+prefs.timeString(true),false);
        Button end=button("Bitir "+prefs.timeString(false),false);
        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(48),1);
        p1.rightMargin=dp(9);
        times.addView(start,p1);times.addView(end,new LinearLayout.LayoutParams(0,dp(48),1));
        box.addView(times);
        start.setOnClickListener(v->pickTime(true,start));
        end.setOnClickListener(v->pickTime(false,end));
        gap(box,8);
        final String[] names={"Pzt","Sal","Çar","Per","Cum","Cmt","Paz"};
        LinearLayout weekdays=row();
        for(int i=0;i<7;i++){
            final int day=i;int bit=1<<i;
            Button b=button(names[i],false);b.setTextSize(11);
            b.setTextColor((prefs.days()&bit)!=0?CYAN:MUTED);
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(46),1);
            cell.rightMargin=i<6?dp(3):0;
            weekdays.addView(b,cell);
            b.setOnClickListener(v->{
                int mask=prefs.days();prefs.days((mask&bit)!=0?mask&~bit:mask|bit);
                if(RecorderService.running)send(RecorderService.ACTION_REFRESH);
                show(1);
            });
        }
        box.addView(weekdays);gap(box,12);
        action=button("Mesai kaydını etkinleştir",true);box.addView(action);
        action.setOnClickListener(v->{
            if(RecorderService.historyActive||RecorderService.historyScheduledArmed){
                toast("Mesaiyi başlatmadan önce zamanlı veya manuel geçmiş dinlemeyi kapat.");
            }else if(RecorderService.running){
                send(RecorderService.ACTION_STOP);
                handler.postDelayed(()->show(1),650);
            }else startWork();
        });
        gap(box,9);
        muted(box,"Hizmet etkin kaldığı sürece saatleri takip eder. Sistem hizmeti sonlandırırsa kayıt kendiliğinden yeniden başlayamayabilir.");
        LinearLayout folder=accordion("Kayıt klasörü","Mevcut kayıt konumunu incele veya değiştir",false);
        muted(folder,prefs.folder()==null?
            "Klasör seçilmedi. Ayarlar'dan kayıt klasörünü belirle.":
            "Klasör ayarlandı ✓");
        gap(folder,8);
        Button pick=button("Ayarlar • Klasörü değiştir",false);
        folder.addView(pick);pick.setOnClickListener(v->show(3));
    }
    private void reportTab(){
        page.addView(label("Yapay zeka",22,WHITE,true));gap(page,8);
        LinearLayout overview=card("Konuşmadan iş hafızasına");
        muted(overview,"Sesini yazıya dönüştür; kararları, görevleri ve beklenen dönüşleri takip et.");
        gap(overview,12);
        String last=getSharedPreferences("gunizi",MODE_PRIVATE).getString("summaryLast","");
        String err=getSharedPreferences("gunizi",MODE_PRIVATE).getString("summaryError","");
        overview.addView(label(!err.isEmpty()?"Son rapor hatası: "+err:
            !last.isEmpty()?last:"Henüz rapor oluşturulmadı.",13,err.isEmpty()?CYAN:0xffdf8a7d,false));
        gap(overview,10);
        Button open=button("＋  Ses kaydı analiz et",true);
        overview.addView(open);
        open.setOnClickListener(v->startActivity(new Intent(this,SummaryActivity.class)));
        gap(overview,10);
        Button memory=button("Günlük iş hafızam",false);
        memory.setOnClickListener(v->startActivity(new Intent(this,WorkMemoryActivity.class)));
        Button topicTable=button("Konu ve kararlar",false);
        topicTable.setOnClickListener(v->startActivity(new Intent(this,ReportTableActivity.class)));
        pair(overview,memory,topicTable);
        gap(overview,7);
        Button savedNotes=button("Notlarım",false);
        savedNotes.setOnClickListener(v->startActivity(new Intent(this,UserNotesActivity.class)));
        Button models=button("Yapay zeka modelleri",false);
        models.setOnClickListener(v->startActivity(new Intent(this,ModelSettingsActivity.class)));
        pair(overview,savedNotes,models);
        gap(overview,8);
        muted(overview,"Analiz cihazında yapılır. Uzun kayıtlar daha fazla işlem süresi ve pil kullanabilir.");
    }
    private void recordingSettings(){
        LinearLayout card=card("Ses kaydı ve dosya adı");
        RecordingOptions options=prefs.recordingOptions();
        muted(card,options.description());
        muted(card,"Önerilen: uzun konuşmalar için OGG Dengeli; paylaşım ve uyumluluk için M4A Dengeli. Yüksek kalite daha fazla yer kaplar.");
        muted(card,"Format ve kalite bir sonraki kayıt / geçmiş dinleme oturumunda geçerli olur. Aktif ses kesilmez.");
        gap(card,8);
        Button format=button("Format: "+RecordingOptions.FORMATS[options.format],false);
        format.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Kayıt formatı")
            .setSingleChoiceItems(RecordingOptions.FORMATS,options.format,(d,i)->{
                prefs.recordingOptions(i,options.quality);d.dismiss();show(3);
            }).setNegativeButton("Vazgeç",null).show());
        Button quality=button("Kalite: "+RecordingOptions.QUALITIES[options.quality],false);
        quality.setOnClickListener(v->{
            String[] choices=new String[3];
            for(int i=0;i<3;i++)choices[i]=RecordingOptions.QUALITIES[i]+" · "+
                new RecordingOptions(options.format,i).description()+(i==1?" (Önerilen)":"");
            new AlertDialog.Builder(this).setTitle("Kayıt kalitesi")
                .setSingleChoiceItems(choices,options.quality,(d,i)->{
                    prefs.recordingOptions(options.format,i);d.dismiss();show(3);
                }).setNegativeButton("Vazgeç",null).show();
        });
        pair(card,format,quality);
        gap(card,8);
        muted(card,"Örnek: "+prefs.recordingName("Mesai",java.time.LocalDateTime.now(Prefs.ZONE),options));
        Button naming=button("Dosya adını özelleştir",false);
        card.addView(naming);
        naming.setOnClickListener(v->showRecordingNameDialog(options));
    }
    private void showRecordingNameDialog(RecordingOptions options){
        LinearLayout body=column();
        body.setPadding(dp(22),dp(8),dp(22),dp(8));
        EditText prefix=new EditText(this);
        prefix.setSingleLine(true);prefix.setHint("Dosya adı ön eki");
        prefix.setText(prefs.recordingPrefix());
        body.addView(prefix);
        String[] labels={"Tarih (yıl-ay-gün)","Gün adı (Pazartesi vb.)","Saat ve dakika","Saniye","Kayıt türü (Mesai / Gecmis)"};
        int[] bits={RecordingOptions.DATE,RecordingOptions.DAY,RecordingOptions.TIME,RecordingOptions.SECONDS,RecordingOptions.MODE};
        CheckBox[] checks=new CheckBox[bits.length];
        TextView preview=new TextView(this);
        Runnable update=()->{
            int flags=0;
            for(int i=0;i<bits.length;i++)if(checks[i]!=null&&checks[i].isChecked())flags|=bits[i];
            preview.setText("Örnek: "+RecordingOptions.name(prefix.getText().toString(),flags,"Mesai",
                java.time.LocalDateTime.now(Prefs.ZONE),options.extension()));
        };
        for(int i=0;i<bits.length;i++){
            CheckBox box=new CheckBox(this);box.setText(labels[i]);
            box.setChecked((prefs.recordingNameFields()&bits[i])!=0);
            checks[i]=box;body.addView(box);box.setOnCheckedChangeListener((b,on)->update.run());
        }
        body.addView(preview);
        prefix.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}
            public void afterTextChanged(android.text.Editable s){}
        });
        update.run();
        new AlertDialog.Builder(this).setTitle("Dosya adı").setView(body)
            .setNegativeButton("Vazgeç",null).setPositiveButton("Kaydet",(d,w)->{
                int flags=0;for(int i=0;i<bits.length;i++)if(checks[i].isChecked())flags|=bits[i];
                prefs.recordingName(prefix.getText().toString(),flags);show(3);
            }).show();
    }
    private void settingsTab(){
        recordingSettings();
        LinearLayout updates=card("Uygulama güncellemeleri");
        boolean independent=AppUpdateWorker.independent(this);
        muted(updates,independent?
            "Yeni GitHub sürümlerini takip edebilirsin. Bu bağımsız paket diğer APK'nın "+
            "üzerine otomatik yüklenmez; kayıtların korunur.":
            "GitHub Releases üzerinden yeni sürümleri kontrol eder. Uyumlu ve "+
            "doğrulanmış APK varsa Wi-Fi'de indirebilir; kurulumu sen onaylarsın.");
        gap(updates,7);
        Switch auto=new Switch(this);
        auto.setText("Yeni sürümleri otomatik kontrol et");
        auto.setTextColor(WHITE);
        auto.setTextSize(14);
        auto.setChecked(AppUpdateWorker.enabled(this));
        auto.setEnabled(true);
        updates.addView(auto,new LinearLayout.LayoutParams(-1,dp(51)));
        auto.setOnCheckedChangeListener((v,on)->{
            AppUpdateWorker.enable(this,on);
            toast(on?"Sürüm kontrolü açıldı. Uygun olduğunda GitHub kontrol edilecek.":
                "Otomatik sürüm kontrolü kapatıldı.");
        });
        gap(updates,7);
        Button update=button("Sürüm kontrolü",false);
        update.setOnClickListener(v->startActivity(new Intent(this,UpdateActivity.class)));
        Button releases=button("GitHub Releases",false);
        releases.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(AppUpdateWorker.RELEASES))));
        pair(updates,update,releases);

        LinearLayout models=card("Yapay zeka modelleri");
        muted(models,"Whisper ve Qwen3 indirmeleri burada yönetilir. İndirme arka planda ilerler; "+
            "Yapay Zeka ekranı konuşma çözümleme ve rapor oluşturma içindir.");
        gap(models,9);
        Button modelSettings=button("Modelleri seç / İndirme durumunu gör",true);
        models.addView(modelSettings);
        modelSettings.setOnClickListener(v->startActivity(new Intent(this,ModelSettingsActivity.class)));
        LinearLayout permissions=accordion("İzinler","Mikrofon ve bildirim izinlerini yönet",false);
        muted(permissions,"Gerekli izinleri bir kez ver. Hiçbir izin senin onayın olmadan etkinleştirilmez.");
        gap(permissions,10);
        boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        boolean note=Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        permissions.addView(label("Mikrofon  "+(mic?"✓":"izin gerekli")+
            "     Bildirim  "+(note?"✓":"izin gerekli"),13,mic&&note?CYAN:MUTED,false));
        gap(permissions,8);
        Button microphone=button("Mikrofon izni",false);
        microphone.setOnClickListener(v->{
            if(!mic)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);
            else toast("Mikrofon izni var.");
        });
        gap(permissions,7);
        Button notify=button("Bildirim izni",false);
        notify.setOnClickListener(v->{
            if(!note&&Build.VERSION.SDK_INT>=33)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);
            else toast("Bildirim izni var.");
        });
        pair(permissions,microphone,notify);
        LinearLayout storage=accordion("Depolama ve yedekleme","Kayıt klasörü • Şifreli yedekler",false);
        folderLabel=label(prefs.folder()==null?"Klasör seçilmedi":"Kayıt klasörü seçildi ✓",14,CYAN,true);
        storage.addView(folderLabel);gap(storage,9);
        Button choose=button("Kayıt klasörü",false);
        choose.setOnClickListener(v->chooseFolder());
        gap(storage,7);
        Button recover=button("Şifreli yedekler",false);
        recover.setOnClickListener(v->send(RecorderService.ACTION_RECOVER));
        pair(storage,choose,recover);
        LinearLayout recordingNotice=accordion("Telefon görüşmeleri","Android mikrofon kısıtları ve bilgilendirme",false);
        muted(recordingNotice,"Günİzi normal mikrofon izniyle hücresel aramaların iki tarafını "+
            "güvenilir şekilde kaydedemez. Telefonunun kendi arama kaydı işlevi "+
            "destekleniyorsa onu kullanabilirsin. Görüşmeleri kaydetmek için gerekli "+
            "bilgilendirme ve izinleri gözetmelisin.");
        LinearLayout notice=accordion("Kayıt bildirimi","Android mikrofon bildirimi ve gizlilik",false);
        muted(notice,"Kayıt sırasında Android güvenlik gereği mikrofon bildirimini "+
            "gösterir. Günİzi bildirimi sessiz tutar; bildirim sırasını Samsung belirler.");
        gap(notice,8);
        Button quiet=button("Kayıt bildirim ayarlarını aç",false);notice.addView(quiet);
        quiet.setOnClickListener(v->{
            Intent intent=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID,"gunizi_recording_quiet_v2");
            try{startActivity(intent);}catch(Exception e){
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
            }
        });
        LinearLayout battery=accordion("Pil ve gizlilik","Pil karşılaştırması ve Samsung ayarları",false);
        Button compare=button("Pil tüketimi",false);
        compare.setOnClickListener(v->startActivity(new Intent(this,BatteryInfoActivity.class)));
        gap(battery,7);
        Button optimize=button("Samsung pil ayarları",false);
        optimize.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName())));}
        });
        pair(battery,compare,optimize);
        LinearLayout about=accordion("Hakkında • İletişim • Lisanslar",
            "Sürüm, geri bildirim ve açık kaynak lisansları",false);
        about.addView(label("Geliştiren: Numan Değirmenci",15,WHITE,true));
        gap(about,7);
        String version="Günİzi";
        try{
            version="Günİzi v"+getPackageManager().getPackageInfo(
                getPackageName(),0).versionName;
        }catch(Exception ignored){}
        muted(about,version+" • Yerel ses kaydı ve yapay zeka");
        gap(about,9);
        Button feedback=button("Görüş / hata bildir • E-posta",false);
        about.addView(feedback);
        feedback.setOnClickListener(v->{
            Intent mail=new Intent(Intent.ACTION_SENDTO,
                Uri.parse("mailto:numandegirmenc@gmail.com"));
            mail.putExtra(Intent.EXTRA_SUBJECT,"Günİzi • Geri bildirim");
            try{startActivity(mail);}catch(Exception e){
                toast("E-posta uygulaması bulunamadı: numandegirmenc@gmail.com");
            }
        });
        gap(about,7);
        Button github=button("GitHub'da Günİzi projesini aç",false);
        about.addView(github);
        github.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/numandegirmenci/gunizi-android"))));
        gap(about,7);
        muted(about,"numandegirmenc@gmail.com");
        gap(about,14);
        about.addView(label("Yasal bilgiler",15,WHITE,true));
        muted(about,"Kullanılan açık kaynak bileşenlerin lisansları ve geliştirici atıfları.");
        Button licenses=button("Açık kaynak lisanslarını görüntüle ▼",false);
        about.addView(licenses);
        LinearLayout credits=new LinearLayout(this);
        credits.setOrientation(LinearLayout.VERTICAL);
        credits.setVisibility(View.GONE);
        about.addView(credits);
        muted(credits,"Günİzi, CallVault kaynak kodundan uyarlanmış bağımsız ve değiştirilmiş bir uygulamadır. "+
            "CallVault veya ShizuCallRecorder'ın resmi sürümü değildir.");
        Button upstream=button("CallVault • Kaynak kodu ve GPL lisansı",false);
        credits.addView(upstream);
        upstream.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/madkongo/CallVault"))));
        Button original=button("Orijinal proje • ShizuCallRecorder",false);
        credits.addView(original);
        original.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/kitsumed/ShizuCallRecorder"))));
        muted(credits,"Telif hakları ve lisanslar korunur. Ayrıntılar: GitHub → LICENSE ve THIRD_PARTY_NOTICES.md.");
        licenses.setOnClickListener(v->{
            boolean open=credits.getVisibility()!=View.VISIBLE;
            credits.setVisibility(open?View.VISIBLE:View.GONE);
            licenses.setText(open?"Lisans bilgilerini gizle ▲":"Açık kaynak lisanslarını görüntüle ▼");
        });
    }
    private void timedHistoryCard(){
        LinearLayout plan=accordion("Zamanlı geçmiş dinleme",
            RecorderService.historyScheduledArmed?
                "Plan etkin • "+prefs.historyTimeString(true)+"–"+prefs.historyTimeString(false):
                "İsteğe bağlı • Günleri ve saatleri belirle",false);
        muted(plan,"Gün ve saatleri seç. Plan açıkken yalnızca seçili zaman diliminde mikrofonla geçmiş ses biriktirilir.");
        gap(plan,10);
        LinearLayout times=row();
        Button begin=button("Başla "+prefs.historyTimeString(true),false);
        Button finish=button("Bitir "+prefs.historyTimeString(false),false);
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(46),1);
        left.rightMargin=dp(8);
        times.addView(begin,left);
        times.addView(finish,new LinearLayout.LayoutParams(0,dp(46),1));
        plan.addView(times);
        begin.setOnClickListener(v->pickHistoryTime(true));
        finish.setOnClickListener(v->pickHistoryTime(false));
        gap(plan,10);
        LinearLayout days=row();
        String[] names={"Pzt","Sal","Çar","Per","Cum","Cmt","Paz"};
        for(int i=0;i<names.length;i++){
            final int bit=1<<i;
            Button day=button(names[i],false);
            day.setTextSize(11);
            day.setTextColor((prefs.historyDays()&bit)!=0?CYAN:MUTED);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
            if(i<6)lp.rightMargin=dp(3);
            days.addView(day,lp);
            day.setOnClickListener(v->{
                int mask=prefs.historyDays();
                prefs.historyDays((mask&bit)!=0?mask&~bit:mask|bit);
                if(RecorderService.historyScheduledArmed)send(RecorderService.ACTION_REFRESH);
                show(0);
            });
        }
        plan.addView(days);
        gap(plan,11);
        scheduleState=label("Plan kapalı",13,CYAN,true);plan.addView(scheduleState);
        gap(plan,9);
        scheduleActionButton=button("Zamanlı dinlemeyi etkinleştir",true);
        plan.addView(scheduleActionButton);
        scheduleActionButton.setOnClickListener(v->{
            if(RecorderService.historyScheduledArmed){
                new AlertDialog.Builder(this)
                    .setTitle("Zamanlı dinlemeyi kapat")
                    .setMessage("Mikrofon duracak. Kaydedilmemiş geçici geçmiş temizlenecek.")
                    .setNegativeButton("Vazgeç",null)
                    .setPositiveButton("Planı kapat",(d,w)->{
                        send(RecorderService.ACTION_DISARM_HISTORY_SCHEDULE);
                        handler.postDelayed(()->show(0),550);
                    }).show();
                return;
            }
            if(RecorderService.running||RecorderService.historyActive){
                toast("Önce diğer kayıt hizmetini durdur.");return;
            }
            if(!prefs.validHistorySchedule()){
                toast("En az bir gün seç ve başlangıç/bitiş saatlerini farklı yap.");return;
            }
            prefs.mode(Prefs.MODE_HISTORY);
            startServiceAction(RecorderService.ACTION_ARM_HISTORY_SCHEDULE);
            handler.postDelayed(()->show(0),550);
        });
        gap(plan,9);
        muted(plan,"Saat dışında mikrofon tamamen kapanır; hafif bir Android bildirimi planı izlemeye devam eder. "+
            "Ekran kapalıyken saat geçişleri gecikebilir. Sistem hizmeti durdurursa uygulamayı açıp planı yeniden etkinleştir.");
        gap(plan,6);
        muted(plan,"Önemli: Zaman aralığı sonunda kaydedilmemiş geçmiş silinir. "+
            "Saklamak istediğin sesi süre dolmadan 'Son geçmişi kaydet' ile kaydet. Geceye taşan vardiyalar desteklenir.");
    }
    private void pickHistoryTime(boolean start){
        new TimePickerDialog(this,(view,h,m)->{
            prefs.historyTime(start,h,m);
            if(RecorderService.historyScheduledArmed)send(RecorderService.ACTION_REFRESH);
            show(0);
        },start?prefs.historyStartHour():prefs.historyEndHour(),
            start?prefs.historyStartMinute():prefs.historyEndMinute(),true).show();
    }
    private void pickTime(boolean first,Button b){
        new TimePickerDialog(this,(v,h,m)->{
            if(first)prefs.start(h,m);else prefs.end(h,m);
            if(!prefs.validRange()){toast("Başlangıç saati bitişten önce olmalı.");return;}
            if(RecorderService.running)send(RecorderService.ACTION_REFRESH);
            show(1);
        },first?prefs.startHour():prefs.endHour(),
            first?prefs.startMinute():prefs.endMinute(),true).show();
    }
    private void chooseFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,FOLDER);
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            Uri uri=data.getData();
            getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            prefs.folder(uri);show(tab);toast("Kayıt klasörü belirlendi.");
        }catch(Exception e){toast("Klasör hatası: "+e.getMessage());}
    }
    private void startHistory(){
        prefs.mode(Prefs.MODE_HISTORY);startServiceAction(RecorderService.ACTION_START_HISTORY);
    }
    private void startWork(){
        prefs.mode(Prefs.MODE_WORK);
        if(!prefs.validRange()||prefs.days()==0){
            toast("Önce geçerli saatleri ve çalışma günlerini seç.");return;
        }
        startServiceAction(RecorderService.ACTION_START);
    }
    private void startServiceAction(String actionName){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            pendingMicAction=actionName;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;
        }
        if(!RecorderService.ACTION_START_HISTORY.equals(actionName)&&
            !RecorderService.ACTION_ARM_HISTORY_SCHEDULE.equals(actionName)&&prefs.folder()==null){
            toast("Önce kayıt klasörünü seç.");return;
        }
        try{
            startForegroundService(new Intent(this,RecorderService.class).setAction(actionName));
            handler.postDelayed(this::refresh,900);
        }catch(Exception e){toast("Kayıt başlatılamadı: "+e.getMessage());}
    }
    private void send(String a){
        try{startService(new Intent(this,RecorderService.class).setAction(a));}
        catch(Exception e){toast("İşlem başarısız: "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] names,int[] results){
        super.onRequestPermissionsResult(request,names,results);
        if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
            if(request==MIC){
                String pending=pendingMicAction;pendingMicAction=null;
                if(pending!=null)startServiceAction(pending);
                else toast("Mikrofon izni verildi. Kaydı başlatabilirsin.");
            }else toast("Bildirim izni verildi.");
        }else toast("İzin verilmedi.");show(tab);
    }
    private void refresh(){
        if(tab==0){
            boolean active=RecorderService.historyActive;
            int held=active?RecorderService.liveHistorySeconds():0;
            if(historyGauge!=null)historyGauge.update(held);
            boolean armed=RecorderService.historyScheduledArmed;
            if(status!=null)status.setText(active?
                (armed?"Zamanlı dinleme açık":"Dinleme açık"):
                armed?"Zamanlı plan beklemede • Mikrofon kapalı":"Dinleme kapalı");
            if(substatus!=null)substatus.setText(active?
                "Biriken geçici geçmiş: "+String.format(Locale.ROOT,"%02d:%02d:%02d",
                    held/3600,(held/60)%60,held%60)+" / 06:00:00":
                armed?"Mikrofon kapalı • "+prefs.historyTimeString(true)+"–"+
                    prefs.historyTimeString(false)+" aralığı bekleniyor":
                "Dinlemeyi açınca geçmiş birikmeye başlar.");
            if(action!=null){
                action.setText(armed?"Zamanlı plan yönetiliyor":
                    active?"Dinlemeyi durdur":"Dinlemeyi aç");
                action.setEnabled(!armed);
            }
            if(scheduleState!=null){
                String detail=armed?
                    (active?"Plan çalışıyor • Geçmiş birikiyor":
                        "Plan etkin • Saat bekleniyor • Mikrofon kapalı"):
                    prefs.historyScheduleEnabled()?
                        "Plan kayıtlı ancak hizmet durmuş • Yeniden etkinleştir":
                        "Plan kapalı";
                scheduleState.setText(detail);
            }
            if(scheduleActionButton!=null)
                scheduleActionButton.setText(armed?
                    "Zamanlı dinlemeyi kapat":
                    prefs.historyScheduleEnabled()?"Planı yeniden etkinleştir":
                        "Zamanlı dinlemeyi etkinleştir");
            if(saveHistoryButton!=null){
                int requested=prefs.durationMinutes()*60;
                int actual=Math.min(requested,held);
                String label=held==0?"Henüz kaydedilecek geçmiş yok":
                    actual<requested?"Mevcut "+formatPast(actual)+" kaydet":
                    "Son "+formatDuration(prefs.durationMinutes())+" kaydet";
                saveHistoryButton.setText(label);
                saveHistoryButton.setEnabled(active&&held>0&&prefs.folder()!=null);
            }
        }else if(tab==1){
            if(status!=null)status.setText(RecorderService.historyActive||
                RecorderService.historyScheduledArmed?
                "Önce geçmiş dinleme planını kapat":
                RecorderService.running?RecorderService.stateText:"Mesai planı hazır");
            if(substatus!=null)substatus.setText(RecorderService.running?
                RecorderService.lastEvent:prefs.timeString(true)+"–"+prefs.timeString(false)+
                " • Telefon saatine göre");
            if(action!=null){
                action.setText((RecorderService.historyActive||
                    RecorderService.historyScheduledArmed)?"Önce geçmiş planını kapat":
                    RecorderService.running?"Mesai hizmetini durdur":"Mesai kaydını etkinleştir");
                action.setEnabled(!RecorderService.historyActive&&
                    !RecorderService.historyScheduledArmed);
            }
        }
    }
    private static String formatPast(int seconds){
        if(seconds<60)return seconds+" saniyeyi";
        if(seconds<3600)return (seconds/60)+" dakikayı";
        return (seconds/3600)+" saat "+((seconds/60)%60)+" dakikayı";
    }
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
