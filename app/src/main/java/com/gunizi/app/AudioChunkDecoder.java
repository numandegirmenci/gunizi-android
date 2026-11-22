package com.gunizi.app;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/** Decode an audio document in a streaming manner; emit 5-minute mono PCM WAV clips. */
final class AudioChunkDecoder {
    interface Consumer {void onChunk(File wav,long offsetMs,int percent)throws Exception;}
    private static final int RATE=16000,CHUNK_SECONDS=5*60,MAX=CHUNK_SECONDS*RATE*2;
    private final Context context;
    private final Consumer consumer;
    private final ByteArrayOutputStream pcm=new ByteArrayOutputStream(MAX);
    private int channels=1,rate=16000;
    private long sampleCount=0,phase=0,sum=0;
    private int averageCount=0;
    private long currentStartMs=0;
    private int sequence=0;
    private int progress=0;
    private AudioChunkDecoder(Context c,Consumer x){context=c;consumer=x;}
    static void decode(Context c,Uri uri,Consumer callback)throws Exception{
        new AudioChunkDecoder(c,callback).process(uri);
    }
    private void process(Uri uri)throws Exception{
        MediaExtractor ex=new MediaExtractor();MediaCodec codec=null;
        try{
            ex.setDataSource(context,uri,null);
            int track=-1;
            for(int i=0;i<ex.getTrackCount();i++){
                String mime=ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
            }
            if(track<0)throw new IOException("Ses akışı bulunamadı");
            ex.selectTrack(track);
            MediaFormat input=ex.getTrackFormat(track);
            String mime=input.getString(MediaFormat.KEY_MIME);
            rate=input.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channels=Math.max(1,input.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            long duration=input.containsKey(MediaFormat.KEY_DURATION)?
                input.getLong(MediaFormat.KEY_DURATION):0;
            codec=MediaCodec.createDecoderByType(mime);
            codec.configure(input,null,null,0);codec.start();
            boolean inputEnd=false,outputEnd=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            long guard=0;
            while(!outputEnd){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("Ses çözümlemesi iptal edildi");
                if(++guard>10000000)throw new IOException("Ses çözümleme zaman aşımı");
                if(!inputEnd){
                    int index=codec.dequeueInputBuffer(10000);
                    if(index>=0){
                        ByteBuffer buffer=codec.getInputBuffer(index);
                        if(buffer==null)throw new IOException("Ses giriş tamponu yok");
                        buffer.clear();
                        int n=ex.readSampleData(buffer,0);
                        if(n<0){
                            codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnd=true;
                        }else{
                            long time=ex.getSampleTime();
                            codec.queueInputBuffer(index,0,n,time,0);
                            ex.advance();
                        }
                    }
                }
                int index=codec.dequeueOutputBuffer(info,10000);
                if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                    MediaFormat format=codec.getOutputFormat();
                    rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels=Math.max(1,format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    if(format.containsKey(MediaFormat.KEY_PCM_ENCODING)&&
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING)==AudioFormat.ENCODING_PCM_FLOAT)
                        throw new IOException("Bu cihazın PCM_FLOAT ses çıkışı henüz desteklenmiyor");
                }else if(index>=0){
                    if(info.size>0){
                        ByteBuffer buffer=codec.getOutputBuffer(index);
                        if(buffer!=null){
                            buffer.position(info.offset);buffer.limit(info.offset+info.size);
                            buffer.order(ByteOrder.LITTLE_ENDIAN);
                            int frames=info.size/(2*channels);
                            for(int i=0;i<frames;i++){
                                int mono=0;
                                for(int ch=0;ch<channels;ch++)mono+=buffer.getShort();
                                mono/=channels;
                                sum+=mono;averageCount++;phase+=RATE;
                                if(phase>=rate){
                                    short value=(short)(sum/Math.max(1,averageCount));
                                    pcm.write(value&255);pcm.write((value>>>8)&255);
                                    sampleCount++;phase-=rate;sum=0;averageCount=0;
                                    if(pcm.size()>=MAX)emit();
                                }
                            }
                        }
                    }
                    if(duration>0)progress=(int)Math.max(0,
                        Math.min(99,info.presentationTimeUs*100/duration));
                    outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(index,false);
                }
            }
            if(pcm.size()>0)emit();
        }finally{
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            ex.release();
        }
    }
    private void emit()throws Exception{
        byte[] bytes=pcm.toByteArray();
        if(bytes.length==0)return;
        File temp=File.createTempFile("gunizi_chunk_"+sequence++,".wav",context.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(temp)){
                header(out,bytes.length);
                out.write(bytes);out.flush();
            }
            consumer.onChunk(temp,currentStartMs,progress);
        }finally{
            temp.delete();
            currentStartMs+=bytes.length*1000L/(RATE*2);
            pcm.reset();
        }
    }
    private static void header(OutputStream out,int bytes)throws IOException{
        out.write(new byte[]{'R','I','F','F'});le32(out,bytes+36);
        out.write(new byte[]{'W','A','V','E','f','m','t',' '});le32(out,16);
        le16(out,1);le16(out,1);le32(out,RATE);le32(out,RATE*2);
        le16(out,2);le16(out,16);
        out.write(new byte[]{'d','a','t','a'});le32(out,bytes);
    }
    private static void le16(OutputStream o,int n)throws IOException{o.write(n&255);o.write((n>>>8)&255);}
    private static void le32(OutputStream o,int n)throws IOException{
        for(int i=0;i<4;i++)o.write((n>>>(i*8))&255);
    }
}
