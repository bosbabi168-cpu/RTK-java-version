package org.rtk.map.script;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import org.rtk.map.MapServer;
import org.rtk.map.User;
import org.rtk.map.data.MapData;

/**
 * Global yang menyentuh <b>dunia</b>, bukan satu pemain: peta yang bisa
 * diubah saat berjalan, cuaca, klan, lelang, puisi, dan pencarian karakter
 * offline.
 *
 * <h2>Kenapa terpisah dari {@code ScriptEngine}</h2>
 *
 * <p>Semuanya sempat terpasang sebagai <b>stub warn-once</b> — 59 nama yang
 * terdaftar di {@code sl.c} tetapi tidak melakukan apa pun di port ini, dan
 * {@code luaaudit} melaporkannya sebagai "0 celah" karena stub terhitung
 * "terdefinisi" (Peringatan #73). Tiga di antaranya dipakai <b>980x</b>
 * gabungan.</p>
 *
 * <h2>⚠️ Geometri peta DIBAGI antar peta</h2>
 *
 * <p>{@code setTile}/{@code setObject}/{@code setPass} tidak boleh menulis
 * langsung ke berkas petanya: {@code MapRegistry} men-cache satu
 * {@code MapFile} per <b>nama berkas</b>, dan 9.850 peta hanya menunjuk
 * 2.919 berkas. Pemisahannya ada di {@code MapData.setTile} dan kawan-kawan
 * (salin-saat-ditulis); jangan melewatinya (Peringatan #74).</p>
 */
public final class WorldBindings {

    private static final Logger log = LogManager.getLogger(WorldBindings.class);

    /** Papan puisi mentah dan papan puisi terbit — dua id tetap di C. */
    private static final int BOARD_POEM_DRAFT = 19;
    private static final int BOARD_POETRY = 10;

    private WorldBindings() {
    }

    public static void register(ScriptEngine e) {
        peta(e);
        cuaca(e);
        klan(e);
        karakter(e);
        papanDanLelang(e);
        sisanya(e);
    }

    // ==================================================================
    // peta yang bisa diubah saat berjalan
    // ==================================================================

