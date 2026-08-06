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
  migration-backup:
    enabled: true
    fail-startup-on-backup-error: true
    maximum-backups: 5

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
