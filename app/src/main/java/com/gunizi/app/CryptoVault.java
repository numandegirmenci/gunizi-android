package com.gunizi.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** AES-GCM backup of a COMPLETED session. Active recordings are not encrypted. */
final class CryptoVault {
    private static final String ALIAS="gunizi_temp_backup_v1";
    private static final byte[] MAGIC=new byte[]{'G','I','Z','1'};
    private static SecretKey key()throws Exception{
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))
            return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return kg.generateKey();
    }
    static File encrypt(Context context,File source)throws Exception{
        return encryptNamed(context,source,source.getName());
    }
    static File encryptNamed(Context context,File source,String name)throws Exception{
        return encryptIn(context,source,"encrypted_pending",name);
    }
    static File encryptEphemeral(Context context,File source)throws Exception{
        return encryptIn(context,source,"encrypted_rolling_history",source.getName());
    }
    private static File encryptIn(Context context,File source,String folder,String name)throws Exception{
        File dir=new File(context.getNoBackupFilesDir(),folder);
        if(!dir.exists()&&!dir.mkdirs())throw new IOException("Yedek klasörü oluşturulamadı");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        String ext=RecordingOptions.extensionForName(name);
        File target=new File(dir,name+".giz");
        for(int i=2;!target.createNewFile();i++){
            target=new File(dir,name.substring(0,name.length()-ext.length())+"_"+i+ext+".giz");
        }
        try(InputStream input=new FileInputStream(source);
            FileOutputStream raw=new FileOutputStream(target)){
            raw.write(MAGIC);raw.write(iv.length);raw.write(iv);
            try(CipherOutputStream output=new CipherOutputStream(raw,cipher)){
                byte[] buf=new byte[65536];int n;
                while((n=input.read(buf))!=-1)output.write(buf,0,n);
            }
        }catch(Exception e){target.delete();throw e;}
        return target;
    }
    static File decryptToTemp(Context context,File encrypted)throws Exception{
        String name=encrypted.getName().replaceFirst("\\.giz$","");
        File tmp=File.createTempFile("gunizi_restore_",RecordingOptions.extensionForName(name),context.getCacheDir());
        try(InputStream raw=new FileInputStream(encrypted)){
            byte[] header=new byte[4];
            new DataInputStream(raw).readFully(header);
            if(!Arrays.equals(header,MAGIC))throw new IOException("Geçersiz yedek biçimi");
            int len=raw.read();
            if(len<12||len>16)throw new IOException("Geçersiz şifreleme verisi");
            byte[] iv=new byte[len];new DataInputStream(raw).readFully(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            try(CipherInputStream input=new CipherInputStream(raw,cipher);
                FileOutputStream output=new FileOutputStream(tmp)){
                byte[] buf=new byte[65536];int n;
                while((n=input.read(buf))!=-1)output.write(buf,0,n);
            }
            return tmp;
        }catch(Exception e){tmp.delete();throw e;}
    }
}
