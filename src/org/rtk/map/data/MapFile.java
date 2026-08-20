package org.rtk.map.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pembaca berkas peta `.map` dari `rtkmaps/Accepted/`.
 *
 * Port dari pembacaan peta di `map.c` (map_read, ~baris 1185-1220). Format
 * berkasnya sederhana dan seluruhnya big-endian:
 *
 * <pre>
 *   offset 0 : uint16 xs        lebar peta (petak)
 *   offset 2 : uint16 ys        tinggi peta (petak)
 *   offset 4 : xs*ys x { uint16 tile, uint16 pass, uint16 obj }
 * </pre>
 *
 * Jadi ukuran berkas selalu {@code 4 + xs*ys*6} byte — terverifikasi cocok
 * pada seluruh 3.544 berkas di `Accepted/` tanpa pengecualian.
 *
 * Arti tiap kanal:
 * <ul>
 *   <li>{@code tile} — id petak lantai, rujukan ke tileset milik klien.</li>
 *   <li>{@code pass} — penghalang statis: 0 bisa dilewati, bukan-0 tembok.
 *       ({@code map_canmove()} di C mengembalikan 1 = TIDAK bisa lewat bila
 *       nilai ini bukan nol.) Di berkas nilainya hanya 0 atau 1; saat
 *       runtime server C memakai ulang ladang ini untuk menandai objek yang
 *       sedang menempati petak.</li>
 *   <li>{@code obj} — lapisan objek/dekorasi di atas lantai, 0 berarti kosong.</li>
 * </ul>
 *
 * Metadata peta (nama, BGM, PvP, cahaya, cuaca, batas level, dsb.) TIDAK ada
 * di berkas ini — semuanya dari tabel MySQL `Maps`, yang menunjuk ke berkas
 * lewat kolom `MapFile`.
 *
 * Nilai disimpan sebagai {@code short} agar setara dengan {@code unsigned
 * short} di C; seluruh pengakses sudah memakai topeng {@code & 0xFFFF}.
 */
public final class MapFile {

    /** Ukuran header: xs + ys, masing-masing uint16. */
    public static final int HEADER_BYTES = 4;

    /** Jumlah byte per petak: tile + pass + obj, masing-masing uint16. */
    public static final int BYTES_PER_TILE = 6;

    private final String name;
    private final int xs;
    private final int ys;
    private final short[] tile;
    private final short[] pass;
    private final short[] obj;

    private MapFile(String name, int xs, int ys, short[] tile, short[] pass, short[] obj) {
        this.name = name;
        this.xs = xs;
        this.ys = ys;
        this.tile = tile;
        this.pass = pass;
        this.obj = obj;
    }

    /**
     * Muat satu berkas `.map`.
     *
     * @throws IOException bila berkas tidak terbaca
     * @throws IllegalArgumentException bila ukurannya tidak sesuai header
     */
    public static MapFile load(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        return parse(path.getFileName().toString(), raw);
    }

    /** Versi yang bekerja atas isi berkas yang sudah dibaca. */
    public static MapFile parse(String name, byte[] raw) {
        if (raw.length < HEADER_BYTES) {
            throw new IllegalArgumentException(name + ": berkas terlalu kecil (" + raw.length + " byte)");
        }
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        int xs = buf.getShort() & 0xFFFF;
        int ys = buf.getShort() & 0xFFFF;

        long expected = (long) HEADER_BYTES + (long) xs * ys * BYTES_PER_TILE;
        if (expected != raw.length) {
            throw new IllegalArgumentException(name + ": ukuran tidak cocok — xs=" + xs
                    + " ys=" + ys + " seharusnya " + expected + " byte, nyatanya " + raw.length);
        }

        int n = xs * ys;
        short[] tile = new short[n];
        short[] pass = new short[n];
        short[] obj = new short[n];
        for (int i = 0; i < n; i++) {
            tile[i] = buf.getShort();
            pass[i] = buf.getShort();
            obj[i] = buf.getShort();
        }
        return new MapFile(name, xs, ys, tile, pass, obj);
    }

    public String name() {
        return name;
    }

    public int width() {
        return xs;
    }

    public int height() {
        return ys;
    }

    public int tileCount() {
        return xs * ys;
    }

    private int index(int x, int y) {
        if (x < 0 || y < 0 || x >= xs || y >= ys) {
            throw new IndexOutOfBoundsException("(" + x + "," + y + ") di luar peta " + xs + "x" + ys);
        }
        return x + y * xs;
    }

    /** Id petak lantai. */
    public int tile(int x, int y) {
        return tile[index(x, y)] & 0xFFFF;
    }

    /** Penghalang statis; 0 = bisa dilewati. Setara read_pass() di C. */
    public int pass(int x, int y) {
        return pass[index(x, y)] & 0xFFFF;
    }

    /** Lapisan objek; 0 = kosong. Setara read_obj() di C. */
    public int obj(int x, int y) {
        return obj[index(x, y)] & 0xFFFF;
    }

    /** Kebalikan dari map_canmove(): true bila petak bisa diinjak. */
    public boolean walkable(int x, int y) {
        return pass(x, y) == 0;
    }

    /** Jumlah petak yang terhalang. */
    public int blockedCount() {
        int n = 0;
        for (short p : pass) {
            if ((p & 0xFFFF) != 0) {
                n++;
            }
        }
        return n;
    }

    /**
     * Gambar peta sebagai teks: '.' bisa dilewati, '#' tembok, 'o' ada objek
     * di atas petak yang bisa dilewati. Berguna untuk memeriksa dengan mata
     * bahwa hasil pembacaan masuk akal.
     */
    public String toAscii(int maxW, int maxH) {
        int w = Math.min(xs, maxW);
        int h = Math.min(ys, maxH);
        StringBuilder sb = new StringBuilder((w + 1) * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!walkable(x, y)) {
                    sb.append('#');
                } else if (obj(x, y) != 0) {
                    sb.append('o');
                } else {
                    sb.append('.');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return name + " " + xs + "x" + ys + " (" + tileCount() + " petak, "
                + blockedCount() + " terhalang)";
    }
}
