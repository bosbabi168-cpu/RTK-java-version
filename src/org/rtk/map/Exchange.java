package org.rtk.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.Item;

/**
 * Pertukaran barang antar pemain — port {@code sd->exchange} beserta
 * seluruh jalurnya di clif.c ({@code clif_startexchange},
 * {@code clif_exchange_additem}, {@code clif_exchange_sendok},
 * {@code clif_exchange_finalize}, {@code clif_exchange_close}).
 *
 * <h2>Barang DITITIPKAN, bukan ditandai</h2>
 *
 * <p>⚠️ Ini inti keamanannya, dan paling mudah salah: barang yang
 * ditawarkan <b>benar-benar dikeluarkan dari inventaris</b> dan disimpan di
 * sini sampai pertukaran selesai atau dibatalkan. Membatalkan
 * <b>mengembalikannya</b>.</p>
 *
 * <p>Kalau titipan ini diubah jadi sekadar penanda "barang ini ditawarkan"
 * tanpa mencabutnya, pemain bisa menjatuhkan atau menjual barang yang sama
 * di tengah pertukaran dan menggandakannya. Kalau sebaliknya dicabut tanpa
 * jalur pengembalian, barang hilang setiap kali pertukaran batal. Keduanya
 * pernah jadi bug klasik di server sejenis.</p>
 *
 * <h2>Persetujuan dua tahap</h2>
 *
 * <p>Menekan "tukar" <b>bukan</b> berarti selesai. Yang pertama menekan
 * hanya ditandai; barang baru berpindah ketika <b>yang kedua</b> menekan.
 * Itu sebabnya {@link #confirm} punya dua cabang yang terlihat mirip.</p>
 */
public final class Exchange {

    private static final Logger log = LogManager.getLogger(Exchange.class);

    /** Batas titipan per pemain ({@code item[52]} di C). */
    public static final int MAX_ITEMS = 52;

    /**
     * Keadaan pertukaran milik <b>satu</b> pemain. Tiap peserta punya
     * miliknya sendiri; keduanya saling menunjuk lewat {@link #target}.
     */
    public static final class State {
        /** Id pemain lawan; 0 berarti tidak sedang bertukar. */
        public long target;
        /** Barang yang sudah dititipkan — sudah keluar dari inventaris. */
        public final List<Item> items = new ArrayList<>();
        /** Emas yang ditawarkan; masih ada di dompet sampai pertukaran jadi. */
        public long gold;
        /** Sudah menekan "tukar". */
        public boolean done;

        public boolean active() {
            return target != 0;
        }
    }

    private Exchange() {
    }

    /**
     * clif_startexchange(): buka jendela pertukaran dengan pemain lain.
     *
     * <p>Penjaga yang ditiru: sasaran harus pemain di <b>peta yang sama</b>,
     * dan pemain ber-stealth tidak bisa diajak kecuali pengajaknya GM.</p>
     *
     * <p>⚠️ Menukar dengan <b>diri sendiri</b> tidak ditolak diam-diam —
     * C menjawabnya dengan lelucon ("You move your items from one hand to
     * another, but quickly get bored."). Ditiru, karena itu satu-satunya
     * umpan balik yang pemain terima.</p>
     */
    public static boolean start(User sd, long targetId) {
        if (targetId == sd.id) {
            MapServer.clientView.messageToPlayer(sd, 5,
                    "Kamu memindahkan barang dari satu tangan ke tangan lain, "
                    + "lalu cepat bosan.");
            return false;
        }
        User tsd = MapServer.userById(targetId);
        if (tsd == null || tsd.m != sd.m) {
            return false;
        }
        boolean tersembunyi = (tsd.optFlags & User.OPT_STEALTH) != 0;
        if (tersembunyi && !sd.isGm()) {
            return false;
        }

        sd.exchange.target = tsd.id;
        tsd.exchange.target = sd.id;
        MapServer.clientView.exchangeOpened(sd, tsd);
        return true;
    }

