# Worq & Travel Android

Bu proje GitHub Actions ile doğrudan kurulabilir Android APK üretir.

## Özellikler
- Native Android 5 saniyelik tam ekran açılış görseli
- 5.970 İstanbul firma kaydı
- Performanslı ilçe / cluster haritası
- GROW mavi, diğer firmalar kırmızı
- Dokunmatik firma pinleri ve firma bilgi kartı
- Firma adı ekranda; Google Maps'e yalnızca adres metni gider
- Toplu ilçe rotası ve rota parçalama
- Gerçek Android GPS izni
- Haritada mavi mevcut konum noktası ve doğruluk çemberi
- Konumuma Git butonu

## GitHub Actions
Repo'ya push edildiğinde `.github/workflows/build-apk.yml` otomatik çalışır.

Başarılı build sonrası:
Actions > ilgili çalışma > Artifacts > `Worq-Travel-Android-APK`

İndirilen ZIP içindeki `Worq-Travel.apk` telefona kurulabilir.

Not: Bu sürümün applicationId'si `com.worq.travel.mobile` olduğu için önceki deneme APK'larıyla imza çakışması yaşamadan ayrı uygulama olarak kurulabilir.
