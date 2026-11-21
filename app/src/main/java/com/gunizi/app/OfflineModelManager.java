package com.gunizi.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Explicit one-time HTTPS model download. Recorded audio NEVER goes to the network.
 * Official Turkish Vosk model, Apache-2.0, about 35 MB compressed.
 */
final class OfflineModelManager {
    private static final String NAME="vosk-model-small-tr-0.3";
    private static final String SOURCE="https://alphacephei.com/vosk/models/"+NAME+".zip";
    interface Listener { void status(String message); }
    static File root(Context c){return new File(c.getNoBackupFilesDir(),"models");}
    static File modelPath(Context c){return new File(root(c),NAME);}
    static boolean ready(Context c){
        File m=modelPath(c);
        return new File(m,"am/final.mdl").isFile() && new File(m,"conf/model.conf").isFile();
    }
    private static void notify(Listener l,String s){
        new Handler(Looper.getMainLooper()).post(()->l.status(s));
    }
    static void install(Context context,Listener listener){
        new Thread(()->{
            File tmp=null;File staging=new File(root(context),"unpacking");
            try{
                if(ready(context)){notify(listener,"Türkçe model hazır.");return;}
                File base=root(context);
                if(!base.exists()&&!base.mkdirs())throw new IOException("Model klasörü oluşturulamadı");
                deleteTree(staging);if(!staging.mkdirs())throw new IOException("Model klasörü açılamadı");
                tmp=new File(base,"turkish-model-download.tmp");
                notify(listener,"Türkçe ses modeli indiriliyor (yaklaşık 35 MB)…");
                HttpURLConnection conn=(HttpURLConnection)new URL(SOURCE).openConnection();
                conn.setConnectTimeout(20000);conn.setReadTimeout(40000);
                conn.setRequestProperty("User-Agent","GunIzi Android/0.2");
                if(conn.getResponseCode()!=200)throw new IOException("İndirme HTTP "+conn.getResponseCode());
                long total=conn.getContentLengthLong();
                if(total>100L*1024*1024)throw new IOException("Model dosyası beklenenden büyük");
                try(InputStream input=conn.getInputStream();OutputStream out=new FileOutputStream(tmp)){
                    byte[] buf=new byte[32768];int n;long read=0;int previous=-1;
                    while((n=input.read(buf))!=-1){
                        if(Thread.currentThread().isInterrupted())throw new IOException("İndirme kesildi");
                        read+=n;
                        if(read>100L*1024*1024)throw new IOException("Model indirme boyutu aşıldı");
                        out.write(buf,0,n);
                        if(total>0){
                            int percentage=(int)(read*100/total);
                            if(percentage/10!=previous/10){
                                previous=percentage;
                                notify(listener,"Türkçe model indiriliyor: %"+percentage);
                            }
                        }
                    }
                }finally{conn.disconnect();}
                notify(listener,"Model kuruluyor…");
                long extracted=0;int files=0;
                try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(new FileInputStream(tmp)))){
                    ZipEntry entry;
                    while((entry=z.getNextEntry())!=null){
                        if(++files>3000)throw new IOException("Çok fazla model dosyası");
                        File destination=new File(staging,entry.getName());
                        String safe=staging.getCanonicalPath()+File.separator;
                        if(!destination.getCanonicalPath().startsWith(safe))
                            throw new IOException("Geçersiz model arşivi");
                        if(entry.isDirectory()){destination.mkdirs();continue;}
                        File parent=destination.getParentFile();
                        if(!parent.exists()&&!parent.mkdirs())throw new IOException("Klasör oluşturulamadı");
                        try(FileOutputStream out=new FileOutputStream(destination)){
                            byte[] buffer=new byte[32768];int n;
                            while((n=z.read(buffer))!=-1){
                                extracted+=n;
                                if(extracted>500L*1024*1024)throw new IOException("Model sınırı aşıldı");
                                out.write(buffer,0,n);
                            }
                        }
                        z.closeEntry();
                    }
                }
                File unpacked=new File(staging,NAME);
                if(!new File(unpacked,"am/final.mdl").isFile())
                    throw new IOException("Model dosyaları eksik");
                File destination=modelPath(context);
                deleteTree(destination);
                if(!unpacked.renameTo(destination))
                    throw new IOException("Modeli taşırken hata oluştu");
                notify(listener,"Türkçe çevrimdışı model kuruldu ✓");
            }catch(Exception e){
                notify(listener,"Model kurulamadı: "+e.getMessage());
            }finally{
                if(tmp!=null)tmp.delete();
                deleteTree(staging);
            }
        },"GunIzi-ModelInstaller").start();
    }
    private static void deleteTree(File f){
        if(!f.exists())return;
        if(f.isDirectory()){
            File[] list=f.listFiles();
            if(list!=null)for(File x:list)deleteTree(x);
        }
        f.delete();
    }
}
