package org.rtk.map.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;

/**
 * Port of map_read() (map/map.c ~baris 1081-1240): memuat seluruh peta yang
 * menjadi tanggung jawab map server ini.
 *
 * Metadata datang dari tabel MySQL {@code Maps} (disaring
 * {@code MapServer = serverid}), sedangkan geometrinya dari berkas
 * {@code .map} yang ditunjuk kolom {@code MapFile}.
 *
 * Catatan penting yang ditemukan saat menganalisa data: <b>banyak baris
 * `Maps` berbagi berkas geometri yang sama</b> (7.648 baris peta menunjuk
 * hanya 2.640 berkas unik). Karena itu berkas di-cache per nama, sehingga
 * peta yang bentuknya sama tidak dibaca berulang dari disk — tapi setiap
 * peta tetap punya {@link MapData} sendiri karena metadata dan isinya
 * (pemain, mob) berbeda.
 */
public final class MapRegistry {

    private static final Logger log = LogManager.getLogger(MapRegistry.class);

    private final Map<Integer, MapData> maps = new LinkedHashMap<>();
    private int missingFiles;

    /** Folder peta yang dipakai saat memuat — dibutuhkan {@link #reloadGeometry}. */
    private String lastRoot = "maps";

    /** Peta berdasarkan MapId; null bila tidak dimuat di server ini. */
    public MapData get(int mapId) {
        return maps.get(mapId);
    }

    public boolean isLoaded(int mapId) {
        return maps.containsKey(mapId);
    }

    public int size() {
        return maps.size();
    }

    public int missingFiles() {
        return missingFiles;
    }

    /**
     * setMap(): ganti geometri sebuah peta dengan berkas lain saat berjalan.
     *
     * <p>Berkasnya dicari di folder peta lebih dulu, baru apa adanya —
     * skrip menuliskan path relatif terhadap folder kerja server C
     * (mis. {@code "../rtkmaps/Accepted/test/test1.map"}), yang tidak sama
     * dengan tempat berkas peta tinggal di project ini.</p>
     *
     * <p>⚠️ Salinannya <b>selalu pribadi</b>: peta instance tidak boleh
     * berbagi geometri dengan peta lain (Peringatan #74).</p>
     */
    public boolean reloadGeometry(MapData map, String berkas) {
        String nama = Paths.get(berkas).getFileName().toString();
        for (Path calon : new Path[] {
                Paths.get(lastRoot, nama), Paths.get(berkas)}) {
            if (java.nio.file.Files.isRegularFile(calon)) {
                try {
                    map.replaceGeometry(MapFile.load(calon).mutableCopy());
                    return true;
                } catch (java.io.IOException e) {
                    log.error("[MAP] setMap: '{}' gagal dibaca", calon, e);
                    return false;
                }
            }
        }
        log.warn("[MAP] setMap: berkas '{}' tidak ditemukan (dicari juga '{}')",
                berkas, Paths.get(lastRoot, nama));
        return false;
    }

    /** Daftarkan peta yang dibuat langsung — dipakai uji dan peta instance. */
    public void put(MapData m) {
        maps.put(m.id, m);
    }

    /** Cabut sebuah peta dari registry. */
    public void remove(int mapId) {
        maps.remove(mapId);
    }

    public java.util.Collection<MapData> all() {
        return maps.values();
    }

    /** Daftar MapId yang dimuat — dikirim ke char server lewat paket 0x3001. */
    public List<Integer> mapIds() {
        return new ArrayList<>(maps.keySet());
    }

