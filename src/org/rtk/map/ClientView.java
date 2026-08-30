package org.rtk.map;

import org.rtk.map.data.BlockList;
import org.rtk.map.data.MapData;

/**
 * Apa yang perlu <b>diketahui klien</b> ketika sesuatu terjadi di dunia.
 *
 * <p>Ini batas antara <b>logika permainan</b> dan <b>protokol</b>. Logika
 * memanggil peristiwa di sini ("mantra dilupakan", "NPC berpindah"); yang
 * menerjemahkannya jadi byte adalah implementasi — {@link RetroTkClientView}
 * untuk protokol RetroTK lama, dan implementasi kedua untuk protokol baru
 * yang dirancang bersama klien libGDX.</p>
 *
 * <h2>Kenapa ini ada</h2>
 *
 * <p>Sampai 26 Agustus 2026 logika permainan memanggil {@code Clif.*}
 * <b>langsung</b> — mis. {@code User.scriptRemoveSpell()} memanggil
 * {@code Clif.removeSpell()} di tengah logikanya. Selama ~89 binding yang
 * tersisa diport dengan pola itu, mengganti protokol berarti menyentuh ulang
 * semuanya. Saat lapisan ini dibuat baru <b>12 titik panggilan</b> yang
 * terpengaruh; kalau ditunda sampai porting selesai, jumlahnya berlipat.</p>
 *
 * <h2>Aturan</h2>
 *
 * <ul>
 *   <li><b>Kode di luar paket protokol JANGAN memanggil {@code Clif}
 *       langsung.</b> Tambahkan peristiwa baru di sini, lalu
 *       implementasikan di adapter.</li>
 *   <li>Nama method menyebut <b>apa yang terjadi</b>, bukan paket apa yang
 *       dikirim. {@code playerSpellRemoved}, bukan {@code send0x18}.</li>
 *   <li>Daftar method di sini pada akhirnya <b>adalah</b> spesifikasi
 *       protokol baru — diturunkan dari kebutuhan nyata skrip, bukan
 *       dikarang dari nol.</li>
 * </ul>
 */
public interface ClientView {

    // ------------------------------------------------------------------
    // Pemain
    // ------------------------------------------------------------------

    /**
     * Pemain baru masuk dunia dan perlu gambaran awal seluruhnya: identitas,
     * peta, status, posisi, dan isi area pandangnya.
     */
    void playerEnteredWorld(User sd);

    /** Tampilan pemain perlu digambar ulang (mis. setelah pindah peta). */
    void playerViewRefreshed(User sd);

    /**
     * Pemain berpindah ke peta LAIN di server yang sama (portal, mantra
     * gateway, {@code player:warp} di skrip).
     *
     * <p>⚠️ Ini bukan sekadar menggambar ulang. Klien memegang berkas
     * {@code .map} sendiri, jadi selama ia tidak diberi tahu nomor peta yang
     * baru ia akan terus menggambar peta LAMA — dengan tembok, nama, dan
     * (sejak K5) musik yang salah — sambil raganya berjalan di peta baru.
     * Dulu hanya {@link #playerEnteredWorld} yang mengirim petanya, sehingga
     * satu-satunya perpindahan yang benar adalah yang lewat sambung ulang
     * antar map server (Peringatan #149).</p>
     */
    void playerMapChanged(User sd);

    /**
     * Nilai status pemain berubah.
     *
     * @param flags kombinasi {@code SFLAG_*}; bagian mana yang berubah
     */
    void playerStatusChanged(User sd, int flags);

    /**
     * Jati diri pemain berubah — jalur, tanda, gelar: hal yang menempel pada
     * karakternya, bukan angka statusnya.
     */
    void playerIdentityChanged(User sd);

    /**
     * Sisa waktu sebuah mantra yang sedang menempel pada pemain berubah.
     *
     * @param seconds sisa waktu dalam detik; 0 = mantranya berakhir
     * @param casterName nama penyihirnya bila orang lain, "" bila dirinya
     *                   sendiri atau tidak diketahui
     */
    void playerDurationChanged(User sd, int spellId, int seconds, String casterName);

