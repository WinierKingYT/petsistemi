# DISPLAY_MODEL Representation

`DISPLAY_MODEL`, Item/Block/Text Display entity'lerinden oluşan parent-child skeleton ve
state tabanlı keyframe animasyonları sağlar. ModelEngine gerektirmez; resource-pack
custom-model-data parçalarıyla robot, kuş, drone, ejderha veya mekanik pet üretilebilir.

```yaml
representation:
  type: DISPLAY_MODEL
  root: body
  parts:
    body:
      type: ITEM
      material: IRON_NUGGET
      model-data: 1301

    left-wing:
      parent: body
      type: ITEM
      material: FEATHER
      model-data: 1302
      transform:
        translation: [0.6, 0.1, 0.0]
        rotation: [0.0, 0.0, -12.0]
        scale: [0.7, 0.7, 0.7]

  animations:
    MOVING:
      duration-ticks: 10
      loop: true
      bones:
        left-wing:
          - tick: 0
            rotation: [0.0, 0.0, -35.0]
          - tick: 5
            rotation: [0.0, 0.0, 35.0]
          - tick: 10
            rotation: [0.0, 0.0, -35.0]
```

## Part türleri

- `ITEM` veya `ITEM_DISPLAY`
- `BLOCK` veya `BLOCK_DISPLAY`
- `TEXT` veya `TEXT_DISPLAY`

`material`/`item-material`, `model-data`/`custom-model-data` alias'ları eşdeğerdir.
Skeleton 1-64 part içerebilir. İç içe graph representation veya mob entity kabul edilmez.

## Transform ve hierarchy

Her part yerel `translation`, derece cinsinden XYZ `rotation` ve `scale` taşır. Runtime
parent quaternion, scale ve translation değerlerini child bone'a aktarır. Böylece parent
döndüğünde child yalnızca kendi ekseninde dönmez; parent çevresindeki konumu da değişir.

Modelin tamamına uygulanan `representation.scale`, skeleton root scale'ine eklenir. Bu,
level/evolution transformlarının bütün modeli birlikte büyütmesini sağlar. Display entity'ler
`FIXED` billboard moduna alınır.

## Animation kanalları

Kanallar `IDLE`, `MOVING`, `SPRINTING`, `SLEEPING` ve `ATTACKING` state'lerini kullanır.
Her animation:

- `duration-ticks`: 1-12000
- `loop`: state varsayılanını override eder
- `bones`: bone başına sıralanan keyframe listesi

Translation, rotation ve scale keyframe'ler arasında doğrusal interpolate edilir. Kanalı
olmayan bone static transformunu korur; loop olmayan klip son pozu tutar. Skeleton kimliği,
parent ilişkisi ve part provider'ı canlı refresh/evolution sırasında değiştirilemez.

Paketli [mechanical_bird.yml](../src/main/resources/pets/mechanical_bird.yml) örneği üç
bone ve IDLE/MOVING kanalları içerir.

