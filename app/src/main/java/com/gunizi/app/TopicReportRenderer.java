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
            // Preserve every extracted row. Large reports are grouped and initially collapsed.
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
        java.util.Map<String,java.util.List<Entry>> grouped=new java.util.LinkedHashMap<>();
        for(Entry e:entries){
            String key=e.parts[1].toLowerCase(java.util.Locale.forLanguageTag("tr-TR"))
                .replaceAll("\\s+"," ").trim();
            grouped.computeIfAbsent(key,k->new java.util.ArrayList<>()).add(e);
        }
        int tasks=0,decisions=0;
        for(Entry e:entries){
            if(!e.parts[4].equalsIgnoreCase("Belirtilmedi"))tasks++;
            if(!e.parts[3].equalsIgnoreCase("Belirtilmedi"))decisions++;
        }
        StringBuilder s=new StringBuilder();
        s.append("<!doctype html><html lang='tr'><head><meta charset='utf-8'>")
         .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
         .append("<title>Günİzi Konu Analizi</title>")
         .append("<style>body{font:14px system-ui;background:#090e1b;color:#edf8ff;margin:0;padding:16px}")
         .append("h2{margin:5px 0 10px}.muted{color:#a9b9ca;line-height:1.5}")
         .append(".stats{display:flex;gap:8px;margin:14px 0;flex-wrap:wrap}")
         .append(".stat{background:#172031;border-radius:14px;padding:12px;flex:1;min-width:75px}")
         .append(".stat b{display:block;font-size:22px;color:#3bdbc9}")
         .append("details{background:#172031;border-radius:14px;margin:10px 0;border:1px solid #2d3b4d}")
         .append("summary{cursor:pointer;padding:15px;font-weight:700;color:white}")
         .append("summary small{display:block;color:#b0bfd0;font-weight:400;margin-top:5px}")
         .append(".scroll{overflow-x:auto}table{width:100%;min-width:650px;border-collapse:collapse}")
         .append("th{text-align:left;background:#233448;color:#3bdbc9;padding:11px}")
         .append("td{padding:11px;vertical-align:top;border-top:1px solid #324053;white-space:pre-wrap;overflow-wrap:anywhere}")
         .append("tr:nth-child(even){background:#111a2a}</style></head><body>")
         .append("<h2>Günİzi • Günlük konu analizi</h2>")
         .append("<p class='muted'>Kaynak: ").append(esc(name))
         .append("<br>Karar ve görevleri kaynaktaki sesle doğrula.</p>")
         .append("<div class='stats'><div class='stat'><b>").append(grouped.size())
         .append("</b>Konu</div><div class='stat'><b>").append(decisions)
         .append("</b>Karar ifadesi</div><div class='stat'><b>").append(tasks)
         .append("</b>Takip ifadesi</div></div>");
        for(java.util.List<Entry> group:grouped.values()){
            s.append("<details><summary>").append(esc(group.get(0).parts[1]))
             .append("<small>").append(group.size())
             .append(" görüşme bölümü</small></summary><div class='scroll'><table>")
             .append("<thead><tr><th>Saat</th><th>Konuşulanlar</th><th>Karar</th>")
             .append("<th>Yapılacak</th><th>Sorumlu</th></tr></thead><tbody>");
            for(Entry e:group){
                s.append("<tr>");
                for(int index:new int[]{0,2,3,4,5}){
                    s.append("<td>").append(esc(e.parts[index]));
                    if(index==0){
                        long moment=timestampMs(e.parts[0]);
                        if(moment>=0)
                            s.append(" <a style='color:#3bdbc9' href='gunizi://listen?ms=")
                                .append(moment).append("'>Ses bölümünü dinle</a>");
                    }
                    s.append("</td>");
                }
                s.append("</tr>");
            }
            s.append("</tbody></table></div></details>");
        }
        return s.append("<p class='muted'>Otomatik konuşma tanıma ve özetleme hatalı olabilir.</p>")
            .append("</body></html>").toString();
    }
    static long timestampMs(String value){
        if(value==null)return -1;
        java.util.regex.Matcher m=java.util.regex.Pattern
            .compile("(?<!\\d)(\\d{2}):(\\d{2}):(\\d{2})(?!\\d)")
            .matcher(value);
        if(!m.find())return -1;
        try{
            long h=Long.parseLong(m.group(1)),min=Long.parseLong(m.group(2)),
                sec=Long.parseLong(m.group(3));
            if(min>=60||sec>=60||h>24)return -1;
            return ((h*60+min)*60+sec)*1000L;
        }catch(Exception ignored){return -1;}
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
