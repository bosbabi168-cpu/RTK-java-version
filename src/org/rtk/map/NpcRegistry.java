package org.rtk.map;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.map.data.BlockList;
import org.rtk.map.data.MapData;
import org.rtk.map.data.MapRegistry;

/**
 * Port of npc_init() (map/npc.c) — muat NPC dari tabel
 * {@code NPCs<serverId>} lalu tempatkan di peta.
 *
 * <p>Sekaligus menyediakan padanan {@code id_db} / {@code map_id2bl()} di C:
 * indeks id &rarr; benda, yang dipakai saat klien mengirim id objek yang
 * diklik pemain (opcode 0x43). Indeks itu <b>bukan</b> indeks spasial
 * ({@link MapData} punya sendiri); keduanya melayani pertanyaan berbeda —
 * "siapa di sekitar sini" vs "benda mana yang ber-id ini".</p>
 */
public final class NpcRegistry {

    private static final Logger log = LogManager.getLogger(NpcRegistry.class);

    /** Semua NPC yang dimuat, urut NpcId. */
    private final List<Npc> npcs = new ArrayList<>();

    /** map_id2bl(): id blok -&gt; benda. */
    private final Map<Long, BlockList> byId = new LinkedHashMap<>();

    /** Nama skrip -&gt; NPC pertama dengan nama itu (dipakai skrip Lua). */
    private final Map<String, Npc> byName = new LinkedHashMap<>();

    /** map_id2bl(): cari benda dari id blok, atau null. */
    public BlockList byId(long id) {
        return byId.get(id);
    }

    /** Daftarkan benda apa pun ke indeks id (map_addiddb). */
    public void register(BlockList bl) {
        byId.put(bl.id, bl);
    }

    /** Cabut dari indeks id (map_deliddb). */
    public void unregister(BlockList bl) {
        byId.remove(bl.id);
    }

    public Npc byName(String name) {
        return byName.get(name);
    }

    public List<Npc> all() {
        return npcs;
    }

    public int count() {
        return npcs.size();
    }

