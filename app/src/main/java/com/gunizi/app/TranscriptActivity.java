package com.gunizi.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** View or share local Whisper transcription without a Qwen summary model. */
public final class TranscriptActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        int bg=0xff090e1b,white=0xfff0f7ff,secondary=0xffa2b4c9;
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*getResources().getDisplayMetrics().density);
        root.setPadding(pad,pad,pad,pad);root.setBackgroundColor(bg);
        TextView title=new TextView(this);title.setText("Konuşma dökümü");
        title.setTextColor(white);title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
        TextView fileLabel=new TextView(this);fileLabel.setTextColor(secondary);
        fileLabel.setText(getSharedPreferences("gunizi",MODE_PRIVATE)
            .getString("transcriptSource","Henüz konuşma dökümü yok."));
        fileLabel.setTextSize(13);root.addView(fileLabel);
        TextView body=new TextView(this);body.setTextColor(white);body.setTextSize(14);
        body.setTextIsSelectable(true);body.setPadding(6,18,6,18);
        String transcript=read();
        body.setText(transcript);
        Button share=new Button(this);share.setText("Metni paylaş / Samsung Notes");
        share.setAllCaps(false);
        share.setOnClickListener(v->{
            if(transcript.isEmpty())return;
            Intent send=new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT,transcript);
            startActivity(Intent.createChooser(send,"Konuşma dökümünü paylaş"));
        });
        root.addView(share);
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private String read(){
        String stored=getSharedPreferences("gunizi",MODE_PRIVATE)
            .getString("transcriptInternal",null);
        try{
            if(stored==null)return "Henüz konuşma dökümü oluşturulmadı.";
            File allowed=new File(getNoBackupFilesDir(),"transcripts").getCanonicalFile();
            File file=new File(stored).getCanonicalFile();
            if(!file.getPath().startsWith(allowed.getPath()+File.separator)||!file.isFile())
                return "Konuşma dökümü bulunamadı.";
            if(file.length()>4*1024*1024)return "Döküm çok büyük. Dosyayı kayıt klasöründen aç.";
            return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        }catch(Exception e){return "Döküm okunamadı: "+e.getMessage();}
    }
}
