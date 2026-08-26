package org.rtk.map.data;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;

/**
 * Port of board_db.c — daftar papan pesan ({@code BoardNames}) dan judul
 * papan ({@code BoardTitles}).
 *
 * <p>Dua tabel dengan peran berbeda dan nama yang mirip:</p>
 * <ul>
 *   <li><b>{@code BoardNames}</b> — <i>papan</i>-nya sendiri: nama tampilan,
 *       syarat level, apakah aksesnya diatur skrip. Inilah yang dimaksud
 *       {@code showBoard(id)}.</li>
 *   <li><b>{@code BoardTitles}</b> — <i>gelar</i> yang muncul di depan nama
 *       penulis pada daftar kiriman ("Prajurit Budi"). Hanya dipakai untuk
 *       menyusun teksnya.</li>
 * </ul>
 */
public final class BoardDb {

    private static final Logger log = LogManager.getLogger(BoardDb.class);

    /** Satu papan. {@code scripted} berarti aksesnya diputuskan skrip Lua. */
    public record Board(int id, String name, String yname, int level,
                        int gmLevel, int path, int clan, boolean scripted,
                        int sort) {
    }

    private final Map<Integer, Board> byId = new HashMap<>();
    private final Map<String, Integer> idByName = new HashMap<>();
    private final Map<Integer, String> titleById = new HashMap<>();

    /** boarddb_search(): papan menurut idnya, atau null. */
    public Board board(int id) {
        return byId.get(id);
    }

    /** boarddb_name(): nama tampilan papan; "" bila tak dikenal. */
    public String nameOf(int id) {
        Board b = byId.get(id);
        return b == null ? "" : b.name();
    }

    /** boarddb_yname(): nama skrip papan, dipakai memanggil kait `check`. */
    public String scriptNameOf(int id) {
        Board b = byId.get(id);
        return b == null ? "" : b.yname();
    }

    /** boarddb_script(): true bila akses papan ini diputuskan skrip. */
    public boolean isScripted(int id) {
        Board b = byId.get(id);
        return b != null && b.scripted();
    }

    /** boarddb_id(): id papan dari namanya; 0 bila tak dikenal. */
    public int idOf(String name) {
        return name == null ? 0 : idByName.getOrDefault(name.toLowerCase(), 0);
    }

    /** bn_name(): gelar penulis menurut idnya; "" bila tak ada. */
    public String titleOf(int id) {
        return titleById.getOrDefault(id, "");
    }

    public int count() {
        return byId.size();
    }

    /** boarddb_read() + bn_read(): muat kedua tabel. */
    public int load(Sql sql) {
        byId.clear();
        idByName.clear();
        titleById.clear();

        int rows = sql.forEachRow(
                "SELECT `BnmId`,`BnmDescription`,`BnmLevel`,`BnmGMLevel`,`BnmPthId`,"
                + "`BnmClnId`,`BnmScripted`,`BnmIdentifier`,`BnmSortOrder`"
                + " FROM `BoardNames`",
                rs -> {
                    Board b = new Board(
                            rs.getInt("BnmId"),
                            str(rs.getString("BnmDescription")),
                            str(rs.getString("BnmIdentifier")),
                            rs.getInt("BnmLevel"),
                            rs.getInt("BnmGMLevel"),
                            rs.getInt("BnmPthId"),
                            rs.getInt("BnmClnId"),
                            rs.getInt("BnmScripted") != 0,
                            rs.getInt("BnmSortOrder"));
                    byId.put(b.id(), b);
                    if (!b.name().isEmpty()) {
                        idByName.putIfAbsent(b.name().toLowerCase(), b.id());
                    }
                    if (!b.yname().isEmpty()) {
                        idByName.putIfAbsent(b.yname().toLowerCase(), b.id());
                    }
                });
        if (rows < 0) {
            log.error("[BOARD] gagal membaca tabel BoardNames");
            return 0;
        }

        int titles = sql.forEachRow(
                "SELECT `BtlId`,`BtlDescription` FROM `BoardTitles`",
                rs -> titleById.put(rs.getInt("BtlId"), str(rs.getString("BtlDescription"))));
        if (titles < 0) {
            log.error("[BOARD] gagal membaca tabel BoardTitles");
        }

        log.info("[BOARD] {} papan dan {} gelar dimuat", byId.size(), titleById.size());
        return byId.size();
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
