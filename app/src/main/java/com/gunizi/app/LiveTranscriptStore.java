package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Local-only, source-bound incremental speech transcript; never uploads data. */
final class LiveTranscriptStore {
    private static final String PREF="gunizi";
    private static final Object LOCK=new Object();
    private static File directory(Context c) {
        return new File(c.getNoBackupFilesDir(),"transcripts");
    }
    static void begin(Context c,String jobId,String sourceUri) {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        p.edit().putString("analysisTimeline","")
            .putString("analysisLivePath","")
            .putString("analysisLiveJob",jobId)
            .putString("analysisLiveSourceUri",sourceUri==null?"":sourceUri).apply();
        if(jobId==null||!jobId.matches("[a-fA-F0-9-]{36}"))return;
        try {
            File dir=directory(c);
            if(!dir.exists()&&!dir.mkdirs())return;
            File f=new File(dir,"live_"+jobId+".txt");
            try(FileOutputStream out=new FileOutputStream(f,false)){out.getFD().sync();}
            if(jobId.equals(p.getString("analysisWorkId",""))) {
                p.edit().putString("analysisLivePath",f.getAbsolutePath()).apply();
            }
        }catch(IOException ignored) { /* Timeline preference still works without file. */ }
    }
    static void append(Context c,String jobId,String chunk) {
        if(chunk==null||chunk.trim().isEmpty()||
            chunk.contains("[[GUNIZI_SESSIZ_BOLUM]]"))return;
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        synchronized(LOCK) {
            if(!jobId.equals(p.getString("analysisWorkId",""))||
               !jobId.equals(p.getString("analysisLiveJob","")))return;
            String fileName=p.getString("analysisLivePath","");
            if(!fileName.isEmpty())try {
                File parent=directory(c).getCanonicalFile();
                File file=new File(fileName).getCanonicalFile();
                if(file.getPath().startsWith(parent.getPath()+File.separator)){
                    try(FileOutputStream out=new FileOutputStream(file,true)) {
                        out.write(chunk.getBytes(StandardCharsets.UTF_8));
                    }
                }
            }catch(IOException ignored){}
            String prior=p.getString("analysisTimeline","");
            // Keep bounded preference data. Full text lives only in the private transcript file.
            String lines=prior+chunk;
            String[] split=lines.split("\\r?\\n");
            StringBuilder window=new StringBuilder();
            int kept=0;
            for(int i=split.length-1;i>=0&&kept<16;i--){
                if(split[i].trim().isEmpty())continue;
                if(window.length()+split[i].length()>4500)break;
                window.insert(0,split[i]+"\\n");
                kept++;
            }
            p.edit().putString("analysisTimeline",window.toString()).apply();
        }
    }
    static String current(Context c, String selectedSource) {
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String current=p.getString("analysisLiveSourceUri","");
        if(selectedSource==null||!selectedSource.equals(current))return "";
        String path=p.getString("analysisLivePath","");
        if(path==null||path.isEmpty())return "";
        try {
            File allowed=directory(c).getCanonicalFile();
            File file=new File(path).getCanonicalFile();
            if(!file.getPath().startsWith(allowed.getPath()+File.separator)||
                !file.isFile()||file.length()>8L*1024*1024)return "";
            return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        }catch(IOException e){return "";}
    }
}
