# CLAUDE.md — RTK-java-version

Panduan untuk sesi pengembangan (berbantuan AI maupun manusia) di project
ini. Baca README.md untuk gambaran lengkap; file ini fokus ke hal yang
harus diketahui SEBELUM mengubah kode.

## Apa project ini

Port Java SE dari **RTK-Server** (`../RTK-Server`), server MMO
RetroTK/NexusTK yang aslinya ditulis dalam C (`rtk/src/`: login-server,
char-server, map-server) + MySQL + skrip konten Lua (`rtklua/`, 907 file).
Kebijakan port: **setia byte-per-byte terhadap protokol wire C** supaya
klien RetroTK asli tetap kompatibel. Konten Lua TIDAK dikonversi — dijalankan
apa adanya lewat LuaJ (keputusan desain, lihat README "Scripting engine").

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
  `./run.sh cliftest` (paket klien, gerakan, portal, penggambaran, gambar ulang peta, dialog NPC, toko — 218 assertion),
  `./run.sh dbtest` (lapisan database ke MySQL hidup — 111 assertion;
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

## Audit skrip Lua (`./run.sh luaaudit`)

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

20. **`pcl_setattr` di C tidak punya setter posisi — jangan tambahkan.**
   C menyediakan **164** atribut pemain yang bisa ditulis skrip, dan
   `x`/`y`/`m` **bukan** salah satunya. Port ini sempat menambahkannya
   sendiri, dan itu salah: menulis koordinat langsung memindahkan pemain
   **tanpa memperbarui indeks blok peta**, sehingga ia hilang dari pandangan
   pemain lain dan tabrakan jadi kacau. Setter itu sudah dihapus. Skrip
   memindahkan pemain lewat method `warp()`, yang melewati
   `Pc.warp` → `delBlock`/`addBlock`. Kalau menambah setter atribut baru,
   cocokkan dulu dengan daftar di `pcl_setattr`.

21. ~~**Binding barang memakai inventaris tiruan.**~~ **DIBERESKAN
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

22. **Jangan biarkan `extLib/*.jar` ter-gitignore.** `.gitignore` berbasis
   template Java GitHub mengabaikan `*.jar`; project ini sengaja tanpa
   Maven sehingga jar HARUS ikut ter-commit — baris `!extLib/*.jar` wajib
   ada. Tanpa itu, clone di server CentOS menghasilkan `extLib/` kosong
   dan build gagal. Verifikasi: `git check-ignore -v extLib/*.jar` harus
   tidak menghasilkan apa pun.

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
  loader crash. Angka terkini: 254 method dipanggil skrip, 12 punya
  binding Java riil, 15 stub, 144 belum ada (lihat "Angka acuan binding
  skrip" di roadmap).
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

### Daftar pekerjaan berikutnya (disusun ulang 20 Agustus 2026)

Daftar lama dipecah jadi tiga trek karena ternyata **saling bergantung**,
bukan satu antrean lurus. Trek A dan B bisa jalan paralel.

**Sudah selesai** (jangan diulang): analisa `rtkmaps` + pembaca `.map`;
data game dipindah ke dalam project; serialisasi `mmo_charstatus` +
`CharPersistence` + protokol 0x3003/0x3803/0x3004/0x3007; fondasi dunia
peta (`MapData`, indeks spasial) + penempatan pemain (`User`, `Pc`);
survei `Origin Nexia`.

---

#### Trek A — sampai server bisa dimainkan (berurutan)

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
**Belum ada:** `clif_cmoblook_sub` (mob — menunggu A5). Dengan itu A1
selesai sejauh yang bisa dikerjakan sebelum mob ada.

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

**A5. Mob & pertarungan** ← MULAI DI SINI — `mob.c` (2.411 baris): spawn,
AI, kerusakan. Sekaligus membuka sisa A1 (`clif_cmoblook_sub`, penggambaran
mob) dan sebagian besar dari 118 binding yang belum diport.

---

#### Trek B — aset & tooling (paralel, tidak memblokir Trek A)

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
- **C2.** 4 berkas meta hilang: `login.conf` meminta 5
  (`RidableAnimals`, `CharicInfo0/1`, `ItemInfo0/1`), `meta/` hanya punya
  `RidableAnimals`. Kandidat sumber ada di `RTK-Server/rtk/decrypted/`.
  Inilah sebab tooltip item hilang (README asli langkah 11).
- **C3.** Perpindahan **antar map server** (di C: lookup `MapServer` di
  tabel `Maps`); sekarang ditolak dengan pesan jelas.
- **C4.** Papan pesan & surat (char server 0x3009-0x300F).

---

#### Angka acuan binding skrip (diukur 20 Agustus 2026)

Ini **mengoreksi** angka lama "±30 dari ±209" yang beredar di catatan
sebelumnya:

| | jumlah |
|---|---|
| method dipanggil skrip (unik) | **254** |
| disediakan `player.lua` sendiri (lapisan Lua) | 110 |
| binding Java riil | **12** |
| binding Java stub (no-op) | 15 |
| **belum ada implementasi sama sekali** | **144** |

Paling sering dipakai yang belum ada: `calcStat` (232×),
`hasLegend` (229×), `sendSide` (183×), `getObjectsInMap` (177×),
`hasDuration` (171×), `getEquippedItem` (166×), `killCount` (143×),
`addLegend` (136×), `getInventoryItem` (133×), `swingTarget` (129×).
Urutan ini yang menentukan prioritas port subsistem di Trek A.
Perintah untuk menghitung ulang ada di riwayat sesi — ulangi bila
`Bindings.java` berubah banyak.

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
