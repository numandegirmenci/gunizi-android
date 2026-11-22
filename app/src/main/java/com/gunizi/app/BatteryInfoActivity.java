package com.gunizi.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.BatteryManager;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.*;
import java.time.LocalTime;
import java.util.Locale;

/**
 * Explains mode difference without presenting invented battery measurements.
 * Estimation slider is expressly hypothetical and excludes AI processing.
 */
public final class BatteryInfoActivity extends Activity{
    private LinearLayout content;
    private TextView output;
    private Prefs prefs;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);prefs=new Prefs(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0b1020);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(28),dp(22),dp(28));
        scroll.addView(content);setContentView(scroll);
        label("Pil ve Performans",26,true);
        label("Günİzi'nin iki modunu karşılaştır. Gerçek tüketim için aynı cihazda birkaç günlük test gerekir.",14,false);
        BatteryManager battery=getSystemService(BatteryManager.class);
        int pct=battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        label("Şu anki telefon pili: "+(pct>=0?pct+"%":"ölçülemiyor"),17,true);
        int start=prefs.startHour()*60+prefs.startMinute();
        int stop=prefs.endHour()*60+prefs.endMinute();
        double work=Math.max(0,(stop-start)/60.0);
        label("Mesai Kaydı: "+format(work)+" saat/gün",17,true);
        double selected=prefs.durationMinutes()/60.0;
        label("İsteğe Bağlı Kayıt: "+format(selected)+" saat (seçili süre)",17,true);
        label(work>0&&selected>0?"Mesai modu, seçili isteğe bağlı kayıttan "+format(work/selected)+
            " kat uzun sürer. Bu pil tüketiminin aynı oranda artacağı anlamına gelmez.":
            "Geçerli mesai aralığı belirlenmedi.",14,false);
        label("Teorik Opus arşivi (16 kbps)",17,true);
        label("Mesai: "+format(work*7.2)+" MB · İsteğe Bağlı: "+
            format(selected*7.2)+" MB (16 kbps Opus)",14,false);
        label("Yalnızca kayıt başlatıldığında mikrofon çalışır. İsteğe bağlı kayıtta süre dolunca otomatik kapanır.",13,false);
        label("Örnek pil senaryosu",20,true);
        label("Aşağıdaki kaydırıcı, uygulamanın gerçek tüketimi değildir. Yalnızca saatlik ek tüketim varsayımının süre farkına etkisini gösterir.",13,false);
        SeekBar seek=new SeekBar(this);seek.setMax(19);seek.setProgress(7);content.addView(seek);
        output=label("",17,true);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
                update(work,selected,0.1+progress*0.1);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        update(work,selected,0.8);
        label("Pil maliyeti bileşenleri",20,true);
        label("1. Mikrofon: açık kaldığı sürece güç kullanır.\n"
            +"2. Opus sıkıştırması: mesai modunda işlemci kullanır.\n"
            +"3. Şifreleme ve diske yazma: aralıklı ek enerji tüketir.\n"
            +"4. Yerel konuşma tanıma: işlenirken yüksek işlemci yükü oluşturabilir.\n"
            +"5. Özet çıkarma: uzun kayıtlar için işlem süresi ve sıcaklık artabilir.\n"
            +"6. Ekran ve diğer uygulamalar: toplam pil düşüşünü etkiler.",14,false);
        label("Pil tasarrufu yaklaşımı",20,true);
        label("Günlük otomatik rapor şarjdayken hazırlanır. RAM geçmişi ise yalnızca Sürekli Kayıt modunda tutulur. "
            +"Pil koruma ayarları mikrofonun arka planda kesin çalışmasını garanti etmez.",14,false);
    }
    private String format(double v){return String.format(Locale.forLanguageTag("tr-TR"),"%.1f",v);}
    private TextView label(String txt,int size,boolean bold){
        TextView t=new TextView(this);t.setText(txt);
        t.setTextColor(bold?Color.WHITE:0xffbac8d8);t.setTextSize(size);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(10),0,dp(10));content.addView(t);return t;
    }
    private void update(double work,double selected,double rate){
        output.setText("Varsayım: saatlik %"+format(rate)+" ek pil\n"
            +"Mesai: %"+format(rate*work)+" · Sürekli: %"+format(rate*24)+"\n"
            +"Fark: +"+format(rate*(24-work))+" yüzde puanı (yalnızca örnek)");
    }
}
