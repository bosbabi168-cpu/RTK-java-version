package org.rtk.map;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.map.data.BlockList;
import org.rtk.map.data.MapData;

/**
 * Port of {@code map_additem()} / {@code map_delitem()} / {@code map_id2fl()}
 * (map/map.c:269-311) — daftar barang yang tergeletak di dunia.
 *
 * <p>Barang lantai <b>tidak disimpan ke database</b>: ia lahir saat sesuatu
 * dijatuhkan dan lenyap saat dipungut atau saat server berhenti. Itu sebabnya
 * registry ini tidak punya pemuat, tidak seperti NPC dan mob.</p>
 *
 * <p>Idnya diambil dari rentang terpisah mulai
 * {@link FloorItem#FLOORITEM_START_NUM} supaya tidak bertabrakan dengan
 * pemain (di bawah {@code MOB_START_NUM}), mob, atau NPC. Di C rentang itu
 * dikelola larik {@code object[]} yang <b>memakai ulang slot kosong</b>;
 * di sini nomornya menaik terus, karena satu-satunya hal yang bergantung
 * padanya adalah keunikan.</p>
 */
public final class FloorItemRegistry {

    private static final Logger log = LogManager.getLogger(FloorItemRegistry.class);

    /** map_id2fl(): id blok -&gt; barang lantai. */
    private final Map<Long, FloorItem> byId = new LinkedHashMap<>();

    private long nextId = FloorItem.FLOORITEM_START_NUM;

    /** map_id2fl(): cari barang lantai dari id bloknya, atau null. */
    public FloorItem byId(long id) {
        return byId.get(id);
    }

    public int count() {
        return byId.size();
    }

    public java.util.Collection<FloorItem> all() {
        return byId.values();
    }

    /**
     * map_additem(): beri id, daftarkan ke indeks id global dan ke petaknya.
     *
     * @return true bila barangnya benar-benar masuk ke peta
     */
    public boolean add(FloorItem fl, MapData map) {
        if (map == null) {
            return false;
        }
        if (byId.size() >= FloorItem.MAX_FLOORITEM) {
            log.error("[ITEM] batas {} barang lantai tercapai", FloorItem.MAX_FLOORITEM);
            return false;
        }
        fl.id = nextId++;
        if (!map.addBlock(fl)) {
            return false;
        }
        byId.put(fl.id, fl);
        if (idIndex != null) {
            idIndex.register(fl);   // map_addiddb
        }
        return true;
    }

    /**
     * map_delitem(): cabut barang dari indeks id dan dari petaknya.
     *
     * <p>C melakukannya dalam urutan {@code map_deliddb} lalu
     * {@code map_delblock} lalu {@code FREE} — pemanggilnya yang wajib
     * mengirim {@code clif_lookgone} <b>lebih dulu</b>, selagi barangnya
     * masih tahu di petak mana ia berdiri.</p>
     */
    public boolean remove(long id) {
        FloorItem fl = byId.remove(id);
        if (fl == null) {
            return false;
        }
        if (idIndex != null) {
            idIndex.unregister(fl);
        }
        MapData map = MapServer.world.get(fl.m);
        if (map != null && fl.onMap) {
            map.delBlock(fl);
        }
        return true;
    }

    /**
     * Indeks id global — sama seperti mob, barang lantai wajib ada di sana
     * karena skrip merujuknya lewat id: {@code player:pickUp(item.id)}.
     */
    private NpcRegistry idIndex;

    public void useIdIndex(NpcRegistry index) {
        this.idIndex = index;
    }

