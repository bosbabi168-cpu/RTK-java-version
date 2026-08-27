package org.rtk.map;

import java.util.List;

import org.rtk.common.Session;
import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.Item;
import org.rtk.common.mmo.SettingFlags;
import org.rtk.map.data.BlockList;
import org.rtk.map.data.Equip;
import org.rtk.map.data.ItemDb;
import org.rtk.map.data.MapData;
import org.rtk.map.proto.Wire;

/**
 * Arah keluar protokol <b>RTK2</b> — pasangan {@code org.rtk.map.proto.Inbound}.
 *
 * <p>Implementasi kedua {@link ClientView}, di samping
 * {@link RetroTkClientView}. Keduanya menerima <b>setiap</b> peristiwa dan
 * masing-masing menyaring penerimanya sendiri lewat {@link User#rtk2};
 * penyalurnya {@link ProtocolRouter}.</p>
 *
 * <h2>Apa yang berubah dari RetroTK, dan kenapa</h2>
 *
 * <ul>
 *   <li><b>Grafik dikirim mentah.</b> RetroTK punya tiga aturan penambah
 *       untuk satu ladang (32768 mob/NPC, 49152 ikon kustom, tanpa penambah
 *       untuk ikon barang biasa — Peringatan #34). Di sini nilainya apa
 *       adanya, ditemani {@code kind} bendanya; klien yang memutuskan dari
 *       kumpulan grafik mana ia mengambil gambar.</li>
 *   <li><b>Perlengkapan dikirim sebagai daftar, bukan offset tetap.</b>
 *       RetroTK menaruh tiap slot di offset tertentu dan memakai
 *       {@code 0xFFFF} sebagai "kosong"; mantel bahkan <b>menimpa</b> zirah
 *       di offset yang sama. Di sini server sudah memutuskan apa yang
 *       tergambar, lalu mengirim daftar {@code (slot, grafik, warna)} yang
 *       panjangnya sesuai isinya.</li>
 *   <li><b>Tidak ada dua paket untuk satu maksud.</b> RetroTK memakai
 *       0x1D, 0x33, dan 0x07 untuk "gambar ulang benda ini" tergantung
 *       jenisnya; di sini satu {@code EV_OBJECT_APPEARANCE} untuk semuanya,
 *       karena bloknya sendiri sudah menyebut jenisnya.</li>
 *   <li><b>Nama benda ikut di bloknya.</b> Klien tidak perlu menyimpan
 *       tabel nama untuk menggambar label.</li>
 * </ul>
 *
 * <h2>Yang sengaja TIDAK dibawa</h2>
 *
 * <p>Ladang tampilan yang di RetroTK diisi tetapi tidak pernah dipakai —
 * warna nama, warna garis tepi, dan ladang {@code passflag} pada paket
 * benda — tidak ada di sini. Begitu pula {@code sendMyStatus} TAHAP 1
 * (klan, gelar, pasangan): datanya memang belum ada, dan mengirim ladang
 * kosong hanya memindahkan pekerjaannya ke klien.</p>
 */
public final class Rtk2ClientView implements ClientView {

    /** Jenis benda di blok benda. */
    private static final int KIND_PC = 1;
    private static final int KIND_MOB = 2;
    private static final int KIND_NPC = 3;
    private static final int KIND_ITEM = 4;

    /** Bit 0 blok benda: digambar sebagai karakter, bukan sebagai gambar tunggal. */
    private static final int FLAG_AS_CHAR = 1;

    /** Ragam dialog pada {@code EV_DIALOG}. */
    private static final int DLG_TEXT = 0;
    private static final int DLG_MENU = 1;
    private static final int DLG_INPUT = 2;
    private static final int DLG_BUY = 3;
    private static final int DLG_SELL = 4;
    private static final int DLG_SEQ = 5;

    // ==================================================================
    // pengiriman
    // ==================================================================

    /** Sesi pemain, atau null bila ia <b>bukan</b> klien RTK2. */
    private static Session sesi(User sd) {
        return sd == null || !sd.rtk2 ? null : MapServer.net.session(sd.fd);
    }

    private static void kirim(User sd, Wire.Writer w) {
        Session s = sesi(sd);
        if (s == null) {
            return;
        }
        byte[] f = w.frame();
        s.wfifoBytes(0, f, f.length);
        s.wfifoSet(f.length);
    }

