package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.work.WorkInfo;

/**
 * Adaptive and compact AI progress card. It never invents native model-load
 * percentages and separates global audio progress from per-chunk progress.
 */
final class AiLiveStatusCard extends LinearLayout {
    private static final int BG=0xff172031,WHITE=0xffe9f5ff,
        MUTED=0xffa2b4c9,TEAL=0xff38d4ca,WARNING=0xffedb975,
        SUB=0xff263649;
    private final TextView step,headline,description,stageTimer,elapsed,
        audio,parts,globalPercent,preview,alert,details,previewTitle;
    private final ProgressBar progress;
    private final LinearLayout previewBox,detailBox;
    private final Button expandPreview,detailToggle,cancel,copyInfo;
    private boolean expanded=false,showDetails=false;
    private String copiedDiagnostic="";
    private final boolean narrow;

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(15));
        d.setStroke(dp(1),0xff2d3b50);return d;
    }
    private TextView addText(LinearLayout to,String message,int font,boolean bold){
        TextView v=new TextView(getContext());v.setText(message);
        v.setTextColor(bold?WHITE:MUTED);v.setTextSize(font);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin=dp(7);to.addView(v,lp);return v;
    }
    private static void changed(TextView view,String value){
        if(!value.contentEquals(view.getText()))view.setText(value);
    }
    private Button action(String text){
        Button b=new Button(getContext());b.setAllCaps(false);b.setText(text);
        b.setTextColor(WHITE);b.setTextSize(13);b.setBackground(shape(SUB));
        return b;
    }
    private TextView metric(LinearLayout holder,String label,String value){
        LinearLayout tile=new LinearLayout(getContext());tile.setOrientation(VERTICAL);
        tile.setPadding(dp(11),dp(10),dp(9),dp(7));
        tile.setBackground(shape(0xff202f42));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(0,0,dp(6),0);holder.addView(tile,lp);
        TextView caption=addText(tile,label,11,false);
        caption.setSingleLine(true);caption.setEllipsize(TextUtils.TruncateAt.END);
        TextView result=addText(tile,value,14,true);
        result.setSingleLine(true);result.setEllipsize(TextUtils.TruncateAt.END);
        return result;
    }

    AiLiveStatusCard(Context context){
        super(context);setOrientation(VERTICAL);
        setPadding(dp(16),dp(17),dp(16),dp(15));setBackground(shape(BG));
        narrow=getResources().getConfiguration().screenWidthDp<365;

        LinearLayout heading=new LinearLayout(context);heading.setOrientation(HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.bottomMargin=dp(8);addView(heading,hp);
        TextView eyebrow=addText(heading,"03   CANLI İŞLEM",14,true);
        eyebrow.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        step=addText(heading,"Hazır",12,true);
        step.setGravity(Gravity.END);step.setTextColor(TEAL);
        step.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
        headline=addText(this,"Henüz analiz başlatılmadı",16,true);
        description=addText(this,"Kayıt seçip işlem başlattığında ayrıntıları burada göreceksin.",12,false);
        description.setMaxLines(2);description.setEllipsize(TextUtils.TruncateAt.END);

        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(TEAL));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff37475b));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(7));
        bp.setMargins(0,dp(8),0,dp(6));addView(progress,bp);
        globalPercent=addText(this,"İşlem başlamadı",12,true);
        globalPercent.setTextColor(TEAL);

        LinearLayout metricsRow=new LinearLayout(context);metricsRow.setOrientation(HORIZONTAL);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);
        mp.setMargins(0,dp(5),0,dp(8));addView(metricsRow,mp);
        elapsed=metric(metricsRow,"GEÇEN SÜRE","00:00:00");
        parts=metric(metricsRow,narrow?"BÖLÜM":"İŞLENEN BÖLÜM","—");
        if(!narrow){
            stageTimer=metric(metricsRow,"BU AŞAMA","00:00:00");
        }else{
            stageTimer=addText(this,"Aşama 00:00:00",11,false);
        }
        audio=addText(this,"İşlenen ses: —",12,false);
        audio.setTextColor(TEAL);

        alert=addText(this,"",12,false);
        alert.setTextColor(WARNING);alert.setVisibility(GONE);

        previewBox=new LinearLayout(context);previewBox.setOrientation(VERTICAL);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);
        pp.topMargin=dp(9);addView(previewBox,pp);
        previewTitle=addText(previewBox,"SON ÇIKARILAN METİN",11,true);
        previewTitle.setTextColor(TEAL);
        preview=addText(previewBox,"",13,false);
        preview.setMaxLines(3);preview.setEllipsize(TextUtils.TruncateAt.END);
        expandPreview=action("Metnin devamını göster");
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(42));
        ep.bottomMargin=dp(8);previewBox.addView(expandPreview,ep);
        expandPreview.setVisibility(GONE);
        expandPreview.setOnClickListener(v->{
            expanded=!expanded;preview.setMaxLines(expanded?Integer.MAX_VALUE:3);
            preview.setEllipsize(expanded?null:TextUtils.TruncateAt.END);
            expandPreview.setText(expanded?"Metni daralt":"Metnin devamını göster");
        });
        previewBox.setVisibility(GONE);

        detailToggle=action("İşlem ayrıntılarını göster");
        LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(-1,dp(42));
        dlp.topMargin=dp(6);addView(detailToggle,dlp);
        detailBox=new LinearLayout(context);detailBox.setOrientation(VERTICAL);
        detailBox.setPadding(dp(12),dp(12),dp(12),dp(4));
        detailBox.setBackground(shape(0xff111b2a));
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-1,-2);
        detailLp.topMargin=dp(8);addView(detailBox,detailLp);
        details=addText(detailBox,"",12,false);
        copyInfo=action("Hata / işlem bilgisini kopyala");
        detailBox.addView(copyInfo,new LinearLayout.LayoutParams(-1,dp(43)));
        copyInfo.setOnClickListener(v->{
            ClipboardManager clipboard=(ClipboardManager)getContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
            if(clipboard!=null){
                clipboard.setPrimaryClip(ClipData.newPlainText("Günİzi teknik tanı",copiedDiagnostic));
                Toast.makeText(getContext(),"Teknik bilgi kopyalandı",Toast.LENGTH_SHORT).show();
            }
        });
        detailBox.setVisibility(GONE);
        detailToggle.setOnClickListener(v->{
            showDetails=!showDetails;detailBox.setVisibility(showDetails?VISIBLE:GONE);
            detailToggle.setText(showDetails?"Ayrıntıları gizle":"İşlem ayrıntılarını göster");
        });
        cancel=action("Analizi durdur");
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(48));
        cp.topMargin=dp(10);addView(cancel,cp);
        cancel.setVisibility(GONE);
    }
    void onCancelClick(OnClickListener callback){cancel.setOnClickListener(callback);}

    void render(SharedPreferences prefs,WorkInfo.State state){
        String phase=prefs.getString("analysisPhase",AiStageMetrics.QUEUED);
        boolean neverStarted=prefs.getLong("analysisStartedAt",0)==0&&state==null;
        String mode=prefs.getString("analysisMode",DailySummaryWorker.TRANSCRIPT);
        String message=prefs.getString("analysisStage","");
        String error=prefs.getString("summaryError","");
        boolean queued=state==WorkInfo.State.ENQUEUED;
        boolean failed=state==WorkInfo.State.FAILED||!error.isEmpty();
        boolean stopped=state==WorkInfo.State.CANCELLED||
            AiStageMetrics.CANCELLED.equals(phase);
        boolean completed=state==WorkInfo.State.SUCCEEDED||
            AiStageMetrics.DONE.equals(phase);
        boolean running=state==WorkInfo.State.RUNNING||
            (!failed&&!stopped&&!completed&&prefs.getLong("analysisStartedAt",0)>
                prefs.getLong("analysisCompletedAt",0));
        if(queued){running=true;phase=AiStageMetrics.QUEUED;}
        if(stopped){running=false;phase=AiStageMetrics.CANCELLED;}
        if(failed){running=false;phase=AiStageMetrics.ERROR;}
        if(completed&&!failed&&!stopped){running=false;phase=AiStageMetrics.DONE;}
        final String stageName=neverStarted?"Henüz analiz başlatılmadı":
            AiStageMetrics.displayStage(phase);
        changed(headline,stageName);
        String display=neverStarted?"Kaydını seçip işlemi başlattığında ilerleme burada görünecek.":
            failed&&!error.isEmpty()?"Hata: "+error:
            stopped?"Kaynak ses korunuyor. İstersen analizi tekrar başlatabilirsin.":
            message.isEmpty()?"Kayıt seçip analiz başlatabilirsin.":message;
        changed(description,display);
        String suffix=DailySummaryWorker.TRANSCRIPT.equals(mode)?" · Yalnızca metin":" · Metin + özet";
        changed(step,neverStarted?"Hazır":AiProgressStatus.stageNumber(phase,mode));
        int percent=prefs.getInt("analysisPercent",0);
        boolean indeterminate=running&&AiProgressStatus.indeterminate(phase);
        progress.setIndeterminate(indeterminate);
        if(!indeterminate)progress.setProgress(Math.min(100,Math.max(0,percent)));
        changed(globalPercent,indeterminate?"Model hazırlanıyor • Sayısal ilerleme yok":
            failed?"İşlem tamamlanamadı":stopped?"İşlem iptal edildi":
            completed?"%100 • Tamamlandı":!running?"Henüz analiz başlatılmadı":
            "%"+Math.max(0,percent)+" • Genel analiz ilerlemesi");
        long start=prefs.getLong("analysisStartedAt",0);
        long completedAt=prefs.getLong("analysisCompletedAt",0);
        long clock=prefs.getLong("analysisStartedElapsed",0);
        long now=android.os.SystemClock.elapsedRealtime();
        long elapsedMs=0;
        if(start>0){
            if(running&&clock>0&&now>=clock)
                elapsedMs=now-clock;
            else
                elapsedMs=Math.max(0,(completedAt>0?completedAt:System.currentTimeMillis())-start);
        }
        changed(elapsed,AiProgressStatus.time(elapsedMs));
        long phaseDuration=running?AiStageMetrics.stageMs(getContext()):0;
        changed(stageTimer,narrow?"Aşama: "+AiProgressStatus.time(phaseDuration):
            AiProgressStatus.time(phaseDuration));
        int currentPart=prefs.getInt("analysisChunkIndex",0),
            totalParts=prefs.getInt("analysisChunkTotal",0);
        int llmDone=prefs.getInt("analysisPartsDone",0),
            llmTotal=prefs.getInt("analysisPartsTotal",0);
        boolean summarizing=AiStageMetrics.LLM_LOADING.equals(phase)||
            AiStageMetrics.EXTRACTING.equals(phase)||
            AiStageMetrics.MERGING.equals(phase);
        changed(parts,summarizing?
            (llmTotal>0?llmDone+" / "+llmTotal:"Hazırlanıyor"):
            AiProgressStatus.partLabel(currentPart,totalParts));
        long audioDone=prefs.getLong("analysisAudioProcessedMs",0),
            audioTotal=prefs.getLong("analysisAudioDuration",0);
        String audioLabel=neverStarted?"":summarizing?
            "Ses çözümlendi • Şimdi metinden konu ve görevler çıkarılıyor":
            audioTotal>0?
                "İşlenen ses: "+AiProgressStatus.audioLabel(audioDone,audioTotal)+
                    " • %"+AiProgressStatus.totalPercent(audioDone,audioTotal):
                "İşlenen ses süresi hazırlanıyor";
        changed(audio,audioLabel);
        audio.setVisibility(audioLabel.isEmpty()?GONE:VISIBLE);

        String excerpt=prefs.getString("analysisPreview","");
        boolean hasPreview=!excerpt.isEmpty();
        previewBox.setVisibility(hasPreview?VISIBLE:GONE);
        if(hasPreview){
            changed(preview,excerpt);
            boolean longText=excerpt.length()>180;
            expandPreview.setVisibility(longText?VISIBLE:GONE);
            changed(previewTitle,summarizing?"SON ÇIKARILAN KONU":"SON OKUNAN KONUŞMA");
        }
        long stale=System.currentTimeMillis()-prefs.getLong("analysisLastAdvanceAt",0);
        String warning=AiProgressStatus.attention(phase,stale,phaseDuration,running);
        changed(alert,warning);alert.setVisibility(warning.isEmpty()?GONE:VISIBLE);
        cancel.setVisibility(running?VISIBLE:GONE);
        if(showDetails){
            String file=prefs.getString("analysisSource","");
            String vad=prefs.getBoolean("aiSileroVad",true)?"Açık":"Kapalı";
            String data="Kaynak: "+file+
                "\nİşlem: "+mode+suffix+
                "\nAşama: "+phase+" • "+stageName+
                "\nİşlenen bölüm: "+AiProgressStatus.partLabel(currentPart,totalParts)+
                "\nBölüm motoru: %"+prefs.getInt("analysisNativePercent",0)+
                "\nİşlenen ses: "+AiProgressStatus.audioLabel(audioDone,audioTotal)+
                "\nSilero VAD: "+vad+
                "\nWhisper açılışı: "+AiProgressStatus.time(prefs.getLong("lastWhisperLoadMs",0))+
                "\nÖzet modeli açılışı: "+AiProgressStatus.time(prefs.getLong("lastLlamaLoadMs",0))+
                "\nHata: "+(error.isEmpty()?"Yok":error);
            changed(details,data);copiedDiagnostic=data;
        }
    }
}
