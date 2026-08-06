# COMMANDS.md - Komut Listesi

## Oyuncu Komutları
- `/pet`: Oyuncu Pet Yönetim Menüsünü (GUI) açar.
- `/pet list [page]`: Oyuncunun sahip olduğu petleri listeler.
- `/pet summon <id>`: Belirtilen peti dünyada yanınıza çağırır.
- `/pet dismiss`: Çağırılmış peti geri gönderir (despawn).
- `/pet info [id]`: Pet detay bilgilerini gösterir.
- `/pet rename <id> <yeni_isim>`: Pet ismini değiştirir.
- `/pet mode <follow|stay|wander>`: Aktif petin takip modunu ayarlar (kalıcıdır).
- `/pet emote <ad>`: Petin tanımında (`emotes:`) yer alan bir emoteyi oynatır.
- `/pet help`: Komut yardım menüsünü görüntüler.

Pet tanımı bir `permission:` alanı taşıyorsa, o peti çağırmak için oyuncunun ilgili
yetkiye sahip olması gerekir; alan yoksa pet herkese açıktır. Bkz. [PERMISSIONS.md](PERMISSIONS.md).

## Admin Komutları
- `/petadmin give <oyuncu> <definition_id>`: Oyuncuya belirtilen türde yeni pet verir.
- `/petadmin remove <oyuncu> <pet_id>`: Oyuncunun petini kalıcı olarak siler.
- `/petadmin disable <pet_id>`: Peti devre dışı bırakır (DISABLED).
- `/petadmin enable <pet_id>`: Devre dışı bırakılmış peti tekrar kullanılabilir yapar.
- `/petadmin list <oyuncu>`: Oyuncunun petlerini listeler.
- `/petadmin info <pet_id>`: Pet detaylarını gösterir.
- `/petadmin addxp <pet_id> <miktar>`: Pet tecrübe puanı ekler.
- `/petadmin setxp <pet_id> <miktar>`: Pet tecrübe puanını doğrudan ayarlar.
- `/petadmin setlevel <pet_id> <seviye>`: Pet seviyesini ayarlar.
- `/petadmin summon <oyuncu> <pet_id>`: Oyuncunun petini çağırır.
- `/petadmin dismiss <oyuncu>`: Oyuncunun aktif petini kaldırır.
- `/petadmin inspect`: Baktığınız varlığın pet verilerini gösterir.
- `/petadmin reload`: Config, mesajlar ve pet tanımlarını atomik olarak yeniden yükler.
- `/petadmin health`: Veritabanı ve runtime durum raporunu sunar.
- `/petadmin backup`: Manuel veritabanı yedeği oluşturur.
- `/petadmin reconcile all [--dry-run]`: Tutarsız durumları tarar ve uzlaştırır.
