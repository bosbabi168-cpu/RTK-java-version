package org.rtk.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.map.data.MapData;
import org.rtk.map.data.MapRegistry;

/**
 * Port of mobdb_read() + mobspawn_read() (map/mob.c) — muat jenis mob dari
 * tabel {@code Mobs}, lalu lahirkan mob dari tabel
 * {@code Spawns<serverId>}.
 *
 * <p>Dua tabel, dua peran: {@code Mobs} adalah <b>jenis</b> (716 baris,
 * dipakai berulang) dan {@code Spawns} adalah <b>titik kelahiran</b> (1.175
 * baris, satu mob hidup per baris).</p>
 */
public final class MobRegistry {

    private static final Logger log = LogManager.getLogger(MobRegistry.class);

    /** Jenis mob menurut MobId. */
    private final Map<Long, MobData> types = new HashMap<>();

    /** Nama skrip -&gt; jenis; dipakai skrip yang memanggil mob dengan nama. */
    private final Map<String, MobData> typesByName = new HashMap<>();

    /** Mob yang hidup di server ini. */
    private final List<Mob> mobs = new ArrayList<>();

    /** Id blok berikutnya untuk mob; mulai dari MOB_START_NUM seperti C. */
    private long nextId = Mob.MOB_START_NUM;

    public MobData type(long mobId) {
        return types.get(mobId);
    }

    public MobData typeByName(String yname) {
        return yname == null ? null : typesByName.get(yname.toLowerCase());
    }

    public List<Mob> all() {
        return mobs;
    }

    /**
     * Tambahkan satu mob yang dibuat di luar jalur spawn (mis. dipanggil
     * skrip), sekaligus mendaftarkannya ke indeks id.
     */
    public void add(Mob mob) {
        mobs.add(mob);
        if (idIndex != null) {
            idIndex.register(mob);
        }
    }

    public int typeCount() {
        return types.size();
    }

    /** mobdb_read(): muat seluruh jenis mob. */
    public int loadTypes(Sql sql) {
        long t0 = System.currentTimeMillis();
        types.clear();
        typesByName.clear();

        int rows = sql.forEachRow(
                "SELECT `MobId`,`MobDescription`,`MobIdentifier`,`MobBehavior`,`MobAI`,"
                + "`MobLook`,`MobLookColor`,`MobVita`,`MobMana`,`MobExperience`,`MobHit`,"
                + "`MobLevel`,`MobMight`,`MobGrace`,`MobMoveTime`,`MobSpawnTime`,`MobArmor`,"
                + "`MobSound`,`MobAttackTime`,`MobProtection`,`MobReturnDistance`,`MobSex`,"
                + "`MobFace`,`MobFaceColor`,`MobHair`,`MobHairColor`,`MobSkinColor`,"
                + "`MobState`,`MobIsChar`,`MobWill`,`MobMinimumDamage`,`MobMaximumDamage`,"
                + "`MobMark`,`MobIsNpc`,`MobIsBoss` FROM `Mobs`",
                rs -> {
                    MobData d = new MobData();
                    d.id = rs.getLong("MobId");
                    // PERHATIKAN: untuk mob, Description = nama tampilan dan
                    // Identifier = nama skrip. Pada NPC justru terbalik.
                    d.name = str(rs.getString("MobDescription"));
                    d.yname = str(rs.getString("MobIdentifier"));
                    d.type = rs.getInt("MobBehavior");
                    d.subtype = rs.getInt("MobAI");
                    d.look = rs.getInt("MobLook");
                    d.lookColor = rs.getInt("MobLookColor");
                    d.vita = rs.getLong("MobVita");
                    d.mana = rs.getLong("MobMana");
                    d.exp = rs.getLong("MobExperience");
                    d.hit = rs.getInt("MobHit");
                    d.level = rs.getInt("MobLevel");
                    d.might = rs.getInt("MobMight");
                    d.grace = rs.getInt("MobGrace");
                    d.will = rs.getInt("MobWill");
                    d.moveTime = rs.getInt("MobMoveTime");
                    d.attackTime = rs.getInt("MobAttackTime");
                    d.spawnTime = rs.getInt("MobSpawnTime");
                    d.baseArmor = rs.getInt("MobArmor");
                    d.protection = rs.getInt("MobProtection");
                    d.sound = rs.getInt("MobSound");
                    d.returnDistance = rs.getInt("MobReturnDistance");
                    d.minDam = rs.getInt("MobMinimumDamage");
                    d.maxDam = rs.getInt("MobMaximumDamage");
                    d.mark = rs.getInt("MobMark");
                    d.mobType = rs.getInt("MobIsChar");
                    d.isNpc = rs.getInt("MobIsNpc") != 0;
                    d.isBoss = rs.getInt("MobIsBoss") != 0;
                    d.sex = rs.getInt("MobSex");
                    d.face = rs.getInt("MobFace");
                    d.faceColor = rs.getInt("MobFaceColor");
                    d.hair = rs.getInt("MobHair");
                    d.hairColor = rs.getInt("MobHairColor");
                    d.skinColor = rs.getInt("MobSkinColor");
                    d.state = rs.getInt("MobState");

                    types.put(d.id, d);
                    if (!d.yname.isEmpty()) {
                        typesByName.putIfAbsent(d.yname.toLowerCase(), d);
                    }
                });

        if (rows < 0) {
            log.error("[MOB] gagal membaca tabel Mobs");
            return 0;
        }
        log.info("[MOB] {} jenis mob dimuat dalam {} ms",
                types.size(), System.currentTimeMillis() - t0);
        return types.size();
    }

