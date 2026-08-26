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
}
