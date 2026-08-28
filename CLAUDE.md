# CLAUDE.md — RTK-java-version

Panduan sesi pengembangan. Baca file ini SEBELUM mengubah kode; README.md
untuk gambaran lengkap; **`docs/PERINGATAN.md` untuk 102 jebakan yang sudah
pernah memakan waktu** — wajib dibuka setiap kali menyentuh area yang
disebut di sana.

Ditulis ulang 28 Agustus 2026 dari audit menyeluruh (bukan salinan catatan
lama). Versi lama tersimpan di `../_backup_docs_2026-08-28/`.

## Apa project ini

Port Java SE dari **RTK-Server** (`../RTK-Server`, C: login/char/map server
+ MySQL + 907 skrip Lua). Konten Lua TIDAK dikonversi — dijalankan apa
adanya lewat LuaJ.

**ARAH FINAL (26 Agustus 2026):** protokol RetroTK **diganti** dengan
rancangan sendiri (**RTK2**, `docs/PROTOKOL-RTK2.md`) dan klien dibuat
sendiri memakai libGDX di repo terpisah **`../RTK-client`**. Kesetiaan pada
C dipertahankan untuk **logika permainan** (aset paling berharga: 907 skrip
Lua, 9.850 peta, 4.476 portal, 716 mob, 2.545 item), bukan untuk format
kabel. Paket `clif_*` RetroTK masih ada berdampingan (`ProtocolRouter`)
tapi prioritasnya rendah dan `Clif.sendMyStatus()` sengaja dibiarkan
TAHAP 1 — jangan habiskan waktu melengkapinya.

## Aturan arsitektur yang TIDAK boleh dilanggar

1. **Batas logika ↔ protokol.** Logika permainan hanya bicara lewat
   **`ClientView`** (peristiwa keluar) dan **`ClientCommands`** (aksi masuk,
   diimplementasikan `MapCommands`). Kode di luar lapisan protokol **JANGAN
   memanggil `Clif.*` langsung**. Butuh paket baru → tambah peristiwa di
   `ClientView`, implementasikan di adapter (`RetroTkClientView` /
   `Rtk2ClientView`). Nama method menyebut *apa yang terjadi*, bukan paket
   apa yang dikirim. (Kebocoran yang diketahui: parameter viewport di
   `npcMoved()` — sudah ditandai di Javadoc.)
2. **`Wire.java` ada DUA (di sini dan di `../RTK-client`) dan keduanya
   identik byte-per-byte kecuali `package`.** Setiap perubahan menaikkan
   `Wire.VERSION` di KEDUA sisi; salin utuh, jangan sunting sebelah.
   `./run.sh wiresync` menjaga ini — jalankan setiap kali menyentuh Wire.
3. **Endianness:** `rfifoW/wfifoW/...L` = little-endian (protokol
   antar-server 0x1000/0x2000/0x3000/0x3800); varian `BE` = big-endian
   (protokol klien RetroTK). RTK2 punya aturan sendiri di `Wire.java`.
4. **`nbproject/` dan `build.xml` milik NetBeans** (J2SE with Ant, TANPA
   Maven/Gradle) — jangan diutak-atik; `build.xml` dibiarkan polos.
   Library hanya dari `extLib/*.jar`; jar baru → daftarkan di
   `extLib/README.md` + `nbproject/project.properties` (dua entri).
5. **Java 25** (`javac.source/target` di `nbproject/project.properties` dan
   `RELEASE` di `build.sh` harus sinkron). Main-Class = `org.rtk.RtkLauncher`
   — server/entry point baru wajib didaftarkan di sana.
6. **Diagnostik IDE VSCode = noise** (workspace tidak kenal `src/` +
   `extLib/`). Patokan kebenaran: `javac` di terminal.
7. **Referensi kebenaran semantik = sumber C** (`../RTK-Server/rtk/src/`),
   terutama `map/sl.c` untuk binding skrip. Bug C yang sengaja
   dipertahankan diberi komentar "Faithful port note" — jangan "diperbaiki".
8. `crypt.enckey` dan `crypt.handshake_key` adalah bagian protokol klien
   RetroTK — jangan ubah.

