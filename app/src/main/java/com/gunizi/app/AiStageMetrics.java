package com.gunizi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import java.util.Locale;

/**
 * Persistent, non-sensitive stage telemetry. Stage identifiers are stable and
 * durations use a monotonic clock within one boot; no conversation content logged.
 */
final class AiStageMetrics {
    static final String PREF="gunizi";
    static final String PREFLIGHT="preflight",MODEL_LOADING="model_loading",
        AUDIO_DECODING="audio_decoding",ASR="asr",TEXT_READY="text_ready",
        LLM_LOADING="llm_loading",EXTRACTING="extracting",MERGING="merging",
        DONE="done",ERROR="error";
    static void enter(Context c,String stage){
        long now=SystemClock.elapsedRealtime();
        SharedPreferences prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String previous=prefs.getString("analysisPhase","");
        long start=prefs.getLong("analysisPhaseStartElapsed",0);
        SharedPreferences.Editor e=prefs.edit();
        if(!previous.isEmpty()&&start>0&&now>=start)
            e.putLong("duration_"+previous,now-start);
        e.putString("analysisPhase",stage)
            .putLong("analysisPhaseStartElapsed",now)
            .putLong("analysisProgressAt",System.currentTimeMillis()).apply();
    }
    static String stage(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
            .getString("analysisPhase","");
    }
    static long stageMs(Context c){
        SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        long start=p.getLong("analysisPhaseStartElapsed",0);
        if(start==0)return 0;
        long now=SystemClock.elapsedRealtime();
        return Math.max(0,now-start);
    }
    static String displayStage(String stage){
        if(PREFLIGHT.equals(stage))return "Ses dosyası kontrol ediliyor";
        if(MODEL_LOADING.equals(stage))return "Whisper modeli belleğe alınıyor";
        if(AUDIO_DECODING.equals(stage))return "Ses okunuyor";
        if(ASR.equals(stage))return "Konuşmalar yazıya dökülüyor";
        if(TEXT_READY.equals(stage))return "Konuşma metni hazır";
        if(LLM_LOADING.equals(stage))return "Özet modeli belleğe alınıyor";
        if(EXTRACTING.equals(stage))return "Konular ve görevler çıkarılıyor";
        if(MERGING.equals(stage))return "Günlük rapor hazırlanıyor";
        if(DONE.equals(stage))return "Tamamlandı";
        if(ERROR.equals(stage))return "İşlem başarısız";
        return "İşlem bekleniyor";
    }
    static String displayTime(long millis){
        long n=Math.max(0,millis/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",
            n/3600,(n/60)%60,n%60);
    }
}
