# Günİzi Android • v0.2 (deneysel)

Türkçe, çevrimdışı odaklı Android ses kayıt uygulaması. Gecko'nun RAM'de döngüsel ses belleği yaklaşımı temel alınarak **ayrı bir prototip** olarak geliştirilmiştir.

## İki çalışma modu

**Sürekli Kayıt (Gecko tarzı):**
- 16 kHz, mono, 16 bit ses, parçalı ve döngüsel RAM tamponunda tutulur.
- Hedef 256 MiB = yaklaşık **2 saat 19 dakika** geriye dönük ses. Düşük Java heap sınırı varsa %60 ile sınırlanır; gerçek kapasite uygulamada gösterilir.
- Kullanıcı istediğinde geçmişi seçtiği klasöre WAV olarak aktarır. 2 saatten uzun WAV dosyası **yaklaşık 270 MB** olabilir.
- RAM belleği kalıcı arşiv değildir; sistem kapatırsa kaybolabilir.
- Her 24 saati otomatik kalıcı kaydetmez. 24 saatlik **günlük özet yalnızca elde kayıt varsa** mümkündür.

**Mesai Kaydı:**
- Çalışma günleri, başlangıç / bitiş saatleri ve klasör bir kez seçilir.
- Android izin veriyorsa, **kullanıcı tarafından önceden başlatılmış mikrofon servisi açıkken** kayıt otomatik başlar ve biter.
- Gün sonunda 16 kbps mono Opus/OGG dosyası seçilen klasöre aktarılır.
- Tamamlanmış OGG için Android Keystore / AES-GCM ile şifreli koruma ve kurtarma işlemi yapılır. **Aktif dosyanın ve son 5 dakikanın sürekli şifreli koruması henüz yok**.

## Yerel Türkçe konuşma tanıma ve günlük özet

- **Vosk small-tr-0.3** (~35 MB sıkıştırılmış, Apache 2.0) Türkçe modelini kullanıcı **bir defa açıkça** indirir: <https://alphacephei.com/vosk/models>.
- Model kurulduktan sonra ses kaydı, döküm ve özet **cihazdan dışarı aktarılmadan** işlenir.
- Mesai kaydı tamamlandığında, şarj ve pil koşulları uygunsa Android WorkManager günlük rapor oluşturmak üzere işi sıraya alır.
- Günİzi > Yerel yapay zeka ekranında seçilmiş başka bir ses kaydı da raporlanabilir.
- Raporda **özet + 30 dakikalık akış + takip edilebilecek ifadeler + eksiksiz algılanmış konuşma dökümü** .txt dosyasına yazılır.
- Özetleme şu anda **alıntı temelli / çıkarımsal**; **üretken dil modeli (LLM) içermez**. Konuşmayı yazıya çeviren bölüm gerçek yerel makine öğrenmesi modelidir.
- Modelin Türkçe özel isimlerde, birden fazla kişinin konuşmasında ve gürültüde hata yapması mümkündür; kayıttan çıkarılmayan bilgileri üretmeye çalışmaz.
- Saatler ses dosyasının başlangıcına göre yazılır. Konuşma olmayan aralıklar veya eksik ses yakalama durumları kayıtta görünmeyebilir.

## Pil tüketimi karşılaştırması

- Ana ekrandaki **Pil ve Performans** alanından iki modun kayıt süreleri ve teorik ses boyutları karşılaştırılabilir.
- Kaydırıcılı pil hesabı **varsayımsaldır**. Uygulamaya ait gerçek enerji ölçümü olarak sunulmaz.
- Gerçek tüketim hedef cihazda kontrollü testlerle belirlenmelidir.
- Şarjda özetleme varsayılanı, gün içinde ağır yapay zeka işinin pil tüketmesini azaltmayı amaçlar.

## İzinler ve güvenlik

- **Mikrofon:** çalışma için gerekli; kullanıcı uygulama açıkken izin verir ve kayıt hizmetini başlatır.
- **Bildirim:** mikrofon hizmetini ve kayıt/özetleme durumunu görmek için; Android 13+ ayarı.
- **Kayıt klasörü:** Android'in klasör seçicisiyle tek seferlik ve kalıcı SAF erişimi.
- **İnternet:** yalnızca kullanıcının başlattığı Türkçe model indirmesi; ses ve döküm sunucuya gönderilmez.
- **İsteğe bağlı Samsung pil ayarı:** uygulama sistem pil optimizasyon ayarlarına yönlendirebilir. Herhangi bir izni sessizce vermez.
- Android 14+ arka plandan mikrofon foreground service başlatma kısıtlaması **aşılamaz**. Servis sistem tarafından kapatıldığında otomatik başlangıç garanti edilmez.
- Aynı anda Gecko ve Günİzi mikrofon kullanıyorsa ses erişimi rekabeti yaşanabilir. Test sırasında tek uygulama önerilir.
- Diğer kişilerin konuşmalarını kaydetmeden önce ilgili kişileri bilgilendirin ve gerekli izinleri alın.

## APK

GitHub Actions > **Android Debug APK** > son başarılı işlem > **Artifacts** > `GunIzi-debug-apk`.

Bu **debug/test APK**'sıdır; cihaz üstünde uzun süreli ve hukuki kritik kayıtlarda güvenilirlik doğrulanmadı. Özel telefon ve Android sürümlerine bağlı sorunlar olabilir.

## Kaynak ve krediler

- Gecko: <https://github.com/anantdark/Gecko>
- Vosk: <https://alphacephei.com/vosk/>
- Android background microphone foreground service kısıtlamaları: <https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start>
