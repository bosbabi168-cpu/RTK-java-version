package org.rtk.map.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Uji fondasi dunia map server: indeks spasial {@link MapData} di atas peta
 * asli — menempatkan benda, memindahkannya, dan menanyakan area pandang.
 *
 * Jalankan: {@code ./run.sh worldtest}
 */
public final class MapWorldTest {

    private static final Logger log = LogManager.getLogger(MapWorldTest.class);
    private static int failures;

    private MapWorldTest() {
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException e) {
            log.error("gagal", e);
            System.exit(1);
        }
        if (failures == 0) {
            log.info("ALL WORLD TESTS PASSED");
        } else {
            log.error("{} TEST GAGAL", failures);
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        String root = args.length > 0 ? args[0] : "maps";
        Path kugnae = Paths.get(root, "TK000000.map");
        if (!Files.isRegularFile(kugnae)) {
            log.error("peta contoh tidak ditemukan: {}", kugnae);
            System.exit(1);
        }

        MapData map = new MapData(0, MapFile.load(kugnae));
        map.title = "Kugnae";
        log.info("=== {} ===", map);

        // ---- geometri ----
        check("ukuran peta terbaca", map.xs == 220 && map.ys == 220);
        check("di luar batas ditolak", !map.inBounds(-1, 0) && !map.inBounds(map.xs, 0));
        int walk = 0;
        int block = 0;
        for (int y = 0; y < map.ys; y++) {
            for (int x = 0; x < map.xs; x++) {
                if (map.walkable(x, y)) {
                    walk++;
                } else {
                    block++;
                }
            }
        }
        log.info("petak bisa dilewati {} / terhalang {}", walk, block);
        check("ada petak bisa dilewati dan ada tembok", walk > 0 && block > 0);
        check("total petak konsisten", walk + block == map.xs * map.ys);

        // ---- indeks spasial ----
        BlockList player = entity(1, BlockList.Type.PC, 100, 100);
        check("pemain masuk peta", map.addBlock(player));
        check("hitungan pemain naik", map.users == 1);
        check("pemain tidak bisa masuk dua kali", !map.addBlock(player));
        check("koordinat blok benar (100/8 = 12)", player.bx == 12 && player.by == 12);

        BlockList mob = entity(2, BlockList.Type.MOB, 101, 100);
        map.addBlock(mob);
        check("mob tidak menambah hitungan pemain", map.users == 1);
        check("dua benda terdaftar", map.blockCount() == 2);

        check("objectsAt menemukan pemain", map.objectsAt(100, 100).contains(player));
        check("objectsAt petak kosong", map.objectsAt(150, 150).isEmpty());

        // ---- area pandang ----
        List<BlockList> seen = new ArrayList<>();
        map.foreachInArea(100, 100, null, seen::add);
        check("area pandang melihat keduanya", seen.size() == 2);

        seen.clear();
        map.foreachInArea(100, 100, BlockList.Type.MOB, seen::add);
        check("saring jenis: hanya mob", seen.size() == 1 && seen.get(0) == mob);

        // benda jauh tidak terlihat (area hanya x+-9, y+-8)
        BlockList far = entity(3, BlockList.Type.PC, 130, 100);
        map.addBlock(far);
        seen.clear();
        map.foreachInArea(100, 100, null, seen::add);
        check("benda 30 petak jauhnya tidak terlihat", !seen.contains(far));

        // tepat di batas: x+9 harus terlihat, x+10 tidak
        BlockList edgeIn = entity(4, BlockList.Type.PC, 109, 100);
        BlockList edgeOut = entity(5, BlockList.Type.PC, 110, 100);
        map.addBlock(edgeIn);
        map.addBlock(edgeOut);
        seen.clear();
        map.foreachInArea(100, 100, null, seen::add);
        check("batas area x+9 terlihat", seen.contains(edgeIn));
        check("batas area x+10 tidak terlihat", !seen.contains(edgeOut));

        // ---- perpindahan ----
        map.moveBlock(player, 101, 101);
        check("pindah dalam blok sama", player.x == 101 && player.y == 101 && player.bx == 12);
        map.moveBlock(player, 40, 40);
        check("pindah lintas blok memperbarui indeks", player.bx == 5 && player.by == 5);
        check("pemain ditemukan di lokasi baru", map.objectsAt(40, 40).contains(player));
        check("lokasi lama sudah kosong dari pemain", !map.objectsAt(101, 101).contains(player));
        check("jumlah benda tetap setelah pindah", map.blockCount() == 5);

        // ---- keluar peta ----
        map.delBlock(player);
        check("pemain keluar peta", !player.onMap && map.users == 3);
        check("benda berkurang", map.blockCount() == 4);
        check("keluar dua kali tidak berpengaruh", !map.delBlock(player));

        // ---- tepi peta: area digeser, bukan dipotong ----
        BlockList corner = entity(6, BlockList.Type.PC, 0, 0);
        map.addBlock(corner);
        seen.clear();
        map.foreachInArea(0, 0, null, seen::add);
        check("benda di pojok tetap terlihat dari pojok", seen.contains(corner));

        // ---- registry: geometri yang sama dipakai ulang ----
        MapRegistry reg = new MapRegistry();
        List<String> files = List.of("TK000000.map", "TK000001.map", "TK000000.map");
        int n = reg.loadFromFiles(root, files);
        check("registry memuat 3 peta", n == 3);
        check("MapId 0 dan 2 memakai berkas sama tapi objek berbeda",
                reg.get(0) != reg.get(2) && reg.get(0).xs == reg.get(2).xs);
        check("peta tak dikenal mengembalikan null", reg.get(9999) == null && !reg.isLoaded(9999));

        playerTest(root);
    }

