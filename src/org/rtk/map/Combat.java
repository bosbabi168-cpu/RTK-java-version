package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.Item;
import org.rtk.common.mmo.SkillInfo;
import org.rtk.map.data.BlockList;
import org.rtk.map.data.Equip;

/**
 * Pipeline serangan jarak dekat — port {@code pcl_swingtarget},
 * {@code clif_mob_damage}, dan {@code clif_pc_damage}.
 *
 * <h2>Ini logika, bukan paket</h2>
 *
 * <p>Nama {@code clif_*} di sumber C menyesatkan: ketiganya nyaris tidak
 * mengirim paket. Isinya memanggil skrip Lua yang menentukan <b>apakah
 * pukulan kena</b> ({@code hitCritChance} mengisi {@code critchance}) dan
 * <b>berapa kerusakannya</b> ({@code swingDamage} mengisi {@code damage}),
 * lalu menyebarkan kait ke perlengkapan, mantra pasif, dan mantra yang
 * sedang aktif. Tugas port-nya menyediakan JALUR-nya, bukan menulis ulang
 * aturan mainnya — persis pelajaran besar A4-A5.</p>
 *
 * <h2>Kuirk C yang sengaja ditiru</h2>
 *
 * <ul>
 *   <li><b>Suara pukulan selalu 0.</b> C memanggil
 *       {@code itemdb_soundhit()}, tapi ladang {@code sound_hit} tidak
 *       pernah diisi dari SQL — ia hanya ada di struct. Jadi bunyi
 *       kena-pukul di server asli tidak pernah berbeda per senjata.
 *       Suara <i>ayunan</i> ({@code ItmSound}) memang dimuat dan dipakai.</li>
 *   <li><b>Durabilitas selalu berkurang.</b> Penjaga {@code itemdb_ethereal()}
 *       juga tidak pernah diisi dari SQL, sehingga cabang "barang halus tidak
 *       aus" tidak pernah tercapai.</li>
 *   <li><b>Pembulatan kerusakan</b> memakai {@code (int)(damage += 0.5f)} —
 *       menambah 0,5 lalu memotong, dan <b>menyimpan balik</b> nilai yang
 *       sudah bertambah itu ke pemain.</li>
 * </ul>
 */
public final class Combat {

    private static final Logger log = LogManager.getLogger(Combat.class);

    /** Peluang aus senjata per pukulan: C memakai {@code rnd(100) > 75}. */
    private static final int AMBANG_AUS = 75;

    private Combat() {
    }

    /**
     * pcl_swingtarget(): pemain mengayun ke sebuah sasaran.
     *
     * <p>Bunyi ayunan dimainkan di posisi <b>penyerang</b>, sedangkan bunyi
     * kena dimainkan di posisi <b>sasaran</b> — dua panggilan berbeda di C.</p>
     */
    public static void swingAt(User sd, BlockList target) {
        if (sd == null || target == null) {
            return;
        }
        Item senjata = sd.status.equipAt(Equip.WEAP);
        if (senjata != null && senjata.id > 0) {
            MapServer.clientView.soundPlayed(sd,
                    MapServer.itemDb.info(senjata.id).sound());
        }
        if (target instanceof Mob mob) {
            mobDamage(sd, mob);
        } else if (target instanceof User tsd) {
            pcDamage(sd, tsd);
        }
    }

    /** clif_mob_damage(): pemain memukul mob. */
    static void mobDamage(User sd, Mob mob) {
        if (mob.state == MobData.MOB_DEAD) {
            return;
        }
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        var penyerang = engine.playerRef(sd.scriptPlayer());
        var sasaran = engine.objectRef(mob);

        // Lua yang memutuskan kena atau tidak, lalu berapa kerusakannya.
        panggil(engine, "hitCritChance", penyerang, sasaran);
        if (sd.critChance <= 0) {
            return;
        }
        panggil(engine, "swingDamage", penyerang, sasaran);

        bunyiKena(sd, mob);
        ausSenjata(sd);

        int damage = bulatkanDamage(sd);
        mob.addThreat(sd.id, damage);

        kaitPerlengkapan(engine, sd, penyerang, sasaran);
        kaitMantra(engine, sd, penyerang, sasaran);

        MobRegistry.fireAttacked(engine, mob, sd);
    }

    /** clif_pc_damage(): pemain memukul pemain lain. */
    static void pcDamage(User sd, User target) {
        if (target.status.state == 1) {
            return; // sasaran sedang mati
        }
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        var penyerang = engine.playerRef(sd.scriptPlayer());
        var sasaran = engine.playerRef(target.scriptPlayer());

        panggil(engine, "hitCritChance", penyerang, sasaran);
        if (sd.critChance <= 0) {
            return;
        }
        panggil(engine, "swingDamage", penyerang, sasaran);
        bulatkanDamage(sd);

        bunyiKena(sd, target);
        kaitPerlengkapan(engine, sd, penyerang, sasaran);
    }

