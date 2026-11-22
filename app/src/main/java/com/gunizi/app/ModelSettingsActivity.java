package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.work.*;
import java.io.File;
import java.util.List;

/** Single, explicit place to select and download offline AI models. */
public final class ModelSettingsActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,2500);}
    };
    private TextView whisperStatus,qwenStatus;
    private Button whisperButton,qwenButton;
    private AnalysisPreferences analysis;
    private Prefs preferences;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable panel(){
        GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(16));return d;
    }
    private TextView text(LinearLayout root,String title,int size,boolean bold){
        TextView t=new TextView(this);t.setText(title);t.setTextColor(bold?WHITE:MUTED);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
        params.bottomMargin=dp(10);root.addView(t,params);return t;
    }
    private LinearLayout card(LinearLayout root){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(panel());c.setPadding(dp(16),dp(17),dp(16),dp(16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(14);
        root.addView(c,p);return c;
    }
    private Button button(LinearLayout root,String label){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.bottomMargin=dp(8);root.addView(b,p);return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        analysis=new AnalysisPreferences(this);preferences=new Prefs(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(17),dp(20),dp(17),dp(30));
        root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        text(root,"Yapay Zeka Modelleri",24,true);
        text(root,"Modeller yalnızca ilk kurulumda internetten indirilir. "
            +"İndirme sırasında başka uygulamaya geçebilirsin; ilerleme bildirimden takip edilir. "
            +"Seslerin ve raporların telefondan çıkmaz.",13,false);
        LinearLayout speech=card(root);
        text(speech,"1. Konuşmayı yazıya dökme",18,true);
        RadioGroup speechChoices=new RadioGroup(this);speechChoices.setOrientation(RadioGroup.VERTICAL);
        RadioButton base=new RadioButton(this),small=new RadioButton(this),
            compact=new RadioButton(this),turbo=new RadioButton(this),
            turboQ8=new RadioButton(this);
        base.setId(View.generateViewId());small.setId(View.generateViewId());
        compact.setId(View.generateViewId());turbo.setId(View.generateViewId());
        turboQ8.setId(View.generateViewId());
        base.setText("Whisper Base • 148 MB · Hafif");
        compact.setText("Whisper Small Q5 • 190 MB · Hızlı");
        small.setText("Whisper Small • 488 MB · Klasik");
        turbo.setText("Whisper Turbo Q5 • 574 MB · Klasik");
        turboQ8.setText("Whisper Turbo Q8 • 874 MB · CallVault önerisi");
        for(RadioButton item:new RadioButton[]{base,compact,small,turbo,turboQ8}){
            item.setTextColor(WHITE);speechChoices.addView(item);
        }
        speech.addView(speechChoices);
        String chosenSpeech=analysis.speechModel();
        speechChoices.check(AiModelStore.WHISPER_TURBO_Q8.equals(chosenSpeech)?
            turboQ8.getId():AiModelStore.WHISPER_TURBO.equals(chosenSpeech)?
            turbo.getId():AiModelStore.WHISPER_Q5.equals(chosenSpeech)?
            compact.getId():AiModelStore.WHISPER.equals(chosenSpeech)?
            small.getId():base.getId());
        speechChoices.setOnCheckedChangeListener((group,checked)->{
            analysis.speechModel(checked==turboQ8.getId()?AiModelStore.WHISPER_TURBO_Q8:
                checked==turbo.getId()?AiModelStore.WHISPER_TURBO:
                checked==compact.getId()?AiModelStore.WHISPER_Q5:
                checked==small.getId()?AiModelStore.WHISPER:AiModelStore.WHISPER_BASE);
            refresh();
        });
        text(speech,"CallVault whisper.cpp kullanılır. Q8 seçeneği cihazın ARM hızlandırmasından yararlanabilir.",13,false);
        Switch vad=new Switch(this);
        vad.setText("Silero VAD • Konuşma olmayan bölümleri atla");
        vad.setTextColor(WHITE);
        vad.setChecked(getSharedPreferences("gunizi",MODE_PRIVATE)
            .getBoolean("aiSileroVad",true));
        vad.setOnCheckedChangeListener((v,checked)->
            getSharedPreferences("gunizi",MODE_PRIVATE).edit()
                .putBoolean("aiSileroVad",checked).apply());
        speech.addView(vad);
        text(speech,"Silero APK içinde bulunur. Özellikle uzun sessizliklerde faydalı olabilir; "+
            "gerekirse konuşma kaybını kontrol etmek için kapatabilirsin.",12,false);
        whisperStatus=text(speech,"Hazırlanıyor…",13,false);
        whisperButton=button(speech,"Konuşma modelini indir");
        whisperButton.setOnClickListener(v->confirm(analysis.speechModel()));
        LinearLayout language=card(root);
        text(language,"2. Ayrıntılı özet ve konu tablosu",18,true);
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(RadioGroup.VERTICAL);
        RadioButton compactSummary=new RadioButton(this),
            strong=new RadioButton(this),gemma=new RadioButton(this);
        compactSummary.setId(View.generateViewId());
        strong.setId(View.generateViewId());gemma.setId(View.generateViewId());
        gemma.setText("Gemma 4 E2B QAT • 2,62 GB · CallVault önerisi");
        compactSummary.setText("Qwen3-1.7B • 1,28 GB · Alternatif");
        strong.setText("Qwen3-4B • 2,5 GB · Alternatif");
        for(RadioButton item:new RadioButton[]{gemma,compactSummary,strong}){
            item.setTextColor(WHITE);choices.addView(item);
        }
        language.addView(choices);
        choices.check(AiModelStore.QWEN4.equals(preferences.aiModel())?
            strong.getId():AiModelStore.QWEN.equals(preferences.aiModel())?
            compactSummary.getId():gemma.getId());
        choices.setOnCheckedChangeListener((group,checked)->{
            preferences.aiModel(checked==strong.getId()?AiModelStore.QWEN4:
                checked==compactSummary.getId()?AiModelStore.QWEN:AiModelStore.GEMMA);
            refresh();
        });
        qwenStatus=text(language,"Hazırlanıyor…",13,false);
        qwenButton=button(language,"Özet modelini indir");
        qwenButton.setOnClickListener(v->confirm(preferences.aiModel()));
        LinearLayout help=card(root);
        text(help,"İndirme ve pil",17,true);
        text(help,"• İndirilen kısmı kaybolmaz: bağlantı kesilirse devam etmeyi dener.\n"
            +"• Büyük indirmelerde yeterli boş alan ve mümkünse Wi-Fi kullan.\n"
            +"• İndirme, konuşma çözümleme veya günlük rapor oluşturma işlemi değildir.\n"
            +"• İndirme sırasında bildirimleri kapatma; Android kısıtlamaları işleri geciktirebilir.",13,false);
        Button refresh=button(help,"İndirme durumunu yenile");
        refresh.setOnClickListener(v->refresh());
        refresh();
    }
    private void confirm(String model){
        if(AiModelStore.ready(this,model))return;
        String label=AiModelStore.label(model);
        new AlertDialog.Builder(this).setTitle(label+" indirilsin mi?")
            .setMessage("İndirme bildirimde ilerleyecek ve uygulamadan çıkınca devam etmeyi "+
                "deneyecek. Bağlantı koparsa kalan kısmı tekrar indirmeden sürdürmeyi dener.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("İndir",(d,w)->{
                AiModelDownloadWorker.enqueue(this,model);
                refresh();
            }).show();
    }
    private void refresh(){
        showModel(analysis.speechModel(),whisperStatus,whisperButton);
        showModel(preferences.aiModel(),qwenStatus,qwenButton);
    }
    private void showModel(String model,TextView state,Button button){
        if(state==null||button==null)return;
        if(AiModelStore.ready(this,model)){
            state.setText("Model hazır ✓ • Çevrimdışı kullanılabilir");
            button.setText("Model zaten kurulu");button.setEnabled(false);return;
        }
        button.setEnabled(true);button.setText("İndirmeyi başlat / sürdür");
        File target=AiModelStore.file(this,model);
        File part=new File(target.getAbsolutePath()+".part");
        if(part.exists())state.setText("İndirilen: "+(part.length()/1048576)+" MB • Devam edilebilir");
        else state.setText("Henüz indirilmedi");
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
                    if(info.getState()==WorkInfo.State.RUNNING||
                        info.getState()==WorkInfo.State.ENQUEUED){
                        state.setText((stage==null?"İndirme sırada":stage)+
                            (pct>0?" • %"+pct:""));
                        button.setText("İndirme sürüyor");button.setEnabled(false);
                    }else if(info.getState()==WorkInfo.State.FAILED){
                        state.setText("İndirme başarısız. Tekrar deneyebilirsin.");
                        button.setEnabled(true);
                    }else if(info.getState()==WorkInfo.State.SUCCEEDED&&AiModelStore.ready(this,model)){
                        state.setText("Model hazır ✓");button.setEnabled(false);
                    }
                });
            }catch(Exception ignored){}
        },"GunIzi-ModelUI").start();
    }
    @Override protected void onResume(){super.onResume();handler.post(heartbeat);}
    @Override protected void onPause(){handler.removeCallbacks(heartbeat);super.onPause();}
}
