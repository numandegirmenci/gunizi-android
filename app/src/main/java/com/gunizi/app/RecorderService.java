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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-compliant user-started microphone service with work and on-demand modes. */
public final class RecorderService extends Service {
    public static final String ACTION_START="com.gunizi.START";
    public static final String ACTION_START_MANUAL="com.gunizi.START_MANUAL";
    public static final String ACTION_STOP="com.gunizi.STOP";
    public static final String ACTION_RECOVER="com.gunizi.RECOVER";
    public static final String ACTION_REFRESH="com.gunizi.REFRESH";
    public static volatile boolean running=false,recording=false;
    public static volatile String stateText="Hazır",lastEvent="Kayıt yapılmadı";
    public static volatile long manualEndElapsed=0;
    public static int remainingSeconds(){
        return (int)Math.max(0,(manualEndElapsed-SystemClock.elapsedRealtime()+999)/1000);
    }
    private static final int ID=103;
    private static final String CHANNEL="gunizi_recording";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private MediaRecorder recorder;
    private File activeFile;
    private String activeDay="";
    private Prefs prefs;
    private boolean manualActive=false;
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
        channel.setDescription("Mikrofon kullanımı ve kayıt süresi");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_REFRESH:intent.getAction();
        if(ACTION_STOP.equals(action)){
            stopCurrent();shutdownIdle();return START_NOT_STICKY;
        }
        if(ACTION_RECOVER.equals(action)){
            recover();return running?START_STICKY:START_NOT_STICKY;
        }
        if(ACTION_REFRESH.equals(action)&&!running){
            stopSelf();return START_NOT_STICKY;
        }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stateText="Mikrofon izni gerekli";stopSelf();return START_NOT_STICKY;
        }
        if(!running){
            try{
                startForeground(ID,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                running=true;
            }catch(Exception e){
                Log.e("GunIzi","Foreground microphone refused",e);
                stateText="Android mikrofon başlatmayı engelledi";
                stopSelf();return START_NOT_STICKY;
            }
            handler.post(monitor);
        }
        if(ACTION_START_MANUAL.equals(action)){
            if(!Prefs.MODE_MANUAL.equals(prefs.mode())){
                lastEvent="Önce İsteğe Bağlı Kayıt modunu seç";
            } else if(recorder==null) {
                manualActive=true;
                manualEndElapsed=SystemClock.elapsedRealtime()+prefs.durationMinutes()*60_000L;
                startRecording(LocalDateTime.now(Prefs.ZONE).toLocalDate().toString());
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
        boolean manual=Prefs.MODE_MANUAL.equals(prefs.mode());
        String title=manual?"Günİzi · İsteğe bağlı kayıt":"Günİzi · Mesai kaydı";
        String text=manual?(manualActive?"Kalan "+remainingSeconds()/60+" dk "+remainingSeconds()%60+" sn":
            "Hazır, mikrofon boşta"):(recording?"Mesai kaydı sürüyor":
                prefs.timeString(true)+"–"+prefs.timeString(false)+" bekleniyor");
        return new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(title)
            .setContentText(text).setContentIntent(open).setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause,"Durdur ve kaydet",stop).build();
    }
    private void applySchedule(){
        if(Prefs.MODE_MANUAL.equals(prefs.mode())){
            if(manualActive&&recorder!=null){
                if(SystemClock.elapsedRealtime()>=manualEndElapsed){
                    stopRecording();manualActive=false;manualEndElapsed=0;shutdownIdle();
                }else{
                    stateText="Kayıt sürüyor · "+remainingSeconds()/60+" dk "+remainingSeconds()%60+" sn kaldı";
                }
            }else if(!manualActive){
                if(recorder!=null)stopRecording();
                stateText="İsteğe bağlı kayıt hazır";
            }
            return;
        }
        manualActive=false;manualEndElapsed=0;
        if(!prefs.validRange()){stateText="Mesai saatlerini kontrol et";return;}
        if(prefs.folder()==null){stateText="Kayıt klasörü seçilmedi";return;}
        boolean should=prefs.inWorkWindow();
        String day=LocalDateTime.now(Prefs.ZONE).toLocalDate().toString();
        if(recorder!=null&&(!should||!activeDay.equals(day)))stopRecording();
        if(should&&recorder==null)startRecording(day);
        if(!should&&recorder==null)stateText="Mesai kaydı beklemede";
    }
    private void startRecording(String day){
        if(prefs.folder()==null){stateText="Önce klasör seç";return;}
        File dir=new File(getFilesDir(),"pending_audio");
        if(!dir.exists()&&!dir.mkdirs()){stateText="Geçici klasör açılamadı";return;}
        File output=new File(dir,(manualActive?"GunIzi_Secimli_":"GunIzi_Mesai_")+day+"_"+
            LocalDateTime.now(Prefs.ZONE).format(DateTimeFormatter.ofPattern("HHmmss"))+".ogg");
        MediaRecorder r=null;
        try{
            r=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS);
            r.setAudioChannels(1);
            r.setAudioSamplingRate(16000);
            r.setAudioEncodingBitRate(16000);
            r.setOutputFile(output.getAbsolutePath());
            r.prepare();r.start();
            recorder=r;activeFile=output;activeDay=day;recording=true;
            stateText=manualActive?"İsteğe bağlı kayıt başladı":"Mesai kaydı sürüyor";
        }catch(Exception e){
            Log.e("GunIzi","MediaRecorder start",e);
            stateText="Ses kaydı başlatılamadı: "+e.getClass().getSimpleName();
            if(r!=null)r.release();
            output.delete();recording=false;
            if(manualActive){manualActive=false;manualEndElapsed=0;shutdownIdle();}
        }
    }
    private void stopRecording(){
        MediaRecorder r=recorder;
        if(r==null)return;
        recorder=null;recording=false;
        File audio=activeFile;activeFile=null;
        boolean completed=false;
        try{r.stop();completed=true;}catch(Exception e){Log.e("GunIzi","Recorder stop",e);}
        finally{try{r.reset();}catch(Exception ignored){}r.release();}
        if(audio==null)return;
        if(!completed||audio.length()<64){
            audio.delete();stateText="Kayıt tamamlanamadı";return;
        }
        stateText="Dosya kaydediliyor";
        io.execute(()->{
            File backup=null;
            try{
                backup=CryptoVault.encrypt(getApplicationContext(),audio);
                android.net.Uri saved=ExportManager.writeFile(getApplicationContext(),
                    prefs.folder(),audio,audio.getName(),"audio/ogg");
                if(AiModelStore.ready(getApplicationContext(),AiModelStore.WHISPER)&&
                   AiModelStore.ready(getApplicationContext(),new Prefs(getApplicationContext()).aiModel()))
                    DailySummaryWorker.queue(getApplicationContext(),saved,audio.getName(),true);
                stateText="Kayıt klasöre kaydedildi";
                lastEvent=audio.getName();
                audio.delete();
                backup.delete();
            }catch(Exception e){
                Log.e("GunIzi","Export / backup",e);
                stateText="Aktarım başarısız · Kayıt kurtarma için korundu";
                lastEvent="Kayıt kaydedilemedi: "+e.getMessage();
                // Keep raw audio and/or encrypted backup until successful manual recovery.
            }
        });
    }
    private void recover(){
        io.execute(()->{
            File dir=new File(getNoBackupFilesDir(),"encrypted_pending");
            File[] files=dir.listFiles((d,n)->n.endsWith(".giz"));
            if(files==null||files.length==0){lastEvent="Kurtarılacak yedek yok";return;}
            int recovered=0;
            for(File file:files){
                File raw=null;
                try{
                    raw=CryptoVault.decryptToTemp(getApplicationContext(),file);
                    ExportManager.writeFile(getApplicationContext(),prefs.folder(),raw,
                        file.getName().replaceFirst("\\.giz$",""),"audio/ogg");
                    if(file.delete())recovered++;
                }catch(Exception e){lastEvent="Kurtarma hatası: "+e.getMessage();}
                finally{if(raw!=null)raw.delete();}
            }
            if(recovered>0)lastEvent=recovered+" kayıt kurtarıldı";
        });
    }
    private void stopCurrent(){
        if(recorder!=null)stopRecording();
        manualActive=false;manualEndElapsed=0;
    }
    private void shutdownIdle(){
        running=false;handler.removeCallbacks(monitor);
        try{stopForeground(STOP_FOREGROUND_REMOVE);}catch(Exception ignored){}
        stopSelf();
    }
    @Override public void onDestroy(){
        running=false;handler.removeCallbacks(monitor);
        stopCurrent();io.shutdown();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
