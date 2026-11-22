package com.gunizi.app;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Virtualized transcript timeline. Reads only appended bytes at each poll.
 * A nine-hour transcript never rebuilds a single giant TextView.
 */
public final class TranscriptActivity extends Activity {
    private static final int BG=0xff090e1b,WHITE=0xfff0f7ff,MUTED=0xffa2b4c9,TEAL=0xff38d4ca;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Line> rows=new ArrayList<>();
    private final ByteArrayOutputStream partial=new ByteArrayOutputStream(8192);
    private File currentFile;
    private long offset;
    private String selectedSource;
    private TextView status;
    private ListView timeline;
    private Button follow,share;
    private boolean followLatest=true;
    private BaseAdapter adapter;
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){refresh();handler.postDelayed(this,1700);}
    };
    private static final class Line {
        final String timestamp,spoken;
        final long atMs;
        Line(String stamp,String words,long ms){timestamp=stamp;spoken=words;atMs=ms;}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(10));return d;
    }
    private TextView label(LinearLayout holder,String title,int size,boolean strong){
        TextView v=new TextView(this);v.setText(title);v.setTextSize(size);
        v.setTextColor(strong?WHITE:MUTED);
        if(strong)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        holder.addView(v);return v;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);
        root.setPadding(dp(13),dp(15),dp(13),dp(10));
        root.setBackgroundColor(BG);root.setOrientation(LinearLayout.VERTICAL);
        label(root,"Konuşma akışı",23,true);
        status=label(root,"Kaynak kontrol ediliyor…",12,false);
        LinearLayout actions=new LinearLayout(this);
        LinearLayout.LayoutParams buttons=new LinearLayout.LayoutParams(-1,dp(45));
        buttons.topMargin=dp(12);buttons.bottomMargin=dp(8);root.addView(actions,buttons);
        follow=new Button(this);follow.setText("Canlı takip: açık");
        follow.setTextSize(12);follow.setAllCaps(false);
        share=new Button(this);share.setText("Metni paylaş");share.setAllCaps(false);
        share.setTextSize(12);
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-1,1);
        half.rightMargin=dp(6);actions.addView(follow,half);
        actions.addView(share,new LinearLayout.LayoutParams(0,-1,1));
        follow.setOnClickListener(v->{
            followLatest=!followLatest;
            follow.setText(followLatest?"Canlı takip: açık":"Canlı takip: kapalı");
            if(followLatest&&adapter.getCount()>0)
                timeline.setSelection(adapter.getCount()-1);
        });
        share.setOnClickListener(v->shareTranscript());
        timeline=new ListView(this);
        timeline.setDividerHeight(dp(6));timeline.setCacheColorHint(BG);
        timeline.setBackgroundColor(BG);
        adapter=new BaseAdapter(){
            @Override public int getCount(){return rows.size();}
            @Override public Object getItem(int position){return rows.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int position,View existing,ViewGroup parent){
                LinearLayout block;TextView stamp,words;
                if(existing instanceof LinearLayout){
                    block=(LinearLayout)existing;
                    stamp=(TextView)block.getChildAt(0);
                    words=(TextView)block.getChildAt(1);
                }else{
                    block=new LinearLayout(TranscriptActivity.this);
                    block.setOrientation(LinearLayout.VERTICAL);
                    block.setPadding(dp(12),dp(9),dp(12),dp(10));
                    block.setBackground(bg(0xff172031));
                    stamp=label(block,"",11,true);stamp.setTextColor(TEAL);
                    words=label(block,"",14,false);words.setTextColor(WHITE);
                    words.setTextIsSelectable(true);
                }
                Line line=rows.get(position);
                stamp.setText(line.timestamp.isEmpty()?"Zaman işareti yok":line.timestamp);
                words.setText(line.spoken);
                return block;
            }
        };
        timeline.setAdapter(adapter);
        timeline.setOnItemClickListener((parent,view,position,id)->{
            Line row=rows.get(position);
            if(row.atMs<0||selectedSource==null){
                Toast.makeText(this,"Bu bölümün güvenilir ses zamanı yok.",Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this,AudioEvidenceActivity.class)
                .putExtra(AudioEvidenceActivity.EXTRA_SOURCE,selectedSource)
                .putExtra(AudioEvidenceActivity.EXTRA_START,row.atMs));
        });
        timeline.setOnScrollListener(new android.widget.AbsListView.OnScrollListener(){
            @Override public void onScrollStateChanged(android.widget.AbsListView v,int state){
                if(state==SCROLL_STATE_TOUCH_SCROLL&&timeline.getLastVisiblePosition()<
                    rows.size()-2){
                    followLatest=false;follow.setText("Canlı takip: kapalı");
                }
            }
            @Override public void onScroll(android.widget.AbsListView v,int first,int count,int total){}
        });
        root.addView(timeline,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        refresh();
    }
    private File safeFile(String path){
        if(path==null||path.isEmpty())return null;
        try{
            File allowed=new File(getNoBackupFilesDir(),"transcripts").getCanonicalFile();
            File f=new File(path).getCanonicalFile();
            if(!f.getPath().startsWith(allowed.getPath()+File.separator)||
                !f.isFile()||f.length()>32L*1024*1024)return null;
            return f;
        }catch(Exception e){return null;}
    }
    private void refresh(){
        SharedPreferences p=getSharedPreferences("gunizi",MODE_PRIVATE);
        selectedSource=p.getString("aiSelectedSourceUri",null);
        String sourceName=p.getString("aiSelectedSourceName","Ses kaydı");
        String liveUri=p.getString("analysisLiveSourceUri","");
        String savedUri=p.getString("transcriptSourceUri","");
        boolean sameLive=selectedSource!=null&&selectedSource.equals(liveUri);
        boolean sameSaved=selectedSource!=null&&selectedSource.equals(savedUri);
        long start=p.getLong("analysisStartedAt",0),done=p.getLong("analysisCompletedAt",0);
        boolean live=sameLive&&start>0&&(done==0||done<start);
        File candidate=live?safeFile(p.getString("analysisLivePath","")):
            sameSaved?safeFile(p.getString("transcriptInternal","")):
                sameLive?safeFile(p.getString("analysisLivePath","")):null;
        if(candidate==null){
            if(currentFile!=null){currentFile=null;rows.clear();partial.reset();offset=0;adapter.notifyDataSetChanged();}
            status.setText(sourceName+"\nHenüz konuşma dökümü bulunamadı.");
            share.setEnabled(false);return;
        }
        if(!candidate.equals(currentFile)){
            currentFile=candidate;offset=0;partial.reset();rows.clear();
            adapter.notifyDataSetChanged();
        }
        try{
            long length=candidate.length();
            if(length<offset){offset=0;partial.reset();rows.clear();}
            if(length>offset){
                // Bound UI work per poll; remaining bytes are read on the next update.
                try(RandomAccessFile file=new RandomAccessFile(candidate,"r")){
                    file.seek(offset);
                    byte[] block=new byte[32768];
                    long end=Math.min(length,offset+512L*1024L);
                    int added=0;
                    while(offset<end){
                        int n=file.read(block,0,(int)Math.min(block.length,end-offset));
                        if(n<0)break;
                        offset+=n;
                        for(int j=0;j<n;j++){
                            if(block[j]=='\n'){
                                String line=partial.toString("UTF-8");
                                partial.reset();
                                add(line);added++;
                            }else if(partial.size()<8192)partial.write(block[j]);
                        }
                    }
                    if(added>0){
                        adapter.notifyDataSetChanged();
                        if(followLatest)timeline.setSelection(rows.size()-1);
                    }
                }
            }
            status.setText(sourceName+"\n"+(live?"Analiz sürüyor":"Tamamlanan konuşmalar")+
                " · "+rows.size()+" satır · "+
                (offset<length?"Metin yükleniyor":"Güncel"));
            share.setEnabled(!rows.isEmpty());
        }catch(Exception e){status.setText("Konuşma okunamadı: "+e.getClass().getSimpleName());}
    }
    private void add(String text){
        String clean=text.trim();if(clean.isEmpty()||clean.contains("[[GUNIZI_SESSIZ_BOLUM]]"))return;
        int end=clean.indexOf(']');
        String stamp=clean.startsWith("[")&&end>0?clean.substring(1,end):"";
        long time=TopicReportRenderer.timestampMs(stamp);
        String spoken=stamp.isEmpty()?clean:clean.substring(end+1).trim();
        if(!spoken.isEmpty())rows.add(new Line(stamp,spoken,time));
    }
    private void shareTranscript(){
        if(currentFile==null||!currentFile.isFile())return;
        File source=currentFile;
        new Thread(()->{
            try{
                File dir=new File(getCacheDir(),"shared_transcripts");
                if(!dir.exists()&&!dir.mkdirs())throw new IOException("Paylaşım alanı açılamadı");
                File[] old=dir.listFiles();
                if(old!=null)for(File f:old)
                    if(System.currentTimeMillis()-f.lastModified()>86400_000L)f.delete();
                File out=new File(dir,"GunIzi_Konusma.txt");
                try(InputStream in=new FileInputStream(source);
                    OutputStream dest=new FileOutputStream(out)){
                    byte[] buf=new byte[32*1024];int n;
                    while((n=in.read(buf))!=-1)dest.write(buf,0,n);
                }
                Uri content=FileProvider.getUriForFile(this,getPackageName()+
                    ".updatefiles",out);
                runOnUiThread(()->{
                    Intent send=new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_STREAM,content);
                    send.setClipData(ClipData.newUri(getContentResolver(),"Günİzi döküm",content));
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(send,"Konuşma dökümünü paylaş"));
                });
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,
                    "Paylaşım hazırlanamadı: "+e.getMessage(),Toast.LENGTH_LONG).show());
            }
        },"GunIzi-TranscriptShare").start();
    }
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(heartbeat);handler.post(heartbeat);}
    @Override protected void onPause(){handler.removeCallbacks(heartbeat);super.onPause();}
}