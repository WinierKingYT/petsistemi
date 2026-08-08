# ENGINE-ROADMAP.md — Motor Yol Haritası

Bu doküman **motorun** nasıl açılacağını tanımlar: kayıt sistemleri, davranış motoru,
animasyon soyutlaması, model sağlayıcıları ve editör.

Diğer dokümanlarla ilişkisi:

| Doküman | Kapsam |
|---|---|
| `VISION.md` | **Ne** yapılacak — 33 sistemlik ürün kataloğu, Faz A/B/C |
| `ROADMAP-EXECUTION.md` | **Ne yapıldı** — Milestone 0-13 takibi |
| `ENGINE-ROADMAP.md` (bu) | **Nasıl** yapılacak — motorun taşıyıcı fazları, MF1-MF9 |

> Adlandırma: bu dokümanın fazları `MF1`…`MF9` ("Motor Fazı") olarak numaralanır.
> `VISION.md`'deki **Faz A/B/C** ve `ROADMAP-EXECUTION.md`'deki **Milestone 0-13** ile
> karıştırılmamalıdır — üç eksen birbirinden bağımsızdır. `VISION.md` bir özelliği
> tarif eder; bu doküman o özelliğin hangi motor katmanı üzerine oturacağını söyler.

---

## Planı belirleyen üç kısıt

**1. Ortada çalışan bir ürün var.** 17.030 satır, 201 dosya, 503 test, `0.2.0-alpha.1`.
Bu bir greenfield değil. Her faz **tek başına yayınlanabilir** olmak zorundadır. "MF3
bitmeden hiçbir şey çalışmıyor" durumu bu projenin en büyük riskidir — teknik değil,
terk edilme riski.

**2. Sadece bir karar geri döndürülemez.** Registry keying (MF1). Diğer her şey bugün
ne kadar pahalıysa altı ay sonra da o kadar pahalıdır. Bu yüzden fazlar "en değerli"
sırasına göre değil, **"ertelendikçe pahalılaşan"** sırasına göre dizilmiştir.

**3. Planlama detayı mesafeyle azalır.** MF1-MF2 dosya seviyesinde tanımlıdır.
MF6+ yalnızca niyet ve bağımlılık olarak yazılmıştır. Şimdi detaylandırmak YAGNI olur;
MF2 bittiğinde ne istediğimiz değişecektir.

---

## Kilitlenen kararlar (2026-08-08)

Bu üç cevap planın şeklini belirledi ve fazların içeriğine doğrudan yansıdı.

### K1 — Hedef sürüm: şimdilik 1.20.4, ileride 1.21+

Geliştirme 1.20.4 üzerinde sürer; 1.21 ve üstü **sonra** hedeflenecektir.

> **Sonucu:** Sürüm taşıması ileriye bırakılıyor, ama bugün ucuza alınabilecek bir
> önlem var. `FAST_DIGGING` olayı bunun ön izlemesiydi: Java sabiti derleniyordu ama
> 1.20.4 registry'si yalnızca `HASTE` adına cevap veriyordu — buff sessizce hiç
> uygulanmıyordu. Bu tür ad çözümlemeleri **tek bir yerde toplanmalı** ki 1.21 portu
> geniş bir tarama değil, küçük bir diff olsun.
>
> MF1'e dahil edildi: Bukkit enum/registry ad çözümlemeleri tek bir seam arkasına alınır.

### K2 — 3D modeller yapılacak

> **Sonucu:** MF4 (Animation) ve MF5 (Model Provider) spekülatif olmaktan çıkıp
> **kritik zincire** girdi. İki somut etkisi var:
>
> - MF4 yalnızca vanilla'ya göre değil, **adlandırılmış klip / öncelik / harmanlama**
>   kavramları olan bir model motoruna göre tasarlanmalıdır. Aksi halde MF5 adaptörü
>   animasyon mantığını içine emer ve bir daha sökülemez.
> - MF1'de `RuntimeRepresentationType`'ın açılması zorunlu hale geldi — `modelengine:model`
>   bir temsil türü olarak kaydedilebilmelidir.

### K3 — Şimdilik tek sunucu, ileride network olabilir

