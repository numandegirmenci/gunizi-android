package com.gunizi.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Local-only scrollable topic table; scripts, network and file access disabled. */
public final class ReportTableActivity extends Activity {
    private WebView viewer;
    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        LinearLayout root=new LinearLayout(this);
        root.setBackgroundColor(0xff090e1b);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(14,22,14,16);
        TextView title=new TextView(this);
        title.setText("Günİzi · Konu Tablosu");title.setTextSize(22);
        title.setTextColor(Color.WHITE);root.addView(title);
        Button close=new Button(this);close.setText("Geri");close.setAllCaps(false);
        close.setOnClickListener(v->finish());root.addView(close);
        Button note=new Button(this);note.setText("Raporu Notlarıma Ekle");
        note.setAllCaps(false);root.addView(note);
        note.setOnClickListener(v->{
            android.content.Intent intent=new android.content.Intent(this,UserNotesActivity.class);
            intent.putExtra("openLatestReport",true);
            startActivity(intent);
        });
        Button memory=new Button(this);
        memory.setText("Günlük iş hafızam");memory.setAllCaps(false);
        root.addView(memory);
        memory.setOnClickListener(v->startActivity(new Intent(this,WorkMemoryActivity.class)));
        viewer=new WebView(this);
        viewer.setBackgroundColor(0xff090e1b);
        WebSettings w=viewer.getSettings();
        w.setJavaScriptEnabled(false);
        w.setAllowFileAccess(false);
        w.setAllowContentAccess(false);
        w.setBlockNetworkLoads(true);
        viewer.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                return handleEvidence(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){
                return handleEvidence(Uri.parse(url));
            }
        });
        root.addView(viewer,new LinearLayout.LayoutParams(-1,0,1f));
        setContentView(root);
        String latest=getSharedPreferences("gunizi",Context.MODE_PRIVATE)
            .getString("latestHtmlReport",null);
        String html="<html lang='tr'><meta charset='utf-8'><body style='background:#090e1b;"
            +"color:#eff7ff;font:16px system-ui;padding:20px'>"
            +"Henüz hazırlanmış konu tablosu yok. Rapor sekmesinden ses dosyası seçerek "
            +"yerel analiz başlatabilirsin.</body></html>";
        if(latest!=null){
            try{
                File f=new File(latest);
                File allowed=new File(getNoBackupFilesDir(),"reports").getCanonicalFile();
                if(f.getCanonicalPath().startsWith(allowed.getPath()+File.separator)&&f.isFile())
                    html=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);
            }catch(Exception ignored){}
        }
        viewer.loadDataWithBaseURL(null,html,"text/html","UTF-8",null);
    }
    private boolean handleEvidence(Uri uri){
        if(!"gunizi".equals(uri.getScheme()))return true;
        if(!"listen".equals(uri.getHost()))return true;
        try{
            long ms=Long.parseLong(uri.getQueryParameter("ms"));
            if(ms<0||ms>24L*3600_000L)return true;
            String source=getSharedPreferences("gunizi",MODE_PRIVATE)
                .getString("lastReportSourceUri",null);
            if(source==null||source.isEmpty())return true;
            startActivity(new Intent(this,AudioEvidenceActivity.class)
                .putExtra(AudioEvidenceActivity.EXTRA_SOURCE,source)
                .putExtra(AudioEvidenceActivity.EXTRA_START,ms));
        }catch(Exception ignored){}
        return true;
    }
    @Override protected void onDestroy(){
        if(viewer!=null){viewer.destroy();viewer=null;}
        super.onDestroy();
    }
}
