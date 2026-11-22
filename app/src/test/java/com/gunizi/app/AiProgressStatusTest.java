package com.gunizi.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class AiProgressStatusTest {
    @Test public void audioProgressStaysGlobalAcrossNativeChunkResets(){
        assertEquals(7,AiProgressStatus.totalChunks(203000));
        assertEquals(73,AiProgressStatus.totalPercent(150000,203000));
        assertEquals("00:02:30 / 00:03:23",AiProgressStatus.audioLabel(150000,203000));
        assertEquals("Bölüm 6 / 7",AiProgressStatus.partLabel(6,7));
    }
    @Test public void unknownAudioLengthNeverShowsInventedPercentage(){
        assertEquals(0,AiProgressStatus.totalChunks(0));
        assertEquals(0,AiProgressStatus.totalPercent(20000,0));
        assertEquals("Ses süresi hesaplanıyor",AiProgressStatus.audioLabel(0,0));
    }
    @Test public void stageSpecificProgressIsHonest(){
        assertTrue(AiProgressStatus.indeterminate(AiStageMetrics.MODEL_LOADING));
        assertTrue(AiProgressStatus.indeterminate(AiStageMetrics.LLM_LOADING));
        assertFalse(AiProgressStatus.indeterminate(AiStageMetrics.ASR));
        assertFalse(AiProgressStatus.indeterminate(AiStageMetrics.DONE));
    }
    @Test public void longRunningIsWarningNotCrash(){
        assertEquals("",AiProgressStatus.attention(
            AiStageMetrics.ASR,85000,85000,true));
        assertTrue(AiProgressStatus.attention(
            AiStageMetrics.ASR,91000,91000,true).contains("olabilir"));
        assertEquals("",AiProgressStatus.attention(
            AiStageMetrics.MODEL_LOADING,999999,999999,false));
    }
    @Test public void timeKeepsSecondsAtNineHours(){
        assertEquals("09:00:01",AiProgressStatus.time(32401000));
    }
}
