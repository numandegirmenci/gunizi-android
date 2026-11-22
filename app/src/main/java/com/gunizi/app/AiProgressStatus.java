package com.gunizi.app;

import java.util.Locale;

/** Stable, unit-testable presentation rules for on-device inference progress. */
final class AiProgressStatus {
    static final int CHUNK_MS=30000,FIRST_CHUNK_MS=12000;
    private AiProgressStatus(){}

    static int totalChunks(long durationMs){
        if(durationMs<=0)return 0;
        if(durationMs<=FIRST_CHUNK_MS)return 1;
        return (int)Math.min(Integer.MAX_VALUE,
            1+(durationMs-FIRST_CHUNK_MS+CHUNK_MS-1)/CHUNK_MS);
    }
    static int totalPercent(long processedMs,long durationMs){
        if(durationMs<=0)return 0;
        return (int)Math.min(100,Math.max(0,processedMs)*100L/durationMs);
    }
    static String time(long milliseconds){
        long seconds=Math.max(0,milliseconds/1000);
        return String.format(Locale.ROOT,"%02d:%02d:%02d",
            seconds/3600,(seconds/60)%60,seconds%60);
    }
    static String audioLabel(long processedMs,long durationMs){
        if(durationMs<=0)return "Ses süresi hesaplanıyor";
        return time(Math.min(durationMs,Math.max(0,processedMs)))+" / "+time(durationMs);
    }
    static String partLabel(int index,int total){
        if(index<=0)return "Bölümler hazırlanıyor";
        return total<=0?"Bölüm "+index:"Bölüm "+Math.min(index,total)+" / "+total;
    }
    static String stageNumber(String stage,String mode){
        if(AiStageMetrics.DONE.equals(stage))return "Tamamlandı";
        if(AiStageMetrics.ERROR.equals(stage))return "Hata";
        if(AiStageMetrics.CANCELLED.equals(stage))return "İptal edildi";
        if(AiStageMetrics.QUEUED.equals(stage))return "Sırada";
        if(DailySummaryWorker.TEXT_REPORT.equals(mode))
            return AiStageMetrics.LLM_LOADING.equals(stage)?"1 / 2":"2 / 2";
        boolean onlyText=DailySummaryWorker.TRANSCRIPT.equals(mode);
        if(AiStageMetrics.PREFLIGHT.equals(stage)||
            AiStageMetrics.MODEL_LOADING.equals(stage))return onlyText?"1 / 2":"1 / 3";
        if(AiStageMetrics.AUDIO_DECODING.equals(stage)||
            AiStageMetrics.ASR.equals(stage)||
            AiStageMetrics.TEXT_READY.equals(stage))return onlyText?"2 / 2":"2 / 3";
        if(AiStageMetrics.LLM_LOADING.equals(stage)||
            AiStageMetrics.EXTRACTING.equals(stage)||
            AiStageMetrics.MERGING.equals(stage))return "3 / 3";
        return "Durum";
    }
    static boolean indeterminate(String stage){
        return AiStageMetrics.MODEL_LOADING.equals(stage)||
            AiStageMetrics.LLM_LOADING.equals(stage)||
            AiStageMetrics.PREFLIGHT.equals(stage);
    }
    static String attention(String stage,long sinceProgressMs,
                            long sincePhaseMs,boolean running){
        if(!running)return "";
        if((AiStageMetrics.MODEL_LOADING.equals(stage)||
            AiStageMetrics.LLM_LOADING.equals(stage))&&sincePhaseMs>=90000)
            return "Model uzun süredir hazırlanıyor. Bu aşamada yüzde hesaplanamaz; "+
                "cihaz belleği ve model uyumluluğu etkili olabilir.";
        if(AiStageMetrics.ASR.equals(stage)&&sinceProgressMs>=90000)
            return "Bu ses bölümünde ilerleme gecikti. "+
                "Uygulama hâlâ çalışıyor olabilir; bekleme süresi kaydediliyor.";
        return "";
    }
}
