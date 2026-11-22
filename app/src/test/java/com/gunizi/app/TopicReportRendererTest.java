package com.gunizi.app;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class TopicReportRendererTest {
    @Test public void allSourceChunksRemainInTopicTable(){
        String payload="[[GUNIZI_TABLO_BASLA]]\n"
            +"00:12:01\tRuhsat\tBaşvuru belgesi istendi\tBelirtilmedi\tBelge kontrolü\tBelirtilmedi\n"
            +"04:45:23\tAbonelik\tElektrik soruldu\tEksik bilgi\tFatura kontrolü\tAhmet\n"
            +"07:20:10\tRuhsat\tBaşvuru hatırlatıldı\tTamamlanacak\tBelgeyi ilet\tZeynep\n"
            +"[[GUNIZI_TABLO_BITIR]]";
        List<TopicReportRenderer.Entry> rows=TopicReportRenderer.parse(payload);
        assertEquals(3,rows.size());
        String html=TopicReportRenderer.html(rows,"9 saatlik kayıt");
        assertTrue(html.contains("3 görüşme")||html.contains("2 görüşme"));
        assertTrue(html.contains("04:45:23"));
        assertTrue(html.contains("07:20:10"));
        assertTrue(html.contains("<details><summary>"));
        String notes=TopicReportRenderer.noteText(rows);
        assertTrue(notes.contains("Ahmet"));
        assertTrue(notes.contains("Zeynep"));
    }
    @Test public void htmlEscapesUntrustedConversationContent(){
        String transcript="[[GUNIZI_TABLO_BASLA]]\n"
            +"00:00:11\tİşlem\t<script>alert(1)</script>\tBelirtilmedi\tA & B\tBelirtilmedi\n"
            +"[[GUNIZI_TABLO_BITIR]]";
        String html=TopicReportRenderer.html(TopicReportRenderer.parse(transcript),"X & Y");
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("X &amp; Y"));
    }
    @Test public void emptyExtractionIsExplicitlyMarkedUncertain(){
        List<TopicReportRenderer.Entry> rows=TopicReportRenderer.parse(
            "[[GUNIZI_TABLO_BASLA]]\n[[GUNIZI_TABLO_BITIR]]");
        assertEquals(1,rows.size());
        assertTrue(rows.get(0).parts[1].contains("oluşturulamadı"));
    }
    @Test public void twelveHundredRowLimitDoesNotDiscardLongWorkday(){
        StringBuilder payload=new StringBuilder("[[GUNIZI_TABLO_BASLA]]\n");
        for(int i=0;i<1505;i++){
            payload.append("00:00:01\tKonu").append(i)
                .append("\tAyrıntı ").append(i)
                .append("\tBelirtilmedi\tBelirtilmedi\tBelirtilmedi\n");
        }
        payload.append("[[GUNIZI_TABLO_BITIR]]");
        List<TopicReportRenderer.Entry> rows=TopicReportRenderer.parse(payload.toString());
        assertEquals(1505,rows.size());
        assertTrue(TopicReportRenderer.html(rows,"9 saat").contains("Konu1504"));
    }
    @Test public void audioEvidenceTimestampsAreParsedOnlyWhenValid(){
        assertEquals(3723000L,TopicReportRenderer.timestampMs("01:02:03"));
        assertEquals(-1L,TopicReportRenderer.timestampMs("Belirtilmedi"));
        assertEquals(-1L,TopicReportRenderer.timestampMs("01:68:03"));
        assertTrue(TopicReportRenderer.html(TopicReportRenderer.parse(
            "[[GUNIZI_TABLO_BASLA]]\n00:00:03\tKonu\tKonuşma\tKarar\tBelirtilmedi\tBelirtilmedi\n[[GUNIZI_TABLO_BITIR]]"),"Kayıt")
            .contains("gunizi://listen?ms=3000"));
    }

}
