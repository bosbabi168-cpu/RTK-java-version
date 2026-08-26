package org.rtk.map.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.common.mmo.BankItem;

/**
 * Port of clan_db.c dan {@code map_loadclanbank}/{@code map_saveclanbank}
 * (map/map.c) — klan beserta <b>bank bersamanya</b>.
 *
 * <h2>Satu larik, dua nama</h2>
 *
 * <p>⚠️ <b>"Bank klan" dan "bank subpath" adalah penyimpanan yang SAMA.</b>
 * {@code getClanBankItems} dan {@code getSubpathBankItems} di C membaca
 * larik {@code clan->clanbanks[]} yang identik; yang berbeda hanya
 * <b>bentuk keluarannya</b> — sepuluh ladang per barang versus lima.
 * Membuat penyimpanan kedua untuk subpath akan memecah isi bank yang
 * seharusnya satu.</p>
 *
 * <p>Isinya dimuat <b>per klan, saat dibutuhkan</b>, dan seluruh isinya
 * ditulis ulang setiap kali berubah — sama seperti C, yang menyimpan
 * dengan menyapu 255 slot.</p>
 */
public final class ClanDb {

    private static final Logger log = LogManager.getLogger(ClanDb.class);

    /** Batas slot bank per klan; {@code for (x = 0; x < 255; x++)} di C. */
    public static final int MAX_SLOTS = 255;

    private final Map<Integer, String> nameById = new HashMap<>();
    private final Map<Integer, List<BankItem>> bankByClan = new HashMap<>();

    /** clandb_name(): nama klan, atau "" bila tak dikenal. */
    public String nameOf(int clanId) {
        return nameById.getOrDefault(clanId, "");
    }

    public int count() {
        return nameById.size();
    }

    /** clandb_read(): muat daftar klan. Bank-nya menyusul saat dibutuhkan. */
    public int load(Sql sql) {
        nameById.clear();
        bankByClan.clear();
        int rows = sql.forEachRow("SELECT `ClnId`,`ClnName` FROM `Clans`",
                rs -> {
                    String n = rs.getString("ClnName");
                    nameById.put(rs.getInt("ClnId"), n == null ? "" : n);
                });
        if (rows < 0) {
            log.error("[CLAN] gagal membaca tabel Clans");
            return 0;
        }
        log.info("[CLAN] {} klan dimuat", nameById.size());
        return nameById.size();
    }

    /**
     * map_loadclanbank(): isi bank sebuah klan, dimuat sekali lalu disimpan
     * di memori.
     *
     * <p>⚠️ <b>Kolom {@code CbkPosition} dibaca tapi TIDAK dipakai untuk
     * menempatkan barangnya.</b> C menyalin baris ke-{@code i} ke slot
     * ke-{@code i}, jadi lubang nomor slot di database <b>dirapatkan</b>
     * saat dimuat. Ditiru apa adanya — kalau tidak, nomor slot di memori
     * akan berbeda dari yang dilihat C dan penyimpanan berikutnya menggeser
     * seluruh isinya.</p>
     */
    public List<BankItem> bank(Sql sql, int clanId) {
        List<BankItem> ada = bankByClan.get(clanId);
        if (ada != null) {
            return ada;
        }
        List<BankItem> isi = new ArrayList<>();
        int rows = sql.forEachRow(
                "SELECT `CbkEngrave`,`CbkItmId`,`CbkAmount`,`CbkChaIdOwner`,"
                + "`CbkPosition`,`CbkCustomLook`,`CbkCustomLookColor`,`CbkCustomIcon`,"
                + "`CbkCustomIconColor`,`CbkProtected`,`CbkNote`,`CbkTimer`"
                + " FROM `ClanBanks` WHERE `CbkClnId` = ? LIMIT " + MAX_SLOTS,
                rs -> {
                    BankItem b = new BankItem();
                    b.itemId = rs.getLong("CbkItmId");
                    b.amount = rs.getLong("CbkAmount");
                    b.owner = rs.getLong("CbkChaIdOwner");
                    b.time = rs.getLong("CbkTimer");
                    b.realName = str(rs.getString("CbkEngrave"));
                    b.protectedFlag = rs.getLong("CbkProtected");
                    b.customIcon = rs.getLong("CbkCustomIcon");
                    b.customIconColor = rs.getLong("CbkCustomIconColor");
                    b.customLook = rs.getLong("CbkCustomLook");
                    b.customLookColor = rs.getLong("CbkCustomLookColor");
                    b.note = str(rs.getString("CbkNote"));
                    isi.add(b);
                }, clanId);
        if (rows < 0) {
            log.error("[CLAN] gagal membaca bank klan {}", clanId);
        }
        bankByClan.put(clanId, isi);
        return isi;
    }

