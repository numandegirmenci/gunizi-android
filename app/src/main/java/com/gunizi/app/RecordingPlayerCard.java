package com.gunizi.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.*;
import java.util.Locale;

/** Real recording playback with seeking; no fabricated waveform or mock progress. */
final class RecordingPlayerCard extends LinearLayout {
    private static final int WHITE=0xffe8f5ff,MUTED=0xffaabccc,TEAL=0xff39d6ca;
    private final TextView name,position,metadata;
    private final SeekBar seek;
    private final Button play;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Uri source;
    private MediaPlayer player;
    private boolean userSeeking;
    private long pendingSeek=-1;
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){
            if(player!=null){
                try{
                    int pos=player.getCurrentPosition(),total=player.getDuration();
                    if(!userSeeking){seek.setMax(Math.max(1000,total));seek.setProgress(pos);}
                    position.setText(time(pos)+" / "+time(total));
                    play.setText(player.isPlaying()?"Duraklat":"Dinle");
                }catch(IllegalStateException ignored){}
            }
            handler.postDelayed(this,450);
        }
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(){
        GradientDrawable d=new GradientDrawable();d.setColor(0xff122b36);d.setCornerRadius(dp(17));
        d.setStroke(dp(1),0xff2e5b61);return d;
    }
    RecordingPlayerCard(Context c){
        super(c);setOrientation(VERTICAL);setPadding(dp(16),dp(14),dp(16),dp(12));
        setBackground(surface());
        name=new TextView(c);name.setText("Henüz ses kaydı seçilmedi");
        name.setTextSize(16);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        name.setTextColor(WHITE);name.setSingleLine(false);
        addView(name,new LayoutParams(-1,-2));
        metadata=new TextView(c);metadata.setText("Kayıt seçerek başla");
        metadata.setTextSize(12);metadata.setTextColor(MUTED);
        LayoutParams mp=new LayoutParams(-1,-2);mp.topMargin=dp(5);addView(metadata,mp);
        seek=new SeekBar(c);seek.setEnabled(false);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(TEAL));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(TEAL));
        LayoutParams sp=new LayoutParams(-1,dp(42));sp.topMargin=dp(10);addView(seek,sp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar bar){userSeeking=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                userSeeking=false;
                try{if(player!=null)player.seekTo(bar.getProgress());}catch(Exception ignored){}
            }
            @Override public void onProgressChanged(SeekBar bar,int p,boolean fromUser){}
        });
        LinearLayout bottom=new LinearLayout(c);
        bottom.setOrientation(HORIZONTAL);bottom.setGravity(Gravity.CENTER_VERTICAL);
        position=new TextView(c);position.setText("00:00 / 00:00");position.setTextColor(MUTED);
        position.setTextSize(12);bottom.addView(position,new LayoutParams(0,-2,1));
        play=new Button(c);play.setAllCaps(false);play.setText("Dinle");
        play.setEnabled(false);play.setOnClickListener(v->toggle());
        bottom.addView(play,new LayoutParams(dp(110),dp(48)));
        addView(bottom,new LayoutParams(-1,-2));
    }
    void setSource(Uri uri,AiAudioPreflight.Info info){
        release();
        source=uri;pendingSeek=-1;
        name.setText(info.name);
        metadata.setText(info.description());
        position.setText("00:00 / "+time(info.durationMs));
        seek.setMax((int)Math.min(Integer.MAX_VALUE,Math.max(1000,info.durationMs)));
        seek.setProgress(0);seek.setEnabled(true);
        play.setEnabled(true);play.setText("Dinle");
    }
    void clear(){
        release();source=null;name.setText("Henüz ses kaydı seçilmedi");
        metadata.setText("Ses seçip konuşma metni veya özet oluştur");
        seek.setProgress(0);seek.setEnabled(false);play.setEnabled(false);
        position.setText("00:00 / 00:00");
    }
    private void toggle(){
        if(source==null)return;
        try{
            if(player==null){
                player=new MediaPlayer();
                player.setDataSource(getContext(),source);
                player.setOnPreparedListener(p->{
                    seek.setMax(Math.max(1000,p.getDuration()));
                    if(pendingSeek>=0){
                        p.seekTo((int)Math.min(Math.max(0,p.getDuration()-1),pendingSeek));
                        pendingSeek=-1;
                    }
                    p.start();
                    handler.removeCallbacks(heartbeat);handler.post(heartbeat);
                });
                player.setOnCompletionListener(p->{
                    play.setText("Yeniden dinle");
                });
                play.setText("Hazırlanıyor…");player.prepareAsync();
            }else if(player.isPlaying()){player.pause();play.setText("Devam et");}
            else {player.start();play.setText("Duraklat");}
        }catch(Exception ex){
            release();metadata.setText("Ses oynatılamadı: "+
                ex.getClass().getSimpleName());play.setText("Yeniden dene");
        }
    }
    void playAt(long ms){
        if(source==null)return;
        pendingSeek=Math.max(0,ms);
        if(player==null)toggle();
        else try{
            player.seekTo((int)Math.min(Integer.MAX_VALUE,pendingSeek));
            pendingSeek=-1;
            if(!player.isPlaying())player.start();
        }catch(Exception ignored){}
    }
    void seekTo(long ms){
        try{if(player!=null)player.seekTo((int)Math.min(Integer.MAX_VALUE,Math.max(0,ms)));}
        catch(Exception ignored){}
    }
    void release(){
        handler.removeCallbacks(heartbeat);
        if(player!=null){
            try{player.reset();player.release();}catch(Exception ignored){}
            player=null;
        }
    }
    private static String time(long milliseconds){
        long sec=Math.max(0,milliseconds/1000);
        return sec>=3600?
            String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60):
            String.format(Locale.ROOT,"%02d:%02d",sec/60,sec%60);
    }
}