    /**
     * Indeks id global ({@code map_addiddb} di C). Mob HARUS terdaftar di
     * sini, karena skrip merujuk mob lewat id — mis.
     * {@code player:addThreat(mob.id, dmg)}. Tanpa itu satu-satunya cara
     * menemukan mob adalah memindai seluruh daftar, yang berarti 1.175
     * perbandingan setiap pukulan.
     */
    private NpcRegistry idIndex;

    /** Sambungkan ke indeks id global sebelum memuat spawn. */
    public void useIdIndex(NpcRegistry index) {
        this.idIndex = index;
    }

    /**
     * mobspawn_read(): lahirkan mob dari titik-titik kelahiran.
     *
     * <p>Seperti versi C, koordinat yang melewati tepi peta <b>dijepit</b>
     * ke dalam batas, dan baris yang peta atau jenis mobnya tidak dikenal
     * dilewati dengan hitungan tersendiri supaya kelemahannya terlihat.</p>
     *
     * @return jumlah mob yang benar-benar lahir
     */
    public int loadSpawns(Sql sql, int serverId, MapRegistry world) {
        long t0 = System.currentTimeMillis();
        mobs.clear();
        nextId = Mob.MOB_START_NUM;

        int[] stat = new int[3]; // 0=lahir, 1=peta tak dimuat, 2=jenis tak dikenal
        List<Mob> lahir = new ArrayList<>();

        int rows = sql.forEachRow(
                "SELECT `SpnMapId`,`SpnX`,`SpnY`,`SpnMobId`,`SpnLastDeath`,`SpnId`,"
                + "`SpnStartTime`,`SpnEndTime`,`SpnMobIdReplace` "
                + "FROM `Spawns" + serverId + "` ORDER BY `SpnId`",
                rs -> {
                    long mobId = rs.getLong("SpnMobId");
                    MobData d = types.get(mobId);
                    if (d == null) {
                        stat[2]++;
                        return;
                    }
                    int m = rs.getInt("SpnMapId");
                    MapData map = world.get(m);
                    if (map == null) {
                        stat[1]++;
                        return;
                    }

                    Mob mob = new Mob();
                    mob.data = d;
                    mob.spawnId = rs.getLong("SpnId");
                    mob.lastDeath = rs.getLong("SpnLastDeath");
                    mob.m = m;
                    mob.x = Math.min(rs.getInt("SpnX"), map.xs - 1);
                    mob.y = Math.min(rs.getInt("SpnY"), map.ys - 1);
                    mob.startM = mob.m;
                    mob.startX = mob.x;
                    mob.startY = mob.y;
                    mob.id = nextId++;
                    resetStats(mob);
                    lahir.add(mob);
                });

        if (rows < 0) {
            log.error("[MOB] gagal membaca tabel Spawns{}", serverId);
            return 0;
        }

        for (Mob mob : lahir) {
            MapData map = world.get(mob.m);
            if (map != null && map.addBlock(mob)) {
                mobs.add(mob);
                if (idIndex != null) {
                    idIndex.register(mob);   // map_addiddb
                }
                stat[0]++;
            }
        }

        log.info("[MOB] {} mob lahir dari {} baris `Spawns{}` ({} peta tak dimuat, "
                + "{} jenis tak dikenal) dalam {} ms",
                stat[0], rows, serverId, stat[1], stat[2], System.currentTimeMillis() - t0);
        return stat[0];
    }