> **Sonucu:** MySQL ve network senkronizasyonu MF8'de kalır. Ancak bugün ücretsiz
> alınabilecek bir önlem var: **yeni migration'lar SQLite'a özgü sözdizimi
> kullanmamalıdır.** Bu bir faz değil, MF1'den itibaren geçerli bir kuraldır.
>
> **Güncelleme (2026-08-08):** MF8 ile MySQL backend üretim bootstrap'ına bağlandı;
> network modu paylaşımlı event günlüğü ve dağıtık yazma kilitleriyle eklendi.

---

## Bağımlılık zinciri

```
MF1  Namespaced Registry ──┬──→ MF2  Behavior Engine ──→ MF3  Ability / Skill
                           │
                           ├──→ MF4  Animation ─────────→ MF5  Model Provider
                           │
                           └──→ MF6  Collection GUI + Editor

MF7  Gameplay        (bağımsız — MF2 sonrası anlamlı)
MF8  Ekosistem       (bağımsız — en son)
MF9  Visual Graph    (MF1 + MF4 + MF5 sonrası)
```

MF2 ve MF6 birbirine bağlı değildir; paralel ilerleyebilirler.

---

## MF1 — Namespaced Registry

> **Tamamlandı (2026-08-08):** Movement ve representation registry'leri artık
> `NamespacedKey` ile genişletilebilir. Yerleşik enum API'leri geriye uyumluluk için
> korunur; YAML özel anahtarları tanımda taşınır ve runtime dispatch'te önceliklidir.

| | |
|---|---|
| **Boyut** | M — ölçüldü: 8 + 11 dosya, çoğu mekanik |
| **Risk** | Orta — tanım katmanına dokunur, 503 testin dayanağı |
| **Bağımlılık** | Yok |
| **Tek başına yayınlanabilir** | Evet |

### Sorun

`PetMovementRegistry` bugün bir `EnumMap<PetMovementType, PetMovementController>`.
Enum kapalı bir tiptir. Bunun pratik sonucu:

> Üçüncü parti bir plugin **tek bir movement bile ekleyemez**. Eklemek için enum'a değer
> koyması, yani bizim jar'ı yeniden derlemesi gerekir.

`PetRepresentationRegistry` için de aynısı geçerlidir — ve K2 gereği `modelengine:model`
bir temsil türü olarak kaydedilebilmelidir.

### Kapsam

- Her iki registry `NamespacedKey` anahtarına geçer
- Yerleşikler `petsistemi:orbit`, `petsistemi:item_display` gibi kaydolur
- **Enum'lar silinmez.** Yerleşik sabit olarak kalırlar: derleme güvenliği ve mevcut
  testlerin dayanağı budur
- Parser: YAML'daki ad önce yerleşik adla eşleştirilir; tutmazsa `NamespacedKey.fromString`
  ile özel anahtar olarak çözülür
- `PetMovementDefinition` / `PetRepresentationDefinition` anahtarı taşır
- **K1 gereği:** Bukkit enum ve registry ad çözümlemeleri tek bir seam arkasına toplanır

### Çıkış kriteri — pazarlık edilmez

> Test kapsamında sahte bir plugin `test:custom_movement` anahtarıyla bir kontrolcü kaydeder.
> Bir pet YAML'ı `movement.type: test:custom_movement` yazar. Pet o kontrolcüyle hareket eder.
> Mevcut 503 test yeşil kalır.

**Doğrulama:** `test:custom_movement` YAML anahtarı ayrıştırılıp tanımda korunur ve
aynı anahtarla kaydedilen controller registry'den çözülür. Tam test paketi yeşildir.

### Kapsam dışı

- Enum'ları silmek
- Validator'ı yeniden yazmak
- Movement/representation sayısını artırmak

### Anahtar uzayı kararı (2026-08-08)

`petsistemi:*` yalnızca çekirdeğin yerleşik kayıtlarına ayrılır. Pet Pack veya üçüncü
taraf eklenti tarafından getirilen kayıtlar sahibinin namespace'ini kullanır
(`frostpack:orbit`, `modelengine:model`). Böylece paket kimliği yayınlandıktan sonra
çekirdek adlarıyla çakışmaz.

---

## MF2 — Behavior Engine (Trigger / Condition / Action)

