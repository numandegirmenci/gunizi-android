package com.gunizi.app;
import android.media.MediaRecorder;
import android.media.MediaMuxer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;

/** Immutable profile: never mix codecs or sample rates in a rolling session. */
final class RecordingOptions {
    static final String[] FORMATS={"OGG / Opus","M4A / AAC"};
    static final String[] QUALITIES={"Ekonomik","Dengeli","Yüksek"};
    static final int DATE=1, DAY=2, TIME=4, SECONDS=8, MODE=16;
    static final int DEFAULT_FIELDS=DATE|TIME|SECONDS|MODE;
    final int format,quality;
    RecordingOptions(int format,int quality){
        this.format=format==1?1:0;
        this.quality=quality>=0&&quality<=2?quality:0;
    }
    String extension(){return format==1?".m4a":".ogg";}
    String mime(){return format==1?"audio/mp4":"audio/ogg";}
    int container(){return format==1?MediaRecorder.OutputFormat.MPEG_4:MediaRecorder.OutputFormat.OGG;}
    int encoder(){return format==1?MediaRecorder.AudioEncoder.AAC:MediaRecorder.AudioEncoder.OPUS;}
    int muxer(){return format==1?MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4:MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG;}
    int bitrate(){return (format==1?new int[]{32,64,128}:new int[]{16,32,64})[quality]*1000;}
    int sampleRate(){return new int[]{16000,24000,48000}[quality];}
    String description(){
        return String.format(Locale.forLanguageTag("tr-TR"),"%s · %d kbps · %d kHz · Mono · yaklaşık %.1f MB/saat",
            FORMATS[format],bitrate()/1000,sampleRate()/1000,bitrate()*3600.0/8000000.0);
    }
    static String mimeForName(String name){return name.toLowerCase(Locale.ROOT).endsWith(".m4a")?"audio/mp4":"audio/ogg";}
    static String extensionForName(String name){return name.toLowerCase(Locale.ROOT).endsWith(".m4a")?".m4a":".ogg";}
    static String safePrefix(String value){
        String s=value==null?"":value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        if(s.length()>60)s=s.substring(0,60).trim();
        return s.isEmpty()?"Günİzi":s;
    }
    static String name(String prefix,int flags,String mode,LocalDateTime time,String extension){
        ArrayList<String> parts=new ArrayList<>();
        parts.add(safePrefix(prefix));
        if((flags&MODE)!=0)parts.add(mode);
        if((flags&DATE)!=0)parts.add(time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if((flags&DAY)!=0)parts.add(time.format(DateTimeFormatter.ofPattern("EEEE",Locale.forLanguageTag("tr-TR"))));
        if((flags&TIME)!=0)parts.add(time.format(DateTimeFormatter.ofPattern((flags&SECONDS)!=0?"HH-mm-ss":"HH-mm")));
        else if((flags&SECONDS)!=0)parts.add(time.format(DateTimeFormatter.ofPattern("ss"))+"sn");
        return String.join("_",parts)+extension;
    }
}
