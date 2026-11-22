package com.gunizi.app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Temporary rolling Ogg/Opus segments. Not a RAM-only buffer.
 * Six hours of 16 kbps compressed audio is about 43 MB, vs ~659 MB raw PCM.
 * All segments live in private cache and are removed when listening stops.
 */
final class HistoryAudioStore {
    static final int MAX_MINUTES=360;
    static final long MAX_MS=MAX_MINUTES*60000L;
    static final long SEGMENT_MS=30000L;
    static final class Segment {
        final File file;
        final long start,end;
        int pins;
        Segment(File file,long start,long end){this.file=file;this.start=start;this.end=end;}
    }
    private final File dir;
    private final ArrayList<Segment> segments=new ArrayList<>();
    HistoryAudioStore(Context context){
        dir=new File(context.getCacheDir(),"gunizi_rolling_history");
        if(!dir.exists())dir.mkdirs();
        File[] old=dir.listFiles();
        if(old!=null)for(File f:old)if(f.getName().startsWith("rolling_"))f.delete();
    }
    synchronized void add(File f,long start,long end){
        if(f==null||!f.isFile()||f.length()<64){if(f!=null)f.delete();return;}
        segments.add(new Segment(f,start,end));
        prune(SystemClock.elapsedRealtime());
    }
    synchronized long secondsAvailable(long liveStart){
        long now=SystemClock.elapsedRealtime();
        long earliest=liveStart>0?liveStart:now;
        for(Segment s:segments)earliest=Math.min(earliest,s.start);
        // Sum sample window duration; skips at segment boundaries are small but possible.
        return Math.max(0,Math.min(MAX_MS,now-earliest))/1000;
    }
    synchronized File nextFile(){
        if(!dir.exists()&&!dir.mkdirs())throw new IllegalStateException("Geçici ses alanı açılamadı");
        return new File(dir,"rolling_"+SystemClock.elapsedRealtime()+"_"+System.nanoTime()+".ogg");
    }
    synchronized List<Segment> pinSince(long cutoff){
        ArrayList<Segment> chosen=new ArrayList<>();
        for(Segment s:segments)if(s.end>cutoff&&s.start<=SystemClock.elapsedRealtime()){
            s.pins++;chosen.add(s);
        }
        return chosen;
    }
    synchronized void release(List<Segment> pinned){
        for(Segment s:pinned)s.pins=Math.max(0,s.pins-1);
        prune(SystemClock.elapsedRealtime());
    }
    synchronized void prune(long now){
        long earliest=now-MAX_MS-SEGMENT_MS;
        for(int i=segments.size()-1;i>=0;i--){
            Segment s=segments.get(i);
            if(s.end<earliest&&s.pins==0){s.file.delete();segments.remove(i);}
        }
    }
    synchronized void discard(){
        for(int i=segments.size()-1;i>=0;i--){
            Segment s=segments.get(i);
            if(s.pins==0){s.file.delete();segments.remove(i);}
        }
    }
    /** Re-mux selected Opus samples into a single playable OGG file, no recompression. */
    static File merge(Context context,List<Segment> source,long cutoff,long endTime)throws IOException{
        if(source==null||source.isEmpty())throw new IOException("Geçmiş ses birikmedi");
        File result=File.createTempFile("gunizi_history_export_",".ogg",context.getCacheDir());
        MediaMuxer muxer=null;boolean started=false;boolean success=false;
        long lastPts=-1;int samples=0,trackOut=-1;
        try{
            muxer=new MediaMuxer(result.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG);
            ByteBuffer buffer=ByteBuffer.allocateDirect(512*1024);
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            for(Segment s:source){
                MediaExtractor extractor=new MediaExtractor();
                try{
                    extractor.setDataSource(s.file.getAbsolutePath());
                    int track=-1;
                    for(int i=0;i<extractor.getTrackCount();i++){
                        MediaFormat fmt=extractor.getTrackFormat(i);
                        String mime=fmt.getString(MediaFormat.KEY_MIME);
                        if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
                    }
                    if(track<0)continue;
                    extractor.selectTrack(track);
                    if(!started){
                        trackOut=muxer.addTrack(extractor.getTrackFormat(track));
                        muxer.start();started=true;
                    }
                    while(true){
                        buffer.clear();
                        int size=extractor.readSampleData(buffer,0);
                        if(size<0)break;
                        long sample=extractor.getSampleTime();
                        if(sample<0){extractor.advance();continue;}
                        long sourceTime=s.start*1000L+sample;
                        if(sourceTime>=endTime*1000L)break;
                        if(sourceTime>=cutoff*1000L){
                            long relative=Math.max(0,sourceTime-cutoff*1000L);
                            if(relative<=lastPts)relative=lastPts+1;
                            info.set(0,size,relative,extractor.getSampleFlags()&
                                MediaCodec.BUFFER_FLAG_KEY_FRAME);
                            buffer.position(0);buffer.limit(size);
                            muxer.writeSampleData(trackOut,buffer,info);
                            lastPts=relative;samples++;
                        }
                        extractor.advance();
                    }
                }finally{extractor.release();}
            }
            if(samples==0)throw new IOException("Seçilen aralıkta ses verisi yok");
            success=true;return result;
        }finally{
            if(muxer!=null){
                if(started)try{muxer.stop();}catch(Exception e){success=false;}
                muxer.release();
            }
            if(!success)result.delete();
        }
    }
}
