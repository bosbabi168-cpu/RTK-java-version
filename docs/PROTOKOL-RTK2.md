# RTK2 — protokol arah masuk

Rancangan sendiri, menggantikan format kabel RetroTK. Dokumen ini untuk
orang yang **menulis kliennya** (Trek B, libGDX).

> **Sumber kebenaran adalah `src/org/rtk/map/proto/Wire.java`.** Berkas ini
> ringkasan yang bisa dibaca; kalau keduanya berbeda, kodenya yang benar.
> Semua nilai opcode di bawah adalah konstanta di kelas itu — salin dari
> sana, jangan ketik ulang angkanya.

Hitung ulang jumlah opcodenya dengan
`grep -c "public static final int OP_" src/org/rtk/map/proto/Wire.java` —
jangan percaya angka yang tertulis di prosa mana pun, termasuk di sini.

**Status: kedua arah berdiri.** Masuk lewat `map/proto/Inbound`, keluar
lewat `map/Rtk2ClientView` (51 peristiwa). Keduanya hidup berdampingan
dengan RetroTK; lihat "Hidup berdampingan" di bawah.

## Bingkai

```
u16 panjang    banyak byte SETELAH ladang ini (opcode + muatan)
u16 opcode
...muatan
```

Bingkai terkecil 4 byte (`panjang` = 2, muatan kosong). Batas satu bingkai
8.192 byte, batas satu string 4.096 byte.

**Big-endian di mana pun, tanpa pengecualian.** Tidak ada enkripsi, tidak
ada indeks kunci, tidak ada nomor urut paket.

### Tipe

| Tipe | Bentuk |
|---|---|
| `u8` | satu byte |
| `u16` | dua byte, big-endian |
| `u32` | empat byte, big-endian |
| `u64` | delapan byte, big-endian |
| `str` | `u16 panjang` + byte UTF-8 |

Nomor slot **0-basis**, di kabel maupun di server.

### Contoh

Pemain melangkah ke bawah dari (100, 100):

```
00 07   0100   02   00 64   00 64
 │       │      │     │       └── u16 y  = 100
 │       │      │     └────────── u16 x  = 100
 │       │      └──────────────── u8 arah = 2 (bawah)
 │       └─────────────────────── opcode  = OP_WALK
 └─────────────────────────────── panjang = 7 (2 opcode + 5 muatan)
```

Total 9 byte.

## Opcode

### 0x00xx — sesi

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0001` | `HELLO` | `u32 magic("RTK2"), u16 versi, str namaKarakter` |
| `0x0002` | `PING` | — |

`HELLO` **wajib bingkai pertama**. Magic atau versi yang tidak cocok →
koneksi ditutup, bukan ditebak. Sebelum `HELLO` diterima, opcode lain
diabaikan.

### 0x01xx — gerak & sasaran

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0100` | `WALK` | `u8 arah, u16 x, u16 y` |
| `0x0110` | `CLICK` | `u64 idBenda` |

`arah`: 0 atas, 1 kanan, 2 bawah, 3 kiri. **Nilai di luar 0..3 tidak
menggeser apa pun** — itu perilaku server, bukan kesalahan klien.

`x`/`y` adalah posisi yang **diyakini klien**. Server membandingkannya
dengan catatannya sendiri; berbeda berarti pemain ditarik kembali. Kirim
posisi sebelum melangkah, bukan sesudah.

`CLICK` dengan id 0 berarti "benda yang terakhir diklik" — dipakai untuk
melanjutkan percakapan yang sedang berjalan.