    /** Interval tik AI mob, mengikuti {@code timer_insert(50, 50, ...)} di C. */
    public static final long TICK_MS = 50;

    /**
     * Nama tabel AI Lua menurut {@code MobAI}, sesuai urutan cabang di
     * {@code mob_handle_sub}. Indeks 4 berarti "pakai skrip mob itu
     * sendiri" ({@code yname}), jadi dibiarkan null.
     *
     * <p>⚠️ {@code mob_ai_hard} (indeks 2) dan {@code mob_ai_boss}
     * (indeks 3) <b>tidak pernah didefinisikan</b> di konten Lua — lihat
     * `luascript/PERUBAHAN.md`. Saat ini tidak berdampak karena tidak ada
     * mob ber-MobAI 2 atau 3 di database, tapi kalau nanti ada, kait
     * AI-nya akan gagal dan itu akan tercatat di log.</p>
     */
    private static final String[] AI_TABLES = {
        "mob_ai_basic", "mob_ai_normal", "mob_ai_hard",
        "mob_ai_boss", null, "mob_ai_ghost"
    };

    /**
     * mob_handle_sub(): satu tik AI untuk seluruh mob — dipanggil tiap 50 ms.
     *
     * <p>Urutan pemeriksaannya ditiru dari C, termasuk dua penyaring yang
     * penting untuk beban server:</p>
     * <ul>
     *   <li>mob <b>diam</b> ({@code MobBehavior >= 2}) dilewati sama sekali;</li>
     *   <li>mob di peta <b>tanpa pemain</b> dilewati — tidak ada gunanya
     *       menggerakkan mob yang tak seorang pun melihat. Ini bukan
     *       penghematan buatan kami; versi C melakukan hal yang sama.</li>
     * </ul>
     *
     * @return jumlah kait AI yang terpicu pada tik ini
     */
    public int runTimers(org.rtk.map.script.ScriptEngine engine, MapRegistry world) {
        if (engine == null) {
            return 0;
        }
        int fired = 0;
        long now = System.currentTimeMillis() / 1000;

        // mob yang nyawanya habis diurus lebih dulu, supaya tidak sempat
        // bergerak atau menyerang setelah mati
        fired += reapDead(engine, world);

        for (Mob mob : mobs) {
            MobData d = mob.data;
            if (d == null) {
                continue;
            }

            // kelahiran ulang: hanya setelah jeda spawnTime berlalu
            if (mob.state == MobData.MOB_DEAD) {
                if (d.spawnTime > 0 && mob.lastDeath + d.spawnTime <= now) {
                    respawn(mob, world);
                }
                continue;
            }

            if (d.type >= MobData.MOB_STATIONARY) {
                continue;   // mob diam tidak pernah bergerak sendiri
            }

            MapData map = world.get(mob.m);
            if (map == null || map.users == 0) {
                continue;   // tidak ada yang melihat: lewati
            }

            mob.moveTimer += TICK_MS;
            mob.attackTimer += TICK_MS;

            boolean menyerang = mob.target != 0 && d.attackTime > 0
                    && mob.attackTimer >= d.attackTime;
            if (menyerang) {
                mob.attackTimer = 0;
                if (fireAi(engine, mob, "attack")) {
                    fired++;
                }
                continue;   // menyerang menggantikan bergerak pada tik ini
            }

            if (d.moveTime > 0 && mob.moveTimer >= d.moveTime) {
                mob.moveTimer = 0;
                if (fireAi(engine, mob, "move")) {
                    fired++;
                }
            }
        }
        return fired;
    }