    /**
     * Siarkan ke pemain RTK2 di sekitar sebuah benda.
     *
     * <p>Bingkainya disusun <b>sekali</b> lalu disalin ke tiap penerima —
     * tanpa enkripsi per-sesi, tidak ada alasan menyusunnya berulang. Itu
     * berbeda dari RetroTK, yang harus mengenkripsi ulang untuk tiap pemain
     * karena kuncinya per-sesi.</p>
     */
    private static void siarkan(BlockList dari, Wire.Writer w, boolean termasukDiri) {
        MapData map = MapServer.world.get(dari.m);
        if (map == null) {
            return;
        }
        byte[] f = w.frame();
        map.foreachInArea(dari.x, dari.y, BlockList.Type.PC, bl -> {
            if (!(bl instanceof User to) || (!termasukDiri && bl == dari)) {
                return;
            }
            Session ts = sesi(to);
            if (ts == null) {
                return;
            }
            ts.wfifoBytes(0, f, f.length);
            ts.wfifoSet(f.length);
        });
    }

    /** Siarkan ke seluruh pemain RTK2 di peta yang sama. */
    private static void siarkanPeta(BlockList dari, Wire.Writer w) {
        byte[] f = dari == null ? null : w.frame();
        if (f == null) {
            return;
        }
        for (User to : MapServer.onlineChars.values()) {
            if (to.m != dari.m) {
                continue;
            }
            Session ts = sesi(to);
            if (ts == null) {
                continue;
            }
            ts.wfifoBytes(0, f, f.length);
            ts.wfifoSet(f.length);
        }
    }

    // ==================================================================
    // blok yang dipakai berulang
    // ==================================================================

    /**
     * Blok barang: cukup untuk menggambar dan memberi nama satu keping.
     *
     * <p>Dua nama, seperti paket inventaris RetroTK (Peringatan #36): yang
     * pertama teks yang <b>dilihat pemain</b> (ukiran bila ada), yang kedua
     * nama <b>jenisnya</b> untuk mencocokkan gambar dan tooltip.
     * Menyamakannya membuat barang berukir kehilangan gambarnya.</p>
     */
    private static void blokBarang(Wire.Writer w, Item it) {
        if (it == null || it.id <= 0) {
            w.u64(0).u32(0).str("").str("").u16(0).u8(0).u32(0).u32(0);
            return;
        }
        ItemDb.Info info = MapServer.itemDb.info(it.id);
        String tampil = it.realName != null && !it.realName.isEmpty()
                ? it.realName : info.tampilan();
        int ikon = it.customIcon != 0 ? (int) it.customIcon : info.look().icon();
        int warna = it.customIcon != 0
                ? (int) it.customIconColor : info.look().iconColor();
        w.u64(it.id)
         .u32(it.amount)
         .str(tampil)
         .str(info.name())
         .u16(ikon)
         .u8(warna)
         .u32(it.dura)
         .u32(Math.max(it.protectedFlag, info.protectedValue()));
    }

    /**
     * Blok benda: identitas, tempat, dan wujud.
     *
     * <pre>
     *   u64 id, u8 kind, u16 x, u16 y, u8 side,
     *   u16 grafik, u8 warnaGrafik, u8 bendera, str nama
     *   [bila bendera bit 0: blok wujud karakter]
     * </pre>
     *
     * @param penonton pemain yang akan melihatnya — menentukan apa yang
     *                 boleh terlihat (jebakan, penyamaran, klan)
     * @return false bila benda ini <b>tidak boleh</b> digambar untuknya
     */
    private static boolean blokBenda(Wire.Writer w, User penonton, BlockList bl) {
        if (bl instanceof User who) {
            return blokPemain(w, penonton, who);
        }
        if (bl instanceof Mob mb) {
            if (mb.state == MobData.MOB_DEAD || mb.data.mobType == 1) {
                return false;
            }
            dasar(w, mb.id, KIND_MOB, mb.x, mb.y, mb.side, mb.look, mb.lookColor,
                    mb.data.isNpc ? FLAG_AS_CHAR : 0, mb.data.name);
            return true;
        }
        if (bl instanceof Npc nd) {
            if (nd.subtype != 0 || nd.npcType == 1) {
                return false;
            }
            dasar(w, nd.id, KIND_NPC, nd.x, nd.y, nd.side,
                    (int) nd.graphicId, (int) nd.graphicColor, FLAG_AS_CHAR,
                    nd.displayName);
            return true;
        }
        if (bl instanceof FloorItem fl) {
            // ⚠️ Penyaring jebakan ada di SISI PENERIMA, bukan di pengirim:
            // jebakan yang sama terlihat oleh yang sudah menemukannya dan
            // tidak oleh yang belum (Peringatan #33). Karena itu bingkainya
            // tidak boleh disusun sekali lalu disiarkan ke semua.
            if (!fl.visibleTo(penonton.id)) {
                return false;
            }
            ItemDb.Info info = MapServer.itemDb.info(fl.data.id);
            int ikon = fl.data.customIcon != 0
                    ? (int) fl.data.customIcon : info.look().icon();
            int warna = fl.data.customIcon != 0
                    ? (int) fl.data.customIconColor : info.look().iconColor();
            dasar(w, fl.id, KIND_ITEM, fl.x, fl.y, 0, ikon, warna, 0,
                    info.tampilan());
            w.u32(fl.data.amount);
            return true;
        }
        return false;
    }

