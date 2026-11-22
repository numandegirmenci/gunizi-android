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
    static final String WHISPER="whisper", WHISPER_BASE="whisper_base",
        WHISPER_TURBO="whisper_turbo",
        QWEN="qwen", QWEN4="qwen4";
    private static final String TURBO_FILE="ggml-large-v3-turbo-q5_0.bin";
    private static final String BASE_FILE="ggml-base.bin";
    private static final String W_FILE="ggml-small.bin",Q_FILE="Qwen3-1.7B-Q4_K_M.gguf",
        Q4_FILE="Qwen3-4B-Q4_K_M.gguf";
    private static final String Q4_URL="https://huggingface.co/ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf";
    private static final String TURBO_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin";
    private static final String BASE_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin";
    private static final String BASE_SHA="60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe";
    private static final long BASE_BYTES=147951465L;
    private static final String W_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.bin";
    private static final String Q_URL="https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf";
    private static final String Q4_SHA="ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328";
    private static final String TURBO_SHA="394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2";
    private static final String W_SHA="1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b";
    private static final String Q_SHA="d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5";
    private static final long W_BYTES=487601967L,TURBO_BYTES=574000000L,
        Q_BYTES=1280000000L,Q4_BYTES=2500000000L;
    interface Status {void onStatus(String text,boolean complete);}
    private static File base(Context c){return new File(c.getNoBackupFilesDir(),"ai_models");}
    static File whisper(Context c){return new File(base(c),W_FILE);}
    static File whisperBase(Context c){return new File(base(c),BASE_FILE);}
    static File whisperTurbo(Context c){return new File(base(c),TURBO_FILE);}
    static File speechFile(Context c,String model){
        return WHISPER_TURBO.equals(model)?whisperTurbo(c):
            WHISPER_BASE.equals(model)?whisperBase(c):whisper(c);
    }
    static File qwen(Context c){return new File(base(c),Q_FILE);}
    static File qwen4(Context c){return new File(base(c),Q4_FILE);}
    static File selectedQwen(Context c){
        return QWEN4.equals(new Prefs(c).aiModel())?qwen4(c):qwen(c);
    }
    static boolean ready(Context c,String which){
        File f=WHISPER_TURBO.equals(which)?whisperTurbo(c):
            WHISPER_BASE.equals(which)?whisperBase(c):
            WHISPER.equals(which)?whisper(c):QWEN4.equals(which)?qwen4(c):qwen(c);
        File marker=new File(f.getParentFile(),f.getName()+".verified");
        long min=WHISPER_TURBO.equals(which)?510L:
            WHISPER_BASE.equals(which)?130L:
            WHISPER.equals(which)?400L:QWEN4.equals(which)?2100L:1000L;
        return f.isFile()&&marker.isFile()&&f.length()>min*1024*1024;
    }
    static void downloadBlocking(Context c,String which,Status listener)throws Exception {
            final boolean w=WHISPER.equals(which)||WHISPER_BASE.equals(which)||
                WHISPER_TURBO.equals(which);
            final boolean turbo=WHISPER_TURBO.equals(which);
            final boolean compact=WHISPER_BASE.equals(which);
            File target=turbo?whisperTurbo(c):compact?whisperBase(c):
                w?whisper(c):QWEN4.equals(which)?qwen4(c):qwen(c);
            String expected=turbo?TURBO_SHA:compact?BASE_SHA:
                w?W_SHA:QWEN4.equals(which)?Q4_SHA:Q_SHA;
            String url=turbo?TURBO_URL:compact?BASE_URL:
                w?W_URL:QWEN4.equals(which)?Q4_URL:Q_URL;
            if(!WHISPER.equals(which)&&!WHISPER_BASE.equals(which)&&
               !WHISPER_TURBO.equals(which)&&
               !QWEN.equals(which)&&!QWEN4.equals(which))
                throw new IllegalArgumentException("Geçersiz model");
            long limit=turbo?750L*1024*1024:compact?190L*1024*1024:
                w?600L*1024*1024:QWEN4.equals(which)?3100L*1024*1024:1600L*1024*1024;
            File part=new File(target.getAbsolutePath()+".part");
            try{
                if(ready(c,which)){status(listener,"Model daha önce kuruldu.",true);return;}
                File dir=base(c);
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Model dizini oluşturulamadı");
                long already=part.isFile()?part.length():0;
                long remaining=Math.max(0,(turbo?TURBO_BYTES:compact?BASE_BYTES:
                    w?W_BYTES:QWEN4.equals(which)?Q4_BYTES:Q_BYTES)-already);
                long free=new StatFs(dir.getAbsolutePath()).getAvailableBytes();
                if(free<remaining+250L*1024*1024)
                    throw new IOException("Yeterli boş alan yok. En az "+
                        ((remaining+250L*1024*1024)/(1024*1024))+" MB boş alan gerekli.");
                status(listener,w?(turbo?"Whisper Turbo modeli indiriliyor…":
                    compact?"Whisper base indiriliyor…":"Whisper small indiriliyor…"):
                    "Qwen3 yerel özet modeli indiriliyor…",false);
                HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
                conn.setConnectTimeout(25000);conn.setReadTimeout(40000);
                conn.setRequestProperty("User-Agent","GunIzi-Android/0.3");
                if(already>0)conn.setRequestProperty("Range","bytes="+already+"-");
                int response=conn.getResponseCode();
                if(response!=206&&response!=200)throw new IOException("Sunucu HTTP "+response);
                boolean append=response==206&&already>0;
                if(!append){already=0;part.delete();}
                long length=conn.getContentLengthLong();
                long total=length>0?length+already:(turbo?TURBO_BYTES:compact?BASE_BYTES:
                    w?W_BYTES:QWEN4.equals(which)?Q4_BYTES:Q_BYTES);
                try(InputStream in=new BufferedInputStream(conn.getInputStream());
                    FileOutputStream out=new FileOutputStream(part,append)){
                    byte[] buf=new byte[64*1024];int n,old=-1;long size=already;
                    while((n=in.read(buf))!=-1){
                        if(Thread.currentThread().isInterrupted())
                            throw new InterruptedException("İndirme durduruldu");
                        size+=n;
                        if(size>limit)throw new IOException("Beklenmeyen model boyutu");
                        out.write(buf,0,n);
                        int pct=(int)Math.min(99,size*100/Math.max(1,total));
                        if(pct/2!=old/2){old=pct;status(listener,(turbo?"Whisper Turbo":compact?"Whisper base":w?"Whisper":"Qwen3")+" %"+pct+" • "+
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
                if(target.exists()&&!target.delete())
                    throw new IOException("Önceki model dosyası silinemedi");
                if(!part.renameTo(target))throw new IOException("Model yerleştirilemedi");
                File marker=new File(target.getParentFile(),target.getName()+".verified");
                try(FileOutputStream out=new FileOutputStream(marker)){
                    out.write(expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                status(listener,(w?"Whisper":"Qwen3")+" modeli kullanıma hazır ✓",true);
            }catch(Exception e){throw e;}
    }
    private static void status(Status callback,String txt,boolean done){
        callback.onStatus(txt,done);
    }
}
