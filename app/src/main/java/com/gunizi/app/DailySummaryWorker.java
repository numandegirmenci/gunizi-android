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
 * Full recording transcription with a local Turkish Vosk model.
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
        if(uri==null||!OfflineModelManager.ready(c))return;
        Data data=new Data.Builder().putString(URI,uri.toString()).putString(NAME,name).build();
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
        if(address==null||fileName==null||!OfflineModelManager.ready(c))
            return Result.failure();
        ensureChannel(c);
        try{
            int type=Build.VERSION.SDK_INT>=35?
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING:
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
            setForegroundAsync(new ForegroundInfo(206,notice("Türkçe ses modeli hazırlanıyor"),type)).get();
            String report=DailyReportEngine.analyze(c,Uri.parse(address),fileName,(pct,message)->{
                setProgressAsync(new Data.Builder().putInt("percent",pct).putString("stage",message).build());
                if(pct%10==0)c.getSystemService(NotificationManager.class).notify(206,notice(message));
            });
            String output=fileName.replaceAll("(?i)\\.(ogg|opus|wav|m4a)$","");
            ExportManager.writeText(c,new Prefs(c).folder(),report,"GunIzi_Ozet_"+output+".txt");
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
