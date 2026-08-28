# RTK Java Version

*Baca dalam bahasa lain: [English](README.en.md)*

Port Java SE dari **RTK-Server** — server MMO RetroTK/NexusTK yang aslinya
ditulis dalam C (login server, char server, map server) + MySQL + skrip
konten Lua. Konten Lua **tidak dikonversi** — 907 skrip asli dijalankan apa
adanya lewat LuaJ.

Ditulis ulang 28 Agustus 2026 dari audit menyeluruh. Riwayat lengkap versi
lama ada di `../_backup_docs_2026-08-28/` dan riwayat git.

## Arah project

Sejak 26 Agustus 2026 project ini **tidak lagi mengejar kompatibilitas
byte-per-byte** dengan klien RetroTK. Yang berlaku:

- **Protokol sendiri (RTK2)** — dua arah, dirancang dari kebutuhan nyata
  skrip: 46 opcode masuk, 57 peristiwa keluar. Spesifikasi:
  [`docs/PROTOKOL-RTK2.md`](docs/PROTOKOL-RTK2.md).
- **Klien sendiri (libGDX)** — dikembangkan di repo terpisah
  `../RTK-client`, belum di-commit sampai seluruh aset diganti buatan
  sendiri.
- **Logika permainan adalah aset paling berharga** dan dijaga setia pada C:
  907 skrip Lua, 9.850 peta, 4.476 portal, 716 jenis mob, 2.545 item.
- Protokol RetroTK lama masih ada berdampingan (`ProtocolRouter`) tapi
  berprioritas rendah.

Logika dan protokol dipisahkan oleh antarmuka `ClientView` (peristiwa
keluar) dan `ClientCommands` (aksi masuk) — mengganti protokol berarti
menulis adapter baru, bukan menyentuh logika.

## Yang sudah berjalan

- **Login server**: port penuh — login, buat karakter, ganti password,
  meta file, maintenance mode, banned IP, lockout brute-force.
- **Char server**: handshake login & map, autentikasi, muat/simpan karakter
  (11 tabel), surat, papan pesan antar-server.
- **Map server**: masuk dunia, gerakan + portal, dialog & toko NPC
  (coroutine), pertarungan & AI mob, barang lantai, inventaris &
  perlengkapan, pakai/makan/lempar barang, pertukaran antar pemain, grup,
  daftar abaikan, setelan pemain, tunggangan, durasi/aether mantra,
  pengalaman & hitungan bunuh, bank + bank klan, kiriman/surat/hadiah,
  papan pesan, peta yang bisa diubah saat berjalan.
- **Scripting**: 906/906 skrip termuat 0 error; celah binding **0**
  (satu-satunya sisa `testPacket`, sengaja tidak diport).
- **Pengujian**: 10 gerbang regresi luring (903 assertion `cliftest`,
  234 `dbtest`) + gerbang klien sungguhan `livetest` (182 pemeriksaan;
  **194** pada setup dua map server, `./tools/uji-dua-server.sh`).
  36 dari 46 opcode RTK2 kini pernah benar-benar dikirim klien sungguhan.
- **Terjemahan Indonesia**: SELESAI — 0 dari 9.812 titik dialog masih
  berbahasa Inggris; `livetest` menuntut dialog yang sampai ke pemain
  berbahasa Indonesia.

Daftar pekerjaan yang tersisa: lihat **ROADMAP di [CLAUDE.md](CLAUDE.md)**.

## Prasyarat

- **JDK 25** (level bahasa project = 25); di mesin deploy cukup JRE/JDK 25.
- **NetBeans** (project J2SE "Java with Ant") — opsional; `build.sh` bisa
  build lewat `javac` saja. **Tanpa Maven/Gradle** — 7 jar eksternal ada di
  `extLib/` dan ikut ter-commit.
- **MySQL** dengan database `RTK` (login/char server exit tanpa DB, sama
  dengan perilaku C; map server dan sebagian besar uji tetap jalan).

Project **mandiri** — seluruh data game ada di dalam repo: `maps/`
(3.544 `.map`), `luascript/` (907 `.lua`), `database/` (skema + dump 54
tabel). Sumber C asli (`../RTK-Server`) hanya rujukan; tidak dibaca saat
runtime.

## Quick start

