package com.gunizi.app;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import java.io.File;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Fail-closed APK upgrade verification. Never prompt uninstalling the current app.
 * Package name, versionCode, and signer certificate must be compatible.
 */
final class ReleaseApkValidator {
    static final class Result {
        final boolean compatible;
        final String message;
        Result(boolean success, String info){compatible=success;message=info;}
    }

    private static byte[] fingerprint(Signature signature) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());
    }

    private static boolean sameSigner(Signature[] installed,Signature[] incoming) throws Exception {
        if(installed==null||incoming==null||installed.length==0||incoming.length==0)return false;
        if(installed.length!=incoming.length)return false;
        for(Signature a:installed){
            boolean found=false;
            for(Signature b:incoming)if(Arrays.equals(fingerprint(a),fingerprint(b)))found=true;
            if(!found)return false;
        }
        return true;
    }

    static Result verify(Context context,File apk){
        if(apk==null||!apk.isFile()||apk.length()<100_000)
            return new Result(false,"İndirilen APK bulunamadı.");
        try{
            PackageManager pm=context.getPackageManager();
            int flags=PackageManager.GET_SIGNING_CERTIFICATES;
            PackageInfo existing=pm.getPackageInfo(context.getPackageName(),flags);
            PackageInfo next=pm.getPackageArchiveInfo(apk.getAbsolutePath(),flags);
            if(next==null)return new Result(false,"İndirilen APK okunamadı.");
            if(!context.getPackageName().equals(next.packageName))
                return new Result(false,"APK farklı bir uygulamaya ait. Kuruluma izin verilmedi.");
            if(next.getLongVersionCode()<=existing.getLongVersionCode())
                return new Result(false,"İndirilen sürüm kurulu sürümden yeni değil.");
            if(next.signingInfo==null||existing.signingInfo==null)
                return new Result(false,"APK imzası doğrulanamadı. Kurulum durduruldu.");
            Signature[] oldSigners=existing.signingInfo.hasMultipleSigners()
                ? existing.signingInfo.getApkContentsSigners()
                : existing.signingInfo.getSigningCertificateHistory();
            Signature[] newSigners=next.signingInfo.hasMultipleSigners()
                ? next.signingInfo.getApkContentsSigners()
                : next.signingInfo.getSigningCertificateHistory();
            if(!sameSigner(oldSigners,newSigners))
                return new Result(false,
                    "Bu APK farklı bir anahtarla imzalanmış. Uygulamanı silme: "
                    +"mevcut kayıtları ve modelleri koruyarak bu paketin üzerine kurulum yapılamaz. "
                    +"Gelecek sürümler aynı kalıcı anahtarla yayınlanmalıdır.");
            return new Result(true,"Uygulama kimliği, sürüm ve imza uyumlu. Mevcut veriler korunarak güncelleme yapılabilir.");
        }catch(Exception e){
            return new Result(false,"Güncelleme güvenlik denetimi başarısız: "+e.getClass().getSimpleName());
        }
    }
}
