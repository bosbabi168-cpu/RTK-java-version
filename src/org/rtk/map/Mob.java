package org.rtk.map;

import org.rtk.map.data.BlockList;

/**
 * Port of struct mobspawn_data / MOB (map/map.h) — satu mob yang sedang
 * hidup di peta.
 *
 * <p>Jenisnya ada di {@link #data}; kelas ini hanya membawa keadaan yang
 * berubah-ubah (nyawa sekarang, sasaran, penghitung timer). Banyak mob
 * berbagi satu {@link MobData}, jadi <b>jangan</b> menulis nilai per-mob ke
 * sana.</p>
 */
public final class Mob extends BlockList
        implements org.rtk.map.script.ScriptAttrs,
                   org.rtk.map.script.ScriptCallBase {

    /** Awal rentang id blok milik mob (MOB_START_NUM di map.h). */
    public static final long MOB_START_NUM = 1073741823L;

    public MobData data;

    /** Baris {@code Spawns<serverId>} yang melahirkan mob ini. */
    public long spawnId;

    /**
     * Pemain yang "memiliki" mob ini ({@code mob->owner} di C) — diisi
     * {@code spawn()} dari skrip; jebakan memakainya untuk tahu siapa
     * pemasangnya. 0 bila mob tabel biasa.
     */
    public long owner;

    /**
     * {@code mob->onetime}: mob yang dilahirkan skrip, bukan dari tabel
     * {@code Spawns}. Setelah mati ia <b>tidak lahir kembali</b> — itu satu-
     * satunya beda perilakunya, dan penjaganya ada di
     * {@link MobRegistry#runTimers}.
     */
    public boolean oneTime;

    public long currentVita;
    public long currentMana;
    public long maxVita;
    public long maxMana;

    /** Keadaan hidup/mati; MOB_DEAD berarti tidak digambar dan tidak menghalangi. */
    public int state = MobData.MOB_ALIVE;

    /** Keadaan tampilan (setara {@code charstate}), bukan hidup/mati. */
    public int charState;

    public int side = 2;
    public int look;
    public int lookColor;

    /** Id benda yang sedang jadi sasaran, 0 bila tidak ada. */
    public long target;

    /**
     * {@code mob->canmove}: penanda "langkah berikutnya terhalang", diisi
     * sapuan {@code mob_move} pada petak tujuan. Dinolkan tiap kali
     * {@code move_mob_intent} dipanggil.
     *
     * <p>⚠️ Namanya terbalik dari artinya: {@code canmove == 1} berarti
     * <b>TIDAK</b> boleh melangkah.</p>
     */
    public boolean canMove;

    /**
     * Id pemain yang terakhir melukai mob ini ({@code mob->attacker} di C).
     *
     * <p>Dipakai saat mob mati: dialah yang menerima jatuhan barang dan
     * yang dikirim ke kait {@code on_death}. Tanpa ini, mob mati tanpa ada
     * yang bisa disebut pembunuhnya.</p>
     */
    public long attacker;

    /**
     * Kerusakan dan hasil lemparan kritis pukulan berikutnya — pasangan
     * {@code User.damage}/{@code User.critChance}. Diisi skrip Lua atau
     * {@code removeHealth}, bukan dihitung server.
     */
    public double damage;
    public int critChance;

    /**
     * Tabel ancaman: id pemain &rarr; total kerusakan yang ia timbulkan.
     *
     * <p>Diisi lewat {@code player:addThreat(mobId, damage)} dari skrip.
     * AI memakainya untuk memilih sasaran — yang paling banyak melukai
     * yang dikejar, bukan yang terakhir memukul.</p>
     */
    public final java.util.Map<Long, Long> threat = new java.util.LinkedHashMap<>();

    /** Tambah ancaman dari satu pemain, dan jadikan ia penyerang terakhir. */
    public void addThreat(long playerId, long amount) {
        if (playerId == 0 || amount <= 0) {
            return;
        }
        threat.merge(playerId, amount, Long::sum);
        attacker = playerId;
    }

    /**
     * pcl_setthreat(): setel ancaman satu pemain ke nilai TERTENTU (bukan
     * menambah seperti {@link #addThreat}).
     *
     * <p>⚠️ <b>CELAH YANG DIKETAHUI:</b> C juga menyegarkan
     * {@code mob->lastaction} di sini (sl.c, {@code pcl_setthreat}). Port ini
     * belum punya padanannya — tidak ada ladang "kapan terakhir mob berbuat
     * sesuatu", hanya {@code moveTimer}/{@code attackTimer}. Akibatnya baru
     * terasa ketika logika mob-menganggur-lalu-pulang diport: mob yang
     * diancam lewat skrip tidak akan ikut dianggap "baru saja aktif".
     * Sengaja TIDAK dikarang di sini supaya tidak jadi state mati yang
     * menyesatkan.</p>
     */
    public void setThreat(long playerId, long amount) {
        if (playerId == 0) {
            return;
        }
        threat.put(playerId, amount);
    }

    /** mobl_checkthreat(): ancaman pemain itu, 0 bila tidak ada di tabel. */
    public long checkThreat(long playerId) {
        return threat.getOrDefault(playerId, 0L);
    }

    /** Id pemain dengan ancaman terbesar, atau 0 bila tabelnya kosong. */
    public long topThreat() {
        long best = 0;
        long bestVal = 0;
        for (var e : threat.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    /** Penghitung timer dalam milidetik, sepola dengan NPC. */
    public long moveTimer;
    public long attackTimer;

    /** Waktu mati terakhir, untuk menghitung kelahiran ulang. */
    public long lastDeath;

    /** Titik kelahiran, tempat mob kembali bila terlalu jauh. */
    public int startM = -1;
    public int startX;
    public int startY;

    public Mob() {
        this.type = Type.MOB;
    }

    /** Nama kelas skrip Lua — {@code mob->data->yname} di C. */
    public String scriptName() {
        return data == null ? "" : data.yname;
    }

    /** Nama yang dilihat pemain. */
    public String displayName() {
        return data == null ? "" : data.name;
    }

    public boolean isAlive() {
        return state != MobData.MOB_DEAD;
    }

    /** true bila mob ini digambar sebagai karakter (paket 0x33). */
    public boolean drawsAsCharacter() {
        return data != null && data.mobType == 1;
    }

    /**
     * Atribut mob yang dibaca skrip ({@code mobl_getattr} di C).
     *
     * <p>Nilai yang berubah-ubah ({@code health}, {@code target}) datang
     * dari mob ini; yang tetap ({@code minDam}, {@code level}) dari
     * jenisnya. Membedakan keduanya penting: menulis nilai per-mob ke
     * {@link MobData} akan mengubah <b>semua</b> mob sejenis sekaligus.</p>
     */
    @Override
    public org.luaj.vm2.LuaValue scriptAttr(String attr) {
        var n = org.luaj.vm2.LuaValue.class;
        return switch (attr) {
            case "health" -> org.luaj.vm2.LuaValue.valueOf((double) currentVita);
            case "maxHealth" -> org.luaj.vm2.LuaValue.valueOf((double) maxVita);
            case "magic" -> org.luaj.vm2.LuaValue.valueOf((double) currentMana);
            case "maxMagic" -> org.luaj.vm2.LuaValue.valueOf((double) maxMana);
            case "target" -> org.luaj.vm2.LuaValue.valueOf((double) target);
            case "attacker" -> org.luaj.vm2.LuaValue.valueOf((double) attacker);
            case "state" -> org.luaj.vm2.LuaValue.valueOf(state);
            case "charState" -> org.luaj.vm2.LuaValue.valueOf(charState);
            case "side" -> org.luaj.vm2.LuaValue.valueOf(side);
            case "look" -> org.luaj.vm2.LuaValue.valueOf(look);
            case "lookColor" -> org.luaj.vm2.LuaValue.valueOf(lookColor);
            case "id" -> org.luaj.vm2.LuaValue.valueOf((double) id);
            case "m", "map" -> org.luaj.vm2.LuaValue.valueOf(m);
            case "x" -> org.luaj.vm2.LuaValue.valueOf(x);
            case "y" -> org.luaj.vm2.LuaValue.valueOf(y);
            // dari jenisnya — sama untuk semua mob sejenis
            case "yname" -> org.luaj.vm2.LuaValue.valueOf(scriptName());
            case "name" -> org.luaj.vm2.LuaValue.valueOf(displayName());
            case "level" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.level);
            case "minDam" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.minDam);
            case "maxDam" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.maxDam);
            case "might" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.might);
            case "grace" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.grace);
            case "will" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.will);
            case "hit" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.hit);
            case "armor" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.baseArmor);
            case "protection" -> data == null ? null
                    : org.luaj.vm2.LuaValue.valueOf(data.protection);
            case "experience" -> data == null ? null
                    : org.luaj.vm2.LuaValue.valueOf((double) data.exp);
            case "isBoss" -> org.luaj.vm2.LuaValue.valueOf(data != null && data.isBoss ? 1 : 0);
            default -> null;
        };
    }

    /**
     * Setel atribut mob dari skrip ({@code mobl_setattr} di C).
     *
     * <p>Di sinilah pertarungan sebenarnya terjadi: skrip mengurangi
     * {@code health}, dan begitu mencapai nol mob harus dinyatakan mati.
     * Penanganan kematiannya <b>tidak</b> di sini — kelas ini tidak tahu
     * soal peta; {@link MobRegistry#applyDamage} yang mengurusnya.</p>
     */
    @Override
    public boolean scriptSetAttr(String attr, org.luaj.vm2.LuaValue v) {
        switch (attr) {
            case "health" -> currentVita = Math.max(0, (long) v.todouble());
            case "maxHealth" -> maxVita = (long) v.todouble();
            case "magic" -> currentMana = Math.max(0, (long) v.todouble());
            case "maxMagic" -> maxMana = (long) v.todouble();
            case "target" -> target = (long) v.todouble();
            case "attacker" -> attacker = (long) v.todouble();
            case "state" -> state = v.toint();
            case "charState" -> charState = v.toint();
            case "side" -> side = v.toint();
            case "look" -> look = v.toint();
            case "lookColor" -> lookColor = v.toint();
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * mobl_callbase(): panggil kait skrip milik mob ini.
     *
     * <p>Argumen keduanya penyerang mob; bila tidak ada, C mengirim
     * <b>mob itu sendiri</b> — bukan nil — jadi skrip tidak perlu
     * mengeceknya.</p>
     */
    @Override
    public boolean scriptCallBase(String method) {
        var engine = MapServer.scriptEngine;
        if (engine == null || data == null || method == null || method.isEmpty()) {
            return false;
        }
        var diriRef = engine.objectRef(this);
        org.luaj.vm2.LuaValue lawanRef = diriRef;

        if (attacker != 0) {
            for (User u : MapServer.onlineChars.values()) {
                if (u.id == attacker) {
                    lawanRef = engine.playerRef(u.scriptPlayer());
                    break;
                }
            }
        }
        try {
            return engine.doScript(data.yname, method, diriRef, lawanRef);
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "Mob{" + scriptName() + " id=" + id + " @" + m + ":" + x + "," + y
                + " vita=" + currentVita + "/" + maxVita
                + (state == MobData.MOB_DEAD ? " MATI" : "") + "}";
    }
}