    /**
     * Muat seluruh NPC milik server ini dan tempatkan ke peta.
     *
     * <p>Kolom yang dibaca mengikuti {@code npc_init()} persis. NPC yang
     * petanya tidak dimuat di server ini dilewati — sama seperti portal,
     * itu urusan map server lain (roadmap C3).</p>
     *
     * @return jumlah NPC yang berhasil dimuat
     */
    public int load(Sql sql, int serverId, MapRegistry world) {
        long t0 = System.currentTimeMillis();
        npcs.clear();
        byName.clear();

        int[] stat = new int[3]; // 0=dimuat, 1=peta tak dimuat, 2=gagal masuk indeks
        int rows = sql.forEachRow(
                "SELECT `NpcId`, `NpcIdentifier`, `NpcDescription`, `NpcType`, `NpcMapId`, "
                + "`NpcX`, `NpcY`, `NpcLook`, `NpcLookColor`, `NpcTimer`, `NpcSex`, `NpcSide`, "
                + "`NpcState`, `NpcFace`, `NpcFaceColor`, `NpcHair`, `NpcHairColor`, "
                + "`NpcSkinColor`, `NpcIsChar`, `NpcIsF1Npc`, `NpcIsRepairNpc`, `NpcIsShopNpc`, "
                + "`NpcIsBankNpc`, `NpcReturnDistance`, `NpcMoveTime`, `NpcCanReceiveItem` "
                + "FROM `NPCs" + serverId + "` ORDER BY `NpcId`",
                rs -> {
                    Npc nd = new Npc();
                    nd.npcId = rs.getLong("NpcId");
                    nd.name = str(rs.getString("NpcIdentifier"));
                    nd.displayName = str(rs.getString("NpcDescription"));
                    nd.subtype = rs.getInt("NpcType");
                    nd.m = rs.getInt("NpcMapId");
                    nd.x = rs.getInt("NpcX");
                    nd.y = rs.getInt("NpcY");
                    nd.graphicId = rs.getLong("NpcLook");
                    nd.graphicColor = rs.getLong("NpcLookColor");
                    nd.actionTime = rs.getLong("NpcTimer");
                    nd.sex = rs.getInt("NpcSex");
                    nd.side = rs.getInt("NpcSide");
                    nd.state = rs.getInt("NpcState");
                    nd.face = rs.getInt("NpcFace");
                    nd.faceColor = rs.getInt("NpcFaceColor");
                    nd.hair = rs.getInt("NpcHair");
                    nd.hairColor = rs.getInt("NpcHairColor");
                    nd.skinColor = rs.getInt("NpcSkinColor");
                    nd.npcType = rs.getInt("NpcIsChar");
                    nd.f1Npc = rs.getInt("NpcIsF1Npc") == 1;
                    nd.repairNpc = rs.getInt("NpcIsRepairNpc") != 0;
                    nd.shopNpc = rs.getInt("NpcIsShopNpc") != 0;
                    nd.bankNpc = rs.getInt("NpcIsBankNpc") != 0;
                    nd.returnDistance = rs.getInt("NpcReturnDistance");
                    nd.moveTime = rs.getLong("NpcMoveTime");
                    nd.canReceiveItem = rs.getInt("NpcCanReceiveItem") != 0;

                    nd.startM = nd.m;
                    nd.startX = nd.x;
                    nd.startY = nd.y;
                    nd.id = nd.f1Npc ? Npc.F1_NPC : Npc.blockIdFor(nd.npcId);

                    npcs.add(nd);
                });

        if (rows < 0) {
            log.error("[NPC] gagal membaca tabel NPCs{}", serverId);
            return 0;
        }

        for (Npc nd : npcs) {
            byId.put(nd.id, nd);
            byName.putIfAbsent(nd.name, nd);

            if (!nd.occupiesTile()) {
                stat[0]++;
                continue; // ada di indeks id saja, tidak menempati petak
            }
            MapData map = world.get(nd.m);
            if (map == null) {
                stat[1]++;
                continue;
            }
            if (!map.addBlock(nd)) {
                stat[2]++;
                if (stat[2] <= 5) {
                    log.warn("[NPC] {} gagal masuk indeks peta {} di ({},{})",
                            nd.name, nd.m, nd.x, nd.y);
                }
                continue;
            }
            stat[0]++;
        }

        loadEquipment(sql, serverId);

        log.info("[NPC] {} NPC dimuat dari {} baris `NPCs{}` ({} peta tak dimuat, "
                + "{} gagal ditempatkan) dalam {} ms",
                stat[0], rows, serverId, stat[1], stat[2], System.currentTimeMillis() - t0);
        return stat[0];
    }

    /**
     * Bagian kedua {@code npc_init()}: perlengkapan NPC yang tampil sebagai
     * karakter ({@code NpcIsChar == 1}).
     *
     * <p>Di C dibaca satu NPC per query di dalam loop; di sini satu query
     * untuk semuanya lalu dipetakan — hasilnya sama, tapi tidak menembak
     * database ratusan kali.</p>
     */
    private void loadEquipment(Sql sql, int serverId) {
        java.util.Map<Long, Npc> byNpcId = new java.util.HashMap<>();
        for (Npc nd : npcs) {
            byNpcId.put(nd.npcId, nd);
        }

        int[] n = {0};
        int rows = sql.forEachRow(
                "SELECT `NeqNpcId`,`NeqLook`,`NeqColor`,`NeqSlot` FROM `NPCEquipment" + serverId + "`",
                rs -> {
                    Npc nd = byNpcId.get(rs.getLong("NeqNpcId"));
                    int slot = rs.getInt("NeqSlot");
                    if (nd == null || slot < 0 || slot >= org.rtk.map.data.Equip.COUNT) {
                        return;
                    }
                    org.rtk.common.mmo.Item it = new org.rtk.common.mmo.Item();
                    it.id = rs.getLong("NeqLook");
                    it.customLookColor = rs.getLong("NeqColor");
                    it.amount = 1;   // C mengikat kolom '1' sebagai amount
                    it.pos = slot;
                    nd.equip[slot] = it;
                    n[0]++;
                });
        if (rows < 0) {
            log.warn("[NPC] tabel NPCEquipment{} tidak terbaca — NPC berperlengkapan "
                    + "akan tampil polos", serverId);
            return;
        }
        log.info("[NPC] {} perlengkapan NPC dimuat dari {} baris", n[0], rows);
    }

