# Worq & Travel Android — 4.669 Firma / Pin Stability Fix

Bu sürüm GitHub Actions ile APK üretmek için hazırlanmıştır.

## Veri
- Yalnızca son CSV'deki 4.669 firma vardır.
- Firma verilerinde soru işaretine dönüşmüş Türkçe karakter yoktur.
- Eski 11.465 firma datası yüklenmez.
- Google Maps'e yalnızca firma adres alanı gönderilir.

## Pin bilgi kartı düzeltmesi
- Firma pinleri pointer-down sırasında sabitlenir; arka plandaki geocoding işlemleri dokunma sırasında marker katmanını yeniden oluşturmaz.
- Native geocoder sonuçları tek tek marker katmanını silip yeniden çizmez; aktif geocoding kuyruğu tamamlanınca tek seferde yenilenir.
- Android WebView için firma markerına Leaflet click yanında doğrudan pointer-up dokunma olayı da bağlanmıştır.
- Aynı firma için art arda oluşan click/pointer olayları çift açılmayı önlemek için kısa süreli tekilleştirilir.
- Cluster tıklamasından sonra otomatik yakınlaştırma/ayrıştırma devam eder.
- Firma kartında Google Maps'te Aç düğmesi vardır.

Uygulama kimliği: `com.worq.travel.firmas`
Sürüm: `3.1.4-github-pin-stability` (versionCode 35)
