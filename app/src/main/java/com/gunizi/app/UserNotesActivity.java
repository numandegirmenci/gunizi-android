package com.gunizi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;

/** Editable, explicitly saved user notes. Share sheet can send to Samsung Notes. */
public final class UserNotesActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,MUTED=0xffa2b4c9;
    private EditText title,body;
    private LinearLayout saved;
    private TextView state;
    private String noteId=null;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void gap(LinearLayout root,int n){root.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private TextView label(LinearLayout c,String text,int size,boolean strong){
        TextView t=new TextView(this);
        t.setText(text);t.setTextSize(size);t.setTextColor(strong?WHITE:MUTED);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);c.addView(t);return t;
    }
    private LinearLayout panel(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15),dp(15),dp(15),dp(15));
        GradientDrawable bg=new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(15));c.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        parent.addView(c,p);return c;
    }
    private Button button(LinearLayout p,String text){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);p.addView(b);return b;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(20),dp(16),dp(22));sc.addView(root);
        setContentView(sc);
        label(root,"Günİzi • Notlarım",25,true);gap(root,9);
        label(root,"Yapay zeka raporunu kendi notlarına ekle, düzenle ve istersen "
            +"Samsung Notes ile paylaş. Hiçbir not otomatik olarak başka uygulamaya gönderilmez.",13,false);
        gap(root,15);
        LinearLayout editor=panel(root);
        label(editor,"Not başlığı",15,true);gap(editor,6);
        title=new EditText(this);title.setSingleLine(true);title.setTextColor(WHITE);
        title.setHintTextColor(MUTED);title.setHint("Örn. Günlük iş özeti");
        title.setTextSize(16);editor.addView(title);
        gap(editor,12);
        label(editor,"Not içeriği",15,true);gap(editor,6);
        body=new EditText(this);body.setTextColor(WHITE);body.setHintTextColor(MUTED);
        body.setTextSize(14);body.setGravity(android.view.Gravity.TOP);
        body.setMinLines(9);body.setHint("Rapordan ekle veya kendi notunu yaz...");
        editor.addView(body,new LinearLayout.LayoutParams(-1,dp(300)));
        gap(editor,8);
        Button load=button(editor,"Son yapay zeka raporunu not alanına getir");
        load.setOnClickListener(v->loadLatest());
        Button save=button(editor,"Notlarıma kaydet");
        save.setOnClickListener(v->save());
        Button share=button(editor,"Samsung Notes / diğer uygulamaya aktar");
        share.setOnClickListener(v->share());
        Button export=button(editor,"Seçtiğim klasöre TXT kaydet");
        export.setOnClickListener(v->exportFile());
        state=label(editor,"Notlar sadece cihazında saklanır.",13,false);
        LinearLayout history=panel(root);
        label(history,"Kaydedilmiş notlar",18,true);
        gap(history,10);
        saved=new LinearLayout(this);saved.setOrientation(LinearLayout.VERTICAL);
        history.addView(saved);
        showNotes();
        if(getIntent().getBooleanExtra("openLatestReport",false))loadLatest();
    }
    private void loadLatest(){
        try{
            String report=UserNotesStore.latestDraft(this);
            if(report.isEmpty()){state.setText("Önce bir ses kaydını analiz ederek rapor oluştur.");return;}
            if(body.length()>0){
                new AlertDialog.Builder(this)
                    .setTitle("Not içeriğini değiştir")
                    .setMessage("Düzenleme alanında yazdıkların yeni raporla değiştirilecek. Devam edilsin mi?")
                    .setNegativeButton("Vazgeç",null)
                    .setPositiveButton("Getir",(d,w)->setContent(report)).show();
            }else setContent(report);
        }catch(Exception ex){state.setText("Rapor okunamadı: "+ex.getMessage());}
    }
    private void setContent(String report){
        title.setText(UserNotesStore.title(report));
        body.setText(UserNotesStore.body(report));noteId=null;
        state.setText("Rapor yüklendi. İncele ve Notlarıma kaydet.");
    }
    private void save(){
        try{
            noteId=UserNotesStore.save(this,noteId,title.getText().toString(),
                body.getText().toString());
            state.setText("Not kaydedildi. Üzerinde tekrar düzenleme yapabilirsin.");
            showNotes();
        }catch(Exception e){state.setText(e.getMessage());}
    }
    private void share(){
        if(title.length()==0||body.length()==0){state.setText("Önce not başlığını ve içeriğini doldur.");return;}
        String content=title.getText().toString().trim()+"\n\n"+body.getText().toString();
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,title.getText().toString().trim());
        send.putExtra(Intent.EXTRA_TEXT,content);
        try{
            startActivity(Intent.createChooser(send,"Notları Samsung Notes veya başka uygulamaya gönder"));
        }catch(Exception e){state.setText("Paylaşım açılamadı: "+e.getMessage());}
    }
    private void exportFile(){
        if(body.length()==0){state.setText("Önce not içeriğini doldur.");return;}
        new Thread(()->{
            String result;
            try{
                if(new Prefs(this).folder()==null)throw new java.io.IOException("Önce Ayarlar'dan kayıt klasörü seç.");
                String text=title.getText().toString()+"\n\n"+body.getText().toString();
                ExportManager.writeText(this,new Prefs(this).folder(),text,
                    "GunIzi_Not_"+System.currentTimeMillis()+".txt");
                result="Not TXT olarak klasöre kaydedildi.";
            }catch(Exception e){result="Dışa aktarma hatası: "+e.getMessage();}
            String info=result;
            runOnUiThread(()->state.setText(info));
        },"GunIzi-NoteExport").start();
    }
    private void showNotes(){
        saved.removeAllViews();
        List<String> ids=UserNotesStore.list(this);
        if(ids.isEmpty()){label(saved,"Henüz kaydedilmiş not yok.",13,false);return;}
        for(String id:ids){
            try{
                String note=UserNotesStore.readNote(this,id);
                Button item=button(saved,UserNotesStore.title(note));
                item.setOnClickListener(v->{
                    if(body.length()>0){
                        new AlertDialog.Builder(this)
                            .setTitle("Kaydedilmiş not açılsın mı?")
                            .setMessage("Kaydedilmemiş değişiklikler kaybolabilir.")
                            .setNegativeButton("Vazgeç",null)
                            .setPositiveButton("Aç",(d,w)->open(id,note)).show();
                    }else open(id,note);
                });
            }catch(Exception ignored){}
        }
    }
    private void open(String id,String note){
        noteId=id;title.setText(UserNotesStore.title(note));
        body.setText(UserNotesStore.body(note));
        state.setText("Kayıtlı not açıldı; değişiklikleri kaydedebilirsin.");
    }
}
