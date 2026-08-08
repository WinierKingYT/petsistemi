# Pet Item Action System

MF7a, bir oyuncunun elindeki itemi aktif petinde kullanmasını veri güdümlü ve genişletilebilir
bir aksiyon hattına taşır. Yem, açma katalizörü ve ileride eklenecek evrim itemleri aynı
eşleştirme/tüketim/cooldown kurallarını kullanır.

## YAML şeması

`item-actions`, pet tanımının kökünde kimlikle anahtarlanmış bölümdür:

```yaml
item-actions:
  feed_bone:
    item:
      material: BONE
      # custom-model-data: 12001  # isteğe bağlı ikinci eşleştirici
    consume: 1
    cooldown-seconds: 2
    min-level: 1
    max-level: 0                 # 0 = üst sınır yok
    # permission: petsistemi.item.feed.wolf
    action: petsistemi:gain_experience
    parameters:
      amount: 25
```

Oyuncu itemi ana elinde tutup kendi aktif petine sağ tıklar. Materyal ve varsa
`custom-model-data` birlikte eşleşir. Off-hand olayı ikinci kez çalıştırılmaz.

Paketlenmiş `wolf.yml` örneği yalnızca yeni kurulumlarda otomatik kopyalanır. Eklenti mevcut
`plugins/PetSistemi/pets/wolf.yml` dosyasının üzerine yazmaz; mevcut sunucularda örnek bölümü
elle ekleyin veya kendi pet tanımınızda aynı şemayı kullanın.

## Yerleşik aksiyonlar

### `petsistemi:gain_experience`

`parameters.amount` kadar XP verir. XP, `ITEM_ACTION` kaynağıyla standart progression
servisinden geçer; level-up eventleri ve canlı pet yenilemesi normal akışını korur.

### `petsistemi:unlock_pet`

```yaml
action: petsistemi:unlock_pet
parameters:
  definition-id: phoenix
```

Oyuncuya hedef tanımdan yeni bir pet verir. Mevcut limitler, tanım kontrolü, event ve profil
cache güncellemesi standart `PetService` hattında uygulanır.

### `petsistemi:evolve_pet`

```yaml
action: petsistemi:evolve_pet
parameters:
  target-id: phoenix
```

Sağ tıklanan petin tanımını kalıcı olarak hedef tanımla değiştirir. Pet UUID'si, sahibi,
özel adı, seviyesi, XP'si, kullanılabilirlik ve seçim kaydı korunur. Pet aktifse yeni
representation ana thread'de yeniden oluşturulur; spawn başarısız olursa veritabanı ve
runtime eski tanıma geri alınır. `PetPreEvolutionEvent` iptal kapısı,
`PetEvolutionEvent` ise tamamlanma bildirimi sağlar.

### Petsiz unlock itemi

Aktif pet gerektirmeyen unlock itemini yönetici üretir:

```text
/petadmin unlockitem <oyuncu> <tanım_id> [miktar] [materyal]
```

Varsayılan materyal `NAME_TAG`'dir. Item PDC içinde şema sürümü ve hedef tanım kimliğiyle
işaretlenir; oyuncu havaya veya bloğa sağ tıklayarak kullanır. İşlem asenkron başarısızsa
rezerve edilen item iade edilir. Aynı oyuncunun eşzamanlı ikinci unlock isteği tüketimden
önce reddedilir. Üçüncü taraflar Bukkit `PetUnlockItemService` ile aynı itemleri üretebilir.

## İşlem güvenliği

- Eşleşmeyen item normal pet menüsü/mount etkileşimine bırakılır.
- Yetki, seviye, adet, handler, cooldown ve devam eden istek kontrolleri tüketimden önce yapılır.
- Asenkron aksiyon başladığında tüketilecek item ana elden rezerve edilir.
- Aksiyon başarısız olursa item envantere iade edilir; envanter doluysa oyuncunun yanına düşer.
- Cooldown yalnızca başarılı sonuçtan sonra başlar.
- Aynı oyuncu/pet/aksiyon için devam eden ikinci istek reddedilir.
- Creative oyunculardan item tüketilmez.

## Üçüncü taraf aksiyon kaydı

Motor Bukkit Services Manager üzerinden `PetItemActionService` yayımlar:

```java
PetItemActionService service = Bukkit.getServicesManager().load(PetItemActionService.class);
service.registerAction(new NamespacedKey(plugin, "heal"), (context, parameters) -> {
    // Bukkit çağrılarını ana thread üzerinde tutun; uzun I/O için CompletionStage döndürün.
    return CompletableFuture.completedFuture(PetItemActionResult.success("Pet iyileştirildi."));
});
```

YAML tarafında aksiyon `eklenti_adı:heal` olarak çağrılır. Core, üçüncü taraf key'lerini
parser ve validator boyunca korur; handler kurulu değilse item tüketmeden açık hata döner.

## MF7 kapsam durumu

MF7 tamamlandı: ortak item-action sözleşmesi, XP/yem, aktif pet katalizörleri, kalıcı
seçimli evrim, petsiz unlock itemi, orders ve sürüş kontrolü aynı gameplay katmanında
çalışır.
