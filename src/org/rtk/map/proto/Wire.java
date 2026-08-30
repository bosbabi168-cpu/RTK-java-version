package org.rtk.map.proto;


/**
 * Format kabel <b>RTK2</b> arah masuk — rancangan sendiri, bukan RetroTK.
 *
 * <h2>Kenapa protokol baru, bukan pembaca RetroTK</h2>
 *
 * <p>Arah project sudah final (26 Agustus 2026): protokolnya diganti dan
 * kliennya dibuat sendiri dengan libGDX. Menulis pembaca RetroTK untuk aksi
 * yang logikanya sudah ada berarti mengerjakan sesuatu yang umurnya
 * pendek — dua kali kerja untuk hasil yang sama. Jadi jalur masuknya
 * langsung dirancang ulang, dan RetroTK dibiarkan hidup berdampingan
 * sampai klien sendiri berdiri (lihat {@link #isRetroTk}).</p>
 *
 * <h2>Bingkai</h2>
 *
 * <pre>
 *   u16 panjang    banyak byte SETELAH ladang ini (opcode + muatan)
 *   u16 opcode     lihat daftar di bawah
 *   ...muatan
 * </pre>
 *
 * <p>Bingkai terkecil 4 byte ({@code panjang} = 2, muatan kosong).</p>
 *
 * <h2>Lima jebakan RetroTK yang sengaja dibuang</h2>
 *
 * <ol>
 *   <li><b>Dua endianness dalam satu server.</b> RetroTK memakai
 *       little-endian antar-server dan big-endian ke klien, dan campuran itu
 *       melahirkan Peringatan #3 dan #10 — termasuk satu uji yang lolos
 *       tanpa menguji kodenya. RTK2 <b>big-endian, tanpa pengecualian</b>.
 *       Satu aturan tidak bisa diterapkan setengah-setengah.</li>
 *   <li><b>Ladang panjang yang bukan panjang sebenarnya.</b>
 *       {@code set_packet_indexes()} menimpa ladang panjang dengan nilai+3
 *       lalu menempelkan tiga byte kunci, jadi "yang ditulis kode" berbeda
 *       dari "yang dikirim" — dua kali membuat ekspektasi uji salah
 *       (Peringatan #17). Di sini {@code panjang} adalah panjang.</li>
 *   <li><b>Enkripsi XOR beserta urutan dekripsinya.</b> Ia tidak melindungi
 *       apa pun (kuncinya ada di berkas konfigurasi) tetapi menciptakan
 *       aturan urutan yang halus: paket perkenalan justru <b>tidak boleh</b>
 *       didekripsi, dan melanggarnya membuat klien menggantung di layar
 *       muat. RTK2 polos; kerahasiaan transport nanti dari TLS, yang memang
 *       tugasnya.</li>
 *   <li><b>Nomor urut di ladang [4] yang hanya ada di sebagian paket</b>
 *       (Peringatan #29). Arah masuk tidak membutuhkannya sama sekali.</li>
 *   <li><b>Nomor slot 1-basis di kabel, 0-basis di server.</b> Setiap
 *       penangan RetroTK diawali {@code RFIFOB(fd,5) - 1}, dan sekali lupa
 *       berarti salah slot secara senyap. RTK2 <b>0-basis di kedua sisi</b>.</li>
 * </ol>
 *
 * <h2>Yang juga tidak ada, dan itu disengaja</h2>
 *
 * <ul>
 *   <li><b>Permintaan gambar ulang.</b> Klien RetroTK menitipkan
 *       "gambar ulang petak ini" pada paket langkah yang sama — kebocoran
 *       yang sampai sekarang masih terbawa di
 *       {@code ClientCommands.RedrawRequest}. Server tahu sendiri apa yang
 *       baru terlihat; RTK2 tidak bertanya.</li>
 *   <li><b>Perjalanan bolak-balik "berapa banyak?"</b> Saat pemain
 *       menyerahkan setumpuk barang, server RetroTK mengirim paket balik
 *       untuk menanyakan jumlahnya ({@code clif_parse_exchange} ragam 1 dan
 *       2). Klien sudah tahu isi tumpukannya — di RTK2 jumlahnya ikut di
 *       permintaan pertama, dan kedua ragam itu jadi satu.</li>
 * </ul>
 *
 * <h2>Opcode</h2>
 *
 * <p>Dua byte, dikelompokkan per subsistem supaya aksi baru bisa
 * ditambahkan tanpa menomori ulang apa pun, dan supaya sebuah opcode di log
 * langsung terbaca golongannya.</p>
 */
public final class Wire {

    private Wire() {
    }

    /** Empat byte pertama {@link #OP_HELLO}: {@code "RTK2"}. */
    public static final int MAGIC = 0x52544B32;

    /** Versi protokol. Naikkan bila tata letak bingkai mana pun berubah. */
    public static final int VERSION = 11;  // 11: musik peta di EV_SELF_MAP (K5); 10: pra-login RTK2 (K3-lanjutan); 9: papan baca-tulis (K2-lanjutan); 8: EV_TRANSFER (R3/C3); 7: EV_WORLD_TIME; 6: R1 batch 2; 5: ekspresi & susun ulang; 4: sandi; 3: EV_SPELL_SLOT; 2: blok wujud

    /** {@code u16 panjang} + {@code u16 opcode}. */
    public static final int HEADER = 4;

    /**
     * Batas satu bingkai, termasuk header.
     *
     * <p>⚠️ Angka ini <b>ikut jadi bagian pemilihan protokol</b>: selama
     * batasnya di bawah 0xAA00, byte tinggi ladang panjang tidak akan pernah
     * bernilai 0xAA, sehingga {@link #isRetroTk} tidak bisa salah menebak.
     * Menaikkannya melewati 43.520 akan mematahkan itu — lihat
     * catatan di sana sebelum mengubahnya.</p>
     */
    public static final int MAX_FRAME = 8192;

    /** Batas panjang satu string dalam bingkai. */
    public static final int MAX_STRING = 4096;

    // ------------------------------------------------------------------
    // 0x00xx — sesi
    // ------------------------------------------------------------------

    /** {@code u32 magic, u16 versi, str namaKarakter} — wajib bingkai pertama. */
    public static final int OP_HELLO = 0x0001;

    /** Tanpa muatan; tidak menyentuh logika sama sekali. */
    public static final int OP_PING = 0x0002;

    // ------------------------------------------------------------------
    // 0x01xx — gerak & sasaran
    // ------------------------------------------------------------------

