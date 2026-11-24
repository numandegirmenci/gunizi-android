package com.gunizi.app;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Daily work memory: discussed, mine, awaiting response, closed; always source-linked. */
public final class WorkMemoryActivity extends Activity {
    private static final int BG=0xff090e1b,PANEL=0xff172031,WHITE=0xffeff7ff,
        MUTED=0xffa2b4c9,CYAN=0xff38d4ca;
    private WorkMemoryStore.Report report;
    private final ArrayList<WorkMemoryStore.Item> visible=new ArrayList<>();
    private ListView list;
    private BaseAdapter adapter;
    private TextView sourceTitle,summary,empty;
    private LinearLayout filters;
    private final Button[] tabs=new Button[4];
    private int filter=0;
    private int generation=0;
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private GradientDrawable shape(int color){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(13),dp(17),dp(13),dp(11));root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header);
        TextView title=text(header,"Günlük iş hafızam",21,true);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        Button history=button("Kayıtlar",false);
        header.addView(history,new LinearLayout.LayoutParams(dp(98),dp(43)));
        history.setOnClickListener(v->chooseReport());
        Button search=button("Tüm kayıtlarda ara",true);
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        search.setOnClickListener(v->searchLibrary());
        sourceTitle=text(root,"Rapor seçiliyor…",12,false);
        summary=text(root,"Konular ve görevler kaynağıyla birlikte gösterilir.",12,false);
        filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);scroller.addView(filters);
        LinearLayout.LayoutParams flp=new LinearLayout.LayoutParams(-1,dp(49));
        flp.topMargin=dp(13);flp.bottomMargin=dp(9);root.addView(scroller,flp);
        String[] names={"Konuşulanlar","Bana kalanlar","Dönüş beklediklerim","Sonuçlananlar"};
        for(int i=0;i<4;i++){
            final int idx=i;Button b=button(names[i],false);
            tabs[i]=b;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(45));
            lp.rightMargin=dp(7);filters.addView(b,lp);
            b.setOnClickListener(v->{filter=idx;rebuild();});
        }
        empty=text(root,"Henüz rapor yok.",13,false);
        list=new ListView(this);list.setCacheColorHint(0);list.setDividerHeight(dp(7));
        list.setBackgroundColor(BG);
        adapter=new BaseAdapter(){
            @Override public int getCount(){return visible.size();}
            @Override public Object getItem(int i){return visible.get(i);}
            @Override public long getItemId(int i){return i;}
            @Override public View getView(int position,View convert,android.view.ViewGroup parent){
                return itemView(visible.get(position),convert);
            }
        };
        list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        String latest=getSharedPreferences("gunizi",MODE_PRIVATE)
            .getString("workMemoryLatest",null);
        if(latest==null){List<String> ids=WorkMemoryStore.recent(this);
            if(!ids.isEmpty())latest=ids.get(0);}
        if(latest!=null)load(latest);else rebuild();
    }
    private TextView text(LinearLayout root,String value,int font,boolean strong){
        TextView t=new TextView(this);t.setText(value);
        t.setTextSize(font);t.setTextColor(strong?WHITE:MUTED);
        if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(t);return t;
    }
    private Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);
        b.setTextColor(primary?BG:WHITE);
        b.setBackground(shape(primary?CYAN:0xff25364a));return b;
    }
    private void load(String id){
        final int pending=++generation;
        sourceTitle.setText("İş hafızası yükleniyor…");
        new Thread(()->{
            try{
                WorkMemoryStore.Report loaded=WorkMemoryStore.load(this,id);
                runOnUiThread(()->{
                    if(pending!=generation||isFinishing()||isDestroyed())return;
                    report=loaded;
                    getSharedPreferences("gunizi",MODE_PRIVATE).edit()
                        .putString("workMemoryLatest",id).apply();
                    sourceTitle.setText(loaded.name+" · "+new SimpleDateFormat("dd.MM.yyyy HH:mm",
                        new Locale("tr","TR")).format(new Date(loaded.created)));
                    rebuild();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(pending==generation)sourceTitle.setText("Rapor açılamadı: "+e.getMessage());
                });
            }
        },"GunIzi-MemoryLoad").start();
    }
    private void searchLibrary(){
        EditText query=new EditText(this);
        query.setSingleLine(true);query.setHint("Konu, kişi, mağaza veya görev");
        new AlertDialog.Builder(this).setTitle("İş hafızasında ara")
            .setView(query).setNegativeButton("Vazgeç",null)
            .setPositiveButton("Ara",(d,w)->runLibrarySearch(query.getText().toString())).show();
    }
    private void runLibrarySearch(String query){
        if(MemorySearch.words(query).isEmpty()){
            Toast.makeText(this,"En az iki harfli bir arama sözcüğü gir.",Toast.LENGTH_SHORT).show();return;
        }
        final ProgressDialog busy=new ProgressDialog(this);
        busy.setMessage("Yerel kayıtlar aranıyor…");busy.setCancelable(true);busy.show();
        final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        busy.setOnCancelListener(d->cancelled.set(true));
        new Thread(()->{
            try{
                MemoryLibrarySearch.Result result=MemoryLibrarySearch.search(this,query,cancelled);
                runOnUiThread(()->{
                    if(cancelled.get()||isFinishing()||isDestroyed())return;
                    busy.dismiss();
                    if(result.hits.isEmpty()){
                        new AlertDialog.Builder(this).setTitle("Sonuç bulunamadı")
                            .setMessage("İş hafızasında eşleşen satır yok. Okunamayan rapor: "+result.unreadable)
                            .setPositiveButton("Tamam",null).show();return;
                    }
                    String[] labels=new String[result.hits.size()];
                    for(int i=0;i<labels.length;i++){
                        MemoryLibrarySearch.Hit h=result.hits.get(i);
                        labels[i]=(h.related?"İlişkili konu • ":"Eşleşme • ")+h.name+"\n"+h.item.topic+" · "+h.item.content;
                    }
                    new AlertDialog.Builder(this).setTitle("Kayıt araması · "+labels.length+" sonuç")
                        .setItems(labels,(d,which)->{
                            MemoryLibrarySearch.Hit h=result.hits.get(which);
                            if(h.item.timeMs>=0&&!h.uri.isEmpty()){
                                startActivity(new Intent(this,AudioEvidenceActivity.class)
                                    .putExtra(AudioEvidenceActivity.EXTRA_SOURCE,h.uri)
                                    .putExtra(AudioEvidenceActivity.EXTRA_START,h.item.timeMs));
                            }else load(h.reportId);
                        }).setNegativeButton("Kapat",null).show();
                    if(result.unreadable>0)Toast.makeText(this,result.unreadable+" rapor okunamadı.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception error){
                runOnUiThread(()->{
                    if(cancelled.get()||isFinishing()||isDestroyed())return;
                    busy.dismiss();Toast.makeText(this,"Arama tamamlanamadı.",Toast.LENGTH_LONG).show();
                });
            }
        },"GunIzi-LibrarySearch").start();
    }
    private void chooseReport(){
        List<String> ids=WorkMemoryStore.recent(this);
        if(ids.isEmpty()){Toast.makeText(this,"Henüz kayıtlı iş hafızası yok.",Toast.LENGTH_LONG).show();return;}
        String[] options=new String[ids.size()];
        for(int i=0;i<ids.size();i++){
            try{
                options[i]=WorkMemoryStore.describe(this,ids.get(i));
            }catch(Exception e){options[i]="Rapor #"+(i+1);}
        }
        new AlertDialog.Builder(this).setTitle("İncelenecek kayıt")
            .setItems(options,(d,which)->load(ids.get(which)))
            .setNegativeButton("Vazgeç",null).show();
    }
    private void rebuild(){
        visible.clear();
        int[] counts=new int[4];
        if(report!=null)for(WorkMemoryStore.Item item:report.items){
            counts[0]++;
            if(WorkMemoryStore.MINE.equals(item.status))counts[1]++;
            if(WorkMemoryStore.WAIT.equals(item.status))counts[2]++;
            if(WorkMemoryStore.DONE.equals(item.status))counts[3]++;
            if(filter==0||(filter==1&&WorkMemoryStore.MINE.equals(item.status))||
               (filter==2&&WorkMemoryStore.WAIT.equals(item.status))||
               (filter==3&&WorkMemoryStore.DONE.equals(item.status)))
                visible.add(item);
        }
        for(int i=0;i<tabs.length;i++){
            tabs[i].setBackground(shape(i==filter?0xff146862:0xff25364a));
            tabs[i].setTextColor(WHITE);
        }
        summary.setText(report==null?"Henüz yerel bir rapor oluşturulmadı.":
            counts[0]+" konu/karar/görev · "+counts[1]+" bana kalan · "+
            counts[2]+" dönüş beklenen · "+counts[3]+" sonuçlanan");
        empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE);
        empty.setText(report==null?"Önce bir ses kaydını analiz et.":
            "Bu bölümde henüz kayıt yok. Bir görevi açıp durumunu doğrulayabilirsin.");
        adapter.notifyDataSetChanged();list.setSelection(0);
    }
    private static final class Holder {
        LinearLayout container,content;
        TextView heading,message,note;
        Button more;
    }
    private View itemView(WorkMemoryStore.Item item,View existing){
        final Holder h;
        if(existing instanceof LinearLayout&&existing.getTag() instanceof Holder){
            h=(Holder)existing.getTag();
        }else{
            h=new Holder();
            h.container=new LinearLayout(this);
            h.container.setOrientation(LinearLayout.HORIZONTAL);
            h.container.setPadding(dp(12),dp(11),dp(8),dp(11));
            h.container.setBackground(shape(PANEL));
            h.content=new LinearLayout(this);
            h.content.setOrientation(LinearLayout.VERTICAL);
            h.container.addView(h.content,new LinearLayout.LayoutParams(0,-2,1));
            h.heading=text(h.content,"",12,true);h.heading.setTextColor(CYAN);
            h.message=text(h.content,"",14,false);
            h.message.setTextColor(WHITE);h.message.setMaxLines(5);
            h.note=text(h.content,"",11,false);
            h.more=button("⋯",false);
            h.container.addView(h.more,new LinearLayout.LayoutParams(dp(49),dp(43)));
            h.container.setTag(h);
        }
        String type=WorkMemoryStore.DECISION.equals(item.kind)?"Karar":
            WorkMemoryStore.TASK.equals(item.kind)?"Görev":"Görüşme";
        String stamp=item.timeMs>=0?" · "+String.format(Locale.ROOT,"%02d:%02d:%02d",
            item.timeMs/3600000,(item.timeMs/60000)%60,(item.timeMs/1000)%60):"";
        h.heading.setText(type+" • "+item.topic+stamp);
        h.message.setText(item.content);
        String note=item.confirmed?"Durum tarafınızdan doğrulandı":
            item.suggested?"Yapay zeka önerisi · Sesle doğrulayın":
            "Kaynak zamanına dokunarak doğrulayın";
        h.note.setText(note+(item.contact.isEmpty()?"":" · Beklenen: "+item.contact));
        h.content.setOnClickListener(v->play(item));
        h.more.setOnClickListener(v->popup(h.more,item));
        return h.container;
    }
    private void play(WorkMemoryStore.Item item){
        if(report==null||item.timeMs<0){
            Toast.makeText(this,"Bu ifade için doğrulanmış ses zamanı bulunamadı.",Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this,AudioEvidenceActivity.class)
            .putExtra(AudioEvidenceActivity.EXTRA_SOURCE,report.sourceUri)
            .putExtra(AudioEvidenceActivity.EXTRA_START,item.timeMs));
    }
    private void popup(View anchor,WorkMemoryStore.Item item){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("Ses bölümünü dinle").setOnMenuItemClickListener(m->{play(item);return true;});
        menu.getMenu().add("Bu iş bana ait").setOnMenuItemClickListener(m->{
            save(item,WorkMemoryStore.MINE,"");return true;});
        menu.getMenu().add("Dönüş bekliyorum").setOnMenuItemClickListener(m->{
            EditText contact=new EditText(this);contact.setSingleLine(true);
            contact.setHint("Kimden dönüş bekleniyor?");
            contact.setText(item.contact);
            new AlertDialog.Builder(this).setTitle("Dönüş beklenen kişi veya birim")
                .setView(contact).setNegativeButton("Vazgeç",null)
                .setPositiveButton("Kaydet",(d,w)->save(item,WorkMemoryStore.WAIT,
                    contact.getText().toString())).show();return true;
        });
        menu.getMenu().add("Sonuçlandı").setOnMenuItemClickListener(m->{
            save(item,WorkMemoryStore.DONE,"");return true;});
        menu.getMenu().add("İncelenecek olarak işaretle").setOnMenuItemClickListener(m->{
            save(item,WorkMemoryStore.REVIEW,"");return true;});
        menu.show();
    }
    private void save(WorkMemoryStore.Item item,String status,String contact){
        try{
            WorkMemoryStore.setStatus(this,report.id,item.id,status,contact);
            item.status=status;item.contact=contact;item.confirmed=true;item.suggested=false;
            rebuild();Toast.makeText(this,"Takip durumu kaydedildi.",Toast.LENGTH_SHORT).show();
        }catch(Exception e){Toast.makeText(this,"Kaydedilemedi: "+e.getMessage(),
            Toast.LENGTH_LONG).show();}
    }
}