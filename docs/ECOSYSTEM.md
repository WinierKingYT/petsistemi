# MF8 Ekosistem Rehberi

MF8; seçilebilir MySQL kalıcılığı, aynı veritabanını kullanan sunucular arasında pet
senkronizasyonu, sürümlü Pet Pack arşivleri ve doğrulanmış marketplace indirmelerini kapsar.

## MySQL ve network modu

Her sunucu aynı MySQL veritabanını kullanmalı, fakat benzersiz bir `server-id` taşımalıdır:

```yaml
database:
  backend: MYSQL
  mysql:
    host: 127.0.0.1
    port: 3306
    database: petsistemi
    username: petsistemi
    password: "gizli"
    use-ssl: true
    connect-timeout-ms: 10000

ecosystem:
  network:
    enabled: true
    server-id: lobby-1
    poll-interval-ticks: 20
    batch-size: 100
    retention-hours: 24
```

Plugin açılışta MySQL şemasını V9'a getirir. Değişiklikler sıralı event günlüğüne yazılır;
uzak sunucular profil önbelleğini temizler ve çevrimiçi oyuncunun seçili pet runtime'ını
yeniler. MySQL `GET_LOCK` kilitleri aynı sahip/pet üzerindeki eşzamanlı network yazmalarını
serialize eder. Network modu yalnızca `database.backend: MYSQL` ile açılabilir.

SQLite migration yedeği ve `/petadmin backup` dosya kopyası MySQL için çalıştırılmaz.
MySQL yedekleri sağlayıcının snapshot veya `mysqldump` mekanizmasıyla alınmalıdır. SQLite
verisi otomatik olarak MySQL'e kopyalanmaz; geçiş öncesinde bakım penceresinde kontrollü
ETL/import yapılmalıdır.

## Pet Pack biçimi

`.petpack`, kökünde `pack.yml` ve `pets/*.yml` taşıyan ZIP arşividir:

```yaml
schema-version: 1
id: forest-pack
namespace: forest
version: 1.0.0
minimum-engine-version: 0.2.0
description: Orman dostları
authors: [Faruk]
dependencies: []
```

`pets/fox.yml` kurulurken `forest:fox` kimliğini alır. Arşiv yolu, dosya sayısı, sıkıştırılmış
ve açılmış boyut, SemVer, motor sürümü, bağımlılıklar ve bütün çapraz pet referansları
yayından önce doğrulanır. Kurulum atomiktir; başarısız doğrulama dosyaları ve registry
snapshot'ını geri alır. Yükseltme artık paketten çıkarılmış eski tanımları temizler;
bağımlısı bulunan paket kaldırılamaz.

- Gelen arşiv: `plugins/PetSistemi/packs/inbox/`
- Kurulum receipt'leri: `plugins/PetSistemi/packs/installed/`
- Dışa aktarılanlar: `plugins/PetSistemi/packs/exports/`
- İndirme geçicileri: `plugins/PetSistemi/packs/downloads/`

Komutlar:

- `/petadmin pack list`
- `/petadmin pack install <dosya.petpack>`
- `/petadmin pack uninstall <pack_id>`
- `/petadmin pack export <id> <namespace> <version> <pet_id...>`

Üçüncü taraf eklentiler Bukkit Services Manager'dan `PetPackService` alabilir.

## Marketplace

Marketplace varsayılan olarak kapalıdır. Katalog HTTPS olmalıdır; yalnızca yerel testler
için loopback HTTP kabul edilir.

```yaml
ecosystem:
  marketplace:
    enabled: true
    catalog-url: https://example.org/pets/catalog.yml
    require-sha256: true
    maximum-download-bytes: 10485760
    request-timeout-ms: 10000
```

Katalog `schema-version: 1` ve bir `entries` listesi taşır. Her girişte `id`, SemVer
`version`, `download-url` ve normal politikada 64 haneli `sha256` bulunur. Katalog 1 MiB,
paket yapılandırılmış indirme boyutuyla sınırlıdır. Yönlendirme sonrası nihai URL tekrar
doğrulanır; sağlanan checksum, politika zorunlu olmasa bile kontrol edilir.

- `/petadmin marketplace refresh`
- `/petadmin marketplace list`
- `/petadmin marketplace install <id>`

API tüketicileri `PetNetworkSyncService`, `PetPackService` ve etkinse
`PetMarketplaceService` hizmetlerini Bukkit Services Manager üzerinden alabilir.
