# COMPOSITE Representation

`COMPOSITE`, farklı representation controller'larını tek bir adlandırılmış visual graph
altında birleştirir. Her component mevcut `ENTITY`, `ITEM_DISPLAY`, `BLOCK_DISPLAY`,
`TEXT_DISPLAY`, `PARTICLE`, `INVISIBLE`, `MULTI_ENTITY` veya kayıtlı harici provider
anahtarlarından birini kullanır.

```yaml
representation:
  type: COMPOSITE
  root: body
  components:
    body:
      type: ENTITY
      entity-type: WOLF
      baby: true

    crown:
      parent: body
      type: ITEM_DISPLAY
      item-material: GOLD_NUGGET
      custom-model-data: 1201
      transform:
        translation: [0.0, 0.75, 0.0]
        rotation: [0.0, 0.0, 0.0]
        scale: [0.45, 0.45, 0.45]

    aura:
      parent: body
      type: PARTICLE
      particle-type: FLAME
      particle-count: 6
      particle-offset: 0.25
      transform:
        translation: [0.0, 0.2, 0.0]
```

Vektörler `[x, y, z]` listesi veya `x/y/z` alt alanlarıyla yazılabilir. `translation`
için `transform.offset` ve doğrudan `offset` alias'ları da kabul edilir.

## Kurallar

- Graph tam olarak bir `root` taşır ve 2-32 component içerir.
- Root dışındaki her component mevcut bir `parent` belirtir.
- Component kimlikleri küçük harfe normalize edilir, benzersiz ve döngüsüz olmalıdır.
- İç içe `COMPOSITE` desteklenmez.
- Component tipi için kayıtlı controller bulunamazsa spawn atomik olarak geri alınır.
- Root hareket motorunun server-entity anchor'ıdır. Mevcut runtime sürümünde bütün
  component'ların server entity anchor üretmesi gerekir.
- Canlı refresh sırasında material, model-data, particle ve scale değişebilir; component
  kimliği, parent ilişkisi ve provider anahtarı değişemez.

## Runtime davranışı

Composite controller component'ların içeriğini kendisi render etmez. Her node'u ilgili
controller'a delege eder; tick, update, idle/sleep, animation ve remove çağrılarını bütün
node'lara dağıtır. Child node'lar parent entity konumuna göre senkronize edilir. Yerel
translation parent yaw'ıyla döner, X/Y rotation yaw/pitch'e eklenir ve yerel scale component
representation scale'iyle çarpılır. Z/roll metadata'sı `DISPLAY_MODEL` dilimi için korunur.

Paketli `fire_familiar.yml`, `ITEM_DISPLAY + ITEM_DISPLAY + PARTICLE` birleşiminin çalışan
örneğidir.

