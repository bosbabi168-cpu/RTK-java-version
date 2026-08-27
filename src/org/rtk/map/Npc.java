package org.rtk.map;

import org.rtk.map.data.BlockList;

/**
 * Port of struct NPC (map/npc.h) — satu NPC yang berdiri di peta.
 *
 * <p>Sama seperti {@link User}, kelas ini <b>adalah</b> benda di peta
 * ({@link BlockList}) dan membawa datanya sendiri. Sumber datanya tabel
 * {@code NPCs<serverId>}, dimuat oleh {@link NpcRegistry}.</p>
 *
 * <h3>Dua nama, dua kegunaan</h3>
 * {@link #name} ({@code NpcIdentifier}) adalah <b>nama skrip</b> — dipakai
 * mesin Lua untuk mencari kelas skripnya, mis. {@code sl_doscript(name,
 * "click", ...)}. {@link #displayName} ({@code NpcDescription}) adalah yang
 * dilihat pemain. Keduanya sering berbeda, jadi jangan ditukar.
 *
 * <h3>Penomoran id</h3>
 * Id blok <b>bukan</b> {@code NpcId} apa adanya, melainkan
 * {@code NPC_START_NUM + NpcId - 2} persis seperti versi C — lihat
 * {@link #blockIdFor}. Klien mengirim balik id inilah saat pemain mengklik
 * ({@code clif_handle_clickgetinfo}, opcode 0x43), jadi rumusnya tidak boleh
 * diubah.
 */
