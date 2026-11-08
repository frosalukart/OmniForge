# OmniForge 1.0.0 — satu plugin untuk semua item RPG

Menggabungkan konsep **ItemForge + LegendaryForge** dan menjembatani **MMOItems, ExecutableItems,
AdvancedEnchantments, AuraSkills, RaceCore, ModelEngine**. Target: Paper 26.2 (kompatibel 26.3), Java 25.

## Cara build
Butuh JDK 25 + Maven:   `mvn package`   -> `target/OmniForge-1.0.0.jar`
Tanpa JDK? Upload folder ini ke GitHub, tab **Actions** -> "Build OmniForge" -> download artifact jar-nya.

## Pasang
1. Taruh jar di `plugins/`. ItemForge & LegendaryForge **sebaiknya dilepas** (fungsinya sudah ada di sini).
2. Start server, cek log: `Hook MMOItems: OK`, `Hook AuraSkills: OK`, dst.
3. Ketik `/of` -> GUI.

## Perintah (`/of`, izin `omniforge.admin`)
| Perintah | Fungsi |
|---|---|
| `/of` | buka GUI |
| `/of give <player> <ref> [n]` | ref = `id` / `mmoitems:SWORD.KATANA` / `ei:id` |
| `/of link <id>` | pegang item MMOItems/EI -> bikin profil & ikat ke item itu |
| `/of info` | lihat identitas item di tangan (ref, profil, race) |
| `/of list`, `/of reload` | daftar item / reload |

## Konsep penggabungan
* **Item native** (`items/*.yml`): format superset ItemForge (item ItemForge lama bisa di-copy langsung).
* **`base:`** bikin item dari MMOItems / ExecutableItems, lalu menambah texture, enchant AE, ability, syarat race, dsb.
* **`bind:`** item MMOItems/EI yang sudah ada (dari crate, shop, dll) otomatis mendapat ability profil. Cara tercepat: pegang item -> `/of link namaprofil`.

## Action ability
`SOUND:key:vol:pitch` `PARTICLE:nama:jumlah:sebaran` `MESSAGE:teks` `ACTIONBAR:teks` `COMMAND:cmd` `PLAYER_COMMAND:cmd`
`POTION:efek:detik:level` `POTION_TARGET` `POTION_NEARBY:efek:detik:level:radius` `HEAL:n` `FEED:n`
`DAMAGE_NEARBY:radius:damage` `DAMAGE_TARGET:n` `IGNITE_NEARBY:radius:tick` `IGNITE_TARGET:tick`
`KNOCKBACK_NEARBY:radius:power` `LIGHTNING_NEARBY:radius` `LIGHTNING_TARGET` `DASH:power` `LAUNCH:power`
`MODEL:id:tick` `MODEL_TARGET:id:tick` (ModelEngine) `MANA:n` `AURA_XP:skill:n` (AuraSkills) `GIVE:ref:n`
Placeholder: `%player%`, `%target%`.

## Texture
Sama seperti ItemForge: `item-model: "ns:nama"` (resource pack 1.21.2+) dan/atau `custom-model-data: 1001`.
Dua-duanya diterapkan lewat API komponen item modern (tidak memakai API lama yang bisa dihapus).

## Catatan jujur
* Source ini **belum diuji di server asli** (lingkungan pembuatnya tidak punya Paper API/JDK 25). Semua integrasi
  memakai reflection dan tiap hook mencetak statusnya di log; kalau ada yang `TERPASANG tapi API tidak cocok`, kirim log-nya.
* Ability hard-coded LegendaryForge (mis. `JUDGMENT_RAY`) tidak ikut dipindah; buat ulang lewat `actions`.
* Item lama buatan ItemForge/LegendaryForge (tag PDC berbeda) tidak dikenali; berikan ulang dengan `/of give`.
