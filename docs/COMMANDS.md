# COMMANDS.md - Komut Listesi

## Oyuncu Komutları
- `/pet`: Oyuncu Pet Yönetim Menüsünü (GUI) açar.
- `/pet list [page]`: Oyuncunun sahip olduğu petleri listeler.
- `/pet summon <id>`: Belirtilen peti dünyada yanınıza çağırır.
- `/pet dismiss`: Çağırılmış peti geri gönderir (despawn).
- `/pet info [id]`: Pet detay bilgilerini gösterir.
- `/pet rename <id> <yeni_isim>`: Pet ismini değiştirir.
- `/pet help`: Komut yardım menüsünü görüntüler.

## Admin Komutları
- `/petadmin give <oyuncu> <definition_id>`: Oyuncuya belirtilen türde yeni pet verir.
- `/petadmin remove <oyuncu> <pet_id>`: Oyuncunun petini kalıcı olarak siler.
- `/petadmin disable <pet_id>`: Peti devre dışı bırakır (DISABLED).
- `/petadmin enable <pet_id>`: Devre dışı bırakılmış peti tekrar kullanılabilir yapar.
- `/petadmin addxp <pet_id> <miktar>`: Pet tecrübe puanı ekler.
- `/petadmin setlevel <pet_id> <seviye>`: Pet seviyesini ayarlar.
- `/petadmin reload`: Config, mesajlar ve pet tanımlarını atomik olarak yeniden yükler.
- `/petadmin health`: Veritabanı ve runtime durum raporunu sunar.
- `/petadmin backup`: Manuel veritabanı yedeği oluşturur.
- `/petadmin reconcile all [--dry-run]`: Tutarsız durumları tarar ve uzlaştırır.
