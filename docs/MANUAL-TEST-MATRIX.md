# MANUAL-TEST-MATRIX.md - Manuel Test Matrisi

## Kurulum
- [ ] Temiz data klasörü ile başlama
- [ ] Bozuk config dosyası ile başlama (Fail-fast doğrulanmalı)
- [ ] Geçersiz locale tanımı ile başlama
- [ ] Hatalı pet YAML dosyası ile **açılış**: geçerli petler yüklenir, bozuk dosya konsolda SEVERE olarak adıyla ve hata listesiyle raporlanır (sunucu kullanılabilir kalır)
- [ ] Tüm pet dosyaları bozukken açılış: "Hiçbir pet tanımı yüklenemedi" uyarısı görünür, sunucu yine de açılır
- [ ] SQLite dosyasına erişilememe durumu

## Migration
- [ ] Fresh database kurulumu
- [ ] V1 database upgrade
- [ ] V2 database upgrade
- [ ] V3 database upgrade
- [ ] V3 + DISABLED selected pet upgrade
- [ ] V3 + orphan ACTIVE pet upgrade
- [ ] V3 + imposter selection upgrade
- [ ] İkinci migration çalıştırması (Idempotency)

## Lifecycle
- [ ] `/pet` GUI üzerinden çağırma (Summon)
- [ ] Üst üste hızlı summon denemesi (Exploit engelleme)
- [ ] Pet değiştirme (Switch)
- [ ] Pet kaldırma (Dismiss)
- [ ] Oyuncu oyundan ayrılma/girme (Quit/Join)
- [ ] Chunk unload & reload
- [ ] Sunucu restart sonrası pet restore

## GUI & Admin
- [ ] 0 pet durumunda GUI görünümü
- [ ] Pagination (20+ pet durumunda sayfa geçişleri)
- [ ] Inventory shift-click / drag engelleme
- [ ] `/petadmin inspect <player>`
- [ ] `/petadmin reconcile all --dry-run`
- [ ] `/petadmin backup`

## Display Petler (Modüler Runtime)
- [ ] `arcane_crystal` (ITEM_DISPLAY + ORBIT) çağırma: kristal sahibin etrafında döner
- [ ] `floating_book` (ITEM_DISPLAY + FLYING_FOLLOW) çağırma: omuz üstünde süzülür
- [ ] `shoulder_orb` (ITEM_DISPLAY + ANCHORED) çağırma: sağ omuzda sabit durur, dönerken sahibiyle birlikte döner
- [ ] `void_cube` (BLOCK_DISPLAY + HOVER) çağırma: blok görünür, yumuşak yukarı-aşağı salınır
- [ ] `spirit_flame` (PARTICLE + HOVER) çağırma: sahibin üzerinde alev parçacıkları
- [ ] `ghost_scribe` (TEXT_DISPLAY + TRAIL) çağırma: yürürken pet arkada iz oluşturur, text doğru görünür
- [ ] `familiar_swarm` (MULTI_ENTITY + FORMATION) çağırma: 1 birincil + 3 çocuk formasyonda takip eder
- [ ] Display petin custom-model-data'sı resource pack ile doğru görünür
- [ ] Display pet sahibin arkasında kalınca ışınlanır (teleport-distance)
- [ ] Display pete hasar verilemez (invulnerable), moblarla çarpışmaz (collision-free)
- [ ] `/pet mode stay` ile display pet yerinde durur; `follow` ile tekrar takip eder
- [ ] Display pet isim etiketi (nameplate) Lv. ile doğru render edilir (TEXT_DISPLAY/PARTICLE hariç)
- [ ] Legacy `wolf` pet (ENTITY + GROUND_FOLLOW) eski davranışıyla çalışmaya devam eder
- [ ] Hatalı `representation.type` / `movement.type` içeren YAML **açılışta**: yalnızca o pet atlanır, diğerleri yüklenir, konsolda hata görünür
- [ ] Aynı hatalı YAML ile `/petadmin reload`: reload **başarısız** döner ve çalışan tanımlar değişmeden kalır (atomik reload); dosya düzeltilip tekrar reload edilince yüklenir
- [ ] Display pet ile sunucu restart sonrası restore çalışır
- [ ] Swarm (MULTI_ENTITY) dismiss edilince çocuk entity'lerin de silindiği görülür (entity leak yok)
- [ ] Swarm çocuklarına hasar verilemez; çocuğa sağ tık → sahibi pet menüsünü görür (child koruması)
- [ ] `ghost_scribe` (TEXT_DISPLAY) rename edilince ekrandaki yazı yeni isimle güncellenir
- [ ] `shoulder_orb` seviye atlayınca isim etiketi Lv. güncellenir; `spirit_flame` aurası bozulmaz (çift particle yok)
- [ ] `/petadmin setlevel` sonrası nameplate anında güncellenir

