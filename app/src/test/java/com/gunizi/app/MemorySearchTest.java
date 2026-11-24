package com.gunizi.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemorySearchTest {
    @Test public void turkishAndCase(){
        assertEquals("isik cagri",MemorySearch.normalize("IŞIK ÇAĞRI"));
        assertTrue(MemorySearch.score("İSKAN","İskan belgesi","", "", "")>0);
    }
    @Test public void rankingAndUnrelated(){
        assertTrue(MemorySearch.score("ruhsat","Ruhsat","", "", "")>
            MemorySearch.score("ruhsat","Konu","ruhsat", "", ""));
        assertEquals(0,MemorySearch.score("ruhsat","Fatura","ödeme", "", ""));
        assertTrue(MemorySearch.score("belediye","Konu","belediyeden", "", "")>0);
    }
    @Test public void genericTopicsNotLinked(){
        assertEquals("",MemorySearch.topicKey("Görüşme"));
        assertEquals("ruhsat takibi",MemorySearch.topicKey("RUHSAT TAKİBİ"));
    }
}