## Lokasi & layout

```
GitHub/
├── RTK-Server/          # sumber C asli (rujukan; TIDAK dibaca saat runtime)
├── RTK-java-version/    # SERVER — repo ini, isi langsung di root
└── RTK-client/          # KLIEN libGDX — repo terpisah, belum di-commit
                         #   sampai seluruh aset diganti buatan sendiri
```

Project mandiri — data game ada di dalamnya: `maps/` (3.544 `.map`),
`luascript/` (907 `.lua`), `database/` (migrasi + dump MySQL, 54 tabel),
`meta/`, `db/`. Rantai konfigurasi: `resources/rtk-server.properties` →
`conf/*.conf` (menimpa) → argumen CLI. Jangan menanam path di kode.

## Build & uji — SEMBILAN gerbang

Build: NetBeans (*Clean and Build*) → `dist/RTK-java-version.jar`, atau
`./build.sh` → `dist/RTK-java.jar`. Menjalankan:
`./run.sh {login|char|map|all|status|stop}`.

Delapan gerbang luring, semua wajib hijau sebelum pekerjaan dianggap
selesai:

```
./build.sh
./run.sh scripttest   # 906 skrip Lua + coroutine dialog
./run.sh maptest      # 3.544 berkas peta
./run.sh chartest     # serialisasi karakter
./run.sh worldtest    # dunia peta + penempatan pemain
./run.sh cliftest     # paket, protokol RTK2, subsistem — 824 assertion
./run.sh dbtest       # lapisan database — 234 assertion; butuh MySQL
./run.sh luaaudit     # pemeriksa statis 907 skrip Lua
./run.sh wiresync     # Wire.java sinkron dengan repo klien
```

**Gerbang KESEMBILAN — yang paling banyak menemukan bug.** Gerbang luring
menguji kode terhadap dirinya sendiri dan **tidak bisa melihat "sesuatu
yang tidak terjadi"** (Peringatan #88–#101 hampir semua lolos dari gerbang
luring):

```
./run.sh all
(cd ../RTK-client && ./run.sh livetest 127.0.0.1 2001 Adrielle)   # 89 pemeriksaan
```

