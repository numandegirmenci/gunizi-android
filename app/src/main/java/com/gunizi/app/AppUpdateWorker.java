package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Authenticated HTTPS GitHub Release update discovery.
 * Installs are ALWAYS triggered by the user via Android Package Installer.
 * Only published Releases (not arbitrary commits or Actions debug APKs) are accepted.
 */
public final class AppUpdateWorker extends Worker {
    static final String CHANNEL="gunizi_release_updates_visible_v2",WORK="gunizi-update-check";
    static final String API="https://api.github.com/repos/numandegirmenci/gunizi-android/releases/latest";
    static final String RELEASES="https://github.com/numandegirmenci/gunizi-android/releases";
    static final String PREF="gunizi_update";
    static final String APK="pending_release.apk";
    public AppUpdateWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    static File packageFile(Context c){
        File dir=new File(c.getFilesDir(),"updates");
        if(!dir.exists())dir.mkdirs();
        return new File(dir,APK);
    }
    static void schedule(Context c){
        // A debug side-by-side test app must never install a release APK for the other package.
        if(c.getPackageName().endsWith(".test")||c.getPackageName().endsWith(".ai2")||c.getPackageName().endsWith(".ai3"))return;
        if(!enabled(c)){
            WorkManager.getInstance(c).cancelUniqueWork(WORK);
            WorkManager.getInstance(c).cancelUniqueWork(WORK+"-initial");return;
        }
        Constraints constraints=new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(
            AppUpdateWorker.class,12,TimeUnit.HOURS)
            .setConstraints(constraints).addTag("gunizi-updates").build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(
            WORK,ExistingPeriodicWorkPolicy.KEEP,r);
        // A newly installed app should not need to wait for the first 12-hour interval.
        long checked=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getLong("lastChecked",0);
        if(System.currentTimeMillis()-checked>6L*60*60*1000){
            OneTimeWorkRequest initial=new OneTimeWorkRequest.Builder(AppUpdateWorker.class)
                .setConstraints(constraints).addTag("gunizi-update-initial").build();
            WorkManager.getInstance(c).enqueueUniqueWork(
                WORK+"-initial",ExistingWorkPolicy.KEEP,initial);
        }
    }
    static boolean enabled(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getBoolean("enabled",true);
    }
    static void enable(Context c,boolean value){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putBoolean("enabled",value).apply();
        schedule(c);
    }
    @NonNull @Override public Result doWork(){
        if(!enabled(getApplicationContext()))return Result.success();
        try{
            check(getApplicationContext(),true);
            return Result.success();
        }catch(IOException ex){
            return Result.retry();
        }catch(Exception ex){
            return Result.failure();
        }
    }
    interface Listener{void result(String status);}
    static void checkAsync(Context c,Listener listener){
        new Thread(()->{
            String result;
            try{result=check(c,false);}
            catch(Exception e){result="Güncelleme kontrol edilemedi: "+e.getMessage();}
            String message=result;
            new Handler(Looper.getMainLooper()).post(()->listener.result(message));
        },"GunIzi-ReleaseCheck").start();
    }
    private static JSONObject json(URL url)throws Exception{
        if(!"https".equalsIgnoreCase(url.getProtocol()))throw new IOException("HTTPS gerekli");
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        con.setRequestProperty("Accept","application/vnd.github+json");
        con.setRequestProperty("User-Agent","GunIzi-Android-updater");
        con.setConnectTimeout(12000);con.setReadTimeout(20000);
        try{
            int status=con.getResponseCode();
            if(status==404)return null;
            if(status!=200)throw new IOException("GitHub HTTP "+status);
            try(InputStream in=con.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1){
                    if(out.size()+n>512000)throw new IOException("Güncelleme bilgisi sınırı aşıldı");
                    out.write(b,0,n);
                }
                return new JSONObject(out.toString("UTF-8"));
            }
        }finally{con.disconnect();}
    }
    static String check(Context c,boolean background)throws Exception{
        JSONObject release=json(new URL(API));
        if(release==null){
            recordChecked(c);
            return "Henüz yayınlanmış GitHub sürümü yok.";
        }
        if(release.optBoolean("draft",false)||release.optBoolean("prerelease",false))
            return "Yalnızca kararlı yayınlar güncellenir.";
        String tag=release.optString("tag_name","");
        int remote=versionCode(release);
        int current=(int)c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();
        recordChecked(c);
        if(remote<=current){
            android.content.SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            p.edit().putString("remoteTag",tag)
                .putString("releaseNotes",release.optString("body","").substring(0,
                    Math.min(12000,release.optString("body","").length()))).apply();
            return "Günİzi güncel • "+tag;
        }
        JSONArray assets=release.optJSONArray("assets");
        JSONObject candidate=null;
        if(assets!=null)for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.optJSONObject(i);
            if(asset==null)continue;
            String name=asset.optString("name","").toLowerCase(Locale.ROOT);
            if(name.startsWith("gunizi-")&&name.endsWith(".apk")&&
                asset.optLong("size",0)>100000&&asset.optLong("size",0)<150L*1024*1024){
                candidate=asset;break;
            }
        }
        if(candidate==null)return "Yeni sürüm ("+tag+") var, fakat imzalı APK henüz yayınlanmamış.";
        String destination=candidate.optString("browser_download_url","");
        String sha=candidate.optString("digest",""); // modern GitHub Release asset API
        if(!destination.startsWith("https://github.com/numandegirmenci/gunizi-android/releases/download/"))
            throw new IOException("Güvenilir yayın adresi yok");
        android.content.SharedPreferences pref=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String notes=release.optString("body","");
        if(notes.length()>12000)notes=notes.substring(0,12000);
        pref.edit().putInt("remoteCode",remote).putString("remoteTag",tag)
            .putString("releaseUrl",release.optString("html_url",RELEASES))
            .putString("assetUrl",destination).putString("assetSha",sha)
            .putString("releaseNotes",notes)
            .putString("releaseSummary",shortNotes(notes)).apply();
        // Automatic transfer only on unmetered networks, and only if GitHub supplies a SHA-256 digest.
        boolean unmetered=!c.getSystemService(ConnectivityManager.class).isActiveNetworkMetered();
        if(unmetered&&sha.matches("sha256:[a-fA-F0-9]{64}")){
            try{download(c,destination,sha,remote);}catch(Exception ex){
                pref.edit().putString("lastDownloadError",ex.getMessage()).apply();
            }
        }
        boolean downloaded=packageFile(c).isFile()&&pref.getInt("downloadVersion",0)==remote;
        showUpdateNotice(c,tag,remote,downloaded,notes);
        return "Yeni sürüm bulundu: "+tag+(downloaded?" • APK indirildi":" • Yenilikleri görüntüle");
    }
    private static int versionCode(JSONObject release){
        String notes=release.optString("body","");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile(
            "(?im)^versionCode\\s*[:=]\\s*(\\d+)\\s*$").matcher(notes);
        if(m.find())try{return Integer.parseInt(m.group(1));}catch(Exception ignored){}
        String tag=release.optString("tag_name","");
        java.util.regex.Matcher v=java.util.regex.Pattern.compile(
            "^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?.*$").matcher(tag);
        if(!v.matches())return 0;
        try{return Integer.parseInt(v.group(1))*10000+
            Integer.parseInt(v.group(2))*100+Integer.parseInt(v.group(3)==null?"0":v.group(3));}
        catch(Exception ex){return 0;}
    }
    private static void download(Context c,String target,String expected,int version)throws Exception{
        File finalFile=packageFile(c);
        android.content.SharedPreferences prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(finalFile.length()>100000&&prefs.getInt("downloadVersion",0)==version)return;
        File tmp=new File(finalFile.getParentFile(),"pending.tmp");
        tmp.delete();
        URL url=new URL(target);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        con.setInstanceFollowRedirects(true);con.setConnectTimeout(18000);con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent","GunIzi Android Update");
        try{
            if(con.getResponseCode()!=200)throw new IOException("APK indirilemedi: HTTP "+con.getResponseCode());
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(InputStream in=con.getInputStream();FileOutputStream out=new FileOutputStream(tmp)){
                byte[] b=new byte[32768];int n;long total=0;
                while((n=in.read(b))!=-1){
                    total+=n;
                    if(total>150L*1024*1024)throw new IOException("APK boyutu sınırı aşıldı");
                    digest.update(b,0,n);out.write(b,0,n);
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException("İndirme kesildi");
                }
                out.getFD().sync();
            }
            StringBuilder result=new StringBuilder("sha256:");
            for(byte b:digest.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!result.toString().equalsIgnoreCase(expected))throw new IOException("APK özeti eşleşmedi");
            if(finalFile.exists()&&!finalFile.delete())throw new IOException("Eski APK silinemedi");
            if(!tmp.renameTo(finalFile))throw new IOException("APK taşınamadı");
            prefs.edit().putInt("downloadVersion",version).apply();
        }finally{
            con.disconnect();tmp.delete();
        }
    }
    private static void recordChecked(Context c){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putLong("lastChecked",System.currentTimeMillis()).apply();
    }

    static String shortNotes(String body){
        if(body==null||body.isEmpty())return "Sürüm notlarını görmek için dokun.";
        for(String line:body.split("\\R")){
            String t=line.trim();
            if(t.startsWith("- ")||t.startsWith("* ")){
                t=t.substring(2).replace("**","").trim();
                return t.length()>100?t.substring(0,100)+"…":t;
            }
        }
        return "Bu sürümdeki yenilikleri görüntüle.";
    }

    private static void showUpdateNotice(Context c,String version,int remote,
                                          boolean downloaded,String notes){
        NotificationManager mgr=c.getSystemService(NotificationManager.class);
        android.content.SharedPreferences prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        if(prefs.getInt("notifiedVersion",0)==remote)return;
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Günİzi • Yeni sürümler",NotificationManager.IMPORTANCE_DEFAULT);
        // Audible interruptions are disabled, but the notification stays visible.
        channel.setSound(null,null);
        channel.setShowBadge(true);
        channel.setDescription("Yeni sürüm yayınlandığında yenilikleri ve kurulumu gösterir");
        mgr.createNotificationChannel(channel);
        Intent open=new Intent(c,UpdateActivity.class);
        PendingIntent pi=PendingIntent.getActivity(c,211,open,
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        String summary=shortNotes(notes);
        Notification.BigTextStyle style=new Notification.BigTextStyle()
            .bigText("Bu sürümde neler yeni?\n"+summary+
                "\nKurulumdan önce ayrıntılı değişiklikleri incele.")
            .setBigContentTitle("Günİzi "+version+" güncellemesi");
        Notification notification=new Notification.Builder(c,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Günİzi "+version+" hazır")
            .setContentText(summary)
            .setStyle(style).setContentIntent(pi).setAutoCancel(true)
            .setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_RECOMMENDATION)
            .addAction(android.R.drawable.ic_menu_info_details,
                "Yenilikleri gör",pi)
            .build();
        mgr.notify(211,notification);
        prefs.edit().putInt("notifiedVersion",remote).apply();
    }
}
