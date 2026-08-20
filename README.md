# RTK Java Version

Port Java SE dari server MMO **RetroTK** (bergaya NexusTK), yang aslinya
ditulis dalam C — sumbernya: [unkmc/RTK-Server](https://github.com/unkmc/RTK-Server).

> Catatan tentang project aslinya: walaupun sering disebut "server Lua",
> inti servernya sebenarnya ditulis dalam **C** (login/char/map server) —
> Lua (907 berkas) hanya dipakai sebagai bahasa *scripting konten game*
> (quest, spell, NPC, mob) yang dijalankan map server lewat binding `sl.c`.
> Project ini menerjemahkan inti server C tersebut ke Java, dan menjalankan
> konten Lua-nya **tanpa diubah** lewat LuaJ.

## Prasyarat

- **JDK 25** (level bahasa project = 25). Untuk mesin pengembangan;
  di server cukup **JRE/JDK 25** karena tidak ada proses build di sana.
- **NetBeans** sebagai IDE + build (membawa Ant sendiri). Opsional:
  `build.sh` untuk build cepat lewat `javac` saja.
- **MySQL** dengan database `RTK` (untuk login/char server; map server
  bisa hidup tanpa DB dengan fungsi terbatas).

Project ini **mandiri** — seluruh data game sudah ada di dalam repo, jadi
setelah clone tidak perlu mengunduh apa pun lagi:

| Folder | Isi |
|---|---|
| `maps/` | 3.544 berkas peta `.map` (~38 MB) |
| `luascript/` | 907 skrip `.lua` (~6,8 MB) |
| `database/` | skema + dump MySQL (~13 MB) |

Lokasi folder peta dan skrip bisa dipindah lewat `map_path` dan `lua_path`
di `conf/map.conf`.

## Quick start

Di mesin pengembangan:

```
# 1. build di NetBeans: Run > Clean and Build Project
#    (atau tanpa NetBeans: ./build.sh)

# 2. uji regresi scripting - harus ALL TESTS PASSED
./run.sh scripttest

# 3. jalankan ketiga server
./run.sh all          # login -> char -> map
./run.sh status
./run.sh stop
```

## Arsitektur

Sama seperti versi C, server terdiri dari 3 proses yang saling terhubung
lewat TCP:

```
klien game ──► LoginServer (port 2000)
                   │  0x1000..0x1004 / 0x2001..0x2004
                   ▼
               CharServer  (port 2005)  ──► MySQL (database RTK)
                   │  0x3000..0x3005 / 0x3800..0x3804
                   ▼
               MapServer   (port 2001)  ──► klien game (setelah redirect)
```

Port di atas adalah nilai efektif dari `conf/` (`inter.conf` →
`login_port: 2000`, `char_port: 2005`; `map.conf` → `map_port: 2001`), yang
menimpa default di `rtk-server.properties`. Hanya port login dan map yang
perlu terbuka ke publik.

Sejak lapisan jaringan dibuat per-instance, ketiganya **secara teknis bisa
dijalankan dalam satu JVM**; `run.sh` tetap menjalankannya sebagai tiga
proses terpisah demi isolasi restart dan crash. Satu-satunya sisa state
global adalah nama file di `ServerLog`.

## Model threading (lapisan jaringan)

Versi C memakai state global: satu tabel session, satu selector, satu
handler. Port ini **tidak** menirunya — tiap server punya instance
[`NetServer`](src/org/rtk/common/NetServer.java) sendiri (selector, tabel
session, dan handler terpisah), sehingga ketiganya bisa hidup berdampingan
dalam satu JVM tanpa saling menimpa.

Di dalam satu server, IO dipisahkan dari logika lewat antrean:

```
   [IO thread]  selector: accept / read / write
        │  append ke buffer baca session
        ▼
   ArrayBlockingQueue<Session>      (dedup: 1 entri per session)
        │
   [Logic thread]  timer → parse paket → susun balasan
        │  wfifoSet() → outbox (ConcurrentLinkedQueue<byte[]>)
        ▼
   [IO thread]  kirim isi outbox ke socket
```

Aturan konkurensi yang dipegang:

- **Buffer baca** (`rdata`) disentuh dua thread, jadi selalu di bawah
  monitor `Session`: IO mengunci saat menambah data, logic mengunci selama
  memproses paket.
- **Buffer tulis** (`wdata`) hanya milik logic thread. `wfifoSet()`
  menyalin paket jadi ke `outbox` yang thread-safe, jadi IO thread tak
  pernah menyentuh area penyusunan. Ini penting karena parser sering
  menulis ke **session lain** (mis. login meneruskan paket ke koneksi char
  server).
- **Tabel session** memakai `AtomicReferenceArray` — ditulis IO thread saat
  accept dan logic thread saat menutup koneksi.
- Antrean dibatasi (`FD_SETSIZE + 16`) tapi dijamin tak pernah penuh karena
  satu session hanya boleh antre satu kali; IO thread karena itu tidak
  pernah terblokir.
- Logic thread menunggu dengan timeout sampai timer berikutnya jatuh tempo,
  jadi tidak ada busy-loop.

**Logika permainan sengaja tetap satu thread per server.** Bukan karena
tidak bisa di-pool, tapi karena dua alasan yang mengikat: urutan paket per
koneksi harus terjaga (klien mengirim "gerak" lalu "serang"), dan engine
skrip LuaJ beserta coroutine per pemain **tidak thread-safe**. Yang dipecah
adalah IO dari logika — bagian yang memang aman dipecah.

## Membuka di NetBeans

Project ini adalah **NetBeans J2SE (Java with Ant) project** standar —
metadata di `nbproject/` di-generate dan dikelola NetBeans sendiri.
`build.xml` sengaja **dibiarkan dalam bentuk bawaan NetBeans** (tidak ada
target kustom), supaya IDE bebas mengelolanya tanpa risiko tertimpa.

| File | Ikut git? | Keterangan |
|---|---|---|
| `nbproject/project.xml` | ya | tipe project + daftar source root |
| `nbproject/project.properties` | ya | classpath `extLib/`, level bahasa, main class |
| `nbproject/build-impl.xml` | ya | **di-generate NetBeans — jangan diedit tangan** |
| `nbproject/genfiles.properties` | ya | checksum pemicu regenerasi `build-impl.xml` |
| `nbproject/private/` | tidak | setelan lokal per-mesin (path JDK, dsb.) |
| `build.xml` | ya | bentuk bawaan NetBeans, jangan tambah target di sini |
| `manifest.mf` | ya | manifest dasar untuk jar |

`File > Open Project…` lalu pilih folder **`RTK-java-version`** (yang berisi
`build.xml` dan `nbproject/`). Source root yang terdaftar: `src` (kode Java),
plus `resources`, `extLib`, dan `conf` supaya foldernya terlihat di project
tree.

### Main Class

Satu jar hanya bisa punya **satu** `Main-Class`, sedangkan RTK punya tiga
server. Karena itu ada kelas dispatcher [`org.rtk.RtkLauncher`](src/org/rtk/RtkLauncher.java)
— set inilah sebagai Main Class:

> **Project Properties > Run > Main Class = `org.rtk.RtkLauncher`**

Server dipilih lewat argumen pertama:

```
java -jar RTK-java-version.jar login       # login server
java -jar RTK-java-version.jar char        # char server
java -jar RTK-java-version.jar map         # map server
java -jar RTK-java-version.jar scripttest  # uji regresi scripting
```

Argumen berikutnya diteruskan apa adanya ke server, jadi opsi asli tetap
jalan: `java -jar RTK-java-version.jar login --conf conf/login.conf`.
Untuk menjalankan satu server langsung dari tombol **Run** di NetBeans,
isi *Arguments* dengan `login` / `char` / `map`.

## Cara build

**Alur yang dipakai: compile di lokal, deploy manual ke server.** Server
tidak perlu JDK lengkap atau proses build sama sekali.

### 1. Build di NetBeans (jalur utama)

`Run > Clean and Build Project` menghasilkan:

```
dist/
├── RTK-java-version.jar     # kelas RTK (+ salinan jar extLib sebagai resource)
└── lib/                     # dependensi yang benar-benar dipakai saat runtime
    ├── HikariCP-5.1.0.jar
    ├── log4j-api-2.24.3.jar
    └── ... (5 lainnya)
```

> **Penting:** manifest jar utama menunjuk ke `lib/` lewat `Class-Path`,
> jadi **folder `dist/lib/` harus ikut disalin** ke server — jar utama
> saja tidak cukup. (Jar dependensi memang juga ikut terbungkus di dalam
> jar utama karena `extLib` terdaftar sebagai source root, tapi jar
> bersarang tidak bisa dimuat JVM; yang benar-benar dipakai adalah
> `dist/lib/`.)

### 2. Salin ke server

Yang perlu ada di server:

```
/opt/rtk-java/
├── dist/          # hasil build NetBeans (jar + lib/)
├── conf/          # konfigurasi (login/char/inter/map/lang .conf)
├── maps/          # berkas peta .map
├── luascript/     # skrip Lua
├── meta/          # meta file yang dikirim ke klien
├── logs/          # dibuat otomatis; output & PID
└── run.sh
```

### 3. Jalankan dengan run.sh

[`run.sh`](run.sh) membungkus `java -jar <jar> <server> &`:

```
./run.sh login        # jalankan login server di background
./run.sh char
./run.sh map
./run.sh all          # ketiganya berurutan dengan jeda
./run.sh status       # cek yang hidup
./run.sh stop         # hentikan semua
./run.sh scripttest   # foreground, uji regresi
```

Setiap server ditulis PID-nya ke `logs/<server>.pid` dan output konsolnya
ke `logs/<server>.console.log`. Opsi JVM: `JAVA_OPTS="-Xmx512m" ./run.sh login`.

### Alternatif: build.sh (javac murni, tanpa NetBeans)

[`build.sh`](build.sh) meng-compile dengan `javac --release 25` dan mengemas
`dist/RTK-java.jar` (Main-Class sama: `org.rtk.RtkLauncher`). Berguna untuk
uji cepat di mesin tanpa NetBeans (mis. menjalankan regresi di CI). `run.sh`
menerima kedua nama jar dan memilih yang paling baru.

```
./build.sh
./run.sh scripttest
```

- **Java SE untuk logika inti**, dengan library eksternal di
  [`extLib/`](extLib/README.md) (tanpa Maven): driver JDBC MySQL,
  **HikariCP** (pool koneksi), **Log4j2** (logging), dan **LuaJ**
  (menjalankan skrip Lua). MD5 (`java.security`), zlib/CRC32
  (`java.util.zip`), dan networking (`java.nio`) memakai API Java SE standar.
- Level bahasa: **Java 25** (`javac.source`/`javac.target` = 25 di
  `nbproject/project.properties`, `--release 25` di `build.sh`). Fitur
  bahasa modern boleh dipakai — mis. `RtkLauncher` memakai switch
  expression. **Konsekuensi: server wajib punya JRE/JDK 25 atau lebih
  baru.**

## Menjalankan

1. Siapkan database MySQL `RTK` memakai `database/` di project ini:
   `database/scripts/` berisi 21 skrip migrasi (52 tabel) yang dijalankan
   berurutan oleh `database/migrate.sh`, dan
   `database/2020-09-02-21-55-01_RTK.sql.bak` adalah dump lengkap berisi
   data konten — antara lain **7.974 baris `Maps`** dan **4.476 baris
   `Warps`** yang dipakai map server.
2. Sesuaikan `conf/char.conf` (kredensial SQL), `conf/inter.conf`
   (id/pw antar-server), `conf/map.conf` (IP publik map server).
   Format file konfigurasi **identik dengan versi C** — file di folder
   `conf/` disalin langsung dari repo asli.
3. Jalankan berurutan dengan `./run.sh all` (login → char → map, ada
   jeda antar server), atau satu per satu: `./run.sh login`, tunggu log
   "Connected to Login Server", lalu `./run.sh char` dan `./run.sh map`.

### Konfigurasi teknis (resources/rtk-server.properties)

Nilai yang di versi C berupa hardcode (key enkripsi `ENCKEY`, key handshake
`KruIn7inc`, URL patch, port default, ambang lockout brute-force, interval
reconnect antar-server, tuning pool HikariCP, ukuran buffer socket, default
path skrip Lua) sekarang berada di
[`resources/rtk-server.properties`](resources/rtk-server.properties).
File ini dimuat dari **classpath** oleh `common/Props.java` (ikut ter-copy
ke `build/classes` dan jar oleh target `resources`), dengan fallback ke
`resources/rtk-server.properties` di working directory.

Urutan prioritas konfigurasi:

1. **`rtk-server.properties`** — nilai default teknis saat start.
2. **`conf/*.conf`** (format C asli) — dibaca setelahnya dan **menimpa**
   nilai yang sama (mis. `login_port`, `char_port`, `map_port`, `map_path`,
   `lua_path`).

Lokasi data game memakai pola yang sama, dari paling lemah ke paling kuat:

| Data | properties | conf/map.conf | argumen CLI |
|---|---|---|---|
| Peta | `map.path=maps` | `map_path: maps` | `./run.sh maptest <path>` |
| Skrip Lua | `lua.path=luascript` | `lua_path: luascript` | `./run.sh scripttest <path>` |

Bawaannya menunjuk folder di dalam project ini, jadi server jalan tanpa
konfigurasi tambahan.

File/key yang hilang tidak pernah menghentikan server — setiap pembacaan
punya nilai fallback bawaan yang identik dengan perilaku C asli.

> Peringatan: `crypt.enckey` dan `crypt.handshake_key` adalah bagian dari
> protokol klien RetroTK. Mengubahnya membuat klien resmi tidak bisa
> terhubung — hanya ubah bila Anda juga mengubah sisi klien.

Urutan startup, argumen CLI (`--conf`, `--inter`, `--lang`), dan protokol
wire semuanya mengikuti versi C, sehingga klien RetroTK dan tool yang sudah
ada tetap kompatibel. Output diagnostik konsol (dulu `printf`/`System.out`)
sekarang dikelola Log4j2 — lihat bagian di bawah.

## Deploy di CentOS (systemd)

Prinsip: **tidak ada compile di server.** Build di lokal (NetBeans), salin
hasilnya, jalankan.

1. Pasang **JRE/JDK 25** di server. Repo bawaan CentOS umumnya belum
   menyediakannya, jadi ambil dari Adoptium/Temurin atau Oracle, mis.:

   ```
   sudo dnf install -y java-25-openjdk        # bila tersedia di repo
   # atau unduh Temurin 25 dan set JAVA_HOME
   java -version                              # pastikan 25+
   ```

2. Siapkan direktori dan user:

   ```
   sudo useradd -r -s /sbin/nologin rtk
   sudo mkdir -p /opt/rtk-java
   ```

3. Dari mesin lokal, salin hasil build dan pendukungnya:

   ```
   rsync -av dist conf maps luascript meta run.sh rtk@server:/opt/rtk-java/
   sudo chown -R rtk:rtk /opt/rtk-java
   ```

   Pastikan **`dist/lib/` ikut tersalin** — manifest jar menunjuk ke sana.

4. Jalankan. Cara paling sederhana lewat `run.sh`:

   ```
   sudo -u rtk /opt/rtk-java/run.sh all
   sudo -u rtk /opt/rtk-java/run.sh status
   ```

   Atau sebagai service systemd, contoh
   `/etc/systemd/system/rtk-login.service`:

   ```ini
   [Unit]
   Description=RTK Login Server
   After=network.target mysqld.service

   [Service]
   User=rtk
   WorkingDirectory=/opt/rtk-java
   ExecStart=/usr/bin/java -Xmx256m -jar dist/RTK-java-version.jar login
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

   Duplikasi untuk `rtk-char.service` (argumen `char`,
   `After=… rtk-login.service`) dan `rtk-map.service` (argumen `map`,
   `After=… rtk-char.service`). Perhatikan: systemd menjalankan proses di
   foreground, jadi **tanpa** `&` — biarkan systemd yang mengelola.

5. Aktifkan dan buka port:

   ```
   sudo systemctl daemon-reload
   sudo systemctl enable --now rtk-login rtk-char rtk-map
   sudo firewall-cmd --permanent --add-port={2000,2001,2005}/tcp && sudo firewall-cmd --reload
   ```

   (Sesuaikan dengan port di `conf/`; hanya port login dan map yang perlu
   terbuka ke publik, port char cukup antar-server.)

6. Pantau: `journalctl -u rtk-login -f`, atau file rolling per komponen di
   `/opt/rtk-java/logs/`. Bila dijalankan lewat `run.sh`, output konsol ada
   di `logs/<server>.console.log`.

## Connection pooling (HikariCP)

`common/Sql.java` tidak lagi memegang satu koneksi JDBC tunggal seperti
`db_mysql.c` — setiap `connect()` membangun sebuah **HikariCP pool**
(`extLib/HikariCP-5.1.0.jar`), dan setiap query mengambil/mengembalikan
koneksi dari pool tersebut lewat try-with-resources. Konfigurasi pool per
server (di `Sql.connect()`): maksimum 10 koneksi, minimum idle 2, connection
timeout 10 detik, idle timeout 10 menit, max lifetime 30 menit, plus
prepared-statement caching. Nama pool otomatis mengikuti nama database
(`RTK-<db>`) sehingga mudah dibedakan di log saat ketiga server berjalan
bersamaan.

## Logging (Log4j2)

Semua `System.out.println`/`printf` diagnostik sudah diganti dengan logger
Log4j2 (`org.apache.logging.log4j.Logger`, satu instance per kelas). Konfigurasi
ada di [`resources/log4j2.xml`](resources/log4j2.xml) dan otomatis ikut ter-copy
ke `build/classes`/jar oleh `build.xml`. Setiap komponen menulis ke file log
rolling-nya sendiri di `logs/`, sambil tetap tampil di console NetBeans:

| Package | File log | Isi |
|---|---|---|
| `org.rtk.login.*` | `logs/login.log` | Login server (clif, intif) |
| `org.rtk.charserver.*` | `logs/char.log` | Char server (logif, mapif, char_db) |
| `org.rtk.map.*` | `logs/map.log` | Map server (skeleton + intif) |
| `org.rtk.common.*` + root (termasuk log internal HikariCP) | `logs/common.log` | Socket, timer, config, SQL/HikariCP |

File roll otomatis **setiap hari (tengah malam)**, diarsip sebagai
`login-2026-08-19.log.gz` dst. dan digzip. Retensi diatur lewat aksi
`Delete` + `IfLastModified age="30d"` pada tiap `<RollingFile>`, jadi
arsip lebih tua dari **30 hari dihapus otomatis** setiap kali terjadi
rollover — dihitung dari waktu modifikasi file, bukan sekadar jumlah
generasi, sehingga tetap akurat meski server sempat mati beberapa hari.
Ubah properti `RETENTION_DAYS` di `log4j2.xml` untuk mengubah masa retensi.
Level default `INFO`; ubah `level="..."` per `<Logger>` di `log4j2.xml`
untuk melihat log `DEBUG` (mis. hex dump paket tak dikenal di
`LoginClif.clifDebug`).

> Catatan: mekanisme `ServerLog.addLog()` / `ServerLog.logAdd()` (port dari
> `add_log()`/`Log_Add()` di C, yang menulis ke file bernama dinamis seperti
> `logs/regreject.log`, `logs/validlogin.log`, `logs/BANNED.log` sesuai nama
> event) **tetap dipertahankan apa adanya** — itu bukan `System.out.println`
> dan merupakan mekanisme logging *game event* terpisah yang formatnya
> sengaja dibuat identik dengan versi C, bukan log diagnostik server.

## Status port

| Komponen C | File Java | Status |
|---|---|---|
| `common/crypt.c` | `common/Crypt.java` | ✅ penuh (XOR crypt, key table, packet indexes; round-trip teruji) |
| `common/md5calc.c` | `common/Md5.java` | ✅ penuh (via MessageDigest) |
| `common/socket.c` + macro RFIFO/WFIFO | `common/NetServer.java`, `common/Session.java` | ✅ penuh — **instance per server**, java.nio selector di IO thread sendiri, serah terima paket lewat `ArrayBlockingQueue`; throttle + IP lockout |
| `common/timer.c` | `common/TimerSystem.java` | ✅ penuh — **instance per server**, berjalan di logic thread |
| `common/core.c` | `common/Core.java` | ✅ penuh — **instance per server**; logic thread yang mengonsumsi antrean paket (bukan lagi loop sekuensial global) |
| `common/db_mysql.c` | `common/Sql.java` | ✅ via JDBC + PreparedStatement |
| config reader (`config_read`) | `common/Config.java` | ✅ penuh |
| **login server** (`login.c`, `clif.c`, `intif.c`) | `login/LoginServer.java`, `login/LoginClif.java`, `login/LoginIntif.java` | ✅ **penuh** — versi check, login, buat karakter, ganti password, meta file (zlib+CRC32), maintenance mode, require_reg, banned IP, brute-force lockout, redirect ke map server |
| **char server** (`char.c`, `logif.c`, `mapif.c`, `char_db.c`) | `charserver/CharServer.java`, `Logif.java`, `Mapif.java`, `CharDb.java` | ✅ handshake login & map, autentikasi karakter, buat karakter, ganti password, routing map. ⚠️ blob `mmo_charstatus` (load/save karakter penuh, 0x3003/0x3004) & board/mail belum |
| **map server** (`map.c`, `intif.c` + 33rb baris gameplay) | `map/MapServer.java`, `map/MapIntif.java` | ⚠️ **skeleton** — konek+auth ke char server, daftar map, terima routing pemain (0x3802→0x3002) sehingga alur login lengkap; gameplay belum |
| **scripting engine** (`sl.c`, 11rb baris) | `map/script/ScriptEngine.java`, `ScriptClass.java`, `ScriptInstance.java`, `Bindings.java`, `ScriptPlayer.java` | ✅ **arsitektur inti jalan via LuaJ** — seluruh 906 skrip Lua asli termuat tanpa error; object model typel (__index: getattr→prototype→data table), dispatch `root.method`, coroutine `_async` + primitif dialog blocking (`menu`/`dialog`/`input`) teruji end-to-end lewat `Accepted/player.lua` asli. ⚠️ baru ±30 dari ±209 method player yang riil; sisanya stub warn-once menunggu port engine gameplay |
| save server (`saveif.c` — di C pun sudah dinonaktifkan) | — | ❌ tidak diport (timer koneksinya di-comment di C) |

## Catatan desain

- **Endianness** — host C-nya little-endian: `RFIFOW/WFIFOW` = akses LE,
  `SWAP16/SWAP32` = akses BE. Di Java keduanya eksplisit:
  `rfifoW/wfifoW` (LE, protokol antar-server) dan `rfifoWBE/wfifoWBE`
  (BE, protokol klien).
- **fd sebagai identitas sesi** — indeks sesi integer dipertahankan karena
  ikut dikirim dalam protokol antar-server (login meneruskan fd kliennya
  ke char server dan menerimanya kembali).
- **SQL injection** — versi C menyisipkan string langsung ke query;
  port ini memakai `PreparedStatement` dengan perilaku yang sama.
- **Threading** — state jaringan global versi C diganti instance per
  server, dan IO dipisah dari logika lewat antrean. Logika permainan
  sengaja tetap satu thread per server (urutan paket + LuaJ tidak
  thread-safe); lihat [Model threading](#model-threading-lapisan-jaringan).
- Bug kecil versi C yang **sengaja dipertahankan** demi kompatibilitas:
  autentikasi antar-server hanya menolak bila id **dan** pw dua-duanya
  salah (`strcmp(a) && strcmp(b)`), dicatat di komentar kode.
- Bug versi C yang **diperbaiki** (dicatat di komentar): parsing
  `start_money` / `start_point` di `char.c` memakai `strcmpi(...) == 1`
  sehingga tidak pernah aktif; di port ini berfungsi normal.

## Scripting engine (LuaJ) — menjalankan skrip Lua asli

Paket `org.rtk.map.script` adalah port arsitektur `sl.c` di atas **LuaJ**
(`extLib/luaj-jse-3.0.1.jar`, interpreter Lua murni Java), sehingga konten
game (900+ berkas di `luascript/`) berjalan **tanpa diubah**:

- **Loading** meniru `sl_init`/`sl_reload`: `Developers/sys.lua` dimuat
  pertama, lalu seluruh `.lua` di `Accepted/` dan `Developers/` (rekursif,
  `sys.lua` di-skip). Status saat ini: **906/906 file termuat, 0 error**.
- **Object model `typel`**: Player/NPC/Mob/registry adalah userdata dengan
  metatable bersama; `__index` mencari di getter Java → prototype → data
  table, persis urutan `typel_mtindex` di C. Prototype `Player` diekspos
  sebagai global sehingga `Accepted/player.lua` bisa menambahkan method
  level-tinggi (`menuString`, `dialogSeq`, banking, dll.) dari sisi Lua —
  layering yang sama dengan server C.
- **Dialog blocking** memakai coroutine LuaJ: `_async()` membuat coroutine
  per pemain (`sd->coref`), primitif `menu`/`dialog`/`input`/`menuSeq`/
  `inputSeq` melakukan yield, dan engine me-resume dengan jawaban klien
  (indeks menu berupa angka; `dialog` dengan "next"/"previous"/"quit").
- **Dispatch event**: `doScript("blood", "click", playerRef)` =
  `sl_doscript_blargs`, terlindung error handler.
- Binding yang belum diimplementasi terdaftar sebagai **stub warn-once** —
  skrip tetap termuat dan setiap binding yang kurang muncul di log, bukan
  membuat loader crash.

Uji: `./run.sh scripttest` — memuat seluruh skrip lalu menjalankan
interaksi NPC lengkap (klik → menu → pilih → dialog 2 halaman → pemberian
item → tulis registry) melalui `player.lua` asli, dengan pemain tiruan.
Path skrip diatur lewat `lua_path` di `conf/map.conf` (default
`luascript`, folder di dalam project); `lua_enable: 0` mematikannya.

## Pengujian

Belum ada framework unit test (sengaja, agar tetap Java SE murni); gerbang
regresi utamanya adalah **`./run.sh scripttest`** (`map/script/ScriptTest.java`),
yang harus selalu hijau:

- **Fase 1** — memuat `sys.lua` + seluruh `Accepted/` dan `Developers/`
  dari `luascript/`; **gagal bila ada satu berkas pun yang error**
  (saat ini 906/906 OK).
- **Fase 2** — menjalankan interaksi NPC lengkap dengan pemain tiruan
  melalui `Accepted/player.lua` asli: klik → yield di menu → resume pilihan
  → dialog 2 halaman ("next"/"next") → addItem → tulis registry → coroutine
  selesai → interaksi kedua. 14 assertion, exit code 1 bila ada yang gagal.

> **Penting:** `ScriptTest` **tidak menyentuh lapisan jaringan sama sekali**.
> Kalau mengubah `NetServer`/`Session`/`Core`, uji dengan tes integrasi TCP
> sungguhan — buka listen port, sambungkan socket klien, lalu pastikan:
> handler accept berjalan, beberapa paket berturut-turut diproses **sesuai
> urutan**, timer tetap jalan, session ditutup dan slotnya dipakai ulang,
> serta puluhan koneksi paralel semuanya benar. Pola ini sudah dipakai saat
> refactor threading dan seluruhnya lolos.

Uji manual lain yang berguna: jalankan map server tanpa MySQL — harus tetap
hidup dengan peringatan (fallback map 0), dan log per komponen muncul di
`logs/`. Cek pemisahan thread dengan `jcmd <pid> Thread.print` — harus ada
thread `rtk-io-<nama>` terpisah dari thread logika. Alur login end-to-end
membutuhkan MySQL berisi database `RTK` dan klien RetroTK.

## Status & roadmap

### Tahap 1 — selesai

Fondasi server-side sudah berdiri dan terverifikasi: login server port
penuh, char server port inti, map server skeleton dengan routing pemain
jalan ujung-ke-ujung, scripting engine memuat 906/906 skrip Lua asli,
plus infrastruktur (HikariCP, Log4j2, properties, build NetBeans/`build.sh`,
deploy `run.sh`) dan arsitektur jaringan instance-per-server dengan IO
thread terpisah.

### Tahap 2 — rencana berikutnya

1. **Analisa berkas peta** — ✅ **selesai**. Format `.map` sudah dibedah dan
   ada pembacanya: [`map/data/MapFile.java`](src/org/rtk/map/data/MapFile.java),
   diperiksa lewat `./run.sh maptest`. Header `uint16 BE xs, ys`, lalu
   `xs*ys` × {`tile`, `pass`, `obj`} masing-masing `uint16` BE — cocok pada
   **3.544/3.544 berkas** (6,7 juta petak, muat ±160 ms). `pass` 0 = bisa
   dilewati. Server hanya membaca `Accepted/`; `TKR Maps/` salinan lama.
   Metadata dari tabel `Maps` — 7.648 peta berbagi hanya 2.640 berkas
   geometri. Warp dari tabel `Warps`, bukan dari `warps.txt`.
2. **Analisa skrip Lua** (`luascript/`) — skripnya sudah *berjalan*, tapi isinya belum
   dipetakan. Analisa ini menentukan urutan port subsistem gameplay:
   dahulukan binding yang paling sering dipanggil (`sendMinitext` 2.902×,
   `dialogSeq` 2.343×, `sendStatus` 1.102×, `warp` 842×).
3. **Pelajari folder `Origin Nexia`** (klien NexusTK asli, 1,9 GB) — survei
   awal sudah menjawab pertanyaan lama: **grafis peta memang ada di sana.**
   `Data/` berisi 253 arsip `.dat`; 52 di antaranya (`tile*`, `tilec*`,
   213 MB) adalah grafis petak yang dirujuk kolom `tile`/`obj` di `.map`.
   Format arsipnya sudah terbaca, dan isinya mencakup **440 berkas `.epf`
   (gambar) beserta 170 `.pal` (palet)**. Yang belum: dekoder EPF→gambar
   dan pemetaan id petak ke frame yang benar.
4. **Tools editor lokal berbasis HTML + JavaScript** — dijalankan langsung
   di browser tanpa server/build, untuk menyunting berkas `.map` di
   `maps/` dan skrip Lua di `luascript/`.
5. **Client game desktop dengan Java + libGDX** — menggantikan klien
   RetroTK asli. libGDX ditaruh sebagai jar di `extLib/` (tetap tanpa
   Maven), kemungkinan project terpisah yang berbagi kode protokol dan
   format map. Prasyarat: dekoder EPF dari langkah 3.

### Trek paralel: melengkapi port server

1. **Serialisasi `mmo_charstatus`** (`mmo.h`, `char_db.c` 1.700 baris) —
   load/save karakter penuh sebagai blob terkompresi zlib (0x3003/0x3803,
   0x3004). Prasyarat untuk gameplay.
2. **Gameplay map server** (`map.c`, `pc.c`, `mob.c`, `npc.c`, `clif.c`
   ±33.000 baris). Setiap subsistem yang selesai langsung mengisi binding
   stub di `map/script/Bindings.java` (±209 method player dipakai skrip;
   ±30 sudah riil).
3. **Persistensi registry skrip** — `player.registry` dkk. saat ini
   in-memory; sambungkan ke tabel `Registry`/`RegistryString`/
   `NPCRegistry`/`QuestRegistry` via `CharDb`.

## Struktur folder

```
RTK-java-version/
├── build.xml                    # bentuk bawaan NetBeans (jangan tambah target)
├── manifest.mf                  # manifest dasar untuk jar
├── build.sh                     # alternatif build tanpa NetBeans (javac --release 25)
├── run.sh                       # start/stop server: java -jar <jar> <server> &
├── nbproject/                   # metadata NetBeans J2SE project
│   ├── project.xml              #   tipe project + source root
│   ├── project.properties       #   classpath extLib, level bahasa 25, main class
│   ├── build-impl.xml           #   DI-GENERATE NetBeans (jangan diedit tangan)
│   ├── genfiles.properties      #   checksum pemicu regenerasi build-impl.xml
│   └── private/                 #   setelan lokal per-mesin (.gitignore)
├── CLAUDE.md                    # panduan untuk pengembangan berbantuan AI
├── src/org/rtk/
│   ├── RtkLauncher.java         # Main-Class jar: dispatcher login/char/map/scripttest
│   ├── common/                  # port rtk/src/common: Crypt, Session/NetServer,
│   │                            #   TimerSystem, Core, Config, Sql (HikariCP),
│   │                            #   ServerLog, Props, Md5
│   ├── login/                   # port rtk/src/login (LoginServer, LoginClif, LoginIntif)
│   ├── charserver/              # port rtk/src/char (CharServer, Logif, Mapif, CharDb)
│   └── map/                     # port (skeleton) rtk/src/map (MapServer, MapIntif)
│       └── script/              # port sl.c di atas LuaJ (ScriptEngine, ScriptClass,
│                                #   ScriptInstance, Bindings, ScriptPlayer, ScriptTest)
├── resources/
│   ├── log4j2.xml               # konfigurasi logging (rolling harian, retensi 30 hari)
│   └── rtk-server.properties    # default teknis (crypt key, port, pool, buffer, lua path)
├── extLib/                      # 7 jar eksternal (JDBC, HikariCP, Log4j2+SLF4J, LuaJ) — tanpa Maven
├── conf/                        # file konfigurasi format C asli (menimpa properties)
├── maps/                        # 3.544 berkas peta .map (~38 MB)
├── luascript/                   # 907 skrip .lua (~6,8 MB)
├── meta/                        # meta file yang dikirim ke klien
├── logs/                        # log server + console log + PID (.gitignore)
├── build/  dist/                # hasil build (.gitignore)
└── .gitignore
```
