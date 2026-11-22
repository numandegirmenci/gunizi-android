package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import java.io.File;
import java.util.List;

/** Compact, task-first offline AI model selection. Downloads remain explicit. */
public final class ModelSettingsActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,3500);}
    };
    private TextView whisperStatus,qwenStatus,selectedSpeech,selectedSummary,presetHint;
    private Button whisperButton,qwenButton;
    private RadioGroup speechChoices,summaryChoices;
    private AnalysisPreferences analysis;
    private Prefs preferences;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;
    }
    private TextView text(LinearLayout root,String value,int size,boolean strong){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(strong?WHITE:MUTED);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(7);root.addView(t,p);return t;
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15),dp(15),dp(15),dp(14));c.setBackground(shape(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(12);parent.addView(c,p);return c;
    }
    private Button button(LinearLayout root,String title,boolean primary){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(primary?BG:WHITE);b.setBackground(shape(primary?CYAN:0xff27364b));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));
        p.bottomMargin=dp(5);root.addView(b,p);return b;
    }
    private Button chip(LinearLayout root,String title){
        Button b=new Button(this);b.setText(title);b.setAllCaps(false);
        b.setTextSize(12);b.setTextColor(WHITE);b.setBackground(shape(0xff283b51));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(44));
        p.rightMargin=dp(8);root.addView(b,p);return b;
    }
    private RadioButton option(RadioGroup group,String title){
        RadioButton b=new RadioButton(this);b.setId(View.generateViewId());
        b.setText(title);b.setTextColor(WHITE);b.setTextSize(14);
        b.setPadding(0,dp(3),0,dp(3));group.addView(b);return b;
    }
    private void toggle(LinearLayout panel,Button trigger){
        boolean open=panel.getVisibility()!=View.VISIBLE;
        panel.setVisibility(open?View.VISIBLE:View.GONE);
        trigger.setText(open?"Diğer modelleri gizle ▲":"Diğer modeller ve ayarlar ▼");
    }
    private void confirmPreset(String title,String speech,String summary,String help){
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Konuşma: "+AiModelStore.label(speech)+"\n"+
                "Özet: "+AiModelStore.label(summary)+"\n\n"+
                "Bu işlem seçtiğin modelleri değiştirir. "+
                "Önceki modeller silinmez, yeniden indirilmeleri gerekmez. Devam edilsin mi?")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Bu modelleri seç",(dialog,which)->
                select(speech,summary,help))
            .show();
    }
    private void select(String speech,String summary,String help){
        analysis.speechModel(speech);preferences.aiModel(summary);
        presetHint.setText(help);
        updateChoiceChecks();
        refresh();
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        analysis=new AnalysisPreferences(this);preferences=new Prefs(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(25));
        scroll.addView(root);setContentView(scroll);

        text(root,"Model merkezi",23,true);
        text(root,"Seçili modellerin korunur. Yeni model seçersen değişiklik senden onay alınarak yapılır.",12,false);

        LinearLayout adviser=card(root);
        text(adviser,"Kullanıma göre öneriler",16,true);
        text(adviser,"Modeli değiştirmek zorunda değilsin. Hız, RAM ve konuşma kalitesi değişebilir.",12,false);
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
        if(manager!=null)manager.getMemoryInfo(memory);
        final double ramGb=memory.totalMem>0?memory.totalMem/(1024d*1024d*1024d):0d;
        final boolean lowerRam=ramGb>0&&ramGb<8d;
        text(adviser,ramGb>0?
            "Bu telefon: yaklaşık "+String.format(java.util.Locale.ROOT,"%.1f",ramGb)+
            " GB RAM · "+(lowerRam?"Hafif modellerle başlaman önerilir.":"Ayrıntılı modeller denenebilir."):
            "Telefon RAM bilgisi okunamadı. Hafif modelle başlaman önerilir.",12,false);

        HorizontalScrollView horizontal=new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
        horizontal.addView(chips);adviser.addView(horizontal);
        presetHint=text(adviser,"5 dakikalık bir kayıt için Dengeli seçeneğiyle başlayabilirsin.",12,false);
        Button quick=chip(chips,"5 dk · Dengeli");
        quick.setOnClickListener(v->confirmPreset("Dengeli model seçimi",
            AiModelStore.WHISPER_Q5,AiModelStore.QWEN,
            "Kısa kayıtlar için daha hafif indirme ve bellek kullanımı. Önce metni kontrol et."));
        Button clear=chip(chips,"5–60 dk · Ayrıntılı");
        clear.setOnClickListener(v->confirmPreset("Ayrıntılı model seçimi",
            lowerRam?AiModelStore.WHISPER_Q5:AiModelStore.WHISPER_TURBO_Q8,
            lowerRam?AiModelStore.QWEN:AiModelStore.GEMMA,
            lowerRam?"RAM kapasiten nedeniyle hafif seçenekler seçildi. İstersen diğer modelleri ayrıca deneyebilirsin.":
                "Ayrıntılı analiz için daha büyük modeller seçildi. İşlem süresi ve RAM tüketimi artabilir."));
        Button longAudio=chip(chips,"1 saat+ · Ekonomik");
        longAudio.setOnClickListener(v->confirmPreset("Uzun kayıt modeli",
            AiModelStore.WHISPER_Q5,AiModelStore.QWEN,
            "Uzun kayıtları parçalara bölerek, daha az yer kaplayan modellerle denemek için. Şarj önerilir."));
        text(adviser,"İpucu: Sadece konuşmayı yazıya dökmek istiyorsan ikinci modeli indirmen gerekmez.",12,false);

        LinearLayout speech=card(root);
        text(speech,"Konuşmayı yazıya dök",17,true);
        selectedSpeech=text(speech,"Seçilen konuşma modeli",14,true);
        whisperStatus=text(speech,"Kontrol ediliyor…",12,false);
        whisperButton=button(speech,"Modeli indir",true);
        whisperButton.setOnClickListener(v->confirm(analysis.speechModel()));
        Button speechMore=button(speech,"Diğer modeller ve ayarlar ▼",false);
        LinearLayout speechAdvanced=new LinearLayout(this);
        speechAdvanced.setOrientation(LinearLayout.VERTICAL);
        speechAdvanced.setVisibility(View.GONE);speech.addView(speechAdvanced);
        text(speechAdvanced,"Konuşma modeli seçimi",13,true);
        speechChoices=new RadioGroup(this);speechChoices.setOrientation(RadioGroup.VERTICAL);
        RadioButton base=option(speechChoices,"Base · 148 MB · Deneme / hafif");
        RadioButton smallQ5=option(speechChoices,"Small Q5 · 190 MB · Dengeli");
        RadioButton small=option(speechChoices,"Small · 488 MB · Klasik");
        RadioButton turboQ5=option(speechChoices,"Turbo Q5 · 574 MB · Ayrıntılı");
        RadioButton turboQ8=option(speechChoices,"Turbo Q8 · 874 MB · Ayrıntılı / yüksek bellek");
        speechAdvanced.addView(speechChoices);
        speechChoices.setOnCheckedChangeListener((group,id)->{
            analysis.speechModel(id==turboQ8.getId()?AiModelStore.WHISPER_TURBO_Q8:
                id==turboQ5.getId()?AiModelStore.WHISPER_TURBO:
                id==smallQ5.getId()?AiModelStore.WHISPER_Q5:
                id==small.getId()?AiModelStore.WHISPER:AiModelStore.WHISPER_BASE);
            refresh();
        });
        Switch vad=new Switch(this);vad.setText("Sessiz bölümleri otomatik atla");
        vad.setTextColor(WHITE);vad.setTextSize(14);
        vad.setChecked(getSharedPreferences("gunizi",MODE_PRIVATE)
            .getBoolean("aiSileroVad",true));
        vad.setOnCheckedChangeListener((view,checked)->
            getSharedPreferences("gunizi",MODE_PRIVATE).edit()
                .putBoolean("aiSileroVad",checked).apply());
        speechAdvanced.addView(vad);
        text(speechAdvanced,"Uzun sessizliklerde yardımcı olabilir. Konuşma atlandığını düşünürsen kapatıp yeniden dene.",12,false);
        speechMore.setOnClickListener(v->toggle(speechAdvanced,speechMore));

        LinearLayout summary=card(root);
        text(summary,"Özet ve yapılacaklar",17,true);
        selectedSummary=text(summary,"Seçilen özet modeli",14,true);
        qwenStatus=text(summary,"Kontrol ediliyor…",12,false);
        qwenButton=button(summary,"Modeli indir",true);
        qwenButton.setOnClickListener(v->confirm(preferences.aiModel()));
        Button summaryMore=button(summary,"Diğer modeller ve ayarlar ▼",false);
        LinearLayout summaryAdvanced=new LinearLayout(this);
        summaryAdvanced.setOrientation(LinearLayout.VERTICAL);
        summaryAdvanced.setVisibility(View.GONE);summary.addView(summaryAdvanced);
        text(summaryAdvanced,"Özet modeli seçimi",13,true);
        summaryChoices=new RadioGroup(this);summaryChoices.setOrientation(RadioGroup.VERTICAL);
        RadioButton qwen=option(summaryChoices,"Qwen3 1.7B · 1,28 GB · Hafif");
        RadioButton gemma=option(summaryChoices,"Gemma 4 E2B · 2,62 GB · Ayrıntılı");
        RadioButton qwen4=option(summaryChoices,"Qwen3 4B · 2,5 GB · Alternatif");
        summaryAdvanced.addView(summaryChoices);
        summaryChoices.setOnCheckedChangeListener((group,id)->{
            preferences.aiModel(id==qwen4.getId()?AiModelStore.QWEN4:
                id==qwen.getId()?AiModelStore.QWEN:AiModelStore.GEMMA);
            refresh();
        });
        summaryMore.setOnClickListener(v->toggle(summaryAdvanced,summaryMore));

        text(root,"İndirmeler bildirimde takip edilir. Model dosyaları ve seçimin sen değiştirene kadar korunur.",12,false);
        button(root,"Durumu yenile",false).setOnClickListener(v->refresh());
        // Restore selections after wiring listeners. Neither preset nor screen opening starts a download.
        String s=analysis.speechModel(),m=preferences.aiModel();
        speechChoices.check(AiModelStore.WHISPER_TURBO_Q8.equals(s)?turboQ8.getId():
            AiModelStore.WHISPER_TURBO.equals(s)?turboQ5.getId():
            AiModelStore.WHISPER_Q5.equals(s)?smallQ5.getId():
            AiModelStore.WHISPER.equals(s)?small.getId():base.getId());
        summaryChoices.check(AiModelStore.QWEN4.equals(m)?qwen4.getId():
            AiModelStore.QWEN.equals(m)?qwen.getId():gemma.getId());
        refresh();
    }
    private void updateChoiceChecks(){
        // Presets update the stored choices; recreate checked state without navigation.
        if(speechChoices!=null){
            String s=analysis.speechModel();
            for(int i=0;i<speechChoices.getChildCount();i++){
                RadioButton b=(RadioButton)speechChoices.getChildAt(i);
                String name=b.getText().toString();
                if((AiModelStore.WHISPER_Q5.equals(s)&&name.startsWith("Small Q5"))||
                   (AiModelStore.WHISPER_TURBO_Q8.equals(s)&&name.startsWith("Turbo Q8")))
                    speechChoices.check(b.getId());
            }
        }
        if(summaryChoices!=null){
            String s=preferences.aiModel();
            for(int i=0;i<summaryChoices.getChildCount();i++){
                RadioButton b=(RadioButton)summaryChoices.getChildAt(i);
                if((AiModelStore.QWEN.equals(s)&&b.getText().toString().startsWith("Qwen3 1.7"))||
                   (AiModelStore.GEMMA.equals(s)&&b.getText().toString().startsWith("Gemma")))
                    summaryChoices.check(b.getId());
            }
        }
    }
    private void confirm(String model){
        if(AiModelStore.ready(this,model))return;
        new AlertDialog.Builder(this).setTitle(AiModelStore.label(model)+" indirilsin mi?")
            .setMessage("Model yalnızca cihazına indirilir. İndirme bildirimden izlenebilir; ses veya notların yüklenmez.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("İndir",(d,w)->{
                AiModelDownloadWorker.enqueue(this,model);refresh();
            }).show();
    }
    private void refresh(){
        if(selectedSpeech!=null)selectedSpeech.setText(AiModelStore.label(analysis.speechModel()));
        if(selectedSummary!=null)selectedSummary.setText(AiModelStore.label(preferences.aiModel()));
        showModel(analysis.speechModel(),whisperStatus,whisperButton);
        showModel(preferences.aiModel(),qwenStatus,qwenButton);
    }
    private void showModel(String model,TextView state,Button button){
        if(state==null||button==null)return;
        if(AiModelStore.ready(this,model)){
            state.setText("Hazır ✓ • İnternetsiz kullanılabilir");
            button.setText("Kurulu ✓");button.setEnabled(false);return;
        }
        button.setEnabled(true);button.setText("İndir / sürdür");
        File target=AiModelStore.file(this,model);
        File part=new File(target.getAbsolutePath()+".part");
        state.setText(part.exists()?"İndirilen "+(part.length()/1048576)+" MB · Devam edilebilir":"Henüz indirilmedi");
        new Thread(()->{
            try{
                List<WorkInfo> work=WorkManager.getInstance(this)
                    .getWorkInfosForUniqueWork(AiModelDownloadWorker.TAG+"-"+model).get();
                if(work.isEmpty())return;
                WorkInfo info=work.get(work.size()-1);
                String stage=info.getProgress().getString("stage");
                int pct=info.getProgress().getInt("percent",0);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(!model.equals(analysis.speechModel())&&!model.equals(preferences.aiModel()))return;
                    if(info.getState()==WorkInfo.State.RUNNING||info.getState()==WorkInfo.State.ENQUEUED){
                        state.setText((stage==null?"İndirme sırası bekleniyor":stage)+(pct>0?" · %"+pct:""));
                        button.setText("İndiriliyor…");button.setEnabled(false);
                    }else if(info.getState()==WorkInfo.State.FAILED){
                        state.setText("İndirme tamamlanamadı. Yeniden deneyebilirsin.");
                        button.setEnabled(true);
                    }else if(info.getState()==WorkInfo.State.SUCCEEDED&&AiModelStore.ready(this,model)){
                        state.setText("Hazır ✓");button.setEnabled(false);
                    }
                });
            }catch(Exception ignored){}
        },"GunIzi-ModelStatus").start();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(heartbeat);handler.post(heartbeat);}
    @Override protected void onPause(){handler.removeCallbacks(heartbeat);super.onPause();}
}