    /** Id berikutnya untuk NPC sementara. */
    private long nextTempId = Npc.NPCT_START_NUM;

    /**
     * bll_addnpc(): lahirkan NPC sementara dari skrip.
     *
     * <p>Dipakai 54× oleh konten — jebakan, dekorasi, dan NPC event.
     * Idnya diambil dari rentang terpisah ({@link Npc#NPCT_START_NUM}) agar
     * tidak bertabrakan dengan NPC tetap dari tabel, dan NPC-nya langsung
     * masuk indeks blok <b>dan</b> indeks id.</p>
     *
     * <p>Setelah lahir, kait skrip {@code on_spawn} dipanggil — persis
     * seperti di C.</p>
     *
     * @return NPC yang baru lahir, atau null bila petanya tidak dimuat
     */
    public Npc addTemp(org.rtk.map.script.ScriptEngine engine, MapRegistry world,
                       String name, int m, int x, int y, int subtype,
                       long actionTime, long duration, long ownerId, long moveTime,
                       String displayName) {
        MapData map = world.get(m);
        if (map == null) {
            log.warn("[NPC] skrip melahirkan '{}' di peta {} yang tidak dimuat", name, m);
            return null;
        }

        Npc nd = new Npc();
        nd.name = name == null ? "" : name;
        nd.displayName = displayName == null || displayName.isEmpty() ? "nothing" : displayName;
        nd.subtype = subtype;
        nd.m = m;
        nd.x = Math.min(Math.max(x, 0), map.xs - 1);
        nd.y = Math.min(Math.max(y, 0), map.ys - 1);
        nd.startM = nd.m;
        nd.startX = nd.x;
        nd.startY = nd.y;
        nd.actionTime = actionTime;
        nd.duration = duration;
        nd.ownerId = ownerId;
        nd.moveTime = moveTime;
        nd.temporary = true;
        nd.id = nextTempId++;

        if (nd.occupiesTile() && !map.addBlock(nd)) {
            log.warn("[NPC] '{}' gagal masuk indeks peta {} di ({},{})", nd.name, m, nd.x, nd.y);
            return null;
        }
        npcs.add(nd);
        byId.put(nd.id, nd);
        byName.putIfAbsent(nd.name, nd);

        if (engine != null) {
            fire(engine, nd, "on_spawn");
        }
        return nd;
    }

    /**
     * Cabut NPC sementara dari dunia. NPC tetap dari tabel tidak boleh
     * dihapus lewat sini — mereka hidup selama server hidup.
     */
    public boolean removeTemp(Npc nd, MapRegistry world) {
        if (nd == null || !nd.temporary) {
            return false;
        }
        MapData map = world.get(nd.m);
        if (map != null && nd.onMap) {
            map.delBlock(nd);
        }
        byId.remove(nd.id);
        return npcs.remove(nd);
    }

