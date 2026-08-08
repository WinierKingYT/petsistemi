# Pet Binek ve Sürüş Kontrolü

MF7d, daha önce yalnızca oyuncuyu pet entity'sine yolcu olarak ekleyen temel etkileşimi
gerçek bir runtime controller'a taşır. Binek oturumu, giriş okuma, hız/uçuş fiziği, izinler ve
temizlik tek yerde yönetilir.

## Etkinleştirme

Sürüş varsayılan olarak kapalıdır. Önce global anahtarı açın:

```yaml
features:
  riding:
    enabled: true
```

Pet bazında ayarlar:

```yaml
mount:
  enabled: true
  # permission: companionpets.mount.wolf
  speed-multiplier: 1.25
  allow-fly: false
```

`speed-multiplier` değeri `0.1–3.0` arasında olmalıdır. `mount` bölümü olmayan eski tanımlar,
global sürüş anahtarı açıkken `1.0` kara sürüşüyle geriye uyumlu çalışır. Açıkça
`mount.enabled: false` yazılan pet global ayardan bağımsız olarak binilemez.

Paketlenmiş `wolf.yml` kara bineği örneği içerir. Mevcut sunuculardaki pet dosyalarının üzerine
yazılmadığı için örnek blok gerekiyorsa elle eklenmelidir.

## Kontroller

- Sahip, çömelip aktif petine sağ tıklayarak biner.
- `W/S` ileri/geri, `A/D` yana yönlendirme yapar.
- Kara bineğinde `Space` zıplatır; basılı tutmak her tick tekrar zıplatmaz.
- Uçabilen binekte bakış açısı irtifayı yönlendirir, `Space` yükselme desteği verir.
- Standart Minecraft `Shift` davranışıyla veya tekrar Shift + sağ tıkla inilir.

`speed-multiplier`, blok/tick taban hızına uygulanır. Binek sürülürken petin normal follow veya
wander movement controller'ı çalıştırılmaz; evolution, transform, animation ve visual tick
zinciri çalışmayı sürdürür.

## Güvenlik ve yaşam döngüsü

- Yalnızca aktif petin sahibi binebilir; tanımdaki ek permission her binişte ve tick'te kontrol edilir.
- Global ayar reload ile kapanır veya izin kaybedilirse oyuncu güvenli biçimde indirilir.
- Uçuş sırasında kapatılan gravity değeri inme, dismiss, quit, world-change ve plugin shutdown
  temizliğinde önceki değerine döndürülür.
- Pet değişir, entity kaybolur veya oyuncuyla dünya ayrışırsa oturum otomatik kapanır.
- Binek oturumu veritabanına yazılmaz; restart sonrası oyuncu otomatik bindirilmez.

## Paper 1.20.4 giriş adaptörü

Paper 1.20.4 Bukkit API'si oyuncunun WASD durumunu doğrudan yayımlamaz. Bu nedenle sürüme
bağımlı input alanları `ReflectivePlayerMountInputProvider` içinde izole edilmiştir; core başka
hiçbir yerde CraftBukkit/NMS tipine bağlanmaz. Sunucu mapping'i değişirse eklenti açılmaya devam
eder, bir kez uyarı yazar ve yalnızca sürüş hareketini güvenli biçimde durdurur.

## Bukkit servisi

Üçüncü taraflar ana thread üzerinde `PetMountService` yükleyerek aktif peti bindirip indirebilir:

```java
PetMountService mounts = Bukkit.getServicesManager().load(PetMountService.class);
PetMountResult result = mounts.toggleMount(player);
```

Servis, `MOUNTED`, `DISMOUNTED`, `DISABLED`, `NO_PERMISSION` gibi açık sonuç durumları döndürür.

## MF7 kapsam durumu

MF7d tamamlandı; seçimli/kalıcı item evrimi ve petsiz unlock item akışı MF7e ile eklenerek
MF7 bütünü `COMPLETED` durumuna geçti.