### 0x02xx — barang

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0200` | `PICKUP` | `u8 ragam` (0 sekeping, bukan-nol semua) |
| `0x0201` | `DROP_ITEM` | `u8 slot, u8 ragam` |
| `0x0202` | `DROP_GOLD` | `u64 jumlah` |
| `0x0210` | `HAND_ITEM` | `u8 slot, u16 jumlah` |
| `0x0211` | `HAND_GOLD` | `u64 jumlah` |
| `0x0220` | `WIELD` | `u8 slot` — kenakan isi slot inventaris |
| `0x0221` | `UNEQUIP` | `u8 slot` — **indeks `EQ_*`**, lihat catatan |
| `0x0222` | `EAT` | `u8 slot` — hanya barang `ITM_EAT` |
| `0x0223` | `USE` | `u8 slot` — jenis apa pun |
| `0x0224` | `THROW` | `u8 slot, u8 konfirmasi` |

⚠️ **`UNEQUIP` memakai indeks `EQ_*` apa adanya** (0 senjata, 1 zirah,
2 perisai, 3 helm, 4 kiri, 5 kanan, 6 sub-kiri, 7 sub-kanan, 8 aksesori
wajah, 9 mahkota, 10 mantel, 11 kalung, 12 sepatu, 13 jubah, 14 aksesori
wajah kedua). RetroTK memakai penomoran panel kliennya sendiri —
1, 2, 3, 4, 6, 7, 8, 13, 14, 16, 20–23, berlubang di 5, 9–12, 15, 17–19 —
yang harus diterjemahkan dua arah di server. Itu tabel yang dibuang.

⚠️ **`WIELD` tidak langsung memasang.** Server memeriksa syaratnya lalu
memanggil kait skrip; skrip itulah yang memindahkan barangnya. Klien tidak
perlu tahu soal itu, tetapi perlu tahu bahwa **panel bisa tidak berubah**
bila skripnya menolak — tunggu peristiwa perlengkapan, jangan menebak
sendiri.

⚠️ **`THROW` dijawab dua kali untuk sebagian barang.** Barang
ber-`ItmThrownConfirm` dijawab pesan "Are you sure...?" pada kiriman
pertama; kirim ulang dengan `konfirmasi = 1` setelah pemain menjawab.
Berbeda dari RetroTK, server tidak mengirim paket pertanyaan tersendiri.

`HAND_*` menyerahkan ke **petak yang sedang dihadapi**, dan sasarannya
menentukan tiga perilaku berbeda: pemain membuka jendela pertukaran, mob
menyimpannya (dan menjatuhkannya kembali saat mati), NPC menjalankan kait
skrip `handItem`.

### 0x03xx — bicara

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0300` | `SAY` | `u8 saluran, str teks` |
| `0x0301` | `WHISPER` | `str tujuan, str teks` |

`saluran`: 0 sekitar, 1 berteriak. Teks maksimal 100 huruf.

`tujuan` pada `WHISPER` **tidak selalu nama orang** — empat nama pendek
adalah saluran:

| Tujuan | Arti |
|---|---|
| `!` | klan |
| `!!` | grup |
| `@` | subpath |
| `?` | pemula |

### 0x04xx — pertukaran antar pemain

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0400` | `EXCHANGE_START` | `u64 idLawan` |
| `0x0401` | `EXCHANGE_ITEM` | `u8 slot, u16 jumlah` |
| `0x0402` | `EXCHANGE_GOLD` | `u64 jumlah` |
| `0x0403` | `EXCHANGE_CANCEL` | — |
| `0x0404` | `EXCHANGE_CONFIRM` | `u64 idLawan` |

`EXCHANGE_CONFIRM` mengirim ulang id lawan **dengan sengaja**: server
membandingkannya dengan catatannya, dan yang berbeda membatalkan kedua
sisi. Jangan kirim 0 "karena server sudah tahu".

Persetujuannya **dua tahap**: `CONFIRM` pertama hanya menandai; barangnya
berpindah saat pihak kedua mengirimkannya.

### 0x05xx — pertarungan

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0500` | `ATTACK` | — |

**Tanpa sasaran.** Yang menentukan apa yang kena adalah skrip, yang
mencari sendiri isi petak di depan pemain. Server juga membatasi
kecepatannya menurut senjata — mengirim lebih cepat tidak menambah ayunan.

### 0x06xx — jawaban dialog

| Opcode | Nama | Muatan |
|---|---|---|
| `0x0600` | `ANSWER_MENU` | `u8 ragam, ...` |
| `0x0601` | `ANSWER_DIALOG` | `u8 ragam, ...` |