    /**
     * npc_runtimers(): satu tik timer NPC — dipanggil tiap 100 ms.
     *
     * <p>Pola penghitungnya ditiru dari C: setiap tik menambah 100 ms ke
     * penghitung, dan begitu melewati ambangnya skrip dipanggil lalu
     * penghitungnya <b>direset ke 0</b> (bukan dikurangi ambang), sehingga
     * jeda berikutnya dihitung dari saat skrip jalan.</p>
     *
     * <p>NPC dengan {@code moveTime}/{@code actionTime} bernilai 0 dilewati
     * — itu penanda "tidak berjalan sendiri", bukan "jalan secepat mungkin".</p>
     *
     * @param engine mesin skrip; null berarti tidak ada yang bisa dipanggil
     * @return jumlah skrip yang terpicu pada tik ini
     */
    public int runTimers(org.rtk.map.script.ScriptEngine engine) {
        if (engine == null) {
            return 0;
        }
        int fired = 0;
        java.util.List<Npc> kedaluwarsa = null;

        for (Npc nd : npcs) {
            // npc_duration(): NPC sementara berumur terbatas memicu
            // `endAction` lalu dicabut dari dunia
            if (nd.temporary && nd.duration > 0) {
                nd.duraTimer += TICK_MS;
                if (nd.duraTimer >= nd.duration) {
                    nd.duraTimer = 0;
                    if (fire(engine, nd, "endAction")) {
                        fired++;
                    }
                    if (kedaluwarsa == null) {
                        kedaluwarsa = new ArrayList<>();
                    }
                    kedaluwarsa.add(nd);
                    continue;
                }
            }
            if (nd.actionTime > 0) {
                nd.actionTimer += TICK_MS;
                if (nd.actionTimer >= nd.actionTime) {
                    nd.actionTimer = 0;
                    if (fire(engine, nd, "action")) {
                        fired++;
                    }
                }
            }
            if (nd.moveTime > 0) {
                nd.moveTimer += TICK_MS;
                if (nd.moveTimer >= nd.moveTime) {
                    nd.moveTimer = 0;
                    if (fire(engine, nd, "move")) {
                        fired++;
                    }
                }
            }
        }

        if (kedaluwarsa != null) {
            for (Npc nd : kedaluwarsa) {
                removeTemp(nd, MapServer.world);
            }
        }
        return fired;
    }

    /** Interval tik timer NPC, mengikuti {@code timer_insert(100, 100, ...)} di C. */
    public static final long TICK_MS = 100;

