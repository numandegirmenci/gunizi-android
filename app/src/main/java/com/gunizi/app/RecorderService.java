package com.gunizi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.*;
import android.util.Log;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two distinct user-visible modes:
 * HISTORY: user explicitly starts a rolling temporary Opus buffer, exports PAST minutes.
 * WORK: unchanged planned start/stop and automatic daily file export.
 * Neither mode can reconstruct speech before the microphone started.
 */
public final class RecorderService extends Service {
    public static final String ACTION_START="com.gunizi.START";
    public static final String ACTION_START_HISTORY="com.gunizi.START_HISTORY";
    public static final String ACTION_SAVE_HISTORY="com.gunizi.SAVE_HISTORY";
    public static final String ACTION_STOP="com.gunizi.STOP";
    public static final String ACTION_RECOVER="com.gunizi.RECOVER";
    public static final String ACTION_REFRESH="com.gunizi.REFRESH";
    public static volatile boolean running=false,recording=false,historyActive=false;
    public static volatile int historyHeldSeconds=0;
    public static volatile String stateText="Hazır",lastEvent="Henüz kayıt oluşturulmadı";
    private static final int ID=103;
    private static final String CHANNEL="gunizi_recording";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private MediaRecorder recorder;
    private File activeFile;
    private String activeDay="";
    private Prefs prefs;
    private HistoryAudioStore historyStore;
    private MediaRecorder historyRecorder;
    private File historyCurrentFile;
    private long historyCurrentStart=0;
    private long historyStart=0;
    private long lastHistoryExportAt=0;
    private boolean shutdownAfterExport=false;