    /**
     * Muat semua peta milik server ini.
     *
     * @param mapRoot folder berisi berkas .map
     * @return jumlah peta yang berhasil dimuat
     */
    public int load(Sql sql, int serverId, String mapRoot) {
        maps.clear();
        missingFiles = 0;
        Path root = Paths.get(mapRoot);
        lastRoot = mapRoot;
        Map<String, MapFile> cache = new HashMap<>();
        long t0 = System.currentTimeMillis();

        int rows = sql.forEachRow(
                "SELECT `MapId`,`MapName`,`MapFile`,`MapBGM`,`MapBGMType`,`MapPvP`,`MapSpells`,`MapLight`,"
                + "`MapWeather`,`MapSweepTime`,`MapChat`,`MapGhosts`,`MapRegion`,`MapIndoor`,`MapWarpout`,"
                + "`MapBind`,`MapReqLvl`,`MapReqPath`,`MapReqMark`,`MapCanSummon`,`MapReqVita`,`MapReqMana`,"
                + "`MapLvlMax`,`MapVitaMax`,`MapManaMax`,`MapRejectMsg`,`MapCanUse`,`MapCanEat`,`MapCanSmoke`,"
                + "`MapCanMount`,`MapCanGroup`,`MapCanEquip` "
                + "FROM `Maps` WHERE `MapServer` = ? ORDER BY `MapId`",
                rs -> {
                    int mapId = rs.getInt("MapId");
                    String file = rs.getString("MapFile");
                    if (file == null || file.isEmpty()) {
                        return;
                    }
                    MapFile geo = cache.get(file);
                    if (geo == null) {
                        Path p = root.resolve(file);
                        if (!Files.isRegularFile(p)) {
                            missingFiles++;
                            if (missingFiles <= 5) {
                                log.warn("[MAP] berkas peta hilang: {} (MapId {})", file, mapId);
                            }
                            return;
                        }
                        try {
                            geo = MapFile.load(p);
                        } catch (IOException | RuntimeException e) {
                            missingFiles++;
                            log.error("[MAP] gagal membaca {} (MapId {}): {}", file, mapId, e.getMessage());
                            return;
                        }
                        cache.put(file, geo);
                    }

                    MapData md = new MapData(mapId, geo);
                    md.mapFile = file;
                    md.title = str(rs.getString("MapName"));
                    md.bgm = rs.getInt("MapBGM");
                    md.bgmType = rs.getInt("MapBGMType");
                    md.pvp = rs.getInt("MapPvP");
                    md.spell = rs.getInt("MapSpells");
                    md.light = rs.getInt("MapLight");
                    md.weather = rs.getInt("MapWeather");
                    md.sweepTime = rs.getLong("MapSweepTime");
                    md.canTalk = rs.getInt("MapChat");
                    md.showGhosts = rs.getInt("MapGhosts");
                    md.region = rs.getInt("MapRegion");
                    md.indoor = rs.getInt("MapIndoor");
                    md.warpout = rs.getInt("MapWarpout");
                    md.bind = rs.getInt("MapBind");
                    md.reqLvl = rs.getLong("MapReqLvl");
                    md.reqPath = rs.getInt("MapReqPath");
                    md.reqMark = rs.getInt("MapReqMark");
                    md.canSummon = rs.getInt("MapCanSummon");
                    md.reqVita = rs.getLong("MapReqVita");
                    md.reqMana = rs.getLong("MapReqMana");
                    md.lvlMax = rs.getLong("MapLvlMax");
                    md.vitaMax = rs.getLong("MapVitaMax");
                    md.manaMax = rs.getLong("MapManaMax");
                    md.rejectMsg = str(rs.getString("MapRejectMsg"));
                    md.canUse = rs.getInt("MapCanUse");
                    md.canEat = rs.getInt("MapCanEat");
                    md.canSmoke = rs.getInt("MapCanSmoke");
                    md.canMount = rs.getInt("MapCanMount");
                    md.canGroup = rs.getInt("MapCanGroup");
                    md.canEquip = rs.getInt("MapCanEquip");
                    maps.put(mapId, md);
                }, serverId);

        long ms = System.currentTimeMillis() - t0;
        if (rows < 0) {
            log.error("[MAP] gagal membaca tabel Maps");
            return 0;
        }
        log.info("[MAP] {} peta dimuat dari {} baris `Maps` ({} berkas geometri unik, {} hilang) dalam {} ms",
                maps.size(), rows, cache.size(), missingFiles, ms);
        return maps.size();
    }