    /** {@code u8 arah, u16 x, u16 y} — x/y adalah posisi yang diyakini klien. */
    public static final int OP_WALK = 0x0100;

    /** {@code u64 idBenda} — 0 berarti "yang terakhir diklik". */
    public static final int OP_CLICK = 0x0110;

    /**
     * Tanpa muatan — naiki (atau turuni) tunggangan di petak yang dihadapi.
     *
     * <p>⚠️ Di C ini <b>bukan</b> opcode tersendiri: ia ragam 0 dari paket
     * setelan {@code 0x1B}, dengan byte penanda di offset 7. Dipisah di
     * sini karena ia satu-satunya ragam yang mengubah <b>dunia</b>, bukan
     * setelan — dan menyatukannya berarti satu ladang dengan dua arti.</p>
     */
    public static final int OP_RIDE = 0x0120;

    /**
     * Berputar di tempat tanpa melangkah: {@code u8 arah} (R1/K4).
     * Cermin {@code clif_parseside} (0x11) — menyetel arah, menyiarkannya,
     * lalu memicu kait skrip {@code onTurn}.
     */
    public static final int OP_TURN = 0x0121;

    /**
     * Emosi: {@code u8 emosi} (R1/K4). Cermin {@code clif_parseemotion}
     * (0x1D) — hanya berlaku pada keadaan normal, dan nomor aksinya
     * <b>emosi + 11</b> di sisi server.
     */
    public static final int OP_EMOTE = 0x0122;

    /**
     * Minta sapuan ulang sekeliling (R1/K4). Cermin {@code case 0x38} yang
     * memanggil {@code clif_refresh}; arah keluarnya sudah ada sejak awal
     * ({@link #EV_SELF_REFRESH}).
     */
    public static final int OP_REFRESH = 0x0123;

    /**
     * Melihat benda: {@code u64 id} (R1/K4). Cermin {@code clif_parselookat}
     * — yang di C mengirim petak lalu menyapu isinya; di sini klien sudah
     * memegang id tiap benda, jadi ia menyebut yang dimaksud. Efeknya sama:
     * kait skrip {@code onLook} dipicu untuk pasangan (penonton, benda).
     */
    public static final int OP_LOOK_AT = 0x0124;

    /**
     * Minta profil diri sendiri (R1). Cermin {@code case 0x2D} —
     * {@code clif_mystaytus} bila ladang [5] nol, {@code clif_groupstatus}
     * bila tidak. Di RTK2 keduanya jadi satu permintaan: {@code u8 ragam}
     * (0 diri, 1 grup), dan jawabannya {@link #EV_SELF_PROFILE} /
     * {@link #EV_GROUP} yang sudah ada.
     */
    public static final int OP_PROFILE = 0x0125;

    /**
     * Ubah teks profil sendiri: {@code str teks} (R1).
     * Cermin {@code clif_changeprofile} (0x4F), tanpa gambarnya — gambar
     * profil RetroTK adalah blob berformat klien lama.
     */
    public static final int OP_PROFILE_EDIT = 0x0126;

    /** Minta daftar kota (R1). Cermin {@code clif_sendtowns} (0x66). */
    public static final int OP_TOWNS = 0x0127;

    /** Minta papan peringkat (R1). Cermin {@code clif_parseranking} (0x7D). */
    public static final int OP_RANKING = 0x0128;

    // ------------------------------------------------------------------
    // 0x02xx — barang
    // ------------------------------------------------------------------

    /** {@code u8 ragam} — 0 sekeping, bukan-nol seluruh tumpukan. */
    public static final int OP_PICKUP = 0x0200;

    /** {@code u8 slot, u8 ragam} — slot <b>0-basis</b>. */
    public static final int OP_DROP_ITEM = 0x0201;

    /** {@code u64 jumlah}. */
    public static final int OP_DROP_GOLD = 0x0202;

    /** {@code u8 slot, u16 jumlah} — serahkan ke petak yang dihadapi. */
    public static final int OP_HAND_ITEM = 0x0210;

    /** {@code u64 jumlah} — serahkan ke pemain di petak yang dihadapi. */
    public static final int OP_HAND_GOLD = 0x0211;

    /** {@code u8 slot} — kenakan isi slot inventaris. */
    public static final int OP_WIELD = 0x0220;

    /**
     * {@code u8 slot} — lepas isi slot perlengkapan.
     *
     * <p>⚠️ Slotnya indeks {@code EQ_*} apa adanya. RetroTK memakai
     * penomoran panel kliennya sendiri (1,2,3,4,6,7,8,13,14,16,20..23),
     * yang berlubang dan harus diterjemahkan dua arah; RTK2 tidak.</p>
     */
    public static final int OP_UNEQUIP = 0x0221;

    /** {@code u8 slot} — makan isi slot; hanya barang berjenis ITM_EAT. */
    public static final int OP_EAT = 0x0222;

    /** {@code u8 slot} — pakai isi slot, jenis apa pun. */
    public static final int OP_USE = 0x0223;

    /** {@code u8 slot, u8 konfirmasi} — lempar isi slot ke arah hadap. */
    public static final int OP_THROW = 0x0224;

    /**
     * Tukar posisi dua barang di kantong: {@code u8 dari, u8 ke} (R1/K4).
     * Cermin {@code clif_parsechangepos} → {@code pc_changeitem}.
     * ⚠️ Slot 0-BASIS di kabel; C memakai 1-basis dan menguranginya sendiri.
     */
    public static final int OP_SWAP_ITEM = 0x0225;

    /**
     * Tukar posisi dua mantra di buku: {@code u8 dari, u8 ke} (R1/K4).
     * Cermin {@code clif_parsechangespell}, yang menghapus kedua slot di
     * klien lalu mengirim ulang seluruh buku.
     */
    public static final int OP_SWAP_SPELL = 0x0226;

    // ------------------------------------------------------------------
    // 0x03xx — bicara
    // ------------------------------------------------------------------

    /** {@code u8 saluran, str teks} — 0 sekitar, 1 berteriak. */
    public static final int OP_SAY = 0x0300;

    /** {@code str tujuan, str teks} — tujuan bisa "!" / "!!" / "@" / "?". */
    public static final int OP_WHISPER = 0x0301;

