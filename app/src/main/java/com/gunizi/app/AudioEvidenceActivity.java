package com.gunizi.app;

import android.app.Activity;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;

/** Read-only playback at a verified source timestamp. No silent upload. */
public final class AudioEvidenceActivity extends Activity {
    static final String EXTRA_SOURCE="gunizi.sourceUri";
    static final String EXTRA_START="gunizi.audioStartMs";
    private RecordingPlayerCard player;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        int pad=Math.round(getResources().getDisplayMetrics().density*16);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(0xff090e1b);
        TextView title=new TextView(this);
        title.setText("Konuşmanın geçtiği yeri dinle");
        title.setTextSize(21);title.setTextColor(0xffeef8ff);
        root.addView(title);
        TextView notice=new TextView(this);
        notice.setText("Yapay zekanın çıkardığı bilgiyi kaynak sesle karşılaştır.");
        notice.setTextColor(0xffa2b4c9);notice.setTextSize(13);
        root.addView(notice);
        player=new RecordingPlayerCard(this);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=pad;root.addView(player,lp);
        Button back=new Button(this);back.setText("Geri");back.setAllCaps(false);
        root.addView(back);back.setOnClickListener(v->finish());
        setContentView(root);
        String source=getIntent().getStringExtra(EXTRA_SOURCE);
        long seek=Math.max(0,getIntent().getLongExtra(EXTRA_START,0L));
        SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        boolean known=source!=null&&(source.equals(p.getString("transcriptSourceUri",""))||
            source.equals(p.getString("lastReportSourceUri",""))||
            source.equals(p.getString("aiSelectedSourceUri",""))||
            WorkMemoryStore.hasSource(this,source));
        if(!known){notice.setText("Kayıt doğrulanamadı. Ses bağlantısı açılmadı.");return;}
        Uri uri=Uri.parse(source);
        notice.setText("Ses hazırlanıyor…");
        new Thread(()->{
            try{
                AiAudioPreflight.Info info=AiAudioPreflight.inspect(this,uri);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    if(info.durationMs>0&&seek>info.durationMs){
                        notice.setText("İşaretli zaman sesin süresini aşıyor.");
                        return;
                    }
                    player.setSource(uri,info);
                    player.playAt(seek);
                    notice.setText("Sesin bu noktasından oynatılıyor · "+(seek/1000)+" sn");
                });
            }catch(Exception ex){
                runOnUiThread(()->notice.setText("Ses açılamadı; kaynağın yerini kontrol et."));
            }
        },"GunIzi-EvidencePreflight").start();
    }
    @Override protected void onDestroy(){
        if(player!=null)player.release();
        super.onDestroy();
    }
}