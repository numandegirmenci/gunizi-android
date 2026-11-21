package com.gunizi.app;

/** Fixed-size, thread-safe 16-bit PCM ring buffer. Preserves most recent samples. */
final class PcmRingBuffer {
    private final byte[] ring;
    private int position=0, size=0;
    PcmRingBuffer(int maxBytes) { ring=new byte[maxBytes]; }
    synchronized void write(byte[] src,int offset,int count) {
        if(count>=ring.length){offset+=count-ring.length;count=ring.length;}
        int a=Math.min(count,ring.length-position);
        System.arraycopy(src,offset,ring,position,a);
        if(count>a)System.arraycopy(src,offset+a,ring,0,count-a);
        position=(position+count)%ring.length;
        size=Math.min(ring.length,size+count);
    }
    synchronized byte[] snapshot() {
        byte[] result=new byte[size];
        int oldest=(position-size+ring.length)%ring.length;
        int first=Math.min(size,ring.length-oldest);
        System.arraycopy(ring,oldest,result,0,first);
        if(first<size)System.arraycopy(ring,0,result,first,size-first);
        return result;
    }
    synchronized int size() { return size; }
}
