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
 * {@code Clif} sekarang hanyalah pembacanya.</p>
 *
 * <p>Kelas ini <b>tidak boleh</b> menyentuh {@code Session}, {@code rfifo*},
 * atau opcode mana pun. Kalau sebuah baris di sini butuh salah satunya,
 * baris itu ada di tempat yang salah.</p>
 *
 * <p>Untuk mengirim sesuatu ke klien ia memakai {@link MapServer#clientView},
 * sama seperti seluruh logika lain.</p>
 *
 * <h2>⚠️ Yang MASIH salah tempat</h2>
 *
 * <p>Lima helper dipanggil dari sini tetapi badannya masih tinggal di
 * {@code Clif}, padahal isinya <b>logika permainan</b>, bukan protokol:
 * {@code blocksMovement} (aturan siapa menghalangi siapa),
 * {@code updateCamera}, {@code fireWalkScripts} (kait skrip saat
 * melangkah), {@code checkWarpTile} (petak portal dan syarat masuk peta),
 * dan {@code clickNpc}.</p>
 *
 * <p>Empat lainnya — {@code snapBack}, {@code sendWalkConfirm},
 * {@code broadcastWalk}, {@code redrawArea} — memang milik {@code Clif};
 * itu bukan salah tempat, hanya belum lewat {@link ClientView}.</p>
 *
 * <p>Pemisahannya sengaja <b>bertahap</b>: memindahkan antarmukanya lebih
 * dulu membuat batasnya terlihat dan tetap terjaga 552 assertion
 * {@code cliftest}; memindahkan badan kelima helper itu sekaligus akan
 * menyentuh ratusan baris tanpa satu pun gerbang regresi yang berubah —
 * perubahan besar yang tidak bisa dibuktikan benar. Kerjakan saat protokol
 * baru dirancang, satu helper per langkah.</p>
 */
public final class MapCommands implements ClientCommands {

    private static final Logger log = LogManager.getLogger(MapCommands.class);


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
            Clif.snapBack(sd);
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
                    if (Clif.blocksMovement(sd, bl, map)) {
                        sd.blocked = true;
                        break;
                    }
                }
            }
        }

        if (sd.blocked || sd.paralyzed) {
            Clif.snapBack(sd);
            return;
        }

        Clif.updateCamera(sd, map, direction, nx, ny);
        Clif.sendWalkConfirm(sd, direction, oldX, oldY);

        if (nx == sd.x && ny == sd.y) {
            return;   // terjepit di tepi: tidak benar-benar berpindah
        }

        Clif.broadcastWalk(sd, direction, oldX, oldY);
        map.moveBlock(sd, nx, ny);

        // ⚠️ Harus di sini: setelah perpindahan, tetapi SEBELUM kait skrip
        // dan pemeriksaan portal — portal bisa memindahkan pemain ke peta
        // lain, dan menggambar ulang petak peta lama sesudah itu salah.
        // Lihat catatan kebocoran di ClientCommands.playerWalks.
        if (redraw != null) {
            Clif.redrawArea(sd, map, redraw.x(), redraw.y(),
                    redraw.width(), redraw.height(), redraw.checksum());
        }

        Clif.fireWalkScripts(sd);
        Clif.checkWarpTile(sd, map);
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
            Clif.clickNpc(sd, nd);
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
            Clif.flushDialog(sd, p);
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
