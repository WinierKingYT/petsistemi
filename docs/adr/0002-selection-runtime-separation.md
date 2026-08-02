# ADR 0002: Availability, Selection ve Runtime State Ayrımı

## Durum
Kabul Edildi

## Bağlam
Eski mimaride petlerin veritabanındaki durumları ile dünyadaki fiziksel durumları tek bir `ACTIVE` / `AVAILABLE` statüsünde karıştırılıyordu. Bu durum oyuncu offline olduğunda veritabanında petin seçili ancak dünyada doğmamış durumunu ifade etmeyi zorlaştırıyordu.

## Karar
State kavramı 3 bağımsız modele ayrılmıştır:
1. **PetAvailabilityState** (`AVAILABLE`, `DISABLED`): Kalıcı veritabanı durumu.
2. **PetSelection** (`player_selected_pets`): Oyuncunun aktif tercih ettiği pet.
3. **PetRuntimeState** (`SPAWNING`, `ACTIVE`, `RESTORING`, `DESPAWNING`, `FAILED`): Dünyadaki canlı varlığın bellek içi (memory) yaşam döngüsü.

## Sonuçlar
- Oyuncu oyundan çıktığında veritabanı seçimi bozulmaz.
- `DISABLED` petlerin seçilmesi veya doğurulması kesin olarak engellenir.
- Network senkronizasyonu ve multi-server yönetimi için temiz bir altyapı sağlanır.