    /**
     * mobdb_dropitem() (map/mob.c:769) — jatuhkan sekeping barang ke petak.
     *
     * <p>⚠️ <b>Barang sejenis di petak yang sama digabung, bukan ditumpuk
     * jadi dua benda.</b> C menyapu petaknya dengan {@code mob_addtocurrent}
     * lebih dulu; kalau ketemu barang ber-id sama, jumlahnya ditambahkan ke
     * yang sudah ada dan benda baru dibuang. Karena itu satu petak tidak
     * pernah berisi dua tumpuk barang yang sama.</p>
     *
     * <p>Daftar {@code looters} diisi dari grup penyerang mob: kalau ia
     * bergrup, seluruh anggota grup berhak; kalau tidak, hanya dirinya.</p>
     *
     * @param source benda yang menjatuhkan (mob, NPC, atau pemain); boleh null
     * @return barang lantai yang terbentuk atau yang jumlahnya bertambah,
     *         null bila gagal
     */
    public FloorItem drop(BlockList source, long itemId, int amount, int dura,
                          long protectedFlag, long owner, int m, int x, int y) {
        MapData map = MapServer.world.get(m);
        if (map == null || amount <= 0) {
            return null;
        }

        // map_foreachincell(mob_addtocurrent, ...): gabung dengan yang sudah
        // ada. ⚠️ Penyaringnya di sini HANYA id barang — beda dari jalur
        // jatuh-dari-inventaris, yang jauh lebih ketat (lihat
        // {@link #dropFromInventory}). Dua aturan berbeda itu ada di C:
        // `mob_addtocurrent` vs `pc_addtocurrent`.
        for (BlockList bl : map.objectsAt(x, y)) {
            if (bl instanceof FloorItem ada && ada.data.id == itemId) {
                ada.lastAmount = ada.data.amount;
                ada.data.amount += amount;
                return ada;
            }
        }

        FloorItem fl = new FloorItem();
        fl.data.id = itemId;
        fl.data.amount = amount;
        fl.data.dura = dura;
        fl.data.protectedFlag = protectedFlag;
        fl.data.owner = owner;

        if (source instanceof Mob mob) {
            BlockList penyerang = mob.attacker > 0
                    ? MapServer.blockById(mob.attacker) : null;
            if (penyerang instanceof User sd) {
                fl.looters[0] = sd.id;
            }
        }
        return tempatkan(fl, map, m, x, y);
    }

    /** Bagian bersama: taruh barang di petaknya lalu tampakkan ke sekitar. */
    private FloorItem tempatkan(FloorItem fl, MapData map, int m, int x, int y) {
        fl.m = m;
        fl.x = x;
        fl.y = y;
        fl.timer = System.currentTimeMillis() / 1000;
        if (!add(fl, map)) {
            return null;
        }
        // clif_object_look_sub2 ke seluruh area: barangnya langsung terlihat
        MapServer.clientView.floorItemAppeared(fl);
        return fl;
    }

    /**
     * pc_addtocurrent(): aturan penggabungan untuk barang yang dijatuhkan
     * <b>pemain dari inventarisnya</b> — jauh lebih ketat daripada jatuhan
     * mob.
     *
     * <p>Keduanya harus <b>utuh</b> (dura penuh), dan sepuluh atributnya
     * harus sama semua: id, pemilik, nama asli, ikon & warna kustom, wujud &
     * warna kustom, catatan, penanda custom, dan perlindungan. Karena itu
     * pedang penyok tidak pernah menyatu dengan pedang mulus, dan barang
     * ber-nama-asli (hasil ukiran) tetap terpisah.</p>
     */
    private boolean bolehGabung(FloorItem ada, org.rtk.common.mmo.Item it) {
        if (ada.data.dura < MapServer.itemDb.info(ada.data.id).durability()) {
            return false;
        }
        return ada.data.id == it.id
                && ada.data.owner == it.owner
                && teks(ada.data.realName).equalsIgnoreCase(teks(it.realName))
                && ada.data.customIcon == it.customIcon
                && ada.data.customIconColor == it.customIconColor
                && ada.data.customLook == it.customLook
                && ada.data.customLookColor == it.customLookColor
                && teks(ada.data.note).equals(teks(it.note))
                && ada.data.custom == it.custom
                && ada.data.protectedFlag == it.protectedFlag;
    }

    private static String teks(String s) {
        return s == null ? "" : s;
    }

