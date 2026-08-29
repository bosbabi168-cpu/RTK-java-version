package org.rtk.map;

import java.util.List;

import org.rtk.common.mmo.Item;
import org.rtk.map.data.BlockList;
import org.rtk.map.data.MapData;

/**
 * Menyalurkan tiap peristiwa {@link ClientView} ke <b>seluruh</b>
 * implementasi protokol yang hidup.
 *
 * <h2>Kenapa memanggil semuanya, bukan memilih satu</h2>
 *
 * <p>Pilihan yang tampak wajar — "lihat protokol pemainnya, panggil yang
 * cocok" — tidak bisa dipakai, dan alasannya ada di antarmukanya sendiri:
 * <b>separuh peristiwa tidak punya satu penerima</b>. {@code objectActed},
 * {@code mobSpawned}, {@code floorItemAppeared} dan sembilan lainnya
 * menyiarkan ke semua yang ada di sekitar sebuah benda, dan sekitar itu bisa
 * berisi pemain dari kedua protokol sekaligus. Penyalur ini tidak punya cara
 * membelah siaran seperti itu tanpa mengulang seluruh logika areanya.</p>
 *
 * <p>Jadi arahnya dibalik: <b>tiap implementasi menyaring penerimanya
 * sendiri.</b> Keduanya menerima setiap peristiwa dan mengabaikan pemain
 * yang bukan miliknya. Penyaringnya satu tempat per protokol —
 * {@code Clif.sessionOf} dan {@code Rtk2ClientView.sesi} — sehingga tidak
 * ada jalur yang bisa terlewat.</p>
 *
 * <p>Biayanya satu panggilan method kosong per peristiwa per protokol yang
 * tidak terpakai. Itu murah, dan tidak bergantung pada seberapa hati-hati
 * kode pemanggilnya.</p>
 *
 * <p>⚠️ Kelas ini <b>tidak boleh</b> berisi logika apa pun selain
 * meneruskan. Kalau sebuah method di sini mulai memutuskan sesuatu, itu
 * tanda keputusannya salah tempat.</p>
 */
public final class ProtocolRouter implements ClientView {

    private final List<ClientView> tujuan;

    public ProtocolRouter(ClientView... views) {
        this.tujuan = List.of(views);
    }

    /** Implementasi yang sedang disalurkan — dipakai uji. */
    public List<ClientView> views() {
        return tujuan;
    }

    @Override
    public void playerEnteredWorld(User sd) {
        tujuan.forEach(v -> v.playerEnteredWorld(sd));
    }

    @Override
    public void playerViewRefreshed(User sd) {
        tujuan.forEach(v -> v.playerViewRefreshed(sd));
    }

    @Override
    public void playerMapChanged(User sd) {
        tujuan.forEach(v -> v.playerMapChanged(sd));
    }

    @Override
    public void playerStatusChanged(User sd, int flags) {
        tujuan.forEach(v -> v.playerStatusChanged(sd, flags));
    }

    @Override
    public void playerIdentityChanged(User sd) {
        tujuan.forEach(v -> v.playerIdentityChanged(sd));
    }

    @Override
    public void playerDurationChanged(User sd, int spellId, int seconds, String caster) {
        tujuan.forEach(v -> v.playerDurationChanged(sd, spellId, seconds, caster));
    }

    @Override
    public void playerAetherChanged(User sd, int spellId, int seconds) {
        tujuan.forEach(v -> v.playerAetherChanged(sd, spellId, seconds));
    }

    @Override
    public void playerInventorySlotChanged(User sd, int slot) {
        tujuan.forEach(v -> v.playerInventorySlotChanged(sd, slot));
    }

    @Override
    public void playerInventorySlotCleared(User sd, int slot, int reason) {
        tujuan.forEach(v -> v.playerInventorySlotCleared(sd, slot, reason));
    }

    @Override
    public void playerSpellSlotChanged(User sd, int slot) {
        tujuan.forEach(v -> v.playerSpellSlotChanged(sd, slot));
    }

    @Override
    public void playerProfile(User sd) {
        tujuan.forEach(v -> v.playerProfile(sd));
    }

    @Override
    public void playerTransferred(User sd, String host, int port,
                                  int m, int x, int y) {
        tujuan.forEach(v -> v.playerTransferred(sd, host, port, m, x, y));
    }

    @Override
    public void worldTimeChanged(User sd) {
        tujuan.forEach(v -> v.worldTimeChanged(sd));
    }

