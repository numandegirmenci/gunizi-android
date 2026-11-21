package com.gunizi.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.os.*;
import android.util.Log;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-started microphone foreground service. Does not bypass Android background restrictions. */
public final class RecorderService extends Service {
    public static final String ACTION_START="com.gunizi.START";
    public static final String ACTION_STOP="com.gunizi.STOP";
    public static final String ACTION_SAVE_LAST="com.gunizi.SAVE_LAST";
    public static final String ACTION_RECOVER="com.gunizi.RECOVER";
    public static final String ACTION_REFRESH="com.gunizi.REFRESH";

    public static volatile boolean running=false;
    public static volatile boolean recording=false;
    public static volatile String stateText="Hazır";
    public static volatile String lastEvent="Henüz kayıt oluşturulmadı";
    private static final String CHANNEL="gunizi_recording";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean captureActive=new AtomicBoolean(false);
    public static int ramMinutes(){return memoryBytes()/32000/60;}
    private static int memoryBytes() {
        long heap=Runtime.getRuntime().maxMemory();
        return (int)Math.min(256L*1024*1024,Math.max(32L*1024*1024,(long)(heap*0.60)));
    }
    private final PcmRingBuffer ring=new PcmRingBuffer(memoryBytes());
    private AudioRecord audioRecord;
    private Thread captureThread;
    private MediaRecorder recorder;
    private File activeFile;
    private String activeDay="";
    private Prefs prefs;

    private final Runnable monitor=new Runnable(){
        @Override public void run(){
            if(!running)return;
            applySchedule();
            handler.postDelayed(this,15000);
        }
    };

