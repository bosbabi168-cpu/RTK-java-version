# Glosarium terjemahan Indonesia

Sumber kebenaran tunggal untuk penerjemahan skrip Lua. **Selalu baca berkas ini
sebelum menerjemahkan berkas baru**, dan tambahkan entri baru ke sini alih-alih
memutuskan sendiri di tempat.

Alasannya bukan kerapian: kata kunci `speech` adalah **yang diketik pemain**.
Kalau `rabbit` jadi `kelinci` di satu berkas dan `terwelu` di berkas lain, NPC
yang kedua tidak akan pernah bisa diajak bicara — dan gagalnya **senyap**, tidak
ada error, NPC-nya hanya diam.

Keputusan gaya (ditetapkan user 26 Agustus 2026):
- **Gaya campuran menurut karakter** — tetua/bangsawan pakai `Anda`, pedagang/
  anak-anak/mob pakai `kamu`/`kau`.
- **Nama diri dipertahankan** — `Mythic Nexus`, `Kugnae`, `Ju Jak` tetap.
- **Kata benda umum diterjemahkan** — `Forest` → `Hutan`, `Town` → `Kota`.
- **Nama guild diterjemahkan** — `Warrior's Guild` → `Guild Prajurit`,
  `Mage's Guild` → `Guild Penyihir`.

---

## JANGAN DITERJEMAHKAN

### 1. Perintah GM (38 buah, semua diawali `/`)

`/act` `/age` `/cfloor` `/cmapfloor` `/cspells` `/dps` `/freeasync` `/gfx`
`/gfxtoggle` `/gmc` `/gmclick` `/gmspell` `/imstuck` `/items` `/kill`
`/kill mob` `/kill pc` `/make char` `/make login` `/make map` `/map` `/mapfile`
`/mapsweep` `/metan` `/mobs` `/nmap` `/online` `/ping` `/played` `/pmap`
`/reload` `/save` `/sweep` `/testlua` `/tl` `/totalitems` `/totalmobs` `/trade`
`/ww`

Ini perintah, bukan percakapan. Menerjemahkannya memutus perkakas GM.

### 2. Kode alat debug grafis (±80 buah)

⚠️ **Aturannya PER-BERKAS, bukan per-kata.** Ditemukan saat penerapan: kata
yang sama berarti hal berbeda tergantung berkasnya.

- `pass` di `Accepted/speech.lua:1418` = petak bisa dilewati (`getPass`), tapi
  di `Accepted/NPCs/kaming/sya.lua:87` = izin lewat untuk quest.
- `throw`, `spell`, `sound`, `skin`, `side`, `tile`, `obj`, `dye` **hanya**
  muncul di `speech.lua` sebagai perkakas grafis — bukan kosakata pemain.

Karena itu berkas berikut **dikecualikan seluruhnya** dari penerjemahan kata
kunci: `Accepted/speech.lua`, apa pun di bawah `Tools/`, `God_Tools*`,
`gm_click*`.

Bahaya tambahan: `speech.lua:1357` memakai `string.match(lspeech, "throw (%d+)")`
— kata kuncinya tertanam di dalam **pola**, jadi menerjemahkan sisi `==` saja
akan membuat keduanya desinkron tanpa error.


Pola: awalan `p`/`n`/`c` + nama bagian, mis. `ptile` `ntile` `tile`, `pweapc`
`nweapc` `weapc`, `pmantlec` `nmantlec`, `phelmc` `nhelmc`, `pfaceac` `nfaceac`,
`parmorc` `narmorc`, `pbootsc` `nbootsc`, `pcrownc` `ncrownc`, `pshieldc`
`nshieldc`, `psdye` `nsdye`, `pdye` `ndye`, `picon` `nicon`, `piconc` `niconc`,
`pdis` `ndis`, `pdisc` `ndisc`, `pobj` `nobj` `cobj`, `pskin` `nskin`, `pside`
`nside`, `psound` `nsound`, `pspell` `nspell`, `pthrow` `nthrow`, `pact`,
`phairc` `nhairc`, `pfacec` `nfacec`, `packetn` `packetp` `packetsubn`
`packetsubp`, `svn up`, `reload`.

