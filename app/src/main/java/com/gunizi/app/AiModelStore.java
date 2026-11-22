package com.gunizi.app;

import android.content.Context;
import android.os.StatFs;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * On-device weights for pinned CallVault native whisper.cpp / llama.cpp backends.
 * Hashes and exact sizes for CallVault weights come from its pinned model catalogue.
 * Models are fetched on explicit user request only; no recordings are uploaded.
 */
final class AiModelStore {
    static final String WHISPER="whisper",WHISPER_BASE="whisper_base",
        WHISPER_TURBO="whisper_turbo",WHISPER_Q5="whisper_small_q5",
        WHISPER_TURBO_Q8="whisper_turbo_q8";
    static final String QWEN="qwen",QWEN4="qwen4",GEMMA="gemma4";
    static final class Spec{
        final String id,file,url,sha,label;
        final long bytes,minimum;
        final boolean exact,speech;
        Spec(String id,String file,String url,String sha,long bytes,long minimum,
             String label,boolean exact,boolean speech){
            this.id=id;this.file=file;this.url=url;this.sha=sha;
            this.bytes=bytes;this.minimum=minimum;this.label=label;
            this.exact=exact;this.speech=speech;
        }
    }
    private static final String HF="https://huggingface.co/";
    private static final Spec[] CATALOG={
        new Spec(WHISPER_Q5,"ggml-small-q5_1.bin",
            HF+"ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin",
            "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
            190085487L,185000000L,"Whisper Small Q5 • Hızlı",true,true),
        new Spec(WHISPER_TURBO_Q8,"ggml-large-v3-turbo-q8_0.bin",
            HF+"ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q8_0.bin",
            "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1",
            874188075L,870000000L,"Whisper Turbo Q8 • Yüksek kalite",true,true),
        new Spec(WHISPER_TURBO,"ggml-large-v3-turbo-q5_0.bin",
            HF+"ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
            574041195L,570000000L,"Whisper Turbo Q5 • Eski seçenek",true,true),
        new Spec(WHISPER_BASE,"ggml-base.bin",
            HF+"ggerganov/whisper.cpp/resolve/main/ggml-base.bin",
            "60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe",
            147951465L,145000000L,"Whisper Base • Küçük model",true,true),
        new Spec(WHISPER,"ggml-small.bin",
            HF+"ggerganov/whisper.cpp/resolve/main/ggml-small.bin",
            "1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b",
            487601967L,400000000L,"Whisper Small • Eski seçenek",false,true),
        new Spec(GEMMA,"gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf",
            HF+"unsloth/gemma-4-E2B-it-qat-GGUF/resolve/main/gemma-4-E2B-it-qat-UD-Q4_K_XL.gguf",
            "e531007218dfab990486a5de7676a6932d6ea8dea233d1f698d7c21cf8a16889",
            2620370976L,2600000000L,"Gemma 4 E2B QAT • CallVault",true,false),
        new Spec(QWEN,"Qwen3-1.7B-Q4_K_M.gguf",
            HF+"ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf",
            "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
            1280000000L,1000000000L,"Qwen3 1.7B • Alternatif",false,false),
        new Spec(QWEN4,"Qwen3-4B-Q4_K_M.gguf",
            HF+"ggml-org/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf",
            "ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328",
            2500000000L,2100000000L,"Qwen3 4B • Alternatif",false,false)
    };
    private static Spec spec(String id){
        for(Spec model:CATALOG)if(model.id.equals(id))return model;
        throw new IllegalArgumentException("Bilinmeyen yapay zeka modeli: "+id);
    }
    static boolean isSpeech(String id){return spec(id).speech;}
    static String label(String id){return spec(id).label;}
    private static File dir(Context c){return new File(c.getNoBackupFilesDir(),"ai_models");}
    static File file(Context c,String id){return new File(dir(c),spec(id).file);}
    static File speechFile(Context c,String id){
        if(!isSpeech(id))throw new IllegalArgumentException("Konuşma modeli değil");
        return file(c,id);
    }
    static File whisper(Context c){return file(c,WHISPER);}
    static File whisperBase(Context c){return file(c,WHISPER_BASE);}
    static File whisperTurbo(Context c){return file(c,WHISPER_TURBO);}
    static File whisperQ5(Context c){return file(c,WHISPER_Q5);}
    static File turboQ8(Context c){return file(c,WHISPER_TURBO_Q8);}
    static File qwen(Context c){return file(c,QWEN);}
    static File qwen4(Context c){return file(c,QWEN4);}
    static File gemma(Context c){return file(c,GEMMA);}
    static File selectedQwen(Context c){return file(c,new Prefs(c).aiModel());}
    interface Status {void onStatus(String message,boolean done);}
    static boolean ready(Context c,String id){
        Spec m=spec(id);
        File target=file(c,id);
        if(!target.isFile()||target.length()<m.minimum)return false;
        if(m.exact&&target.length()!=m.bytes)return false;
        File marker=new File(target.getPath()+".verified");
        if(!marker.isFile())return false;
        try{
            byte[] bytes=new byte[80];
            int n;
            try(FileInputStream in=new FileInputStream(marker)){n=in.read(bytes);}
            return n==64&&m.sha.equals(new String(bytes,0,n,java.nio.charset.StandardCharsets.US_ASCII));
        }catch(IOException e){return false;}
    }
    static void downloadBlocking(Context c,String id,Status callback)throws Exception{
        Spec m=spec(id);
        if(ready(c,id)){callback.onStatus("Model hazır ✓",true);return;}
        File folder=dir(c);
        if(!folder.exists()&&!folder.mkdirs())throw new IOException("Model klasörü oluşturulamadı");
        File target=file(c,id),part=new File(target.getPath()+".part");
        long already=part.isFile()?part.length():0;
        long max=m.exact?m.bytes:(long)(m.bytes*1.25);
        if(already>max){if(!part.delete())throw new IOException("Eksik model sıfırlanamadı");already=0;}
        long free=new StatFs(folder.getAbsolutePath()).getAvailableBytes();
        long remaining=Math.max(0,m.bytes-already);
        if(free<remaining+250L*1024*1024)
            throw new IOException("Yeterli boş alan yok. Gerekli: "+
                (remaining/1048576+250)+" MB.");
        HttpURLConnection connection=(HttpURLConnection)new URL(m.url).openConnection();
        connection.setConnectTimeout(25000);
        connection.setReadTimeout(40000);
        connection.setRequestProperty("User-Agent","GunIzi-AI-Native/0.11");
        if(already>0)connection.setRequestProperty("Range","bytes="+already+"-");
        try{
            int status=connection.getResponseCode();
            if(status!=200&&status!=206)
                throw new IOException("Model sunucusu HTTP "+status);
            boolean append=already>0&&status==206;
            if(append){
                String range=connection.getHeaderField("Content-Range");
                if(range==null||!range.startsWith("bytes "+already+"-"))
                    throw new IOException("Sunucu indirmenin kaldığı noktayı doğrulamadı");
            }else{already=0;if(part.exists()&&!part.delete())
                throw new IOException("Önceki kısmi model silinemedi");}
            long content=connection.getContentLengthLong();
            long expected=content>0?content+already:m.bytes;
            callback.onStatus(m.label+" indiriliyor",false);
            try(InputStream in=new BufferedInputStream(connection.getInputStream());
                FileOutputStream out=new FileOutputStream(part,append)){
                byte[] buffer=new byte[128*1024];int n,previous=-1;long size=already;
                while((n=in.read(buffer))!=-1){
                    if(Thread.currentThread().isInterrupted)
                        throw new InterruptedException("İndirme durduruldu");
                    size+=n;
                    if(size>max)throw new IOException("Model beklenenden büyük");
                    out.write(buffer,0,n);
                    int pct=(int)Math.min(99,size*100/Math.max(expected,1));
                    if(pct/2!=previous/2){previous=pct;
                        callback.onStatus(m.label+" %"+pct+" • "+
                            String.format(Locale.ROOT,"%.0f MB",size/1048576.0),false);}
                }
                out.getFD().sync();
            }
        }finally{connection.disconnect();}
        if(m.exact&&part.length()!=m.bytes)throw new IOException("İndirilen model boyutu yanlış");
        callback.onStatus("SHA-256 doğrulanıyor: "+m.label,false);
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(part))){
            byte[] b=new byte[128*1024];int n;
            while((n=in.read(b))!=-1)digest.update(b,0,n);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        if(!m.sha.equals(hex.toString()))
            throw new IOException("SHA-256 uyuşmadı. Eksik/yanlış model kullanılamaz.");
        if(target.exists()&&!target.delete())throw new IOException("Eski model değiştirilemedi");
        if(!part.renameTo(target))throw new IOException("Doğrulanan model taşınamadı");
        try(FileOutputStream marker=new FileOutputStream(new File(target.getPath()+".verified"))){
            marker.write(m.sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            marker.getFD().sync();
        }
        callback.onStatus(m.label+" kullanıma hazır ✓",true);
    }
}