    /**
     * {@code u8 aksi, str nama} — daftar abaikan. Aksi <b>2 tambah</b>,
     * <b>3 hapus</b>, mengikuti {@code iCmd} di {@code clif_parseignore}.
     *
     * <p>⚠️ Daftarnya <b>tidak disimpan</b> dan hilang saat pemain keluar —
     * itu memang perilaku C, bukan yang belum dikerjakan. Tabel
     * {@code Friends} adalah hal yang berbeda meski namanya mirip.</p>
     */
    public static final int OP_IGNORE = 0x0302;

    /**
     * Simpan daftar teman: {@code u8 jumlah, jumlah × str nama} (R1).
     * Cermin {@code clif_parsefriends} (0x77) — C menyimpan 20 slot tetap
     * di tabel {@code Friends}; nama yang tidak dikirim dikosongkan.
     */
    public static final int OP_FRIENDS = 0x0303;

    /**
     * Setel penanda pemburu: {@code u8 nyala, str catatan} (R1).
     * Cermin {@code clif_huntertoggle} (0x84) → kolom {@code ChaHunter}
     * dan {@code ChaHunterNote}.
     */
    public static final int OP_HUNTER = 0x0304;

    /**
     * Papan pesan & pos (R1): {@code u8 aksi, u16 papan, u16 pos}.
     * Cermin {@code clif_handle_boards} (0x3B) — aksi 1 tampilkan daftar
     * papan, 2 tampilkan pos sebuah papan, 3 baca pos, 5 hapus pos,
     * 9 nmail. Sisi tampilannya ({@link #EV_BOARD_LIST} dkk.) sudah ada.
     */
    public static final int OP_BOARD = 0x0305;

    /** Ambil kiriman (R1). Cermin {@code clif_parseparcel} (0x41). */
    public static final int OP_PARCEL = 0x0306;

    /**
     * Tulis kiriman papan (K2-lanjutan): {@code u16 papan, str topik, str isi}.
     *
     * <p>⚠️ Opcode TERSENDIRI, bukan aksi lain di {@link #OP_BOARD}.
     * {@code OP_BOARD} bermuatan tetap (aksi, papan, pos) dan dibaca sebagai
     * tiga angka; menyelipkan dua string ber-panjang-variabel ke dalamnya
     * berarti pembacanya harus tahu aksi mana yang bermuatan lain — tepat
     * jenis percabangan yang membuat aliran bergeser diam-diam
     * (Peringatan #103).</p>
     */
    public static final int OP_BOARD_WRITE = 0x0307;

    // ------------------------------------------------------------------
    // 0x00xx — PRA-LOGIN (K3-lanjutan)
    //
    // ⚠️ Penyimpangan yang DISENGAJA dari alur tiga server C. Di RetroTK
    // pemain menyambung ke login server (akun), lalu char server (daftar &
    // pembuatan karakter), baru map server. Klien RTK2 memakai SATU titik
    // sambung untuk ketiganya: menambah dua protokol TCP lagi berarti tiga
    // kali permukaan bingkai, tiga kali tabel panjang paket, dan tiga
    // tempat yang bisa bergeser diam-diam (Peringatan #103) — untuk alur
    // yang selesai dalam satu sambungan. Map server memang sudah
    // memverifikasi sandi sejak K3.4.
    // ------------------------------------------------------------------

    /**
     * Masuk dengan AKUN: {@code str email, str sandi}.
     *
     * <p>Jawabannya {@link #EV_CHAR_LIST}. Akun yang berhasil masuk
     * <b>diingat pada sesi</b>, sehingga {@link #OP_HELLO} untuk karakter
     * milik akun itu tidak perlu sandi karakter lagi — itulah arti "pilih
     * karakter".</p>
     */
    public static final int OP_ACCOUNT_LOGIN = 0x0010;

    /**
     * Buat karakter baru: {@code str nama, str sandi, u8 sex, u16 wajah,
     * u16 rambut, u8 warnaRambut, u8 warnaWajah, u8 negara}.
     * Jawabannya {@link #EV_ACCOUNT_RESULT}.
     */
    public static final int OP_CREATE_CHAR = 0x0011;

    // ------------------------------------------------------------------
    // 0x07xx — setelan pemain
    // ------------------------------------------------------------------

    /**
     * {@code u8 jenis, u8 paketAwal} — balik satu setelan
     * ({@code clif_changestatus}).
     *
     * <p>Jenis: 1 bisik, 2 grup, 3 teriak, 4 nasihat, 5 sihir, 6 cuaca,
     * 7 pusat-alam, 8 tukar, 9 gerak cepat, 10 obrolan klan, 13 suara,
     * 14 helm, 15 kalung. Jenis lain diabaikan tanpa jawaban, seperti
     * cabang {@code default} di C.</p>
     *
     * <p>⚠️ <b>Ini SAKLAR, bukan penetapan.</b> Muatannya menyebut setelan
     * <b>mana</b>, bukan nilai barunya; server yang membalik bitnya
     * ({@code settingFlags ^= FLAG_x}). Klien yang mengirim "nyalakan"
     * dua kali akan mematikannya lagi. Sama dengan
     * {@code player.settings} di Lua, yang juga XOR (sl.c:6881) meski
     * terbaca seperti penetapan biasa.</p>
     *
     * <p>⚠️ {@code paketAwal} ada semata untuk mempertahankan satu keanehan
     * C: paket suara yang dikirim klien <b>sekali saat masuk</b> diabaikan
     * server ({@code if (RFIFOB(fd,4) == 3) return 0}) — di sana penandanya
     * kebetulan nomor urut paket. Tanpa penanda ini, setiap pemain yang
     * masuk akan mematikan suaranya sendiri.</p>
     */
    public static final int OP_SETTING = 0x0700;

    // ------------------------------------------------------------------
    // 0x03Fx — grup (party)
    // ------------------------------------------------------------------

    /**
     * {@code u8 aksi, str nama} — grup. Aksi <b>0 ajak/keluarkan</b>
     * ({@code clif_addgroup}), <b>1 keluar sendiri</b>
     * ({@code clif_leavegroup}), <b>2 minta kiriman ulang jendela grup</b>
     * ({@code clif_groupstatus}).
     *
     * <p>⚠️ Aksi 0 adalah <b>saklar</b>, bukan "ajak" saja: mengirimkannya
     * atas nama anggota grup yang saya pimpin justru mengeluarkan dia.
     * Itu perilaku C, dan satu-satunya cara mengeluarkan anggota lain.</p>
     *
     * <p>Nama diabaikan untuk aksi 1 dan 2, tetapi ladangnya tetap ada —
     * bingkai yang tidak habis terbaca ditolak {@code Inbound}.</p>
     */
    public static final int OP_GROUP = 0x03F0;

