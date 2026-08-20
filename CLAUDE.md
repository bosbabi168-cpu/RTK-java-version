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
kecil, dan isi project ada **langsung di root repo** (tidak ada lagi
subfolder `RTK-java/`; user memindahkannya naik satu level pada 19 Agustus
2026). Repo asli C ada sebagai **tetangga**:

```
GitHub/
├── RTK-Server/          # sumber C + rtklua (repo terpisah)
└── RTK-java-version/    # project ini (repo sendiri, ada build.xml + nbproject/)
```

Konsekuensi: path relatif ke skrip Lua adalah **`../RTK-Server/rtklua`**
(bukan `../../`). Nilai ini muncul di 4 tempat yang harus konsisten:
`conf/map.conf` (`lua_path`), `resources/rtk-server.properties`
(`lua.path`), default di `MapServer.java`, dan default argumen di
`ScriptTest.java`. Kalau folder dipindah lagi, perbarui keempatnya.

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
     `NetServer` sama sekali.
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

**1. Analisa `rtkmaps`** (`../RTK-Server/rtkmaps`, 7.082 file `.map`, 87 MB)

Format `.map` sudah diketahui dari `map.c` (baris ~1185–1220) dan
terverifikasi pada `Accepted/256blank.map`:

```
header : uint16 BE xs, uint16 BE ys
tiles  : xs*ys x { uint16 BE tile, uint16 BE pass, uint16 BE obj }
```

Cek ukuran: 256×256 × 3 × 2 byte + 4 byte header = 393.220 byte = ukuran
file sebenarnya. `pass` adalah data tabrakan (collision), `tile` layer
lantai, `obj` layer objek. Metadata peta (nama, BGM, PvP, cahaya, cuaca,
batas level, dsb.) TIDAK ada di file ini — datangnya dari tabel MySQL
`Maps`, kolom `MapFile` menunjuk ke nama file `.map`.
`rtkmaps/warps.txt` (1.329 baris) berformat
`source,x,y,destination,x,y`. Target tahap ini: loader `.map` di Java +
struktur data peta, prasyarat untuk gameplay maupun client.

**2. Analisa `rtklua`** (`../RTK-Server/rtklua`, 907 file, ~164rb baris)

Sudah *berjalan* lewat LuaJ, tapi belum *dianalisa isinya*. Tujuannya
memetakan binding apa saja yang benar-benar dibutuhkan konten: ±209 method
player dipakai skrip, baru ±30 yang riil di `Bindings.java`. Analisa ini
yang menentukan urutan port subsistem gameplay (`pc.c`, `mob.c`, `npc.c`,
`clif.c`) — implementasikan yang paling sering dipanggil lebih dulu
(`sendMinitext` 2.902×, `dialogSeq` 2.343×, `sendStatus` 1.102×,
`warp` 842×, dst.).

**3. Client game desktop: Java + libGDX**

Menggantikan klien RetroTK asli (Windows/`RetroTK.exe`). Konsekuensi
penting: begitu klien dibuat sendiri, **kewajiban byte-fidelity protokol
bisa ditinjau ulang** — tapi selama klien lama masih dipakai untuk
pengujian, protokol wire tetap tidak boleh berubah (lihat Peringatan #2).
libGDX = jar tambahan di `extLib/` (tetap tanpa Maven). Kemungkinan besar
jadi project terpisah dari server, berbagi kode protokol/format map.

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