    /**
     * Panggil kait AI mob. Tabel mana yang dipakai ditentukan
     * {@code MobAI}: 0..3 dan 5 memakai tabel bersama {@code mob_ai_*},
     * sedangkan 4 memakai skrip milik mob itu sendiri ({@code yname}).
     */
    /** Skrip AI yang dipakai mob ini, atau null bila MobAI di luar jangkauan. */
    private static String aiRoot(MobData d) {
        if (d.subtype >= 0 && d.subtype < AI_TABLES.length && AI_TABLES[d.subtype] != null) {
            return AI_TABLES[d.subtype];
        }
        if (d.subtype == 4) {
            return d.yname;   // skrip mob itu sendiri
        }
        return null;
    }

    /**
     * Port {@code clif_send_mob_health()} — namanya di C <b>menyesatkan</b>:
     * fungsi itu tidak mengirim paket apa pun, isinya murni memanggil kait
     * {@code on_attacked} milik AI mob, dan parameter damage/critical-nya
     * tidak pernah dipakai.
     *
     * <p>Argumen kedua adalah <b>penyerangnya</b>; bila tidak ketemu, C
     * memakai <b>mob itu sendiri</b> ({@code map_id2bl(mob->bl.id)}), bukan
     * nil — sama seperti jebakan {@code callBase}.</p>
     */
    public static void fireAttacked(org.rtk.map.script.ScriptEngine engine,
                                    Mob mob, Object penyerang) {
        if (engine == null || mob == null) {
            return;
        }
        String root = aiRoot(mob.data);
        if (root == null) {
            return;
        }
        try {
            org.luaj.vm2.LuaValue lawan = penyerang instanceof User u
                    ? engine.playerRef(u.scriptPlayer())
                    : engine.objectRef(penyerang != null ? penyerang : mob);
            engine.doScript(root, "on_attacked", engine.objectRef(mob), lawan);
        } catch (RuntimeException e) {
            log.error("[MOB] kait on_attacked pada '{}' gagal", root, e);
        }
    }

    private boolean fireAi(org.rtk.map.script.ScriptEngine engine, Mob mob, String method) {
        MobData d = mob.data;
        String root = aiRoot(d);
        if (root == null) {
            return false;     // MobAI di luar jangkauan: tidak ada kait
        }

        try {
            // C mengirim mob dan SASARANNYA:
            // `sl_doscript_blargs("mob_ai_basic", "move", 2, &mob->bl, bl)`.
            // Tanpa argumen, setiap kait AI error "attempt to index nil".
            var mobRef = engine.objectRef(mob);
            if (mob.target != 0) {
                var bl = idIndex == null ? null : idIndex.byId(mob.target);
                if (bl != null) {
                    return engine.doScript(root, method, mobRef, engine.objectRef(bl));
                }
                for (User u : MapServer.onlineChars.values()) {
                    if (u.id == mob.target) {
                        return engine.doScript(root, method, mobRef,
                                engine.playerRef(u.scriptPlayer()));
                    }
                }
            }
            return engine.doScript(root, method, mobRef);
        } catch (RuntimeException e) {
            log.error("[MOB] kait AI '{}' pada '{}' (MobAI {}) gagal",
                    method, root, d.subtype, e);
            return false;
        }
    }

