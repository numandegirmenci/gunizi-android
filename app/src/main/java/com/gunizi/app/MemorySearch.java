package com.gunizi.app;

import java.text.Normalizer;
import java.util.*;

/** Local lexical retrieval. Not an embedding model; Turkish-aware normalized terms. */
final class MemorySearch {
    static String normalize(String value) {
        if(value==null)return "";
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace('ı','i'),
            Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    static Set<String> words(String value){
        Set<String> result=new LinkedHashSet<>();
        for(String word:normalize(value).split(" "))if(word.length()>=2)result.add(word);
        return result;
    }
    static int score(String query,String topic,String content,String responsible,String contact){
        Set<String> q=words(query);
        if(q.isEmpty())return 0;
        Set<String> title=words(topic);
        Set<String> all=words(topic+" "+content+" "+responsible+" "+contact);
        int score=0,matched=0;
        for(String term:q){
            if(all.contains(term)){score+=title.contains(term)?5:3;matched++;}
            else if(term.length()>=4){
                for(String word:all)if(word.startsWith(term)){score+=1;matched++;break;}
            }
        }
        if(matched==0)return 0;
        return score+(matched==q.size()?3:0);
    }
    static String topicKey(String topic){
        String n=normalize(topic);
        return n.equals("gorusme")||n.equals("belirtilmedi")||n.length()<3?"":n;
    }
}