    /**
     * map_saveclanbank(): tulis ulang <b>seluruh</b> isi bank klan.
     *
     * <p>Bukan menyimpan yang berubah saja — C menyapu 255 slot dan
     * meng-INSERT atau meng-UPDATE tiap slot menurut isinya. Di sini
     * barisnya dihapus lalu ditulis ulang, yang hasil akhirnya sama dan
     * tidak meninggalkan slot hantu bila isinya menyusut.</p>
     */
    public boolean save(Sql sql, int clanId) {
        List<BankItem> isi = bankByClan.get(clanId);
        if (isi == null) {
            return false;
        }
        sql.update("DELETE FROM `ClanBanks` WHERE `CbkClnId` = ?", clanId);
        int pos = 0;
        for (BankItem b : isi) {
            if (b.itemId <= 0) {
                continue;
            }
            sql.update(
                    "INSERT INTO `ClanBanks` (`CbkClnId`,`CbkItmId`,`CbkAmount`,"
                    + "`CbkChaIdOwner`,`CbkTimer`,`CbkEngrave`,`CbkCustomLook`,"
                    + "`CbkCustomLookColor`,`CbkCustomIcon`,`CbkCustomIconColor`,"
                    + "`CbkProtected`,`CbkNote`,`CbkPosition`)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    clanId, b.itemId, b.amount, b.owner, b.time, b.realName,
                    b.customLook, b.customLookColor, b.customIcon, b.customIconColor,
                    b.protectedFlag, b.note, pos++);
        }
        return true;
    }

    /**
     * pcl_clanbankdeposit(): titipkan barang ke bank klan.
     *
     * <p>Barang yang <b>seluruh sepuluh atributnya sama</b> digabung
     * jumlahnya; kalau tidak ada yang cocok, dipakai slot kosong pertama.
     * Penuh (255 slot) berarti gagal.</p>
     */
    public boolean deposit(Sql sql, int clanId, BankItem baru) {
        List<BankItem> isi = bank(sql, clanId);
        for (BankItem b : isi) {
            if (cocok(b, baru)) {
                b.amount += baru.amount;
                return save(sql, clanId);
            }
        }
        if (isi.size() >= MAX_SLOTS) {
            return false;
        }
        isi.add(baru);
        return save(sql, clanId);
    }

    /**
     * pcl_clanbankwithdraw(): ambil barang dari bank klan.
     *
     * <p>Slot yang habis dibuang. ⚠️ C <b>tidak</b> memadatkan larik
     * sepenuhnya: ia menggeser tiap entri satu slot ke kiri dalam
     * <b>satu lintasan maju</b>, jadi dua lubang berurutan tidak tertutup
     * seluruhnya. Di sini daftar Java memang tidak berlubang, sehingga
     * hasil akhirnya sama tanpa perlu meniru lintasan itu.</p>
     *
     * @return false bila barangnya tidak ada atau jumlahnya kurang
     */
    public boolean withdraw(Sql sql, int clanId, BankItem cari, long amount) {
        List<BankItem> isi = bank(sql, clanId);
        for (int i = 0; i < isi.size(); i++) {
            BankItem b = isi.get(i);
            if (!cocok(b, cari)) {
                continue;
            }
            if (b.amount - amount < 0) {
                return false;
            }
            b.amount -= amount;
            if (b.amount <= 0) {
                isi.remove(i);
            }
            return save(sql, clanId);
        }
        return false;
    }

    /** Sepuluh atribut yang harus sama persis agar dua barang dianggap satu. */
    private static boolean cocok(BankItem a, BankItem b) {
        return a.itemId == b.itemId
                && a.owner == b.owner
                && a.time == b.time
                && str(a.realName).equalsIgnoreCase(str(b.realName))
                && a.protectedFlag == b.protectedFlag
                && a.customIcon == b.customIcon
                && a.customIconColor == b.customIconColor
                && a.customLook == b.customLook
                && a.customLookColor == b.customLookColor;
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