    private static void peta(ScriptEngine e) {
        // settile / setobject / setpass: tulis satu petak lalu kirim ulang
        // petak peta ke semua yang ada di area. Ketiganya tiga baris di C.
        e.set("setTile", a -> ubahPetak(a, MapData::setTile));
        e.set("setObject", a -> ubahPetak(a, MapData::setObj));
        e.set("setPass", a -> ubahPetak(a, MapData::setPass));

        e.set("getMapIsLoaded", a ->
                LuaValue.valueOf(MapServer.world.get(a.optint(1, -1)) != null));

        e.set("getMapUsers", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m == null) {
                return LuaValue.valueOf(0);
            }
            int n = 0;
            for (User u : MapServer.onlineChars.values()) {
                if (u.m == m.id) {
                    n++;
                }
            }
            return LuaValue.valueOf(n);
        });

        e.set("getMapPvP", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            return m == null ? LuaValue.NIL : LuaValue.valueOf(m.pvp);
        });
        e.set("setMapPvP", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m != null) {
                m.pvp = a.optint(2, 0);
            }
            return LuaValue.NONE;
        });

        /*
         * ⚠️ getMapTitle membaca DATABASE, bukan peta yang sedang dimuat —
         * jadi ia mengembalikan judul aslinya walau setMapTitle sudah
         * mengubahnya di memori. Itu di C; jangan "dirapikan" jadi membaca
         * map.title, karena skrip memakainya justru untuk mengembalikan
         * judul asli setelah sebuah event selesai.
         */
        e.set("getMapTitle", a -> {
            String judul = MapServer.sql.queryString(
                    "SELECT `MapName` FROM `Maps` WHERE `MapId` = ?", a.optint(1, -1));
            return judul == null ? LuaValue.NIL : LuaValue.valueOf(judul);
        });
        e.set("setMapTitle", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m != null) {
                m.title = a.optjstring(2, "");
                for (User u : MapServer.onlineChars.values()) {
                    if (u.m == m.id) {
                        MapServer.clientView.playerViewRefreshed(u);
                    }
                }
            }
            return LuaValue.NONE;
        });

        e.set("getMapAttribute", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            return m == null ? LuaValue.NIL : bacaAtribut(m, a.optjstring(2, ""));
        });
        e.set("setMapAttribute", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m != null) {
                tulisAtribut(m, a.optjstring(2, ""), a.arg(3));
            }
            return LuaValue.NONE;
        });

        e.set("setLight", a -> {
            int region = a.optint(1, 0);
            int indoor = a.optint(2, 0);
            int light = a.optint(3, 0);
            for (MapData m : MapServer.world.all()) {
                // ⚠️ Hanya peta yang cahayanya MASIH 0 yang berubah — di C
                // `if (map[x].light == 0)`. Jadi menyalakan lampu dua kali
                // tidak melakukan apa-apa pada putaran kedua, dan peta yang
                // punya cahaya sendiri tidak pernah tertimpa.
                if (m.region == region && m.indoor == indoor && m.light == 0) {
                    m.light = light;
                }
            }
            return LuaValue.NONE;
        });

        e.set("getWarps", a -> {
            LuaTable t = new LuaTable();
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m == null) {
                return t;
            }
            int i = 1;
            for (MapData.Warp w : m.allWarps()) {
                t.set(i++, LuaValue.valueOf(m.id));
                t.set(i++, LuaValue.valueOf(w.x()));
                t.set(i++, LuaValue.valueOf(w.y()));
                t.set(i++, LuaValue.valueOf(w.tm()));
                t.set(i++, LuaValue.valueOf(w.tx()));
                t.set(i++, LuaValue.valueOf(w.ty()));
            }
            return t;
        });

        e.set("setWarps", a -> {
            MapData dari = MapServer.world.get(a.optint(1, -1));
            MapData ke = MapServer.world.get(a.optint(4, -1));
            if (dari == null || ke == null) {
                return LuaValue.FALSE;
            }
            dari.addWarp(a.optint(2, 0), a.optint(3, 0),
                    ke.id, a.optint(5, 0), a.optint(6, 0));
            return LuaValue.TRUE;
        });

        e.set("saveMap", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            String berkas = a.optjstring(2, "");
            if (m == null || berkas.isEmpty()) {
                return LuaValue.FALSE;
            }
            try {
                java.nio.file.Files.write(java.nio.file.Paths.get(berkas),
                        m.geometry().toBytes());
                return LuaValue.TRUE;
            } catch (java.io.IOException ex) {
                log.error("[DUNIA] saveMap('{}') gagal", berkas, ex);
                return LuaValue.FALSE;
            }
        });

        /*
         * setMap(m, berkas, judul, ...) memuat ulang berkas `.map` ke slot
         * peta yang sudah ada. ⚠️ Argumen ke-2 adalah PATH BERKAS, dan di C
         * ia dibuka apa adanya relatif terhadap folder kerja server. Di sini
         * dicari di folder peta lebih dulu supaya skrip tidak perlu tahu
         * dari mana servernya dijalankan.
         */
        e.set("setMap", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            String berkas = a.optjstring(2, "");
            if (m == null || berkas.isEmpty()) {
                return LuaValue.FALSE;
            }
            if (!MapServer.world.reloadGeometry(m, berkas)) {
                return LuaValue.FALSE;
            }
            if (!a.isnil(3)) {
                m.title = a.optjstring(3, m.title);
            }
            m.bgm = a.optint(4, m.bgm);
            m.bgmType = a.optint(5, m.bgmType);
            m.pvp = a.optint(6, m.pvp);
            m.spell = a.optint(7, m.spell);
            m.light = a.optint(8, m.light);
            m.weather = a.optint(9, m.weather);
            m.sweepTime = a.optlong(10, m.sweepTime);
            m.canTalk = a.optint(11, m.canTalk);
            m.showGhosts = a.optint(12, m.showGhosts);
            m.region = a.optint(13, m.region);
            m.indoor = a.optint(14, m.indoor);
            m.warpout = a.optint(15, m.warpout);
            m.bind = a.optint(16, m.bind);
            m.reqLvl = a.optlong(17, m.reqLvl);
            m.reqVita = a.optlong(18, m.reqVita);
            m.reqMana = a.optlong(19, m.reqMana);
            for (User u : MapServer.onlineChars.values()) {
                if (u.m == m.id) {
                    MapServer.clientView.playerViewRefreshed(u);
                }
            }
            return LuaValue.TRUE;
        });

        // --- pengubah peta (tabel MapModifiers) ---
        e.set("getMapModifiers", a -> {
            LuaTable t = new LuaTable();
            int[] i = {1};
            MapServer.sql.forEachRow(
                    "SELECT `ModModifier`,`ModValue` FROM `MapModifiers` WHERE `ModMapId` = ?",
                    rs -> {
                        t.set(i[0]++, LuaValue.valueOf(str(rs.getString("ModModifier"))));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("ModValue")));
                    }, a.optint(1, -1));
            return t;
        });
        e.set("addMapModifier", a -> LuaValue.valueOf(MapServer.sql.update(
                "INSERT INTO `MapModifiers` (`ModMapId`,`ModModifier`,`ModValue`) VALUES (?,?,?)",
                a.optint(1, 0), a.optjstring(2, ""), a.optint(3, 0)) > 0));
        e.set("removeMapModifier", a -> LuaValue.valueOf(MapServer.sql.update(
                "DELETE FROM `MapModifiers` WHERE `ModMapId` = ? AND `ModModifier` = ?",
                a.optint(1, 0), a.optjstring(2, "")) >= 0));
        e.set("removeMapModifierId", a -> LuaValue.valueOf(MapServer.sql.update(
                "DELETE FROM `MapModifiers` WHERE `ModMapId` = ?",
                a.optint(1, 0)) >= 0));
        e.set("getFreeMapModifierId", a -> {
            Integer maks = MapServer.sql.queryInt(
                    "SELECT MAX(`ModMapId`) FROM `MapModifiers`");
            // ⚠️ queryInt mengembalikan null untuk SQL NULL, dan MAX() pada
            // himpunan kosong memang NULL — bukan 0 (Peringatan #44).
            return LuaValue.valueOf(maks == null ? 1 : maks + 1);
        });

        /*
         * getObjectsMap: badannya DIKOMENTARI SELURUHNYA di C — ia tidak
         * pernah mengembalikan apa pun. Diport sebagai tiruan setia, sama
         * seperti addGift/retrieveGift (Peringatan #46). Mengisinya dengan
         * tebakan justru mengubah perilaku.
         */
        e.set("getObjectsMap", a -> LuaValue.NONE);
    }

    /** Bagian bersama setTile/setObject/setPass. */
    private static LuaValue ubahPetak(org.luaj.vm2.Varargs a, PetakSetter setter) {
        MapData m = MapServer.world.get(a.optint(1, -1));
        if (m == null) {
            return LuaValue.NONE;
        }
        int x = a.optint(2, 0);
        int y = a.optint(3, 0);
        setter.set(m, x, y, a.optint(4, 0));
        // map_foreachinarea(sl_updatepeople, ...): yang di sekitar petak itu
        // menerima petak petanya lagi.
        m.foreachInArea(x, y, org.rtk.map.data.BlockList.Type.PC, bl -> {
            if (bl instanceof User u) {
                MapServer.clientView.mapTilesChanged(u);
            }
        });
        return LuaValue.NONE;
    }

    private interface PetakSetter {
        void set(MapData m, int x, int y, int v);
    }

    private static LuaValue bacaAtribut(MapData m, String nama) {
        return switch (nama) {
            case "xmax" -> LuaValue.valueOf(m.xs - 1);
            case "ymax" -> LuaValue.valueOf(m.ys - 1);
            case "mapTitle" -> LuaValue.valueOf(m.title);
            case "mapFile" -> LuaValue.valueOf(m.mapFile);
            case "bgm" -> LuaValue.valueOf(m.bgm);
            case "bgmType" -> LuaValue.valueOf(m.bgmType);
            case "pvp" -> LuaValue.valueOf(m.pvp);
            case "spell" -> LuaValue.valueOf(m.spell);
            case "light" -> LuaValue.valueOf(m.light);
            case "weather" -> LuaValue.valueOf(m.weather);
            case "sweepTime" -> LuaValue.valueOf((double) m.sweepTime);
            case "canTalk" -> LuaValue.valueOf(m.canTalk);
            case "showGhosts" -> LuaValue.valueOf(m.showGhosts);
            case "region" -> LuaValue.valueOf(m.region);
            case "indoor" -> LuaValue.valueOf(m.indoor);
            case "warpOut" -> LuaValue.valueOf(m.warpout);
            case "bind" -> LuaValue.valueOf(m.bind);
            case "reqLvl" -> LuaValue.valueOf((double) m.reqLvl);
            case "reqVita" -> LuaValue.valueOf((double) m.reqVita);
            case "reqMana" -> LuaValue.valueOf((double) m.reqMana);
            case "reqPath" -> LuaValue.valueOf(m.reqPath);
            case "reqMark" -> LuaValue.valueOf(m.reqMark);
            case "maxLvl" -> LuaValue.valueOf((double) m.lvlMax);
            case "maxVita" -> LuaValue.valueOf((double) m.vitaMax);
            case "maxMana" -> LuaValue.valueOf((double) m.manaMax);
            case "canSummon" -> LuaValue.valueOf(m.canSummon);
            case "canUse" -> LuaValue.valueOf(m.canUse);
            case "canEat" -> LuaValue.valueOf(m.canEat);
            case "canSmoke" -> LuaValue.valueOf(m.canSmoke);
            case "canMount" -> LuaValue.valueOf(m.canMount);
            case "canGroup" -> LuaValue.valueOf(m.canGroup);
            default -> LuaValue.NIL;
        };
    }

    private static void tulisAtribut(MapData m, String nama, LuaValue v) {
        switch (nama) {
            case "mapTitle" -> m.title = v.tojstring();
            case "mapFile" -> m.mapFile = v.tojstring();
            case "bgm" -> m.bgm = v.toint();
            case "bgmType" -> m.bgmType = v.toint();
            case "pvp" -> m.pvp = v.toint();
            case "spell" -> m.spell = v.toint();
            case "light" -> m.light = v.toint();
            case "weather" -> m.weather = v.toint();
            case "sweepTime" -> m.sweepTime = (long) v.todouble();
            case "canTalk" -> m.canTalk = v.toint();
            case "showGhosts" -> m.showGhosts = v.toint();
            case "region" -> m.region = v.toint();
            case "indoor" -> m.indoor = v.toint();
            case "warpOut" -> m.warpout = v.toint();
            case "bind" -> m.bind = v.toint();
            case "reqLvl" -> m.reqLvl = (long) v.todouble();
            case "reqVita" -> m.reqVita = (long) v.todouble();
            case "reqMana" -> m.reqMana = (long) v.todouble();
            case "reqPath" -> m.reqPath = v.toint();
            case "reqMark" -> m.reqMark = v.toint();
            case "maxLvl" -> m.lvlMax = (long) v.todouble();
            case "maxVita" -> m.vitaMax = (long) v.todouble();
            case "maxMana" -> m.manaMax = (long) v.todouble();
            case "canSummon" -> m.canSummon = v.toint();
            case "canUse" -> m.canUse = v.toint();
            case "canEat" -> m.canEat = v.toint();
            case "canSmoke" -> m.canSmoke = v.toint();
            case "canMount" -> m.canMount = v.toint();
            case "canGroup" -> m.canGroup = v.toint();
            default -> log.debug("[DUNIA] atribut peta '{}' tidak dikenal", nama);
        }
    }

    // ==================================================================
    // cuaca
    // ==================================================================

    private static void cuaca(ScriptEngine e) {
        e.set("getWeather", a -> {
            int region = a.optint(1, 0);
            int indoor = a.optint(2, 0);
            for (MapData m : MapServer.world.all()) {
                if (m.region == region && m.indoor == indoor) {
                    return LuaValue.valueOf(m.weather);
                }
            }
            return LuaValue.NIL;
        });

        e.set("getWeatherM", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            return m == null ? LuaValue.NIL : LuaValue.valueOf(m.weather);
        });

        e.set("setWeather", a -> {
            int region = a.optint(1, 0);
            int indoor = a.optint(2, 0);
            int cuaca = a.optint(3, 0);
            for (MapData m : MapServer.world.all()) {
                if (m.region == region && m.indoor == indoor && cuacaBebas(m)) {
                    pasangCuaca(m, cuaca);
                }
            }
            return LuaValue.NONE;
        });

        e.set("setWeatherM", a -> {
            MapData m = MapServer.world.get(a.optint(1, -1));
            if (m != null && cuacaBebas(m)) {
                pasangCuaca(m, a.optint(2, 0));
            }
            return LuaValue.NONE;
        });
    }

    /**
     * ⚠️ Cuaca <b>musiman</b> tidak boleh menimpa cuaca yang dipasang skrip.
     *
     * <p>Penjaganya registry per-peta {@code artificial_weather_timer}:
     * selama capnya masih di masa depan, peta itu dilewati. Cap yang sudah
     * lewat <b>dibersihkan di sini</b>, bukan oleh timer tersendiri — jadi
     * memanggil {@code setWeather} juga yang memulihkan peta yang cuaca
     * buatannya sudah kedaluwarsa.</p>
     */
    private static boolean cuacaBebas(MapData m) {
        long sekarang = System.currentTimeMillis() / 1000;
        int cap = reg(m).getOrDefault("artificial_weather_timer", 0);
        if (cap <= sekarang && cap > 0) {
            reg(m).put("artificial_weather_timer", 0);
            return true;
        }
        return cap == 0;
    }

    private static void pasangCuaca(MapData m, int cuaca) {
        m.weather = cuaca;
        for (User u : MapServer.onlineChars.values()) {
            if (u.m == m.id) {
                MapServer.clientView.weatherChanged(u, cuaca);
            }
        }
    }

    // ==================================================================
    // klan & jalur — seluruhnya SQL
    // ==================================================================

    private static void klan(ScriptEngine e) {
        e.set("getClanName", a -> {
            String n = MapServer.sql.queryString(
                    "SELECT `ClnName` FROM `Clans` WHERE `ClnId` = ?", a.optint(1, 0));
            return n == null ? LuaValue.valueOf(0) : LuaValue.valueOf(n);
        });
        e.set("setClanName", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Clans` SET `ClnName` = ? WHERE `ClnId` = ?",
                a.optjstring(2, ""), a.optint(1, 0)) >= 0));

        e.set("getClanTribute", a -> {
            Integer v = MapServer.sql.queryInt(
                    "SELECT `ClnTribute` FROM `Clans` WHERE `ClnId` = ?", a.optint(1, 0));
            return LuaValue.valueOf(v == null ? 0 : v);
        });
        e.set("setClanTribute", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Clans` SET `ClnTribute` = ? WHERE `ClnId` = ?",
                a.optlong(2, 0), a.optint(1, 0)) >= 0));
        e.set("addClanTribute", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Clans` SET `ClnTribute` = `ClnTribute` + ? WHERE `ClnId` = ?",
                a.optlong(2, 0), a.optint(1, 0)) >= 0));

        e.set("getClanBankSlots", a -> {
            Integer v = MapServer.sql.queryInt(
                    "SELECT `ClnBankSlots` FROM `Clans` WHERE `ClnId` = ?", a.optint(1, 0));
            return LuaValue.valueOf(v == null ? 0 : v);
        });
        e.set("setClanBankSlots", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Clans` SET `ClnBankSlots` = ? WHERE `ClnId` = ?",
                a.optint(2, 0), a.optint(1, 0)) >= 0));

        e.set("getClanRoster", a -> {
            LuaTable t = new LuaTable();
            int[] i = {1};
            MapServer.sql.forEachRow(
                    "SELECT `ChaId`,`ChaClnRank` FROM `Character` WHERE `ChaClnId` = ?",
                    rs -> {
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("ChaId")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("ChaClnRank")));
                    }, a.optint(1, 0));
            return t;
        });

        // ⚠️ Bergabung menyetel rank 1 dan MENGOSONGKAN gelar; keluar
        // menyetel klan 0, rank 0, dan gelar kosong. Ketiga ladang selalu
        // bergerak bersama di C — menyisakan salah satunya membuat gelar
        // klan lama menempel pada pemain yang sudah keluar.
        e.set("addClanMember", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Character` SET `ChaClnId` = ?, `ChaClanTitle` = '', "
                + "`ChaClnRank` = '1' WHERE `ChaId` = ?",
                a.optint(2, 0), a.optlong(1, 0)) >= 0));
        e.set("removeClanMember", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Character` SET `ChaClnId` = '0', `ChaClanTitle` = '', "
                + "`ChaClnRank` = '0' WHERE `ChaId` = ?", a.optlong(1, 0)) >= 0));
        e.set("updateClanMemberRank", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Character` SET `ChaClnRank` = ? WHERE `ChaId` = ?",
                a.optint(2, 0), a.optlong(1, 0)) >= 0));
        e.set("updateClanMemberTitle", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Character` SET `ChaClanTitle` = ? WHERE `ChaId` = ?",
                a.optjstring(2, ""), a.optlong(1, 0)) >= 0));

        e.set("addPathMember", a -> LuaValue.valueOf(MapServer.sql.update(
                "UPDATE `Character` SET `ChaPthId` = ?, `ChaPthRank` = '0' WHERE `ChaId` = ?",
                a.optint(2, 0), a.optlong(1, 0)) >= 0));
        e.set("removePathMember", a -> {
            long id = a.optlong(1, 0);
            // ⚠️ "Keluar dari subpath" berarti KEMBALI KE JALUR DASARNYA,
            // bukan jadi tanpa jalur. Di C kelas barunya diambil dari
            // pemainnya sendiri bila online, atau dari database bila tidak.
            User u = MapServer.userById(id);
            int kelas = u != null ? MapServer.classDb.pathOf(u.status.charClass) : 0;
            if (u == null) {
                Integer p = MapServer.sql.queryInt(
                        "SELECT `ChaPthId` FROM `Character` WHERE `ChaId` = ?", id);
                kelas = p == null ? 0 : MapServer.classDb.pathOf(p);
            }
            return LuaValue.valueOf(MapServer.sql.update(
                    "UPDATE `Character` SET `ChaPthId` = ?, `ChaPthRank` = '0' "
                    + "WHERE `ChaId` = ?", kelas, id) >= 0);
        });
    }

    // ==================================================================
    // karakter offline
    // ==================================================================

    private static void karakter(ScriptEngine e) {
        /*
         * getOfflineID menerima id ATAU nama, dan hasilnya kebalikan
         * masukannya: diberi angka ia mengembalikan nama, diberi teks ia
         * mengembalikan id. Satu nama untuk dua arah — ada di C.
         */
        e.set("getOfflineID", a -> {
            if (a.isnumber(1)) {
                String n = MapServer.sql.queryString(
                        "SELECT `ChaName` FROM `Character` WHERE `ChaId` = ?",
                        a.optlong(1, 0));
                return n == null ? LuaValue.FALSE : LuaValue.valueOf(n);
            }
            Integer id = MapServer.sql.queryInt(
                    "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?",
                    a.optjstring(1, ""));
            return id == null ? LuaValue.FALSE : LuaValue.valueOf(id);
        });

        e.set("setOfflinePlayerRegistry", a -> {
            long id;
            if (a.isnumber(1)) {
                id = a.optlong(1, 0);
            } else {
                Integer cari = MapServer.sql.queryInt(
                        "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?",
                        a.optjstring(1, ""));
                if (cari == null) {
                    return LuaValue.FALSE;
                }
                id = cari;
            }
            String kunci = a.optjstring(2, "");
            long nilai = a.optlong(3, 0);
            // ⚠️ Kolomnya `RegIdentifier`, bukan `RegKey`. Salah nama di
            // sini lolos audit SQL lama tanpa satu pun tanda — itulah yang
            // membuat audit tahap 1 diperbaiki jadi memakai EXPLAIN.
            MapServer.sql.update("DELETE FROM `Registry` WHERE `RegChaId` = ? "
                    + "AND `RegIdentifier` = ?", id, kunci);
            return LuaValue.valueOf(MapServer.sql.update(
                    "INSERT INTO `Registry` (`RegChaId`,`RegIdentifier`,`RegValue`) "
                    + "VALUES (?,?,?)", id, kunci, nilai) >= 0);
        });
    }

    // ==================================================================
    // papan, puisi, lelang
    // ==================================================================

    private static void papanDanLelang(ScriptEngine e) {
        e.set("selectBulletinBoard", a -> {
            // ⚠️ X-nya dicocokkan dalam RENTANG ±1, Y persis. Papan digambar
            // selebar dua petak, jadi berdiri di sebelahnya tetap membukanya.
            int m = a.optint(1, 0);
            int x = a.optint(2, 0);
            Integer id = MapServer.sql.queryInt(
                    "SELECT `BoardId` FROM `BoardLocations` WHERE `BoardM` = ? "
                    + "AND `BoardX` BETWEEN ? AND ? AND `BoardY` = ? LIMIT 1",
                    m, x - 1, x + 1, a.optint(3, 0));
            return LuaValue.valueOf(id == null ? 0 : id);
        });

        e.set("addToBoard", a -> {
            int papan = a.optint(1, 0);
            Integer maks = MapServer.sql.queryInt(
                    "SELECT MAX(`BrdPosition`) FROM `Boards` WHERE `BrdBnmId` = ?", papan);
            var kini = java.time.LocalDate.now();
            // Penulisnya "SYSTEM" — kiriman ini datang dari skrip, bukan pemain.
            return LuaValue.valueOf(MapServer.sql.update(
                    "INSERT INTO `Boards` (`BrdBnmId`,`BrdChaName`,`BrdTopic`,"
                    + "`BrdPost`,`BrdMonth`,`BrdDay`,`BrdPosition`) "
                    + "VALUES (?,'SYSTEM',?,?,?,?,?)",
                    papan, a.optjstring(2, ""), a.optjstring(3, ""),
                    kini.getMonthValue(), kini.getDayOfMonth(),
                    (maks == null ? 0 : maks) + 1) >= 0);
        });

        /*
         * ⚠️ Kolomnya `BrdHighlighted`, bukan warna — dan argumen keduanya
         * `BrdPosition` (nomor urut kiriman di papan itu), bukan `BrdId`.
         * Keduanya sempat salah dan lolos audit SQL lama; yang menangkapnya
         * audit EXPLAIN yang baru.
         */
        e.set("setPostColor", a -> {
            MapServer.sql.update(
                    "UPDATE `Boards` SET `BrdHighlighted` = ? WHERE `BrdBnmId` = ? "
                    + "AND `BrdPosition` = ?",
                    a.optint(3, 0), a.optint(1, 0), a.optint(2, 0));
            return LuaValue.NONE;
        });

        e.set("getPoems", a -> {
            LuaTable t = new LuaTable();
            int[] i = {1};
            MapServer.sql.forEachRow(
                    "SELECT `BrdId`,`BrdTopic`,`BrdChaId` FROM `Boards` WHERE `BrdBnmId` = ?",
                    rs -> {
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("BrdId")));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("BrdChaId")));
                        t.set(i[0]++, LuaValue.valueOf(str(rs.getString("BrdTopic"))));
                    }, BOARD_POEM_DRAFT);
            return t;
        });

        e.set("clearPoems", a -> LuaValue.valueOf(MapServer.sql.update(
                "DELETE FROM `Boards` WHERE `BrdBnmId` = ?", BOARD_POEM_DRAFT) >= 0));

        /*
         * ⚠️ "Menyalin" puisi sebenarnya MEMINDAHKANNYA: barisnya di-UPDATE
         * ke papan puisi terbit, bukan disalin. Namanya menyesatkan, dan
         * kalau ditiru sebagai salinan sungguhan, papan draf akan menumpuk
         * puisi yang sudah terbit.
         */
        e.set("copyPoemToPoetry", a -> {
            Integer maks = MapServer.sql.queryInt(
                    "SELECT MAX(`BrdPosition`) FROM `Boards` WHERE `BrdBnmId` = ?",
                    BOARD_POETRY);
            var kini = java.time.LocalDate.now();
            return LuaValue.valueOf(MapServer.sql.update(
                    "UPDATE `Boards` SET `BrdBnmId` = ?, `BrdChaName` = ?, "
                    + "`BrdPosition` = ?, `BrdMonth` = ?, `BrdDay` = ? WHERE `BrdId` = ?",
                    BOARD_POETRY, a.optjstring(2, ""), (maks == null ? 0 : maks) + 1,
                    kini.getMonthValue(), kini.getDayOfMonth(), a.optint(1, 0)) >= 0);
        });

        e.set("getAuctions", a -> {
            LuaTable t = new LuaTable();
            int[] i = {1};
            MapServer.sql.forEachRow(
                    "SELECT `AuctionId`,`AuctionChaId`,`AuctionItmId`,`AuctionItmAmount`,"
                    + "`AuctionItmDurability`,`AuctionPrice`,`AuctionItmEngrave`,"
                    + "`AuctionItmTimer`,`AuctionItmCustomIcon`,`AuctionItmCustomIconColor`,"
                    + "`AuctionItmCustomLook`,`AuctionItmCustomLookColor`,"
                    + "`AuctionItmProtected`,`AuctionBidding` FROM `Auctions`",
                    rs -> {
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionId")));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionChaId")));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionItmId")));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionItmAmount")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmDurability")));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionPrice")));
                        t.set(i[0]++, LuaValue.valueOf(str(rs.getString("AuctionItmEngrave"))));
                        t.set(i[0]++, LuaValue.valueOf((double) rs.getLong("AuctionItmTimer")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmCustomIcon")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmCustomIconColor")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmCustomLook")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmCustomLookColor")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionItmProtected")));
                        t.set(i[0]++, LuaValue.valueOf(rs.getInt("AuctionBidding")));
                    });
            return t;
        });

        e.set("listAuction", a -> LuaValue.valueOf(MapServer.sql.update(
                "INSERT INTO `Auctions` (`AuctionChaId`,`AuctionItmId`,`AuctionItmAmount`,"
                + "`AuctionItmDurability`,`AuctionPrice`,`AuctionItmEngrave`,"
                + "`AuctionItmTimer`,`AuctionItmCustomIcon`,`AuctionItmCustomIconColor`,"
                + "`AuctionItmCustomLook`,`AuctionItmCustomLookColor`,"
                + "`AuctionItmProtected`,`AuctionBidding`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                a.optlong(1, 0), a.optlong(2, 0), a.optlong(3, 0), a.optint(4, 0),
                a.optlong(5, 0), a.optjstring(6, ""), a.optlong(7, 0),
                a.optint(8, 0), a.optint(9, 0), a.optint(10, 0), a.optint(11, 0),
                a.optint(12, 0), a.optint(13, 0)) >= 0));

        e.set("removeAuction", a -> LuaValue.valueOf(MapServer.sql.update(
                "DELETE FROM `Auctions` WHERE `AuctionId` = ?", a.optlong(1, 0)) >= 0));
    }

    // ==================================================================
    // sisanya
    // ==================================================================

    private static void sisanya(ScriptEngine e) {
        /*
         * ⚠️ guitext GLOBAL berbeda dari method player:guitext().
         * Yang ini menyiarkan ke SELURUH PETA, dan dengan
         * (type == -1, m == 0) ke SELURUH SERVER — itulah yang dipakai
         * pengumuman event (Peringatan #75).
         */
        e.set("guitext", a -> {
            int type = a.optint(1, 0);
            int m = a.optint(2, 0);
            String teks = a.optjstring(3, "");
            for (User u : MapServer.onlineChars.values()) {
                if ((type == -1 && m == 0) || u.m == m) {
                    MapServer.clientView.guiTextToPlayer(u, teks);
                }
            }
            return LuaValue.NONE;
        });

        e.set("getXPforLevel", a -> {
            int path = a.optint(1, 0);
            // ⚠️ Argumen pertama boleh JALUR atau KELAS: nilai di atas 5
            // diterjemahkan dulu jadi jalur dasarnya. Tanpa itu tiap subpath
            // menjawab 0.
            if (path > 5) {
                path = MapServer.classDb.pathOf(path);
            }
            return LuaValue.valueOf((double) MapServer.classDb.level(path, a.optint(2, 0)));
        });

        e.set("getSpellLevel", a ->
                LuaValue.valueOf(MapServer.spellDb.levelOf(a.optjstring(1, ""))));

        e.set("getWisdomStarMultiplier", a -> {
            Double v = MapServer.sql.queryDouble(
                    "SELECT `WSMultiplier` FROM `WisdomStar`");
            return LuaValue.valueOf(v == null ? 0 : v);
        });
        e.set("setWisdomStarMultiplier", a -> {
            MapServer.sql.update("UPDATE `WisdomStar` SET `WSMultiplier` = ?",
                    a.optdouble(1, 0));
            return LuaValue.NONE;
        });

        e.set("getSetItems", a -> {
            LuaTable t = new LuaTable();
            // Selalu LIMA pasang, terisi atau tidak — bentuknya tetap supaya
            // skrip bisa menyapunya tanpa memeriksa panjang.
            for (int i = 1; i <= 10; i++) {
                t.set(i, LuaValue.valueOf(0));
            }
            MapServer.sql.forEachRow(
                    "SELECT `ItmSetItem1`,`ItmSetItem1Amount`,`ItmSetItem2`,"
                    + "`ItmSetItem2Amount`,`ItmSetItem3`,`ItmSetItem3Amount`,"
                    + "`ItmSetItem4`,`ItmSetItem4Amount`,`ItmSetItem5`,"
                    + "`ItmSetItem5Amount` FROM `ItemSets` WHERE `ItmSetId` = ? LIMIT 1",
                    rs -> {
                        for (int i = 0; i < 5; i++) {
                            t.set(i * 2 + 1, LuaValue.valueOf(
                                    (double) rs.getLong("ItmSetItem" + (i + 1))));
                            t.set(i * 2 + 2, LuaValue.valueOf(
                                    (double) rs.getLong("ItmSetItem" + (i + 1) + "Amount")));
                        }
                    }, a.optlong(1, 0));
            return t;
        });

        e.set("getMobAttributes", a -> {
            var db = MapServer.mobs.type(a.optlong(1, 0));
            if (db == null) {
                return LuaValue.NIL;
            }
            LuaTable t = new LuaTable();
            t.set(1, LuaValue.valueOf((double) db.vita));
            t.set(2, LuaValue.valueOf(db.baseArmor));
            t.set(3, LuaValue.valueOf((double) db.exp));
            t.set(4, LuaValue.valueOf(db.might));
            t.set(5, LuaValue.valueOf(db.will));
            t.set(6, LuaValue.valueOf(db.grace));
            t.set(7, LuaValue.valueOf(db.look));
            t.set(8, LuaValue.valueOf(db.lookColor));
            t.set(9, LuaValue.valueOf(db.level));
            t.set(10, LuaValue.valueOf(db.name));
            t.set(11, LuaValue.valueOf(db.yname));
            return t;
        });

        e.set("addMob", a -> LuaValue.valueOf(MapServer.sql.update(
                "INSERT INTO `Spawns" + MapServer.serverId + "` (`SpnMapId`,`SpnX`,`SpnY`,"
                + "`SpnMobId`,`SpnLastDeath`,`SpnStartTime`,`SpnEndTime`,`SpnMobIdReplace`) "
                + "VALUES (?,?,?,?,0,25,25,0)",
                a.optint(1, 0), a.optint(2, 0), a.optint(3, 0), a.optlong(4, 0)) >= 0));

        /*
         * sendMeta menyuruh seluruh klien memuat ulang berkas meta. Itu
         * konsep RetroTK sepenuhnya (C2), dan klien sendiri tidak akan
         * membacanya — jadi diport sebagai tiruan yang tidak melakukan apa
         * pun, dengan catatan ini alih-alih diam.
         */
        e.set("sendMeta", a -> {
            log.debug("[DUNIA] sendMeta() dilewati: berkas meta konsep RetroTK (Trek C2)");
            return LuaValue.NONE;
        });
    }

    /** Registry per-peta, tempat penjaga cuaca buatan disimpan. */
    private static java.util.Map<String, Integer> reg(MapData m) {
        var e = MapServer.scriptEngine;
        return e == null ? new java.util.HashMap<>() : e.mapReg(m.id);
    }

    private static String str(String s) {
        return s == null ? "" : s;
    }
}
