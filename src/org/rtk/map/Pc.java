package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.map.data.MapData;
import org.rtk.map.data.MapRegistry;

/**
 * Port of map/pc.c — operasi terhadap pemain. Baru bagian penempatan dan
 * perpindahan peta; perhitungan pertarungan, barang, dan mantra menyusul.
 */
public final class Pc {

    private static final Logger log = LogManager.getLogger(Pc.class);

    private Pc() {
    }

    /**
     * pc_setpos(): setel posisi pemain, tanpa menyentuh indeks blok.
     *
     * Persis seperti versi C: fungsi ini hanya mengubah koordinat. Yang
     * mendaftarkan pemain ke peta adalah {@link #spawn} (di C:
     * {@code clif_spawn} → {@code map_addblock}). Pemisahan ini
     * dipertahankan karena beberapa alur memang menyetel posisi dulu, baru
     * menampilkan pemain belakangan.
     *
     * @return false bila id-nya ternyata milik mob (penjaga yang sama
     *         dengan {@code bl.id >= MOB_START_NUM} di C)
     */
    public static boolean setPos(User sd, int m, int x, int y) {
        if (sd.id >= User.MOB_START_NUM) {
            return false;
        }
        sd.m = m;
        sd.x = x;
        sd.y = y;
        sd.type = User.Type.PC;
        return true;
    }

    /**
     * clif_spawn(): daftarkan pemain ke indeks blok petanya sehingga ia
     * mulai terlihat oleh pemain lain.
     */
    public static boolean spawn(MapRegistry world, User sd) {
        MapData map = world.get(sd.m);
        if (map == null) {
            log.error("[PC] {} tidak bisa muncul: peta {} tidak dimuat di server ini", sd.name(), sd.m);
            return false;
        }
        if (!map.addBlock(sd)) {
            log.error("[PC] {} gagal masuk indeks peta {} di ({},{})", sd.name(), sd.m, sd.x, sd.y);
            return false;
        }
        return true;
    }

    /** clif_quit(): cabut pemain dari indeks blok petanya. */
    public static void despawn(MapRegistry world, User sd) {
        if (!sd.onMap) {
            return;
        }
        MapData map = world.get(sd.m);
        if (map != null) {
            map.delBlock(sd);
        }
    }

    /**
     * pc_warp(): pindahkan pemain ke peta/koordinat lain.
     *
     * Meniru urutan versi C: validasi & jepit koordinat, cabut dari peta
     * lama, setel posisi, daftarkan ke peta baru. Kaitan skrip
     * {@code mapLeave} / {@code mapEnter} ikut dipanggil bila petanya
     * berganti, karena banyak konten rtklua bergantung padanya.
     *
     * Nilai {@code x == -1} berarti tengah peta, sama seperti di C.
     *
     * @return false bila peta tujuan tidak dimuat di server ini. Versi C
     *         pada kasus ini memindahkan pemain ke map server lain
     *         (mencari `MapServer` di tabel `Maps`); perpindahan antar map
     *         server belum diport — lihat roadmap.
     */
    public static boolean warp(MapRegistry world, User sd, int m, int x, int y) {
        MapData dest = world.get(m);
        if (dest == null) {
            log.warn("[PC] {} minta pindah ke peta {} yang tidak dimuat di server ini "
                    + "(perpindahan antar map server belum diport)", sd.name(), m);
            return false;
        }

        if (x == -1) {
            x = dest.xs / 2;
            y = dest.ys / 2;
        }
        // jepit ke dalam batas peta, seperti versi C
        x = Math.max(0, Math.min(x, dest.xs - 1));
        y = Math.max(0, Math.min(y, dest.ys - 1));

        int oldMap = sd.m;
        boolean mapChanged = m != oldMap;

        if (mapChanged) {
            fireScript(sd, "mapLeave");
            if (dest.canMount == 0) {
                fireScript(sd, "onDismount");
            }
        }

        despawn(world, sd);
        setPos(sd, m, x, y);
        if (!spawn(world, sd)) {
            return false;
        }

        // gambar ulang dunia di sisi klien setelah berpindah
        Clif.refresh(sd);

        if (mapChanged) {
            fireScript(sd, "mapEnter");
        }
        log.debug("[PC] {} pindah ke {}:{},{}{}", sd.name(), m, x, y, mapChanged ? " (ganti peta)" : "");
        return true;
    }