```bash
# 1. build: NetBeans "Clean and Build", atau
./build.sh                        # -> dist/RTK-java.jar

# 2. uji regresi — semua harus hijau
./run.sh scripttest maptest       # (jalankan satu per satu)
./run.sh chartest
./run.sh worldtest
./run.sh cliftest
./run.sh dbtest                   # butuh MySQL
./run.sh luaaudit
./run.sh wiresync                 # butuh ../RTK-client (skip bila tak ada)

# 3. jalankan ketiga server
./run.sh all                      # login (2000) -> char (2005/2006) -> map (2001)
./run.sh status
./run.sh stop

# 4. gerbang klien sungguhan (dari repo klien)
(cd ../RTK-client && ./run.sh livetest 127.0.0.1 2001 Adrielle)
```

## Arsitektur

Tiga server dalam satu jar (`Main-Class: org.rtk.RtkLauncher`, dipilih
lewat argumen pertama). Tiap server = instance `NetServer` sendiri
(selector, tabel session, handler), jadi ketiganya bisa hidup dalam satu
JVM.

Threading per server — IO dipisah dari logika:

```
[IO thread]  selector: accept / read / write
     │  append ke buffer baca session
     ▼
ArrayBlockingQueue<Session>          (dedup: 1 entri per session)
     │
[Logic thread]  timer → parse paket → susun balasan
     │  wfifoSet() → outbox (ConcurrentLinkedQueue)
     ▼
[IO thread]  kirim isi outbox ke socket
```

Logika permainan sengaja **satu thread per server**: urutan paket per
koneksi harus terjaga, dan LuaJ + coroutine per pemain tidak thread-safe.

