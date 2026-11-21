package com.gunizi.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.View;
import android.widget.*;
import androidx.work.*;
import java.util.List;

/** One-time model download and manual replay of a previously saved full-day recording. */
public final class SummaryActivity extends Activity {
    private static final int PICK_AUDIO=321;
    private TextView status;
    private LinearLayout root;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0b1020);
        root=new LinearLayout(this);root.setPadding(32,44,32,50);
        root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        label("← Günİzi • Yerel Rapor",26,true);
        label("Telefonun içinde Türkçe konuşma tanıma ve alıntı temelli günlük özet.",15,false);
        label("Kayıtlar, konuşma dökümü ve özet hiçbir sunucuya gönderilmez.",14,false);
        status=label(OfflineModelManager.ready(this)?"Türkçe model hazır ✓":
            "Türkçe modeli bir defa indirmen gerekiyor (yaklaşık 35 MB).",15,true);
        Button download=button("Türkçe çevrimdışı modeli indir");
        download.setEnabled(!OfflineModelManager.ready(this));
        download.setOnClickListener(v->{
            download.setEnabled(false);
            OfflineModelManager.install(this,msg->{
                status.setText(msg);
                if(OfflineModelManager.ready(this))download.setEnabled(false);
                else if(msg.startsWith("Model kurulamadı"))download.setEnabled(true);
            });
        });
        Button analyze=button("Kaydedilmiş ses dosyasından rapor oluştur");
        analyze.setOnClickListener(v->{
            if(!OfflineModelManager.ready(this)){
                status.setText("Önce Türkçe modeli yükle");return;
            }
            if(new Prefs(this).folder()==null){
                status.setText("Önce ana ekrandan rapor klasörünü seç");return;
            }
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,PICK_AUDIO);
        });
        label("Mesai kaydında model kurulmuşsa rapor şarja bağlandığında otomatik hazırlanır. "
            +"Bu butonla istediğin kayıt için hemen iş emri verebilirsin.",13,false);
        Button refresh=button("Rapor durumunu yenile");
        refresh.setOnClickListener(v->showStatus());
        showStatus();
    }
    private TextView label(String s,int size,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
        t.setTextColor(bold?Color.WHITE:0xffb2c4d6);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,12,0,14);root.addView(t);return t;
    }
    private Button button(String s){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);
        root.addView(b);return b;
    }
    private void showStatus(){
        android.content.SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        String err=p.getString("summaryError","");
        String last=p.getString("summaryLast","");
        if(!err.isEmpty())status.setText("Son rapor hatası: "+err);
        else if(!last.isEmpty())status.setText(last);
        new Thread(()->{
            try{
                List<WorkInfo> items=WorkManager.getInstance(this)
                    .getWorkInfosByTag("gunizi-local-summary").get();
                if(items==null||items.isEmpty())return;
                WorkInfo current=items.get(items.size()-1);
                if(current.getState()==WorkInfo.State.RUNNING||
                   current.getState()==WorkInfo.State.ENQUEUED){
                    String stage=current.getProgress().getString("stage");
                    runOnUiThread(()->status.setText(current.getState()==WorkInfo.State.ENQUEUED?
                        "Günlük özet uygun pil/şarj koşullarında hazırlanacak":
                        stage==null?"Yerel model işliyor…":stage));
                }
            }catch(Exception ignored){}
        },"GunIzi-SummaryStatus").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK_AUDIO||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception ignored){}
        String name="Secilen_Kayit.ogg";
        try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
            null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);
        }catch(Exception ignored){}
        DailySummaryWorker.queue(this,uri,name,false);
        status.setText("Rapor için iş emri verildi. Telefonu şarja takman önerilir.");
    }
}