    private final Runnable monitor=new Runnable(){
        @Override public void run(){
            if(!running)return;
            applySchedule();
            if(running){
                getSystemService(NotificationManager.class).notify(ID,notification());
                handler.postDelayed(this,1000);
            }
        }
    };
    @Override public void onCreate(){
        super.onCreate();
        prefs=new Prefs(this);
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Günİzi kayıt durumu",NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setDescription("Mikrofon ve kayıt durumunu gösterir");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_REFRESH:intent.getAction();
        if(ACTION_STOP.equals(action)){
            if(historyActive){
                stopHistory(true);
                shutdownIdle();
            }else{
                if(recorder!=null){shutdownAfterExport=true;stopWorkRecording();}
                else shutdownIdle();
            }
            return START_NOT_STICKY;
        }
        if(ACTION_SAVE_HISTORY.equals(action)){
            if(historyActive)saveHistory();
            else lastEvent="Önce geçmiş dinlemeyi başlat";
            return running?START_STICKY:START_NOT_STICKY;
        }
        if(ACTION_RECOVER.equals(action)){recover();return running?START_STICKY:START_NOT_STICKY;}
        if(ACTION_REFRESH.equals(action)&&!running){stopSelf();return START_NOT_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stateText="Mikrofon izni gerekli";stopSelf();return START_NOT_STICKY;
        }
        if(!running){
            try{
                startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                running=true;
            }catch(Exception e){
                Log.e("GunIzi","Microphone service blocked",e);
                stateText="Android mikrofon başlatmayı engelledi";stopSelf();return START_NOT_STICKY;
            }
            handler.post(monitor);
        }
        if(ACTION_START_HISTORY.equals(action)){
            if(recorder!=null){
                lastEvent="Mesai kaydı çalışırken geçmiş dinleme başlatılamaz";
            }else if(!historyActive){
                prefs.mode(Prefs.MODE_HISTORY);
                startHistory();
            }
        }else{
            applySchedule();
        }
        if(running)getSystemService(NotificationManager.class).notify(ID,notification());
        return START_STICKY;
    }
    private Notification notification(){
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,3,
            new Intent(this,RecorderService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean history=historyActive||Prefs.MODE_HISTORY.equals(prefs.mode());
        String title=history?"Günİzi · Geçmiş dinleme":"Günİzi · Mesai kaydı";
        String desc=history?(historyActive?
            "Biriken geçmiş: "+historyHeldSeconds/60+" dk "+historyHeldSeconds%60+" sn":
            "Dinleme kapalı"):
            (recording?"Mesai kaydı sürüyor":
            "Saat "+prefs.timeString(true)+"–"+prefs.timeString(false)+" bekleniyor");
        Notification.Builder builder=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title).setContentText(desc).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause,"Dinlemeyi durdur",stop);
        if(historyActive){
            PendingIntent save=PendingIntent.getService(this,4,
                new Intent(this,RecorderService.class).setAction(ACTION_SAVE_HISTORY),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_save,"Geçmişi kaydet",save);
        }
        return builder.build();
    }
    private void applySchedule(){
        if(historyActive){
            if(!Prefs.MODE_HISTORY.equals(prefs.mode()))return;
            long now=SystemClock.elapsedRealtime();
            if(historyRecorder!=null&&now-historyCurrentStart>=HistoryAudioStore.SEGMENT_MS){
                finishHistorySegment();
                startHistorySegment();
            }
            if(historyActive&&historyStore!=null){
                historyHeldSeconds=(int)historyStore.secondsAvailable(historyCurrentStart);
                stateText="Geçmiş dinleme açık";
            }
            return;
        }
        if(Prefs.MODE_HISTORY.equals(prefs.mode())){
            stateText="Geçmiş dinleme başlatılmadı";return;
        }
        // Work schedule is intentionally preserved.
        if(!prefs.validRange()){stateText="Mesai saatlerini kontrol et";return;}
        if(prefs.folder()==null){stateText="Kayıt klasörü seçilmedi";return;}
        boolean should=prefs.inWorkWindow();
        String day=LocalDateTime.now(Prefs.ZONE).toLocalDate().toString();
        if(recorder!=null&&(!should||!activeDay.equals(day)))stopWorkRecording();
        if(should&&recorder==null)startWorkRecording(day);
        if(!should&&recorder==null)stateText="Mesai kaydı beklemede";
    }
    private MediaRecorder prepareRecorder(File output)throws Exception{
        MediaRecorder r=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
        try{
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16000);
            r.setAudioEncodingBitRate(16000);
            r.setOutputFile(output.getAbsolutePath());
            r.prepare();r.start();
            return r;
        }catch(Exception e){try{r.release();}catch(Exception ignored){}throw e;}
    }
    private void startHistory(){
        historyStore=new HistoryAudioStore(this);
        historyStart=SystemClock.elapsedRealtime();
        historyHeldSeconds=0;
        historyActive=true;
        recording=true;
        if(!startHistorySegment()){
            historyActive=false;recording=false;
            shutdownIdle();
        }else{
            stateText="Geçmiş dinleme açık";
            lastEvent="Geçici geçmiş birikmeye başladı";
        }
    }
    private boolean startHistorySegment(){
        if(!historyActive||historyStore==null)return false;
        File output=historyStore.nextFile();
        try{
            historyCurrentStart=SystemClock.elapsedRealtime();
            historyRecorder=prepareRecorder(output);
            historyCurrentFile=output;
            return true;
        }catch(Exception e){
            Log.e("GunIzi","History segment start",e);
            stateText="Geçmiş dinleme başarısız: "+e.getClass().getSimpleName();
            output.delete();historyRecorder=null;historyCurrentFile=null;
            return false;
        }
    }
    private void finishHistorySegment(){
        MediaRecorder current=historyRecorder;
        if(current==null)return;
        historyRecorder=null;
        File file=historyCurrentFile;historyCurrentFile=null;
        long start=historyCurrentStart;
        long end=SystemClock.elapsedRealtime();
        boolean valid=false;
        try{current.stop();valid=true;}
        catch(Exception e){Log.e("GunIzi","History segment stop",e);}
        finally{try{current.reset();}catch(Exception ignored){}current.release();}
        if(valid&&historyStore!=null)historyStore.add(file,start,end);
        else if(file!=null)file.delete();
    }
    private void stopHistory(boolean deleteTemporary){
        if(!historyActive)return;
        finishHistorySegment();
        historyActive=false;recording=false;historyHeldSeconds=0;
        if(historyStore!=null&&deleteTemporary)historyStore.discard();
        historyStore=null;historyCurrentStart=0;historyStart=0;
        stateText="Geçmiş dinleme durduruldu";
    }
    private void saveHistory(){
        if(historyStore==null||!historyActive)return;
        long now=SystemClock.elapsedRealtime();
        long available=Math.min(HistoryAudioStore.MAX_MS,now-historyStart);
        if(available<1000){lastEvent="Henüz geçmiş birikmedi";return;}
        // Close the current Opus segment, then immediately continue listening.
        finishHistorySegment();
        if(!startHistorySegment()){
            historyActive=false;recording=false;
            stateText="Mikrofon yeniden başlatılamadı";
            shutdownIdle();return;
        }
        long end=SystemClock.elapsedRealtime();
        long take=Math.min((long)prefs.durationMinutes()*60000L,available);
        long cutoff=end-take;
        List<HistoryAudioStore.Segment> selected=historyStore.pinSince(cutoff);
        final HistoryAudioStore store=historyStore;
        final int savedSeconds=(int)(take/1000);
        if(selected.isEmpty()){lastEvent="Kaydedilebilir ses bulunamadı";return;}
        if(prefs.folder()==null){
            store.release(selected);lastEvent="Kayıt klasörü seçilmedi";return;
        }
        stateText="Geçmiş hazırlanıyor · Dinleme devam ediyor";
        io.execute(()->{
            File merged=null,backup=null;
            try{
                merged=HistoryAudioStore.merge(getApplicationContext(),selected,cutoff,end);
                backup=CryptoVault.encrypt(getApplicationContext(),merged);
                String name="GunIzi_Gecmis_"+savedSeconds+"sn_"+
                    LocalDateTime.now(Prefs.ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))+".ogg";
                android.net.Uri exported=ExportManager.writeFile(
                    getApplicationContext(),prefs.folder(),merged,name,"audio/ogg");
                lastEvent="Son "+savedSeconds+" saniyelik geçmiş kaydedildi";
                if(AiModelStore.ready(this,AiModelStore.WHISPER)&&
                   AiModelStore.ready(this,new Prefs(this).aiModel()))
                    DailySummaryWorker.queue(this,exported,name,true);
                if(backup!=null)backup.delete();
            }catch(Exception e){
                Log.e("GunIzi","Past audio export",e);
                lastEvent="Geçmiş kaydedilemedi: "+e.getMessage()+
                    (backup!=null?" · Şifreli yedek korundu":"");
            }finally{
                if(merged!=null)merged.delete();
                store.release(selected);
                if(historyActive)stateText="Geçmiş dinleme açık";
            }
        });
    }
    private void startWorkRecording(String day){
        File dir=new File(getFilesDir(),"pending_audio");
        if(!dir.exists()&&!dir.mkdirs()){stateText="Geçici klasör açılamadı";return;}
        File output=new File(dir,"GunIzi_Mesai_"+day+"_"+
            LocalDateTime.now(Prefs.ZONE).format(DateTimeFormatter.ofPattern("HHmmss"))+".ogg");
        try{
            recorder=prepareRecorder(output);
            activeFile=output;activeDay=day;recording=true;stateText="Mesai kaydı sürüyor";
        }catch(Exception e){
            Log.e("GunIzi","Work recorder start",e);
            output.delete();recording=false;
            stateText="Mesai kaydı başlatılamadı: "+e.getClass().getSimpleName();
        }
    }
    private void stopWorkRecording(){
        MediaRecorder r=recorder;if(r==null)return;
        recorder=null;recording=false;
        File audio=activeFile;activeFile=null;
        boolean completed=false;
        try{r.stop();completed=true;}catch(Exception e){Log.e("GunIzi","Work stop",e);}
        finally{try{r.reset();}catch(Exception ignored){}r.release();}
        if(audio==null)return;
        if(!completed||audio.length()<64){audio.delete();stateText="Kayıt tamamlanamadı";return;}
        stateText="Mesai kaydı klasöre aktarılıyor";
        io.execute(()->{
            File backup=null;
            try{
                backup=CryptoVault.encrypt(getApplicationContext(),audio);
                android.net.Uri saved=ExportManager.writeFile(getApplicationContext(),prefs.folder(),
                    audio,audio.getName(),"audio/ogg");
                if(AiModelStore.ready(this,AiModelStore.WHISPER)&&
                    AiModelStore.ready(this,new Prefs(this).aiModel()))
                    DailySummaryWorker.queue(this,saved,audio.getName(),true);
                stateText="Mesai kaydı kaydedildi";lastEvent=audio.getName();
                audio.delete();backup.delete();
            }catch(Exception e){
                Log.e("GunIzi","Work export",e);
                stateText="Kurtarma için kayıt korundu";
                lastEvent="Kayıt aktarımı başarısız: "+e.getMessage();
            }finally{
                if(shutdownAfterExport)handler.post(()->{
                    shutdownAfterExport=false;shutdownIdle();
                });
            }
        });
    }
    private void recover(){
        io.execute(()->{
            File dir=new File(getNoBackupFilesDir(),"encrypted_pending");
            File[] files=dir.listFiles((d,n)->n.endsWith(".giz"));
            if(files==null||files.length==0){lastEvent="Kurtarılacak kayıt yok";return;}
            int count=0;
            for(File file:files){
                File raw=null;
                try{
                    raw=CryptoVault.decryptToTemp(this,file);
                    ExportManager.writeFile(this,prefs.folder(),raw,
                        file.getName().replaceFirst("\\.giz$",""),"audio/ogg");
                    if(file.delete())count++;
                }catch(Exception e){lastEvent="Kurtarma hatası: "+e.getMessage();}
                finally{if(raw!=null)raw.delete();}
            }
            if(count>0)lastEvent=count+" kayıt kurtarıldı";
        });
    }
    private void shutdownIdle(){
        running=false;handler.removeCallbacks(monitor);
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
        stopSelf();
    }
    @Override public void onDestroy(){
        running=false;handler.removeCallbacks(monitor);
        if(historyActive)stopHistory(true);
        if(recorder!=null)stopWorkRecording();
        io.shutdown();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
