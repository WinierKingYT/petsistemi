# Pet Emir Sistemi

MF7c, kalıcı takip modlarını ve tek seferlik pet komutlarını ortak, namespaced bir emir
motorunda toplar. Oyuncular `/pet order <emir>` kullanır; eski `/pet mode` komutu aynı motoru
çağıran geriye uyumlu bir kısayol olarak kalır.

## Yerleşik emirler

| Anahtar | Komut | Davranış |
|---|---|---|
| `petsistemi:follow` | `/pet order follow` | Pet sahibini takip eder; seçim kaydına yazılır. |
| `petsistemi:stay` | `/pet order stay` | Pet bulunduğu bölgede kalır; seçim kaydına yazılır. |
| `petsistemi:wander` | `/pet order wander` | Pet sahibinin yakınında dolaşır; seçim kaydına yazılır. |
| `petsistemi:come` | `/pet order come` | Bir defalık emirle ana ve child entity'leri sahibinin arkasına getirir. |

`/pet mode follow|stay|wander` üretimde aynı `PetOrderEngine` hattına gider. Böylece aktif pet
kontrolü, pet tanımındaki `allowed-modes`, devam eden işlem koruması ve sonuç mesajları iki
komut arasında ayrışmaz. `come` kalıcı takip modunu değiştirmez.

## İşlem güvenliği

- Emir yalnızca oyuncunun aktif petine uygulanır.
- Pet tanımı bulunamazsa veya handler kayıtlı değilse işlem açık hata ile durur.
- Aynı oyuncunun önceki emri tamamlanmadan ikinci emir başlatılmaz.
- `follow`, `stay` ve `wander`, pet tanımındaki `allowed-modes` listesine uyar.
- Asenkron handler hataları sonuç future'ına çevrilir; pending kilidi her sonuçta temizlenir.
- Komut yanıtı Bukkit ana thread'ine geri taşınır.

## Üçüncü taraf emir kaydı

Motor Bukkit Services Manager üzerinden `PetOrderService` yayımlar:

```java
PetOrderService service = Bukkit.getServicesManager().load(PetOrderService.class);
NamespacedKey wave = new NamespacedKey(plugin, "wave");

service.registerOrder(wave, context -> {
    context.petEntity().getWorld().spawnParticle(
            Particle.HEART, context.petEntity().getLocation(), 4);
    return CompletableFuture.completedFuture(PetOrderResult.success("Petiniz el salladı."));
});

service.executeOrder(player, wave);
```

Uzun I/O için handler bir `CompletionStage` döndürebilir. Bukkit entity çağrıları yine ana
thread üzerinde yapılmalıdır. Üçüncü taraf anahtarları tab completion'da `eklenti:emir`
biçiminde gösterilir.

## MF7 kapsam durumu

MF7c tamamlandı: açık order API/registry, yerleşik kalıcı modlar, tek seferlik `come`, komut ve
Bukkit servis entegrasyonu. Mount-control MF7d'de, seçimli/kalıcı item evrimi ve petsiz
unlock itemi MF7e'de tamamlandı; MF7 bütünü `COMPLETED` durumundadır.
