package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Persist every extracted item and its source time without truncating long days. */
final class WorkMemoryStore {
    static final String REVIEW="REVIEW",MINE="MINE",WAIT="WAIT",DONE="DONE";
    static final String TOPIC="TOPIC",TASK="TASK",DECISION="DECISION";
    static final class Item {
        String id,kind,topic,content,responsible,status,contact;
        long timeMs;boolean confirmed,suggested;
    }
    static final class Report {
        String id,name,sourceUri,sourceHash;
        long created;
        final ArrayList<Item> items=new ArrayList<>();
    }
    private static File root(Context c)throws IOException {
        File d=new File(c.getNoBackupFilesDir(),"work_memory");
        if(!d.exists()&&!d.mkdirs())throw new IOException("İş hafızası klasörü açılamadı");
        return d;
    }
    private static boolean valid(String id){
        return id!=null&&id.matches("[0-9a-fA-F]{32}");
    }
    private static File file(Context c,String id,String suffix)throws IOException{
        if(!valid(id))throw new IOException("İş hafızası kimliği hatalı");
        return new File(root(c),id+suffix);
    }
    private static boolean filled(String s){
        return s!=null&&!s.trim().isEmpty()&&!s.equalsIgnoreCase("Belirtilmedi");
    }
    private static String clean(String s){
        return s==null?"":s.replace("\u0000","").trim();
    }
    private static String suggest(String kind,String content){
        String s=content.toLowerCase(new Locale("tr","TR"));
        if((s.contains("tamamlandı")||s.contains("sonuçlandı")||
            s.contains("çözüldü")||s.contains("kapatıldı"))&&
            !s.contains("tamamlanmadı")&&!s.contains("sonuçlanmadı")&&
            !s.contains("henüz")&&!s.contains("değil"))return DONE;
        if(s.contains("dönüş bek")||s.contains("yanıt bek")||
            s.contains("cevap bek")||s.contains("onay bek")||
            s.contains("haber bek"))return WAIT;
        if(TASK.equals(kind)&&(s.contains("benim")||s.contains("bana ")||
            s.contains("yapacağım")||s.contains("ilgileniyorum")))return MINE;
        return REVIEW;
    }
    private static String entryKey(String reportId,int ordinal,String kind,
                                   String stamp,String topic,String content)throws Exception{
        String base=reportId+"|"+ordinal+"|"+kind+"|"+stamp+"|"+topic+"|"+content;
        byte[] bytes=MessageDigest.getInstance("SHA-256")
            .digest(base.getBytes(StandardCharsets.UTF_8));
        StringBuilder id=new StringBuilder(32);
        for(int i=0;i<16;i++)id.append(String.format(Locale.ROOT,"%02x",bytes[i]&255));
        return id.toString();
    }
    private static void write(AtomicFile target,byte[] data)throws IOException{
        FileOutputStream out=null;
        try{
            out=target.startWrite();out.write(data);out.getFD().sync();
            target.finishWrite(out);
        }catch(IOException e){if(out!=null)target.failWrite(out);throw e;}
    }
    static String save(Context c,String id,String name,String uri,String digest,
                       List<TopicReportRenderer.Entry> entries)throws IOException {
        if(!valid(id)||digest==null||!digest.matches("[0-9a-f]{64}"))
            throw new IOException("Kayıt kimliği doğrulanamadı");
        AtomicFile table=new AtomicFile(file(c,id,".jsonl"));
        FileOutputStream raw=null;
        int count=0;
        try{
            raw=table.startWrite();
            BufferedWriter writer=new BufferedWriter(new OutputStreamWriter(
                raw,StandardCharsets.UTF_8),64*1024);
            for(TopicReportRenderer.Entry entry:entries){
                String[] p=entry.parts;
                String kind=filled(p[3])?DECISION:filled(p[4])?TASK:TOPIC;
                String value=DECISION.equals(kind)?p[3]:TASK.equals(kind)?p[4]:p[2];
                if(!filled(value))continue;
                JSONObject row=new JSONObject();
                row.put("id",entryKey(id,count++,kind,p[0],p[1],value));
                row.put("kind",kind);row.put("topic",clean(p[1]));
                row.put("content",clean(value));row.put("responsible",clean(p[5]));
                row.put("atMs",TopicReportRenderer.timestampMs(p[0]));
                row.put("suggestedStatus",suggest(kind,value));
                writer.write(row.toString());writer.newLine();
            }
            if(count==0)throw new IOException("İş hafızasına eklenecek içerik yok");
            writer.flush();raw.getFD().sync();
            table.finishWrite(raw);raw=null;
        }catch(Exception ex){
            if(raw!=null)table.failWrite(raw);
            throw new IOException("İş hafızası satırları yazılamadı",ex);
        }
        JSONObject meta=new JSONObject();
        try{
            meta.put("id",id);meta.put("name",name);meta.put("uri",uri);
            meta.put("digest",digest);meta.put("created",System.currentTimeMillis());
            meta.put("count",count);
        }catch(Exception ex){throw new IOException("Rapor kimliği yazılamadı",ex);}
        write(new AtomicFile(file(c,id,".meta")),
            meta.toString().getBytes(StandardCharsets.UTF_8));
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("workMemoryLatest",id).putString("workMemoryError","").apply();
        return id;
    }
    static Report load(Context c,String id)throws IOException{
        JSONObject meta=readJson(file(c,id,".meta"));
        Report report=new Report();
        report.id=id;report.name=meta.optString("name","Ses kaydı");
        report.sourceUri=meta.optString("uri","");
        report.sourceHash=meta.optString("digest","");
        report.created=meta.optLong("created",0);
        JSONObject states=readState(c,id);
        File data=file(c,id,".jsonl");
        if(data.length()>80L*1024*1024)throw new IOException("Rapor çok büyük");
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                new FileInputStream(data),StandardCharsets.UTF_8))){
            String line;
            while((line=reader.readLine())!=null){
                if(line.isEmpty())continue;
                JSONObject v;
                try{v=new JSONObject(line);}catch(Exception e){continue;}
                Item item=new Item();
                item.id=v.optString("id");item.kind=v.optString("kind",TOPIC);
                item.topic=v.optString("topic");item.content=v.optString("content");
                item.responsible=v.optString("responsible","");
                item.timeMs=v.optLong("atMs",-1);
                String suggested=v.optString("suggestedStatus",REVIEW);
                JSONObject state=states.optJSONObject(item.id);
                item.confirmed=state!=null&&state.optBoolean("confirmed",false);
                item.status=item.confirmed?state.optString("status",REVIEW):suggested;
                item.contact=state!=null?state.optString("contact",""):"";
                item.suggested=!item.confirmed&&!REVIEW.equals(item.status);
                report.items.add(item);
            }
        }catch(Exception e){throw new IOException("İş hafızası okunamadı",e);}
        return report;
    }
    private static JSONObject readJson(File f)throws IOException {
        if(!f.isFile()||f.length()>8*1024*1024)
            throw new IOException("Rapor meta verisi bulunamadı");
        try(InputStream in=new FileInputStream(f)){
            byte[] raw=new byte[(int)f.length()];
            int off=0;while(off<raw.length){int n=in.read(raw,off,raw.length-off);
                if(n<0)throw new IOException("Eksik rapor verisi");off+=n;}
            return new JSONObject(new String(raw,StandardCharsets.UTF_8));
        }catch(Exception e){throw new IOException("Rapor bilgisi okunamadı",e);}
    }
    private static JSONObject readState(Context c,String id)throws IOException {
        File f=file(c,id,".state");
        return f.isFile()?readJson(f):new JSONObject();
    }
    static void setStatus(Context c,String reportId,String itemId,String status,String contact)throws IOException {
        if(!Arrays.asList(REVIEW,MINE,WAIT,DONE).contains(status))
            throw new IOException("Geçersiz takip durumu");
        JSONObject map=readState(c,reportId);
        JSONObject next=new JSONObject();
        try{
            next.put("status",status);next.put("confirmed",true);
            next.put("contact",contact==null?"":contact.trim());
            map.put(itemId,next);
        }catch(Exception ex){throw new IOException("Durum güncellenemedi",ex);}
        write(new AtomicFile(file(c,reportId,".state")),
            map.toString().getBytes(StandardCharsets.UTF_8));
    }
    static List<String> recent(Context c){
        File[] files;
        try{files=root(c).listFiles((dir,name)->name.matches("[0-9a-fA-F]{32}\\.meta"));}
        catch(IOException e){return Collections.emptyList();}
        if(files==null)return Collections.emptyList();
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        ArrayList<String> ids=new ArrayList<>();
        for(File f:files)ids.add(f.getName().replace(".meta",""));
        return ids;
    }
    static String describe(Context c,String id)throws IOException {
        JSONObject meta=readJson(file(c,id,".meta"));
        String name=meta.optString("name","Ses kaydı");
        long created=meta.optLong("created",0);
        return name+" · "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",
            new Locale("tr","TR")).format(new Date(created));
    }
    static boolean hasSource(Context c,String uri){
        if(uri==null||uri.isEmpty())return false;
        for(String id:recent(c))try{
            if(uri.equals(readJson(file(c,id,".meta")).optString("uri","")))return true;
        }catch(Exception ignored){}
        return false;
    }
}