> **Tamamlandı (2026-08-08):** Namespaced trigger, condition ve action registry'leri,
> deterministik behavior executor ve native `behaviors:` YAML şeması eklendi. Mevcut
> `reactions:`, `emotes:` ve `buffs:` tanımları runtime'da behavior pipeline'ına
> çevriliyor; dış davranış korunuyor. Üçüncü taraf eklentiler `BehaviorService`'i Bukkit
> ServicesManager üzerinden alarak kendi kayıtlarını ekleyebilir ve trigger çalıştırabilir.

| | |
|---|---|
| **Boyut** | XL — bu projedeki en büyük tek iş |
| **Risk** | Yüksek |
| **Bağımlılık** | MF1 |
| **Tek başına yayınlanabilir** | Evet — eski şema çalışmaya devam eder |

### Neden

Projenin en yüksek getirili soyutlaması. Bugün dağınık duran mekanizmalar bunun altında
birleşir:

| Bugün | MF2 sonrası |
|---|---|
| `PetReactionType` — 4 sabit tetikleyici | Trigger registry (açık) |
| `PetEmoteController` | Action |
| `PetBuffController` | Action |

MCPets bu gücü MythicMobs'tan **ödünç alır**. MF2 tamamlandığında biz aynı gücü kendi
motorumuzdan elde ederiz — admin pet yapmak için ikinci bir mob sistemi öğrenmek zorunda
kalmaz. Ürünün ana farklılaştırıcısı budur.

### Kritik tasarım kuralı

**`reactions:` ve `emotes:` şemaları çalışmaya devam eder.** Geriye dönük kırılma yoktur;
eski şema yeni motora çevrilir. Yayınlanmış pet YAML'larını kıran bir motor, motor değil
yeniden yazımdır.

### Çıkış kriteri

> `reactions:` ve `emotes:` içeriden behavior olarak ifade edilir, dışarıdan davranışları
> hiç değişmez. Üçüncü parti bir plugin kendi trigger ve action'ını kaydedebilir.

**Doğrulama:** Legacy reaction/emote/buff testleri ile native behavior parser/runtime
testleri yeşildir; sahte bir üçüncü taraf eklenti `BehaviorService` kaydını Bukkit
ServicesManager üzerinden çözmektedir. Tam paket 512/512 yeşildir.

---

## MF3 — Ability / Skill

> **Tamamlandı (2026-08-08):** Ability tanımı behavior üzerine kuruldu; YAML parser,
> validator, namespaced ability anahtarları, owner/ability bazlı cooldown, hedef seçimi
> (`NONE`, pet, owner, owner-target, nearest-living, area-around-pet), projectile/AoE
> action'ları ve `/pet ability <ad>` eklendi. Oyuncu `/pet ability bind <ad>` ile
> çömelme + el değiştirme tuşuna oturumluk ability bağlayabilir; bağ yokken vanilla
> el değiştirme davranışı etkilenmez.

| | |
|---|---|
| **Boyut** | L · **Risk** Orta · **Bağımlılık** MF2 |

MF2 tamamlandığında ability, **cooldown ve hedefleme eklenmiş bir behavior**'dan ibarettir.
Ayrı bir motor değildir. MF2'den önce yapılırsa iki kez yazılır — bu yüzden sıralama
zorunludur.

Kapsam: cooldown, hedef seçimi, mermi ve alan efektleri, `/pet ability` ve tuş bağlama.

**Doğrulama:** Cooldown yalnızca başarılı action sonrasında başlar; hedefsiz deneme
cooldown tüketmez. Projectile hedef vektörü, çoklu alan hedefleri, binding controller
ve swap-hand listener testleri dahil tam paket 522/522 yeşildir.

---

## MF4 — Animation Abstraction

> **Tamamlandı (2026-08-08):** Provider-bağımsız `PetAnimationStateMachine`
> IDLE/MOVING/SPRINTING/SLEEPING/ATTACKING durumlarını yönetir. State tanımları
> namespaced klip, öncelik, blend-in/out ve loop metadata'sı taşır; eski
> `animation: WALK/SLEEP` şeması uyumluluk adaptörü olarak korunur. Vanilla ve display
> temsilleri aynı `PetRepresentationController.applyAnimation` arayüzünden çalışır ve
> harici model eklentisi olmadan oturma/uyku ölçeği davranışını sürdürür.

| | |
|---|---|
| **Boyut** | L · **Risk** Orta · **Bağımlılık** MF1 |

