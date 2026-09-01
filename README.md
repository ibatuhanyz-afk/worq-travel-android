# Worq & Travel Android — Türkiye V3

Bu proje GitHub Actions ile doğrudan kurulabilir Android APK üretir.

## Özellikler
- Native Android 5 saniyelik tam ekran açılış görseli
- `Maps_V2.xlsx` kaynağından 11.465 Türkiye firma kaydı
- Türkiye > il > ilçe > firma şeklinde performanslı harita/küme görünümü
- GROW / GROW_PLC mavi, diğer firmalar kırmızı
- Dokunmatik firma pinleri, küme firma listesi ve firma bilgi kartı
- Firma adı ekranda; Google Maps arama ve rota sorgularına yalnızca `Organization - Address` gönderilir
- İl/ilçe filtreleri, arama, durum ve segment filtreleri
- Toplu ilçe rotası ve Google Maps rota parçalama
- Gerçek Android GPS izni, mavi mevcut konum noktası ve doğruluk çemberi
- Firma uzaklığı yalnızca kilometre olarak gösterilir; süre tahmini yoktur
- Eski İstanbul verisinde aynı adresi bulunan 5.790 kaydın mevcut koordinatı korunur
- Yeni adreslerin hassas koordinatı Android Geocoder ile ihtiyaç halinde adres üzerinden bulunup cihazda önbelleğe alınır

## GitHub Actions
Repo'ya push edildiğinde `.github/workflows/build-apk.yml` otomatik çalışır.

Başarılı build sonrası:
Actions > ilgili çalışma > Artifacts > `Worq-Travel-Android-APK`

İndirilen ZIP içindeki `Worq-Travel.apk` telefona kurulabilir.

Uygulama kimliği: `com.worq.travel.mobile`
Sürüm: `3.0-turkiye-distance` (versionCode 30)
