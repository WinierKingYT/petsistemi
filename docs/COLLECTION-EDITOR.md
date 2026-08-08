# Collection GUI ve Oyun İçi Tanım Editörü

MF6, oyuncu koleksiyon görünümünü pet tanım kataloğundan ayırır ve yöneticilerin temel
tanım alanlarını YAML açmadan değiştirmesini sağlar.

## Oyuncu koleksiyonu

`/pet collection` (Türkçe eş adı: `/pet koleksiyon`) sunucudaki tüm pet tanımlarını
gösterir. Sahip olunan tanımlar açık, diğerleri kilitli görünür. Açık bir tanımda:

- sol tık o tanımdaki ilk peti çağırır;
- sağ tık ilk petin inceleme ekranını açar;
- filtre düğmesi tüm tanımlarla yalnızca sahip olunanlar arasında geçiş yapar;
- önceki/sonraki düğmeleri 28 öğelik gerçek sayfalar arasında ilerler.

Eski `/pet` ve `/pet menu` ekranı yalnızca oyuncunun sahip olduğu tekil petleri göstermeye
devam eder. Bu ekranın sayfalaması da MF6 ile düzeltilmiştir.

## Yönetici editörü

Gerekli izin: `companionpets.admin.editor` (varsayılan: OP).

```text
/petadmin editor
/petadmin editor <tanım_id>
```

İlk komut tanım kataloğunu, ikincisi belirtilen tanımın editörünü doğrudan açar. Şu alanlar
chat girdisiyle düzenlenebilir:

- görünen ad (`display-name`)
- GUI materyali (`gui-material`)
- görünüm türü (`representation.type`)
- harici model kimliği (`representation.model-id`)
- entity türü (`entity-type`)
- hareket türü (`movement.type`)

İsteğe bağlı alanlarda `-` yazmak alanı kaldırır. `iptal` veya `cancel` yalnızca bekleyen
alan girdisini iptal eder. Parlama (`glowing` / `representation.glowing`) GUI düğmesiyle
açılıp kapatılır.

## Kayıt güvenliği

Editör doğrudan canlı dosyanın üzerine yazmaz:

1. YAML bellek içi taslakta değiştirilir; editörde gösterilmeyen özel/üçüncü taraf alanlar
   taslakta korunur.
2. Taslak, üretimde kullanılan `PetDefinitionYamlParser` ve `PetDefinitionValidator`
   zincirinden geçirilir.
3. Dosya oturum açıldıktan sonra dışarıdan değişmişse iyimser kilit kaydı reddeder.
4. Geçerli taslak geçici dosyaya yazılıp atomik taşıma ile hedefin yerini alır.
5. `pets/` klasöründeki bütün tanımlar katı modda yeniden okunur. Tek bir hata varsa
   düzenlenen dosya eski içeriğine geri döndürülür ve canlı snapshot değiştirilmez.
6. Başarılı aday snapshot tek adımda yayımlanır.

Aktif olarak çağrılmış bir pet otomatik yeniden doğmaz; görünüm/hareket değişiklikleri bir
sonraki çağırmada uygulanır. Bukkit YAML yazıcısı kayıtta biçimlendirme ve yorum yerleşimini
normalize edebilir. Editör mevcut tanımları düzenler; tanım oluşturma/silme bu fazın kapsamında
değildir.
