package com.gunizi.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Explicit, resumable (HTTP Range) model downloads with pinned SHA256.
 * Network access is used ONLY for the requested weights; no audio or transcripts are uploaded.
 */
final class AiModelStore {
    static final String WHISPER="whisper", QWEN="qwen";
    private static final String W_FILE="ggml-small.bin",Q_FILE="Qwen3-1.7B-Q4_K_M.gguf";
    private static final String W_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin";
    private static final String Q_URL="https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf";
    private static final String W_SHA="1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b";
    private static final String Q_SHA="d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5";
    private static final long W_BYTES=487601967L,Q_BYTES=1280000000L;
    interface Status {void onStatus(String text,boolean complete);}
    private static File base(Context c){return new File(c.getNoBackupFilesDir(),"ai_models");}
    static File whisper(Context c){return new File(base(c),W_FILE);}
    static File qwen(Context c){return new File(base(c),Q_FILE);}
    static boolean ready(Context c,String which){
        File f=WHISPER.equals(which)?whisper(c):qwen(c);
        File marker=new File(f.getParentFile(),f.getName()+".verified");
        return f.isFile()&&marker.isFile()&&f.length()>(WHISPER.equals(which)?400L:1000L)*1024*1024;
    }
    static void fetch(Context c,String which,Status listener){
        new Thread(()->{
            final boolean w=WHISPER.equals(which);
            File target=w?whisper(c):qwen(c);
            String expected=w?W_SHA:Q_SHA;
            String url=w?W_URL:Q_URL;
            long limit=w?600L*1024*1024:1600L*1024*1024;
            File part=new File(target.getAbsolutePath()+".part");
            try{
                if(ready(c,which)){status(listener,"Model daha önce kuruldu.",true);return;}
                File dir=base(c);
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Model dizini oluşturulamadı");
                long already=part.isFile()?part.length():0;
                long remaining=Math.max(0,(w?W_BYTES:Q_BYTES)-already);
                long free=new StatFs(dir.getAbsolutePath()).getAvailableBytes();
                if(free<remaining+250L*1024*1024)
                    throw new IOException("Yeterli boş alan yok. En az "+
                        ((remaining+250L*1024*1024)/(1024*1024))+" MB boş alan gerekli.");
                status(listener,w?"Whisper Türkçe modeli indiriliyor…":"Qwen3 yerel özet modeli indiriliyor…",false);
                HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(25000);conn.setReadTimeout(40000);
                conn.setRequestProperty("User-Agent","GunIzi-Android/0.3");
                if(already>0)conn.setRequestProperty("Range","bytes="+already+"-");
                int response=conn.getResponseCode();
                if(response!=206&&response!=200)throw new IOException("Sunucu HTTP "+response);
                boolean append=response==206&&already>0;
                if(!append){already=0;part.delete();}
                long length=conn.getContentLengthLong();
                long total=length>0?length+already:(w?W_BYTES:Q_BYTES);
                try(InputStream in=new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out=new FileOutputStream(part,append)){
                    byte[] buf=new byte[64*1024];int n,old=-1;long size=already;
                    while((n=in.read(buf))!=-1){
                        size+=n;
                        if(size>limit)throw new IOException("Beklenmeyen model boyutu");
                        out.write(buf,0,n);
                        int pct=(int)Math.min(99,size*100/Math.max(1,total));
                        if(pct/2!=old/2){old=pct;status(listener,(w?"Whisper":"Qwen3")+" %"+pct+" • "+
                            String.format(Locale.ROOT,"%.0f MB",size/1048576.0),false);}
                    }
                    out.getFD().sync();
                }finally{conn.disconnect();}
                status(listener,"Model bütünlüğü denetleniyor…",false);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=new BufferedInputStream(new FileInputStream(part))){
                    byte[] buf=new byte[128*1024];int n;
                    while((n=in.read(buf))!=-1)digest.update(buf,0,n);
                }
                StringBuilder hex=new StringBuilder();
                for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
                if(!expected.equals(hex.toString()))throw new IOException("Model SHA-256 doğrulaması başarısız");
                if(!part.renameTo(target))throw new IOException("Model yerleştirilemedi");
                File marker=new File(target.getParentFile(),target.getName()+".verified");
                try(FileOutputStream out=new FileOutputStream(marker)){
                    out.write(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                status(listener,(w?"Whisper":"Qwen3")+" modeli kullanıma hazır ✓",true);
            }catch(Exception e){status(listener,"Model hatası: "+e.getMessage(),false);}
        },"GunIzi-Download-"+which).start();
    }
    private static void status(Status callback,String txt,boolean done){
        new Handler(Looper.getMainLooper()).post(()->callback.onStatus(txt,done));
    }
}
