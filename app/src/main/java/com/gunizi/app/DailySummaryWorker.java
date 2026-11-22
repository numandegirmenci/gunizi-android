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
import com.gunizi.app.ai.asr.SpeechEngineRegistry;

/**
 * Two real workflows: transcription only (Whisper) and full daily report
 * (Whisper then Qwen). Runs in a foreground WorkManager worker and exposes status.
 */
public final class DailySummaryWorker extends Worker {
    private static final String CH="gunizi_ai_progress_v2";
    public static final String URI="sourceUri",NAME="sourceName",MODE="analysisMode";
    public static final String TRANSCRIPT="transcript",REPORT="report",TEXT_REPORT="text_report";
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
            .putLong("analysisStartedAt",System.currentTimeMillis())
            .putLong("analysisProgressAt",System.currentTimeMillis())
            .putString("analysisPreview","")
            .putInt("analysisPartsDone",0).putInt("analysisPartsTotal",0)
            .putString("analysisWorkId",task.getId().toString())
            .putLong("analysisCompletedAt",0)
            .putString("summaryError","").apply();
        WorkManager.getInstance(c).enqueueUniqueWork(
            "gunizi-analysis-"+mode+"-"+Integer.toHexString(uri.toString().hashCode()),
            ExistingWorkPolicy.REPLACE,task);
    }
    /** Summarize a stored local transcript; do not reload Whisper. */
    public static boolean queueFromTranscript(Context c){
        android.content.SharedPreferences prefs=c.getSharedPreferences("gunizi",Context.MODE_PRIVATE);
        String local=prefs.getString("transcriptInternal",null);
        String name=prefs.getString("transcriptSource",null);
        if(local==null||name==null||!AiModelStore.ready(c,new Prefs(c).aiModel()))
            return false;
        try{
            File dir=new File(c.getNoBackupFilesDir(),"transcripts").getCanonicalFile();
            File file=new File(local).getCanonicalFile();
            if(!file.getPath().startsWith(dir.getPath()+File.separator)||
               !file.isFile()||file.length()==0)return false;
        }catch(IOException e){return false;}
        AnalysisPreferences a=new AnalysisPreferences(c);
        Data data=new Data.Builder().putString(NAME,name).putString(MODE,TEXT_REPORT)
            .putString("transcriptPath",local)
            .putString("analysisFocus",a.focus())
            .putString("speechModel",a.speechModel())
            .putString("languageModel",new Prefs(c).aiModel()).build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(DailySummaryWorker.class)
            .setInputData(data)
            .setConstraints(new Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag("gunizi-local-summary").build();
        long now=System.currentTimeMillis();
        prefs.edit().putString("analysisSource",name)
            .putString("analysisMode",TEXT_REPORT)
            .putString("analysisStage","Hazır konuşma dökümü Qwen3 için sırada")
            .putInt("analysisPercent",0)
            .putLong("analysisStartedAt",now).putLong("analysisProgressAt",now)
            .putString("summaryError","").putString("analysisPreview","")
            .putString("analysisWorkId",request.getId().toString())
            .putLong("analysisCompletedAt",0).apply();
        WorkManager.getInstance(c).enqueueUniqueWork("gunizi-text-summary",
            ExistingWorkPolicy.REPLACE,request);
        return true;
    }
    private void progress(int percent,String message,String source){
        int n=Math.max(0,Math.min(100,percent));
        Data data=new Data.Builder().putInt("percent",n)
            .putString("stage",message).putString("source",source).build();
        setProgressAsync(data);
        Context c=getApplicationContext();
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("analysisStage",message).putInt("analysisPercent",n)
            .putLong("analysisProgressAt",System.currentTimeMillis())
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
        boolean useExistingText=TEXT_REPORT.equals(mode);
        if(fileName==null||(!useExistingText&&uriString==null)||
           (!useExistingText&&!AiModelStore.ready(c,speech))||
           ((REPORT.equals(mode)||useExistingText)&&!AiModelStore.ready(c,language)))
            return Result.failure();
        ensureChannel(c);
        final String selectedMode=mode;
        final String source=fileName;
        try{
            int type=Build.VERSION.SDK_INT>=35?
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            setForegroundAsync(new ForegroundInfo(ACTIVE_NOTICE,
                notice(source,useExistingText?"Kaydedilen konuşma metni açılıyor":
                    "Konuşma modeli hazırlanıyor",0,true),type)).get();
            AiProgress listener=(percent,message)->progress(percent,message,source);
            AiStageMetrics.enter(c,AiStageMetrics.PREFLIGHT);
            if(!useExistingText){
                listener.update(0,"Ses biçimi ve süresi kontrol ediliyor");
                AiAudioPreflight.Info info=AiAudioPreflight.inspect(c,Uri.parse(uriString));
                c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                    .putLong("analysisAudioDuration",info.durationMs)
                    .putString("analysisCodec",info.codec).apply();
            }
            String base=fileName.replaceAll("(?i)\\.(ogg|opus|wav|m4a|mp3|aac)$","");
            File cacheFolder=new File(c.getNoBackupFilesDir(),"ai_drafts");
            cacheFolder.mkdirs();
            File transcriptCache=new File(cacheFolder,
                safe(base)+"_"+speech+"_"+Integer.toHexString(
                    (uriString==null?"saved-transcript":uriString).hashCode())+".txt");
            String transcript;
            if(useExistingText){
                listener.update(70,"Konuşma metni okunuyor • Whisper tekrar çalıştırılmıyor");
                String local=getInputData().getString("transcriptPath");
                String saved=c.getSharedPreferences("gunizi",Context.MODE_PRIVATE)
                    .getString("transcriptInternal","");
                if(local==null||!local.equals(saved))
                    throw new IOException("Kayıtlı döküm yolu değişmiş");
                File allowed=new File(c.getNoBackupFilesDir(),"transcripts").getCanonicalFile();
                File existing=new File(local).getCanonicalFile();
                if(!existing.getPath().startsWith(allowed.getPath()+File.separator)||
                   !existing.isFile()||existing.length()>8L*1024*1024)
                    throw new IOException("Konuşma dökümü bulunamadı");
                transcript=new String(Files.readAllBytes(existing.toPath()),StandardCharsets.UTF_8);
                if(transcript.trim().isEmpty())throw new IOException("Konuşma dökümü boş");
            }else{
                if(transcriptCache.isFile()&&transcriptCache.length()>50){
                    transcript=new String(Files.readAllBytes(transcriptCache.toPath()),StandardCharsets.UTF_8);
                    listener.update(70,"Önceki konuşma dökümü bulundu");
                }else{
                    transcript=SpeechEngineRegistry.transcribe(c,Uri.parse(uriString),speech,listener);
                    try(FileOutputStream out=new FileOutputStream(transcriptCache)){
                        out.write(transcript.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
                    }
                }
                listener.update(70,"Konuşmalar yazıya döküldü; sonuç kaydediliyor");
                File internal=writeInternal(c,base,transcript);
                c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                    .putString("transcriptInternal",internal.getAbsolutePath())
                    .putString("transcriptSource",fileName).apply();
            }
            Uri folder=new Prefs(c).folder();
            if(folder!=null&&!useExistingText)ExportManager.writeText(c,folder,
                "GÜNİZİ • TAM TÜRKÇE KONUŞMA DÖKÜMÜ\n"+transcript,
                "GunIzi_Dokum_"+base+".txt");
            if(transcript.trim().isEmpty())
                throw new IOException("Kaydın içinde anlaşılır konuşma bulunamadı. "+
                    "Kaydı dinleyip daha hafif Whisper modeliyle tekrar dene.");
            if(TRANSCRIPT.equals(selectedMode)){
                AiStageMetrics.enter(c,AiStageMetrics.TEXT_READY);
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
        }catch(OutOfMemoryError oom){
            AiStageMetrics.enter(c,AiStageMetrics.ERROR);
            return failure(c,source,"Model açılırken RAM yetersiz kaldı. "+
                "Whisper base veya small seçip yeniden dene.");
        }catch(LinkageError nativeError){
            AiStageMetrics.enter(c,AiStageMetrics.ERROR);
            return failure(c,source,"Yerel konuşma motoru başlatılamadı: "+
                nativeError.getClass().getSimpleName());
        }catch(Exception e){
            if(isStopped())return Result.failure();
            AiStageMetrics.enter(c,AiStageMetrics.ERROR);
            String message=(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());
            c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
                .putString("summaryError",message)
                .putString("analysisStage","Hata: "+message)
                .putLong("analysisCompletedAt",System.currentTimeMillis()).apply();
            c.getSystemService(NotificationManager.class).notify(FINISHED_NOTICE+1,
                notice(source,"İşlem tamamlanamadı: "+message,0,false));
            return Result.failure();
        }
    }
    private Result failure(Context c,String source,String detail){
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("summaryError",detail)
            .putString("analysisStage","Hata: "+detail)
            .putLong("analysisCompletedAt",System.currentTimeMillis()).apply();
        c.getSystemService(NotificationManager.class).notify(FINISHED_NOTICE+1,
            notice(source,"İşlem tamamlanamadı: "+detail,0,false));
        return Result.failure();
    }
    private void finish(Context c,String title,String message){
        AiStageMetrics.enter(c,AiStageMetrics.DONE);
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("summaryLast",message).putString("summaryError","")
            .putInt("analysisPercent",100).putString("analysisStage",title)
            .putLong("analysisCompletedAt",System.currentTimeMillis()).apply();
        c.getSystemService(NotificationManager.class).notify(FINISHED_NOTICE,
            notice(title,message,100,false));
    }
}
