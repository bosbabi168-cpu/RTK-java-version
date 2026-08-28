# Peringatan Penting — RTK-java-version

Daftar jebakan & pelajaran #1–#102, dipindahkan utuh dari CLAUDE.md lama
(28 Agustus 2026) agar CLAUDE.md tetap ramping. Nomornya JANGAN diubah —
banyak catatan lain merujuk "Peringatan #NN". Tambahkan peringatan baru di
ekor daftar dengan nomor lanjutan.


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

42. **BOD = "Break on Death", dan ia BUKAN penyimpanan.** Namanya terdengar
   seperti tas penyimpanan barang mati; sebenarnya `sd->boditems` adalah
   **daftar gores sementara**: `deductDuraEquip` dan `checkInvBod`
   mengisinya sambil menghancurkan barang, memanggil kait
   `characterLog.bodLog` <b>sekali</b> di akhir, lalu
   <b>mengosongkannya</b>. Skrip hanya bisa membacanya
   (`getBODItem(n)`, atribut `BODItemCount`) <b>di dalam kait itu</b>; di
   luar itu selalu kosong. Kolom sumbernya `ItmBoD`.

   Perkiraan awal roadmap ("subsistem besar, kerjakan sebagai satu blok")
   **terlalu tinggi** — isinya dua sapuan dan satu daftar.

43. **Perlindungan barang MENYELAMATKAN barangnya DAN menghentikan seluruh
   sapuan.** Bila barang yang hendak hancur punya perlindungan, C
   mengurangi satu perlindungan, memulihkan ketahanannya penuh, memanggil
   kait `equipRestore`/`invRestore`, lalu **`return 0` dari dalam
   perulangan**. Akibatnya:
   - slot-slot **sesudahnya tidak ikut diperiksa** pada pemanggilan itu;
   - kait `bodLog` **tidak pernah dipanggil**, sehingga barang yang sudah
     telanjur masuk daftar BOD pada sapuan yang sama tidak pernah dilaporkan
     ke skrip log.

   Terlihat seperti kelalaian, tetapi ditiru apa adanya. Menggantinya dengan
   `continue` akan mengubah berapa banyak barang yang hilang saat mati.

   Terkait: **peringatan ketahanan punya LIMA ambang** (50/25/10/5/1%),
   masing-masing menaikkan bendera `repair` satu tingkat dan hanya berbunyi
   bila benderanya persis satu di bawahnya. Port ini sempat hanya memuat
   ambang 50%, jadi barang yang sudah menipis lewat itu tidak pernah
   memperingatkan lagi. Sudah dilengkapi.

44. **`Sql.queryInt` dulu mengembalikan 0 untuk SQL NULL.** `getInt`
   memang begitu, jadi "tidak ada nilainya" dan "nilainya nol" jadi tak
   terbedakan. Yang membuatnya berbahaya adalah **agregat**:
   `SELECT MAX(x) ... WHERE ...` pada himpunan kosong menghasilkan **satu
   baris berisi NULL**, bukan nol baris — sehingga pemanggilnya mengira
   nilai tertingginya 0. Ketahuan pada nomor urut kiriman, yang seharusnya
   mulai dari 0 tapi malah dari 1. Sekarang `queryInt` memeriksa
   `wasNull()`; kalau menambah pembaca kolom baru, periksa hal yang sama.

45. **Nomor urut kiriman dan surat adalah nomor PER PENERIMA, bukan kunci.**
   `ParPosition` / `MalPosition` diisi "tertinggi milik penerima + 1".
   Karena itu menghapus satu kiriman **tidak** menomori ulang sisanya —
   lubang di tengah dibiarkan, dan kiriman berikutnya tetap mengambil
   tertinggi + 1, bukan mengisi lubangnya. Menomori ulang akan memutus
   `removeParcel`, yang menghapus **berdasarkan nomor itu**.

   Dua hal lagi di keluarga yang sama:
   - **`npcflag` mengubah arti `sender`**: bila bukan nol, pengirimnya NPC
     dan idnya digeser `+ NPC_START_NUM - 2`.
   - **`removeParcel` meminta sebelas argumen dan memakai satu.** Hanya
     nomor urutnya yang menentukan baris mana yang terhapus; sisanya
     peninggalan log yang dikomentari. Jangan menjadikannya penyaring.

46. **`addGift`/`retrieveGift` badannya DIKOMENTARI SELURUHNYA di C.**
   Keduanya tidak melakukan apa pun di server aslinya, jadi skrip yang
   mengandalkannya memang sudah rusak di sana. Diport sebagai tiruan setia
   yang mengembalikan nil — mengisinya dengan tebakan justru mengubah
   perilaku.

   ⚠️ Terkait pengujian: **`dbtest` menulis ke `logs/char.log`**, persis
   seperti `cliftest` menulis ke `logs/map.log` (Peringatan di bagian
   "Cara memastikan"). Baris ERROR dari uji yang gagal akan terlihat seperti
   error server pada pemeriksaan berikutnya. Perhatikan cap waktunya.

