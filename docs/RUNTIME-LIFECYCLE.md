# RUNTIME-LIFECYCLE.md - PetSistemi Runtime Varlık ve State Machine Dokümanı

## 1. Runtime State Machine

Petlerin hafızadaki (memory) yaşam döngüsü açık durum geçişleri (State Machine) ile yönetilir:

```
    ┌─────────────┐
    │   ABSENT    │
    └──────┬──────┘
           │ (spawn request)
           ▼
    ┌─────────────┐
    │  SPAWNING   │
    └──────┬──────┘
           │ (success)
           ▼
    ┌─────────────┐   (chunk unload / crash)   ┌─────────────┐
    │   ACTIVE    ├───────────────────────────►│  RESTORING  │
    └──────┬──────┘                            └──────┬──────┘
           │ (dismiss / quit)                         │ (success)
           ▼                                          ▼
    ┌─────────────┐                            ┌─────────────┐
    │ DESPAWNING  │◄───────────────────────────┤   FAILED    │
    └─────────────┘     (retries exhausted)    └─────────────┘
```

## 2. Recovery Queue ve Merkezi Kurtarma

- Chunk Unload, sunucu yeniden başlatılması veya beklenmeyen varlık silinmelerinde merkezi `PetRecoveryQueue` devreye girer.
- Üstel geri Çekilme (Exponential Backoff): 1s, 3s, 10s gecikmelerle maksimum 3 deneme yapılır.
- Başarısızlık durumunda outcome açıkça `RETRIES_EXHAUSTED` veya `DEFINITION_MISSING` olarak işaretlenir.
