package org.rtk.map;

import org.rtk.common.mmo.CharStatus;
import org.rtk.map.data.BlockList;

/**
 * Port of USER / struct map_sessiondata (map/map.h) — pemain yang sedang
 * bermain di map server.
 *
 * Di C, struct ini menggabungkan tiga hal: {@code block_list bl} (posisi di
 * peta), {@code mmo_charstatus status} (data tersimpan), dan puluhan ladang
 * runtime yang tidak ikut disimpan ke database. Di sini pembagiannya dibuat
 * eksplisit: kelas ini <b>adalah</b> {@link BlockList} (mewarisi posisi),
 * menyimpan {@link CharStatus} sebagai data persisten, dan menaruh nilai
 * turunan di ladangnya sendiri.
 *
 * Hanya ladang runtime yang sudah benar-benar dipakai yang diport. Sisanya
 * ({@code npc_*}, exchange, group, dsb.) menyusul bersama subsistem yang
 * membutuhkannya — lihat roadmap di CLAUDE.md.
 */
public final class User extends BlockList {

    /**
     * Batas id: nilai di atasnya milik mob, bukan pemain.
     * Setara MOB_START_NUM di map.h.
     */
    public static final long MOB_START_NUM = 1073741823L;

    /** fd sesi klien pada {@link org.rtk.common.NetServer} milik map server. */
    public final int fd;

    /** Data yang tersimpan ke database (dikirim/diterima lewat 0x3003/0x3004). */
    public final CharStatus status;

    // ---- nilai turunan (dihitung ulang saat masuk, tidak disimpan) ----
    public long maxHp;
    public long maxMp;
    public int might;
    public int will;
    public int grace;
    public int armor;

    // ---- keadaan sesaat ----
    public boolean canMove = true;
    public boolean isWalking;
    public boolean paralyzed;
    public boolean blind;
    public int speed;
    public int direction;

    /** Peta & posisi tujuan saat berpindah, sebelum benar-benar sampai. */
    public int destMap = -1;
    public int destX;
    public int destY;

    public User(int fd, CharStatus status) {
        this.fd = fd;
        this.status = status;
        this.id = status.id;
        this.type = Type.PC;
        this.graphicId = status.disguise;
        this.graphicColor = status.disguiseColor;
    }

    /** Nama karakter — dipakai luas oleh log dan skrip. */
    public String name() {
        return status.name;
    }

    public boolean isGm() {
        return status.gmLevel > 0;
    }

    /**
     * Hitung ulang nilai turunan dari {@link CharStatus}.
     * Setara pc_calcstatus() di C, masih versi sederhana: rumus penuh
     * (bonus perlengkapan, buff, path) menyusul bersama port `pc.c`.
     */
    public void recalcStatus() {
        maxHp = status.baseHp;
        maxMp = status.baseMp;
        might = (int) status.baseMight;
        will = (int) status.baseWill;
        grace = (int) status.baseGrace;
        armor = status.baseArmor;

        if (status.hp > maxHp) {
            status.hp = maxHp;
        }
        if (status.mp > maxMp) {
            status.mp = maxMp;
        }
    }

    @Override
    public String toString() {
        return "User{" + status.name + " (id=" + id + ", lv " + status.level + ") @"
                + m + ":" + x + "," + y + "}";
    }
}