    /**
     * Panggil satu kait skrip milik NPC.
     *
     * <p>Kegagalan satu NPC tidak boleh menghentikan tik untuk NPC lain —
     * satu skrip rusak jangan sampai membekukan seluruh dunia.</p>
     */
    private boolean fire(org.rtk.map.script.ScriptEngine engine, Npc nd, String method) {
        try {
            // C selalu mengirim NPC-nya sendiri sebagai argumen pertama, dan
            // pemiliknya sebagai argumen kedua bila ada
            // (`sl_doscript_blargs(nd->name, "move", 2, &nd->bl, &tsd->bl)`).
            // Skrip memakainya langsung — `function(npc) ... npc.side ...` —
            // jadi memanggil tanpa argumen membuat SETIAP kait timer error
            // dengan "attempt to index nil". Pernah kejadian.
            var npcRef = engine.objectRef(nd);
            if (nd.ownerId != 0) {
                for (User u : MapServer.onlineChars.values()) {
                    if (u.id == nd.ownerId) {
                        return engine.doScript(nd.name, method, npcRef,
                                engine.playerRef(u.scriptPlayer()));
                    }
                }
            }
            return engine.doScript(nd.name, method, npcRef);
        } catch (RuntimeException e) {
            log.error("[NPC] kait '{}' pada {} gagal", method, nd.name, e);
            return false;
        }
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
    // ------------------------------------------------------------------
    // npc_move(): NPC melangkah satu petak ke arah hadapnya
    // ------------------------------------------------------------------

    /**
     * Port of npc_move() (map/npc.c) — dipakai skrip lewat
     * {@code npc:move()}, kait AI NPC yang paling sering dipanggil.
     *
     * <p>Alurnya: hitung petak tujuan dari {@code nd.side}, tolak bila
     * tujuannya <b>portal</b> (NPC tidak boleh menginjak portal), tolak
     * bila ada benda atau tembok yang menghalangi, lalu pindahkan dan
     * siarkan. Sekalian dihitung jalur petak yang baru terbuka di depan
     * NPC supaya pemain di situ melihatnya masuk.</p>
     *
     * <p><b>Dua pemeriksaan C yang sengaja tidak diport:</b>
     * {@code clif_object_canmove()} dan {@code clif_object_canmove_from()}
     * membaca tabel {@code objectFlags[]}. Tabel itu di-alokasi nol di
     * {@code object_flag_init()} dan barisnya yang mengisi
     * ({@code objectFlags[z] = flag;}) <b>dikomentari</b> di sumber aslinya
     * — jadi keduanya selalu mengembalikan 0 pada build C yang sebenarnya.
     * Meniru keduanya berarti menambah pembacaan {@code SObj.tbl} (18.954
     * entri) yang efeknya nol.</p>
     *
     * @return true bila NPC benar-benar berpindah
     */
    public static boolean move(Npc nd) {
        if (nd == null) {
            return false;
        }
        org.rtk.map.data.MapData map = MapServer.world.get(nd.m);
        if (map == null) {
            return false;
        }

        final int backX = nd.x;
        final int backY = nd.y;
        final int direction = nd.side;
        int dx = backX;
        int dy = backY;

        // Jalur petak yang baru terbuka di depan NPC. Angka-angka ini
        // ditiru apa adanya dari C — tidak simetris, dan sengaja begitu:
        // area pandang klien 19x17 dengan titik pandang tidak di tengah.
        int x0 = backX;
        int y0 = backY;
        int x1 = 0;
        int y1 = 0;
        boolean nothingNew = false;

        switch (direction) {
            case 0 -> { // atas
                if (backY > 0) {
                    dy = backY - 1;
                    x0 -= 9;
                    if (x0 < 0) {
                        x0 = 0;
                    }
                    y0 -= 9;
                    y1 = 1;
                    x1 = 19;
                    if (y0 < 7) {
                        nothingNew = true;
                    }
                    if (y0 == 7) {
                        y1 += 7;
                        y0 = 0;
                    }
                    if (x0 + 19 + 9 >= map.xs) {
                        x1 += 9 - ((x0 + 19 + 9) - map.xs);
                    }
                    if (x0 <= 8) {
                        x1 += x0;
                        x0 = 0;
                    }
                }
            }
            case 1 -> { // kanan
                if (backX < map.xs) {
                    x0 += 10;
                    y0 -= 8;
                    if (y0 < 0) {
                        y0 = 0;
                    }
                    dx = backX + 1;
                    y1 = 17;
                    x1 = 1;
                    if (x0 > map.xs - 9) {
                        nothingNew = true;
                    }
                    if (x0 == map.xs - 9) {
                        x1 += 9;
                    }
                    if (y0 + 17 + 8 >= map.ys) {
                        y1 += 8 - ((y0 + 17 + 8) - map.ys);
                    }
                    if (y0 <= 7) {
                        y1 += y0;
                        y0 = 0;
                    }
                }
            }
            case 2 -> { // bawah
                if (backY < map.ys) {
                    x0 -= 9;
                    if (x0 < 0) {
                        x0 = 0;
                    }
                    y0 += 9;
                    dy = backY + 1;
                    y1 = 1;
                    x1 = 19;
                    if (y0 + 8 > map.ys) {
                        nothingNew = true;
                    }
                    if (y0 + 8 == map.ys) {
                        y1 += 8;
                    }
                    if (x0 + 19 + 9 >= map.xs) {
                        x1 += 9 - ((x0 + 19 + 9) - map.xs);
                    }
                    if (x0 <= 8) {
                        x1 += x0;
                        x0 = 0;
                    }
                }
            }
            case 3 -> { // kiri
                if (backX > 0) {
                    x0 -= 10;
                    y0 -= 8;
                    if (y0 < 0) {
                        y0 = 0;
                    }
                    y1 = 17;
                    x1 = 1;
                    dx = backX - 1;
                    if (x0 < 8) {
                        nothingNew = true;
                    }
                    if (x0 == 8) {
                        x0 = 0;
                        x1 += 8;
                    }
                    if (y0 + 17 + 8 >= map.ys) {
                        y1 += 8 - ((y0 + 17 + 8) - map.ys);
                    }
                    if (y0 <= 7) {
                        y1 += y0;
                        y0 = 0;
                    }
                }
            }
            default -> {
                // Arah di luar 0..3 tidak menggeser apa pun — sama seperti
                // `switch` tanpa default di C. Lihat Peringatan #13.
            }
        }

        if (dx >= map.xs) {
            dx = map.xs - 1;
        }
        if (dy >= map.ys) {
            dy = map.ys - 1;
        }

        // NPC tidak boleh menginjak portal.
        if (map.warpAt(dx, dy) != null) {
            return false;
        }

        if (blockedBy(map, dx, dy, nd)) {
            return false;
        }

        if (!map.walkable(dx, dy)) {
            return false;
        }

        if (x0 > map.xs) {
            x0 = map.xs - 1;
        }
        if (y0 > map.ys) {
            y0 = map.ys - 1;
        }
        if (x0 < 0) {
            x0 = 0;
        }
        if (y0 < 0) {
            y0 = 0;
        }
        if (dx >= map.xs || dx < 0) {
            dx = backX;
        }
        if (dy >= map.ys || dy < 0) {
            dy = backY;
        }

        if (dx == backX && dy == backY) {
            return false;
        }

        map.moveBlock(nd, dx, dy);

        if (!nothingNew && x1 > 0 && y1 > 0) {
            Clif.npcRevealStrip(nd, map, x0, y0, x0 + (x1 - 1), y0 + (y1 - 1));
        }

        Clif.npcMove(nd, backX, backY);
        return true;
    }

    /**
     * Port of npc_warp() (map/npc.c) — pindahkan NPC seketika ke peta lain.
     *
     * <p>Penjaga yang ikut diport: hanya NPC ber-id di atas
     * {@code NPC_START_NUM} yang boleh dipindah. Setelah pindah, wujudnya
     * digambar ulang untuk pemain di area tujuan.</p>
     */
    public static void warp(Npc nd, int m, int x, int y) {
        if (nd == null || nd.id < Npc.NPC_START_NUM) {
            return;
        }
        org.rtk.map.data.MapData asal = MapServer.world.get(nd.m);
        if (asal != null) {
            asal.delBlock(nd);
        }
        org.rtk.map.data.MapData tujuan = MapServer.world.get(m);
        if (tujuan == null) {
            log.warn("[NPC] {} diminta pindah ke peta {} yang tidak ada", nd.name, m);
            if (asal != null) {
                asal.addBlock(nd);
            }
            return;
        }
        nd.m = m;
        nd.x = x;
        nd.y = y;
        tujuan.addBlock(nd);

        tujuan.foreachInArea(x, y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User sd) {
                Clif.sendNpcLook(sd, nd);
            }
        });
    }

    /**
     * npc_move_sub(): true bila ada benda di petak tujuan yang menghalangi.
     *
     * <p>Yang <b>tidak</b> menghalangi: NPC ber-subtype bukan nol, mob yang
     * sudah mati, pemain yang mati di peta ber-{@code show_ghosts}, pemain
     * ber-state -1, dan GM level 50 ke atas — GM tinggi dilewati NPC begitu
     * saja.</p>
     */
    private static boolean blockedBy(org.rtk.map.data.MapData map, int dx, int dy, Npc nd) {
        for (org.rtk.map.data.BlockList bl : map.objectsAt(dx, dy)) {
            if (bl == nd) {
                continue;
            }
            if (bl instanceof Npc other) {
                if (other.subtype != 0) {
                    continue;
                }
                return true;
            }
            if (bl instanceof Mob mb) {
                if (!mb.isAlive()) {
                    continue;
                }
                return true;
            }
            if (bl instanceof User sd) {
                if (sd.status.state == -1 || sd.status.gmLevel >= 50) {
                    continue;
                }
                if (map.showGhosts != 0 && sd.status.state == 1) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

}
