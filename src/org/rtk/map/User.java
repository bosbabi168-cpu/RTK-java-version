package org.rtk.map;

import org.rtk.common.mmo.CharStatus;
import org.rtk.map.data.BlockList;

/**
 * Port of USER / struct map_sessiondata (map/map.h) — pemain yang sedang
 * bermain di map server.
 *
 * Di C, struct ini menggabungkan tiga hal: {@code block_list bl} (posisi di
 * peta), {@code mmo_charstatus status} (data tersimpan), dan puluhan ladang
 * runtime yang tidak ikut disimpan ke database. Di sini pembagiannya dibuat
 * eksplisit: kelas ini <b>adalah</b> {@link BlockList} (mewarisi posisi),
 * menyimpan {@link CharStatus} sebagai data persisten, dan menaruh nilai
 * turunan di ladangnya sendiri.
 *
 * Hanya ladang runtime yang sudah benar-benar dipakai yang diport. Sisanya
 * ({@code npc_*}, exchange, group, dsb.) menyusul bersama subsistem yang
 * membutuhkannya — lihat roadmap di CLAUDE.md.
 */
public final class User extends BlockList
        implements org.rtk.map.script.ScriptPlayer.Owner {

    /**
     * Batas id: nilai di atasnya milik mob, bukan pemain.
     * Setara MOB_START_NUM di map.h.
     */
    public static final long MOB_START_NUM = 1073741823L;

    /** fd sesi klien pada {@link org.rtk.common.NetServer} milik map server. */
    public final int fd;

    /** Data yang tersimpan ke database (dikirim/diterima lewat 0x3003/0x3004). */
    public final CharStatus status;

    /**
     * Tabel kunci enkripsi sesi, diturunkan dari nama karakter.
     * Setara {@code sd->EncHash} yang diisi {@code populate_table()} di C;
     * dipakai {@link org.rtk.map.Clif#encrypt} untuk sebagian besar paket.
     */
    public final String encHash;

    // ---- nilai turunan (dihitung ulang saat masuk, tidak disimpan) ----
    public long maxHp;
    public long maxMp;
    public int might;
    public int will;
    public int grace;
    public int armor;

    // ---- keadaan sesaat ----
    public boolean canMove = true;
    public boolean isWalking;
    public boolean paralyzed;
    public boolean blind;
    public boolean drunk;

    /** Penanda kotak masuk: 1 = paket baru, 16 = pesan baru, 17 = keduanya. */
    public int flags;

    /** Jumlah anggota grup; >0 memicu pembaruan darah grup setelah status. */
    public int groupCount;
    public int speed;
    public int direction;

    /**
     * Posisi kamera klien dalam petak layar (0..16 / 0..14).
     * Setara {@code sd->viewx} / {@code sd->viewy} di C: server ikut
     * melacaknya karena nilai ini dikirim balik pada tiap langkah.
     */
    public int viewX = 8;
    public int viewY = 7;

    /**
     * Id blok benda yang terakhir diklik pemain ({@code sd->last_click}).
     * Dialog NPC memakainya sebagai "lawan bicara" — paket dialog dikirim
     * atas nama id ini, bukan atas nama NPC yang kebetulan terdekat.
     */
    public long lastClick;

    /** Grafik & warna yang dipakai kotak dialog ({@code npc_g}/{@code npc_gc}). */
    public int npcGraphic;
    public int npcGraphicColor;

    /** 0 = dialog bergrafik, 1 = dialog berwujud NPC ({@code sd->dialogtype}). */
    public int dialogType;

    /** true bila langkah terakhir tertahan (setara {@code sd->canmove} di C). */
    public boolean blocked;

    /**
     * Bendera pilihan sesaat ({@code sd->optFlags} di C, enum optFlag_*).
     * Baru dua yang dipakai; sisanya menyusul bersama subsistemnya.
     */
    public int optFlags;

    /** optFlag_stealth: GM tak terlihat, tidak menghalangi langkah. */
    public static final int OPT_STEALTH = 32;

    /** optFlag_ghosts: pemain ini bisa melihat (dan tertahan oleh) hantu. */
    public static final int OPT_GHOSTS = 256;

    /** optFlag_walkthrough: GM menembus penghalang. */
    public static final int OPT_WALKTHROUGH = 128;

    /** status.state == 1: pemain sedang jadi hantu (mati). */
    public static final int STATE_GHOST = 1;

    /** status.state == -1: pemain disembunyikan sepenuhnya. */
    public static final int STATE_HIDDEN = -1;

    /** Peta & posisi tujuan saat berpindah, sebelum benar-benar sampai. */
    public int destMap = -1;
    public int destX;
    public int destY;

    public User(int fd, CharStatus status) {
        this.fd = fd;
        this.status = status;
        this.id = status.id;
        this.type = Type.PC;
        this.graphicId = status.disguise;
        this.graphicColor = status.disguiseColor;
        this.encHash = org.rtk.common.Crypt.populateTable(status.name);
    }

    /**
     * Objek pemain milik mesin skrip — <b>satu per pemain, seumur sesi</b>.
     *
     * <p>Dibuat malas lewat {@link #scriptPlayer()}. Ini penting: dialog NPC
     * berjalan di atas coroutine yang keadaannya (coroutine + dialog yang
     * sedang menunggu) menempel pada objek ini. Kalau objeknya dibuat ulang
     * tiap panggilan — seperti sebelumnya — setiap dialog akan lupa dirinya
     * sendiri begitu pemain menjawab.</p>
     */
    private org.rtk.map.script.ScriptPlayer scriptPlayer;

    /** Objek skrip milik pemain ini; dibuat sekali lalu dipakai terus. */
    public org.rtk.map.script.ScriptPlayer scriptPlayer() {
        if (scriptPlayer == null) {
            scriptPlayer = new org.rtk.map.script.ScriptPlayer((int) id, status.name);
            scriptPlayer.owner = this;
            // C1: registry skrip DAN registry karakter adalah peta yang sama,
            // jadi apa pun yang ditulis skrip otomatis ikut tersimpan lewat
            // CharPersistence — tidak ada penyalinan yang bisa terlewat.
            scriptPlayer.bindRegistries(status.registry, status.registryString,
                    status.npcRegistry, status.questRegistry);
        }
        syncScriptPlayer();
        return scriptPlayer;
    }

    /** Skrip menulis {@code player.level} — teruskan ke data tersimpan. */
    @Override
    public void scriptSetLevel(int level) {
        status.level = level;
    }

    // ------------------------------------------------------------------
    // atribut karakter: jalur skrip -> CharStatus
    // ------------------------------------------------------------------

    /**
     * pcl_getattr(): baca atribut karakter dari data tersimpan.
     *
     * <p>Hanya atribut yang benar-benar sudah diport yang dijawab; sisanya
     * mengembalikan null supaya pemanggil tahu bedanya antara "nilainya 0"
     * dan "belum diport". Daftar penuh di C ada 164 atribut yang bisa
     * ditulis — yang di sini sengaja dibatasi pada yang sudah punya ladang
     * padanan di {@link org.rtk.common.mmo.CharStatus}.</p>
     */
    @Override
    public Long scriptGetAttr(String name) {
        return switch (name) {
            case "money" -> status.money;
            case "bankMoney" -> status.bankMoney;
            case "maxInv" -> (long) status.maxInv;
            case "health" -> status.hp;
            case "magic" -> status.mp;
            case "baseHealth" -> status.baseHp;
            case "baseMagic" -> status.baseMp;
            case "exp" -> status.exp;
            case "mark" -> (long) status.mark;
            case "totem" -> (long) status.totem;
            case "tier" -> (long) status.tier;
            case "country" -> (long) status.country;
            case "side" -> (long) status.side;
            case "state" -> (long) status.state;
            case "maxSlots" -> status.maxSlots;
            default -> null;
        };
    }

    /** pcl_setattr(): tulis atribut karakter ke data tersimpan. */
    @Override
    public boolean scriptSetAttr(String name, long v) {
        switch (name) {
            case "money" -> status.money = v;
            case "bankMoney" -> status.bankMoney = v;
            case "maxInv" -> status.maxInv = (int) v;
            case "health" -> status.hp = v;
            case "magic" -> status.mp = v;
            case "baseHealth" -> status.baseHp = v;
            case "baseMagic" -> status.baseMp = v;
            case "exp" -> status.exp = v;
            case "mark" -> status.mark = (int) v;
            case "totem" -> status.totem = (int) v;
            case "tier" -> status.tier = (int) v;
            case "country" -> status.country = (int) v;
            case "side" -> status.side = (int) v;
            case "state" -> status.state = (int) v;
            case "maxSlots" -> status.maxSlots = v;
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // barang: jalur skrip -> inventaris tersimpan
    // ------------------------------------------------------------------

    /**
     * pcl_additem(): tambahkan barang ke {@link org.rtk.common.mmo.CharStatus#inventory}.
     *
     * <p>Barang bertumpuk digabungkan ke slot yang sudah ada sampai batas
     * {@code ItmStackAmount}; sisanya membuka slot baru. Barang yang tidak
     * bertumpuk selalu satu slot per keping. Batas slot adalah
     * {@code ChaMaximumInventory}, jadi penambahan bisa <b>gagal sebagian</b>
     * — dan itu dilaporkan, bukan didiamkan.</p>
     */
    @Override
    public boolean scriptAddItem(String name, int amount) {
        var db = MapServer.itemDb;
        var info = db.infoByName(name);
        if (info == null || amount <= 0) {
            return false;
        }
        int stack = Math.max(1, info.stackAmount());
        int sisa = amount;

        if (stack > 1) {
            for (org.rtk.common.mmo.Item it : status.inventory) {
                if (sisa <= 0) {
                    break;
                }
                if (it.id == info.id() && it.amount < stack) {
                    int muat = Math.min(stack - it.amount, sisa);
                    it.amount += muat;
                    sisa -= muat;
                }
            }
        }

        while (sisa > 0) {
            if (status.inventory.size() >= status.maxInv) {
                return false;   // inventaris penuh; sebagian mungkin sudah masuk
            }
            org.rtk.common.mmo.Item baru = new org.rtk.common.mmo.Item();
            baru.id = info.id();
            baru.amount = Math.min(stack, sisa);
            baru.pos = status.inventory.size();
            status.inventory.add(baru);
            sisa -= baru.amount;
        }
        return true;
    }

    /** pcl_removeinventoryitem(): buang barang, boleh lintas beberapa slot. */
    @Override
    public int scriptRemoveItem(String name, int amount) {
        var info = MapServer.itemDb.infoByName(name);
        if (info == null || amount <= 0) {
            return 0;
        }
        int terbuang = 0;
        var it = status.inventory.iterator();
        while (it.hasNext() && terbuang < amount) {
            org.rtk.common.mmo.Item item = it.next();
            if (item.id != info.id()) {
                continue;
            }
            int ambil = Math.min(item.amount, amount - terbuang);
            item.amount -= ambil;
            terbuang += ambil;
            if (item.amount <= 0) {
                it.remove();
            }
        }
        return terbuang;
    }

    /**
     * pcl_sell(): slot inventaris yang berisi salah satu barang bernama itu.
     *
     * <p>Urutannya mengikuti urutan slot, bukan urutan nama yang diminta —
     * klien menampilkan barangnya dari isi slot, jadi yang penting nomornya
     * benar.</p>
     */
    @Override
    public java.util.List<Integer> scriptItemSlots(java.util.List<String> names) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (names == null || names.isEmpty()) {
            return out;
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (String n : names) {
            var info = MapServer.itemDb.infoByName(n);
            if (info != null) {
                ids.add(info.id());
            }
        }
        for (int i = 0; i < status.inventory.size(); i++) {
            if (ids.contains(status.inventory.get(i).id)) {
                out.add(i);
            }
        }
        return out;
    }

    /** pcl_hasitem(): total jumlah barang itu di inventaris. */
    @Override
    public int scriptCountItem(String name) {
        var info = MapServer.itemDb.infoByName(name);
        if (info == null) {
            return 0;
        }
        int n = 0;
        for (org.rtk.common.mmo.Item it : status.inventory) {
            if (it.id == info.id()) {
                n += it.amount;
            }
        }
        return n;
    }

    /** Salin nilai yang bisa berubah ke objek skrip sebelum dipakai. */
    public void syncScriptPlayer() {
        if (scriptPlayer == null) {
            return;
        }
        scriptPlayer.level = status.level;
        scriptPlayer.sex = status.sex;
        scriptPlayer.path = status.charClass;
        scriptPlayer.gmLevel = status.gmLevel;
        scriptPlayer.m = m;
        scriptPlayer.x = x;
        scriptPlayer.y = y;
    }

    /** Nama karakter — dipakai luas oleh log dan skrip. */
    public String name() {
        return status.name;
    }

    public boolean isGm() {
        return status.gmLevel > 0;
    }

    /**
     * Hitung ulang nilai turunan dari {@link CharStatus}.
     * Setara pc_calcstatus() di C, masih versi sederhana: rumus penuh
     * (bonus perlengkapan, buff, path) menyusul bersama port `pc.c`.
     */
    public void recalcStatus() {
        maxHp = status.baseHp;
        maxMp = status.baseMp;
        might = (int) status.baseMight;
        will = (int) status.baseWill;
        grace = (int) status.baseGrace;
        armor = status.baseArmor;

        if (status.hp > maxHp) {
            status.hp = maxHp;
        }
        if (status.mp > maxMp) {
            status.mp = maxMp;
        }
    }

    @Override
    public String toString() {
        return "User{" + status.name + " (id=" + id + ", lv " + status.level + ") @"
                + m + ":" + x + "," + y + "}";
    }
}
