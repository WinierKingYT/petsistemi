# PROCEDURAL Representation

`PROCEDURAL`, matematiksel bir şeklin noktalarına kalıcı Item/Block/Text Display node'ları
yerleştirir. `PARTICLE_MODEL` her çizimde geçici parçacık üretirken PROCEDURAL spawn edilen
display graph'ını hareket ettirir; resource-pack itemleri, bloklar veya metin sembolleriyle
galaksi, DNA, küre, dalga ve geometrik companion'lar kurulabilir.

```yaml
representation:
  type: PROCEDURAL
  shape: CONSTELLATION
  points: 16
  radius: 1.15
  height: 1.0
  rotation-speed: 1.6
  pulse-amplitude: 0.12
  pulse-speed: 4.0
  update-interval-ticks: 1
  content:
    type: ITEM
    material: AMETHYST_SHARD
    model-data: 15201
    scale: [0.18, 0.18, 0.18]
```

## Şekiller

- `RING`: Dairesel yörünge.
- `SPHERE`: Fibonacci küresi.
- `HELIX`: Sabit yarıçaplı çift sarmal yolu.
- `SPIRAL`: Merkezden dışarı büyüyen düz spiral.
- `CUBE`: Küpün 12 kenarı.
- `WAVE`: Yatay sinüs dalgası.
- `CONE`: Yukarı doğru daralan sarmal.
- `CONSTELLATION`: Deterministik, farklı yarıçaplı yıldız kümesi.

## Alanlar ve sınırlar

- `points`: 3–32 kalıcı display node'u.
- `radius`: 0–8 blok; `height`: 0–16 blok.
- `rotation-speed`: Tick başına -360..360 derece.
- `pulse-amplitude`: 0–1; hem node uzaklığını hem display ölçeğini etkiler.
- `pulse-speed`: Pulse fazının tick başına derece ilerlemesi.
- `update-interval-ticks`: 1–10; display konumlarının güncellenme aralığı.
- `content`: Yalnız `ITEM`/`ITEM_DISPLAY`, `BLOCK`/`BLOCK_DISPLAY` veya
  `TEXT`/`TEXT_DISPLAY` olabilir.

Ortak animation state motoru hareket zamanını değiştirir: uyku yavaş, sprint ve saldırı
daha hızlıdır. `representation.scale` bütün yapının koordinat ölçeğidir; `content.scale`
tek node'un görsel boyutudur.

Her PROCEDURAL pet bir görünmez root marker ve `points` kadar server-side Display entity
oluşturur. Bu nedenle yoğun lobby'lerde düşük nokta sayısı veya ilerideki packet backend
tercih edilmelidir. Node sayısı ve content provider canlı evolution sırasında değiştirilemez.
PROCEDURAL, COMPOSITE içinde atomik component olarak da kullanılabilir. Paketli örnek
`arcane_galaxy.yml` dosyasıdır.
