# SPRITE Representation

`SPRITE`, resource-pack custom model data karelerini bir `ItemDisplay` üzerinde değiştirerek
2D veya 2.5D pet üretir. Entity yeniden spawn edilmez; yalnızca item karesi güncellenir.

```yaml
representation:
  type: SPRITE
  material: PAPER
  billboard: CENTER
  scale: [0.8, 0.8, 0.8]
  animations:
    IDLE:
      frame-ticks: 6
      loop: true
      frames: [14101, 14102, 14103, 14102]
    MOVING:
      frame-ticks: 3
      loop: true
      frames: [14111, 14112, 14113]
```

## Alanlar

- `material`: Resource pack modelini taşıyan geçerli item. Genellikle `PAPER` kullanılır.
- `billboard`: `CENTER`, `VERTICAL`, `HORIZONTAL` veya `FIXED`.
- `scale`: ItemDisplay ölçeği.
- `animations.<STATE>.frame-ticks`: Her karenin ekranda kalacağı tick sayısı.
- `animations.<STATE>.loop`: Son kareden sonra başa dönüp dönmeyeceği.
- `animations.<STATE>.frames`: Sıralı custom-model-data değerleri; 1–256 kare.

Desteklenen state adları ortak animasyon motoruyla aynıdır: `IDLE`, `MOVING`,
`SPRINTING`, `SLEEPING` ve `ATTACKING`. İstenen state tanımlı değilse `IDLE`, o da yoksa
ilk tanımlı animasyon kullanılır. Tek karelik eski kullanım için `custom-model-data`
verilip `animations` atlanabilir.

`SPRITE` atomik bir sağlayıcıdır; bu nedenle `COMPOSITE` içindeki bir component olarak da
kullanılabilir. Paketli `pixel_slime.yml` tam örnektir. Karelerin gerçekten görünmesi için
aynı custom-model-data değerlerini sağlayan resource pack modelleri gerekir; packsiz
istemcide temel `material` görünür.
