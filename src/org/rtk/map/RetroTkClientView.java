package org.rtk.map;

import org.rtk.map.data.BlockList;
import org.rtk.map.data.MapData;

/**
 * Implementasi {@link ClientView} untuk protokol <b>RetroTK asli</b>:
 * setiap peristiwa permainan diterjemahkan jadi paket {@code clif_*}.
 *
 * <p>Kelas ini sengaja <b>tipis</b> — tidak ada logika permainan di sini,
 * hanya pemetaan peristiwa ke paket. Itulah gunanya: begitu protokol baru
 * dirancang untuk klien libGDX, cukup tulis implementasi kedua dan tukar di
 * {@link MapServer#clientView}, tanpa menyentuh satu baris pun logika.</p>
 *
 * <p>Rujukan paketnya ada di {@link Clif} masing-masing method.</p>
 */
public final class RetroTkClientView implements ClientView {

    @Override
    public void playerEnteredWorld(User sd) {
        Clif.sendWorldEntry(sd);
    }

    @Override
    public void playerViewRefreshed(User sd) {
        Clif.refresh(sd);
    }

    @Override
    public void playerStatusChanged(User sd, int flags) {
        Clif.sendStatus(sd, flags);
    }

    @Override
    public void playerIdentityChanged(User sd) {
        Clif.sendMyStatus(sd);
    }

    @Override
    public void playerDurationChanged(User sd, int spellId, int seconds, String casterName) {
        Clif.sendDuration(sd, spellId, seconds, casterName);
    }

    @Override
    public void playerAetherChanged(User sd, int spellId, int seconds) {
        Clif.sendAether(sd, spellId, seconds);
    }

    @Override
    public void playerInventorySlotChanged(User sd, int slot) {
        Clif.sendAddItem(sd, slot);
    }

    @Override
    public void playerInventorySlotCleared(User sd, int slot, int reason) {
        Clif.sendDelItem(sd, slot, reason);
    }

    @Override
    public void playerSpellRemoved(User sd, int slot) {
        Clif.removeSpell(sd, slot);
    }

    @Override
    public void playerSpellSlotChanged(User sd, int slot) {
        Clif.sendMagic(sd, slot);
    }

    @Override
    public void playerProfile(User sd) {
        // Di RetroTK profil menumpang clif_mystaytus (0x39), yang sengaja
        // dibiarkan TAHAP 1 karena protokolnya memang akan diganti.
        Clif.sendMyStatus(sd);
    }

    @Override
    public void playerTransferred(User sd, String host, int port,
                                  int m, int x, int y) {
        Clif.transfer(sd, host, port);
    }

    @Override
    public void worldTimeChanged(User sd) {
        Clif.sendTime(sd);
    }

    @Override
    public void townListToPlayer(User sd, java.util.List<String> kota) {
        Clif.sendTowns(sd, kota);
    }

    @Override
    public void rankingToPlayer(User sd, String judul, java.util.List<Object[]> baris) {
        // clif_parseranking menyusun jendela teks 0x7D; belum diport dan
        // nilainya rendah karena format kabelnya akan diganti.
    }

    @Override
    public void objectSideChanged(BlockList bl) {
        Clif.sendSide(bl);
    }

    @Override
    public void objectAnimation(BlockList bl, int animation, int times) {
        Clif.sendAnimation(bl, animation, times);
    }

    @Override
    public void objectAnimationAt(BlockList bl, int animation, int times, int x, int y) {
        Clif.sendAnimationXy(bl, animation, times, x, y);
    }

    @Override
    public void popupToPlayer(User sd, String text) {
        Clif.popup(sd, text);
    }

    @Override
    public void playerCameraChanged(User sd, int x, int y) {
        // Urutan diambil dari pcl_changeview: kamera dulu, lalu benda-benda
        // digambar ulang, lalu sisa gambar lama dibuang, baru karakternya.
        Clif.sendXyChange(sd, x, y);
        Clif.redrawSurroundings(sd);
    }