    /**
     * pc_getitemscript() (map/pc.c) — pemain memungut barang lantai.
     *
     * <p>Tiga cabang yang ditiru dari C:</p>
     * <ul>
     *   <li><b>Barang ber-id 0 adalah UANG.</b> Jumlahnya langsung masuk emas
     *       pemain dan bendanya lenyap — bukan barang inventaris.</li>
     *   <li>Barang yang {@code ItmDroppable}-nya bukan nol <b>tidak bisa
     *       dipungut</b> pemain biasa (namanya menyesatkan; lihat
     *       {@code ItemDb.cannotBePickedUp}). GM tetap bisa.</li>
     *   <li>Barang tak-bertumpuk yang jumlahnya lebih dari satu diambil
     *       <b>sekeping demi sekeping</b>; sisanya tetap di lantai.</li>
     * </ul>
     *
     * @return true bila ada sesuatu yang berpindah tangan
     */
    public boolean pickUp(User sd, long floorId) {
        FloorItem fl = byId.get(floorId);
        if (fl == null) {
            return false;
        }

        // id 0 = uang
        if (fl.data.id == 0) {
            sd.scriptSetAttr("money",
                    sd.scriptGetAttr("money") + fl.data.amount);
            MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_XPMONEY);
            hapus(fl);
            return true;
        }

        if (MapServer.itemDb.cannotBePickedUp(fl.data.id) && !sd.isGm()) {
            MapServer.clientView.messageToPlayer(sd, 5,
                    "Barang itu tidak bisa dipungut.");
            return false;
        }

        // ⚠️ C juga memeriksa `sd->pickuptype` di sini — setelan pemain
        // "pungut satu / pungut semua" yang belum diport. Port ini berperilaku
        // seperti pickuptype == 0, nilai bawaannya.
        int ambil;
        if (MapServer.itemDb.stackAmountOf(fl.data.id) == 1 && fl.data.amount > 1) {
            ambil = 1;
        } else {
            ambil = fl.data.amount;
        }

        if (!sd.addItemById(fl.data.id, ambil, fl.data.dura)) {
            return false;   // inventaris penuh: barangnya tetap di lantai
        }
        fl.lastAmount = fl.data.amount;
        fl.data.amount -= ambil;
        if (fl.data.amount <= 0) {
            hapus(fl);
        }
        return true;
    }

    /**
     * pc_dropitemmap() (map/pc.c) — pemain menjatuhkan isi satu slot
     * inventaris ke petak tempatnya berdiri.
     *
     * @param type 0 = sekeping, bukan-nol = seluruh isi slot
     */
    public FloorItem dropFromInventory(User sd, int slot, int type) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return null;
        }
        org.rtk.common.mmo.Item it = sd.status.inventory.get(slot);
        if (it.id <= 0 || it.amount <= 0) {
            return null;
        }
        int jumlah = type != 0 ? it.amount : 1;
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return null;
        }

        // Sapuan penggabungan hanya berjalan bila barang yang dijatuhkan
        // sendiri masih utuh — penjaga itu ada di pc_dropitemmap, sebelum
        // pc_addtocurrent dipanggil.
        FloorItem hasil = null;
        if (it.dura >= MapServer.itemDb.info(it.id).durability()) {
            for (BlockList bl : map.objectsAt(sd.x, sd.y)) {
                if (bl instanceof FloorItem ada && bolehGabung(ada, it)) {
                    ada.lastAmount = ada.data.amount;
                    ada.data.amount += jumlah;
                    // pc_addtocurrent mengosongkan daftar yang berhak memungut
                    java.util.Arrays.fill(ada.looters, 0);
                    hasil = ada;
                    break;
                }
            }
        }

        if (hasil == null) {
            FloorItem fl = new FloorItem();
            fl.data.id = it.id;
            fl.data.amount = jumlah;
            fl.data.dura = it.dura;
            fl.data.protectedFlag = it.protectedFlag;
            fl.data.owner = it.owner;
            fl.data.realName = it.realName;
            fl.data.note = it.note;
            fl.data.custom = it.custom;
            fl.data.customIcon = it.customIcon;
            fl.data.customIconColor = it.customIconColor;
            fl.data.customLook = it.customLook;
            fl.data.customLookColor = it.customLookColor;
            hasil = tempatkan(fl, map, sd.m, sd.x, sd.y);
            if (hasil == null) {
                return null;
            }
        }

        it.amount -= jumlah;
        if (it.amount <= 0) {
            sd.status.inventory.remove(slot);
        }
        return hasil;
    }

    /** Cabut barang dari dunia sekaligus dari layar pemain di sekitarnya. */
    public void hapus(FloorItem fl) {
        MapServer.clientView.objectRemoved(fl);   // clif_lookgone, selagi masih di petak
        remove(fl.id);
    }
}
