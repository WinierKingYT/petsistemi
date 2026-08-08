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
- [ ] `/pet collection`: 18 tanım açık/kilitli durumuyla görünür; sahip olunan filtresi doğru çalışır
- [ ] Koleksiyonda açık pet sol tıkla çağrılır, sağ tıkla incelenir; kilitli pet işlem yapmaz
- [ ] `/petadmin editor <tanım_id>` chat girdisini taslağa alır; `iptal` ve `-` davranışları doğrudur
- [ ] Editörde geçersiz materyal/tür kaydedilmez ve doğrulama hataları gösterilir
- [ ] Editör açıkken dosya dışarıdan değiştirilirse kayıt çakışma uyarısıyla reddedilir
- [ ] Başarılı editör kaydı sonraki summon'da uygulanır; hatalı başka YAML varsa dosya geri alınır
- [ ] `/petadmin inspect <player>`
- [ ] `/petadmin reconcile all --dry-run`
- [ ] `/petadmin backup`

## MF7a Item Actions
- [ ] Aktif `wolf` petine ana elde `BONE` ile sağ tık: 1 kemik tüketilir ve 25 XP eklenir
- [ ] Aynı yem 2 saniye içinde tekrar kullanılırsa tüketilmez ve cooldown mesajı görünür
- [ ] Off-hand etkileşimi item aksiyonunu ikinci kez çalıştırmaz
- [ ] Yetersiz item, uygunsuz seviye veya eksik permission durumunda item tüketilmez
- [ ] Asenkron action başarısız olduğunda rezerve edilen item envantere geri gelir
- [ ] Creative oyuncuda action çalışır ancak item tüketilmez

## MF7b Seviye Evrimi
- [ ] `phoenix` seviye 19'da temel ad/ölçekle görünür
- [ ] Seviye 20 olduğunda aynı UUID ile `Kadim Anka` adı ve 1.8 ölçek uygulanır
- [ ] `/petadmin setlevel` ile seviye tekrar 19'a çekilince temel görünüm geri gelir
- [ ] Evrim görünümü dismiss/summon ve sunucu restart sonrasında kalıcı seviyeden yeniden kurulur
- [ ] Evrilmiş petin gece transform'u, idle ölçeği ve animasyonları birlikte çalışır
- [ ] Eksik `target-id` veya farklı representation/movement provider kullanan hedef reload'u reddeder

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

## Pet Emirleri (MF7c)
- [ ] `/pet order follow`, `/pet order stay`, `/pet order wander` aktif petin modunu değiştirir ve restart sonrası korur
- [ ] Eski `/pet mode follow|stay|wander` aynı sonuç ve kalıcılık davranışını üretir
- [ ] `allowed-modes: [FOLLOW]` tanımlı pette `stay`/`wander` emirleri reddedilir ve tab completion'da görünmez
- [ ] `/pet order come` peti oyuncunun arkasına getirir; mevcut kalıcı takip modunu değiştirmez
- [ ] MULTI_ENTITY pette `/pet order come` ana entity ve bütün child entity'leri birlikte taşır
- [ ] Aktif pet yokken emir açık hata döndürür; hızlı ikinci emir ilk işlem sürerken reddedilir
- [ ] Bukkit `PetOrderService` üzerinden kaydedilen `eklenti:emir` çalışır ve tab completion'da görünür

## Pet Binekleri (MF7d)
- [ ] `features.riding.enabled: false` iken `mount.enabled: true` pet binmeyi reddeder
- [ ] Global sürüş açıkken sahibi Shift + sağ tıkla `wolf` üzerine biner; başka oyuncu binemez
- [ ] WASD kara bineğini bakış yönüne göre sürer, Space tek basışta zıplatır
- [ ] `speed-multiplier: 1.25`, `1.0` bineğe göre gözle görülür fakat kontrollü hız artışı üretir
- [ ] `allow-fly: true` binekte bakış pitch'i yükselme/alçalmayı, Space yükselmeyi kontrol eder
- [ ] Sürüş sırasında FOLLOW/WANDER controller velocity'yi geri ezmez; animasyon/görsel güncellenir
- [ ] Shift ile inince ve global riding reload ile kapanınca önceki gravity değeri geri gelir
- [ ] Binek üzerindeyken dismiss, pet değişimi, world change, quit ve plugin disable güvenli indirir
- [ ] Geçersiz `speed-multiplier` (`0`, `4`, NaN) pet tanımının yüklenmesini engeller
- [ ] Bukkit `PetMountService` üçüncü taraf eklentiden mount/dismount sonucu döndürür

## Kalıcı Evrim ve Petsiz Unlock Itemleri (MF7e)
- [ ] `petsistemi:evolve_pet` itemi aktif pete kullanıldığında UUID, ad, seviye ve XP korunur; görünüm hedef tanıma geçer
- [ ] Aktif olmayan sahipli pete servis üzerinden evrim uygulandığında değişiklik restart sonrası korunur
- [ ] Evrim hedefinin representation/movement türü farklıysa aktif pet güvenli biçimde yeni controller ile yeniden doğar
- [ ] Hedef runtime spawn kasıtlı bozulduğunda pet kaydı ve eski görünüm geri gelir; item iade edilir
- [ ] `PetPreEvolutionEvent` iptal edilince DB, runtime ve item adedi değişmez
- [ ] `/petadmin unlockitem <oyuncu> wolf` ile verilen item aktif pet yokken havaya sağ tıkla pet açar
- [ ] Unlock sırasında DB hatasında item envantere döner; dolu envanterde oyuncunun yanına düşer
- [ ] Unlock itemine hızlı çift sağ tık ikinci pet kaydı üretmez
- [ ] Sıradan aynı materyal (`NAME_TAG`) PDC işareti yoksa unlock işlemi başlatmaz
- [ ] Bukkit `PetUnlockItemService` ile üretilen item komutla üretilen itemle aynı şekilde çalışır

## Ekosistem (MF8)

- [ ] İki sunucu aynı MySQL'e farklı `server-id` ile bağlandığında A'daki seçim/değişiklik B'deki çevrimiçi oyuncunun petini bir poll aralığında yeniler
- [ ] Aynı pete iki sunucudan eşzamanlı XP/değişiklik yazımı kilit zaman aşımı olmadan tamamlanır ve veri bozulmaz
- [ ] MySQL modunda otomatik dosya yedeği zamanlanmaz; `/petadmin backup` sağlayıcı snapshot/mysqldump yönlendirmesi verir
- [ ] Geçerli `.petpack` inbox'tan kurulur, `namespace:pet` tanımı listelenir ve restart sonrası receipt korunur
- [ ] Paket yükseltmesinde yeni sürümden çıkarılan pet dosyası silinir; bozuk referanslı yükseltme eski sürüme rollback yapar
- [ ] Başka kurulu paketin bağımlı olduğu paket kaldırılamaz
- [ ] ZIP traversal, dosya/adet/boyut sınırı ve yüksek `minimum-engine-version` kurulumdan önce reddedilir
- [ ] Marketplace yalnız HTTPS kataloğu kabul eder; hatalı SHA-256 paket dosyasını yayınlamadan siler
- [ ] `/petadmin marketplace refresh|list|install` ana thread'i bloklamadan tamamlanır
