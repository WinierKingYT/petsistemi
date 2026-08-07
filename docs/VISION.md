# VISION.md — Companion Runtime Vizyonu

Pet sistemi yalnızca "oyuncunun arkasından gelen mob" değildir. Pet; oyuncuya bağlanan,
çevreye tepki veren, şekil değiştiren, birden fazla parçadan oluşan veya belirli
durumlarda ortaya çıkan bir **companion runtime**'dır. Bu doküman ürün yönünü tanımlar
ve her sistemin mevcut uygulama durumunu işaretler.

> Durum lejantı:
> ✅ **MEVCUT** — kodda çalışıyor, testli · 🟡 **KISMİ** — çekirdek var, vizyon şeması yok ·
> ⬜ **PLAN** — tasarım aşamasında

---

## 1. Ürün Aileleri

Tüm sistemler dört aile üzerine kurulur:

| Aile | Tanım | Temsil örneği |
|---|---|---|
| **Canlı petler** | Gerçek entity, doğal animasyon, pathfinding | `ENTITY + GROUND_FOLLOW` |
| **Familiar petler** | Collision'suz görseller (kitap, kristal, ruh, drone) | `ITEM_DISPLAY/BLOCK_DISPLAY + FLYING_FOLLOW/ORBIT` |
| **Attached petler** | Oyuncuya bağlı modeller (omuz, sırt, kafa üstü) | `DISPLAY + ANCHORED/SHOULDER` |
| **Special petler** | Sürü, gölge, portal, constellation, dönüşen petler | `COMPOSITE/PARTICLE + SPECIAL_MOVEMENT` |

---

## 2. Sistem Kataloğu ve Durum

### A. Sabit konum / takip temelleri

| # | Sistem | Durum | Not |
|---|---|---|---|
| 1 | Sabit konumlu takipçi (ANCHORED) | ✅ | 8 pozisyon: BEHIND_RIGHT, BEHIND_LEFT, FRONT, ABOVE_HEAD, RIGHT_SHOULDER, LEFT_SHOULDER, WAIST, BELOW; `distance/height/rotate-with-owner` |
| 2 | Accessory pet (ATTACHED_DISPLAY, kemik/pose) | ⬜ | Yaw/pitch/pose ile hesaplanır; yüksek risk |
| 16 | Yüzeye bağlı (SURFACE_CRAWL) | ⬜ | Zemin/duvar/tavan; display rotasyonlarıyla kontrol |
| 17 | Yeraltı (BURROW) | ⬜ | Derinlik, toprak parçacığı, idle'da çıkma |
| 33 | Pozisyon slotları | ⬜ | Çoklu pet çakışması için; ileride |

### B. Familiar hareketleri

| # | Sistem | Durum | Not |
|---|---|---|---|
| 3 | Idle/sleep state (states: MOVING/IDLE) | ✅ | Global feature (`idle-sleep.enabled`, `idle-seconds`) + per-pet `states:` şeması (`after-ticks` eşiği ezer, animasyon seçimi; `sleepy_cat` örneği). ENTITY'de Sittable sit, display'lerde ×0.65 rest scale |
| 6 | Gölge peti (SHADOW_TRAIL) | ✅ | Zemin projeksiyonlu trail; `shadow_wisp` (BLOCK_DISPLAY) — yer seviyesinde kayar, highestBlockY takibi |
| 7 | Hareket taklidi (MIRROR) | ✅ | Zıplama/eğilme/dönme kopyalama, delay-ticks; `mirror_doll` (ENTITY VILLAGER) — OwnerPose buffer + LivingEntity setSneaking/setGliding |
| 8 | Gecikmeli hayalet (ECHO) | ✅ | Oyuncunun geçmiş konumunu sabit hızla yeniden oynatır (consuming queue); TRAIL'den farkı: noktalar tüketilir, beklenir; `echo_phantom` (ENTITY ALLAY) |
| 12 | Takımyıldız (CONSTELLATION) | ⬜ | Noktalar + çizgiler (particle/display) |
| 15 | Serbest dolaşma (ROAM_NEAR_OWNER) | ✅ | Sahip etrafında rastgele yarıçap içinde dolaşma, hedefe ulaşınca yeni hedef; yarıçap `movement.follow-distance` (varsayılan 4.0), hız `follow-speed`; `roam_fox` (ENTITY FOX) |
| 24 | Emote sistemi | ✅ | `/pet emote <ad>` + tab completion, per-pet `emotes:` şeması (sound/particle/cooldown-seconds), per-owner cooldown; `sleepy_cat` örneği |

### C. Çok parçalı petler

