# ARCHITECTURE.md - PetSistemi Mimari Dokümanı

## 1. Katmanlı Mimari Yapısı

`PetSistemi`, Paper 1.20.4 üzerinde çalışan modüler, katmanlı bir mimariye sahiptir:

```
[ Presentation Layer ] (Commands, GUIs, Listeners, Localization)
         │
         ▼
[ Application Layer ] (Use Cases: Query, Lifecycle, Ownership, Admin, Progression)
         │
         ▼
[ Domain Layer ] (PetInstance, PetDefinition, PetSelection, AvailabilityState, Clean Models)
    ▲         ▲
    │         │
[ Runtime ]  [ Persistence ] (DatabaseExecutor, Repositories, SchemaMigrator)
```

## 2. Temel İlkeler ve Sorumluluk Ayrımı

1. **Clean Domain**: Domain katmanı Bukkit/Paper bağımlılıklarından (`Player`, `Entity`, `Inventory`, `Component`) tamamen arındırılmıştır.
2. **Application Decoupling**: Command ve GUI katmanları asla `PetRepository` veya SQL bağlantılarına doğrudan erişmez; tüm işlemler Application Servisleri (`PetLifecycleService`, `PetAdminService`, vb.) üzerinden yürütülür.
3. **Thread Safety & Isolation**: Bukkit entity doğurma, silme ve GUI işlemleri **Paper Main Thread** üzerinde; tüm SQLite OKU/YAZ/MİGRATION operasyonları ise **Single-Thread Database Executor** üzerinde yürütülür.
4. **State Separation**:
   - **Availability State**: `AVAILABLE`, `DISABLED` (Persistent Storage)
   - **Selection**: `player_selected_pets` (Persistent Storage)
   - **Runtime State**: `SPAWNING`, `ACTIVE`, `RESTORING`, `DESPAWNING`, `FAILED` (Active Memory Registry)
