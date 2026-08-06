# PET-DEFINITIONS.md - Pet Tanımlama Rehberi

Tüm pet tanımları `plugins/PetSistemi/pets/` klasöründe `.yml` dosyaları halinde saklanır.
Dosya adı (`.yml` uzantısı hariç) pet kimliği (`id`) olur.

## Şema Sürümü

Her dosya `schema-version: 1` ile başlamalıdır.

## Klasik Mob Pet (Legacy — geriye dönük uyumlu)

Eski düz anahtarlar desteklenmeye devam eder ve bir **ENTITY + GROUND_FOLLOW** pet'e eşlenir.

```yaml
schema-version: 1
display-name: "<gold>Kurt Dostu</gold>"
description:
  - "<gray>Sadık ve cesur bir kurt arkadaş.</gray>"
gui-material: WOLF_SPAWN_EGG
entity-type: WOLF
baby: false
glowing: false
invulnerable: true
silent: false
gravity: true
progression:
  enabled: true
  maximum-level: 100
nameplate:
  enabled: true
  format:
    - "<gold>{pet_name}</gold> <gray>Lv.{level}</gray>"
permission: companionpets.pet.wolf
```

Legacy dosyada `movement` bölümü yoksa takip davranışı `config.yml`'deki
`runtime.*` değerleriyle yönetilir (eski davranış aynen korunur).

## Yeni Modüler Şema (representation + movement)

Bir pet iki bağımsız parçadan oluşur:

- **`representation`**: petin *görünümü* (mob, item-display, ...)
- **`movement`**: petin *hareketi* (yürüme, süzülme, yörünge, ...)

`representation` veya `movement` bölümü varsa legacy düz anahtarları ezilir.

### Örnek 1 — Yörüngede dönen kristal (ITEM_DISPLAY + ORBIT)

```yaml
schema-version: 1
display-name: "<dark_purple>Arcane Crystal</dark_purple>"
description:
  - "<light_purple>Etrafınızda dönen büyülü bir kristal.</light_purple>"
gui-material: AMETHYST_SHARD
permission: companionpets.pet.arcanecrystal
representation:
  type: ITEM_DISPLAY
  item-material: AMETHYST_SHARD
  custom-model-data: 12001
  scale:
    x: 1.2
    y: 1.2
    z: 1.2
  glowing: true
movement:
  type: ORBIT
  orbit:
    radius: 1.7
    height: 1.4
    angular-speed: 1.2
    clockwise: true
  update-interval-ticks: 5
progression:
  enabled: true
  maximum-level: 100
nameplate:
  enabled: true
  format:
    - "<light_purple>{pet_name}</light_purple> <gray>Lv.{level}</gray>"
```

### Örnek 2 — Omuz üzerinde süzülen kitap (ITEM_DISPLAY + FLYING_FOLLOW)

```yaml
schema-version: 1
display-name: "<aqua>Floating Tome</aqua>"
gui-material: BOOK
permission: companionpets.pet.floatingbook
representation:
  type: ITEM_DISPLAY
  item-material: ENCHANTED_BOOK
movement:
  type: FLYING_FOLLOW
  height: 1.5
  side-offset: 1.1
  follow-speed: 0.18
  teleport-distance: 24.0
  update-interval-ticks: 5
```

### Örnek 3 — Omuza sabitlenmiş kristal (ITEM_DISPLAY + ANCHORED)

```yaml
schema-version: 1
display-name: "<light_purple>Shoulder Orb</light_purple>"
gui-material: END_CRYSTAL
permission: companionpets.pet.shoulderorb
representation:
  type: ITEM_DISPLAY
  item-material: END_CRYSTAL
  scale:
    x: 0.6
    y: 0.6
    z: 0.6
movement:
  type: ANCHORED
  anchor:
    position: RIGHT_SHOULDER
    distance: 1.8
    height: 0.6
    rotate-with-owner: true
  update-interval-ticks: 5
```

### Örnek 4 — İz bırakan ruh yazmanı (TEXT_DISPLAY + TRAIL)

```yaml
schema-version: 1
display-name: "<gray>Ghost Scribe</gray>"
gui-material: NAME_TAG
permission: companionpets.pet.ghostscribe
representation:
  type: TEXT_DISPLAY
  scale:
    x: 0.9
    y: 0.9
    z: 0.9
movement:
  type: TRAIL
  follow-distance: 6.0
  teleport-distance: 30.0
  update-interval-ticks: 5
nameplate:
  enabled: false
```

### Örnek 5 — Alev aurası (PARTICLE + HOVER)

