package com.gunizi.app;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure JVM assertions for user-visible AI progress and preflight metadata. */
public class AiPipelineV010Test {
    @Test public void stageNamesStayDistinct(){
        assertEquals("Whisper modeli belleğe alınıyor",
            AiStageMetrics.displayStage(AiStageMetrics.MODEL_LOADING));
        assertEquals("Konuşmalar yazıya dökülüyor",
            AiStageMetrics.displayStage(AiStageMetrics.ASR));
        assertEquals("Konular ve görevler çıkarılıyor",
            AiStageMetrics.displayStage(AiStageMetrics.EXTRACTING));
        assertEquals("Tamamlandı",
            AiStageMetrics.displayStage(AiStageMetrics.DONE));
    }
    @Test public void progressElapsedTimeHasSeconds(){
        assertEquals("00:00:01",AiStageMetrics.displayTime(1000));
        assertEquals("00:04:03",AiStageMetrics.displayTime(243500));
        assertEquals("09:30:00",AiStageMetrics.displayTime(34200000));
    }
    @Test public void preflightDetailsKeepSourceDuration(){
        AiAudioPreflight.Info info=new AiAudioPreflight.Info(
            "Günlük kayıt.ogg","audio/opus","source",203000,200000,48000,1);
        assertTrue(info.description().contains("3:23"));
        assertTrue(info.description().contains("audio/opus"));
        assertEquals(203000,info.durationMs);
    }
}
