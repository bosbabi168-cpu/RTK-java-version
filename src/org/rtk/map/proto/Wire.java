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
    public static final int VERSION = 1;

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

    // ------------------------------------------------------------------
    // 0x03xx — bicara
    // ------------------------------------------------------------------

    /** {@code u8 saluran, str teks} — 0 sekitar, 1 berteriak. */
    public static final int OP_SAY = 0x0300;

    /** {@code str tujuan, str teks} — tujuan bisa "!" / "!!" / "@" / "?". */
    public static final int OP_WHISPER = 0x0301;

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
    /** {@code u16 n, (str nama, u32 kekuatan)[]} */
    public static final int EV_POWER_BOARD = EV | 0x0604;

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
}
