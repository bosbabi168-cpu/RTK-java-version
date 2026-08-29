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

    /**
     * Barang yang diserahkan pemain kepada mob ini ({@code mob->inventory}).
     *
     * <p>⚠️ Ini <b>bukan</b> lubang tempat barang hilang, walau terlihat
     * begitu: isinya <b>dijatuhkan kembali saat mob mati</b>
     * ({@code mobdb_drops}, mob.c:730). Membuang bagian itu berarti setiap
     * barang yang diserahkan ke mob lenyap selamanya.</p>
     *
     * <p>Kosong untuk hampir semua mob, jadi daftarnya tumbuh sesuai
     * kebutuhan alih-alih memesan {@code MAX_INVENTORY} slot untuk tiap
     * satu dari 1.175 mob yang hidup.</p>
     */
    public final java.util.List<org.rtk.common.mmo.Item> inventory =
            new java.util.ArrayList<>();

    /**
     * Terima barang dari pemain. Digabung bila <b>empat</b> atribut sama —
     * id, ketahanan, pemilik, dan perlindungan; itu daftar yang dipakai C di
     * {@code clif_handitem}, lebih longgar daripada sepuluh atribut pada
     * penggabungan bank maupun jatuhan inventaris (Peringatan #31 dan #49).
     */
    public void receiveItem(org.rtk.common.mmo.Item src, int amount) {
        for (org.rtk.common.mmo.Item it : inventory) {
            if (it.id == src.id && it.dura == src.dura
                    && it.owner == src.owner && it.protectedFlag == src.protectedFlag) {
                it.amount += amount;
                return;
            }
        }
        org.rtk.common.mmo.Item baru = new org.rtk.common.mmo.Item();
        baru.id = src.id;
        baru.amount = amount;
        baru.dura = src.dura;
        baru.owner = src.owner;
        baru.protectedFlag = src.protectedFlag;
        inventory.add(baru);
    }


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

    /** Peluang meleset per sepuluh ribu ({@code mob->miss}, sl.c:5301). */
    public int miss;

    // ---- pengali pertarungan (map.h: float sleep, deduction, dmgshield) ----
    /** Pengali tidur; 1 = normal. */
    public double sleepMul = 1.0;
    /** Pengali potongan; 1 = normal. */
    public double deduction = 1.0;
    /** Perisai kerusakan yang menyerap dulu. */
    public double dmgShield;
    public boolean paralyzed;
    public boolean blind;
    public boolean confused;
    public long confuseTarget;

    /**
     * Mantra yang sedang menempel pada mob: nama -&gt; saat berakhir (ms).
     *
     * <p>C menyimpannya di {@code mob->dura[]}. Di sini cukup peta kecil:
     * yang dibutuhkan skrip hanyalah "masih menempel atau tidak" dan
     * "berapa sisanya" ({@code Mob.checkIfCast}, mob.lua:419).</p>
     */
    private final java.util.Map<String, Long> durasi = new java.util.HashMap<>();

    /** Sisa durasi mantra dalam milidetik; 0 bila tidak menempel. */
    public long durasiSisa(String nama) {
        Long akhir = durasi.get(nama);
        if (akhir == null) {
            return 0;
        }
        long sisa = akhir - System.currentTimeMillis();
        if (sisa <= 0) {
            durasi.remove(nama);
            return 0;
        }
        return sisa;
    }

    /** Tempelkan mantra selama sekian milidetik; 0 melepasnya. */
    public void setDurasi(String nama, long ms) {
        if (ms <= 0) {
            durasi.remove(nama);
        } else {
            durasi.put(nama, System.currentTimeMillis() + ms);
        }
    }

    /**
     * Tabel ancaman: id pemain &rarr; total kerusakan yang ia timbulkan.
     *
     * <p>Diisi lewat {@code player:addThreat(mobId, damage)} dari skrip.
     * AI memakainya untuk memilih sasaran — yang paling banyak melukai
     * yang dikejar, bukan yang terakhir memukul.</p>
     */
    public final java.util.Map<Long, Long> threat = new java.util.LinkedHashMap<>();

    /**
     * Kerusakan yang dicatat per <b>pemain</b> ({@code mob->dmgindtable}) dan
     * per <b>grup</b> ({@code mob->dmggrptable}).
     *
     * <p>⚠️ Ini <b>bukan</b> tabel ancaman. Ancaman menentukan siapa yang
     * dikejar mob; dua tabel ini hanya catatan siapa menyumbang berapa,
     * dipakai skrip untuk membagi jatuhan dan pengalaman. Angkanya
     * <b>ditambahkan</b>, bukan ditimpa — sama seperti ancaman.</p>
     */
    public final java.util.Map<Long, Double> indDamage = new java.util.LinkedHashMap<>();
    public final java.util.Map<Integer, Double> groupDamage = new java.util.LinkedHashMap<>();

    /** MAX_THREATCOUNT: batas kedua tabel kerusakan di C. */
    public static final int MAX_DAMAGE_ENTRIES = 50;

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
            // ⚠️ `miss` dipakai `hitCritChance.lua` untuk KEDUA belah pihak:
            // pemain memukul mob DAN mob memukul pemain. Tanpa ladang ini
            // skripnya gagal sebelum sempat menghitung apa pun (#157).
            case "miss" -> org.luaj.vm2.LuaValue.valueOf(miss);
            case "blType" -> org.luaj.vm2.LuaValue.valueOf(
                    org.rtk.map.data.BlockList.BL_MOB);
            // ⚠️ Skrip memakai huruf besar: `target.IsBoss` di swingDamage.
            // Nama atribut peka huruf, jadi "isBoss" saja tidak cukup.
            case "IsBoss" -> org.luaj.vm2.LuaValue.valueOf(
                    data != null && data.isBoss ? 1 : 0);
            case "sleep" -> org.luaj.vm2.LuaValue.valueOf(sleepMul);
            case "deduction" -> org.luaj.vm2.LuaValue.valueOf(deduction);
            case "dmgShield" -> org.luaj.vm2.LuaValue.valueOf(dmgShield);
            case "paralyzed" -> org.luaj.vm2.LuaValue.valueOf(paralyzed);
            case "blind" -> org.luaj.vm2.LuaValue.valueOf(blind);
            case "confused" -> org.luaj.vm2.LuaValue.valueOf(confused);
            case "confuseTarget" -> org.luaj.vm2.LuaValue.valueOf((double) confuseTarget);
            case "mobID" -> org.luaj.vm2.LuaValue.valueOf(
                    data == null ? 0 : (double) data.id);
            case "damage" -> org.luaj.vm2.LuaValue.valueOf(damage);
            case "critChance" -> org.luaj.vm2.LuaValue.valueOf(critChance);
            case "armor" -> data == null ? null : org.luaj.vm2.LuaValue.valueOf(data.baseArmor);
            case "protection" -> data == null ? null
                    : org.luaj.vm2.LuaValue.valueOf(data.protection);
            case "experience" -> data == null ? null
                    : org.luaj.vm2.LuaValue.valueOf((double) data.exp);
            case "isBoss" -> org.luaj.vm2.LuaValue.valueOf(data != null && data.isBoss ? 1 : 0);
            default -> {
                org.rtk.map.script.Bindings.lapor("mob", attr);
                yield null;
            }
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
            case "miss" -> miss = (int) v.todouble();
            case "damage" -> damage = v.todouble();
            case "critChance" -> critChance = (int) v.todouble();
            case "sleep" -> sleepMul = v.todouble();
            case "deduction" -> deduction = v.todouble();
            case "dmgShield" -> dmgShield = v.todouble();
            case "paralyzed" -> paralyzed = v.toboolean();
            case "blind" -> blind = v.toboolean();
            case "confused" -> confused = v.toboolean();
            case "confuseTarget" -> confuseTarget = (long) v.todouble();
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
