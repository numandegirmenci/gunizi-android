package com.gunizi.app;

import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Locale;

/** SHA-256 of the actual source bytes: names and provider URIs are not identities. */
final class SourceFingerprint {
    private SourceFingerprint(){}
    static String of(Context context,Uri uri)throws IOException {
        if(uri==null)throw new IOException("Kaynak ses bulunamadı");
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Kaynak ses okunamadı");
            MessageDigest dig=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[64*1024];
            long total=0;int n;
            while((n=in.read(buffer))!=-1){
                if(Thread.currentThread().isInterrupted())
                    throw new IOException("Dosya kontrolü iptal edildi");
                if(n==0)continue;
                total+=n;
                if(total>1024L*1024L*1024L)throw new IOException("Ses 1 GB güvenlik sınırını aşıyor");
                dig.update(buffer,0,n);
            }
            if(total==0)throw new IOException("Ses dosyası boş");
            StringBuilder hex=new StringBuilder(64);
            for(byte b:dig.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
            return hex.toString();
        }catch(IOException e){throw e;}
        catch(Exception e){throw new IOException("Ses kimliği hesaplanamadı",e);}
    }
}