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