Perkakas pengembang untuk menguji sprite. Tidak pernah dilihat pemain biasa.

### 3. Nama diri (tokoh, tempat, makhluk mitologis)

`baekho` · `chongun` · `chung ryong` · `dae-whan` · `geomancer` · `hyun moo` ·
`ironheart` · `jadespear` · `ju jak` · `kawlana` · `kimesh` · `laptev` ·
`majhum` · `nagnag` · `sute` · `udis` · `kugnae` · `koguryo` · `clan`

⚠️ `chung ryong's might` → `chung ryong's might` (nama diri + posesif Inggris).
Butuh keputusan terpisah kalau mau jadi `kekuatan chung ryong`.

### 4. Bunyi & celetuk

`woof!` · `grrowl!` · `bark!` · `meow` · `lol` · `humm dee do dum do hee`

Dipertahankan; onomatope lintas bahasa dan sebagian lelucon.

---

## DITERJEMAHKAN — kosakata pemain

Kolom kiri = yang tertulis di skrip sekarang. Kolom kanan = yang akan diketik
pemain.

| Inggris | Indonesia | Catatan |
|---|---|---|
| alchemy | alkimia | |
| book | buku | |
| bridge | jembatan | |
| calamity | malapetaka | 12x — kata kunci quest utama |
| capture the winds | tangkap angin | |
| cleanse | sucikan | |
| cleanse curse | sucikan kutukan | |
| coal | batu bara | |
| combine | gabung | |
| compass | kompas | |
| complete | selesai | |
| demon | iblis | |
| demons | iblis | ⚠️ jamak/tunggal jadi sama — aman, keduanya cocok |
| desert | gurun | |
| dragon | naga | |
| draw | gambar | |
| draw map | gambar peta | |
| dusk shaman | dukun senja | |
| earth dragon | naga bumi | |
| elemental orb | bola elemen | |
| finish | selesaikan | ⚠️ beda dari `complete` — jangan disamakan |
| fish | ikan | |
| forge metal | tempa logam | |
| forgive | maafkan | |
| forgiveness | pengampunan | |
| fragment | pecahan | |
| gem | permata | |
| ginseng | ginseng | sama dalam bahasa Indonesia |
| greater | agung | |
| greater alliance | aliansi agung | |
| gruff ring | cincin kasar | |
| hello | halo | |
| hello! | halo! | |
| hi | hai | |
| how old am i | berapa umurku | |
| how old am i? | berapa umurku? | |
| i lost my tiger mail | aku kehilangan surat harimauku | |
| i'd like to fish | aku ingin memancing | ⚠️ ada dua varian di korpus |
| id like to fish | aku ingin memancing | varian tanpa apostrof |
| ice beast | binatang es | |
| jewel | permata | ⚠️ sama dengan `gem` — cek konteks per berkas |
| legend | legenda | |
| lockpick | congkel kunci | |
| map | peta | |
| map fragment | pecahan peta | |
| metal | logam | |
| metal orb | bola logam | |
| might | kekuatan | |
| minor | kecil | |
| minor quest | misi kecil | |
| moon | bulan | |
| pass | lewat | ⚠️ bisa "izin lewat" — cek konteks |
| pick up armor | ambil baju zirah | |
| prepare | siapkan | |
| prepare noodles | siapkan mi | |
| quest | misi | |
| rabbit | kelinci | |
| ring | cincin | |
| scrap | rongsokan | |
| scraps | rongsokan | |
| scribe | juru tulis | |
| seal | segel | |
| secret | rahasia | |
| sewers | selokan | |
| shard | serpihan | |
| shield | perisai | |
| side | pihak | |
| skin | kulit | |
| smelt | lebur | |
| smith armor | tempa zirah | |
| sound | suara | |
| special collections | koleksi khusus | |
| special deal | penawaran khusus | |
| special delivery | kiriman khusus | |
| special guest | tamu khusus | |
| special occasion | acara khusus | |
| special order | pesanan khusus | |
| spell | mantra | |
| spicy chicken wings | sayap ayam pedas | |
| spoon | sendok | |
| stars | bintang | |
| statue | patung | |
| statues | patung | |
| strange metal | logam aneh | |
| sweet summer blossoms | bunga musim panas manis | |
| tailor | penjahit | |
| the calamity | malapetaka itu | |
| throw | lempar | |
| tiger | harimau | |
| transport | angkut | |
| twine | benang | |
| virtue | kebajikan | |
| water skin | kantong air | |
| waypoint | titik jalan | 13x — terbanyak; ada di tabel `keywords` |
| weave | tenun | |
| weave wind | tenun angin | |
| wilderness life | kehidupan liar | |
| wood | kayu | |

