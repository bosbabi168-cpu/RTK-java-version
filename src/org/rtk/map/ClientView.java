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

    /** Satu mantra lenyap dari buku mantra pemain. */
    void playerSpellRemoved(User sd, int slot);

    // ------------------------------------------------------------------
    // Benda di dunia (pemain, NPC, atau mob)
    // ------------------------------------------------------------------

    /** Pihak/kubu sebuah benda berubah, sehingga warnanya ikut berubah. */
    void objectSideChanged(BlockList bl);

    /** Animasi dimainkan pada benda itu sendiri. */
    void objectAnimation(BlockList bl, int animation, int times);

    /** Animasi dimainkan pada petak tertentu, bukan pada bendanya. */
    void objectAnimationAt(BlockList bl, int animation, int times, int x, int y);

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
}