Bugün animasyon `PetIdleAnimation` enum'u ve dağınık koşullardan ibaret. Model sağlayıcıya
geçmeden **önce** provider-bağımsız bir state machine gerekir.

```
IDLE ⇄ MOVING → SPRINTING
  ↓
SLEEPING        ATTACKING
```

**K2 gereği** bu soyutlama adlandırılmış klip, öncelik ve harmanlama kavramlarını
taşımalıdır — yalnızca vanilla'nın ifade edebildiği kadarını değil.

### Çıkış kriteri

> Vanilla ve display petleri aynı animasyon arayüzünü kullanır. ModelEngine kurulu
> değilken de sistem anlamlı çalışır.

---

## MF5 — Model Provider Adaptörleri

> **Tamamlandı (2026-08-08):** Core-dışı `PetModelProvider`/`ModelProviderService`
> sözleşmesi ve `modelengine:model`, `itemsadder:model`, `oraxen:model` adaptörleri
> eklendi. ModelEngine MF4 priority/blend/loop geçişlerini, ItemsAdder custom entity
> kliplerini, Oraxen ise ItemDisplay tabanlı ortak state fallback'ini kullanır. Harici
> API sınıfları yansıtmalı adaptör sınırında kaldığından sağlayıcı jar'ları build'e veya
> dağıtım artifact'ına girmez; eksik provider plugin açılışını etkilemez.

| | |
|---|---|
| **Boyut** | L · **Risk** Düşük (izole) · **Bağımlılık** MF1 + MF4 |

ModelEngine, ItemsAdder, Oraxen adaptörleri. **Core hiçbirine bağımlı olmaz** —
`compileOnly`, PlaceholderAPI'de uygulanan desenin aynısı.

K2 gereği bu faz artık spekülatif değildir; MCPets'e karşı asıl görsel açık buradadır.

---

## MF6 — Collection GUI + Oyun İçi Editör

> **Tamamlandı (2026-08-08):** `/pet collection` tüm tanımları açık/kilitli durumuyla,
> sahiplik filtresi ve gerçek sayfalama ile sunar. `/petadmin editor [tanım_id]` temel
> görünüm/hareket alanlarını ve parlama ayarını oyun içinden düzenler. Taslaklar üretim
> parser/validator zincirinden geçer; dış dosya çakışması reddedilir, dosya atomik yazılır
> ve bütün klasör doğrulanmadan canlı snapshot yayımlanmaz. Ayrıntılar:
> [COLLECTION-EDITOR.md](COLLECTION-EDITOR.md).

| | |
|---|---|
| **Boyut** | XL · **Risk** Düşük · **Bağımlılık** MF1 (şema kararlılığı) |

MCPets'in `/mcpets editor` karşılığı. Bu **teknik değil, benimsenme** yatırımıdır:
18 petimiz var ve admin bunları YAML bilmeden düzenleyemiyor.

MF2/MF3'e bağımlı değildir — MF2 ile paralel ilerleyebilir.

---

## MF7 — Gameplay · MF8 — Ekosistem

