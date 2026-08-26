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
}
