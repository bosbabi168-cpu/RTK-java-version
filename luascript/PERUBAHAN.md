# Perubahan pada skrip Lua

Folder `luascript/` aslinya salinan **byte-identik** dari `rtklua/` milik
RTK-Server. Berkas ini mencatat setiap penyimpangan dari salinan itu, supaya
bisa diterapkan ulang bila konten disegarkan dari upstream.

Semua perubahan di bawah ditemukan oleh `./run.sh luaaudit` pada
**21 Agustus 2026** dan diverifikasi satu per satu terhadap sumber C
(`RTK-Server/rtk/src/map/sl.c`) sebelum diubah.

Yang diperbaiki hanya kesalahan yang **pasti** — masing-masing dijamin
melempar error saat baris itu dijalankan. Kasus yang butuh keputusan desain
sengaja **tidak** disentuh; lihat bagian "Dilaporkan, belum diperbaiki".

---

## 1. `taget` → `target` (8 tempat, 2 berkas)

- `Accepted/Spells/mage/cure_paralysis.lua` baris 20, 50, 80, 110
- `Accepted/Spells/poet/cure_paralysis.lua` baris 20, 50, 80, 109

```lua
-- sebelum
taget:sendAction(6, 35)
-- sesudah
target:sendAction(6, 35)
```

`taget` adalah global yang tidak pernah ada, jadi `taget:sendAction(...)`
memanggil method pada nil. Baris di sekitarnya memakai `target:` dengan
benar. Karena ini pernyataan terakhir dalam efek mantra, efek utamanya
sudah terlanjur jalan — yang hilang animasi aksi si pemantra, plus error di
log setiap kali mantra dipakai.

## 2. `Npc(...)` → `NPC(...)` (3 tempat)

`Accepted/Tools/God_Tools.lua` baris 414–416. Konstruktor yang didaftarkan
mesin skrip bernama `NPC`; Lua membedakan huruf besar-kecil. String literal
`"Npc"` di baris 400 dan 411 **tidak** diubah — itu memang teks pilihan menu.

## 3. `fronttargets[1]` → `fronttarget[1]` (1 tempat)

`Accepted/Instances/instance_boss.lua` baris 685. Variabel yang
diisi dua baris sebelumnya bernama `fronttarget`, dan seluruh baris lain di
blok yang sama memakai bentuk tunggal.

## 4. `might.might` → `block.might` (1 tempat)

`Accepted/Spells/Subpaths/Chongun/inspire_valor.lua` baris 40, di `recast`.
Bandingkan dengan `uncast` tepat di bawahnya yang memakai
`block.might = block.might - 3`.

> Catatan: `"might"` memang muncul di `sl.c`, tapi sebagai **nama properti
> objek** (`block.might`), bukan sebagai global. Karena itu audit sempat
> salah menganggapnya sah — lihat catatan keterbatasan di CLAUDE.md.

## 5. `:SendStatus()` → `:sendStatus()` (13 tempat, 6 berkas)

Nama binding di `sl.c` adalah `sendStatus` (huruf kecil di depan), dan
1.102 pemakaian lain di korpus sudah benar. Ke-13 ini salah ketik.

## 6. `:getObjectInCell(` → `:getObjectsInCell(` (1 tempat)

`Accepted/NPCs/subpaths/ranger/rabbit_invasion.lua` baris 528. Binding di
`sl.c` bernama `getObjectsInCell` (pakai "s").

## 7. `after_death` ganda di `mythic_rabbit.lua` (6 tabel)

Keenam tabel bos kelinci (`mythic_hare`, `hare_witch`, `divine_rabbit`,
`rabbit_witch`, `spirit_rabbit`, `rabbit_avenger`) mendefinisikan
`after_death` **dua kali** dalam tabel yang sama. Di Lua, definisi kedua
menimpa yang pertama tanpa peringatan.

```lua
-- yang pertama (mati, tidak pernah jalan)
after_death = function(mob)
    setMapRegistry(mob.m, "lastDeath", os.time())
end,
-- yang kedua (menimpa)
after_death = function(mob)
    mob_ai_mythic.after_death(mob)
end
```

Akibatnya `lastDeath` **tidak pernah tercatat**, padahal
`Accepted/NPCs/trap/mob_spawn.lua` baris 158 dan 182 membacanya untuk
menerapkan jeda 1.500 detik (25 menit) antar-kemunculan bos kelinci:

```lua
local rabtimer = getMapRegistry(npc.m, "lastDeath")
if chance == 1 and (os.time() >= (rabtimer + 1500)) then
```

Karena `rabtimer` selalu bernilai awal, syarat waktunya selalu terpenuhi —
jeda kemunculan efektif mati, dan hanya peluang acak 1-dari-10 yang
membatasi. Perbaikannya menggabungkan keduanya ke dalam satu handler:

```lua
after_death = function(mob)
    setMapRegistry(mob.m, "lastDeath", os.time())
    mob_ai_mythic.after_death(mob)
end
```

Berkas `mythic_*` lain tidak terdampak (masing-masing hanya punya satu
`after_death` per tabel).

---

## Dilaporkan, belum diperbaiki

Butuh keputusan desain atau implementasi fitur, jadi **sengaja dibiarkan**
agar tidak menebak perilaku yang dimaksud.

### `itemstable` di luar cakupan — `Accepted/Crafting/tailoring.lua`

`local itemstable` dideklarasikan di baris 291, tetapi dipakai di baris
352, 356, 357, 368 yang berada di cabang `else` **di luar** jangkauan
deklarasi itu. Di sana `itemstable` terbaca sebagai global nil, sehingga
`itemstable[rand]` melempar error.

Tidak diperbaiki karena menaikkan deklarasinya saja tidak cukup: isi tabel
dibangun dari `chosenItem` dan `quality` yang juga lokal di cabang
sebelahnya. Perlu diputuskan dulu perilaku menjahit yang dimaksud.

### `mob_ai_hard` dan `mob_ai_boss` tidak pernah didefinisikan

`Accepted/Mobs/mob.lua` memanggil keduanya di 6 tempat (dispatcher AI untuk
`aiType == 2` dan `aiType == 3`), padahal tabel yang ada hanya
`mob_ai_basic`, `mob_ai_normal`, `mob_ai_ghost`, `mob_ai_cotw`, dan
`mob_ai_mythic`.

**Saat ini tidak berdampak:** seluruh 716 baris tabel `Mobs` di database
hanya memakai `MobAI` 0 (407 mob) dan 4 (309 mob) — tidak ada satu pun yang
bernilai 2 atau 3. Jadi ini cabang mati. Perlu diperiksa lagi saat
mengerjakan A5 (mob & pertarungan), terutama bila nanti ada mob baru
memakai aiType 2/3.

### Method yang tidak ada di mana pun (6 nama)

Dipanggil pada objek pemain tetapi tidak terdaftar di `sl.c` **maupun**
didefinisikan di Lua, jadi bukan sekadar "binding belum diport" — memang
tidak pernah ada, termasuk di repo asli:

| Method | Tempat |
|---|---|
| `addGMSpells` | `Accepted/speech.lua:2147` (perintah GM `/gmspell`) |
| `bowShoot` | `Accepted/Items/Weapons/Bows/elixir_bow.lua:26` |
| `buyCustom` | `Accepted/NPCs/Common/auctioneer.lua:459` |
| `hairFaceMenu` | `Accepted/player.lua:454` |
| `returnInn` | `Accepted/Scripts/eventScripts/minigames.lua:438` |
| `totemName` | `Accepted/Tools/Gm_Click/click/gm_click.lua:218` |

### Global yang tidak ada di mana pun

Sisa nama pada daftar "AKAN MELEDAK" di keluaran `luaaudit` — sebagian
besar tabel perkakas GM (`common_tools`, `private_tools`, `user_pages`,
`player_info`, `npc_alter_stats_menu`, `vending_menu`, …) dan tabel event
(`ctf`, `war`, `bomb_game`, `targeta4a`/`b`/`c`). Semuanya sudah tidak ada
di repo asli juga; folder `History/`, `History-archive/`, dan `Research/`
yang tidak ikut disalin sudah diperiksa dan **berisi 0 berkas `.lua`**,
jadi tidak ada konten yang hilang saat pemindahan.

`Developers/sys.lua` mendefinisikan `getDialog()` yang membaca tabel
`dialogs` (sistem banyak bahasa) — tabel itu tidak pernah ada, tetapi
`getDialog` juga tidak pernah dipanggil dari mana pun, jadi kode mati.

---

## Perubahan di sisi mesin (bukan di skrip)

Dicatat di sini karena ditemukan oleh audit yang sama. Ada di
`src/org/rtk/map/script/ScriptEngine.java`:

- **`loadstring`** — dihapus di Lua 5.2, sedangkan LuaJ 3.0.1 mengikuti 5.2
  dan skrip aslinya ditulis untuk 5.1. Dipakai **12 kali**, antara lain
  oleh daftar syarat mantra (`Accepted/Scripts/Spells.lua`) dan **toko NPC**
  (`Accepted/Scripts/verbalScripts/checkShop.lua`). Ditambahkan shim
  `loadstring = loadstring or load`, sepola dengan shim `unpack` yang sudah
  ada.
- **pustaka `debug`** — tidak ikut `JsePlatform.standardGlobals()`, padahal
  `Developers/sys.lua` memakai `debug.traceback()` di `_errhandler`, yang
  berarti penangan error justru ikut gagal. Ditambahkan `DebugLib`.
