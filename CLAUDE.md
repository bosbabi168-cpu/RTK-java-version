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
(`*.sql.bak`: 7.974 baris `Maps`, 4.476 `Warps`). Jalankan lewat
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
  `./run.sh cliftest` (paket klien, gerakan, portal — 87 assertion).

```
./build.sh            # atau build di NetBeans
./run.sh scripttest   # GERBANG REGRESI — 906 skrip Lua
./run.sh maptest      # GERBANG REGRESI — 3.544 berkas peta
./run.sh chartest     # GERBANG REGRESI — serialisasi karakter
./run.sh worldtest    # GERBANG REGRESI — dunia peta + pemain
./run.sh cliftest     # GERBANG REGRESI — paket klien + gerakan + portal
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
- MySQL biasanya TIDAK tersedia di lingkungan dev — login/char server exit
  saat start (perilaku sama dengan C), map server & ScriptTest tetap jalan.
  Itu bukan bug.

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
     7.648 baris `Maps` hanya menunjuk 2.640 berkas unik. Tiap peta tetap
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

14. **Jangan biarkan `extLib/*.jar` ter-gitignore.** `.gitignore` berbasis
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

**A1. Paket `clif_*` dasar — SELESAI (20 Agustus 2026).**
`org.rtk.map.Clif`: `sendId` (0x05), `sendMapInfo` (0x15), `sendXy` (0x04),
`sendTime` (0x20), `sendAck` (0x1E), `sendRefreshTrigger` (0x22),
`blockMovement` (0x51), `sendWorldEntry`, `refresh`. Semua big-endian
(`wfifoWBE`), berbeda dari protokol antar-server.
**Belum ada:** `clif_sendstatus`, `clif_getchararea`, `clif_sendmapdata`
(cabang redraw `0x06`) dan `clif_*look_sub` — pemain lain belum digambar
ulang saat masuk area pandang.
Verifikasi akhir tetap butuh klien nyata; `cliftest` menutupi tata letak
byte-nya (paket dibangun, **didekripsi balik**, diperiksa per-offset).

**A2. Gerakan — SELESAI (20 Agustus 2026).**
`Clif.parseWalk` (opcode 0x06): deteksi desinkron → tarik balik, tabrakan
lewat `MapData.walkable()` + `blocksMovement`, kamera (`viewX`/`viewY`),
konfirmasi 0x26 ke pemain, siaran 0x0C ke sekitar (AREA_WOS), `moveBlock`,
kait skrip, lalu **petak portal**. Ikut diport: tabel `Warps`
(`MapRegistry.loadWarps` → `MapData.addWarp`/`warpAt`), syarat masuk peta
+ pesan penolakan, `clif_pushback`, `clif_sendmsg`/`sendMiniText` (0x0A).

**A3. Uji end-to-end dengan MySQL sungguhan** ← MULAI DI SINI
Sekaligus memvalidasi `CharPersistence` yang **sampai sekarang belum pernah dijalankan terhadap
database hidup** (baru dicek lewat pencocokan 300 nama kolom).

**A4. NPC & dialog** — `clif_parsenpcdialog` menyambungkan engine Lua ke
klien. Begitu ini jalan, 906 skrip yang sudah termuat mulai benar-benar
berfungsi.

**A5. Mob & pertarungan** — `mob.c` (2.411 baris): spawn, AI, kerusakan.

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

- **C1.** Sambungkan `ScriptPlayer` ke `CharStatus` supaya registry yang
  ditulis skrip ikut tersimpan (sisi penyimpanan sudah ada).
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