```yaml
schema-version: 1
display-name: "<dark_red>Spirit Flame</dark_red>"
gui-material: BLAZE_POWDER
permission: companionpets.pet.spiritflame
representation:
  type: PARTICLE
  particle-type: SOUL_FIRE_FLAME
  particle-count: 6
  particle-offset: 0.4
  particle-speed: 0.02
movement:
  type: HOVER
  height: 1.8
  update-interval-ticks: 5
nameplate:
  enabled: false
```

### Örnek 6 — Formasyon takip eden çiçek sürüsü (MULTI_ENTITY + FORMATION)

```yaml
schema-version: 1
display-name: "<gold>Familiar Swarm</gold>"
gui-material: ALLIUM
permission: companionpets.pet.familiarswarm
representation:
  type: MULTI_ENTITY
  item-material: ALLIUM
  child-count: 3
  child-material: POPPY
  scale:
    x: 0.7
    y: 0.7
    z: 0.7
movement:
  type: FORMATION
  update-interval-ticks: 5
```

### Örnek 7 — Yüzen obsidyen küp (BLOCK_DISPLAY + HOVER)

```yaml
schema-version: 1
display-name: "<dark_purple>Void Cube</dark_purple>"
gui-material: CRYING_OBSIDIAN
permission: companionpets.pet.voidcube
representation:
  type: BLOCK_DISPLAY
  block-material: CRYING_OBSIDIAN
movement:
  type: HOVER
  height: 2.2
  update-interval-ticks: 5
```

### Örnek 8 — Per-pet durumlar: uyuyan kedi (ENTITY + GROUND_FOLLOW + states)

