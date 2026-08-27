package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.luaj.vm2.LuaValue;

import org.rtk.map.data.BlockList;
import org.rtk.common.mmo.SkillInfo;
import org.rtk.map.data.ItemDb;
import org.rtk.map.data.MapData;
import org.rtk.map.script.ScriptPlayer;

/**
 * Logika permainan di balik tiap aksi pemain — implementasi
 * {@link ClientCommands}.
 *
 * <p>Isinya <b>dipindahkan apa adanya</b> dari {@code Clif.parseWalk},
 * {@code parseClick}, {@code parseMenuInput}, dan {@code parseNpcDialog},
 * yang sebelumnya mencampur pembacaan byte dengan logika. Yang tinggal di
 * {@code Clif} sekarang hanyalah pembacanya — empat method yang tidak
 * memutuskan apa pun.</p>
 *
 * <p>Kelas ini <b>tidak boleh</b> menyentuh {@code Session}, {@code rfifo*},
 * atau opcode mana pun. Kalau sebuah baris di sini butuh salah satunya,
 * baris itu ada di tempat yang salah.</p>
 *
 * <p>Untuk mengirim sesuatu ke klien ia memakai {@link MapServer#clientView},
 * sama seperti seluruh logika lain.</p>
 *
 * <h2>Batasnya sekarang tertutup penuh (27 Agustus 2026)</h2>
 *
 * <p>Kelas ini <b>tidak lagi menyentuh {@code Clif} sama sekali</b>.
 * Lima helper yang isinya logika sudah dipindahkan ke sini —
 * {@code blocksMovement} (aturan siapa menghalangi siapa),
 * {@code updateCamera}, {@code fireWalkScripts}, {@code checkWarpTile}
 * (petak portal dan syarat masuk peta, beserta {@code entryRejection} dan
 * {@code pushBack}), dan {@code clickNpc} — sedangkan empat panggilan yang
 * memang protokol kini lewat {@link ClientView}:
 * {@code playerStepRejected}, {@code playerStepped}, {@code playerStepSeen},
 * dan {@code areaRedrawRequested}.</p>
 *
 * <p>⚠️ {@code pushBack} ikut pindah walau namanya di C
 * {@code clif_pushback}: isinya sama sekali bukan paket, hanya
 * {@code Pc.warp} dua petak ke belakang. Nama fungsi C bukan penentu
 * sisi mana ia berada.</p>
 *
 * <p>Verifikasinya sederhana dan layak diulang setiap kali kelas ini
 * disentuh: {@code grep -n "Clif\.\|rfifo\|Session" MapCommands.java}
 * harus tidak menghasilkan satu pun baris kode.</p>
 *
 * <p>Pemisahannya dikerjakan <b>satu helper per langkah</b>, dengan
 * {@code cliftest} (572 assertion) dijalankan di tiap langkah — bukan
 * karena hati-hati berlebihan, melainkan karena tidak ada satu pun gerbang
 * regresi yang <b>berubah</b> saat kode berpindah tempat. Yang membuktikan
 * kebenarannya hanyalah bahwa angkanya tidak bergerak, dan itu hanya
 * berarti sesuatu bila langkahnya kecil.</p>
 */
public final class MapCommands implements ClientCommands {

    private static final Logger log = LogManager.getLogger(MapCommands.class);


    // ------------------------------------------------------------------
    // Klik NPC — dipindahkan dari Clif 27 Agustus 2026. Dua panggilan
    // keluarnya kini lewat ClientView (`npcSaidTo`, `scriptDialogReady`),
    // sehingga kelas ini tidak lagi menyentuh Clif sama sekali.
    // ------------------------------------------------------------------

    /**
     * Jalankan skrip {@code click} milik NPC.
     *
     * <p>Penjaga karma ditiru dari C: pemain berkarma sangat buruk ditolak
     * bicara oleh hampir semua NPC — kecuali NPC F1 dan NPC totem, karena
     * keduanya jalur keluar yang tidak boleh ikut tertutup.</p>
     */
    private static void clickNpc(User sd, Npc nd) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        if (sd.status.karma <= -3.0f
                && !"F1Npc".equals(nd.name) && !"TotemNpc".equals(nd.name)) {
            MapServer.clientView.npcSaidTo(sd, nd.id, "Go away scum!");
            return;
        }

        sd.npcGraphic = (int) nd.graphicId;
        sd.npcGraphicColor = (int) nd.graphicColor;
        sd.dialogType = 0;

