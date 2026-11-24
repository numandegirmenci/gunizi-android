package com.gunizi.app;

import android.content.Context;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded ranked local matches plus exact-topic siblings; each result retains its audio source. */
final class MemoryLibrarySearch {
    static final class Hit {
        final String reportId,name,uri;
        final WorkMemoryStore.Item item;
        final int score;
        final boolean related;
        Hit(WorkMemoryStore.Report report,WorkMemoryStore.Item item,int score,boolean related){
            this.reportId=report.id;this.name=report.name;this.uri=report.sourceUri;
            this.item=item;this.score=score;this.related=related;
        }
    }
    static final class Result {
        final List<Hit> hits=new ArrayList<>();
        int unreadable;
    }
    static Result search(Context c,String query,AtomicBoolean cancelled){
        Result result=new Result();
        List<String> ids=WorkMemoryStore.recent(c);
        Comparator<Hit> rank=Comparator.comparingInt((Hit h)->h.score).reversed();
        PriorityQueue<Hit> best=new PriorityQueue<>(rank.reversed());
        Set<String> failed=new HashSet<>();
        for(String id:ids){
            if(cancelled.get())return result;
            WorkMemoryStore.Report report;
            try{report=WorkMemoryStore.load(c,id);}catch(Exception e){failed.add(id);continue;}
            for(WorkMemoryStore.Item item:report.items){
                if(cancelled.get())return result;
                int score=MemorySearch.score(query,item.topic,item.content,item.responsible,item.contact);
                if(score<=0)continue;
                Hit hit=new Hit(report,item,score,false);
                if(best.size()<30)best.add(hit);
                else if(score>best.peek().score){best.poll();best.add(hit);}
            }
        }
        result.unreadable=failed.size();
        result.hits.addAll(best);result.hits.sort(rank);
        Set<String> seedTopics=new HashSet<>(),seedIds=new HashSet<>(),seedReports=new HashSet<>();
        for(Hit hit:result.hits){
            seedIds.add(hit.reportId+":"+hit.item.id);seedReports.add(hit.reportId);
            String topic=MemorySearch.topicKey(hit.item.topic);if(!topic.isEmpty())seedTopics.add(topic);
        }
        if(seedTopics.isEmpty())return result;
        int related=0;
        for(String id:ids){
            if(cancelled.get()||related>=12)break;
            if(failed.contains(id))continue;
            WorkMemoryStore.Report report;
            try{report=WorkMemoryStore.load(c,id);}catch(Exception e){failed.add(id);continue;}
            for(WorkMemoryStore.Item item:report.items){
                if(cancelled.get()||related>=12)break;
                if(seedIds.contains(id+":"+item.id)||!seedTopics.contains(MemorySearch.topicKey(item.topic)))continue;
                // Prefer new recordings, not more rows from the same seed report.
                if(seedReports.contains(id))continue;
                result.hits.add(new Hit(report,item,0,true));related++;
            }
        }
        result.unreadable=failed.size();
        return result;
    }
}
