package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import java.util.List;

/** Human-readable AI workspace: choose source, transcribe OR summarize, inspect results. */
public final class SummaryActivity extends Activity {
    private static final int PICK=321,BG=0xff090e1b,PANEL=0xff172031,
        WHITE=0xfff0f7ff,MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private String chosenMode=DailySummaryWorker.REPORT;
    private ProgressBar progress;
    private TextView stage,source,progressText,modelState;
    private EditText focus;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,1800);}
    };
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private GradientDrawable bg(int color){
        GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(15));return b;
    }
    private TextView text(LinearLayout root,String txt,int size,boolean strong){
        TextView v=new TextView(this);v.setText(txt);v.setTextSize(size);
        v.setTextColor(strong?WHITE:MUTED);
        if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);
        root.addView(v,p);return v;
    }
    private LinearLayout card(LinearLayout root){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(17),dp(17),dp(17));c.setBackground(bg(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(13);
        root.addView(c,p);return c;
    }
    private Button button(LinearLayout root,String label,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTextSize(15);b.setTextColor(primary?BG:WHITE);
        b.setBackground(bg(primary?CYAN:0xff2a3b53));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));
        p.bottomMargin=dp(9);root.addView(b,p);return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)chosenMode=state.getString("chosenMode",DailySummaryWorker.REPORT);
        build();
    }
    @Override protected void onSaveInstanceState(Bundle b){
        super.onSaveInstanceState(b);b.putString("chosenMode",chosenMode);
    }
    private void build(){
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17),dp(20),dp(17),dp(24));sc.addView(root);
        setContentView(sc);
        text(root,"Günİzi • Yapay Zeka",24,true);
        text(root,"Kaydını seç, ne yapılacağını belirle. İşlem sırasında başka "+
            "uygulamalara geçebilirsin; ilerleme ve sonuç bildirimde gösterilir.",13,false);

        LinearLayout analyze=card(root);
        text(analyze,"1 • Ne yapmak istiyorsun?",18,true);
        text(analyze,"Konuşmayı Yazıya Dök: yalnızca Whisper gerekir. "+
            "Özetle + Tablo: Whisper ve Qwen3 gerekir; konu, karar ve görevleri de çıkarır.",13,false);
        Button transcribe=button(analyze,"Konuşmayı Yazıya Dök",true);
        transcribe.setOnClickListener(v->choose(DailySummaryWorker.TRANSCRIPT));
        Button summarize=button(analyze,"Özetle + Ayrıntılı Konu Tablosu",false);
        summarize.setOnClickListener(v->choose(DailySummaryWorker.REPORT));

        LinearLayout tracker=card(root);
        text(tracker,"2 • Canlı işlem durumu",18,true);
        source=text(tracker,"Ses kaydı seçilmedi.",14,true);
        stage=text(tracker,"Henüz işlem başlatılmadı.",13,false);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(
            android.content.res.ColorStateList.valueOf(CYAN));
        tracker.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));
        progressText=text(tracker,"%0",13,true);
        text(tracker,"Döküm sırasında: ses parçaları çözümlenir. "+
            "Özet sırasında: konular, kararlar ve görevler çıkarılır. "+
            "Otomatik mesai raporu şarj koşulunu bekleyebilir.",12,false);
        Button refresh=button(tracker,"Durumu yenile",false);
        refresh.setOnClickListener(v->refresh());
        Button cancel=button(tracker,"Çalışan analizi durdur",false);
        cancel.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Analiz durdurulsun mu?")
            .setMessage("Ses dosyası korunur. Tamamlanan işlem parçaları, mümkün olduğunda yeniden kullanılır.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Durdur",(d,w)->{
                WorkManager.getInstance(this).cancelAllWorkByTag("gunizi-local-summary");
                stage.setText("İptal istendi; ses dosyası korunuyor.");
            }).show());

        LinearLayout results=card(root);
        text(results,"3 • Sonuçlar",18,true);
        Button transcript=button(results,"Konuşma dökümünü görüntüle",false);
        transcript.setOnClickListener(v->startActivity(new Intent(this,TranscriptActivity.class)));
        Button topics=button(results,"Konular • Kararlar • Görevler",false);
        topics.setOnClickListener(v->startActivity(new Intent(this,ReportTableActivity.class)));
        Button notes=button(results,"Notlarıma ekle / Samsung Notes ile paylaş",false);
        notes.setOnClickListener(v->{
            Intent i=new Intent(this,UserNotesActivity.class);
            i.putExtra("openLatestReport",true);startActivity(i);
        });

        LinearLayout instructions=card(root);
        text(instructions,"4 • Özetlemede neye odaklanayım?",17,true);
        text(instructions,"Yalnızca özet ve tablo oluştururken uygulanır. "+
            "Konuşmayı yazıya dökme işlemi için değişiklik yapman gerekmez.",13,false);
        focus=new EditText(this);
        focus.setText(new AnalysisPreferences(this).focus());
        focus.setTextColor(WHITE);focus.setHintTextColor(MUTED);
        focus.setTextSize(14);focus.setMinLines(3);focus.setMaxLines(8);
        focus.setHint("Örnek: Ruhsat görüşmeleri, son tarihler, sorumlular ve bekleyen işler…");
        instructions.addView(focus);
        Button save=button(instructions,"Analiz tercihini kaydet",false);
        save.setOnClickListener(v->{
            new AnalysisPreferences(this).focus(focus.getText().toString());
            Toast.makeText(this,"Analiz tercihi kaydedildi.",Toast.LENGTH_SHORT).show();
        });

        LinearLayout models=card(root);
        text(models,"Model ve indirme ayarları",17,true);
        modelState=text(models,"Model durumları kontrol ediliyor…",13,false);
        Button settings=button(models,"Ayarlar • Yapay Zeka Modelleri",false);
        settings.setOnClickListener(v->startActivity(new Intent(this,ModelSettingsActivity.class)));
        text(models,"Model indirmek ayrı bir işlemdir. İndirme, konuşma "+
            "çözümleme veya günlük özetleme anlamına gelmez.",12,false);
        refresh();
    }
    private void choose(String mode){
        chosenMode=mode;
        new AnalysisPreferences(this).focus(focus.getText().toString());
        boolean whisper=AiModelStore.ready(this,new AnalysisPreferences(this).speechModel());
        boolean qwen=AiModelStore.ready(this,new Prefs(this).aiModel());
        if(!whisper||(DailySummaryWorker.REPORT.equals(mode)&&!qwen)){
            new AlertDialog.Builder(this).setTitle("Model kurulumu gerekli")
                .setMessage(!whisper?"Önce Whisper konuşma modelini kur.":
                    "Konuşmayı yazıya dökmek için Whisper hazır. Ancak özet ve konu tablosu için Qwen3 de tam olarak indirilip doğrulanmalı.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("Model ayarlarını aç",(d,w)->
                    startActivity(new Intent(this,ModelSettingsActivity.class))).show();
            return;
        }
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("audio/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name="Ses_Kaydi.ogg";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
            null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        DailySummaryWorker.queue(this,uri,name,false,chosenMode);
        refresh();
    }
    private void refresh(){
        if(modelState==null)return;
        String speech=new AnalysisPreferences(this).speechModel();
        String qwen=new Prefs(this).aiModel();
        modelState.setText("Whisper: "+(AiModelStore.ready(this,speech)?"Hazır ✓":"Eksik")+
            "    •    Qwen3: "+(AiModelStore.ready(this,qwen)?"Hazır ✓":"Eksik"));
        android.content.SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        String filename=p.getString("analysisSource","");
        String phase=p.getString("analysisStage","");
        String error=p.getString("summaryError","");
        int percent=p.getInt("analysisPercent",0);
        source.setText(filename.isEmpty()?"Henüz ses kaydı seçilmedi.":"Kaynak: "+filename);
        stage.setText(!error.isEmpty()?"Son hata: "+error:phase.isEmpty()?
            "İşlem başlatmak için yukarıdan bir seçenek seç.":phase);
        progress.setProgress(percent);
        progressText.setText("%"+percent);
        new Thread(()->{
            try{
                List<WorkInfo> jobs=WorkManager.getInstance(this)
                    .getWorkInfosByTag("gunizi-local-summary").get();
                WorkInfo found=null;
                for(WorkInfo job:jobs){
                    if(job.getState()==WorkInfo.State.RUNNING){found=job;break;}
                    if(job.getState()==WorkInfo.State.ENQUEUED)found=job;
                }
                if(found==null)return;
                final WorkInfo selected=found;
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||stage==null)return;
                    if(selected.getState()==WorkInfo.State.ENQUEUED){
                        stage.setText("İşlem sırada • pil veya şarj koşulu bekleniyor");
                    }else{
                        String txt=selected.getProgress().getString("stage");
                        int value=selected.getProgress().getInt("percent",percent);
                        if(txt!=null)stage.setText(txt);
                        progress.setProgress(value);progressText.setText("%"+value);
                    }
                });
            }catch(Exception ignored){}
        },"GunIzi-AIStatus").start();
    }
    @Override protected void onResume(){super.onResume();handler.post(tick);}
    @Override protected void onPause(){handler.removeCallbacks(tick);super.onPause();}
}
