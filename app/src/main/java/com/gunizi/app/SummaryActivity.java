package com.gunizi.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import androidx.work.*;
import java.util.List;

/** Model setup requires explicit clicks; inference itself runs exclusively on the phone. */
public final class SummaryActivity extends Activity {
    private static final int PICK=321;
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private LinearLayout root;
    private TextView status,whisperState,qwenState;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);build();
    }
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private LinearLayout card(){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(15),dp(17),dp(14));c.setBackground(bg(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        root.addView(c,p);return c;
    }
    private TextView text(LinearLayout parent,String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(bold?WHITE:MUTED);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);
        parent.addView(t,p);return t;
    }
    private Button button(LinearLayout parent,String value){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);
        b.setTextSize(14);b.setTextColor(WHITE);b.setBackground(bg(0xff2b3a55));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(47));p.bottomMargin=dp(8);
        parent.addView(b,p);return b;
    }
    private void build(){
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(25));scroll.addView(root);setContentView(scroll);
        LinearLayout head=card();
        Button back=button(head,"← Geri");back.setOnClickListener(v->finish());
        text(head,"Yerel Yapay Zeka",23,true);
        text(head,"9 saatlik ses kaydını cihazda beşer dakikalık bölümler halinde işler.",13,false);
        LinearLayout models=card();
        text(models,"1. Konuşma tanıma · Whisper small",17,true);
        whisperState=text(models,"Türkçe model: yaklaşık 488 MB",13,false);
        Button installWhisper=button(models,"Whisper modelini indir");
        installWhisper.setOnClickListener(v->{
            new android.app.AlertDialog.Builder(this)
                .setTitle("Whisper modelini indir")
                .setMessage("Yaklaşık 488 MB indirilecek. Yalnızca bir kez gerekli. Ses kayıtların sunucuya gönderilmez. Wi-Fi önerilir.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("İndir",(d,w)->{
                    installWhisper.setEnabled(false);
                    AiModelStore.fetch(this,AiModelStore.WHISPER,(message,done)->{
                        whisperState.setText(message);
                        if(done)installWhisper.setEnabled(false);
                        else if(message.startsWith("Model hatası"))installWhisper.setEnabled(true);
                    });
                }).show();
        });
        text(models,"2. Günlük özet · Model seçimi",17,true);
        qwenState=text(models,"Önerilen: Qwen3-4B (2,5 GB, daha fazla RAM ve işlemci yükü).",13,false);
        RadioGroup aiChoices=new RadioGroup(this);
        aiChoices.setOrientation(RadioGroup.VERTICAL);
        RadioButton balanced=new RadioButton(this),strong=new RadioButton(this);
        balanced.setText("Qwen3-1.7B · Dengeli (1,28 GB)");
        strong.setText("Qwen3-4B · Ayrıntılı (2,5 GB)");
        balanced.setTextColor(WHITE);strong.setTextColor(WHITE);
        balanced.setId(View.generateViewId());strong.setId(View.generateViewId());
        aiChoices.addView(balanced);aiChoices.addView(strong);
        models.addView(aiChoices);
        Prefs p=new Prefs(this);
        aiChoices.check(AiModelStore.QWEN4.equals(p.aiModel())?strong.getId():balanced.getId());
        Button installQwen=button(models,"Seçilen Qwen3 modelini indir");
        aiChoices.setOnCheckedChangeListener((group,checked)->{
            String choice=checked==strong.getId()?AiModelStore.QWEN4:AiModelStore.QWEN;
            p.aiModel(choice);
            boolean ready=AiModelStore.ready(this,choice);
            qwenState.setText(ready?"Seçilen model hazır ✓":
                AiModelStore.QWEN4.equals(choice)?
                "Qwen3-4B · 2,5 GB · daha ayrıntılı, daha yüksek pil/RAM":
                "Qwen3-1.7B · 1,28 GB · daha düşük pil/RAM");
            installQwen.setEnabled(!ready);
        });
        installQwen.setOnClickListener(v->{
            final String selected=p.aiModel();
            final String size=AiModelStore.QWEN4.equals(selected)?"2,5 GB":"1,28 GB";
            new android.app.AlertDialog.Builder(this)
                .setTitle("Yerel özet modelini indir")
                .setMessage("Seçilen model "+size+". Wi-Fi, boş alan ve şarj önerilir. "+
                    "Kurulumdan sonra özetleme cihazda çevrimdışı gerçekleşir.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("İndir",(d,w)->{
                    installQwen.setEnabled(false);
                    AiModelStore.fetch(this,selected,(message,done)->{
                        qwenState.setText(message);
                        if(done)installQwen.setEnabled(false);
                        else if(message.startsWith("Model hatası"))installQwen.setEnabled(true);
                    });
                }).show();
        });
        installWhisper.setEnabled(!AiModelStore.ready(this,AiModelStore.WHISPER));
        installQwen.setEnabled(!AiModelStore.ready(this,p.aiModel()));
        if(AiModelStore.ready(this,AiModelStore.WHISPER))
            whisperState.setText("Whisper modeli hazır ✓");
        if(AiModelStore.ready(this,p.aiModel()))
            qwenState.setText("Seçilen Qwen3 modeli hazır ✓");
        LinearLayout instructions=card();
        text(instructions,"Yapay zeka neye dikkat etsin?",18,true);
        text(instructions,"Örneğin ruhsat, abonelik, belediye görüşmeleri, kararlar, "+
            "kişiler veya belirli bir proje. Talimatın sonraki analizlerde uygulanır.",13,false);
        AnalysisPreferences profile=new AnalysisPreferences(this);
        EditText focus=new EditText(this);
        focus.setText(profile.focus());
        focus.setTextColor(WHITE);
        focus.setHintTextColor(MUTED);
        focus.setTextSize(14);
        focus.setMinLines(4);focus.setMaxLines(9);
        focus.setHint("Örn. Alınan kararları, teslim tarihlerini ve sorumluları ayrıca çıkar.");
        instructions.addView(focus);
        Button saveFocus=button(instructions,"Analiz tercihimi kaydet");
        saveFocus.setOnClickListener(v->{
            profile.focus(focus.getText().toString());
            Toast.makeText(this,"Yapay zeka analiz tercihi kaydedildi.",Toast.LENGTH_SHORT).show();
        });
        text(instructions,"Önemli: Yapay zeka kesinleşmeyen işleri karar gibi göstermemeli. "+
            "Her maddede varsa ses kaydının kaynak saati yer alacak.",12,false);

        LinearLayout analyze=card();
        text(analyze,"Günlük rapor",19,true);
        text(analyze,"İki model de kuruluysa mesai kayıtları telefon şarjdayken otomatik "
            +"işleme alınır. Mevcut bir ses kaydını da elle seçebilirsin.",13,false);
        Button select=button(analyze,"Kaydedilmiş sesi seç ve özetle");
        select.setOnClickListener(v->{
            if(!AiModelStore.ready(this,AiModelStore.WHISPER)||
               !AiModelStore.ready(this,new Prefs(this).aiModel())){
                Toast.makeText(this,"Önce iki yerel modeli indir.",Toast.LENGTH_LONG).show();return;
            }
            if(new Prefs(this).folder()==null){
                Toast.makeText(this,"Önce kayıt klasörü seç.",Toast.LENGTH_LONG).show();return;
            }
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,PICK);
        });
        status=text(analyze,"Rapor kuyruğu kontrol ediliyor…",13,false);
        Button topics=button(analyze,"Konu konu özet tablosunu aç");
        topics.setOnClickListener(v->startActivity(new Intent(this,ReportTableActivity.class)));
        Button notes=button(analyze,"Notlarım • Raporu düzenle ve ekle");
        notes.setOnClickListener(v->{
            Intent openNotes=new Intent(this,UserNotesActivity.class);
            openNotes.putExtra("openLatestReport",true);
            startActivity(openNotes);
        });
                Button refresh=button(analyze,"İşlem durumunu yenile");
        refresh.setOnClickListener(v->refreshStatus());
        text(analyze,"Uzun seslerin çözülmesi ve Qwen3 özetlemesi saatler sürebilir. "
            +"Aşırı ısınma veya pil sorunu oluşursa işlemi durdurabilirsin.",12,false);
        Button cancel=button(analyze,"Bekleyen özetlemeleri iptal et");
        cancel.setOnClickListener(v->{
            WorkManager.getInstance(this).cancelAllWorkByTag("gunizi-local-summary");
            status.setText("Özetleme iptal istendi. Kayıt dosyaları korunur.");
        });
        refreshStatus();
    }
    private void refreshStatus(){
        android.content.SharedPreferences prefs=getSharedPreferences("gunizi",MODE_PRIVATE);
        String err=prefs.getString("summaryError",""),last=prefs.getString("summaryLast","");
        if(!err.isEmpty())status.setText("Son hata: "+err);
        else if(!last.isEmpty())status.setText(last);
        else status.setText("Henüz tamamlanmış rapor yok.");
        new Thread(()->{
            try{
                List<WorkInfo> list=WorkManager.getInstance(this)
                    .getWorkInfosByTag("gunizi-local-summary").get();
                if(list==null||list.isEmpty())return;
                WorkInfo current=list.get(list.size()-1);
                if(current.getState()==WorkInfo.State.RUNNING||
                    current.getState()==WorkInfo.State.ENQUEUED){
                    String stage=current.getProgress().getString("stage");
                    runOnUiThread(()->status.setText(current.getState()==WorkInfo.State.ENQUEUED?
                        "Rapor uygun şarj/pil koşullarını bekliyor.":
                        stage==null?"Yerel yapay zeka çalışıyor…":stage));
                }
            }catch(Exception ignored){}
        },"GunIzi-ReportStatus").start();
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
        String name="Secilen_Kayit.ogg";
        try(Cursor cur=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},
            null,null,null)){
            if(cur!=null&&cur.moveToFirst())name=cur.getString(0);
        }catch(Exception ignored){}
        DailySummaryWorker.queue(this,uri,name,false);
        status.setText("Yerel yapay zeka için iş sırasına alındı.");
    }
}