    /**
     * clif_exchange_additem(): titipkan sebagian isi satu slot inventaris.
     *
     * <p>Tiga penjaga, dan urutannya sama seperti C:</p>
     * <ol>
     *   <li>Barang yang <b>tidak boleh ditukar</b> ditolak. ⚠️ Kolomnya
     *       {@code ItmExchangeable}, dan artinya <b>kebalikan namanya</b>:
     *       nilai bukan-nol berarti TIDAK bisa ditukar. Sama menyesatkannya
     *       dengan {@code ItmDroppable} (Peringatan #32).</li>
     *   <li>Ruang inventaris <b>penerima</b> diperiksa di sini, saat
     *       dititipkan — bukan nanti saat pertukaran jadi.</li>
     *   <li>Jumlahnya harus ada di slot itu.</li>
     * </ol>
     *
     * <p>Setelah lolos, barangnya <b>dicabut dari inventaris</b> — lihat
     * catatan titipan di atas.</p>
     */
    public static boolean offerItem(User sd, int slot, int amount) {
        User tsd = MapServer.userById(sd.exchange.target);
        if (tsd == null || slot < 0 || slot >= sd.status.inventory.size() || amount <= 0) {
            return false;
        }
        Item it = sd.status.inventory.get(slot);
        if (it.id <= 0 || amount > it.amount) {
            return false;
        }
        if (MapServer.itemDb.cannotBeExchanged(it.id)) {
            MapServer.clientView.messageToPlayer(sd, 5, "Barang itu tidak bisa ditukar.");
            return false;
        }
        if (tsd.status.inventory.size() >= tsd.status.maxInv) {
            MapServer.clientView.messageToPlayer(sd, 5,
                    "Inventaris lawan tidak cukup.");
            return false;
        }
        if (sd.exchange.items.size() >= MAX_ITEMS) {
            return false;
        }

        Item titip = salin(it);
        titip.amount = amount;
        sd.exchange.items.add(titip);

        // pc_delitem(sd, id, amount, 9): barangnya BENAR-BENAR keluar
        it.amount -= amount;
        if (it.amount <= 0) {
            MapServer.clientView.playerInventorySlotCleared(sd, slot, 9);
        } else {
            MapServer.clientView.playerInventorySlotChanged(sd, slot);
        }

        MapServer.clientView.exchangeItemOffered(sd, tsd, titip,
                sd.exchange.items.size());
        return true;
    }

    /**
     * Bagian gold dari {@code clif_parse_exchange} ragam 3.
     *
     * <p>⚠️ Emas <b>tidak</b> dititipkan seperti barang — ia tetap di
     * dompet sampai pertukaran benar-benar jadi, dan diperiksa ulang saat
     * konfirmasi. Karena itu menawarkan lebih dari yang dimiliki di sini
     * tidak ditolak sebagai kesalahan: C hanya mengabaikan nilainya dan
     * mengirim ulang tampilannya.</p>
     */
    public static void offerGold(User sd, long amount) {
        User tsd = MapServer.userById(sd.exchange.target);
        if (amount <= sd.scriptGetAttr("money")) {
            sd.exchange.gold = amount;
        }
        MapServer.clientView.exchangeGoldOffered(sd, tsd, sd.exchange.gold);
    }

    /**
     * clif_exchange_close(): batalkan, dan <b>kembalikan titipannya</b>.
     *
     * <p>Dipanggil untuk kedua sisi saat salah satu membatalkan. Aman
     * dipanggil pada pemain yang tidak sedang bertukar.</p>
     */
    public static void cancel(User sd) {
        if (sd == null) {
            return;
        }
        sd.exchange.target = 0;
        for (Item it : sd.exchange.items) {
            // pc_additemnolog(): kembali ke inventaris tanpa dicatat
            sd.addItemById(it.id, it.amount, it.dura);
        }
        bersihkan(sd);
    }

