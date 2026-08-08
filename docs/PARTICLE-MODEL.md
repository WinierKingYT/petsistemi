# PARTICLE_MODEL Representation

`PARTICLE_MODEL`, görünmez bir marker etrafında matematiksel nokta dağılımları çizerek
model veya resource pack gerektirmeyen enerji petleri üretir. Hareket controller'ı marker'ı
taşır; görsel her zaman marker'ın güncel dünya konumunda çizilir.

```yaml
representation:
  type: PARTICLE_MODEL
  update-interval-ticks: 2
  model:
    - shape: RING
      particle: END_ROD
      points: 24
      radius: 0.75
      offset: [0.0, 0.15, 0.0]
      rotation-speed: 2.5
    - shape: HELIX
      particle: SOUL_FIRE_FLAME
      points: 20
      radius: 0.35
      height: 1.2
      rotation-speed: -2.0
```

## Şekiller

- `RING`: X/Z düzleminde halka.
- `SPHERE`: Fibonacci dağılımlı küre veya elipsoid.
- `HELIX`: İki turlu sarmal.
- `CUBE`: Küpün 12 kenarı üzerinde dağılım.
- `CONE`: Tabanından ucuna daralan sarmal koni.

## Alanlar ve limitler

- `update-interval-ticks`: 1–20; modelin kaç tick'te bir çizileceği.
- `particle`: Data istemeyen Bukkit particle adı. `BLOCK_CRACK`, `ITEM_CRACK` ve özel
  veri isteyen benzer particle'lar reddedilir.
- `points`: Part başına 3–256 nokta.
- `radius`: 0–8 blok.
- `height`: 0–16 blok.
- `offset`: Marker'a göre `[x, y, z]` yerel ofset.
- `rotation-speed`: Tick başına -360..360 derece; negatif değer ters yön verir.

Bir model en fazla 16 part ve toplam 256 nokta taşıyabilir. Bu limit config doğrulamasında
uygulanır; hatalı model runtime'a ulaşmaz. Ortak animation state motoru dönüş zamanını
etkiler: `SLEEPING` yavaşlatır, `MOVING`/`SPRINTING` hızlandırır, `ATTACKING` kısa süreli
en yüksek hızı kullanır.

`PARTICLE_MODEL` atomik bir graph sağlayıcısıdır ve `COMPOSITE` component'i olarak
kullanılabilir. Paketli tam örnek `astral_spirit.yml` dosyasıdır.