> **MF7a tamamlandı (2026-08-08):** `item-actions` şeması materyal/custom-model-data
> eşleştirmesini; tüketim, seviye/izin, cooldown ve namespaced handler çağrısını tek hatta
> birleştirir. Başarısız asenkron işlemler itemi iade eder, cooldown yalnızca başarıda
> başlar. `petsistemi:gain_experience` ve `petsistemi:unlock_pet` yerleşiktir; üçüncü
> taraflar Bukkit `PetItemActionService` üzerinden aksiyon kaydeder. Ayrıntılar:
> [ITEM-ACTIONS.md](ITEM-ACTIONS.md).
>
> **MF7 tamamlandı (2026-08-08):** MF7a–d üzerine kalıcı `petsistemi:evolve_pet`
> aksiyonu, DB/runtime compensation, iptal edilebilir evrim API olayı ve aktif pet
> gerektirmeyen PDC işaretli unlock itemi eklendi. Unlock itemi yönetici komutu ve Bukkit
> `PetUnlockItemService` üzerinden üretilebilir; başarısız asenkron kullanım itemi iade eder.
> Tam regresyon paketi 575/575 yeşildir.
>
> **MF7b tamamlandı (2026-08-08):** Daha önce yalnızca parse edilen `evolutions:` aşamaları
> kalıcı seviyeden deterministik türetilen runtime tanımlarına bağlandı. En yüksek uygun eşik
> seçilir; hedef tanım, ad ve ölçek override'ı transforms/idle/animation zincirinden önce
> uygulanır. Eksik hedef ile representation/movement sağlayıcı geçişi yüklemede reddedilir.
> Ayrıntılar: [EVOLUTIONS.md](EVOLUTIONS.md).
>
> **MF7c tamamlandı (2026-08-08):** Namespaced ve üçüncü taraf kaydına açık emir motoru;
> kalıcı `follow/stay/wander`, tek seferlik `come`, oyuncu başına pending koruması ve Bukkit
> `PetOrderService` eklendi. `/pet mode` aynı motoru kullanan uyumluluk komutudur. Ayrıntılar:
> [ORDERS.md](ORDERS.md).
>
> **MF7d tamamlandı (2026-08-08):** Kara/uçuş velocity kontrolü, 1.20.4 için izole WASD
> input adaptörü, izin ve global feature kapıları, normal movement bastırması, gravity geri
> yükleme ve lifecycle temizliği `PetMountController` altında toplandı. Bukkit
> `PetMountService` yayımlandı. Ayrıntılar: [MOUNTS.md](MOUNTS.md).

Bilinçli olarak detaylandırılmamıştır. MF2 tamamlandığında bu maddelerin yarısı yeniden
tanımlanacaktır.

- **MF7:** Pet Item Action System (yem / unlock / seçimli evrim), otomatik
  seviye evrimi, kalıcı/seçimli evrim, petsiz unlock itemleri, orders, mount — **tamamlandı**
- **MF8:** MySQL ve network senkronizasyonu (K3), Pet Pack formatı, marketplace

> **MF8 tamamlandı (2026-08-08):** Seçilebilir SQLite/MySQL backend, MySQL V9 şeması ve
> gerçek MySQL CI entegrasyon testi; event-cursor tabanlı network invalidation/runtime
> yenileme ve MySQL dağıtık kilitleri; atomik/rollback'li, bağımlılık ve motor sürümü
> doğrulamalı namespaced Pet Pack kurulumları; HTTPS, redirect, boyut ve SHA-256 korumalı
> marketplace ile üç açık Bukkit servisi tamamlandı. Ayrıntılar:
> [ECOSYSTEM.md](ECOSYSTEM.md). Tam regresyon paketi 587/587 yeşildir.

---

## MF9 — Visual Graph Foundation

> **Temel tamamlandı (2026-08-08):** Domain graph modeli, runtime `PetVisualHandle`,
> server/virtual backend ayrımı ve legacy controller adaptörü eklendi. Coordinator,
> active registry, refresh, evolution, transform, idle ve animation yaşam döngüleri
> handle üzerinden çalışır; mevcut yedi representation ve üçüncü taraf entity tabanlı
> controller sözleşmeleri korunur. Ayrıntılar: [VISUAL-GRAPH.md](VISUAL-GRAPH.md).
>
> **COMPOSITE tamamlandı (2026-08-08):** Adlandırılmış component YAML şeması, döngü ve
> parent doğrulaması, controller delegasyonu, yerel transform senkronizasyonu, atomik
> rollback ve `fire_familiar` örneği eklendi. Ayrıntılar: [COMPOSITE.md](COMPOSITE.md).
>
> **DISPLAY_MODEL tamamlandı (2026-08-08):** Display-only skeleton, tam parent transform
> bileşimi, state/keyframe kanalları, interpolation ve `mechanical_bird` örneği eklendi.
> Ayrıntılar: [DISPLAY-MODEL.md](DISPLAY-MODEL.md).
>
> **SPRITE tamamlandı (2026-08-08):** ItemDisplay billboard, state tabanlı resource-pack
> frame animasyonu, loop/hold davranışı ve `pixel_slime` örneği eklendi. Ayrıntılar:
> [SPRITE.md](SPRITE.md).
>
> **PARTICLE_MODEL tamamlandı (2026-08-08):** Bütçesi doğrulanan ring, sphere, helix,
> cube ve cone örnekleyicileri; state hızları ve `astral_spirit` örneği eklendi.
> Ayrıntılar: [PARTICLE-MODEL.md](PARTICLE-MODEL.md).
>
> **PROCEDURAL tamamlandı (2026-08-08):** Sekiz matematiksel dağılımdan kalıcı
> Item/Block/Text Display graph'ı, rotation/pulse state animasyonu ve `arcane_galaxy`
> örneği eklendi. Ayrıntılar: [PROCEDURAL.md](PROCEDURAL.md).

