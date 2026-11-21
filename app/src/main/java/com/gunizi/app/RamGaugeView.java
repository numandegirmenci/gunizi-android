package com.gunizi.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Simple custom RAM history dial with actual buffered duration, no constant UI redraw in background. */
final class RamGaugeView extends View{
    private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int held=0,maximum=1;
    RamGaugeView(Context c){super(c);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(22);track.setStrokeCap(Paint.Cap.ROUND);track.setColor(0xff253145);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(22);arc.setStrokeCap(Paint.Cap.ROUND);arc.setColor(0xff29d7cb);
        text.setTextAlign(Paint.Align.CENTER);
    }
    void update(int seconds,int limit){held=Math.max(0,seconds);maximum=Math.max(1,limit);invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*0.37f;
        float d=getResources().getDisplayMetrics().density;
        track.setStrokeWidth(13*d);arc.setStrokeWidth(13*d);
        RectF b=new RectF(cx-r,cy-r,cx+r,cy+r);
        canvas.drawArc(b,135,270,false,track);
        canvas.drawArc(b,135,270*Math.min(1f,(float)held/maximum),false,arc);
        text.setColor(0xffd2fff9);text.setTextSize(32*d);text.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",
            held/3600,(held/60)%60,held%60),cx,cy-1*d,text);
        text.setTextSize(12*d);text.setColor(0xff9eb3c5);text.setTypeface(Typeface.DEFAULT);
        canvas.drawText("RAM'deki "+(maximum/60)+" dakika kapasite",cx,cy+24*d,text);
    }
}