---

## Istilah umum di dialog (bukan kata kunci `speech`)

| Inggris | Indonesia |
|---|---|
| Forest | Hutan |
| Town | Kota |
| Warrior's Guild | Guild Prajurit |
| Mage's Guild | Guild Penyihir |
| Inn | Penginapan |
| Bank | Bank |
| Temple | Kuil |
| Blacksmith | Pandai Besi |

---

## Cara menerapkan

1. Terjemahkan **kedua sisi bersamaan**: literal `speech == "..."` DAN tabel
   Lua yang dibandingkan dengannya (`song = {...}` di `lost_legend_chest.lua`,
   `keywords = {...}` di `Scripts/Waypoint.lua`).
2. Jangan sentuh apa pun yang diawali `/`.
3. Nama barang/mob/NPC di skrip adalah **identifier**, bukan teks tampilan —
   biarkan. Terjemahannya dikerjakan di kolom `ItmDescription`,
   `MobDescription`, `NpcDescription` di database.
4. Setelah tiap kelompok berkas: `./run.sh scripttest` dan `./run.sh luaaudit`
   harus tetap bersih.

## Kemajuan

**26 Agustus 2026 — kata kunci `speech` SELESAI.**
174 penggantian di 62 berkas memakai 89 kata kunci, diterapkan mekanis dari
peta di atas sehingga konsisten menurut konstruksi. Berkas perkakas
dikecualikan. Semua 907 berkas tetap parse bersih; enam gerbang regresi hijau.

Ikut diterjemahkan: 14 kata benda umum di tabel `keywords` pada
`Accepted/Scripts/Waypoint.lua` (`forest`→`hutan`, `north`→`utara`,
`gem`→`permata`, `wood`→`kayu`, `weave`→`tenun`, `scribe`→`juru tulis`, …).
Tabel itu dibandingkan lewat `speech == keywords[j]`, jadi ia bagian dari
kosakata yang diketik pemain. Nama tempat di dalamnya dipertahankan
(`kugnae`, `buya`, `nagnang`, `hamgyong`, `noxhil`, `hausson`, `sanhae`,
`rotah`, `thane`, `yon`, `zephyr`, `sel`, `splinter`, `mythic`, `museum`).

**26 Agustus 2026 — petunjuk ketik yang rusak SUDAH ditutup (7 kalimat).**
Menerjemahkan kata kunci membuat kalimat yang *memberitahu pemain kata itu*
jadi menyesatkan. Pencarian sistematis (kalimat yang mengandung kata perintah
`say`/`tell me`/`ask me`/`type` **dan** kata kunci terterjemah) menemukan 19
kandidat; 12 di antaranya positif palsu (`type` sebagai kata benda:
"What type of clothing"). Tujuh yang sungguhan diperbaiki:

