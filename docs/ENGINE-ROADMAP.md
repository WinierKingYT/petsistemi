# ENGINE-ROADMAP.md — Motor Yol Haritası

Bu doküman **motorun** nasıl açılacağını tanımlar: kayıt sistemleri, davranış motoru,
animasyon soyutlaması, model sağlayıcıları ve editör.

Diğer dokümanlarla ilişkisi:

| Doküman | Kapsam |
|---|---|
| `VISION.md` | **Ne** yapılacak — 33 sistemlik ürün kataloğu, Faz A/B/C |
| `ROADMAP-EXECUTION.md` | **Ne yapıldı** — Milestone 0-13 takibi |
| `ENGINE-ROADMAP.md` (bu) | **Nasıl** yapılacak — motorun taşıyıcı fazları, MF1-MF8 |

> Adlandırma: bu dokümanın fazları `MF1`…`MF8` ("Motor Fazı") olarak numaralanır.
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
> Ayrıca mevcut MySQL kodu hiçbir yerde örneklenmiyor — ölü kod. Bkz. Borç Kaydı B5.

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
```

MF2 ve MF6 birbirine bağlı değildir; paralel ilerleyebilirler.

---

## MF1 — Namespaced Registry

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

### Kapsam dışı

- Enum'ları silmek
- Validator'ı yeniden yazmak
- Movement/representation sayısını artırmak

### Açık tasarım sorusu

Pet Pack formatı (MF8) anahtar uzayına bağlıdır: bir paketin getirdiği movement
`petsistemi:orbit` mi yoksa `frostpack:orbit` mi olacak? **Bu soru MF1'de cevaplanmalıdır**,
MF8'de değil — sonradan değiştirmek yayınlanmış paketleri kırar.

---

## MF2 — Behavior Engine (Trigger / Condition / Action)

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

---

## MF3 — Ability / Skill

| | |
|---|---|
| **Boyut** | L · **Risk** Orta · **Bağımlılık** MF2 |

MF2 tamamlandığında ability, **cooldown ve hedefleme eklenmiş bir behavior**'dan ibarettir.
Ayrı bir motor değildir. MF2'den önce yapılırsa iki kez yazılır — bu yüzden sıralama
zorunludur.

Kapsam: cooldown, hedef seçimi, mermi ve alan efektleri, `/pet ability` ve tuş bağlama.

---

## MF4 — Animation Abstraction

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

| | |
|---|---|
| **Boyut** | L · **Risk** Düşük (izole) · **Bağımlılık** MF1 + MF4 |

ModelEngine, ItemsAdder, Oraxen adaptörleri. **Core hiçbirine bağımlı olmaz** —
`compileOnly`, PlaceholderAPI'de uygulanan desenin aynısı.

K2 gereği bu faz artık spekülatif değildir; MCPets'e karşı asıl görsel açık buradadır.

---

## MF6 — Collection GUI + Oyun İçi Editör

| | |
|---|---|
| **Boyut** | XL · **Risk** Düşük · **Bağımlılık** MF1 (şema kararlılığı) |

MCPets'in `/mcpets editor` karşılığı. Bu **teknik değil, benimsenme** yatırımıdır:
18 petimiz var ve admin bunları YAML bilmeden düzenleyemiyor.

MF2/MF3'e bağımlı değildir — MF2 ile paralel ilerleyebilir.

---

## MF7 — Gameplay · MF8 — Ekosistem

Bilinçli olarak detaylandırılmamıştır. MF2 tamamlandığında bu maddelerin yarısı yeniden
tanımlanacaktır.

- **MF7:** Pet Item Action System (yem / evrim / unlock hepsi tek altyapı), orders, mount
- **MF8:** MySQL ve network senkronizasyonu (K3), Pet Pack formatı, marketplace

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
| B5 | Ölü MySQL kodu | Hiçbir yerde örneklenmiyor. **Öneri: silinsin**, MF8'de düzgün eklensin. Özellik gibi görünen ölü kod tuzaktır |
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