Ragamnya:

| Ragam | Muatan | Arti |
|---|---|---|
| 0 | — | batal (skripnya dilepas, bukan dilanjutkan dengan nilai kosong) |
| 1 | `u16` | nomor pilihan menu |
| 2 | `str` | teks yang diketik |
| 3 | `str` | nama barang (daftar beli) |
| 4 | `u8` | nomor slot (daftar jual) |
| 5 | `u8` | langkah dialog bertingkat |

⚠️ **Kedua opcode ini tidak boleh disatukan.** Di sisi skrip keduanya
melanjutkan coroutine yang berbeda; salah pintu berarti melanjutkan
percakapan yang salah. `ANSWER_MENU` untuk primitif `menu` dan `input`,
`ANSWER_DIALOG` untuk `dialog`, `menuSeq`, dan `inputSeq`.

## Hidup berdampingan dengan RetroTK

Kedua protokol dilayani di **port yang sama**, dan pembedanya tanpa
keadaan: paket RetroTK selalu diawali `0xAA`, sedangkan byte pertama
bingkai RTK2 adalah byte tinggi ladang panjang — yang tidak akan pernah
`0xAA` selama batas bingkai di bawah 43.520.

Untuk arah **keluar** tidak ada pembeda yang bisa dipakai per paket, karena
separuh peristiwa menyiarkan ke sekitar sebuah benda yang bisa berisi
pemain dari kedua protokol sekaligus. Karena itu arahnya dibalik:
`ProtocolRouter` memanggil **kedua** implementasi untuk setiap peristiwa,
dan masing-masing menyaring penerimanya sendiri lewat `User.rtk2`.
Penyaringnya satu tempat per protokol — `Clif.sessionOf` dan
`Rtk2ClientView.sesi` — sehingga tidak ada jalur yang bisa terlewat.

⚠️ **Menaikkan `Wire.MAX_FRAME` melewati 43.520 akan mematahkan
pembedaan itu.** Ada assertion khusus untuk ini di `cliftest`.

RetroTK dibiarkan hidup karena kliennya sendiri belum ada; menghapusnya
sekarang berarti tidak ada apa pun yang bisa terhubung.

## Arah keluar — peristiwa

Bingkainya sama persis; yang berbeda hanya rentang opcodenya: **`0x8xxx`**
untuk peristiwa keluar, `0x0xxx` untuk perintah masuk. Keduanya jalur
terpisah sehingga sebenarnya boleh bertabrakan — penomoran terpisah hanya
supaya satu baris log langsung terbaca arahnya.

### Tiga hal yang berubah dari RetroTK

**1. Grafik dikirim mentah.** RetroTK punya tiga aturan penambah untuk satu
ladang: **+32768** untuk mob dan NPC, **+49152** untuk ikon kustom, dan
**tanpa penambah** untuk ikon barang biasa. RTK2 mengirim nilainya apa
adanya, ditemani `kind` bendanya; klien yang memutuskan dari kumpulan
grafik mana ia mengambil gambar.

**2. Perlengkapan adalah daftar, bukan offset tetap.** RetroTK menaruh tiap
slot di offset tertentu dan memakai `0xFFFF` sebagai "kosong"; mantel
bahkan **menimpa** zirah di offset yang sama. Di sini server sudah
memutuskan apa yang tergambar, lalu mengirim `u8 jumlah` diikuti
`(u8 slot, u16 grafik, u8 warna)` sebanyak itu. Pemain tanpa perlengkapan
mengirim `0`, bukan lima belas sentinel.

**3. Satu peristiwa per maksud.** RetroTK memakai `0x1D`, `0x33`, dan
`0x07` untuk "gambar ulang benda ini" tergantung jenisnya. Di sini satu
`EV_OBJECT_APPEARANCE` untuk semuanya, karena blok bendanya sudah menyebut
jenisnya sendiri.

### Blok benda

Dipakai `EV_OBJECT_APPEARED` dan `EV_OBJECT_APPEARANCE`:

