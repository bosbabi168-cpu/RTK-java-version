package org.rtk.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistem grup (party): {@code groups[MAX_GROUPS][MAX_GROUP_MEMBERS]} di C.
 *
 * <p>Port dari {@code clif_addgroup}, {@code clif_leavegroup},
 * {@code clif_updategroup} dan {@code clif_isingroup} di {@code clif.c}.
 * Penyimpanannya diganti daftar berurutan per grup; aturannya tidak.</p>
 *
 * <p>⚠️ <b>Satu sumber kebenaran.</b> Di C setiap anggota memegang salinan
 * sendiri {@code group_count} dan {@code group_leader}, disamakan berkala
 * oleh {@code clif_updategroup}; siapa pun yang lupa memanggilnya
 * meninggalkan anggota dengan hitungan basi yang lalu dipakai sebagai batas
 * loop atas {@code groups[gid][]} — persis resep membaca slot orang lain.
 * Di sini daftar anggotalah yang benar, dan {@code User.groupCount} /
 * {@code User.groupLeader} cuma cerminnya, ditulis hanya oleh kelas ini.</p>
 *
 * <p>⚠️ <b>Pemimpin selalu anggota indeks 0.</b> Bukan kebetulan: di C
 * {@code clif_leavegroup} menggeser daftar lalu menyetel
 * {@code group_leader = groups[gid][0]} bila yang keluar adalah pemimpin,
 * sehingga anggota pertama dan pemimpin tidak pernah berbeda. Skrip Lua
 * ikut bergantung pada ini — {@code slash.lua} membandingkan
 * {@code player.group[1]} sebagai identitas grup.</p>
 */
public final class Groups {

    private static final org.apache.logging.log4j.Logger log =
            org.apache.logging.log4j.LogManager.getLogger(Groups.class);

    /** {@code MAX_GROUPS} di map.h. Id 0 berarti "tidak bergrup". */
    public static final int MAX_GROUPS = 256;

    /** {@code MAX_GROUP_MEMBERS} di map.h. */
    public static final int MAX_ANGGOTA = 256;

    /** Jenis pesan {@code clif_sendmsg} untuk teks mini. */
    private static final int MSG_MINI = 3;

    /** Jenis pesan saluran grup ({@code clif_sendmsg(tsd, 11, ...)}). */
    static final int MSG_GRUP = 11;

    /** id grup -&gt; id karakter anggota, urut; indeks 0 adalah pemimpin. */
    private static final Map<Integer, List<Long>> isi = new ConcurrentHashMap<>();

    private Groups() {
    }

    /** Anggota sebuah grup, salinan hanya-baca; kosong bila grup tak ada. */
    public static List<Long> anggota(int groupId) {
        List<Long> l = isi.get(groupId);
        return l == null ? List.of() : List.copyOf(l);
    }

    /** {@code groups[gid][0]} — id pemimpin, 0 bila grup tidak ada. */
    public static long pemimpin(int groupId) {
        List<Long> l = isi.get(groupId);
        return l == null || l.isEmpty() ? 0L : l.get(0);
    }

    /** Banyak grup hidup; dipakai uji regresi. */
    public static int jumlahGrup() {
        return isi.size();
    }

    /** Buang seluruh grup — hanya untuk uji regresi. */
    public static void bersihkanUntukUji() {
        isi.clear();
    }

    /**
     * clif_isingroup(): {@code tsd} satu grup dengan {@code sd}?
     *
     * <p>⚠️ Di C ini <b>tidak</b> mengembalikan true untuk diri sendiri
     * kecuali kebetulan ikut terdaftar. Pemanggilnya (uji stealth) memang
     * sudah menyaring {@code sd->bl.id != src_sd->bl.id} lebih dulu.</p>
     */
    public static boolean seGrup(User sd, User tsd) {
        if (sd == null || tsd == null || sd.groupId == 0) {
            return false;
        }
        return anggota(sd.groupId).contains(tsd.status.id);
    }

    /**
     * Daftar id untuk {@code player.group} di Lua.
     *
     * <p>⚠️ <b>Pemain sendirian menjawab daftar berisi dirinya</b>, bukan
     * daftar kosong ({@code sl.c}, cabang {@code else} di binding
     * {@code group}). Empat belas skrip memutari daftar ini —
     * {@code exp.lua} membagi pengalaman lewatnya — dan daftar kosong
     * membuat pemain sendirian tidak mendapat apa-apa, diam-diam.</p>
     */
    public static List<Long> daftarSkrip(User sd) {
        List<Long> l = isi.get(sd.groupId);
        if (sd.groupId != 0 && l != null && !l.isEmpty()) {
            return List.copyOf(l);
        }
        return List.of(sd.status.id);
    }

    /** Anggota yang sedang online; anggota offline dilewati seperti di C. */
    public static List<User> anggotaOnline(User sd) {
        List<User> keluar = new ArrayList<>();
        for (long id : anggota(sd.groupId)) {
            User u = MapServer.userById(id);
            if (u != null) {
                keluar.add(u);
            }
        }
        return keluar;
    }

    private static void mini(User sd, String teks) {
        MapServer.clientView.messageToPlayer(sd, MSG_MINI, teks);
    }

