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
    /**
     * pc_checklevel(): panggil kait {@code onLevel} sekali untuk tiap ambang
     * pengalaman yang sudah terlampaui.
     *
     * <p>⚠️ Kenaikan levelnya <b>seluruhnya sisi skrip</b> — di sini tidak
     * ada satu pun penulisan ke {@code status.level}. Karena itu pula
     * perulangannya berjalan sampai level tertinggi dan bisa memanggil kait
     * itu berkali-kali dalam satu panggilan: skripnyalah yang menaikkan
     * level satu per satu.</p>
     *
     * <p>⚠️ {@code path} diambil dari {@code classdb_path} hanya bila id
     * kelasnya di atas 5 — lima id pertama <b>adalah</b> jalurnya sendiri.</p>
     */
    public static void checkLevel(org.rtk.map.script.ScriptEngine engine, User sd) {
        if (engine == null || sd == null) {
            return;
        }
        int path = sd.status.charClass;
        if (path > 5) {
            path = MapServer.classDb.pathOf(path);
        }
        var ref = engine.playerRef(sd.scriptPlayer());
        for (int lv = sd.status.level; lv < org.rtk.map.data.ClassDb.MAX_LEVEL; lv++) {
            if (sd.status.exp >= MapServer.classDb.level(path, lv)) {
                engine.doScript("onLevel", null, ref);
            }
        }
    }

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
     * Serahkan pemain ke map server yang memuat peta tujuannya (R3/C3) —
     * port cabang {@code !map_isloaded} di {@code pc_warp}.
     *
     * <p>⚠️ Koordinatnya dijepit ke 1..255 <b>di sini</b>, sebelum dikirim.
     * Server tujuan memuat pemain dari {@code lastPos} dan tidak punya
     * kesempatan memperbaiki nilai yang mustahil; C melakukan hal yang sama
     * dengan komentar "Just for Justin".</p>
     *
     * @return false bila tabel {@code Maps} tidak mengenal peta itu sama
     *         sekali — barulah perpindahannya benar-benar gagal
     */
    private static boolean transferKeServerLain(User sd, int m, int x, int y) {
        Integer server = MapServer.sql == null || !MapServer.sql.siap() ? null
                : MapServer.sql.queryInt(
                        "SELECT `MapServer` FROM `Maps` WHERE `MapId` = ?", m);
        if (server == null) {
            log.warn("[PC] {} minta pindah ke peta {} yang tidak dikenal tabel Maps",
                    sd.name(), m);
            return false;
        }
        if (server == MapServer.serverId) {
            // Petanya milik server ini tapi tidak termuat — berkasnya hilang
            // atau gagal dibaca. Mengalihkan pemain ke diri sendiri hanya
            // membuat gelung sambung-putus.
            log.error("[PC] peta {} milik server ini tapi TIDAK termuat; "
                    + "{} tidak dipindahkan", m, sd.name());
            return false;
        }
        int tx = x < 1 || x > 255 ? 1 : x;
        int ty = y < 1 || y > 255 ? 1 : y;
        sd.destMap = m;
        sd.destX = tx;
        sd.destY = ty;
        String host = MapServer.mapIpS;
        int port = MapServer.PORT_MAP_DASAR + server;
        log.info("[PC] {} dialihkan ke map server #{} ({}:{}) untuk peta {} ({},{})",
                sd.name(), server, host, port, m, tx, ty);
        MapServer.clientView.playerTransferred(sd, host, port, m, tx, ty);
        return true;
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
     * <p>Peta yang <b>tidak dimuat di server ini</b> ditangani sebagai
     * perpindahan antar map server (R3/C3): server pemiliknya dicari di
     * kolom {@code Maps.MapServer}, tujuannya dicatat di
     * {@code destMap/destX/destY}, lalu klien dialihkan. Posisinya sampai
     * ke server tujuan lewat jalur simpan — {@code MapIntif.saveChar}
     * menuliskan tujuan itu sebagai {@code lastPos}.</p>
     *
     * @return false hanya bila peta tujuan tidak dimuat di sini DAN tidak
     *         ada map server yang mengakuinya
     */
    public static boolean warp(MapRegistry world, User sd, int m, int x, int y) {
        MapData dest = world.get(m);
        if (dest == null) {
            return transferKeServerLain(sd, m, x, y);
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
        MapServer.clientView.playerViewRefreshed(sd);

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
            // R3/C3: posisi tersimpannya milik map server LAIN — serahkan
            // pemainnya ke sana, jangan usir.
            // ⚠️ Di C cabang ini KOSONG (`intif.c:215`, isinya dikomentari),
            // sehingga pemain yang tersimpan di peta server lain lalu masuk
            // ke server yang salah akan terdampar: tidak bisa masuk dunia,
            // dan posisinya tidak pernah berubah sehingga percobaan
            // berikutnya gagal dengan cara yang sama. Menutup sambungan
            // tanpa memberi tahu ke mana harus pergi adalah jalan buntu.
            log.warn("[PC] {} tersimpan di peta {} milik map server lain", sd.name(), m);
            if (transferKeServerLain(sd, m, x, y)) {
                sd.pindahTertunda = true;
            }
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
        MapServer.clientView.playerEnteredWorld(sd);

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
     * Objek pemain milik mesin skrip — satu per pemain, bukan salinan baru
     * tiap panggilan (lihat {@link User#scriptPlayer()}).
     *
     * <p>Registry yang ditulis skrip mengalir langsung ke
     * {@link org.rtk.common.mmo.CharStatus}: keduanya <b>objek yang sama</b>,
     * disambungkan sekali lewat {@code ScriptPlayer.bindRegistries()}
     * (Trek C1, selesai 21 Agustus 2026). Terbukti ujung-ke-ujung di
     * {@code dbtest}.</p>
     */
    static org.rtk.map.script.ScriptPlayer scriptPlayerOf(User sd) {
        return sd.scriptPlayer();
    }
}
