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
- **Menjalankan:** `./run.sh {login|char|map|all|scripttest|status|stop}`,
  yang membungkus `java -jar <jar> <server> &`.

```
./build.sh            # atau build di NetBeans
./run.sh scripttest   # GERBANG REGRESI UTAMA — wajib hijau sebelum selesai
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
9. **Jangan biarkan `extLib/*.jar` ter-gitignore.** `.gitignore` berbasis
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
| `map/map.c,intif.c` | `org.rtk.map` | skeleton; handshake + routing login jalan |
| `map/sl.c` | `org.rtk.map.script` | lihat bawah |

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
  loader crash. ±209 method player dipakai skrip; ±30 sudah riil di
  `Bindings.java` terhadap `ScriptPlayer` in-memory.
- Referensi kebenaran semantik binding: `RTK-Server/rtk/src/map/sl.c`
  (cari `pcl_*`, `bll_*`, `typel_extendproto`, `lua_register`).

## Status & roadmap

### Tahap 1 — SELESAI (19 Agustus 2026)

Fondasi server-side sudah berdiri dan terverifikasi:

- **Login server**: port penuh (login, buat karakter, ganti password, meta
  file, maintenance mode, banned IP, brute-force lockout, redirect ke map).
- **Char server**: handshake login & map, autentikasi karakter, routing map.
- **Map server**: skeleton — handshake + routing pemain jalan ujung-ke-ujung.
- **Scripting engine**: 906/906 file rtklua asli termuat 0 error; dialog
  coroutine teruji lewat `Accepted/player.lua` asli.
- **Infrastruktur**: HikariCP, Log4j2 (rolling harian, retensi 30 hari),
  konfigurasi di `rtk-server.properties`, build NetBeans + `build.sh`,
  deploy `run.sh` (`java -jar <jar> <server> &`).
- **Arsitektur jaringan**: instance per server + IO thread /
  ArrayBlockingQueue / logic thread (lihat Peringatan #8).

### Tahap 2 — rencana berikutnya (urutan dari user)

**1. Analisa `rtkmaps` — SELESAI (20 Agustus 2026)**

Format `.map` sudah dibedah, diverifikasi, dan **sudah ada pembacanya**:
`map/data/MapFile.java` + pemeriksa `map/data/MapFileTest.java`
(jalankan `./run.sh maptest`).

```
offset 0 : uint16 BE xs        lebar
offset 2 : uint16 BE ys        tinggi
offset 4 : xs*ys x { uint16 BE tile, uint16 BE pass, uint16 BE obj }
ukuran berkas = 4 + xs*ys*6
```

Temuan yang perlu diingat:

- Rumus ukuran cocok pada **3.544/3.544 berkas, nol pengecualian**.
  6.710.724 petak total; 435 dimensi berbeda (terbanyak 30×30);
  terbesar `600x600.map`. Waktu muat seluruhnya ±160 ms.
- `pass` di berkas hanya **0 atau 1** — 0 bisa dilewati, bukan-0 tembok
  (`map_canmove()` mengembalikan 1 = TIDAK bisa lewat). Saat runtime,
  server C **memakai ulang** ladang ini untuk id objek yang sedang
  menempati petak, jadi jangan anggap selalu boolean di luar berkas.
- `tile` = id lantai (0..±31k, rujukan tileset klien);
  `obj` = lapisan objek, ±12% petak berisi.
- **Berkas peta kini ada di dalam project**: `maps/` (3.544 berkas, ~38 MB,
  salinan byte-identik dari `rtkmaps/Accepted/` — sudah diverifikasi
  3.544/3.544 identik). Struktur subfolder dipertahankan karena tabel
  `Maps` merujuk mis. `games/sumo_war.map`. Lokasinya diatur
  `map.path` (properties) → `map_path` (`conf/map.conf`) → argumen CLI.
  Sumber aslinya di C: `map.c`:1145 → `../rtkmaps/Accepted/%s`. Folder
  `rtkmaps/TKR Maps/` adalah salinan lama; **abaikan**.
- Metadata peta ada di tabel MySQL `Maps` (33 kolom: nama, BGM, PvP,
  cahaya, cuaca, batas level, dsb.), berkas ditunjuk kolom `MapFile`.
  Di backup DB ada **7.648 baris peta** yang menunjuk hanya **2.640
  berkas unik** — banyak peta **berbagi geometri yang sama** dan hanya
  berbeda metadata. Semua berkas yang dirujuk ADA di disk (0 hilang);
  652 berkas di disk tidak dipakai.
- **Warp bukan dari berkas.** Server membaca tabel `Warps`
  (`npc.c`:165, 6 kolom `SourceMapId,SourceX,SourceY,DestinationMapId,
  DestinationX,DestinationY`, 4.476 baris di backup). `rtkmaps/warps.txt`
  (852 baris, format sama) adalah data sumber/legacy, dan
  `conf/warp_main.conf` menunjuk folder `../mithiamaps/` yang tidak ada
  serta importnya dikomentari di `map.conf` — jangan tertipu.

**2. Analisa `rtklua`** (sekarang di `luascript/`, 907 file, ~164rb baris)

Sudah *berjalan* lewat LuaJ, tapi belum *dianalisa isinya*. Tujuannya
memetakan binding apa saja yang benar-benar dibutuhkan konten: ±209 method
player dipakai skrip, baru ±30 yang riil di `Bindings.java`. Analisa ini
yang menentukan urutan port subsistem gameplay (`pc.c`, `mob.c`, `npc.c`,
`clif.c`) — implementasikan yang paling sering dipanggil lebih dulu
(`sendMinitext` 2.902×, `dialogSeq` 2.343×, `sendStatus` 1.102×,
`warp` 842×, dst.).

**3. Pelajari folder `Origin Nexia` — aset grafis klien (BERIKUTNYA)**

Lokasi: `~/Documents/GitHub/Origin Nexia` (1,9 GB, klien NexusTK/Nexia asli;
ada juga salinan di `~/Downloads/Origin Nexia`). **Survei awal sudah
dilakukan 20 Agustus 2026 — jawabannya: YA, grafis peta bisa diambil dari
sini.** Ini melengkapi kekurangan yang dicatat sebelumnya (berkas `.map`
hanya menyimpan ID angka, tanpa gambar).

Isi `Origin Nexia/Data/` — 253 arsip `.dat`, 1,9 GB:

| Kelompok | Jumlah | Isi |
|---|---|---|
| `tile*.dat` + `tilec*.dat` | 52 (213 MB) | **grafis petak lantai/objek** — yang dirujuk `tile` & `obj` di `.map` |
| `mon*.dat` | 70 | sprite monster |
| `body/coat/helmet/hair/sword/…` | ±40 | sprite perlengkapan karakter |
| `efx*.dat` | 40 | efek |
| `mus*.dat`, `snd.dat` | 8 | audio |

Format arsip `.dat` **sudah terbaca**: 4 byte jumlah entri (LE), lalu tiap
entri 4 byte offset + 13 byte nama; entri terakhir sentinel (offset = akhir
berkas, nama kosong). Contoh: `tilec0.dat` memuat `tilec0.epf` (4,2 MB).

Isi seluruh arsip bila dipindai: **440 `.epf` (gambar), 170 `.pal`
(palet), 14 `.tbl`, 26 `.lst`, 26 `.lsr`, 19 `.dsc`**, plus 246 `.wav` &
66 `.mp3`. Jadi gambar DAN paletnya lengkap.

Yang belum dikerjakan: **dekoder EPF** (EPF + PAL → gambar RGB) dan
memetakan `tile`/`obj` id dari `.map` ke frame EPF yang benar — di sinilah
`rtk/SObj.tbl` (18.954 entri, masih di RTK-Server) dipakai untuk `obj`.

**4. Tools editor lokal: HTML + JavaScript**

Aplikasi sederhana yang dijalankan di lokal (cukup buka berkas HTML, tanpa
server/build), untuk:
- **Edit peta** — memuat `.map` dari `maps/`, menampilkan grid `tile`/
  `pass`/`obj`, menyunting, lalu menyimpan kembali ke format aslinya
  (header uint16 BE xs/ys + xs*ys x 3 uint16 BE — lihat `MapFile.java`).
- **Edit skrip Lua** — menyunting berkas di `luascript/`.

Catatan teknis untuk nanti: JavaScript membaca berkas lokal lewat
`<input type="file">` / File System Access API; endianness **big-endian**
harus eksplisit (`DataView.getUint16(off, false)`). Bila mau menampilkan
petaknya sebagai gambar, dekoder EPF dari langkah 3 jadi prasyarat —
tanpa itu, tampilkan dulu sebagai grid berwarna berdasarkan id/pass.

**5. Client game desktop: Java + libGDX**

Menggantikan klien RetroTK asli (Windows/`RetroTK.exe`). Konsekuensi
penting: begitu klien dibuat sendiri, **kewajiban byte-fidelity protokol
bisa ditinjau ulang** — tapi selama klien lama masih dipakai untuk
pengujian, protokol wire tetap tidak boleh berubah (lihat Peringatan #2).
libGDX = jar tambahan di `extLib/` (tetap tanpa Maven). Kemungkinan besar
jadi project terpisah dari server, berbagi kode protokol/format map.
Prasyarat: dekoder EPF (langkah 3).

### Trek paralel: melengkapi port server

1. Serialisasi `mmo_charstatus` + load/save karakter (char server
   0x3003/0x3803/0x3004) — prasyarat gameplay.
2. Gameplay map server — tiap subsistem selesai langsung mengisi stub
   `Bindings.java`.
3. Persistensi registry skrip ke tabel `Registry`/`RegistryString`/
   `NPCRegistry`/`QuestRegistry` via `CharDb`.

## Kebiasaan project

- Bahasa komunikasi user & dokumentasi: **Bahasa Indonesia** (istilah
  teknis tetap Inggris).
- Selesai mengubah kode: compile bersih (`-Xlint:all` boleh 1 warning
  try-with-resources di Sql), jalankan `./run.sh scripttest`, bersihkan
  `build/`/`dist/`/`logs/` (sudah di .gitignore).
- Update README.md + `extLib/README.md` bila menambah dependensi atau
  mengubah perilaku yang terdokumentasi.