| # | Sistem | Durum | Not |
|---|---|---|---|
| 9 | Sürü peti (SWARM) | ✅ | `SWARM_CLOUD` hareket motoru; 1 birincil + N çocuk birim organik yörüngesel bulut halinde döner (`swarm_bees` örneği) |
| 10 | Lider + yavru (COMPOSITE) | ✅ | MULTI_ENTITY çocukları var; dinamik çocuk entity takip ve dinlenme visual senkronizasyonu |
| 11 | Formasyon (FORMATION) | ✅ | V/çizgi/daire; `spacing`, `rotate-with-owner` |
| 31 | Interaction hitbox | ✅ | Display/Particle petleri için görünmez Paper `Interaction` entity + sağ tık aksiyonu (`PetHitboxDefinition`) |

### D. Dönüşüm / çevre sistemleri

| # | Sistem | Durum | Not |
|---|---|---|---|
| 4 | Dönüşen pet (transformations) | ✅ | `transforms:` şeması: `when` + `apply` görsel ve `entityType` override desteği |
| 5 | Element/çevre peti (environment-variants) | ✅ | `when`: biome, world, time-of-day, weather, `minY/maxY`, `minLight/maxLight`; `wisplight` örneği |
| 18 | Su-kara formu | ✅ | Su/kara durumuna göre `entityType` ve visual override dönüşümü |
| 19 | Araç davranışı (vehicle-behavior) | ⬜ | HORSE/BOAT/MINECART/ELYTRA başına hareket |
| 26 | Büyüyen görsel (visual-progression) | ✅ | Level-scale var (growth/max-multiplier). Seviye bazlı evrim (`PetEvolutionDefinition`) |
| 27 | Evrim (evolution) | ✅ | `evolutions:` şeması ile seviye eşiğine ulaşıldığında hedef tanıma geçiş |
| 28 | Mevsimsel varyant | ⬜ | Sunucu temasından seçilir, tick'te tarih kontrolü yok |

### E. Ortaya çıkma / görünürlük

| # | Sistem | Durum | Not |
|---|---|---|---|
| 13 | Portal peti (spawn-style: PORTAL) | ⬜ | Runtime spawn/despawn sunumu; entry/exit particle + ses |
| 14 | Koşullu varlık (presence: CONDITIONAL) | ⬜ | OWNER_SNEAK/JUMP/hasar/item tetikleri, visible-duration |
| 29 | Hologram/sprite pet | ⬜ | ItemDisplay/TextDisplay tabanlı 2D sprite |
| 30 | Oyuncuya özel görünürlük | ⬜ | OWNER_ONLY → packet/NMS katmanı gerekir; yüksek risk |

### F. Etkileşim / karakter

| # | Sistem | Durum | Not |
|---|---|---|---|
| 20 | Mini oyuncu (HUMANOID_MODEL) | ⬜ | Önce display model; NMS fake-player sonra |
| 21 | Binek pet (MOUNT) | ⬜ | Araç kontrolü, anti-cheat, passenger yönetimi; core stabil olunca |
| 22 | Taşınabilir yuva (home) | ⬜ | Persistence + chunk yönetimi büyütür; 2. büyük milestone |
| 23 | Duygu tepkileri (reactions) | ✅ | OWNER_DAMAGE, LEVEL_UP, REST_START/REST_END ses/parçacık tepkileri |
| 25 | Kişilik (personality) | ✅ | LOYAL, CURIOUS, SHY, ENERGETIC, SLEEPY kişilik tipleri (`PetPersonalityType`) |
| 32 | Mod değiştirme (allowed-modes) | ✅ | FOLLOW/STAY/WANDER persist ediliyor, `stayLocation` demirleme ve `allowed-modes` yetki kısıtlaması |

---

## 3. İlk Genişleme Paketi (tamamlandı)

YAML'dan pet seçimi değil, **pet davranışı oluşturma** için altı temel sistem kuruldu:

```text
1. ENTITY + GROUND_FOLLOW        ✅ legacy `wolf` (gerçek mob, pathfinding)
2. ITEM_DISPLAY + FLYING_FOLLOW  ✅ `floating_book` (omuz üstü süzülme)
3. ITEM_DISPLAY + ORBIT          ✅ `arcane_crystal` (yörünge)
4. DISPLAY + SHOULDER/ANCHORED   ✅ `shoulder_orb` (8 pozisyon, sahibiyle döner)
5. DISPLAY + TRAIL               ✅ `ghost_scribe` (iz bırakma)
6. SWARM + FORMATION             ✅ `familiar_swarm` (çocuk entity temizliği, child koruması)
```