    @Override public void onCreate(){
        super.onCreate();
        prefs=new Prefs(this);
        NotificationManager mgr=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Günİzi kayıt servisi",NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.setDescription("Aktif mikrofon ve kayıt durumu");
        mgr.createNotificationChannel(channel);
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)){
            running=false;
            handler.removeCallbacks(monitor);
            stopCurrent();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            stateText="Durduruldu";
            return START_NOT_STICKY;
        }
        if(ACTION_SAVE_LAST.equals(action)){saveLast();return START_STICKY;}
        if(ACTION_RECOVER.equals(action)){recover();return START_STICKY;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            stateText="Mikrofon izni gerekli";stopSelf();return START_NOT_STICKY;
        }
        if(!running){
            try{
                startForeground(103,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                running=true;
            }catch(Exception e){
                Log.e("GunIzi","Mic service rejected",e);
                stateText="Android mikrofon başlatmayı engelledi";
                stopSelf();return START_NOT_STICKY;
            }
            handler.post(monitor);
        } else applySchedule();
        getSystemService(NotificationManager.class).notify(103,notification());
        return START_STICKY;
    }

    private Notification notification(){
        PendingIntent openPi=PendingIntent.getActivity(this,2,
            new Intent(this,MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stopPi=PendingIntent.getService(this,3,
            new Intent(this,RecorderService.class).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Günİzi • "+(Prefs.MODE_CONTINUOUS.equals(prefs.mode())?"Sürekli dinleme":"Mesai kaydı"))
            .setContentText(Prefs.MODE_CONTINUOUS.equals(prefs.mode())
                ?"Yaklaşık "+(ring.capacitySeconds()/60)+" dakika RAM belleği etkin"
                :"Saat "+prefs.timeString(true)+"–"+prefs.timeString(false)+" • Otomatik kayıt")
            .setSubText("Sesler cihazın içinde tutulur")
            .setCategory(Notification.CATEGORY_SERVICE).setPriority(Notification.PRIORITY_LOW)
            .setContentIntent(openPi).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause,"Durdur",stopPi);
        if(Prefs.MODE_CONTINUOUS.equals(prefs.mode())){
            PendingIntent savePi=PendingIntent.getService(this,4,
                new Intent(this,RecorderService.class).setAction(ACTION_SAVE_LAST),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            builder.addAction(android.R.drawable.ic_menu_save,"Geçmişi kaydet",savePi);
        }
        return builder.build();
    }

    private void applySchedule(){
        if(Prefs.MODE_CONTINUOUS.equals(prefs.mode())){
            if(recorder!=null)stopWorkRecording();
            if(!captureActive.get())startRing();
            return;
        }
        if(captureActive.get())stopRing();
        if(!prefs.validRange()){
            stateText="Mesai saatlerini kontrol et";
            return;
        }
        if(prefs.folder()==null){
            stateText="Önce kayıt klasörünü seç";
            return;
        }
        boolean should=prefs.inWorkWindow();
        String day=LocalDateTime.now(Prefs.ZONE).toLocalDate().toString();
        if(recorder!=null&&(!should||!activeDay.equals(day)))stopWorkRecording();
        if(should&&recorder==null)startWorkRecording(day);
        if(!should&&recorder==null)stateText="Mesai saati bekleniyor";
    }

    private void startWorkRecording(String day){
        File dir=new File(getFilesDir(),"active_recordings");
        if(!dir.exists()&&!dir.mkdirs()){
            stateText="Kayıt klasörü oluşturulamadı";return;
        }
        File output=new File(dir,"GunIzi_"+day+"_"+LocalDateTime.now(Prefs.ZONE)
            .format(DateTimeFormatter.ofPattern("HHmmss"))+".ogg");
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
            r.prepare();
            r.start();
            recorder=r;
            activeFile=output;
            activeDay=day;
            recording=true;
            stateText="Mesai kaydı sürüyor";
        }catch(Exception e){
            Log.e("GunIzi","Opus start",e);
            stateText="Opus kaydı başlatılamadı: "+e.getClass().getSimpleName();
            recording=false;
            if(r!=null)try{r.release();}catch(Exception ignored){}
            output.delete();
        }
    }

    private void stopWorkRecording(){
        MediaRecorder r=recorder;
        if(r==null)return;
        recorder=null;recording=false;
        File f=activeFile;activeFile=null;
        boolean complete=false;
        try{r.stop();complete=true;}
        catch(Exception e){Log.e("GunIzi","Opus stop",e);}
        finally{try{r.reset();}catch(Exception ignored){}r.release();}
        if(f==null)return;
        if(!complete||f.length()<64){
            f.delete();
            stateText="Kayıt tamamlanamadı";return;
        }
        stateText="Dosya klasöre kaydediliyor";
        io.execute(()->{
            try{
                // Completed sessions only. Unfinished active audio is not encrypted.
                File encrypted=CryptoVault.encrypt(getApplicationContext(),f);
                try{
                    android.net.Uri saved=ExportManager.writeFile(
                        getApplicationContext(),prefs.folder(),f,f.getName(),"audio/ogg");
                    if(OfflineModelManager.ready(getApplicationContext()))
                        DailySummaryWorker.queue(getApplicationContext(),saved,f.getName(),true);
                    lastEvent="Kaydedildi: "+f.getName()+
                        (OfflineModelManager.ready(getApplicationContext())?
                          " • Şarjdayken yerel özet hazırlanacak":"");
                    stateText="Mesai kaydı kaydedildi";
                    encrypted.delete();
                    f.delete();
                }catch(Exception e){
                    f.delete();
                    stateText="Aktarma başarısız; şifreli yedek saklandı";
                    lastEvent="Klasöre aktarılamadı: "+e.getMessage();
                }
            }catch(Exception e){
                Log.e("GunIzi","Backup",e);
                stateText="Özel depolamadaki kayıt korundu";
                lastEvent="Yedekleme hatası: "+e.getMessage();
            }
        });
    }

    private void startRing(){
        AudioRecord a=null;
        try{
            int min=AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("Mikrofon biçimi desteklenmiyor");
            a=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,
                AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,
                Math.max(min*4,4096));
            if(a.getState()!=AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("AudioRecord açılamadı");
            a.startRecording();
            audioRecord=a;
            captureActive.set(true);
            recording=true;
            stateText="Sürekli kayıt • Son "+(ring.capacitySeconds()/60)+" dakika RAM'de";
            final AudioRecord source=a;
            captureThread=new Thread(()->{
                byte[] buf=new byte[2560];
                while(captureActive.get()){
                    try{
                        int n=source.read(buf,0,buf.length);
                        if(n>0)ring.write(buf,0,n);
                        else if(n<0){stateText="Mikrofondan ses okunamadı";break;}
                    }catch(Exception e){Log.e("GunIzi","RAM record",e);break;}
                }
            },"GunIzi-RamCapture");
            captureThread.start();
        }catch(Exception e){
            Log.e("GunIzi","AudioRecord",e);
            stateText="RAM kaydı başlatılamadı: "+e.getMessage();
            recording=false;
            if(a!=null)try{a.release();}catch(Exception ignored){}
        }
    }

    private void stopRing(){
        captureActive.set(false);
        recording=false;
        AudioRecord a=audioRecord;audioRecord=null;
        if(a!=null){
            try{a.stop();}catch(Exception ignored){}
            a.release();
        }
        Thread t=captureThread;captureThread=null;
        if(t!=null)try{t.join(500);}catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }

    private void saveLast(){
        if(!Prefs.MODE_CONTINUOUS.equals(prefs.mode())){
            lastEvent="Önce Sürekli Kayıt modunu seç";return;
        }
        if(ring.size()==0){lastEvent="RAM tamponu henüz boş";return;}
        stateText="RAM kaydı dışa aktarılıyor";
        io.execute(()->{
            try{
                String stamp=LocalDateTime.now(Prefs.ZONE)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"));
                int seconds=ring.size()/32000;
                ExportManager.writeWav(getApplicationContext(),prefs.folder(),ring,
                    "GunIzi_Gecmis_"+stamp+".wav");
                lastEvent="Son "+(seconds/60)+" dakika kaydedildi";
                stateText="Sürekli kayıt açık";
            }catch(Exception e){
                lastEvent="Kaydedilemedi: "+e.getMessage();
                stateText="Klasör erişimini kontrol et";
            }
        });
    }

    private void recover(){
        io.execute(()->{
            File dir=new File(getNoBackupFilesDir(),"encrypted_pending");
            File[] files=dir.listFiles((d,n)->n.endsWith(".giz"));
            if(files==null||files.length==0){
                lastEvent="Kurtarılacak yedek yok";return;
            }
            int recovered=0;
            for(File encrypted:files){
                File decrypted=null;
                try{
                    decrypted=CryptoVault.decryptToTemp(getApplicationContext(),encrypted);
                    String name=encrypted.getName().replaceFirst("\\.giz$","");
                    ExportManager.writeFile(getApplicationContext(),prefs.folder(),decrypted,
                        name,"audio/ogg");
                    if(encrypted.delete())recovered++;
                }catch(Exception e){lastEvent="Kurtarma hatası: "+e.getMessage();}
                finally{if(decrypted!=null)decrypted.delete();}
            }
            if(recovered>0)lastEvent=recovered+" kayıt kurtarıldı";
        });
    }

    private void stopCurrent(){
        if(recorder!=null)stopWorkRecording();
        if(captureActive.get())stopRing();
    }

    @Override public void onDestroy(){
        running=false;handler.removeCallbacks(monitor);stopCurrent();io.shutdown();super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
