package com.gunizi.app;
import java.io.IOException;
import java.io.OutputStream;
/** Chunked, bounded, lazy-allocated rolling PCM buffer: up to ~2 h 20 m at 16 kHz. */
final class PcmRingBuffer {
    private static final int CHUNK=1024*1024;
    private final byte[][] chunks;
    private final int capacity;
    private int position=0,size=0;
    PcmRingBuffer(int maxBytes){
        capacity=Math.max(32000,maxBytes);
        chunks=new byte[(capacity+CHUNK-1)/CHUNK][];
    }
    int capacitySeconds(){return capacity/32000;}
    synchronized int size(){return size;}
    private byte[] page(int index){if(chunks[index]==null)chunks[index]=new byte[CHUNK];return chunks[index];}
    synchronized void write(byte[] src,int offset,int count){
        if(count<=0)return;
        if(count>=capacity){offset+=count-capacity;count=capacity;}
        int remain=count;
        while(remain>0){
            int idx=position/CHUNK,within=position%CHUNK;
            int n=Math.min(remain,Math.min(CHUNK-within,capacity-position));
            System.arraycopy(src,offset,page(idx),within,n);
            offset+=n;remain-=n;position=(position+n)%capacity;
        }
        size=Math.min(capacity,size+count);
    }
    /** Stream the memory directly; no 256 MB duplicate snapshot. Capture may pause while exporting. */
    void stream(OutputStream out)throws IOException{
        final int initial,bytes;
        synchronized(this){
            bytes=size;
            initial=(position-size+capacity)%capacity;
        }
        // Copy only 64 KiB at a time under lock so microphone reads are not blocked by slow storage.
        byte[] scratch=new byte[64*1024];
        int oldest=initial,pending=bytes;
        while(pending>0){
            int idx=oldest/CHUNK,within=oldest%CHUNK;
            int n=Math.min(scratch.length,Math.min(pending,
                Math.min(CHUNK-within,capacity-oldest)));
            synchronized(this){
                if(chunks[idx]==null)throw new IOException("Ses tamponu eksik");
                System.arraycopy(chunks[idx],within,scratch,0,n);
            }
            out.write(scratch,0,n);
            oldest=(oldest+n)%capacity;pending-=n;
        }
    }
}
