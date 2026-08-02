# ADR 0004: Değiştirilemez (Immutable) Migration Yönetimi

## Durum
Kabul Edildi

## Bağlam
Daha önce uygulanmış veritabanı migration kodlarının değiştirilmesi, halihazırda o migration sürümünü çalıştırmış canlı veritabanlarında yeni güncellemelerin atlanmasına neden olmaktadır.

## Karar
Uygulanmış migration sınıfları (`V1`, `V2`, `V3`...) değiştirilemez tarihsel sınıflar olarak dondurulmuştur. Her yeni veri modeli düzeltmesi veya uzlaştırması strictly artan sürüm numarasına sahip yeni bir migration sınıfı (`V4`, `V5`, `V6`...) ile eklenecektir.

## Sonuçlar
- Canlı ve eski veritabanı yükseltmeleri (upgrades) %100 öngörülebilir ve güvenilir hâle gelir.
- `schema_migrations` tablosu veritabanının gerçek şema geçmişini yansıtır.