    private static void dasar(Wire.Writer w, long id, int kind, int x, int y,
                              int side, int grafik, int warna, int bendera,
                              String nama) {
        w.u64(id).u8(kind).u16(x).u16(y).u8(side)
         .u16(grafik).u8(warna).u8(bendera).str(nama);
    }

    /**
     * Blok pemain — dasar plus wujud karakternya.
     *
     * <p>Aturan "apa yang terlihat" diselesaikan <b>di sini</b>, bukan di
     * klien: pemain ber-stealth, hantu, dan penyamaran punya perlakuan
     * berbeda tergantung siapa yang melihat dan apakah ia GM. Klien hanya
     * menerima hasilnya.</p>
     */
    private static boolean blokPemain(Wire.Writer w, User penonton, User who) {
        CharStatus st = who.status;
        boolean stealth = (who.optFlags & User.OPT_STEALTH) != 0;

        int state = st.state;
        if ((st.state == 2 || stealth) && who.id != penonton.id && penonton.isGm()) {
            state = 5;   // GM tetap melihat yang tak terlihat, ditandai
        } else if (stealth && st.state == 0
                && (!penonton.isGm() || who.id == penonton.id)) {
            state = 2;
        }

        // Nama disembunyikan pada keadaan tak terlihat — itu satu-satunya
        // yang membedakan "ada tapi samar" dari "tidak ada".
        String nama = state != 2 && state != 5 && st.name != null ? st.name : "";
        dasar(w, who.id, KIND_PC, who.x, who.y, st.side, 0, 0, FLAG_AS_CHAR, nama);

        w.u8(st.sex).u8(state)
         .u16(state == 3 || state == 4 ? st.disguise : 0)
         .u8(state == 4 ? st.disguiseColor : 0)
         .u8(who.speed)
         .u8(st.face).u8(st.hair).u8(st.hairColor)
         .u8(st.faceColor).u8(st.skinColor);

        // Daftar slot yang benar-benar tergambar. Panjangnya ditulis lebih
        // dulu, jadi tidak perlu sentinel "kosong" seperti 0xFFFF di RetroTK.
        java.util.List<int[]> slot = new java.util.ArrayList<>();
        tambahSlot(slot, st, Equip.COAT, st.armorColor);
        if (slot.isEmpty()) {
            tambahSlot(slot, st, Equip.ARMOR, st.armorColor);
        }
        tambahSlot(slot, st, Equip.WEAP, 0);
        tambahSlot(slot, st, Equip.SHIELD, 0);
        if ((st.settingFlags & SettingFlags.HELM) != 0) {
            tambahSlot(slot, st, Equip.HELM, 0);
        }
        tambahSlot(slot, st, Equip.FACEACC, 0);
        tambahSlot(slot, st, Equip.CROWN, 0);
        tambahSlot(slot, st, Equip.FACEACCTWO, 0);
        tambahSlot(slot, st, Equip.MANTLE, 0);
        if ((st.settingFlags & SettingFlags.NECKLACE) != 0) {
            tambahSlot(slot, st, Equip.NECKLACE, 0);
        }
        tambahSlot(slot, st, Equip.BOOTS, 0);

        w.u8(slot.size());
        for (int[] e : slot) {
            w.u8(e[0]).u16(e[1]).u8(e[2]);
        }

        // Penanda nama: 0 biasa, 1 PK, 3 seklan.
        int tanda = 0;
        if (penonton.status.clan == st.clan && st.clan > 0
                && penonton.status.id != st.id) {
            tanda = 3;
        }
        if (st.pk > 0) {
            tanda = 1;
        }
        w.u8(tanda);
        return true;
    }