    /**
     * Periksa mob yang nyawanya sudah habis dan urus kematiannya.
     *
     * <p>Skrip pertarungan melukai mob dengan menulis {@code mob.health}
     * langsung, jadi tidak ada satu titik pun di Java yang tahu kapan
     * pukulan mendarat. Pemeriksaan dilakukan di tik AI — cukup cepat
     * (50 ms) untuk tidak terasa, dan menghindari menaruh logika kematian
     * di setter atribut yang seharusnya bodoh.</p>
     *
     * @return jumlah mob yang baru saja mati pada tik ini
     */
    private int reapDead(org.rtk.map.script.ScriptEngine engine, MapRegistry world) {
        int mati = 0;
        for (Mob mob : mobs) {
            if (mob.state == MobData.MOB_DEAD || mob.currentVita > 0) {
                continue;
            }
            // Siapa pembunuhnya menentukan siapa yang menerima jatuhan.
            // Yang dipakai adalah pemegang ancaman TERBESAR, bukan yang
            // kebetulan memukul terakhir; kalau tabel ancaman kosong,
            // barulah `attacker` dipakai sebagai cadangan.
            long pembunuhId = mob.topThreat();
            if (pembunuhId == 0) {
                pembunuhId = mob.attacker;
            }
            User pembunuh = null;
            for (User u : MapServer.onlineChars.values()) {
                if (u.id == pembunuhId) {
                    pembunuh = u;
                    break;
                }
            }

            kill(mob, world);
            mati++;

            var mobRef = engine.objectRef(mob);
            try {
                if (pembunuh != null) {
                    var plRef = engine.playerRef(pembunuh.scriptPlayer());
                    engine.doScript(mob.scriptName(), "on_death", mobRef, plRef);
                    // mobdb_drops(): jatuhan barang seluruhnya sisi skrip
                    engine.doScript("HandleMobDrops", null, plRef, mobRef);
                } else {
                    // mati tanpa pembunuh yang bisa dilacak (mis. skrip
                    // membunuhnya langsung): kait tetap dipanggil
                    engine.doScript(mob.scriptName(), "on_death", mobRef);
                }
            } catch (RuntimeException e) {
                log.error("[MOB] kait kematian pada '{}' gagal", mob.scriptName(), e);
            }
            mob.threat.clear();
            mob.attacker = 0;
        }
        return mati;
    }

    /**
     * mob_respawn(): hidupkan kembali mob di titik kelahirannya.
     *
     * <p>Mob dikembalikan ke posisi awal, bukan tempat ia mati — itu yang
     * membuat titik spawn tetap pada tempatnya setelah dipancing jauh.</p>
     */
    public boolean respawn(Mob mob, MapRegistry world) {
        MapData map = world.get(mob.startM);
        if (map == null) {
            return false;
        }
        if (mob.onMap) {
            map.delBlock(mob);
        }
        mob.m = mob.startM;
        mob.x = mob.startX;
        mob.y = mob.startY;
        resetStats(mob);
        mob.moveTimer = 0;
        mob.attackTimer = 0;
        return map.addBlock(mob);
    }

    /**
     * Catat mob sebagai mati: cabut dari indeks blok dan mulai hitung jeda
     * kelahiran ulang.
     */
    public void kill(Mob mob, MapRegistry world) {
        mob.state = MobData.MOB_DEAD;
        mob.currentVita = 0;
        mob.target = 0;
        mob.lastDeath = System.currentTimeMillis() / 1000;
        MapData map = world.get(mob.m);
        if (map != null && mob.onMap) {
            map.delBlock(mob);
        }
    }

    /**
     * mob_respawn_getstats(): setel nilai awal mob dari jenisnya.
     * Dipanggil saat lahir dan saat lahir ulang.
     */
    public static void resetStats(Mob mob) {
        MobData d = mob.data;
        if (d == null) {
            return;
        }
        mob.maxVita = d.vita;
        mob.maxMana = d.mana;
        mob.currentVita = d.vita;
        mob.currentMana = d.mana;
        mob.look = d.look;
        mob.lookColor = d.lookColor;
        mob.charState = d.state;
        mob.state = MobData.MOB_ALIVE;
        mob.target = 0;
        mob.graphicId = d.look;
        mob.graphicColor = d.lookColor;
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
