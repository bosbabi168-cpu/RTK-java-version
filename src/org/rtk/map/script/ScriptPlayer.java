package org.rtk.map.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;

/**
 * Minimal player model seen by the scripting layer.
 *
 * This is the script-facing slice of struct USER / mmo_charstatus. Right now
 * it is backed by in-memory state so the scripting subsystem can be developed
 * and tested ahead of the full gameplay port; when pc.c lands, the fields
 * and registries move onto the real player object and persist through
 * char-server saves (Registry / RegistryString / NPCRegistry / QuestRegistry
 * tables, see CharDb).
 */
public class ScriptPlayer {

    public int id;
    public String name = "";
    public int level = 1;
    public int sex;
    public int path;      // class/path id
    public int gmLevel;
    public int m, x, y;   // current map position

    /**
     * pc_readglobalreg / pc_setglobalreg dan kawan-kawannya.
     *
     * <p><b>Sengaja tidak final.</b> Untuk pemain sungguhan, keempat peta ini
     * ditunjuk ke peta milik {@code CharStatus} lewat
     * {@link #bindRegistries}, sehingga apa yang ditulis skrip <b>langsung</b>
     * berada di objek yang disimpan {@code CharPersistence} — tanpa perlu
     * penyalinan yang bisa terlewat. Untuk pemain tiruan di uji, isian
     * bawaan di sini yang dipakai.</p>
     */
    public Map<String, Integer> registry = new HashMap<>();
    public Map<String, String> registryString = new HashMap<>();
    public Map<String, Integer> npcInt = new HashMap<>();
    public Map<String, Integer> questReg = new HashMap<>();

    /**
     * Sambungkan keempat registry ke peta milik karakter yang tersimpan.
     *
     * <p>Dipanggil sekali saat objek skrip dibuat untuk pemain sungguhan.
     * Setelah ini, {@code player.registry["x"] = 1} di Lua menulis ke
     * {@code CharStatus.registry} yang sama — jadi ikut tersimpan tanpa
     * langkah tambahan.</p>
     */
    public void bindRegistries(Map<String, Integer> reg, Map<String, String> regStr,
                               Map<String, Integer> npc, Map<String, Integer> quest) {
        this.registry = reg;
        this.registryString = regStr;
        this.npcInt = npc;
        this.questReg = quest;
    }

    // simple inventory for the early item bindings
    public final Map<String, Integer> items = new HashMap<>();

    /**
     * Terapkan level yang ditulis skrip ke pemain sungguhan.
     *
     * <p>Di C, {@code pcl_setattr} untuk "level" menulis langsung ke
     * {@code sd->status.level}. Di sini jalurnya lewat {@link #owner} supaya
     * nilainya tidak hanya menempel pada objek skrip lalu hilang saat objek
     * itu disegarkan dari karakter.</p>
     */
    public interface Owner {
        void scriptSetLevel(int level);

        /**
         * pcl_additem(): tambahkan barang ke inventaris tersimpan.
         * @return true bila muat dan berhasil ditambahkan
         */
        boolean scriptAddItem(String name, int amount);

        /**
         * pcl_removeinventoryitem(): kurangi barang dari inventaris.
         * @return jumlah yang benar-benar terbuang
         */
        int scriptRemoveItem(String name, int amount);

        /** pcl_hasitem(): total jumlah barang bernama itu yang dimiliki. */
        int scriptCountItem(String name);

        /**
         * pcl_sell(): nomor slot inventaris yang berisi salah satu dari
         * nama barang ini, urut slot.
         */
        java.util.List<Integer> scriptItemSlots(java.util.List<String> names);

        /**
         * pcl_getattr(): nilai atribut karakter, atau null bila atribut itu
         * belum diport. Mengembalikan null berarti "biar penanganan biasa
         * yang mengurus", bukan "nilainya nol".
         */
        Long scriptGetAttr(String name);

        /**
         * pcl_setattr(): setel atribut karakter.
         * @return false bila atribut itu belum diport
         */
        boolean scriptSetAttr(String name, long value);
    }

    /** sd->coref: the coroutine driving this player's current interaction. */
    public LuaThread coroutine;

    /** What the script is currently waiting on (dialog/menu/input). */
    public PendingDialog pendingDialog;

    /** Everything the scripts "sent" to this player (minitext, dialogs...). */
    public final List<String> outbox = new ArrayList<>();

    /** Cached userdata so scripts always see the same object identity. */
    LuaValue udata;
    LuaValue registryUdata;
    LuaValue registryStringUdata;
    LuaValue npcIntUdata;
    LuaValue questUdata;

    public ScriptPlayer(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Pemain sungguhan yang diwakili objek ini, bila ada.
     *
     * <p>Di uji dan skrip mandiri nilainya null — di situlah {@link #outbox}
     * dipakai sebagai pengganti. Saat server berjalan, ladang ini yang
     * membuat mesin skrip tahu ke sesi mana paket dialog harus dikirim.</p>
     *
     * <p>Bertipe Object supaya paket {@code script} tidak bergantung pada
     * {@code org.rtk.map.User} — arah ketergantungannya sengaja satu arah.</p>
     */
    public Object owner;

    /** Description of a blocking call a script yielded on. */
    public static class PendingDialog {
        public final String kind;    // "menu", "menuSeq", "dialog", "input", "inputSeq", "buy"
        public final String message;
        public final List<String> options;

        /** Harga per barang, hanya terisi untuk ragam "buy". */
        public List<Integer> prices;

        public PendingDialog(String kind, String message, List<String> options) {
            this.kind = kind;
            this.message = message;
            this.options = options;
        }

        @Override
        public String toString() {
            return kind + "(" + message + (options != null ? " " + options : "") + ")";
        }
    }
}
