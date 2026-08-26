package org.rtk.map;

/**
 * Apa yang <b>pemain ingin lakukan</b> — pasangan masuk dari
 * {@link ClientView}.
 *
 * <h2>Arah, dan siapa mengimplementasikan apa</h2>
 *
 * <p>Kedua antarmuka ini cermin satu sama lain, tetapi arah dan pemiliknya
 * <b>terbalik</b>, dan itu yang paling mudah tertukar:</p>
 *
 * <table>
 *   <caption>Perbandingan arah</caption>
 *   <tr><th></th><th>{@link ClientView}</th><th>{@code ClientCommands}</th></tr>
 *   <tr><td>Arah</td><td>logika &rarr; klien</td><td>klien &rarr; logika</td></tr>
 *   <tr><td>Isi</td><td>apa yang <b>terjadi</b></td><td>apa yang <b>diminta</b></td></tr>
 *   <tr><td>Diimplementasikan</td><td>lapisan <b>protokol</b></td><td>lapisan <b>logika</b></td></tr>
 *   <tr><td>Dipanggil</td><td>lapisan <b>logika</b></td><td>lapisan <b>protokol</b></td></tr>
 * </table>
 *
 * <h2>Kenapa ini ada</h2>
 *
 * <p>Audit 27 Agustus 2026 menemukan bahwa arah <b>masuk</b> nyaris belum
 * diport: {@code clif_parse()} di C melayani <b>54 opcode</b>, port ini
 * melayani lima. Dan yang lima itu pun mencampur dua hal di satu tempat —
 * {@code Clif.parseWalk} misalnya berisi ~15 baris pembacaan byte dan ~65
 * baris logika permainan (tabrakan, kamera, portal, kait skrip).</p>
 *
 * <p>Selama campuran itu dibiarkan, mengganti protokol berarti menulis
 * ulang logikanya juga. Dengan lapisan ini, yang perlu ditulis untuk
 * protokol baru hanyalah <b>pembaca</b> yang memanggil method-method di
 * sini; logikanya tidak disentuh sama sekali — persis seperti yang sudah
 * berlaku untuk arah keluar.</p>
 *
 * <h2>Aturan</h2>
 *
 * <ul>
 *   <li><b>Nama method menyebut apa yang DIINGINKAN pemain</b>, bukan
 *       opcode apa yang datang. {@code playerWalks}, bukan
 *       {@code parse0x06}.</li>
 *   <li><b>Parameternya sudah berupa nilai permainan</b> — arah, id benda,
 *       nomor slot, teks. Tidak ada offset, tidak ada buffer, tidak ada
 *       {@code Session}. Kalau sebuah method butuh membaca byte, ia salah
 *       tempat.</li>
 *   <li><b>Lapisan logika JANGAN membaca {@code rfifo*}.</b> Sebagaimana
 *       kebalikannya: kode di luar protokol jangan memanggil {@code Clif}.</li>
 *   <li>Daftar method di sini, seperti pada {@link ClientView}, pada
 *       akhirnya <b>adalah</b> spesifikasi protokol baru arah masuk —
 *       diturunkan dari aksi nyata, bukan dikarang dari nol.</li>
 * </ul>
 *
 * <h2>Cakupan saat ini, dan apa yang menyusul</h2>
 *
 * <p>Empat aksi yang sudah punya jalur: berjalan, mengklik benda, menjawab
 * menu/input, dan menjawab dialog NPC. Yang <b>belum ada jalurnya sama
 * sekali</b> — daftar ini adalah sisa spesifikasinya, diambil dari
 * {@code clif_parse()} dan dikelompokkan menurut aksi:</p>
 *
 * <ul>
 *   <li><b>Bicara</b> — mengobrol di area, berbisik ke satu orang, daftar
 *       abaikan. (Sisi keluarnya sudah ada: {@code objectSpoke},
 *       {@code playerSpoke}.)</li>
 *   <li><b>Barang</b> — memungut, menjatuhkan, menjatuhkan emas, melempar,
 *       memakai, memakan. (BL_ITEM sudah ada; tinggal pemicunya.)</li>
 *   <li><b>Perlengkapan</b> — memakai dan melepas. Ini yang menutup
 *       binding {@code takeOff}.</li>
 *   <li><b>Pertukaran antar pemain</b> — menyerahkan barang, menyerahkan
 *       emas, membuka dan menyetujui pertukaran. Menutup
 *       {@code getExchangeItem}; subsistemnya belum ada sama sekali.</li>
 *   <li><b>Mantra & pertarungan</b> — merapal, mengganti mantra, menyerang.
 *       (Sisi logikanya sudah ada: {@code Combat.swingAt}, durasi mantra.)</li>
 *   <li><b>Sosial</b> — grup, teman, profil, emosi.</li>
 *   <li><b>Antarmuka</b> — papan & pos, kiriman, pilihan peta, minimap,
 *       ranking, daftar kota. (Sisi tampilannya sebagian besar sudah ada.)</li>
 * </ul>
 *
 * <p>⚠️ <b>Itu bukan daftar 49 paket yang harus disalin.</b> Format kabelnya
 * memang akan diganti; yang berharga adalah logika di balik tiap aksinya.
 * Tambahkan method di sini ketika logikanya dikerjakan, bukan sebelumnya —
 * antarmuka yang diisi method kosong berhenti jadi spesifikasi dan berubah
 * jadi tebakan.</p>
 */
