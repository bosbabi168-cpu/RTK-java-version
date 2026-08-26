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

    /** Satu mantra lenyap dari buku mantra pemain. */
    void playerSpellRemoved(User sd, int slot);

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
}
