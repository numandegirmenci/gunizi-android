package com.gunizi.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Private local notes. A report is copied only when user explicitly saves it.
 * Android share sheet is used for Samsung Notes or other compatible notes apps.
 */
final class UserNotesStore {
    private static File notesDir(Context c){
        File d=new File(c.getFilesDir(),"personal_notes");
        if(!d.exists())d.mkdirs();
        return d;
    }
    static File reportDraft(Context c){
        File d=new File(c.getNoBackupFilesDir(),"reports");
        if(!d.exists())d.mkdirs();
        return new File(d,"latest_report_note.txt");
    }
    static void latestReport(Context c,String title,String body)throws IOException{
        File report=reportDraft(c);
        File tmp=new File(report.getParentFile(),report.getName()+".tmp");
        write(tmp,title+"\n\n"+body);
        if(report.exists()&&!report.delete())throw new IOException("Önceki taslak silinemedi");
        if(!tmp.renameTo(report))throw new IOException("Rapor taslağı yerleştirilemedi");
    }
    static String latestDraft(Context c)throws IOException{
        File file=reportDraft(c);
        return file.exists()?read(file):"";
    }
    static String save(Context c,String existingId,String title,String body)throws IOException{
        if(title==null||title.trim().isEmpty())throw new IOException("Not başlığı boş olamaz");
        if(body==null||body.trim().isEmpty())throw new IOException("Not içeriği boş olamaz");
        String id=existingId;
        if(id==null||!id.matches("[0-9]{13}_[0-9a-f]{8}"))
            id=System.currentTimeMillis()+"_"+UUID.randomUUID().toString().substring(0,8);
        File target=new File(notesDir(c),id+".txt");
        File temp=new File(notesDir(c),id+".tmp");
        write(temp,title.trim()+"\n\n"+body.trim());
        if(target.exists()&&!target.delete())throw new IOException("Eski not güncellenemedi");
        if(!temp.renameTo(target))throw new IOException("Not yazılamadı");
        return id;
    }
    static void delete(Context c,String id)throws IOException{
        if(id==null||!id.matches("[0-9]{13}_[0-9a-f]{8}"))
            throw new IOException("Not kimliği hatalı");
        File target=new File(notesDir(c),id+".txt");
        if(!target.exists())throw new IOException("Not bulunamadı");
        if(!target.delete())throw new IOException("Not silinemedi");
    }
    static String readNote(Context c,String id)throws IOException{
        if(id==null||!id.matches("[0-9]{13}_[0-9a-f]{8}"))throw new IOException("Not kimliği hatalı");
        File file=new File(notesDir(c),id+".txt");
        if(!file.exists())throw new IOException("Not bulunamadı");
        return read(file);
    }
    static List<String> list(Context c){
        File[] all=notesDir(c).listFiles((dir,name)->name.matches("[0-9]{13}_[0-9a-f]{8}\\.txt"));
        if(all==null)return Collections.emptyList();
        Arrays.sort(all,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        ArrayList<String> ids=new ArrayList<>();
        for(File f:all)ids.add(f.getName().replace(".txt",""));
        return ids;
    }
    static String title(String note){
        if(note==null)return "";
        int n=note.indexOf('\n');
        return (n<0?note:note.substring(0,n)).trim();
    }
    static String body(String note){
        if(note==null)return "";
        int n=note.indexOf('\n');
        return n<0?"":note.substring(n).trim();
    }
    private static String read(File file)throws IOException{
        if(file.length()>2L*1024*1024)throw new IOException("Not 2 MB sınırını aşıyor");
        return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
    }
    private static void write(File file,String body)throws IOException{
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        }
    }
}