    @Override
    public void boardListToPlayer(User sd, int board, int flags1, int flags2,
                                  java.util.List<Clif.BoardEntry> isi) {
        Clif.sendBoardList(sd, board, flags1, flags2, isi);
    }

    @Override
    public void boardPostToPlayer(User sd, int board, int pos, int bendera,
                                  String penulis, String topik, int bulan,
                                  int hari, String isi) {
        // ⚠️ Sengaja kosong: paket "isi satu kiriman" RetroTK belum diport,
        // dan ARAH FINAL project ini adalah RTK2. Dibiarkan diam supaya
        // klien RetroTK tidak menerima paket setengah jadi.
    }

    @Override
    public void exchangeOpened(User sd, User target) {
        Clif.exchangeOpen(sd, target);
    }

    @Override
    public void exchangeItemOffered(User sd, User target,
                                    org.rtk.common.mmo.Item item, int slotInList) {
        Clif.exchangeAddItem(sd, target, item, slotInList);
    }

    @Override
    public void exchangeGoldOffered(User sd, User target, long gold) {
        Clif.exchangeGold(sd, target, gold);
    }

    @Override
    public void exchangeConfirmed(User sd, User target, boolean completed) {
        String pesan = "Kamu bertukar, dan menyerahkan kepemilikan barangnya.";
        Clif.exchangeMessage(sd, pesan, 5, completed ? 0 : 1);
        if (target != null) {
            Clif.exchangeMessage(target, pesan, 5, completed ? 0 : 1);
        }
    }

    @Override
    public void mapSelectionToPlayer(User sd, String title,
                                     java.util.List<Integer> x0, java.util.List<Integer> y0,
                                     java.util.List<String> names, java.util.List<Integer> ids,
                                     java.util.List<Integer> x1, java.util.List<Integer> y1) {
        Clif.mapSelection(sd, title, x0, y0, names, ids, x1, y1);
    }

    @Override
    public void powerBoardToPlayer(User sd) {
        Clif.sendPowerBoard(sd);
    }

    @Override
    public void boardQuestionsToPlayer(User sd, java.util.List<String> headers,
                                       java.util.List<String> questions,
                                       java.util.List<Integer> inputLines) {
        Clif.sendBoardQuestions(sd, headers, questions, inputLines);
    }

    @Override
    public void guiTextToPlayer(User sd, String text) {
        Clif.guiText(sd, text);
    }

    @Override
    public void paperToPlayer(User sd, String text, int width, int height) {
        Clif.paperPopup(sd, text, width, height);
    }

    @Override
    public void urlToPlayer(User sd, int type, String url) {
        Clif.sendUrl(sd, type, url);
    }

    @Override
    public void playerTimerSet(User sd, int type, long seconds) {
        Clif.sendTimer(sd, type, seconds);
    }

    @Override
    public void playerSpoke(User sd, String text, int type) {
        Clif.scriptSay(sd, text, type);
    }

    @Override
    public void playerMovementLocked(User sd, boolean locked) {
        // ⚠️ 0 mengunci, 1 melepas — terbalik dari dugaan, dan itu di C.
        Clif.blockMovement(sd, locked ? 0 : 1);
    }

    @Override
    public void objectAnimationSeenBy(User viewer, BlockList target,
                                      int animation, int times) {
        Clif.sendAnimationTo(viewer, target, animation, times);
    }

    @Override
    public void playerHealthChanged(User sd, int critical, int percent, int damage) {
        Clif.sendPcHealth(sd, critical, percent, damage);
    }

    @Override
    public void chatLineToPlayer(User sd, int type, long speakerId, String text) {
        Clif.chatSelf(sd, type, speakerId, text);
    }

    @Override
    public void soundPlayed(BlockList at, int sound) {
        Clif.playSound(at, sound);
    }

    @Override
    public void objectSpoke(BlockList speaker, int type, String text) {
        Clif.speak(speaker, type, text);
    }

