package com.gunizi.app;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Inspect audio before any expensive native model load. No audio uploaded. */
final class AiAudioPreflight {
    static final class Info {
        final String name,codec,id;
        final long durationMs,bytes;
        final int sampleRate,channels;
        Info(String name,String codec,String id,long durationMs,long bytes,int rate,int channels){
            this.name=name;this.codec=codec;this.id=id;this.durationMs=durationMs;
            this.bytes=bytes;this.sampleRate=rate;this.channels=channels;
        }
        String description(){
            long sec=Math.max(0,durationMs/1000);
            String elapsed=sec>=3600?
                String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60):
                String.format(java.util.Locale.ROOT,"%02d:%02d",sec/60,sec%60);
            return name+" • "+elapsed+" • "+codec;
        }
    }
    static Info inspect(Context c,Uri uri)throws IOException{
        if(uri==null)throw new IOException("Ses kaydı seçilmedi");
        String name="Ses kaydı";long size=-1;
        try(Cursor cur=c.getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cur!=null&&cur.moveToFirst()){
                if(!cur.isNull(0))name=cur.getString(0);
                if(!cur.isNull(1))size=cur.getLong(1);
            }
        }catch(Exception ignored){}
        if(size==0)throw new IOException("Seçilen kayıt boş (0 bayt)");
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(c,uri,null);
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat fmt=extractor.getTrackFormat(i);
                String mime=fmt.containsKey(MediaFormat.KEY_MIME)?
                    fmt.getString(MediaFormat.KEY_MIME):"";
                if(mime==null||!mime.startsWith("audio/"))continue;
                int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?
                    fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):0;
                int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?
                    fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):0;
                long duration=fmt.containsKey(MediaFormat.KEY_DURATION)?
                    fmt.getLong(MediaFormat.KEY_DURATION)/1000:0L;
                if(rate<=0||channels<=0)throw new IOException("Ses örnekleme bilgileri okunamadı");
                if(duration<0)duration=0;
                // URI+size+duration is a cache hint, not a cryptographic content identity.
                String id=hash(uri.toString()+"|"+size+"|"+duration+"|"+mime);
                return new Info(name,mime,id,duration,size,rate,channels);
            }
            throw new IOException("Kayıtta okunabilir ses parçası bulunamadı");
        }catch(IOException e){throw e;}
        catch(Exception ex){throw new IOException("Ses dosyası açılamadı: "+
            ex.getClass().getSimpleName(),ex);}
        finally{extractor.release();}
    }
    private static String hash(String src){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(
                src.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<16;i++)sb.append(String.format(java.util.Locale.ROOT,"%02x",b[i]));
            return sb.toString();
        }catch(Exception e){return Integer.toHexString(src.hashCode());}
    }
}