    /**
     * clif_addgroup(): ajak {@code nama} masuk grup — atau keluarkan dia,
     * bila dia sudah anggota grup yang <b>saya</b> pimpin.
     *
     * <p>Urutan penolakan dipertahankan persis seperti C, karena pesannya
     * berbeda-beda dan pemain memakainya untuk menebak apa yang salah.</p>
     */
    public static void tambah(User sd, String nama) {
        User tsd = MapServer.userByName(nama);
        if (tsd == null) {
            return;   // nullpo_ret: diam saja, seperti aslinya
        }
        if (!sd.isGm() && (tsd.optFlags & User.OPT_STEALTH) != 0) {
            return;
        }
        if (tsd.status.id == sd.status.id) {
            mini(sd, "You can't group yourself...");
            return;
        }
        // Tendang: hanya pemimpin, dan hanya anggota grupnya sendiri.
        if (tsd.groupId != 0 && tsd.groupId == sd.groupId
                && sd.groupLeader == sd.status.id) {
            keluar(tsd);
            return;
        }
        if (sd.groupCount >= MAX_ANGGOTA) {
            mini(sd, "Your group is already full.");
            return;
        }
        // ⚠️ Hantu boleh mengajak yang hidup, tapi yang hidup tidak boleh
        // mengajak hantu — pemeriksaannya memang hanya pada yang diajak.
        if (tsd.status.state == 1) {
            mini(sd, "They are unable to join your party.");
            return;
        }
        if (!bolehBergrupDiPeta(sd)) {
            mini(sd, "You are unable to join a party. (Grouping disabled on map)");
            return;
        }
        if (!bolehBergrupDiPeta(tsd)) {
            mini(sd, "They are unable to join your party. (Grouping disabled on map)");
            return;
        }
        if ((tsd.status.settingFlags & org.rtk.common.mmo.SettingFlags.GROUP) == 0) {
            mini(sd, "They have refused to join your party.");
            return;
        }
        if (tsd.groupId != 0) {
            mini(sd, "They have refused to join your party.");
            return;
        }

        if (sd.groupId == 0) {
            int gid = slotKosong();
            if (gid == 0) {
                mini(sd, "All groups are currently occupied, please try again later.");
                return;
            }
            List<Long> baru = new ArrayList<>();
            baru.add(sd.status.id);
            baru.add(tsd.status.id);
            isi.put(gid, baru);
            sd.groupId = gid;
            tsd.groupId = gid;
        } else {
            isi.get(sd.groupId).add(tsd.status.id);
            tsd.groupId = sd.groupId;
        }

        segarkan(sd.groupId, tsd.name() + " is joining the group.");
    }

    /**
     * clif_leavegroup(): keluarkan {@code sd} dari grupnya.
     *
     * <p>Aman dipanggil pada pemain yang tidak bergrup — jalur logout di C
     * memanggilnya tanpa syarat.</p>
     */
    public static void keluar(User sd) {
        int gid = sd.groupId;
        List<Long> daftar = isi.get(gid);
        if (gid == 0 || daftar == null) {
            lepas(sd);
            return;
        }
        daftar.remove(Long.valueOf(sd.status.id));

        // Grup beranggota satu bukan grup lagi. Di C ini dikerjakan
        // clif_updategroup lewat cabang `tsd->group_count == 1`.
        if (daftar.size() <= 1) {
            for (long id : List.copyOf(daftar)) {
                User sisa = MapServer.userById(id);
                if (sisa != null) {
                    lepas(sisa);
                    kabari(sisa, sd.name() + " is leaving the group.");
                }
            }
            isi.remove(gid);
        } else {
            segarkan(gid, sd.name() + " is leaving the group.");
        }

        lepas(sd);
        mini(sd, "You have left the group.");
        MapServer.clientView.groupStatusChanged(sd);
    }

    /** Lepaskan cermin grup pada satu pemain, tanpa menyentuh daftar. */
    private static void lepas(User sd) {
        sd.groupId = 0;
        sd.groupCount = 0;
        sd.groupLeader = 0;
    }

    /**
     * clif_updategroup(): samakan cermin tiap anggota lalu kirim ulang
     * jendela grup dan darahnya.
     */
    private static void segarkan(int gid, String pesan) {
        List<Long> daftar = isi.get(gid);
        if (daftar == null) {
            return;
        }
        long bos = daftar.isEmpty() ? 0L : daftar.get(0);
        for (long id : List.copyOf(daftar)) {
            User tsd = MapServer.userById(id);
            if (tsd == null) {
                continue;
            }
            tsd.groupId = gid;
            tsd.groupCount = daftar.size();
            tsd.groupLeader = bos;
            kabari(tsd, pesan);
        }
    }

    private static void kabari(User tsd, String pesan) {
        if (pesan != null) {
            mini(tsd, pesan);
        }
        MapServer.clientView.groupHealthChanged(tsd);
        MapServer.clientView.groupStatusChanged(tsd);
    }

    /** Kirim ulang jendela grup ke satu anggota, tanpa pesan mini. */
    public static void kirimUlang(User sd) {
        MapServer.clientView.groupStatusChanged(sd);
    }

    /**
     * pc.c: darah grup disegarkan tiap detak selama pemain bergrup.
     * Aman dipanggil untuk pemain sendirian — langsung pulang.
     */
    public static void detak(User sd) {
        if (sd.groupId == 0 || sd.groupCount == 0) {
            return;
        }
        MapServer.clientView.groupHealthChanged(sd);
    }

    private static boolean bolehBergrupDiPeta(User sd) {
        org.rtk.map.data.MapData md = MapServer.world.get(sd.m);
        return md == null || md.canGroup != 0;
    }

    /**
     * Slot grup bebas pertama; 0 bila penuh.
     *
     * <p>⚠️ Mulai dari 1, bukan 0 — {@code groupid == 0} adalah penanda
     * "tidak bergrup", jadi slot 0 tidak boleh terpakai.</p>
     */
    private static int slotKosong() {
        for (int x = 1; x < MAX_GROUPS; x++) {
            if (!isi.containsKey(x)) {
                return x;
            }
        }
        log.warn("[GRUP] seluruh {} slot grup terpakai", MAX_GROUPS - 1);
        return 0;
    }
}
