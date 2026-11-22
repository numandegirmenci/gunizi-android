package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Resumable WorkManager foreground download; survives activity navigation and app backgrounding. */
public final class AiModelDownloadWorker extends Worker {
    static final String KEY="modelId",TAG="gunizi-model-download";
    private static final String CHANNEL="gunizi_model_downloads_v1";
    private static final int NOTICE_ID=401;
    private volatile long lastNotice=0;
    public AiModelDownloadWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}

    static void enqueue(Context c,String model){
        if(!valid(model))throw new IllegalArgumentException("Bilinmeyen model");
        Data data=new Data.Builder().putString(KEY,model).build();
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(AiModelDownloadWorker.class)
            .setInputData(data).setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .addTag(TAG).addTag(TAG+"-"+model).build();
        WorkManager.getInstance(c).enqueueUniqueWork(TAG+"-"+model,
            ExistingWorkPolicy.KEEP,request);
    }
    static boolean valid(String s){
        return AiModelStore.WHISPER_BASE.equals(s)||
            AiModelStore.WHISPER.equals(s)||
            AiModelStore.WHISPER_TURBO.equals(s)||
            AiModelStore.QWEN.equals(s)||AiModelStore.QWEN4.equals(s);
    }
    private String label(String model){
        if(AiModelStore.WHISPER_TURBO.equals(model))return "Whisper Large v3 Turbo";
        if(AiModelStore.WHISPER_BASE.equals(model))return "Whisper base";
        if(AiModelStore.WHISPER.equals(model))return "Whisper small";
        if(AiModelStore.QWEN4.equals(model))return "Qwen3-4B";
        return "Qwen3-1.7B";
    }
    private void channel(){
        NotificationChannel n=new NotificationChannel(CHANNEL,
            "Günİzi • Model indirmeleri",NotificationManager.IMPORTANCE_LOW);
        n.setDescription("Uygulama arka plandayken yapay zeka model indirmelerini gösterir");
        getApplicationContext().getSystemService(NotificationManager.class)
            .createNotificationChannel(n);
    }
    private Notification notification(String title,String message,int percent,boolean ongoing){
        Context c=getApplicationContext();
        Intent intent=new Intent(c,ModelSettingsActivity.class);
        PendingIntent open=PendingIntent.getActivity(c,403,intent,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(message)
            .setOnlyAlertOnce(true).setOngoing(ongoing).setContentIntent(open)
            .setCategory(Notification.CATEGORY_PROGRESS);
        if(ongoing)b.setProgress(100,Math.min(100,Math.max(0,percent)),percent<=0);
        return b.build();
    }
    @NonNull @Override public Result doWork(){
        Context c=getApplicationContext();
        String model=getInputData().getString(KEY);
        if(!valid(model))return Result.failure();
        channel();
        String name=label(model);
        int noticeId=NOTICE_ID+model.hashCode()%31;
        try{
            setForegroundAsync(new ForegroundInfo(noticeId,
                notification("Günİzi • "+name,"İndirme hazırlanıyor",0,true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)).get();
            if(AiModelStore.ready(c,model)){
                setProgressAsync(new Data.Builder().putInt("percent",100)
                    .putString("stage","Model zaten hazır").build());
                return Result.success();
            }
            final int[] pct={0};
            AiModelStore.downloadBlocking(c,model,(message,done)->{
                if(isStopped()||Thread.currentThread().isInterrupted())
                    throw new IllegalStateException("İndirme durduruldu");
                int percent=pct[0];
                java.util.regex.Matcher matcher=java.util.regex.Pattern.compile(
                    "%(\\d+)").matcher(message);
                if(matcher.find())try{percent=Integer.parseInt(matcher.group(1));}
                catch(NumberFormatException ignored){}
                if(done)percent=100;
                pct[0]=percent;
                setProgressAsync(new Data.Builder().putInt("percent",percent)
                    .putString("stage",message).putString("model",model).build());
                long now=SystemClock.elapsedRealtime();
                if(done||now-lastNotice>1800){
                    lastNotice=now;
                    c.getSystemService(NotificationManager.class)
                        .notify(noticeId,notification("Günİzi • "+name,
                            message,percent,!done));
                }
            });
            c.getSystemService(NotificationManager.class).notify(noticeId+101,
                notification(name+" hazır","Artık çevrimdışı kullanılabilir",100,false));
            return Result.success();
        }catch(Exception e){
            String reason=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            setProgressAsync(new Data.Builder().putInt("percent",0)
                .putString("stage","Model indirilemedi: "+reason)
                .putString("model",model).build());
            c.getSharedPreferences("gunizi_ai_downloads",Context.MODE_PRIVATE).edit()
                .putString("error_"+model,reason).apply();
            if(isStopped())return Result.failure();
            if(getRunAttemptCount()<3)return Result.retry();
            c.getSystemService(NotificationManager.class).notify(noticeId+201,
                notification("Günİzi model indirilemedi",
                    name+" • "+reason,0,false));
            return Result.failure();
        }
    }
}
