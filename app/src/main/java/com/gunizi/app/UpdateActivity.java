package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.File;

/** Installation screen; only Android's own package installer can approve the APK. */
public final class UpdateActivity extends Activity {
    private TextView status;
    private Button install;
    private static final int BG=0xff090e1b,PANEL=0xff172031;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(LinearLayout p,String value,int size,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(bold?0xfff0f7ff:0xffa2b4c9);
        t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setPadding(0,dp(6),0,dp(7));p.addView(t);return t;
    }
    private Button button(LinearLayout p,String value){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);
        p.addView(b);return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView sc=new ScrollView(this);sc.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(25),dp(22),dp(25));sc.addView(root);setContentView(sc);
        text(root,"Günİzi · Güncelleme",25,true);
        text(root,"GitHub'daki yayınlanmış sürümler kontrol edilir. Kodu her kaydettiğinde değil, "
            +"yeni bir sürüm yayınladığında güncelleme hazırlanır.",14,false);
        status=text(root,"Kontrol edilmedi",15,true);
        Button check=button(root,"Şimdi güncelleme kontrol et");
        check.setOnClickListener(v->{
            status.setText("GitHub Releases kontrol ediliyor…");check.setEnabled(false);
            AppUpdateWorker.checkAsync(this,message->{
                status.setText(message);check.setEnabled(true);refresh();
            });
        });
        install=button(root,"İndirilen güncellemeyi yükle");
        install.setOnClickListener(v->install());
        Button releases=button(root,"GitHub sürümlerini aç");
        releases.setOnClickListener(v->startActivity(new Intent(Intent.ACTION_VIEW,
            Uri.parse(AppUpdateWorker.RELEASES))));
        text(root,"Kurulum Android tarafından onaylanır. Eski sürümle farklı imzaya sahip "
            +"bir APK mevcut uygulamanın üzerine yüklenemez. "
            +"Bu durumda kayıtları yedeklemeden uygulamayı kaldırma.",13,false);
        refresh();
    }
    private void refresh(){
        File file=AppUpdateWorker.packageFile(this);
        boolean exists=file.isFile()&&file.length()>100000;
        install.setEnabled(exists);
        if(exists){
            android.content.SharedPreferences p=getSharedPreferences(AppUpdateWorker.PREF,MODE_PRIVATE);
            status.setText("İndirilen yayın: "+p.getString("remoteTag","")+"\n"+status.getText());
        }
    }
    private void install(){
        File f=AppUpdateWorker.packageFile(this);
        if(!f.isFile()){status.setText("İndirilen APK bulunamadı");return;}
        if(!getPackageManager().canRequestPackageInstalls()){
            new AlertDialog.Builder(this)
                .setTitle("Güncelleme kurulum izni")
                .setMessage("Android, Günİzi'nin APK kurulumuna aracılık edebilmesi için "
                    +"bu kaynak üzerinden yükleme izni istiyor. Onay tamamen sana ait.")
                .setNegativeButton("Vazgeç",null)
                .setPositiveButton("İzin ayarları",(d,w)->{
                    Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+getPackageName()));
                    startActivity(i);
                }).show();
            return;
        }
        try{
            Uri uri=FileProvider.getUriForFile(this,
                getPackageName()+".updatefiles",f);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }catch(Exception e){status.setText("Yükleyici açılamadı: "+e.getMessage());}
    }
}
