package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.luaj.vm2.LuaValue;

import org.rtk.map.data.BlockList;
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
}