    /** Sisa aether sebuah mantra pemain berubah; 0 = habis. */
    void playerAetherChanged(User sd, int spellId, int seconds);

    /**
     * Satu slot inventaris pemain perlu digambar ulang di layarnya.
     *
     * <p>Dipakai baik saat barang masuk maupun saat isinya berubah —
     * klien menggambar ulang slotnya apa adanya.</p>
     */
    void playerInventorySlotChanged(User sd, int slot);

    /**
     * Satu slot inventaris pemain dikosongkan.
     *
     * @param reason alasan hilangnya; menentukan kalimat di layar klien
     *               (0 dibuang, 2 dimakan, 6 dipakai, 8 lapuk, 10 dijual, …)
     */
    void playerInventorySlotCleared(User sd, int slot, int reason);

    /** Satu mantra lenyap dari buku mantra pemain. */
    void playerSpellRemoved(User sd, int slot);

    /**
     * Satu slot buku mantra terisi/berubah (K3.2) — pasangan
     * {@link #playerSpellRemoved}. Dikirim untuk tiap slot terisi saat
     * masuk dunia (cermin {@code pc_loadmagic}) dan saat skrip menambah
     * mantra ({@code pcl_addspell} → {@code clif_sendmagic} di C).
     */
    void playerSpellSlotChanged(User sd, int slot);

    /**
     * Profil pemain sendiri (R1) — pengganti {@code sendMyStatus} TAHAP 1.
     * Isinya yang TIDAK bisa dihitung klien: nama kelas, gelar, klan,
     * pasangan, sisa pengalaman ke tingkat berikutnya, dan persen bilah XP.
     */
    void playerProfile(User sd);

    /**
     * Pemain dialihkan ke map server lain (R3/C3) — {@code clif_transfer}.
     *
     * <p>Server ini tidak memuat peta tujuannya; klien diberi alamat server
     * yang memuatnya lalu menyambung ke sana. Posisi tujuannya sendiri ikut
     * lewat jalur simpan ({@code destMap/destX/destY} → {@code lastPos}),
     * bukan lewat paket ini.</p>
     */
    void playerTransferred(User sd, String host, int port, int m, int x, int y);

    /** Kalender dunia berubah (R2) — {@code clif_sendtime}. */
    void worldTimeChanged(User sd);

    /** Daftar kota (R1); namanya saja, seperti {@code clif_sendtowns}. */
    void townListToPlayer(User sd, java.util.List<String> kota);

    /**
     * Papan peringkat (R1).
     *
     * @param baris tiap baris {@code {int peringkat, String nama, long nilai}}
     */
    void rankingToPlayer(User sd, String judul, java.util.List<Object[]> baris);

    /**
     * Satu baris obrolan yang <b>hanya pemain ini</b> yang melihatnya,
     * seolah diucapkan oleh benda tertentu.
     *
     * @param speakerId id benda yang tampak berbicara; 0 = pemain sendiri
     */
    void chatLineToPlayer(User sd, int type, long speakerId, String text);

    /** Kotak teks menimpa layar pemain. */
    void popupToPlayer(User sd, String text);

    /**
     * Kamera pemain digeser tanpa ia berpindah petak, dan seluruh isi
     * pandangannya digambar ulang.
     */
    void playerCameraChanged(User sd, int x, int y);

    /**
     * Daftar isi sebuah papan pesan (atau kotak surat) untuk pemain.
     *
     * @param flags1 bendera tombol tulis/hapus yang dihitung dari hak akses
     * @param flags2 ragam papan: kotak surat atau papan biasa
     */
    void boardListToPlayer(User sd, int board, int flags1, int flags2,
                           java.util.List<Clif.BoardEntry> isi);

