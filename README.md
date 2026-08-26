# RTK Java Version

*Bahasa Indonesia · [English](README.en.md)*

Port Java SE dari server MMO **RetroTK** (bergaya NexusTK), yang aslinya
ditulis dalam C — sumbernya: [unkmc/RTK-Server](https://github.com/unkmc/RTK-Server).

> Catatan tentang project aslinya: walaupun sering disebut "server Lua",
> inti servernya sebenarnya ditulis dalam **C** (login/char/map server) —
> Lua (907 berkas) hanya dipakai sebagai bahasa *scripting konten game*
> (quest, spell, NPC, mob) yang dijalankan map server lewat binding `sl.c`.
> Project ini menerjemahkan inti server C tersebut ke Java, dan menjalankan
> konten Lua-nya **tanpa diubah** lewat LuaJ.

## Arah project (diperbarui 26 Agustus 2026)

**Protokol RetroTK akan diganti dengan rancangan sendiri, dan klien dibuat
sendiri memakai libGDX.** Kompatibilitas byte-per-byte dengan klien RetroTK
asli **bukan lagi tujuan**.

Konsekuensinya untuk siapa pun yang membaca kode ini:

- Yang **terbawa** ke protokol baru: 906 skrip Lua, 9.850 peta, 4.476 portal,
  716 jenis mob, 2.545 item, dan binding logika permainan.
- Yang **akan ditulis ulang**: seluruh lapisan paket `clif_*`. Karena itu
  `Clif.sendMyStatus()` sengaja dibiarkan setengah jadi.
- Kesetiaan pada sumber C tetap dijaga untuk **logika**, tidak untuk format
  kabel.

Sebelum keputusan ini, klien RetroTK asli sempat **berhasil masuk dunia**
setelah empat bug server ditutup (lihat "Status & roadmap"). Keempatnya lolos
dari 294 assertion uji — bukti bahwa uji buatan sendiri tidak bisa
menggantikan klien nyata.

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

# 2. uji regresi - semuanya harus lolos
./run.sh scripttest
./run.sh maptest
./run.sh chartest
./run.sh worldtest
./run.sh cliftest
./run.sh dbtest       # butuh MySQL (lihat bagian "Menjalankan")
./run.sh luaaudit     # alat bantu: pemeriksa statis skrip Lua

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
java -jar RTK-java-version.jar maptest     # uji regresi berkas peta
java -jar RTK-java-version.jar chartest    # uji serialisasi karakter
java -jar RTK-java-version.jar worldtest   # uji dunia peta / penempatan pemain
java -jar RTK-java-version.jar cliftest    # uji paket klien, gerakan, portal
java -jar RTK-java-version.jar dbtest      # uji lapisan database (butuh MySQL)
java -jar RTK-java-version.jar luaaudit    # pemeriksa statis skrip Lua
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

1. Siapkan database MySQL `RTK` memakai `database/` di project ini.
   `database/2020-09-02-21-55-01_RTK.sql.bak` adalah dump lengkap (54 tabel)
   berisi data konten — antara lain **9.850 baris `Maps`** dan **4.476 baris
   `Warps`** yang dipakai map server. `database/scripts/` berisi 21 skrip
   migrasi yang dijalankan berurutan oleh `database/migrate.sh`, berguna
   bila ingin menelusuri riwayat perubahan skema.

   > **Jebakan di Ubuntu/Pop!_OS:** `root` MySQL memakai plugin
   > `auth_socket`, **bukan** password kosong — hanya bisa diakses lewat
   > `sudo mysql`. Gejalanya `ERROR 1698 (28000)`, bukan `1045`. Buat user
   > `rtk` yang sudah tertulis di `conf/char.conf` supaya konfigurasi tidak
   > perlu diubah:

   ```bash
   sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
     GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; FLUSH PRIVILEGES;"

   mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
   ```

   Dump-nya sudah memuat `CREATE DATABASE RTK`, jadi tidak perlu membuatnya
   lebih dulu. **Perhatikan:** baris pertamanya `DROP DATABASE IF EXISTS
   RTK` — periksa dulu isi database `RTK` yang ada sebelum mengimpor ulang.
   Dump dibuat di MySQL 5.7 dan terbukti terimpor ke 8.0.46 tanpa
   penyesuaian. Setelah selesai, `./run.sh dbtest` harus hijau.
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
| `org.rtk.map.*` | `logs/map.log` | Map server (dunia, paket klien, intif, scripting) |
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

## Penyimpanan karakter (mmo_charstatus)

Seluruh keadaan pemain berpindah antara char server dan map server lewat
paket 0x3003 (minta) → 0x3803 (kirim), lalu 0x3004 (simpan) atau 0x3007
(simpan + logout). Penyimpanan sebenarnya tetap MySQL, ditangani
[`CharPersistence`](src/org/rtk/charserver/CharPersistence.java) yang
memuat/menyimpan 11 tabel (`Character`, `Inventory`, `Equipment`, `Banks`,
`SpellBook`, `Aethers`, `Legends`, `Kills`, `Registry`, `RegistryString`,
`NPCRegistry`, `QuestRegistry`).

**Formatnya sengaja berbeda dari versi C.** Di C, isi struct dikirim
sebagai dump memori mentah. Setelah header C-nya dikompilasi untuk
memastikan, angkanya: **3.171.352 byte per karakter**, dengan **82% hanya
array registry yang nyaris selalu kosong** — dan ukuran itu **berbeda antar
target kompilasi** (3.171.352 di 64-bit, 3.168.952 pada build 32-bit asli,
gara-gara `unsigned long`). Jadi tata letak biner C bukan kontrak yang
stabil, sementara kedua ujung paket ini sama-sama kode Java kita sendiri.

[`CharStatusCodec`](src/org/rtk/common/mmo/CharStatusCodec.java) memakai
format ringkas berversi: magic `"RTKC"` + nomor versi, string
panjang-berprefiks, koleksi hanya menulis entri yang terisi, dan hasil
akhirnya tetap dikompresi zlib sehingga lapisan transportasi tidak berubah.

| | struct C | format ini |
|---|---|---|
| karakter baru | 3.171.352 byte | **27 byte** |
| karakter terisi penuh | 3.171.352 byte | **4.263 byte** (743× lebih kecil) |

Konsekuensinya: char server C tidak bisa dipasangkan dengan map server Java
(dan sebaliknya) — trade-off yang diambil sadar. Bila tata letak berubah,
naikkan `CharStatusCodec.VERSION` supaya blob lama ditolak dengan pesan
jelas, bukan salah dibaca diam-diam.

## Dunia peta & pemain

Map server memuat seluruh peta miliknya saat start: metadata dari tabel
`Maps` (disaring `MapServer = ServerId`) digabung geometri dari berkas
`.map`. Karena 9.850 baris `Maps` hanya menunjuk 2.919 berkas unik,
geometrinya di-cache per nama berkas — peta yang bentuknya sama tidak
dibaca berulang dari disk, tapi tetap punya isi (pemain, mob) sendiri.

**Indeks spasial.** Tiap peta dibagi menjadi blok 8×8 petak; setiap blok
menyimpan daftar benda di dalamnya. Pertanyaan "siapa saja di sekitar
sini" jadi hanya menyentuh beberapa blok, bukan menyapu seluruh peta
(Kugnae saja 48.400 petak). Pemain dan mob disimpan pada indeks terpisah,
seperti `block[]` dan `block_mob[]` di versi C.

**Area pandang klien** adalah x±9, y±8 (18×16 petak). Bila menembus tepi
peta, area itu **digeser, bukan dipotong**, supaya pemain yang berdiri di
pinggir tetap melihat sejauh biasanya — perilaku ini ditiru persis dari
`map_foreachinarea()`.

**Pemain.** [`User`](src/org/rtk/map/User.java) adalah port `USER`
(`struct map_sessiondata`): ia *adalah* sebuah benda di peta
(`BlockList`), menyimpan [`CharStatus`](src/org/rtk/common/mmo/CharStatus.java)
sebagai data persisten, dan menaruh nilai turunan (maxHp, might, …) di
ladangnya sendiri. [`Pc`](src/org/rtk/map/Pc.java) menyediakan `setPos`,
`spawn`/`despawn`, `warp`, dan `enterWorld`.

> `Pc.setPos()` sengaja **tidak** menyentuh indeks blok — persis seperti
> `pc_setpos()` di C, yang hanya mengubah koordinat. Yang membuat pemain
> terlihat adalah `Pc.spawn()` (di C: `clif_spawn` → `map_addblock`).

**NPC.** 385 NPC dimuat dari tabel `NPCs<serverId>` beserta perlengkapannya
dan ditempatkan ke indeks blok. Selain indeks spasial, ada indeks id
(padanan `map_id2bl` di C) karena klien mengirim balik **id blok** saat
pemain mengklik sesuatu. Id itu bukan `NpcId` apa adanya melainkan
`NPC_START_NUM + NpcId - 2` — pergeseran aneh yang harus dipertahankan
karena ikut protokol.

**Portal.** Tabel `Warps` (4.476 baris) dimuat ke daftar per blok pada tiap
peta. Data aslinya punya **61 petak yang terdaftar ganda**, 26 di antaranya
dengan tujuan berbeda; versi C memenangkan **baris terakhir**, jadi
pencarian di sini menelusuri daftarnya dari belakang agar hasilnya sama. Ketika pemain melangkah ke petak portal, syarat masuk peta tujuan
(level, vita/mana, mark, path) diperiksa dulu; kalau gagal ia didorong
mundur dua petak disertai pesan penolakan — `MapRejectMsg` peta itu bila
diisi, atau kalimat bawaan dari versi C. Portal ke peta milik map server
lain belum bisa dilalui (lihat roadmap C3).

Satu tambahan yang tidak ada di versi C: bila posisi tersimpan seorang
pemain ternyata berada di dalam tembok (mis. petanya diubah setelah ia
terakhir keluar), `enterWorld` mencari petak aman terdekat alih-alih
membiarkannya tersangkut.

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
| **char server** (`char.c`, `logif.c`, `mapif.c`, `char_db.c`) | `charserver/CharServer.java`, `Logif.java`, `Mapif.java`, `CharDb.java`, `CharPersistence.java` | ✅ handshake login & map, autentikasi karakter, buat karakter, ganti password, routing map, **muat/simpan karakter penuh (0x3003/0x3803/0x3004/0x3007)**. ⚠️ board/mail belum; lapisan database belum diuji ke MySQL sungguhan |
| `common/mmo.h` (`struct mmo_charstatus`) | `common/mmo/CharStatus.java`, `CharStatusCodec.java`, + `Item`/`Legend`/`SkillInfo`/`BankItem`/`Point` | ✅ model 67 kolom `Character` + koleksi, dengan format serialisasi sendiri (lihat di bawah) |
| **map server** (`map.c`, `intif.c`) | `map/MapServer.java`, `map/MapIntif.java` | ✅ konek+auth ke char server, memuat geometri peta, terima routing pemain, minta & terima data karakter (0x3003/0x3803) |
| dunia peta (`map.h` block_list/map_data, `map_read`) | `map/data/BlockList.java`, `MapData.java`, `MapRegistry.java` | ✅ geometri + metadata + indeks spasial blok 8×8, area pandang x±9/y±8 |
| **gameplay** (`pc.c`, `mob.c`, `npc.c`, `clif.c` ±22rb baris) | `map/User.java`, `map/Pc.java`, `map/Clif.java`, `map/Npc*.java`, `map/Mob*.java` | ✅ **Trek A selesai** — masuk dunia + panggambaran sekitar (0x33), gerakan & portal, dialog/menu/input NPC (0x30/0x2F/0x39/0x3A), toko beli-jual, NPC & timernya, mob: 716 jenis + 1.175 spawn, AI (tik 50 ms), pertarungan, kematian & jatuhan barang. ⚠️ belum pernah diuji dengan klien RetroTK asli |
| **scripting engine** (`sl.c`, 11rb baris) | `map/script/ScriptEngine.java`, `ScriptClass.java`, `ScriptInstance.java`, `Bindings.java`, `ScriptPlayer.java` | ✅ **jalan via LuaJ** — 906 skrip asli termuat tanpa error; object model typel, dispatch `root.method`, coroutine `_async` + dialog blocking, registry & inventaris tersambung ke `CharStatus`. ⚠️ dari ±258 method yang dipanggil skrip, **64 masih ada di `sl.c` tapi belum diport**; yang paling sering dipakai sudah ditutup (`sendAction` 905×, `talk` 698×, `playSound` 632×, `updateState` 434×, `setDuration` 423×, `spawn` 381×, `calcStat` 249×, `moveGhost` 84×, plus seluruh keluarga barang lantai). Angka terkini: `./run.sh luaaudit` |
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

Belum ada framework unit test (sengaja, agar tetap Java SE murni). Ada enam
gerbang regresi yang harus selalu hijau:

| Perintah | Menguji |
|---|---|
| `./run.sh scripttest` | 906 skrip Lua termuat + coroutine dialog NPC |
| `./run.sh maptest` | 3.544 berkas peta terbaca sesuai format |
| `./run.sh chartest` | serialisasi karakter (29 assertion) |
| `./run.sh worldtest` | dunia peta + penempatan pemain (53 assertion) |
| `./run.sh cliftest` | paket klien, gerakan, portal, penggambaran, gambar ulang peta, dialog NPC, arah hadap, obrolan & gerakan, durasi mantra, gerak mob, barang lantai (450 assertion) |
| `./run.sh dbtest` | lapisan database ke MySQL hidup (132 assertion) |

**`./run.sh scripttest`** (`map/script/ScriptTest.java`):

- **Fase 1** — memuat `sys.lua` + seluruh `Accepted/` dan `Developers/`
  dari `luascript/`; **gagal bila ada satu berkas pun yang error**
  (saat ini 906/906 OK).
- **Fase 2** — menjalankan interaksi NPC lengkap dengan pemain tiruan
  melalui `Accepted/player.lua` asli: klik → yield di menu → resume pilihan
  → dialog 2 halaman ("next"/"next") → addItem → tulis registry → coroutine
  selesai → interaksi kedua. 14 assertion, exit code 1 bila ada yang gagal.

**`./run.sh chartest`** (`common/mmo/CharStatusTest.java`) menguji round-trip
seluruh field karakter — termasuk kasus tepi seperti unsigned 32-bit di atas
4 miliar, nilai bertanda negatif, float, dan teks UTF-8 — memastikan blob
rusak **ditolak** dengan pesan jelas alih-alih salah baca diam-diam, lalu
memeriksa tata letak byte paket 0x3803/0x3004 per-offset.

**`./run.sh worldtest`** (`map/data/MapWorldTest.java`) menguji indeks
spasial dan penempatan pemain di atas **peta Kugnae asli**, termasuk kasus
batas: benda di x+9 terlihat sedangkan x+10 tidak, pindah lintas blok
memperbarui indeks, pemain yang tersimpan di dalam tembok dipindahkan ke
petak yang bisa dilewati, dan warp ke peta yang tidak dimuat ditolak tanpa
menghilangkan pemain.

**`./run.sh cliftest`** (`map/ClifTest.java`) menguji paket yang dibaca
klien RetroTK. Karena klien asli tidak tersedia di sini, tiap paket
**dibangun, didekripsi balik, lalu diperiksa per-offset** — itu yang
membuktikan jalur kunci (statis vs kunci sesi) dipilih dengan benar, bukan
sekadar bahwa byte-nya keluar. Selain tata letak, yang diuji: gerakan
(konfirmasi 0x26, siaran 0x0C ke sekitar, desinkron ditarik balik,
tabrakan tembok/pemain, GM menembus, arah tidak sah), aturan tepi peta,
benda tak terlihat (hantu, GM ber-stealth), serta petak portal beserta
syarat masuk peta dan pesan penolakannya.

**`./run.sh dbtest`** (`charserver/DbTest.java`) satu-satunya gerbang yang
membutuhkan MySQL hidup. Tiga tahap:

1. **Audit SQL** — setiap pernyataan SQL di kode port diambil dari berkas
   sumbernya lalu di-`prepare` ke server. MySQL memvalidasi nama tabel dan
   kolom saat prepare, jadi satu nama salah langsung ketahuan. Cara ini
   menggantikan pencocokan ratusan nama kolom secara manual, dan tidak bisa
   basi: ujinya membaca kode yang sedang berlaku. Dua pernyataan yang nama
   tabelnya disusun saat runtime tidak bisa di-`prepare`, jadi sengaja
   dilewati di sini dan diuji lewat round-trip di tahap 3 — jumlahnya ikut
   dilaporkan supaya tidak terlihat lolos padahal tidak diperiksa.
2. **Data dunia** — 9.850 peta dan 4.476 portal dimuat dari tabel asli, lalu
   tiap petak portal diperiksa apakah benar menunjuk petak yang ada di peta
   tujuan.
3. **Round-trip karakter** — karakter uji diisi nilai ekstrem (unsigned
   32-bit penuh, bigint 9 miliar, kolom bertanda negatif, float karma),
   disimpan, dibaca ulang, dan dibandingkan ladang per ladang termasuk
   inventaris, bank, legenda, aether, buku mantra, dan seluruh registry.

Uji ini **tidak menyentuh data yang sudah ada** — ia hanya menulis pada
karakter buatannya sendiri, dan menghapusnya kembali di akhir maupun saat
gagal.

### Audit skrip Lua (`./run.sh luaaudit`)

`scripttest` membuktikan 906 skrip **termuat**; ia tidak bisa membuktikan
skripnya benar. Lua baru memeriksa apa pun saat barisnya dijalankan, jadi
salah ketik nama fungsi baru meledak ketika pemain menyentuh NPC-nya.
`luaaudit` menutup celah itu tanpa menjalankan skripnya: seluruh berkas
diparse dengan **parser LuaJ yang sama** dengan runtime, lalu diperiksa
kunci tabel ganda, definisi ganda, dan nama yang dipakai tapi tidak ada.

Daftar "nama yang ada" tidak ditebak — mesin skrip dinyalakan lebih dulu,
lalu tabel global dan prototipe `Player`/`NPC`/`Mob` dibaca isinya.

Keluarannya memisahkan dua hal yang mudah tertukar:

- **"AKAN MELEDAK"** — global tak terdefinisi yang *dipanggil atau
  diindeks*. Sekadar membaca global tak terdefinisi itu sah di Lua
  (hasilnya nil, dan banyak skrip memang mengeceknya), jadi hanya yang
  benar-benar berakibat error yang masuk daftar ini.
- **"belum diport" vs "tidak ada di mana pun"** — nama disilangkan ke
  `sl.c` versi C. Yang ada di sana berarti celah port yang sudah diketahui;
  yang tidak ada di mana pun berarti salah ketik atau kode mati.

Audit pertama (21 Agustus 2026) menemukan 13 berkas yang perlu diperbaiki
dan dua celah kompatibilitas Lua 5.1/5.2 di mesin skrip. Semuanya tercatat
di [`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md).

> **Penting:** karena perbaikan itu, `luascript/` **tidak lagi
> byte-identik** dengan `rtklua/` milik upstream. Baca `PERUBAHAN.md`
> sebelum menyalin ulang konten dari sana.

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
penuh, char server port inti, map server dengan routing pemain jalan
ujung-ke-ujung, scripting engine memuat 906/906 skrip Lua asli,
plus infrastruktur (HikariCP, Log4j2, properties, build NetBeans/`build.sh`,
deploy `run.sh`) dan arsitektur jaringan instance-per-server dengan IO
thread terpisah.

### Status terakhir — 26 Agustus 2026

Titik berangkat untuk sesi berikutnya.

| | |
|---|---|
| Gerbang regresi | 6/6 hijau (`cliftest` **450** assertion) |
| `logs/map.log` server hidup | **0 ERROR / 0 WARN** |
| Ketiga server | jalan berdampingan (`./run.sh all`), tautan map↔char stabil |
| Binding skrip | **70 belum diport** (64 di `sl.c` + 6 salah ketik); global belum diport **1** |
| Binding yang masih **stub** | **tidak ada lagi yang nyata** — tinggal `sendSound` dan `updateStatus`, yang tidak ada di `sl.c` sama sekali |
| Skrip Lua | 906/906 termuat, 0 error |
| **Klien RetroTK asli** | **berhasil masuk dunia** — lalu perburuan protokol dihentikan |

Sesi 26 Agustus menutup dua blok: (1) binding yang selama ini masih
**stub** — `talk` (698x), `sendAction` (905x), `playSound` (632x),
`updateState` (434x), `delete`, `refresh`, `sendHealth`, `removeItemSlot`,
`updatePath`; dan (2) **subsistem durasi & aether mantra**
(`map/Durations.java`): `setDuration` (423x) plus 15 binding sekeluarga
dan tik satu detik `bl_duratimer()`; serta (3) `moveGhost` (84x, cara
hampir seluruh AI mob bergerak) dan `spawn` (381x, jebakan / mob event /
boss instance), yang bersama-sama **menghabiskan daftar stub**; serta (4)
**BL_ITEM**, barang di lantai — subsistem besar terakhir yang belum ada
sama sekali, yang sendirian membuka ~95 titik panggilan; serta (5) sisa
varian gerak mob (`moveIntent`, `checkMove`, `moveIgnoreObject`).

⚠️ **Angka `luaaudit` tidak mengukur pekerjaan ini dengan adil.** Binding
yang diport dari keadaan *stub* tidak pernah terhitung di audit — bagi
audit namanya sudah "terdefinisi". Lihat baris "masih stub" di atas.

Memastikan keadaan ini masih berlaku:

```bash
./build.sh
for t in scripttest maptest chartest worldtest cliftest dbtest; do ./run.sh $t; done
./run.sh stop && rm -f logs/map.log && ./run.sh all
sleep 240 && grep -c ERROR logs/map.log     # harus 0
```

> ⚠️ **Jangan menjalankan gerbang regresi sementara server hidup** —
> `cliftest` dan `luaaudit` menulis ke `logs/map.log` yang sama, sehingga
> hitungan ERROR tercemar oleh baris yang memang diharapkan uji.

#### Pekerjaan lanjutan setelah uji ujung-ke-ujung

**1. Uji dengan klien RetroTK asli — satu-satunya penghambat nyata.**
Tidak ada kode baru yang perlu ditulis; semua persiapan sudah beres
(`127.0.0.1`, `version: 750`, Wine 9.0 i386, `ddraw.dll` asli).
Sampai pemain sungguhan masuk dunia, **seluruh Trek A tetap berstatus
"diverifikasi offline"**.

**2. Binding yang masih stub — tidak melempar error, tapi juga tidak
berfungsi.** Kategori berbahaya justru karena `map.log` tetap bersih: stub
hanya menulis WARN sekali lalu mengembalikan nil.

| Method | Pemakaian | Keadaan |
|---|---|---|
| `sendAction` | 905x | ✅ diport 26 Agu |
| `talk` | 698x | ✅ diport 26 Agu (`clif_speak` 0x0D) |
| `playSound` | 632x | ✅ diport 26 Agu |
| `updateState` | 434x | ✅ diport 26 Agu |
| `setDuration` | 423x | ✅ diport 26 Agu |
| `spawn` | 381x | **masih stub** — butuh `mobspawn_onetime` |
| `setAether` | 225x | ✅ diport 26 Agu |
| `msg` | 133x | ✅ diport 26 Agu |
| `delete` | 109x | ✅ diport 26 Agu |
| `moveGhost` | 84x | ✅ diport 26 Agu |
| `dropItemXY` / `throw` / `dropItem` / `pickUp` | ~90x | ✅ diport 26 Agu (BL_ITEM) |

**Daftar stub dan BL_ITEM dua-duanya sudah beres.** Yang tersisa: inventaris
& perlengkapan (~65 titik, butuh paket kirim-inventaris), lalu ekor panjang
kelompok-kelompok kecil — lihat `CLAUDE.md` untuk roadmap berurut.

⚠️ Dua jalur yang baru diport **belum pernah berjalan di server hidup**:
tik durasi dan `moveGhost`. Keduanya hanya menyala untuk pemain yang
benar-benar online (tik AI mob melewati peta ber-`map.users == 0`), jadi
butuh klien sungguhan untuk mengujinya.

~~**3. BL_ITEM (barang di lantai) belum ada sama sekali**~~ — **selesai
26 Agustus 2026 sore** (`map/FloorItem`, `map/FloorItemRegistry`).
Penyaring `...WithTraps` kini benar-benar berbeda dari varian biasa, dan
jatuhan mob terlihat di tanah.

**4. Inventaris & perlengkapan** — ~65 titik panggilan (`updateInv` 24x,
`stripEquip`, keluarga `deductDura*`), butuh paket kirim-inventaris yang
belum ada. Ini penghambat terbesar berikutnya.

**5. Sisa Trek C** — C2 (berkas meta), C3 (warp antar map server),
C4 (papan pesan & surat, paling bebas hambatan).

**Bug yang ditemukan dan sudah ditutup pada putaran ini:** daftar stub
menimpa binding baru; field `name` LuaJ membuat pelaporan stub lumpuh
sehingga hanya binding hilang *pertama* yang pernah dilaporkan;
`objectRef()` membungkus pemain sebagai NPC; `bladestorm_trap.lua`
memanggil method pada nil.

### Pekerjaan berikutnya

Dipecah tiga trek karena butir-butirnya saling bergantung, bukan satu
antrean lurus. Trek B dan C bisa berjalan paralel.

**Trek A — SELESAI (21 Agustus 2026).** Server kini bisa dimainkan
ujung-ke-ujung dari sisi logika:

1. **Paket `clif_*` dasar** — pemain masuk dunia, menerima id/peta/posisi/
   waktu/panel status, lalu **melihat sekelilingnya**: pemain lain, NPC,
   dan mob (paket 0x33). Petak peta digambar ulang saat berjalan, dengan
   checksum agar petak yang sudah dipegang klien tidak dikirim ulang.
2. **Gerakan** — deteksi desinkron, tabrakan, kamera, siaran ke sekitar,
   dan petak portal beserta syarat masuk peta.
3. **Uji end-to-end dengan MySQL sungguhan** — `./run.sh dbtest`, yang juga
   memvalidasi `CharPersistence` dan pemuat `Warps`.
4. **NPC & dialog** — klik NPC (0x43), kotak dialog/menu/input
   (0x30 / 0x2F), jawaban pemain lewat 0x39 dan 0x3A, NPC berwujud
   karakter, timer NPC, dan **toko lengkap** (beli dan jual). Binding
   barang menulis ke inventaris sungguhan dan ikut tersimpan.
5. **Mob & pertarungan** — 716 jenis mob dan 1.175 spawn dari database,
   AI dengan tik 50 ms, pertarungan lewat skrip, tabel ancaman, kematian
   dengan `on_death` dan jatuhan barang.

**Putaran perbaikan dari server hidup (24 Agustus 2026).** Menjalankan
ketiga server dan membaca `logs/map.log` menutup **21 error skrip unik
menjadi 0 ERROR / 0 WARN**. Semuanya kait timer NPC — kode yang baru
berjalan ketika server benar-benar hidup, sehingga tidak satu pun dari 6
gerbang regresi maupun `luaaudit` menyentuhnya. Sejak itu `map.log` bersih
dijadikan syarat setelah menyentuh binding atau kait skrip.

> ⚠️ **Semua itu masih diverifikasi secara offline.** Paket dibangun,
> didekripsi balik, dan diperiksa per-offset — tapi belum satu pun pernah
> dibaca klien RetroTK asli.

**Prioritas sekarang**, diurutkan menurut apa yang menghambat:

1. **Uji dengan klien asli.** Ubah `map_ip` di `conf/map.conf` lalu
   `./run.sh all`. Inilah satu-satunya cara memeriksa hal yang tidak bisa
   diperiksa uji buatan sendiri — dan sekadar **menjalankan ketiga server**
   sudah membuktikan nilainya: dua bug yang lolos dari seluruh gerbang
   regresi baru ketahuan di sana (buffer tulis yang membuang isinya saat
   tumbuh, dan kait timer NPC yang dipanggil tanpa argumen).
   Klien `Origin Nexia.exe` adalah PE32 32-bit, jadi bisa dicoba lewat Wine
   sebelum menyiapkan VM Windows.
2. **Binding yang paling sering dipakai** — `calcStat` (249×), `addNPC`,
   `addSpell`, `bankDeposit`/`bankWithdraw`. Tidak ada hambatan.
3. **Papan pesan & surat (C4)** — murni protokol, bisa diuji offline.
4. **Berkas meta (C2) dan warp antar-map-server (C3)** — bisa dibangun,
   tapi tidak bisa dibuktikan benar tanpa klien.

**Trek B — aset & tooling** (paralel, tidak memblokir Trek A)

1. **Dekoder EPF** — EPF + PAL → gambar RGB, plus pemetaan id `tile`/`obj`
   ke frame (untuk `obj` perlu `SObj.tbl`, 18.954 entri). Prasyarat B3.
2. **Tools editor HTML + JavaScript** — edit `.map` dan skrip Lua langsung
   di browser. Bisa dimulai sebelum dekoder EPF selesai, dengan grid
   berwarna dari id/`pass` lebih dulu.
3. **Client desktop Java + libGDX** — prasyarat: dekoder EPF.

**Trek C — utang teknis**

- ~~Sambungkan `ScriptPlayer` ke `CharStatus`~~ — **selesai 21 Agustus
  2026.** Registry skrip dan registry karakter kini objek yang sama, jadi
  apa pun yang ditulis skrip langsung ikut tersimpan. Terbukti sampai ke
  tabel `Registry` di `dbtest`.
- **Empat berkas meta yang hilang** — `login.conf` meminta 5, `meta/` hanya
  punya `RidableAnimals`. Inilah sebab tooltip item tidak muncul.
  Kandidatnya **ada** di `RTK-Server/rtk/decrypted/` (29 berkas) dan
  formatnya sekeluarga, tapi namanya tidak cocok persis (`ItemInfo0C.dat`
  vs `ItemInfo0`) dan `RideableAnimals.dat` bukan pasangan berkas yang
  sedang dipakai. Perlu klien untuk memastikan mana yang benar.
- **Perpindahan antar map server** — sekarang ditolak dengan pesan jelas;
  butuh dua map server berjalan untuk mengujinya.
- **Papan pesan & surat** (char server) — paling bebas hambatan di trek
  ini: protokol dan tabelnya sudah ada, bisa diuji offline.

**Acuan prioritas binding skrip.** Dari ±258 method yang dipanggil skrip,
**64 masih ada di `sl.c` tapi belum diport** per 26 Agustus 2026
(110 → 100 → 64), dan tinggal **1 global** (`lock`) yang belum ada —
turun dari 6. Angka terkini selalu dari `./run.sh luaaudit`.

Sudah selesai 21 Agustus 2026 — yang paling sering dipakai lebih dulu:
`calcStat` (**249×**), `addNPC` (54×), `addSpell` (28×), `callBase` (12×),
`hasSpell`, `bankDeposit`/`bankWithdraw`. Dengan itu bank berfungsi dan
skrip bisa melahirkan NPC sementara (jebakan, dekorasi) yang hilang sendiri
setelah durasinya habis.

**Putaran 24 Agustus 2026 memakai patokan berbeda: `map.log` dari server
yang benar-benar berjalan**, bukan jumlah pemakaian di korpus. Hasilnya
`sendSide`, `npc:move()`/`warp()`, seluruh keluarga `getObjects*`
(`InCell`/`InArea`/`InSameMap`/`InMap` + varian `Alive` + `getBlock` +
`getUsers`, 178 pemakaian gabungan), `getMapXMax`/`getMapYMax`, `getMapRegistry`/`setMapRegistry`
(**per peta**, bukan satu tabel global), `getPass`/`getObject`/`getTile`/`getWarp`,
konstruktor `Player(id)`, `sendAnimation`/`sendAnimationXY`, konstanta
`MOB_*` dan `F1_NPC` — plus `player:sendStatus()` yang ternyata masih stub
padahal paketnya sudah ada sejak A1.

Sisanya tidak ada lagi yang menonjol jauh — `hasLegend`,
`getEquippedItem`, dan `killCount` ada di kisaran 100–200 pemakaian.
Angka ini berubah setiap kali binding ditambah, jadi jalankan
`./run.sh luaaudit` untuk hitungan terkini alih-alih mengandalkan yang
tertulis di sini.

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
│   ├── RtkLauncher.java         # Main-Class jar: dispatcher login/char/map + uji
│   ├── common/                  # port rtk/src/common: Crypt, Session/NetServer,
│   │   │                        #   TimerSystem, Core, Config, Sql (HikariCP),
│   │   │                        #   ServerLog, Props, Md5
│   │   └── mmo/                 # port mmo.h: CharStatus + codec (blob karakter)
│   ├── login/                   # port rtk/src/login (LoginServer, LoginClif, LoginIntif)
│   ├── charserver/              # port rtk/src/char (CharServer, Logif, Mapif,
│   │                            #   CharDb, CharPersistence)
│   └── map/                     # port rtk/src/map (MapServer, MapIntif, User, Pc,
│       │                        #   Clif = paket klien + gerakan)
│       ├── data/                #   dunia: MapFile, MapData, BlockList, MapRegistry
│       └── script/              #   port sl.c di atas LuaJ (ScriptEngine, ScriptClass,
│                                #     ScriptInstance, Bindings, ScriptPlayer)
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
