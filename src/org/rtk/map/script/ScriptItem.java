package org.rtk.map.script;

import org.luaj.vm2.LuaValue;
import org.rtk.common.mmo.Item;
import org.rtk.map.MapServer;
import org.rtk.map.data.ItemDb;

/**
 * Port {@code biteml_type} ("BoundItem") — satu barang <b>milik pemain</b>
 * (slot inventaris, perlengkapan, atau bank) sebagaimana terlihat dari skrip.
 *
 * <p>Aturan pembacaan atribut mengikuti {@code biteml_getattr} (sl.c:3537):
 * ladang milik <b>instance</b> dijawab sendiri, dan apa pun yang tidak
 * dikenal <b>jatuh ke data jenis barang</b> lewat {@code itemdb_search(id)}
 * ({@code iteml_getattr}, sl.c:3450-an). Karena itu {@code item.amount}
 * membaca jumlah di slot ini, sedangkan {@code item.price} membaca harga dari
 * tabel {@code Items}.</p>
 *
 * <p>⚠️ {@code id} sengaja <b>tidak</b> dijawab di lapisan instance — di C ia
 * ikut jatuh ke {@code iteml_getattr} sehingga yang terbaca adalah id jenis
 * barangnya. Nilainya kebetulan sama, tapi jalurnya ditiru supaya barang
 * dengan id tak dikenal berperilaku sama seperti aslinya (mengembalikan id
 * dari entri kosong, bukan id mentah di slot).</p>
 *
 * <p>⚠️ Nama barang mengikuti konvensi yang sama dengan mob (jebakan #12):
 * {@code yname} = {@code ItmIdentifier} (nama untuk skrip), {@code name} =
 * {@code ItmDescription} (nama tampilan). Menukarnya membuat pencarian
 * barang gagal secara senyap.</p>
 */
public final class ScriptItem implements ScriptAttrs {

    /** Slot aslinya; perubahan atribut menulis balik ke sini. */
    public final Item item;

    public ScriptItem(Item item) {
        this.item = item;
    }

    private ItemDb.Info info() {
        return MapServer.itemDb.info(item.id);
    }

    @Override
    public LuaValue scriptAttr(String name) {
        if (name == null) {
            return null;
        }
        // --- ladang instance (biteml_getattr) ---
        switch (name) {
            case "amount": return LuaValue.valueOf(item.amount);
            case "dura": return LuaValue.valueOf(item.dura);
            case "protected": return LuaValue.valueOf((double) item.protectedFlag);
            case "owner": return LuaValue.valueOf((double) item.owner);
            case "realName": return LuaValue.valueOf(item.realName == null ? "" : item.realName);
            case "time": return LuaValue.valueOf((double) item.time);
            case "repairCheck": return LuaValue.valueOf(item.repair);
            case "custom": return LuaValue.valueOf((double) item.custom);
            case "customLook": return LuaValue.valueOf((double) item.customLook);
            case "customLookColor": return LuaValue.valueOf((double) item.customLookColor);
            case "customIcon": return LuaValue.valueOf((double) item.customIcon);
            case "customIconColor": return LuaValue.valueOf((double) item.customIconColor);
            case "note": return LuaValue.valueOf(item.note == null ? "" : item.note);
            default: break;
        }
        // --- jatuh ke data jenis barang (iteml_getattr) ---
        ItemDb.Info d = info();
        ItemDb.Look look = d.look();
        ItemDb.Stats st = d.stats();
        switch (name) {
            case "id": return LuaValue.valueOf((double) d.id());
            case "name": return LuaValue.valueOf(d.tampilan());
            case "yname": return LuaValue.valueOf(d.name() == null ? "" : d.name());
            case "type": return LuaValue.valueOf(d.type());
            case "price": return LuaValue.valueOf(d.buyPrice());
            case "sell": return LuaValue.valueOf(d.sellPrice());
            case "stackAmount": return LuaValue.valueOf(d.stackAmount());
            case "maxAmount": return LuaValue.valueOf(d.maxAmount());
            case "icon": return LuaValue.valueOf(look.icon());
            case "iconC": return LuaValue.valueOf(look.iconColor());
            case "look": return LuaValue.valueOf(look.look());
            case "lookC": return LuaValue.valueOf(look.lookColor());
            case "vita": return LuaValue.valueOf(st.vita());
            case "mana": return LuaValue.valueOf(st.mana());
            case "might": return LuaValue.valueOf(st.might());
            case "will": return LuaValue.valueOf(st.will());
            case "grace": return LuaValue.valueOf(st.grace());
            case "ac": case "armor": return LuaValue.valueOf(st.armor());
            case "hit": return LuaValue.valueOf(st.hit());
            case "dam": return LuaValue.valueOf(st.dam());
            case "healing": return LuaValue.valueOf(st.healing());
            case "minSDmg": return LuaValue.valueOf(st.minSdam());
            case "maxSDmg": return LuaValue.valueOf(st.maxSdam());
            case "minLDmg": return LuaValue.valueOf(st.minLdam());
            case "maxLDmg": return LuaValue.valueOf(st.maxLdam());
            default: return null; // biar pencarian lanjut ke prototype
        }
    }

    /** biteml_setattr (sl.c:3519) — hanya ladang instance yang bisa ditulis. */
    @Override
    public boolean scriptSetAttr(String name, LuaValue value) {
        if (name == null || value == null) {
            return false;
        }
        switch (name) {
            case "id": item.id = value.tolong(); return true;
            case "amount": item.amount = value.toint(); return true;
            case "dura": item.dura = value.toint(); return true;
            case "protected": item.protectedFlag = value.tolong(); return true;
            case "owner": item.owner = value.tolong(); return true;
            case "realName": item.realName = value.tojstring(); return true;
            case "time": item.time = value.tolong(); return true;
            case "repairCheck": item.repair = value.toint(); return true;
            case "custom": item.custom = value.tolong(); return true;
            case "customLook": item.customLook = value.tolong(); return true;
            case "customLookColor": item.customLookColor = value.tolong(); return true;
            case "customIcon": item.customIcon = value.tolong(); return true;
            case "customIconColor": item.customIconColor = value.tolong(); return true;
            case "note": item.note = value.tojstring(); return true;
            default: return false;
        }
    }
}