    /** Uji User + pc_setpos / warp / enterWorld di atas peta asli. */
    private static void playerTest(String root) throws IOException {
        log.info("=== pemain (User + pc_setpos) ===");

        MapRegistry world = new MapRegistry();
        world.loadFromFiles(root, List.of("TK000000.map", "TK000001.map"));
        MapData kugnae = world.get(0);

        org.rtk.common.mmo.CharStatus st = new org.rtk.common.mmo.CharStatus();
        st.id = 77;
        st.name = "Pemain";
        st.level = 50;
        st.baseHp = 5000;
        st.hp = 9999;      // lebih besar dari maksimum: harus dijepit
        st.baseMp = 2000;
        st.mp = 100;
        st.baseMight = 120;

        // cari petak yang benar-benar bisa dilewati untuk titik awal
        int sx = -1;
        int sy = -1;
        for (int y = 0; y < kugnae.ys && sx < 0; y++) {
            for (int x = 0; x < kugnae.xs; x++) {
                if (kugnae.walkable(x, y)) {
                    sx = x;
                    sy = y;
                    break;
                }
            }
        }
        st.lastPos = new org.rtk.common.mmo.Point(0, sx, sy);

        org.rtk.map.User sd = new org.rtk.map.User(5, st);
        check("User membawa id & nama dari CharStatus", sd.id == 77 && sd.name().equals("Pemain"));
        check("User adalah BlockList bertipe PC", sd.type == BlockList.Type.PC);

        check("masuk dunia berhasil", org.rtk.map.Pc.enterWorld(world, sd));
        check("terdaftar di indeks peta", sd.onMap && kugnae.users == 1);
        check("posisi sesuai data tersimpan", sd.m == 0 && sd.x == sx && sd.y == sy);
        check("nilai turunan dihitung (maxHp dari baseHp)", sd.maxHp == 5000 && sd.might == 120);
        check("vita dijepit ke maksimum (9999 -> 5000)", sd.status.hp == 5000);

        // pc_setpos tidak menyentuh indeks — sesuai versi C
        int beforeCount = kugnae.blockCount();
        org.rtk.map.Pc.setPos(sd, 0, sx + 1, sy);
        check("pc_setpos mengubah koordinat", sd.x == sx + 1);
        check("pc_setpos TIDAK mengubah indeks blok", kugnae.blockCount() == beforeCount);
        org.rtk.map.Pc.setPos(sd, 0, sx, sy); // kembalikan

        // warp dalam peta yang sama
        int tx = -1;
        int ty = -1;
        for (int y = kugnae.ys - 1; y >= 0 && tx < 0; y--) {
            for (int x = kugnae.xs - 1; x >= 0; x--) {
                if (kugnae.walkable(x, y)) {
                    tx = x;
                    ty = y;
                    break;
                }
            }
        }
        check("warp dalam peta sama", org.rtk.map.Pc.warp(world, sd, 0, tx, ty));
        check("posisi & indeks ikut pindah",
                sd.x == tx && sd.y == ty && kugnae.objectsAt(tx, ty).contains(sd));
        check("pemain tetap satu (tidak terduplikasi)", kugnae.users == 1);

        // warp ke tengah peta dengan x = -1
        check("warp x=-1 ke tengah peta", org.rtk.map.Pc.warp(world, sd, 0, -1, -1));
        check("koordinat tengah benar", sd.x == kugnae.xs / 2 && sd.y == kugnae.ys / 2);

        // koordinat di luar batas harus dijepit, bukan error
        check("warp di luar batas dijepit", org.rtk.map.Pc.warp(world, sd, 0, 99999, 99999));
        check("terjepit ke tepi peta", sd.x == kugnae.xs - 1 && sd.y == kugnae.ys - 1);

        // pindah peta
        MapData other = world.get(1);
        check("warp lintas peta", org.rtk.map.Pc.warp(world, sd, 1, 1, 1));
        check("keluar dari peta lama", kugnae.users == 0);
        check("masuk ke peta baru", other.users == 1 && sd.m == 1);

        // peta yang tidak dimuat harus ditolak, bukan crash
        check("warp ke peta tak dimuat ditolak", !org.rtk.map.Pc.warp(world, sd, 9999, 1, 1));
        check("pemain tetap di peta lama setelah warp gagal", sd.m == 1 && other.users == 1);

        // tersimpan di dalam tembok -> dipindah ke petak aman
        int wx = -1;
        int wy = -1;
        for (int y = 0; y < kugnae.ys && wx < 0; y++) {
            for (int x = 0; x < kugnae.xs; x++) {
                if (!kugnae.walkable(x, y)) {
                    wx = x;
                    wy = y;
                    break;
                }
            }
        }
        org.rtk.common.mmo.CharStatus stuck = new org.rtk.common.mmo.CharStatus();
        stuck.id = 78;
        stuck.name = "Tersangkut";
        stuck.baseHp = 100;
        stuck.lastPos = new org.rtk.common.mmo.Point(0, wx, wy);
        org.rtk.map.User sd2 = new org.rtk.map.User(6, stuck);
        check("pemain di dalam tembok tetap bisa masuk", org.rtk.map.Pc.enterWorld(world, sd2));
        check("dipindah ke petak yang bisa dilewati", kugnae.walkable(sd2.x, sd2.y));

        // keluar dunia
        org.rtk.map.Pc.despawn(world, sd2);
        check("keluar dunia melepas dari indeks", !sd2.onMap && kugnae.users == 0);
    }

    private static BlockList entity(long id, BlockList.Type type, int x, int y) {
        BlockList b = new BlockList();
        b.id = id;
        b.type = type;
        b.x = x;
        b.y = y;
        return b;
    }
}
