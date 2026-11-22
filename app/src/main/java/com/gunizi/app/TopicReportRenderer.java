package com.gunizi.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Produce escaped, topic-by-topic HTML and CSV from the local Qwen3 report. */
final class TopicReportRenderer {
    static final String START="[[GUNIZI_TABLO_BASLA]]";
    static final String END="[[GUNIZI_TABLO_BITIR]]";
    private static final String[] COLUMNS={"Saat / Aralık","Konu","Konuşulanlar","Alınan karar","Takip edilecek iş","Sorumlu"};
    static final class Entry {
        final String[] parts;
        Entry(String[] p){parts=p;}
    }
    static List<Entry> parse(String report){
        ArrayList<Entry> results=new ArrayList<>();
        int start=report.indexOf(START),end=report.indexOf(END);
        String table=start>=0?report.substring(start+START.length(),end>start?end:report.length()):"";
        for(String line:table.split("\\R")){
            line=line.replace("###","").trim();
            if(line.isEmpty())continue;
            String[] arr=line.split("\\t",-1);
            if(arr.length<6&&line.contains("|")){
                String trimmed=line.replaceAll("^\\|","").replaceAll("\\|$","");
                arr=trimmed.split("\\|",-1);
            }
            if(arr.length<6)continue;
            String[] cols=new String[6];
            for(int i=0;i<6;i++){
                cols[i]=arr[i].trim().replaceAll("(?i)<\\/?think[^>]*>","");
                if(cols[i].length()>1600)cols[i]=cols[i].substring(0,1600);
                if(cols[i].isEmpty())cols[i]="Belirtilmedi";
            }
            if(cols[1].equalsIgnoreCase("Konu")||cols[0].equalsIgnoreCase("Saat")
                ||cols[0].matches("[- :]+"))continue;
            results.add(new Entry(cols));
            if(results.size()>=1200)break;
        }
        if(results.isEmpty())
            results.add(new Entry(new String[]{"Belirsiz","Konu tablosu oluşturulamadı",
                "Yapay zeka yapılandırılmış konu üretemedi. Ayrıntılar tam metin raporunda.",
                "Belirlenemedi","Tam metni kontrol et","Belirtilmedi"}));
        return results;
    }

    /** Readable plain-text topic summary for the user's own notes or Samsung Notes sharing. */
    static String noteText(List<Entry> entries){
        java.util.Map<String,java.util.List<Entry>> groups=new java.util.LinkedHashMap<>();
        for(Entry e:entries){
            String topic=e.parts[1].trim();
            groups.computeIfAbsent(topic,k->new java.util.ArrayList<>()).add(e);
        }
        StringBuilder result=new StringBuilder("GÜNİZİ • KONU KONU GÜNLÜK NOTLAR\n");
        result.append("Otomatik analizdir; önemli bilgileri ses kaydından doğrula.\n\n");
        for(java.util.Map.Entry<String,java.util.List<Entry>> group:groups.entrySet()){
            result.append("■ ").append(group.getKey()).append("\n");
            for(Entry row:group.getValue()){
                result.append("Saat: ").append(row.parts[0]).append("\n");
                result.append("Konuşulanlar: ").append(row.parts[2]).append("\n");
                result.append("Karar: ").append(row.parts[3]).append("\n");
                result.append("Takip: ").append(row.parts[4]).append("\n");
                result.append("Sorumlu: ").append(row.parts[5]).append("\n\n");
            }
            result.append("\n");
            if(result.length()>200000){
                result.append("Kalan ayrıntılar HTML/CSV raporunda bulunur.\n");
                break;
            }
        }
        return result.toString();
    }

    static String csv(List<Entry> entries){
        StringBuilder s=new StringBuilder("\uFEFF");
        for(String h:COLUMNS)s.append('"').append(h).append('"').append(';');
        s.append('\n');
        for(Entry e:entries){
            for(String cell:e.parts)s.append('"').append(cell.replace(String.valueOf((char)34),String.valueOf((char)34)+String.valueOf((char)34)))
                .append('"').append(';');
            s.append('\n');
        }
        return s.toString();
    }
    private static String esc(String s){
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace(String.valueOf((char)34),"&quot;").replace("'","&#39;");
    }
    static String html(List<Entry> entries,String name){
        StringBuilder s=new StringBuilder();
        s.append("<!doctype html><html lang='tr'><head><meta charset='utf-8'>")
         .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
         .append("<title>Günİzi – Konu Tablosu</title>")
         .append("<style>body{font:14px system-ui,-apple-system,sans-serif;background:#090e1b;color:#eff7ff;")
         .append("margin:0;padding:18px}h2{margin:8px 0 4px}.meta{color:#a2b4c9;margin:0 0 12px}")
         .append(".scroll{overflow-x:auto;border:1px solid #344055;border-radius:14px}")
         .append("table{border-collapse:collapse;width:100%;min-width:930px}th{background:#1e3544;color:#49d9cf;")
         .append("text-align:left;padding:13px;position:sticky;top:0}td{border-bottom:1px solid #2f384b;")
         .append("padding:12px;vertical-align:top;white-space:pre-wrap;overflow-wrap:anywhere}tr:nth-child(even)")
         .append("{background:#151e30}</style></head><body><h2>Günİzi · Günlük konu raporu</h2>")
         .append("<p class='meta'>Kaynak: ").append(esc(name)).append("</p>")
         .append("<p class='meta'>Otomatik çıkarım; önemli maddeleri sesle doğrula.</p>")
         .append("<div class='scroll'><table><thead><tr>");
        for(String h:COLUMNS)s.append("<th>").append(esc(h)).append("</th>");
        s.append("</tr></thead><tbody>");
        for(Entry e:entries){
            s.append("<tr>");
            for(String cell:e.parts)s.append("<td>").append(esc(cell)).append("</td>");
            s.append("</tr>");
        }
        return s.append("</tbody></table></div></body></html>").toString();
    }
    static File saveInternal(Context c,String html,String filename)throws IOException{
        File root=new File(c.getNoBackupFilesDir(),"reports");
        if(!root.exists()&&!root.mkdirs())throw new IOException("Rapor dizini açılamadı");
        String safe=filename.replaceAll("[^a-zA-Z0-9_-]","_");
        File target=new File(root,safe+".html");
        File part=new File(root,safe+".tmp");
        try(FileOutputStream out=new FileOutputStream(part)){
            out.write(html.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
        if(target.exists()&&!target.delete())throw new IOException("Eski rapor silinemedi");
        if(!part.renameTo(target))throw new IOException("Rapor kaydedilemedi");
        c.getSharedPreferences("gunizi",Context.MODE_PRIVATE).edit()
            .putString("latestHtmlReport",target.getAbsolutePath()).apply();
        return target;
    }
}
