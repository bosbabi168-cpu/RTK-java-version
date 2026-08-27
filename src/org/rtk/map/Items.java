package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.luaj.vm2.LuaValue;

import org.rtk.common.mmo.Item;
import org.rtk.common.mmo.SkillInfo;
import org.rtk.map.data.Equip;
import org.rtk.map.data.ItemDb;
import org.rtk.map.data.MapData;
import org.rtk.map.data.MapMsg;

/**
 * Memakai, mengenakan, melepas, dan melempar barang — port
 * {@code pc_useitem}, {@code pc_equipitem}, {@code pc_equipscript},
 * {@code pc_unequip}, {@code pc_unequipscript} (map/pc.c) dan
 * {@code clif_throwitem_script} (map/clif.c).
 *
 * <h2>Server hanya menyiapkan; SKRIP yang memindahkan barangnya</h2>
 *
 * <p>Pola yang sama dengan bicara, memungut, dan menyerang (Peringatan #58),
 * tetapi di sini bolak-balik <b>dua kali</b> dan itu yang paling mudah salah
 * dibaca:</p>
 *
 * <pre>
 *   klien: "pakai slot 3"
 *     -&gt; {@link #useItem}      guard, lalu {@link #equipItem} bila perlengkapan
 *     -&gt; {@link #equipItem}    guard + syarat, simpan equipId/invSlot,
 *                              panggil kait `onEquip`
 *     -&gt; skrip `onEquip`       memeriksa state, lalu memanggil player:equip()
 *     -&gt; {@link #equipScript}  BARU DI SINI barangnya benar-benar berpindah
 * </pre>
 *
 * <p>Artinya {@code equipItem} <b>tidak memasang apa pun</b>, dan
 * {@code equipScript} <b>tidak memeriksa syarat apa pun</b>. Menggabungkan
 * keduanya akan melewati kait konten yang ditumpangi banyak barang khusus,
 * dan sebaliknya memindahkan syarat ke {@code equipScript} membuat
 * {@code forceEquip} ikut tertahan.</p>
 *
 * <p>Keadaan yang menghubungkan keduanya ada di {@link User}:
 * {@code equipId}, {@code invSlot}, {@code takeOffId}. Itu sebabnya ketiganya
 * ladang pemain, bukan parameter.</p>
 */
public final class Items {

    private static final Logger log = LogManager.getLogger(Items.class);

    /** Tipe pesan 0x0A: 3 = mini text. */
    private static final int MSG_MINI = 3;

    /** {@code status.state}: 1 arwah, 2 menyamar, 3 menunggang, 4 berubah wujud. */
    private static final int ST_SPIRIT = 1;
    private static final int ST_INVIS = 2;
    private static final int ST_MOUNT = 3;
    private static final int ST_MORPH = 4;

    /** Rentang id barang tunggangan, dari cabang PC_MOUNTED di pc_useitem. */
    private static final int MOUNT_ID_MIN = 15000;
    private static final int MOUNT_ID_MAX = 17000;

    /** Rentang {@code ItmLook} senjata dua tangan. */
    private static final int TWOHAND_LOOK_MIN = 10000;
    private static final int TWOHAND_LOOK_MAX = 29999;

    /**
     * Kunci pesan "sudah dikenakan" per slot.
     *
     * <p>⚠️ {@code EQ_FACEACCTWO} sengaja null: {@code clif_sendequip} punya
     * {@code default: return -1} untuk slot itu, jadi memasangnya tidak
     * memberi tahu pemain apa pun. Ditiru apa adanya.</p>
     */
    private static final String[] KUNCI_SLOT = {
        "MAP_EQWEAP", "MAP_EQARMOR", "MAP_EQSHIELD", "MAP_EQHELM",
        "MAP_EQLEFT", "MAP_EQRIGHT", "MAP_EQSUBLEFT", "MAP_EQSUBRIGHT",
        "MAP_EQFACEACC", "MAP_EQCROWN", "MAP_EQMANTLE", "MAP_EQNECKLACE",
        "MAP_EQBOOTS", "MAP_EQCOAT", null
    };

    private Items() {
    }

    // ==================================================================
    // pc_useitem
    // ==================================================================

