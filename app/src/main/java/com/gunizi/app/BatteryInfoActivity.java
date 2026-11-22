package com.gunizi.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.Locale;

/** Transparent explanation, without fabricated per-hour battery drain. */
public final class BatteryInfoActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,MUTED=0xffa2b4c9;
    private LinearLayout page;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout parent,String v,int sz,boolean strong){
        TextView t=new TextView(this);t.setText(v);t.setTextColor(strong?WHITE:MUTED);
        t.setTextSize(sz);if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(9);
        parent.addView(t,p);return t;
    }
    private LinearLayout card(String title){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(16),dp(17),dp(15));
        GradientDrawable bg=new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(16));
        c.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);
        page.addView(c,p);text(c,title,17,true);return c;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);Prefs pref=new Prefs(this);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(22),dp(18),dp(24));sc.addView(page);setContentView(sc);
        text(page,"Pil ve Performans",24,true);
        text(page,"Gerçek tüketim, cihazda kullanım testleriyle ölçülür. "+
            "Burada kesin pil yüzdesi uydurmadan hangi işlemin neden pil harcadığını açıklıyoruz.",14,false);
        BatteryManager b=getSystemService(BatteryManager.class);
        int capacity=b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        text(page,capacity>=0?"Şu an telefon pili: %"+capacity:
            "Pil yüzdesi okunamadı",16,true);

        LinearLayout history=card("Geçmiş Kaydı");
        text(history,"Dinleme açıkken mikrofon ve Opus sıkıştırması devam eder. "+
            "1 dakika seçmek, mikrofonun yalnızca 1 dakika çalıştığı anlamına gelmez: "+
            "seçim, geçmişten dışa aktarılacak süredir.",14,false);
        text(history,"En fazla 6 saatlik geçici ses geçmişi korunur. "+
            "Dinlemeyi kapatırsan yeni güç tüketimi durur ve kaydedilmemiş geçmiş temizlenir.",13,false);

        LinearLayout work=card("Mesai Kaydı");
        int start=pref.startHour()*60+pref.startMinute();
        int end=pref.endHour()*60+pref.endMinute();
        int mins=Math.max(0,end-start);
        text(work,"Planlanan günlük süre: "+(mins/60)+" saat "+(mins%60)+" dakika. "+
            "Telefonun kendi saatine göre çalışır.",14,false);
        text(work,"Mesai içinde mikrofon kayıt yapar ve Opus dosyasını kaydeder. "+
            "Kaydetme ve şifreleme de kısa süre işlemci kullanabilir.",13,false);

        LinearLayout ai=card("Yerel Yapay Zeka");
        text(ai,"Whisper ile uzun konuşma çözümleme ve Qwen3 ile ayrıntılı "+
            "özetleme yoğun işlemci, RAM ve pil kullanabilir. "+
            "Özellikle 9 saatlik kayıtlarda şarjdayken çalıştırılmalıdır.",14,false);
        text(ai,"Otomatik rapor, şarj koşulları uygun olduğunda başlar. "+
            "Model seçimi, sıcaklık ve konuşma miktarı işlem süresini etkiler.",13,false);

        LinearLayout note=card("Ne kadar pil harcar?");
        text(note,"Kesin yüzde gösterebilmek için aynı cihazda gerçek ölçüm gerekir. "+
            "Genel olarak mikrofonun açık kaldığı süre uzadıkça kayıt tüketimi artar; "+
            "ağır yerel yapay zeka analizi kısa süreli mikrofon kaydından daha yoğun güç kullanabilir.",14,false);
        text(note,"Bu ekran şimdilik ölçüm sonucu değil, çalışma mantığının açıklamasıdır.",12,false);
    }
}
