package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Explicit user instructions are snapshotted for each local AI analysis. */
final class AnalysisPreferences {
    private static final String PREF="gunizi_ai_preferences";
    private final SharedPreferences p;
    static final String DEFAULT="Gün içindeki bütün önemli konuları kapsa. "+
        "Özellikle konuşulan işler, alınan kesin kararlar, yapılacak görevler, "+
        "sorumlu kişiler, süre ve tarihler, bekleyen talepler, riskler ve "+
        "takip edilmesi gereken konuları çıkar. Sıradan sohbeti kısa tut. "+
        "Söylenmemiş bir kararı veya sorumluyu uydurma.";
    AnalysisPreferences(Context c){
        p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    String focus(){
        String s=p.getString("focus",DEFAULT);
        return s==null||s.trim().isEmpty()?DEFAULT:s.trim();
    }
    void focus(String value){
        String s=value==null?"":value.trim();
        if(s.length()>1500)s=s.substring(0,1500);
        p.edit().putString("focus",s.isEmpty()?DEFAULT:s).apply();
    }
    String speechModel(){
        String id=p.getString("speech_model",AiModelStore.WHISPER_TURBO_Q8);
        return AiModelStore.WHISPER_TURBO.equals(id)||
            AiModelStore.WHISPER_BASE.equals(id)||
            AiModelStore.WHISPER_Q5.equals(id)||
            AiModelStore.WHISPER_TURBO_Q8.equals(id)||
            AiModelStore.WHISPER.equals(id)?id:AiModelStore.WHISPER_TURBO_Q8;
    }
    void speechModel(String id){
        if(!AiModelStore.WHISPER.equals(id)&&
           !AiModelStore.WHISPER_BASE.equals(id)&&
           !AiModelStore.WHISPER_Q5.equals(id)&&
           !AiModelStore.WHISPER_TURBO_Q8.equals(id)&&
           !AiModelStore.WHISPER_TURBO.equals(id))
            throw new IllegalArgumentException("Konuşma modeli");
        p.edit().putString("speech_model",id).apply();
    }
    boolean includeDetails(){return p.getBoolean("details",true);}
    void includeDetails(boolean v){p.edit().putBoolean("details",v).apply();}
    boolean separateTasks(){return p.getBoolean("tasks",true);}
    void separateTasks(boolean v){p.edit().putBoolean("tasks",v).apply();}
}
