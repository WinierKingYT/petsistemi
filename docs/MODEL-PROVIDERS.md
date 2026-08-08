# Model Provider Adaptörleri

PetSistemi harici model eklentilerini doğrudan core sınıflarına bağlamaz. Her sağlayıcı
`PetModelProvider` sözleşmesi üzerinden standart representation ve MF4 animasyon akışına
katılır. Sağlayıcı jar'ları PetSistemi artifact'ına gömülmez.

## Yerleşik sağlayıcılar

| `representation.type` | `model-id` | Runtime | Adlandırılmış animasyon |
|---|---|---|---|
| `modelengine:model` | Model Engine blueprint/model id | Gizli base entity + ActiveModel | Klip, priority, blend-in/out, loop |
| `itemsadder:model` | ItemsAdder custom entity namespaced id | `CustomEntity` | Klip adı |
| `oraxen:model` | Oraxen item id | `ItemDisplay` | Display idle/sleep fallback |

ModelEngine ve ItemsAdder adaptörleri provider kliplerine geçiş yapar. Oraxen item modelleri
iskelet animasyonu sunmadığı için ortak display davranışını kullanır; örneğin SLEEPING
durumunda model ölçeği küçülür.

## YAML örnekleri

```yaml
representation:
  type: modelengine:model
  model-id: phoenix_pet
  entity-type: ARMOR_STAND
  scale: { x: 1.0, y: 1.0, z: 1.0 }

states:
  IDLE:
    clip: phoenix:idle
    blend-in-ticks: 3
  MOVING:
    clip: phoenix:walk
    priority: 10
  ATTACKING:
    clip: phoenix:bite
    priority: 100
    blend-in-ticks: 2
    blend-out-ticks: 3
    loop: false
```

```yaml
representation:
  type: itemsadder:model
  model-id: petpack:forest_fox
```

```yaml
representation:
  type: oraxen:model
  model-id: floating_book
```

Klip namespaced key'inin `key` bölümü (`phoenix:bite` için `bite`) sağlayıcının animasyon
API'sine gönderilir. Namespace, farklı paketlerin YAML kimliklerini çakıştırmaması içindir.

## Eksik sağlayıcı davranışı

`ModelEngine`, `ItemsAdder` veya `Oraxen` kurulu değilse PetSistemi normal şekilde açılır ve
ilgili adaptör kaydedilmez. Bu provider'a bağlı bir pet çağrılırsa vanilla entity'ye sessizce
düşmek yerine `Representation provider kayıtlı değil` hatası üretilir. Böylece eksik kurulum
yanlış görselle maskelenmez.

## Üçüncü taraf sağlayıcı

Başka bir plugin `PetModelProvider` uygulayıp Bukkit `ServicesManager` üzerinden
`ModelProviderService` alarak kaydedebilir:

```java
ModelProviderService service = Bukkit.getServicesManager().load(ModelProviderService.class);
if (service != null) {
    service.register(new MyModelProvider());
}
```

Provider `key()` değeri, pet YAML'ındaki `representation.type` ile aynı olmalıdır.

## Sağlayıcı API kaynakları

- Model Engine: https://git.mythiccraft.io/mythiccraft/model-engine-4/-/wikis/API/Basic/Add-Remove-Model
- ItemsAdder: https://itemsadder.devs.beer/developers/java-api/examples
- Oraxen: https://docs.oraxen.com/developers/api