47. **Paket papan di C mengirim DUMP STRUCT MENTAH — port ini tidak.**
   0x3009/0x300A/0x300C memakai
   {@code memcpy(&a, ..., sizeof(struct board_show_0))}, sehingga panjangnya
   bergantung pada padding kompilator. Itu **alasan yang sama** dengan blob
   karakter (Peringatan #9): kedua ujungnya kode Java kita sendiri, jadi
   tata letak C bukan kontrak yang harus dipatuhi. Ladangnya ditulis
   eksplisit di `MapIntif.requestBoard` dan `Mapif.parseShowPosts`, dan
   panjang paket 0x3009 di tabel dispatcher **-1**, bukan angka tetap.
   Yang tetap ditiru adalah <b>semantiknya</b>.

48. **Tiga nama menyesatkan sekaligus di keluarga papan.**
   - **`powerBoard` bukan papan pesan.** Ia daftar pemain online di peta
     beserta "power rating" ({@code baseHealth + baseMagic}), dan tidak
     menyentuh tabel `Boards` sama sekali.
   - **Papan 0 bukan papan.** Ia kotak surat pribadi, dibaca dari tabel
     `Mail` dengan nama kolom yang sama sekali berbeda; hanya bentuk
     hasilnya yang sama. Hak tulis/hapusnya selalu penuh.
   - **`BoardNames` vs `BoardTitles`.** Yang pertama papannya, yang kedua
     <b>gelar penulis</b> yang muncul di depan namanya pada daftar kiriman
     ("Prajurit Budi"). Keduanya dibaca `BoardDb`.

   Dua jebakan angka di keluarga yang sama:
   - **`boardCanWrite == 6` menggantikan seluruh bendera, tidak di-OR** —
     artinya "klien harus mengirim paket saat tombol tulis diklik", dipakai
     papan yang dijawab skrip.
   - **`flags1` bergantung pada popup DAN papan sekaligus**, bukan salah
     satunya: kotak surat selalu memakai cabang bukan-popup.

49. **"Bank klan" dan "bank subpath" adalah penyimpanan yang SAMA.**
   `getClanBankItems` dan `getSubpathBankItems` di C membaca larik
   {@code clan->clanbanks[]} yang identik; yang berbeda hanya <b>bentuk
   keluarannya</b> — sepuluh ladang per barang versus lima (id, jumlah,
   pemilik, ukiran, waktu). Membuat penyimpanan kedua untuk subpath akan
   memecah isi bank yang seharusnya satu, dan barang yang dititipkan lewat
   satu pintu tidak akan terlihat dari pintu lainnya.

   Dua hal lagi di keluarga bank:
   - **Penggabungan menuntut SEPULUH atribut sama persis** — id, pemilik,
     waktu, ukiran, perlindungan, ikon & wujud kustom beserta warnanya.
     Beda satu saja berarti slot terpisah. (Aturan yang sama dengan
     penggabungan barang lantai jalur pemain; lihat Peringatan #31.)
   - **Kolom `CbkPosition` dibaca tapi TIDAK dipakai menempatkan barang.**
     C menyalin baris ke-{@code i} ke slot ke-{@code i}, jadi lubang nomor
     slot di database <b>dirapatkan</b> saat dimuat. Ditiru apa adanya —
     kalau tidak, nomor slot di memori berbeda dari yang dilihat C dan
     penyimpanan berikutnya menggeser seluruh isinya.

50. **Audit sempat menghitung pendaftaran yang DIKOMENTARI di `sl.c`.**
   `LuaAudit.bacaNamaC()` menyapu sumber C dengan regex, dan regex tidak
   tahu soal komentar — sehingga baris seperti
   {@code //typel_extendproto(&pcl_type, "addActivationKey", ...)} ikut
   terhitung "ada di sl.c". Akibatnya binding yang **tidak ada di server
   aslinya pun** dilaporkan sebagai celah port yang menunggu dikerjakan.
   Sekarang komentar dibuang lebih dulu; dua nama pindah ke kategori
   "tidak ada di mana pun", tempatnya yang benar.

   Ini pola yang sama dengan Peringatan #30 dan prototipe `FloorItem` yang
   terlewat: **angka audit hanya sejujur cara ia mengumpulkan namanya.**
   Sebelum memakainya sebagai ukuran, periksa dulu apa yang ia hitung.

51. **`setIndDmg`/`setGrpDmg` BUKAN tabel ancaman.** Ancaman
   ({@code addThreat}) menentukan siapa yang dikejar mob; dua tabel ini
   hanya catatan siapa menyumbang kerusakan berapa, dipakai skrip untuk
   membagi jatuhan dan pengalaman. Angkanya ditambahkan, batasnya 50 entri,
   dan {@code setGrpDmg} mencatat menurut **id grup** pemain, bukan id
   pemainnya.

52. **Dua antarmuka, arah dan pemilik TERBALIK.** Ini yang paling mudah
   tertukar saat menyentuh lapisan protokol:

   | | `ClientView` | `ClientCommands` |
   |---|---|---|
   | Arah | logika &rarr; klien | klien &rarr; logika |
   | Isi | apa yang **terjadi** | apa yang **diminta** |
   | Diimplementasikan | lapisan **protokol** | lapisan **logika** |
   | Dipanggil | lapisan **logika** | lapisan **protokol** |

   Aturannya sepasang: **kode logika jangan memanggil `Clif`**, dan
   **kode logika jangan membaca `rfifo*`**. Kalau sebuah baris di
   `MapCommands` butuh `Session` atau opcode, baris itu salah tempat.

   ⚠️ **Satu kebocoran yang disengaja dan sudah ditandai:**
   `playerWalks` membawa `RedrawRequest` — permintaan klien RetroTK untuk
   menggambar ulang sepetak wilayah, yang menumpang paket langkah yang
   sama. Ia ada di sana karena **urutannya mengikat**: penggambaran harus
   terjadi setelah pemain berpindah tetapi sebelum kait skrip dan
   pemeriksaan portal — portal bisa memindahkannya ke peta lain, dan
   menggambar ulang peta lama sesudah itu salah. Jadi tidak bisa dikerjakan
   pemanggil setelah method-nya kembali. **Buang saat protokol baru
   dirancang**; server baru tahu sendiri apa yang baru terlihat.

53. **Pertukaran: barang DITITIPKAN, bukan ditandai.** Barang yang
   ditawarkan <b>benar-benar dikeluarkan dari inventaris</b> dan disimpan di
   {@code sd->exchange} sampai selesai atau dibatalkan; membatalkan
   mengembalikannya lewat {@code pc_additemnolog}.

   Kedua arah kesalahannya berbahaya:
   - Kalau titipannya jadi sekadar penanda tanpa mencabut barangnya, pemain
     bisa menjual atau menjatuhkan barang yang sama di tengah pertukaran —
     **penggandaan**.
   - Kalau dicabut tanpa jalur pengembalian, barang **hilang** setiap kali
     pertukaran batal.

   Dua hal lagi:
   - **Persetujuannya DUA TAHAP.** Menekan "tukar" sekali hanya menandai;
     barang berpindah ketika pihak <b>kedua</b> menekan. Karena itu
     {@code confirm} punya dua cabang yang terlihat mirip.
   - **`ItmExchangeable` kebalikan namanya lagi** — nilai bukan-nol berarti
     barangnya TIDAK bisa ditukar (42 dari 2.545 barang). Sekeluarga dengan
     `ItmDroppable` (Peringatan #32); dibungkus
     `ItemDb.cannotBeExchanged()` supaya tidak menyesatkan lagi.
   - **Emas TIDAK dititipkan** seperti barang — ia tetap di dompet dan
     diperiksa ulang saat konfirmasi. Menawarkan lebih dari yang dimiliki
     tidak ditolak sebagai kesalahan; nilainya hanya diabaikan.

54. **Nama fungsi C bukan penentu sisi mana kode itu berada.**
   `clif_pushback` namanya berawalan `clif_` dan tinggal di `clif.c`, tapi
   badannya **tidak menyentuh satu byte pun**: isinya `Pc.warp` dua petak ke
   belakang. Ia logika, dan sekarang tinggal di `MapCommands`. Kebalikannya
   juga ada: `flushDialog` terdengar seperti utilitas internal, padahal
   isinya murni penerjemahan keadaan skrip jadi paket — itu protokol, dan ia
   tetap di `Clif` di balik peristiwa `scriptDialogReady`.

   Patokan yang dipakai saat memisahkan lapisan masuk: **lihat badannya,
   bukan namanya.** Kalau sebuah baris menyentuh `Session`, `rfifo*`, atau
   opcode, ia protokol; kalau ia memutuskan apa yang **terjadi** di dunia,
   ia logika. Awalan `clif_` di C tidak memberi tahu yang mana.

   Verifikasi batasnya satu perintah, dan layak diulang tiap kali
   `MapCommands` disentuh:

   ```
   grep -n "Clif\.\|rfifo\|Session" src/org/rtk/map/MapCommands.java
   ```

   harus tidak menghasilkan satu pun baris kode.

55. **Memindahkan kode tidak mengubah satu pun angka gerbang regresi — dan
   itu justru masalahnya.** Kelima helper dipindah dengan `cliftest` tetap
   572 assertion di tiap langkah. Angka yang tidak bergerak **bukan bukti
   apa-apa** bila langkahnya besar: uji yang sama akan tetap hijau kalau
   sebuah helper terlewat dipindahkan, atau kalau badannya berubah halus di
   tengah perpindahan.

   Karena itu pemindahannya dikerjakan **satu helper per langkah**, dengan
   build + `cliftest` di antara tiap langkah. Bukan kehati-hatian
   berlebihan: itu satu-satunya cara membuat "angkanya tidak berubah"
   berarti sesuatu. Aturan yang sama berlaku untuk refactor apa pun di
   project ini yang tidak menambah uji baru.

56. **RTK2: satu port, dua protokol, pembeda TANPA KEADAAN.** Paket
   RetroTK selalu diawali `0xAA`; byte pertama bingkai RTK2 adalah byte
   tinggi ladang panjang, yang tidak akan pernah `0xAA` **selama
   `Wire.MAX_FRAME` di bawah 43.520**. Karena itu tidak ada tabel "sesi ini
   protokol apa" yang harus dibersihkan saat pemain terputus, dan tidak ada
   keadaan yang bisa jadi basi.

   ⚠️ **Menaikkan `MAX_FRAME` melewati 43.520 mematahkan pembedaan itu**
   dan gejalanya akan terlihat sebagai paket RetroTK yang tiba-tiba rusak,
   bukan sebagai batas bingkai yang salah. Ada assertion khusus di
   `cliftest` yang menjaga hubungan ini; jangan dihapus saat menyetel batas.

57. **Lima jebakan RetroTK yang dibuang di RTK2 — dan kenapa.** Rinciannya
   di javadoc `Wire`; ringkasnya, tiap satu pernah menghasilkan bug nyata di
   port ini:
   - **dua endianness** (Peringatan #3, #10 — satu uji lolos tanpa menguji
     kodenya) → RTK2 big-endian tanpa pengecualian;
   - **ladang panjang yang bukan panjang** (Peringatan #17, dua kali membuat
     ekspektasi uji salah) → panjang adalah panjang;
   - **enkripsi XOR beserta urutan dekripsinya** (paket perkenalan justru
     tidak boleh didekripsi; melanggarnya membuat klien menggantung) →
     polos, kerahasiaan nanti dari TLS;
   - **nomor urut di ladang [4] yang hanya ada di sebagian paket**
     (Peringatan #29) → tidak ada;
   - **slot 1-basis di kabel, 0-basis di server** → 0-basis di keduanya.

   Dua hal lain juga sengaja tidak ada: **permintaan gambar ulang** yang
   menumpang paket langkah (kebocoran di Peringatan #52), dan **perjalanan
   bolak-balik "berapa banyak?"** saat menyerahkan setumpuk barang — klien
   sudah tahu isi tumpukannya, jadi `clif_parse_exchange` ragam 1 dan 2 jadi
   satu opcode.

58. **`clif_parsesay` TIDAK menyiarkan apa pun — badannya dikomentari.**
   Yang benar-benar terjadi saat pemain mengetik: kalimatnya disimpan di
   `sd->speech`, kait `on_say` tiap mantra yang dikuasai dipanggil, lalu
   skrip **`onSay`** (`Accepted/speech.lua`) yang memutuskan segalanya —
   termasuk memanggil `player:talk()` bila memang perlu terdengar.

   Menambahkan siaran di sisi server membuat **setiap kalimat terkirim dua
   kali**. Pola yang sama berlaku untuk memungut (`onPickUp` yang menyapu
   petak dan memanggil `addGold`/`addItem`, bukan server) dan menyerang
   (`swing` yang mencari sasarannya sendiri — itulah sebabnya paket serang
   tidak membawa sasaran sama sekali).

   ⚠️ Konsekuensinya: **`player.speech` adalah satu-satunya atribut pemain
   yang bernilai string**, sehingga ia tidak bisa lewat jembatan
   `scriptGetAttr` yang mengembalikan `Long`. Ia punya cabang sendiri di
   `Bindings.definePlayer` lewat `ScriptPlayer.Owner.scriptGetSpeech()`.
   Tanpa itu `speech.lua` meledak di baris pertamanya, di
   `string.lower(nil)`.

59. **Emas yang dijatuhkan punya aturan gabung KETIGA.** Setelah jatuhan mob
   (id sama) dan jatuhan inventaris (sepuluh atribut sama; Peringatan #31),
   emas melebur dengan **tumpukan koin mana pun** — penyaringnya
   `id >= 0 && id <= 3`, karena keempat id itu tingkat gambar, bukan jenis
   barang (0 sekeping, 1 untuk 2–99, 2 untuk 100–999, 3 untuk 1.000 ke
   atas).

   Dua hal yang terlihat seperti kelalaian tapi ada di C:
   - **Tingkatnya tidak dihitung ulang setelah melebur** — sekeping koin
     yang menyerap 5.000 emas tetap tergambar sebagai sekeping.
   - **Emas dipotong dari dompet SEBELUM kait skrip berjalan**, dan tidak
     dikembalikan bila kaitnya membatalkan lewat `fakeDrop`. Skrip yang
     memakai `fakeDrop` pada emas mengandalkan pemotongan itu sudah terjadi.

60. **`mob->inventory` bukan lubang tempat barang hilang.** Barang yang
   diserahkan pemain ke mob masuk ke sana, dan **dijatuhkan kembali saat
   mob mati** (`mobdb_drops`, mob.c:730) — lewat jalur jatuhan mob, jadi
   aturan gabungnya id saja. Membuang bagian itu membuat setiap barang yang
   diserahkan ke mob lenyap selamanya.

   Terkait, di keluarga yang sama: **`NpcCanReceiveItem` bernilai 0 bukan
   berarti NPC diam saja** — ia menjawab dengan ejekan ("Keep your junky
   ... with you!"). Itu satu-satunya umpan balik pemain.

61. **`playerInventorySlotCleared` MENGOSONGKAN slotnya, bukan sekadar
   memberi tahu klien.** Ini pengulangan Peringatan #37 dari sisi
   pemanggil, dan sempat kejadian lagi 27 Agustus 2026 di
   `playerHandsItem`: menambahkan `inventory.remove(slot)` sesudahnya
   membuang slot pemain **berikutnya**, dan gejalanya
   `IndexOutOfBoundsException` di tempat yang berbeda. Yang menangkapnya
   uji yang ditulis di sesi yang sama.

62. **Kueri yang DISUSUN RUNTIME tidak terlihat audit SQL `dbtest`.**
   Audit tahap 1 menyapu literal string dari berkas sumber, jadi
   `ClassDb.loadPaths` — yang merangkai enam belas kolom `PthMark0..15`
   dalam perulangan — lolos tanpa pernah divalidasi skema hidup. Sama
   dengan `CharPersistence.reg()` yang sudah lebih dulu dikecualikan.
   Jalur seperti ini **wajib diuji lewat pemanggilan sungguhan** di tahap 2,
   bukan diandalkan pada auditnya.

   Ini keluarga yang sama dengan Peringatan #30 dan #50: **angka audit hanya
   sejujur cara ia mengumpulkan namanya.**

63. **Mengenakan barang itu DUA LANGKAH, dan keduanya tidak boleh
   digabung.** `pc_equipitem` <b>tidak memasang apa pun</b>: ia memeriksa
   syarat, menyimpan `equipId` + `invSlot`, lalu memanggil kait `onEquip`.
   Yang memindahkan barangnya adalah `pc_equipscript`, dipanggil <b>skrip</b>
   lewat `player:equip()`. Melepas sama persis: `pc_unequip` menandai
   `takeOffId` dan memanggil `onUnequip`; `pc_unequipscript`
   (`player:takeOff()`) yang mencabut.

   Menggabungkan keduanya melewati kait yang ditumpangi banyak barang
   khusus; memindahkan syaratnya ke langkah kedua membuat `forceEquip` ikut
   tertahan. Keadaan penghubungnya (`equipId`, `invSlot`, `takeOffId`) ada
   di `User` justru karena itu.

   ⚠️ `takeOffId` bernilai **-1** saat kosong, bukan 0 — 0 adalah slot
   senjata yang sah.

64. **Pasangan kiri/kanan cincin dan anting TIDAK simetris.** Di C ini empat
   blok `if` <b>berurutan</b>, bukan `else if`, sehingga hasil blok
   sebelumnya ikut diuji blok berikutnya. Akibatnya:
   - cincin (`EQ_LEFT`) yang kiri penuh dan kanan kosong **pindah** ke
     kanan — seperti dugaan;
   - anting (`EQ_SUBLEFT`) justru sebaliknya: yang **kosong** dipindah ke
     `EQ_SUBRIGHT`, dan yang penuh malah **tetap** di kiri.

   Anting pertama karena itu mendarat di **sub-kanan**. Terlihat seperti
   salah ketik di C, tetapi memperbaikinya akan memindahkan setiap anting
   yang sudah dikenakan pemain ke slot yang berbeda dari yang tersimpan di
   database. Ada assertion khusus untuk kedua perilaku itu di `cliftest`.

65. **`ItmUnequip` kebalikan namanya — nilai 1 berarti TIDAK bisa
   dilepas.** Keluarga keempat setelah `ItmDroppable` (Peringatan #32),
   `ItmExchangeable` (#53), dan `mob->canmove` (#35). Dibungkus
   `ItemDb.cannotBeUnequipped()`. Di data asli hanya **1 barang** memakainya,
   jadi salah arah tidak akan terlihat dari statistik — hanya dari barang
   itu.

   Dua jebakan lain di keluarga barang yang sama:
   - **Ketahanan rokok (`ITM_SMOKE`) adalah sisa isap, bukan keausan** —
     berkurang satu tiap pakai, habis berarti barangnya hilang.
   - **Umur barang (`ItmTimer`) mulai dihitung saat DIPAKAI pertama kali**,
     bukan saat didapat. Penjaganya `!it.time`, jadi pemakaian kedua tidak
     memundurkan kedaluwarsanya.

66. **`clif_parsewield` menerima jenis 3..16, bukan 3..17.** `ITM_HAND`
   (17) punya slot perlengkapan dan bisa dikenakan lewat "pakai", tetapi
   <b>tidak</b> lewat pintu "kenakan". Karena itu `ItemDb.ITM_EQUIP_MAX`
   (17) bukan batas yang benar untuk pintu itu.

   Terkait, dan lebih berbahaya: `pc_useitem` menghitung slot tujuannya
   dengan `itemdb_type - 3` **tanpa batas atas**, sehingga barang bukan-
   perlengkapan (`ITM_ETC` = 18 ke atas) **membaca di luar larik
   `equip[]`** di C. Port ini menjaga rentangnya; efeknya sama untuk barang
   yang sah, tanpa perilaku tak tentu.

67. **Audit Lua tidak pernah melaporkan `equip`, dan itu blind spot yang
   sudah tertulis.** Binding `player:equip()` tidak ada sama sekali di port
   ini sampai 27 Agustus 2026, tetapi `./run.sh luaaudit` **tidak pernah
   menyebutnya** — karena `equip` kebetulan juga nama kunci tabel di tiga
   skrip (`Clone.lua`, `basic_sickle.lua`, dan sebuah berkas alat GM),
   sehingga audit menganggapnya "terdefinisi di korpus".

   Ini persis kelemahan yang sudah dicatat di bagian "Audit skrip Lua", dan
   sekarang ada contoh nyatanya: **binding yang paling banyak dipakai bisa
   hilang tanpa satu baris pun laporan.** Silangkan ke `sl.c` saat
   mengerjakan satu keluarga fungsi, jangan menunggu audit menyebutnya.

68. **Empat atribut pemain yang dibaca skrip tapi belum pernah ada.**
   `speech` (string), `flank` dan `backstab` (boolean, 134 pemakaian
   gabungan di skrip pertarungan), dan `enchant` (angka). Ketiadaannya tidak
   pernah dilaporkan audit mana pun — audit memeriksa nama <b>fungsi</b>,
   bukan atribut.

   ⚠️ **Boolean wajib lewat jalur khususnya sendiri**
   (`ScriptPlayer.Owner.scriptGetSpecial`), bukan dipaksakan jadi angka
   lewat jembatan `scriptGetAttr` yang mengembalikan `Long`: **di Lua angka
   0 itu BENAR**, sehingga `if player.flank then` akan selalu masuk. Salah
   seperti itu tidak melempar error di mana pun; ia hanya membuat seluruh
   skrip pertarungan berperilaku terbalik.

69. **Dua protokol keluar tidak bisa "pilih salah satu" — keduanya harus
   dipanggil.** Pilihan yang tampak wajar (lihat protokol pemainnya, panggil
   implementasi yang cocok) tidak bisa dipakai, dan alasannya ada di
   antarmukanya sendiri: **separuh peristiwa `ClientView` tidak punya satu
   penerima.** `objectActed`, `mobSpawned`, `floorItemAppeared`, dan
   sembilan lainnya menyiarkan ke sekitar sebuah benda — dan sekitar itu
   bisa berisi pemain dari kedua protokol sekaligus.

   Karena itu arahnya dibalik: `ProtocolRouter` memanggil **kedua**
   implementasi untuk setiap peristiwa, dan masing-masing **menyaring
   penerimanya sendiri**. Penyaringnya satu tempat per protokol —
   `Clif.sessionOf()` dan `Rtk2ClientView.sesi()` — sehingga tidak ada jalur
   yang bisa terlewat.

   ⚠️ **Jangan memanggil `MapServer.net.session()` langsung dari `Clif`.**
   Seluruh paket di sana, per-pemain maupun siaran, wajib mengambil sesinya
   lewat `sessionOf()`; itu satu-satunya yang membuat penjaganya bisa
   dipercaya. Delapan pemanggilan langsung sempat ada dan semuanya sudah
   dialihkan.

70. **Efek samping di dalam fungsi paket meledak begitu protokolnya lebih
   dari satu.** `clif_senddelitem` di C mengosongkan slot inventaris
   sekaligus mengirim paketnya. Dengan dua implementasi hidup, efek samping
   itu berjalan **dua kali**, dan yang kedua membuang slot pemain
   **berikutnya** — Peringatan #61 yang muncul lagi di tempat yang berbeda.

   Pengosongannya sekarang milik sisi logika
   (`User.clearInventorySlot()`), dipanggil kelima titik yang memang
   bermaksud mengosongkannya. Fungsi paketnya tidak menyentuh keadaan sama
   sekali.

   **Aturan umumnya:** setiap kali sebuah fungsi paket mengubah keadaan
   permainan, cari tahu berapa kali ia akan dipanggil. Selama hanya ada satu
   protokol, jawabannya selalu "sekali" — dan itu yang menyembunyikan
   masalahnya selama ini.

71. **Blok benda RTK2 disusun ULANG untuk tiap penonton.** Siaran biasa
   menyusun bingkainya sekali lalu menyalinnya ke tiap penerima — lebih
   murah, dan tanpa enkripsi per-sesi memang tidak ada alasan menyusunnya
   berulang. Tetapi blok benda **tidak boleh** diperlakukan begitu: isinya
   bergantung pada siapa yang melihat.

   - jebakan yang belum ditemukan **tidak digambar sama sekali**
     (Peringatan #33);
   - pemain ber-stealth tampak berbeda bagi GM;
   - penanda seklan hanya muncul untuk sesama anggota.

   Itu sebabnya `perPenonton()` terpisah dari `siarkan()` di
   `Rtk2ClientView`. Menyatukan keduanya akan membocorkan jebakan ke semua
   orang di area.

72. **Setelan pemain (`settingFlags`) sekarang satu sumber.** Nilainya
   sempat tersalin di tiga tempat — `Clif`, `MapCommands`, dan sekali lagi
   saat implementasi protokol kedua ditulis. Duplikasi seperti itu tidak
   pernah salah saat ditulis; ia salah saat salah satunya diperbaiki.
   Semuanya kini merujuk `org.rtk.common.mmo.SettingFlags`.

   Tempatnya di model karakter, **bukan** di kelas paket: bit-bit itu
   keputusan pemain tentang apa yang ingin ia terima dan izinkan, walau
   sebagian menentukan apa yang dikirim.

73. **⚠️ Audit dulu melaporkan "0 celah global" padahal ada 59 — SUDAH
   DIPERBAIKI DI ALATNYA.** Ini Peringatan #30 dengan angkanya, dan angkanya
   besar: `ScriptEngine.registerGlobals()` memasang **63 global sebagai stub
   warn-once**, dan **59 di antaranya terdaftar di `sl.c`** — celah port yang
   nyata. Bagi audit semuanya "terdefinisi" (stub adalah fungsi yang sah: ia
   tidak melempar, hanya menulis WARN sekali lalu mengembalikan nil), jadi
   baris `GLOBAL ada di sl.c tapi BELUM DIPORT: 0 nama` **tidak berarti apa
   yang dibacanya**.

   Sejak 27 Agustus 2026 `LuaAudit` menghitungnya sendiri lewat
   `ScriptEngine.stubNames()` dan mencetak bagian
   **"GLOBAL masih STUB dan ADA di sl.c"**, diurutkan menurut jumlah
   pemakaian. Baca bagian itu, bukan hanya baris "BELUM DIPORT".

   ⚠️ **Masih ada satu celah yang lolos bahkan dari laporan baru:** binding
   yang mengembalikan **nilai tetap** alih-alih dipasang sebagai stub —
   `getXPforLevel` (12x), `checkOnline`, `curServer`. Ketiganya kini
   bertanda komentar di `ScriptEngine`. Kalau menambah binding sementara,
   pakai daftar `stubs`, jangan `set(nama, args -> valueOf(0))`.

   Yang terbesar, dengan jumlah pemakaian di 907 skrip:

   | Binding | Pakai | Isinya di C |
   |---|---|---|
   | `setTile` | 530x | tulis satu petak + gambar ulang untuk yang di area |
   | `setObject` | 364x | sama, larik `obj` |
   | `setPass` | 88x | sama, larik `pass` |
   | `getOfflineID` | 24x | satu kueri nama ↔ id |
   | `setMap` | 23x | muat ulang berkas `.map` ke slot peta |
   | `getMobAttributes` | 15x | |
   | `guitext` (global) | 14x | siaran sepeta / se-server |
   | `setWeather` | 14x | |
   | `getXPforLevel` | 12x | tabel `ClassDb` **sudah ada** |

   Ketiga yang teratas isinya **tiga baris masing-masing**. 980 titik
   panggilan tergantung padanya, dan semuanya kini gagal diam-diam.

   ⚠️ Titik panggilan pertama `setTile` adalah
   **`Accepted/Tools/map_editor.lua`** — kontennya sudah membawa editor peta
   dalam permainan. Itu berkaitan langsung dengan Trek B2.

74. **Geometri peta DIBAGI antar peta — `setTile` naif akan merusak
   banyak peta sekaligus.** 9.850 baris `Maps` hanya menunjuk 2.919 berkas
   unik, dan `MapRegistry` sengaja men-cache `MapFile` per nama berkas
   (Peringatan #11). Di C tidak begitu: tiap peta `CALLOC` lariknya sendiri.

   Akibatnya `setTile(m, x, y, n)` yang langsung menulis ke
   `MapData.geometry` akan mengubah petak itu **di setiap peta yang memakai
   berkas yang sama** — dan tidak ada satu pun uji yang akan menangkapnya,
   karena kedua peta memang "benar" secara terpisah.

   Jalan yang benar: **salin-saat-ditulis.** Peta yang pertama kali diubah
   mengkloning `MapFile`-nya jadi salinan pribadi. Hanya peta instance dan
   event yang pernah berubah, jadi biayanya nyaris nol.

75. **Ada DUA `guitext`, dan hanya satu yang diport.** `player:guitext(teks)`
   (method, sudah ada) menulis di layar satu pemain; **`guitext(type, m, teks)`
   (global, masih stub)** menyiarkan ke seluruh peta — dan dengan
   `type == -1, m == 0` ke **seluruh server**. Pengumuman event memakai yang
   kedua, jadi selama ia stub tidak ada pengumuman yang pernah sampai.

   Pola yang sama layak dicurigai pada nama lain yang muncul sebagai global
   <b>dan</b> method.

76. **⚠️ Audit SQL `dbtest` DULU TIDAK MEMVALIDASI NAMA TABEL MAUPUN KOLOM
   SAMA SEKALI.** Selama tiga hari berkas ini menulis bahwa 64 pernyataan
   "di-prepare ke server, sehingga nama tabel/kolom divalidasi MySQL
   sendiri". **Itu tidak pernah benar.** `prepareStatement()` +
   `getParameterMetaData()` dikerjakan **di sisi klien** oleh connector
   MySQL; bahkan ini lolos:

   ```
   SELECT * FROM `TabelNgawur` WHERE `x` = ?     -> LOLOS
   SELECT `KolomNgawur` FROM `Registry` ...      -> LOLOS
   ```

   Ketahuan 27 Agustus 2026 karena sebuah kueri baru memakai kolom
   `RegKey` (nama aslinya `RegIdentifier`), lolos audit, lalu gagal di
   tahap 2 saat benar-benar dijalankan.

   **Sekarang auditnya memakai `EXPLAIN`** dengan placeholder diganti
   `NULL` — itu memaksa server mem-parse dan me-resolve nama untuk SELECT,
   INSERT, UPDATE, <b>dan</b> DELETE tanpa menyentuh satu baris data pun.
   Percobaan pertama memakai `getMetaData()` dan hanya menangkap SELECT;
   INSERT/UPDATE dengan kolom ngawur tetap lolos.

   ⚠️ Dua penyesuaian yang wajib ada: `LIMIT NULL` bukan SQL yang sah, jadi
   placeholder di `LIMIT`/`OFFSET` diganti angka. Tanpa itu audit menolak
   pernyataan yang justru benar — berbohong ke arah sebaliknya.

   Perbaikan ini langsung menemukan **dua kolom salah** di binding yang baru
   ditulis pada hari yang sama. Ini keluarga yang sama dengan Peringatan
   #30, #50, dan #73: **alat verifikasi hanya sejujur apa yang benar-benar
   ia jalankan** — dan "di-prepare tanpa error" ternyata tidak menjalankan
   apa pun.

77. **`setPostColor` salah nama di DUA tempat sekaligus.** Kolomnya
   `BrdHighlighted` (bukan warna, melainkan penanda sorot), dan argumen
   keduanya `BrdPosition` — nomor urut kiriman **di papan itu** — bukan
   `BrdId`. Keluarga yang sama dengan Peringatan #45: nomor urut per
   penerima, bukan kunci baris.

78. **`getMapTitle` membaca DATABASE, bukan peta yang sedang dimuat.**
   Jadi ia mengembalikan judul aslinya walau `setMapTitle` sudah
   mengubahnya di memori. Terlihat seperti kelalaian, tetapi skrip memakai
   justru itu: mengembalikan judul asli setelah sebuah event selesai.
   Mengubahnya jadi membaca `map.title` akan membuat peta event tersangkut
   dengan judul eventnya selamanya.

   Dua saudaranya di keluarga yang sama:
   - **`setLight` hanya mengisi peta yang cahayanya MASIH 0.** Menyalakan
     lampu dua kali tidak melakukan apa pun pada putaran kedua, dan peta
     yang punya cahaya sendiri tidak pernah tertimpa.
   - **`copyPoemToPoetry` MEMINDAHKAN, bukan menyalin.** Barisnya di-UPDATE
     ke papan puisi terbit. Meniru namanya sebagai salinan sungguhan akan
     membuat papan draf menumpuk puisi yang sudah terbit.

79. **Cuaca musiman tidak boleh menimpa cuaca buatan.** Penjaganya registry
   per-peta `artificial_weather_timer`: selama capnya masih di masa depan,
   peta itu dilewati `setWeather`/`setWeatherM`. ⚠️ Cap yang **sudah lewat
   dibersihkan di dalam penjaga itu sendiri**, bukan oleh timer tersendiri —
   jadi yang memulihkan peta setelah cuaca buatannya kedaluwarsa adalah
   panggilan `setWeather` berikutnya. Menghapus pembersihan itu membuat peta
   terkunci pada cuaca buatannya selamanya.

80. **`setMap` mengganti UKURAN peta, jadi indeks spasialnya harus dibangun
   ulang.** Peta instance memuat berkas `.map` sembarang ke slot yang sama,
   dan ukurannya berbeda-beda. Karena itu `xs`/`ys`/`bxs`/`bys` di
   `MapData` **tidak final**, dan `replaceGeometry()` mendaftarkan ulang
   setiap benda yang masih ada menurut koordinatnya.

   ⚠️ Melewati pendaftaran ulang itu tidak melempar error: gejalanya mob
   dan pemain yang "tidak ada" padahal berdiri di sana, karena setiap
   pencarian menunjuk blok yang salah.

81. **`Wire.java` ADA DUA, dan itu disengaja.** Repo klien terpisah
   (keputusan 27 Agustus 2026 — lihat bagian "TREK B: REPO TERPISAH" di
   atas), jadi kontrak protokol RTK2 hidup sebagai dua salinan yang harus
   tetap identik.

   ⚠️ Drift di sini **tidak melempar error**: klien membaca ladang yang
   salah dan menafsirkan byte yang salah, lalu gagal di tempat yang jauh
   dari sebabnya. Ini kegagalan yang paling mahal dicari di seluruh project.

   Tiga penjaga, dan ketiganya wajib:
   - **naikkan `Wire.VERSION` di kedua sisi** pada setiap perubahan —
     handshake menolak versi berbeda dengan pesan jelas, sehingga drift
     muncul sebagai penolakan sambungan, bukan sebagai byte salah tafsir;
   - **salin utuh**, jangan sunting sebelah lalu menyesuaikan yang lain
     dengan tangan;
   - **gerbang penyelaras** yang membandingkan kedua berkas bila repo klien
     ada di mesin yang sama, dan melewati diri sendiri dengan pesan jelas
     bila tidak.

   Peringatan #72 (`settingFlags` tersalin di tiga tempat) adalah kasus yang
   sama dalam skala kecil: **duplikasi tidak pernah salah saat ditulis; ia
   salah saat salah satunya diperbaiki.** Bedanya kali ini duplikasinya
   dipilih sadar, dengan alasan yang lebih kuat daripada risikonya — jadi
   penjaganya yang harus nyata, bukan niatnya.

82. **`Wire.java` tidak boleh mengimpor apa pun yang khusus satu sisi.**
   Ia sempat menerima {@code common.Session} langsung, dan itu terlihat wajar
   selama hanya server yang memakainya. Begitu repo klien berdiri, berkas
   yang <b>harus disalin utuh</b> ternyata membawa serta seluruh lapisan
   jaringan server — dan salinan utuh itulah yang dijaga `wiresync`.

   Sekarang ia membaca lewat antarmuka `Wire.Bytes` (dua method: `u8(pos)`
   dan `rest()`), dan tiap sisi menyediakan adaptornya sendiri —
   `Inbound.bytesOf(Session)` di server, buffer soket di klien. Tanpa
   penyalinan, jadi tidak ada biaya runtime.

   **Aturan umumnya:** berkas yang hidup sebagai dua salinan harus bergantung
   pada bahasa saja. Kopling apa pun ke satu sisi akan memaksa salinannya
   berbeda, dan salinan yang berbeda adalah drift yang sudah dimulai.

83. **DUA penjaga berbeda untuk `Wire.java`, dan keduanya perlu.** Mudah
   tertukar:

   | Yang berubah | Penjaganya |
   |---|---|
   | struktur Java (nama method, refactor) | `./run.sh wiresync` |
   | byte di kabel (opcode, ladang, urutan) | naikkan `Wire.VERSION` di **kedua** sisi |

   ⚠️ **Menaikkan `VERSION` untuk refactor Java adalah kesalahan** — bytenya
   tidak berubah, jadi handshake akan menolak klien yang sebenarnya cocok.
   Sebaliknya, mengandalkan `wiresync` saja tidak cukup: ia hanya berjalan
   bila kedua repo ada di mesin yang sama, sementara `VERSION` ikut terkirim
   ke klien mana pun.

84. **Header `SObj.tbl` punya dua byte yang bukan bagian entri.** Setelah
   `u32 jumlahEntri` ada `u16` bernilai 1 (mungkin versi tabel), lalu entri
   pertama. Melewatkannya menggeser seluruh sapuan dua byte, dan gejalanya
   entri pertama "menyebut 75 frame".

   Yang menangkapnya bukan mata, melainkan **penjaga kewarasan**: batas 12
   frame per objek (angka tertinggi di data asli) membuat tata letak yang
   melenceng gagal di entri kedua, bukan setelah gambarnya terlihat aneh.
   Pasang penjaga seperti itu di tiap pembaca format biner.

   ⚠️ Yang **tidak** terverifikasi: arah tumpukan framenya. Dari 2.217 objek
   yang lebih tinggi dari satu petak di 25 peta sungguhan, 1.061 punya kolom
   kosong di atasnya tetapi 1.156 tidak — jadi dugaan "satu id menutupi satu
   kolom ke atas" **tidak terkonfirmasi**. Itu pertanyaan penggambaran, dan
   hanya bisa dijawab saat gambarnya muncul di layar.

85. **Indeks daftar `status.inventory` BUKAN nomor slot.** Slotnya ada di
   `Item.pos`, dan `CharStatus.inventoryAt(slot)` ada justru untuk itu —
   javadoc-nya bahkan memperingatkan "supaya tidak ada yang salah mengira
   urutan daftar = urutan slot". Tetap saja beberapa tempat memakai
   `inventory.get(slot)` dan membatasi dengan `inventory.size()`.

   Ini bukan kasus teoretis. Pada data nyata kantong Adrielle terisi di slot
   **0–5 lalu 21–26**, dan **keenam** karakter yang punya barang berlubang
   seperti itu — nol yang rapat. Dengan pembacaan indeks, slot 6 mengirim
   barang milik slot 21, dan slot 12 ke atas ditolak mentah-mentah karena
   daftarnya cuma 12 panjang. Tidak ada yang melempar error.

   **SEMUANYA SUDAH DIPERBAIKI 28 Agu 2026.** Enam tempat, bukan empat:
   - `Rtk2ClientView.playerInventorySlotChanged` — `inventoryAt` + `maxInv`;
   - `User.clearInventorySlot` — `removeInventoryAt`, bukan
     `inventory.remove(slot)` yang mencabut menurut indeks daftar;
   - `User.addItemById` — `firstFreeInventorySlot`, bukan `inventory.size()`
     yang bisa memberi slot **yang sudah terisi** (barang di 0,1,2,4 → barang
     baru dapat pos 4, dua barang di satu slot);
   - `User.scriptRemoveItemSlot` — `inventoryAt` + `removeInventoryAt`,
     dibatasi `maxInv` (bentuk lamanya menolak slot di atas jumlah barang
     walau slotnya benar-benar terisi);
   - `Clif.sendAddItem` (jalur RetroTK) — pola yang sama;
   - `CharPersistence.saveItems` — `it.pos`, TITIK. Bentuk lamanya
     `it.pos != 0 ? it.pos : pos` jatuh ke indeks daftar bila posnya nol,
     karena nol dikira "belum disetel". Itu selamat **hanya** selama barang
     slot 0 kebetulan selalu jadi elemen pertama (pemuatannya
     `ORDER BY InvPosition`). Begitu `addItemById` diperbaiki dan slot 0 diisi
     ulang, barangnya ada di ujung daftar dan **tersimpan di slot yang
     salah**. Memperbaiki empat yang pertama tanpa yang ini justru akan
     merusak data.

   Dua helper baru di `CharStatus`: `removeInventoryAt(slot)` dan
   `firstFreeInventorySlot(maks)`. Ujinya `chartest` bagian "kantong
   berlubang" — sepuluh pemeriksaan, dan dua sabotase (slot baru = ukuran
   daftar; buang menurut indeks) membuatnya merah.

87. **Uji yang datanya terlalu jinak tidak menguji apa pun.** Contoh kantong
   di `DbTest` berisi **tepat satu barang di slot 0** — dan pada kantong
   serapat itu indeks daftar selalu sama dengan nomor slot. Enam bug di
   Peringatan #85 bertahan berbulan-bulan karena tidak ada satu pun uji yang
   membedakan keduanya. Contohnya kini berlubang (slot 5, 0, 26) **dan** slot
   0 sengaja bukan elemen pertama daftar.

   Dua pemeriksaan yang selama ini palsu ikut ketahuan begitu datanya
   diperbaiki:
   - `sql.rowCount("SELECT COUNT(*) …")` menghitung berapa **baris hasil**
     yang dikembalikan kueri, dan `COUNT(*)` selalu mengembalikan tepat satu
     baris. Jadi nilainya **selalu 1**, apa pun isi tabelnya — dan
     pemeriksaannya lulus hanya karena contohnya kebetulan berisi satu
     barang. Sekarang `queryInt`.
   - "baris Registry sesuai jumlah entri" membandingkan contoh `c` dengan
     database yang sudah ditambahi `scriptRegistryTest` sebelumnya. Sekarang
     dibandingkan dengan karakter yang dimuat ulang — dua pembacaan atas
     keadaan yang sama.

   ⚠️ Pola yang berulang: **pemeriksaan yang jawabannya tidak pernah
   bergantung pada yang diperiksa bukan pemeriksaan.** Kalau sebuah uji tidak
   pernah bisa merah, ia hanya menghias laporan.

86. **Klien RTK2 tidak pernah menerima isi kantongnya.**
   `Rtk2ClientView.playerEnteredWorld` mengirim identitas, peta, status,
   posisi, refresh — lalu berhenti. Karakter uji membawa 12 barang dan
   kliennya menerima nol. Tidak ada yang gagal; kantongnya cuma kosong
   selamanya, dan itu tampak seperti masalah di sisi klien.

   **Diperbaiki 28 Agu 2026** lewat `bawaan(sd)`: menyapu `0..maxInv` dan
   `0..Equip.COUNT`, mengirim hanya slot yang **terisi** (klien mulai dengan
   kantong kosong, jadi slot kosong tidak perlu dikirim). Terverifikasi
   melawan server hidup: `slotKantong x12` di slot 0–5 dan 21–26,
   `slotPerlengkapan x5`, cocok persis dengan database.

   ⚠️ Yang menemukannya bukan gerbang mana pun, melainkan **menyambungkan
   klien sungguhan ke server sungguhan**. Gerbang di kedua repo menguji
   simetri kodenya sendiri; tidak satu pun bisa tahu bahwa sebuah peristiwa
   tidak pernah dikirim. Alatnya `RTK-client/run.sh probe`.

88. **Lima celah port yang SEMUANYA "mengembalikan nil diam-diam".** Ditemukan
   28 Agu 2026 dengan menjalankan klien RTK2 sungguhan melawan server
   sungguhan (`RTK-client/run.sh livetest`). `luaaudit` melaporkan **0
   temuan** pada saat yang sama — dan itu benar menurut ukurannya: tidak satu
   pun nama hilang. Yang salah adalah nilai yang dikembalikan.

   | Celah | Akibat |
   |---|---|
   | `Item(x)` terdaftar sebagai *placeholder* — konstruktor tanpa getter | **setiap** ladang nil. Skrip memakainya **2.192×** di **409 berkas**; `Item(x).id` sendiri 1.163×. Toko NPC menampilkan barang tanpa harga karena `table.insert(prices, Item(i).price)` tidak menyisipkan apa pun |
   | `NPC(x)` hanya membungkus argumennya, tidak pernah mencari NPC-nya | `NPC("Tower").look` nil → `onScriptedTilesArena.lua` gagal di dalam `convertGraphic`, **bukan** di baris pencariannya |
   | `NpcRegistry.byName` diindeks `NpcIdentifier`, peka besar-kecil | C memakai `NpcDescription` + `strcmpi` (`npc.c:280`, `map_name2npc`). NPC bernama "Tower" ada di database sebagai identifier "ArenaMasterNpc" |
   | `player.attacker` tidak ada (hanya `Mob` yang punya) | dipakai skrip **289×**; `player.lua:9` gagal setiap kali pemain memakai barang penyembuh |
   | Daftar beli mengirim **identifier**, C mengirim **nama tampilan** (`clif.c:12455`) | `buyExtend` mencocokkan jawaban dengan `Item(x).name` (nama tampilan) — tidak pernah cocok, `return nil`, toko terbuka lalu diam |

   ⚠️ Pola bersamanya: **nil di Lua tidak meledak di tempat ia lahir.** Ia
   meledak nanti, di fungsi lain, saat dipakai berhitung atau di-index. Setiap
   satu dari lima ini muncul sebagai error di berkas yang sama sekali tidak
   bersalah. Alat statis tidak bisa melihatnya; hanya menjalankannya bisa.

89. **Pilihan menu RTK2 0-basis, lapisan logika 1-basis.** `player.lua`
   mengembalikan `options[selection]` dan tabel Lua mulai dari 1; klien
   RetroTK memang mengirim 1-basis. Jalur RTK2 meneruskan 0-basis apa adanya,
   jadi pilihan pertama jadi `options[0]` = nil: **seluruh sistem menu NPC
   mati untuk klien RTK2** — NPC bicara sekali lalu membisu, tanpa satu pun
   error. Penyesuaiannya di `Inbound.jawaban`, dan `Answer.choice` kini
   didokumentasikan sebagai 1-basis.

90. **Uji luring tidak bisa menemukan "sesuatu yang tidak terjadi".** Gerbang
   di kedua repo menguji simetri kodenya sendiri: bingkai yang disusun repo
   ini, dibongkar repo ini. Tidak satu pun bisa tahu bahwa server tidak
   pernah mengirim isi kantong, atau bahwa `Item()` mengembalikan nil, atau
   bahwa jawaban menu meleset satu. Semuanya ditemukan `livetest` —
   klien sungguhan, server sungguhan, karakter sungguhan.

   ⚠️ Jalankan `RTK-client/run.sh livetest` setiap kali menyentuh protokol,
   binding skrip, atau alur masuk dunia. Tujuh gerbang luring tetap perlu,
   tetapi **tidak cukup**.

91. **`CancelledKeyException` di `NetServer` bukan sekadar bising.** Utas
   permainan memanggil `sessionEof()` saat pemain keluar — ia membatalkan
   `SelectionKey` dan menutup channel — sementara utas IO bisa sedang berada
   tepat di antara `key.isValid()` dan pembacaan kesiapannya. `isReadable()`
   dan `isWritable()` masing-masing memeriksa keabsahan sendiri, jadi
   pemeriksaan kedua melempar.

   ⚠️ Yang membuatnya lebih dari kosmetik: pengecualiannya lolos ke penangkap
   `RuntimeException` **di luar** lingkaran per-key, sehingga **seluruh sisa
   key pada putaran itu ikut terbuang**. Satu pemain keluar menunda IO pemain
   lain sampai putaran berikutnya. Hal yang sama berlaku di
   `applyWriteInterest()`, yang letaknya bahkan di luar lingkaran — satu
   pengecualian di sana membuang seluruh putaran `select()`.

   Perbaikannya: `readyOps()` dibaca **sekali**, dan `CancelledKeyException`
   ditangkap **per key** sebagai keadaan normal, bukan kesalahan.

92. **Balasan char server dipasang ke fd TANPA memeriksa sesinya masih sama.**
   Ini yang paling berbahaya dari semuanya, dan ditemukan uji sambung-putus
   berulang di `livetest`.

   `intif_load` mengirim nomor fd ke char server; balasannya
   (`parseCharLoad`) memasang karakter ke `onlineChars[fd]`. Tetapi
   **nomor fd dipakai ulang**: `newFd()` memberikan slot kosong terkecil ke
   sambungan berikutnya. Kalau pemain memutus tepat setelah memperkenalkan
   diri, balasannya tiba saat fd itu sudah milik orang lain — dan
   **karakter pemain pertama dipasang ke sambungan pemain kedua**.

   Gejalanya di log cuma "memperkenalkan diri dua kali"; pemain kedua gagal
   masuk tanpa penjelasan. Dalam permainan hidup dengan dua pemain yang
   menyambung berdekatan, akibatnya jauh lebih buruk daripada gagal masuk.

   Penjaganya `MapIntif.menunggu`: permintaan mencatat nama **dan objek
   `Session`**-nya. Sambungan baru selalu objek `Session` berbeda, jadi
   perbandingan identitas menutup kasus "orang yang sama menyambung ulang"
   sekalipun. `handleDisconnect` melupakan permintaan yang menggantung.

   ⚠️ Balapan tidak bisa ditunggu, ia harus **dipancing**. `livetest`
   menyambung–memutus 8× beruntun dengan menutup tepat setelah mengirim,
   bukan setelah jawabannya sampai. Pada uji pertama sesudah churn itu,
   sambungan berikutnya langsung gagal — dan penjaganya kini membuang 27
   balasan basi dalam tiga putaran uji.

93. **Merapal mantra: SELESAI 28 Agu 2026** — `OP_CAST`,
   `ClientCommands.playerCastsSpell`, dan port `clif_parsemagic`
   (clif.c:8804). Urutannya mengikat dan diambil apa adanya: aether → bisu →
   bentuk muatan → kait global `onCast` → `<nama>.cast(pemain, sasaran)`.
   `SpellDb` kini memuat `SplType`, `SplMute`, `SplCanFail`, `SplAether`,
   `SplActive`.

   ⚠️ **Bentuk muatan ditentukan SERVER**, dari `SplType` mantra di slot itu —
   bukan diakui klien. Klien tidak boleh memilih bentuknya sendiri: kalau ia
   salah mengaku, server membaca ladang yang tidak ada.

   ⚠️ **Bisu itu ambang, bukan ya/tidak.** Mantra dibungkam hanya bila
   `SplMute <= sd.silence`; menyederhanakannya jadi boolean membungkam
   semuanya sekaligus.

   ⚠️ **Jenis mantra yang tidak dikenal TIDAK dirapal** (C: `default: return
   0`). Merapal dengan muatan yang salah bentuk lebih buruk daripada tidak
   merapal.

94. **Server tidak pernah memeriksa bingkai masuk habis terbaca.** Klien yang
   mengirim ladang tambahan diterima diam-diam: panjang bingkainya tetap
   benar, jadi byte berlebih cuma dilewati. Itu persis yang terjadi pada
   ladang kata sandi hantu di `OP_HELLO` — dikirim berbulan-bulan, tidak
   pernah dibaca, tanpa satu pun tanda.

   `Inbound.dispatch` kini menuntut `r.rest() == 0` dan menutup sambungan bila
   tidak. Sisi klien sudah punya penjaga setara (`Blocks.sisaHarusHabis`)
   sejak awal; sisi server tertinggal.

95. **`bll_getattr` membagikan ~20 atribut PETA ke setiap benda di atasnya**
   (sl.c:4350) — pemain, mob, NPC, barang lantai. Port ini hanya memberikannya
   ke objek `Map`, sehingga `player.mapTitle` (dipakai **141×**),
   `player.region` (15×), dan `player.warpOut` (6×) semuanya nil. Mantra
   `gateway` gagal dengan "attempt to compare nil with number" **di berkasnya
   sendiri**, bukan di tempat yang salah.

   ⚠️ Perbaikan pertamanya **merusak lebih banyak daripada yang diperbaiki**:
   `WorldBindings.bacaAtribut` berakhir `default -> LuaValue.NIL`, **bukan
   null**. Meneruskan sembarang nama ke sana membuat setiap atribut pemain
   dijawab nil dan seluruh method-nya tertutupi — "attempt to call nil" di
   empat berkas yang tidak bersalah. Yang benar: **daftar tertutup**, diambil
   persis dari `bll_getattr`, dan `NIL` diterjemahkan kembali jadi `null`.

   Pelajarannya sama seperti Peringatan #88: **fungsi yang mengembalikan
   `LuaValue.NIL` alih-alih `null` mengaku sudah menjawab.** Periksa ujung
   setiap getter sebelum merantainya ke getter lain.

96. **Pemain kedua tidak menerima satu byte pun — DIPERBAIKI 28 Agu 2026.**
   Setelah sambung-putus berulang, sambungan berikutnya sering mati sebelah:
   server mencatat pemainnya masuk dunia dan soketnya tetap **0 byte**. Dua
   dari tiga percobaan, nol error.

   ⚠️ **Sebabnya penjaga fd-reuse dari Peringatan #92 itu sendiri.** Ia
   memakai `menunggu.remove(clientFd)` di baris pertama, jadi balasan
   **basi** untuk fd yang sudah dipakai ulang ikut **menghapus entri
   permintaan yang MASIH HIDUP** di fd itu. Balasan basinya ditolak dengan
   benar; balasan yang sah tiba sesudahnya, tidak menemukan entri apa pun,
   dan ikut dibuang. Perbaikan yang memperkenalkan bug lebih halus daripada
   yang diperbaikinya.

   Yang benar: **lihat dulu (`get`), hapus hanya setelah cocok.**

   ⚠️ Empat jam ditghabiskan menuduh jalur tulis — `applyWriteInterest`,
   `CancelledKeyException`, minat tulis, throttle IP — karena gejalanya
   "byte tidak keluar". Yang akhirnya menunjukkannya: melacak **nomor fd**
   dari permintaan sampai balasan, bukan melacak byte. Kalau gejalanya di
   satu lapisan tetapi jejaknya bersih, lapisan itu bukan tempatnya.

   Penjaga regresinya di `livetest`, **setelah** churn. Menaruhnya sebelum
   churn membuat gerbangnya selalu hijau dan buta terhadap bug ini —
   dibuktikan dengan mengembalikan bugnya: 3 dari 3 jalanan merah, dan hijau
   lagi setelah dipulihkan.

97. **Daftar abaikan: SELESAI 28 Agu 2026** — `OP_IGNORE` (aksi 2 tambah,
   3 hapus, mengikuti `iCmd` di `clif_parseignore`), penyimpanan di
   `User.daftarAbaikan`, penyaring di jalur ucapan.

   ⚠️ **Nama `clif_isignore` di C menyesatkan**: ia mengembalikan **0 bila
   salah satu mengabaikan yang lain** dan 1 bila boleh lewat. Padanan di sini
   `User.bolehSalingDengar`, dinamai menurut apa yang dijawabnya.

   ⚠️ **Daftarnya TIDAK disimpan** dan hilang saat pemain keluar — itu memang
   perilaku C (rantai di memori, hanya diisi `clif_parseignore`). Tabel
   `Friends` adalah hal yang **berbeda** meski namanya mirip: ia dipakai
   binding `getFriends`, dan pemakaiannya di konten sudah dikomentari.

   ⚠️ **Penyaringnya hanya untuk UCAPAN.** Di C ia dipasang di
   `clif_send_sub` dengan syarat `RBUFB(buf,3) == 0x0D` — paket obrolan saja.
   Memasangnya di semua siaran akan menyembunyikan gerak dan kemunculan
   benda: yang diabaikan jadi tak terlihat, bukan tak terdengar.

98. **Jalur ucapan pemain adalah `playerSpoke`, BUKAN `objectSpoke`.**
   `speech.lua` memanggil `player:speak()`; `player:talk()` dipakai benda dan
   pesan khusus. Penyaring abaikan sempat dipasang hanya di `objectSpoke`,
   sehingga ucapan biasa lolos tanpa disaring sama sekali.

   ⚠️ Yang menyembunyikannya: **uji dua arah yang lulus tanpa arti.** Area
   pandang server adalah persegi x±9 / y±8 yang **digeser** (bukan dipotong)
   di tepi peta — meniru C persis — sehingga jangkauan ucapan **tidak
   simetris**: pemain di pojok mendengar yang di tengah, tidak sebaliknya.
   Uji "B tidak mendengar A setelah diabaikan" lulus karena B memang tidak
   pernah mendengar A, dengan atau tanpa abaikan.

   Pelajarannya: **setiap uji "tidak terjadi" wajib punya baseline "terjadi"
   lebih dulu.** Tanpa itu ia tidak membuktikan apa pun, dan justru
   menyembunyikan bug. Sifat dua arahnya kini diuji di `cliftest`, tempat
   posisi tidak ikut campur.

99. **`session_eof(fd)` menutup sambungan menurut NOMOR fd, dan nomor fd
   dipakai ulang.** Objek `Session` yang lama bisa masih tersimpan di
   antrean logika setelah slotnya dibebaskan. Ketika utas logika akhirnya
   memprosesnya, `sessions[3]` sudah berisi sambungan **pemain lain** —
   dan `sessionEof(s.fd)` menutup sambungan pemain itu.

   Gejalanya: pemain menyambung, mengirim `HELLO`, server memuat
   karakternya, menulis "masuk dunia" di log — lalu klien menerima
   **0 byte, 0 peristiwa**. Tidak ada satu pun error di mana pun. Dua dari
   delapan percobaan.

   ⚠️ **Penjaga nullnya tidak cukup.** `if (session(s.fd) != null)` benar
   secara harfiah tapi salah maksudnya: yang ditanyakan "masih ada isinya?",
   yang seharusnya ditanyakan "masih **objek yang sama**?". Tiga tempat di
   `NetServer` mengidapnya sekaligus (`handle()` di pintu masuk, di dalam
   lingkaran parse, dan cabang eof). Kini semuanya membandingkan objek, dan
   ada `sessionEof(Session)` beridentitas di samping versi ber-fd.

   Ini keluarga yang sama dengan Peringatan #96, satu lapisan lebih bawah:
   **fd yang dipakai ulang adalah nama yang berpindah pemilik.** Setiap
   struktur yang menyimpan fd melewati batas waktu — antrean, permintaan
   yang menunggu, timer — harus menyimpan identitas pemiliknya juga.

100. **Sistem grup: `player.group` pemain SENDIRIAN berisi dirinya sendiri,
   bukan tabel kosong.** `sl.c:7058` punya cabang `else` yang mengisi tabel
   dengan `sd->status.id` saat `group_count == 0`. Empat belas skrip
   memutari daftar ini.

   ⚠️ Akibat kalau salah — dan ini yang ditemukan saat memport grup:
   **`exp.lua` membagi pengalaman lewat daftar itu.** Tabel kosong berarti
   `#player.group == 0`, perulangannya tidak berjalan sekali pun, dan
   pemain sendirian tidak mendapat pengalaman apa pun dari mob — tanpa
   error, tanpa jejak di log.

   ⚠️ Yang jauh lebih besar dan ikut ketahuan: **kait `onGetExp` tidak
   pernah dipanggil sama sekali** di port ini. `clif.c:2474` memanggilnya
   tepat setelah jatuhan mob, bersama `addtokillreg` dan `pc_checklevel`;
   ketiganya hilang dari `MobRegistry.reapDead`. Artinya membunuh mob tidak
   memberi pengalaman **dan** tidak menambah hitungan bunuh yang dibaca
   skrip quest. Sembilan gerbang luring hijau selama itu: tidak ada yang
   bisa melihat sesuatu yang tidak terjadi.

101. **Paket setelan pemain adalah SAKLAR, bukan penetapan — dan begitu
   juga `player.settings` di Lua.** `clif_changestatus` (clif.c:14240,
   dipanggil dari `case 0x1B` dengan `type = RFIFOB(fd,6)`) menerima nomor
   setelan lalu menjalankan `settingFlags ^= FLAG_x`. Klien tidak pernah
   mengirim nilai barunya. Jembatan Lua `sl.c:6881` sama: `player.settings
   = 2` **membalik** bit grup, ia tidak menyetel setelan menjadi 2 —
   padahal terbaca persis seperti penetapan biasa.

   Yang mudah terlewat di dalamnya:
   - **Ragam 10 (obrolan klan) bukan bendera setelan.** Ladangnya sendiri
     (`status.clan_chat`), dibalik dengan `(x + 1) % 2`, dan **tidak**
     menyusulkan kiriman status seperti ragam lain.
   - **Ragam 13 (suara) mengabaikan paket pembuka** — `if (RFIFOB(fd,4)
     == 3) return 0`, dengan penandanya kebetulan nomor urut paket. Tanpa
     itu setiap pemain mematikan suaranya sendiri saat masuk dunia.
   - **Ragam 2 (grup) mati = KELUAR dari grup**, bukan sekadar menolak
     ajakan berikutnya.
   - **Ragam 14/15 (helm/kalung) juga menulis registry** `show_helmet` /
     `show_necklace` yang dibaca skrip, ditambahkan di C 6 April 2017.
   - **Urutan "kirim status" dan "kirim teks" berbeda-beda antar ragam**;
     grup dan tukar mengirim status lebih dulu, sisanya teks dulu.
   - **Perataan spasi pada teksnya berarti.** Klien menampilkannya dalam
     kolom berhuruf lebar tetap; "Show Necklace" memang punya spasi lebih
     banyak dari yang lain — salah ketik C yang terlihat di layar.
   - **Ragam 0 bukan setelan sama sekali**: ia naik/turun tunggangan
     (`clif_findmount`), dan pesan "nothing here that you can ride" datang
     dari **pemanggilnya** setelah kait `onMount` gagal mengubah keadaan —
     sehingga peta yang melarang menunggang memberi **dua** pesan.

   ⚠️ **Kontrol negatif menemukan lubang di uji hidupnya sendiri.** Versi
   pertama mengirim **dua** paket ragam-tak-dikenal lalu memeriksa "kata
   setelan tidak berubah". Kalau ragam tak dikenal ternyata membalik sebuah
   bit, dua paket membaliknya kembali dan pemeriksaannya tetap hijau. Untuk
   sifat XOR, **jumlah paket harus ganjil** — genap selalu lulus. Itu
   varian baru dari #98: bukan "tidak terjadi" tanpa baseline, tapi "tidak
   terjadi" yang **saling meniadakan**.

102. **Gerbang `livetest` MEMAKAN data karakter uji, dan gagalnya muncul di
   pemeriksaan yang salah.** Langkah `barang` memanggil `use` pada slot
   pertama; barang yang bisa dimakan habis. Setelah puluhan jalan, kantong
   `Adrielle` terkuras **10 → 0** — lalu yang merah bukan langkah itu,
   melainkan **"kantong sampai saat masuk dunia (bukan kosong diam-diam)"**
   di langkah masuk dunia. Gejalanya persis seperti server berhenti
   mengirim inventaris, padahal ujinya sendiri yang menghabiskannya.

   Pelajarannya: **uji yang mengubah keadaan permanen harus dibatasi supaya
   tidak bisa menghabiskannya.** Langkah `barang` kini berhenti bila kantong
   tinggal di bawah tiga slot; uji setelan mengembalikan setiap bit yang
   dibaliknya.

   ⚠️ **Kolom slot inventaris di MySQL bernama `InvPosition`.** Saat
   memulihkan kantong `Adrielle` lewat `INSERT` tanpa kolom itu, keenam
   barisnya mendarat di slot 0 dan klien hanya melihat **satu** barang —
   server melaporkan "6 barang" dengan benar, jadi log server dan tampilan
   klien tidak cocok tanpa satu pun error. Keluarga yang sama dengan
   Peringatan #85: indeks daftar dan nomor slot adalah dua hal berbeda.

103. **Entri tabel panjang paket antar-server yang salah menandai "variabel"
   MEMUTUS sambungan char server — dan baru terpicu bertahun kemudian.**
   `Mapif.PACKET_LEN_TABLE` menandai `0x3009` (papan pesan) sebagai `-1`
   alias variabel, dan pembaca variabel mengambil panjangnya dari
   `rfifoL(2)` — offset yang di paket itu justru berisi **fd klien**.
   Aliran antar-server bergeser, paket berikutnya terbaca sebagai opcode
   `0x0000`, char server menjawab "Unhandled inter-server packet" lalu
   **menutup sambungan map server**. Sesudah itu tidak ada karakter yang
   bisa dimuat, sehingga uji-uji berikutnya gagal karena hal yang sama
   sekali tidak mereka uji. Di C entri ini `sizeof(struct board_show_0)+2`
   — **tetap**, bukan variabel. Perbaikannya: paket papan berukuran tetap
   34 byte dengan ladang nama 16 byte.
   ⚠️ Yang membuatnya bertahan begitu lama: sisi **tampilan** papan sudah
   lama diport, tapi tidak ada satu pun jalur MASUK yang pernah memintanya.
   Kode yang tidak pernah dipanggil tidak pernah salah. Ia baru meledak
   pada menit R1 memberi papan tombolnya yang pertama.

104. **Penghitung peristiwa di `livetest` hanya menghitung yang DIREKAM.**
   `Dunia.peristiwa` naik di dalam `catat()`, yang hanya dipanggil dari
   method yang perekamnya override. Peristiwa yang dibongkar `EventDecoder`
   tetapi tidak di-override — `EV_OBJECT_ACTED`, `EV_BOARD_LIST` — tidak
   pernah terhitung. Dua uji ditulis dengan pola `peristiwa > sebelum` dan
   keduanya **merah padahal servernya benar**; satu lagi bisa saja hijau
   karena peristiwa lain yang kebetulan lewat. Periksa peristiwa yang
   dimaksud secara langsung (rekam nilainya, lalu uji nilainya), jangan
   menghitung jumlah.

105. **Uji yang "mengembalikan keadaan" bisa MERUSAKNYA bila yang
   dikembalikan tidak pernah dibaca.** Blok kalender dunia di `cliftest`
   menyimpan `WorldTime.hour/day/season/year` sebagai "nilai lama", menik
   satu jam, lalu menuliskannya kembali ke tabel `Time`. Tetapi `cliftest`
   tidak pernah memanggil `WorldTime.load()`, jadi yang tersimpan adalah
   **nilai bawaan** (0/1/1/1) — dan pemulihannya menimpa kalender sungguhan
   (4/72/2/6) dengan bawaan itu. Gerbangnya tetap hijau; yang hilang adalah
   data, bukan pemeriksaan.
   ⚠️ Pola amannya: **baca dulu dari sumbernya**, baru simpan sebagai nilai
   lama. Keluarga yang sama dengan Peringatan #102 (`livetest` memakan
   barang karakter uji): uji yang menulis ke data nyata harus memulihkan
   apa yang BENAR-BENAR ada, bukan apa yang kebetulan ada di memori.

106. **Satu nilai uji tidak cukup untuk membedakan jawaban nyata dari
   jawaban kebetulan.** Uji `curSeason` mula-mula menyetel musim dunia ke 3
   lalu menuntut binding menjawab 3. Kontrol negatif — mengembalikan versi
   lama yang menghitung musim dari **bulan komputer** — tetap **HIJAU**,
   karena bulan Agustus kebetulan menghasilkan 3 juga. Ujinya diperbaiki
   dengan menjalankan dua himpunan nilai yang berbeda; jawaban tetap
   maupun jawaban jam dinding tidak bisa mengikuti keduanya.

107. **Binding pembaca petak MELEMPAR di tepi peta, dan yang mati adalah
   skripnya.** `getObject`, `getTile`, dan `getPass` mengindeks larik petak
   langsung seperti C — tetapi Java memeriksa batas dan C tidak. Pemain
   yang berdiri di tepi peta membuat `onLook` memeriksa petak di
   koordinat −1, `IndexOutOfBoundsException` naik lewat LuaJ, dan
   **seluruh kait `onLook` mati** (`scripts.lua:265`) untuk pemain itu.
   Tidak ada paket yang salah dan tidak ada gerbang luring yang merah;
   yang terlihat hanyalah "melihat sesuatu tidak melakukan apa-apa" di
   pinggir peta. Ketiganya kini lewat penjaga `diLuar()` yang mengembalikan
   0, bukan melempar — jawaban yang sama dengan yang dibaca C dari memori
   di luar larik, tanpa membaca memori di luar larik.

108. **Pemuat portal MEMBUANG portal lintas map server — di C juga.**
   `map_readwarp()` melewati portal yang peta TUJUANNYA tidak dimuat, dan
   peta milik map server lain memang tidak pernah dimuat. Akibatnya cabang
   lintas-server di `pc_warp()` **tidak pernah bisa dicapai lewat portal**
   di C: kodenya ada, jalannya tidak. Port ini sengaja MENYIMPANG — hanya
   peta ASAL yang wajib dimuat — supaya fiturnya benar-benar terpakai.
   Penyimpangan disengaja, bukan port yang meleset; jangan "diperbaiki"
   kembali ke perilaku C. Pemeriksaan batas koordinat ikut disyaratkan
   hanya bila peta tujuan ada di server ini.

109. **C MENGUSIR pemain yang tersimpan di peta milik server lain — badan
   `intif.c:215` kosong.** Karakter yang tersimpan di peta server lain
   (mis. karena berpindah lalu server mati) tidak bisa masuk sama sekali:
   `enterWorld` menolak, dan tidak ada yang mengalihkannya. Port ini
   mengalihkannya (`transferKeServerLain`) alih-alih mengusirnya.
   ⚠️ Perpindahannya butuh paket sampai ke klien SEBELUM sesi ditutup:
   penutupan diberi jeda `TUTUP_SETELAH_ALIH_MS` (500 ms) di `MapIntif`.
   Menutup langsung tidak melempar apa pun — kliennya hanya terputus
   tanpa pernah tahu ke mana harus pergi.

110. **Langkah `livetest` yang berjalan harus MENUNGGU langkahnya sampai,
   bukan tidur sekian milidetik.** Pencari jalur uji C3 memberi jeda tetap
   220 ms per langkah lalu menyimpulkan "posisi tidak berubah = petak
   terhalang". Kabar langkah datang lebih lambat dari itu, jadi **setiap
   petak koridor dicap terhalang**, rutenya habis, dan langkahnya berakhir
   dengan "LEWATI: portal tidak terpicu" — uji yang melewati dirinya
   sendiri karena pewaktunya, bukan karena fiturnya. Tunggu akibatnya
   (`tungguSenyap`), jangan menakar waktu.

111. **Perekam `livetest` tidak memindahkan pemainnya pada langkah yang
   BERHASIL.** `Dunia.melangkah()` hanya menaikkan penghitung; `x,y` cuma
   berubah kalau server kebetulan mengirim `posisi` atau `langkahDitolak`.
   Setiap uji yang berjalan lalu memeriksa "sampai di mana" karena itu
   buta — varian ketiga dari Peringatan #104. Perekam kini menghitung
   petak barunya dari arah + petak asal yang dikirim server.

112. **Langkah uji yang bisa MELEWATI DIRINYA SENDIRI bukan gerbang.**
   Uji C3 butuh map server kedua, jadi versi pertamanya menulis "LEWATI"
   bila perpindahan tidak terjadi — yang berarti C3 bisa mundur total dan
   gerbangnya tetap hijau. Kini prasyaratnya diperiksa TERPISAH dari yang
   diuji: port 2002 dijajaki dulu; kalau tidak ada server kedua langkahnya
   memang dilewati dengan pesan cara menjalankannya, tetapi kalau ADA,
   gagal berpindah adalah **MERAH**. Prasyarat yang tidak terpenuhi dan
   fitur yang rusak harus terlihat berbeda.

113. **Menerjemahkan PROSA bisa memutus quest, bukan hanya kata kuncinya.**
   Kata kunci `speech` sudah diterjemahkan lebih dulu (26 Agu 2026), jadi
   yang tersisa "cuma prosa". Tetapi prosa itu memuat kalimat yang
   MENYURUH pemain mengetik kata kunci — dan terjemahan bebasnya memilih
   kata lain: `smith.lua` menyuruh mengucapkan 'Acara Istimewa' sementara
   penjahitnya hanya menerima `"acara khusus"`, dan `woodland_angel.lua`
   menyuruh mengucapkan 'Finish' sementara kaitnya menunggu
   `"selesaikan"`. Keduanya membuat quest buntu tanpa satu pun error.
   ⚠️ Pemeriksanya sekarang otomatis: `tools/terjemahan/petunjuk-ketik.py`
   membandingkan setiap kata berkutip di kalimat perintah dengan daftar
   kata kunci `speech` yang benar-benar ada di korpus. Jalankan setiap
   kali menyentuh dialog.

114. **Opsi menu dibangun dengan TIGA konstruksi, dan alat yang hanya
   tahu satu akan melapor "nol sisa" dengan percaya diri.** Selain daftar
   sebaris `menuSeq(topik, {"A","B"}, {})`, konten memakai
   `local opts = {...}` yang diserahkan lewat NAMA, dan
   `table.insert(opts, "…")` yang menambah belakangan. Ketiganya tampil
   di layar pemain. Alat inventaris sempat melapor **0 sisa** tiga kali
   berturut-turut sementara klien sungguhan menampilkan menu
   `[Buy, Sell, Banking, …]` — setiap kali yang menemukan adalah
   `livetest`, bukan alatnya. ⚠️ Angka dari alat statis hanya sejujur
   konstruksi yang ia kenal; yang memutuskan tetap klien.

115. **Literal di dalam tabel dialog belum tentu teks layar.** Tabel yang
   diserahkan ke `dialogSeq` memuat `convertGraphic(723, "item")` — dan
   "item" di situ adalah nama RUANG GRAFIK. Menerjemahkannya membuat
   gambar dialog hilang tanpa error. Pemindai kini membutakan argumen
   panggilan identifier (`convertGraphic`, `Item`, `addItem`, …) sebelum
   mengambil literal dari sebuah tabel.

116. **`next` / `previous` / `quit` BUKAN teks layar — itu nilai
   protokol.** `player.lua` membangun opsi paging dialog dari string
   yang sama persis dengan yang DIKIRIM KLIEN (`answerDialog "next"` di
   `Ui.java`). Menerjemahkannya membuat kedua sisi player.lua tetap
   konsisten — sehingga tidak ada yang melempar — tetapi klien mengirim
   "next" dan skrip menunggu "berikutnya": **seluruh dialog NPC berhenti
   bisa dibalik halamannya.** Ditangkap `scripttest` (6 pemeriksaan
   merah), bukan oleh alat terjemahan. Kini terdaftar di
   `tools/terjemahan/dikecualikan.json`.

117. **Skrip penambal yang GAGAL DIAM-DIAM membuat pengukuran berbohong.**
   Dua kali `str.replace` di alat inventaris tidak menemukan polanya
   (beda escaping) dan tetap menulis berkas tanpa perubahan; hasilnya
   "masih Inggris: 0" diukur dengan detektor yang belum ditambal, dan
   pekerjaan yang belum ada dilaporkan selesai. ⚠️ Setiap penambalan
   berkas alat sekarang memakai `assert pola in isi` sebelum menulis —
   penambal yang tidak menemukan sasarannya harus MELEMPAR, bukan
   melanjutkan.

118. **Nomor slot kantong dari klien adalah POSISI, bukan indeks daftar —
   dan seluruh jalur perintah masuk salah memakainya.** `CharStatus.inventory`
   adalah `List` yang RAPAT, sedangkan nomor slot yang dikirim ke klien
   berasal dari `inventoryAt()` yang berbasis posisi. Kantong nyata
   BERLUBANG (karakter uji: posisi 0, 2, 3, 5 dalam daftar 4 elemen), jadi
   `inventory.get(slot)` mengenai barang yang SALAH — atau, bila posisinya
   melewati ukuran daftar, tidak melakukan apa pun. Tujuh perintah kena:
   jatuhkan, pakai, kenakan, makan, lempar, serahkan, dan tawarkan-tukar.
   ⚠️ Yang membuatnya bertahan lama: **tidak ada pesan galat**. Pemain
   menekan "jatuhkan" dan tidak terjadi apa-apa. Javadoc `inventoryAt()`
   sudah memperingatkan jebakan ini sejak lama — yang hilang adalah
   pemakaiannya di jalur masuk.

119. **Mengubah inventaris di server bukan berarti klien tahu.**
   `FloorItemRegistry.dropFromInventory()` dan `User.addItemById()`
   mengubah isi kantong tanpa memanggil `playerInventorySlotChanged/Cleared`.
   Akibatnya barang yang dijatuhkan tetap terlihat di kantong, dan barang
   yang dipungut tidak pernah muncul — sampai pemain login ulang. Tidak ada
   yang melempar. Setiap tempat yang menyentuh `status.inventory` wajib
   mengabarkan slot yang berubah.

120. **`.ID` tidak pernah diimplementasikan — 1.292 pemakaian di 347 berkas
   diam-diam menerima nil.** Skrip memakai `.ID` untuk **id BENDA di dunia**
   (`bl->id` di C), berbeda dari `.id` yang berarti id JENIS barang pada
   benda lantai dan id mob pada mob. Tidak ada satu pun getter Java yang
   menjawab `"ID"`, sehingga:
   - `onPickup.lua` memanggil `player:pickUp(groundItems[i].ID)` →
     `pickUp(nil)` → `pickUp(0)` → id 0 tidak ditemukan → **memungut barang
     dari tanah tidak pernah berhasil, untuk siapa pun, sejak awal**;
   - `herb_pipe.lua` menyetel `player.attacker = player.ID` → nil → kait
     `on_attacked` memakai attacker nil → 12 baris `script error` di
     `map.log` yang terlihat sama sekali tidak berhubungan.
   ⚠️ `luaaudit` tidak bisa melihat ini: ia memeriksa nama METHOD, bukan
   nama ATRIBUT. Satu perbaikan (`idBenda()` di `Bindings`) menyelesaikan
   keduanya sekaligus — dan itulah tandanya bahwa dua gejala yang tampak
   terpisah sering punya satu sebab.

121. **Uji yang memegang barang pemain harus mengembalikannya, dan harus
   MERAH bila gagal.** Versi pertama langkah jatuh-pungut menuntut slot
   kantong KOSONG setelah menjatuhkan — padahal barang bertumpuk hanya
   berkurang jumlahnya. Ia merah untuk server yang benar, lalu berhenti
   sebelum memungut, dan `rabbit_meat` benar-benar tertinggal di lantai.
   Uji yang menyentuh data nyata wajib: (a) menuntut hal yang benar, dan
   (b) berteriak dengan nama barangnya bila pemulihan gagal, supaya barang
   yang tertinggal tidak pernah lolos tanpa diketahui.

122. **Kegagalan yang berbeda harus terlihat berbeda.** "Memungut tidak
   mengembalikan barang" bisa berarti dua hal yang sangat berbeda:
   barangnya masih tergeletak (pungutnya tidak jalan), atau barangnya
   lenyap dari lantai tetapi tidak muncul di kantong (server memungutnya
   lalu lupa mengabari klien — yang kedua terlihat seperti barang HILANG).
   Satu pemeriksaan gabungan tidak bisa membedakannya; dua pemeriksaan
   terpisah langsung menunjuk sebabnya.

123. **Satu proses hanya punya kolam koneksi MILIKNYA.** `CharDb` memakai
   `CharServer.sql` lewat static import; kolam itu hanya tersambung di
   proses char server. Map server punya kolamnya sendiri, dan memanggil
   `CharDb.newChar(...)` dari sana membuat setiap kueri melempar
   `Connection pool is not initialized` — yang **ditelan dan berubah
   menjadi `null`**, lalu diterjemahkan menjadi "gagal membuat karakter".
   Pembuatan karakter tampak berjalan, membalas dengan sopan, dan tidak
   pernah membuat apa pun. Sekarang ada `newChar(Sql, ...)` yang memaksa
   pemanggil menyebut kolam mana yang dipakainya.
   ⚠️ Kesalahannya **tidak muncul di `logs/map.log`**: `org.rtk.common.Sql`
   jatuh ke logger Root, jadi tercatat di `logs/common.log`. Sapuan log
   "0 ERROR di map.log" TIDAK cukup — `common.log` wajib ikut disapu.

124. **fd DIPAKAI ULANG; apa pun yang diingat per-fd wajib dilupakan saat
   putus.** `akunSesi` (fd → akun yang sudah masuk) tidak dibersihkan di
   `handleDisconnect`. Sambungan baru yang kebetulan mendapat nomor fd
   bekas sesi yang sudah masuk akun **mewarisi akun itu**, dan boleh masuk
   ke karakter milik akun tersebut **tanpa sandi apa pun**. Ini lubang
   keamanan, bukan sekadar kesalahan pembukuan. Ditemukan oleh kontrol
   negatif livetest ("sambungan tanpa akun DITOLAK dengan sandi kosong") —
   bukan oleh membaca kode, yang tampak benar.

125. **Jangan pernah mengiterasi daftar yang boleh diubah oleh skrip yang
   dipanggil di dalam iterasi itu.** `NpcRegistry.runTimers` berjalan di
   atas `npcs` sambil memanggil kait Lua yang boleh memunculkan atau
   mencabut NPC. Hasilnya `ConcurrentModificationException` yang
   membatalkan **sisa tik**: seluruh NPC sesudah yang bersangkutan berhenti
   bergerak sampai tik berikutnya. Gejalanya di dunia hampir tak terlihat
   (NPC "kadang" diam), dan jejaknya ada di `logs/common.log` — lihat #123
   soal log yang tidak disapu. Iterasi kini di atas salinan.

126. **Nilai yang ada di satu lapisan belum tentu sampai ke lapisan yang
   mengirimnya.** `--sandi` mengisi `RtkGame.sandi`, tetapi layar masuk
   dibuat dengan konstruktor tanpa sandi — jadi ladang sandinya kosong dan
   yang dikirim ke server adalah **sandi kosong**. Kode di kedua sisi
   terlihat benar; yang salah adalah sambungannya. Ketahuan dari GAMBAR
   TANGKAPAN layar masuk ("Email atau sandi akun salah" dengan ladang sandi
   kosong), bukan dari membaca kode dan bukan dari gerbang mana pun —
   karena livetest berbicara langsung dengan protokol dan tidak pernah
   melewati layar masuk. Jalur antarmuka butuh buktinya sendiri.

127. **Gerbang yang menyiapkan servernya sendiri hanya mematikan server yang
   IA ketahui.** `tools/uji-dua-server.sh` memanggil `./run.sh stop`, yang
   bekerja dari berkas PID milik `run.sh`. Map server yang dijalankan
   dengan tangan (`java -jar dist/RTK-java.jar map`) tidak tercatat di
   sana: ia selamat, tetap memegang port 2001, dan map server milik skrip
   mati dengan `BindException` yang **hanya ada di `logs/map.console.log`**.
   Livetest lalu berbicara dengan server yang salah — server yang mengira
   peta 330 masih miliknya — jadi C3 gagal ("pemain berhenti di (21,13)
   tanpa dialihkan") padahal kodenya benar. Sebelum menjalankan gerbang dua
   server: `pgrep -a java | grep RTK-java` harus KOSONG.
