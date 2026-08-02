# MANUAL-TEST-MATRIX.md - Manuel Test Matrisi

## Kurulum
- [ ] Temiz data klasörü ile başlama
- [ ] Bozuk config dosyası ile başlama (Fail-fast doğrulanmalı)
- [ ] Geçersiz locale tanımı ile başlama
- [ ] Hatalı pet YAML dosyası (Diğer geçerli petlerin yüklenmesi doğrulanmalı)
- [ ] SQLite dosyasına erişilememe durumu

## Migration
- [ ] Fresh database kurulumu
- [ ] V1 database upgrade
- [ ] V2 database upgrade
- [ ] V3 database upgrade
- [ ] V3 + DISABLED selected pet upgrade
- [ ] V3 + orphan ACTIVE pet upgrade
- [ ] V3 + imposter selection upgrade
- [ ] İkinci migration çalıştırması (Idempotency)

## Lifecycle
- [ ] `/pet` GUI üzerinden çağırma (Summon)
- [ ] Üst üste hızlı summon denemesi (Exploit engelleme)
- [ ] Pet değiştirme (Switch)
- [ ] Pet kaldırma (Dismiss)
- [ ] Oyuncu oyundan ayrılma/girme (Quit/Join)
- [ ] Chunk unload & reload
- [ ] Sunucu restart sonrası pet restore

## GUI & Admin
- [ ] 0 pet durumunda GUI görünümü
- [ ] Pagination (20+ pet durumunda sayfa geçişleri)
- [ ] Inventory shift-click / drag engelleme
- [ ] `/petadmin inspect <player>`
- [ ] `/petadmin reconcile all --dry-run`
- [ ] `/petadmin backup`
