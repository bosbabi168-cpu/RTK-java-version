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
 * <p>Enam belas aksi punya jalur penuh: berjalan, mengklik, menjawab
 * menu/input, menjawab dialog, bicara, berbisik, memungut, menjatuhkan
 * barang, menjatuhkan emas, menyerahkan barang/emas, menyerang,
 * mengenakan, melepas, memakan, memakai, dan melempar —
 * ditambah lima perintah pertukaran. Pembacanya
 * {@code org.rtk.map.proto.Inbound} (protokol <b>RTK2</b>, rancangan
 * sendiri); {@code Clif.parse*} melayani empat aksi pertama untuk klien
 * RetroTK lama.</p>
 *
 * <p>Yang <b>belum ada jalurnya</b> — sisa spesifikasinya, dikelompokkan
 * menurut aksi dan bukan menurut opcode:</p>
 *
 * <ul>
 *   <li><b>Mantra</b> — merapal, mengganti mantra.</li>
 *   <li><b>Sosial</b> — grup, teman, profil, emosi, daftar abaikan
 *       (yang terakhir juga menutup penyaring {@code clif_isignore}).</li>
 *   <li><b>Antarmuka</b> — papan &amp; pos, kiriman, pilihan peta, minimap,
 *       ranking, daftar kota. Sisi tampilannya sebagian besar sudah ada.</li>
 *   <li><b>Berputar di tempat</b> — sengaja dilewati untuk sekarang;
 *       arah hadap sudah ikut berubah saat berjalan.</li>
 * </ul>
 *
 * <p>⚠️ <b>Itu bukan daftar paket yang harus disalin.</b> Yang berharga
 * adalah logika di balik tiap aksinya. Tambahkan method di sini ketika
 * logikanya dikerjakan, bukan sebelumnya — antarmuka yang diisi method
 * kosong berhenti jadi spesifikasi dan berubah jadi tebakan.</p>
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

    // ------------------------------------------------------------------
    // Pertukaran barang antar pemain
    // ------------------------------------------------------------------

    /** Pemain mengajak pemain lain bertukar barang. */
    void playerStartsExchange(User sd, long targetId);

    /**
     * Pemain menitipkan sebagian isi satu slot inventaris ke pertukaran.
     *
     * <p>⚠️ Barangnya <b>keluar dari inventaris</b> saat ini juga; lihat
     * {@link Exchange}. Membatalkan mengembalikannya.</p>
     */
    void playerOffersItem(User sd, int slot, int amount);

    /** Pemain mengubah jumlah emas yang ditawarkan. */
    void playerOffersGold(User sd, long amount);

    /** Pemain membatalkan — kedua sisi ditutup dan titipannya dikembalikan. */
    void playerCancelsExchange(User sd);

    /**
     * Pemain menekan "tukar".
     *
     * <p>{@code claimedTarget} adalah lawan yang <b>disebut klien</b>.
     * Logika membandingkannya dengan yang dicatat server; berbeda berarti
     * kedua sisi dibatalkan. Sama seperti {@code claimedX}/{@code claimedY}
     * pada {@link #playerWalks}, ini pertahanan terhadap klien yang
     * berbohong — jangan dilewati.</p>
     */
    void playerConfirmsExchange(User sd, long claimedTarget);

    // ------------------------------------------------------------------
    // Bicara
    // ------------------------------------------------------------------

    /**
     * Pemain mengetik sesuatu untuk didengar sekitarnya.
     *
     * <p>⚠️ <b>Server tidak menyiarkan apa pun di sini</b>, dan itu bukan
     * kelalaian. Di C badan yang menyiarkan {@code clif_parsesay}
     * <b>dikomentari seluruhnya</b>; yang benar-benar terjadi adalah
     * kalimatnya disimpan di {@code sd->speech}, lalu skrip
     * {@code onSay} yang memutuskan — termasuk memanggil
     * {@code player:talk()} bila memang perlu terdengar. Itulah kenapa
     * kata kunci {@code speech} adalah <b>yang diketik pemain</b>
     * (lihat CLAUDE.md, bagian terjemahan).</p>
     *
     * @param channel 0 sekitar, 1 berteriak
     */
    void playerSays(User sd, int channel, String text);

    /**
     * Pemain berbisik.
     *
     * <p>⚠️ {@code target} tidak selalu nama orang — empat nama pendek
     * adalah <b>saluran</b>: {@code "!"} klan, {@code "!!"} grup,
     * {@code "@"} subpath, {@code "?"} pemula. Memperlakukan semuanya
     * sebagai nama pemain akan membuat keempat saluran itu menjawab
     * "tidak ditemukan".</p>
     */
    void playerWhispers(User sd, String target, String text);

    // ------------------------------------------------------------------
    // Barang
    // ------------------------------------------------------------------

    /**
     * Pemain memungut dari petak tempatnya berdiri.
     *
     * <p>⚠️ Yang benar-benar memungut adalah <b>skrip</b> {@code onPickUp},
     * bukan method ini. Server hanya menyiapkan keadaannya (mode pungut,
     * kait mantra yang sedang berjalan) lalu memanggil skripnya — sama
     * seperti {@code clif_parsegetitem} di C.</p>
     *
     * @param mode 0 = sekeping, bukan-nol = seluruh tumpukan
     *             ({@code sd->pickuptype})
     */
    void playerPicksUp(User sd, int mode);

    /**
     * Pemain menjatuhkan isi satu slot inventaris ke petaknya.
     *
     * <p>⚠️ Kait {@code on_drop} dipanggil <b>sebelum</b> barangnya benar-
     * benar jatuh, dan skrip bisa membatalkannya lewat {@code fakeDrop}.
     * Urutan itu ada di C dengan alasan yang ditulis di sumbernya:
     * mensimulasikan jatuh tanpa benar-benar menjatuhkan.</p>
     *
     * @param slot nomor slot <b>0-basis</b>
     * @param all  false = sekeping, true = seluruh isi slot
     */
    void playerDropsItem(User sd, int slot, boolean all);

    /** Pemain menjatuhkan emas; ia mendarat sebagai tumpukan koin di lantai. */
    void playerDropsGold(User sd, long amount);

    /**
     * Pemain menyerahkan barang ke <b>apa pun yang dihadapinya</b>.
     *
     * <p>⚠️ Ini gerakan pembuka, bukan lanjutan pertukaran yang sudah
     * terbuka — dan sasarannya menentukan tiga perilaku yang sama sekali
     * berbeda: <b>pemain</b> membuka jendela pertukaran, <b>mob</b>
     * menerima barangnya ke inventarisnya sendiri, dan <b>NPC</b> memanggil
     * kait skrip {@code handItem} bila memang menerima barang.</p>
     */
    void playerHandsItem(User sd, int slot, int amount);

    /** Pemain menyerahkan emas ke pemain yang dihadapinya. */
    void playerHandsGold(User sd, long amount);

    // ------------------------------------------------------------------
    // Perlengkapan & memakai barang
    // ------------------------------------------------------------------

    /**
     * Pemain mengenakan isi satu slot inventaris.
     *
     * <p>⚠️ <b>Mengenakan tidak langsung memasang.</b> Server memeriksa
     * syaratnya lalu memanggil kait skrip {@code onEquip}; skrip itulah
     * yang memanggil {@code player:equip()} dan memindahkan barangnya.
     * Banyak barang khusus menumpang kait tersebut, jadi jalan pintas
     * langsung ke pemasangan akan melewatinya.</p>
     */
    void playerWields(User sd, int slot);

    /**
     * Pemain melepas isi satu slot perlengkapan.
     *
     * @param equipSlot indeks {@code EQ_*}, bukan kode panel klien
     */
    void playerUnequips(User sd, int equipSlot);

    /**
     * Pemain memakan sesuatu. Hanya barang berjenis {@code ITM_EAT};
     * selain itu ditolak dengan pesan.
     */
    void playerEatsItem(User sd, int slot);

    /**
     * Pemain memakai barang apa pun.
     *
     * <p>⚠️ Ini pintu yang <b>tidak menyaring jenis</b> sama sekali —
     * mengenakan perlengkapan pun sah lewat sini. Yang menentukan apa yang
     * terjadi adalah jenis barangnya, dan tiap jenis punya kait skrip
     * globalnya sendiri.</p>
     */
    void playerUsesItem(User sd, int slot);

    /**
     * Pemain melempar isi satu slot.
     *
     * @param confirmed pemain sudah menjawab pertanyaan "yakin?"; hanya
     *                  barang ber-{@code ItmThrownConfirm} yang menanyakannya
     */
    void playerThrowsItem(User sd, int slot, boolean confirmed);

    // ------------------------------------------------------------------
    // Pertarungan
    // ------------------------------------------------------------------

    /**
     * Pemain mengayunkan senjata yang sedang dipegang.
     *
     * <p>⚠️ <b>Tidak ada sasaran di parameternya</b>, dan itu bukan
     * penyederhanaan: di C {@code clif_parseattack} pun tidak menerima
     * sasaran. Yang menentukan apa yang kena adalah skrip {@code swing},
     * yang mencari sendiri isi petak di depan pemain. Menambahkan sasaran
     * dari klien di sini justru membuka pintu memukul sesuatu yang tidak
     * bersebelahan.</p>
     */
    void playerAttacks(User sd);

    /**
     * Pemain menjawab menu, kotak isian, atau daftar toko.
     *
     * <p>Empat ragam jawaban yang <b>tidak bisa disatukan</b>, karena
     * masing-masing melanjutkan primitif skrip yang berbeda dan tipe
     * jawabannya pun berbeda:</p>
     * <ul>
     *   <li>{@link Answer#choice} — nomor pilihan menu, <b>1-BASIS</b>;</li>
     *   <li>{@link Answer#text} — teks yang diketik;</li>
     *   <li>{@link Answer#itemName} — <b>nama</b> barang yang dibeli
     *       (daftar beli dijawab dengan nama, bukan indeks);</li>
     *   <li>{@link Answer#slot} — <b>nomor slot</b> inventaris yang dijual.</li>
     * </ul>
     *
     * <p>{@code null} berarti pemain membatalkan — coroutine skripnya
     * dilepas, bukan dilanjutkan dengan nilai kosong.</p>
     */
    /**
     * Pemain merapal mantra dari slot buku mantranya.
     *
     * <p>⚠️ {@code slot} <b>0-basis</b>. RetroTK mengirim 1-basis dan setiap
     * penangannya diawali {@code RFIFOB(fd,5) - 1}; jangan menambahkan
     * pengurangan itu di sini.</p>
     *
     * <p>Bentuk {@code muatan} ditentukan {@code SplType} mantranya, bukan
     * klien: ragam 2 membawa id sasaran, ragam 1 membawa teks jawaban, ragam
     * 5 tidak membawa apa-apa. Ragam lain <b>tidak dirapal sama sekali</b> —
     * itu yang dilakukan C ({@code clif_parsemagic} default: {@code return
     * 0}).</p>
     *
     * @param target id benda sasaran; 0 bila mantranya bukan ragam 2
     * @param question teks jawaban; null bila mantranya bukan ragam 1
     */
    void playerCastsSpell(User sd, int slot, long target, String question);

    /**
     * Pemain menambah atau menghapus nama dari daftar abaikannya.
     *
     * @param tambah true menambah, false menghapus
     */
    void playerChangesIgnore(User sd, boolean tambah, String name);

    /**
     * Pemain mengubah grupnya ({@code clif_addgroup} /
     * {@code clif_leavegroup} / {@code clif_groupstatus}).
     *
     * @param aksi 0 ajak-atau-keluarkan {@code nama}, 1 keluar sendiri,
     *             2 minta kiriman ulang jendela grup
     * @param nama nama pemain yang diajak; diabaikan untuk aksi 1 dan 2
     */
    void playerChangesGroup(User sd, int aksi, String nama);

    /**
     * Pemain membalik satu setelan ({@code clif_changestatus}).
     *
     * <p>⚠️ <b>Saklar, bukan penetapan</b> — lihat {@code Wire.OP_SETTING}.
     * Pemanggil hanya menyebut setelan mana; nilai barunya dihitung di
     * sini.</p>
     *
     * @param jenis     nomor setelan, penomoran C
     * @param paketAwal true bila ini paket pembuka klien yang harus
     *                  diabaikan untuk setelan suara
     */
    void playerChangesSetting(User sd, int jenis, boolean paketAwal);

    /**
     * Pemain menaiki tunggangan di petak yang dihadapinya, atau turun bila
     * sedang menunggang ({@code clif_findmount} + ragam 0
     * {@code clif_changestatus}).
     */
    void playerRides(User sd);

    /**
     * Pemain berputar di tempat menghadap {@code arah} (R1/K4).
     * Bukan langkah: posisinya tidak berubah.
     */
    void playerTurns(User sd, int arah);

    /** Pemain memperagakan emosi (R1/K4). */
    void playerEmotes(User sd, int emosi);

    /** Pemain minta sekelilingnya digambar ulang (R1/K4). */
    void playerRequestsRefresh(User sd);

    /**
     * Pemain memeriksa sebuah benda (R1/K4) — memicu kait skrip
     * {@code onLook}; apa yang muncul di layar diputuskan skripnya.
     */
    void playerLooksAt(User sd, long objectId);

    /** Pemain menukar posisi dua barang di kantong (R1/K4). */
    void playerSwapsItems(User sd, int dari, int ke);

    /** Pemain menukar posisi dua mantra di buku mantra (R1/K4). */
    void playerSwapsSpells(User sd, int dari, int ke);

    /**
     * Pemain membuka profilnya sendiri (R1).
     *
     * @param ragam 0 = profil diri, 1 = status grup
     */
    void playerOpensProfile(User sd, int ragam);

    /** Pemain menyunting teks profilnya (R1). */
    void playerEditsProfile(User sd, String teks);

    /** Pemain minta daftar kota (R1). */
    void playerRequestsTowns(User sd);

    /** Pemain minta papan peringkat (R1). */
    void playerRequestsRanking(User sd);

    /** Pemain menyimpan daftar temannya (R1); daftar kosong = menghapus. */
    void playerSavesFriends(User sd, java.util.List<String> teman);

    /** Pemain menyalakan/mematikan penanda pemburu beserta catatannya (R1). */
    void playerSetsHunter(User sd, boolean nyala, String catatan);

    /**
     * Pemain memakai papan pesan (R1).
     *
     * @param aksi  1 daftar papan, 2 isi papan, 3 baca pos, 5 hapus pos,
     *              9 nmail — nomor yang sama dengan {@code clif_handle_boards}
     */
    void playerUsesBoard(User sd, int aksi, int papan, int pos);

    /** Pemain mencoba mengambil kiriman (R1). */
    void playerCollectsParcel(User sd);

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

        /**
         * Pilihan menu, <b>1-BASIS</b>.
         *
         * <p>⚠️ Basisnya ditentukan Lua, bukan protokol: {@code player.lua}
         * mengembalikan {@code options[selection]} dan tabel Lua mulai dari
         * 1. Klien RetroTK memang mengirim 1-basis; jalur RTK2 yang 0-basis
         * menambahkan satu di {@code Inbound}. Melewatkan penyesuaian itu
         * membuat pilihan pertama jadi {@code nil} — skripnya berhenti tanpa
         * satu pun cabang cocok, dan tanpa error.</p>
         */
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
