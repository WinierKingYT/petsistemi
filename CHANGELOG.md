# CHANGELOG - PetSistemi

## [0.1.0-alpha.1] - 2026-08-03

### Eklenenler
- Gradle Shadow Plugin entegrasyonu ve `PetSistemi-0.1.0-alpha.1.jar` release artifact üretimi.
- Baştan sona yenilenmiş `DatabaseMigration` ve `MigrationRunner` mimarisi (V1, V2, V3, V4, V5, V6, V7).
- Otomatik migration öncesi veritabanı yedeği alma sistemi (`database-backups/`).
- `PetAvailabilityState` (`AVAILABLE`, `DISABLED`), `PetSelection` (`player_selected_pets`) ve `PetRuntimeState` modellerine geçiş.
- Bağımsız `PetPluginBootstrap` ve modüler `Registrar` sınıfları.
- Typed `PluginConfiguration` ve `ConfigurationValidator` fail-fast mekanizması.
- `MessageService`, MiniMessage tag kaçırma koruması ve `tr_TR` / `en_US` dil paketleri.
- `PetListMenu` GUI (sayfalama, status göstergeleri ve koruma).
- `PetIdResolver` kısa UUID çözümleme mantığı.
- `ExperienceCurve` eğrileri (Linear, Exponential, Table).
- `AuditLogger` ile `pet_audit_log` veritabanı denetim kaydı.
- `DatabaseExecutor` (`PetSistemi-Database-1` single-thread asenkron veritabanı yürütücüsü).
- Public API paketindeki `PetSelectionChangedEvent` ve `PetRecoveryFailedEvent` etkinlikleri.
- GitHub Actions CI build workflow (`.github/workflows/build.yml`).