    /**
     * Isi SATU kiriman papan (K2-lanjutan).
     *
     * @param bendera hak yang dihitung CHAR SERVER, bukan yang diklaim
     *                klien: bit 1 boleh menulis, bit 2 boleh menghapus
     */
    void boardPostToPlayer(User sd, int board, int pos, int bendera,
                           String penulis, String topik, int bulan, int hari,
                           String isi);

    // ------------------------------------------------------------------
    // Pertukaran barang antar pemain
    // ------------------------------------------------------------------

    /** Jendela pertukaran terbuka antara dua pemain. */
    void exchangeOpened(User sd, User target);

    /**
     * Satu barang dititipkan ke pertukaran — <b>kedua pihak</b> perlu
     * melihatnya, tetapi dengan tampilan berbeda: yang menitipkan melihat
     * jumlahnya, lawannya hanya melihat namanya.
     *
     * @param slotInList nomor urut barang itu di daftar titipan (1-basis)
     */
    void exchangeItemOffered(User sd, User target, org.rtk.common.mmo.Item item,
                             int slotInList);

    /** Jumlah emas yang ditawarkan berubah. */
    void exchangeGoldOffered(User sd, User target, long gold);

    /**
     * Salah satu pihak menekan "tukar".
     *
     * @param completed false = baru satu pihak yang setuju (barang belum
     *                  berpindah); true = keduanya setuju dan pertukarannya
     *                  sudah terjadi
     */
    void exchangeConfirmed(User sd, User target, boolean completed);

    /** Daftar peta yang bisa dipilih pemain (peta rumah, teleporter). */
    void mapSelectionToPlayer(User sd, String title,
                              java.util.List<Integer> x0, java.util.List<Integer> y0,
                              java.util.List<String> names, java.util.List<Integer> ids,
                              java.util.List<Integer> x1, java.util.List<Integer> y1);

    /** Daftar pemain di peta ini beserta "power rating"-nya. */
    void powerBoardToPlayer(User sd);

    /** Formulir pertanyaan papan; jawabannya kembali lewat dialog. */
    void boardQuestionsToPlayer(User sd, java.util.List<String> headers,
                                java.util.List<String> questions,
                                java.util.List<Integer> inputLines);

    /** Teks besar melayang di tengah layar pemain (pengumuman event). */
    void guiTextToPlayer(User sd, String text);

    /** Kotak "kertas" berisi teks panjang, dengan ukuran yang ditentukan skrip. */
    void paperToPlayer(User sd, String text, int width, int height);

    /**
     * Alamat web dibuka di klien pemain.
     *
     * @param type 0 = peramban dalam permainan, 1 = peramban luar lalu klien
     *             ditutup, 2 = jendela sembul
     */
    void urlToPlayer(User sd, int type, String url);

    /**
     * Penghitung waktu dipasang di layar pemain.
     *
     * @param seconds lama dalam detik
     */
    void playerTimerSet(User sd, int type, long seconds);

    /**
     * Pemain berbicara — <b>namanya disisipkan server</b> di depan pesannya.
     *
     * @param type 1 = berteriak (seluruh peta); selain itu bicara biasa
     */
    void playerSpoke(User sd, String text, int type);

    /**
     * Gerak pemain dikunci atau dilepas di sisi klien.
     *
     * <p>⚠️ Terbalik dari dugaan: {@code lock} mengirim 0 dan {@code unlock}
     * mengirim 1. Itu di C.</p>
     */
    void playerMovementLocked(User sd, boolean locked);

    /**
     * Animasi dimainkan pada sebuah benda, tetapi <b>hanya satu pemain</b>
     * yang melihatnya.
     */
    void objectAnimationSeenBy(User viewer, BlockList target, int animation, int times);

    /**
     * Nyawa pemain berubah, dan angka kerusakannya perlu terlihat.
     *
     * @param percent sisa nyawa dalam persen (0..100)
     */
    void playerHealthChanged(User sd, int critical, int percent, int damage);

