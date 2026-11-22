package com.gunizi.app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** Fully local Turkish speech recognition (Vosk model) + extractive structured report. */
public final class DailyReportEngine {
    public interface Progress {void update(int percent,String message);}
    static final class Segment {
        final double start,end; final String text;
        Segment(double s,double e,String t){start=s;end=e;text=t;}
    }
    static String analyze(Context context,Uri audioUri,String fileName,Progress progress)throws Exception{
        if(!OfflineModelManager.ready(context))throw new IllegalStateException("Türkçe modeli önce indir");
        List<Segment> transcript=new ArrayList<>();
        progress.update(0,"Türkçe model yükleniyor…");
        try(Model model=new Model(OfflineModelManager.modelPath(context).getAbsolutePath());
            Recognizer recognizer=new Recognizer(model,16000.0f)){
            recognizer.setWords(true);
            MediaExtractor extractor=new MediaExtractor();
            MediaCodec decoder=null;
            try{
                extractor.setDataSource(context,audioUri,null);
                int track=-1;
                for(int i=0;i<extractor.getTrackCount();i++){
                    String mime=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                    if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
                }
                if(track<0)throw new IllegalArgumentException("Ses izi bulunamadı");
                extractor.selectTrack(track);
                MediaFormat input=extractor.getTrackFormat(track);
                String mime=input.getString(MediaFormat.KEY_MIME);
                decoder=MediaCodec.createDecoderByType(mime);
                decoder.configure(input,null,null,0);decoder.start();
                int rate=input.containsKey(MediaFormat.KEY_SAMPLE_RATE)?
                    input.getInteger(MediaFormat.KEY_SAMPLE_RATE):48000;
                int channels=input.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?
                    input.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
                long duration=input.containsKey(MediaFormat.KEY_DURATION)?
                    input.getLong(MediaFormat.KEY_DURATION):0;
                MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                boolean endIn=false,endOut=false;
                Downsampler resampler=new Downsampler(rate,channels);
                int lastPct=-1;
                while(!endOut){
                    int in=decoder.dequeueInputBuffer(10000);
                    if(in>=0&&!endIn){
                        ByteBuffer ib=decoder.getInputBuffer(in);
                        if(ib==null)throw new IllegalStateException("Ses giriş tamponu yok");
                        ib.clear();
                        int n=extractor.readSampleData(ib,0);
                        if(n<0){
                            decoder.queueInputBuffer(in,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            endIn=true;
                        }else{
                            long pts=extractor.getSampleTime();
                            decoder.queueInputBuffer(in,0,n,pts,0);
                            extractor.advance();
                        }
                    }
                    int out=decoder.dequeueOutputBuffer(info,10000);
                    if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat format=decoder.getOutputFormat();
                        rate=format.containsKey(MediaFormat.KEY_SAMPLE_RATE)?
                            format.getInteger(MediaFormat.KEY_SAMPLE_RATE):rate;
                        channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?
                            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):channels;
                        resampler=new Downsampler(rate,channels);
                        if(format.containsKey(MediaFormat.KEY_PCM_ENCODING)&&
                           format.getInteger(MediaFormat.KEY_PCM_ENCODING)==android.media.AudioFormat.ENCODING_PCM_FLOAT)
                            throw new IllegalArgumentException("PCM_FLOAT biçimi henüz desteklenmiyor");
                    }else if(out>=0){
                        if(info.size>0){
                            ByteBuffer ob=decoder.getOutputBuffer(out);
                            if(ob!=null){
                                ob.position(info.offset);ob.limit(info.offset+info.size);
                                byte[] decoded=new byte[info.size];ob.get(decoded);
                                byte[] pcm=resampler.convert(decoded);
                                if(pcm.length>0&&recognizer.acceptWaveForm(pcm,pcm.length))
                                    appendResult(recognizer.getResult(),transcript);
                            }
                        }
                        endOut=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        if(duration>0){
                            int pct=(int)Math.min(99,Math.max(0,info.presentationTimeUs*100/duration));
                            if(pct/5!=lastPct/5){
                                lastPct=pct;
                                progress.update(pct,"Konuşmalar cihazda çözülüyor: %"+pct);
                            }
                        }
                        decoder.releaseOutputBuffer(out,false);
                    }
                    if(Thread.currentThread().isInterrupted())throw new InterruptedException("İşlem iptal edildi");
                }
                appendResult(recognizer.getFinalResult(),transcript);
            }finally{
                if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}decoder.release();}
                extractor.release();
            }
        }
        progress.update(100,"Günlük rapor hazırlanıyor…");
        return formatReport(transcript,fileName);
    }
    private static void appendResult(String json,List<Segment> entries){
        if(json==null||json.isEmpty())return;
        try{
            JSONObject root=new JSONObject(json);
            String text=root.optString("text","").trim();
            if(text.isEmpty())return;
            JSONArray words=root.optJSONArray("result");
            double first=entries.isEmpty()?0:entries.get(entries.size()-1).end;
            double last=first;
            if(words!=null&&words.length()>0){
                first=words.getJSONObject(0).optDouble("start",first);
                last=words.getJSONObject(words.length()-1).optDouble("end",first);
            }
            entries.add(new Segment(first,last,text));
        }catch(Exception ignored){}
    }
    private static final class Downsampler{
        private final int rate,ch;private long accum=0,sum=0;private int count=0;
        Downsampler(int rate,int channels){this.rate=Math.max(16000,rate);this.ch=Math.max(1,channels);}
        byte[] convert(byte[] pcm){
            ByteArrayOutputStream out=new ByteArrayOutputStream(Math.max(256,pcm.length/3));
            ByteBuffer data=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            int frames=pcm.length/(2*ch);
            for(int f=0;f<frames;f++){
                int combined=0;
                for(int c=0;c<ch;c++)combined+=data.getShort();
                sum+=combined/ch;count++;accum+=16000;
                if(accum>=rate){
                    int sample=(int)(sum/count);
                    out.write(sample&255);out.write((sample>>>8)&255);
                    sum=0;count=0;accum-=rate;
                }
            }
            return out.toByteArray();
        }
    }
    private static String time(double seconds){
        long s=(long)Math.max(0,seconds);
        return String.format(Locale.forLanguageTag("tr-TR"),"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);
    }
    private static String formatReport(List<Segment> segments,String sourceName){
        StringBuilder output=new StringBuilder();
        output.append("GÜNİZİ • GÜNLÜK SES RAPORU\n\n");
        output.append("Kaynak: ").append(sourceName).append("\n");
        output.append("Model: Vosk small-tr (yerel konuşma tanıma)\n");
        output.append("Özetleme: Yerel, alıntı temelli otomatik konu çıkarımı (üretken LLM değil)\n");
        output.append("Not: Yazıya dönüşümde isim ve kelime hataları olabilir. Bu raporda ses dışı olay uydurulmaz.\n\n");
        if(segments.isEmpty()){
            output.append("Anlaşılır konuşma bulunamadı.\n");
            return output.toString();
        }
        // Extractive ranking; all recognized material remains available in full transcript.
        Map<String,Integer> frequency=new HashMap<>();
        for(Segment s:segments)for(String w:terms(s.text))frequency.merge(w,1,Integer::sum);
        List<Segment> ordered=new ArrayList<>(segments);
        ordered.sort((a,b)->Double.compare(score(b,frequency),score(a,frequency)));
        output.append("GENEL ÖZET\n");
        int picks=Math.min(12,Math.max(4,segments.size()/12));
        List<Segment> top=new ArrayList<>(ordered.subList(0,Math.min(picks,ordered.size())));
        top.sort(Comparator.comparingDouble(s->s.start));
        for(Segment s:top)output.append("• [").append(time(s.start)).append("] ").append(s.text).append("\n");
        output.append("\nGÜNÜN AKIŞI (30 DAKİKALIK BÖLÜMLER)\n");
        Map<Integer,List<Segment>> buckets=new TreeMap<>();
        for(Segment s:segments){
            int bucket=(int)(s.start/1800);
            buckets.computeIfAbsent(bucket,k->new ArrayList<>()).add(s);
        }
        for(Map.Entry<Integer,List<Segment>> e:buckets.entrySet()){
            Segment best=null;double bestScore=-1;
            for(Segment s:e.getValue()){
                double v=score(s,frequency);
                if(v>bestScore){best=s;bestScore=v;}
            }
            if(best!=null)output.append("• [").append(time(e.getKey()*1800)).append("–")
                .append(time((e.getKey()+1)*1800)).append("] ").append(best.text)
                .append(" (").append(e.getValue().size()).append(" konuşma bölümü)\n");
        }
        output.append("\nTAKİP GEREKTİREBİLECEK KONUŞMALAR (doğrulanmalı)\n");
        Pattern task=Pattern.compile("(?iu)(yarın|haftaya|gönder|kontrol|tamamla|yapılacak|bakalım|bekliyor|hatırlat|ruhsat|belediye|abonelik|evrak|toplantı|öde)");
        int listed=0;
        for(Segment s:segments){
            if(task.matcher(s.text).find()){
                output.append("• [").append(time(s.start)).append("] ").append(s.text).append("\n");
                if(++listed>=45){output.append("(Diğerleri tam dökümde)\n");break;}
            }
        }
        if(listed==0)output.append("Belirgin bir takip ifadesi tespit edilmedi.\n");
        output.append("\nTAM KONUŞMA DÖKÜMÜ\n");
        for(Segment s:segments)output.append("[").append(time(s.start)).append("–")
            .append(time(s.end)).append("] ").append(s.text).append("\n");
        output.append("\nToplam çözümlenen konuşma bölümü: ").append(segments.size()).append("\n");
        return output.toString();
    }
    private static final Set<String> STOP=new HashSet<>(Arrays.asList(
        "ve","veya","bir","bu","şu","o","da","de","ile","için","ama","ki","yani","çok","gibi",
        "ben","sen","biz","siz","onlar","olarak","var","yok","şey","diye","evet","hayır",
        "oldu","olacak","daha","sonra","önce","bunu","bana","bize","biraz","tamam","şimdi"
    ));
    private static List<String> terms(String input){
        ArrayList<String> words=new ArrayList<>();
        for(String x:input.toLowerCase(Locale.forLanguageTag("tr-TR")).split("[^\\p{L}]+")){
            if(x.length()>=4&&!STOP.contains(x))words.add(x);
        }
        return words;
    }
    private static double score(Segment s,Map<String,Integer> freq){
        List<String> tokens=terms(s.text);
        if(tokens.isEmpty())return 0;
        double result=0;
        for(String w:tokens)result+=Math.log1p(freq.getOrDefault(w,0));
        return result/Math.sqrt(tokens.size())*(tokens.size()>3?1:0.4);
    }
}
