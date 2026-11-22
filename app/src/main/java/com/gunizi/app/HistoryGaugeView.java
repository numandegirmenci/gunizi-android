package com.gunizi.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import java.util.Locale;

/** Live history dial; displays actual elapsed listening time, capped at six hours. */
public final class HistoryGaugeView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int seconds=0;
    private static final int MAX=6*3600;
    private float density;
    public HistoryGaugeView(Context ctx){super(ctx);density=getResources().getDisplayMetrics().density;}
    public void update(int seconds){
        int next=Math.max(0,Math.min(MAX,seconds));
        if(this.seconds!=next){this.seconds=next;invalidate();}
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float cx=getWidth()/2f,cy=getHeight()/2f;
        float radius=Math.min(getHeight(),getWidth())*0.38f;
        RectF rect=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
        p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(13*density);
        p.setColor(0xff2c414c);c.drawArc(rect,130,280,false,p);
        p.setColor(0xff33d7c0);c.drawArc(rect,130,Math.max(0.5f,280f*seconds/MAX),false,p);
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        p.setTextSize(34*density);p.setColor(0xffe5fffc);
        c.drawText(String.format(Locale.ROOT,"%02d:%02d:%02d",
            seconds/3600,(seconds/60)%60,seconds%60),cx,cy-3*density,p);
        p.setTextSize(12*density);p.setTypeface(Typeface.DEFAULT);
        p.setColor(0xffa9bdcb);
        c.drawText("Son 6 saate kadar geçici geçmiş",cx,cy+27*density,p);
    }
}
