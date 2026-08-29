# CLAUDE.md — RTK-java-version

Panduan sesi pengembangan. Baca file ini SEBELUM mengubah kode; README.md
untuk gambaran lengkap; **`docs/PERINGATAN.md` untuk 159 jebakan yang sudah
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

## Build & uji — TIGA BELAS gerbang

Build: NetBeans (*Clean and Build*) → `dist/RTK-java-version.jar`, atau
`./build.sh` → `dist/RTK-java.jar`. Menjalankan:
`./run.sh {login|char|map|all|status|stop}`.

Delapan gerbang luring, semua wajib hijau sebelum pekerjaan dianggap
selesai:

```
./build.sh
./run.sh scripttest   # 906 skrip Lua + coroutine dialog + kalender dunia
./run.sh maptest      # 3.544 berkas peta
./run.sh chartest     # serialisasi karakter
./run.sh worldtest    # dunia peta + penempatan pemain
./run.sh cliftest     # paket, protokol RTK2, subsistem — 914 assertion
./run.sh dbtest       # lapisan database — 234 assertion; butuh MySQL
./run.sh luaaudit     # pemeriksa statis 907 skrip Lua
./run.sh wiresync     # Wire.java sinkron dengan repo klien
./run.sh elixirtest   # SATU PERTANDINGAN ELIXIR penuh — 34 pemeriksaan
./run.sh carnagetest  # SATU PERTANDINGAN CARNAGE penuh — 28 pemeriksaan
./run.sh sumotest     # SATU PERTANDINGAN SUMO WAR penuh — 21 pemeriksaan
./run.sh beachtest    # SATU RONDE BEACH WAR penuh — 22 pemeriksaan
```

⚠️ Keempat gerbang acara memakai **penyalaan server yang sama**
dengan `./run.sh map` (`MapServer.boot`), jadi map server lain harus MATI —
portnya bentrok. Perkakas bersamanya di `AcaraUji`.

**Gerbang KESEMBILAN — yang paling banyak menemukan bug.** Gerbang luring
menguji kode terhadap dirinya sendiri dan **tidak bisa melihat "sesuatu
yang tidak terjadi"** (Peringatan #88–#101 hampir semua lolos dari gerbang
luring):

```
./run.sh all
(cd ../RTK-client && ./run.sh livetest 127.0.0.1 2001 Adrielle)   # 225 pemeriksaan
```

**Gerbang KESEPULUH — dua map server.** Perpindahan pemain antar map
server (R3/C3) tidak bisa dibuktikan oleh satu server, dan pembagian
petanya ada di kolom `Maps.MapServer` di database — bukan di conf. Satu
perintah menyiapkan semuanya lalu **memulihkan fixture-nya sendiri**
(termasuk bila gagal atau ditekan Ctrl-C):

```
./tools/uji-dua-server.sh      # 167 pemeriksaan (155 + 12 butir C3)
```