| | |
|---|---|
| **Boyut** | L — foundation tamam, yeni representation controller'ları ayrı dilimler |
| **Risk** | Orta — bütün görsel yaşam döngüsüne dokunur |
| **Bağımlılık** | MF1 + MF4 + MF5 |
| **Tek başına yayınlanabilir** | Evet — mevcut YAML değişmeden çalışır |

MF9'un amacı enum'u çok sayıda özel durumla büyütmek değil, temsilin ne olduğu ile nasıl
render edildiğini ayırmaktır. `PLAYER_HEAD` item içeriği, `PACKET_ENTITY` render backend'i,
`EQUIPMENT_MODEL` ise composite template'i olarak modellenebilir.

Foundation sonrasındaki uygulama sırası:

1. ✅ `COMPOSITE` — farklı representation node'larını tek pet altında birleştirme
2. ✅ `DISPLAY_MODEL` — parent-child transformlu çok parçalı display modeli
3. ✅ `SPRITE` — billboard/animated 2D görsel ailesi
4. ✅ `PARTICLE_MODEL` ve `PROCEDURAL` — şekil tabanlı görseller
5. Packet backend, owner-only görünürlük ve fake-player sağlayıcısı

---

## Yapmayacaklarımız

Açıkça kapsam dışı — maliyeti getirisini karşılamıyor:

| Madde | Gerekçe |
|---|---|
| `PetDefinition`'ı parçalamak | 30 bileşen can sıkıyor ama Builder acıyı aldı. 201 dosyalık refactor açar, **sıfır yeni yetenek** getirir |
| Movement sayısını artırmak | Zaten 15 tane var. Sorun sayı değil, tipin kapalı olması |
| Web editor | MF6 (oyun içi) çalışmadan konuşmak erken |
| MythicMobs'u yeniden yazmak | MF2/MF3 pet ölçeğinde yeterli |

---

## Borç kaydı

Faz değiller, ama plana dahiller — çünkü unutulursa birikirler.

| # | Borç | Durum |
|---|---|---|
| B1 | Manuel test matrisi (~90 madde) | **0 işaretli.** ~15'i yalnızca insanın yapabileceği görsel doğrulama |
| B2 | `spirit_flame` süzülmüyor | Cevapsız — `pets/` silinip açılış logundaki `Yüklenen petler:` satırı gerekiyor |
| B3 | Yapısal borç: 9-11 parametreli ctor'lar, 552 satırlık `PetRuntimeCoordinator` | Bilinçli ertelendi |
| B4 | Yaşam döngüsü entegrasyon testi | DB + koordinatör kablolaması gerekiyor |
| B5 | Ölü MySQL kodu | **Kapandı (MF8):** typed config, bootstrap, şema migratorü, repository adaptörleri ve CI MySQL 8.4 testiyle canlı hatta alındı |
| B6 | Buff YAML'ları mevcut kurulumlara ulaşmıyor | `pets/` klasörü varsa üzerine yazılmaz; yeni `buffs:` bölümleri için o dosyalar silinmeli |

---

## Revizyon politikası

- Bir faz **tamamlandığında** burada işaretlenir ve `ROADMAP-EXECUTION.md`'ye tek satır
  özet düşülür
- MF6 ve sonrasının detayı, MF2 tamamlanmadan yazılmaz
- Kilitlenen kararlar (K1-K3) değişirse etkilenen fazlar yeniden değerlendirilir;
  kararın kendisi silinmez, **üzerine tarihli yeni karar yazılır**

---

## Önerilen ilerleme

MF1 tek parça bitirilip yayınlanır. **MF2'ye başlamadan önce durulur ve değerlendirilir** —
MF2 bu projedeki en büyük tek iştir; ona girmeden önce MF1'in gerçekten işe yaradığının
görülmesi gerekir.