public final class Npc extends BlockList
        implements org.rtk.map.script.ScriptAttrs {

    /** Awal rentang id blok milik NPC (NPC_START_NUM di map.h). */
    public static final long NPC_START_NUM = 3221225472L;

    /** Id khusus NPC F1 — satu-satunya yang tidak mengikuti rumus biasa. */
    public static final long F1_NPC = 4294967295L;

    /**
     * Awal rentang id NPC <b>sementara</b> (NPCT_START_NUM di map.h) —
     * NPC yang dilahirkan skrip, mis. jebakan dan dekorasi.
     * Rentangnya terpisah dari NPC tetap supaya keduanya tidak bertabrakan.
     */
    public static final long NPCT_START_NUM = 3321225472L;

    /** Nama skrip ({@code NpcIdentifier}) — kunci ke kelas skrip Lua. */
    public String name = "";

    /** Nama yang dilihat pemain ({@code NpcDescription}). */
    public String displayName = "";

    /** {@code NpcId} asli dari database, sebelum digeser jadi id blok. */
    public long npcId;

    // ---- tampilan ----
    public int sex;
    public int side;
    public int state;
    public int face;
    public int faceColor;
    public int hair;
    public int hairColor;
    public int armorColor;
    public int skinColor;

    // ---- perilaku ----
    /** {@code NpcIsChar}: 1 = tampil sebagai karakter, bukan grafik tunggal. */
    /**
     * {@code enum { SCRIPT, FLOOR }} (map/map.h:71) — nilai {@code subtype}.
     * NPC ber-subtype 1 atau 2 adalah benda lantai (jebakan, dekorasi) yang
     * kait {@code click}-nya ikut terpicu oleh mob yang menginjaknya.
     */
    public static final int SUBTYPE_SCRIPT = 0;
    public static final int SUBTYPE_FLOOR = 1;

    public int npcType;
    public boolean f1Npc;
    public boolean shopNpc;
    public boolean repairNpc;
    public boolean bankNpc;
    public boolean canReceiveItem;
    public int returnDistance;

    /**
     * {@code NpcCanReceiveItem}: NPC ini menerima barang yang diserahkan
     * pemain, lewat kait skrip {@code handItem}.
     *
     * <p>⚠️ Nilai 0 <b>bukan</b> berarti diam saja — NPC menjawab dengan
     * ejekan ("Keep your junky ... with you!"). Itu satu-satunya umpan balik
     * pemain, jadi cabang itu tidak boleh dihilangkan.</p>
     */
    public int receiveItem;
    public long moveTime;
    public long actionTime;

    /**
     * Perlengkapan per slot {@link org.rtk.map.data.Equip}; null = kosong.
     * Diisi dari tabel {@code NPCEquipment<serverId>}.
     */
    public final org.rtk.common.mmo.Item[] equip =
            new org.rtk.common.mmo.Item[org.rtk.map.data.Equip.COUNT];

    /** true bila slot ini terisi (setara {@code equip[x].amount} di C). */
    public boolean hasEquip(int slot) {
        return slot >= 0 && slot < equip.length
                && equip[slot] != null && equip[slot].amount > 0;
    }

    /**
     * Penghitung timer, dalam milidetik. Ditambah 100 setiap tik oleh
     * {@link NpcRegistry#runTimers}, lalu direset saat skripnya dipanggil —
     * persis pola {@code npc_movetime}/{@code npc_action} di C.
     */
    public long moveTimer;
    public long actionTimer;

    /** Penghitung durasi hidup, untuk NPC sementara berumur terbatas. */
    public long duraTimer;

    /** Umur NPC sementara dalam ms; 0 = tidak pernah kedaluwarsa. */
    public long duration;

    /** true bila NPC ini dilahirkan skrip, bukan dari tabel NPCs. */
    public boolean temporary;

    /** true bila NPC sedang kembali ke titik awalnya ({@code returning}). */
    public boolean returning;

    /** Waktu aksi terakhir, dipakai skrip untuk menjeda perilaku. */
    public long lastAction;

    /**
     * Pemain yang "memiliki" NPC ini, bila ada ({@code nd->owner} di C).
     * NPC milik pemain menerima pemainnya sebagai argumen kedua saat
     * skrip {@code move}/{@code action} dipanggil.
     */
    public long ownerId;

    /** Posisi awal, untuk NPC yang berjalan lalu kembali. */
    public int startM = -1;
    public int startX;
    public int startY;

    public Npc() {
        this.type = Type.NPC;
    }

    /**
     * Rumus id blok versi C: {@code NPC_START_NUM + NpcId - 2}.
     *
     * Pergeseran &minus;2 itu tampak aneh tapi <b>disengaja dipertahankan</b>:
     * klien mengirim balik id ini saat mengklik NPC, jadi mengubahnya
     * membuat setiap klik meleset ke NPC lain (atau ke tidak ada).
     */
    public static long blockIdFor(long npcId) {
        return NPC_START_NUM + npcId - 2;
    }

    /**
     * true bila NPC ini ikut didaftarkan ke indeks blok peta.
     * Versi C hanya memasukkan {@code subtype < 3}; sisanya ada di indeks id
     * saja (dipakai skrip, tapi tidak menempati petak).
     */
    public boolean occupiesTile() {
        return subtype < 3;
    }

    /**
     * Atribut NPC yang dibaca skrip ({@code pcl}/{@code npcl_getattr} di C).
     *
     * <p>{@code yname} adalah <b>nama skrip</b> dan {@code name} nama
     * tampilan — di C keduanya memang terbalik dari dugaan: {@code nd->name}
     * dipakai untuk mencari kelas skrip, {@code nd->npc_name} untuk
     * ditampilkan. Skrip memakai {@code yname} 565x, jadi salah memetakan
     * di sini akan terasa di mana-mana.</p>
     */
    @Override
    public org.luaj.vm2.LuaValue scriptAttr(String attr) {
        return switch (attr) {
            case "yname" -> org.luaj.vm2.LuaValue.valueOf(name);
            case "name" -> org.luaj.vm2.LuaValue.valueOf(displayName);
            case "id" -> org.luaj.vm2.LuaValue.valueOf((double) id);
            case "m", "map" -> org.luaj.vm2.LuaValue.valueOf(m);
            case "x" -> org.luaj.vm2.LuaValue.valueOf(x);
            case "y" -> org.luaj.vm2.LuaValue.valueOf(y);
            case "side" -> org.luaj.vm2.LuaValue.valueOf(side);
            case "sex" -> org.luaj.vm2.LuaValue.valueOf(sex);
            case "state" -> org.luaj.vm2.LuaValue.valueOf(state);
            case "face" -> org.luaj.vm2.LuaValue.valueOf(face);
            case "hair" -> org.luaj.vm2.LuaValue.valueOf(hair);
            case "shopNPC" -> org.luaj.vm2.LuaValue.valueOf(shopNpc ? 1 : 0);
            case "bankNPC" -> org.luaj.vm2.LuaValue.valueOf(bankNpc ? 1 : 0);
            case "repairNPC" -> org.luaj.vm2.LuaValue.valueOf(repairNpc ? 1 : 0);
            case "actionTime" -> org.luaj.vm2.LuaValue.valueOf((double) actionTime);
            case "moveTime" -> org.luaj.vm2.LuaValue.valueOf((double) moveTime);
            // Titik awal & perilaku kembali: dipakai skrip AI NPC untuk
            // menghitung jarak dari rumahnya. Tanpa ini `npc.startX` bernilai
            // nil dan `distanceXY()` gagal dengan "arithmetic on nil" —
            // pernah membanjiri log dengan ribuan baris.
            case "startM" -> org.luaj.vm2.LuaValue.valueOf(startM);
            case "startX" -> org.luaj.vm2.LuaValue.valueOf(startX);
            case "startY" -> org.luaj.vm2.LuaValue.valueOf(startY);
            case "retDist" -> org.luaj.vm2.LuaValue.valueOf(returnDistance);
            case "returning" -> org.luaj.vm2.LuaValue.valueOf(returning);
            case "owner" -> org.luaj.vm2.LuaValue.valueOf((double) ownerId);
            case "duration" -> org.luaj.vm2.LuaValue.valueOf((double) duration);
            case "lastAction" -> org.luaj.vm2.LuaValue.valueOf((double) lastAction);
            case "subType" -> org.luaj.vm2.LuaValue.valueOf(subtype);
            case "npcType" -> org.luaj.vm2.LuaValue.valueOf(npcType);
            case "look" -> org.luaj.vm2.LuaValue.valueOf((double) graphicId);
            case "lookColor" -> org.luaj.vm2.LuaValue.valueOf((double) graphicColor);
            case "faceColor" -> org.luaj.vm2.LuaValue.valueOf(faceColor);
            case "hairColor" -> org.luaj.vm2.LuaValue.valueOf(hairColor);
            case "skinColor" -> org.luaj.vm2.LuaValue.valueOf(skinColor);
            case "armorColor" -> org.luaj.vm2.LuaValue.valueOf(armorColor);
            default -> null;
        };
    }

    /**
     * npcl_setattr(): atribut NPC yang boleh diubah skrip.
     *
     * <p>Skrip AI menulis {@code npc.side} dan {@code npc.returning} setiap
     * langkah, jadi tanpa setter ini NPC tidak akan pernah berbelok atau
     * pulang ke titik awalnya.</p>
     */
    @Override
    public boolean scriptSetAttr(String attr, org.luaj.vm2.LuaValue v) {
        switch (attr) {
            case "side" -> side = v.toint();
            case "state" -> state = v.toint();
            case "returning" -> returning = v.toboolean();
            case "retDist" -> returnDistance = v.toint();
            case "actionTime" -> actionTime = (long) v.todouble();
            case "duration" -> duration = (long) v.todouble();
            case "owner" -> ownerId = (long) v.todouble();
            case "lastAction" -> lastAction = (long) v.todouble();
            case "look" -> graphicId = (long) v.todouble();
            case "lookColor" -> graphicColor = (long) v.todouble();
            case "face" -> face = v.toint();
            case "faceColor" -> faceColor = v.toint();
            case "hair" -> hair = v.toint();
            case "hairColor" -> hairColor = v.toint();
            case "skinColor" -> skinColor = v.toint();
            case "armorColor" -> armorColor = v.toint();
            case "sex" -> sex = v.toint();
            case "subType" -> subtype = v.toint();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "Npc{" + name + (displayName.isEmpty() ? "" : " \"" + displayName + "\"")
                + " id=" + id + " @" + m + ":" + x + "," + y + "}";
    }
}