    @Override
    public void townListToPlayer(User sd, java.util.List<String> kota) {
        tujuan.forEach(v -> v.townListToPlayer(sd, kota));
    }

    @Override
    public void rankingToPlayer(User sd, String judul, java.util.List<Object[]> baris) {
        tujuan.forEach(v -> v.rankingToPlayer(sd, judul, baris));
    }

    @Override
    public void playerSpellRemoved(User sd, int slot) {
        tujuan.forEach(v -> v.playerSpellRemoved(sd, slot));
    }

    @Override
    public void chatLineToPlayer(User sd, int type, long speakerId, String text) {
        tujuan.forEach(v -> v.chatLineToPlayer(sd, type, speakerId, text));
    }

    @Override
    public void popupToPlayer(User sd, String text) {
        tujuan.forEach(v -> v.popupToPlayer(sd, text));
    }

    @Override
    public void playerCameraChanged(User sd, int x, int y) {
        tujuan.forEach(v -> v.playerCameraChanged(sd, x, y));
    }

    @Override
    public void boardListToPlayer(User sd, int board, int f1, int f2,
                                  List<Clif.BoardEntry> isi) {
        tujuan.forEach(v -> v.boardListToPlayer(sd, board, f1, f2, isi));
    }

    @Override
    public void boardPostToPlayer(User sd, int board, int pos, int bendera,
                                  String penulis, String topik, int bulan,
                                  int hari, String isi) {
        tujuan.forEach(v -> v.boardPostToPlayer(sd, board, pos, bendera,
                penulis, topik, bulan, hari, isi));
    }

    @Override
    public void exchangeOpened(User sd, User target) {
        tujuan.forEach(v -> v.exchangeOpened(sd, target));
    }

    @Override
    public void exchangeItemOffered(User sd, User target, Item item, int slotInList) {
        tujuan.forEach(v -> v.exchangeItemOffered(sd, target, item, slotInList));
    }

    @Override
    public void exchangeGoldOffered(User sd, User target, long gold) {
        tujuan.forEach(v -> v.exchangeGoldOffered(sd, target, gold));
    }

    @Override
    public void exchangeConfirmed(User sd, User target, boolean completed) {
        tujuan.forEach(v -> v.exchangeConfirmed(sd, target, completed));
    }

    @Override
    public void mapSelectionToPlayer(User sd, String title, List<Integer> x0,
                                     List<Integer> y0, List<String> names,
                                     List<Integer> ids, List<Integer> x1,
                                     List<Integer> y1) {
        tujuan.forEach(v -> v.mapSelectionToPlayer(sd, title, x0, y0, names, ids, x1, y1));
    }

    @Override
    public void powerBoardToPlayer(User sd) {
        tujuan.forEach(v -> v.powerBoardToPlayer(sd));
    }

    @Override
    public void boardQuestionsToPlayer(User sd, List<String> headers,
                                       List<String> questions, List<Integer> lines) {
        tujuan.forEach(v -> v.boardQuestionsToPlayer(sd, headers, questions, lines));
    }

    @Override
    public void guiTextToPlayer(User sd, String text) {
        tujuan.forEach(v -> v.guiTextToPlayer(sd, text));
    }

    @Override
    public void paperToPlayer(User sd, String text, int width, int height) {
        tujuan.forEach(v -> v.paperToPlayer(sd, text, width, height));
    }

    @Override
    public void urlToPlayer(User sd, int type, String url) {
        tujuan.forEach(v -> v.urlToPlayer(sd, type, url));
    }

    @Override
    public void playerTimerSet(User sd, int type, long seconds) {
        tujuan.forEach(v -> v.playerTimerSet(sd, type, seconds));
    }

    @Override
    public void playerSpoke(User sd, String text, int type) {
        tujuan.forEach(v -> v.playerSpoke(sd, text, type));
    }

    @Override
    public void playerMovementLocked(User sd, boolean locked) {
        tujuan.forEach(v -> v.playerMovementLocked(sd, locked));
    }

    @Override
    public void objectAnimationSeenBy(User viewer, BlockList target, int anim, int times) {
        tujuan.forEach(v -> v.objectAnimationSeenBy(viewer, target, anim, times));
    }

    @Override
    public void playerHealthChanged(User sd, int critical, int percent, int damage) {
        tujuan.forEach(v -> v.playerHealthChanged(sd, critical, percent, damage));
    }

    @Override
    public void objectSideChanged(BlockList bl) {
        tujuan.forEach(v -> v.objectSideChanged(bl));
    }

    @Override
    public void objectAnimation(BlockList bl, int animation, int times) {
        tujuan.forEach(v -> v.objectAnimation(bl, animation, times));
    }

