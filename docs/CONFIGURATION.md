# CONFIGURATION.md - PetSistemi Yapılandırma Rehberi

```yaml
# Dil ve Lokasyon Yapılandırması
locale: 'tr_TR'

# Limitler
limits:
  maximum-owned-pets: 20

# İsimlendirme Kuralları
naming:
  minimum-length: 2
  maximum-length: 16
  allow-colors: false
  allow-formatting: false

# Gelişim ve Tecrübe (Progression)
progression:
  enabled: true
  maximum-level: 100

# Runtime Varlık Davranışları
runtime:
  tick-interval-ticks: 5
  start-distance: 5.0
  stop-distance: 2.0
  teleport-distance: 15.0
  follow-speed: 1.2

# Veritabanı ve Otomatik Yüzey Yedeği
database:
  backend: SQLITE # MYSQL network/paylaşımlı veri için
  mysql:
    host: 127.0.0.1
    port: 3306
    database: petsistemi
    username: root
    password: ""
    use-ssl: false
    connect-timeout-ms: 10000
  migration-backup:
    enabled: true
    fail-startup-on-backup-error: true
    maximum-backups: 5

# MF8 Ekosistemi
ecosystem:
  network:
    enabled: false
    server-id: server-1 # Aynı DB'deki her sunucuda benzersiz
    poll-interval-ticks: 20
    batch-size: 100
    retention-hours: 24
  pet-packs:
    maximum-files: 128
    maximum-archive-bytes: 10485760
    maximum-expanded-bytes: 52428800
  marketplace:
    enabled: false
    catalog-url: ""
    require-sha256: true
    maximum-download-bytes: 10485760
    request-timeout-ms: 10000

# Özellik Bayrakları
features:
  abilities:
    enabled: false
  particles:
    enabled: true
  magnet:
    enabled: false
  riding:
    enabled: false

  # Idle/Sleep: sahibi idle-seconds saniye hareket etmezse pet oturur/dinlenir
  idle-sleep:
    enabled: false
    idle-seconds: 45

  # Reaction'lar: hasar/level-up/rest geçişlerinde ses + parçacık
  reactions:
    enabled: false

  # Level Scaling: display petler seviyeyle büyür (sadece display representation'lar)
  level-scaling:
    enabled: false
    growth-per-level: 0.02
    max-multiplier: 1.5
```

Network modu yalnız MySQL ile açılır. Pet Pack ve marketplace ayrıntıları için
[ECOSYSTEM.md](ECOSYSTEM.md) belgesine bakın.
