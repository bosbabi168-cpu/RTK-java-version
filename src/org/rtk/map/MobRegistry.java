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

    /**
     * Daftarkan satu jenis mob. Dipakai {@link #loadTypes} dan uji yang
     * berjalan tanpa database. Nama pertama menang bila ada {@code yname}
     * ganda, seperti pencarian berurutan di C.
     */
    public void registerType(MobData d) {
        types.put(d.id, d);
        if (d.yname != null && !d.yname.isEmpty()) {
            typesByName.putIfAbsent(d.yname.toLowerCase(), d);
        }
    }

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

                    registerType(d);
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

            // kelahiran ulang: hanya setelah jeda spawnTime berlalu, dan
            // hanya untuk mob tabel. Mob yang dilahirkan skrip sekali pakai
            // (`mob->onetime` di C: `state == MOB_DEAD && !mob->onetime`).
            if (mob.state == MobData.MOB_DEAD) {
                if (!mob.oneTime && d.spawnTime > 0
                        && mob.lastDeath + d.spawnTime <= now) {
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
    /**
     * Bagian kedua {@code mobdb_drops()}: tumpahkan isi inventaris mob ke
     * petak kematiannya, lalu kosongkan daftarnya.
     *
     * <p>Dijatuhkan lewat jalur <b>jatuhan mob</b> ({@code mobdb_dropitem}),
     * bukan jalur inventaris pemain — jadi aturan gabungnya id saja
     * (Peringatan #31).</p>
     */
    static void jatuhkanInventaris(Mob mob) {
        if (mob.inventory.isEmpty()) {
            return;
        }
        for (org.rtk.common.mmo.Item it : mob.inventory) {
            if (it.id != 0 && it.amount >= 1) {
                MapServer.floorItems.drop(mob, it.id, it.amount, it.dura,
                        it.protectedFlag, it.owner, mob.m, mob.x, mob.y);
            }
        }
        mob.inventory.clear();
    }

    /** Pintu masuk {@link #jatuhkanInventaris} untuk uji regresi. */
    public void jatuhkanInventarisUji(Mob mob) {
        jatuhkanInventaris(mob);
    }

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
                    // ...kecuali isi inventaris mob, yang di C dijatuhkan
                    // oleh mobdb_drops sendiri tepat setelah kait itu.
                    // Inilah yang mengembalikan barang yang pernah
                    // diserahkan pemain kepada mob ini.
                    jatuhkanInventaris(mob);

                    // ⚠️ clif.c:2469-2489 — TIGA langkah yang selama ini
                    // hilang seluruhnya dari port ini: daftar bunuh, kait
                    // pengalaman, lalu periksa level. Tanpa `onGetExp` mob
                    // yang mati tidak pernah memberi pengalaman sama
                    // sekali, dan itu tidak menimbulkan error apa pun —
                    // gerbang luring tidak bisa melihat sesuatu yang tidak
                    // terjadi.
                    daftarBunuh(pembunuh, mob);
                    try {
                        engine.doScript("onGetExp", null, plRef, mobRef);
                    } catch (RuntimeException e) {
                        log.error("[MOB] kait onGetExp pada '{}' gagal",
                                mob.displayName(), e);
                    }
                    periksaLevel(engine, pembunuh);
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
     * addtokillreg() / clif_addtokillreg(): catat satu ekor ke hitungan
     * bunuh.
     *
     * <p>⚠️ Pemain <b>sendirian</b> hanya mencatat bila mob itu memang
     * memberi pengalaman ({@code if (mob->exp)}); pemain <b>bergrup</b>
     * mencatat tanpa syarat itu, untuk setiap anggota di peta yang sama.
     * Bedanya ada di C dan tidak jelas disengaja, tapi ditiru: skrip quest
     * membaca hitungan ini.</p>
     */
    private static void daftarBunuh(User pembunuh, Mob mob) {
        long jenis = mob.data == null ? 0 : mob.data.id;
        if (jenis == 0) {
            return;
        }
        if (pembunuh.groupId == 0) {
            if (mob.data.exp > 0) {
                tambahSatu(pembunuh, jenis);
            }
            return;
        }
        for (User t : Groups.anggotaOnline(pembunuh)) {
            if (t.m == pembunuh.m) {
                tambahSatu(t, jenis);
            }
        }
    }

    /** {@code MAX_KILLREG} di mmo.h: tabelnya penuh pada 5000 jenis. */
    private static final int MAX_KILLREG = 5000;

    private static void tambahSatu(User sd, long jenis) {
        Long ada = sd.status.killReg.get(jenis);
        if (ada == null && sd.status.killReg.size() >= MAX_KILLREG) {
            return;   // tabel penuh: C diam-diam berhenti mencatat
        }
        sd.status.killReg.merge(jenis, 1L, Long::sum);
    }

    /**
     * pc_checklevel() untuk pembunuh, atau untuk tiap anggota grup yang ada
     * di peta yang sama dan tidak sedang jadi hantu.
     */
    private static void periksaLevel(org.rtk.map.script.ScriptEngine engine,
                                     User pembunuh) {
        if (pembunuh.groupId == 0) {
            Pc.checkLevel(engine, pembunuh);
            return;
        }
        for (User t : Groups.anggotaOnline(pembunuh)) {
            if (t.m == pembunuh.m && t.status.state != 1) {
                Pc.checkLevel(engine, t);
            }
        }
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
     * moveghost_mob() (map/mob.c:1518) — mob melangkah satu petak ke arah
     * yang sedang dihadapinya.
     *
     * <p>Ini {@code moveGhost}, method skrip belum-diport terbanyak (84x):
     * hampir seluruh AI mob di {@code Accepted/Mobs/mob.lua} bergerak
     * lewatnya. Strukturnya kembar dengan {@link NpcRegistry#move} — angka
     * jalur petak yang baru terbuka pun sama persis, tidak simetris karena
     * area pandang klien 19x17 dengan titik pandang bukan di tengah.</p>
     *
     * <p>Tiga penjaga yang ditiru dan mudah terlewat:</p>
     * <ul>
     *   <li><b>Mob tidak boleh menginjak portal</b> — dicek sebelum apa pun
     *       yang lain, dan C langsung {@code return 0} di situ.</li>
     *   <li><b>Penghalang tidak berlaku bila mob sedang mengejar sasaran.</b>
     *       Di C ketiga cek tabrakan digabung {@code && mob->target == 0},
     *       jadi mob yang punya target menembus apa pun. Itu di C, bukan
     *       kelalaian port.</li>
     *   <li><b>Menginjak jebakan memanggil kait {@code click} milik NPC
     *       lantai</b> dengan (mob, npc) — {@code mob_trap_look}. Hanya satu
     *       jebakan per langkah yang terpicu.</li>
     * </ul>
     *
     * @return true bila mob benar-benar berpindah
     */
    public boolean moveGhost(org.rtk.map.script.ScriptEngine engine, Mob mob) {
        return langkah(engine, mob, false);
    }

    /**
     * move_mob_ignore_object() (map/mob.c:1330) — <b>persis</b>
     * {@link #moveGhost}, hanya tanpa cek tabrakan.
     *
     * <p>Di C kedua fungsi itu disalin utuh; bedanya cuma satu blok yang
     * <b>dikomentari</b> di versi ini:</p>
     *
     * <pre>{@code
     * /*if((map_canmove(m,dx,dy)==1) || (mob->canmove==1)) {
     *     mob->canmove=0;
     *     return 0;
     * }*&#47;
     * }</pre>
     *
     * <p>Jadi mob menembus tembok <b>dan</b> benda lain — tapi
     * <b>portal tetap menghentikannya</b>, karena penjaga portal ada di atas
     * blok yang dikomentari itu. Dipakai skrip untuk hantu dan mob yang
     * sengaja boleh lewat mana saja.</p>
     *
     * <p>⚠️ Sapuan {@code mob_move} tetap dijalankan di C dan hasilnya
     * ditampung {@code cm = mob->canmove;} yang <b>tidak pernah dibaca</b> —
     * kode mati. Tidak ditiru.</p>
     */
    public boolean moveIgnoreObject(org.rtk.map.script.ScriptEngine engine, Mob mob) {
        return langkah(engine, mob, true);
    }

    /**
     * Badan bersama {@code moveghost_mob} dan {@code move_mob_ignore_object}.
     *
     * @param abaikanTabrakan true = versi tembus segalanya; portal tetap
     *                        menghentikan langkah pada kedua versi
     */
    private boolean langkah(org.rtk.map.script.ScriptEngine engine, Mob mob,
                            boolean abaikanTabrakan) {
        if (mob == null || mob.state == MobData.MOB_DEAD) {
            return false;
        }
        MapData map = MapServer.world.get(mob.m);
        if (map == null) {
            return false;
        }

        final int backX = mob.x;
        final int backY = mob.y;
        final int direction = mob.side;
        int dx = backX;
        int dy = backY;

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
                // Arah di luar 0..3 tidak menggeser apa pun — `switch` di C
                // juga tanpa default. Lihat Peringatan #13.
            }
        }

        if (dx >= map.xs) {
            dx = map.xs - 1;
        }
        if (dy >= map.ys) {
            dy = map.ys - 1;
        }

        // Mob tidak boleh menginjak portal.
        if (map.warpAt(dx, dy) != null) {
            return false;
        }

        // Mob yang sedang mengejar sasaran menembus penghalang — di C ketiga
        // cek tabrakan bersyarat `&& mob->target == 0`. Versi
        // move_mob_ignore_object melewati seluruh blok ini.
        if (!abaikanTabrakan && mob.target == 0
                && (NpcRegistry.blockedBy(map, dx, dy, mob) || !map.walkable(dx, dy))) {
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

        map.moveBlock(mob, dx, dy);

        boolean adaPetakBaru = !nothingNew && x1 > 0 && y1 > 0;
        MapServer.clientView.mobMoved(mob, map, backX, backY,
                adaPetakBaru ? x0 : -1, adaPetakBaru ? y0 : -1,
                adaPetakBaru ? x0 + (x1 - 1) : -1,
                adaPetakBaru ? y0 + (y1 - 1) : -1);

        trapLook(engine, map, mob);
        return true;
    }

    /**
     * mobl_checkmove() — <b>uji coba</b> langkah berikutnya tanpa benar-benar
     * melangkah: bolehkah mob maju satu petak ke arah yang dihadapinya?
     *
     * <p>Dipakai AI skrip untuk memutuskan berbelok sebelum menabrak.</p>
     *
     * <p>⚠️ <b>Dua beda halus dari {@link #moveGhost}, dan keduanya ada di
     * C:</b></p>
     * <ul>
     *   <li>Petak tujuannya dihitung <b>sederhana</b> (±1 menurut arah),
     *       tanpa perhitungan jalur pandang yang rumit itu — jadi tidak ada
     *       cabang {@code nothingnew} di sini sama sekali.</li>
     *   <li><b>Tidak ada pengecualian untuk mob yang sedang mengejar.</b>
     *       {@code moveGhost} membiarkan mob ber-{@code target} menembus
     *       penghalang, tetapi {@code checkMove} tetap menjawab "tidak
     *       boleh". Jadi jawabannya bisa <b>berbeda</b> dari apa yang
     *       sebenarnya terjadi kalau {@code moveGhost} dipanggil — itu di C,
     *       bukan kelalaian port.</li>
     * </ul>
     */
    public boolean checkMove(Mob mob) {
        if (mob == null || mob.state == MobData.MOB_DEAD) {
            return false;
        }
        MapData map = MapServer.world.get(mob.m);
        if (map == null) {
            return false;
        }
        int dx = mob.x;
        int dy = mob.y;
        switch (mob.side) {
            case 0 -> dy -= 1;
            case 1 -> dx += 1;
            case 2 -> dy += 1;
            case 3 -> dx -= 1;
            default -> {
                // arah tak sah tidak menggeser apa pun — lihat Peringatan #13
            }
        }
        if (dx >= map.xs) {
            dx = map.xs - 1;
        }
        if (dy >= map.ys) {
            dy = map.ys - 1;
        }
        if (map.warpAt(dx, dy) != null) {
            return false;
        }
        return !NpcRegistry.blockedBy(map, dx, dy, mob) && map.walkable(dx, dy);
    }

    /**
     * move_mob_intent() (map/mob.c:2270) — <b>hadapkan</b> mob ke sasaran
     * bila sasarannya tepat di sebelahnya.
     *
     * <p>⚠️ <b>Namanya menjanjikan gerakan, tapi ia tidak pernah bergerak.</b>
     * Seluruh badan yang memindahkan mob <b>dikomentari</b> di sumber C;
     * yang tersisa hanya: kalau sasaran bersebelahan (persis satu petak,
     * lurus), putar menghadapnya dan kembalikan 1; selain itu kembalikan 0
     * tanpa melakukan apa pun. Skrip AI memakainya sebagai "sudah cukup
     * dekat untuk menyerang?", bukan sebagai perintah jalan.</p>
     *
     * <p>Siaran arah hanya dikirim bila arahnya <b>benar-benar berubah</b>.</p>
     *
     * @return true bila sasaran bersebelahan
     */
    public boolean moveIntent(Mob mob, org.rtk.map.data.BlockList sasaran) {
        if (mob == null || sasaran == null) {
            return false;
        }
        mob.canMove = false;
        int ax = Math.abs(mob.x - sasaran.x);
        int ay = Math.abs(mob.y - sasaran.y);
        if (!((ax == 0 && ay == 1) || (ax == 1 && ay == 0))) {
            return false;
        }
        int arahLama = mob.side;
        if (mob.x < sasaran.x) {
            mob.side = 1;
        }
        if (mob.x > sasaran.x) {
            mob.side = 3;
        }
        if (mob.y < sasaran.y) {
            mob.side = 2;
        }
        if (mob.y > sasaran.y) {
            mob.side = 0;
        }
        if (arahLama != mob.side) {
            MapServer.clientView.objectSideChanged(mob);
        }
        return true;
    }

    /**
     * mob_trap_look(): mob menginjak NPC lantai (jebakan) — kait
     * {@code click} miliknya dipanggil dengan (mob, npc).
     *
     * <p>Hanya <b>satu</b> jebakan per langkah yang terpicu; di C penjaganya
     * {@code def[0]} yang langsung disetel 1 setelah pemicu pertama.</p>
     */
    private void trapLook(org.rtk.map.script.ScriptEngine engine, MapData map, Mob mob) {
        if (engine == null) {
            return;
        }
        for (org.rtk.map.data.BlockList bl : map.objectsAt(mob.x, mob.y)) {
            if (!(bl instanceof Npc nd) || (nd.subtype != Npc.SUBTYPE_FLOOR
                    && nd.subtype != 2)) {
                continue;
            }
            try {
                engine.doScript(nd.name, "click", engine.objectRef(mob),
                        engine.objectRef(nd));
            } catch (RuntimeException e) {
                log.error("[MOB] kait jebakan '{}' gagal", nd.name, e);
            }
            return;   // def[0] = 1: cukup satu
        }
    }

    /**
     * mobspawn_onetime() (map/mob.c:225) — lahirkan mob dari skrip.
     *
     * <p>Ini {@code spawn}, stub besar terakhir (381x): jebakan, mob event,
     * dan boss instance semuanya lahir lewat sini.</p>
     *
     * <p>Bedanya dari mob tabel {@code Spawns}: mob ini <b>sekali pakai</b> —
     * setelah mati ia tidak lahir kembali, dan {@link #runTimers} melewatinya
     * saat memeriksa kelahiran ulang. Titik awalnya tetap dicatat supaya
     * skrip yang memindahkannya masih punya acuan.</p>
     *
     * @param owner id pemain yang "memiliki" mob ini (jebakan mencarinya);
     *              0 bila tidak ada
     * @return id blok mob yang lahir, urut
     */
    public java.util.List<Long> spawnOneTime(org.rtk.map.script.ScriptEngine engine,
                                             long mobId, int m, int x, int y,
                                             int amount, long owner) {
        java.util.List<Long> lahir = new ArrayList<>();
        MobData d = types.get(mobId);
        MapData map = MapServer.world.get(m);
        if (d == null || map == null || amount <= 0) {
            return lahir;
        }
        for (int i = 0; i < amount; i++) {
            Mob mob = new Mob();
            mob.data = d;
            mob.startM = m;
            mob.startX = x;
            mob.startY = y;
            mob.m = m;
            mob.x = Math.min(x, map.xs - 1);
            mob.y = Math.min(y, map.ys - 1);
            mob.owner = owner;
            mob.oneTime = true;
            mob.id = nextId++;
            resetStats(mob);

            if (!map.addBlock(mob)) {
                continue;
            }
            add(mob);            // daftar tik + indeks id (map_addiddb)
            lahir.add(mob.id);

            // mob_respawn(): terlihat oleh sekitarnya, lalu dua kait lahir —
            // yang umum lebih dulu, baru milik jenis mobnya sendiri.
            MapServer.clientView.mobSpawned(mob);
            if (engine != null) {
                var ref = engine.objectRef(mob);
                try {
                    engine.doScript("on_spawn", null, ref);
                    engine.doScript(mob.scriptName(), "on_spawn", ref);
                } catch (RuntimeException e) {
                    log.error("[MOB] kait on_spawn '{}' gagal", mob.scriptName(), e);
                }
            }
        }
        return lahir;
    }

    /**
     * Bagian mob dari {@code bll_delete()}: cabut mob dari dunia
     * <b>selamanya</b> — indeks blok, indeks id, dan daftar tik.
     *
     * <p>Bedanya dengan {@link #kill}: yang dibunuh tetap ada dan menunggu
     * kelahiran ulang, yang dihapus tidak pernah kembali. Di C bedanya
     * kelihatan karena {@code bll_delete} memanggil {@code FREE(bl)}.</p>
     */
    public boolean remove(Mob mob, MapRegistry world) {
        MapData map = world.get(mob.m);
        if (map != null && mob.onMap) {
            map.delBlock(mob);
        }
        if (idIndex != null) {
            idIndex.unregister(mob);
        }
        return mobs.remove(mob);
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
