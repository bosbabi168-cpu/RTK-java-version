package org.rtk.map.script;

import org.luaj.vm2.LuaValue;

/**
 * Benda permainan yang bisa menjawab pembacaan atribut dari skrip Lua.
 *
 * <p>Dipakai NPC (dan nanti mob) supaya {@code npc.yname},
 * {@code npc.bankNPC}, dan kawan-kawannya terbaca dari objek aslinya.
 * Antarmukanya ditaruh di paket skrip supaya ketergantungannya satu arah:
 * {@code org.rtk.map} bergantung ke sini, bukan sebaliknya.</p>
 */
public interface ScriptAttrs {

    /**
     * Nilai atribut, atau null bila tidak dikenal.
     *
     * <p>Mengembalikan null berarti "biar pencarian lanjut ke prototype /
     * data table", bukan "nilainya nol" — bedanya penting karena skrip
     * sering menambahkan ladangnya sendiri ke objek.</p>
     */
    LuaValue scriptAttr(String name);

    /**
     * Setel atribut dari skrip; false berarti atribut itu tidak dikenal.
     *
     * <p>Bawaannya menolak semua — benda yang memang bisa diubah skrip
     * (mob: {@code health}, {@code target}, …) menimpanya.</p>
     */
    default boolean scriptSetAttr(String name, LuaValue value) {
        return false;
    }
}
