package org.rtk.map;

import org.luaj.vm2.LuaValue;

import org.rtk.common.mmo.Item;
import org.rtk.map.data.BlockList;
import org.rtk.map.script.ScriptAttrs;

/**
 * Port of {@code struct flooritem_data} (map/map.h:372) — sekeping barang
 * yang tergeletak di petak peta.
 *
 * <p>Ini benda dunia keempat setelah pemain, NPC, dan mob: ia menempati
 * petak, punya id blok sendiri, dan ikut tersapu {@code map_foreachincell}.
 * Selama ia belum ada, {@code dropItem} / {@code dropItemXY} / {@code pickUp}
 * (~95 titik panggilan) tidak punya tempat untuk menaruh apa pun, dan
 * jatuhan mob lenyap begitu saja.</p>
 *
 * <h2>Dua daftar id yang gampang tertukar</h2>
 *
 * <ul>
 *   <li>{@code looters} — siapa yang <b>berhak memungut</b>. Diisi saat mob
 *       mati, dari grup penyerangnya (atau penyerangnya sendiri bila tanpa
 *       grup).</li>
 *   <li>{@code trapsTable} — siapa yang <b>bisa melihat</b> jebakan ini.
 *       Tinggal di {@link Item#trapsTable} karena di C pun ia bagian dari
 *       {@code struct item}, bukan dari flooritem. Inilah yang membedakan
 *       {@code getObjectsInCell} dari {@code getObjectsInCellWithTraps}.</li>
 * </ul>
 *
 * <p>⚠️ {@code timer} <b>bukan</b> hitung mundur. Di C ia diisi
 * {@code time(NULL)} saat barang jatuh dan tidak pernah dibaca oleh server —
 * hanya skrip yang memakainya untuk tahu sudah berapa lama barang tergeletak.
 * Tidak ada pembersihan otomatis barang lantai di server aslinya.</p>
 */
public final class FloorItem extends BlockList implements ScriptAttrs {

    /** FLOORITEM_START_NUM (map/map.h:18) — awal rentang id barang lantai. */
    public static final long FLOORITEM_START_NUM = 2047483647L;

    /** MAX_FLOORITEM (map/map.h:19). */
    public static final int MAX_FLOORITEM = 100000000;

    /** MAX_GROUP_MEMBERS — panjang daftar yang berhak memungut. */
    public static final int MAX_LOOTERS = 8;

    /** Isi barangnya; tata letaknya sama dengan barang di inventaris. */
    public final Item data = new Item();

    /** {@code fl->lastamount}: jumlah sebelum perubahan terakhir. */
    public int lastAmount;

    /** Detik epoch saat barang ini jatuh — lihat catatan di atas. */
    public long timer;

    /** Id pemain yang berhak memungut; 0 = slot kosong. */
    public final long[] looters = new long[MAX_LOOTERS];

    public FloorItem() {
        this.type = Type.ITEM;
    }

    /** Jebakan hanya terlihat oleh yang sudah menemukannya. */
    public boolean isTrap() {
        return MapServer.itemDb.info(data.id).type() == org.rtk.map.data.ItemDb.ITM_TRAPS;
    }

    /** Apakah pemain ini ada di {@code trapsTable} barang tersebut. */
    public boolean spottedBy(long playerId) {
        for (int id : data.trapsTable) {
            if (id != 0 && id == playerId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Terlihat oleh pemain ini? Jebakan yang belum ditemukan tidak digambar
     * dan tidak dikembalikan {@code getObjectsInCell} biasa —
     * {@code map_foreachincell} melewatinya, sedangkan varian
     * {@code ...withtraps} menyertakannya.
     */
    public boolean visibleTo(long playerId) {
        return !isTrap() || spottedBy(playerId);
    }

    /** fll_addTrapSpotters(): tandai bahwa pemain ini menemukan jebakannya. */
    public void addTrapSpotter(long playerId) {
        for (int i = 0; i < data.trapsTable.length; i++) {
            if (data.trapsTable[i] == 0) {
                data.trapsTable[i] = (int) playerId;
                return;
            }
        }
        // Larik di C tetap 100 slot; di sini tumbuh sesuai kebutuhan supaya
        // blob karakter tidak membawa 100 nol untuk tiap barang.
        int[] besar = java.util.Arrays.copyOf(data.trapsTable,
                Math.max(8, data.trapsTable.length * 2));
        besar[data.trapsTable.length] = (int) playerId;
        data.trapsTable = besar;
    }

    // ------------------------------------------------------------------
    // fll_getattr / fll_setattr
    // ------------------------------------------------------------------

    @Override
    public LuaValue scriptAttr(String attr) {
        return switch (attr) {
            case "id" -> LuaValue.valueOf((double) data.id);
            case "amount" -> LuaValue.valueOf(data.amount);
            case "lastAmount" -> LuaValue.valueOf(lastAmount);
            case "owner" -> LuaValue.valueOf((double) data.owner);
            case "realName" -> LuaValue.valueOf(data.realName == null ? "" : data.realName);
            case "dura" -> LuaValue.valueOf(data.dura);
            case "protected" -> LuaValue.valueOf((double) data.protectedFlag);
            case "custom" -> LuaValue.valueOf((double) data.custom);
            case "customIcon" -> LuaValue.valueOf((double) data.customIcon);
            case "customIconC" -> LuaValue.valueOf((double) data.customIconColor);
            case "customLook" -> LuaValue.valueOf((double) data.customLook);
            case "customLookC" -> LuaValue.valueOf((double) data.customLookColor);
            case "note" -> LuaValue.valueOf(data.note == null ? "" : data.note);
            case "timer" -> LuaValue.valueOf((double) timer);
            case "looters" -> {
                org.luaj.vm2.LuaTable t = new org.luaj.vm2.LuaTable();
                for (int i = 0; i < looters.length; i++) {
                    t.set(i + 1, LuaValue.valueOf((double) looters[i]));
                }
                yield t;
            }
            // fll_getattr jatuh ke iteml_getattr untuk nama apa pun yang lain,
            // jadi sifat JENIS barangnya ikut terbaca dari sini.
            case "name" -> LuaValue.valueOf(MapServer.itemDb.info(data.id).tampilan());
            case "yname" -> LuaValue.valueOf(MapServer.itemDb.info(data.id).name());
            case "type" -> LuaValue.valueOf(MapServer.itemDb.info(data.id).type());
            default -> null;
        };
    }

    @Override
    public boolean scriptSetAttr(String attr, LuaValue value) {
        switch (attr) {
            case "amount" -> data.amount = (int) value.todouble();
            case "owner" -> data.owner = (long) value.todouble();
            case "dura" -> data.dura = (int) value.todouble();
            case "realName" -> data.realName = value.tojstring();
            case "protected" -> data.protectedFlag = (long) value.todouble();
            case "custom" -> data.custom = (long) value.todouble();
            case "customIcon" -> data.customIcon = (long) value.todouble();
            case "customIconC" -> data.customIconColor = (long) value.todouble();
            case "customLook" -> data.customLook = (long) value.todouble();
            case "customLookC" -> data.customLookColor = (long) value.todouble();
            case "note" -> data.note = value.tojstring();
            case "timer" -> timer = (long) value.todouble();
            case "looters" -> {
                if (!value.istable()) {
                    return false;
                }
                org.luaj.vm2.LuaTable t = value.checktable();
                for (int i = 0; i < looters.length; i++) {
                    looters[i] = (long) t.get(i + 1).optdouble(0);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "FloorItem[" + data.id + " x" + data.amount + " @" + m + ":" + x + "," + y + "]";
    }
}
