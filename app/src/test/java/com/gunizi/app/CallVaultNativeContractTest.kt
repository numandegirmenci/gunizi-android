package com.gunizi.app

import com.baba.callvault.summary.SummaryGrammar
import com.baba.callvault.transcription.SpeechGapSnap
import com.baba.callvault.transcription.TranscriptSegment
import org.junit.Assert.*
import org.junit.Test

/** Engine-independent tests for the shipped CallVault port and Günİzi model choices. */
class CallVaultNativeContractTest {
    @Test fun sileroTimestampSeamIsSnappedToRealSpeech() {
        val speech=listOf(SpeechGapSnap.Speech(0,1000),
            SpeechGapSnap.Speech(2000,3000))
        val output=SpeechGapSnap.apply(
            listOf(TranscriptSegment(1300,1450,"Türkçe deneme")),speech)
        assertEquals(2000L,output.first().startMs)
        assertTrue(output.first().endMs>output.first().startMs)
    }
    @Test fun strictSummaryGrammarContainsDecisionAndActionArrays(){
        val grammar=SummaryGrammar.JSON
        assertTrue(grammar.contains("decisions"))
        assertTrue(grammar.contains("actionItems"))
        assertTrue(grammar.contains("keyFacts"))
        assertTrue(grammar.contains("intent"))
    }
    @Test fun callVaultModelIdsHaveDistinctSpeechAndLanguageProfiles(){
        assertTrue(AiModelStore.isSpeech(AiModelStore.WHISPER_TURBO_Q8))
        assertTrue(AiModelStore.isSpeech(AiModelStore.WHISPER_Q5))
        assertFalse(AiModelStore.isSpeech(AiModelStore.GEMMA))
        assertTrue(AiModelStore.label(AiModelStore.GEMMA).contains("Gemma"))
    }
}