## Companion Sistemleri (Idle/Sleep, Reaction, Level Scaling, Mode Persistence)
- [ ] `features.idle-sleep.enabled: true` iken sahibi `idle-seconds` boyunca hiç hareket etmezse ENTITY pet oturur (Sittable sit); hareket edince ayağa kalkar
- [ ] ITEM_DISPLAY/BLOCK_DISPLAY peti rest modunda küçülür (scale × 0.65); TEXT_DISPLAY de küçülür
- [ ] Pet, sahibi hareketsizken bile 3 bloktan fazla uzaklaşırsa uyanır (restten çıkar)
- [ ] `features.reactions.enabled: true` iken: sahibi hasar alınca pet growl sesi + öfke parçacığı çıkarır (8 blok içinde)
- [ ] Pet seviye atlayınca happy parçacığı + mırlama sesi oynar
- [ ] Rest başlangıcı (uykuya geçiş) ve uyanışta ses duyulur
- [ ] `features.reactions.enabled: false` iken hiçbir reaction ses/parçacığı oluşmaz
- [ ] `features.level-scaling.enabled: true` iken display pet seviye yükseldikçe büyür (growth-per-level), max-multiplier'ı aşmaz
- [ ] `features.level-scaling.enabled: false` iken display pet boyutu seviyeden bağımsızdır
- [ ] `/pet mode stay` → sunucu restart → pet STAY moduyla geri gelir (follow_mode persistence)
- [ ] `/pet mode wander` → summon tekrar edilince WANDER modu uygulanır
- [ ] follow_mode kolonu V8 migration ile otomatik eklenir; eski veriler FOLLOW default alır
- [ ] Config reload sonrası idle-sleep/reactions/level-scaling değişiklikleri anında etkili olur (snapshot)

## Per-Pet States (states: MOVING/IDLE)
- [ ] `sleepy_cat` summon edilir; global `features.idle-sleep.enabled: false` iken bile sahibi 5 saniye (100 tick) durursa kedi uyur; hareket edince uyanır
- [ ] `sleepy_cat` `states.IDLE.after-ticks` global `idle-seconds`'tan küçükken daha erken uyur (per-pet eşik önceliği)
- [ ] `states.IDLE.animation: NONE` olan pet hiçbir koşulda rest moduna girmez (global feature açık olsa bile)
- [ ] Yalnızca `MOVING` tanımlı pet (IDLE yok) global feature kapalıyken uyumaz, açıkken global eşiği kullanır
- [ ] `states.IDLE.animation: WALK` yazılırsa tanım yüklenmez (validator hatası konsolda; mevcut tanımlar korunur)
- [ ] `states` bölümü olmayan eski petler (ör. `wolf`) davranışını aynen sürdürür (geri uyumluluk)

## Dönüşümler ve Çevre Varyantları (transforms)
- [ ] `wisplight` summon edilir; gece olunca (dünya saati 13000+) item `SOUL_LANTERN` + glow olur, gündüz `GLOWSTONE_DUST`'a döner
- [ ] `wisplight` suya girince item `LIGHT_BLUE_STAINED_GLASS` olur; sudan çıkınca geri döner
- [ ] Gece transform'u sırasında seviye atlanır/rename edilirse transform'lu görünüm korunur (refreshVisual derived definition kullanır)
- [ ] `wisplight` geceyken rest'e girerse ölçek küçülmesi transform'lu item üzerinde görünür
- [ ] `when.biome` eşleşmeyen biyomda transform uygulanmaz; `when.world` farklı dünyada uygulanmaz
- [ ] `when.weather: RAIN` yağmurlu havada uygulanır; açık havada base görünüm kalır
- [ ] `owner-state: FLYING` iken uçunca transform görünür; yere inince geri döner
- [ ] Hatalı transform (`when` veya `apply` boş, bilinmeyen biome/material) tanım yüklemesini engeller; eski tanımlar korunur
- [ ] `transforms` bölümü olmayan petlerde davranış değişmez (geri uyumluluk)

## Per-Pet Reactions (reactions: OWNER_DAMAGE/LEVEL_UP/REST_START/REST_END)
- [ ] `sleepy_cat` summon iken sahibi hasar alınca `ENTITY_CAT_HISS` sesi + 4 adet `VILLAGER_ANGRY` parçacığı çıkar (pet tanımındaki değerler; global default'u ezer)
- [ ] `sleepy_cat` seviye atlayınca 10 adet `VILLAGER_HAPPY` parçacığı çıkar (sound tanımsız → global default ses)
- [ ] `sleepy_cat` uyuyunca/uyanınca purr/ambient sesleri aynen devam eder (reaction tanımsız → global default)
- [ ] `reactions.LEVEL_UP.enabled: false` yazılırsa bu pet için seviye tepkisi tamamen kapalıdır (diğer petler etkilenmez)
- [ ] `reactions` bölümü olmayan petlerde (ör. `wolf`) global feature değerleri aynen kullanılır (geri uyumluluk)
- [ ] Hatalı sound/particle adı, `particle-count: 900` veya `volume: 3.0` tanım yüklemesini engeller; eski tanımlar korunur

## Per-Pet Emotes (emotes: + /pet emote)
- [ ] `/pet emote purr` → kedi `ENTITY_CAT_PURR` sesi + `HEART` parçacığı oynatır; 10 saniye içinde tekrar deneyince "Kalan süre: N saniye" mesajı gelir
- [ ] `/pet emote hiss` → `ENTITY_CAT_HISS` + `SMOKE_NORMAL`; 5 saniye cooldown
- [ ] `/pet emote <ad>` tab completion önerilen emote adlarını tamamlar; `/pet emote p` yazınca `purr` önerilir
- [ ] Aktif pet yokken `/pet emote purr` → "Önce petinizi çağırın"; tanımlı emote olmayan pette "tanımlı emote yok"
- [ ] Bilinmeyen isim `/pet emote xyz` → "Geçersiz emote" + mevcut emote listesi
- [ ] Emote'lu pet despawn edilip yeniden summon edilince cooldown sıfırlanır (cleanup ownerId ile)
- [ ] `features.reactions.enabled: false` iken emote ses/parçacığı oluşmaz ama cooldown mesajları normal akışta döner
- [ ] Büyük harfli isimler küçük harfe normalize edilir: `/pet emote PURR` çalışır