    @Override
    public void objectActed(BlockList bl, int action, int time, int sound) {
        Clif.sendAction(bl, action, time, sound);
    }

    @Override
    public void objectAppearanceChanged(BlockList bl) {
        Clif.broadcastLook(bl);
    }

    @Override
    public void objectRemoved(BlockList bl) {
        Clif.lookGone(bl);
    }

    @Override
    public void messageToPlayer(User sd, int type, String text) {
        Clif.sendMsg(sd, type, text);
    }

    @Override
    public void npcMoved(Npc nd, MapData map, int fromX, int fromY,
                         int revealX0, int revealY0, int revealX1, int revealY1) {
        // Petak yang baru terlihat digambar DULU, baru perpindahannya —
        // urutan ini ditiru dari npc_move() di C.
        if (revealX1 >= 0 && revealY1 >= 0 && map != null) {
            Clif.npcRevealStrip(nd, map, revealX0, revealY0, revealX1, revealY1);
        }
        Clif.npcMove(nd, fromX, fromY);
    }

    @Override
    public void npcAppearedTo(User viewer, Npc nd) {
        Clif.sendNpcLook(viewer, nd);
    }

    @Override
    public void mobMoved(Mob mb, MapData map, int fromX, int fromY,
                         int revealX0, int revealY0, int revealX1, int revealY1) {
        // Sama seperti NPC: petak baru digambar DULU, baru perpindahannya.
        if (revealX1 >= 0 && revealY1 >= 0 && map != null) {
            Clif.mobRevealStrip(mb, map, revealX0, revealY0, revealX1, revealY1);
        }
        Clif.mobMove(mb, fromX, fromY);
    }

    @Override
    public void mobSpawned(Mob mb) {
        Clif.broadcastLook(mb);
    }

    @Override
    public void floorItemAppeared(FloorItem fl) {
        Clif.broadcastLook(fl);
    }

    @Override
    public void objectThrown(BlockList from, int toX, int toY,
                             int icon, int color, int action) {
        Clif.throwAnimation(from, toX, toY, icon, color, action);
    }

    @Override
    public void scriptDialogReady(User sd, org.rtk.map.script.ScriptPlayer p) {
        Clif.flushDialog(sd, p);
    }

    @Override
    public void npcSaidTo(User sd, long npcId, String text) {
        Clif.sendScriptMes(sd, npcId, text, false, false);
    }

    @Override
    public void playerStepRejected(User sd) {
        Clif.snapBack(sd);
    }

    @Override
    public void playerStepped(User sd, int direction, int fromX, int fromY) {
        Clif.sendWalkConfirm(sd, direction, fromX, fromY);
    }

    @Override
    public void playerStepSeen(User sd, int direction, int fromX, int fromY) {
        Clif.broadcastWalk(sd, direction, fromX, fromY);
    }

    @Override
    public void areaRedrawRequested(User sd, MapData map, int x, int y,
                                    int width, int height, int checksum) {
        Clif.redrawArea(sd, map, x, y, width, height, checksum);
    }

    @Override
    public void playerEquipmentChanged(User sd, int slot) {
        Clif.equipIt(sd, slot);
    }

    @Override
    public void playerEquipmentCleared(User sd, int slot) {
        Clif.unequipIt(sd, slot);
    }

    @Override
    public void mapTilesChanged(User sd) {
        Clif.redrawAround(sd);
    }

    @Override
    public void weatherChanged(User sd, int weather) {
        Clif.sendWeather(sd, weather);
    }

    @Override
    public void groupStatusChanged(User sd) {
        Clif.sendGroupStatus(sd);
    }

    @Override
    public void groupHealthChanged(User sd) {
        Clif.sendGroupHealth(sd);
    }

    @Override
    public void playerSettingsChanged(User sd) {
        // Sengaja kosong: di RetroTK kata setelan menumpang paket status
        // (`clif_sendstatus` offset 22 & 51), yang sudah dikirim pemanggil
        // lewat playerStatusChanged. Tidak ada paket tersendiri untuknya.
    }
}
