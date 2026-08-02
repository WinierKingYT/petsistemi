# TESTING.md - PetSistemi Test Dokümanı

## Test Çalıştırma Komutları

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"; ./gradlew clean test
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot"; ./gradlew clean test shadowJar
```

## Test Katmanları

1. **Domain Testleri**: State doğrulama, XP eğrileri, isim kuralları, ID resolver.
2. **Persistence Testleri**: Repositories, Schema Migration fixture'ları, Backup, Rollback, Composite Foreign Key.
3. **Runtime Testleri**: Dynamic Proxy failure-injection, State Machine geçişleri, Recovery Queue.
4. **Application Testleri**: Use case servisleri, Query, Lifecycle, Admin servisleri.
5. **Config & Messages Testleri**: Validation fail-fast, MiniMessage placeholder kaçırma.
