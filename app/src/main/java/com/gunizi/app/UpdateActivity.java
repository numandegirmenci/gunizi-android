package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;

/**
 * Buddy-like update detail screen:
 * release notes first, approval to install second.
 * Installed app is NEVER uninstalled by this screen.
 */
public final class UpdateActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xfff0f7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private TextView status,headline,changes,compatibility;
    private Button install,check;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable panel(){
        GradientDrawable bg=new GradientDrawable();bg.setColor(PANEL);bg.setCornerRadius(dp(16));return bg;
    }
    private TextView label(LinearLayout parent,String value,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(bold?WHITE:MUTED);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(13);parent.addView(t,p);return t;
    }
    private Button button(LinearLayout parent,String value){
        Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(14);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));
        p.bottomMargin=dp(10);parent.addView(b,p);return b;
    }
    private LinearLayout card(LinearLayout root){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18),dp(16),dp(18),dp(17));c.setBackground(panel());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(12);root.addView(c,p);return c;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(24),dp(18),dp(24));sc.addView(root);setContentView(sc);
        label(root,"Günİzi · Güncellemeler",24,true);
        LinearLayout latest=card(root);
        headline=label(latest,"GitHub sürümü",19,true);
        status=label(latest,"Güncelleme bilgisi kontrol ediliyor…",14,false);
        check=button(latest,"Yeni sürümleri kontrol et");
        check.setOnClickListener(v->check());
        LinearLayout notes=card(root);
        label(notes,"Bu güncellemede neler var?",18,true);
        changes=label(notes,"GitHub'daki yayın notları bu alanda gösterilir.",14,false);
        LinearLayout upgrade=card(root);
        label(upgrade,"Uygulamayı güncelle",18,true);
        compatibility=label(upgrade,"Önceki kayıtların ve ayarların korunması için imza kontrol edilecek.",13,false);
        install=button(upgrade,"Mevcut uygulamanın üzerine yükle");
        install.setOnClickListener(v->install());
        label(upgrade,"Android, kurulumu onaylamanı isteyecek. Günİzi, "
            +"mevcut sürümü kendiliğinden kaldırmaz ve kayıtlarını silmez.",13,false);
        Button releases=button(root,"GitHub yayın sayfasını aç");
        releases.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(AppUpdateWorker.RELEASES))));
        refresh();
    }

    private void check(){
        check.setEnabled(false);
        status.setText("GitHub Releases kontrol ediliyor…");
        AppUpdateWorker.checkAsync(this,message->{
            status.setText(message);
            check.setEnabled(true);
            refresh();
        });
    }

    private void refresh(){
        android.content.SharedPreferences p=getSharedPreferences(AppUpdateWorker.PREF,MODE_PRIVATE);
        String tag=p.getString("remoteTag","");
        String notes=p.getString("releaseNotes","");
        if(!tag.isEmpty())headline.setText("Günİzi "+tag);
        changes.setText(prettyNotes(notes));
        File file=AppUpdateWorker.packageFile(this);
        int remote=p.getInt("remoteCode",0);
        int saved=p.getInt("downloadVersion",0);
        boolean downloaded=file.isFile()&&file.length()>100_000&&remote>0&&saved==remote;
        install.setEnabled(false);
        if(!downloaded){
            compatibility.setText("Yeni bir APK henüz indirilmedi. "+
                "Uygun Wi-Fi bağlantısında yeni sürümü kontrol et. "+
                "Gerekirse GitHub yayın sayfasından açabilirsin.");
            install.setText("İndirme bekleniyor");
            return;
        }
        ReleaseApkValidator.Result result=ReleaseApkValidator.verify(this,file);
        compatibility.setText(result.message);
        compatibility.setTextColor(result.compatible?CYAN:0xffedb46f);
        install.setEnabled(result.compatible);
        install.setText(result.compatible?
            "Mevcut uygulamanın üzerine güncelle":"İmza / sürüm uyumsuzluğu");
    }

    private static String prettyNotes(String raw){
        if(raw==null||raw.trim().isEmpty())
            return "Bu sürümün açıklaması henüz yayınlanmamış.";
        StringBuilder out=new StringBuilder();
        for(String line:raw.split("\\R")){
            String s=line.trim();
            if(s.startsWith("versionCode:"))continue;
            if(s.startsWith("### "))s="\n"+s.substring(4).toUpperCase(java.util.Locale.forLanguageTag("tr-TR"));
            else if(s.startsWith("## "))s="\n"+s.substring(3).toUpperCase(java.util.Locale.forLanguageTag("tr-TR"));
            else if(s.startsWith("# "))s="\n"+s.substring(2);
            else if(s.startsWith("- "))s="• "+s.substring(2);
            else if(s.startsWith("* "))s="• "+s.substring(2);
            s=s.replace("**","").replace("`","");
            if(!s.isEmpty())out.append(s).append("\n");
        }
        return out.toString().trim();
    }

    private void install(){
        File file=AppUpdateWorker.packageFile(this);
        ReleaseApkValidator.Result result=ReleaseApkValidator.verify(this,file);
        if(!result.compatible){
            compatibility.setText(result.message);
            install.setEnabled(false);
            return;
        }
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this)
                .setTitle("Android kurulum izni gerekli")
                .setMessage("Günİzi'nin doğrulanmış APK'yı yükleyiciye sunabilmesi için "
                    +"bu kaynaktan uygulama yüklemeyi onaylamalısın. "
                    +"Mevcut uygulama kaldırılmayacak.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("İzin ayarları",(d,w)->{
                    Intent intent=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+getPackageName()));
                    startActivity(intent);
                }).show();
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".updatefiles",file);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(Exception e){
            compatibility.setText("Android yükleyicisi açılamadı: "+e.getMessage());
        }
    }
}
