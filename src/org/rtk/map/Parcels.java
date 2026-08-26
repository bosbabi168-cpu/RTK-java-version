package org.rtk.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.common.mmo.Item;

/**
 * Port paket kiriman antar pemain — tabel {@code Parcels}
 * ({@code bll_sendparcel}, {@code pcl_getparcel}, {@code pcl_getparcellist},
 * {@code pcl_removeparcel} di sl.c).
 *
 * <p>Seluruhnya SQL murni: tidak ada paket klien, tidak ada keadaan di
 * memori. Karena itu blok ini bisa diuji sepenuhnya offline — cukup MySQL
 * hidup, tanpa klien.</p>
 *
 * <h2>Nomor urut kiriman</h2>
 *
 * <p>{@code ParPosition} bukan kunci utama, melainkan <b>nomor urut per
 * penerima</b>: pengirim mencari nomor terbesar milik penerima lalu memakai
 * nomor berikutnya. Itu sebabnya menghapus kiriman <b>tidak</b> menomori
 * ulang sisanya — lubang di tengah dibiarkan, dan kiriman berikutnya tetap
 * mengambil nomor tertinggi + 1.</p>
 */
public final class Parcels {

    private static final Logger log = LogManager.getLogger(Parcels.class);

    private Parcels() {
    }

    /**
     * Satu kiriman: barangnya, pengirimnya, dan nomor urutnya.
     *
     * <p>⚠️ {@code npcFlag} mengubah <b>arti</b> {@code sender}: bila bukan
     * nol, pengirimnya NPC dan idnya digeser
     * {@code + NPC_START_NUM - 2} supaya tidak bertabrakan dengan id
     * pemain. Skrip membaca {@code sender} apa adanya, jadi pergeseran itu
     * harus terjadi di sini.</p>
     */
    public static final class Parcel {
        public final Item data = new Item();
        public long sender;
        public int pos;
        public int npcFlag;
    }

    private static final String KOLOM =
            "`ParItmId`, `ParAmount`, `ParChaIdOwner`, `ParEngrave`, `ParSender`,"
            + " `ParPosition`, `ParNpc`, `ParCustomLook`, `ParCustomLookColor`,"
            + " `ParCustomIcon`, `ParCustomIconColor`, `ParProtected`, `ParItmDura`";

    /**
     * bll_sendparcel(): titipkan sebuah barang untuk pemain lain.
     *
     * @return true bila barisnya benar-benar masuk
     */
    public static boolean send(Sql sql, long receiver, long sender, long itemId, int amount,
                               long owner, String engrave, int npcFlag,
                               long customLook, long customLookColor,
                               long customIcon, long customIconColor,
                               long protectedFlag, int dura) {
        Integer max = sql.queryInt(
                "SELECT MAX(`ParPosition`) FROM `Parcels` WHERE `ParChaIdDestination` = ?",
                receiver);
        int posBaru = (max == null ? -1 : max) + 1;

        int n = sql.update(
                "INSERT INTO `Parcels` (`ParChaIdDestination`, `ParSender`, `ParItmId`,"
                + " `ParAmount`, `ParChaIdOwner`, `ParEngrave`, `ParPosition`, `ParNpc`,"
                + " `ParCustomLook`, `ParCustomLookColor`, `ParCustomIcon`,"
                + " `ParCustomIconColor`, `ParProtected`, `ParItmDura`)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                receiver, sender, itemId, amount, owner,
                engrave == null ? "" : engrave, posBaru, npcFlag,
                customLook, customLookColor, customIcon, customIconColor,
                protectedFlag, dura);
        return n > 0;
    }

    /**
     * pcl_getparcel(): kiriman <b>pertama</b> yang menunggu pemain ini,
     * atau null bila tidak ada.
     *
     * <p>C mengirim minitext "You have no pending parcels." saat kosong;
     * pesan itu dikirim pemanggilnya di sini supaya lapisan ini tetap bebas
     * protokol.</p>
     */
    public static Parcel first(Sql sql, long charId) {
        List<Parcel> semua = list(sql, charId, 1);
        return semua.isEmpty() ? null : semua.get(0);
    }

    /** pcl_getparcellist(): seluruh kiriman yang menunggu pemain ini. */
    public static List<Parcel> list(Sql sql, long charId) {
        return list(sql, charId, Integer.MAX_VALUE);
    }

    private static List<Parcel> list(Sql sql, long charId, int batas) {
        List<Parcel> out = new ArrayList<>();
        int rows = sql.forEachRow(
                "SELECT " + KOLOM + " FROM `Parcels` WHERE `ParChaIdDestination` = ?",
                rs -> {
                    if (out.size() >= batas) {
                        return;
                    }
                    Parcel p = new Parcel();
                    p.data.id = rs.getLong("ParItmId");
                    p.data.amount = rs.getInt("ParAmount");
                    p.data.owner = rs.getLong("ParChaIdOwner");
                    p.data.realName = str(rs.getString("ParEngrave"));
                    p.data.customLook = rs.getLong("ParCustomLook");
                    p.data.customLookColor = rs.getLong("ParCustomLookColor");
                    p.data.customIcon = rs.getLong("ParCustomIcon");
                    p.data.customIconColor = rs.getLong("ParCustomIconColor");
                    p.data.protectedFlag = rs.getLong("ParProtected");

                    // dura 0 berarti "pakai bawaan jenis barangnya"
                    int dura = rs.getInt("ParItmDura");
                    p.data.dura = dura == 0
                            ? MapServer.itemDb.info(p.data.id).durability() : dura;

                    p.pos = rs.getInt("ParPosition");
                    p.npcFlag = rs.getInt("ParNpc");
                    long sender = rs.getLong("ParSender");
                    // Lihat catatan di Parcel: pengirim NPC digeser ke rentang id NPC.
                    p.sender = p.npcFlag == 0 ? sender : sender + Npc.NPC_START_NUM - 2;
                    out.add(p);
                }, charId);
        if (rows < 0) {
            log.error("[PARCEL] gagal membaca kiriman untuk karakter {}", charId);
        }
        return out;
    }

    /**
     * pcl_removeparcel(): buang satu kiriman.
     *
     * <p>⚠️ Yang menentukan baris mana yang terhapus <b>hanya penerima dan
     * nomor urutnya</b>. Sebelas argumen lain yang diminta binding ini
     * (barang, jumlah, pemilik, ukiran, …) <b>tidak dipakai sama sekali</b>
     * di C — sisa dari pencatatan log yang dikomentari. Jangan memakainya
     * sebagai penyaring tambahan: skrip mengirim nilai seadanya ke sana.</p>
     */
    public static boolean remove(Sql sql, long charId, int pos) {
        return sql.update(
                "DELETE FROM `Parcels` WHERE `ParChaIdDestination` = ? AND `ParPosition` = ?",
                charId, pos) > 0;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
