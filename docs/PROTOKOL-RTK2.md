# RTK2 — protokol arah masuk

Rancangan sendiri, menggantikan format kabel RetroTK. Dokumen ini untuk
orang yang **menulis kliennya** (Trek B, libGDX).

> **Sumber kebenaran adalah `src/org/rtk/map/proto/Wire.java`.** Berkas ini
> ringkasan yang bisa dibaca; kalau keduanya berbeda, kodenya yang benar.
> Semua nilai opcode di bawah adalah konstanta di kelas itu — salin dari
> sana, jangan ketik ulang angkanya.

**Status: hanya arah MASUK.** Arah keluar masih memakai `RetroTkClientView`.
Klien RTK2 yang lengkap butuh `Rtk2ClientView` (49 peristiwa) lebih dulu —
lihat "Yang belum" di bawah.

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

⚠️ **Menaikkan `Wire.MAX_FRAME` melewati 43.520 akan mematahkan
pembedaan itu.** Ada assertion khusus untuk ini di `cliftest`.

RetroTK dibiarkan hidup karena kliennya sendiri belum ada; menghapusnya
sekarang berarti tidak ada apa pun yang bisa terhubung.

## Penanganan kesalahan

Bingkai yang tidak bisa dibaca **menutup koneksi**, tidak dilewati.
Setelah satu bingkai rusak, batas bingkai berikutnya sudah tidak diketahui
— melanjutkan berarti menafsirkan sampah sebagai perintah. Yang dianggap
rusak: ladang panjang di bawah 2, bingkai melewati batas, muatan yang
habis sebelum ladangnya lengkap, string yang mengaku lebih panjang dari
bingkainya, dan ragam jawaban yang tidak dikenal.

## Yang belum ada

Aksi yang **belum punya opcode**, karena logikanya memang belum ada:
memakai & melepas perlengkapan, memakai & memakan barang, melempar,
merapal mantra, grup, teman, profil, emosi, daftar abaikan, papan & pos,
minimap, ranking, berputar di tempat.

Tambahkan opcodenya **saat logikanya dikerjakan**, bukan sebelumnya.
Nomornya tinggal disisipkan di golongan yang sesuai — itulah sebabnya
opcodenya dua byte dan berjarak.

Dan yang terbesar: **arah keluar belum dirancang.** `ClientView` (49
peristiwa) baru punya satu implementasi, `RetroTkClientView`. Klien RTK2
yang benar-benar bisa dimainkan butuh `Rtk2ClientView` — daftar
peristiwanya sudah jadi spesifikasinya.
