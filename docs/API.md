# API.md - PetSistemi Public API Rehberi

`PetSistemi`, diğer Bukkit/Paper eklentilerinin pet yönetimi ve gelişim sistemine erişebilmesi için `Bukkit.getServicesManager()` üzerinden erişilebilen temiz bir Public API sunar.

## API Servisini Alma
```java
PetService petService = Bukkit.getServicesManager().load(PetService.class);
PetExperienceService xpService = Bukkit.getServicesManager().load(PetExperienceService.class);
```

## Örnek Kullanım
```java
// Oyuncunun aktif petini alma
Optional<PetSnapshot> activePet = petService.getSelectedPet(player.getUniqueId());

// Pet çağırma
petService.summon(player, petId);

// Tecrübe puanı ekleme
xpService.addExperience(petId, 250L, ExperienceSource.MOB_KILL);
```

## Etkinlikler (Events)
- `PetPreSummonEvent` (İptal Edilebilir)
- `PetSummonedEvent`
- `PetPreDismissEvent` (İptal Edilebilir)
- `PetDismissedEvent`
- `PetPreRenameEvent` (İptal Edilebilir)
- `PetRenamedEvent`
- `PetGainExperienceEvent`
- `PetLevelUpEvent`
- `PetSelectionChangedEvent`
- `PetRecoveryFailedEvent`