    // ------------------------------------------------------------------
    // Benda di dunia (pemain, NPC, atau mob)
    // ------------------------------------------------------------------

    /** Pihak/kubu sebuah benda berubah, sehingga warnanya ikut berubah. */
    void objectSideChanged(BlockList bl);

    /** Animasi dimainkan pada benda itu sendiri. */
    void objectAnimation(BlockList bl, int animation, int times);

    /** Animasi dimainkan pada petak tertentu, bukan pada bendanya. */
    void objectAnimationAt(BlockList bl, int animation, int times, int x, int y);

    /** Bunyi terdengar di sekitar sebuah benda. */
    void soundPlayed(BlockList at, int sound);

    /**
     * Sebuah benda berbicara, dan <b>semua yang berada di sekitarnya</b>
     * mendengar — termasuk pembicaranya sendiri bila ia pemain.
     *
     * @param type ragam obrolan di C ({@code clif_speak}); menentukan warna
     *             dan tempat teksnya muncul di klien
     */
    void objectSpoke(BlockList speaker, int type, String text);

    /**
     * Sebuah benda memainkan gerakan: menyerang, melempar, duduk, merapal,
     * atau makan.
     *
     * @param action ragam gerakan (0 diam, 1 serang, 2 lempar, 3 tembak,
     *               4/5 duduk, 6 sihir, 7/8 makan)
     * @param time   lama gerakan
     * @param sound  bunyi yang menyertainya; 0 = tanpa bunyi
     */
    void objectActed(BlockList bl, int action, int time, int sound);

    /**
     * Wujud sebuah benda berubah tanpa ia berpindah — menyamar, menghilang,
     * berganti perlengkapan, mati jadi hantu.
     *
     * <p>Yang di sekitarnya perlu menggambar ulang benda itu <b>di tempat
     * yang sama</b>; posisinya sendiri tidak ikut berubah.</p>
     */
    void objectAppearanceChanged(BlockList bl);

    /** Sebuah benda lenyap dari dunia dan tidak boleh tergambar lagi. */
    void objectRemoved(BlockList bl);

    /**
     * Pesan teks untuk seorang pemain.
     *
     * @param type ragam pesan di C ({@code clif_sendmsg}); 5 = peringatan
     */
    void messageToPlayer(User sd, int type, String text);

    // ------------------------------------------------------------------
    // NPC
    // ------------------------------------------------------------------

    /**
     * NPC berpindah satu petak.
     *
     * <p>⚠️ <b>Kebocoran yang diketahui:</b> empat parameter terakhir adalah
     * petak yang <i>baru masuk</i> jangkauan pandang akibat perpindahan itu —
     * konsep milik viewport RetroTK, bukan peristiwa permainan. Idealnya
     * adapter yang menghitungnya sendiri dari arah gerak. Dibiarkan di sini
     * dulu supaya perpindahan ke lapisan ini tidak sekaligus memindahkan
     * perhitungan viewport; <b>rapikan saat protokol baru dirancang</b>.
     * Kirim {@code revealX1 < 0} bila tidak ada petak baru.</p>
     */
    void npcMoved(Npc nd, MapData map, int fromX, int fromY,
                  int revealX0, int revealY0, int revealX1, int revealY1);

    /** Satu NPC menjadi terlihat oleh seorang pemain. */
    void npcAppearedTo(User viewer, Npc nd);

    // ------------------------------------------------------------------
    // Mob
    // ------------------------------------------------------------------

    /**
     * Mob berpindah satu petak.
     *
     * <p>Empat parameter terakhir membawa kebocoran yang sama dengan
     * {@link #npcMoved} — petak yang baru masuk jangkauan pandang. Rapikan
     * keduanya bersamaan saat protokol baru dirancang; kirim
     * {@code revealX1 < 0} bila tidak ada petak baru.</p>
     */
    void mobMoved(Mob mb, MapData map, int fromX, int fromY,
                  int revealX0, int revealY0, int revealX1, int revealY1);