    /**
     * pc_useitem(): satu pintu untuk memakan, memakai, dan mengenakan.
     *
     * <p>Ketiga paket RetroTK yang berbeda ({@code 0x1A} makan,
     * {@code 0x1C} pakai, {@code 0x1E} kenakan) semuanya bermuara di sini;
     * yang berbeda hanya penyaring jenis barang <b>sebelum</b> memanggilnya.
     * Karena itu ketiga perintah di {@code ClientCommands} tetap terpisah,
     * tetapi badannya satu.</p>
     */
    public static void useItem(User sd, int slot) {
        if (sd == null || slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        Item it = sd.status.inventory.get(slot);
        if (it.id <= 0) {
            return;
        }
        if (it.owner != 0 && it.owner != sd.status.id) {
            pesan(sd, "You cannot use this, it does not belong to you!");
            return;
        }

        ItemDb.Info info = MapServer.itemDb.info(it.id);
        int jenis = info.type();

        // Slot tujuan yang akan tertimpa. ⚠️ Di C ini `itemdb_type - 3` tanpa
        // batas atas, sehingga barang bukan-perlengkapan (ITM_ETC = 18 dan
        // ke atas) MEMBACA DI LUAR larik equip[]. Di sini rentangnya dijaga;
        // efeknya sama untuk barang yang sah, tanpa perilaku tak tentu.
        int slotEq = jenis - 3;
        if (slotEq >= 0 && slotEq < Equip.COUNT && !sd.isGm()) {
            Item dipakai = sd.status.equipAt(slotEq);
            if (dipakai != null && dipakai.id > 0
                    && MapServer.itemDb.cannotBeUnequipped(dipakai.id)) {
                pesan(sd, "You are unable to unequip that.");
                return;
            }
        }

        if (!syaratJalur(sd, info)) {
            return;
        }
        if (sd.status.state == ST_SPIRIT) {
            pesan(sd, MapMsg.of("MAP_ERRGHOST"));
            return;
        }
        if (sd.status.state == ST_MOUNT) {
            if (it.id > MOUNT_ID_MIN && it.id < MOUNT_ID_MAX) {
                // Memakai tunggangan saat sedang menunggang = turun.
                kait(sd, "onDismount", null);
                return;
            }
            pesan(sd, MapMsg.of("MAP_ERRMOUNT"));
            return;
        }

        // itemdb_time(): umur barang mulai dihitung saat DIPAKAI PERTAMA
        // KALI, bukan saat didapat. Karena itu penjaganya `!it.time`.
        if (info.expiresAt() > 0 && it.time == 0) {
            it.time = System.currentTimeMillis() / 1000 + info.expiresAt();
        }

        MapData map = MapServer.world.get(sd.m);
        String yname = info.name();

        switch (jenis) {
            case ItemDb.ITM_EAT -> {
                if (!bolehDiPeta(sd, map == null ? 0 : map.canEat, "You cannot eat this here.")) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "use", null);
                delItem(sd, slot, 1, 2);
            }
            case ItemDb.ITM_USE -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "use", null);
                delItem(sd, slot, 1, 6);
            }
            case ItemDb.ITM_SMOKE -> {
                if (!bolehDiPeta(sd, map == null ? 0 : map.canSmoke,
                        "You cannot smoke this here.")) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "use", null);
                // ⚠️ Rokok memakai KETAHANAN sebagai sisa isap, bukan sebagai
                // keausan: tiap pakai berkurang satu, habis berarti hilang.
                it.dura -= 1;
                if (it.dura == 0) {
                    delItem(sd, slot, 1, 3);
                } else {
                    MapServer.clientView.playerInventorySlotChanged(sd, slot);
                }
            }
            // Sepuluh jenis berikut sama persis kecuali kait globalnya.
            case ItemDb.ITM_USESPC, ItemDb.ITM_BAG, ItemDb.ITM_ETC -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "use", null);
            }
            case ItemDb.ITM_MAP -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                // ⚠️ Satu-satunya yang kait globalnya BUKAN fungsi lepas:
                // `maps.use`, bukan `use`. Menyeragamkannya membuat peta
                // gulung tidak pernah terbuka.
                pakaiLalu(sd, slot, yname, "maps", "use");
            }
            case ItemDb.ITM_MOUNT -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "onMountItem", null);
            }
            case ItemDb.ITM_FACE -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "useFace", null);
            }
            case ItemDb.ITM_SET -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "useSetItem", null);
            }
            case ItemDb.ITM_SKIN -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "useSkinItem", null);
            }
            case ItemDb.ITM_HAIR_DYE -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "useHairDye", null);
            }
            case ItemDb.ITM_FACEACCTWO -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                pakaiLalu(sd, slot, yname, "useBeardItem", null);
            }
            case ItemDb.ITM_QUIVER -> {
                if (!bolehPakai(sd, map)) {
                    return;
                }
                // ⚠️ Badan aslinya DIKOMENTARI SELURUHNYA di C — yang tersisa
                // hanya pesan ini. Anak panah memang dipakai lewat busur,
                // bukan lewat klik. Mengisinya dengan tebakan akan mengubah
                // perilaku, bukan memperbaikinya.
                pesan(sd, "This item is only usable with a bow.");
            }
            case ItemDb.ITM_WEAP, ItemDb.ITM_ARMOR, ItemDb.ITM_SHIELD,
                 ItemDb.ITM_HELM, ItemDb.ITM_LEFT, ItemDb.ITM_RIGHT,
                 ItemDb.ITM_SUBLEFT, ItemDb.ITM_SUBRIGHT, ItemDb.ITM_FACEACC,
                 ItemDb.ITM_CROWN, ItemDb.ITM_MANTLE, ItemDb.ITM_NECKLACE,
                 ItemDb.ITM_BOOTS, ItemDb.ITM_COAT, ItemDb.ITM_HAND -> {
                if (map != null && map.canEquip == 0 && !sd.isGm()) {
                    pesan(sd, "You cannot equip/de-equip on this map.");
                    return;
                }
                equipItem(sd, slot);
            }
            default -> log.debug("[ITEM] jenis {} tidak punya perilaku pakai", jenis);
        }
    }

    /**
     * Syarat jalur dan tanda ({@code itemdb_class} / {@code itemdb_rank}).
     *
     * <p>⚠️ Tiga cabangnya tidak simetris, dan itu ada di C: jalur <b>5</b>
     * (GM) melewati semuanya; syarat di bawah 6 dicocokkan ke <b>jalur
     * dasar</b> pemain; syarat 6 ke atas dicocokkan ke <b>kelas</b> pemain
     * persis. Menyeragamkan ketiganya mengunci seluruh barang subpath.</p>
     */
    private static boolean syaratJalur(User sd, ItemDb.Info info) {
        int syarat = info.reqs().path();
        if (syarat == 0) {
            return true;   // tanpa syarat: tanda pun tidak diperiksa
        }
        int jalurPemain = MapServer.classDb.pathOf(sd.status.charClass);
        if (jalurPemain == 5) {
            return true;
        }
        if (syarat < 6) {
            if (jalurPemain != syarat) {
                pesan(sd, MapMsg.of("MAP_ERRITMPATH"));
                return false;
            }
        } else if (sd.status.charClass != syarat) {
            pesan(sd, MapMsg.of("MAP_ERRITMPATH"));
            return false;
        }
        if (sd.status.mark < info.reqs().mark()) {
            pesan(sd, MapMsg.of("MAP_ERRITMMARK"));
            return false;
        }
        return true;
    }

    private static boolean bolehPakai(User sd, MapData map) {
        return bolehDiPeta(sd, map == null ? 0 : map.canUse, "You cannot use this here.");
    }

    private static boolean bolehDiPeta(User sd, int bendera, String tolakan) {
        if (bendera == 0 && !sd.isGm()) {
            pesan(sd, tolakan);
            return false;
        }
        return true;
    }

    /**
     * Bagian yang sama di tiap cabang: catat slotnya, buang percakapan yang
     * sedang berjalan, jalankan kait barangnya lalu kait globalnya.
     *
     * <p>⚠️ {@code sl_async_freeco} (membuang coroutine) dipanggil
     * <b>sebelum</b> skripnya, bukan sesudah. Tanpa itu, memakai barang di
     * tengah percakapan NPC akan menumpuk dua coroutine pada pemain yang
     * sama dan jawaban berikutnya masuk ke yang salah.</p>
     */
    private static void pakaiLalu(User sd, int slot, String yname,
                                  String rootGlobal, String methodGlobal) {
        sd.invSlot = slot;
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        engine.cancel(sd.scriptPlayer());
        LuaValue ref = engine.playerRef(sd.scriptPlayer());
        kait(sd, yname, "use", ref);
        kait(sd, rootGlobal, methodGlobal, ref);
        MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
    }

    // ==================================================================
    // mengenakan
    // ==================================================================

    /**
     * pc_equipitem(): syarat mengenakan, lalu <b>panggil kaitnya</b>.
     *
     * <p>Tidak memindahkan apa pun — lihat catatan dua langkah di kelas ini.
     * Yang memindahkan {@link #equipScript}, dipanggil skrip lewat
     * {@code player:equip()}.</p>
     */
    public static void equipItem(User sd, int slot) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        Item it = sd.status.inventory.get(slot);
        if (it.id <= 0) {
            return;
        }
        if (sd.status.state != 0 && !sd.isGm()) {
            pesanKeadaan(sd);
            return;
        }
        // ⚠️ Di C pembandingnya `sd->bl.id` di sini, tetapi `sd->status.id`
        // di pc_useitem — dua id yang berbeda untuk pemeriksaan yang sama.
        // Yang dipakai di sini id blok, ditiru apa adanya.
        if (it.owner != 0 && it.owner != sd.id) {
            pesan(sd, "This does not belong to you.");
            return;
        }

        String tolak = syaratKenakan(sd, it.id);
        if (tolak != null) {
            pesan(sd, tolak);
            return;
        }

        ItemDb.Info info = MapServer.itemDb.info(it.id);
        int slotEq = info.type() - 3;
        if (slotEq < 0 || slotEq >= Equip.COUNT) {
            return;   // bukan perlengkapan
        }
        if (!syaratNilai(sd, info)) {
            pesan(sd, "Your stats are too low to equip that.");
            return;
        }

        sd.equipId = it.id;
        sd.invSlot = slot;
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        LuaValue ref = engine.playerRef(sd.scriptPlayer());
        kait(sd, "onEquip", null, ref);
        kait(sd, info.name(), "onEquip", ref);
    }

    /**
     * pc_canequipitem(): senjata dua tangan, level, might, dan jenis kelamin.
     *
     * @return pesan penolakan, atau null bila boleh
     */
    private static String syaratKenakan(User sd, long itemId) {
        ItemDb.Info info = MapServer.itemDb.info(itemId);
        Item senjata = sd.status.equipAt(Equip.WEAP);
        if (senjata != null && senjata.id > 0 && info.type() == ItemDb.ITM_SHIELD
                && duaTangan(senjata.id)) {
            return MapMsg.of("MAP_ERRITM2H");
        }
        Item perisai = sd.status.equipAt(Equip.SHIELD);
        if (perisai != null && perisai.id > 0 && info.type() == ItemDb.ITM_WEAP
                && duaTangan(itemId)) {
            return MapMsg.of("MAP_ERRITM2H");
        }
        if (sd.status.level < info.reqs().level()) {
            return MapMsg.of("MAP_ERRITMLEVEL");
        }
        if (sd.might < info.reqs().might()) {
            return MapMsg.of("MAP_ERRITMMIGHT");
        }
        // ItmSex 2 berarti untuk siapa saja.
        if (sd.status.sex != info.reqs().sex() && info.reqs().sex() != 2) {
            return MapMsg.of("MAP_ERRITMSEX");
        }
        return null;
    }

    private static boolean duaTangan(long itemId) {
        int look = MapServer.itemDb.info(itemId).look().look();
        return look >= TWOHAND_LOOK_MIN && look <= TWOHAND_LOOK_MAX;
    }

    /**
     * pc_canequipstats(): hanya bonus <b>negatif</b> yang bisa menolak.
     *
     * <p>Barang yang mengurangi darah lebih banyak dari yang dimiliki pemain
     * tidak boleh dikenakan — kalau tidak, memakainya langsung mematikan.
     * Bonus positif tidak pernah jadi syarat, walau namanya terdengar
     * begitu.</p>
     */
    private static boolean syaratNilai(User sd, ItemDb.Info info) {
        int vita = info.stats().vita();
        int mana = info.stats().mana();
        if (vita < 0 && Math.abs(vita) > sd.maxHp) {
            return false;
        }
        return !(mana < 0 && Math.abs(mana) > sd.maxMp);
    }

    /**
     * pc_equipscript(): <b>di sinilah</b> barangnya berpindah.
     *
     * <p>Dipanggil skrip lewat {@code player:equip()} dari kait
     * {@code onEquip}, memakai {@code equipId} dan {@code invSlot} yang
     * sudah disiapkan {@link #equipItem}.</p>
     */
    public static void equipScript(User sd) {
        if (sd.equipId <= 0) {
            return;
        }
        ItemDb.Info info = MapServer.itemDb.info(sd.equipId);
        int slotEq = pasanganKiriKanan(sd, info.type() - 3);
        if (slotEq < 0 || slotEq >= Equip.COUNT) {
            sd.equipId = 0;
            return;
        }
        if (sd.status.state != 0 && !sd.isGm()) {
            pesanKeadaan(sd);
            return;
        }

        Item ada = sd.status.equipAt(slotEq);
        if (ada != null && ada.id > 0) {
            // Slotnya terisi: lepaskan dulu. Pemasangannya menyusul lewat
            // pc_unequipscript, yang melihat equipId masih terisi dan
            // menukar keduanya sekaligus.
            sd.target = sd.id;
            sd.attacker = sd.id;
            sd.takeOffId = slotEq;
            var engine = MapServer.scriptEngine;
            if (engine != null) {
                LuaValue ref = engine.playerRef(sd.scriptPlayer());
                kait(sd, "onUnequip", null, ref);
                kait(sd, info.name(), "equip", ref);
            }
            sd.equipId = 0;
            return;
        }

        if (sd.invSlot < 0 || sd.invSlot >= sd.status.inventory.size()) {
            sd.equipId = 0;
            return;
        }
        Item dari = sd.status.inventory.get(sd.invSlot);
        Item dipakai = salin(dari);
        dipakai.pos = slotEq;
        dipakai.amount = 1;
        sd.status.equip.add(dipakai);
        delItem(sd, sd.invSlot, 1, 6);

        var engine = MapServer.scriptEngine;
        if (engine != null) {
            kait(sd, info.name(), "equip", engine.playerRef(sd.scriptPlayer()));
        }
        sd.equipId = 0;

        lepasPesona(sd, slotEq);
        umumkanSlot(sd, slotEq);
        selesaiUbahPerlengkapan(sd);
    }

    /**
     * Cincin dan anting berpasangan kiri/kanan — dan aturannya <b>tidak
     * simetris</b>.
     *
     * <p>⚠️ Di C ini empat blok {@code if} berurutan, <b>bukan</b>
     * {@code else if}, sehingga hasil blok sebelumnya ikut diuji blok
     * berikutnya. Akibatnya:</p>
     * <ul>
     *   <li>{@code EQ_LEFT} yang penuh sementara kanan kosong <b>pindah</b>
     *       ke kanan — seperti yang diharapkan;</li>
     *   <li>{@code EQ_SUBLEFT} yang penuh sementara kanan kosong justru
     *       <b>tetap</b> di kiri, dan yang <b>kosong</b> malah dipindah ke
     *       {@code EQ_SUBRIGHT}. Persyaratannya terbalik dari pasangan
     *       atasnya.</li>
     * </ul>
     *
     * <p>Terlihat seperti salah ketik di C, tetapi ditiru apa adanya:
     * "memperbaikinya" akan memindahkan setiap anting yang sudah dikenakan
     * pemain ke slot yang berbeda dari yang tersimpan di database.</p>
     */
    private static int pasanganKiriKanan(User sd, int slotEq) {
        int ret = slotEq;
        if (ret == Equip.LEFT) {
            ret = terisi(sd, Equip.LEFT) && !terisi(sd, Equip.RIGHT)
                    ? Equip.RIGHT : Equip.LEFT;
        }
        if (ret == Equip.RIGHT) {
            ret = terisi(sd, Equip.RIGHT) && !terisi(sd, Equip.LEFT)
                    ? Equip.LEFT : Equip.RIGHT;
        }
        if (ret == Equip.SUBLEFT) {
            ret = terisi(sd, Equip.SUBLEFT) && !terisi(sd, Equip.SUBRIGHT)
                    ? Equip.SUBLEFT : Equip.SUBRIGHT;
        }
        if (ret == Equip.SUBRIGHT) {
            ret = terisi(sd, Equip.SUBRIGHT) && !terisi(sd, Equip.SUBLEFT)
                    ? Equip.SUBLEFT : Equip.SUBRIGHT;
        }
        return ret;
    }

    private static boolean terisi(User sd, int slotEq) {
        Item it = sd.status.equipAt(slotEq);
        return it != null && it.id > 0;
    }

    // ==================================================================
    // melepas
    // ==================================================================

    /**
     * pc_unequip(): tandai slot yang hendak dilepas lalu panggil kaitnya.
     *
     * @return false bila slotnya memang kosong
     */
    public static boolean unequip(User sd, int slotEq) {
        if (!terisi(sd, slotEq)) {
            return false;
        }
        sd.takeOffId = slotEq;
        var engine = MapServer.scriptEngine;
        if (engine != null) {
            kait(sd, "onUnequip", null, engine.playerRef(sd.scriptPlayer()));
        }
        return true;
    }

    /**
     * pc_unequipscript(): <b>di sinilah</b> barangnya benar-benar dilepas —
     * dan bila {@code equipId} terisi, ditukar sekaligus.
     *
     * <p>Dipanggil skrip lewat {@code player:takeOff()}.</p>
     *
     * <p>Dua cabangnya bukan pengulangan: yang pertama <b>menukar</b>
     * (barang baru dari inventaris masuk, yang lama keluar) dan hanya
     * dipakai jalur mengenakan-di-slot-terisi; yang kedua <b>melepas</b>
     * begitu saja.</p>
     */
    public static void unequipScript(User sd) {
        int slotEq = sd.takeOffId;
        if (slotEq < 0 || slotEq >= Equip.COUNT) {
            return;
        }
        Item lama = sd.status.equipAt(slotEq);
        if (lama == null || lama.id <= 0) {
            return;
        }
        long idLama = lama.id;

        if (sd.equipId > 0) {
            if (sd.invSlot < 0 || sd.invSlot >= sd.status.inventory.size()) {
                return;
            }
            Item baru = salin(sd.status.inventory.get(sd.invSlot));
            baru.pos = slotEq;
            sd.status.equip.remove(lama);
            sd.status.equip.add(baru);
            delItem(sd, sd.invSlot, 1, 6);
            // pc_additem(&it): yang lama pulang ke inventaris
            sd.addItemById(lama.id, 1, lama.dura);
            umumkanSlot(sd, slotEq);
            baru.amount = 1;
        } else {
            // ⚠️ Inventaris penuh berarti barangnya TETAP DIKENAKAN. Di C
            // `if (pc_additem(sd, &it)) return 1;` mendahului penghapusan
            // slotnya — urutan itu yang menjaga barangnya tidak lenyap.
            if (!sd.addItemById(lama.id, 1, lama.dura)) {
                return;
            }
            sd.status.equip.remove(lama);
            sd.target = sd.id;
            sd.attacker = sd.id;
        }

        lepasPesona(sd, slotEq);

        var engine = MapServer.scriptEngine;
        if (engine != null) {
            kait(sd, MapServer.itemDb.info(idLama).name(), "unequip",
                    engine.playerRef(sd.scriptPlayer()));
        }
        sd.takeOffId = -1;
        selesaiUbahPerlengkapan(sd);
    }

    /** Senjata terpesona kehilangan pesonanya saat dilepas atau diganti. */
    private static void lepasPesona(User sd, int slotEq) {
        if (slotEq == Equip.WEAP && sd.enchanted > 1.0f) {
            sd.enchanted = 1.0f;
            sd.flank = false;
            sd.backstab = false;
            pesan(sd, "Your weapon loses its enchantment.");
        }
    }

    /** Ekor yang sama di kedua jalur: hitung ulang nilai lalu perbarui tampilan. */
    private static void selesaiUbahPerlengkapan(User sd) {
        sd.scriptCalcStat();
        MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_ALL);
        MapServer.clientView.objectAppearanceChanged(sd);
    }

    /**
     * clif_sendequip(): tampilkan slotnya lalu beri tahu pemain apa yang
     * kini dikenakan ("W:Klewang").
     *
     * <p>⚠️ Ikut menyapu data rusak: barang bernama {@code "??"} dibuang
     * dari slotnya alih-alih ditampilkan — sekeluarga dengan penyapu di
     * {@code clif_sendadditem} (Peringatan #37).</p>
     */
    private static void umumkanSlot(User sd, int slotEq) {
        Item it = sd.status.equipAt(slotEq);
        if (it != null && it.id > 0
                && "??".equalsIgnoreCase(MapServer.itemDb.info(it.id).tampilan())) {
            sd.status.equip.remove(it);
            return;
        }
        String kunci = slotEq >= 0 && slotEq < KUNCI_SLOT.length
                ? KUNCI_SLOT[slotEq] : null;
        if (kunci == null || it == null) {
            return;   // EQ_FACEACCTWO: C mengembalikan -1 tanpa mengirim apa pun
        }
        String nama = it.realName != null && !it.realName.isEmpty()
                ? it.realName : MapServer.itemDb.info(it.id).tampilan();
        MapServer.clientView.playerEquipmentChanged(sd, slotEq);
        pesan(sd, MapMsg.of(kunci).replace("%s", nama));
    }

    // ==================================================================
    // melempar (clif_parsethrow + clif_throwitem_script)
    // ==================================================================

    /**
     * Petak pendaratan lemparan: telusuri sampai <b>delapan</b> petak ke
     * arah hadap, berhenti di penghalang pertama.
     *
     * <p>Yang menghentikan: benda apa pun (NPC, pemain, mob), tembok, dan
     * <b>portal</b>. Petak terakhir yang bersih itulah tujuannya; kalau
     * langkah pertama sudah terhalang, barangnya mendarat di kaki pelempar
     * sendiri — dan {@link #throwScript} memakai itu untuk memilih animasi
     * jatuh alih-alih animasi lempar.</p>
     */
    public static int[] throwLanding(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return new int[] {sd.x, sd.y};
        }
        int dx = sd.status.side == 1 ? 1 : sd.status.side == 3 ? -1 : 0;
        int dy = sd.status.side == 0 ? -1 : sd.status.side == 2 ? 1 : 0;
        int nx = sd.x;
        int ny = sd.y;
        for (int i = 0; i < 8; i++) {
            int x1 = Math.max(0, Math.min(sd.x + (i * dx) + dx, map.xs - 1));
            int y1 = Math.max(0, Math.min(sd.y + (i * dy) + dy, map.ys - 1));
            boolean terhalang = !map.walkable(x1, y1) || map.warpAt(x1, y1) != null;
            if (!terhalang) {
                for (org.rtk.map.data.BlockList bl : map.objectsAt(x1, y1)) {
                    if (bl.type != org.rtk.map.data.BlockList.Type.ITEM && bl.id != sd.id) {
                        terhalang = true;
                        break;
                    }
                }
            }
            if (terhalang) {
                break;
            }
            nx = x1;
            ny = y1;
        }
        return new int[] {nx, ny};
    }

    /**
     * clif_throwitem_script(): lemparkan isi {@code invSlot} ke
     * {@code throwX}/{@code throwY}.
     *
     * <p>Dipanggil skrip lewat {@code player:throwItem()} dari kait
     * {@code onThrow}, jadi skrip bisa mengubah tujuannya lebih dulu.</p>
     *
     * <p>⚠️ <b>Selalu sekeping</b>, berapa pun isi slotnya — di C
     * {@code type} pada jalur ini tetap 0 dan tidak pernah diisi.</p>
     */
    public static void throwScript(User sd) {
        int slot = sd.invSlot;
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        Item it = sd.status.inventory.get(slot);
        if (it.id <= 0 || it.amount <= 0) {
            return;
        }
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        int x = Math.max(0, Math.min(sd.throwX, map.xs - 1));
        int y = Math.max(0, Math.min(sd.throwY, map.ys - 1));

        ItemDb.Info info = MapServer.itemDb.info(it.id);
        Item jatuh = salin(it);
        jatuh.amount = 1;

        // Penggabungan hanya disapu bila barangnya utuh — penjaga yang sama
        // dengan jatuh-dari-inventaris (Peringatan #31).
        FloorItem gabung = null;
        if (it.dura >= info.durability()) {
            for (org.rtk.map.data.BlockList bl : map.objectsAt(x, y)) {
                if (bl instanceof FloorItem ada && ada.data.id == it.id) {
                    ada.lastAmount = ada.data.amount;
                    ada.data.amount += 1;
                    java.util.Arrays.fill(ada.looters, 0);
                    gabung = ada;
                    break;
                }
            }
        }

        delItem(sd, slot, 1, 4);

        FloorItem mendarat = gabung;
        if (gabung == null) {
            FloorItem fl = new FloorItem();
            fl.data.id = jatuh.id;
            fl.data.amount = 1;
            fl.data.dura = jatuh.dura;
            fl.data.owner = jatuh.owner;
            fl.data.protectedFlag = jatuh.protectedFlag;
            fl.data.realName = jatuh.realName;
            fl.data.customIcon = jatuh.customIcon;
            fl.data.customIconColor = jatuh.customIconColor;
            mendarat = MapServer.floorItems.place(fl, sd.m, x, y);
        }

        // ⚠️ Pembedanya `sd.x != x` — hanya sumbu X. Melempar ke atas atau
        // ke bawah karena itu memakai animasi JATUH, bukan animasi lempar.
        // Ada di C; terlihat seperti kelalaian tapi ditiru apa adanya.
        if (sd.x != x) {
            int ikon = jatuh.customIcon != 0
                    ? (int) jatuh.customIcon + 49152 : info.look().icon();
            int warna = jatuh.customIcon != 0
                    ? (int) jatuh.customIconColor : info.look().iconColor();
            MapServer.clientView.objectThrown(sd, x, y, ikon, warna, 2);
        } else {
            MapServer.clientView.objectActed(sd, 2, 30, 0);
        }
        if (mendarat != null && gabung == null) {
            MapServer.clientView.floorItemAppeared(mendarat);
        }
    }

    // ==================================================================
    // alat bantu
    // ==================================================================

    /**
     * pc_delitem(): kurangi isi slot; kosong berarti slotnya dibuang.
     *
     * <p>Sisa yang masih ada dilaporkan ke pemain sebagai "Roti (1)" —
     * jumlah yang <b>diambil</b>, bukan yang tersisa. Itu di C.</p>
     */
    public static void delItem(User sd, int slot, int amount, int alasan) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        Item it = sd.status.inventory.get(slot);
        if (it.id <= 0) {
            return;
        }
        it.amount -= amount;
        if (it.amount <= 0) {
            sd.clearInventorySlot(slot);
            MapServer.clientView.playerInventorySlotCleared(sd, slot, alasan);
        } else {
            pesan(sd, MapServer.itemDb.info(it.id).tampilan() + " (" + amount + ")");
            MapServer.clientView.playerInventorySlotChanged(sd, slot);
        }
    }

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
        return c;
    }

    private static void pesanKeadaan(User sd) {
        switch (sd.status.state) {
            case ST_SPIRIT -> pesan(sd, "Spirits can't do that.");
            case ST_INVIS, ST_MORPH -> pesan(sd, "You can't do that while transformed.");
            case ST_MOUNT -> pesan(sd, "You can't do that while riding a mount.");
            default -> { }
        }
    }

    private static void pesan(User sd, String teks) {
        MapServer.clientView.messageToPlayer(sd, MSG_MINI, teks);
    }

    private static void kait(User sd, String root, String method) {
        var engine = MapServer.scriptEngine;
        if (engine != null) {
            kait(sd, root, method, engine.playerRef(sd.scriptPlayer()));
        }
    }

    private static void kait(User sd, String root, String method, LuaValue ref) {
        var engine = MapServer.scriptEngine;
        if (engine == null || root == null || root.isEmpty()) {
            return;
        }
        try {
            engine.doScript(root, method, ref);
        } catch (RuntimeException e) {
            log.error("[ITEM] kait '{}' pada '{}' gagal untuk {}",
                    method, root, sd.name(), e);
        }
    }
}