    /**
     * Muat petak portal dari tabel {@code Warps}.
     *
     * Port dari pemuat warp di {@code map/npc.c}. Seperti versi C, baris
     * yang peta asal ATAU tujuannya tidak dimuat di server ini dilewati —
     * portal ke peta milik map server lain baru bisa dipakai setelah
     * perpindahan antar map server diport (roadmap C3).
     *
     * <p>Query sengaja <b>tanpa ORDER BY</b>, sama seperti C. Urutan baris
     * ikut menentukan hasil pada petak yang terdaftar ganda — lihat catatan
     * di {@link MapData#warpAt}. Jangan menambahkan ORDER BY tanpa memutuskan
     * perubahan perilaku itu memang diinginkan.</p>
     *
     * @return jumlah portal yang berhasil didaftarkan
     */
    public int loadWarps(org.rtk.common.Sql sql) {
        long t0 = System.currentTimeMillis();
        // 0=terdaftar, 1=peta ASAL tak dimuat, 2=di luar batas,
        // 3=tujuan di map server lain (tetap didaftarkan)
        int[] stat = new int[4];
        int rows = sql.forEachRow(
                "SELECT `SourceMapId`, `SourceX`, `SourceY`, "
                + "`DestinationMapId`, `DestinationX`, `DestinationY` FROM `Warps`",
                rs -> {
                    int mm = rs.getInt(1);
                    int mx = rs.getInt(2);
                    int my = rs.getInt(3);
                    int tm = rs.getInt(4);
                    int tx = rs.getInt(5);
                    int ty = rs.getInt(6);

                    MapData src = maps.get(mm);
                    MapData dst = maps.get(tm);
                    if (src == null) {
                        stat[1]++;
                        return;
                    }
                    // ⚠️ MENYIMPANG DARI C, DENGAN SENGAJA (R3/C3).
                    // C membuang portal yang peta TUJUANNYA tidak dimuat di
                    // server ini (`npc.c`: `if (!map_isloaded(mm) ||
                    // !map_isloaded(tm)) continue;`). Akibatnya setiap portal
                    // antar map server lenyap saat dimuat — dan jalur
                    // perpindahan antar-server di `pc_warp` jadi kode mati
                    // yang tidak bisa dicapai portal mana pun.
                    // Port ini menyimpannya: yang wajib dimuat hanya peta
                    // ASAL, karena hanya itu yang perlu didaftari petaknya.
                    // Menginjaknya menyerahkan pemain ke server pemilik peta
                    // tujuan (Pc.warp), bukan diam-diam tidak terjadi apa-apa.
                    if (dst == null) {
                        stat[3]++;
                    }
                    if (mx > src.xs - 1 || my > src.ys - 1
                            || (dst != null && (tx > dst.xs - 1 || ty > dst.ys - 1))) {
                        stat[2]++;
                        if (stat[2] <= 5) {
                            log.warn("[MAP] portal di luar batas: {}:{},{} -> {}:{},{}",
                                    mm, mx, my, tm, tx, ty);
                        }
                        return;
                    }
                    src.addWarp(mx, my, tm, tx, ty);
                    stat[0]++;
                });

        if (rows < 0) {
            log.error("[MAP] gagal membaca tabel Warps");
            return 0;
        }
        log.info("[MAP] {} portal dimuat dari {} baris `Warps` ({} peta asal tak dimuat, "
                + "{} di luar batas, {} menuju map server lain) dalam {} ms",
                stat[0], rows, stat[1], stat[2], stat[3], System.currentTimeMillis() - t0);
        return stat[0];
    }

    /**
     * Muat peta langsung dari berkas tanpa database — untuk pengujian dan
     * agar map server tetap bisa hidup saat MySQL belum tersedia.
     */
    public int loadFromFiles(String mapRoot, List<String> files) {
        maps.clear();
        missingFiles = 0;
        int id = 0;
        for (String f : files) {
            Path p = Paths.get(mapRoot).resolve(f);
            try {
                MapData md = new MapData(id, MapFile.load(p));
                md.mapFile = f;
                md.title = f;
                maps.put(id++, md);
            } catch (IOException | RuntimeException e) {
                missingFiles++;
            }
        }
        return maps.size();
    }

    private static String str(String v) {
        return v == null ? "" : v;
    }
}