Ia meminjamkan peta 330 (Buya) ke map server 1, menjalankan
`conf/map2.conf` di port 2002, menjalankan `livetest`, lalu memulangkan
peta itu ke server 0. ⚠️ Jangan biarkan pinjamannya menetap: di setup satu
server, peta milik server lain tidak dimuat sama sekali — Buya lenyap.
⚠️ `livetest` MELEWATI langkah C3 bila tidak ada apa pun di port 2002,
tetapi bila port itu hidup dan perpindahannya gagal, ia **MERAH**
(Peringatan #112).

⚠️ **Aturan uji untuk setiap subsistem baru:** pemeriksaan di `cliftest`
DAN `livetest`, lalu **rusak kodenya sengaja** untuk membuktikan gerbangnya
bisa merah (kontrol negatif — dua kali menemukan lubang di ujinya sendiri).
⚠️ Jangan jalankan gerbang saat server hidup — keduanya menulis ke
`logs/map.log` yang sama dan hitungan ERROR jadi tercemar.
⚠️ **Sapu `logs/common.log` juga, bukan hanya `map.log`.** `org.rtk.common.*`
(termasuk `Sql` dan `TimerSystem`) jatuh ke logger Root: dua bug nyata hari
ini — kolam koneksi salah proses dan `ConcurrentModificationException` di
timer NPC — TIDAK muncul di `map.log` sama sekali (Peringatan #123, #125).
⚠️ Sebelum `uji-dua-server.sh`: `pgrep -a java | grep RTK-java` harus
KOSONG. Server yang dijalankan dengan tangan tidak dimatikan `run.sh stop`,
menahan port 2001, dan ujinya diam-diam berbicara dengan server yang salah
(Peringatan #127).
⚠️ `livetest` memakan barang karakter uji (Peringatan #102) — periksa
kantong `Adrielle` bila langkah masuk dunia mendadak merah.
⚠️ `livetest` juga MEMINDAHKAN pemain uji (langkah C3 berjalan ke portal
dan pulang lagi; langkah mantra merapal `gateway` yang memindahkannya ke
Buya). Ia memulangkannya sendiri ke petak semula — kalau tidak, langkah
"abaikan" dan "grup" gagal karena JARAK, bukan karena fiturnya
(Peringatan #110).
⚠️ Pemulihan fixture itu **dibaca ulang sampai menempel** (Peringatan
#148): simpanan karakter mendarat di MySQL beberapa ratus milidetik
sesudah map server mencatat "simpan + logout", jadi menulis lalu langsung
lanjut kadang ditimpa — dan yang merah adalah langkah pertukaran, fitur
yang tidak bersalah.

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
**Status: SELESAI — 0 dari 9.812 titik dialog masih Inggris (R4).**
Alatnya di `tools/terjemahan/`: `inventaris.py` (ukur), `terapkan.py`
(terapkan katalog secara POSISIONAL), `separuh.py` (baris separuh
terjemah), `petunjuk-ketik.py` (kalimat yang menyuruh mengetik harus
menyebut kata kunci `speech` yang benar). Katalognya `kamus-*.json`
(3.980 entri) dan daftar yang sengaja dibiarkan Inggris
`dikecualikan.json`.

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

**K1.1–K1.5 SELESAI (28 Agu 2026)** — sisi server dari roadmap K1 klien:
- **K1.1** sapuan area RTK2 MENYERTAKAN pemain sendiri
  (`Rtk2ClientView.playerViewRefreshed`); klien mengenali bloknya lewat id
  `EV_SELF_IDENTITY`, posisi tetap dari `EV_SELF_STEPPED`.
- **K1.2 (Wire v2):** blok benda dibenahi mengikuti RetroTK — NPC
  `npcType==1` kini DIKIRIM (dulu tidak pernah) dengan FLAG_AS_CHAR + blok
  wujud + slot NPCEquipment (`NeqLook` = nomor grafik LANGSUNG, tanpa
  ItemDb); NPC biasa kini TANPA FLAG_AS_CHAR dan membawa `graphicId`
  mentah (ruang look mob — di RetroTK "32768 + graphicId"), dulu
  benderanya keliru menyala dan klien menggambar badan telanjang. Mob
  ber-`MobIsNpc` juga membawa blok wujud (0 baris di DB, tapi jalurnya
  ada). `Wire.VERSION` naik ke 2 di kedua repo.
- Semua dijaga `cliftest` + `livetest` + kontrol negatif.
- **K2 sisi server:** `EV_SELF_SETTINGS` kini dikirim SAAT masuk dunia
  (`playerEnteredWorld`) — dulu hanya saat setelan berubah, sehingga
  jendela setelan klien buta sampai pemain membalik sesuatu.

**K3 sisi server SELESAI (28 Agu 2026):**
- **K3.1** ERROR bising `sendWorldEntry` "tidak punya sesi aktif" tiap
  login RTK2 sudah jadi `log.debug` (klien RTK2 selalu melewati jalur
  RetroTK — itu jalannya router, bukan cacat).
- **K3.2 buku mantra** (`EV_SPELL_SLOT`, Wire v3): dulu hanya PENGHAPUSAN
  mantra (`EV_SPELL_REMOVED`) yang pernah dikirim; kini isi buku ikut saat
  masuk dunia (cermin `pc_loadmagic`) dan saat `addSpell`. Tiap slot bawa
  tipe (`SplType`) supaya klien tahu bentuk muatan `OP_CAST`. `SpellDb`
  dapat kolom `SplQuestion`; `Clif.sendMagic` (0x17) untuk sisi RetroTK.
- **K3.3 penyusun blok pindah ke `Wire.Blok`** (Wire.java): blok barang,
  dasar, dan wujud kini punya satu penulis+pembaca di berkas yang dijaga
  `wiresync` — drift tata letak muatan langsung merah dengan nomor baris,
  bukan lagi tak terjaga. `Rtk2ClientView` dan `Blocks` klien tinggal
  memetakan domain ke primitif.
- **K3.4 login bersandi** (`OP_HELLO` + sandi, Wire v4): map server
  memverifikasi sandi terhadap `ChaPassword` (`CharDb.isPass`) sebelum
  meminta data karakter — dulu siapa pun yang tahu nama bisa masuk. Sandi
  salah = sambungan ditolak.
- **K3-lanjutan pra-login RTK2** (Wire v10, 28 Agu 2026 malam): map server
  kini melayani `OP_ACCOUNT_LOGIN` (→ `EV_CHAR_LIST`) dan `OP_CREATE_CHAR`
  (→ `EV_ACCOUNT_RESULT`) **pada sambungan yang sama** dengan `OP_HELLO`.
  Ini **menyimpang dari C dengan sengaja**: di C pemain menyambung tiga
  kali (login → char → map); RTK2 memakai satu titik. Sesi yang sudah masuk
  akun boleh memilih karakter miliknya tanpa sandi karakter —
  kepemilikannya diperiksa ULANG ke tabel `Accounts` (`akunMemiliki`),
  klien tidak pernah boleh menyebut karakter mana miliknya. Slot akun
  diperiksa SEBELUM `newChar` supaya akun penuh tidak meninggalkan baris
  `Character` yatim. Lihat Peringatan #123, #124.
- **K2-lanjutan papan baca-tulis** (Wire v9): `OP_BOARD_WRITE` /
  `EV_BOARD_POST` menembus map server → char server → MySQL
  (`MapIntif` 0x300A baca, 0x300B hapus, 0x300C tulis; `Mapif` 0x380A
  balas). ⚠️ Papan 0 adalah KOTAK SURAT, bukan papan biasa. `cliftest`
  dapat gerbang baru: nomor opcode/peristiwa ganda langsung merah (satu
  tabrakan `EV_BOARD_POST` vs `EV_TOWNS` ditemukan begitu gerbangnya ada).
- Semua dijaga `cliftest` + `livetest` + kontrol negatif (K3.4 sempat
  lolos palsu karena karakter masih online → diberi baseline "sandi benar
  masuk" lebih dulu; `cobaMasuk` wajib memompa `Connection.proses()`).

| | |
|---|---|
| Gerbang luring | **12/12 hijau** (`cliftest` 914, `dbtest` 234, `elixirtest` 34, `carnagetest` 28, `sumotest` 21, `beachtest` 22) |
| Gerbang klien sungguhan | `livetest` **225** pemeriksaan hijau — **seluruh 46 opcode kini pernah dikirim klien sungguhan** |
| Protokol RTK2 | **46 opcode masuk, 57 peristiwa keluar** (Wire v11) |
| Binding skrip | method sisa **1** (`testPacket`, sengaja); global **0** celah; 4 nama Kan + 8 nama salah-ketik = kode mati konten |
| Skrip Lua | 906/906 termuat, 0 error; `map.log` server hidup 0 ERROR/WARN |
| Peringatan tercatat | #1–#159 → `docs/PERINGATAN.md` (#146 MapBGM 902 = daftar putar; #149 pindah peta sesama server tidak pernah dikirim ke klien; #150 kamera klien salah ruang sehingga peta besar hitam; #152 peta kosong = datanya; ⚠️ #153 pemain terdampar kini dijatuhkan ke inn kebangsaannya; **#154 barang di lantai sempat jadi TEMBOK sehingga memungut mustahil**; #155 warna varian mob: mekanisme dipahami, aturan belum; #156 57% frame objek peta digambar salah tempat; ⚠️ **#157 MOB BELUM BISA DIBUNUH**; #158 animasi efek dulu dibuang klien — kini tergambar; #159 animasi pukulan menunggu Motion.tbl) |
| Penghambat utama | **antarmuka klien** (`../RTK-client`) — server mengirim lebih banyak daripada yang bisa digambar |

## ROADMAP — menuju server yang dipakai normal & lancar tanpa bug

Goal: pemain masuk lewat klien RTK2, semua aksi pemain punya jalur, tidak
ada jalur yang gagal senyap. Urutan = prioritas. Angka opcode C hanya
rujukan perilaku — format kabelnya RTK2, bukan port byte.

### Ringkasan (30 Agustus 2026)

| Tahap | Isi | Status |
|---|---|---|
| **R1** | Aksi pemain tanpa jalur masuk RTK2 (16 butir audit `clif_parse`) | ✅ **SELESAI** 28 Agu 2026 |
| **R2** | TODO di kode — nol tersisa di `src/` | ✅ **SELESAI** 28 Agu 2026 |
| **R3** | Sisa Trek C: **C3** pindah antar map server terbukti pulang-pergi; **C2** meta RetroTK diputuskan TIDAK diport | ✅ **SELESAI** 28 Agu 2026 |
| **R4** | Terjemahan dialog — 0 dari **9.812** titik masih Inggris | ✅ **SELESAI** 28 Agu 2026 |
| **R5** | Stabilisasi berkelanjutan — **enam putaran**, cakupan opcode **46/46** | ⏳ **BERJALAN** |

Putaran R5 sejauh ini:

| Putaran | Fokus | Yang dibongkar |
|---|---|---|
| **1** (28 Agu) | Cakupan opcode 25 → 36 | slot kantong posisi-vs-indeks (#118) · perubahan inventaris tak dikabarkan (#119) · **`.ID` tidak pernah diimplementasikan sehingga MEMUNGUT tidak pernah berhasil** (#120) |
| **2** (28 Agu malam) | Pembuatan karakter & sesi | kolam koneksi salah proses (#123) · **fd dipakai ulang → masuk tanpa sandi** (#124) · CME timer NPC (#125) · sandi tak sampai ke layar masuk (#126) |
| **3** (29 Agu dini) | **Mesin acara berkala** | `map_cronjob()` dan registry sedunia `GameRegistry<id>` **tidak pernah diport** — tidak ada acara, kelahiran bos, `mapLight()`, `itemspawner()` yang pernah jalan |
| **4** (29 Agu) | **Elixir & Carnage berjalan penuh** | `os.time()` LuaJ berpecahan (#134) · **`hasItem` dikembalikan sebagai JUMLAH — 419 syarat barang quest selalu gagal** (#135) · wujud karakter tak terbaca skrip (#136) · `baseClass` hilang (#138) |
| **5** (29 Agu) | **Sumo & Beach berjalan; opcode 46/46** | **`player:warp` di skrip tidak pernah memindahkan pemain — 856 pemakaian diam total** (#140) |
| **6** (29–30 Agu) | **Pertarungan & animasi** | **mob tidak bisa dibunuh: rantai putus di ENAM tempat** (#157) · mob mati lenyap seketika (#162) · gerbang dua server merah karena premis langkahnya sendiri (#163) |

Sisi klien untuk putaran 6 ada di repo `RTK-client`: `Motion.tbl` ternyata
urutan LAPISAN bukan tabel frame (#160), dan klien **membuang** setiap
nomor aksi di bawah 11 sehingga tidak pernah ada animasi menyerang (#161).

### Gerbang hari ini

| Gerbang | Pemeriksaan | Apa yang dijaga |
|---|---|---|
| `cliftest` | **917** | seluruh jalur `Clif`/protokol, termasuk kematian mob menyiarkan `objectRemoved` |
| `dbtest` | **235** | kontrak basis data & `hasItem` sesuai C |
| `worldtest` | 53 | kalender dunia, registry sedunia |
| `scripttest` | 42 | kait Lua global & cron benar-benar bisa dipanggil |
| `chartest` | 39 | codec `CharStatus` |
| `elixirtest` | 34 | satu pertandingan Elixir penuh |
| `carnagetest` | 28 | satu pertandingan Carnage penuh |
| `beachtest` | 22 | satu ronde Beach penuh |
| `sumotest` | 21 | satu pertandingan Sumo penuh |
| `maptest` · `luaaudit` · `wiresync` | — | seluruh `.map` terbaca · sintaks Lua · dua salinan `Wire.java` identik |
| **`tools/uji-dua-server.sh`** | **237** | perpindahan antar map server, pulang-pergi |
| **`livetest`** (repo klien) | **236** | server sungguhan lewat klien sungguhan |

⚠️ `livetest` pada map server yang **baru hidup 30 detik** memberi merah
palsu — beri waktu menetap dulu (Peringatan #163).

**R1. Aksi pemain tanpa jalur masuk RTK2 — ✅ SELESAI 28 Agustus 2026.**
Seluruh 16 butir hasil audit `clif_parse()` kini punya jalurnya, dikerjakan
berpasangan dengan K4 di repo klien (Wire v5 lalu v6).

| Aksi | Opcode RTK2 | Catatan port |
|---|---|---|
| Berputar di tempat | `OP_TURN` | `clif_parseside`: setel arah + kait `onTurn`; bukan langkah |
| Emosi | `OP_EMOTE` | aksi = **emosi + 11**, waktu `0x4E`, hanya keadaan normal |
| Segarkan sekeliling | `OP_REFRESH` | `case 0x38` → `clif_refresh` |
| Melihat pemain/objek lain | `OP_LOOK_AT` | kait `onLook`; RTK2 mengirim **id**, bukan petak |
| Susun ulang barang | `OP_SWAP_ITEM` | `pc_changeitem`: tukar POS, kirim ulang KEDUA slot |
| Susun ulang mantra | `OP_SWAP_SPELL` | `clif_parsechangespell` |
| Status diri lengkap | `OP_PROFILE` ragam 0 → `EV_SELF_PROFILE` | **pengganti `sendMyStatus` TAHAP 1**: nama kelas, gelar, klan, pasangan, TNL, persen XP, teks profil, legenda |
| Status grup | `OP_PROFILE` ragam 1 | menyegarkan `EV_GROUP` yang sudah ada |
| Ubah profil | `OP_PROFILE_EDIT` | teks saja; gambar profil RetroTK adalah blob klien lama |
| Daftar kota | `OP_TOWNS` → `EV_TOWNS` | dari baris `town:` di `conf/map.conf` (`map_town_add`) |
| Ranking | `OP_RANKING` → `EV_RANKING` | dari `RankingScores`; daftar KOSONG tetap dikirim |
| Daftar teman | `OP_FRIENDS` | 20 kolom tetap di tabel `Friends`, seperti C |
| Penanda pemburu | `OP_HUNTER` | `ChaHunter` + `ChaHunterNote` (dipotong 40 huruf) |
| Papan pesan & pos | `OP_BOARD` | aksi 1/2/9 tersambung; 3 & 5 (baca/hapus pos) belum |
| Kiriman/parcel | `OP_PARCEL` | `clif_parseparcel` memang hanya satu baris pesan di C |
| Minimap | — | **tidak perlu opcode**: klien sudah tahu nomor petanya sendiri; di C 0x7C hanya mengirim balik id peta |
| Simpan paperpopup | — | `case 0x23` di C **badannya kosong** (kode mati) |
| Sistem kreasi | — | `0x6B` `createdb_start`; niche, diputuskan tidak diport |

⚠️ **Yang ditemukan saat mengerjakannya:** papan pesan membongkar
Peringatan **#103** — entri tabel panjang paket antar-server `0x3009`
salah ditandai variabel, yang MEMUTUS sambungan char server. Sisi
tampilannya sudah lama ada, tapi tidak ada jalur masuk yang pernah
memintanya; kode yang tidak pernah dipanggil tidak pernah salah.

**R2. TODO di kode — ✅ SELESAI 28 Agustus 2026.** Nol TODO tersisa di
`src/`.

| Butir | Perbaikan |
|---|---|
| Mob mati menghalangi langkah | `clif_canmove_sub`: `MOB_DEAD` tidak menghalangi. Bangkai masih terdaftar di petaknya sampai disapu, jadi pemain dulu terhalang oleh sesuatu yang tak terlihat |
| Klik mob | port cabang `BL_MOB` di `clif_handle_clickgetinfo`: kait `onLook` lalu `click` milik `mob->data->yname`; radius 10, atau 0 untuk mob bertipe 3 |
| Kalender dunia | **`WorldTime` baru** — port `get_time_thing` + `change_time_char`: dimuat dari tabel `Time`, tik tiap 450 detik (jam→hari 92→musim 4→tahun), disiarkan (`EV_WORLD_TIME`, Wire v7), disimpan balik. `curTime/curDay/curSeason/curYear` kini menjawab dari kalender DUNIA; hanya keluarga `real*` yang memakai jam dinding |
| `curServer` | id server dari `conf/map.conf`, bukan 0 tetap |
| `checkOnline` | tiga ragam SQL seperti C (id / semua non-GM / nama) |
| `getXPforLevel` | membaca `ClassDb`; ⚠️ `path > 5` adalah **kelas** dan diterjemahkan dulu lewat `pathOf` |

⚠️ **Dua jebakan uji yang ikut ketahuan** (Peringatan #105 & #106): uji
kalender sempat **menimpa tabel `Time` sungguhan** dengan nilai bawaan
karena ia menyimpan "nilai lama" yang tidak pernah dibaca; dan uji
`curSeason` sempat **lulus dengan kode yang salah** karena satu nilai uji
kebetulan sama dengan hasil jam dinding. Keduanya diperbaiki — baca dulu
sebelum menyimpan, dan uji dengan dua nilai berbeda.

**R3. Sisa Trek C — ✅ SELESAI 28 Agustus 2026.**

**C3 perpindahan antar map server: DIPORT dan TERBUKTI HIDUP.** Pemain
yang menginjak portal ke peta milik map server lain kini diserahkan
(`EV_TRANSFER`, Wire **v8**): server asal mengirim host+port+peta+petak,
klien menyambung ke sana, dan pemainnya masuk di peta tujuan. Dibuktikan
dua map server sungguhan, pulang-pergi, di gerbang `./tools/uji-dua-server.sh`.

| Butir | Isi |
|---|---|
| Peristiwa baru | `EV_TRANSFER` + `ClientView.playerTransferred` + `Clif.transfer` (0x03, port RetroTK) |
| Jalur masuk | `Pc.warp()` memanggil `transferKeServerLain()` bila petanya tidak dimuat di sini |
| Portal | ⚠️ `MapRegistry.loadWarps` **sengaja menyimpang dari C** — hanya peta ASAL yang wajib dimuat. Di C portal lintas-server dibuang pemuatnya, jadi cabang lintas-server `pc_warp` tak pernah tercapai (Peringatan #108) |
| Pemain terdampar | ⚠️ C mengusirnya (badan `intif.c:215` kosong); port ini MENGALIHKANNYA, dengan jeda tutup 500 ms supaya paketnya sempat terkirim (Peringatan #109) |
| Koordinat di luar batas | dijepit ke **1**, bukan 255 — "Just for Justin" di C |

**C2 empat berkas meta RetroTK: DIPUTUSKAN TIDAK DIPORT.** Alasannya dua,
dan yang kedua menentukan:

1. Berkas meta hanya dipakai paket **login RetroTK** `0x6F`
   (`LoginClif`: daftar nama + CRC32). Klien RTK2 tidak pernah bicara ke
   login server sama sekali — sejak K3.4 map server memeriksa sandi
   sendiri. Tidak ada satu pun jalur yang membacanya.
2. Satu-satunya sumber berkas itu adalah `RTK-Server/rtk/decrypted/` —
   **data klien NexusTK**. Repo ini ada di bawah git; menyalinnya ke
   `meta/` berarti menaruh aset NexusTK ke dalam riwayat git, persis yang
   dihindari aturan K6 di repo klien. Nilainya nol, ongkosnya permanen.

Perilaku sekarang aman apa adanya: `metacrc()` mengembalikan **0** untuk
berkas yang tidak ada, jadi klien RetroTK menerima daftar dengan checksum
nol dan tidak ada yang melempar. Kalau suatu saat berkas meta memang
dibutuhkan, isinya **dibuat sendiri**, bukan disalin.

**R4. Terjemahan dialog — ✅ SELESAI 28 Agustus 2026.** Nol dari **9.812**
titik dialog di 665 berkas masih berbahasa Inggris; katalog 3.980 entri di
`tools/terjemahan/kamus-*.json`, diterapkan **posisional** sehingga nama
barang/mob/NPC (identifier) tidak pernah tersentuh.

| Yang dikerjakan | Catatan |
|---|---|
| Prosa dialog, menu, pesan sistem | dari `sendMinitext`, `talk`, `dialogSeq`, `menuSeq`, `menuString`, `input`, `addLegend` (arg 0 saja) |
| Pembanding ikut diterjemahkan | nilai balik `menuString` adalah string opsinya; `pilihan == "Yes"` diganti bersama opsinya |
| Sengaja Inggris | nama barang (`Scroll of Protection`, `Juk-do`), nama stat (`Will`), dan **nilai protokol** (`next`/`previous`/`quit`) → `dikecualikan.json` |

⚠️ **Empat temuan yang hanya muncul karena diuji lewat klien sungguhan**
(Peringatan #113–#117): kalimat yang menyuruh pemain mengetik kata kunci
sempat menyebut kata yang salah dan **membuat dua quest buntu**; opsi menu
dibangun dengan **tiga konstruksi** berbeda sehingga alat statis tiga kali
melapor "nol sisa" sementara menu di layar masih Inggris; literal di dalam
tabel dialog bisa berupa **nama ruang grafik**, bukan teks; dan
`next`/`previous` ternyata **nilai protokol** yang dikirim klien —
menerjemahkannya memutus paging seluruh dialog NPC (ditangkap
`scripttest`). `livetest` kini menuntut dialog + opsi menu yang sampai ke
pemain berbahasa Indonesia, dengan kontrol negatif yang terbukti merah.

**R5. Stabilisasi berkelanjutan — ⏳ BERJALAN, putaran pertama selesai
28 Agustus 2026.** Cara kerjanya: **ukur cakupan, isi celahnya, lalu biarkan
klien sungguhan yang memutuskan.** Putaran pertama menaikkan opcode yang
pernah benar-benar dikirim klien dari **25 menjadi 36 dari 43**, dan
menemukan tiga cacat yang tidak satu pun gerbang luring bisa lihat:

| Cacat | Akibat sebelum diperbaiki |
|---|---|
| Slot kantong: klien mengirim **posisi**, server memakainya sebagai **indeks daftar** (Peringatan #118) | tujuh perintah (jatuh, pakai, kenakan, makan, lempar, serah, tukar) mengenai barang yang SALAH atau diam saja — tanpa pesan |
| Perubahan inventaris tidak dikabarkan ke klien (#119) | barang yang dijatuhkan tetap terlihat, barang yang dipungut tidak pernah muncul, sampai login ulang |
| **`.ID` tidak pernah diimplementasikan** — 1.292 pemakaian di 347 berkas (#120) | **memungut barang dari tanah tidak pernah berhasil**, untuk siapa pun, sejak awal; plus 12 `script error` di `map.log` yang tampak tidak berhubungan |

⚠️ Ketiganya ditemukan oleh langkah `livetest` baru, bukan oleh gerbang
luring — dan yang ketiga menutup **seluruh** `script error` di `map.log`
sekaligus. Satu perbaikan menyelesaikan dua gejala yang tampak terpisah.

Langkah `livetest` yang ditambahkan (18 pemeriksaan): jatuh-pungut yang
memulihkan dirinya sendiri, kenakan-lepas, membalik halaman dialog
(`answerDialog "next"` — penjaga nyaris-celaka R4), serangan, dan
**pertukaran dua pemain** yang dibuka, diisi, lalu DIBATALKAN sehingga
kantong kedua pemain kembali seperti semula. Gerbang luring baru:
`scripttest` memeriksa kait GLOBAL (`onPickUp`/`onLook`/`onTurn`/`onGetExp`)
benar-benar ada — `doScript()` mengembalikan false tanpa suara bila
namanya salah; `cliftest` memeriksa `.ID` menjawab id benda. Kontrol
negatif membuktikan keduanya merah.

**`map.log` dengan pemain sungguhan: 0 `script error` di jalur yang
diuji.** Sisa WARN/ERROR seluruhnya dipicu ujinya sendiri (sandi salah,
bingkai rusak, sambung-putus beruntun) — itu memang yang harus dicatat.

⚠️ **`script error` yang tersisa datang dari FIXTURE, bukan dari kode.**
`core = NPC(4294967295)` (`luascript/Developers/sys.lua:53`) dipakai
**1.036 kali di 27 berkas** sebagai pemegang `gameRegistry`. Ia menunjuk
NPC F1 — baris `NPCs0` ber-`NpcIsF1Npc=1`, yang di C DAN di port ini diberi
id blok khusus `F1_NPC = 0xFFFFFFFF` alih-alih rumus biasa
`NPC_START_NUM + NpcId − 2` (`npc.c:268,315`). Barisnya **ada** di
`NPCs0`, jadi di map server 0 `core` terisi dan skripnya jalan.

Yang tidak punya NPC sama sekali adalah **map server kedua**: tabel
`NPCs1`/`NPCEquipment1` tidak ada di dump ini, jadi di sana `core` nil dan
`NPC("Tower")` nil — dan dari sanalah dua `script error`
(`onScriptedTilesArena`, `onScriptedTilesElixir`) berasal saat gerbang dua
server berjalan. Map server kini **mengatakannya saat start**
("NPC F1 siap" / peringatan bila tidak ada), supaya kegagalan sediam itu
tidak lagi ditemukan dari satu petak acara.

⚠️ Saya sempat menyimpulkan sebaliknya ("`core` selalu nil, di C pun") dari
membaca rumus id saja tanpa memeriksa jalur F1 — pelajaran yang sama
dengan Peringatan #114/#124: kesimpulan dari membaca kode saja harus
dibuktikan pada server hidup.

**Putaran kedua (28 Agu 2026 malam, bersama K2-lanjutan & K3-lanjutan).**
Empat cacat lagi, semuanya lolos dari kedelapan gerbang luring:

| Cacat | Akibat sebelum diperbaiki |
|---|---|
| `CharDb` memakai kolam koneksi char server dari dalam proses MAP (#123) | pembuatan karakter membalas dengan sopan dan **tidak pernah membuat apa pun**; kesalahannya hanya di `logs/common.log` |
| `akunSesi` tidak dibersihkan saat putus, padahal fd dipakai ulang (#124) | sambungan baru **mewarisi akun** sesi sebelumnya dan bisa masuk ke karakternya **tanpa sandi** — lubang keamanan |
| `NpcRegistry.runTimers` mengiterasi daftar yang diubah skrip (#125) | `ConcurrentModificationException` membatalkan **sisa tik**: NPC sesudahnya berhenti bergerak |
| Sandi tidak diteruskan ke layar masuk klien (#126) | `--sandi` terisi tapi yang dikirim sandi kosong; hanya terlihat di gambar tangkapan |

⚠️ #124 ditemukan **kontrol negatif** ("sambungan tanpa akun DITOLAK dengan
sandi kosong"), bukan pembacaan kode — kodenya terlihat benar. #123 dan
#125 tercatat di `logs/common.log`, yang sebelumnya tidak pernah disapu:
disiplin "map.log 0 ERROR" ternyata buta terhadap seluruh `org.rtk.common`.

**Putaran ketiga (29 Agu 2026 dini hari) — MESIN ACARA BERKALA.** Dua
bagian besar dari `map.c` ternyata belum diport sama sekali, dan keduanya
tidak menghasilkan error apa pun: mereka hanya membuat sebagian dunia diam.

| Yang hilang | Akibat |
|---|---|
| `map_cronjob()` — timer 1 detik yang memanggil `cronJobSec/Min/5Min/30Min/Hour/Day` | **tidak ada acara berkala yang pernah dimulai** (elixir, carnage, sumo, beach), tidak ada kelahiran bos, tidak ada `mapLight()`, tidak ada `itemspawner()` |
| `map_loadgameregistry()` / `map_savegameregistry()` — registry sedunia di `GameRegistry<serverid>` | nilai seperti `red_potions_available` dan `elixirRound` selalu mulai 0 dan hilang saat server mati; tabelnya ADA dan berisi, tetapi tidak pernah dibaca |

Keduanya kini ada. Buktinya di server hidup: `sumo_respawn_time` berubah
tiap tik, baris `msg1` **ditulis sendiri** oleh `cronJob30Min`, dan
broadcast setengah-jamnya benar-benar terkirim. Registry mengikuti C:
tulis-terus tiap perubahan, nilai 0 MENGHAPUS barisnya, dan kuncinya
tidak peka huruf besar-kecil (`strcmpi`) tetapi **ejaan pertama
dipertahankan** supaya `carnageMaxHealth` tidak berpasangan dengan baris
kedua bernama `carnagemaxhealth`.

Gerbang baru di `scripttest`: keenam kait cron ada sebagai fungsi Lua, dan
`cronJobDay` benar-benar bisa dipanggil lewat `doScript` — kait yang
berganti nama membuat `doScript` mengembalikan false **tanpa suara**, pola
kegagalan yang sama yang membuat mesin ini diam selama ini. Kontrol negatif
membuktikannya merah.

⚠️ **Yang masih membuat `script error` di gerbang dua server, dan memang
seharusnya:** map server yang hanya memegang SATU peta tidak bisa
menemukan NPC atau peta milik server lain. `boss_spawn.lua:44` memanggil
`math.random(1, getMapXMax(peta))` untuk peta yang tidak ia miliki (0 →
"interval is empty"), dan `onScriptedTilesArena` mencari `NPC("Tower")`
yang tinggal di peta 31 milik server 0. C berperilaku sama; isi skripnya
menganggap dunia satu server.

⚠️ **Acara yang tetap TIDAK bisa jalan:** `ctf` (dipakai
`arena_exit_teleporter.lua`) dan `bomb_game` (bomber war) tidak
terdefinisi di mana pun di pohon Lua ini — berkasnya memang tidak ikut.
`processKanDonations` juga hilang (peringatan sekali per proses).

**Putaran keempat (29 Agu 2026) — SATU PERTANDINGAN ELIXIR BERJALAN.**
Gerbang baru `./run.sh elixirtest` memainkan pertandingan penuh di atas
penyalaan server sungguhan: pintu dibuka → 6 pemain → regu dibentuk
(3 lawan 3) → arena disiapkan → dua ronde dimenangi lewat
`ElixirGameNpc.handItem` → NPC hadiah muncul → acara menutup dirinya
sendiri. Yang dipercepat hanya JAMNYA; tiap peralihan keadaan tetap
diputuskan skrip acara.

Menjalankannya membongkar **tiga cacat mesin skrip** yang tidak satu pun
gerbang pernah lihat, dan ketiganya diam total:

| Cacat | Akibat |
|---|---|
| `os.time()` LuaJ berpecahan (#134) | **setiap** `os.time() == x` gagal — 13 tempat, termasuk penutupan pintu Elixir dan Carnage; acara tidak pernah lewat tahap pertama |
| `hasItem` dikembalikan sebagai JUMLAH, bukan `true`/kekurangan (#135) | `hasItem(x,1) == true` dipakai **419×** dan SELALU false — setiap syarat barang di quest gagal; bentuk `if hasItem(...)` malah selalu lolos |
| Wujud karakter (`face`, `hair`, `armorColor`, …) tak pernah dibaca skrip (#136) | `clone.equip` melempar "compare nil with number" — **seluruh sistem klon mati**: penyamaran, pewarnaan regu, potret NPC |

⚠️ Dua gerbang lama justru **mengunci perilaku yang salah** — `dbtest`
menuntut `hasItem` menjumlahkan lintas slot, `scripttest` memakai
`hasItem(...) + 39`. Keduanya ditulis dari perilaku port, bukan dari sumber
C, jadi keduanya membela bugnya (keluarga yang sama dengan gerbang helm
#130). Sudah ditulis ulang ke kontrak C.

**Carnage juga terbukti berjalan (29 Agu 2026).** `./run.sh carnagetest`
(28 pemeriksaan) memainkan pertandingan Carnage penuh: pintu dibuka →
8 pemain berempat jalur kelas → regu dibagi menurut jalur lalu diwarnai
berselang-seling jadi kubu → arena 3017 → dua ronde dimenangi → pintu
keluar kubu juara dibuka. Bentuknya berbeda dari Elixir dan itulah
gunanya: rondenya dimenangi bukan dengan menyerahkan barang, melainkan
dengan menjadi **satu-satunya warna yang masih hidup** — jadi ujinya
mematikan kubu lain (`state = 1`) dan **skripnya sendiri** yang menghitung
lalu menaikkan skor.

Satu binding lagi yang ternyata hilang: **`baseClass`** (jalur kelas,
`classdb_path` di sl.c:7601) dipakai konten **183×** dan tidak pernah
dijawab — tanpa itu Carnage menaruh seluruh pemain di kubu 0 dan tidak ada
ronde yang bisa dimenangi. Keluarga yang sama dengan #136.

⚠️ **Carnage TIDAK menutup dirinya sendiri** seperti Elixir: keadaan 101
adalah keadaan terakhirnya, dan yang membereskan adalah `closeGame()` —
dipanggil skrip hanya bila pesertanya kurang, atau oleh acara berikutnya
lewat `init`. Itu rancangan skripnya, bukan cacat; gerbangnya memanggil
penutupnya apa adanya lalu memeriksa akibatnya.

**Putaran kelima (29 Agu 2026) — SUMO, BEACH, dan TUJUH OPCODE TERAKHIR.**

- `./run.sh sumotest` (21): pendaftaran → ditutup sendiri → 8 pemain
  dipindahkan ke arena → 30 poin dikumpulkan lewat **dorongan sungguhan**
  (`SumoWarNpc.push`; petak airnya dicari dari data peta, bukan ditulis di
  uji) → `winnerCheck` menyatakan pemenang → acara membereskan diri.
- `./run.sh beachtest` (22): arena **dipilih acak skrip** → tepat **50
  tembakan sungguhan** (`BeachWarNpc.hit`) memenangi satu ronde → skor
  disetel ulang untuk ronde berikutnya.
- **Cakupan opcode kini 46/46.** Tujuh yang terakhir dikerjakan di
  `livetest`: `profileEdit` (sunting lalu kembalikan), `dropGold` (jatuh +
  pungut, memulihkan sendiri), `handItem`/`handGold` (dua pemain
  bersebelahan, lalu dibatalkan), `eat` dan `throw` lewat **jalur
  penolakan/konfirmasi** supaya tidak memakan barang pemain uji, dan
  `hello` yang memang selalu lewat `Commands`.

⚠️ Yang dibongkar putaran ini: **`player:warp` di skrip tidak pernah
memindahkan pemain** (#140) — 856 pemakaian diam total. Sesudah
diperbaiki, konten mulai benar-benar memindahkan karakter uji, dan
`livetest` harus memulihkan fixture-nya sendiri.

**Putaran keenam (29–30 Agu 2026) — PERTARUNGAN & ANIMASI.** Mob akhirnya
bisa dibunuh, dan kematiannya terlihat di layar.

| Cacat | Akibat sebelum diperbaiki |
|---|---|
| **Rantai pertarungan putus di ENAM tempat** (#157) | menyerang mob tidak pernah mengurangi nyawanya, untuk siapa pun |
| Mob mati **lenyap seketika** (#162) | animasi kematian di `monster.dna` aksi 0 tidak pernah punya kesempatan digambar |
| Gerbang dua server merah bergantian (#163) | premis langkah "portal sesama server" salah di setup itu — peta tujuannya justru yang DIPINJAMKAN ke server kedua |

Enam lapis #157, tiap lapis menyembunyikan lapis berikutnya: `block.miss`
tak terikat → `hitCritChance` gagal → `critChance` 0; `block.blType` tak
terikat → seluruh cabang pemain dilewati; serah-terima
`damage`/`critChance`/`minSDam`/`maxSDam` tak terikat; kelas `Mob` tidak
punya `sendHealth`/`hasDuration`/`setDuration`; `player.rage`,
`target.IsBoss`, `target.sleep` tak terikat sehingga kerusakan tetap 0;
dan terakhir **`MobRegistry.kill()` menyetel keadaan mati lalu
mengeluarkan mob dari petaknya tanpa mengabari siapa pun.**

⚠️ Alat yang membongkar tiga lapis terakhir adalah **diagnostik
`[ATTR-KOSONG]`** di `Bindings.lapor` — satu baris DEBUG per atribut yang
dibaca skrip tetapi tidak terikat, sekali seumur proses. Atribut yang
hilang **tidak melempar**; ia menjawab nil dan diam. Keluarga yang sama
dengan #120, #136, #138.

⚠️ Urutan siaran menentukan: `objectActed` (aksi 0) harus dikirim
**selagi mob masih terdaftar di petaknya**, karena klien memutuskan "ini
mob, bukan pemain" dengan menengok bendanya sendiri.

**Sisa:** skenario multi-pemain masih terbatas pada grup, abaikan,
pertukaran, dan serah-terima; acara Bomber War belum dibuktikan berjalan.

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
