# CLAUDE.md — RTK-java-version

Panduan untuk sesi pengembangan (berbantuan AI maupun manusia) di project
ini. Baca README.md untuk gambaran lengkap; file ini fokus ke hal yang
harus diketahui SEBELUM mengubah kode.

## ⚠️ ARAH PROJECT BERUBAH — 26 Agustus 2026 (FINAL)

**Protokol RetroTK akan DIGANTI dengan rancangan sendiri, dan klien dibuat
sendiri memakai libGDX.** Baca ini sebelum memutuskan apa pun.

Yang berubah:

| | Sebelum | Sekarang |
|---|---|---|
| Kompatibilitas byte-per-byte dengan klien RetroTK | tujuan utama | **bukan tujuan** |
| Paket `clif_*` (`0x0D`, `0x07`, `0x33`, `0x58`, berkas meta C2) | prioritas tinggi | **rendah — akan ditulis ulang** |
| Logika permainan (binding Lua, skrip, data dunia) | pendukung | **aset paling berharga** |

Yang **terbawa utuh** ke protokol apa pun: 906 skrip Lua, 9.850 peta, 4.476
portal, 716 jenis mob, 2.545 item, dan ~89 binding yang belum diport.

Yang **akan dibuang**: format kabel. Karena itu `Clif.sendMyStatus()` sengaja
dibiarkan **TAHAP 1** (struktur benar, isi kosong) — jangan habiskan waktu
melengkapinya.

### Lapisan pemisah logika ↔ protokol (SUDAH ADA sejak 26 Agustus 2026)

**`ClientView`** (`src/org/rtk/map/ClientView.java`) adalah batas antara
logika permainan dan protokol. Logika memanggil **peristiwa** —
`playerSpellRemoved(sd, slot)`, `npcMoved(...)` — dan implementasi yang
menerjemahkannya jadi byte. Yang ada sekarang: **`RetroTkClientView`**
(memetakan ke `Clif.*`). Protokol baru = tulis implementasi kedua lalu tukar
`MapServer.clientView`; **logika tidak perlu disentuh sama sekali**.

⚠️ **ATURAN: kode di luar lapisan protokol JANGAN memanggil `Clif.*`
langsung.** Tambahkan peristiwa baru di `ClientView`, implementasikan di
adapter. Ini berlaku terutama saat memport ~89 binding yang tersisa — pola
lama (`User.scriptRemoveSpell()` → `Clif.removeSpell()`) adalah persis yang
membuat penggantian protokol jadi mahal.

Nama method menyebut **apa yang terjadi**, bukan paket apa yang dikirim.
Daftar method di antarmuka itu pada akhirnya **adalah** spesifikasi protokol
baru — diturunkan dari kebutuhan nyata skrip, bukan dikarang dari nol.

Cakupan saat ini: seluruh panggilan keluar sudah lewat lapisan ini —
**25 peristiwa** per 26 Agustus 2026 sore, naik dari 9 saat lapisan ini
dibuat pagi harinya. Yang bertambah: `playerIdentityChanged`,
`playerDurationChanged`, `playerAetherChanged`, `objectSpoke`,
`objectActed`, `objectAppearanceChanged`, `objectRemoved`, `objectThrown`,
`mobMoved`, `mobSpawned`, `floorItemAppeared`. Hitung ulang dengan
`grep -c "^    void " src/org/rtk/map/ClientView.java`. Yang **belum**: arah **masuk** (`Clif.parseWalk`,
`parseClick`, `parseMenuInput`, `parseNpcDialog`, `decrypt` di
`MapServer.clientParse`) — itu loop dispatcher paket, urusan terpisah yang
juga perlu dipindah saat protokol baru dirancang.

⚠️ **Kebocoran yang diketahui:** `npcMoved()` masih membawa empat parameter
petak-yang-baru-terlihat — konsep viewport RetroTK, bukan peristiwa
permainan. Idealnya adapter menghitungnya sendiri dari arah gerak. Sudah
ditandai di Javadoc-nya; rapikan saat protokol baru dirancang.

## Apa project ini

Port Java SE dari **RTK-Server** (`../RTK-Server`), server MMO
RetroTK/NexusTK yang aslinya ditulis dalam C (`rtk/src/`: login-server,
char-server, map-server) + MySQL + skrip konten Lua (`rtklua/`, 907 file).
Konten Lua TIDAK dikonversi — dijalankan apa adanya lewat LuaJ (keputusan
desain, lihat README "Scripting engine").

Kebijakan port **dulunya** setia byte-per-byte terhadap protokol wire C.
Sejak 26 Agustus 2026 itu tidak lagi berlaku (lihat bagian di atas); kesetiaan
pada C tetap dipertahankan untuk **logika**, tidak untuk format kabel.

## Lokasi & layout (PENTING)

Project berada di `~/Documents/GitHub/RTK-java-version` — huruf "java"
kecil, dan isi project ada **langsung di root repo** (tidak ada subfolder
`RTK-java/`). Repo sumber C ada sebagai tetangga:

```
GitHub/
├── RTK-Server/          # sumber C asli (rujukan; TIDAK dibaca saat runtime)
└── RTK-java-version/    # project ini
```

**Project ini mandiri sejak 20 Agustus 2026** — data game sudah disalin ke
dalamnya, jadi server tidak lagi membaca `../RTK-Server` saat berjalan
(sudah diuji dari folder terpisah tanpa RTK-Server sama sekali):

| Folder | Isi | Asal | Default config |
|---|---|---|---|
| `maps/` | 3.544 `.map`, ~38 MB | `rtkmaps/Accepted/` | `map.path` / `map_path` |
| `luascript/` | 907 `.lua`, ~6,8 MB | `rtklua/` (Accepted + Developers) | `lua.path` / `lua_path` |
| `database/` | skema + dump MySQL, ~13 MB | `database/` | — (dipakai manual saat setup) |

Keduanya memakai rantai prioritas yang sama:
`rtk-server.properties` → `conf/map.conf` → argumen CLI. Kalau memindahkan
folder data, cukup ubah `conf/map.conf`; **jangan** menanam path di kode.

`database/` berisi 21 skrip migrasi (52 tabel) + dump lengkap
(`*.sql.bak`: 9.850 baris `Maps`, 4.476 `Warps` — diukur setelah impor
sungguhan 21 Agustus 2026; angka lama "7.974" salah). Jalankan lewat
`database/migrate.sh`.

`RTK-Server` **tetap disimpan** (keputusan user 20 Agustus 2026) sebagai
rujukan kebenaran saat mem-port (`rtk/src/**.c` — masih ±2/3 pekerjaan
tersisa) dan sumber bila konten diperbarui. Yang BELUM disalin dan masih
hanya ada di sana: `rtk/src/` (sumber C), `rtk/SObj.tbl` (18.954 entri,
wajib untuk klien libGDX), `rtk/decrypted/` (metadata item/char —
kandidat untuk 4 berkas meta yang hilang; `login.conf` minta 5, `meta/`
cuma punya `RidableAnimals`). Repo itu clone bersih dari
`github.com/unkmc/RTK-Server` sehingga bisa di-clone ulang bila perlu.

## Build & test

**Alur resmi: compile di lokal, deploy manual ke server.** Tidak ada
proses build di server.

- **Build utama: NetBeans** (`Run > Clean and Build Project`) →
  `dist/RTK-java-version.jar` + `dist/lib/`.
- **Build alternatif tanpa NetBeans:** `./build.sh` (javac `--release 25`)
  → `dist/RTK-java.jar`. Dipakai untuk uji cepat/headless.
- **Menjalankan:** `./run.sh {login|char|map|all|status|stop}`, yang
  membungkus `java -jar <jar> <server> &`.
- **Uji regresi** (semua wajib hijau sebelum selesai):
  `./run.sh scripttest` (906 skrip Lua + coroutine dialog),
  `./run.sh maptest` (3.544 berkas peta),
  `./run.sh chartest` (serialisasi karakter, 29 assertion),
  `./run.sh worldtest` (dunia peta + penempatan pemain, 53 assertion),
  `./run.sh cliftest` (paket klien, gerakan, portal, penggambaran, gambar
  ulang peta, dialog NPC, toko, mob, AI & pertarungan, obrolan & gerakan,
  durasi mantra, gerak mob, barang lantai, inventaris, buku mantra,
  tampilan & timer, simpan paksa — **518 assertion**),
  `./run.sh dbtest` (lapisan database ke MySQL hidup — 132 assertion;
  butuh MySQL, lihat "Menyiapkan MySQL lokal").
- **Alat bantu** (bukan gerbang regresi): `./run.sh luaaudit` — pemeriksa
  statis 907 skrip Lua. Lihat "Audit skrip Lua" di bawah.

```
./build.sh            # atau build di NetBeans
./run.sh scripttest   # GERBANG REGRESI — 906 skrip Lua
./run.sh maptest      # GERBANG REGRESI — 3.544 berkas peta
./run.sh chartest     # GERBANG REGRESI — serialisasi karakter
./run.sh worldtest    # GERBANG REGRESI — dunia peta + pemain
./run.sh cliftest     # GERBANG REGRESI — paket klien + gerakan + portal
./run.sh dbtest       # GERBANG REGRESI — database (butuh MySQL hidup)
./run.sh luaaudit     # alat bantu — pemeriksa statis skrip Lua
```

Perintah manual setara:

```
CP=$(echo extLib/*.jar | tr ' ' ':')
javac --release 25 -encoding UTF-8 -cp "$CP" -d build/classes $(find src -name "*.java")
cp resources/log4j2.xml resources/rtk-server.properties build/classes/
java -cp "build/classes:$CP" org.rtk.map.script.ScriptTest
```

- Level bahasa **Java 25** — fitur modern boleh dipakai (mis. switch
  expression di `RtkLauncher`). Diatur di dua tempat yang harus sinkron:
  `javac.source`/`javac.target` di `nbproject/project.properties` dan
  `RELEASE` di `build.sh`. Konsekuensi: **server wajib JRE/JDK 25+**.
- **`Main-Class` jar = `org.rtk.RtkLauncher`** (dispatcher). Satu jar hanya
  bisa punya satu main class, sedangkan ada tiga server; launcher memilih
  lewat argumen pertama (`login`/`char`/`map`/`scripttest`) dan meneruskan
  sisa argumen apa adanya. Kalau menambah server/entry point baru,
  daftarkan di `RtkLauncher` juga.