    // ------------------------------------------------------------------
    // 0x04xx — pertukaran antar pemain
    // ------------------------------------------------------------------

    /** {@code u64 idLawan}. */
    public static final int OP_EXCHANGE_START = 0x0400;

    /** {@code u8 slot, u16 jumlah} — jumlahnya sudah pasti; tanpa tanya balik. */
    public static final int OP_EXCHANGE_ITEM = 0x0401;

    /** {@code u64 jumlah}. */
    public static final int OP_EXCHANGE_GOLD = 0x0402;

    /** Tanpa muatan. */
    public static final int OP_EXCHANGE_CANCEL = 0x0403;

    /** {@code u64 idLawan} — lawan yang <b>disebut klien</b>, diperiksa ulang. */
    public static final int OP_EXCHANGE_CONFIRM = 0x0404;

    // ------------------------------------------------------------------
    // 0x05xx — pertarungan
    // ------------------------------------------------------------------

    /** Tanpa muatan — ayunkan senjata yang sedang dipegang. */
    public static final int OP_ATTACK = 0x0500;

    /**
     * {@code u8 slot} + muatan menurut ragam mantranya:
     * ragam 2 (bersasaran) {@code u64 idSasaran}, ragam 1 (bertanya)
     * {@code str jawaban}, ragam 5 (ke diri sendiri) tanpa muatan.
     *
     * <p>⚠️ <b>Slot buku mantra 0-BASIS</b>, seperti seluruh nomor lain di
     * RTK2. RetroTK mengirimnya 1-basis dan setiap penangannya diawali
     * {@code RFIFOB(fd,5) - 1}; di sini pengurangan itu tidak ada, dan tidak
     * boleh ditambahkan "supaya cocok".</p>
     *
     * <p>⚠️ Ragam tidak ikut di kabel — <b>server yang menentukannya</b> dari
     * {@code SplType} mantra di slot itu. Klien tidak boleh memilih bentuk
     * muatannya sendiri: kalau ia salah mengaku, server akan membaca ladang
     * yang tidak ada.</p>
     */
    public static final int OP_CAST = 0x0501;

    // ------------------------------------------------------------------
    // 0x06xx — jawaban dialog
    // ------------------------------------------------------------------

    /**
     * {@code u8 ragam, muatan} — jawaban untuk {@code menu} dan {@code input}.
     * Ragam: 0 batal (tanpa muatan), 1 {@code u16 pilihan},
     * 2 {@code str teks}, 3 {@code str namaBarang}, 4 {@code u8 slot},
     * 5 {@code u8 langkah} (tombol lanjut/kembali pada dialog bertingkat).
     */
    public static final int OP_ANSWER_MENU = 0x0600;

    /**
     * {@code u8 ragam, muatan} — jawaban untuk {@code dialog},
     * {@code menuSeq}, dan {@code inputSeq}.
     *
     * <p>⚠️ Sengaja <b>terpisah</b> dari {@link #OP_ANSWER_MENU}: di sisi
     * skrip keduanya melanjutkan coroutine yang berbeda. Menyatukan kedua
     * opcode akan melanjutkan yang salah — lihat
     * {@code ClientCommands.playerAnswersDialog}.</p>
     */
    public static final int OP_ANSWER_DIALOG = 0x0601;

    // ------------------------------------------------------------------
    // pemilihan protokol
    // ------------------------------------------------------------------

    /**
     * Buffer masuk, dilihat sebagai byte saja.
     *
     * <p>⚠️ Kelas ini <b>tidak boleh</b> bergantung pada kelas jaringan sisi
     * mana pun. Ia format kabel, dan formatnya dibaca dua program yang
     * berbeda: server memakai {@code common.Session}, klien memakai buffer
     * soketnya sendiri. Kopling ke salah satunya membuat berkas ini mustahil
     * disalin utuh — dan salinan utuh itulah yang dijaga gerbang
     * penyelaras.</p>
     *
     * <p>Sengaja tanpa penyalinan: pemanggil menunjuk ke buffernya sendiri,
     * sehingga membaca satu bingkai tidak mengalokasikan apa pun.</p>
     */
    public interface Bytes {

        /** Byte tak bertanda pada posisi {@code pos} dari awal bingkai. */
        int u8(int pos);

        /** Banyak byte yang tersedia mulai dari awal bingkai. */
        int rest();
    }

    /**
     * Bingkai yang menunggu di buffer ini milik RetroTK, bukan RTK2.
     *
     * <p>Pembedanya <b>tanpa keadaan</b> dan itu disengaja: paket RetroTK
     * selalu dimulai penanda {@code 0xAA}, sedangkan byte pertama bingkai
     * RTK2 adalah byte tinggi ladang panjang — yang tidak akan pernah
     * {@code 0xAA} selama {@link #MAX_FRAME} di bawah 43.520. Jadi tidak
     * perlu tabel "sesi ini protokol apa" yang harus dibersihkan saat
     * pemain terputus, dan tidak ada keadaan yang bisa jadi basi.</p>
     */
    public static boolean isRetroTk(Bytes b) {
        return b.rest() > 0 && b.u8(0) == 0xAA;
    }

    /**
     * Panjang total bingkai di depan buffer, atau <b>0 bila belum lengkap</b>.
     *
     * @throws Malformed bila ladang panjangnya tidak masuk akal
     */
    public static int frameLength(Bytes b) {
        if (b.rest() < HEADER) {
            return 0;
        }
        int len = (b.u8(0) << 8) | b.u8(1);
        if (len < 2) {
            throw new Malformed("panjang bingkai " + len + " lebih kecil dari opcode");
        }
        int total = 2 + len;
        if (total > MAX_FRAME) {
            throw new Malformed("bingkai " + total + " byte melewati batas " + MAX_FRAME);
        }
        return b.rest() < total ? 0 : total;
    }

    /** Opcode bingkai yang sudah dipastikan lengkap oleh {@link #frameLength}. */
    public static int opcode(Bytes b) {
        return (b.u8(2) << 8) | b.u8(3);
    }