⚠️ **Aturan uji untuk setiap subsistem baru:** pemeriksaan di `cliftest`
DAN `livetest`, lalu **rusak kodenya sengaja** untuk membuktikan gerbangnya
bisa merah (kontrol negatif — dua kali menemukan lubang di ujinya sendiri).
⚠️ Jangan jalankan gerbang saat server hidup — keduanya menulis ke
`logs/map.log` yang sama dan hitungan ERROR jadi tercemar.
⚠️ `livetest` memakan barang karakter uji (Peringatan #102) — periksa
kantong `Adrielle` bila langkah masuk dunia mendadak merah.

Tanpa MySQL: login/char server exit saat start (sama dengan C); map server
dan gerbang selain `dbtest` tetap jalan. Setup MySQL lokal (jebakan
`auth_socket` di Ubuntu — pakai `sudo mysql`, error-nya 1698 bukan 1045):

```
sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  CREATE USER IF NOT EXISTS 'rtk'@'%' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"
mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
```

(Dump diawali `DROP DATABASE IF EXISTS RTK` — periksa isi database dulu.)

## Scripting engine & luaaudit

- `ScriptEngine` = port `sl.c`: muat `Developers/sys.lua` → seluruh
  `Accepted/` + `Developers/`. Status yang dijaga: **906/906 termuat,
  0 error**.
- Object model `typel`: `__index` = getter Java → prototype → data table.
  Prototype `Player` diperluas `Accepted/player.lua`. **Implement binding
  di lapisan primitif** (`menu`, `dialog`, `input` = yield coroutine),
  bukan di level tinggi.
- `luaaudit`: **baca bagian "GLOBAL masih STUB" dulu** — stub terdefinisi
  tapi tidak berbuat apa-apa dan tidak masuk hitungan "belum diport".
  Angka "belum diport" adalah **batas bawah** (nama yang kebetulan sama
  dengan kunci tabel di korpus jadi tak terlihat — kasus `addLegend`).
  Silangkan ke `sl.c`. Daftar penuh: `-Drtk.audit.penuh=true`.
- Perubahan konten Lua dicatat di `luascript/PERUBAHAN.md` — wajib dibaca
  sebelum menyegarkan konten dari upstream.

## Terjemahan Indonesia

Sumber kebenaran gaya & istilah: **`luascript/GLOSARIUM.md`**.

- **Nama barang/mob/NPC di skrip = IDENTIFIER, bukan teks tampilan** —
  menerjemahkannya mematahkan `addItem` dkk. secara senyap. Terjemahan nama
  dikerjakan di kolom `*Description` database (`database/terjemahan/`).
- **Kata kunci `speech` = yang diketik pemain** — sudah diterjemahkan
  (aturannya per-berkas; `speech.lua`, `Tools/`, `God_Tools*`, `gm_click*`
  dikecualikan). Setiap kata kunci baru diterjemahkan → **cari ulang kalimat
  yang menyuruh pemain mengetiknya**, atau quest putus secara senyap.
- `conf/lang.conf` (pesan penolakan) bisa diterjemahkan tanpa sentuh kode.

## Peta arsitektur (C → Java) — ringkas

| C | Java |
|---|---|
| `common/socket.c`, `core.c`, `timer.c`, `crypt.c`, `db_mysql.c` | `common/NetServer`+`Session`, `Core`, `TimerSystem`, `Crypt`, `Sql` (instance per server; IO thread → ArrayBlockingQueue → logic thread) |
| `login/*`, `char/*` | `org.rtk.login`, `org.rtk.charserver` (port penuh, termasuk blob 0x3003/0x3004) |
| `map/clif.c` keluar / `clif_parse()` masuk | `map/Clif` (RetroTK) · `map/proto/Wire`+`Inbound`+`Rtk2ClientView` (RTK2) · `ProtocolRouter` (berdampingan) |
| penangan aksi pemain | `ClientCommands` (antarmuka) + `MapCommands` (logika, bebas `Clif`/`Session`) |
| `map/sl.c` | `map.script`: `Bindings`, `WorldBindings`, `ScriptEngine`, `LuaAudit` |
| subsistem | `Durations` (durasi/aether), `FloorItem*` (BL_ITEM), `Items` (pakai/kenakan/lempar), `Exchange`, `Groups`, `Boards`, `Parcels`, `Combat`, `Mob`/`MobData` (AI), `Npc`, `Pc`/`User` |
| data | `map/data/`: `MapData`+`MapRegistry`+`MapFile`, `ItemDb`, `SpellDb`, `ClassDb`, `ClanDb`, `BoardDb`, `MapMsg`, `Equip` |

## STATUS — 28 Agustus 2026 (diaudit ulang hari ini)

| | |
|---|---|
| Gerbang luring | **8/8 hijau** (`cliftest` 824, `dbtest` 234) |
| Gerbang klien sungguhan | `livetest` 89 pemeriksaan hijau |
| Protokol RTK2 | **29 opcode masuk, 48 peristiwa keluar** |
| Binding skrip | method sisa **1** (`testPacket`, sengaja); global **0** celah; 4 nama Kan + 8 nama salah-ketik = kode mati konten |
| Skrip Lua | 906/906 termuat, 0 error; `map.log` server hidup 0 ERROR/WARN |
| Peringatan tercatat | #1–#102 → `docs/PERINGATAN.md` |
| Penghambat utama | **antarmuka klien** (`../RTK-client`) — server mengirim lebih banyak daripada yang bisa digambar |

## ROADMAP — menuju server yang dipakai normal & lancar tanpa bug

Goal: pemain masuk lewat klien RTK2, semua aksi pemain punya jalur, tidak
ada jalur yang gagal senyap. Urutan = prioritas. Angka opcode C hanya
rujukan perilaku — format kabelnya RTK2, bukan port byte.

**R1. Aksi pemain yang BELUM punya jalur masuk RTK2** (hasil audit
`clif_parse()` C 54 opcode vs `Wire.java` 29 — sisanya sudah ada:
jalan, klik, ambil/jatuhkan/lempar, pakai/kenakan, makan/pakai barang,
bicara/bisik, abaikan, grup, setelan, tunggangan, pertukaran, serang,
rapal mantra, jawab menu/dialog):

| Aksi | Rujukan C | Catatan |
|---|---|---|
| Status diri lengkap + status grup | `0x2D` `clif_mystaytus`/`clif_groupstatus` | jendela profil sendiri; pengganti `sendMyStatus` TAHAP 1 |
| Melihat pemain/objek lain | `0x09`/`0x0A` `clif_parselookat*`, `0x73` sub-4 userlook | profil + perlengkapan orang lain |
| Berputar di tempat | `0x11` `clif_parseside` | |
| Emosi | `0x1D` `clif_parseemotion` | |
| Susun ulang mantra / geser barang | `0x30` `clif_parsechangespell`/`changepos` | |
| Minta refresh | `0x38` `clif_refresh` | keluarnya (`EV_SELF_REFRESH`) sudah ada |
| Papan pesan & pos — arah masuk | `0x3B`, `0x34`, `0x4C` powerboards, `0x73` sub-0 | sisi tampilan (`EV_BOARD_*`) sudah ada |
| Kiriman/parcel — arah masuk | `0x41` `clif_parseparcel` | logika `Parcels` sudah ada |
| Simpan paperpopup | `0x23` | menulis di kertas |
| Daftar kota | `0x66` `clif_sendtowns` | |
| Minimap | `0x7C` `clif_sendminimap` | |
| Ranking + hadiah | `0x7D` | |
| Daftar teman & hunter | `0x77`, `0x84` | |
| Ubah profil | `0x4F` `clif_changeprofile` | |
| Ganti tampilan | `0x82` `clif_parseviewchange` | |
| Sistem kreasi | `0x6B` `createdb_start` | niche — putuskan perlu/tidak |

**R2. TODO di kode** (semua sudah ditandai di sumbernya):
`MapCommands:243` mob mati tidak menghalangi langkah · `MapCommands:395`
klik mob → `onLook` + kait `click` · `ScriptEngine:480` musim dari
kalender dunia · `ScriptEngine:489,591` `curServer`/`checkOnline`
mengembalikan nilai tetap.

**R3. Sisa Trek C:** C3 warp antar map server (masih ditolak dengan pesan
jelas; butuh dua map server + klien) · C2 empat berkas meta RetroTK
(nilai menurun — klien sendiri tidak membacanya; kandidat di
`RTK-Server/rtk/decrypted/`).

**R4. Terjemahan dialog** — ~3.800 titik dialog NPC (903 di 56 berkas
prioritas); ikuti aturan di atas + GLOSARIUM.

**R5. Stabilisasi berkelanjutan:** setiap subsistem R1 dapat pemeriksaan
`cliftest` + `livetest` + kontrol negatif; skenario multi-pemain diperluas;
`map.log` dijaga 0 ERROR/WARN dengan pemain sungguhan online.

**Sengaja TIDAK dikerjakan:** `testPacket` (debug GM tulis byte mentah) ·
melengkapi `sendMyStatus` RetroTK · 8 nama binding yang tidak ada di
`sl.c` maupun konten (salah ketik/kode mati) · 4 nama Kan (tidak terdaftar
di `sl.c`).

## Kebiasaan project

- Bahasa komunikasi & dokumentasi: **Bahasa Indonesia** (istilah teknis
  tetap Inggris).
- **README dua bahasa yang bercermin:** `README.md` (ID, utama) dan
  `README.en.md` (EN) — struktur bagiannya sama persis; ubah satu →
  perbarui yang lain **di sesi yang sama**.
- Selesai mengubah kode: compile bersih, jalankan gerbang yang relevan
  (minimal `scripttest`), jangan commit `build/`/`dist/`/`logs/`.
- Peringatan baru → tambahkan di ekor `docs/PERINGATAN.md` dengan nomor
  lanjutan (#103, …), dan rujuk nomornya dari tempat lain.
- `ServerLog.addLog/logAdd` = port log game-event C; diagnostik pakai
  Log4j2 — jangan tambah `System.out.println`.
