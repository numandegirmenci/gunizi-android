package com.gunizi.app;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;

/** Uses persistable Storage Access Framework folder grants. */
final class ExportManager {
    static Uri writeFile(Context c,Uri folder,File src,String name,String mime) throws IOException {
        if(folder==null)throw new IOException("Kayıt klasörü seçilmedi");
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(folder,DocumentsContract.getTreeDocumentId(folder));
        Uri out=DocumentsContract.createDocument(c.getContentResolver(),parent,mime,name);
        if(out==null)throw new IOException("Klasörde dosya oluşturulamadı");
        boolean success=false;
        try(InputStream in=new FileInputStream(src);
            OutputStream os=c.getContentResolver().openOutputStream(out,"w")){
            if(os==null)throw new IOException("Dosya yazma izni yok");
            byte[] buf=new byte[65536]; int n;
            while((n=in.read(buf))!=-1)os.write(buf,0,n);
            os.flush();success=true;return out;
        } finally{
            if(!success)try{DocumentsContract.deleteDocument(c.getContentResolver(),out);}catch(Exception ignored){}
        }
    }
    static Uri writeWav(Context c,Uri folder,byte[] pcm,String name) throws IOException {
        if(folder==null)throw new IOException("Kayıt klasörü seçilmedi");
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(folder,DocumentsContract.getTreeDocumentId(folder));
        Uri out=DocumentsContract.createDocument(c.getContentResolver(),parent,"audio/wav",name);
        if(out==null)throw new IOException("WAV oluşturulamadı");
        boolean success=false;
        try(OutputStream os=c.getContentResolver().openOutputStream(out,"w")){
            if(os==null)throw new IOException("Dosya akışı açılamadı");
            writeWavHeader(os,pcm.length,16000,1,16);
            os.write(pcm);os.flush();success=true;return out;
        }finally{
            if(!success)try{DocumentsContract.deleteDocument(c.getContentResolver(),out);}catch(Exception ignored){}
        }
    }
    private static void writeWavHeader(OutputStream out,int bytes,int hz,int ch,int bits)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream(44);
        b.write(new byte[]{'R','I','F','F'});le32(b,36L+bytes);
        b.write(new byte[]{'W','A','V','E','f','m','t',' '});le32(b,16);le16(b,1);
        le16(b,ch);le32(b,hz);le32(b,(long)hz*ch*bits/8);
        le16(b,ch*bits/8);le16(b,bits);
        b.write(new byte[]{'d','a','t','a'});le32(b,bytes);out.write(b.toByteArray());
    }
    private static void le16(OutputStream o,int x)throws IOException{o.write(x&255);o.write((x>>>8)&255);}
    private static void le32(OutputStream o,long x)throws IOException{for(int i=0;i<4;i++)o.write((int)(x>>>(i*8))&255);}
}
