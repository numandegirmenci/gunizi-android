package com.gunizi.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Notes-first local workspace. Existing note files and sharing remain compatible. */
public final class UserNotesActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private EditText title,body,search;
    private LinearLayout saved,editor;
    private TextView state,count;
    private ScrollView scroll;
    private String noteId=null,originalTitle="",originalBody="";
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private TextView label(LinearLayout parent,String value,int size,boolean strong){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);
        t.setTextColor(strong?WHITE:MUTED);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(t);return t;
    }
    private LinearLayout column(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(shape(PANEL));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(12);parent.addView(c,p);return c;
    }
    private Button button(String name,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);b.setTextSize(13);
        b.setTextColor(primary?BG:WHITE);
        b.setBackground(shape(primary?CYAN:0xff293a50));return b;
    }
    private void buttonInRow(LinearLayout row,Button b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);
        p.rightMargin=dp(7);row.addView(b,p);
    }
    private boolean dirty(){
        return !title.getText().toString().equals(originalTitle)||
            !body.getText().toString().equals(originalBody);
    }
    private void protect(Runnable action){
        if(editor.getVisibility()!=View.VISIBLE||!dirty()){action.run();return;}
        new AlertDialog.Builder(this).setTitle("Kaydedilmemiş değişiklikler")
            .setMessage("Mevcut notundaki değişiklikleri kaydetmedin. Devam edersen bu değişiklikler kaybolur.")
            .setNegativeButton("Vazgeç",null)
            .setPositiveButton("Devam et",(d,w)->action.run()).show();
    }
    private void edit(String id,String titleText,String content,String message){
        noteId=id;
        title.setText(titleText);body.setText(content);
        originalTitle=titleText;originalBody=content;
        state.setText(message);editor.setVisibility(View.VISIBLE);
        scroll.post(()->scroll.smoothScrollTo(0,editor.getTop()));
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(18),dp(16),dp(22));scroll.addView(root);setContentView(scroll);

        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);root.addView(toolbar);
        TextView heading=label(toolbar,"Notlarım",24,true);
        heading.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("+ Yeni not",true);
        toolbar.addView(add,new LinearLayout.LayoutParams(dp(120),dp(43)));
        add.setOnClickListener(v->protect(()->edit(null,"","","Yeni not oluşturuluyor.")));
        label(root,"Notlarını bul, düzenle veya paylaş. Hepsi cihazında saklanır.",12,false);

        LinearLayout shortcuts=column(root);
        LinearLayout shortcutRow=new LinearLayout(this);shortcuts.addView(shortcutRow);
        Button fromReport=button("Son rapordan oluştur",false);
        buttonInRow(shortcutRow,fromReport);
        fromReport.setOnClickListener(v->protect(this::loadLatest));
        Button shareInfo=button("Notları ara ↓",false);
        buttonInRow(shortcutRow,shareInfo);
        shareInfo.setOnClickListener(v->search.requestFocus());

        editor=column(root);editor.setVisibility(View.GONE);
        label(editor,"Not düzenleyici",17,true);
        title=new EditText(this);title.setSingleLine(true);title.setTextColor(WHITE);
        title.setHintTextColor(MUTED);title.setTextSize(16);
        title.setHint("Başlık: Örn. Mağaza açılışları");
        editor.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        body=new EditText(this);body.setTextColor(WHITE);body.setHintTextColor(MUTED);
        body.setTextSize(14);body.setGravity(Gravity.TOP);body.setMinLines(5);
        body.setHint("Kararlar, yapılacaklar, sorumlular…");
        LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,dp(180));
        bodyLp.bottomMargin=dp(8);editor.addView(body,bodyLp);
        LinearLayout mainActions=new LinearLayout(this);editor.addView(mainActions);
        Button save=button("Kaydet",true);buttonInRow(mainActions,save);
        save.setOnClickListener(v->save());
        Button share=button("Paylaş",false);buttonInRow(mainActions,share);
        share.setOnClickListener(v->share());
        Button extra=button("Diğer işlemler ···",false);
        editor.addView(extra,new LinearLayout.LayoutParams(-1,dp(44)));
        extra.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(this,extra);
            menu.getMenu().add("TXT olarak kaydet").setOnMenuItemClickListener(item->{exportFile();return true;});
            menu.getMenu().add("Son raporla değiştir").setOnMenuItemClickListener(item->{
                protect(this::loadLatest);return true;
            });
            menu.getMenu().add("Düzenlemeyi kapat").setOnMenuItemClickListener(item->{
                protect(()->editor.setVisibility(View.GONE));return true;
            });
            menu.show();
        });
        state=label(editor,"Düzenlemelerini kaydetmeyi unutma.",12,false);

        LinearLayout listPanel=column(root);
        LinearLayout listHeader=new LinearLayout(this);listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);listPanel.addView(listHeader);
        TextView listTitle=label(listHeader,"Kayıtlı notlar",17,true);
        listTitle.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        count=label(listHeader,"",12,false);
        search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
        search.setTextColor(WHITE);search.setHintTextColor(MUTED);
        search.setHint("Notlarında ara…");
        listPanel.addView(search,new LinearLayout.LayoutParams(-1,dp(47)));
        saved=new LinearLayout(this);saved.setOrientation(LinearLayout.VERTICAL);
        listPanel.addView(saved);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){showNotes();}
            @Override public void afterTextChanged(Editable e){}
        });
        showNotes();
        if(getIntent().getBooleanExtra("openLatestReport",false))loadLatest();
    }
    private void loadLatest(){
        try{
            String report=UserNotesStore.latestDraft(this);
            if(report.isEmpty()){
                Toast.makeText(this,"Önce bir kayıt için yapay zeka raporu oluştur.",Toast.LENGTH_LONG).show();
                return;
            }
            edit(null,UserNotesStore.title(report),UserNotesStore.body(report),
                "Rapor taslağı getirildi. Düzenleyip kaydedebilirsin.");
        }catch(Exception ex){
            Toast.makeText(this,"Rapor okunamadı: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void save(){
        try{
            noteId=UserNotesStore.save(this,noteId,title.getText().toString(),
                body.getText().toString());
            originalTitle=title.getText().toString();originalBody=body.getText().toString();
            state.setText("Kaydedildi ✓");
            showNotes();
            Toast.makeText(this,"Not kaydedildi",Toast.LENGTH_SHORT).show();
        }catch(Exception e){state.setText(e.getMessage());}
    }
    private void share(){
        if(title.length()==0||body.length()==0){
            state.setText("Paylaşmak için başlık ve içerik gir.");return;
        }
        Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,title.getText().toString().trim());
        send.putExtra(Intent.EXTRA_TEXT,title.getText().toString().trim()+
            "\n\n"+body.getText().toString());
        try{
            startActivity(Intent.createChooser(send,"Notu paylaş"));
        }catch(Exception e){state.setText("Paylaşım açılamadı: "+e.getMessage());}
    }
    private void exportFile(){
        if(body.length()==0){state.setText("Önce not içeriği gir.");return;}
        String titleText=title.getText().toString(),bodyText=body.getText().toString();
        new Thread(()->{
            String result;
            try{
                if(new Prefs(this).folder()==null)
                    throw new java.io.IOException("Önce Ayarlar'dan kayıt klasörü seç.");
                ExportManager.writeText(this,new Prefs(this).folder(),
                    titleText+"\n\n"+bodyText,
                    "GunIzi_Not_"+System.currentTimeMillis()+".txt");
                result="TXT dosyası kaydedildi.";
            }catch(Exception e){result="Dışa aktarma hatası: "+e.getMessage();}
            String finalResult=result;
            runOnUiThread(()->state.setText(finalResult));
        },"GunIzi-NoteExport").start();
    }
    private void showNotes(){
        if(saved==null)return;
        saved.removeAllViews();
        List<String> ids=UserNotesStore.list(this);
        count.setText(ids.size()+" not");
        String filter=search.getText().toString().trim().toLowerCase(new Locale("tr","TR"));
        int visible=0;
        for(String id:ids){
            try{
                String note=UserNotesStore.readNote(this,id);
                String noteTitle=UserNotesStore.title(note),noteBody=UserNotesStore.body(note);
                if(!filter.isEmpty()&&!note.toLowerCase(new Locale("tr","TR")).contains(filter))continue;
                visible++;
                LinearLayout row=new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(9),dp(10),dp(3),dp(10));
                LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);
                rowLp.bottomMargin=dp(7);row.setBackground(shape(0xff213148));
                saved.addView(row,rowLp);
                LinearLayout details=new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                row.addView(details,new LinearLayout.LayoutParams(0,-2,1));
                TextView head=label(details,noteTitle,15,true);head.setMaxLines(1);
                head.setEllipsize(android.text.TextUtils.TruncateAt.END);
                String preview=noteBody.replace('\n',' ').trim();
                if(preview.length()>100)preview=preview.substring(0,100)+"…";
                TextView snippet=label(details,preview,12,false);snippet.setMaxLines(2);
                try{
                    long created=Long.parseLong(id.substring(0,13));
                    label(details,new SimpleDateFormat("dd MMM yyyy",new Locale("tr","TR"))
                        .format(new Date(created)),11,false);
                }catch(Exception ignored){}
                details.setOnClickListener(v->protect(()->edit(id,noteTitle,noteBody,
                    "Kayıtlı not açıldı. Değişiklikleri kaydedebilirsin.")));
                Button more=button("···",false);
                row.addView(more,new LinearLayout.LayoutParams(dp(47),dp(42)));
                more.setOnClickListener(v->{
                    PopupMenu menu=new PopupMenu(this,more);
                    menu.getMenu().add("Notu aç").setOnMenuItemClickListener(item->{
                        protect(()->edit(id,noteTitle,noteBody,"Kayıtlı not açıldı."));return true;
                    });
                    menu.getMenu().add("Notu sil").setOnMenuItemClickListener(item->{
                        new AlertDialog.Builder(this).setTitle("Not silinsin mi?")
                            .setMessage(noteTitle+" kalıcı olarak silinecek.")
                            .setNegativeButton("Vazgeç",null)
                            .setPositiveButton("Sil",(d,w)->{
                                try{
                                    UserNotesStore.delete(this,id);
                                    if(id.equals(noteId)){
                                        editor.setVisibility(View.GONE);
                                        noteId=null;originalTitle="";originalBody="";
                                    }
                                    showNotes();
                                }catch(Exception e){
                                    Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                                }
                            }).show();
                        return true;
                    });
                    menu.show();
                });
            }catch(Exception ignored){}
        }
        if(visible==0)
            label(saved,filter.isEmpty()?
                "Henüz kayıtlı notun yok. Yeni not oluştur veya son raporunu ekle.":
                "Aramana uygun not bulunamadı.",13,false);
    }
    @Override public void onBackPressed(){
        protect(()->super.onBackPressed());
    }
}