    /**
     * clif_exchange_sendok(): pemain menekan "tukar".
     *
     * <p>Dua cabang yang terlihat mirip tetapi berbeda arti: bila lawan
     * <b>sudah</b> menekan lebih dulu, pertukarannya <b>jadi sekarang</b>;
     * kalau belum, sisi ini hanya ditandai dan keduanya diberi tahu.</p>
     *
     * <p>Dua penjaga sebelum itu, keduanya membatalkan <b>kedua sisi</b>:
     * sasaran yang tidak cocok (tanda pertukaran dipalsukan klien), dan
     * emas yang ternyata sudah tidak cukup.</p>
     */
    public static void confirm(User sd, long claimedTarget) {
        User tsd = MapServer.userById(sd.exchange.target);

        if (sd.exchange.target != claimedTarget) {
            // Klien menyebut lawan yang berbeda dari yang dicatat server.
            MapServer.clientView.messageToPlayer(sd, 4, "Pertukaran dibatalkan.");
            if (tsd != null) {
                MapServer.clientView.messageToPlayer(tsd, 4, "Pertukaran dibatalkan.");
            }
            cancel(sd);
            cancel(tsd);
            return;
        }
        if (sd.exchange.gold > sd.scriptGetAttr("money")) {
            MapServer.clientView.messageToPlayer(sd, 4, "Emasmu tidak sebanyak itu.");
            if (tsd != null) {
                MapServer.clientView.messageToPlayer(tsd, 4, "Pertukaran dibatalkan.");
            }
            cancel(sd);
            cancel(tsd);
            return;
        }
        if (tsd == null) {
            cancel(sd);
            return;
        }

        if (tsd.exchange.done) {
            finalize(sd, tsd);
            return;
        }
        sd.exchange.done = true;
        MapServer.clientView.exchangeConfirmed(sd, tsd, false);
    }

    /**
     * clif_exchange_finalize(): pindahkan isi kedua titipan sekaligus.
     *
     * <p>Kait {@code characterLog.exchangeLogWrite} dipanggil lebih dulu
     * dengan <b>kedua pemain</b> — pencatatan harus terjadi selagi kedua
     * titipan masih utuh.</p>
     *
     * <p>Emas berpindah satu arah saja per sisi: masing-masing menyerahkan
     * yang ditawarkannya sendiri.</p>
     */
    private static void finalize(User sd, User tsd) {
        var engine = MapServer.scriptEngine;
        if (engine != null) {
            try {
                engine.doScript("characterLog", "exchangeLogWrite",
                        engine.playerRef(sd.scriptPlayer()),
                        engine.playerRef(tsd.scriptPlayer()));
            } catch (RuntimeException e) {
                log.error("[TUKAR] kait exchangeLogWrite gagal", e);
            }
        }

        for (Item it : sd.exchange.items) {
            tsd.addItemById(it.id, it.amount, it.dura);
        }
        for (Item it : tsd.exchange.items) {
            sd.addItemById(it.id, it.amount, it.dura);
        }

        pindahEmas(sd, tsd, sd.exchange.gold);
        pindahEmas(tsd, sd, tsd.exchange.gold);

        MapServer.clientView.exchangeConfirmed(sd, tsd, true);
        MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_XPMONEY);
        MapServer.clientView.playerStatusChanged(tsd, Clif.SFLAG_XPMONEY);

        sd.exchange.target = 0;
        tsd.exchange.target = 0;
        bersihkan(sd);
        bersihkan(tsd);
    }

    private static void pindahEmas(User dari, User ke, long jumlah) {
        if (jumlah <= 0) {
            return;
        }
        dari.scriptSetAttr("money", dari.scriptGetAttr("money") - jumlah);
        ke.scriptSetAttr("money", ke.scriptGetAttr("money") + jumlah);
    }

    /** clif_exchange_cleanup(): kosongkan titipan, tanda, dan emasnya. */
    private static void bersihkan(User sd) {
        if (sd == null) {
            return;
        }
        sd.exchange.items.clear();
        sd.exchange.gold = 0;
        sd.exchange.done = false;
    }

    private static Item salin(Item src) {
        Item c = new Item();
        c.id = src.id;
        c.amount = src.amount;
        c.dura = src.dura;
        c.owner = src.owner;
        c.protectedFlag = src.protectedFlag;
        c.custom = src.custom;
        c.customIcon = src.customIcon;
        c.customIconColor = src.customIconColor;
        c.customLook = src.customLook;
        c.customLookColor = src.customLookColor;
        c.realName = src.realName;
        c.note = src.note;
        c.time = src.time;
        return c;
    }
}