    // ------------------------------------------------------------------

    /**
     * {@code damage = (int)(sd->damage += 0.5f)} — perhatikan {@code +=}:
     * nilai yang sudah ditambah 0,5 ikut TERSIMPAN kembali ke pemain, bukan
     * hanya dipakai sekali. Ditiru apa adanya.
     */
    private static int bulatkanDamage(User sd) {
        sd.damage += 0.5f;
        return (int) sd.damage;
    }

    /**
     * C memanggil {@code itemdb_soundhit()} yang selalu 0 (lihat catatan
     * kelas). Dipertahankan sebagai panggilan tersendiri supaya jelas bahwa
     * ini bunyi KENA di posisi sasaran, bukan bunyi ayunan.
     */
    private static void bunyiKena(User sd, BlockList sasaran) {
        Item senjata = sd.status.equipAt(Equip.WEAP);
        if (senjata != null && senjata.id > 0) {
            MapServer.clientView.soundPlayed(sasaran, 0);
        }
    }

    private static void ausSenjata(User sd) {
        // C: rnd(100) > 75. Logika permainan satu thread per server, jadi
        // ThreadLocalRandom aman dan tanpa kontensi.
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) > AMBANG_AUS) {
            deductDura(sd, Equip.WEAP, 1);
        }
    }

    /**
     * clif_deductdura(): kurangi durabilitas satu slot perlengkapan.
     * Tidak berlaku di peta PvP — di sana senjata tidak aus.
     */
    public static void deductDura(User sd, int slot, int val) {
        Item it = sd.status.equipAt(slot);
        if (it == null || it.id <= 0) {
            return;
        }
        var map = MapServer.world.get(sd.m);
        if (map != null && map.pvp != 0) {
            return;
        }
        it.dura -= val;
        checkDura(sd, slot);
    }

    /**
     * clif_checkdura(): peringatkan pemain saat durabilitas menipis.
     * Bendera {@code repair} menjaga peringatannya hanya muncul sekali.
     */
    public static void checkDura(User sd, int slot) {
        Item it = sd.status.equipAt(slot);
        if (it == null || it.id <= 0) {
            return;
        }
        int max = MapServer.itemDb.info(it.id).durability();
        if (max <= 0) {
            return;
        }
        float persen = (float) it.dura / (float) max;
        if (persen <= 0.5f && it.repair == 0) {
            MapServer.clientView.messageToPlayer(sd, 5,
                    "Your " + MapServer.itemDb.info(it.id).tampilan() + " is at 50%.");
            it.repair = 1;
        }
    }

    /** Kait `on_hit` pada tiap barang yang dikenakan (14 slot pertama di C). */
    private static void kaitPerlengkapan(org.rtk.map.script.ScriptEngine engine, User sd,
                                         org.luaj.vm2.LuaValue penyerang,
                                         org.luaj.vm2.LuaValue sasaran) {
        for (int i = 0; i < 14; i++) {
            Item it = sd.status.equipAt(i);
            if (it != null && it.id > 0) {
                String yname = MapServer.itemDb.info(it.id).name();
                if (yname != null && !yname.isEmpty()) {
                    panggil(engine, yname, "on_hit", penyerang, sasaran);
                }
            }
        }
    }

    /** Kait `passive_on_hit` (buku mantra) dan `on_hit_while_cast` (yang aktif). */
    private static void kaitMantra(org.rtk.map.script.ScriptEngine engine, User sd,
                                   org.luaj.vm2.LuaValue penyerang,
                                   org.luaj.vm2.LuaValue sasaran) {
        for (int id : sd.status.spells) {
            if (id > 0) {
                String yname = MapServer.spellDb.nameOf(id);
                if (yname != null && !yname.isEmpty()) {
                    panggil(engine, yname, "passive_on_hit", penyerang, sasaran);
                }
            }
        }
        for (SkillInfo s : sd.status.duraAether) {
            if (s.id > 0 && s.duration > 0) {
                String yname = MapServer.spellDb.nameOf(s.id);
                if (yname != null && !yname.isEmpty()) {
                    panggil(engine, yname, "on_hit_while_cast", penyerang, sasaran);
                }
            }
        }
    }

    /** Kait tanpa nama method: `sl_doscript_blargs(root, NULL, ...)` di C. */
    private static void panggil(org.rtk.map.script.ScriptEngine engine, String root,
                                org.luaj.vm2.LuaValue a, org.luaj.vm2.LuaValue b) {
        panggil(engine, root, null, a, b);
    }

    private static void panggil(org.rtk.map.script.ScriptEngine engine, String root,
                                String method,
                                org.luaj.vm2.LuaValue a, org.luaj.vm2.LuaValue b) {
        try {
            engine.doScript(root, method, a, b);
        } catch (RuntimeException e) {
            log.error("[COMBAT] kait '{}' pada '{}' gagal", method, root, e);
        }
    }
}