    @Override
    public void objectAnimationAt(BlockList bl, int animation, int times, int x, int y) {
        tujuan.forEach(v -> v.objectAnimationAt(bl, animation, times, x, y));
    }

    @Override
    public void soundPlayed(BlockList at, int sound) {
        tujuan.forEach(v -> v.soundPlayed(at, sound));
    }

    @Override
    public void objectSpoke(BlockList speaker, int type, String text) {
        tujuan.forEach(v -> v.objectSpoke(speaker, type, text));
    }

    @Override
    public void objectActed(BlockList bl, int action, int time, int sound) {
        tujuan.forEach(v -> v.objectActed(bl, action, time, sound));
    }

    @Override
    public void objectAppearanceChanged(BlockList bl) {
        tujuan.forEach(v -> v.objectAppearanceChanged(bl));
    }

    @Override
    public void objectRemoved(BlockList bl) {
        tujuan.forEach(v -> v.objectRemoved(bl));
    }

    @Override
    public void messageToPlayer(User sd, int type, String text) {
        tujuan.forEach(v -> v.messageToPlayer(sd, type, text));
    }

    @Override
    public void npcMoved(Npc nd, MapData map, int fromX, int fromY,
                         int newX, int newY, int seenX, int seenY) {
        tujuan.forEach(v -> v.npcMoved(nd, map, fromX, fromY, newX, newY, seenX, seenY));
    }

    @Override
    public void npcAppearedTo(User viewer, Npc nd) {
        tujuan.forEach(v -> v.npcAppearedTo(viewer, nd));
    }

    @Override
    public void mobMoved(Mob mb, MapData map, int fromX, int fromY,
                         int newX, int newY, int seenX, int seenY) {
        tujuan.forEach(v -> v.mobMoved(mb, map, fromX, fromY, newX, newY, seenX, seenY));
    }

    @Override
    public void mobSpawned(Mob mb) {
        tujuan.forEach(v -> v.mobSpawned(mb));
    }

    @Override
    public void floorItemAppeared(FloorItem fl) {
        tujuan.forEach(v -> v.floorItemAppeared(fl));
    }

    @Override
    public void objectThrown(BlockList from, int toX, int toY, int icon,
                             int color, int action) {
        tujuan.forEach(v -> v.objectThrown(from, toX, toY, icon, color, action));
    }

    @Override
    public void scriptDialogReady(User sd, org.rtk.map.script.ScriptPlayer p) {
        tujuan.forEach(v -> v.scriptDialogReady(sd, p));
    }

    @Override
    public void npcSaidTo(User sd, long npcId, String text) {
        tujuan.forEach(v -> v.npcSaidTo(sd, npcId, text));
    }

    @Override
    public void playerStepRejected(User sd) {
        tujuan.forEach(v -> v.playerStepRejected(sd));
    }

    @Override
    public void playerStepped(User sd, int direction, int fromX, int fromY) {
        tujuan.forEach(v -> v.playerStepped(sd, direction, fromX, fromY));
    }

    @Override
    public void playerStepSeen(User sd, int direction, int fromX, int fromY) {
        tujuan.forEach(v -> v.playerStepSeen(sd, direction, fromX, fromY));
    }

    @Override
    public void areaRedrawRequested(User sd, MapData map, int x, int y,
                                    int width, int height, int checksum) {
        tujuan.forEach(v -> v.areaRedrawRequested(sd, map, x, y, width, height, checksum));
    }

    @Override
    public void playerEquipmentChanged(User sd, int slot) {
        tujuan.forEach(v -> v.playerEquipmentChanged(sd, slot));
    }

    @Override
    public void playerEquipmentCleared(User sd, int slot) {
        tujuan.forEach(v -> v.playerEquipmentCleared(sd, slot));
    }

    @Override
    public void mapTilesChanged(User sd) {
        tujuan.forEach(v -> v.mapTilesChanged(sd));
    }

    @Override
    public void weatherChanged(User sd, int weather) {
        tujuan.forEach(v -> v.weatherChanged(sd, weather));
    }

    @Override
    public void groupStatusChanged(User sd) {
        tujuan.forEach(v -> v.groupStatusChanged(sd));
    }

    @Override
    public void groupHealthChanged(User sd) {
        tujuan.forEach(v -> v.groupHealthChanged(sd));
    }

    @Override
    public void playerSettingsChanged(User sd) {
        tujuan.forEach(v -> v.playerSettingsChanged(sd));
    }
}
