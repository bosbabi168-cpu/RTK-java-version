package org.rtk.map.proto;

import org.rtk.common.Session;

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
     * Bingkai yang menunggu di buffer ini milik RetroTK, bukan RTK2.
     *
     * <p>Pembedanya <b>tanpa keadaan</b> dan itu disengaja: paket RetroTK
     * selalu dimulai penanda {@code 0xAA}, sedangkan byte pertama bingkai
     * RTK2 adalah byte tinggi ladang panjang — yang tidak akan pernah
     * {@code 0xAA} selama {@link #MAX_FRAME} di bawah 43.520. Jadi tidak
     * perlu tabel "sesi ini protokol apa" yang harus dibersihkan saat
     * pemain terputus, dan tidak ada keadaan yang bisa jadi basi.</p>
     */
    public static boolean isRetroTk(Session s) {
        return s.rfifoRest() > 0 && s.rfifoB(0) == 0xAA;
    }

    /**
     * Panjang total bingkai di depan buffer, atau <b>0 bila belum lengkap</b>.
     *
     * @throws Malformed bila ladang panjangnya tidak masuk akal
     */
    public static int frameLength(Session s) {
        if (s.rfifoRest() < HEADER) {
            return 0;
        }
        int len = s.rfifoWBE(0);
        if (len < 2) {
            throw new Malformed("panjang bingkai " + len + " lebih kecil dari opcode");
        }
        int total = 2 + len;
        if (total > MAX_FRAME) {
            throw new Malformed("bingkai " + total + " byte melewati batas " + MAX_FRAME);
        }
        return s.rfifoRest() < total ? 0 : total;
    }

    /** Opcode bingkai yang sudah dipastikan lengkap oleh {@link #frameLength}. */
    public static int opcode(Session s) {
        return s.rfifoWBE(2);
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

        private final Session s;
        private int pos;
        private final int end;

        /** Mulai membaca tepat setelah opcode. */
        public Reader(Session s, int frameLength) {
            this.s = s;
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
            return s.rfifoB(pos++);
        }

        public int u16() {
            butuh(2);
            int v = s.rfifoWBE(pos);
            pos += 2;
            return v;
        }

        public long u32() {
            butuh(4);
            long v = s.rfifoLBE(pos) & 0xFFFFFFFFL;
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
            long hi = s.rfifoLBE(pos) & 0xFFFFFFFFL;
            long lo = s.rfifoLBE(pos + 4) & 0xFFFFFFFFL;
            pos += 8;
            return (hi << 32) | lo;
        }

        /** {@code u16 panjang} + byte UTF-8. */
        public String str() {
            int len = u16();
            if (len > MAX_STRING) {
                throw new Malformed("string " + len + " byte melewati batas " + MAX_STRING);
            }
            butuh(len);
            byte[] b = s.rfifoBytes(pos, len);
            pos += len;
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }

        /** Byte yang belum terbaca di bingkai ini. */
        public int rest() {
            return end - pos;
        }
    }
}