    /**
     * Tempatkan pemain saat pertama masuk dunia — jalur setelah data
     * karakter tiba lewat 0x3803.
     *
     * Bila posisi tersimpannya tidak sah (peta tak dimuat atau petaknya
     * tembok), pemain dijatuhkan ke titik aman terdekat agar tidak
     * tersangkut di dalam dinding.
     */
    public static boolean enterWorld(MapRegistry world, User sd) {
        sd.recalcStatus();

        int m = sd.status.lastPos.m;
        int x = sd.status.lastPos.x;
        int y = sd.status.lastPos.y;

        MapData map = world.get(m);
        if (map == null) {
            log.warn("[PC] {} tersimpan di peta {} yang tidak dimuat di server ini", sd.name(), m);
            return false;
        }

        if (!map.walkable(x, y)) {
            int[] safe = findWalkableNear(map, x, y);
            if (safe == null) {
                log.error("[PC] {} tidak menemukan petak aman di peta {}", sd.name(), m);
                return false;
            }
            log.warn("[PC] {} tersimpan di petak terhalang ({},{}), dipindah ke ({},{})",
                    sd.name(), x, y, safe[0], safe[1]);
            x = safe[0];
            y = safe[1];
        }

        setPos(sd, m, x, y);
        if (!spawn(world, sd)) {
            return false;
        }
        // beri tahu klien: id, peta, posisi, waktu (clif.c saat pemain masuk)
        Clif.sendWorldEntry(sd);

        fireScript(sd, "mapEnter");
        log.info("[PC] {} masuk dunia di {} ({}:{},{}), vita {}/{} mana {}/{}",
                sd.name(), map.title, m, x, y, sd.status.hp, sd.maxHp, sd.status.mp, sd.maxMp);
        return true;
    }

    /**
     * Cari petak bisa dilewati terdekat dengan pencarian spiral.
     * Dipakai saat posisi tersimpan pemain ternyata terhalang — mis. karena
     * petanya diubah setelah ia terakhir keluar.
     */
    static int[] findWalkableNear(MapData map, int cx, int cy) {
        if (map.walkable(cx, cy)) {
            return new int[]{cx, cy};
        }
        int maxR = Math.max(map.xs, map.ys);
        for (int r = 1; r < maxR; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    // hanya periksa cincin terluar radius r
                    if (Math.abs(dx) != r && Math.abs(dy) != r) {
                        continue;
                    }
                    int nx = cx + dx;
                    int ny = cy + dy;
                    if (map.walkable(nx, ny)) {
                        return new int[]{nx, ny};
                    }
                }
            }
        }
        return null;
    }

    /** Panggil kaitan skrip global bila engine Lua aktif. */
    private static void fireScript(User sd, String event) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        try {
            MapServer.scriptEngine.doScript(event, event, MapServer.scriptEngine.playerRef(scriptPlayerOf(sd)));
        } catch (RuntimeException e) {
            log.error("[PC] kaitan skrip '{}' gagal untuk {}", event, sd.name(), e);
        }
    }

    /**
     * Jembatan sementara ke model pemain milik engine skrip.
     * TODO: satukan {@link User} dan {@code ScriptPlayer} supaya registry
     * skrip langsung menulis ke {@link org.rtk.common.mmo.CharStatus}
     * (butir roadmap "sambungkan registry skrip ke karakter").
     */
    static org.rtk.map.script.ScriptPlayer scriptPlayerOf(User sd) {
        org.rtk.map.script.ScriptPlayer p =
                new org.rtk.map.script.ScriptPlayer((int) sd.id, sd.status.name);
        p.level = sd.status.level;
        p.sex = sd.status.sex;
        p.path = sd.status.charClass;
        p.gmLevel = sd.status.gmLevel;
        p.m = sd.m;
        p.x = sd.x;
        p.y = sd.y;
        return p;
    }
}