```
u64 id
u8  kind        1 pemain, 2 mob, 3 NPC, 4 barang
u16 x, u16 y
u8  side
u16 grafik      MENTAH
u8  warnaGrafik
u8  bendera     bit 0 = digambar sebagai karakter
str nama
```

Bila `kind == 4` (barang), menyusul `u32 jumlah`.
Bila bit 0 menyala, menyusul **blok wujud karakter**:

```
u8  sex, u8 state, u16 penyamaran, u8 warnaPenyamaran, u8 kecepatan
u8  wajah, u8 rambut, u8 warnaRambut, u8 warnaWajah, u8 warnaKulit
u8  jumlahSlot
{ u8 slot, u16 grafik, u8 warna } * jumlahSlot
u8  penandaNama    0 biasa, 1 PK, 3 seklan
```

⚠️ **Blok benda disusun ulang untuk tiap penonton**, tidak sekali lalu
disiarkan. Isinya memang bergantung pada siapa yang melihat: jebakan yang
belum ditemukan tidak digambar sama sekali, pemain ber-stealth tampak
berbeda bagi GM, dan penanda seklan hanya muncul untuk sesama anggota.

### Daftar peristiwa

Ambil nilainya dari `Wire.java`, bukan dari sini.

| Golongan | Isi |
|---|---|
| `0x81xx` | pemain itu sendiri — identitas, peta, posisi, kamera, status, darah, segarkan, kunci gerak, langkah ditolak/diterima, timer, durasi & aether |
| `0x82xx` | barang & perlengkapan — slot inventaris, slot dikosongkan, slot perlengkapan, mantra dihapus |
| `0x83xx` | teks — obrolan, pesan, popup, teks layar, kertas, URL, benda berbicara |
| `0x84xx` | pertukaran — terbuka, barang, emas, persetujuan |
| `0x85xx` | benda — muncul, pindah, hilang, wujud berubah, arah hadap, animasi, gerakan, lemparan, bunyi |
| `0x86xx` | dialog & antarmuka — dialog/menu/isian/toko, pilihan peta, daftar papan, formulir papan, daftar kekuatan |

### Yang sengaja TIDAK ada di arah keluar

- **Permintaan gambar ulang** (`areaRedrawRequested`) tidak diteruskan sama
  sekali. Ia kebocoran RetroTK: klien menitipkan "gambar ulang petak ini"
  pada paket langkah. Server tahu sendiri apa yang baru terlihat.
- **Ladang tampilan yang tidak pernah dipakai** — warna nama, warna garis
  tepi, `passflag` pada paket benda.
- **`sendMyStatus` TAHAP 1** (klan, gelar, pasangan, TNL): datanya memang
  belum ada, dan mengirim ladang kosong hanya memindahkan pekerjaannya ke
  klien.

## Penanganan kesalahan

Bingkai yang tidak bisa dibaca **menutup koneksi**, tidak dilewati.
Setelah satu bingkai rusak, batas bingkai berikutnya sudah tidak diketahui
— melanjutkan berarti menafsirkan sampah sebagai perintah. Yang dianggap
rusak: ladang panjang di bawah 2, bingkai melewati batas, muatan yang
habis sebelum ladangnya lengkap, string yang mengaku lebih panjang dari
bingkainya, dan ragam jawaban yang tidak dikenal.

## Yang belum ada

Aksi yang **belum punya opcode**, karena logikanya memang belum ada:
merapal mantra, grup, teman, profil, emosi, daftar abaikan, papan & pos,
minimap, ranking, berputar di tempat.

Tambahkan opcodenya **saat logikanya dikerjakan**, bukan sebelumnya.
Nomornya tinggal disisipkan di golongan yang sesuai — itulah sebabnya
opcodenya dua byte dan berjarak.

Arah keluar sudah berdiri (51 peristiwa). Yang **belum pernah diuji**:
tidak satu byte pun RTK2 pernah dibaca klien sungguhan, karena kliennya
belum ada. Itu butir terakhir roadmap, dan sengaja diletakkan di sana.
