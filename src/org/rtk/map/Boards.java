package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Papan pesan sisi map server — {@code boards_showposts()} dan
 * {@code boards_readpost()} (map/map.c), plus penjaga hak tulis/hapus.
 *
 * <h2>Kenapa tata letak antar-servernya BUKAN tiruan C</h2>
 *
 * <p>Di C paket 0x3009/0x300A/0x300C mengirim <b>dump struct C mentah</b>
 * ({@code memcpy(&a, ..., sizeof(struct board_show_0))}), sehingga
 * panjangnya bergantung pada padding kompilator. Itu persis alasan blob
 * karakter tidak ditiru byte-per-byte (lihat Peringatan #9): <b>kedua ujung
 * paket ini kode Java kita sendiri</b>, jadi tata letak C bukan kontrak yang
 * harus dipatuhi — dan meniru paddingnya justru rapuh.</p>
 *
 * <p>Karena itu ladangnya ditulis eksplisit di sini dan di
 * {@code charserver/Mapif}, dengan urutan yang sama. Yang tetap ditiru
 * adalah <b>semantiknya</b>: bendera hak akses, penomoran halaman 20
 * kiriman, dan urutan menurun.</p>
 */
public final class Boards {

    private static final Logger log = LogManager.getLogger(Boards.class);

    private Boards() {
    }

    /** BOARD_CAN_WRITE / BOARD_CAN_DEL (map.h). */
    public static final int CAN_WRITE = 1;
    public static final int CAN_DEL = 2;

    /**
     * Nilai khusus {@code board_canwrite}: bukan sekadar "boleh menulis",
     * melainkan "klien harus mengirim paket saat tombol tulis diklik" —
     * dipakai papan yang dijawab skrip (mis. papan pengaduan). Diteruskan
     * apa adanya sebagai bendera, bukan di-OR seperti nilai lain.
     */
    public static final int WRITE_ASK_SCRIPT = 6;

    /** Berapa kiriman per halaman; {@code LIMIT bcount*20, 20} di C. */
    public static final int PER_PAGE = 20;

    /**
     * boards_showposts(): minta daftar isi sebuah papan dari char server.
     *
     * <p>Hak akses diputuskan <b>di sini</b>, bukan di char server:</p>
     * <ul>
     *   <li><b>Papan 0 adalah kotak surat</b>, bukan papan — pemiliknya
     *       selalu boleh menulis dan menghapus.</li>
     *   <li>Papan lain: bila {@code BnmScripted}, kait Lua {@code check}
     *       yang menentukan (skrip menulis {@code boardCanWrite}); bila
     *       tidak, semua orang boleh menulis.</li>
     *   <li>GM level 99 selalu boleh menulis <b>dan</b> menghapus.</li>
     * </ul>
     *
     * @param page halaman ke berapa (0 = terbaru); {@code sd.bcount} di C
     */
    public static boolean show(org.rtk.map.script.ScriptEngine engine, User sd,
                               int board, int page, boolean popup) {
        sd.boardCanWrite = 0;
        sd.boardCanDel = 0;
        sd.board = board;
        sd.boardPage = page;
        sd.boardPopup = popup;

        if (board == 0) {
            sd.boardCanWrite = CAN_WRITE;
            sd.boardCanDel = CAN_DEL;
        } else {
            if (MapServer.boardDb.isScripted(board)) {
                String yname = MapServer.boardDb.scriptNameOf(board);
                if (engine != null && !yname.isEmpty()) {
                    try {
                        engine.doScript(yname, "check", engine.playerRef(sd.scriptPlayer()));
                    } catch (RuntimeException e) {
                        log.error("[BOARD] kait '{}.check' gagal", yname, e);
                    }
                }
            } else {
                sd.boardCanWrite = CAN_WRITE;
            }
            if (sd.status.gmLevel == 99) {
                sd.boardCanWrite = CAN_WRITE;
                sd.boardCanDel = CAN_DEL;
            }
        }

        int flags = 0;
        // ⚠️ Nilai 6 menggantikan seluruh bendera, tidak di-OR. Lihat
        // WRITE_ASK_SCRIPT.
        if (sd.boardCanWrite == WRITE_ASK_SCRIPT) {
            flags = WRITE_ASK_SCRIPT;
        } else if (sd.boardCanWrite != 0) {
            flags |= CAN_WRITE;
        }
        if (sd.boardCanDel != 0) {
            flags |= CAN_DEL;
        }

        return MapIntif.requestBoard(sd, board, page, flags, popup);
    }

    /**
     * Bendera tampilan yang dihitung char server dari hak akses di atas.
     *
     * <p>Dipisah jadi method sendiri karena logikanya berlapis dan mudah
     * salah baca: {@code flags1} bergantung pada <b>popup dan papan
     * sekaligus</b>, bukan salah satunya.</p>
     */
    public static int displayFlags1(int flags, boolean popup, int board) {
        if (flags == WRITE_ASK_SCRIPT) {
            return WRITE_ASK_SCRIPT;
        }
        boolean bolehTulis = (flags & CAN_WRITE) != 0;
        if (popup && board != 0) {
            return bolehTulis ? 2 : 0;
        }
        return bolehTulis ? 3 : 1;
    }

    /** {@code flags2}: 4 untuk kotak surat, 2 untuk papan biasa. */
    public static int displayFlags2(int board) {
        return board == 0 ? 4 : 2;
    }
}