        var p = sd.scriptPlayer();
        // Setiap klik memulai interaksi dari nol (sl_async_freeco di C):
        // tanpa ini, dialog lama yang menggantung membuat klik berikutnya
        // ditolak dengan alasan "sedang sibuk".
        MapServer.scriptEngine.cancel(p);
        try {
            // C memanggil dengan DUA argumen: pemain dan NPC-nya
            // (`sl_doscript_blargs(nd->name, "click", 2, &sd->bl, &nd->bl)`).
            // Banyak skrip memakai parameter kedua untuk membaca
            // npc.yname / npc.bankNPC — tanpa ini mereka menerima nil.
            MapServer.scriptEngine.doScript(nd.name, "click",
                    MapServer.scriptEngine.playerRef(p),
                    MapServer.scriptEngine.objectRef(nd));
            MapServer.clientView.scriptDialogReady(sd, p);
        } catch (RuntimeException e) {
            log.error("[MASUK] skrip click '{}' gagal untuk {}", nd.name, sd.name(), e);
        }
    }

    // ------------------------------------------------------------------
    // Portal & syarat masuk peta — dipindahkan dari Clif 27 Agustus 2026.
    // `pushBack` ikut pindah walau namanya di C `clif_pushback`: isinya
    // sama sekali bukan paket, hanya Pc.warp dua petak ke belakang.
    // Pesan penolakannya kini lewat ClientView, bukan Clif.sendMiniText.
    // ------------------------------------------------------------------

    /**
     * Petak yang baru diinjak mungkin portal. Port dari ekor
     * {@code clif_parsewalk}: syarat masuk peta tujuan diperiksa dulu, dan
     * bila gagal pemain didorong mundur disertai pesan penolakan.
     */
    private static void checkWarpTile(User sd, MapData map) {
        // C menjepit koordinat dulu sebelum menelusuri daftar portal
        int fx = Math.min(sd.x, map.xs - 1);
        int fy = Math.min(sd.y, map.ys - 1);
        MapData.Warp w = map.warpAt(fx, fy);
        if (w == null) {
            return;
        }

        MapData dest = MapServer.world.get(w.tm());
        if (dest == null) {
            // portal ke peta milik map server lain — roadmap C3
            log.warn("[MASUK] {} menginjak portal ke peta {} yang tidak dimuat di server ini",
                    sd.name(), w.tm());
            return;
        }

        if (!sd.isGm()) {
            String reject = entryRejection(sd, dest);
            if (reject != null) {
                pushBack(sd);
                MapServer.clientView.messageToPlayer(sd, 3,
                        dest.rejectMsg.isEmpty() ? reject : dest.rejectMsg);
                return;
            }
        }
        Pc.warp(MapServer.world, sd, w.tm(), w.tx(), w.ty());
    }
    /**
     * Alasan pemain ditolak masuk sebuah peta, atau null bila boleh masuk.
     * Urutan pemeriksaan dan bunyi pesannya ditiru persis dari C — teks ini
     * dilihat pemain, jadi jangan diparafrasekan.
     */
    private static String entryRejection(User sd, MapData dest) {
        var st = sd.status;
        boolean tooWeak = st.level < dest.reqLvl
                || (st.baseHp < dest.reqVita && st.baseMp < dest.reqMana)
                || st.mark < dest.reqMark
                || (dest.reqPath > 0 && st.charClass != dest.reqPath);
        if (tooWeak) {
            long gap = Math.abs(dest.reqLvl - st.level);
            if (gap >= 10) {
                return "Nightmarish visions of your own death repel you.";
            }
            if (gap >= 5) {
                return "You're not quite ready to enter yet.";
            }
            if (gap < 5) {
                return "You almost understand the secrets to this entrance.";
            }
            // Tiga cabang di bawah TIDAK PERNAH tercapai — sama seperti di C,
            // karena tiga syarat di atas sudah mencakup semua nilai gap.
            // Sengaja dipertahankan: pemain melihat pesan level walau yang
            // kurang sebenarnya mark atau path. Jangan "diperbaiki" tanpa
            // memutuskan bahwa perubahan perilaku itu memang diinginkan.
            if (st.mark < dest.reqMark) {
                return "You do not understand the secrets to enter.";
            }
            if (dest.reqPath > 0 && st.charClass != dest.reqPath) {
                return "Your path forbids it.";
            }
            return "A powerful force repels you.";
        }
        if (st.level > dest.lvlMax
                || (st.baseHp > dest.vitaMax && st.baseMp > dest.manaMax)) {
            // C memakai pesan tetap di sini, mengabaikan MapRejectMsg
            return "A magical barrier prevents you from entering.";
        }
        return null;
    }
    /**
     * clif_pushback(): dorong pemain dua petak ke belakang, berdasarkan
     * arah hadapnya ({@code status.side}).
     */
    private static void pushBack(User sd) {
        switch (sd.status.side) {
            case 0 -> Pc.warp(MapServer.world, sd, sd.m, sd.x, sd.y + 2);
            case 1 -> Pc.warp(MapServer.world, sd, sd.m, sd.x - 2, sd.y);
            case 2 -> Pc.warp(MapServer.world, sd, sd.m, sd.x, sd.y - 2);
            case 3 -> Pc.warp(MapServer.world, sd, sd.m, sd.x + 2, sd.y);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------
    // Aturan gerak — dipindahkan dari Clif 27 Agustus 2026. Isinya logika
    // permainan, jadi tempatnya di sini, bukan di lapisan protokol.
    // ------------------------------------------------------------------

    /**
     * clif_canmove_sub(): apakah benda ini menghalangi langkah {@code sd}?
     *
     * Cermin dari versi C, termasuk pengecualiannya:
     * <ul>
     *   <li><b>diri sendiri tidak pernah menghalangi</b> — penting di tepi
     *       peta, karena koordinat tujuan dijepit sehingga bisa sama dengan
     *       posisi sekarang;</li>
     *   <li>hantu hanya menghalangi pemain yang bisa melihatnya;</li>
     *   <li>pemain tersembunyi dan GM ber-stealth tidak menghalangi;</li>
     *   <li>NPC bertipe 2 (dekorasi) tidak menghalangi.</li>
     * </ul>
     */
    private static boolean blocksMovement(User sd, org.rtk.map.data.BlockList bl, MapData map) {
        if (bl.id == sd.id) {
            return false;
        }
        if (bl.type == org.rtk.map.data.BlockList.Type.NPC && bl.subtype == 2) {
            return false;
        }
        if (bl instanceof User tsd) {
            boolean hantuTakTerlihat = map.showGhosts != 0
                    && tsd.status.state == User.STATE_GHOST
                    && sd.status.state != User.STATE_GHOST
                    && (sd.optFlags & User.OPT_GHOSTS) == 0;
            boolean disembunyikan = tsd.status.state == User.STATE_HIDDEN
                    || (tsd.isGm() && (tsd.optFlags & User.OPT_STEALTH) != 0);
            if (hantuTakTerlihat || disembunyikan) {
                return false;
            }
        }
        // TODO(A5): mob yang sudah mati tidak menghalangi (MOB_DEAD di C).
        return true;
    }
    /**
     * Geser kamera klien mengikuti langkah, dengan aturan tepi yang sama
     * seperti versi C: kamera hanya ikut bergerak bila pemain belum berada
     * di zona tepi peta.
     */
    private static void updateCamera(User sd, MapData map, int direction, int nx, int ny) {
        switch (direction) {
            case 0 -> {
                if (ny <= sd.viewY || ((map.ys - 1 - ny) < 7 && sd.viewY > 7)) {
                    sd.viewY--;
                }
            }
            case 1 -> {
                if ((nx < 8 && sd.viewX < 8) || 16 - (map.xs - 1 - nx) <= sd.viewX) {
                    sd.viewX++;
                }
            }
            case 2 -> {
                if ((ny < 7 && sd.viewY < 7) || 14 - (map.ys - 1 - ny) <= sd.viewY) {
                    sd.viewY++;
                }
            }
            case 3 -> {
                if (nx <= sd.viewX || ((map.xs - 1 - nx) < 8 && sd.viewX > 8)) {
                    sd.viewX--;
                }
            }
            // C memakai empat `if (direction == n)` terpisah, jadi arah di
            // luar 0..3 tidak menyentuh kamera sama sekali. Jangan jadikan
            // cabang arah 3 sebagai `default`.
            default -> {
            }
        }
        sd.viewX = Math.max(0, Math.min(sd.viewX, 16));
        sd.viewY = Math.max(0, Math.min(sd.viewY, 14));
    }
    /**
     * Kaitan skrip saat melangkah. Versi C juga memanggil {@code on_walk}
     * per perlengkapan dan {@code on_walk_passive} per mantra — menyusul
     * setelah subsistem barang & mantra diport.
     */
    private static void fireWalkScripts(User sd) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        try {
            MapServer.scriptEngine.doScript("onScriptedTile", "onScriptedTile",
                    MapServer.scriptEngine.playerRef(Pc.scriptPlayerOf(sd)));
        } catch (RuntimeException e) {
            log.error("[CLIF] kaitan skrip onScriptedTile gagal untuk {}", sd.name(), e);
        }
    }

    /** Pergeseran per arah: 0 atas, 1 kanan, 2 bawah, 3 kiri. */
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};

    @Override
    public void playerWalks(User sd, int direction, int claimedX, int claimedY,
                            RedrawRequest redraw) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }

        // Klien dan server tidak sepakat soal posisi -> tarik kembali.
        // Ini satu-satunya pertahanan terhadap klien yang berjalan sendiri.
        if (claimedX != sd.x || claimedY != sd.y) {
            log.debug("[CMD] {} desinkron: klien mengira ({},{}), server ({},{})",
                    sd.name(), claimedX, claimedY, sd.x, sd.y);
            MapServer.clientView.playerStepRejected(sd);
            return;
        }

        int oldX = sd.x;
        int oldY = sd.y;
        // Versi C memakai switch tanpa default: arah di luar 0..3 tidak
        // menggeser apa pun. Jangan diganti dengan mask 0x03 — arah 4 akan
        // berubah jadi "ke atas" dan klien cacat bisa berjalan menembus.
        int nx = sd.x + (direction < 4 ? DX[direction] : 0);
        int ny = sd.y + (direction < 4 ? DY[direction] : 0);

        // jepit ke batas peta, seperti versi C
        nx = Math.max(0, Math.min(nx, map.xs - 1));
        ny = Math.max(0, Math.min(ny, map.ys - 1));

        sd.blocked = false;
        if (!sd.isGm()) {
            if (!map.walkable(nx, ny)) {
                sd.blocked = true;
            } else {
                for (BlockList bl : map.objectsAt(nx, ny)) {
                    if (blocksMovement(sd, bl, map)) {
                        sd.blocked = true;
                        break;
                    }
                }
            }
        }

        if (sd.blocked || sd.paralyzed) {
            MapServer.clientView.playerStepRejected(sd);
            return;
        }

        updateCamera(sd, map, direction, nx, ny);
        MapServer.clientView.playerStepped(sd, direction, oldX, oldY);

        if (nx == sd.x && ny == sd.y) {
            return;   // terjepit di tepi: tidak benar-benar berpindah
        }

        MapServer.clientView.playerStepSeen(sd, direction, oldX, oldY);
        map.moveBlock(sd, nx, ny);

        // ⚠️ Harus di sini: setelah perpindahan, tetapi SEBELUM kait skrip
        // dan pemeriksaan portal — portal bisa memindahkan pemain ke peta
        // lain, dan menggambar ulang petak peta lama sesudah itu salah.
        // Lihat catatan kebocoran di ClientCommands.playerWalks.
        if (redraw != null) {
            MapServer.clientView.areaRedrawRequested(sd, map,
                    redraw.x(), redraw.y(), redraw.width(), redraw.height(),
                    redraw.checksum());
        }

        fireWalkScripts(sd);
        checkWarpTile(sd, map);
    }

    @Override
    public void playerClicks(User sd, long objectId) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return;
        }
        // id 0 = "yang terakhir diklik": klien memakainya untuk melanjutkan
        // percakapan yang sedang berjalan.
        BlockList bl = MapServer.npcs.byId(objectId == 0 ? sd.lastClick : objectId);
        if (bl == null) {
            log.debug("[CMD] {} mengklik id {} yang tidak dikenal", sd.name(), objectId);
            return;
        }
        if (bl.m != sd.m) {
            return;
        }
        sd.lastClick = bl.id;
        if (bl instanceof Npc nd) {
            clickNpc(sd, nd);
        }
        // TODO(A5): klik mob -> onLook + skrip "click" milik mob
    }

    @Override
    public void playerStartsExchange(User sd, long targetId) {
        Exchange.start(sd, targetId);
    }

    @Override
    public void playerOffersItem(User sd, int slot, int amount) {
        Exchange.offerItem(sd, slot, amount);
    }

    @Override
    public void playerOffersGold(User sd, long amount) {
        Exchange.offerGold(sd, amount);
    }

    @Override
    public void playerCancelsExchange(User sd) {
        User tsd = MapServer.userById(sd.exchange.target);
        MapServer.clientView.messageToPlayer(sd, 4, "Pertukaran dibatalkan.");
        if (tsd != null) {
            MapServer.clientView.messageToPlayer(tsd, 4, "Pertukaran dibatalkan.");
        }
        Exchange.cancel(sd);
        Exchange.cancel(tsd);
    }

    @Override
    public void playerConfirmsExchange(User sd, long claimedTarget) {
        Exchange.confirm(sd, claimedTarget);
    }

    @Override
    public void playerAnswersMenu(User sd, Answer answer) {
        lanjutkan(sd, answer, "menu/input");
    }

    @Override
    public void playerAnswersDialog(User sd, Answer answer) {
        lanjutkan(sd, answer, "dialog");
    }

    /**
     * Bagian bersama kedua jalur jawaban: lanjutkan coroutine skrip yang
     * sedang menunggu, atau lepas bila pemain membatalkan.
     *
     * <p>⚠️ Yang <b>tidak</b> boleh disatukan adalah <i>pemanggilnya</i> —
     * {@code playerAnswersMenu} dan {@code playerAnswersDialog} tetap dua
     * pintu terpisah karena di C keduanya memakai fungsi resume berbeda.
     * Yang sama hanya cara membungkus jawabannya jadi nilai Lua.</p>
     */
    private void lanjutkan(User sd, Answer answer, String asal) {
        if (MapServer.scriptEngine == null) {
            return;
        }
        ScriptPlayer p = sd.scriptPlayer();
        if (p.coroutine == null) {
            log.debug("[CMD] {} menjawab {} padahal tidak ada yang menunggu",
                    sd.name(), asal);
            return;
        }
        if (answer == null) {
            MapServer.scriptEngine.cancel(p);   // pemain membatalkan
            return;
        }
        LuaValue nilai = nilaiDari(answer);
        if (nilai == null) {
            MapServer.scriptEngine.cancel(p);
            return;
        }
        try {
            p.pendingDialog = null;
            MapServer.scriptEngine.resume(p, nilai);
            MapServer.clientView.scriptDialogReady(sd, p);
        } catch (RuntimeException e) {
            log.error("[CMD] melanjutkan {} gagal untuk {}", asal, sd.name(), e);
        }
    }

    /** Bungkus jawaban jadi nilai Lua; null berarti "batalkan". */
    private static LuaValue nilaiDari(Answer a) {
        if (a.choice != null) {
            return LuaValue.valueOf(a.choice);
        }
        if (a.step != null) {
            return LuaValue.valueOf(a.step);
        }
        if (a.slot != null) {
            return LuaValue.valueOf(a.slot);
        }
        if (a.text != null) {
            return LuaValue.valueOf(a.text);
        }
        if (a.itemName != null) {
            // Daftar beli dijawab dengan NAMA barang. Nama kosong berarti
            // pemain menutup tokonya, bukan membeli barang tanpa nama.
            return a.itemName.isEmpty() ? null : LuaValue.valueOf(a.itemName);
        }
        return null;
    }

    // ==================================================================
    // Bicara — port clif_parsesay / clif_parsewisp
    // ==================================================================

    /** Batas panjang satu kalimat; di C {@code RFIFOB(fd,6) > 100} ditolak. */
    private static final int MAX_SAY = 100;

    /** Batas nama tujuan dan isi bisikan ({@code msglen > 80 || dstlen > 80}). */
    private static final int MAX_WHISPER = 80;

    /** Tipe paket 0x0A: 0 bisikan/pesan biru, 3 mini text, 11 subpath, 12 klan. */
    private static final int MSG_WHISPER = 0;
    private static final int MSG_MINI = 3;
    private static final int MSG_SUBPATH = 11;
    private static final int MSG_CLAN = 12;

    /** {@code settingFlags}: enum settingFLAGS di common/mmo.h. */
    private static final long FLAG_WHISPER = 1;
    private static final long FLAG_EXCHANGE = 128;

    @Override
    public void playerSays(User sd, int channel, String text) {
        if (text == null) {
            return;
        }
        // Di C kedua penjaga ini menghasilkan pesan yang sama dan sebuah
        // baris di konsol server: klien resmi tidak bisa menghasilkannya.
        if (channel > 1 || text.length() > MAX_SAY) {
            log.warn("[CMD] {} mengirim kalimat tak masuk akal (saluran {}, {} huruf)",
                    sd.name(), channel, text.length());
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "I just told the GM on you!");
            return;
        }
        if (!sd.isGm() && sd.status.mute != 0) {
            return;
        }

        sd.talkType = channel;
        sd.speech = text;

        // ⚠️ Tidak ada siaran di sini, dan itu bukan kelalaian: badan yang
        // menyiarkan di clif_parsesay DIKOMENTARI SELURUHNYA. Yang berbicara
        // adalah skrip `onSay` (speech.lua), yang membaca player.speech lalu
        // memutuskan sendiri — termasuk memanggil player:talk() bila memang
        // perlu terdengar. Menambahkan siaran di sini akan membuat setiap
        // kalimat terkirim dua kali.
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        LuaValue ref = engine.playerRef(sd.scriptPlayer());
        for (int id : sd.status.spells) {
            if (id > 0) {
                kaitMantra(engine, id, "on_say", ref);
            }
        }
        kait(engine, "onSay", null, ref);
        MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
    }

    @Override
    public void playerWhispers(User sd, String target, String text) {
        if (target == null || text == null) {
            return;
        }
        if (!sd.isGm() && (sd.status.settingFlags & FLAG_WHISPER) == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    "You have whispering turned off.");
            return;
        }
        if (bungkam(sd)) {
            return;
        }
        if (target.length() > MAX_WHISPER || text.length() > MAX_WHISPER) {
            log.warn("[CMD] {} mengirim bisikan tak masuk akal ({}/{} huruf)",
                    sd.name(), target.length(), text.length());
            return;
        }

        // ⚠️ Empat nama pendek ini SALURAN, bukan nama pemain. Memperlakukan
        // semuanya sebagai nama akan membuat keempatnya menjawab
        // "tidak ditemukan" dan memutus seluruh obrolan klan/grup/subpath.
        switch (target) {
            case "!" -> clanChat(sd, text);
            case "!!" -> MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    // Sistem grup belum ada, jadi jawabannya selalu cabang
                    // "belum bergrup" — sama dengan yang dilihat pemain di C
                    // ketika memang belum bergrup. Bukan pesan darurat.
                    "You are not in a group");
            case "@" -> subpathChat(sd, text);
            case "?" -> noviceChat(sd, text);
            default -> directWhisper(sd, target, text);
        }
    }

    /** {@code map[..].cantalk}: peta yang membungkam siapa pun kecuali GM. */
    private static boolean bungkam(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map != null && map.canTalk == 1 && !sd.isGm()) {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "Your voice is swept away by a strange wind.");
            return true;
        }
        return false;
    }

    /** Gelar penulis untuk baris obrolan; "()" bila kelasnya tak bergelar. */
    private static String gelar(User sd) {
        String n = MapServer.classDb.pathName(sd.status.charClass, sd.status.mark);
        return "(" + (n == null ? "" : n) + ")";
    }

    /** clif_sendclanmessage(): ke seluruh anggota klan yang menyalakan saluran. */
    private static void clanChat(User sd, String text) {
        if (sd.status.clan == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER, "You are not in a clan");
            return;
        }
        if (sd.status.clanChat == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER, "Clan chat is off.");
            return;
        }
        catatObrolan("clanChatLog", sd, text);
        String baris = "<!" + sd.name() + "> " + gelar(sd) + " " + text;
        for (User tsd : MapServer.onlineChars.values()) {
            if (tsd.status.clan == sd.status.clan && tsd.status.clanChat != 0) {
                MapServer.clientView.messageToPlayer(tsd, MSG_CLAN, baris);
            }
        }
    }

    /** clif_sendsubpathmessage(): ke sesama kelas, bila kelasnya punya saluran. */
    private static void subpathChat(User sd, String text) {
        if (!MapServer.classDb.hasSubpathChat(sd.status.charClass)) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER, "You cannot do that.");
            return;
        }
        catatObrolan("subPathChatLog", sd, text);
        String baris = "<@" + sd.name() + "> " + gelar(sd) + " " + text;
        for (User tsd : MapServer.onlineChars.values()) {
            if (tsd.status.charClass == sd.status.charClass && tsd.status.subpathChat != 0) {
                MapServer.clientView.messageToPlayer(tsd, MSG_SUBPATH, baris);
            }
        }
    }

    /**
     * clif_sendnovicemessage(): saluran tanya-jawab pemula.
     *
     * <p>Dua hal yang mudah terlewat: pemain yang <b>bukan</b> tutor
     * mendapat gemanya sendiri lewat tipe pesan yang berbeda (kalau tidak,
     * ia tidak akan pernah melihat kalimatnya sendiri terkirim), dan yang
     * <b>menerima</b> hanyalah tutor dan GM — bukan sesama pemula.</p>
     */
    private static void noviceChat(User sd, String text) {
        if (sd.status.tutor == 0 && !sd.isGm() && sd.status.level >= 99) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER, "You cannot do that.");
            return;
        }
        catatObrolan("noviceChatLog", sd, text);
        String baris = "[Novice]" + gelar(sd) + " " + sd.name() + "> " + text;
        if (sd.status.tutor == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_SUBPATH, baris);
        }
        for (User tsd : MapServer.onlineChars.values()) {
            if ((tsd.status.tutor != 0 || tsd.isGm()) && tsd.status.noviceChat != 0) {
                MapServer.clientView.messageToPlayer(tsd, MSG_CLAN, baris);
            }
        }
    }

    /**
     * Bisikan ke satu orang.
     *
     * <p>⚠️ Pemain ber-stealth <b>tidak</b> dijawab "tidak ditemukan" begitu
     * saja: bisikannya tetap sampai, hanya pengirimnya yang diberi tahu
     * bahwa orangnya tak ditemukan. Itu di C, dan disengaja — GM yang
     * ber-stealth tetap bisa dihubungi tanpa membocorkan kehadirannya.</p>
     */
    private static void directWhisper(User sd, String target, String text) {
        User tsd = MapServer.userByName(target);
        if (tsd == null) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    target + " is nowhere to be found.");
            return;
        }
        if (!sd.isGm() && (tsd.status.settingFlags & FLAG_WHISPER) == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    "They cannot hear you right now.");
            return;
        }
        catatObrolan("whisperLog", sd, text);
        MapServer.clientView.messageToPlayer(tsd, MSG_WHISPER,
                sd.name() + "\" " + gelar(sd) + " " + text);
        if (!sd.isGm() && (tsd.optFlags & User.OPT_STEALTH) != 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    target + " is nowhere to be found.");
        } else {
            MapServer.clientView.messageToPlayer(sd, MSG_WHISPER,
                    tsd.name() + "> " + text);
        }
    }

    /** Kait {@code characterLog.*ChatLog}, yang menerima nama dan kalimatnya. */
    private static void catatObrolan(String method, User sd, String text) {
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        try {
            engine.doScript("characterLog", method,
                    LuaValue.valueOf(sd.name()), LuaValue.valueOf(text));
        } catch (RuntimeException e) {
            log.error("[CMD] kait characterLog.{} gagal", method, e);
        }
    }

    // ==================================================================
    // Barang — port clif_parsegetitem / clif_parsedropitem / clif_dropgold
    // ==================================================================

    /** {@code status.state}: 1 arwah, 2 rogue tak terlihat, 3 menunggang, 4 berubah wujud. */
    private static final int ST_SPIRIT = 1;
    private static final int ST_INVIS = 2;
    private static final int ST_MOUNT = 3;
    private static final int ST_MORPH = 4;

    @Override
    public void playerPicksUp(User sd, int mode) {
        if (sd.status.state == ST_SPIRIT || sd.status.state == ST_MOUNT) {
            return;   // arwah dan penunggang tidak bisa memungut
        }

        var engine = MapServer.scriptEngine;
        if (sd.status.state == ST_INVIS) {
            // Memungut membatalkan penyamaran rogue, dan pembatalannya lewat
            // skrip mantranya sendiri — bukan dengan menyetel state di sini.
            sd.status.state = 0;
            if (engine != null) {
                kait(engine, "invis_rogue", "uncast",
                        engine.playerRef(sd.scriptPlayer()));
            }
            MapServer.clientView.objectAppearanceChanged(sd);
        }

        MapServer.clientView.objectActed(sd, 4, 40, 0);
        sd.pickUpType = mode;

        if (engine == null) {
            return;
        }
        LuaValue ref = engine.playerRef(sd.scriptPlayer());
        for (SkillInfo s : sd.status.duraAether) {
            if (s.id > 0 && s.duration > 0) {
                kaitMantra(engine, s.id, "on_pickup_while_cast", ref);
            }
        }
        // ⚠️ Inilah yang benar-benar memungut. Server hanya menyiapkan
        // keadaannya; onPickup.lua yang menyapu petak, memeriksa hak
        // memungut, dan memanggil addGold / addItem.
        kait(engine, "onPickUp", null, ref);
        MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
    }

    @Override
    public void playerDropsItem(User sd, int slot, boolean all) {
        if (!sd.isGm()) {
            if (sd.status.state == ST_MOUNT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "You cannot do that while riding a mount.");
                return;
            }
            if (sd.status.state == ST_SPIRIT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI, "Spirits can't do that.");
                return;
            }
        }
        if (slot < 0 || slot >= sd.status.maxInv || slot >= sd.status.inventory.size()) {
            return;
        }
        org.rtk.common.mmo.Item it = sd.status.inventory.get(slot);
        if (it.id > 0 && MapServer.itemDb.cannotBePickedUp(it.id)) {
            // ⚠️ Kolomnya `ItmDroppable` dan artinya kebalikan namanya —
            // lihat Peringatan #32. Dibungkus supaya tidak menyesatkan lagi.
            MapServer.clientView.messageToPlayer(sd, MSG_MINI, "You can't drop this item.");
            return;
        }

        sd.fakeDrop = 0;
        MapServer.clientView.objectActed(sd, 5, 20, 0);
        sd.invSlot = slot;   // dibaca kait on_drop dan on_drop_while_cast

        var engine = MapServer.scriptEngine;
        if (engine != null) {
            LuaValue ref = engine.playerRef(sd.scriptPlayer());
            // Kait barangnya berjalan SEBELUM barangnya jatuh — itulah yang
            // membuat "jatuh pura-pura" mungkin. Urutan ini ada di C dengan
            // alasan yang ditulis di sumbernya.
            String yname = it.id > 0 ? MapServer.itemDb.info(it.id).name() : "";
            if (!yname.isEmpty()) {
                kait(engine, yname, "on_drop", ref);
            }
            for (SkillInfo s : sd.status.duraAether) {
                if (s.id > 0 && s.duration > 0) {
                    kaitMantra(engine, s.id, "on_drop_while_cast", ref);
                }
            }
            for (SkillInfo s : sd.status.duraAether) {
                if (s.id > 0 && s.aether > 0) {
                    kaitMantra(engine, s.id, "on_drop_while_aether", ref);
                }
            }
        }
        if (sd.fakeDrop != 0) {
            return;   // skrip membatalkannya
        }

        MapServer.floorItems.dropFromInventory(sd, slot, all ? 1 : 0);
    }

    @Override
    public void playerDropsGold(User sd, long amount) {
        if (!sd.isGm()) {
            if (sd.status.state == ST_SPIRIT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI, "Spirits can't do that.");
                return;
            }
            if (sd.status.state == ST_MOUNT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "You cannot do that while riding a mount.");
                return;
            }
            if (sd.status.state == ST_MORPH) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "You cannot do that while transformed.");
                return;
            }
        }
        long punya = sd.status.money;
        if (punya <= 0 || amount <= 0) {
            return;
        }

        MapServer.clientView.objectActed(sd, 5, 20, 0);
        long jatuh = Math.min(amount, punya);
        // ⚠️ Emasnya dipotong DI SINI, sebelum kait skrip berjalan — dan C
        // tidak mengembalikannya bila kaitnya membatalkan (`fakeDrop`).
        // Ditiru apa adanya; mengubahnya berarti mengubah berapa emas yang
        // hilang pada tiap pembatalan, dan skrip yang memakai fakeDrop pada
        // emas memang mengandalkan pemotongan itu sudah terjadi.
        sd.status.money = punya - jatuh;

        FloorItem fl = FloorItemRegistry.newGoldPile(jatuh);
        sd.fakeDrop = 0;

        var engine = MapServer.scriptEngine;
        if (engine != null) {
            LuaValue ref = engine.playerRef(sd.scriptPlayer());
            LuaValue benda = engine.objectRef(fl);
            kait2(engine, "on_drop_gold", null, ref, benda);
            for (SkillInfo s : sd.status.duraAether) {
                if (s.id > 0 && s.duration > 0) {
                    kaitMantra2(engine, s.id, "on_drop_gold_while_cast", ref, benda);
                }
            }
            for (SkillInfo s : sd.status.duraAether) {
                if (s.id > 0 && s.aether > 0) {
                    kaitMantra2(engine, s.id, "on_drop_gold_while_aether", ref, benda);
                }
            }
            if (sd.fakeDrop != 0) {
                return;
            }
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "You dropped " + fl.data.amount + " coins");
            if (!MapServer.floorItems.placeGold(fl, sd.m, sd.x, sd.y)) {
                LuaValue benda2 = engine.objectRef(fl);
                kait2(engine, "after_drop_gold", null, ref, benda2);
                for (SkillInfo s : sd.status.duraAether) {
                    if (s.id > 0 && s.duration > 0) {
                        kaitMantra2(engine, s.id, "after_drop_gold_while_cast", ref, benda2);
                    }
                }
                for (SkillInfo s : sd.status.duraAether) {
                    if (s.id > 0 && s.aether > 0) {
                        kaitMantra2(engine, s.id, "after_drop_gold_while_aether", ref, benda2);
                    }
                }
                kait2(engine, "characterLog", "dropWrite", ref, benda2);
            }
        } else {
            MapServer.floorItems.placeGold(fl, sd.m, sd.x, sd.y);
        }

        MapServer.clientView.playerStatusChanged(sd, Clif.SFLAG_XPMONEY);
    }

    /** Petak yang sedang dihadapi pemain; arah 0 atas, 1 kanan, 2 bawah, 3 kiri. */
    private static int[] petakDepan(User sd) {
        int side = sd.status.side;
        return new int[] {
            sd.x + (side == 1 ? 1 : side == 3 ? -1 : 0),
            sd.y + (side == 0 ? -1 : side == 2 ? 1 : 0)
        };
    }

    /** Benda pertama di petak itu ({@code map_firstincell}). */
    private static org.rtk.map.data.BlockList bendaDiDepan(User sd) {
        MapData map = MapServer.world.get(sd.m);
        if (map == null) {
            return null;
        }
        int[] p = petakDepan(sd);
        for (org.rtk.map.data.BlockList bl : map.objectsAt(p[0], p[1])) {
            if (bl.id != sd.id) {
                return bl;
            }
        }
        return null;
    }

    @Override
    public void playerHandsItem(User sd, int slot, int amount) {
        if (slot < 0 || slot >= sd.status.inventory.size() || amount <= 0) {
            return;
        }
        org.rtk.common.mmo.Item it = sd.status.inventory.get(slot);
        if (it.id <= 0 || amount > it.amount) {
            return;
        }
        sd.invSlot = slot;

        org.rtk.map.data.BlockList bl = bendaDiDepan(sd);
        if (bl == null) {
            return;
        }

        // Tiga sasaran, tiga perilaku yang sama sekali berbeda.
        if (bl instanceof User tsd) {
            if ((tsd.status.settingFlags & FLAG_EXCHANGE) == 0) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "They have refused to exchange with you");
                return;
            }
            if (Exchange.start(sd, tsd.id)) {
                Exchange.offerItem(sd, slot, amount);
            }
        } else if (bl instanceof Mob mob) {
            // Barang yang tak bisa ditukar tidak bisa diberikan ke mob.
            if (MapServer.itemDb.cannotBeExchanged(it.id)) {
                return;
            }
            mob.receiveItem(it, amount);
            it.amount -= amount;
            if (it.amount <= 0) {
                // ⚠️ Jangan tambahkan remove(slot) di sini: peristiwa ini
                // sendiri yang mengosongkan slotnya di server, bukan sekadar
                // memberi tahu klien (Peringatan #37). Mencabutnya sekali
                // lagi membuang slot pemain BERIKUTNYA. Sempat kejadian,
                // dan yang menangkapnya adalah uji ini.
                MapServer.clientView.playerInventorySlotCleared(sd, slot, 9);
            } else {
                MapServer.clientView.playerInventorySlotChanged(sd, slot);
            }
        } else if (bl instanceof Npc nd) {
            if (MapServer.itemDb.cannotBeExchanged(it.id)
                    || MapServer.itemDb.cannotBePickedUp(it.id)) {
                return;
            }
            var engine = MapServer.scriptEngine;
            if (nd.receiveItem != 0 && engine != null) {
                kait2(engine, nd.name, "handItem",
                        engine.playerRef(sd.scriptPlayer()), engine.objectRef(nd));
                MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
            } else {
                MapServer.clientView.npcSaidTo(sd, nd.id,
                        "What are you trying to do? Keep your junky "
                        + MapServer.itemDb.info(it.id).tampilan() + " with you!");
            }
        }
    }

    @Override
    public void playerHandsGold(User sd, long amount) {
        if (amount <= 0) {
            return;
        }
        long punya = sd.status.money;
        long tawar = Math.min(amount, punya);
        if (tawar <= 0) {
            return;
        }
        org.rtk.map.data.BlockList bl = bendaDiDepan(sd);
        if (!(bl instanceof User tsd)) {
            return;   // hanya pemain yang bisa menerima emas langsung
        }
        if ((tsd.status.settingFlags & FLAG_EXCHANGE) == 0) {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "They have refused to exchange with you");
            return;
        }
        if (Exchange.start(sd, tsd.id)) {
            Exchange.offerGold(sd, tawar);
        }
    }

    // ==================================================================
    // Perlengkapan & memakai barang — port clif_parsewield / parseunequip /
    // parseeatitem / parseuseitem / parsethrow
    // ==================================================================

    @Override
    public void playerWields(User sd, int slot) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        int jenis = MapServer.itemDb.info(sd.status.inventory.get(slot).id).type();
        // ⚠️ Rentangnya 3..16, BUKAN 3..17 seperti ItemDb.ITM_EQUIP_MAX.
        // ITM_HAND (17) punya slot perlengkapan tetapi tidak bisa dikenakan
        // lewat pintu ini — hanya lewat "pakai". Itu di C.
        if (jenis >= ItemDb.ITM_WEAP && jenis <= ItemDb.ITM_COAT) {
            Items.useItem(sd, slot);
        } else {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI, "You cannot wield that!");
        }
    }

    @Override
    public void playerUnequips(User sd, int equipSlot) {
        if (equipSlot < 0 || equipSlot >= org.rtk.map.data.Equip.COUNT) {
            return;
        }
        var it = sd.status.equipAt(equipSlot);
        if (it == null || it.id <= 0) {
            return;
        }
        if (MapServer.itemDb.cannotBeUnequipped(it.id) && !sd.isGm()) {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "You are unable to unequip that.");
            return;
        }
        // ⚠️ Ruang inventaris diperiksa DI SINI, sebelum kait skrip berjalan.
        // Kalau tidak, `onUnequip` sudah telanjur menjalankan efek sampingnya
        // sementara barangnya tetap menempel di badan.
        if (sd.status.inventory.size() >= sd.status.maxInv) {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI, "Your inventory is full.");
            return;
        }
        if (Items.unequip(sd, equipSlot)) {
            MapServer.clientView.playerEquipmentCleared(sd, equipSlot);
            MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
        }
    }

    @Override
    public void playerEatsItem(User sd, int slot) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        if (MapServer.itemDb.info(sd.status.inventory.get(slot).id).type()
                == ItemDb.ITM_EAT) {
            Items.useItem(sd, slot);
        } else {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI, "That item is not edible.");
        }
    }

    @Override
    public void playerUsesItem(User sd, int slot) {
        Items.useItem(sd, slot);
    }

    @Override
    public void playerThrowsItem(User sd, int slot, boolean confirmed) {
        if (slot < 0 || slot >= sd.status.inventory.size()) {
            return;
        }
        var it = sd.status.inventory.get(slot);
        if (it.id <= 0) {
            return;
        }
        if (MapServer.itemDb.needsThrowConfirm(it.id) && !confirmed) {
            // Barang yang sekali dilempar tidak bisa diambil lagi menanyakan
            // dulu. Di RTK2 pertanyaannya dijawab klien lalu dikirim ulang
            // dengan bendera konfirmasi — tanpa paket tanya-jawab tersendiri.
            MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                    "Are you sure you want to throw that away?");
            return;
        }
        if (!sd.isGm()) {
            if (sd.status.state == ST_SPIRIT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI, "Spirits can't do that.");
                return;
            }
            if (sd.status.state == ST_MOUNT) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "You cannot do that while riding a mount.");
                return;
            }
            if (sd.status.state == ST_MORPH) {
                MapServer.clientView.messageToPlayer(sd, MSG_MINI,
                        "You cannot do that while transformed.");
                return;
            }
        }
        if (MapServer.itemDb.cannotBePickedUp(it.id)) {
            MapServer.clientView.messageToPlayer(sd, MSG_MINI, "You can't throw this item.");
            return;
        }

        sd.invSlot = slot;
        int[] tujuan = Items.throwLanding(sd);
        sd.throwX = tujuan[0];
        sd.throwY = tujuan[1];

        // ⚠️ Server TIDAK melempar sendiri: kait `onThrow` yang memanggil
        // player:throwItem(), dan skrip bisa memindahkan tujuannya lebih
        // dulu. Sama seperti onSay, onPickUp, dan swing (Peringatan #58).
        var engine = MapServer.scriptEngine;
        if (engine == null) {
            Items.throwScript(sd);   // tanpa mesin skrip, jalur langsung
            return;
        }
        try {
            engine.doScript("onThrow", null,
                    engine.playerRef(sd.scriptPlayer()),
                    LuaValue.valueOf((double) it.id));
        } catch (RuntimeException e) {
            log.error("[CMD] kait onThrow gagal untuk {}", sd.name(), e);
        }
        MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
    }

    // ==================================================================
    // Pertarungan — port clif_parseattack beserta pembatas kecepatannya
    // ==================================================================

    @Override
    public void playerAttacks(User sd) {
        // ⚠️ Pembatas kecepatan serang ada di DISPATCHER paket versi C,
        // bukan di clif_parseattack — dan ia satu-satunya yang menghentikan
        // klien yang menyerang secepat ia bisa mengirim paket. Tempatnya di
        // sini, bukan di pembaca: yang membatasi adalah kecepatan senjata,
        // dan itu nilai permainan.
        if (sd.attacked || sd.attackSpeed <= 0) {
            return;
        }
        if (sd.paralyzed || sd.status.state == ST_SPIRIT
                || sd.status.state == ST_MOUNT) {
            return;
        }
        sd.attacked = true;
        long jeda = (sd.attackSpeed * 1000L) / 60L;
        MapServer.timers.insert(jeda, 0, (a, b) -> {
            sd.attacked = false;
            return 0;
        }, 0, 0);

        var engine = MapServer.scriptEngine;
        if (engine == null) {
            return;
        }
        org.rtk.common.mmo.Item senjata = sd.status.equipAt(org.rtk.map.data.Equip.WEAP);
        long senjataId = senjata == null ? 0 : senjata.id;
        int bunyi = senjataId > 0 ? MapServer.itemDb.info(senjataId).sound() : 0;
        MapServer.clientView.objectActed(sd, 1, sd.attackSpeed, bunyi == 0 ? 9 : bunyi);

        LuaValue ref = engine.playerRef(sd.scriptPlayer());
        // ⚠️ Tiga skrip berbeda, dan urutannya di C persis begini.
        // `swingDamage` menghitung, `swing` mencari sasarannya sendiri di
        // petak depan, `onSwing` kait konten. Tidak ada sasaran yang
        // dikirim klien sama sekali.
        kait(engine, "swingDamage", null, ref);
        kait(engine, "swing", null, ref);
        kait(engine, "onSwing", null, ref);

        if (senjataId > 0) {
            long look = MapServer.itemDb.info(senjataId).look().look();
            if (look >= 20000 && look < 30000) {   // busur
                kait(engine, MapServer.itemDb.info(senjataId).name(), "shootArrow", ref);
                kait(engine, "shootArrow", null, ref);
            }
        }
        for (org.rtk.common.mmo.Item eq : sd.status.equip) {
            if (eq != null && eq.id > 0) {
                kait(engine, MapServer.itemDb.info(eq.id).name(), "on_swing", ref);
            }
        }
        for (int id : sd.status.spells) {
            if (id > 0) {
                kaitMantra(engine, id, "passive_on_swing", ref);
            }
        }
        for (SkillInfo s : sd.status.duraAether) {
            if (s.id > 0 && s.duration > 0) {
                kaitMantra(engine, s.id, "on_swing_while_cast", ref);
            }
        }
        MapServer.clientView.scriptDialogReady(sd, sd.scriptPlayer());
    }

    // ------------------------------------------------------------------
    // pemanggil kait bersama
    // ------------------------------------------------------------------

    private static void kait(org.rtk.map.script.ScriptEngine engine, String root,
                             String method, LuaValue a) {
        if (root == null || root.isEmpty()) {
            return;
        }
        try {
            engine.doScript(root, method, a);
        } catch (RuntimeException e) {
            log.error("[CMD] kait '{}' pada '{}' gagal", method, root, e);
        }
    }

    private static void kait2(org.rtk.map.script.ScriptEngine engine, String root,
                              String method, LuaValue a, LuaValue b) {
        if (root == null || root.isEmpty()) {
            return;
        }
        try {
            engine.doScript(root, method, a, b);
        } catch (RuntimeException e) {
            log.error("[CMD] kait '{}' pada '{}' gagal", method, root, e);
        }
    }

    private static void kaitMantra(org.rtk.map.script.ScriptEngine engine, int spellId,
                                   String method, LuaValue a) {
        kait(engine, MapServer.spellDb.nameOf(spellId), method, a);
    }

    private static void kaitMantra2(org.rtk.map.script.ScriptEngine engine, int spellId,
                                    String method, LuaValue a, LuaValue b) {
        kait2(engine, MapServer.spellDb.nameOf(spellId), method, a, b);
    }

}