`states` bölümü pet'e özel boşta kalma/sleep davranışı tanımlar. IDLE bölümü
tanımlıysa bu pet için idle-sleep, global `features.idle-sleep.enabled` bayrağından
bağımsız olarak **açıktır** ve `after-ticks` (oyun tick'i, 20 tps) global
`idle-seconds` değerini **ezer**. MOVING animasyonu şu an yalnızca şema olarak
saklanır/doğrulanır (runtime'da doğal mob animasyonu kullanılır).

```yaml
schema-version: 1
display-name: "<yellow>Uyuyan Kedi</yellow>"
description:
  - "<gray>Sahibi uzun süre durursa kıvrılıp uyuyan kedi dostu.</gray>"
gui-material: CAT_SPAWN_EGG
permission: companionpets.pet.sleepycat
representation:
  type: ENTITY
  entity-type: CAT
  baby: true
  silent: true
movement:
  type: GROUND_FOLLOW
  follow-distance: 2.5
  teleport-distance: 24.0
states:
  MOVING:
    animation: WALK
  IDLE:
    after-ticks: 100
    animation: SLEEP
reactions:
  OWNER_DAMAGE:
    sound: ENTITY_CAT_HISS
    particle: VILLAGER_ANGRY
    particle-count: 4
    volume: 0.9
  LEVEL_UP:
    particle: VILLAGER_HAPPY
    particle-count: 10
emotes:
  purr:
    sound: ENTITY_CAT_PURR
    particle: HEART
    particle-count: 5
    cooldown-seconds: 10
  hiss:
    sound: ENTITY_CAT_HISS
    particle: SMOKE
    particle-count: 6
    cooldown-seconds: 5
progression:
  enabled: true
  maximum-level: 100
nameplate:
  enabled: true
  format:
    - "<yellow>{pet_name}</yellow> <gray>Lv.{level}</gray>"
```

### Örnek 9 — Çevre dönüşümü: wisplight (ITEM_DISPLAY + HOVER + transforms)

`transforms` bölümü pet'in görünümünü ortam koşullarına göre değiştirir. Her
transform bir `when` (koşullar) ve bir `apply` (görsel değişim) içerir. Tüm
koşullar eşleştiğinde `apply` alanları base representation'ın üzerine yazılır;
eşleşme yoksa base görünüm kullanılır. `PetInstance` (isim, seviye, mod) korunur.

```yaml
schema-version: 1
display-name: "<yellow>Wisplight</yellow>"
description:
  - "<gray>Gece feneri, gündüz toz zerresi.</gray>"
gui-material: GLOWSTONE_DUST
permission: companionpets.pet.wisplight
representation:
  type: ITEM_DISPLAY
  item-material: GLOWSTONE_DUST
  scale:
    x: 0.7
    y: 0.7
    z: 0.7
movement:
  type: HOVER
  height: 1.6
  teleport-distance: 24.0
  update-interval-ticks: 5
transforms:
  night:
    when:
      time-of-day: NIGHT
    apply:
      representation:
        item-material: SOUL_LANTERN
        glowing: true
        scale:
          x: 0.9
          y: 0.9
          z: 0.9
  water:
    when:
      owner-state: IN_WATER
    apply:
      representation:
        item-material: LIGHT_BLUE_STAINED_GLASS
progression:
  enabled: true
  maximum-level: 100
nameplate:
  enabled: true
  format:
    - "<yellow>{pet_name}</yellow> <gray>Lv.{level}</gray>"
```

## Alan Referansı

### Üst seviye alanlar

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `schema-version` | — | Zorunlu, `1`. |
| `display-name` | dosya adı | MiniMessage destekli görünen isim. |
| `description` | `[]` | MiniMessage destekli açıklama satırları. |
| `gui-material` | — | GUI listesinde kullanılan material. |
| `permission` | — | Bu peti verme/kullanma yetkisi. |
| `progression.enabled` | `true` | XP/seviye sistemi açık mı. |
| `progression.maximum-level` | `100` | Maksimum seviye. |
| `nameplate.enabled` | `true` | İsim etiketi açık mı. |
| `nameplate.format` | `"<gradient:#ffaa00:#ff5500>{pet_name}</gradient> <gray>Lv.{level}</gray>"` | MiniMessage formatı; `{pet_name}` ve `{level}` placeholder'ları. |

### `representation`

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `type` | `ENTITY` | `ENTITY`, `ITEM_DISPLAY`, `BLOCK_DISPLAY`, `TEXT_DISPLAY`, `PARTICLE`, `INVISIBLE`, `MULTI_ENTITY`. Hepsi bu sürümde desteklenir. |
| `entity-type` | `entity-type` (legacy) → `WOLF` | Sadece `ENTITY` için: çağırılabilir bir `LivingEntity`. |
| `baby` | `false` | Sadece `ENTITY`: yavru görünüm. |
| `glowing` | `false` | Parlak aura. |
| `invulnerable` | `true` | Hasar alamaz. |
| `silent` | `false` | Ses çıkarmaz. |
| `gravity` | `true` | Yerçekimi (display'ler için genelde `false`). |
| `item-material` | `AMETHYST_SHARD` | `ITEM_DISPLAY` (görsel) ve `MULTI_ENTITY` (birincil): material. |
| `block-material` | — | Sadece `BLOCK_DISPLAY`: blok material (ör. `CRYING_OBSIDIAN`). `item-material` da kabul edilir (eş anlamlı). |
| `custom-model-data` | — | `ITEM_DISPLAY`/`BLOCK_DISPLAY`: model ID (resource pack için). |
| `scale` | `1.0 / 1.0 / 1.0` | Display türleri: `x`, `y`, `z` ölçeği (0'dan büyük). `features.level-scaling.enabled` iken seviyeyle orantılı büyür; rest (idle/sleep) modunda × 0.65 küçülür. |
| `particle-type` | — | Sadece `PARTICLE`: particle adı (ör. `SOUL_FIRE_FLAME`), `Particle.valueOf`. |
| `particle-count` | `0` | Sadece `PARTICLE`: tick başına particle sayısı (1–500). |
| `particle-offset` | `0.0` | Sadece `PARTICLE`: yayılım ofseti (blok). |
| `particle-speed` | `0.0` | Sadece `PARTICLE`: particle hızı. |
| `child-count` | `0` | Sadece `MULTI_ENTITY`: çocuk entity sayısı (1–8). |
| `child-material` | — | Sadece `MULTI_ENTITY`: çocukların materiali. |

> Not: `TEXT_DISPLAY` petin adını gösterir; `PARTICLE`/`INVISIBLE`/`MULTI_ENTITY` çocukları
> nameplate göstermez. `MULTI_ENTITY`'de particle alanları çalışma zamanında yok sayılır.

### `movement`

`movement` bölümü yoksa: `ENTITY` → legacy config davranışı; display türleri →
varsayılan `FLYING_FOLLOW` (yükseklik 1.5, yan ofset 1.1, hız 0.18).

| Alan | Varsayılan | Açıklama |
|---|---|---|---|
| `type` | `GROUND_FOLLOW` | `GROUND_FOLLOW`, `FLYING_FOLLOW`, `HOVER`, `ORBIT`, `TRAIL`, `FORMATION`, `SHOULDER`, `ANCHORED`, `STATIC_NEAR_OWNER`, `TELEPORT_ONLY`, `ECHO`, `SHADOW_TRAIL`, `ROAM_NEAR_OWNER`, `MIRROR`. Hepsi bu sürümde desteklenir. |
| `follow-distance` | config | `GROUND_FOLLOW`: takibi başlatma mesafesi; `TRAIL`/`ECHO`/`SHADOW_TRAIL`: iz uzunluğu (blok); `ROAM_NEAR_OWNER`: dolaşma yarıçapı. |
| `teleport-distance` | config / `24.0` | Bu mesafeden uzaksa sahibine ışınlanır. |
| `follow-speed` | config / `0.18` | Takip hızı. |
| `update-interval-ticks` | `0` (her tick) | Hareket güncelleme aralığı (oyun tick'i cinsinden). |
| `height` | — | `FLYING_FOLLOW`: sahibin üstündeki yükseklik; `HOVER`/`SHOULDER`: süzülme yüksekliği. |
| `side-offset` | — | `FLYING_FOLLOW`: sahibin sağındaki yatay ofset; `MIRROR`: sahibin önündeki yatay ofset. |
| `delay-ticks` | `0` | `MIRROR`: pose taklidi gecikmesi (tick, 0–600). |
| `orbit.radius` | `1.7` | `ORBIT`: yörünge yarıçapı (0'dan büyük, zorunlu). |
| `orbit.height` | `1.4` | `ORBIT`: yörünge yüksekliği. |
| `orbit.angular-speed` | `1.2` | `ORBIT`: açısal hız (radyan/saniye). |
| `orbit.direction` | `CLOCKWISE` | `ORBIT`: `CLOCKWISE` veya `COUNTER_CLOCKWISE`. |
| `anchor.position` | `BEHIND_RIGHT` | `ANCHORED`: `BEHIND_RIGHT`, `BEHIND_LEFT`, `FRONT`, `ABOVE_HEAD`, `RIGHT_SHOULDER`, `LEFT_SHOULDER`, `WAIST`, `BELOW`. |
| `anchor.distance` | `1.8` | `ANCHORED`: sahibe yatay mesafe. |
| `anchor.height` | `0.4` | `ANCHORED`: sahibe göre yükseklik. |
| `anchor.rotate-with-owner` | `true` | `ANCHORED`: pet sahibin dönüşüyle birlikte döner mi. |

### `states`

`states` bölümü opsiyoneldir; yoksa davranış tamamen global config'e bağlıdır.

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `MOVING.animation` | — | `WALK` veya `NONE`. Şu an yalnızca doğrulanır; mob'larda doğal animasyon kullanılır. |
| `IDLE.animation` | — | `SIT`, `SLEEP`, `LOOK_AROUND` veya `NONE`. `NONE` bu pet için idle-sleep'i tamamen kapatır. `SIT`/`SLEEP`/`LOOK_AROUND` mevcut rest görselini tetikler (mob: oturma; display: × 0.65 ölçek). |
| `IDLE.after-ticks` | `0` | Boşta kalma eşiği (tick). `0` ise global `features.idle-sleep.idle-seconds` kullanılır; `> 0` ise onu ezer. |

> `IDLE` bölümü tanımlıysa pet, global `features.idle-sleep.enabled` kapalı olsa
> bile idle-sleep kullanır. Yalnızca `MOVING` tanımlıysa global davranış değişmez.

### `transforms`

`transforms` bölümü opsiyoneldir; yoksa pet görünümü sabittir. Her anahtar bir
transform'dur (isim serbest; deterministik sıralama için anahtarlar alfabetik
işlenir). Eşleşen ilk transform uygulanır.

`transforms.<ad>.when` — tümü opsiyonel, **en az biri zorunlu**; koşullar AND ile
birleşir:

| Alan | Değerler | Açıklama |
|---|---|---|
| `owner-state` | `WALKING`, `FLYING`, `SNEAKING`, `IN_WATER`, `FALLING`, `RIDING` | Sahibin o anki durumu. |
| `biome` | Biyom adı (ör. `PLAINS`, `DESERT`) | Sahibin bulunduğu biyom (`Biome.valueOf`). |
| `world` | Dünya adı (ör. `world`, `world_nether`) | Sahibin dünyası. |
| `time-of-day` | `DAY`, `NIGHT` | Dünya saati (gece: 13000–23000). |
| `weather` | `CLEAR`, `RAIN`, `THUNDER` | Sahibin dünyasındaki hava durumu. |

`transforms.<ad>.apply.representation` — **en az bir alan zorunlu**; yalnızca
belirtilen alanlar base'i ezer (representation tipi değişmez):

| Alan | Açıklama |
|---|---|
| `item-material` | Display'in item'ı (ITEM_DISPLAY/MULTI_ENTITY birincil). |
| `block-material` | BLOCK_DISPLAY bloğu. |
| `custom-model-data` | Item/block model ID. |
| `scale` | `x/y/z` ölçeği (0'dan büyük). |
| `particle-type` | PARTICLE türü. |
| `particle-count` | PARTICLE sayısı (0–500). |
| `glowing` | Parlama açık/kapalı. |
| `baby` | ENTITY'de yavru görünüm (Ageable moblar). |

> Sınırlar: transform'lar görsel katmanı değiştirir; **entity tipi / representation
> tipi değişimi** (örn. karada WOLF, suda KEDİ) bu sürümde desteklenmez — Faz B
> kapsamındaki su-kara formu (#18) ile gelecek. ENTITY'de `glowing`/`baby`
> değişimi desteklenir.

### `reactions`

`reactions` bölümü opsiyoneldir; yoksa (veya bir alt alan tanımsızsa) ilgili tepki
için global default (config'teki `features.reactions.*`) kullanılır. Her anahtar
bir `PetReactionType`'dır:

| Anahtar | Tetikleyici |
|---|---|
| `OWNER_DAMAGE` | Sahibi hasar aldığında. |
| `LEVEL_UP` | Pet seviye atladığında. |
| `REST_START` | Pet uyumaya/rest'e geçtiğinde. |
| `REST_END` | Pet rest'ten kalktığında. |

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `enabled` | `true` | `false` ise bu tepki pet için tamamen kapalı. |
| `sound` | global default | Sound adı (ör. `ENTITY_CAT_HISS`), `Sound.valueOf`. `nil` → global default. |
| `particle` | global default | Particle adı (ör. `VILLAGER_ANGRY`), `Particle.valueOf`. |
| `particle-count` | global default | Particle sayısı (0–500). |
| `volume` | global default | Ses seviyesi (0–2.0). |

### `emotes`

`emotes` bölümü opsiyoneldir; yoksa `/pet emote` komutu "bu pet için tanımlı
emote yok" döner. Anahtarlar emote adıdır (küçük harf, `a-z`, `0-9`, `-`, `_`);
virgülle ayrılmış isim listesi doğrulamada desteklenir (örn. `purr, stretch`).

| Alan | Varsayılan | Açıklama |
|---|---|---|
| `enabled` | `true` | `false` ise bu emote oynatılamaz. |
| `sound` | — | Sound adı, `Sound.valueOf`. |
| `particle` | — | Particle adı, `Particle.valueOf`. |
| `particle-count` | `5` | Particle sayısı (0–500). |
| `cooldown-seconds` | `10` | İki oynatma arası minimum süre (oyuncu başına). |

```yaml
emotes:
  purr:
    sound: ENTITY_CAT_PURR
    particle: HEART
    cooldown-seconds: 10
```

## Doğrulama

Tanım yüklemesi atomiktir: herhangi bir dosya doğrulamadan geçemezse **hiçbir değişiklik
uygulanmaz** ve mevcut tanımlar korunur. Hatalar sunucu konsoluna yazılır.

Bu sürümde desteklenen kombinasyonlar:

| Representation | Önerilen Movement'lar |
|---|---|
| `ENTITY` | `GROUND_FOLLOW`, `FLYING_FOLLOW`, `ORBIT`, `HOVER`, `TELEPORT_ONLY` |
| `ITEM_DISPLAY` | `FLYING_FOLLOW`, `ORBIT`, `HOVER`, `SHOULDER`, `ANCHORED`, `TRAIL`, `TELEPORT_ONLY` |
| `BLOCK_DISPLAY` | `HOVER`, `FLYING_FOLLOW`, `ANCHORED`, `TRAIL` |
| `TEXT_DISPLAY` | `TRAIL`, `FLYING_FOLLOW`, `ANCHORED` |
| `PARTICLE` | `HOVER`, `FLYING_FOLLOW`, `ANCHORED` |
| `INVISIBLE` | `STATIC_NEAR_OWNER`, `TELEPORT_ONLY` |
| `MULTI_ENTITY` | `FORMATION`, `TRAIL` |

> Teknik olarak validator tüm representation × movement kombinasyonlarına izin verir;
> yukarıdaki tablo anlamlı ve test edilmiş kombinasyonları gösterir. `STATIC_NEAR_OWNER`
> ile `TELEPORT_ONLY` aynı davranışı üretir (ışınlama + sabit durma).
