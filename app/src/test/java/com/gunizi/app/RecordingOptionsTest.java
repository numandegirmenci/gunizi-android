package com.gunizi.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDateTime;

public class RecordingOptionsTest {
    @Test public void legacyAndM4aProfiles(){
        RecordingOptions old=new RecordingOptions(0,0);
        assertEquals(".ogg",old.extension());assertEquals(16000,old.bitrate());
        RecordingOptions aac=new RecordingOptions(1,1);
        assertEquals(".m4a",aac.extension());assertEquals(64000,aac.bitrate());
        assertEquals("audio/mp4",RecordingOptions.mimeForName("sample.m4a"));
        assertEquals("audio/ogg",RecordingOptions.mimeForName("sample.ogg"));
    }
    @Test public void optionalFieldsAndSafeName(){
        LocalDateTime time=LocalDateTime.of(2026,11,24,17,4,32);
        assertEquals("Günİzi_Mesai_2026-11-24_Salı_17-04-32.m4a",
            RecordingOptions.name("Günİzi",31,"Mesai",time,".m4a"));
        assertEquals("Is_Ofis.ogg",RecordingOptions.name("Is/Ofis",0,"Mesai",time,".ogg"));
        assertEquals("Günİzi.ogg",RecordingOptions.name("",0,"Mesai",time,".ogg"));
    }
}