    /** Satu slot perlengkapan, hanya bila ada dan memang bergambar. */
    private static void tambahSlot(List<int[]> keluar, CharStatus st, int slotEq,
                                   int warnaPaksa) {
        Item it = st.equipAt(slotEq);
        if (it == null || it.id <= 0) {
            return;
        }
        int look = it.customLook != 0
                ? (int) it.customLook : MapServer.itemDb.lookOf(it.id);
        if (look == -1) {
            return;   // barang yang memang tidak tergambar
        }
        int warna = warnaPaksa > 0 ? warnaPaksa
                : (it.customLook != 0 ? (int) it.customLookColor
                                      : MapServer.itemDb.lookColorOf(it.id));
        keluar.add(new int[] {slotEq, look, warna});
    }

    // ==================================================================
    // pemain itu sendiri
    // ==================================================================

    @Override
    public void playerEnteredWorld(User sd) {
        if (sesi(sd) == null) {
            return;
        }
        // ⚠️ Urutannya masih mengikat, tetapi alasannya berubah: bukan lagi
        // karena klien peka terhadap urutan paket (RetroTK), melainkan
        // karena identitas dan petanya harus sampai sebelum apa pun yang
        // merujuk keduanya. Peta dulu, lalu diri sendiri, lalu sekeliling.
        identitas(sd);
        petaSendiri(sd);
        playerStatusChanged(sd, Clif.SFLAG_ALL);
        kirim(sd, new Wire.Writer(Wire.EV_SELF_POSITION)
                .u16(sd.x).u16(sd.y).u8(sd.status.side));
        playerViewRefreshed(sd);
    }

