# ADR 0005: Pet Tanımı Yükleme Hata Modları (Açılış vs. Reload)

## Durum
Kabul Edildi

## Bağlam
`AtomicPetDefinitionRegistry` tek bir tanım hattı (parse → validate) kullanır ve bu hat
hem sunucu açılışında hem de `/petadmin reload` sırasında çalışır. Başlangıçta her iki yol
da "tek hatalı dosya → tüm aday reddedilir" davranışını paylaşıyordu.

Bu, reload için doğru ama açılış için yıkıcıydı: yöneticinin `pets/` klasörüne eklediği tek
bir yazım hatası, sunucunun **hiçbir peti olmadan** açılmasına yol açıyordu. Oyuncular sahip
oldukları petleri çağıramıyor, `/pet list` boş dönüyordu; üstelik hata yalnızca konsolda tek
bir satırdı. Paketlenmiş dosyalarda gerçekten yaşandı: `shadow_wisp.yml` ve `sleepy_cat.yml`
doğrulamadan geçemediği için tüm kayıt reddediliyordu.

`MANUAL-TEST-MATRIX.md` de bu belirsizliği yansıtıyordu — bir satır bozuk dosyada diğer
petlerin yüklenmesini, başka bir satır hiçbirinin yüklenmemesini bekliyordu.

## Karar
İki yolun hata modları bilinçli olarak ayrıştırıldı:

| Yol | Davranış | Gerekçe |
|---|---|---|
| **Açılış** (`reload()`) | Hoşgörülü: geçerli tanımlar yayımlanır, bozuk dosyalar adları ve hata listeleriyle `SEVERE` loglanır | Yönetici hatası tüm oyuncuları cezalandırmamalı; sunucu çalışır kalmalı |
| **`/petadmin reload`** (`loadCandidateSnapshot()`) | Katı: tek hata bile aday snapshot'ı reddeder, çalışan tanımlar dokunulmadan kalır | Atomik reload garantisi — canlı sunucu asla yarım geçerli bir duruma geçmez |

Ortak tarama `scanPetsFolder()` içinde toplanır ve `ScanResult(definitions, errorsPerFile)`
döndürür. Tarama tanım hatalarında istisna fırlatmaz; "bu hata ölümcül mü" kararı çağırana
aittir.

## Sonuçlar
- Bozuk bir pet dosyası artık yalnızca o peti devre dışı bırakır; sunucu ve diğer petler çalışır.
- Atomik reload garantisi korunur: reload ya tamamen uygulanır ya da hiç uygulanmaz.
- Hatalar dosya adı + hata listesiyle raporlandığı için yönetici neyi düzelteceğini bilir.
- Açılışta hiçbir tanım yüklenemezse ayrıca uyarılır (sessiz boş kayıt yok).
- `PetDefinitionLoadFailureModeTest` her iki modu da kilitler; birinin diğerine kayması testi kırar.
