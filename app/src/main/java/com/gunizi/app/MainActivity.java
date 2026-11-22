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
    private Button action,saveHistoryButton;
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
        b.setMinHeight(dp(48));return b;
    }
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}
    private void show(int selected){
        tab=selected;
        LinearLayout shell=column();shell.setBackgroundColor(BG);
        shell.setPadding(dp(17),dp(19),dp(17),dp(9));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=label("◉ Günİzi",26,WHITE,true);header.addView(logo,new LinearLayout.LayoutParams(0,-2,1f));
        TextView caption=label("ÖZEL • ÇEVRİMDIŞI",10,CYAN,true);
        header.addView(caption);shell.addView(header);gap(shell,13);
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);
        page=column();page.setPadding(dp(1),dp(3),dp(1),dp(8));sc.addView(page);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1f));
        gap(shell,7);
        nav=row();
        String[] tabs={"Kayıt","Mesai","Rapor","Ayarlar"};
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
        LinearLayout intro=card("Geçmiş Kaydı");
        muted(intro,"Dinlemeyi açtığında sesler geçici geçmişe eklenir. "+
            "İstediğin anda son 1 dakika ile son 6 saat arasındaki birikmiş sesi kaydedebilirsin.");
        gap(intro,7);
        muted(intro,"Bu ekran gelecekteki süreyi kaydetmez: seçimin, dinlemenin "+
            "başladığı andan itibaren biriken GEÇMİŞ sese uygulanır.");

        LinearLayout live=card("Canlı geçmiş");
        historyGauge=new HistoryGaugeView(this);
        live.addView(historyGauge,new LinearLayout.LayoutParams(-1,dp(220)));
        status=label("Dinleme kapalı",22,CYAN,true);
        live.addView(status);gap(live,4);
        substatus=label("Dinlemeyi açınca sayaç ilerler.",13,MUTED,false);
        live.addView(substatus);gap(live,12);
        action=button("Dinlemeyi aç",true);
        live.addView(action);
        action.setOnClickListener(v->{
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

        LinearLayout choose=card("Geçmişten ne kadarını kaydetmek istersin?");
        muted(choose,"Seçilen süre geriye doğrudur. Henüz o kadar geçmiş birikmediyse "+
            "kullanılabilen süre kaydedilir.");
        gap(choose,12);
        int[] minutes={1,5,15,60,120,240,360};
        String[] titles={"1 dk","5 dk","15 dk","1 saat","2 saat","4 saat","6 saat"};
        for(int startIndex=0;startIndex<minutes.length;startIndex+=4){
            LinearLayout row=row();
            for(int i=startIndex;i<Math.min(startIndex+4,minutes.length);i++){
                final int duration=minutes[i];
                Button b=button(titles[i],false);
                b.setTextSize(12);
                b.setTextColor(prefs.durationMinutes()==duration?CYAN:WHITE);
                b.setBackground(drawable(prefs.durationMinutes()==duration?0xff244d57:EDGE));
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(46),1);
                if(i<Math.min(startIndex+4,minutes.length)-1)cell.rightMargin=dp(7);
                row.addView(b,cell);
                b.setOnClickListener(v->{
                    prefs.durationMinutes(duration);
                    show(0);
                });
            }
            choose.addView(row);
            gap(choose,8);
        }
        saveHistoryButton=button("Son "+formatDuration(prefs.durationMinutes())+" kaydet",true);
        choose.addView(saveHistoryButton);
        saveHistoryButton.setOnClickListener(v->{
            if(!RecorderService.historyActive){toast("Önce dinlemeyi aç.");return;}
            if(RecorderService.historyHeldSeconds<1){toast("Henüz geçmiş birikmedi.");return;}
            if(prefs.folder()==null){toast("Önce bir kayıt klasörü seç.");return;}
            send(RecorderService.ACTION_SAVE_HISTORY);
            toast("Biriken geçmiş kaydediliyor. Dinleme devam eder.");
        });
        gap(choose,6);
        muted(choose,"Kaydetmek, dinlemeyi sonlandırmaz. En fazla son 6 saat korunur.");

        LinearLayout folder=card("Kayıt klasörü");
        folderLabel=label(prefs.folder()==null?"Klasör seçilmedi":"Kayıt klasörü hazır ✓",
            13,prefs.folder()==null?0xffedb46f:CYAN,true);
        folder.addView(folderLabel);gap(folder,8);
        Button pick=button("Klasör seç / değiştir",false);
        folder.addView(pick);pick.setOnClickListener(v->chooseFolder());
        gap(folder,4);
        muted(folder,"Gizlilik: Dinleme sırasında sesler sıkıştırılmış şekilde geçici uygulama "+
            "bellek alanına yazılır. 'Kaydet' demedikçe seçilen klasöre aktarılmaz. "+
            "Dinlemeyi durdurunca kaydedilmeyen parçalar silinir.");
    }
    private static String formatDuration(int m){
        return m<60?m+" dakikayı":m/60+" saati";
    }
    private void workTab(){
        LinearLayout box=card("Mesai Kaydı");
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
            if(RecorderService.historyActive){
                toast("Mesaiyi başlatmadan önce Kayıt sekmesindeki geçmiş dinlemeyi durdur.");
            }else if(RecorderService.running){
                send(RecorderService.ACTION_STOP);
                handler.postDelayed(()->show(1),650);
            }else startWork();
        });
        gap(box,9);
        muted(box,"Hizmet etkin kaldığı sürece saatleri takip eder. Sistem hizmeti sonlandırırsa kayıt kendiliğinden yeniden başlayamayabilir.");
        LinearLayout folder=card("Otomatik kayıt dosyaları");
        folderLabel=label(prefs.folder()==null?"Klasör seçilmedi":"Klasör seçildi ✓",14,
            prefs.folder()==null?0xffedb46f:CYAN,true);
        folder.addView(folderLabel);gap(folder,8);
        Button pick=button("Kayıt klasörünü seç",false);folder.addView(pick);
        pick.setOnClickListener(v->chooseFolder());
    }
    private void reportTab(){
        LinearLayout overview=card("Günlük Özet");
        muted(overview,"Uzun kayıtları parçalı şekilde yazıya dönüştürür; dil modeli kurulmuşsa "
            +"bütün parçaları birleştirerek ayrıntılı rapor hazırlar.");
        gap(overview,12);
        String last=getSharedPreferences("gunizi",MODE_PRIVATE).getString("summaryLast","");
        String err=getSharedPreferences("gunizi",MODE_PRIVATE).getString("summaryError","");
        overview.addView(label(!err.isEmpty()?"Son rapor hatası: "+err:
            !last.isEmpty()?last:"Henüz rapor oluşturulmadı.",13,err.isEmpty()?CYAN:0xffdf8a7d,false));
        gap(overview,10);
        Button open=button("Yerel yapay zeka ve raporlar",true);
        overview.addView(open);
        open.setOnClickListener(v->startActivity(new Intent(this,SummaryActivity.class)));
        gap(overview,7);
        Button topicTable=button("Konu konu rapor tablosu",false);
        overview.addView(topicTable);
        topicTable.setOnClickListener(v->startActivity(new Intent(this,ReportTableActivity.class)));
        gap(overview,7);
        Button savedNotes=button("Notlarım",false);
        overview.addView(savedNotes);
        savedNotes.setOnClickListener(v->startActivity(new Intent(this,UserNotesActivity.class)));
        LinearLayout info=card("Gerçekten çevrimdışı");
        muted(info,"Sesler veya konuşma dökümleri sunucuya gönderilmez. "
            +"Yalnızca modellerin ilk indirmesi için internet gerekir. "
            +"9 saatlik kayıtların çözülmesi uzun sürebilir ve şarj gerektirebilir.");
    }
    private void settingsTab(){
        LinearLayout updates=card("Uygulama güncellemeleri");
        muted(updates,"Yeni sürümleri GitHub Releases üzerinden arka planda kontrol eder. "+
            "Wi-Fi bağlantısında APK'yı indirebilir. Kurulumu her zaman sen onaylarsın.");
        gap(updates,9);
        Switch auto=new Switch(this);auto.setText("Otomatik güncelleme kontrolü");
        auto.setTextColor(WHITE);auto.setChecked(AppUpdateWorker.enabled(this));
        updates.addView(auto);
        auto.setOnCheckedChangeListener((v,on)->AppUpdateWorker.enable(this,on));
        gap(updates,8);
        Button update=button("Güncellemeleri kontrol et",false);
        updates.addView(update);
        update.setOnClickListener(v->startActivity(new Intent(this,UpdateActivity.class)));

        LinearLayout permissions=card("İzinler");
        muted(permissions,"Gerekli izinleri bir kez ver. Hiçbir izin senin onayın olmadan etkinleştirilmez.");
        gap(permissions,10);
        boolean mic=checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
        boolean note=Build.VERSION.SDK_INT<33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        permissions.addView(label("Mikrofon  "+(mic?"✓":"izin gerekli")+
            "     Bildirim  "+(note?"✓":"izin gerekli"),13,mic&&note?CYAN:MUTED,false));
        gap(permissions,8);
        Button microphone=button("Mikrofon izni",false);permissions.addView(microphone);
        microphone.setOnClickListener(v->{
            if(!mic)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);
            else toast("Mikrofon izni var.");
        });
        gap(permissions,7);
        Button notify=button("Bildirim izni",false);permissions.addView(notify);
        notify.setOnClickListener(v->{
            if(!note&&Build.VERSION.SDK_INT>=33)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFY);
            else toast("Bildirim izni var.");
        });
        LinearLayout storage=card("Depolama ve yedekleme");
        folderLabel=label(prefs.folder()==null?"Klasör seçilmedi":"Kayıt klasörü seçildi ✓",14,CYAN,true);
        storage.addView(folderLabel);gap(storage,9);
        Button choose=button("Kayıt klasörünü değiştir",false);storage.addView(choose);
        choose.setOnClickListener(v->chooseFolder());
        gap(storage,7);
        Button recover=button("Şifreli yedekleri kurtar",false);storage.addView(recover);
        recover.setOnClickListener(v->send(RecorderService.ACTION_RECOVER));
        LinearLayout recordingNotice=card("Telefon görüşmeleri");
        muted(recordingNotice,"Günİzi normal mikrofon izniyle hücresel aramaların iki tarafını "+
            "güvenilir şekilde kaydedemez. Telefonunun kendi arama kaydı işlevi "+
            "destekleniyorsa onu kullanabilirsin. Görüşmeleri kaydetmek için gerekli "+
            "bilgilendirme ve izinleri gözetmelisin.");
        LinearLayout notice=card("Sessiz kayıt bildirimi");
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
        LinearLayout battery=card("Pil ve gizlilik");
        Button compare=button("Pil tüketimi karşılaştırması",false);battery.addView(compare);
        compare.setOnClickListener(v->startActivity(new Intent(this,BatteryInfoActivity.class)));
        gap(battery,7);
        Button optimize=button("Samsung arka plan / pil ayarları",false);battery.addView(optimize);
        optimize.setOnClickListener(v->{
            try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName())));}
        });
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
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC);return;
        }
        if(!RecorderService.ACTION_START_HISTORY.equals(actionName)&&prefs.folder()==null){
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
                if(tab==0)startHistory();
                else if(tab==1)startWork();
                else toast("Mikrofon izni verildi. Kayıt kendiliğinden başlamaz.");
            }else toast("Bildirim izni verildi.");
        }else toast("İzin verilmedi.");show(tab);
    }
    private void refresh(){
        if(tab==0){
            boolean active=RecorderService.historyActive;
            int held=active?RecorderService.historyHeldSeconds:0;
            if(historyGauge!=null)historyGauge.update(held);
            if(status!=null)status.setText(active?"Dinleme açık":"Dinleme kapalı");
            if(substatus!=null)substatus.setText(active?
                "Kaydedilmiş geçmiş: "+String.format(Locale.ROOT,"%02d:%02d:%02d",
                    held/3600,(held/60)%60,held%60)+" / 06:00:00":
                "Dinlemeyi açınca geçmiş birikmeye başlar.");
            if(action!=null)action.setText(active?"Dinlemeyi durdur":"Dinlemeyi aç");
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
            if(status!=null)status.setText(RecorderService.historyActive?
                "Geçmiş dinleme açık":
                RecorderService.running?RecorderService.stateText:"Mesai planı hazır");
            if(substatus!=null)substatus.setText(RecorderService.running?
                RecorderService.lastEvent:prefs.timeString(true)+"–"+prefs.timeString(false)+
                " • Telefon saatine göre");
            if(action!=null){
                action.setText(RecorderService.historyActive?"Önce geçmiş dinlemeyi durdur":
                    RecorderService.running?"Mesai hizmetini durdur":"Mesai kaydını etkinleştir");
                action.setEnabled(!RecorderService.historyActive);
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
