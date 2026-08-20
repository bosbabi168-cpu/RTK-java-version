package org.rtk.map.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Port of struct map_data (map/map.h) — satu peta yang sedang aktif di map
 * server: geometri dari berkas {@code .map}, metadata dari tabel MySQL
 * {@code Maps}, dan indeks spasial isinya.
 *
 * <h3>Indeks spasial</h3>
 * Sama seperti versi C, peta dibagi menjadi blok {@link #BLOCK_SIZE}×{@link
 * #BLOCK_SIZE} petak. Setiap blok menyimpan daftar benda yang ada di
 * dalamnya, sehingga pencarian "siapa saja di sekitar sini" hanya menyentuh
 * beberapa blok, bukan seluruh peta. Di C daftar itu berupa linked list
 * ({@code bl->next/prev}); di sini memakai {@link ArrayList} per blok.
 *
 * Pemain dan mob disimpan pada indeks terpisah ({@code block} dan
 * {@code block_mob} di C), karena banyak operasi hanya menyangkut salah
 * satunya.
 */
public final class MapData {

    /** Sisi satu blok indeks, dalam petak. Setara BLOCK_SIZE di map.h. */
    public static final int BLOCK_SIZE = 8;

    /** Lebar area pandang klien, dalam petak (AREAX_SIZE). */
    public static final int AREA_X = 18;

    /** Tinggi area pandang klien, dalam petak (AREAY_SIZE). */
    public static final int AREA_Y = 16;

    // ---- identitas & metadata (tabel `Maps`) ----
    public final int id;
    public String title = "";
    public String mapFile = "";
    public String rejectMsg = "";
    public int bgm;
    public int bgmType;
    public int pvp;
    public int spell;
    public int light;
    public int weather;
    public int region;
    public int indoor;
    public int warpout;
    public int bind;
    public int canTalk;
    public int showGhosts;
    public long sweepTime;
    public long reqLvl;
    public long reqVita;
    public long reqMana;
    public long lvlMax;
    public long vitaMax;
    public long manaMax;
    public int reqMark;
    public int reqPath;
    public int canSummon;
    public int canUse;
    public int canEat;
    public int canSmoke;
    public int canMount;
    public int canGroup;
    public int canEquip;

    // ---- geometri (berkas .map) ----
    private final MapFile geometry;
    public final int xs;
    public final int ys;
    private final int bxs;
    private final int bys;

    // ---- indeks spasial ----
    private final List<BlockList>[] blocks;    // pemain, npc, barang
    private final List<BlockList>[] blocksMob; // mob

    /** Jumlah pemain yang sedang berada di peta ini (map[m].user di C). */
    public int users;

    /**
     * Satu petak portal: berdiri di ({@link #x},{@link #y}) memindahkan
     * pemain ke peta {@code tm} pada ({@code tx},{@code ty}).
     * Setara {@code struct warp_list} di map.h.
     */
    public record Warp(int x, int y, int tm, int tx, int ty) {
    }

    /**
     * Portal, diindeks per blok 8x8 seperti {@code map[m].warp[]} di C.
     * Dibuat malas: sebagian besar peta tidak punya portal sama sekali.
     */
    private List<Warp>[] warps;

    @SuppressWarnings("unchecked")
    public MapData(int id, MapFile geometry) {
        this.id = id;
        this.geometry = geometry;
        this.xs = geometry.width();
        this.ys = geometry.height();
        this.bxs = (xs + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.bys = (ys + BLOCK_SIZE - 1) / BLOCK_SIZE;
        this.blocks = new List[bxs * bys];
        this.blocksMob = new List[bxs * bys];
    }

    public MapFile geometry() {
        return geometry;
    }

    // ------------------------------------------------------------------
    // geometri
    // ------------------------------------------------------------------

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < xs && y < ys;
    }

    /**
     * Setara kebalikan map_canmove(): true bila petak boleh diinjak.
     * Hanya memeriksa penghalang statis dari berkas peta — benda yang
     * sedang menempati petak diperiksa terpisah lewat {@link #objectsAt}.
     */
    public boolean walkable(int x, int y) {
        return inBounds(x, y) && geometry.walkable(x, y);
    }

    public int tile(int x, int y) {
        return geometry.tile(x, y);
    }

    public int obj(int x, int y) {
        return geometry.obj(x, y);
    }

    // ------------------------------------------------------------------
    // indeks spasial (map_addblock / map_delblock / map_foreachinarea)
    // ------------------------------------------------------------------

    private int blockIndex(int x, int y) {
        return (x / BLOCK_SIZE) + (y / BLOCK_SIZE) * bxs;
    }

    private List<BlockList>[] indexFor(BlockList bl) {
        return bl.type == BlockList.Type.MOB ? blocksMob : blocks;
    }

    /** map_addblock(): daftarkan benda pada petak tempatnya berdiri. */
    public boolean addBlock(BlockList bl) {
        if (!inBounds(bl.x, bl.y)) {
            return false;
        }
        if (bl.onMap) {
            return false; // di C: "bl->prev != NULL"
        }
        bl.m = id;
        bl.bx = bl.x / BLOCK_SIZE;
        bl.by = bl.y / BLOCK_SIZE;

        List<BlockList>[] idx = indexFor(bl);
        int i = blockIndex(bl.x, bl.y);
        if (idx[i] == null) {
            idx[i] = new ArrayList<>(4);
        }
        idx[i].add(bl);
        bl.onMap = true;
        if (bl.type == BlockList.Type.PC) {
            users++;
        }
        return true;
    }

    /** map_delblock(): cabut benda dari indeks. */
    public boolean delBlock(BlockList bl) {
        if (!bl.onMap) {
            return false;
        }
        List<BlockList>[] idx = indexFor(bl);
        int i = bl.bx + bl.by * bxs;
        if (i >= 0 && i < idx.length && idx[i] != null) {
            idx[i].remove(bl);
        }
        bl.onMap = false;
        if (bl.type == BlockList.Type.PC && users > 0) {
            users--;
        }
        return true;
    }

    /** Pindahkan benda ke petak lain, memperbarui indeks bila ganti blok. */
    public boolean moveBlock(BlockList bl, int nx, int ny) {
        if (!inBounds(nx, ny)) {
            return false;
        }
        int nbx = nx / BLOCK_SIZE;
        int nby = ny / BLOCK_SIZE;
        if (bl.onMap && (nbx != bl.bx || nby != bl.by)) {
            delBlock(bl);
            bl.x = nx;
            bl.y = ny;
            return addBlock(bl);
        }
        bl.x = nx;
        bl.y = ny;
        return true;
    }

    /**
     * Daftarkan satu petak portal. Port dari pemuat {@code Warps} di
     * npc.c: daftarnya diindeks per blok, bukan per petak.
     */
    @SuppressWarnings("unchecked")
    public void addWarp(int x, int y, int tm, int tx, int ty) {
        if (!inBounds(x, y)) {
            return;
        }
        if (warps == null) {
            warps = new List[bxs * bys];
        }
        int b = (x / BLOCK_SIZE) + (y / BLOCK_SIZE) * bxs;
        if (warps[b] == null) {
            warps[b] = new ArrayList<>(4);
        }
        warps[b].add(new Warp(x, y, tm, tx, ty));
    }

    /**
     * Portal tepat di petak ini, atau null. Meniru penelusuran di
     * {@code clif_parsewalk}: cari dalam blok yang memuat petak tersebut.
     */
    public Warp warpAt(int x, int y) {
        if (warps == null || !inBounds(x, y)) {
            return null;
        }
        List<Warp> list = warps[(x / BLOCK_SIZE) + (y / BLOCK_SIZE) * bxs];
        if (list == null) {
            return null;
        }
        for (Warp w : list) {
            if (w.x == x && w.y == y) {
                return w;
            }
        }
        return null;
    }

    /** Jumlah portal terdaftar di peta ini. */
    public int warpCount() {
        if (warps == null) {
            return 0;
        }
        int n = 0;
        for (List<Warp> l : warps) {
            if (l != null) {
                n += l.size();
            }
        }
        return n;
    }

    /** Benda apa saja yang tepat berada di petak ini (kedua indeks). */
    public List<BlockList> objectsAt(int x, int y) {
        List<BlockList> out = new ArrayList<>(2);
        if (!inBounds(x, y)) {
            return out;
        }
        int i = blockIndex(x, y);
        collectAt(blocks[i], x, y, out);
        collectAt(blocksMob[i], x, y, out);
        return out;
    }

    private static void collectAt(List<BlockList> list, int x, int y, List<BlockList> out) {
        if (list == null) {
            return;
        }
        for (BlockList b : list) {
            if (b.x == x && b.y == y) {
                out.add(b);
            }
        }
    }

    /**
     * map_foreachinarea(): jalankan aksi untuk setiap benda dalam area
     * pandang klien di sekitar (x,y).
     *
     * Batas areanya meniru versi C persis: x-9..x+9 dan y-8..y+8, lalu
     * <b>digeser</b> (bukan sekadar dipotong) bila menembus tepi peta,
     * supaya lebar area tetap penuh selama petanya memungkinkan.
     *
     * @param type null berarti semua jenis
     */
    public void foreachInArea(int x, int y, BlockList.Type type, Consumer<BlockList> action) {
        int x0 = x - 9;
        int y0 = y - 8;
        int x1 = x + 9;
        int y1 = y + 8;

        if (x0 < 0) {
            x1 += -x0;
            x0 = 0;
            if (x1 >= xs) {
                x1 = xs - 1;
            }
        }
        if (y0 < 0) {
            y1 += -y0;
            y0 = 0;
            if (y1 >= ys) {
                y1 = ys - 1;
            }
        }
        if (x1 >= xs) {
            x0 -= x1 - xs + 1;
            x1 = xs - 1;
            if (x0 < 0) {
                x0 = 0;
            }
        }
        if (y1 >= ys) {
            y0 -= y1 - ys + 1;
            y1 = ys - 1;
            if (y0 < 0) {
                y0 = 0;
            }
        }

        for (int by = y0 / BLOCK_SIZE; by <= y1 / BLOCK_SIZE; by++) {
            for (int bx = x0 / BLOCK_SIZE; bx <= x1 / BLOCK_SIZE; bx++) {
                int i = bx + by * bxs;
                if (i < 0 || i >= blocks.length) {
                    continue;
                }
                scan(blocks[i], x0, y0, x1, y1, type, action);
                scan(blocksMob[i], x0, y0, x1, y1, type, action);
            }
        }
    }

    private static void scan(List<BlockList> list, int x0, int y0, int x1, int y1,
                             BlockList.Type type, Consumer<BlockList> action) {
        if (list == null) {
            return;
        }
        // salin dulu: aksi boleh memindahkan/menghapus benda
        for (BlockList b : list.toArray(new BlockList[0])) {
            if (b.x >= x0 && b.x <= x1 && b.y >= y0 && b.y <= y1
                    && (type == null || b.type == type)) {
                action.accept(b);
            }
        }
    }

    /** Jumlah benda terdaftar di seluruh peta (untuk pemeriksaan). */
    public int blockCount() {
        int n = 0;
        for (List<BlockList> l : blocks) {
            if (l != null) {
                n += l.size();
            }
        }
        for (List<BlockList> l : blocksMob) {
            if (l != null) {
                n += l.size();
            }
        }
        return n;
    }

    @Override
    public String toString() {
        return "MapData{" + id + " '" + title + "' " + xs + "x" + ys
                + ", blok " + bxs + "x" + bys + ", isi " + blockCount() + ", pemain " + users + "}";
    }
}