- Library eksternal HANYA dari `extLib/*.jar` (kebijakan user: **tanpa
  Maven/Gradle**). Menambah jar baru:
  1. unduh dari Maven Central ke `extLib/`,
  2. daftarkan di `extLib/README.md`,
  3. tambahkan ke `nbproject/project.properties` — dua tempat:
     `file.reference.<nama>.jar=extLib/<nama>.jar` dan entri baru di
     `javac.classpath`. Paling aman lewat NetBeans:
     *Project Properties > Libraries > Add JAR*.

  `build.sh` memakai wildcard `extLib/*.jar` jadi otomatis terikut, dan
  `.gitignore` punya `!extLib/*.jar` supaya jar-nya ikut ter-commit
  (lihat Peringatan #8).
- Tanpa MySQL, login/char server exit saat start (perilaku sama dengan C),
  sedangkan map server, ScriptTest, dan gerbang regresi lain tetap jalan.
  Itu bukan bug. `dbtest` satu-satunya yang wajib punya database.

### Menyiapkan MySQL lokal

**Jebakan:** di Ubuntu/Pop!_OS, `root` MySQL memakai plugin `auth_socket`,
bukan password kosong — jadi hanya bisa diakses lewat `sudo mysql`.
Gejalanya `ERROR 1698 (28000)`, bukan `1045`. Buat user yang memang sudah
tertulis di `conf/char.conf` supaya tidak perlu mengubah konfigurasi:

```
sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  CREATE USER IF NOT EXISTS 'rtk'@'%' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"

mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
```

Dump-nya sudah memuat `CREATE DATABASE RTK` + seluruh isinya (54 tabel),
jadi `database/migrate.sh` hanya diperlukan bila ingin menelusuri migrasi
satu per satu. **Perhatikan:** dump diawali `DROP DATABASE IF EXISTS RTK` —
periksa dulu isi database `RTK` yang ada sebelum mengimpor ulang.
Dump dibuat di MySQL 5.7; impor ke 8.0.46 berjalan tanpa penyesuaian
(terbukti 21 Agustus 2026).

## Terjemahan Indonesia (mulai 26 Agustus 2026)

Sumber kebenaran: **`luascript/GLOSARIUM.md`** — baca sebelum menerjemahkan
berkas baru, dan tambahkan entri ke sana alih-alih memutuskan sendiri.

Keputusan gaya: **campuran menurut karakter** (tetua `Anda`, pedagang/mob
`kau`), **nama diri dipertahankan** (`Mythic Nexus`, `Kugnae`, `Ju Jak`),
nama guild diterjemahkan (`Guild Prajurit`).

⚠️ **Nama barang/mob/NPC di skrip Lua adalah IDENTIFIER, bukan teks
tampilan.** `player:addItem("apple", 1)` mencocokkan `ItmIdentifier`, dan
`ItemDb.infoByName` diindeks dengan kolom itu. Menerjemahkannya di skrip akan
membuat 586 pemanggilan `addItem` gagal menemukan barangnya — **secara
senyap**. Terjemahan nama dikerjakan di kolom `*Description` di database
(`database/terjemahan/`). Terverifikasi: **nol** skrip memakai nama tampilan.

⚠️ **Kata kunci `speech` adalah YANG DIKETIK PEMAIN.** Sudah diterjemahkan
(174 penggantian, 62 berkas). Dari 231 kata kunci hanya ~90 kosakata pemain;
sisanya 38 perintah GM (`/...`) dan ~80 kode debug grafis (`ptile`, `nweapc`).
**Aturannya per-berkas, bukan per-kata**: `Accepted/speech.lua`, `Tools/`,
`God_Tools*`, `gm_click*` dikecualikan — `pass` di `speech.lua` berarti petak
bisa dilewati, di `sya.lua` berarti izin lewat.

⚠️ **Setiap kali kata kunci baru diterjemahkan, WAJIB cari ulang kalimat yang
menyuruh pemain mengetiknya** (`say`/`tell me`/`ask me`/`type` + kata kunci).
Kalau tidak, quest-nya tidak bisa diselesaikan dan gagalnya **senyap** — NPC
tetap menjawab, hanya tidak pernah pada kata yang dianjurkannya sendiri.

## Audit skrip Lua (`./run.sh luaaudit`)

⚠️ **Angka "belum diport" adalah BATAS BAWAH, bukan angka pasti.**
`addLegend` dipakai 140x, terdaftar di `sl.c:6080`, dan tidak ada sama sekali
di kode kita — tapi **tidak dilaporkan**, karena `jukebox.lua:115` kebetulan
punya kunci tabel bernama sama dan audit menganggapnya "terdefinisi di
korpus". Binding apa pun yang namanya sama dengan kunci tabel atau fungsi
lokal di salah satu dari 907 berkas jadi tak terlihat. Silangkan ke `sl.c`,
jangan percaya angkanya mentah.

Pakai `-Drtk.audit.penuh=true` untuk daftar utuh — bawaannya terpotong di 10
nama, sehingga 90 sisanya tidak pernah terbaca.

`scripttest` hanya membuktikan 906 skrip **termuat**. Lua tidak memeriksa
apa pun sampai barisnya benar-benar dijalankan, jadi salah ketik nama
fungsi baru meledak ketika pemain menyentuh NPC-nya.
`map/script/LuaAudit` menutup celah itu tanpa menjalankan skripnya:
parse seluruh berkas dengan **parser LuaJ yang sama** dengan runtime, lalu
cari kunci tabel ganda, definisi fungsi ganda, parameter ganda, serta nama
yang dipakai tapi tidak ada.

Daftar "nama yang ada" **tidak ditebak**: mesin skrip dinyalakan dulu, lalu
tabel global dan prototipe `Player`/`NPC`/`Mob` dibaca isinya — jadi binding
Java, pustaka standar, dan definisi dari skrip lain ikut terhitung.

Dua pembedaan yang membuat keluarannya bisa dipakai:

1. **Dipanggil/diindeks vs sekadar dibaca.** Membaca global tak
   terdefinisi di Lua *sah* dan menghasilkan nil (banyak skrip memang
   mengeceknya dengan `~= nil`). Yang meledak adalah `x()` atau `x.y` saat
   `x` nil. Hanya kelompok kedua yang dilaporkan sebagai "AKAN MELEDAK".
2. **Belum diport vs tidak ada sama sekali.** Nama disilangkan ke
   `RTK-Server/rtk/src/map/sl.c`. Yang ada di sana = celah port yang sudah
   diketahui; yang tidak ada di mana pun = salah ketik atau kode mati.

   ⚠️ **Ambil hanya titik pendaftaran yang sebenarnya**
   (`lua_register`, `typel_extendproto`, `lua_setglobal`, dan makro
   `SL_EXPOSE_ENUM` yang memakai stringify `#val` sehingga namanya tidak
   pernah muncul sebagai literal). Menyapu semua literal string dari `sl.c`
   ikut menangkap **nama properti objek** (`"might"`, `"level"`, …),
   sehingga salah ketik seperti `might.might` terlihat sah. Ini pernah
   kejadian dan sempat menyembunyikan satu bug nyata.

Hasil audit 21 Agustus 2026 dan setiap perubahan yang menyusul dicatat di
[`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md) — **wajib dibaca sebelum
menyegarkan konten Lua dari upstream**, karena `luascript/` tidak lagi
byte-identik dengan `rtklua/`.

## Peringatan penting

1. **Diagnostik IDE (VSCode Java) adalah noise** — workspace tidak mengenal
   source root `src/` dan classpath `extLib/`, jadi setiap edit memunculkan
   error palsu ("package does not match", "import cannot be resolved").
   **Patokan kebenaran adalah `javac` di terminal**, bukan panel IDE.
2. **Jangan mengubah byte layout paket.** Semua offset/panjang paket ikut
   `rtk/src/*/{clif,intif,logif,mapif}.c`. Kalau menyentuh kode paket,
   hitung ulang header BE di [1..2], posisi key indexes, dan argumen
   `wfifoSet()` — lalu bandingkan dengan C-nya.
3. **Konvensi endianness** (host C little-endian):
   `rfifoW/wfifoW/rfifoL/wfifoL` = little-endian (protokol antar-server,
   opcode 0x1000/0x2000/0x3000/0x3800); `rfifoWBE/wfifoWBE/...LBE` =
   big-endian (= `SWAP16/SWAP32` di C; protokol klien: header panjang,
   field dalam paket 0xAA).
4. **fd integer = bagian protokol.** Login mengirim fd kliennya ke char
   server dan menerimanya kembali (W di offset 2). Jangan ganti skema
   session id.
5. **Bug C yang sengaja dipertahankan** (ada komentar "Faithful port note"):
   auth antar-server menolak hanya bila id DAN pw dua-duanya salah. Jangan
   "diperbaiki". Bug yang sudah diperbaiki sengaja: parsing
   `start_money`/`start_point` (strcmpi==1) di CharServer.
6. **`ServerLog.addLog/logAdd` ≠ logging diagnostik.** Itu port
   `add_log()`/`Log_Add()` C (log game-event ke file dinamis). Diagnostik
   memakai Log4j2 (`LogManager.getLogger`) — jangan tambah
   `System.out.println` baru.
7. **`nbproject/` DAN `build.xml` milik NetBeans — jangan diutak-atik.**
   Project ini **NetBeans J2SE (Java with Ant)**
   (`org.netbeans.modules.java.j2seproject`). Keputusan user (19 Agustus
   2026): pertahankan bentuk bawaan NetBeans sepenuhnya.
   - `build.xml` **dibiarkan polos** — jangan tambahkan target kustom di
     situ. NetBeans pernah menimpanya dan menghapus target buatan tangan.
     Kebutuhan menjalankan server dipenuhi lewat `run.sh`, bukan target Ant.
   - `nbproject/build-impl.xml` di-generate dari `project.xml`; jangan
     diedit tangan (checksum di `genfiles.properties`).
   - `src`, `resources`, `extLib`, `conf` semuanya terdaftar sebagai source
     root. Efeknya isi `extLib/` dan `conf/` ikut tersalin ke
     `build/classes` dan terbungkus ke dalam jar. **Ini dibiarkan sesuai
     keputusan user** (`dist.archive.excludes` sengaja kosong). Perlu
     diketahui: jar bersarang di dalam jar TIDAK dimuat JVM — dependensi
     yang benar-benar dipakai saat runtime datang dari `dist/lib/` lewat
     `Class-Path` di manifest. Karena itu **`dist/lib/` wajib ikut disalin**
     saat deploy.
   - `nbproject/private/` = setelan lokal per-mesin, **jangan** di-commit.
   - `ant jar` butuh task CopyLibs dari IDE, jadi gagal di mesin tanpa
     NetBeans. Itu bukan masalah karena build memang selalu dilakukan di
     lokal.
8. **Aturan konkurensi lapisan jaringan.** Sejak refactor 19 Agustus 2026,
   `NetServer`/`TimerSystem`/`Core` adalah **instance per server** (bukan
   statis lagi), dan tiap server punya IO thread sendiri yang menyerahkan
   pekerjaan ke logic thread lewat `ArrayBlockingQueue<Session>`. Yang wajib
   dijaga saat menyentuh lapisan ini:
   - `rdata` (buffer baca) disentuh IO + logic → **selalu di bawah
     `synchronized (session)`**. `NetServer.readSession()` dan
     `NetServer.handle()` sudah melakukannya; jangan baca `rdata` dari
     thread lain.
   - `wdata` (area susun paket) **hanya milik logic thread**. Jangan
     membuat IO thread menyentuhnya. `wfifoSet()` menyalin paket ke
     `outbox` (ConcurrentLinkedQueue) — itulah satu-satunya jalur ke IO.
     Ini penting karena parser rutin menulis ke *session lain*.
   - Tabel session = `AtomicReferenceArray`; akses lewat `net.session(fd)`,
     jangan simpan referensi array.
   - **Jangan menjadikan logika permainan multi-thread.** Urutan paket per
     koneksi harus terjaga, dan LuaJ (`Globals` + coroutine per pemain)
     tidak thread-safe. Satu logic thread per server adalah keputusan
     desain, bukan kemalasan.
   - Uji lapisan ini dengan tes integrasi TCP sungguhan (accept, urutan
     paket, banyak koneksi paralel) — `ScriptTest` tidak menyentuh
     `NetServer` sama sekali. Cek pemisahan thread dengan
     `jcmd <pid> Thread.print`: harus ada `rtk-io-<nama>` terpisah.
   - Akibat refactor ini ketiga server **secara teknis sudah bisa jalan
     dalam satu JVM**. Sisa penghalangnya tinggal satu: `ServerLog`
     (`logFilename`/`dmpFilename`) masih statis — satu-satunya state
     statis mutable yang tersisa di `common/`. `run.sh` tetap memakai tiga
     proses demi isolasi restart & crash.
9. **Blob karakter memakai format sendiri, BUKAN dump struct C.**
   Keputusan user 20 Agustus 2026, dengan alasan terukur:
   `sizeof(struct mmo_charstatus)` = **3.171.352 byte** (dibuktikan dengan
   mengompilasi header C-nya), **82% hanya array registry kosong**, dan
   ukurannya **berbeda antar target**: 3.171.352 di 64-bit vs 3.168.952 bila
   `unsigned long` = 4 byte (build 32-bit asli). Jadi tata letak biner C
   bukan kontrak yang stabil. Kedua ujung paket 0x3003/0x3004 adalah kode
   Java kita sendiri.
   - Format ada di `common/mmo/CharStatusCodec`: magic `"RTKC"` + versi,
     string panjang-berprefiks, koleksi hanya menulis entri terisi, hasil
     akhir tetap dikompresi zlib (transport tidak berubah).
     Hasil: karakter baru 27 byte, penuh 4.263 byte — **743× lebih kecil**.
   - **Naikkan `CharStatusCodec.VERSION` bila tata letak berubah**, supaya
     blob lama ditolak dengan pesan jelas, bukan salah baca diam-diam.
   - Konsekuensi: char server C tidak bisa dipasangkan dengan map server
     Java (dan sebaliknya). Itu diterima.
10. **Endianness pada uji framing paket.** Protokol antar-server
   (0x1000/0x2000/0x3000/0x3800) **little-endian** (`wfifoW`/`wfifoL`);
   protokol klien big-endian. Pernah kejadian: uji framing ditulis dengan
   helper big-endian sehingga **lolos tapi tidak menguji kode sebenarnya**.
   Kalau menulis uji byte-level, helper-nya wajib meniru urutan byte yang
   dipakai handler, bukan sekadar konsisten dengan dirinya sendiri.
   Bug versi C yang sengaja TIDAK ditiru: handler simpan membaca panjang
   dengan `RFIFOL(fd,1)-6` padahal dispatcher menulis di offset 2 —
   meleset satu byte. Port ini konsisten di offset 2.
11. **Aturan dunia peta (`map/data`, `map/Pc`).**
   - **Indeks spasial** membagi peta jadi blok 8×8 (`BLOCK_SIZE`).
     Benda didaftarkan lewat `MapData.addBlock()`; pemain dan mob disimpan
     pada indeks terpisah, seperti `block[]` vs `block_mob[]` di C.
   - **Area pandang klien = x±9, y±8** (AREAX_SIZE 18 x AREAY_SIZE 16).
     Bila menembus tepi peta, area **digeser, bukan dipotong**, supaya
     lebarnya tetap penuh. Ini ditiru persis dari `map_foreachinarea()`;
     ada assertion khusus untuk batas x+9 vs x+10.
   - **`Pc.setPos()` sengaja TIDAK menyentuh indeks blok** — sama seperti
     `pc_setpos()` di C. Yang mendaftarkan pemain adalah `Pc.spawn()`
     (di C: `clif_spawn` -> `map_addblock`). Jangan "rapikan" dengan
     menggabungkan keduanya; ada alur yang memang menyetel posisi dulu.
   - **Geometri peta di-cache per nama berkas** di `MapRegistry`, karena
     9.850 baris `Maps` hanya menunjuk 2.919 berkas unik. Tiap peta tetap
     dapat `MapData` sendiri (metadata & isinya berbeda).
   - Tambahan yang **tidak ada di C**: bila posisi tersimpan pemain
     ternyata tembok, `Pc.enterWorld()` mencari petak aman terdekat
     (pencarian spiral) agar pemain tidak tersangkut di dinding.
13. **Jebakan pada `clif_parsewalk` (map/Clif).** Tiga hal yang sudah
   pernah salah dan ditutup dengan uji di `cliftest`:
   - **`direction` TIDAK boleh di-mask `& 0x03`.** C memakai `switch`
     tanpa `default`, jadi arah di luar 0..3 tidak menggeser apa pun.
     Dengan mask, arah 4 berubah jadi "ke atas". Karena alasan yang sama,
     cabang arah 3 di `updateCamera` harus `case 3`, **bukan** `default` —
     kalau tidak, arah tak sah ikut menggeser kamera.
   - **Diri sendiri tidak boleh menghalangi langkah.** Di tepi peta
     koordinat tujuan dijepit sehingga sama dengan posisi sekarang;
     tanpa cek `bl.id == sd.id` pemain tertahan di seluruh tepi peta.
     Di C ini dijaga oleh `bl->id != sd->bl.id` dalam `clif_canmove_sub`.
   - **Ladang tipe pada paket 0x0A little-endian.** C menulisnya dengan
     `WFIFOW` tanpa `SWAP16`, berbeda dari ladang lain di protokol klien.
     Ditiru apa adanya — jangan "diseragamkan" jadi big-endian.

   Juga faithful-port yang sengaja dipertahankan: rantai pesan penolakan
   masuk peta di `entryRejection` punya tiga cabang terakhir yang **tidak
   pernah tercapai** (sama seperti C), sehingga kekurangan mark/path pun
   dilaporkan sebagai kekurangan level.

14. **Petak portal yang terdaftar ganda — urutan menentukan tujuan.**
   Data asli punya **61 petak** yang muncul lebih dari sekali di tabel
   `Warps`, dan **26 di antaranya bertujuan berbeda**. Versi C menyimpan
   portal dalam linked list yang disisipi **di depan**
   (`war->next = head; head = war`) lalu mengambil kecocokan pertama —
   artinya **baris terakhir yang menang**. Karena itu
   `MapData.warpAt()` menelusuri daftarnya **dari belakang**; menelusuri
   maju mengirim pemain ke tujuan yang salah pada 26 petak. Konsekuensinya
   `MapRegistry.loadWarps()` sengaja **tanpa `ORDER BY`**, sama seperti C:
   urutan alami baris ikut jadi bagian perilaku. Ditemukan saat A3 dengan
   database sungguhan — `cliftest` tidak menangkapnya karena portal di sana
   dibuat sendiri oleh ujinya.

15. **`luascript/` TIDAK lagi byte-identik dengan `rtklua/` upstream.**
   Sejak 21 Agustus 2026 ada 13 berkas yang diperbaiki (salah ketik yang
   dijamin melempar error, plus kunci tabel ganda). Setiap perubahan
   tercatat di [`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md) —
   **baca dulu sebelum menyalin ulang konten dari upstream**, kalau tidak
   perbaikannya hilang diam-diam. Jalankan `./run.sh luaaudit` setelah
   menyegarkan konten.

16. **Skrip aslinya Lua 5.1, LuaJ mengikuti 5.2 — butuh shim.**
   `ScriptEngine` memasang `unpack = unpack or table.unpack`,
   `loadstring = loadstring or load`, dan memuat `DebugLib` (yang tidak ikut
   `JsePlatform.standardGlobals()`). Tanpa shim `loadstring`, **toko NPC**
   (`checkShop.lua`) dan daftar syarat mantra langsung error — ini sempat
   terlewat karena skripnya tetap *termuat* tanpa masalah; yang gagal hanya
   saat barisnya dijalankan. Kalau menemukan fungsi 5.1 lain yang dipakai
   skrip, tambahkan shim di tempat yang sama.

17. **Panjang paket klien: `setPacketIndexes` MENAMBAH 3 ke ladang panjang.**
   Yang ditulis kode bukan yang dikirim: `set_packet_indexes()` menimpa
   ladang [1..2] dengan nilai+3 lalu menempelkan 3 byte indeks kunci, jadi
   total byte = ladang_akhir + 3 = ditulis + 6. Jebakan ini sudah **dua
   kali** membuat ekspektasi uji salah (pertama di `sendId`, lalu di
   `sendStatus`/`sendNpcLook`) — kodenya benar, ujinya yang keliru. Kalau
   menulis uji panjang paket, ukur dulu nilai sebenarnya, jangan dihitung
   di kepala.

   Terkait: paket gambar NPC memakai ladang `len + 60` sedangkan paket
   pemain `len + 60 + 3` untuk tata letak yang sama. Selisih 3 byte itu ada
   di versi C dan **sengaja ditiru**; jangan diseragamkan tanpa menguji
   dengan klien sungguhan.

18. **Nilai acuan uji harus diambil dari kode C yang UTUH.** Saat memverifikasi
   `nexCRCC`, nilai acuan sempat diambil dengan menyalin tabel CRC dari
   `clif.c` lewat rentang baris — dan rentangnya **kurang 3 nilai**, jadi
   entri 253..255 jadi 0. Akibatnya uji melaporkan port Java salah padahal
   port-nya benar; yang salah acuannya. Kalau membuat program acuan dari
   sumber C, **hitung dulu jumlah elemennya** (tabel ini 256) sebelum
   percaya hasilnya.

19. **Berkas data teks: hanya `level_db.txt` dan `guide_db.txt` yang nyata.**
   Header C menyebut 8 konstanta `*_FILE` (item_db, mob_db, class_db, ...),
   tapi enam di antaranya **peninggalan** — pembacanya sudah memakai MySQL.
   Yang benar-benar masih berupa berkas hanya dua itu, dan keduanya sudah
   disalin ke `db/` (path lewat `db.path` / `db_path`). Jangan mencari
   berkas yang lain; tidak ada, bahkan di upstream.

20. **Buffer tulis yang tumbuh harus menyalin SELURUH isinya.**
   `Session.ensureW()` sempat menyalin hanya `wdataSize` byte — padahal
   byte yang sedang disusun berada **di belakang** `wdataSize` (di
   `wdataSize + pos`), sehingga setiap kali buffer tumbuh semua yang sudah
   ditulis **terbuang diam-diam**. Akibatnya paket daftar peta 19.708 byte
   terkirim sebagai 19.708 byte **nol**, char server membacanya sebagai
   opcode `0x0000` lalu menutup tautan — map↔char putus-nyambung tiap 10
   detik sejak awal.
   ⚠️ **Tidak satu pun dari 6 gerbang regresi menangkapnya**, karena semua
   paket yang diuji lebih kecil dari buffer awal 4 KB. Baru ketahuan saat
   ketiga server benar-benar dijalankan. Sekarang dijaga uji khusus di
   `cliftest` (paket 20.000 byte, isi diperiksa byte per byte).

21. **Kait skrip NPC dan mob WAJIB menerima objeknya sebagai argumen.**
   C selalu mengirim benda itu sendiri:
   `sl_doscript_blargs(nd->name, "move", 2, &nd->bl, &tsd->bl)` — atau
   `1, &nd->bl` bila tanpa pemilik; mob mengirim mob + sasaran. Skrip
   memakainya langsung (`function(npc) ... npc.side ...`), jadi memanggil
   tanpa argumen membuat **setiap kait timer** gagal dengan "attempt to
   index nil". Ini juga baru ketahuan saat server dijalankan sungguhan.

22. **`pcl_setattr` di C tidak punya setter posisi — jangan tambahkan.**
   C menyediakan **164** atribut pemain yang bisa ditulis skrip, dan
   `x`/`y`/`m` **bukan** salah satunya. Port ini sempat menambahkannya
   sendiri, dan itu salah: menulis koordinat langsung memindahkan pemain
   **tanpa memperbarui indeks blok peta**, sehingga ia hilang dari pandangan
   pemain lain dan tabrakan jadi kacau. Setter itu sudah dihapus. Skrip
   memindahkan pemain lewat method `warp()`, yang melewati
   `Pc.warp` → `delBlock`/`addBlock`. Kalau menambah setter atribut baru,
   cocokkan dulu dengan daftar di `pcl_setattr`.

23. ~~**Binding barang memakai inventaris tiruan.**~~ **DIBERESKAN
   21 Agustus 2026.** `addItem`/`removeItem`/`hasItem` sekarang menembus ke
   `CharStatus.inventory` lewat `ScriptPlayer.Owner` — barang yang diberikan
   skrip ikut tersimpan. Terbukti di `dbtest`: Lua memberi barang → `save` →
   baris muncul di tabel `Inventory`.
   Aturan yang ikut diport: barang bertumpuk digabung sampai batas
   `ItmStackAmount` lalu membuka slot baru; barang tak bertumpuk selalu satu
   slot; melebihi `ChaMaximumInventory` membuat `addItem` mengembalikan
   **false** — kegagalan dilaporkan, bukan didiamkan.
   `ItemDb` kini juga memuat `ItmIdentifier` (→ `idOf()`, padanan
   `itemdb_id`, tidak peka besar-kecil), harga beli/jual, dan batas tumpuk.
   Peta `ScriptPlayer.items` tinggal dipakai pemain tiruan di uji.

   **Emas** kini juga tersambung, lewat jembatan atribut umum
   (`ScriptPlayer.Owner.scriptGetAttr`/`scriptSetAttr`) — bukan method
   `addGold` buatan sendiri, karena di C emas memang diubah lewat atribut
   `money` di `pcl_setattr`. Atribut yang sudah tersambung: `money`,
   `bankMoney`, `maxInv`, `health`, `magic`, `baseHealth`, `baseMagic`,
   `exp`, `mark`, `totem`, `tier`, `country`, `side`, `state`, `maxSlots`.
   Menambah atribut baru cukup di satu tempat (`User.scriptGetAttr`/
   `scriptSetAttr`) — cocokkan namanya dengan daftar `pcl_setattr`.

   ⚠️ Masih belum: pencocokan atribut lanjutan pada `hasItem` (engrave,
   `customLook`, dura, owner) yang di C ikut diperiksa — port ini baru
   mencocokkan id barang.

24. **Jangan biarkan `extLib/*.jar` ter-gitignore.** `.gitignore` berbasis
   template Java GitHub mengabaikan `*.jar`; project ini sengaja tanpa
   Maven sehingga jar HARUS ikut ter-commit — baris `!extLib/*.jar` wajib
   ada. Tanpa itu, clone di server CentOS menghasilkan `extLib/` kosong
   dan build gagal. Verifikasi: `git check-ignore -v extLib/*.jar` harus
   tidak menghasilkan apa pun.

25. **Daftar stub di `registerGlobals()` berjalan TERAKHIR — ia menimpa
   binding yang baru diport.** Loop `for (String name : stubs)` dieksekusi
   setelah semua `set(...)`, jadi memport `getMapXMax` tanpa menghapus
   namanya dari array `stubs` membuatnya **tetap** mengembalikan nil.
   Gejalanya menyesatkan: error di skrip bergeser dari "attempt to call
   nil" menjadi "compare nil and number", seolah binding-nya sudah jalan
   tapi datanya salah. Sekarang loop itu **melewati nama yang sudah
   terdaftar** dan menulis WARN — jangan hapus penjaganya. Kejadian
   24 Agustus 2026 saat memport `getMapXMax`/`getMapYMax`/`getMapRegistry`.

26. **`map.log` dari server yang benar-benar berjalan adalah alat uji, dan
   yang paling tajam sejauh ini.** Tiga gerbang regresi + audit statis
   melaporkan semuanya hijau, sedangkan `map.log` menampilkan **21 error
   skrip unik** dari kait timer NPC yang hanya menyala saat server hidup.
   Setelah semuanya ditutup, log turun ke **0 ERROR / 0 WARN** — jadikan
   itu patokan: setelah menyentuh binding atau kait skrip, jalankan
   `./run.sh all`, tunggu ~50 detik, lalu
   `grep -c ERROR logs/map.log` harus 0.
   Deduplikasi error di `ScriptEngine.reportScriptError` memang dirancang
   supaya log tetap terbaca; jangan matikan.

27. **`LibFunction` LuaJ punya field `protected String name` — JANGAN
   memakai variabel bernama `name` di dalam subclass anonimnya.** Di dalam
   `new VarArgFunction() { ... }`, identifier `name` merujuk **field
   warisan itu**, bukan variabel lokal/parameter yang mengelilinginya;
   javac tidak membuat penangkapnya dan tidak memberi peringatan apa pun.
   Akibatnya di `ScriptEngine.stub()`: pesan log selalu berbunyi
   `null()` sehingga binding yang hilang **tidak bisa dikenali**, dan yang
   lebih buruk `warnedStubs.add(null)` berhasil sekali saja sehingga
   **hanya global pertama yang pernah dilaporkan** — sisanya gagal
   diam-diam. Setelah diperbaiki (parameter dinamai `namaBinding`),
   binding yang hilang langsung tampil dengan namanya: `getWarp()`.
   Ditemukan 24 Agustus 2026 dengan membongkar bytecode-nya
   (`javap -p -c`), karena membaca sumbernya saja tidak menampakkan apa pun.

28. **`MapData.delBlock` menolak benda dari peta lain — jangan dilonggarkan.**
   Di C `map_delblock(bl)` selalu memakai `map[bl->m]`, jadi mustahil
   mencabut benda dari peta yang bukan tempatnya. Di sini `delBlock` adalah
   method instance, sehingga peta yang salah bisa dilewatkan; tanpa penjaga
   `bl.m != id` bendanya **tidak** tercabut dari daftar peta aslinya padahal
   `onMap` sudah dimatikan — dan `addBlock` berikutnya mendaftarkannya
   **untuk kedua kalinya**. Efeknya setiap siaran ke area terkirim ganda dan
   tiap sapuan menghitung bendanya dua kali. Lolos dari 294 assertion
   `cliftest`; ketahuan 26 Agustus 2026 hanya karena nomor urut paket naik
   dua per kiriman.

29. **Nomor urut paket [4] hanya ada pada sebagian paket.** `WFIFOHEADER()`
   di C menaikkan `session->increment` lalu menaruhnya di ladang [4]; paket
   yang dibangun dengan `WBUF` atau `WFIFOHEAD` biasa meninggalkannya 0
   (buffernya `CALLOC`). Di Java pilih `Clif.head()` atau `Clif.headSeq()`
   menurut makro yang dipakai fungsi C-nya — **jangan diseragamkan**.
   Yang memakai `WFIFOHEADER`: 0x07, 0x0C (hanya `clif_mob_move`), 0x0D
   (`clif_speak`, **bukan** `pcl_talkself`), 0x13
   (`clif_send_mob_health_sub`, **bukan** `clif_send_pc_healthscript`),
   0x1F, 0x37, 0x3A, 0x3F, 0x51. Dua pasangan itu mudah tertukar karena
   opcodenya sama persis; bedanya cuma makro pembangunnya.

30. **Angka `luaaudit` tidak turun saat binding STUB diport.** Stub sudah
   terdaftar di prototipe, jadi bagi audit namanya "terdefinisi" — memport
   `sendAction` yang dipakai 905x tidak menggerakkan angkanya sedikit pun.
   Karena itu jangan memakai angka audit sebagai ukuran kemajuan tanpa
   melihat daftar stub di `Bindings.java` (`registerGlobals()`,
   `definePlayer()`, `defineBlockList()`). Kebalikannya juga berlaku:
   binding yang belum ada sama sekali baru terlihat setelah dipanggil.

31. **Barang di lantai punya DUA aturan penggabungan yang berbeda.** Yang
   dipakai tergantung siapa yang menjatuhkan, dan menyamakannya salah:
   - **Jatuhan mob/skrip** (`mob_addtocurrent`, lewat `dropItem`) menggabung
     bila **id barangnya sama** — itu saja.
   - **Jatuhan pemain dari inventaris** (`pc_addtocurrent`, lewat
     `forceDrop`) mensyaratkan **keduanya utuh** (dura penuh) **plus sepuluh
     atribut sama persis**: id, pemilik, nama asli, ikon & wujud kustom
     beserta warnanya, catatan, `custom`, dan `protected`. Ia juga
     **mengosongkan daftar `looters`**.

   Akibatnya pedang penyok tidak pernah menyatu dengan pedang mulus, dan
   barang berukir (`realName` terisi) tetap terpisah. Kalau aturan longgar
   dipakai untuk keduanya, barang unik pemain akan saling melebur diam-diam.

32. **Tiga jebakan penamaan pada barang lantai.**
   - **Barang ber-id 0 adalah UANG**, bukan barang. `pc_getitemscript`
     menambahkannya ke emas pemain lalu membuang bendanya.
   - **Kolom `ItmDroppable` artinya kebalikan namanya**: nilai bukan-nol
     berarti barangnya **tidak bisa dipungut** pemain biasa. Dibaca lewat
     `ItemDb.cannotBePickedUp()` supaya namanya tidak menyesatkan lagi.
   - **`throw` bukan bagian dari barang lantai sama sekali** — isinya murni
     animasi (0x16), dan ladang [12]-nya sengaja nol supaya klien tidak
     meninggalkan gambar di tanah. Yang benar-benar menjatuhkan barang
     adalah `dropItemXY`, yang biasanya dipanggil skrip tepat sesudahnya.

33. **Jebakan disaring di DUA tempat, dengan aturan berbeda.**
   - **Pencarian benda** (`map_foreachincell`) membuang **seluruh** barang
     bertipe `ITM_TRAPS`, tanpa melihat `trapsTable`. Itulah satu-satunya
     beda `getObjectsInCell` dan `getObjectsInCellWithTraps`. Sapuan area
     (`map_foreachinarea`) **tidak** punya penyaring ini sama sekali.
   - **Penggambaran** (`clif_object_look_sub`) memakai `trapsTable`:
     jebakan hanya tergambar untuk pemain yang sudah menemukannya.

   Menyamakan keduanya membuat jebakan terlihat oleh semua orang, atau tidak
   pernah bisa ditemukan sama sekali.

34. **Ikon barang lantai dikirim MENTAH; hanya ikon KUSTOM yang +49152.**
   Benda lain (mob, NPC) selalu memakai penambah 32768, jadi menyeragamkan
   ketiganya adalah kesalahan yang wajar tapi tetap salah. Lihat cabang
   BL_ITEM di `Clif.objectLook`.

35. **Tiga varian gerak mob yang TIDAK boleh diseragamkan.** Di C ketiganya
   berdiri dari badan yang sama, tetapi masing-masing membuang bagian yang
   berbeda — dan namanya tidak membantu:
   - **`moveGhost`** (`moveghost_mob`) — langkah penuh. Mob yang sedang
     mengejar (`target != 0`) **menembus penghalang**.
   - **`moveIgnoreObject`** (`move_mob_ignore_object`) — salinan utuh
     `moveghost_mob` dengan satu blok **dikomentari**: cek tembok dan benda.
     Jadi ia menembus segalanya, **tetapi portal tetap menghentikannya**,
     karena penjaga portal ada di atas blok yang dikomentari itu.
   - **`checkMove`** (`mobl_checkmove`) — hanya bertanya, tidak melangkah.
     Petak tujuannya dihitung sederhana (±1), tanpa perhitungan jalur
     pandang. ⚠️ **Pengecualian `target` TIDAK berlaku di sini**, jadi
     jawabannya bisa berbeda dari apa yang benar-benar terjadi kalau
     `moveGhost` dipanggil. Itu di C, bukan kelalaian.

   Dan yang paling menyesatkan: **`moveIntent` tidak pernah memindahkan mob
   sama sekali.** Seluruh badan yang menggerakkannya dikomentari di sumber
   C; yang tersisa hanya "kalau sasaran bersebelahan, putar menghadapnya dan
   kembalikan 1". Skrip memakainya sebagai "sudah cukup dekat untuk
   menyerang?", bukan sebagai perintah jalan.

   Catatan kecil: `mob->canmove` **namanya terbalik dari artinya** —
   nilai 1 berarti TIDAK boleh melangkah.

36. **Paket inventaris 0x0F membawa DUA string dengan peran berbeda.**
   Yang pertama adalah teks yang **dilihat pemain**: nama ukiran bila ada,
   plus hiasan menurut jenis barangnya — `"Roti (5)"` bila jumlahnya lebih
   dari satu, `"Obor [12 jam]"` untuk ITM_SMOKE, `"[T3] Peta"` untuk
   ITM_MAP, `"Tas [40]"` untuk ITM_BAG dan ITM_QUIVER. Yang kedua adalah
   nama **jenis**-nya apa adanya, dipakai klien mencocokkan gambar dan
   tooltip. Menyamakan keduanya membuat barang berukir kehilangan gambarnya.

   Dua hal lagi di paket yang sama:
   - **Ketahanan hanya dikirim untuk jenis 3..17** (perlengkapan). Jenis
     lain memakai ladang yang sama untuk penanda "bertumpuk".
   - **Perlindungan yang menang adalah yang TERBESAR** antara milik
     barangnya dan bawaan jenisnya — di C dua baris `if` yang saling
     menimpa, bukan satu pilihan.

37. **`clif_sendadditem` MENGHAPUS barang rusak, bukan melewatinya.**
   Barang ber-id di bawah 4, dan barang yang tidak ada di tabel `Items`,
   dikosongkan dari inventaris pemain saat paketnya hendak dikirim. Itu
   penyapu data rusak di C dan ditiru apa adanya — kalau tidak, barang
   hantu menumpuk di slot yang tidak bisa dipakai.

   ⚠️ **Akibatnya penyapuan inventaris harus dari BELAKANG.** Menyapu maju
   akan melewati satu slot setiap kali sebuah barang terbuang, karena
   daftarnya menyusut di tengah perulangan. Lihat `Bindings.kirimInventaris`.

   Terkait: **`clif_senddelitem` (0x10) juga mengosongkan slotnya di
   server**, bukan sekadar memberi tahu klien. Skrip mengandalkan itu.

38. **Daftar mantra dikembalikan BERSELANG-SELING, bukan berpasangan.**
   `getUnknownSpells` dan `getAllClassSpells` mengisi tabel Lua dengan
   indeks 1 = nama tampilan, 2 = nama skrip, 3 = nama tampilan, 4 = nama
   skrip, dan seterusnya. Bentuk yang tidak biasa ini ada di C
   (`x += 2` di kedua fungsi) dan skrip mengandalkannya — mengubahnya jadi
   tabel pasangan akan memutus daftar mantra di menu.

   Dua hal lagi di keluarga yang sama:
   - **Delapan belas id mantra bukan mantra**, melainkan penanda bagian di
     buku mantra (0, 100, 200, …, 7000, 10000). Daftar itu **tidak ada di
     database** — di C ia deretan `case ... continue;`, jadi tidak bisa
     disaring lewat SQL. Menyertakannya membuat baris kosong muncul di
     daftar pilihan pemain. Lihat `SpellDb.isSectionMarker`.
   - `getAllClassSpells` punya penjaga `if (found == 0)` yang **mati** di C
     ({@code found} tak pernah disetel di fungsi itu), sehingga mantra yang
     sudah dikuasai pemain **ikut** terdaftar. Hanya `getUnknownSpells`
     yang benar-benar menyaringnya. Ditiru apa adanya.

39. **Satu opcode, dua arti — 0x58 dan 0x04.**
   - **0x58** membersihkan sisa gambar lama bila ladang [5] = 0
     (`clif_destroyold`), dan menampilkan teks besar di tengah layar bila
     [5] = 6 (`clif_guitextsd`). Dua fungsi yang sama sekali berbeda.
   - **0x04** dipakai `clif_sendxy` (posisi pemain) <b>dan</b>
     `clif_sendxychange` (geser kamera saja). Tata letaknya sama; bedanya
     yang pertama menyertai perpindahan, yang kedua tidak.

   Terkait, dua jebakan kecil di keluarga yang sama:
   - **Ladang panjang `clif_guitextsd` menghitung 3 byte indeks kunci dua
     kali** ({@code 8 + panjang + 3}, padahal `setPacketIndexes` menambahkan
     3 lagi sesudahnya). Ada di C; jangan "dirapikan". Lihat Peringatan #17.
   - **Offset kamera di tepi peta DIGESER satu, bukan dijepit** ke batas —
     {@code dx--} / {@code dx++}, bukan {@code dx = batas}.

40. **`lock` mengirim 0 dan `unlock` mengirim 1.** Terbalik dari namanya,
   dan keduanya memakai paket yang sama (`clif_blockmovement`, 0x51).
   Menukarnya membuat pemain terkunci persis ketika skrip bermaksud
   melepasnya.

   Terkait: **`speak` (`clif_sendscriptsay`) bukan `talk` (`clif_speak`)**,
   walau keduanya opcode 0x0D. Yang ini <b>menyisipkan nama pemain</b> di
   depan pesannya, dan ragam 1 mengubah tiga hal sekaligus: pemisahnya
   {@code '!'} bukan {@code ':'}, dan siarannya ke seluruh peta, bukan area.

41. **Menyimpan pemain yang sedang di dunia WAJIB menyegarkan posisinya
   dulu.** `intif_save()` menyalin `bl.x/y/m` ke `status.last_pos` dan
   samaran ke `status.disguise*` <b>tepat sebelum</b> blobnya disusun.
   Tanpa itu yang tersimpan adalah posisi saat pemain <b>masuk</b>, dan
   seluruh perjalanannya hilang. Di Java penyegaran itu sekarang ada di
   dalam `MapIntif.saveChar(User, quit)` supaya tidak bisa terlewat lagi;
   ragam `CharStatus` yang lama hanya untuk karakter yang tidak sedang di
   dunia.

   ⚠️ Ragam `quit` punya satu cabang tambahan: bila **peta tujuan pemain
   tidak dimuat di server ini**, yang disimpan adalah **peta tujuan**, bukan
   posisinya sekarang. Itu jalur perpindahan antar-map-server (Trek C3) —
   pemain sudah "berangkat" secara logika meski raganya masih berdiri di
   peta lama. Menghapusnya akan memulangkan pemain ke peta yang salah
   begitu C3 hidup.

## Konfigurasi (urutan prioritas)

1. `resources/rtk-server.properties` — default teknis (crypt key, port,
   lockout, pool HikariCP, buffer, `lua.path`). Dibaca `common/Props.java`
   dari classpath. Getter selalu punya fallback — key hilang tidak fatal.
2. `conf/*.conf` — format C asli, dibaca saat start dan MENIMPA nilai yang
   sama (login_port, char_port, map_port, lua_path, kredensial SQL, dll.).

`crypt.enckey` ("Urk#nI7ni") dan `crypt.handshake_key` ("KruIn7inc")
adalah bagian dari protokol klien — jangan ubah nilainya.

## Peta arsitektur (C → Java)

| C | Java | Catatan |
|---|---|---|
| `common/socket.c` + macro RFIFO/WFIFO | `common/NetServer` + `common/Session` | **instance per server**; IO thread + ArrayBlockingQueue -> logic thread |
| `common/core.c` | `common/Core` | **instance per server**; logic thread yang mengonsumsi antrean paket |
| `common/crypt.c` | `common/Crypt` | XOR 3-tahap; `setPacketIndexes` menulis 3 byte key di ekor |
| `common/timer.c` | `common/TimerSystem` | **instance per server**; interval<=0 = one-shot; return<0 = batal reschedule |
| `common/db_mysql.c` | `common/Sql` | HikariCP pool + PreparedStatement (bukan interpolasi string) |
| `login/*` | `org.rtk.login` | port penuh |
| `char/*` | `org.rtk.charserver` | blob mmo_charstatus (0x3003/0x3004) belum |
| `map/map.c,intif.c` | `org.rtk.map` | handshake, routing login, dispatcher paket klien, simpan saat keluar |
| `map/sl.c` | `org.rtk.map.script` | lihat bawah |
| `common/mmo.h` (struct mmo_charstatus) | `org.rtk.common.mmo` | model + codec; **format sendiri**, bukan dump struct C (lihat Peringatan #10) |
| `char_db.c` mmo_char_fromdb/todb | `charserver/CharPersistence` | muat/simpan 11 tabel MySQL |
| `map/map.h` block_list, map_data | `map/data/BlockList`, `MapData`, `MapRegistry` | geometri + metadata + indeks spasial blok 8x8 |
| `map/pc.c` (penempatan) | `map/User`, `map/Pc` | USER runtime, pc_setpos/warp/enterWorld |
| `map/clif.c` (paket klien) | `map/Clif` | paket keluar + `parseWalk`; big-endian, dua jalur kunci |
| `map/npc.c` (pemuat `Warps`) | `map/data/MapRegistry.loadWarps` | portal, diindeks per blok 8x8 |
| `map/npc.c` `npc_init()` | `map/Npc`, `map/NpcRegistry` | 385 NPC + indeks id (`map_id2bl`) + perlengkapan |
| `map/class_db.c` `leveldb_read` | `map/data/ClassDb` | tabel pengalaman per path, dari `db/level_db.txt` |
| `map/itemdb.c` `itemdb_look` | `map/data/ItemDb` | tampilan barang dari tabel `Items` |
| `map/clif.c` `nexCRCC` + `clif_sendmapdata` | `map/Clif.nexCrc`, `Clif.sendMapData` | checksum petak peta; cocok = tidak dikirim ulang |
| `map/pc.c` `bl_duratimer` + `sl.c` `pcl_setduration` dkk. | `map/Durations` | durasi & aether mantra; tik 1 detik, kait `while_cast`/`uncast` |
| `map/map.h` `flooritem_data` + `map_additem`/`map_delitem` | `map/FloorItem`, `map/FloorItemRegistry` | barang di lantai (BL_ITEM); dua aturan gabung, penyaring jebakan |

## Scripting engine (org.rtk.map.script)

- `ScriptEngine` = `sl.c`: load `Developers/sys.lua` → semua `Accepted/` +
  `Developers/` (skip sys.lua). Status: **906/906 file, 0 error** — jaga
  angka ini.
- Object model `typel`: `__index` resolusi getter Java → prototype → data
  table instance (urutan persis `typel_mtindex`). Prototype `Player` adalah
  global Lua — `Accepted/player.lua` menambah method level tinggi
  (`menuString`, `dialogSeq`, banking) DI ATAS primitif Java.
- **Implement binding di lapisan primitif**, bukan level tinggi:
  `menu` (resume = indeks angka), `dialog` (resume = "next"/"previous"/
  "quit"), `input`, `menuSeq`, `inputSeq` — semuanya yield coroutine
  (`ScriptEngine.yieldBlocking`) dan engine me-resume lewat
  `ScriptEngine.resume(player, jawaban)`.
- Binding yang belum ada = **stub warn-once** (global) atau error
  "attempt to call nil" yang tertangkap dispatcher (method) — jangan buat
  loader crash. Angka terkini ada di bawah — jangan hafalkan, jalankan
  `./run.sh luaaudit` karena alat itu menghitungnya ulang dari kode yang
  sedang berlaku.
- Referensi kebenaran semantik binding: `RTK-Server/rtk/src/map/sl.c`
  (cari `pcl_*`, `bll_*`, `typel_extendproto`, `lua_register`).

## Status & roadmap

### Tahap 1 — SELESAI (19 Agustus 2026)

Fondasi server-side sudah berdiri dan terverifikasi:

- **Login server**: port penuh (login, buat karakter, ganti password, meta
  file, maintenance mode, banned IP, brute-force lockout, redirect ke map).
- **Char server**: handshake login & map, autentikasi karakter, routing map.
- **Map server**: handshake + routing pemain jalan ujung-ke-ujung.
- **Scripting engine**: 906/906 file rtklua asli termuat 0 error; dialog
  coroutine teruji lewat `Accepted/player.lua` asli.
- **Infrastruktur**: HikariCP, Log4j2 (rolling harian, retensi 30 hari),
  konfigurasi di `rtk-server.properties`, build NetBeans + `build.sh`,
  deploy `run.sh` (`java -jar <jar> <server> &`).
- **Arsitektur jaringan**: instance per server + IO thread /
  ArrayBlockingQueue / logic thread (lihat Peringatan #8).

### Tahap 2 — TREK A SELESAI (21 Agustus 2026)

Server kini bisa dimainkan ujung-ke-ujung dari sisi logika: pemain masuk
dunia dan melihat sekelilingnya, berjalan, melewati portal, bicara dengan
NPC, berbelanja, dan bertarung dengan mob. Rinciannya di A1–A5 di bawah.

**Yang membentuk Trek A jauh lebih ringan dari perkiraan:** semakin dalam,
semakin jelas banyak hal yang tampak "pekerjaan server" ternyata **logika
Lua** — perhitungan kerusakan, jatuhan barang, bank, reparasi, semuanya
skrip. Tugas port-nya menyediakan jalur yang benar (atribut, pencarian
objek, kait skrip), bukan menulis ulang aturan main. Itu sebabnya `mob.c`
yang 2.411 baris tidak perlu diport seluruhnya.

### Putaran perbaikan dari server hidup (24 Agustus 2026)

Menjalankan ketiga server dan membaca `map.log` menutup **21 error skrip
unik** menjadi **0 ERROR / 0 WARN**. Yang gagal semuanya kait timer NPC —
kode yang hanya berjalan ketika server benar-benar hidup, sehingga tidak
satu pun dari 6 gerbang regresi maupun `luaaudit` menyentuhnya. Rincian
binding yang diport ada di "Angka acuan binding skrip" di bawah;
pelajaran prosesnya di Peringatan #25 dan #26.

Ikut dibereskan di sisi konten: `bladestorm_trap.lua` memanggil
`player:sendStatus()` di luar penjaga nil-nya (lihat
`luascript/PERUBAHAN.md` butir 8).

⚠️ **Seluruh Trek A masih diverifikasi secara offline.** Paket dibangun,
didekripsi balik, dan diperiksa per-offset — tapi **belum satu pun pernah
dibaca klien RetroTK asli**. Selama sesi pengerjaannya, tiga kali uji lolos
padahal kodenya salah (opcode menu, tabel CRC, indeks id mob), dan
ketiganya baru ketahuan setelah dicocokkan ke sumber C atau saat uji lain
ikut rusak. Uji buatan sendiri tidak bisa menggantikan klien nyata.

### STATUS TERAKHIR — 26 Agustus 2026 (sore)

Titik berangkat untuk sesi berikutnya. **Baca ini dulu.**

| | |
|---|---|
| Arah | protokol diganti + klien libGDX sendiri (lihat bagian teratas) |
| Gerbang regresi | 6/6 hijau (`cliftest` **518**, `dbtest` **141** assertion) |
| Binding skrip | **45** belum diport (39 di `sl.c` + 6 salah ketik); global belum diport **0** |
| Binding yang masih **stub** | **tidak ada lagi yang nyata** — tinggal `sendSound` dan `updateStatus`, yang tidak ada di `sl.c` sama sekali |
| Klien RetroTK asli | **berhasil masuk dunia** — lalu perburuan dihentikan |
| Terjemahan Indonesia | kata kunci `speech` selesai; dialog ~3.800 titik belum |

#### Yang dikerjakan pada sesi ini

Dua blok, keduanya sudah lewat 6 gerbang + server hidup 0 ERROR / 0 WARN.

**1. Binding yang masih stub — kategori paling berbahaya.** Stub tidak
melempar error, hanya menulis WARN sekali lalu mengembalikan nil, jadi
`map.log` tetap bersih sementara skripnya "berhasil" tanpa efek apa pun.
Diport: `talk` (698x, `clif_speak` 0x0D — tanpa ini NPC tidak pernah
bersuara di layar), `sendAction` (905x, 0x1A), `playSound` (632x),
`updateState` (434x, 0x1D untuk pemain / 0x33 / 0x07), `delete` (109x),
`delFromIDDB`, `sendHealth`, `refresh` (90x), `removeItemSlot` (27x),
`updatePath` (25x), `updateCountry`.

**2. Subsistem durasi & aether mantra** (`map/Durations.java`) —
`setDuration` (423x) plus 15 binding sekeluarga dan tik satu detik
`bl_duratimer()`. Ini **logika permainan, bukan protokol**, jadi tetap
terpakai setelah protokol diganti.

**3. `moveGhost` (84x) dan `spawn` (381x)** — dua penghambat terbesar yang
tersisa. `moveGhost` (`moveghost_mob`, mob.c:1518) adalah cara hampir
seluruh AI mob bergerak; `spawn` (`mobspawn_onetime`) melahirkan jebakan,
mob event, dan boss instance. Ikut lahir: paket benda 0x07 **berkelompok**
dan `clif_mob_move` (0x0C per-sesi).

Dengan itu **daftar stub habis** — tidak ada lagi binding yang "berhasil"
tanpa efek. Yang tersisa semuanya binding yang memang belum ada, dan
memanggilnya melempar error yang terlihat di `map.log`.

**9. `forceSave` (15x)** — `intif_save()`. `MapIntif.saveChar` kini punya
ragam yang menerima `User` dan menyegarkan posisi & samaran dari objek
hidupnya lebih dulu; jalur keluar-dunia ikut memakainya.

**8. Tampilan & timer** — `changeView` (22x), `guitext` (12x), `setTimer`
(11x), `selfAnimation`/`selfAnimationXY` (19x), `paperpopup` (7x),
`speak` (6x), `sendURL` (4x), `lock`/`unlock` (12x). Paket baru: 0x04
(geser kamera), 0x58 (dua ragam), 0x35, 0x66, 0x67, dan 0x0D bernama.

**7. Buku mantra** — `getSpells` (7x), `getSpellName` (4x),
`getSpellYName` (3x), `getSpellNameFromYName` (4x), `getUnknownSpells`
(4x), `getAllClassSpells`, plus `addHealth` (4x). Ikut dibereskan: empat
berkas data (`ItemDb`, `SpellDb`, `NpcRegistry`, `MobRegistry`) masuk ke
audit SQL `dbtest` — sebelumnya kueri mereka **tidak pernah** divalidasi
skema hidup, sehingga kolom yang baru ditambahkan lolos tanpa diperiksa.

**6. Inventaris & perlengkapan** — paket 0x0F (isi slot) dan 0x10
(kosongkan slot), plus `updateInv` (24x), `refreshInventory`, `hasEquipped`
(6x), `hasItemDura`, `deductDura`, `deductDuraInv`, `deductArmor`,
`deductWeapon`. Ikut lahir: `MapServer.charName()` (port `map_id2name`) dan
kolom `ItmText`/`ItmProtected` di `ItemDb`.

**5. Gerak mob lanjutan** — `moveIntent` (11x), `checkMove` (9x),
`moveIgnoreObject` (3x). Ketiganya varian dari mesin `moveGhost` yang sudah
berdiri, jadi murah; tapi ketiganya punya perbedaan halus yang mudah
diseragamkan secara keliru (Peringatan #35).

**4. BL_ITEM — barang di lantai** (`map/FloorItem`, `map/FloorItemRegistry`).
Subsistem besar terakhir yang belum ada sama sekali. Membuka `dropItemXY`
(37x), `throw` (23x), `dropItem` (18x), `pickUp` (12x), `forceDrop`,
`addTrapSpotters`/`getTrapSpotters` — ~95 titik panggilan — dan membuat
`getObjectsInCellWithTraps` akhirnya berbeda dari varian biasa.

⚠️ **Angka luaaudit tidak turun sebanyak pekerjaannya.** Binding yang
diport dari keadaan *stub* **tidak pernah terhitung** di audit — bagi
audit ia sudah "terdefinisi". Jadi 89 → 79 mengecilkan apa yang berubah;
yang benar-benar bergerak ada di kolom "masih stub" di atas.

#### Jebakan baru yang ditemukan sesi ini

- **`MapData.delBlock` pada peta yang SALAH** diam-diam menyetel
  `onMap = false` tanpa mencabut bendanya, sehingga `addBlock` berikutnya
  mendaftarkannya **untuk kedua kalinya** — tiap siaran ke area lalu
  terkirim ganda. Di C mustahil: `map_delblock(bl)` selalu memakai
  `map[bl->m]`. Lolos dari 294 assertion `cliftest`; ketahuan hanya karena
  nomor urut paket naik **dua** per kiriman. Sekarang dijaga:
  `delBlock` menolak bila `bl.m != id`.
- **Nomor urut paket ada di ladang [4], tapi hanya pada sebagian paket.**
  `WFIFOHEADER()` di C menaikkan `session->increment` dan menaruhnya di
  sana; paket yang dibangun dengan `WBUF`/`WFIFOHEAD` biasa meninggalkan
  [4] bernilai 0. Di Java pembagiannya jadi `Clif.head()` vs
  `Clif.headSeq()` — **jangan diseragamkan**. Yang memakai: 0x07, 0x0C
  (`clif_mob_move` saja), 0x0D (`clif_speak`, bukan `pcl_talkself`), 0x13
  (`clif_send_mob_health_sub`, bukan `..._healthscript`), 0x1F, 0x37,
  0x3A, 0x3F, 0x51.
- **0x33 dan 0x1D isinya identik, bergeser 5 byte** — 0x33 membawa
  x/y/side lebih dulu. Karena itu badannya dipisah jadi
  `Clif.charBody(base)`. **Kecuali** `state == 4`: hanya 0x1D yang punya
  tata letak pendek untuk itu.
- **`LuaAudit` mencetak "... dan N lagi" walau daftarnya tidak dipotong**,
  sehingga `-Drtk.audit.penuh=true` tetap mengabarkan 73 nama tersembunyi
  yang sebenarnya sudah tercetak semua. Sudah diperbaiki.

#### Prasyarat yang masih menggantung

⚠️ **Dua jalur baru belum pernah berjalan di server hidup, dan keduanya
butuh pemain sungguhan online:**

- **Tik durasi** — kait `while_cast` / `uncast` / `while_equipped` hanya
  menyala untuk pemain yang online; tanpa pemain, `duraTick()` menyapu
  daftar kosong.
- **`moveGhost`** — tik AI mob **melewati peta tanpa pemain**
  (`map.users == 0`), jadi tidak satu pun dari 1.175 mob bergerak saat
  server diuji sendirian.

Keduanya baru terbukti lewat `cliftest`. Peringatan #26 lahir persis dari
kait timer yang hanya menyala saat server hidup, jadi **ini yang paling
layak diperiksa lebih dulu** begitu ada klien yang bisa masuk.

Belum ikut diport bersama BL_ITEM: **`sd->pickuptype`** (setelan pemain
"pungut satu / pungut semua"); port ini berperilaku seperti nilai
bawaannya, 0. `pc_npc_drop` sengaja dilewati — di C isinya sudah kode mati.

**Pemain sungguhan masuk dunia untuk pertama kalinya** setelah empat bug
ditutup — semuanya lolos dari 294 assertion `cliftest`, dan semuanya
diverifikasi ke sumber C:

1. **`MapIntif.parseAuthAdd` memanggil `requestChar` dengan fd milik ruang
   char server.** Di C (`intif.c:394`) authadd hanya `auth_add` + ack
   `0x3002`; yang memanggil `intif_load` adalah `clif_accept2`
   (`clif.c:409`) dengan fd klien asli. Akibatnya `User` hantu terdaftar di
   `onlineChars[fd]`, sehingga paket perkenalan `0x10` klien tidak pernah
   sampai ke `clientAuth()` dan klien menggantung di layar loading.
2. **Paket perkenalan `0x10` tidak boleh didekripsi.** Cabang `!sd` di C
   return **sebelum** `decrypt()` (`clif.c:11304` vs `11359`) — kunci sesi
   diturunkan dari data karakter yang belum ada saat itu.
3. **`clif_mystaytus` (0x39) dan `clif_spawn` tidak pernah diport** padahal
   ada di `intif.c:229-230`. ⚠️ Ekspektasi lama di `ClifTest` (9 paket tanpa
   `0x39`) justru **mengunci** kelalaian ini — uji yang mengesahkan
   penyimpangan.
4. **`Session.wfifoSet` tidak membersihkan buffer.** C menulis tiap paket di
   offset **baru** pada buffer `CALLOC`, port ini memakai ulang offset yang
   sama — sehingga byte yang tidak ditulis mewarisi paket sebelumnya yang
   **sudah terenkripsi**, dan ikut terkirim karena beberapa paket
   mendeklarasikan panjang lebih besar dari ladang yang benar-benar ditulis
   (`0x1E` sisa 2 byte, `0x05` sisa 1, `0x15` sisa 5, `0x04` sisa 2).
   Efek samping yang baru ketahuan: byte `[4]` tiga paket berisi `03` warisan
   `sendTime`, padahal C mengirim `00`.

**Yang tidak terpecahkan (dan tidak perlu lagi):** klien crash dengan
`allocate_virtual_memory size a4270000` (2,75 GB). Sudah diaudit dan
semuanya **cocok persis dengan C**: keempat paket pertama, `sendStatus`, dan
seluruh rantai kripto (`populate_table`, `generate_key2`,
`set_packet_indexes`, kedua array `isKey`).

⚠️ **PELAJARAN METODE.** Tiga putaran bisect ternyata mengukur **crash Wine
Gecko**, bukan bug paket — semuanya melaporkan `0x40000015` di alamat yang
sama. Baru ketahuan setelah Wine Gecko 2.47.4 dipasang dan pesannya berubah.
**Kalau gejalanya identik persis di beberapa percobaan, curigai lingkungan
sebelum membangun analisis di atasnya.**

**Klien yang benar: `../client-nexia-750/`, bukan `Origin Nexia`.**
Origin Nexia terbukti dimodifikasi (3.089.920 vs 3.085.824 byte — selisih
tepat satu page; `Meta.dat` 58.299 vs 39.517). Installer 750 adalah **Inno
Setup 5.5.7**, diekstrak tanpa sudo:
`wine setup.exe /VERYSILENT /DIR="Z:\..."`. Tidak perlu di-patch — `/etc/hosts`
memetakan `tk0.kru.com` → 127.0.0.1, dan klien memakai ladang **hostname** di
offset 18464, bukan ladang IP di 18432.
Debug: `printf 'cont\nbt\nquit\n' | winedbg ./NexusTK.exe > berkas 2>&1`
— **wajib `./`**, dan output ke berkas bukan pipe (pipe membuat debugger
gagal attach).

---

### Status sebelumnya — 24 Agustus 2026

| | |
|---|---|
| Gerbang regresi | 6/6 hijau (`scripttest`, `maptest`, `chartest`, `worldtest`, `cliftest` 294, `dbtest`) |
| `logs/map.log` server hidup | **0 ERROR / 0 WARN**, stabil ~6 menit runtime |
| Ketiga server | jalan berdampingan (`./run.sh all`), tautan map↔char stabil |
| Binding skrip | 173 tersedia; **100 belum diport**; global belum diport **1** (`lock`) |
| Skrip Lua | 906/906 termuat, 0 error |
| **Klien RetroTK asli** | ⚠️ **BELUM PERNAH BERHASIL LOGIN** |

**Cara memastikan keadaan ini masih berlaku** (bukan sekadar percaya
tulisan di sini):

```
./build.sh
for t in scripttest maptest chartest worldtest cliftest dbtest; do ./run.sh $t; done
./run.sh stop; rm -f logs/map.log; ./run.sh all
sleep 240 && grep -c ERROR logs/map.log     # harus 0
```

⚠️ **Jangan menjalankan gerbang regresi sementara server hidup** —
`cliftest` dan `luaaudit` menulis ke `logs/map.log` yang sama, sehingga
hitungan ERROR jadi tercemar oleh baris yang memang diharapkan uji.
Ini sempat membingungkan: `grep -c ERROR` menunjukkan 38 padahal servernya
bersih; semuanya berasal dari `LuaAudit`.

#### Pekerjaan lanjutan setelah uji ujung-ke-ujung

Diurutkan menurut apa yang menghambat.

**1. Uji dengan klien RetroTK asli — SATU-SATUNYA penghambat nyata.**
Tidak ada kode baru yang perlu ditulis. Semua persiapan sudah beres:
`conf/map.conf` + `conf/login.conf` menunjuk `127.0.0.1`, `version: 750`,
Wine 9.0 (i386) terpasang, `ddraw.dll` sudah dikembalikan ke aslinya.
Yang belum: pemain sungguhan belum pernah masuk dunia. Sampai itu terjadi,
**seluruh Trek A tetap berstatus "diverifikasi offline"**.

**2. Binding yang masih stub — tidak melempar error, tapi juga tidak
berfungsi.** Ini kategori yang berbahaya karena `map.log` tetap bersih:
stub hanya menulis WARN sekali lalu mengembalikan nil. Yang terbesar:

| Method | Pemakaian | Keadaan sekarang |
|---|---|---|
| `sendAction` | 905x | ✅ diport 26 Agu (0x1A + kait `onAction`) |
| `talk` | 698x | ✅ diport 26 Agu (`clif_speak` 0x0D) |
| `playSound` | 632x | ✅ diport 26 Agu |
| `updateState` | 434x | ✅ diport 26 Agu (0x1D / 0x33 / 0x07) |
| `setDuration` | 423x | ✅ diport 26 Agu (`map/Durations`) |
| `spawn` | 381x | ✅ diport 26 Agu (`mobspawn_onetime`) |
| `setAether` | 225x | ✅ diport 26 Agu |
| `msg` | 133x | ✅ diport 26 Agu (`bll_talkcolor`) |
| `delete` | 109x | ✅ diport 26 Agu |
| `refresh` | 90x | ✅ diport 26 Agu |
| `moveGhost` | 84x | ✅ diport 26 Agu (`moveghost_mob` + 0x07 berkelompok) |
| `dropItem` / `dropItemXY` | 55x | ✅ diport 26 Agu (BL_ITEM) |

**Seluruh tabel ini sudah tertutup.** Yang tersisa dari daftar aslinya
hanya penyaring `clif_isignore` (lihat `clif_send_sub`) pada jalur obrolan —
daftar abaikan pemain belum ada.

**3. BL_ITEM (barang di lantai) belum ada.** Ini prasyarat `dropItem`,
`getObjectsInCellWithTraps` yang sungguhan (sekarang identik dengan varian
biasa karena tidak ada floor item untuk disaring), dan jatuhan mob yang
terlihat di tanah.

**4. Sisa Trek C** — C2 (4 berkas meta), C3 (warp antar map server),
C4 (papan pesan & surat, paling bebas hambatan). Rinciannya di bawah.

**Bug yang ditemukan dan SUDAH ditutup pada putaran ini** (jangan dicari
lagi): daftar stub menimpa binding baru (Peringatan #25); field `name`
LuaJ membuat pelaporan stub lumpuh (Peringatan #27); `objectRef()`
membungkus pemain sebagai NPC; `bladestorm_trap.lua` memanggil method pada
nil (`PERUBAHAN.md` butir 8).

### Daftar pekerjaan berikutnya

Dipecah tiga trek karena butir-butirnya saling bergantung, bukan satu
antrean lurus. Trek B dan C bisa jalan paralel.

**Sudah selesai** (jangan diulang): analisa `rtkmaps` + pembaca `.map`;
data game dipindah ke dalam project; serialisasi `mmo_charstatus` +
`CharPersistence`; dunia peta + penempatan pemain; survei `Origin Nexia`;
**seluruh Trek A (A1–A5)** dan **C1**.

---

#### Trek A — SELESAI (rinciannya di bawah, sebagai rujukan)

**A1. Paket `clif_*` dasar — SELESAI (bagian tertunda ditutup 21 Agustus 2026).**
`org.rtk.map.Clif`: `sendId` (0x05), `sendMapInfo` (0x15), `sendXy` (0x04),
`sendTime` (0x20), `sendAck` (0x1E), `sendRefreshTrigger` (0x22),
`blockMovement` (0x51), `sendMsg`/`sendMiniText` (0x0A), **`sendStatus`
(0x08)**, **`sendNpcLook`/`sendCharLook` (0x33)**, **`getCharArea` /
`sendCharArea`**. Semua big-endian.
Urutan `sendWorldEntry` mengikuti `intif.c` persis: ack, time, id, mapinfo,
status, refresh, xy, sapuan area — **klien peka terhadap urutan ini**.
Prasyarat yang ikut diport: `db/level_db.txt` (tabel pengalaman per path,
`map/data/ClassDb`), `map/data/ItemDb` (itemdb_look/lookcolor dari tabel
`Items`), `map/data/Equip` (slot EQ_*, sekaligus didaftarkan sebagai global
Lua), dan perlengkapan NPC (`NPCEquipment<serverId>`).
Ditutup terakhir: **`sendMapData` (0x06)** — gambar ulang petak peta saat
berjalan, lengkap dengan checksum `nexCRCC` (tabel CRC-16/CCITT 256 entri)
sehingga petak yang sudah dipegang klien tidak dikirim ulang, plus
`MapData.foreachInBlock()` untuk menggambar ulang isi petaknya.
**A1 SELESAI PENUH (21 Agustus 2026):** butir terakhir
`clif_cmoblook_sub` ikut diport bersama fondasi A5.

**A2. Gerakan — SELESAI (20 Agustus 2026).**
`Clif.parseWalk` (opcode 0x06): deteksi desinkron → tarik balik, tabrakan
lewat `MapData.walkable()` + `blocksMovement`, kamera (`viewX`/`viewY`),
konfirmasi 0x26 ke pemain, siaran 0x0C ke sekitar (AREA_WOS), `moveBlock`,
kait skrip, lalu **petak portal**. Ikut diport: tabel `Warps`
(`MapRegistry.loadWarps` → `MapData.addWarp`/`warpAt`), syarat masuk peta
+ pesan penolakan, `clif_pushback`, `clif_sendmsg`/`sendMiniText` (0x0A).

**A3. Uji end-to-end dengan MySQL sungguhan — SELESAI (21 Agustus 2026).**
`charserver/DbTest` (`./run.sh dbtest`, 82 assertion), tiga tahap:
(1) **audit SQL** — 64 pernyataan SQL dari 6 berkas sumber diambil dari
kodenya lalu di-`prepare` ke server, sehingga nama tabel/kolom divalidasi
MySQL sendiri (menggantikan pencocokan 300 nama kolom di atas kertas);
2 pernyataan yang disusun runtime (`CharPersistence.reg()`) dilewati dan
diuji lewat round-trip di tahap 3; (2) **data dunia** — 9.850 peta + 4.476
portal dari tabel asli; (3) **round-trip karakter** — karakter uji dengan
nilai ekstrem (unsigned 32-bit penuh, bigint 9 miliar, kolom bertanda
negatif, float karma) disimpan, dibaca ulang, dibandingkan ladang per
ladang, lalu dihapus. Uji ini **tidak menyentuh data yang sudah ada**.
Temuan: bug urutan portal (Peringatan #14).
Catatan: `save()` menulis 66 kolom sedangkan `load()` membaca 68 —
`ChaPassword` dan `ChaLastIP` memang diubah lewat jalur lain, bukan bug.

**A4. NPC & dialog — SEBAGIAN BESAR JALAN (21 Agustus 2026).**
Sudah ada: `Npc` + `NpcRegistry` (385 NPC + indeks id `map_id2bl` +
perlengkapan), **klik (0x43)** dengan penjaga karma, **paket dialog keluar**
`sendScriptMes` (0x30) dan `sendScriptMenu` (0x2F), **jawaban masuk**
`parseNpcDialog` (0x3A: ragam 0x01 dialog, 0x02 menu, 0x04 teks), serta
`ScriptEngine.cancel()` (port `sl_async_freeco`).

⚠️ **Perubahan penting:** `ScriptPlayer` kini **satu per pemain seumur
sesi** (`User.scriptPlayer()`), bukan salinan baru tiap panggilan seperti
sebelumnya. Keadaan coroutine dialog menempel pada objek itu — kalau
dibuat ulang, setiap dialog lupa dirinya sendiri begitu pemain menjawab.

Opcode **0x39** (`clif_handle_menuinput`) juga sudah ada, beserta paket
`sendInput` (0x2F).

⚠️ **Dua opcode yang mudah tertukar — sudah pernah salah di port ini:**
- Paket **menu memakai opcode 0x30, sama dengan kotak dialog.** Yang
  membedakan bagi klien adalah byte [5]/[6]: dialog `0x00/0x01`, menu
  `0x02/0x02`. Opcode **0x2F itu paket `input`**, bukan menu.
- **Jalur jawaban terbelah dua:** primitif `menu` dan `input` dijawab lewat
  **0x39** (ragam 1 = pilihan, 3 = teks), sedangkan `dialog`, `menuSeq`, dan
  `inputSeq` lewat **0x3A** (ragam 0x01/0x02/0x04). Di C keduanya memang
  memakai fungsi resume berbeda (`sl_resumemenu` vs `sl_resumemenuseq`).

Ditambahkan 21 Agustus 2026: **dialog ragam `dialogType == 1`** (kotak
dialog menampilkan wujud NPC; teks bergeser ke offset 66 dan panjangnya
`len + 63`) dan **timer NPC** (`NpcRegistry.runTimers`, tik 100 ms seperti
`timer_insert(100, 100, npc_runtimers, ...)`, memicu kait `move`/`action`).

⚠️ Offset perlengkapan NPC di kotak dialog **berbeda** dari paket gambar
0x33 meski isinya mirip: helm [33] vs [35], sepatu [51] vs [53].
Pergeserannya tidak seragam, jadi `npcPortrait` sengaja terpisah dari
`sendNpcLook` — jangan disatukan.

**A4 SELESAI (21 Agustus 2026).** Toko lengkap: binding `buy` dan `sell`,
paket `sendBuyDialog` (0x2F, [5]=4, [6]=2) dan `sendSellDialog`
(0x2F, [5]=5, [6]=4), jawaban `clif_parsebuy`/`clif_parsesell` pada 0x39
ragam 2/4.
- Daftar **beli** dijawab dengan **nama tampilan** barang, bukan indeks.
- Daftar **jual** berisi **nomor slot inventaris**, dikirim **1-basis**
  (`item[i] + 1` di C) tetapi jawabannya kembali apa adanya — konversinya
  hanya satu arah, dan itu memang begitu di C.

**Skrip kini menerima NPC sebagai argumen kedua** pada kait `click`, seperti
`sl_doscript_blargs(nd->name, "click", 2, &sd->bl, &nd->bl)` di C.
Sebelumnya hanya pemain yang dikirim, sehingga skrip yang membaca
`npc.yname` / `npc.bankNPC` menerima nil. Atribut NPC dijawab lewat
antarmuka `ScriptAttrs`.

⚠️ **`yname` vs `name` pada NPC terbalik dari dugaan:** `nd->name` di C
adalah **nama skrip** (dipakai mencari kelas Lua) dan `nd->npc_name` nama
tampilan. Di Lua keduanya muncul sebagai `npc.yname` (nama skrip, dipakai
565x) dan `npc.name` (nama tampilan). Salah memetakan di sini terasa di
mana-mana.

**Bank & reparasi ternyata bukan pekerjaan server.** `bankNPC`/`repairNPC`
hanya atribut NPC yang dibaca skrip — perilakunya seluruhnya ada di Lua.
Yang dibutuhkan hanya mengekspos atributnya, dan itu sudah. Begitu ini jalan, 906 skrip yang sudah termuat mulai benar-benar
berfungsi.

**A5. Mob & pertarungan — FONDASI JADI (21 Agustus 2026).**
Sudah ada: `MobData` (jenis mob dari tabel `Mobs`, 716 baris), `Mob`
(instance hidup), `MobRegistry` (port `mobdb_read` + `mobspawn_read`;
1.175 mob lahir dari `Spawns<serverId>`, 0 gagal), dan `sendMobLook`
(`clif_cmoblook_sub`, 0x33) yang sekaligus menutup butir terakhir A1.

⚠️ **Nama mob KEBALIKAN dari NPC.** Untuk mob, `MobIdentifier` → `yname`
(nama skrip) dan `MobDescription` → `name` (nama tampilan). Untuk NPC
justru `NpcIdentifier` → `name`. Versi C memang begitu: dispatch skrip mob
memakai `mob->data->yname`, NPC memakai `nd->name`. Menukarnya membuat
seluruh skrip mob tidak pernah terpanggil.

Catatan data: dari 716 jenis, hanya **5** ber-`MobIsChar = 1` (digambar
sebagai karakter) dan kelimanya **tidak punya baris spawn** — mereka
dipanggil skrip (mantra `wind_walk`, mob event). Jadi jalur `sendMobLook`
ada dan teruji, tapi tidak terpicu oleh spawn statis.

**AI mob & kelahiran ulang jadi (21 Agustus 2026):**
`MobRegistry.runTimers` — tik **50 ms** (`timer_insert(50, 50,
mob_timer_spawns, ...)` di C), memicu kait `move`/`attack`, plus
`respawn()` dan `kill()`.

Tabel AI dipilih dari `MobAI`: 0 `mob_ai_basic`, 1 `mob_ai_normal`,
2 `mob_ai_hard`, 3 `mob_ai_boss`, **4 = skrip mob itu sendiri
(`yname`)**, 5 `mob_ai_ghost`. Ingat temuan audit Lua: `mob_ai_hard` dan
`mob_ai_boss` **tidak pernah didefinisikan** di konten — tidak berdampak
sekarang karena database hanya memakai MobAI 0 dan 4.

Dua penyaring yang ditiru dari C dan penting untuk beban server: mob
**diam** (`MobBehavior >= 2`) dilewati sama sekali, dan mob di peta
**tanpa pemain** juga dilewati. Kelahiran ulang mengembalikan mob ke
**titik awalnya**, bukan tempat ia mati — itulah yang membuat titik spawn
tidak bergeser setelah mob dipancing jauh.

**Pertarungan tersambung (21 Agustus 2026) — dan bentuknya bukan seperti
dugaan.** Perhitungan kerusakan **tidak ada di C**: skrip melukai mob
dengan menulis `mob.health` langsung (`mobl_setattr` di sl.c). Jadi yang
diport bukan rumus tempur, melainkan jalur yang dipakai skrip:
- `Mob` mengimplementasikan `ScriptAttrs` — baca/tulis `health`, `target`,
  `state`, dan nilai dari jenisnya (`minDam`, `level`, …).
  ⚠️ Nilai yang **berubah per mob** ada di `Mob`, yang **tetap** di
  `MobData`. Menulis nilai per-mob ke `MobData` akan mengubah semua mob
  sejenis sekaligus.
- `getObjectsInCell(m, x, y, BL_*)` kini mengembalikan **objek asli**,
  bukan tabel kosong — inilah cara `swing.lua` menemukan lawan.
- Konstanta **BL_*** didaftarkan sebagai global Lua. Nilainya **bit**
  (`BL_PC=1`, `BL_MOB=2`, `BL_NPC=4`, `BL_ALL=0x0F`), bukan nomor urut,
  sehingga bisa digabung.
- Kematian dideteksi di tik AI (`reapDead`), bukan di setter atribut:
  tidak ada satu titik pun di Java yang tahu kapan pukulan mendarat, dan
  setter atribut sebaiknya tetap bodoh. Mob bernyawa 0 dicabut dari peta
  dan kait `on_death` dipanggil.

⚠️ **Satu `ScriptPlayer` hanya boleh dipakai satu `ScriptEngine`**, karena
`udata`-nya di-cache. Memakainya dengan engine kedua mengembalikan objek
milik engine pertama, dan gejalanya membingungkan: skrip tampak jalan tapi
tidak menyentuh keadaan yang benar. Pernah kejadian di uji.

**A5 SELESAI (21 Agustus 2026).** Dilengkapi terakhir:
- **Tabel ancaman** di `Mob` (id pemain → total kerusakan), diisi lewat
  `player:addThreat(mobId, damage)`. Yang dicatat **akumulasi**, bukan
  pukulan terakhir — itulah yang membuat mob mengejar penyerang terbesar.
- **Jalur kematian lengkap**: pemegang ancaman terbesar dianggap
  pembunuhnya (`attacker` hanya cadangan bila tabel kosong), lalu
  `on_death(mob, pemain)` dan `HandleMobDrops(pemain, mob)` dipanggil.
  Jatuhan barang seluruhnya sisi skrip — `mobdb_drops` di C pun hanya
  memanggil Lua. Pengalaman juga diberikan skrip lewat atribut
  `mob.experience`.

⚠️ **Mob wajib terdaftar di indeks id global** (`map_addiddb` di C), lewat
`MobRegistry.useIdIndex(npcs)`. Skrip merujuk mob dengan id
(`player:addThreat(mob.id, ...)`), jadi tanpa indeks satu-satunya cara
menemukannya adalah memindai 1.175 mob **setiap pukulan**. Catatan: indeks
id itu kebetulan tinggal di `NpcRegistry` — namanya menyesatkan, isinya
NPC **dan** mob.

---

#### Prioritas sekarang — 26 Agustus 2026 (sore)

> Angkanya dihitung ulang dari `./run.sh luaaudit -Drtk.audit.penuh=true`
> pada tanggal itu; **jangan percaya angka di sini kalau `Bindings.java`
> sudah berubah.** 39 method masih ada di `sl.c` tapi belum diport, plus 6
> yang tidak ada di mana pun (salah ketik / kode mati).

~~**1. BL_ITEM — barang di lantai.**~~ **SELESAI 26 Agustus 2026 (sore).**
Sisa yang berkaitan: `throwItem` (`clif_throwitem_script`, jalur klien
melempar barang) dan `sd->pickuptype`.

**1. Sisa inventaris & perlengkapan — SEMUANYA menunggu subsistem BOD.**
Paket inventaris (0x0F/0x10) dan bagian yang berdiri sendiri sudah diport
26 Agustus 2026 malam. Yang tersisa **semuanya** bergantung pada
`sd->boditems` (simpanan barang yang patah / hilang saat mati), yang belum
ada sama sekali: `stripEquip` (9x), `checkInvBod`, `getBODItem`,
`deductDuraEquip`, `expireItem`. Kerjakan BOD sebagai satu blok, jangan
satu per satu.

~~**2. Gerak mob lanjutan.**~~ **SELESAI 26 Agustus 2026 (malam).**
Ketiganya memang varian dari mesin yang sama — lihat Peringatan #35 untuk
tiga perbedaan halus yang mudah diseragamkan secara keliru.

~~**2. Buku mantra.**~~ **SELESAI 26 Agustus 2026 (malam).** Dua di
antaranya (`getUnknownSpells`, `getAllClassSpells`) menembak database tiap
panggil, jadi ujinya ada di `dbtest`, bukan `cliftest`.

~~**2. Tampilan & timer.**~~ **SELESAI 26 Agustus 2026 (malam).**
Satu-satunya yang sengaja <b>tidak</b> diport: `testPacket` (4x) —
alat debug GM yang menulis byte sembarang ke kabel dari tabel Lua.
Nilainya nol bila protokol memang akan diganti, dan risikonya nyata.

**2. C4 — papan pesan & surat.** `sendMail`, `updateMail`, `sendParcel`
(5x), `getParcel`, `removeParcel`, `getParcelList`, `showBoard`,
`sendBoardQuestions`, `powerBoard`, `addGift`, `retrieveGift` — ~25 titik,
plus protokol char server 0x3009–0x300F. Tabelnya (`Boards`,
`BoardTitles`, `Mail`, `Parcels`) sudah ada, dan **bisa diuji offline**
seperti gerbang regresi lain.

**3. Bank klan & subpath.** `clanBankDeposit`/`clanBankWithdraw`/
`getClanBankItems`/`getSubpathBankItems`/`getBankItems` — 5 titik, dan
logika bank utamanya sudah ada. Murah.

**4. Sisa administratif.** `setAccountBan`,
`setHeroShow`, `setCaptchaKey`/`getCaptchaKey`, `addActivationKey`/
`checkActivationKey`, `mapSelection`, `checkLevel`, `getPK`,
`setIndDmg`/`setGrpDmg`.

**5. Enam nama yang TIDAK ADA di mana pun** (`addGMSpells`, `bowShoot`,
`buyCustom`, `hairFaceMenu`, `returnInn`, `totemName`) — salah ketik atau
kode mati di konten. Diputuskan satu per satu, catat di
`luascript/PERUBAHAN.md`; jangan diport.

⚠️ Ambil prioritas binding dari **`map.log` server yang berjalan** bila
memungkinkan, bukan dari jumlah pemakaian di korpus — putaran 24 Agustus
2026 membuktikan yang benar-benar dipanggil saat server hidup berbeda dari
yang paling banyak tertulis di skrip. Sekarang syaratnya lebih berat:
setelah daftar stub habis, jalur yang tersisa (durasi, AI mob) **butuh
pemain online** untuk menyala sama sekali.

#### Trek B — aset & tooling (paralel)

**B1. Dekoder EPF** — EPF + PAL → gambar RGB, plus pemetaan id
`tile`/`obj` ke frame. Untuk `obj` perlu `rtk/SObj.tbl` (18.954 entri,
masih di RTK-Server, belum disalin). Ini prasyarat B2-visual dan B3.

**B2. Tools editor HTML + JavaScript** — edit `.map` dan skrip Lua,
jalan langsung di browser. Bisa dimulai **sebelum** B1 dengan menampilkan
grid berwarna dari id/`pass`; gambar aslinya menyusul.
Ingat: `.map` **big-endian** → `DataView.getUint16(off, false)`.

**B3. Client desktop Java + libGDX** — prasyarat B1.

---

#### Trek C — utang teknis (kecil, kerjakan saat menyentuh area terkait)

- ~~**C1.**~~ **SELESAI (21 Agustus 2026).** Keempat registry di
  `ScriptPlayer` (`registry`, `registryString`, `npcInt`, `questReg`)
  sekarang **objek yang sama** dengan milik `CharStatus` — disambungkan
  sekali lewat `ScriptPlayer.bindRegistries()` saat objek skrip dibuat,
  jadi tidak ada penyalinan yang bisa terlewat. Terbukti ujung-ke-ujung di
  `dbtest`: Lua menulis → `CharPersistence.save` → baris muncul di tabel
  `Registry`.
  Ikut dibereskan: atribut `level` yang ditulis skrip kini menembus ke
  `CharStatus` (lewat antarmuka `ScriptPlayer.Owner`), dan setter
  `x`/`y`/`m` **dihapus** — lihat Peringatan #21.
- **C2. 4 berkas meta hilang** — `login.conf` meminta 5
  (`RidableAnimals`, `CharicInfo0/1`, `ItemInfo0/1`), `meta/` hanya punya
  `RidableAnimals`. Inilah sebab tooltip item hilang.
  Kandidat **ada** di `RTK-Server/rtk/decrypted/` (29 berkas: `CharicInfo0`
  sampai `CharicInfo22`, `ItemInfo0C`, `ItemInfo1`, `ItemInfo2`,
  `RideableAnimals`, …) dan formatnya sekeluarga dengan yang sudah dipakai
  (string berprefiks panjang). **Dua hal yang belum pasti:** namanya tidak
  cocok persis (`ItemInfo0C.dat` vs yang diminta `ItemInfo0`), dan
  `RideableAnimals.dat` (11 KB, ejaan beda) **bukan** pasangan
  `RidableAnimals` yang dipakai sekarang (45 KB) — jadi berkas di sana
  kemungkinan versi lain. Jalur `send_metafile` sudah jalan, tapi apakah
  klien menerima isinya **tidak bisa diperiksa tanpa klien**.
- **C3. Perpindahan antar map server** (di C: lookup `MapServer` di tabel
  `Maps`); sekarang ditolak dengan pesan jelas. Butuh **dua map server
  berjalan** dengan pembagian peta berbeda, dan verifikasinya praktis
  butuh klien.
- **C4. Papan pesan & surat** (char server 0x3009–0x300F). **Paling bebas
  hambatan di Trek C**: murni protokol + tabel yang sudah ada (`Boards`,
  `BoardTitles`, `Mail`, `Parcels`), bisa diuji offline seperti gerbang
  regresi lain.

---

#### Angka acuan binding skrip

**Jangan percaya angka yang ditulis di sini kalau `Bindings.java` sudah
berubah — jalankan `./run.sh luaaudit`**, yang menghitungnya ulang dari
mesin skrip yang hidup dan dari sumber `sl.c`.

| | 21 Agu | 24 Agu | 26 Agu (sore) |
|---|---|---|---|
| tersedia saat runtime (prototipe Player/NPC/Mob/FloorItem) | 151 | 173 | **214** |
| **ada di `sl.c` tapi belum diport** | 110 | 100 | **67** |
| dipanggil tapi tidak ada di mana pun (salah ketik / kode mati) | 6 | 6 | 6 |
| **global belum diport** | 6 | 1 | **1** (`lock`) |

Sebagai pembanding, sebelum Trek A angkanya 12 binding riil dan 144 method
belum ada; sebagian besar tertutup saat A4–A5 karena ternyata banyak
"pekerjaan server" sebenarnya logika Lua yang hanya butuh jalur yang benar.

⚠️ **Kolom 26 Agu turun jauh lebih sedikit daripada pekerjaannya** — lihat
Peringatan #30. Sepuluh binding terbesar yang ditutup hari itu
(`sendAction` 905x, `talk` 698x, `setDuration` 423x, `spawn` 381x, …)
semuanya berangkat dari keadaan **stub**, dan stub tidak pernah terhitung
di kolom ini sejak awal.

**Sudah diport 21 Agustus 2026** (turun 117 → 110): `calcStat` (249×),
`addNPC` (54×), `addSpell`, `hasSpell`, `bankDeposit`, `bankWithdraw`,
`callBase`.
- `calcStat` = `pc_calcstat`: kembalikan nilai turunan ke nilai dasar lalu
  jumlahkan bonus **seluruh perlengkapan yang dikenakan**. Nilai dasar
  dijaga minimal 5 seperti di C. Butuh `ItemDb` dengan kolom stat
  (`ItmVita`, `ItmMight`, …) yang ikut ditambahkan.
- `addSpell` menerima **nama atau nomor**, seperti di C — nama dicari lewat
  `SpellDb` (port `magicdb_id`, 871 mantra dari tabel `Spells`).
- `bankWithdraw` **mengembalikan barang ke bank** bila inventaris penuh;
  barang tidak boleh lenyap di tengah jalan.

- `addNPC` melahirkan **NPC sementara** dari skrip (jebakan, dekorasi).
  Idnya dari rentang terpisah `NPCT_START_NUM` agar tidak bertabrakan
  dengan NPC tabel; memicu `on_spawn`, dan bila diberi `duration` akan
  memicu `endAction` lalu **dicabut dari dunia**.
  ⚠️ Argumen ke-9 di C dipakai **dua kali** — sebagai `movetime` bila angka
  dan sebagai nama tampilan bila teks. Kelonggaran itu ditiru.
- `callBase` (dipakai `mob.lua` untuk MobAI tipe 4) memanggil kait skrip
  milik mob dengan **mob dan penyerangnya**. Bila tidak ada penyerang, C
  mengirim **mob itu sendiri** sebagai argumen kedua — bukan nil.

**Diport 24 Agustus 2026 — semuanya dipilih dari `map.log` server yang
benar-benar berjalan, bukan dari daftar pemakaian:**

| Binding | Asal masalah |
|---|---|
| `sendSide` (0x11) | 13 dari 21 error di log; kait AI NPC saat berbelok |
| `npc:move()` (`npc_move`) | 8 error; kait AI NPC yang paling sering dipanggil |
| `npc:warp()` (`npc_warp`) | pasangan `move` di prototipe yang sama |
| **seluruh keluarga `getObjects*`** — `InCell`, `InCellWithTraps`, `InArea`, `InSameMap`, `InMap`, masing-masing + varian `Alive`, plus `getBlock` (`map_id2bl`) dan `getUsers` | 178 pemakaian gabungan; di C semuanya dipasang lewat `bll_extendproto`, jadi Player, NPC, dan Mob memiliki set yang **sama persis** |
| `getMapXMax` / `getMapYMax` | batas peta; **indeks terbesar, bukan ukuran** (`xs - 1`) |
| `getMapRegistry` / `setMapRegistry` | registry **per peta** — lihat di bawah |
| `getPass` / `getObject` / `getTile` / `getWarp` | geometri peta apa adanya |
| `addNPC`, `objectCanMove` / `objectCanMoveFrom` **di NPC/Mob** | ada di `bll_extendproto`, bukan milik pemain |
| `Player(id)` / `Player(nama)` (`pcl_ctor`) | jebakan rogue mencari pemiliknya |
| `sendAnimation` / `sendAnimationXY` (0x29) | efek mantra & jebakan |
| **`player:sendStatus()`** | masih stub padahal `Clif.sendStatus` sudah ada sejak A1 — 1.100+ pemakaian |
| `MOB_ALIVE`/`MOB_DEAD`/`MOB_PARA`/`MOB_BLIND`/`MOB_HIT`/`MOB_ESCAPE`, `F1_NPC` | 44 perbandingan `mob.state` yang selama ini selalu salah |

Catatan yang mudah salah pada kelompok ini:

- **`mapRegistry` itu per peta**, bukan satu tabel global
  (`map_readglobalreg(m, ...)`). Kuncinya dicocokkan `strcmpi` di C, jadi
  di sini dinormalkan ke huruf kecil. Atribut `player.mapRegistry` /
  `npc.mapRegistry` terikat ke peta tempat objeknya **sedang berdiri**,
  sehingga instance-nya dibuat per objek, bukan satu untuk seluruh mesin.
- **Varian `Alive` bukan penyaring mob.** Kedua varian sama-sama melewati
  mob mati dan pemain ber-stealth; yang **hanya** ada di
  `getAliveObjects*` adalah melewati pemain yang sedang mati
  (`status.state == 1`) — lihat `bll_getaliveobjects_helper` di `sl.c`.
  Sempat salah dibaca sebagai penyaring mob.
- **`...WithTraps` bedanya cuma barang di lantai.** `map_foreachincell`
  melewati floor item bertipe `ITM_TRAPS`, versi `...withtraps`
  menyertakannya. **Sejak BL_ITEM diport (26 Agustus 2026 sore) penyaring
  itu aktif**, jadi keduanya benar-benar berbeda. Aturan lengkapnya —
  termasuk kenapa `trapsTable` TIDAK ikut dilihat di sini — ada di
  Peringatan #33.
- **`objectRef()` dulu membungkus SEMUA benda sebagai NPC.** Untuk mob itu
  tidak terasa (prototipe NPC dan Mob berisi method yang sama), tetapi
  pemain yang ditemukan `getObjectsInCell(..., BL_PC)` jadi tidak punya
  satu pun method pemain. Sekarang dibungkus menurut jenis aslinya, dan
  pemain selalu lewat `playerRef()` supaya `udata`-nya tetap satu
  (lihat catatan A4 tentang `ScriptPlayer` per pemain).
- **`clif_object_canmove()`/`_from()` sengaja tidak diport.** Keduanya
  membaca `objectFlags[]`, dan baris yang mengisi tabel itu
  (`objectFlags[z] = flag;` di `object_flag_init`) **dikomentari** di
  sumber C — jadi keduanya selalu mengembalikan 0 pada build aslinya.
  Meniru keduanya berarti memport pembacaan `SObj.tbl` (18.954 entri)
  yang efeknya nol.
- **`clif_npc_move` disiarkan sekali, bukan N x N.** C memanggilnya lewat
  `map_foreachinarea(..., BL_PC, ...)` padahal isinya sudah menyiarkan ke
  seluruh area. Paketnya membawa posisi mutlak sehingga pengulangan tidak
  mengubah tampilan klien.

Sisanya (100) tidak ada lagi yang menonjol jauh — `hasLegend`,
`getEquippedItem`, `killCount` ada di kisaran 100–200 pemakaian.

## Kebiasaan project

- Bahasa komunikasi user & dokumentasi: **Bahasa Indonesia** (istilah
  teknis tetap Inggris).
- Selesai mengubah kode: compile bersih (`-Xlint:all` boleh 1 warning
  try-with-resources di Sql), jalankan `./run.sh scripttest`, bersihkan
  `build/`/`dist/`/`logs/` (sudah di .gitignore).
- **README ada dua bahasa:** `README.md` (Bahasa Indonesia, utama) dan
  `README.en.md` (Inggris). Keduanya bercermin — struktur bagiannya sama
  persis. Kalau mengubah salah satu, **perbarui yang lain di sesi yang
  sama**, jangan ditunda. Juga perbarui `extLib/README.md` bila menambah
  dependensi.
