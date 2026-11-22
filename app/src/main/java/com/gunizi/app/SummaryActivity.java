package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.work.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 2.0: one selected recording, two primary actions, one truthful pipeline card.
 * Recording storage stays independent of the AI engine.
 */
public final class SummaryActivity extends Activity {
    private static final int PICK_SOURCE=321,BG=0xff090e1b,PANEL=0xff172031,
        WHITE=0xffeef8ff,MUTED=0xffa2b4c9,TEAL=0xff38d4ca;
    private static final String PREF="gunizi";
    private Uri selectedUri;
    private String selectedName="Ses kaydı";
    private String pendingAction;
    private RecordingPlayerCard playback;
    private TextView sourceInfo,stage,preview,progressLabel,elapsed,phaseInfo,modelStatus;
    private ProgressBar progressBar;
    private Button cancel,transcriptButton,reportButton;
    private EditText focus;
    private final AtomicBoolean statusRefreshing=new AtomicBoolean(false);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timer=new Runnable(){
        @Override public void run(){refreshStatus();handler.postDelayed(this,1000);}
    };
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(18));
        if(color!=TEAL)d.setStroke(dp(1),0xff28394c);
        return d;
    }
    private TextView text(LinearLayout container,String content,int size,boolean bold){
        TextView v=new TextView(this);v.setText(content);v.setTextColor(bold?WHITE:MUTED);
        v.setTextSize(size);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);
        container.addView(v,p);return v;
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);c.setPadding(dp(18),dp(18),dp(18),dp(16));
        c.setOrientation(LinearLayout.VERTICAL);c.setBackground(bg(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(14);parent.addView(c,p);return c;
    }
    private Button resultTab(LinearLayout container,String name){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);
        b.setTextColor(WHITE);b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setBackground(bg(0xff22364a));
        LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,dp(62),1);
        tab.setMargins(0,0,dp(6),0);
        container.addView(b,tab);return b;
    }
    private Button button(LinearLayout parent,String name,boolean primary){
        Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,primary?Typeface.BOLD:Typeface.NORMAL);
        b.setElevation(primary?dp(2):0);
        b.setTextColor(primary?BG:WHITE);
        b.setBackground(bg(primary?TEAL:0xff29384f));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));
        p.bottomMargin=dp(8);parent.addView(b,p);return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.content.SharedPreferences prefs=getSharedPreferences(PREF,MODE_PRIVATE);
        String prev=prefs.getString("aiSelectedSourceUri",null);
        if(prev!=null)try{selectedUri=Uri.parse(prev);}catch(Exception ignored){}
        selectedName=prefs.getString("aiSelectedSourceName","Ses kaydı");
        render();
        if(selectedUri!=null)inspectSource(selectedUri,null);
    }
    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(24),dp(16),dp(30));scroll.addView(root);
        setContentView(scroll);
        TextView eyebrow=text(root,"GÜNİZİ  /  YEREL YAPAY ZEKA",11,true);
        eyebrow.setTextColor(TEAL);eyebrow.setLetterSpacing(0.13f);
        text(root,"Konuşmalarına anlam kat.",26,true);
        text(root,"Kaydı dinle, metnini çıkar, kararları ve yapılacakları gör. "+
            "Sesin ve notların telefonunda kalır.",13,false);
        LinearLayout privacy=new LinearLayout(this);
        privacy.setOrientation(LinearLayout.HORIZONTAL);
        TextView privacyBadge=text(privacy,"●  ÇEVRİMDIŞI ANALİZ    •    CALLVAULT MOTORU",11,true);
        privacyBadge.setTextColor(TEAL);
        privacyBadge.setPadding(dp(10),dp(4),dp(10),dp(4));
        GradientDrawable bgBadge=bg(0xff102e31);
        privacyBadge.setBackground(bgBadge);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);
        bp.bottomMargin=dp(15);root.addView(privacy,bp);

        // Primary screen: recording playback and source, rather than model downloads.
        LinearLayout recording=card(root);
        text(recording,"01   KAYDIN",16,true);
        playback=new RecordingPlayerCard(this);
        recording.addView(playback,new LinearLayout.LayoutParams(-1,-2));
        sourceInfo=text(recording,"Ses kaydını seçerek başla.",12,false);
        Button choose=button(recording,"Başka ses kaydı seç",false);
        choose.setOnClickListener(v->pickSource(null));

        LinearLayout actions=card(root);
        text(actions,"02   YAPAY ZEKA İŞLEMLERİ",16,true);
        text(actions,"Konuşmayı metne dönüştür veya konuları ve görevleri çıkar.",12,false);
        Button transcript=button(actions,"Konuşma metnini çıkar",true);
        transcript.setOnClickListener(v->startAction(DailySummaryWorker.TRANSCRIPT));
        Button summary=button(actions,"Özetle • Konu, karar ve görev tablosu",false);
        summary.setOnClickListener(v->startAction(DailySummaryWorker.REPORT));
        Button again=button(actions,"Hazır metinden yeni özet oluştur",false);
        again.setOnClickListener(v->{
            saveFocus();
            if(!AiModelStore.ready(this,new Prefs(this).aiModel())){
                missingModel(false);return;
            }
            if(!DailySummaryWorker.queueFromTranscript(this)){
                new AlertDialog.Builder(this).setTitle("Henüz konuşma metni yok")
                    .setMessage("Önce konuşmayı yazıya dök. Sonrasında ses dosyasını tekrar "+
                        "çözmeden yeni rapor oluşturabilirsin.")
                    .setPositiveButton("Anladım",null).show();
            }else refreshStatus();
        });

        LinearLayout tracking=card(root);
        text(tracking,"03   CANLI İŞLEM DURUMU",16,true);
        stage=text(tracking,"Henüz analiz başlatılmadı.",15,true);
        phaseInfo=text(tracking,"İşlem aşaması ve kaynak bilgisi burada görünür.",12,false);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        tracking.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(10)));
        LinearLayout metrics=new LinearLayout(this);metrics.setOrientation(LinearLayout.HORIZONTAL);
        progressLabel=text(metrics,"Başlamadı",12,true);
        elapsed=text(metrics,"00:00:00",12,true);
        LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1);
        progressLabel.setLayoutParams(l);
        elapsed.setGravity(Gravity.END);metrics.addView(new android.view.View(this),
            new LinearLayout.LayoutParams(0,1,0));
        tracking.addView(metrics);
        text(tracking,"SON OKUNAN KONUŞMA",12,true);
        preview=text(tracking,"Henüz konuşma çözümlenmedi.",13,false);
        preview.setMaxLines(4);
        cancel=button(tracking,"Analizi durdur",false);
        cancel.setOnClickListener(v->new AlertDialog.Builder(this)
            .setTitle("Analiz durdurulsun mu?")
            .setMessage("Kaynak ses korunur. Tamamlanmış parçalar mümkün olduğunda kullanılabilir.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Durdur",(d,w)->{
                WorkManager.getInstance(this).cancelAllWorkByTag("gunizi-local-summary");
                stage.setText("İptal istendi");
            }).show());

        LinearLayout result=card(root);
        text(result,"04   SONUÇLARIN",16,true);
        text(result,"Metin ve konu tablosu birbirinden bağımsız kaydedilir.",12,false);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        result.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
        transcriptButton=resultTab(tabs,"Metin");
        transcriptButton.setOnClickListener(v->startActivity(new Intent(this,TranscriptActivity.class)));
        reportButton=resultTab(tabs,"Konular ve kararlar");
        reportButton.setOnClickListener(v->startActivity(new Intent(this,ReportTableActivity.class)));
        Button notes=resultTab(tabs,"Notlarım");
        notes.setOnClickListener(v->{
            Intent i=new Intent(this,UserNotesActivity.class);
            i.putExtra("openLatestReport",true);startActivity(i);
        });
        text(result,"Notlarını düzenleyebilir, onayınla Samsung Notes'a paylaşabilirsin.",12,false);

        LinearLayout preferences=card(root);
        text(preferences,"05   ANALİZ TERCİHİM",16,true);
        text(preferences,"Örneğin alınan kararlar, sorumlular, mağaza açılışları ve "+
            "bekleyen konular. Bu talimat yalnızca özetleme için kullanılır.",12,false);
        focus=new EditText(this);focus.setText(new AnalysisPreferences(this).focus());
        focus.setTextColor(WHITE);focus.setHintTextColor(MUTED);
        focus.setTextSize(14);focus.setMinLines(2);focus.setMaxLines(7);
        preferences.addView(focus);
        Button save=button(preferences,"Tercihimi kaydet",false);
        save.setOnClickListener(v->{saveFocus();
            Toast.makeText(this,"Tercih kaydedildi.",Toast.LENGTH_SHORT).show();});

        LinearLayout models=card(root);
        text(models,"MODEL VE MOTOR DURUMU",16,true);
        modelStatus=text(models,"Model durumu kontrol ediliyor.",12,false);
        Button settings=button(models,"Model seçimi • Whisper / Gemma / Qwen",false);
        settings.setOnClickListener(v->
            startActivity(new Intent(this,ModelSettingsActivity.class)));
        refreshStatus();
    }
    private void saveFocus(){
        new AnalysisPreferences(this).focus(focus.getText().toString());
    }
    private void startAction(String mode){
        saveFocus();
        boolean whisper=AiModelStore.ready(this,new AnalysisPreferences(this).speechModel());
        boolean qwen=AiModelStore.ready(this,new Prefs(this).aiModel());
        if(!whisper){missingModel(true);return;}
        if(DailySummaryWorker.REPORT.equals(mode)&&!qwen){missingModel(false);return;}
        if(selectedUri==null){pickSource(mode);return;}
        final Uri uri=selectedUri;
        final String name=selectedName;
        new Thread(()->{
            try{
                AiAudioPreflight.Info info=AiAudioPreflight.inspect(this,uri);
                runOnUiThread(()->{
                    sourceInfo.setText(info.description());
                    DailySummaryWorker.queue(this,uri,name,false,mode);
                    refreshStatus();
                });
            }catch(Exception err){
                runOnUiThread(()->new AlertDialog.Builder(this)
                    .setTitle("Kayda erişilemiyor")
                    .setMessage(err.getMessage()+"\nSes kaydını yeniden seçebilirsin.")
                    .setPositiveButton("Tekrar seç",(d,w)->pickSource(mode))
                    .setNegativeButton("Vazgeç",null).show());
            }
        },"GunIzi-AudioCheck").start();
    }
    private void missingModel(boolean speech){
        new AlertDialog.Builder(this).setTitle(speech?"Whisper gerekli":"Yerel özet modeli gerekli")
            .setMessage(speech?"Konuşma metni için Whisper modelini indir.":
                "Konuşma metni yalnızca Whisper ile çıkarılır; ancak "+
                "konu/karar tablosu için Gemma veya Qwen modelinin kurulması gerekir.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Model ayarları",(d,w)->
                startActivity(new Intent(this,ModelSettingsActivity.class))).show();
    }
    private void pickSource(String action){
        pendingAction=action;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_SOURCE);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_SOURCE||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        selectedUri=uri;
        String action=pendingAction;pendingAction=null;
        inspectSource(uri,action);
    }
    private void inspectSource(Uri uri,String action){
        sourceInfo.setText("Ses dosyası kontrol ediliyor…");
        new Thread(()->{
            try{
                AiAudioPreflight.Info info=AiAudioPreflight.inspect(this,uri);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    selectedName=info.name;selectedUri=uri;
                    getSharedPreferences(PREF,MODE_PRIVATE).edit()
                        .putString("aiSelectedSourceUri",uri.toString())
                        .putString("aiSelectedSourceName",info.name).apply();
                    playback.setSource(uri,info);
                    sourceInfo.setText(info.description());
                    if(action!=null)startAction(action);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    sourceInfo.setText("Ses dosyası açılamadı: "+e.getMessage());
                    if(action!=null)new AlertDialog.Builder(this)
                        .setTitle("Ses dosyası okunamadı")
                        .setMessage(e.getMessage()).setPositiveButton("Tamam",null).show();
                });
            }
        },"GunIzi-SourcePreflight").start();
    }
    private void refreshStatus(){
        if(stage==null)return;
        android.content.SharedPreferences p=getSharedPreferences(PREF,MODE_PRIVATE);
        String speech=new AnalysisPreferences(this).speechModel(),qwen=new Prefs(this).aiModel();
        modelStatus.setText(AiModelStore.label(speech)+": "+
            (AiModelStore.ready(this,speech)?"Hazır ✓":"İndirilmeli")+"\n"+
            AiModelStore.label(qwen)+": "+
            (AiModelStore.ready(this,qwen)?"Hazır ✓":"İndirilmeli")+"\n"+
            "Motor: CallVault whisper.cpp / llama.cpp • Silero VAD "+
            (getSharedPreferences(PREF,MODE_PRIVATE).getBoolean("aiSileroVad",true)?"açık":"kapalı"));
        String stageKey=AiStageMetrics.stage(this);
        String current=p.getString("analysisStage","");
        String error=p.getString("summaryError","");
        String filename=p.getString("analysisSource","");
        boolean running=p.getLong("analysisStartedAt",0)>p.getLong("analysisCompletedAt",0);
        stage.setText(!error.isEmpty()?"İşlem başarısız":current.isEmpty()?
            "Henüz işlem başlatılmadı":current);
        String phaseText=AiStageMetrics.displayStage(stageKey);
        phaseInfo.setText(phaseText+(filename.isEmpty()?"":"  •  "+filename));
        int percent=p.getInt("analysisPercent",0);
        boolean loading=AiStageMetrics.MODEL_LOADING.equals(stageKey)||
            AiStageMetrics.LLM_LOADING.equals(stageKey);
        if(loading&&running){
            progressBar.setIndeterminate(true);
            progressLabel.setText("Model hazırlanıyor • Yüzde hesaplanamıyor");
        }else{
            progressBar.setIndeterminate(false);progressBar.setProgress(percent);
            progressLabel.setText(running?"%"+percent+" işlendi":percent==100?"Tamamlandı":
                error.isEmpty()?"Bekliyor":"Başarısız");
        }
        long started=p.getLong("analysisStartedAt",0),completed=p.getLong("analysisCompletedAt",0);
        long seconds=started<=0?0:Math.max(0,((completed>0?completed:
            System.currentTimeMillis())-started)/1000);
        elapsed.setText(String.format(Locale.ROOT,"%02d:%02d:%02d",
            seconds/3600,(seconds/60)%60,seconds%60));
        long loadingFor=AiStageMetrics.stageMs(this);
        if(loading&&running&&loadingFor>90000)
            phaseInfo.setText(phaseText+" • "+AiStageMetrics.displayTime(loadingFor)+
                " • Uzun sürüyor; model uyumluluğu veya bellek sorunu olabilir.");
        preview.setText(p.getString("analysisPreview","").isEmpty()?
            "Konuşmalar işlendikçe burada kısa bir bölüm görünecek.":
            p.getString("analysisPreview",""));
        transcriptButton.setEnabled(p.getString("transcriptInternal",null)!=null);
        reportButton.setEnabled(p.getString("latestHtmlReport",null)!=null);
        String job=p.getString("analysisWorkId",null);
        if(job!=null&&statusRefreshing.compareAndSet(false,true)){
            new Thread(()->{
                try{
                    WorkInfo info=WorkManager.getInstance(this)
                        .getWorkInfoById(java.util.UUID.fromString(job)).get();
                    if(info!=null)runOnUiThread(()->{
                        if(isFinishing()||isDestroyed())return;
                        String now=getSharedPreferences(PREF,MODE_PRIVATE)
                            .getString("analysisWorkId","");
                        if(!job.equals(now))return;
                        if(info.getState()==WorkInfo.State.ENQUEUED)
                            stage.setText("İşlem sırada • Android koşulları bekleniyor");
                        else if(info.getState()==WorkInfo.State.FAILED)
                            stage.setText("Analiz başarısız • Ayrıntıları kontrol et");
                        else if(info.getState()==WorkInfo.State.CANCELLED)
                            stage.setText("İşlem iptal edildi");
                        else if(info.getState()==WorkInfo.State.SUCCEEDED)
                            stage.setText("İşlem tamamlandı");
                        cancel.setEnabled(info.getState()==WorkInfo.State.RUNNING||
                            info.getState()==WorkInfo.State.ENQUEUED);
                    });
                }catch(Exception ignored){}
                finally{statusRefreshing.set(false);}
            },"GunIzi-JobStatus").start();
        }
    }
    @Override protected void onResume(){
        super.onResume();handler.removeCallbacks(timer);handler.post(timer);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(timer);
        if(playback!=null)playback.release();
        super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(timer);
        if(playback!=null)playback.release();
        super.onDestroy();
    }
}
