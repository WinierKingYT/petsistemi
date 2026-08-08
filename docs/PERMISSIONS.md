# PERMISSIONS.md - Yetki Listesi

| Yetki (Permission) | Açıklama | Varsayılan |
| :--- | :--- | :--- |
| `companionpets.use` | Temel `/pet` komutlarını ve GUI menüsünü kullanma yetkisi | `true` |
| `companionpets.admin` | Tüm yönetici (`/petadmin`) komutlarına erişim yetkisi | `op` |
| `companionpets.admin.give` | Oyunculara pet verme yetkisi | `op` |
| `companionpets.admin.remove` | Pet silme yetkisi | `op` |
| `companionpets.admin.list` | Oyuncunun petlerini listeleme yetkisi | `op` |
| `companionpets.admin.info` | Pet detaylarını görüntüleme yetkisi | `op` |
| `companionpets.admin.addxp` | Pete XP ekleme yetkisi | `op` |
| `companionpets.admin.setxp` | Pet XP ayarlama yetkisi | `op` |
| `companionpets.admin.setlevel` | Pet seviye ayarlama yetkisi | `op` |
| `companionpets.admin.summon` | Oyuncunun petini çağırma yetkisi | `op` |
| `companionpets.admin.dismiss` | Oyuncunun petini kaldırma yetkisi | `op` |
| `companionpets.admin.inspect` | Baktığı varlığı denetleme yetkisi | `op` |
| `companionpets.admin.disable` | Pet devre dışı bırakma yetkisi | `op` |
| `companionpets.admin.enable` | Pet etkinleştirme yetkisi | `op` |
| `companionpets.admin.reload` | Eklenti yapılandırmasını yeniden yükleme yetkisi | `op` |
| `companionpets.admin.health` | Eklenti sağlık raporunu görüntüleme yetkisi | `op` |
| `companionpets.admin.backup` | Manuel veritabanı yedeği alma yetkisi | `op` |
| `companionpets.admin.reconcile` | Veritabanı ve dünyadaki pet durumlarını uzlaştırma yetkisi | `op` |
| `companionpets.admin.pack` | Pet Pack listeleme, kurma, kaldırma ve dışa aktarma yetkisi | `op` |
| `companionpets.admin.marketplace` | Marketplace katalog yenileme, listeleme ve paket kurma yetkisi | `op` |

## Pet Bazlı Yetkiler

Bir pet tanımı (`pets/<id>.yml`) `permission:` alanı taşıyorsa, o pet **yalnızca** bu yetkiye
sahip oyuncular tarafından çağrılabilir. Alan yoksa pet herkese açıktır. Kontrol çağırma
anında yapılır; yeniden bağlanmada otomatik geri yükleme de aynı kontrolden geçer.

Paketlenmiş petlerin yetkileri `plugin.yml` içinde `default: true` olarak tanımlıdır — yani
kurulumdan sonra davranış değişmez. Bir peti kısıtlamak için ilgili düğümü izin
eklentinizde `false` yapmanız yeterlidir.

| Yetki (Permission) | Pet | Varsayılan |
| :--- | :--- | :--- |
| `companionpets.pet.wolf` | Kurt Dostu | `true` |
| `companionpets.pet.arcanecrystal` | Arcane Crystal | `true` |
| `companionpets.pet.floatingbook` | Floating Book | `true` |
| `companionpets.pet.shoulderorb` | Shoulder Orb | `true` |
| `companionpets.pet.ghostscribe` | Ghost Scribe | `true` |
| `companionpets.pet.spiritflame` | Spirit Flame | `true` |
| `companionpets.pet.familiarswarm` | Familiar Swarm | `true` |
| `companionpets.pet.voidcube` | Void Cube | `true` |
| `companionpets.pet.sleepycat` | Uyuyan Kedi | `true` |
| `companionpets.pet.wisplight` | Wisplight | `true` |
| `companionpets.pet.shadowwisp` | Gölge Zerresi | `true` |
| `companionpets.pet.mirrordoll` | Yansıma Bebek | `true` |
| `companionpets.pet.echophantom` | Yankı Hayaleti | `true` |
| `companionpets.pet.roamfox` | Gezgin Tilki | `true` |

Kendi pet tanımınıza yeni bir `permission:` düğümü eklerseniz, düğümü izin eklentinizde
tanımlamayı unutmayın: Bukkit, kayıtlı olmayan düğümleri op olmayan oyunculara kapalı sayar.