    private void identitas(User sd) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_IDENTITY)
                .u64(sd.id).str(sd.name()).u8(sd.status.sex).u8(sd.status.side));
    }

    private void petaSendiri(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        // Bendera peta yang mengubah apa yang boleh dilakukan pemain,
        // dikirim sekaligus supaya klien bisa meredupkan tombolnya sendiri
        // alih-alih menunggu penolakan dari server.
        int bendera = (map.pvp != 0 ? 1 : 0)
                | (map.spell != 0 ? 2 : 0)
                | (map.canUse != 0 ? 4 : 0)
                | (map.canEat != 0 ? 8 : 0)
                | (map.canSmoke != 0 ? 16 : 0)
                | (map.canEquip != 0 ? 32 : 0)
                | (map.canTalk != 1 ? 64 : 0);
        kirim(sd, new Wire.Writer(Wire.EV_SELF_MAP)
                .u16(sd.m).str(map.title == null ? "" : map.title)
                .u16(map.xs).u16(map.ys).u8(map.light).u16(bendera));
    }

    @Override
    public void playerViewRefreshed(User sd) {
        if (sesi(sd) == null) {
            return;
        }
        kirim(sd, new Wire.Writer(Wire.EV_SELF_REFRESH));
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        // Sapuan sekeliling: tiap benda disusun ULANG per penonton, karena
        // apa yang terlihat bergantung pada siapa yang melihat.
        // Tidak ada Type.ALL: indeks blok memisahkan pemain dari benda lain
        // (di C pun `block[]` dan `block_mob[]` terpisah), jadi keempat
        // jenisnya disapu satu per satu.
        for (BlockList.Type t : BlockList.Type.values()) {
            map.foreachInArea(sd.x, sd.y, t, bl -> {
                if (bl.id == sd.id) {
                    return;
                }
                Wire.Writer w = new Wire.Writer(Wire.EV_OBJECT_APPEARED);
                if (blokBenda(w, sd, bl)) {
                    kirim(sd, w);
                }
            });
        }
    }

    @Override
    public void playerStatusChanged(User sd, int flags) {
        CharStatus st = sd.status;
        kirim(sd, new Wire.Writer(Wire.EV_SELF_STATUS)
                .u32(flags)
                .u32(st.level).u64(st.exp)
                .u64(st.hp).u64(sd.maxHp).u64(st.mp).u64(sd.maxMp)
                .u32(sd.might).u32(sd.will).u32(sd.grace)
                .u32(sd.armor).u32(sd.hit).u32(sd.dam)
                .u32(sd.protection).u32(sd.healing)
                .u64(st.money).u64(st.bankMoney)
                .u32(st.maxInv).u32(st.mark).u32(st.tier)
                .u32(st.charClass).u32(st.country).u32(st.pk)
                .u32(Math.round(st.karma * 100)));
    }

    @Override
    public void playerIdentityChanged(User sd) {
        identitas(sd);
    }

    @Override
    public void playerDurationChanged(User sd, int spellId, int seconds,
                                      String casterName) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_DURATION)
                .u16(spellId).u32(seconds).str(casterName));
    }

    @Override
    public void playerAetherChanged(User sd, int spellId, int seconds) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_AETHER).u16(spellId).u32(seconds));
    }

    @Override
    public void playerCameraChanged(User sd, int x, int y) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_CAMERA).u16(x).u16(y));
    }

    @Override
    public void playerHealthChanged(User sd, int critical, int percent, int damage) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_HEALTH)
                .u8(critical).u8(percent).u32(damage));
    }

    @Override
    public void playerMovementLocked(User sd, boolean locked) {
        // ⚠️ Di RetroTK `lock` mengirim 0 dan `unlock` mengirim 1 — terbalik
        // dari namanya (Peringatan #40). Di sini benderanya berarti apa
        // yang tertulis.
        kirim(sd, new Wire.Writer(Wire.EV_SELF_MOVE_LOCK).bool(locked));
    }

    @Override
    public void playerTimerSet(User sd, int type, long seconds) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_TIMER).u8(type).u64(seconds));
    }

    @Override
    public void playerStepRejected(User sd) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_STEP_REJECTED)
                .u16(sd.x).u16(sd.y).u8(sd.status.side));
    }

    @Override
    public void playerStepped(User sd, int direction, int fromX, int fromY) {
        kirim(sd, new Wire.Writer(Wire.EV_SELF_STEPPED)
                .u8(direction).u16(fromX).u16(fromY));
    }

    @Override
    public void playerStepSeen(User sd, int direction, int fromX, int fromY) {
        siarkan(sd, new Wire.Writer(Wire.EV_OBJECT_MOVED)
                .u64(sd.id).u16(fromX).u16(fromY).u16(sd.x).u16(sd.y)
                .u8(sd.status.side), false);
    }

    @Override
    public void areaRedrawRequested(User sd, MapData map, int x, int y,
                                    int width, int height, int checksum) {
        // ⚠️ Sengaja TIDAK ADA di RTK2. Ini kebocoran protokol RetroTK
        // (Peringatan #52): klien menitipkan "gambar ulang petak ini" pada
        // paket langkah. Server tahu sendiri apa yang baru terlihat, jadi
        // pembacanya pun tidak pernah mengisinya — nilainya selalu null.
    }

    // ==================================================================
    // barang & perlengkapan
    // ==================================================================

    @Override
    public void playerInventorySlotChanged(User sd, int slot) {
        if (sesi(sd) == null || slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        Wire.Writer w = new Wire.Writer(Wire.EV_INV_SLOT).u8(slot);
        blokBarang(w, sd.status.inventory.get(slot));
        kirim(sd, w);
    }

    @Override
    public void playerInventorySlotCleared(User sd, int slot, int reason) {
        kirim(sd, new Wire.Writer(Wire.EV_INV_SLOT_CLEARED).u8(slot).u8(reason));
    }

    @Override
    public void playerEquipmentChanged(User sd, int slot) {
        if (sesi(sd) == null) {
            return;
        }
        Wire.Writer w = new Wire.Writer(Wire.EV_EQUIP_SLOT).u8(slot);
        blokBarang(w, sd.status.equipAt(slot));
        kirim(sd, w);
    }

    @Override
    public void playerEquipmentCleared(User sd, int slot) {
        kirim(sd, new Wire.Writer(Wire.EV_EQUIP_SLOT_CLEARED).u8(slot));
    }

    @Override
    public void playerSpellRemoved(User sd, int slot) {
        kirim(sd, new Wire.Writer(Wire.EV_SPELL_REMOVED).u8(slot));
    }

    // ==================================================================
    // teks
    // ==================================================================

    @Override
    public void chatLineToPlayer(User sd, int type, long speakerId, String text) {
        kirim(sd, new Wire.Writer(Wire.EV_CHAT).u8(type).u64(speakerId).str(text));
    }

    @Override
    public void messageToPlayer(User sd, int type, String text) {
        kirim(sd, new Wire.Writer(Wire.EV_MESSAGE).u8(type).str(text));
    }

    @Override
    public void popupToPlayer(User sd, String text) {
        kirim(sd, new Wire.Writer(Wire.EV_POPUP).str(text));
    }

    @Override
    public void guiTextToPlayer(User sd, String text) {
        kirim(sd, new Wire.Writer(Wire.EV_GUI_TEXT).str(text));
    }

    @Override
    public void paperToPlayer(User sd, String text, int width, int height) {
        kirim(sd, new Wire.Writer(Wire.EV_PAPER).str(text).u16(width).u16(height));
    }

    @Override
    public void urlToPlayer(User sd, int type, String url) {
        kirim(sd, new Wire.Writer(Wire.EV_URL).u8(type).str(url));
    }

    @Override
    public void playerSpoke(User sd, String text, int type) {
        // Ragam 1 menyiarkan ke SELURUH peta, bukan hanya sekitar — itu yang
        // membedakan berteriak dari berbicara.
        Wire.Writer w = new Wire.Writer(Wire.EV_OBJECT_SPOKE)
                .u64(sd.id).u8(type).str(text);
        if (type == 1) {
            siarkanPeta(sd, w);
        } else {
            siarkan(sd, w, true);
        }
    }

    @Override
    public void objectSpoke(BlockList speaker, int type, String text) {
        siarkan(speaker, new Wire.Writer(Wire.EV_OBJECT_SPOKE)
                .u64(speaker.id).u8(type).str(text), true);
    }

    @Override
    public void npcSaidTo(User sd, long npcId, String text) {
        kirim(sd, new Wire.Writer(Wire.EV_DIALOG)
                .u8(DLG_TEXT).u64(npcId).u16(0).u8(0).str(text)
                .u16(0).u16(0));
    }

    // ==================================================================
    // pertukaran
    // ==================================================================

    @Override
    public void exchangeOpened(User sd, User target) {
        if (target == null) {
            return;
        }
        kirim(sd, new Wire.Writer(Wire.EV_EXCHANGE_OPENED)
                .u64(target.id).str(target.name()));
        kirim(target, new Wire.Writer(Wire.EV_EXCHANGE_OPENED)
                .u64(sd.id).str(sd.name()));
    }

    @Override
    public void exchangeItemOffered(User sd, User target, Item item, int index) {
        // Kedua sisi melihat titipan yang sama; yang membedakan hanya id
        // pemberinya, sehingga klien tahu di kolom mana menaruhnya.
        Wire.Writer a = new Wire.Writer(Wire.EV_EXCHANGE_ITEM)
                .u64(sd.id).u8(index);
        blokBarang(a, item);
        kirim(sd, a);
        if (target != null) {
            Wire.Writer b = new Wire.Writer(Wire.EV_EXCHANGE_ITEM)
                    .u64(sd.id).u8(index);
            blokBarang(b, item);
            kirim(target, b);
        }
    }

    @Override
    public void exchangeGoldOffered(User sd, User target, long gold) {
        kirim(sd, new Wire.Writer(Wire.EV_EXCHANGE_GOLD).u64(sd.id).u64(gold));
        if (target != null) {
            kirim(target, new Wire.Writer(Wire.EV_EXCHANGE_GOLD).u64(sd.id).u64(gold));
        }
    }

    @Override
    public void exchangeConfirmed(User sd, User target, boolean completed) {
        kirim(sd, new Wire.Writer(Wire.EV_EXCHANGE_STATE)
                .u64(sd.id).bool(completed));
        if (target != null) {
            kirim(target, new Wire.Writer(Wire.EV_EXCHANGE_STATE)
                    .u64(sd.id).bool(completed));
        }
    }

    // ==================================================================
    // benda di dunia
    // ==================================================================

    @Override
    public void objectSideChanged(BlockList bl) {
        int side = bl instanceof User u ? u.status.side
                : bl instanceof Mob m ? m.side
                : bl instanceof Npc n ? n.side : 0;
        siarkan(bl, new Wire.Writer(Wire.EV_OBJECT_SIDE).u64(bl.id).u8(side), true);
    }

    @Override
    public void objectAnimation(BlockList bl, int animation, int times) {
        siarkan(bl, new Wire.Writer(Wire.EV_OBJECT_ANIMATION)
                .u64(bl.id).u16(animation).u16(times), true);
    }

    @Override
    public void objectAnimationAt(BlockList bl, int animation, int times, int x, int y) {
        siarkan(bl, new Wire.Writer(Wire.EV_OBJECT_ANIMATION_AT)
                .u16(animation).u16(times).u16(x).u16(y), true);
    }

    @Override
    public void objectAnimationSeenBy(User viewer, BlockList target,
                                      int animation, int times) {
        if ((viewer.status.settingFlags & SettingFlags.MAGIC) == 0) {
            return;   // pemain mematikan efek mantra
        }
        kirim(viewer, new Wire.Writer(Wire.EV_OBJECT_ANIMATION)
                .u64(target.id).u16(animation).u16(times));
    }

    @Override
    public void soundPlayed(BlockList at, int sound) {
        siarkan(at, new Wire.Writer(Wire.EV_SOUND)
                .u16(sound).u16(at.x).u16(at.y), true);
    }

    @Override
    public void objectActed(BlockList bl, int action, int time, int sound) {
        siarkan(bl, new Wire.Writer(Wire.EV_OBJECT_ACTED)
                .u64(bl.id).u8(action).u16(time).u16(sound), true);
    }

    @Override
    public void objectAppearanceChanged(BlockList bl) {
        perPenonton(bl, Wire.EV_OBJECT_APPEARANCE, true);
    }

    @Override
    public void objectRemoved(BlockList bl) {
        siarkan(bl, new Wire.Writer(Wire.EV_OBJECT_REMOVED).u64(bl.id), true);
    }

    @Override
    public void objectThrown(BlockList from, int toX, int toY, int icon,
                             int color, int action) {
        siarkan(from, new Wire.Writer(Wire.EV_OBJECT_THROWN)
                .u64(from.id).u16(toX).u16(toY).u16(icon).u8(color).u8(action), true);
    }

    @Override
    public void npcMoved(Npc nd, MapData map, int fromX, int fromY,
                         int newX, int newY, int seenX, int seenY) {
        // ⚠️ Empat parameter terakhir adalah petak-yang-baru-terlihat, konsep
        // viewport RetroTK — kebocoran yang sudah ditandai di antarmukanya.
        // RTK2 tidak memakainya: posisi lama dan baru sudah cukup.
        siarkan(nd, new Wire.Writer(Wire.EV_OBJECT_MOVED)
                .u64(nd.id).u16(fromX).u16(fromY).u16(newX).u16(newY)
                .u8(nd.side), true);
    }

    @Override
    public void mobMoved(Mob mb, MapData map, int fromX, int fromY,
                         int newX, int newY, int seenX, int seenY) {
        siarkan(mb, new Wire.Writer(Wire.EV_OBJECT_MOVED)
                .u64(mb.id).u16(fromX).u16(fromY).u16(newX).u16(newY)
                .u8(mb.side), true);
    }

    @Override
    public void npcAppearedTo(User viewer, Npc nd) {
        Wire.Writer w = new Wire.Writer(Wire.EV_OBJECT_APPEARED);
        if (blokBenda(w, viewer, nd)) {
            kirim(viewer, w);
        }
    }

    @Override
    public void mobSpawned(Mob mb) {
        perPenonton(mb, Wire.EV_OBJECT_APPEARED, true);
    }

    @Override
    public void floorItemAppeared(FloorItem fl) {
        perPenonton(fl, Wire.EV_OBJECT_APPEARED, true);
    }

    /**
     * Siarkan blok benda, <b>disusun ulang untuk tiap penonton</b>.
     *
     * <p>⚠️ Tidak bisa disusun sekali lalu disalin seperti siaran lain:
     * isinya bergantung pada siapa yang melihat — jebakan yang belum
     * ditemukan tidak digambar, pemain ber-stealth tampak berbeda bagi GM,
     * dan penanda seklan hanya muncul untuk sesama anggota.</p>
     */
    private void perPenonton(BlockList bl, int opcode, boolean termasukDiri) {
        MapData map = MapServer.world.get(bl.m);
        if (map == null) {
            return;
        }
        map.foreachInArea(bl.x, bl.y, BlockList.Type.PC, o -> {
            if (!(o instanceof User to) || (!termasukDiri && o == bl)) {
                return;
            }
            if (sesi(to) == null) {
                return;
            }
            Wire.Writer w = new Wire.Writer(opcode);
            if (blokBenda(w, to, bl)) {
                kirim(to, w);
            }
        });
    }

    // ==================================================================
    // dialog & antarmuka
    // ==================================================================

    @Override
    public void scriptDialogReady(User sd, org.rtk.map.script.ScriptPlayer p) {
        var d = p.pendingDialog;
        if (d == null || sesi(sd) == null) {
            return;
        }
        int ragam = switch (d.kind) {
            case "menu" -> DLG_MENU;
            case "menuSeq" -> DLG_SEQ;
            case "dialog" -> DLG_TEXT;
            case "input" -> DLG_INPUT;
            case "inputSeq" -> DLG_SEQ;
            case "buy" -> DLG_BUY;
            case "sell" -> DLG_SELL;
            default -> DLG_TEXT;
        };
        Wire.Writer w = new Wire.Writer(Wire.EV_DIALOG)
                .u8(ragam).u64(sd.lastClick)
                .u16(sd.npcGraphic).u8(sd.npcGraphicColor)
                .str(d.message);
        List<String> opsi = d.options == null ? List.of() : d.options;
        w.u16(opsi.size());
        for (String o : opsi) {
            w.str(o);
        }
        List<Integer> harga = d.prices == null ? List.of() : d.prices;
        w.u16(harga.size());
        for (int h : harga) {
            w.u32(h);
        }
        kirim(sd, w);
    }

    @Override
    public void mapSelectionToPlayer(User sd, String title,
                                     List<Integer> x0, List<Integer> y0,
                                     List<String> names, List<Integer> ids,
                                     List<Integer> x1, List<Integer> y1) {
        List<String> nama = names == null ? List.of() : names;
        Wire.Writer w = new Wire.Writer(Wire.EV_MAP_SELECTION)
                .str(title).u16(nama.size());
        for (int i = 0; i < nama.size(); i++) {
            w.str(nama.get(i))
             .u16(nilai(ids, i)).u16(nilai(x1, i)).u16(nilai(y1, i))
             .u16(nilai(x0, i)).u16(nilai(y0, i));
        }
        kirim(sd, w);
    }

    private static int nilai(List<Integer> a, int i) {
        return a != null && i < a.size() && a.get(i) != null ? a.get(i) : 0;
    }

    @Override
    public void boardListToPlayer(User sd, int board, int flags1, int flags2,
                                  List<Clif.BoardEntry> isi) {
        Wire.Writer w = new Wire.Writer(Wire.EV_BOARD_LIST)
                .u16(board).u16(flags1).u16(flags2);
        List<Clif.BoardEntry> daftar = isi == null ? List.of() : isi;
        w.u16(daftar.size());
        for (Clif.BoardEntry e : daftar) {
            w.u32(e.postId()).u16(e.color()).u16(e.titleId())
             .u8(e.month()).u8(e.day())
             .str(e.author()).str(e.topic());
        }
        kirim(sd, w);
    }

    @Override
    public void boardQuestionsToPlayer(User sd, List<String> headers,
                                       List<String> questions,
                                       List<Integer> inputLines) {
        Wire.Writer w = new Wire.Writer(Wire.EV_BOARD_QUESTIONS);
        List<String> h = headers == null ? List.of() : headers;
        w.u16(h.size());
        for (String x : h) {
            w.str(x);
        }
        List<String> q = questions == null ? List.of() : questions;
        w.u16(q.size());
        for (int i = 0; i < q.size(); i++) {
            // Tiap pertanyaan membawa berapa baris isian yang disediakan
            // untuk jawabannya — di RetroTK keduanya larik terpisah yang
            // panjangnya harus dipercaya sama.
            w.str(q.get(i)).u8(nilai(inputLines, i));
        }
        kirim(sd, w);
    }

    @Override
    public void powerBoardToPlayer(User sd) {
        // ⚠️ `powerBoard` BUKAN papan pesan (Peringatan #48): ia daftar
        // pemain online di peta beserta "power rating"-nya, dan tidak
        // menyentuh tabel Boards sama sekali.
        MapData map = MapServer.world.get(sd.m);
        if (map == null || sesi(sd) == null) {
            return;
        }
        List<User> di = new java.util.ArrayList<>();
        for (User u : MapServer.onlineChars.values()) {
            if (u.m == sd.m) {
                di.add(u);
            }
        }
        Wire.Writer w = new Wire.Writer(Wire.EV_POWER_BOARD).u16(di.size());
        for (User u : di) {
            w.str(u.name()).u32(u.status.baseHp + u.status.baseMp);
        }
        kirim(sd, w);
    }
}