Peta lengkap C → Java ada di [CLAUDE.md](CLAUDE.md#peta-arsitektur-c--java--ringkas).

## Konfigurasi

Urutan prioritas (lemah → kuat):

1. `resources/rtk-server.properties` — default teknis (crypt key, port,
   lockout, pool HikariCP, buffer, path data). Dibaca dari classpath;
   key yang hilang selalu punya fallback.
2. `conf/*.conf` — format C asli, menimpa nilai yang sama
   (`login_port`, `char_port`, `map_port`, `map_path`, `lua_path`,
   kredensial SQL).
3. Argumen CLI (`--conf`, `--inter`, `--lang`, path untuk mode uji).

> `crypt.enckey` dan `crypt.handshake_key` adalah bagian protokol klien
> RetroTK — jangan diubah sepihak.

## Database

`database/2020-09-02-21-55-01_RTK.sql.bak` = dump lengkap 54 tabel
(9.850 `Maps`, 4.476 `Warps`); `database/scripts/` + `migrate.sh` = 21
migrasi berurutan bila ingin menelusuri riwayat skema.

> **Jebakan Ubuntu/Pop!_OS:** `root` MySQL memakai `auth_socket`
> (`ERROR 1698`, bukan `1045`) — pakai `sudo mysql`. Buat user `rtk` yang
> sudah tertulis di `conf/char.conf`:

```bash
sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; FLUSH PRIVILEGES;"
mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
```

Dump diawali `DROP DATABASE IF EXISTS RTK` — periksa isi database lama
sebelum mengimpor ulang. Terbukti terimpor dari format 5.7 ke MySQL 8.0.

Terjemahan nama barang/mob/NPC dikerjakan di kolom `*Description`
(`database/terjemahan/`) — **bukan** di skrip Lua, karena nama di skrip
adalah identifier. Teks dialognya sendiri sudah **selesai** diterjemahkan
(0 dari 9.812 titik tersisa); alat dan katalognya di `tools/terjemahan/`,
aturannya di [`luascript/GLOSARIUM.md`](luascript/GLOSARIUM.md).

## Pengujian — sembilan gerbang

| Gerbang | Menguji | Catatan |
|---|---|---|
| `scripttest` | 906 skrip Lua + coroutine dialog + kalender dunia | |
| `maptest` | 3.544 berkas peta | |
| `chartest` | serialisasi karakter | |
| `worldtest` | dunia peta + penempatan pemain | |
| `cliftest` | paket, protokol RTK2, seluruh subsistem | 903 assertion |
| `dbtest` | lapisan database ke MySQL hidup | 234 assertion; butuh MySQL |
| `luaaudit` | pemeriksa statis 907 skrip + celah binding | `-Drtk.audit.penuh=true` untuk daftar utuh |
| `wiresync` | `Wire.java` identik dengan salinan di repo klien | skip bila repo klien tidak ada |
| `elixirtest` | **satu pertandingan Elixir penuh** di atas penyalaan server sungguhan | 34 pemeriksaan; map server lain harus mati |
| `carnagetest` | **satu pertandingan Carnage penuh** (regu per jalur kelas, empat kubu) | 28 pemeriksaan; map server lain harus mati |
| `livetest` | **klien RTK2 sungguhan** masuk dunia + 182 pemeriksaan | dijalankan dari `../RTK-client` |
| `tools/uji-dua-server.sh` | perpindahan pemain antar map server (R3/C3) | 194 pemeriksaan; menyiapkan & memulihkan fixture-nya sendiri |

Sepuluh gerbang pertama luring — menguji kode terhadap dirinya sendiri dan
tidak bisa melihat "sesuatu yang tidak terjadi". Karena itu setiap
subsistem baru wajib dapat pemeriksaan `cliftest` **dan** `livetest`, lalu
kodenya dirusak sengaja untuk membuktikan gerbangnya bisa merah.

## Scripting engine (LuaJ)

`ScriptEngine` = port `sl.c`: muat `Developers/sys.lua` → seluruh
`Accepted/` + `Developers/`. Object model `typel` dipertahankan (`__index`:
getter Java → prototype → data table). Dialog NPC = coroutine yang
di-yield/resume oleh jawaban pemain. Prototype `Player` diperluas oleh
`Accepted/player.lua` di atas primitif Java.

Perubahan konten terhadap upstream dicatat di
[`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md); gaya & istilah
terjemahan di [`luascript/GLOSARIUM.md`](luascript/GLOSARIUM.md).

## Status & roadmap

**Status 29 Agustus 2026:** 10/10 gerbang luring hijau, `livetest` 182
pemeriksaan hijau (194 pada dua map server), protokol RTK2 dua arah
simetris, celah binding 0, `map.log` 0 ERROR/WARN. Pemain baru kini bisa
**mendaftar sendiri** lewat klien (masuk akun, buat karakter, pilih
karakter) — lihat K3-lanjutan. Sejak 29 Agu 2026 **mesin acara berkala
hidup**: `map_cronjob()` (timer 1 detik: `cronJobSec`…`cronJobDay`) dan
registry sedunia `GameRegistry<serverid>` keduanya belum pernah diport,
sehingga tidak ada acara, kelahiran bos, penerangan peta, atau pemunculan
barang yang pernah berjalan. Sejak 29 Agu 2026 **dua acara terbukti
berjalan penuh** — Elixir (`./run.sh elixirtest`) dan Carnage
(`./run.sh carnagetest`) — dan menjalankannya membongkar empat cacat mesin
skrip yang diam total: `os.time()` berpecahan sehingga setiap
`os.time() == x` gagal; `hasItem` dikembalikan sebagai jumlah alih-alih
`true`/kekurangan (dipakai 419× dengan `== true`, jadi **setiap syarat
barang di quest gagal**); wujud karakter yang tidak pernah terbaca skrip
sehingga seluruh sistem klon mati; dan `baseClass` yang hilang sehingga
Carnage tidak bisa membentuk kubu. ⚠️ Sapu `logs/common.log` juga: dua bug
nyata bersembunyi di sana, bukan di `map.log` (Peringatan #123, #125).

Roadmap menuju "server dipakai normal & lancar tanpa bug" — lengkap dengan
tabel aksi pemain yang belum punya jalur masuk RTK2 — ada di
**[CLAUDE.md](CLAUDE.md#roadmap--menuju-server-yang-dipakai-normal--lancar-tanpa-bug)**.
Jebakan & pelajaran #1–#139 di
**[docs/PERINGATAN.md](docs/PERINGATAN.md)**.

## Struktur folder

```
RTK-java-version/
├── build.sh  run.sh             # build javac / start-stop server
├── build.xml  manifest.mf  nbproject/   # NetBeans J2SE (Ant) — jangan diedit tangan
├── CLAUDE.md                    # panduan pengembangan + roadmap
├── docs/                        # PROTOKOL-RTK2.md, PERINGATAN.md
├── src/org/rtk/
│   ├── RtkLauncher.java         # dispatcher login/char/map + mode uji
│   ├── common/  common/mmo/     # socket, crypt, timer, SQL, CharStatus+codec
│   ├── login/  charserver/      # login server, char server
│   └── map/                     # MapServer, User, Clif (RetroTK), Combat, Mob,
│       ├── proto/               #   RTK2: Wire, Inbound (+ WireSyncTest)
│       ├── data/                #   MapData, MapRegistry, ItemDb, SpellDb, ...
│       └── script/              #   ScriptEngine (LuaJ), Bindings, LuaAudit
├── resources/                   # log4j2.xml, rtk-server.properties
├── extLib/                      # 7 jar eksternal (tanpa Maven)
├── conf/                        # konfigurasi format C asli
├── maps/  luascript/  database/  meta/  db/   # data game (mandiri)
└── logs/  build/  dist/         # hasil runtime & build (.gitignore)
```
