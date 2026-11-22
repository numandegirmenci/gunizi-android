package com.gunizi.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Read a time-stamped local transcript during analysis, not just afterwards. */
public final class TranscriptActivity extends Activity {
    private static final int BG=0xff090e1b,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,TEAL=0xff38d4ca;
    private TextView body,state,title;
    private ScrollView scroll;
    private Button share,follow;
    private String transcript="", source="";
    private boolean followLatest=true;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){
        @Override public void run(){update();handler.postDelayed(this,2200);}
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable box(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(13));return d;
    }
    private TextView text(LinearLayout parent,String content,int size,boolean bold){
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);
        t.setTextColor(bold?WHITE:MUTED);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        parent.addView(t);return t;
    }
    private Button button(String label){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);
        b.setAllCaps(false);b.setTextColor(WHITE);
        b.setBackground(box(0xff27384b));return b;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15),dp(17),dp(15),dp(12));root.setBackgroundColor(BG);
        title=text(root,"Konuşma akışı",23,true);
        state=text(root,"Kayıt kontrol ediliyor…",12,false);
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams=new LinearLayout.LayoutParams(-1,dp(44));
        actionParams.topMargin=dp(10);actionParams.bottomMargin=dp(10);
        root.addView(actions,actionParams);
        follow=button("Canlı takip: açık");
        share=button("Metni paylaş");
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-1,1);
        half.rightMargin=dp(7);actions.addView(follow,half);
        actions.addView(share,new LinearLayout.LayoutParams(0,-1,1));
        follow.setOnClickListener(v->{
            followLatest=!followLatest;
            follow.setText(followLatest?"Canlı takip: açık":"Canlı takip: kapalı");
            if(followLatest)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        });
        share.setOnClickListener(v->{
            if(transcript.trim().isEmpty())return;
            Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT,transcript);
            startActivity(Intent.createChooser(intent,"Konuşma metnini paylaş"));
        });
        scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout container=new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(14),dp(14),dp(14),dp(14));
        container.setBackground(box(0xff172031));
        body=text(container,"Henüz konuşma metni yok.",14,false);
        body.setTextIsSelectable(true);body.setLineSpacing(dp(5),1f);
        body.setTextColor(WHITE);
        scroll.addView(container);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        update();
    }
    private String readSaved(String selected){
        SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        String stored=p.getString("transcriptInternal",null);
        String associated=p.getString("transcriptSourceUri",null);
        if(selected==null||!selected.equals(associated)||stored==null)return "";
        try{
            File allowed=new File(getNoBackupFilesDir(),"transcripts").getCanonicalFile();
            File file=new File(stored).getCanonicalFile();
            if(!file.getPath().startsWith(allowed.getPath()+File.separator)||
                !file.isFile()||file.length()>8L*1024*1024)return "";
            return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        }catch(Exception e){return "";}
    }
    private void update(){
        SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        String selected=p.getString("aiSelectedSourceUri",null);
        String name=p.getString("aiSelectedSourceName","Ses kaydı");
        String lastSource=p.getString("analysisLiveSourceUri","");
        boolean same=selected!=null&&selected.equals(lastSource);
        long now=p.getLong("analysisStartedAt",0),end=p.getLong("analysisCompletedAt",0);
        boolean active=same&&now>0&&(end==0||end<now);
        String saved=readSaved(selected);
        String live=LiveTranscriptStore.current(this,selected);
        String current=active?live:!saved.isEmpty()?saved:live;
        String status=current.isEmpty()?
            (active?"Analiz sürüyor. Konuşma tespit edilirse tamamlanan bölümler buraya eklenir.":
                "Bu kayıt için henüz konuşma metni bulunmuyor."):
            (active?"Canlı · Yeni konuşmalar bölüm tamamlandıkça aşağıya eklenir":
                "Tamamlanan konuşma dökümü");
        state.setText(name+"\n"+status);
        share.setEnabled(!current.trim().isEmpty());
        if(current.equals(transcript)&&name.equals(source))return;
        source=name;transcript=current;
        if(current.isEmpty()){
            body.setText("Henüz konuşma bulunamadı. Sessizlik ve ortam gürültüsü metne dönüştürülmeyebilir.");
            return;
        }
        SpannableStringBuilder styled=new SpannableStringBuilder();
        for(String line:current.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;
            int start=styled.length();
            styled.append(line.trim()).append("\n\n");
            int close=line.indexOf(']');
            if(line.startsWith("[")&&close>0){
                styled.setSpan(new ForegroundColorSpan(TEAL),start,
                    Math.min(start+close+1,styled.length()),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        body.setText(styled);
        if(followLatest)scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
    }
    @Override protected void onResume(){
        super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(refresh);super.onPause();
    }
}