| Berkas | Kata kunci |
|---|---|
| `Quests/MinorQuest.lua:7` | `complete` → `selesai` |
| `NPCs/wilderness/yon.lua:78` | `weave` → `tenun` |
| `NPCs/wilderness/splinter.lua:51` | `wood` → `kayu` |
| `NPCs/wilderness/splinter.lua:52` | `scraps` → `rongsokan` |
| `NPCs/Common/smith.lua:256` | `smelt` → `lebur` |
| `NPCs/Common/smith.lua:285` | `metal` → `logam` |
| `NPCs/Common/smith.lua:291` | `smith armor` → `tempa zirah` |
| `NPCs/Common/seamstress.lua:142` | `tailor` → `penjahit` |
| `NPCs/tutorial/main_tutorial_npc.lua:453` | `i'd like to fish` → `aku ingin memancing` |

⚠️ **Pencarian ini WAJIB diulang setiap kali kata kunci baru diterjemahkan.**
Kalau tidak, questnya jadi tidak bisa diselesaikan pemain — dan gagalnya
senyap: NPC tetap menjawab, hanya tidak pernah pada kata yang dianjurkannya
sendiri.

**28 Agustus 2026 — SELURUH TEKS DIALOG SELESAI (R4).**
**0 dari 9.812 titik dialog** di 665 berkas masih berbahasa Inggris.

Cara mengerjakannya, supaya bisa diulang dan diperiksa:

1. **Katalog, bukan sunting satu-satu.** 3.980 entri di
   `tools/terjemahan/kamus-*.json` memetakan teks Inggris → Indonesia.
   Satu string yang sama diterjemahkan sekali dan berlaku di mana pun ia
   muncul — konsisten menurut konstruksi, bukan menurut ingatan.
2. **Penerapan POSISIONAL, bukan cari-ganti.** `tools/terjemahan/terapkan.py`
   hanya mengganti literal yang berdiri di argumen yang benar-benar tampil
   di layar (aturan per-panggilan di `inventaris.py`). Karena itu
   `characterLog.lua` yang menulis " for " ke berkas log tetap utuh
   sementara kalimat toko diterjemahkan.
3. **Pembanding ikut diterjemahkan.** Nilai balik `menuString` adalah
   STRING OPSINYA, jadi `pilihan == "Yes"` diganti bersama opsinya —
   kalau tidak, menunya berbahasa Indonesia tetapi tidak ada cabang yang
   cocok, dan gagalnya senyap.
4. **Empat penjaga, dijalankan setiap kali menyentuh dialog:**
   `inventaris.py` (berapa titik & berapa yang masih Inggris),
   `separuh.py` (baris yang separuh terjemah — bahaya khas potongan
   sambungan), `petunjuk-ketik.py` (kalimat yang menyuruh mengetik harus
   menyebut kata kunci `speech` yang benar), dan `./run.sh scripttest`.
5. **Yang memutuskan tetap klien sungguhan.** `livetest` kini menuntut
   dialog DAN opsi menu yang sampai ke pemain berbahasa Indonesia; ia
   tiga kali menemukan menu Inggris yang dilaporkan "nol sisa" oleh alat
   statis (Peringatan #114), dan `scripttest` menangkap `next`/`previous`
   yang ternyata nilai protokol (Peringatan #116).

**Yang SENGAJA dibiarkan Inggris** (`tools/terjemahan/dikecualikan.json`):
nama barang (`Scroll of Protection`, `Juk-do`, `Armor of the Winds`), nama
stat (`Will`), dan nilai protokol (`next`, `previous`, `quit`). Nama barang
diterjemahkan di kolom `ItmDescription` seperti aturan di atas — kalau
diterjemahkan di skrip saja, menu dan kantong akan menyebut benda yang
sama dengan dua nama berbeda.

## Berkas yang sudah diterjemahkan penuh (dialog + kata kunci)

- `Accepted/NPCs/tutorial/chu_rua_tiger.lua` (26 Agustus 2026) — contoh pertama.
