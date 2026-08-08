# Visual Graph Runtime

Visual Graph, pet görünümünü tek bir `Entity` ve isimsiz child listesi olmaktan çıkarıp
adlandırılmış, parent-child ilişkili bileşenler olarak taşır. Bu katman yeni bir
representation enum'u değildir; `COMPOSITE`, `DISPLAY_MODEL`, `SPRITE` ve ilerideki
packet render backend'inin üzerine kurulacağı ortak yaşam döngüsüdür.

## Temel sözleşmeler

- `PetVisualNodeDefinition`: config/domain tarafındaki bir node'u, parent'ını,
  representation tanımını ve yerel transformunu taşır.
- `PetVisualGraphDefinition`: tek root, benzersiz node kimliği, mevcut parent ve
  döngüsüz graph kurallarını doğrular.
- `PetVisualComponent`: spawn edilmiş bir node'un kararlı kimliğini ve varsa Bukkit
  entity'sini taşır.
- `PetVisualHandle`: bütün spawn edilmiş graph'ın tek runtime handle'ıdır; primary entity,
  secondary entity ve node→entity sorgularını sağlar.
- `PetRenderBackend`: görselin `SERVER` veya ileride `VIRTUAL` backend ile üretilmesini
  representation türünden ayrı tutar.

Node kimlikleri küçük harfe normalize edilir ve `[a-z0-9][a-z0-9._-]{0,63}` kuralına
uyar. `body`, `head`, `left-wing` ve `aura.core` geçerli örneklerdir.

## Geriye uyumluluk

Mevcut `PetRepresentationController` implementasyonlarının değiştirilmesi gerekmez.
Varsayılan adaptör eski `spawn` + `spawnChildren` sonucunu şu graph'a çevirir:

```text
root
├── child-1
├── child-2
└── child-n
```

Eski entity tabanlı `tickVisual`, `updateVisual`, `applyRestState`, `applyAnimation`,
`remove` ve `isValid` metotları korunur. Handle tabanlı runtime metotları ayrı adlar
kullanır; böylece `null` argümanlı üçüncü taraf Java kodunda overload belirsizliği oluşmaz.

`ActivePet` artık handle'ı yaşam döngüsünün otoritesi olarak tutar, fakat eski
`getSpawnedEntity()` ve `getChildren()` görünümlerini üretmeye devam eder. Registry,
graph içindeki bütün server entity'lerini aynı aktif pete eşler.

## Mevcut sınır

`PetVisualHandle` entity taşımayan `VIRTUAL` graph'ları ifade edebilir. Mevcut movement
motoru konum anchor'ı olarak hâlâ bir primary server entity istediği için coordinator
şimdilik entity'siz handle spawn'ını kabul etmez. Packet backend eklendiğinde anchor ve
render konumu ayrılacaktır.

## Uygulanan dilimler

- ✅ `COMPOSITE` YAML şeması, validator ve graph-aware controller
- ✅ Node parent/translation/yaw-pitch/scale çözümleyicisi
- ✅ Atomik spawn rollback ve birleşik lifecycle dispatch
- ✅ `DISPLAY_MODEL` display-only skeleton, quaternion hierarchy ve keyframe kanalları
- ✅ `SPRITE` ItemDisplay billboard ve state tabanlı resource-pack frame animasyonu
- ✅ `PARTICLE_MODEL` bütçeli prosedürel particle şekilleri ve state hızları
- ✅ `PROCEDURAL` kalıcı display-node dağılımları, rotation ve pulse animasyonu

## Sonraki dilimler

1. Server/packet backend seçimi ve owner-only görünürlük

COMPOSITE şeması ve kısıtları için [COMPOSITE.md](COMPOSITE.md) belgesine bakın.
DISPLAY_MODEL kullanımı için [DISPLAY-MODEL.md](DISPLAY-MODEL.md) belgesine bakın.
SPRITE kullanımı için [SPRITE.md](SPRITE.md) belgesine bakın.
PARTICLE_MODEL kullanımı için [PARTICLE-MODEL.md](PARTICLE-MODEL.md) belgesine bakın.
PROCEDURAL kullanımı için [PROCEDURAL.md](PROCEDURAL.md) belgesine bakın.