    /** Satu mob lahir (atau lahir kembali) dan mulai terlihat di sekitarnya. */
    void mobSpawned(Mob mb);

    // ------------------------------------------------------------------
    // Barang di lantai
    // ------------------------------------------------------------------

    /**
     * Sekeping barang jatuh ke petak dan mulai terlihat di sekitarnya.
     *
     * <p>Jebakan hanya terlihat oleh pemain yang sudah menemukannya —
     * penyaringnya di sisi penerima, bukan di sini.</p>
     */
    void floorItemAppeared(FloorItem fl);

    /**
     * Sebuah benda melempar sesuatu ke petak lain — <b>animasinya saja</b>.
     * Barang yang benar-benar mendarat (kalau ada) dilaporkan terpisah lewat
     * {@link #floorItemAppeared}.
     */
    void objectThrown(BlockList from, int toX, int toY, int icon, int color, int action);

    // ------------------------------------------------------------------
    // Dialog NPC
    // ------------------------------------------------------------------

    /**
     * Skrip berhenti di tengah dan menitipkan sesuatu untuk ditampilkan —
     * menu, kotak dialog, isian teks, atau daftar toko.
     *
     * <p>Yang mana persisnya ada di {@code p.pendingDialog}; menerjemahkannya
     * jadi paket adalah urusan lapisan protokol. Logika hanya melaporkan
     * bahwa skripnya <b>sudah punya sesuatu untuk ditunjukkan</b>.</p>
     *
     * <p>Dipanggil setiap kali skrip mungkin telah berhenti: sesudah klik
     * NPC dan sesudah tiap jawaban pemain. Aman bila tidak ada apa-apa yang
     * tertunda — implementasinya tidak melakukan apa pun.</p>
     */
    void scriptDialogReady(User sd, org.rtk.map.script.ScriptPlayer p);

    /**
     * Satu NPC berbicara langsung kepada seorang pemain, tanpa tombol
     * lanjut/kembali — kotak dialog sekali baca.
     *
     * <p>Terpisah dari {@link #scriptDialogReady} karena tidak berasal dari
     * skrip yang tertunda: ini penolakan yang diputuskan server sendiri.</p>
     */
    void npcSaidTo(User sd, long npcId, String text);

    // ------------------------------------------------------------------
    // Langkah pemain
    // ------------------------------------------------------------------

    /**
     * Langkah pemain <b>ditolak</b> — klien harus dikembalikan ke posisi
     * yang dipegang server.
     *
     * <p>Dua sebab, dan keduanya berakhir di sini: klien menyebut koordinat
     * yang tidak cocok dengan catatan server, atau petak tujuannya
     * terhalang. Logika tidak membedakan keduanya ke arah klien.</p>
     */
    void playerStepRejected(User sd);

    /** Langkah pemain diterima — dilaporkan kepada pemain itu sendiri. */
    void playerStepped(User sd, int direction, int fromX, int fromY);

    /**
     * Langkah pemain terlihat oleh sekitarnya.
     *
     * <p>Terpisah dari {@link #playerStepped} karena tidak selalu terjadi:
     * pemain yang terjepit di tepi peta tetap menghadap arah baru tanpa
     * benar-benar berpindah petak.</p>
     */
    void playerStepSeen(User sd, int direction, int fromX, int fromY);

    /**
     * ⚠️ <b>Kebocoran protokol yang disengaja.</b> Klien RetroTK menitipkan
     * permintaan menggambar ulang sepetak wilayah pada paket langkah yang
     * sama, dan urutannya mengikat — lihat catatan di
     * {@code ClientCommands.playerWalks}. Protokol baru tidak akan
     * membutuhkan ini; server tahu sendiri apa yang baru terlihat.
     */
    void areaRedrawRequested(User sd, MapData map, int x, int y,
                             int width, int height, int checksum);

