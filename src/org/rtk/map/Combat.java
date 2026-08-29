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
            log.debug("[COMBAT] {} MELESET (critChance {})", sd.name(), sd.critChance);
            return;
        }
        panggil(engine, "swingDamage", penyerang, sasaran);

        bunyiKena(sd, mob);
        ausSenjata(sd);

        int damage = bulatkanDamage(sd);
        log.debug("[COMBAT] {} memukul {}: crit={} damage={} nyawa mob {}",
                sd.name(), mob.displayName(), sd.critChance, damage, mob.currentVita);
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

    /** Keadaan pemain: {@code enum { PC_ALIVE, PC_DIE, ... }} di map.h. */
    private static final int PC_DIE = 1;

    /**
     * pcl_removehealth(damage, penyerang): kurangi nyawa pemain.
     *
     * <p>Argumen keduanya menentukan <b>siapa yang dianggap melukai</b>.
     * Bila diberikan, ia jadi penyerang baru; bila tidak, penyerang lama
     * yang dipakai. Nilai kerusakannya disimpan pada <b>penyerang</b>, bukan
     * pada korban — itulah cara C mengoper angka ke pipeline berikutnya.</p>
     */
    public static void removeHealth(User sd, int damage, long casterId) {
        BlockList penyerang;
        if (casterId > 0) {
            sd.attacker = casterId;
            penyerang = MapServer.blockById(casterId);
            if (penyerang == null) {
                penyerang = MapServer.userById(casterId);
            }
        } else {
            penyerang = MapServer.blockById(sd.attacker);
            if (penyerang == null) {
                penyerang = MapServer.userById(sd.attacker);
            }
        }

        if (penyerang instanceof User tsd) {
            tsd.damage = damage;
            tsd.critChance = 0;
        } else if (penyerang instanceof Mob tmob) {
            tmob.damage = damage;
            tmob.critChance = 0;
        } else {
            sd.damage = damage;
            sd.critChance = 0;
        }

        if (sd.status.state != PC_DIE) {
            takeDamage(sd, damage, 0);
            MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_HPMP);
        }
    }

    /**
     * clif_send_pc_healthscript(): terapkan kerusakan ke nyawa pemain,
     * tampilkan bilah nyawanya, lalu sebarkan kait skrip.
     *
     * <p>Sekali lagi namanya di C menyesatkan — bagian paketnya cuma
     * sepotong; sisanya logika: kerusakan negatif berarti <b>penyembuhan</b>,
     * nyawa dijepit ke maksimum, dan tiga keluarga kait dipanggil
     * ({@code passive_on_takingdamage} sebelum nyawa berubah,
     * {@code passive_on_takedamage} / {@code on_takedamage_while_cast} /
     * {@code on_takedamage} sesudahnya bila masih hidup).</p>
     */
    public static void takeDamage(User sd, int damage, int critical) {
        var engine = MapServer.scriptEngine;
        BlockList penyerangBl = MapServer.blockById(sd.attacker);
        Object penyerang = penyerangBl != null ? penyerangBl : MapServer.userById(sd.attacker);

        org.luaj.vm2.LuaValue korbanRef = engine == null ? null
                : engine.playerRef(sd.scriptPlayer());
        org.luaj.vm2.LuaValue lawanRef = engine == null ? null : refDari(engine, penyerang);

        // Kait ini berjalan SEBELUM nyawa berubah — skrip masih melihat
        // keadaan sebelum pukulan.
        if (engine != null && damage > 0) {
            for (int id : sd.status.spells) {
                if (id > 0) {
                    panggil(engine, MapServer.spellDb.nameOf(id),
                            "passive_on_takingdamage", korbanRef, lawanRef);
                }
            }
        }

        long maxVita = sd.maxHp;
        long vita = sd.status.hp;
        sd.lastVita = vita;
        // Kerusakan NEGATIF menyembuhkan; C memakai cabang terpisah supaya
        // pengurangan tidak pernah menembus nol.
        if (damage < 0) {
            vita -= damage;
        } else {
            vita = vita < damage ? 0 : vita - damage;
        }
        if (vita > maxVita) {
            vita = maxVita;
        }
        sd.status.hp = vita;

        int persen;
        if (vita == 0 || maxVita <= 0) {
            persen = 0;
        } else {
            persen = (int) ((double) vita / (double) maxVita * 100);
            // Sisa nyawa yang membulat jadi 0 tetap ditampilkan 1%, supaya
            // bilahnya tidak terlihat kosong padahal pemain masih hidup.
            if (persen == 0) {
                persen = 1;
            }
        }

        MapServer.clientView.playerHealthChanged(sd, critical, persen, damage);

        if (engine == null) {
            return;
        }
        if (sd.status.hp > 0 && damage > 0) {
            for (int id : sd.status.spells) {
                if (id > 0) {
                    panggil(engine, MapServer.spellDb.nameOf(id),
                            "passive_on_takedamage", korbanRef, lawanRef);
                }
            }
            for (SkillInfo s : sd.status.duraAether) {
                if (s.id > 0 && s.duration > 0) {
                    panggil(engine, MapServer.spellDb.nameOf(s.id),
                            "on_takedamage_while_cast", korbanRef, lawanRef);
                }
            }
            for (int i = 0; i < 14; i++) {
                Item it = sd.status.equipAt(i);
                if (it != null && it.id > 0) {
                    panggil(engine, MapServer.itemDb.info(it.id).name(),
                            "on_takedamage", korbanRef, lawanRef);
                }
            }
        }

        if (sd.status.hp == 0) {
            panggil(engine, "onDeathPlayer", null, korbanRef, null);
            if (penyerang instanceof User pembunuh) {
                panggil(engine, "onKill", null, korbanRef,
                        engine.playerRef(pembunuh.scriptPlayer()));
            }
            for (Item it : sd.status.inventory) {
                if (it != null && it.id > 0) {
                    panggil(engine, MapServer.itemDb.info(it.id).name(),
                            "on_death", korbanRef, null);
                }
            }
        }
    }

    private static org.luaj.vm2.LuaValue refDari(org.rtk.map.script.ScriptEngine engine,
                                                 Object o) {
        if (o == null) {
            return null;
        }
        return o instanceof User u ? engine.playerRef(u.scriptPlayer()) : engine.objectRef(o);
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
     * clif_deductduraequip() — sapu <b>seluruh perlengkapan</b>: ausi 10%,
     * peringatkan saat menipis, dan hancurkan yang habis.
     *
     * <p>Dipanggil skrip saat pemain mati. Barang yang hancur ditampung di
     * {@link User#bodItems}, lalu kait {@code characterLog.bodLog} dipanggil
     * <b>sekali</b> di akhir sebelum daftarnya dikosongkan.</p>
     *
     * <p>⚠️ <b>Perlindungan MENYELAMATKAN barangnya, dan menghentikan
     * seluruh sapuan.</b> Bila barangnya terlindungi, C mengurangi satu
     * perlindungan, memulihkan ketahanannya penuh, memanggil kait
     * {@code equipRestore}, lalu <b>{@code return 0}</b> — dari dalam
     * perulangan. Jadi slot-slot sesudahnya <b>tidak ikut diperiksa</b> pada
     * pemanggilan itu, dan {@code bodLog} pun tidak dipanggil. Terlihat
     * seperti kelalaian, tetapi ditiru apa adanya.</p>
     */
    public static void deductDuraEquip(org.rtk.map.script.ScriptEngine engine, User sd) {
        var map = MapServer.world.get(sd.m);
        boolean pvp = map != null && map.pvp != 0;

        for (int slot = 0; slot < 14; slot++) {
            Item it = sd.status.equipAt(slot);
            if (it == null || it.id <= 0) {
                continue;
            }
            if (pvp) {
                return;   // C: return, bukan continue — sapuan berhenti total
            }
            sd.equipSlot = slot;

            int max = MapServer.itemDb.info(it.id).durability();
            it.dura -= (int) Math.floor(max * 0.10);
            peringatkanDura(sd, it, max);

            boolean hancur = it.dura <= 0
                    || (sd.status.state == PC_DIE && MapServer.itemDb.breaksOnDeath(it.id));
            if (!hancur) {
                continue;
            }

            if (MapServer.itemDb.protectedOf(it.id) > 0 || it.protectedFlag >= 1) {
                it.protectedFlag -= 1;
                it.dura = max;
                MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_ALL);
                MapServer.clientView.messageToPlayer(sd, 5,
                        "Perlengkapanmu terselamatkan!");
                kait(engine, sd, "characterLog", "equipRestore");
                return;   // ⚠️ lihat catatan di atas: sapuan berhenti di sini
            }

            sd.bodItems.add(salin(it));
            kait(engine, sd, "characterLog", "equipBreak");
            sd.breakId = it.id;
            kait(engine, sd, "onBreak", null);
            kait(engine, sd, MapServer.itemDb.info(it.id).name(), "on_break");
            sd.status.equip.remove(it);

            MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_ALL);
            MapServer.clientView.messageToPlayer(sd, 5, "Perlengkapanmu hancur!");
        }

        kait(engine, sd, "characterLog", "bodLog");
        sd.bodItems.clear();
    }

    /**
     * clif_checkinvbod() — sapu <b>inventaris</b> untuk barang yang hancur
     * saat pemiliknya mati ({@code ItmBoD}).
     *
     * <p>Bedanya dari {@link #deductDuraEquip}: tidak ada pengausan sama
     * sekali di sini. Barang inventaris hanya hancur bila pemainnya
     * <b>sedang mati</b> dan jenisnya memang ber-BoD.</p>
     *
     * <p>⚠️ Perlindungan di sini juga menghentikan seluruh sapuan, sama
     * seperti pada perlengkapan.</p>
     */
    public static void checkInvBod(org.rtk.map.script.ScriptEngine engine, User sd) {
        for (int x = sd.status.inventory.size() - 1; x >= 0; x--) {
            Item it = sd.status.inventory.get(x);
            if (it.id <= 0) {
                continue;
            }
            sd.invSlot = x;
            if (sd.status.state != PC_DIE || !MapServer.itemDb.breaksOnDeath(it.id)) {
                continue;
            }

            if (MapServer.itemDb.protectedOf(it.id) > 0 || it.protectedFlag >= 1) {
                it.protectedFlag -= 1;
                it.dura = MapServer.itemDb.info(it.id).durability();
                MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_ALL);
                MapServer.clientView.messageToPlayer(sd, 5, "Barangmu terselamatkan!");
                kait(engine, sd, "characterLog", "invRestore");
                return;   // sama seperti di perlengkapan: sapuan berhenti
            }

            sd.bodItems.add(salin(it));
            kait(engine, sd, "characterLog", "invBreak");
            sd.breakId = it.id;
            kait(engine, sd, "onBreak", null);
            kait(engine, sd, MapServer.itemDb.info(it.id).name(), "on_break");

            // pc_delitem(sd, x, 1, 9): 9 = "diberikan/hilang"
            sd.clearInventorySlot(x);
            MapServer.clientView.playerInventorySlotCleared(sd, x, 9);
            MapServer.clientView.messageToPlayer(sd, 5, "Barangmu hancur!");
            MapServer.clientView.objectAppearanceChanged(sd);
        }

        kait(engine, sd, "characterLog", "bodLog");
        sd.bodItems.clear();
    }

    /**
     * pcl_expireitem() — buang barang yang masa berlakunya habis, baik di
     * inventaris maupun yang sedang dikenakan.
     *
     * <p>Dua sumber waktu, dan <b>salah satunya cukup</b>: waktu pada
     * barangnya sendiri ({@code item.time}) atau bawaan jenisnya
     * ({@code ItmTimer}). Keduanya <b>waktu mutlak</b> (detik epoch), bukan
     * lama pakai.</p>
     *
     * <p>⚠️ Cabang perlengkapan di C <b>rusak dan tidak ditiru</b>: ia
     * menghitung satu slot inventaris kosong (`eqdel`) <i>sekali</i> sebelum
     * perulangan, lalu untuk tiap perlengkapan yang kedaluwarsa memanggil
     * {@code pc_unequip} disusul {@code pc_delitem(sd, eqdel, ...)} —
     * menghapus apa pun yang kebetulan ada di slot itu, dan slot yang sama
     * lagi untuk barang kedua. Di sini perlengkapan yang kedaluwarsa
     * langsung dibuang tanpa singgah di inventaris.</p>
     */
    public static void expireItems(User sd) {
        long sekarang = System.currentTimeMillis() / 1000;

        for (int x = sd.status.inventory.size() - 1; x >= 0; x--) {
            Item it = sd.status.inventory.get(x);
            if (kedaluwarsa(it, sekarang)) {
                MapServer.clientView.messageToPlayer(sd, 5,
                        "Masa berlaku barangmu sudah habis.");
                // pc_delitem(sd, x, 1, 8): 8 = "lapuk"
                sd.clearInventorySlot(x);
                MapServer.clientView.playerInventorySlotCleared(sd, x, 8);
            }
        }

        for (int slot = 0; slot < 14; slot++) {
            Item it = sd.status.equipAt(slot);
            if (it != null && kedaluwarsa(it, sekarang)) {
                MapServer.clientView.messageToPlayer(sd, 5,
                        "Masa berlaku perlengkapanmu sudah habis.");
                sd.status.equip.remove(it);
                MapServer.clientView.objectAppearanceChanged(sd);
            }
        }
    }

    private static boolean kedaluwarsa(Item it, long sekarang) {
        if (it == null || it.id <= 0) {
            return false;
        }
        long bawaan = MapServer.itemDb.expiresAt(it.id);
        return (it.time > 0 && it.time < sekarang) || (bawaan > 0 && bawaan < sekarang);
    }

    /** Salinan dangkal barang, supaya daftar BOD tidak menunjuk objek yang dibuang. */
    private static Item salin(Item src) {
        Item c = new Item();
        c.id = src.id;
        c.amount = src.amount;
        c.dura = src.dura;
        c.owner = src.owner;
        c.protectedFlag = src.protectedFlag;
        c.custom = src.custom;
        c.customIcon = src.customIcon;
        c.customIconColor = src.customIconColor;
        c.customLook = src.customLook;
        c.customLookColor = src.customLookColor;
        c.realName = src.realName;
        c.note = src.note;
        c.time = src.time;
        c.pos = src.pos;
        return c;
    }

    private static void kait(org.rtk.map.script.ScriptEngine engine, User sd,
                             String root, String method) {
        if (engine == null || root == null || root.isEmpty()) {
            return;
        }
        try {
            engine.doScript(root, method, engine.playerRef(sd.scriptPlayer()));
        } catch (RuntimeException e) {
            log.error("[BOD] kait '{}.{}' gagal", root, method, e);
        }
    }

    /**
     * clif_deductarmor(): tiap perlengkapan yang dikenakan punya
     * <b>peluang 50%</b> ikut aus terkena satu pukulan.
     *
     * <p>C menulisnya sebagai 14 blok {@code if} yang isinya identik, satu
     * per slot 0..13 — bukan sebuah daftar pilihan. Karena itu perulangan
     * di sini setara persis, bukan penyederhanaan yang mengubah perilaku.</p>
     */
    public static void deductArmor(User sd, int hit) {
        for (int slot = 0; slot < 14; slot++) {
            Item it = sd.status.equipAt(slot);
            if (it == null || it.id <= 0) {
                continue;
            }
            if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) > 50) {
                deductDura(sd, slot, hit);
            }
        }
    }

    /** clif_deductweapon(): hanya senjata, dengan peluang 50% yang sama. */
    public static void deductWeapon(User sd, int hit) {
        Item it = sd.status.equipAt(Equip.WEAP);
        if (it != null && it.id > 0
                && java.util.concurrent.ThreadLocalRandom.current().nextInt(100) > 50) {
            deductDura(sd, Equip.WEAP, hit);
        }
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
        peringatkanDura(sd, it, max);
    }

    /**
     * Ambang peringatan ketahanan: 50%, 25%, 10%, 5%, 1%.
     *
     * <p>Bendera {@code repair} naik satu tiap ambang terlewati, dan tiap
     * ambang hanya berbunyi bila bendera itu <b>persis</b> satu tingkat di
     * bawahnya. Itu yang membuat peringatannya muncul sekali per ambang,
     * berurutan, alih-alih berteriak tiap pukulan.</p>
     *
     * <p>Sebelumnya hanya ambang 50% yang diport, jadi barang yang sudah
     * menipis lewat itu tidak pernah memperingatkan lagi.</p>
     */
    private static void peringatkanDura(User sd, Item it, int max) {
        if (max <= 0) {
            return;
        }
        float persen = (float) it.dura / (float) max;
        String nama = MapServer.itemDb.info(it.id).tampilan();
        int[] ambang = {50, 25, 10, 5, 1};
        for (int i = 0; i < ambang.length; i++) {
            if (persen <= ambang[i] / 100.0f && it.repair == i) {
                MapServer.clientView.messageToPlayer(sd, 5,
                        nama + " tinggal " + ambang[i] + "%.");
                it.repair = i + 1;
            }
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
