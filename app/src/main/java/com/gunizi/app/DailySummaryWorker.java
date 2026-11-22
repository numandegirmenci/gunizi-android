package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;

/**
 * Two real workflows: transcription only (Whisper) and full daily report
 * (Whisper then Qwen). Runs in a foreground WorkManager worker and exposes status.
 */
public final class DailySummaryWorker extends Worker {
    private static final String CH="gunizi_ai_progress_v2";
    public static final String URI="sourceUri",NAME="sourceName",MODE="analysisMode";
    public static final String TRANSCRIPT="transcript",REPORT="report";
    private static final int ACTIVE_NOTICE=206,FINISHED_NOTICE=207;
    private volatile long lastNotification=0;
    public DailySummaryWorker(@NonNull Context c,@NonNull WorkerParameters params){super(c,params);}
    private static void ensureChannel(Context c){
        NotificationChannel channel=new NotificationChannel(CH,"Günİzi • Yapay Zeka İşlemleri",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Konuşma çözümleme ve günlük rapor ilerlemesini gösterir");
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private Notification notice(String source,String stage,int percent,boolean working){
        Context c=getApplicationContext();
        PendingIntent open=PendingIntent.getActivity(c,207,
            new Intent(c,SummaryActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,CH)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(working?"Günİzi • "+source:"Günİzi • Analiz tamamlandı")
            .setContentText(stage).setStyle(new Notification.BigTextStyle().bigText(stage))
            .setCategory(working?Notification.CATEGORY_PROGRESS:Notification.CATEGORY_STATUS)
            .setOnlyAlertOnce(true).setOngoing(working).setContentIntent(open);
        if(working)builder.setProgress(100,Math.max(0,Math.min(100,percent)),false);
        return builder.build();
    }
    public static void queue(Context c,Uri uri,String name,boolean charging){
        queue(c,uri,name,charging,REPORT);
    }
    public static void queue(Context c,Uri uri,String name,boolean charging,String mode){
        if(uri==null)return;
        if(!TRANSCRIPT.equals(mode)&&!REPORT.equals(mode))throw new IllegalArgumentException("Analiz modu");
        AnalysisPreferences p=new AnalysisPreferences(c);
        Prefs settings=new Prefs(c);
        if(!AiModelStore.ready(c,p.speechModel()))return;
        if(REPORT.equals(mode)&&!AiModelStore.ready(c,settings.aiModel()))return;
        Data data=new Data.Builder().putString(URI,uri.toString()).putString(NAME,name)
            .putString(MODE,mode).putString("analysisFocus",p.focus())
            .putString("speechModel",p.speechModel())
            .putString("languageModel",settings.aiModel()).build();
        Constraints.Builder constraints=new Constraints.Builder()
            .setRequiresBatteryNotLow(true);
        if(charging)constraints.setRequiresCharging(true);
        OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(DailySummaryWorker.class)
            .setConstraints(constraints.build()).setInputData(data)
            .addTag("gunizi-local-summary").build();
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("analysisSource",name)
            .putString("analysisMode",mode)
            .putString("analysisStage",charging?
                "Şarj ve pil koşullarını bekliyor":"İşlem sıraya alındı")
            .putInt("analysisPercent",0)
            .putString("summaryError","").apply();
        WorkManager.getInstance(c).enqueueUniqueWork(
            "gunizi-analysis-"+mode+"-"+Integer.toHexString(uri.toString().hashCode()),
            ExistingWorkPolicy.REPLACE,task);
    }
    private void progress(int percent,String message,String source){
        int n=Math.max(0,Math.min(100,percent));
        Data data=new Data.Builder().putInt("percent",n)
            .putString("stage",message).putString("source",source).build();
        setProgressAsync(data);
        Context c=getApplicationContext();
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("analysisStage",message).putInt("analysisPercent",n)
            .putString("analysisSource",source).apply();
        long time=SystemClock.elapsedRealtime();
        if(time-lastNotification>=1800||n==0||n==100){
            lastNotification=time;
            c.getSystemService(NotificationManager.class)
                .notify(ACTIVE_NOTICE,notice(source,message,n,true));
        }
        if(isStopped()||Thread.currentThread().isInterrupted())
            throw new IllegalStateException("İşlem iptal edildi");
    }
    private static File outputDir(Context c)throws IOException{
        File dir=new File(c.getNoBackupFilesDir(),"transcripts");
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Konuşma dosyaları dizini oluşturulamadı");
        return dir;
    }
    private static String safe(String name){
        String s=name.replaceAll("[^a-zA-Z0-9_-]","_");
        return s.length()>65?s.substring(0,65):s;
    }
    private static File writeInternal(Context c,String source,String transcript)throws Exception{
        File dir=outputDir(c);
        File dst=new File(dir,safe(source)+"_"+Integer.toHexString(source.hashCode())+".txt");
        File tmp=new File(dir,dst.getName()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){
            out.write(transcript.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
        if(dst.exists()&&!dst.delete())throw new IOException("Önceki döküm değiştirilemedi");
        if(!tmp.renameTo(dst))throw new IOException("Döküm kaydedilemedi");
        return dst;
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        String uriString=getInputData().getString(URI);
        String fileName=getInputData().getString(NAME);
        String mode=getInputData().getString(MODE);
        String speech=getInputData().getString("speechModel");
        String language=getInputData().getString("languageModel");
        if(mode==null)mode=REPORT;
        if(speech==null)speech=new AnalysisPreferences(c).speechModel();
        if(language==null)language=new Prefs(c).aiModel();
        if(uriString==null||fileName==null||!AiModelStore.ready(c,speech)||
           (REPORT.equals(mode)&&!AiModelStore.ready(c,language)))return Result.failure();
        ensureChannel(c);
        final String selectedMode=mode;
        final String source=fileName;
        try{
            int type=Build.VERSION.SDK_INT>=35?
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            setForegroundAsync(new ForegroundInfo(ACTIVE_NOTICE,
                notice(source,"Konuşma modeli başlatılıyor",0,true),type)).get();
            AiProgress listener=(percent,message)->progress(percent,message,source);
            String base=fileName.replaceAll("(?i)\\.(ogg|opus|wav|m4a|mp3|aac)$","");
            File cacheFolder=new File(c.getNoBackupFilesDir(),"ai_drafts");
            cacheFolder.mkdirs();
            File transcriptCache=new File(cacheFolder,
                safe(base)+"_"+speech+"_"+Integer.toHexString(uriString.hashCode())+".txt");
            String transcript;
            if(transcriptCache.isFile()&&transcriptCache.length()>50){
                transcript=new String(Files.readAllBytes(transcriptCache.toPath()),StandardCharsets.UTF_8);
                listener.update(70,"Önceki konuşma dökümü bulundu");
            }else{
                transcript=WhisperDailyTranscriber.transcribe(c,Uri.parse(uriString),speech,listener);
                try(FileOutputStream out=new FileOutputStream(transcriptCache)){
                    out.write(transcript.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
                }
            }
            listener.update(70,"Konuşmalar yazıya döküldü; sonuç kaydediliyor");
            File internal=writeInternal(c,base,transcript);
            c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                .putString("transcriptInternal",internal.getAbsolutePath())
                .putString("transcriptSource",fileName).apply();
            Uri folder=new Prefs(c).folder();
            if(folder!=null)ExportManager.writeText(c,folder,
                "GÜNİZİ • TAM TÜRKÇE KONUŞMA DÖKÜMÜ\n"+transcript,
                "GunIzi_Dokum_"+base+".txt");
            if(TRANSCRIPT.equals(selectedMode)){
                finish(c,"Konuşma dökümü hazır","Yazıya dökülen konuşma: "+fileName);
                return Result.success();
            }
            String focus=getInputData().getString("analysisFocus");
            if(focus==null||focus.trim().isEmpty())focus=AnalysisPreferences.DEFAULT;
            listener.update(71,"Qwen3 ile kararlar ve konular inceleniyor");
            String summary=FullDaySummarizer.summarize(c,transcript,focus,language,listener);
            listener.update(99,"Konu tablosu, notlar ve rapor kaydediliyor");
            String report="GÜNİZİ • YEREL YAPAY ZEKA GÜNLÜK RAPORU\n"+
                "Kaynak: "+fileName+"\nKonuşma modeli: "+speech+"\nÖzet modeli: "+language+
                "\nÖnemli: Kararları kaynak sesle karşılaştır.\n\n"+
                summary+"\n\n----- KONUŞMA DÖKÜMÜ -----\n"+transcript;
            if(folder!=null)ExportManager.writeText(c,folder,report,"GunIzi_Ozet_"+base+".txt");
            List<TopicReportRenderer.Entry> topics=TopicReportRenderer.parse(summary);
            String html=TopicReportRenderer.html(topics,fileName);
            TopicReportRenderer.saveInternal(c,html,base);
            UserNotesStore.latestReport(c,"Günİzi • "+fileName+" • Günlük özet",
                "ANALİZ TERCİHİ: "+focus+"\n\n"+TopicReportRenderer.noteText(topics)+
                "\n\n--- AYRINTILI DEĞERLENDİRME ---\n\n"+summary);
            if(folder!=null){
                ExportManager.writeHtml(c,folder,html,"GunIzi_KonuTablosu_"+base+".html");
                ExportManager.writeCsv(c,folder,TopicReportRenderer.csv(topics),
                    "GunIzi_KonuTablosu_"+base+".csv");
            }
            finish(c,"Günlük rapor hazır","Konu tablosu ve özet: "+fileName);
            return Result.success();
        }catch(Exception e){
            if(isStopped())return Result.failure();
            String message=(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                .putString("summaryError",message)
                .putString("analysisStage","Hata: "+message).apply();
            c.getSystemService(NotificationManager.class).notify(FINISHED_NOTICE+1,
                notice(source,"İşlem tamamlanamadı: "+message,0,false));
            return Result.failure();
        }
    }
    private void finish(Context c,String title,String message){
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("summaryLast",message).putString("summaryError","")
            .putInt("analysisPercent",100).putString("analysisStage",title).apply();
        c.getSystemService(NotificationManager.class).notify(FINISHED_NOTICE,
            notice(title,message,100,false));
    }
}
