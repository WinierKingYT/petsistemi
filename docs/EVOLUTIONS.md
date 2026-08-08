# Seviye Tabanlı Pet Evrimi

MF7b, daha önce yalnızca ayrıştırılan `evolutions:` şemasını runtime'a bağlar. Evrim aşaması
veritabanına ayrı bir bayrak olarak yazılmaz; petin kalıcı seviyesinden her açılışta ve runtime
tick'inde deterministik olarak türetilir. Böylece pet UUID'si, sahibi, adı, XP'si ve seçim kaydı
değişmez.

## Şema

```yaml
evolutions:
  - min-level: 20
    target-id: phoenix
    display-name: "<red><bold>Kadim Anka</bold></red>"
    scale: { x: 1.8, y: 1.8, z: 1.8 }
```

Petin seviyesini karşılayan aşamalar arasından `min-level` değeri en yüksek olan seçilir.
Hiçbir aşama eşleşmezse temel tanım kullanılır. Seviye düşürülürse görünüm daha düşük aşamaya
veya temel tanıma geri döner.

`target-id` aynı pet tanımı olabilir; paketlenmiş `phoenix.yml` bu modeli kullanır. Başka bir
tanıma işaret ederse hedefin görsel ayarları temel alınır, ardından aşamanın `display-name` ve
`scale` override'ları uygulanır.

## Runtime sırası

Her pet tick'inde görsel tanım şu sırada türetilir:

1. kalıcı pet seviyesi → evolution aşaması;
2. evolved tanım → çevre/owner-state `transforms:` override'ı;
3. idle/sleep ölçeği ve animasyon state'i;
4. representation'ın normal görsel tick'i.

Aşama değişmediyse pet her tick yeniden render edilmez. Tanım reload ile değişirse türetilmiş
tanım imzası değişir ve aynı seviyede bile görsel yenilenir.

## Doğrulama ve sınırlar

- `min-level` en az 1 ve aynı pet içinde benzersiz olmalıdır.
- `target-id`, `pets/` klasöründe geçerli ve yüklenebilir bir tanıma işaret etmelidir.
- Ölçek bileşenleri sıfırdan büyük olmalıdır.
- Kaynak ve hedef aynı namespaced representation ve movement sağlayıcısını kullanmalıdır.
  Canlı runtime handle controller değiştirmediği için farklı sağlayıcı geçişi yüklemede reddedilir.
- Hatalı hedef zinciri startup'ta kaynak tanımı dışarıda bırakır; katı `/petadmin reload` sırasında
  bütün snapshot'ı reddeder.

## Kalıcı/seçimli evrim

MF7e ile `petsistemi:evolve_pet` item action'ı kalıcı evrim hattını açar. Otomatik seviye
evriminin aksine bu işlem pet kaydındaki `definition_id` değerini değiştirir. Kimlik ve
ilerleme alanları korunur; aktif pet yeni hedef tanımla yeniden doğar. Ön event iptali,
oyuncu başına işlem kilidi ve runtime spawn hatasında DB + runtime compensation uygulanır.
Örnek şema ve API olayları için [ITEM-ACTIONS.md](ITEM-ACTIONS.md) belgesine bakın.
