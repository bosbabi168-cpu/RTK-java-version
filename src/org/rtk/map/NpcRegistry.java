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
        for (Npc nd : npcs) {
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
            return engine.doScript(nd.name, method);
        } catch (RuntimeException e) {
            log.error("[NPC] kait '{}' pada {} gagal", method, nd.name, e);
            return false;
        }
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
