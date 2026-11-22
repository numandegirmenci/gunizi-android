package com.gunizi.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.concurrent.TimeUnit;

/**
 * Full recording transcription with local Whisper and Qwen3.
 * Auto mode runs when charging; manual mode begins when the user requests it.
 * Never uploads recorded audio or its transcript.
 */
public final class DailySummaryWorker extends Worker {
    private static final String CH="gunizi_ai_progress";
    public static final String URI="sourceUri", NAME="sourceName";
    public DailySummaryWorker(@NonNull Context context,@NonNull WorkerParameters params){
        super(context,params);
    }
    private static void ensureChannel(Context c){
        c.getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CH,"Günİzi yerel özetleme",NotificationManager.IMPORTANCE_LOW));
    }
    private Notification notice(String message){
        Context c=getApplicationContext();
        PendingIntent open=PendingIntent.getActivity(c,207,
            new Intent(c,SummaryActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c,CH)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Günİzi • Yerel özetleme")
            .setContentText(message)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open).build();
    }
    static void queue(Context c,Uri uri,String name,boolean charging){
        if(uri==null||!AiModelStore.ready(c,AiModelStore.WHISPER)||!AiModelStore.ready(c,new Prefs(c).aiModel()))return;
        Data data=new Data.Builder().putString(URI,uri.toString()).putString(NAME,name)
            .putString("analysisFocus",new AnalysisPreferences(c).focus()).build();
        Constraints.Builder constraints=new Constraints.Builder().setRequiresBatteryNotLow(true);
        if(charging)constraints.setRequiresCharging(true);
        OneTimeWorkRequest task=new OneTimeWorkRequest.Builder(DailySummaryWorker.class)
            .setConstraints(constraints.build()).setInputData(data)
            .addTag("gunizi-local-summary").build();
        WorkManager.getInstance(c).enqueueUniqueWork("gunizi-summary-"+name,
            ExistingWorkPolicy.KEEP,task);
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        String address=getInputData().getString(URI);
        String fileName=getInputData().getString(NAME);
        if(address==null||fileName==null||!AiModelStore.ready(c,AiModelStore.WHISPER)||!AiModelStore.ready(c,new Prefs(c).aiModel()))
            return Result.failure();
        ensureChannel(c);
        try{
            int type=Build.VERSION.SDK_INT>=35?
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            setForegroundAsync(new ForegroundInfo(206,notice("Türkçe ses modeli hazırlanıyor"),type)).get();
            String fileBase=fileName.replaceAll("(?i)\\.(ogg|opus|wav|m4a)$","");
            java.io.File drafts=new java.io.File(c.getNoBackupFilesDir(),"ai_drafts");
            drafts.mkdirs();
            java.io.File transcriptCache=new java.io.File(drafts,
                fileBase.replaceAll("[^a-zA-Z0-9_-]","_")+".txt");
            AiProgress listener=(pct,message)->{
                setProgressAsync(new Data.Builder().putInt("percent",pct).putString("stage",message).build());
                if(pct%10==0)c.getSystemService(NotificationManager.class).notify(206,notice(message));
            };
            String transcript;
            if(transcriptCache.isFile()&&transcriptCache.length()>50){
                transcript=new String(java.nio.file.Files.readAllBytes(transcriptCache.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
                listener.update(70,"Önceki konuşma dökümü kurtarıldı");
            }else{
                transcript=WhisperDailyTranscriber.transcribe(c,Uri.parse(address),listener);
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(transcriptCache)){
                    out.write(transcript.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            // Save the complete transcript before LLM inference to prevent loss on a model failure.
            ExportManager.writeText(c,new Prefs(c).folder(),
                "GÜNİZİ • TAM TÜRKÇE KONUŞMA DÖKÜMÜ\\n"+transcript,
                "GunIzi_Dokum_"+fileBase+".txt");
            String focus=getInputData().getString("analysisFocus");
            if(focus==null||focus.trim().isEmpty())focus=AnalysisPreferences.DEFAULT;
            String summary=FullDaySummarizer.summarize(c,transcript,focus,listener);
            String report="GÜNİZİ • YEREL QWEN3 GÜNLÜK RAPORU\\n"+
                "Kaynak ses: "+fileName+"\\nModel: Whisper small + seçilen Qwen3\\n"+
                "Not: Otomatik tanıma ve özet hatalı olabilir; önemli bilgileri sesle karşılaştır.\\n\\n"+
                summary+"\\n\\n----- TAM KONUŞMA DÖKÜMÜ -----\\n"+transcript;
            ExportManager.writeText(c,new Prefs(c).folder(),report,"GunIzi_Ozet_"+fileBase+".txt");
            java.util.List<TopicReportRenderer.Entry> topics=TopicReportRenderer.parse(summary);
            String html=TopicReportRenderer.html(topics,fileName);
            TopicReportRenderer.saveInternal(c,html,fileBase);
            UserNotesStore.latestReport(c,
                "Günİzi • "+fileName+" • Günlük özet",
                "ANALİZ TERCİHİ: "+focus+"\n\n"+summary+
                "\n\nNot: Otomatik çıkarımlar önemli kararlar için kontrol edilmelidir.");
            ExportManager.writeHtml(c,new Prefs(c).folder(),html,"GunIzi_KonuTablosu_"+fileBase+".html");
            ExportManager.writeCsv(c,new Prefs(c).folder(),TopicReportRenderer.csv(topics),
                "GunIzi_KonuTablosu_"+fileBase+".csv");
            transcriptCache.delete();
            c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                .putString("summaryLast","Günlük rapor hazır: "+fileName)
                .putString("summaryError","").apply();
            NotificationManager mgr=c.getSystemService(NotificationManager.class);
            mgr.notify(207,new Notification.Builder(c,CH)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Günİzi raporu tamamlandı")
                .setContentText("Günlük özet ve tam döküm klasöre kaydedildi")
                .setAutoCancel(true).build());
            return Result.success();
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();return Result.retry();
        }catch(Exception e){
            c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                .putString("summaryError",e.getClass().getSimpleName()+": "+e.getMessage()).apply();
            return Result.failure();
        }
    }
}