    /**
     * Pemain sudah berpindah petak; area pandangnya ikut bergeser.
     *
     * <p>Dipanggil SESUDAH {@code map.moveBlock} — {@code sd.x/y} sudah
     * posisi baru, ({@code fromX},{@code fromY}) posisi lama. Adapter yang
     * kliennya tidak meminta sendiri (RTK2) memakai ini untuk mengirim
     * benda yang baru masuk pandangan dan mencabut yang keluar. Adapter
     * RetroTK tidak butuh: kliennya meminta lewat blok 0x06
     * ({@link #areaRedrawRequested}).</p>
     *
     * <p>⚠️ Sebelum 30 Agu 2026 tidak ada jalur ini sama sekali, dan
     * komentar di atas ("server tahu sendiri apa yang baru terlihat")
     * menutupi kenyataan bahwa server TIDAK PERNAH memberitahunya: benda
     * yang masuk pandangan saat berjalan tidak pernah sampai ke klien RTK2
     * (Peringatan #166).</p>
     */
    void playerViewMoved(User sd, int fromX, int fromY);

    // ------------------------------------------------------------------
    // Perlengkapan
    // ------------------------------------------------------------------

    /**
     * Satu slot perlengkapan pemain kini berisi sesuatu yang lain
     * ({@code clif_equipit}).
     *
     * <p>Terpisah dari {@link #objectAppearanceChanged}: yang itu soal
     * bagaimana pemain <b>terlihat oleh orang lain</b>, yang ini soal panel
     * perlengkapan miliknya sendiri. Mengganti cincin mengubah panelnya
     * tanpa mengubah gambarnya sedikit pun.</p>
     */
    void playerEquipmentChanged(User sd, int slot);

    /** Satu slot perlengkapan pemain kini kosong ({@code clif_unequipit}). */
    void playerEquipmentCleared(User sd, int slot);

    // ------------------------------------------------------------------
    // Perubahan peta saat berjalan
    // ------------------------------------------------------------------

    /**
     * Petak di sekitar pemain berubah — lantai, objek, atau penghalangnya.
     *
     * <p>Dipicu {@code setTile}/{@code setObject}/{@code setPass} dari
     * skrip. Berbeda dari {@link #playerViewRefreshed}: yang itu menggambar
     * ulang <b>segalanya</b> termasuk benda dan pemain; yang ini hanya
     * tanahnya.</p>
     */
    void mapTilesChanged(User sd);

    /**
     * Cuaca di peta pemain berubah.
     *
     * <p>⚠️ Pemain yang mematikan setelan cuaca tetap menerima
     * peristiwanya — yang menyaring lapisan protokol, sama seperti efek
     * mantra pada {@link #objectAnimationSeenBy}.</p>
     */
    void weatherChanged(User sd, int weather);

    /**
     * Jendela grup pemain berubah — anggota masuk, keluar, atau grupnya
     * bubar ({@code clif_groupstatus}).
     *
     * <p>Dikirim juga saat grup baru saja bubar: daftar kosong adalah cara
     * memberi tahu klien untuk menutup jendelanya.</p>
     */
    void groupStatusChanged(User sd);

    /**
     * Darah dan mana anggota grup berubah ({@code clif_grouphealth_update}).
     *
     * <p>⚠️ Terpisah dari {@link #groupStatusChanged} karena dipanggil tiap
     * detak selama pemain bergrup, sedangkan susunan grup jarang berubah.</p>
     */
    void groupHealthChanged(User sd);

    /**
     * Setelan pemain berubah ({@code clif_changestatus}).
     *
     * <p>⚠️ Hanya RTK2 yang mengirim apa pun untuk ini. Di RetroTK kata
     * setelan <b>menumpang</b> paket status ({@code clif_sendstatus} menulis
     * {@code settingFlags} di offset 22 dan 51), jadi peristiwa ini tidak
     * punya paket sendiri di sana — bukan karena terlewat.</p>
     */
    void playerSettingsChanged(User sd);
}
