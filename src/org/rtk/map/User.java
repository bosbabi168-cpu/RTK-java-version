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

    private static final org.apache.logging.log4j.Logger log =
            org.apache.logging.log4j.LogManager.getLogger(User.class);

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
    /** Id benda yang terakhir melukai pemain ini ({@code sd->attacker}). */
    public long attacker;

    /**
     * Sasaran pemain saat ini ({@code sd->target}).
     *
     * <p>Disetel kembali ke dirinya sendiri saat perlengkapan berubah —
     * mengganti senjata membatalkan bidikan yang sedang berjalan.</p>
     */
    public long target;

    /** Nyawa sesaat SEBELUM pukulan terakhir ({@code sd->lastvita}). */
    public long lastVita;

    /**
     * Hasil kait {@code hitCritChance}: 0 = meleset, 1 = kena biasa,
     * 2 = kritis. Diisi <b>oleh skrip Lua</b>, dibaca {@link Combat}.
     */
    public int critChance;

    /**
     * Hasil kait {@code swingDamage}: kerusakan pukulan berikutnya. Diisi
     * skrip, dan sengaja bertipe pecahan karena C memakai {@code float} lalu
     * membulatkannya dengan {@code (int)(damage += 0.5f)}.
     */
    public double damage;

    public int armor;

    // ---- nilai turunan dari perlengkapan (pc_calcstat) ----
    public int hit;
    public int dam;
    public int protection;
    public int healing;
    public int minSdam;
    public int maxSdam;
    public int minLdam;
    public int maxLdam;
    public int attackSpeed = 20;

    // ---- keadaan sesaat ----
    public boolean canMove = true;
    public boolean isWalking;
    public boolean paralyzed;
    public boolean blind;
    public boolean drunk;

    /**
     * Tingkat bisu ({@code sd->silence}).
     *
     * <p>⚠️ Bukan boolean, dan itu penting: mantra dibungkam hanya bila
     * {@code SplMute <= silence} ({@code clif_parsemagic}). Jadi bisu tingkat
     * rendah masih menyisakan mantra bernilai mute tinggi — menyederhanakannya
     * jadi ya/tidak membungkam semuanya sekaligus.</p>
     */
    public int silence;

    /**
     * Jawaban yang dibawa mantra bertanya ({@code sd->question}).
     *
     * <p>Diisi saat merapal mantra ragam 1, lalu dibaca skripnya lewat
     * {@code player.question} — dipakai 41× oleh konten.</p>
     */
    public String question = "";

    /**
     * Daftar abaikan ({@code sd->IgnoreList}).
     *
     * <p>⚠️ <b>Tidak pernah disimpan ke database</b>, dan itu bukan
     * kelalaian port: di C ia rantai di memori yang hanya diisi
     * {@code clif_parseignore} dan lenyap saat pemain keluar. Tabel
     * {@code Friends} <b>bukan</b> penyimpanannya — itu tabel berbeda yang
     * dipakai binding {@code getFriends}, meski namanya mirip.</p>
     *
     * <p>Disimpan huruf kecil karena C membandingkan dengan
     * {@code strcmpi}.</p>
     */
    public final java.util.Set<String> daftarAbaikan = new java.util.LinkedHashSet<>();

    /** {@code ignorelist_add()}; false bila namanya sudah ada. */
    public boolean abaikan(String nama) {
        return nama != null && !nama.isEmpty()
                && daftarAbaikan.add(nama.toLowerCase(java.util.Locale.ROOT));
    }

    /** {@code ignorelist_remove()}. */
    public boolean berhentiAbaikan(String nama) {
        return nama != null
                && daftarAbaikan.remove(nama.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean mengabaikan(String nama) {
        return nama != null
                && daftarAbaikan.contains(nama.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * {@code clif_isignore(a, b)}: bolehkah pesan {@code a} sampai ke
     * {@code b}?
     *
     * <p>⚠️ <b>Namanya di C menyesatkan.</b> {@code clif_isignore}
     * mengembalikan <b>0 bila salah satu mengabaikan yang lain</b> dan 1 bila
     * boleh lewat — kebalikan dari yang dikira namanya. Di sini namanya
     * dibuat menyebutkan apa yang dijawabnya, supaya pemakainya tidak perlu
     * mengingat pembalikan itu.</p>
     *
     * <p>Penyaringnya <b>dua arah</b>: yang mengabaikan tidak mendengar, dan
     * yang diabaikan juga tidak. Itu memang perilaku C.</p>
     */
    public static boolean bolehSalingDengar(User a, User b) {
        if (a == null || b == null || a == b) {
            return true;
        }
        return !b.mengabaikan(a.name()) && !a.mengabaikan(b.name());
    }

    /** Penanda kotak masuk: 1 = paket baru, 16 = pesan baru, 17 = keduanya. */
    public int flags;

    /** Jumlah anggota grup; >0 memicu pembaruan darah grup setelah status. */
    public int groupCount;

    /** Id grup pemain ({@code sd->groupid}); 0 berarti tidak bergrup. */
    public int groupId;

    /**
     * Id karakter pemimpin grup ({@code sd->group_leader}); 0 bila sendirian.
     *
     * <p>⚠️ Di C ladang ini disalin ke setiap anggota oleh
     * {@code clif_updategroup} dan karenanya bisa melenceng. Di sini ia
     * cuma <b>cermin</b> dari {@code Groups.pemimpin(groupId)}, ditulis
     * lewat {@link Groups} saja supaya tidak ada dua sumber kebenaran.</p>
     */
    public long groupLeader;

    /**
     * Daftar lawan PK: id pemain -&gt; detik epoch saat ditandai
     * ({@code sd->pvp[20][2]} di C).
     *
     * <p>Batasnya <b>20</b> dan tidak pernah kedaluwarsa sendiri — waktunya
     * dicatat tapi tidak pernah dibaca server. Penuh berarti penandaan baru
     * diabaikan diam-diam.</p>
     */
    public final java.util.LinkedHashMap<Long, Long> pvp = new java.util.LinkedHashMap<>();

    /** MAX PK: {@code for (x = 0; x < 20; x++)} di C. */
    public static final int MAX_PVP = 20;

    /**
     * Pertukaran barang yang sedang berjalan ({@code sd->exchange}).
     * Barang di dalamnya <b>sudah keluar dari inventaris</b> — lihat
     * {@link Exchange}.
     */
    public final Exchange.State exchange = new Exchange.State();
    public int speed;
    public int direction;

    /**
     * Gerakan terakhir yang dimainkan pemain ({@code sd->action} di C):
     * 0 diam, 1 serang, 2 lempar, 3 tembak, 4/5 duduk, 6 sihir, 7/8 makan.
     * Disetel {@code clif_sendaction()} dan dibaca skrip.
     */
    public int action;

    /**
     * Penghitung waktu yang tampil di layar pemain
     * ({@code sd->disptimer*} di C).
     *
     * <p>{@code disptimerType} 1 dan 2 menghitung mundur; nilai lain hanya
     * menampilkan angkanya. {@code disptimerTick} sisa detiknya. Berkurang
     * satu tiap detik lewat {@link MapServer#duraTick}, dan saat habis kait
     * {@code pc_timer.display_timer} dipanggil sekali lalu timernya mati.</p>
     */
    public int disptimerType;
    public long disptimerTick;

    /**
     * Kalimat terakhir yang diucapkan pemain ({@code sd->speech} di C).
     * Disetel {@code clif_sendscriptsay} dan dibaca skrip yang menanggapi
     * kata kunci.
     */
    public String speech = "";

    /**
     * Saluran kalimat terakhir ({@code sd->talktype}): 0 sekitar, 1 berteriak.
     * Dibaca {@code speech.lua}, yang juga <b>menulisnya</b> saat pemain
     * memakai pintasan {@code /s}.
     */
    public int talkType;

    /**
     * Mode pungut yang dipilih pemain ({@code sd->pickuptype}): 0 sekeping,
     * bukan-nol seluruh tumpukan. Dibaca {@code onPickup.lua} — dan skrip
     * itulah yang benar-benar memungut, bukan server.
     */
    public int pickUpType;

    /**
     * Skrip membatalkan jatuhnya barang ({@code sd->fakeDrop}).
     *
     * <p>⚠️ Dipakai 15x di konten untuk <b>mensimulasikan</b> jatuh: kait
     * {@code on_drop} berjalan lebih dulu, dan bila ia menyetel ini,
     * barangnya tidak pernah benar-benar mendarat. Selalu dikembalikan ke 0
     * sebelum kaitnya dipanggil — kalau tidak, satu pembatalan akan
     * menempel pada semua jatuhan berikutnya.</p>
     */
    public int fakeDrop;

    /**
     * Pemain sedang dalam jeda antar-ayunan ({@code sd->attacked}).
     *
     * <p>Inilah pembatas kecepatan serang yang sebenarnya: tanpa ia, klien
     * bisa menyerang secepat ia mampu mengirim paket. Dibersihkan timer
     * satu kali sepanjang {@code attackSpeed} — lihat
     * {@code MapCommands.playerAttacks}.</p>
     */
    /**
     * Sesi ini memakai protokol <b>RTK2</b>, bukan RetroTK.
     *
     * <p>Disetel saat perkenalan dan tidak pernah berubah sesudahnya: yang
     * memilihnya bingkai pertama yang dikirim klien
     * ({@code Wire.isRetroTk}).</p>
     *
     * <p>⚠️ Inilah satu-satunya penentu ke mana byte keluar dikirim. Kedua
     * implementasi {@code ClientView} dipanggil untuk <b>setiap</b>
     * peristiwa; masing-masing yang menyaring penerimanya sendiri lewat
     * bendera ini. Menyaringnya di pemanggil akan berarti setiap satu dari
     * 51 peristiwa perlu tahu soal protokol — persis yang dihindari lapisan
     * ini.</p>
     */
    public boolean rtk2;

    public boolean attacked;

    /**
     * Barang yang <b>sedang dipasang</b> ({@code sd->equipid}) — id jenisnya,
     * bukan slot. Diisi sebelum kait {@code onEquip} dan dibaca
     * {@code equip()} untuk tahu apa yang harus dipasang.
     */
    public long equipId;

    /**
     * Slot perlengkapan yang <b>sedang dilepas</b> ({@code sd->takeoffid}).
     * ⚠️ Bernilai <b>-1</b> saat tidak ada yang sedang dilepas, bukan 0 —
     * 0 adalah slot senjata yang sah.
     */
    public int takeOffId = -1;

    /** Petak tujuan lemparan ({@code sd->throwx}/{@code sd->throwy}). */
    public int throwX;
    public int throwY;

    /**
     * Pengganda senjata terpesona ({@code sd->enchanted}); 1.0 = biasa.
     * Dibaca skrip lewat atribut {@code enchant}.
     */
    public float enchanted = 1.0f;

    /**
     * Bendera serangan sisi/belakang ({@code sd->flank}, {@code sd->backstab}).
     *
     * <p>⚠️ Keduanya <b>boolean</b> di sisi skrip
     * ({@code lua_pushboolean}), dipakai 134x oleh skrip pertarungan.
     * Mengirimnya sebagai angka membuat {@code if player.flank then} selalu
     * benar — lihat {@code ScriptPlayer.Owner.scriptGetSpecial}.</p>
     */
    public boolean flank;
    public boolean backstab;

    /**
     * Barang yang <b>hancur pada sapuan yang sedang berjalan</b>
     * ({@code sd->boditems} di C — BoD = <i>Break on Death</i>).
     *
     * <p>⚠️ Ini <b>bukan</b> penyimpanan permanen, melainkan daftar
     * sementara: {@code deductDuraEquip} dan {@code checkInvBod} mengisinya
     * sambil menghancurkan barang, memanggil kait
     * {@code characterLog.bodLog} sekali di akhir, lalu
     * <b>mengosongkannya</b>. Skrip membacanya lewat {@code getBODItem(n)}
     * dan atribut {@code BODItemCount} — hanya di dalam kait itu.</p>
     */
    public final java.util.List<org.rtk.common.mmo.Item> bodItems =
            new java.util.ArrayList<>();

    /** Id jenis barang yang baru saja hancur ({@code sd->breakid}). */
    public long breakId;

    /** Slot yang sedang disapu ({@code sd->equipslot} / {@code sd->invslot}). */
    public int equipSlot;
    public int invSlot;

    // ---- papan pesan (sd->board*) ----
    /** Papan yang sedang dibuka; 0 berarti kotak surat, bukan papan. */
    public int board;
    /** Halaman daftar kiriman; tiap halaman 20 baris ({@code sd->bcount}). */
    public int boardPage;
    /** true bila daftarnya dibuka sebagai jendela sembul. */
    public boolean boardPopup;
    /**
     * Hak menulis di papan yang sedang dibuka. Nilai <b>6</b> punya arti
     * khusus — lihat {@link Boards#WRITE_ASK_SCRIPT}. Diisi skrip lewat
     * atribut {@code boardCanWrite} pada papan ber-{@code BnmScripted}.
     */
    public int boardCanWrite;
    /** Hak menghapus kiriman di papan yang sedang dibuka. */
    public int boardCanDel;

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
    // mantra & bank
    // ------------------------------------------------------------------

    /** Ubah argumen skrip (nama atau nomor) jadi id mantra; 0 = tak dikenal. */
    private int spellId(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return 0;
        }
        // C menerima keduanya: angka dipakai apa adanya, teks dicari namanya
        try {
            return Integer.parseInt(nameOrId.trim());
        } catch (NumberFormatException e) {
            return MapServer.spellDb.idOf(nameOrId);
        }
    }

    /**
     * pcl_addspell(): ajarkan mantra.
     *
     * <p>Mantra disimpan di {@code CharStatus.spells}, sebuah array
     * berindeks slot. Slot pertama yang kosong dipakai — sama seperti C
     * yang menyapu 52 slot mencari yang kosong.</p>
     */
    @Override
    public boolean scriptAddSpell(String nameOrId) {
        int id = spellId(nameOrId);
        if (id <= 0 || scriptHasSpell(nameOrId)) {
            return false;
        }
        for (int i = 0; i < status.spells.length; i++) {
            if (status.spells[i] == 0) {
                status.spells[i] = id;
                return true;
            }
        }
        // buku mantra penuh: perbesar, karena panjangnya di sini tidak tetap
        int[] baru = java.util.Arrays.copyOf(status.spells, status.spells.length + 1);
        baru[baru.length - 1] = id;
        status.spells = baru;
        return true;
    }

    /** pcl_hasspell(). */
    @Override
    public boolean scriptHasSpell(String nameOrId) {
        int id = spellId(nameOrId);
        if (id <= 0) {
            return false;
        }
        for (int s : status.spells) {
            if (s == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * mobdb_id(): id jenis mob dari {@code MobIdentifier}, atau angka apa
     * adanya bila yang diberikan memang angka — C memeriksa
     * {@code lua_isnumber} lebih dulu, jadi keduanya sah.
     */
    private long mobTypeId(String nameOrId) {
        if (nameOrId == null || nameOrId.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(nameOrId.trim());
        } catch (NumberFormatException e) {
            MobData d = MapServer.mobs.typeByName(nameOrId);
            return d == null ? 0 : d.id;
        }
    }

    /**
     * pcl_hasspace(): apakah barang ini masih muat.
     *
     * <p>Dua kuirk C yang sengaja ditiru:</p>
     * <ol>
     *   <li>Slot bertumpuk <b>pertama</b> yang id+pemiliknya cocok langsung
     *       menentukan jawaban — meski slot berikutnya sebenarnya masih muat.
     *       Menjadikannya "cari yang muat" akan mengubah perilaku toko.</li>
     *   <li>Barang tak bertumpuk ({@code stackAmount <= 1}) tidak pernah
     *       memicu cabang itu; ia hanya butuh satu slot kosong.</li>
     * </ol>
     */
    @Override
    public boolean scriptHasSpace(String nameOrId, int amount, long owner) {
        long itemId;
        try {
            itemId = Long.parseLong(nameOrId.trim());
        } catch (NumberFormatException | NullPointerException e) {
            itemId = MapServer.itemDb.idOf(nameOrId);
        }
        int stackAmount = MapServer.itemDb.stackAmountOf(itemId);
        boolean adaSlotKosong = false;

        for (int slot = 0; slot < status.maxInv; slot++) {
            org.rtk.common.mmo.Item it = status.inventoryAt(slot);
            if (it == null || it.id <= 0) {
                adaSlotKosong = true;
                continue;
            }
            if (it.id == itemId && it.owner == owner && stackAmount > 1) {
                return it.amount + amount <= stackAmount;
            }
        }
        return adaSlotKosong;
    }

    /** pcl_setthreat(): setel (bukan tambah) ancaman pemain ini pada mob. */
    @Override
    public void scriptSetThreat(long mobId, long amount) {
        if (MapServer.npcs.byId(mobId) instanceof Mob mb) {
            mb.setThreat(id, amount);
        }
    }

    /**
     * pcl_removespell(): lupakan mantra, panggil kait {@code on_forget}
     * milik mantra itu, lalu beri tahu klien.
     *
     * <p>Urutannya mengikuti C: kait skrip dipanggil <b>sebelum</b> slotnya
     * dikosongkan, sehingga skrip masih bisa membaca keadaan pemain saat
     * masih memiliki mantra tersebut.</p>
     */
    @Override
    public void scriptRemoveSpell(String nameOrId) {
        int spell = spellId(nameOrId);
        if (spell <= 0) {
            return;
        }
        for (int i = 0; i < status.spells.length; i++) {
            if (status.spells[i] == spell) {
                String yname = MapServer.spellDb.nameOf(spell);
                if (MapServer.scriptEngine != null && yname != null && !yname.isEmpty()) {
                    try {
                        MapServer.scriptEngine.doScript(yname, "on_forget",
                                MapServer.scriptEngine.playerRef(Pc.scriptPlayerOf(this)));
                    } catch (RuntimeException e) {
                        log.error("[PC] kait on_forget mantra {} gagal untuk {}", yname, name(), e);
                    }
                }
                MapServer.clientView.playerSpellRemoved(this, i);
                status.spells[i] = 0;
                return; // C berhenti di kecocokan pertama
            }
        }
    }

    /** pcl_getequippeditem(). */
    @Override
    public org.rtk.common.mmo.Item scriptEquippedItem(int slot) {
        return status.equipAt(slot);
    }

    /** pcl_getinventoryitem(). */
    @Override
    public org.rtk.common.mmo.Item scriptInventoryItem(int slot) {
        return status.inventoryAt(slot);
    }

    /** pcl_hasduration(). */
    @Override
    public boolean scriptHasDuration(String spellName) {
        int id = spellId(spellName);
        if (id <= 0) {
            return false;
        }
        for (org.rtk.common.mmo.SkillInfo s : status.duraAether) {
            if (s.id == id && s.duration > 0) {
                return true;
            }
        }
        return false;
    }

    /** pcl_killcount(). */
    @Override
    public long scriptKillCount(String mobNameOrId) {
        long id = mobTypeId(mobNameOrId);
        if (id == 0) {
            return 0;
        }
        return status.killReg.getOrDefault(id, 0L);
    }

    /** pcl_setkillcount(). */
    @Override
    public void scriptSetKillCount(String mobNameOrId, long amount) {
        long id = mobTypeId(mobNameOrId);
        if (id == 0) {
            return;
        }
        status.killReg.put(id, amount);
    }

    /**
     * pcl_flushkills(): tanpa argumen (atau id 0) menghapus SELURUH hitungan,
     * dengan argumen hanya mob itu. Perilaku "kosong = semua" ada di C.
     */
    @Override
    public void scriptFlushKills(String mobNameOrId) {
        long id = mobTypeId(mobNameOrId);
        if (id == 0) {
            status.killReg.clear();
        } else {
            status.killReg.remove(id);
        }
    }

    /**
     * MAX_LEGENDS di mmo.h = 1000, tapi {@code pcl_addlegend} mensyaratkan
     * slot x <b>dan</b> x+1 sama-sama kosong, sehingga slot terakhir tidak
     * pernah terpakai — kapasitas efektifnya 999. (Di C, pemeriksaan x+1 pada
     * x terakhir bahkan membaca satu slot di luar array; itu tidak ditiru.)
     */
    private static final int MAX_LEGENDS_EFEKTIF = 999;

    /** pcl_addlegend(). */
    @Override
    public void scriptAddLegend(String text, String name, int icon, int color, long tchaid) {
        if (status.legends.size() >= MAX_LEGENDS_EFEKTIF) {
            return; // C diam saja bila penuh
        }
        org.rtk.common.mmo.Legend l = new org.rtk.common.mmo.Legend();
        l.text = text == null ? "" : text;
        l.name = name == null ? "" : name;
        l.icon = icon;
        l.color = color;
        l.tchaid = tchaid;
        status.legends.add(l);
    }

    /** pcl_haslegend() — strcmp, PEKA besar-kecil (lihat catatan antarmuka). */
    @Override
    public boolean scriptHasLegend(String name) {
        if (name == null) {
            return false;
        }
        for (org.rtk.common.mmo.Legend l : status.legends) {
            if (name.equals(l.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * pcl_removelegendbyname() — strcmpi, TIDAK peka besar-kecil.
     *
     * <p>C menyimpan legenda di array 1000 slot lalu memadatkannya dengan
     * satu lintasan geser-satu, yang tidak benar-benar merapatkan bila ada
     * beberapa lubang sekaligus. Di sini koleksinya sudah padat (hanya entri
     * terisi), jadi penghapusan otomatis merapatkan. Setara secara perilaku:
     * lubang di C tidak pernah terlihat karena {@code clif_mystaytus}
     * melewati entri yang nama/teksnya kosong.
     */
    @Override
    public void scriptRemoveLegendByName(String name) {
        if (name == null) {
            return;
        }
        status.legends.removeIf(l -> name.equalsIgnoreCase(l.name));
    }

    /**
     * pcl_bankdeposit(): pindahkan barang dari inventaris ke bank.
     *
     * <p>Barang sejenis digabung ke slot bank yang sudah ada — di C
     * pencocokannya juga memeriksa ukiran, pemilik, dan tampilan khusus;
     * port ini baru mencocokkan id (lihat catatan batas di CLAUDE.md).</p>
     */
    @Override
    public boolean scriptBankDeposit(String itemName, int amount) {
        var info = MapServer.itemDb.infoByName(itemName);
        if (info == null || amount <= 0) {
            return false;
        }
        int diambil = scriptRemoveItem(itemName, amount);
        if (diambil <= 0) {
            return false;   // tidak punya barangnya
        }
        for (org.rtk.common.mmo.BankItem b : status.banks) {
            if (b.itemId == info.id()) {
                b.amount += diambil;
                return true;
            }
        }
        org.rtk.common.mmo.BankItem baru = new org.rtk.common.mmo.BankItem();
        baru.itemId = info.id();
        baru.amount = diambil;
        status.banks.add(baru);
        return true;
    }

    /**
     * pcl_bankwithdraw(): ambil barang dari bank ke inventaris.
     *
     * <p>Bila inventaris penuh, barangnya <b>dikembalikan ke bank</b> —
     * tidak boleh lenyap di tengah jalan.</p>
     *
     * @return jumlah yang benar-benar berpindah
     */
    @Override
    public int scriptBankWithdraw(String itemName, int amount) {
        var info = MapServer.itemDb.infoByName(itemName);
        if (info == null || amount <= 0) {
            return 0;
        }
        var it = status.banks.iterator();
        while (it.hasNext()) {
            org.rtk.common.mmo.BankItem b = it.next();
            if (b.itemId != info.id()) {
                continue;
            }
            int ambil = (int) Math.min(b.amount, amount);
            b.amount -= ambil;
            if (b.amount <= 0) {
                it.remove();
            }
            if (!scriptAddItem(itemName, ambil)) {
                // inventaris penuh: kembalikan supaya tidak hilang
                b.amount += ambil;
                if (!status.banks.contains(b)) {
                    status.banks.add(b);
                }
                return 0;
            }
            return ambil;
        }
        return 0;
    }

    /** bll_addnpc(): lahirkan NPC sementara dari skrip. */
    @Override
    public void scriptAddNpc(String name, int m, int x, int y, int subtype,
                             long actionTime, long duration, long ownerId,
                             long moveTime, String displayName) {
        MapServer.npcs.addTemp(MapServer.scriptEngine, MapServer.world,
                name, m, x, y, subtype, actionTime, duration, ownerId,
                moveTime, displayName);
    }

    /** pcl_calcstat(): hitung ulang nilai turunan dari perlengkapan. */
    @Override
    public void scriptCalcStat() {
        recalcStatus();
    }

    /** pcl_addthreat(): catat ancaman pemain ini pada sebuah mob. */
    @Override
    public void scriptAddThreat(long mobId, long damage) {
        // Lewat indeks id, bukan pemindaian daftar: ini dipanggil setiap
        // kali pukulan mendarat.
        if (MapServer.npcs.byId(mobId) instanceof Mob mb) {
            mb.addThreat(id, damage);
        }
    }

    /**
     * pcl_getobjectsincell(): benda di satu petak, disaring bendera BL_*.
     *
     * <p>Inilah cara skrip pertarungan menemukan lawan. Objek yang
     * dikembalikan adalah <b>objek aslinya</b>, bukan salinan — skrip
     * mengurangi nyawanya lewat situ.</p>
     */
    @Override
    public java.util.List<Object> scriptObjectsInCell(int m, int x, int y, int type) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        var map = MapServer.world.get(m);
        if (map == null) {
            return out;
        }
        int flags = type <= 0 ? org.rtk.map.data.BlockList.BL_ALL : type;
        for (var bl : map.objectsAt(x, y)) {
            if ((bl.blFlag() & flags) != 0) {
                out.add(bl);
            }
        }
        return out;
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
            // ⚠️ Baca biasa; TULISNYA yang XOR — lihat scriptSetAttr.
            case "settings" -> status.settingFlags;
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
            // Jumlah barang yang hancur pada sapuan BOD yang sedang berjalan.
            // Hanya bermakna di dalam kait `characterLog.bodLog`; di luar itu
            // selalu 0, karena daftarnya dikosongkan setiap sapuan selesai.
            case "BODItemCount" -> (long) bodItems.size();
            case "board" -> (long) board;
            case "boardCanWrite" -> (long) boardCanWrite;
            case "boardCanDel" -> (long) boardCanDel;
            /** Id jenis barang yang paling akhir hancur ({@code sd->breakid}). */
            case "breakId" -> breakId;
            // ⚠️ `attacker` dipakai skrip 289 kali — antara lain
            // `player.lua:9` (addHealthExtend) yang dipanggil setiap barang
            // penyembuh. Sebelumnya hanya Mob yang menyediakannya, jadi
            // `player.attacker` bernilai nil dan skripnya gagal dengan
            // "attempt to compare nil with number" — jauh dari sebabnya.
            case "attacker" -> attacker;
            // nilai turunan hasil calcStat — dibaca skrip pertarungan
            case "maxHealth" -> maxHp;
            case "maxMagic" -> maxMp;
            case "might" -> (long) might;
            case "will" -> (long) will;
            case "grace" -> (long) grace;
            case "armor" -> (long) armor;
            case "hit" -> (long) hit;
            case "dam" -> (long) dam;
            case "protection" -> (long) protection;
            case "healing" -> (long) healing;
            case "attackSpeed" -> (long) attackSpeed;
            // keadaan aksi yang dibaca kait skrip (speech.lua, onPickup.lua,
            // dan 15 skrip barang yang memakai fakeDrop)
            case "talkType" -> (long) talkType;
            case "pickUpType" -> (long) pickUpType;
            case "fakeDrop" -> (long) fakeDrop;
            case "invSlot" -> (long) invSlot;
            case "equipSlot" -> (long) equipSlot;
            default -> null;
        };
    }

    @Override
    public org.luaj.vm2.LuaValue scriptGetSpecial(String name) {
        return switch (name) {
            case "speech" -> org.luaj.vm2.LuaValue.valueOf(speech);
            case "flank" -> org.luaj.vm2.LuaValue.valueOf(flank);
            case "backstab" -> org.luaj.vm2.LuaValue.valueOf(backstab);
            case "enchant" -> org.luaj.vm2.LuaValue.valueOf(enchanted);
            // ⚠️ Lewat scriptGetSpecial, bukan scriptGetAttr: jembatan angka
            // di sana tidak bisa membawa String. Dipakai skrip 41×.
            case "question" -> org.luaj.vm2.LuaValue.valueOf(question == null ? "" : question);
            default -> null;
        };
    }

    @Override
    public boolean scriptSetSpecial(String name, org.luaj.vm2.LuaValue v) {
        switch (name) {
            case "speech" -> speech = v.isnil() ? "" : v.tojstring();
            case "flank" -> flank = v.toboolean();
            case "backstab" -> backstab = v.toboolean();
            case "enchant" -> enchanted = (float) v.todouble();
            case "question" -> question = v.isnil() ? "" : v.tojstring();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** pcl_setattr(): tulis atribut karakter ke data tersimpan. */
    @Override
    public boolean scriptSetAttr(String name, long v) {
        switch (name) {
            case "money" -> status.money = v;
            // ⚠️ **XOR, bukan penetapan** (sl.c:6881). `player.settings = 2`
            // MEMBALIK bit grup; ia tidak menyetel setelan menjadi 2.
            // Terbaca persis seperti penetapan biasa di skrip, dan itulah
            // yang membuatnya berbahaya kalau diport begitu saja.
            case "settings" -> status.settingFlags ^= v;
            // Papan ber-BnmScripted memakai ini dari kait `check`; nilai 6
            // bukan sekadar "boleh", lihat Boards.WRITE_ASK_SCRIPT.
            case "boardCanWrite" -> boardCanWrite = (int) v;
            case "boardCanDel" -> boardCanDel = (int) v;
            case "bankMoney" -> status.bankMoney = v;
            case "maxInv" -> status.maxInv = (int) v;
            // Pasangan tulis dari `attacker` di scriptGetAttr: skrip
            // pertarungan menyetelnya sendiri sebelum memanggil penyembuh.
            case "attacker" -> attacker = v;
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
            case "talkType" -> talkType = (int) v;
            case "pickUpType" -> pickUpType = (int) v;
            case "fakeDrop" -> fakeDrop = (int) v;
            case "invSlot" -> invSlot = (int) v;
            case "equipSlot" -> equipSlot = (int) v;
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
        var info = MapServer.itemDb.infoByName(name);
        return info != null && addItemById(info.id(), amount, -1);
    }

    /**
     * Jalur yang sama, tapi barangnya disebut dengan <b>id</b> — dipakai
     * memungut barang lantai, yang tidak pernah tahu namanya.
     *
     * @param dura ketahanan yang dibawa dari barang aslinya; negatif berarti
     *             pakai bawaan jenisnya
     */
    /**
     * Kosongkan satu slot inventaris.
     *
     * <p>⚠️ Ini <b>keadaan permainan</b>, dan tempatnya di sisi logika —
     * bukan di dalam fungsi paket seperti dulu. Sampai 27 Agustus 2026
     * {@code Clif.sendDelItem} yang mencabutnya, sehingga begitu protokol
     * kedua berdiri slotnya tercabut <b>dua kali</b> dan yang kedua membuang
     * slot pemain berikutnya. Itu Peringatan #61 yang muncul lagi di tempat
     * baru; sekarang pemberitahuan dan keadaannya dipisah.</p>
     */
    public void clearInventorySlot(int slot) {
        // ⚠️ removeInventoryAt, BUKAN inventory.remove(slot): yang kedua
        // membuang menurut indeks daftar, yang pada kantong berlubang adalah
        // barang milik slot lain. Lihat Peringatan #85.
        if (slot >= 0) {
            status.removeInventoryAt(slot);
        }
    }

    public boolean addItemById(long itemId, int amount, int dura) {
        var info = MapServer.itemDb.info(itemId);
        if (info.id() == 0 || amount <= 0) {
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
            // ⚠️ Slot diambil dari slot KOSONG pertama, bukan dari
            // inventory.size(). Kantong berlubang membuat keduanya berbeda,
            // dan ukuran daftar bisa menunjuk slot yang SUDAH terisi —
            // dua barang di satu slot, tanpa error. Lihat Peringatan #85.
            int slot = status.firstFreeInventorySlot(status.maxInv);
            if (slot < 0) {
                return false;   // inventaris penuh; sebagian mungkin sudah masuk
            }
            org.rtk.common.mmo.Item baru = new org.rtk.common.mmo.Item();
            baru.id = info.id();
            baru.amount = Math.min(stack, sisa);
            baru.dura = dura >= 0 ? dura : info.durability();
            baru.pos = slot;
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
     * pcl_removeitemslot(): buang barang dari <b>satu slot tertentu</b>,
     * bukan dari mana pun yang namanya cocok.
     *
     * <p>Dipakai skrip tas dan kerajinan yang sudah tahu slot mana yang
     * dimaksud (27x) — mereka menyimpan nomor slot dari daftar yang
     * ditampilkan ke pemain.</p>
     *
     * <p>⚠️ <b>Cabang "isi slot kurang dari yang diminta" selalu berakhir
     * gagal, dan itu memang begitu di C.</b> Isi slotnya tetap dibuang,
     * tetapi karena {@code amount} masih bersisa nilainya tidak pernah nol,
     * sehingga fungsinya jatuh ke {@code return false} di bawah. Jadi
     * pemanggil bisa menerima false <b>setelah</b> barangnya berkurang.
     * Jangan "diperbaiki" — skrip yang ada mengandalkan perilaku ini.</p>
     *
     * @param type ragam pencatatan {@code pc_delitem} (10/9/11 di C); di sini
     *             hanya ikut dicatat, tidak mengubah apa yang dibuang
     */
    public boolean scriptRemoveItemSlot(int slot, int amount, int type) {
        // ⚠️ Dibatasi maxInv dan dicari lewat inventoryAt — bukan indeks
        // daftar. Dengan pembacaan lama, slot di atas jumlah barang ditolak
        // mentah-mentah walau benar-benar terisi. Lihat Peringatan #85.
        if (slot < 0 || slot >= status.maxInv || amount <= 0) {
            return false;
        }
        org.rtk.common.mmo.Item it = status.inventoryAt(slot);
        if (it == null || it.id <= 0) {
            return false;
        }
        if (it.amount < amount && it.amount > 0) {
            status.removeInventoryAt(slot);
            return false;   // lihat catatan di atas
        }
        if (it.amount >= amount) {
            it.amount -= amount;
            if (it.amount <= 0) {
                status.removeInventoryAt(slot);
            }
            return true;
        }
        return false;
    }

    /**
     * pcl_updatePath(): pemain berpindah jalur (path) dan tanda (mark).
     *
     * <p>Perubahannya <b>langsung ditulis ke database</b>, tidak menunggu
     * simpan berkala — di C pun begitu, karena naik jalur adalah peristiwa
     * yang tidak boleh hilang kalau server mati sebelum simpan berikutnya.</p>
     */
    public void scriptUpdatePath(int path, int mark) {
        int p = Math.max(path, 0);
        int mk = Math.max(mark, 0);
        status.charClass = p;
        status.mark = mk;
        try {
            MapServer.sql.update(
                    "UPDATE `Character` SET `ChaPthId` = ?, `ChaMark` = ? WHERE `ChaId` = ?",
                    p, mk, status.id);
        } catch (RuntimeException e) {
            log.error("[LUA] updatePath gagal menulis ke database", e);
            return;
        }
        MapServer.clientView.playerIdentityChanged(this);
    }

    /** pcl_updateCountry(): pemain berpindah negara/kubu. */
    public void scriptUpdateCountry(int country) {
        status.country = country;
        try {
            MapServer.sql.update(
                    "UPDATE `Character` SET `ChaNation` = ? WHERE `ChaId` = ?",
                    country, status.id);
        } catch (RuntimeException e) {
            log.error("[LUA] updateCountry gagal menulis ke database", e);
            return;
        }
        MapServer.clientView.playerStatusChanged(this, Clif.SFLAG_ALL);
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
        // pc_calcstat(): nilai dasar dijaga minimal 5 seperti di C —
        // karakter bernyawa 0 karena data rusak tetap bisa hidup
        if (status.baseHp <= 0) {
            status.baseHp = 5;
        }
        if (status.baseMp <= 0) {
            status.baseMp = 5;
        }

        maxHp = status.baseHp;
        maxMp = status.baseMp;
        might = (int) status.baseMight;
        will = (int) status.baseWill;
        grace = (int) status.baseGrace;
        armor = status.baseArmor;
        hit = 0;
        dam = 0;
        protection = 0;
        healing = 0;
        minSdam = 0;
        maxSdam = 0;
        minLdam = 0;
        maxLdam = 0;
        attackSpeed = 20;

        // bonus dari perlengkapan yang dikenakan
        for (org.rtk.common.mmo.Item it : status.equip) {
            if (it == null || it.id <= 0) {
                continue;
            }
            var st = MapServer.itemDb.info(it.id).stats();
            maxHp += st.vita();
            maxMp += st.mana();
            might += st.might();
            grace += st.grace();
            will += st.will();
            armor += st.armor();
            healing += st.healing();
            dam += st.dam();
            hit += st.hit();
            protection += st.protection();
            minSdam += st.minSdam();
            maxSdam += st.maxSdam();
            minLdam += st.minLdam();
            maxLdam += st.maxLdam();
        }

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