Ek tamamlanan düşük riskli sistemler: idle/sleep (#3 çekirdek), reaction'lar (#23 çekirdek),
level-scale (#26 çekirdek), mode persistence (#32 çekirdek).

**Faz A ilerlemesi:**
- 1️⃣ `states:` per-pet durum şeması ✅ `sleepy_cat` ile (MOVING/IDLE, per-pet `after-ticks`
  global eşiği ezer, `NONE` kapatır; parser + validator + `PetIdleSleepController` entegre).
- 2️⃣ Dönüşümler + çevre varyantları ✅ `wisplight` ile (transforms: `when` owner-state/
  biome/world/time-of-day/weather + `apply` görsel override; `PetTransformController`
  tick'te koşulları değerlendirir, derived definition tüm visual pipeline'a paylaşılır).
  Entity tipi değişimi (su-kara formu) Faz B'de.
- 3️⃣ Per-pet reactions + emotes ✅ `sleepy_cat` ile (reactions: `OWNER_DAMAGE`/`LEVEL_UP`/
  `REST_START`/`REST_END` per-pet override — null alan global default'u kullanır,
  `enabled:false` kapatır; emotes: `/pet emote <ad>` + tab completion + per-owner
  cooldown + `PetEmoteController`; `PetReactionListener` definition-aware).
  Harekete bağlı animasyonlu emote'lar (wave/dance) Faz B kapsamında.
- 4️⃣ ECHO / SHADOW_TRAIL / ROAM_NEAR_OWNER / MIRROR hareketleri ✅ `echo_phantom`,
  `shadow_wisp`, `roam_fox`, `mirror_doll` ile (4 yeni `PetMovementType`, delay-ticks
  alanı ile MIRROR pose taklidi, EchoMovement consuming queue ile path replay,
  ShadowTrailMovement zemin projeksiyonlu trail, RoamNearOwnerMovement rastgele
  yarıçap içinde dolaşma).

---

## 4. Uygulama Yol Haritası

### Faz A — Şu an yapılabilecekler (düşük risk, mevcut core üzerine)
1. `states:` per-pet durum şeması (MOVING/IDLE + animation) — #3'ü pet bazına taşır
2. Dönüşümler + çevre varyantları (owner-state, biome, world, zaman) — #4/#5
3. Per-pet reaction + emote şeması — #23/#24 ✅
4. ECHO / SHADOW_TRAIL / ROAM_NEAR_OWNER / MIRROR hareketleri — #6/#7/#8/#15 ✅
5. SWARM (RANDOM_CLOUD) ve COMPOSITE (role'ler) ayrımı — #9/#10
6. Interaction hitbox (PARTICLE petler) — #31
7. Kişilik profilleri — #25
8. Stage'li visual progression — #26

### Faz B — Orta seviye
Sürü peti, multi-entity pet, formasyon, çevreye göre dönüşüm, mode switching, gölge/echo
hareketi, su-kara form değişimi, kişilik sistemi, surface crawling, composite animasyonlar.

### Faz C — Yüksek risk (sonra)
Fake-player mini pet, owner-only packet pet, binek pet, dünyada kalıcı pet evi,
NMS custom pathfinding, duvar/tavan navigasyonu, kusursuz attached model,
çoklu aktif pet, seçimli evrim ağacı, combat/utility rolleri.

---

## 5. Kombinasyon Fikirleri

| Tasarım | Bileşim |
|---|---|
| Büyülü kitap | ITEM_DISPLAY + FLYING_FOLLOW + IDLE_PAGE_ANIMATION + OWNER_CAST_REACTION |
| Gölge kurt | PARTICLE_MODEL + ECHO + NIGHT_VARIANT + OWNER_DAMAGE_REACTION |
| Mini ejderha | CUSTOM_MODEL + FLYING_FOLLOW + SHOULDER_IDLE + LEVEL_BASED_GROWTH |
| Arı sürüsü | SWARM + RANDOM_CLOUD + FLOWER_REACTION + OWNER_IDLE_ROAMING |
| Yeraltı köstebeği | BLOCK_DISPLAY + BURROW + IDLE_EMERGE + GROUND_PARTICLES |
| Su ruhu | PARTICLE_DISPLAY + ORBIT + WATER_ENVIRONMENT_VARIANT + RAIN_REACTION |
| Mekanik drone | MULTI_ENTITY + HOVER + SELF_ROTATION + FIXED_RIGHT_POSITION |
| Mini oyuncu | HUMANOID_MODEL + MIRROR + OWNER_SKIN + DELAYED_EMOTES |

---

## 6. İlkeler

- Tek sistemlerin gücü sınırlıdır; asıl güç YAML bileşimindedir.
- Yeni sistemler önce display/particle türlerinde ucuz yoldan doğrulanır; mob pathfinding'i
  yalnızca gerçekten gerektiğinde kullanılır.
- Pet instance kimliği korunur; dönüşüm/evrim yalnızca runtime representation/definition
  değiştirir.
- Veritabanında sürü bir pet'tir, beş değil; çoklu pet/slot sistemleri ayrı milestone'dur.
- Yüksek riskli sistemler (binek, pet evi, packet görünürlük) core stabil olmadan eklenmez.
