# PET-DEFINITIONS.md - Pet Tanımlama Rehberi

Tüm pet tanımları `plugins/PetSistemi/pets/` klasöründe `.yml` dosyaları halinde saklanır.

## Örnek Pet Tanımı (`wolf.yml`)
```yaml
schema-version: 1
display-name: "<gold>Kurt Dostu</gold>"
description:
  - "<gray>Sadık ve cesur bir kurt arkadaş.</gray>"
gui-material: WOLF_SPAWN_EGG
entity-type: WOLF
baby: false
glowing: false
invulnerable: true
silent: false
gravity: true
behavior-profile: DEFAULT
follow-speed: 1.2
follow-distances:
  start: 5.0
  stop: 2.0
  teleport: 15.0
progression:
  enabled: true
  maximum-level: 100
permission: companionpets.pet.wolf
```