    /**
     * Bingkai yang tidak bisa dibaca. Ini <b>bukan</b> kesalahan yang bisa
     * dipulihkan: setelah satu bingkai salah, batas bingkai berikutnya sudah
     * tidak diketahui lagi, jadi satu-satunya jawaban yang benar adalah
     * menutup koneksinya.
     */
    public static final class Malformed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Malformed(String pesan) {
            super(pesan);
        }
    }

    /**
     * Pembaca muatan satu bingkai, dengan batas yang dijaga.
     *
     * <p>Setiap pembacaan di luar bingkai melempar {@link Malformed} alih-alih
     * mengembalikan sampah. Ini menutup satu kelas kesalahan yang di C
     * dijaga tangan di tiap penangan — dan tidak selalu:
     * {@code clif_parsewisp} memeriksa panjangnya dengan enam perbandingan
     * berbeda, sementara penangan lain tidak memeriksa sama sekali.</p>
     */
    public static final class Reader {

        private final Bytes b;
        private int pos;
        private final int end;

        /** Mulai membaca tepat setelah opcode. */
        public Reader(Bytes b, int frameLength) {
            this.b = b;
            this.pos = HEADER;
            this.end = frameLength;
        }

        private void butuh(int n) {
            if (pos + n > end) {
                throw new Malformed("muatan habis: butuh " + n
                        + " byte di offset " + pos + ", bingkai berakhir di " + end);
            }
        }

        public int u8() {
            butuh(1);
            return b.u8(pos++);
        }

        public int u16() {
            butuh(2);
            int v = (b.u8(pos) << 8) | b.u8(pos + 1);
            pos += 2;
            return v;
        }

        public long u32() {
            butuh(4);
            long v = 0;
            for (int i = 0; i < 4; i++) {
                v = (v << 8) | b.u8(pos + i);
            }
            pos += 4;
            return v;
        }

        /**
         * Bilangan 64-bit.
         *
         * <p>Emas dan id benda memakai ini walau RetroTK cukup dengan 32-bit.
         * Alasannya bukan kebutuhan hari ini melainkan pengalaman: emas
         * bank sudah tersimpan sebagai {@code bigint} di database, dan ladang
         * yang terlalu sempit adalah kesalahan yang baru terlihat setelah
         * datanya tumbuh — saat memperbaikinya berarti memutus kompatibilitas.
         * Empat byte tambahan pada paket yang jarang lebih murah daripada itu.</p>
         */
        public long u64() {
            butuh(8);
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | b.u8(pos + i);
            }
            pos += 8;
            return v;
        }

        /** {@code u16 panjang} + byte UTF-8. */
        public String str() {
            int len = u16();
            if (len > MAX_STRING) {
                throw new Malformed("string " + len + " byte melewati batas " + MAX_STRING);
            }
            butuh(len);
            byte[] isi = new byte[len];
            for (int i = 0; i < len; i++) {
                isi[i] = (byte) b.u8(pos + i);
            }
            pos += len;
            return new String(isi, java.nio.charset.StandardCharsets.UTF_8);
        }

        /** Byte yang belum terbaca di bingkai ini. */
        public int rest() {
            return end - pos;
        }
    }

    // ==================================================================
    // Arah KELUAR — peristiwa server -> klien
    // ==================================================================

    /**
     * <h2>Penomorannya terpisah, dan itu disengaja</h2>
     *
     * <p>Peristiwa keluar memakai rentang {@code 0x8xxx}, perintah masuk
     * {@code 0x0xxx}. Keduanya jalur yang berbeda sehingga sebenarnya boleh
     * bertabrakan — tetapi satu baris log yang menyebut {@code 0x8501}
     * langsung terbaca arahnya tanpa perlu tahu dari mana ia dicatat. Itu
     * saja alasannya, dan itu cukup.</p>
     *
     * <h2>Grafik dikirim MENTAH</h2>
     *
     * <p>⚠️ RetroTK menambahkan <b>32768</b> pada grafik mob dan NPC,
     * <b>49152</b> pada ikon kustom, dan mengirim ikon barang biasa tanpa
     * penambah sama sekali (Peringatan #34) — tiga aturan untuk satu ladang,
     * dan menyeragamkannya adalah kesalahan yang wajar tapi tetap salah.</p>
     *
     * <p>RTK2 mengirim nilainya <b>apa adanya</b> beserta {@code kind}
     * bendanya. Kliennya yang memutuskan dari kumpulan grafik mana ia
     * mengambil gambar — di sanalah pengetahuan itu memang tinggal.</p>
     */
    private static final int EV = 0x8000;

    // 0x81xx — pemain itu sendiri
    /** {@code u64 id, str nama, u8 sex, u8 side} */
    public static final int EV_SELF_IDENTITY = EV | 0x0100;
    /** {@code u16 m, str nama, u16 xs, u16 ys, u8 cahaya, u16 bendera} */
    public static final int EV_SELF_MAP = EV | 0x0101;
    /** {@code u16 x, u16 y, u8 side} */
    public static final int EV_SELF_POSITION = EV | 0x0102;
    /** {@code u16 x, u16 y} — geser kamera saja, tanpa berpindah. */
    public static final int EV_SELF_CAMERA = EV | 0x0103;
    /** {@code u32 bendera} + blok nilai; lihat {@code Rtk2ClientView}. */
    public static final int EV_SELF_STATUS = EV | 0x0104;
    /** {@code u8 kritis, u8 persen, u32 kerusakan} */
    public static final int EV_SELF_HEALTH = EV | 0x0105;
    /** Tanpa muatan — gambar ulang seluruh pandangan. */
    public static final int EV_SELF_REFRESH = EV | 0x0106;
    /** {@code u8 terkunci} */
    public static final int EV_SELF_MOVE_LOCK = EV | 0x0107;
    /** {@code u16 x, u16 y, u8 side} — langkah ditolak, kembali ke sini. */
    public static final int EV_SELF_STEP_REJECTED = EV | 0x0108;
    /** {@code u8 arah, u16 dariX, u16 dariY} */
    public static final int EV_SELF_STEPPED = EV | 0x0109;
    /** {@code u8 ragam, u64 detik} */
    public static final int EV_SELF_TIMER = EV | 0x010A;
    /** {@code u16 idMantra, u32 detik, str namaPerapal} */
    public static final int EV_SELF_DURATION = EV | 0x010B;
    /** {@code u16 idMantra, u32 detik} */
    public static final int EV_SELF_AETHER = EV | 0x010C;
    /** {@code u8 cuaca} — pemain yang mematikan setelannya tidak menerimanya. */
    public static final int EV_WEATHER = EV | 0x010D;

    /**
     * Kalender dunia (R2): {@code u8 jam, u8 hari, u8 musim, u16 tahun}.
     * Cermin {@code clif_sendtime}, yang di RetroTK hanya membawa jam dan
     * tahun; hari dan musim ikut di sini karena skrip memakainya dan klien
     * tidak bisa menghitungnya sendiri.
     */
    public static final int EV_WORLD_TIME = EV | 0x0110;

    /**
     * Pindah ke map server lain (R3/C3): {@code str host, u16 port,
     * u16 peta, u16 x, u16 y}.
     *
     * <p>Klien menutup sambungan ini lalu menyambung ke alamat itu dengan
     * nama dan sandi yang sama. Petak tujuannya ikut dikirim supaya klien
     * bisa memuat petanya lebih dulu; kebenarannya tetap milik server
     * tujuan, yang menempatkan pemain dari {@code lastPos}.</p>
     *
     * <p>⚠️ Berbeda dari RetroTK (paket 0x03) yang juga mengirim kunci
     * handshake {@code "KruIn7inc"} — RTK2 tidak memakai kunci itu sama
     * sekali; yang membuktikan identitas adalah sandi di {@code OP_HELLO}.</p>
     */
    public static final int EV_TRANSFER = EV | 0x0111;
    /**
     * {@code u32 setelan, u8 obrolanKlan} — seluruh kata setelan pemain.
     *
     * <p>⚠️ <b>Terpisah dari {@link #EV_SELF_STATUS}</b>, yang ladang
     * benderanya adalah "bagian mana yang berubah", bukan setelan. Di
     * RetroTK setelan menumpang paket status; di sini ia berdiri sendiri
     * supaya klien tidak perlu menebak dari paket yang artinya lain.</p>
     *
     * <p>⚠️ {@code obrolanKlan} ikut di sini walau <b>bukan</b> bagian dari
     * {@code settingFlags} — di C ia ladang {@code status.clan_chat}
     * tersendiri, tetapi dibalik lewat paket setelan yang sama.</p>
     */
    public static final int EV_SELF_SETTINGS = EV | 0x010E;

    /**
     * Profil pemain sendiri (R1) — yang TIDAK bisa dihitung klien:
     * {@code str namaKelas, str gelar, str klan, str pasangan,
     * u32 tnl, u16 persenXpKali100, str profil, u16 jumlahLegenda,
     * jumlahLegenda × {str teks, u8 warna, u8 ikon}}.
     *
     * <p>Inilah pengganti {@code Clif.sendMyStatus()} yang sejak awal
     * sengaja dibiarkan TAHAP 1: di RetroTK ladang-ladang ini menumpang
     * paket 0x39; di RTK2 ia berdiri sendiri dan hanya dikirim saat
     * diminta.</p>
     */
    public static final int EV_SELF_PROFILE = EV | 0x010F;

    // 0x82xx — barang & perlengkapan
    /** {@code u8 slot} + blok barang */
    public static final int EV_INV_SLOT = EV | 0x0200;
    /** {@code u8 slot, u8 alasan} */
    public static final int EV_INV_SLOT_CLEARED = EV | 0x0201;
    /** {@code u8 slot} + blok barang */
    public static final int EV_EQUIP_SLOT = EV | 0x0202;
    /** {@code u8 slot} */
    public static final int EV_EQUIP_SLOT_CLEARED = EV | 0x0203;
    /** {@code u8 slot} */
    public static final int EV_SPELL_REMOVED = EV | 0x0204;

    /**
     * Satu slot buku mantra terisi (K3.2): {@code u8 slot, u32 id, u8 tipe,
     * str nama, str pertanyaan}. Tipe = {@code SplType}: menentukan bentuk
     * muatan {@code OP_CAST} untuk slot itu (1 bertanya, 2 bersasaran,
     * selainnya tanpa muatan). Pasangan {@link #EV_SPELL_REMOVED}.
     */
    public static final int EV_SPELL_SLOT = EV | 0x0205;

    // 0x83xx — teks
    /** {@code u8 ragam, u64 idPembicara, str teks} */
    public static final int EV_CHAT = EV | 0x0300;
    /** {@code u8 ragam, str teks} */
    public static final int EV_MESSAGE = EV | 0x0301;
    /** {@code str teks} */
    public static final int EV_POPUP = EV | 0x0302;
    /** {@code str teks} — tulisan besar di tengah layar. */
    public static final int EV_GUI_TEXT = EV | 0x0303;
    /** {@code str teks, u16 lebar, u16 tinggi} */
    public static final int EV_PAPER = EV | 0x0304;
    /** {@code u8 ragam, str url} */
    public static final int EV_URL = EV | 0x0305;
    /** {@code u64 id, u8 ragam, str teks} — benda apa pun berbicara. */
    public static final int EV_OBJECT_SPOKE = EV | 0x0306;

    // 0x84xx — pertukaran
    /** {@code u64 idLawan, str namaLawan} */
    public static final int EV_EXCHANGE_OPENED = EV | 0x0400;
    /** {@code u64 idPemberi, u8 urutan} + blok barang */
    public static final int EV_EXCHANGE_ITEM = EV | 0x0401;
    /** {@code u64 idPemberi, u64 emas} */
    public static final int EV_EXCHANGE_GOLD = EV | 0x0402;
    /** {@code u64 idPenekan, u8 selesai} */
    public static final int EV_EXCHANGE_STATE = EV | 0x0403;

    // 0x85xx — benda di dunia
    /** Blok benda — sesuatu mulai terlihat. */
    public static final int EV_OBJECT_APPEARED = EV | 0x0500;
    /** {@code u64 id, u16 dariX, u16 dariY, u16 keX, u16 keY, u8 side} */
    public static final int EV_OBJECT_MOVED = EV | 0x0501;
    /** {@code u64 id} */
    public static final int EV_OBJECT_REMOVED = EV | 0x0502;
    /** Blok benda — wujudnya berubah di tempat. */
    public static final int EV_OBJECT_APPEARANCE = EV | 0x0503;
    /** {@code u64 id, u8 side} */
    public static final int EV_OBJECT_SIDE = EV | 0x0504;
    /** {@code u64 id, u16 animasi, u16 ulang} */
    public static final int EV_OBJECT_ANIMATION = EV | 0x0505;
    /** {@code u16 animasi, u16 ulang, u16 x, u16 y} — animasi di petak. */
    public static final int EV_OBJECT_ANIMATION_AT = EV | 0x0506;
    /** {@code u64 id, u8 gerakan, u16 waktu, u16 bunyi} */
    public static final int EV_OBJECT_ACTED = EV | 0x0507;
    /** {@code u64 idPelempar, u16 keX, u16 keY, u16 ikon, u8 warna, u8 gerakan} */
    public static final int EV_OBJECT_THROWN = EV | 0x0508;
    /** {@code u16 bunyi, u16 x, u16 y} */
    public static final int EV_SOUND = EV | 0x0509;

    // 0x86xx — dialog & antarmuka
    /**
     * {@code u8 ragam, u64 idNpc, u16 grafik, u8 warnaGrafik, str pesan,}
     * {@code u16 n, str[] pilihan, u16 p, u32[] harga}
     *
     * <p>Ragam: 0 kotak dialog, 1 menu, 2 isian, 3 daftar beli,
     * 4 daftar jual, 5 dialog bertingkat.</p>
     */
    public static final int EV_DIALOG = EV | 0x0600;
    /** {@code str judul, u16 n, entri} */
    public static final int EV_MAP_SELECTION = EV | 0x0601;
    /** Daftar kiriman papan; lihat {@code Rtk2ClientView}. */
    public static final int EV_BOARD_LIST = EV | 0x0602;
    /** {@code u16 n, str[] pertanyaan, u16 m, str[] jawaban} */
    public static final int EV_BOARD_QUESTIONS = EV | 0x0603;
    /**
     * Isi SATU kiriman papan (K2-lanjutan):
     * {@code u16 papan, u32 pos, u8 bendera, str penulis, str topik,
     * u8 bulan, u8 hari, str isi}.
     *
     * <p>{@code bendera} memakai bit yang sama dengan daftar papan:
     * 1 boleh menulis, 2 boleh menghapus — supaya klien tahu tombol mana
     * yang pantas ditawarkan, tanpa menebak dari nama penulis.</p>
     */
    public static final int EV_BOARD_POST = EV | 0x0607;

    /**
     * Daftar karakter milik akun (K3-lanjutan):
     * {@code u16 n, n × (str nama, u16 level, u8 jalur, u16 wajah,
     * u16 rambut, u8 warnaRambut, u8 sex)}.
     */
    public static final int EV_CHAR_LIST = EV | 0x0608;

    /**
     * Hasil aksi pra-login (K3-lanjutan): {@code u8 kode, str pesan}.
     *
     * <p>Kode 0 berhasil; selain itu gagal. Pesannya sudah berbahasa
     * Indonesia dan siap ditampilkan — klien tidak menyusun kalimat sendiri
     * dari kode, supaya alasan penolakan tidak pernah berbeda antara
     * server dan layar.</p>
     */
    public static final int EV_ACCOUNT_RESULT = EV | 0x0609;
    /** {@code u16 n, (str nama, u32 kekuatan)[]} */
    public static final int EV_POWER_BOARD = EV | 0x0604;

    /**
     * Daftar kota (R1): {@code u16 jumlah, jumlah × str nama}.
     * Cermin {@code clif_sendtowns}, yang juga hanya mengirim NAMA —
     * daftarnya datang dari baris {@code town:} di {@code conf/map.conf}
     * ({@code map_town_add}), tanpa koordinat.
     */
    public static final int EV_TOWNS = EV | 0x0605;

    /**
     * Papan peringkat (R1): {@code str judul, u16 jumlah,
     * jumlah × {u16 peringkat, str nama, u32 nilai}}.
     */
    public static final int EV_RANKING = EV | 0x0606;

    // 0x87xx — grup
    /**
     * Isi jendela grup ({@code clif_groupstatus}).
     *
     * <p>{@code u64 idPemimpin, u16 n,} lalu tiap anggota:
     * {@code u64 id, str nama, u8 pemimpin, u8 jalur, u8 keadaan,}
     * {@code u8 wajah, u8 rambut, u8 warnaRambut, u8 s,}
     * {@code (u8 slot, u16 gambar, u8 warna)[s],}
     * {@code u32 maxHp, u32 hp, u32 maxMp, u32 mp}.</p>
     *
     * <p>⚠️ <b>{@code n == 0} berarti "tutup jendela"</b>, dan itu satu-
     * satunya pemberitahuan bahwa grup bubar — tidak ada peristiwa
     * "grup bubar" tersendiri. Di RetroTK pun begitu.</p>
     */
    public static final int EV_GROUP = EV | 0x0700;

    /**
     * Darah &amp; mana anggota grup ({@code clif_grouphealth_update}).
     *
     * <p>{@code u16 n, (u64 id, u32 maxHp, u32 hp, u32 maxMp, u32 mp)[n]}</p>
     *
     * <p>⚠️ Di RetroTK ini <b>satu paket per anggota</b> dan menyeret satu
     * {@code clif_groupstatus} penuh di belakang setiap paketnya. Di RTK2
     * satu peristiwa memuat seluruh anggota dan tidak menyeret apa-apa —
     * jendela grup dikirim hanya saat susunannya berubah.</p>
     */
    public static final int EV_GROUP_HEALTH = EV | 0x0701;

    /**
     * Penyusun bingkai keluar — pasangan {@link Reader}.
     *
     * <p>Menulis ke larik yang tumbuh sendiri, lalu {@link #frame()} mengisi
     * ladang panjang dan opcodenya. Tidak ada enkripsi, tidak ada indeks
     * kunci, tidak ada nomor urut: byte yang ditulis persis byte yang
     * dikirim. Itu yang membuat ekspektasi uji panjang paket bisa dihitung
     * di kepala lagi — sesuatu yang tidak berlaku di RetroTK
     * (Peringatan #17).</p>
     */
    public static final class Writer {

        private byte[] buf = new byte[64];
        private int pos = HEADER;
        private final int op;

        public Writer(int op) {
            this.op = op;
        }

        private void muat(int n) {
            if (pos + n <= buf.length) {
                return;
            }
            int cap = buf.length;
            while (cap < pos + n) {
                cap *= 2;
            }
            buf = java.util.Arrays.copyOf(buf, cap);
        }

        public Writer u8(int v) {
            muat(1);
            buf[pos++] = (byte) v;
            return this;
        }

        public Writer bool(boolean v) {
            return u8(v ? 1 : 0);
        }

        public Writer u16(int v) {
            muat(2);
            buf[pos++] = (byte) (v >> 8);
            buf[pos++] = (byte) v;
            return this;
        }

        public Writer u32(long v) {
            muat(4);
            for (int i = 3; i >= 0; i--) {
                buf[pos++] = (byte) (v >> (i * 8));
            }
            return this;
        }

        public Writer u64(long v) {
            muat(8);
            for (int i = 7; i >= 0; i--) {
                buf[pos++] = (byte) (v >> (i * 8));
            }
            return this;
        }

        /** {@code u16 panjang} + byte UTF-8; null diperlakukan sebagai kosong. */
        public Writer str(String v) {
            byte[] b = (v == null ? "" : v)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (b.length > MAX_STRING) {
                b = java.util.Arrays.copyOf(b, MAX_STRING);
            }
            u16(b.length);
            muat(b.length);
            System.arraycopy(b, 0, buf, pos, b.length);
            pos += b.length;
            return this;
        }

        /** Bingkai lengkap, siap dikirim. */
        public byte[] frame() {
            int len = pos - 2;
            byte[] out = java.util.Arrays.copyOf(buf, pos);
            out[0] = (byte) (len >> 8);
            out[1] = (byte) len;
            out[2] = (byte) (op >> 8);
            out[3] = (byte) op;
            return out;
        }

        /** Panjang total bingkai yang akan dihasilkan. */
        public int length() {
            return pos;
        }
    }

    // ==================================================================
    // blok muatan bersama (K3.3)
    // ==================================================================

    /**
     * Blok yang dipakai beberapa peristiwa: <b>blok barang</b> dan <b>blok
     * benda</b> (dasar + wujud karakter).
     *
     * <p>⚠️ Dulu penyusunnya di {@code Rtk2ClientView} (server) dan
     * pembacanya di {@code Blocks} (klien) — dua berkas berbeda yang tidak
     * dijaga alat mana pun; ladang yang ditambah sebelah baru ketahuan
     * saat runtime. Sejak K3.3 KEDUA arah tinggal di sini, jadi
     * {@code wiresync} ikut menjaganya: menyunting satu sisi membuat
     * gerbangnya merah pada menit itu juga.</p>
     *
     * <p>Kelas ini hanya primitif — tidak boleh mengimpor apa pun yang
     * khusus satu sisi (aturan yang sama dengan {@link Bytes}).</p>
     */
    public static final class Blok {

        private Blok() {
        }

        /** Nilai {@code kind} pada blok benda. */
        public static final int KIND_PC = 1;
        public static final int KIND_MOB = 2;
        public static final int KIND_NPC = 3;
        public static final int KIND_ITEM = 4;

        /** Bit 0 bendera blok benda: digambar sebagai karakter berlapis. */
        public static final int FLAG_AS_CHAR = 1;

        /**
         * Satu barang di kantong, perlengkapan, atau pertukaran. Barang
         * kosong = id 0 dengan seluruh ladang nol (bukan ketiadaan blok).
         */
        public record Barang(long id, long jumlah, String tampilan, String nama,
                             int ikon, int warna, long dura, long dilindungi) {
            public static final Barang KOSONG =
                    new Barang(0, 0, "", "", 0, 0, 0, 0);
        }

        public static void tulisBarang(Writer w, Barang b) {
            w.u64(b.id()).u32(b.jumlah()).str(b.tampilan()).str(b.nama())
             .u16(b.ikon()).u8(b.warna()).u32(b.dura()).u32(b.dilindungi());
        }

        public static Barang bacaBarang(Reader r) {
            return new Barang(r.u64(), r.u32(), r.str(), r.str(),
                    r.u16(), r.u8(), r.u32(), r.u32());
        }

        /** Satu slot perlengkapan yang benar-benar tergambar di badan. */
        public record Slot(int slot, int grafik, int warna) {
        }

        /**
         * Wujud karakter berlapis — menempel pada blok dasar SETIAP benda
         * ber-{@link #FLAG_AS_CHAR}: pemain, NPC {@code npcType==1}, dan
         * mob ber-{@code MobIsNpc}.
         */
        public record Wujud(int sex, int state, int samaran, int warnaSamaran,
                            int kecepatan, int wajah, int rambut,
                            int warnaRambut, int warnaWajah, int warnaKulit,
                            java.util.List<Slot> slot, int tanda) {
        }

        public static void tulisDasar(Writer w, long id, int kind, int x,
                                      int y, int side, int grafik, int warna,
                                      int bendera, String nama) {
            w.u64(id).u8(kind).u16(x).u16(y).u8(side)
             .u16(grafik).u8(warna).u8(bendera).str(nama);
        }

        public static void tulisWujud(Writer w, Wujud u) {
            w.u8(u.sex()).u8(u.state())
             .u16(u.samaran()).u8(u.warnaSamaran())
             .u8(u.kecepatan())
             .u8(u.wajah()).u8(u.rambut()).u8(u.warnaRambut())
             .u8(u.warnaWajah()).u8(u.warnaKulit());
            w.u8(u.slot().size());
            for (Slot e : u.slot()) {
                w.u8(e.slot()).u16(e.grafik()).u8(e.warna());
            }
            w.u8(u.tanda());
        }

        /**
         * Benda apa pun di dunia. {@code wujud} null bila tidak
         * ber-FLAG_AS_CHAR; {@code jumlah} hanya berarti untuk KIND_ITEM.
         */
        public record Benda(long id, int kind, int x, int y, int side,
                            int grafik, int warnaGrafik, int bendera,
                            String nama, Wujud wujud, long jumlah) {
        }

        public static Benda bacaBenda(Reader r) {
            long id = r.u64();
            int kind = r.u8();
            int x = r.u16(), y = r.u16(), side = r.u8();
            int grafik = r.u16(), warna = r.u8(), bendera = r.u8();
            String nama = r.str();
            Wujud wujud = null;
            if ((bendera & FLAG_AS_CHAR) != 0) {
                int sex = r.u8(), state = r.u8();
                int samaran = r.u16(), warnaSamaran = r.u8(), kecepatan = r.u8();
                int wajah = r.u8(), rambut = r.u8(), warnaRambut = r.u8();
                int warnaWajah = r.u8(), warnaKulit = r.u8();
                int n = r.u8();
                java.util.List<Slot> slot = new java.util.ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    slot.add(new Slot(r.u8(), r.u16(), r.u8()));
                }
                wujud = new Wujud(sex, state, samaran, warnaSamaran, kecepatan,
                        wajah, rambut, warnaRambut, warnaWajah, warnaKulit,
                        java.util.List.copyOf(slot), r.u8());
            }
            long jumlah = kind == KIND_ITEM ? r.u32() : 0;
            return new Benda(id, kind, x, y, side, grafik, warna, bendera,
                    nama, wujud, jumlah);
        }
    }
}
