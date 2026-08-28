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

        /**
         * Atribut yang <b>bukan bilangan</b> — string dan boolean.
         *
         * <p>Jembatan {@code scriptGetAttr} mengembalikan {@code Long}, jadi
         * ia tidak bisa membawa {@code speech} (string) maupun
         * {@code flank}/{@code backstab} (boolean).</p>
         *
         * <p>⚠️ Boolean <b>wajib</b> lewat sini, bukan dipaksakan jadi angka:
         * di Lua <b>angka 0 itu benar</b>, sehingga
         * {@code if player.flank then} akan selalu masuk kalau nilainya
         * dikirim sebagai 0. Kesalahan seperti itu tidak melempar error di
         * mana pun — ia hanya membuat 134 pemakaian {@code flank}/
         * {@code backstab} di skrip pertarungan berperilaku terbalik.</p>
         *
         * @return nilainya, atau null bila atribut ini bukan urusannya
         */
        default org.luaj.vm2.LuaValue scriptGetSpecial(String attr) {
            return null;
        }

        /** Pasangan tulis {@link #scriptGetSpecial}. */
        default boolean scriptSetSpecial(String attr, org.luaj.vm2.LuaValue v) {
            return false;
        }

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
         * pcl_addspell(): ajarkan mantra. Menerima nama atau id.
         * @return false bila mantranya tak dikenal atau sudah dimiliki
         */
        boolean scriptAddSpell(String nameOrId);

        /** pcl_hasspell(): true bila pemain sudah punya mantra itu. */
        boolean scriptHasSpell(String nameOrId);

        /**
         * pcl_hasspace(): apakah barang ini masih muat di inventaris.
         *
         * <p>Argumen ke-4 dan seterusnya di C (engrave, customLook, …)
         * <b>tidak memengaruhi hasil</b> — {@code engraved} dihitung lalu
         * tidak pernah dibaca. Karena itu tidak ikut diport.</p>
         */
        boolean scriptHasSpace(String nameOrId, int amount, long owner);

        /**
         * pcl_setthreat(): setel ancaman pemain ini pada mob tertentu ke
         * nilai tetap — beda dari {@code addThreat} yang menambah.
         */
        void scriptSetThreat(long mobId, long amount);

        /**
         * pcl_removespell(): lupakan mantra. Menerima nama atau id.
         *
         * <p>Ikut memicu kait skrip {@code on_forget} milik mantra itu dan
         * paket {@code clif_removespell} (0x18) ke klien, seperti di C.</p>
         */
        void scriptRemoveSpell(String nameOrId);

        /**
         * pcl_getequippeditem(): barang di slot perlengkapan itu, atau null
         * bila slotnya kosong (di C: {@code lua_pushnil}).
         */
        org.rtk.common.mmo.Item scriptEquippedItem(int slot);

        /** pcl_getinventoryitem(): barang di slot inventaris itu, atau null. */
        org.rtk.common.mmo.Item scriptInventoryItem(int slot);

        /**
         * pcl_hasduration(): true bila mantra bernama itu sedang aktif
         * dengan sisa durasi &gt; 0. Nama dicari lewat {@code magicdb_id}.
         */
        boolean scriptHasDuration(String spellName);

        /**
         * pcl_killcount(): berapa kali mob itu sudah dibunuh. Menerima nama
         * ({@code MobIdentifier}) atau id angka, seperti di C.
         */
        long scriptKillCount(String mobNameOrId);

        /** pcl_setkillcount(): setel hitungan bunuh mob itu ke nilai tertentu. */
        void scriptSetKillCount(String mobNameOrId, long amount);

        /**
         * pcl_flushkills(): hapus hitungan bunuh.
         *
         * @param mobNameOrId null/kosong atau id 0 berarti hapus SEMUA
         */
        void scriptFlushKills(String mobNameOrId);

        /**
         * pcl_addlegend(): tambahkan satu baris legenda.
         *
         * <p>⚠️ C <b>tidak</b> memeriksa duplikat — memanggil dua kali dengan
         * nama sama menghasilkan dua baris. Ditiru apa adanya.
         */
        void scriptAddLegend(String text, String name, int icon, int color, long tchaid);

        /**
         * pcl_haslegend(): true bila ada legenda bernama itu.
         *
         * <p>⚠️ C memakai {@code strcmp} di sini — <b>peka besar-kecil</b>,
         * berbeda dari {@link #scriptRemoveLegendByName} yang memakai
         * {@code strcmpi}. Asimetri itu ada di sumbernya, jangan diseragamkan.
         */
        boolean scriptHasLegend(String name);

        /**
         * pcl_removelegendbyname(): buang semua legenda bernama itu.
         *
         * <p>⚠️ Memakai {@code strcmpi} — <b>tidak</b> peka besar-kecil.
         */
        void scriptRemoveLegendByName(String name);

        /**
         * pcl_bankdeposit(): titipkan barang ke bank.
         * @return false bila barangnya tidak ada di inventaris
         */
        boolean scriptBankDeposit(String itemName, int amount);

        /**
         * pcl_bankwithdraw(): ambil barang dari bank.
         * @return jumlah yang benar-benar diambil
         */
        int scriptBankWithdraw(String itemName, int amount);

        /** bll_addnpc(): lahirkan NPC sementara dari skrip. */
        void scriptAddNpc(String name, int m, int x, int y, int subtype,
                          long actionTime, long duration, long ownerId,
                          long moveTime, String displayName);

        /** pcl_calcstat(): hitung ulang nilai turunan dari perlengkapan. */
        void scriptCalcStat();

        /** pcl_addthreat(): catat ancaman pemain ini pada sebuah mob. */
        void scriptAddThreat(long mobId, long damage);

        /**
         * pcl_getobjectsincell(): benda di satu petak, disaring jenis
         * ({@code BL_PC}=1, {@code BL_MOB}=2, {@code BL_NPC}=4 di C).
         */
        java.util.List<Object> scriptObjectsInCell(int m, int x, int y, int type);

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

    /**
     * Cached userdata so scripts always see the same object identity.
     *
     * <p>⚠️ Karena di-cache, satu {@code ScriptPlayer} hanya boleh dipakai
     * oleh <b>satu</b> {@link ScriptEngine}. Memakainya dengan engine kedua
     * akan mengembalikan objek milik engine pertama, dan kegagalannya
     * membingungkan: skrip tampak berjalan tapi tidak pernah menyentuh
     * keadaan yang benar. Pernah kejadian di uji.</p>
     */
    LuaValue udata;
    LuaValue registryUdata;
    LuaValue registryStringUdata;
    LuaValue npcIntUdata;
    LuaValue mapRegistryUdata;
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