public interface ClientCommands {

    /**
     * Pemain melangkah satu petak.
     *
     * <p>{@code claimedX}/{@code claimedY} adalah posisi yang <b>diyakini
     * klien</b> saat ia melangkah. Logika membandingkannya dengan posisi
     * yang dipegang server; kalau berbeda, pemain ditarik kembali dan
     * langkahnya dibatalkan. Itu satu-satunya pertahanan terhadap klien
     * yang berjalan sendiri, jadi jangan dilewati "karena toh sama".</p>
     *
     * <p>⚠️ <b>{@code redraw} adalah kebocoran protokol yang diketahui.</b>
     * Ia permintaan klien RetroTK untuk menggambar ulang sepetak wilayah,
     * dititipkan pada paket langkah yang sama — dua permintaan dalam satu
     * paket. Ia ada di sini karena urutannya penting: penggambaran ulang
     * harus terjadi <b>setelah</b> pemain berpindah tetapi <b>sebelum</b>
     * kait skrip dan pemeriksaan portal, sehingga tidak bisa dikerjakan
     * pemanggil setelah method ini kembali. Protokol baru tidak perlu
     * bertanya — server tahu sendiri apa yang baru terlihat. <b>Buang
     * parameter ini saat protokol baru dirancang.</b></p>
     *
     * @param direction 0 atas, 1 kanan, 2 bawah, 3 kiri; nilai lain
     *                  <b>tidak menggeser apa pun</b> (lihat Peringatan #13)
     * @param redraw    petak yang diminta klien digambar ulang, atau null
     */
    void playerWalks(User sd, int direction, int claimedX, int claimedY,
                     RedrawRequest redraw);

    /**
     * Permintaan gambar ulang yang menumpang paket langkah RetroTK.
     * Lihat catatan di {@link #playerWalks} — ini bukan konsep permainan.
     */
    record RedrawRequest(int x, int y, int width, int height, int checksum) {
    }

    /**
     * Pemain mengklik sebuah benda di dunia.
     *
     * @param objectId id blok benda; <b>0 berarti "yang terakhir diklik"</b>,
     *                 dan klien memang mengirim 0 untuk melanjutkan
     *                 percakapan yang sedang berjalan
     */
    void playerClicks(User sd, long objectId);

    /**
     * Pemain menjawab menu, kotak isian, atau daftar toko.
     *
     * <p>Empat ragam jawaban yang <b>tidak bisa disatukan</b>, karena
     * masing-masing melanjutkan primitif skrip yang berbeda dan tipe
     * jawabannya pun berbeda:</p>
     * <ul>
     *   <li>{@link Answer#choice} — nomor pilihan menu;</li>
     *   <li>{@link Answer#text} — teks yang diketik;</li>
     *   <li>{@link Answer#itemName} — <b>nama</b> barang yang dibeli
     *       (daftar beli dijawab dengan nama, bukan indeks);</li>
     *   <li>{@link Answer#slot} — <b>nomor slot</b> inventaris yang dijual.</li>
     * </ul>
     *
     * <p>{@code null} berarti pemain membatalkan — coroutine skripnya
     * dilepas, bukan dilanjutkan dengan nilai kosong.</p>
     */
    void playerAnswersMenu(User sd, Answer answer);

    /**
     * Jawaban pemain atas dialog bertingkat ({@code dialog}, {@code menuSeq},
     * {@code inputSeq}).
     *
     * <p>⚠️ Jalurnya <b>terpisah</b> dari {@link #playerAnswersMenu} dan itu
     * bukan kebetulan: di C keduanya memakai fungsi resume yang berbeda
     * ({@code sl_resumemenu} vs {@code sl_resumemenuseq}). Menyatukannya
     * akan melanjutkan coroutine yang salah.</p>
     */
    void playerAnswersDialog(User sd, Answer answer);

    /**
     * Satu jawaban pemain, apa pun bentuknya.
     *
     * <p>Dibuat lewat pabrik statis supaya bentuk mana yang dipakai
     * terbaca di tempat pemanggilan, alih-alih empat parameter yang
     * kebanyakannya null.</p>
     */
    final class Answer {
        public final Integer choice;
        public final String text;
        public final String itemName;
        public final Integer slot;
        /** Ragam dialog bertingkat: "next"/"previous"/"quit" dan sejenisnya. */
        public final Integer step;

        private Answer(Integer choice, String text, String itemName,
                       Integer slot, Integer step) {
            this.choice = choice;
            this.text = text;
            this.itemName = itemName;
            this.slot = slot;
            this.step = step;
        }

        public static Answer choice(int n) {
            return new Answer(n, null, null, null, null);
        }

        public static Answer text(String s) {
            return new Answer(null, s, null, null, null);
        }

        public static Answer itemName(String s) {
            return new Answer(null, null, s, null, null);
        }

        public static Answer slot(int n) {
            return new Answer(null, null, null, n, null);
        }

        public static Answer step(int n) {
            return new Answer(null, null, null, null, n);
        }
    }
}
