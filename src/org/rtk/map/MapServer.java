package org.rtk.map;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Config;
import org.rtk.common.Core;
import org.rtk.common.NetServer;
import org.rtk.common.ServerLog;
import org.rtk.common.Session;
import org.rtk.common.Sql;
import org.rtk.common.TimerSystem;


/**
 * SKELETON port of map/map.c and map/intif.c.
 *
 * The original map server is ~35.000 lines of C (movement, combat, items,
 * mobs, NPCs, magic, boards, ...) plus an 11.000-line Lua binding (sl.c)
 * that runs the 900+ gameplay scripts in rtklua/. That part is NOT ported
 * yet — see the project README for the roadmap.
 *
 * What this skeleton already does, protocol-compatible with the C servers:
 *  - reads conf/map.conf and conf/inter.conf
 *  - connects and authenticates to the char server (0x3000 / 0x3800)
 *  - registers its map list (0x3001), read from the `Maps` table when a
 *    database is available, otherwise map id 0
 *  - accepts routed player logins (0x3802) and acknowledges them (0x3002)
 *    so the login flow completes end-to-end
 *  - listens on map_port and logs incoming client connections
 */
public final class MapServer {

    private static final Logger log = LogManager.getLogger(MapServer.class);

    public static int serverId = 0;

    // defaults from rtk-server.properties; conf/*.conf overrides
    public static String mapIpS = "127.0.0.1";
    public static int mapIp;
    public static int mapPort = org.rtk.common.Props.getInt("map.port", 2001);

    public static String charIpS = "127.0.0.1";
    public static int charPort = org.rtk.common.Props.getInt("char.port", 2005);

    private static final long RECONNECT_MS =
            org.rtk.common.Props.getInt("inter.reconnect_seconds", 10) * 1000L;
    public static String charId = "";
    public static String charPw = "";

    public static String sqlId = "";
    public static String sqlPw = "";
    public static String sqlDb = "";
    public static String sqlIp = "";
    public static int sqlPort;

    public static int charFd;
    public static int mapFd;

    /** Folder berkas .map; nama dari tabel `Maps` dicari relatif ke sini. */
    public static String mapPath = org.rtk.common.Props.get("map.path", "maps");

    /** Folder berkas data teks (level_db.txt, guide_db.txt). */
    public static String dbPath = org.rtk.common.Props.get("db.path", "db");

    public static int luaEnable = org.rtk.common.Props.getInt("lua.enable", 1);
    public static String luaPath = org.rtk.common.Props.get("lua.path", "luascript");
    public static org.rtk.map.script.ScriptEngine scriptEngine;

    /** Pemain yang sedang online, dikunci fd klien. */
    public static final java.util.Map<Integer, User> onlineChars =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static final Sql sql = new Sql();

    /** NPC di server ini + indeks id global (padanan `id_db` di C). */
    public static final NpcRegistry npcs = new NpcRegistry();

    /** Jenis mob + mob yang hidup di server ini. */
    public static final MobRegistry mobs = new MobRegistry();

    /**
     * Barang yang tergeletak di petak peta (BL_ITEM). Tidak dimuat dari
     * database — isinya lahir dan lenyap sepanjang server hidup.
     */
    public static final FloorItemRegistry floorItems = new FloorItemRegistry();

    /** Tampilan barang (itemdb_look / itemdb_lookcolor). */
    public static final org.rtk.map.data.ItemDb itemDb = new org.rtk.map.data.ItemDb();

    /** Klan beserta bank bersamanya (tabel `Clans` + `ClanBanks`). */
    public static final org.rtk.map.data.ClanDb clanDb = new org.rtk.map.data.ClanDb();

    /** Papan pesan: nama papan (BoardNames) dan gelar penulis (BoardTitles). */
    public static final org.rtk.map.data.BoardDb boardDb = new org.rtk.map.data.BoardDb();

    /** Nama mantra -> id (magicdb_id). */
    public static final org.rtk.map.data.SpellDb spellDb = new org.rtk.map.data.SpellDb();

    /** Tabel pengalaman per path, dari db/level_db.txt. */
    public static final org.rtk.map.data.ClassDb classDb = new org.rtk.map.data.ClassDb();

    /**
     * Jembatan logika permainan -&gt; klien. Tukar isinya untuk mengganti
     * protokol; logika permainan tidak perlu diubah sama sekali.
     *
     * <p>⚠️ Kode logika <b>jangan</b> memanggil {@code Clif.*} langsung —
     * lewat sini. Lihat {@link ClientView} untuk alasannya.</p>
     */
    /**
     * Lapisan protokol arah keluar — <b>kedua</b> implementasi sekaligus.
     *
     * <p>Bukan "pilih salah satu": tiap implementasi menyaring penerimanya
     * sendiri, karena separuh peristiwa menyiarkan ke sekitar sebuah benda
     * yang bisa berisi pemain dari kedua protokol. Lihat
     * {@link ProtocolRouter}.</p>
     */
    public static ClientView clientView =
            new ProtocolRouter(new RetroTkClientView(), new Rtk2ClientView());

    /**
     * fd yang sudah memperkenalkan diri lewat RTK2, menunggu datanya tiba
     * dari char server.
     *
     * <p>Perlu ada karena perkenalan dan lahirnya {@code User} terpisah oleh
     * perjalanan bolak-balik ke char server — protokolnya sudah diketahui
     * saat bingkai pertama datang, pemainnya belum.</p>
     */
    static final java.util.Set<Integer> rtk2Fds =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Jembatan klien -&gt; logika permainan: apa yang <b>diminta</b> pemain.
     *
     * <p>Pasangan masuk dari {@link #clientView}. Protokol baru cukup
     * menulis pembaca yang memanggil method-method di {@link ClientCommands};
     * isinya — {@link MapCommands} — tidak perlu disentuh.</p>
     *
     * <p>⚠️ Lapisan protokol memanggil ini; lapisan logika
     * <b>mengimplementasikannya</b>. Arahnya kebalikan dari
     * {@code clientView}, dan itu yang paling mudah tertukar.</p>
     */
    public static ClientCommands commands = new MapCommands();

    /** Lapisan jaringan + timer milik map server sendiri. */
    public static final NetServer net = new NetServer("map");
    public static final TimerSystem timers = new TimerSystem();
    private static Core core;
    static final List<Integer> loadedMaps = new ArrayList<>();

    /** Dunia: seluruh peta milik server ini beserta isinya. */
    public static final org.rtk.map.data.MapRegistry world = new org.rtk.map.data.MapRegistry();

    private MapServer() {
    }

    /** inet_addr(): dotted quad to s_addr-style int (first octet lowest). */
    static int inetAddr(String ip) {
        String[] p = ip.split("\\.");
        if (p.length != 4) {
            return 0;
        }
        return (Integer.parseInt(p[0]) & 0xFF)
                | ((Integer.parseInt(p[1]) & 0xFF) << 8)
                | ((Integer.parseInt(p[2]) & 0xFF) << 16)
                | ((Integer.parseInt(p[3]) & 0xFF) << 24);
    }

    /**
     * Port map server pertama; server ke-N memakai {@code PORT_MAP_DASAR + N}
     * (R3/C3).
     *
     * <p>⚠️ Aturan ini diambil apa adanya dari {@code clif_transfer}, yang
     * memetakan id server 0/1/2 ke port 2001/2002/2003 secara <b>hardcode</b>.
     * Selama itu belum ada di konfigurasi, dua map server hanya bisa
     * bertetangga di host yang sama dengan port berurutan.</p>
     */
    public static final int PORT_MAP_DASAR = 2001;

    /** Nama kota dari baris {@code town:} di map.conf ({@code towns[]} di C). */
    public static final java.util.List<String> towns = new java.util.ArrayList<>();

    static void configRead(String cfgFile) {
        Config.read(cfgFile, (key, value) -> {
            switch (key.toLowerCase()) {
                case "map_ip": mapIpS = value; mapIp = inetAddr(value); break;
                case "map_port": mapPort = Integer.parseInt(value); break;
                case "char_ip": charIpS = value; break;
                case "char_port": charPort = Integer.parseInt(value); break;
                case "char_id": charId = value; break;
                case "char_pw": charPw = value; break;
                // ⚠️ `start_point` milik char.conf, tetapi K3-lanjutan membuat
                // karakter DARI PROSES MAP (CharDb.newChar dengan kolam map
                // server). Tanpa membacanya di sini ketiga statik di
                // CharServer tetap 0 dan tiap karakter baru lahir di petak
                // (0,0) peta 0 — sudut Kugnae, bukan start_point (30 Agu 2026).
                case "start_point": {
                    String[] parts = value.split(",");
                    if (parts.length == 3) {
                        org.rtk.charserver.CharServer.startM = Integer.parseInt(parts[0].trim());
                        org.rtk.charserver.CharServer.startX = Integer.parseInt(parts[1].trim());
                        org.rtk.charserver.CharServer.startY = Integer.parseInt(parts[2].trim());
                    }
                    break;
                }
                case "serverid": serverId = Integer.parseInt(value); break;
                case "map_path": mapPath = value; break;
                case "db_path": dbPath = value; break;
                case "lua_enable": luaEnable = Integer.parseInt(value); break;
                case "lua_path": luaPath = value; break;
                // map_town_add(): daftar kota, urut sesuai berkas (R1).
                case "town": towns.add(value.trim()); break;
                case "map_log": ServerLog.setLogfile(value); break;
                case "dump_log": ServerLog.setDmpfile(value); break;
                case "sql_ip": sqlIp = value; break;
                case "sql_port": sqlPort = Integer.parseInt(value); break;
                case "sql_id": sqlId = value; break;
                case "sql_pw": sqlPw = value; break;
                case "sql_db": sqlDb = value; break;
                default: break;
            }
        });
    }

    /**
     * map_read(): muat seluruh peta milik server ini — metadata dari tabel
     * `Maps` dan geometri dari berkas .map di {@link #mapPath}.
     *
     * Bila database tidak tersedia, server tetap hidup dengan daftar peta
     * kosong; kelemahan itu dilaporkan supaya tidak terlihat seperti sukses.
     */
    static void loadMaps() {
        loadedMaps.clear();
        int n = world.load(sql, serverId, mapPath);
        if (n == 0) {
            log.warn("[MAP] tidak ada peta yang dimuat untuk ServerId {} "
                    + "(database belum siap, atau kolom MapServer tidak cocok)", serverId);
        }
        loadedMaps.addAll(world.mapIds());
        if (world.missingFiles() > 0) {
            log.warn("[MAP] {} berkas peta tidak ditemukan di {}", world.missingFiles(), mapPath);
        }
        if (n > 0) {
            world.loadWarps(sql);
            npcs.load(sql, serverId, world);
            itemDb.load(sql);
            spellDb.load(sql);
            mobs.loadTypes(sql);
            mobs.useIdIndex(npcs);   // mob ikut indeks id global (map_addiddb)
            floorItems.useIdIndex(npcs);   // barang lantai juga (map_additem)
            classDb.loadPaths(sql);        // kelas -> jalur, untuk powerBoard
            boardDb.load(sql);             // papan pesan + gelar penulis
            clanDb.load(sql);              // klan; bank-nya dimuat saat dipakai
            mobs.loadSpawns(sql, serverId, world);
        }
        classDb.load(dbPath);
    }

    /** map_id2sd(): pemain online dengan id itu, atau null. */
    public static User userById(long id) {
        for (User u : onlineChars.values()) {
            if (u.id == id || u.status.id == id) {
                return u;
            }
        }
        return null;
    }

    /** map_name2sd(): pemain online dengan nama itu (abai besar-kecil), atau null. */
    public static User userByName(String name) {
        if (name == null) {
            return null;
        }
        for (User u : onlineChars.values()) {
            if (u.status.name.equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    /**
     * map_id2bl(): cari benda dari id blok. Indeks idnya kebetulan tinggal
     * di {@link NpcRegistry} — namanya menyesatkan, isinya NPC <b>dan</b>
     * mob. Pemain dicari terpisah karena tidak masuk indeks itu.
     */
    /**
     * map_id2name(): nama karakter dari id-nya.
     *
     * <p>Pemain yang sedang online dijawab dari memori; sisanya ditanyakan
     * ke tabel {@code Character}. Di C selalu ke database — di sini jalur
     * memori didahulukan karena paket inventaris memanggilnya untuk
     * <b>setiap barang bertuan</b> saat pemain masuk.</p>
     *
     * <p>Id 0 berarti tanpa pemilik; C mengembalikan {@code "None"} untuk
     * itu, tetapi pemanggilnya ({@code clif_sendadditem}) sudah memeriksa
     * nol lebih dulu sehingga teks itu tidak pernah terkirim. Di sini
     * dikembalikan string kosong supaya perbedaannya tidak bisa bocor.</p>
     */
    public static String charName(long id) {
        if (id == 0) {
            return "";
        }
        for (User u : onlineChars.values()) {
            if (u.id == id || u.status.id == id) {
                return u.status.name;
            }
        }
        String nama = sql.queryString(
                "SELECT `ChaName` FROM `Character` WHERE `ChaId` = ?", id);
        return nama == null ? "" : nama;
    }

    public static org.rtk.map.data.BlockList blockById(long id) {
        if (npcs != null) {
            org.rtk.map.data.BlockList bl = npcs.byId(id);
            if (bl != null) {
                return bl;
            }
        }
        for (User u : onlineChars.values()) {
            if (u.id == id || u.status.id == id) {
                return u;
            }
        }
        return null;
    }

    /**
     * map_foreachinarea(..., SAMEMAP, ...): seluruh benda sejenis di satu
     * peta. Dipakai skrip lewat {@code getObjectsInMap}.
     */
    public static java.util.List<Object> objectsInMap(int m, int type) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        var map = world.get(m);
        if (map == null) {
            return out;
        }
        int flags = type <= 0 ? org.rtk.map.data.BlockList.BL_ALL : type;

        if ((flags & org.rtk.map.data.BlockList.BL_PC) != 0) {
            for (User u : onlineChars.values()) {
                if (u.m == m) {
                    out.add(u);
                }
            }
        }
        if ((flags & org.rtk.map.data.BlockList.BL_NPC) != 0) {
            for (Npc nd : npcs.all()) {
                if (nd.m == m) {
                    out.add(nd);
                }
            }
        }
        if ((flags & org.rtk.map.data.BlockList.BL_MOB) != 0) {
            for (Mob mb : mobs.all()) {
                if (mb.m == m && mb.isAlive()) {
                    out.add(mb);
                }
            }
        }
        return out;
    }

    /** check_connect_char(): (re)establish the char-server link. */
    /**
     * Satu tik durasi mantra untuk <b>semua</b> pemain online.
     *
     * <p>Salinan daftarnya diambil dulu karena kait skrip
     * ({@code uncast}, {@code while_cast}) boleh membuat pemain keluar
     * dunia, dan itu mengubah {@code onlineChars} di tengah sapuan.</p>
     */
    static int duraTick() {
        if (scriptEngine == null) {
            return 0;
        }
        for (User sd : new java.util.ArrayList<>(onlineChars.values())) {
            try {
                Durations.tick(scriptEngine, sd);
                // pc.c:648 — darah grup ikut disegarkan tiap tik selama
                // pemain bergrup; pemain sendirian langsung pulang.
                Groups.detak(sd);
            } catch (RuntimeException e) {
                log.error("[DURASI] tik gagal untuk '{}'", sd.status.name, e);
            }
        }
        return 0;
    }

    static int checkConnectChar() {
        if (charFd <= 0 || net.session(charFd) == null) {
            log.info("Attempt to connect to char-server...");
            charFd = net.makeConnection(charIpS, charPort);
            if (charFd <= 0) {
                charFd = 0;
                return 0;
            }
            Session s = net.session(charFd);
            s.funcParse = MapIntif::intifParse;
            s.wfifoW(0, 0x3000);
            s.wfifoStringFixed(2, charId, 32);
            s.wfifoStringFixed(34, charPw, 32);
            s.wfifoL(66, mapIp);
            s.wfifoW(70, mapPort);
            s.wfifoSet(72);
        }
        return 0;
    }

    /**
     * clif_parse(): paket dari klien permainan.
     *
     * Setiap paket berbentuk 0xAA + panjang big-endian + opcode. Sebelum
     * pemain terautentikasi ({@code session_data} masih kosong), hanya
     * opcode 0x10 yang dilayani — persis seperti versi C.
     */
    /**
     * Loop paket klien — <b>dua protokol berdampingan</b>.
     *
     * <p>RetroTK ({@code Clif.parse*}) dan RTK2
     * ({@code org.rtk.map.proto.Inbound}) dilayani di port yang sama, dan
     * pembedanya <b>tanpa keadaan</b>: paket RetroTK selalu diawali
     * {@code 0xAA}. Lihat {@code Wire.isRetroTk} untuk kenapa bingkai RTK2
     * tidak akan pernah bisa disalahtebak.</p>
     *
     * <p>Keduanya hidup bersama karena arah project sudah final — protokol
     * dan kliennya diganti — tetapi klien penggantinya belum ada. Menghapus
     * jalur RetroTK sekarang berarti tidak ada apa pun yang bisa terhubung
     * sampai Trek B selesai; membiarkannya tidak menghalangi apa pun.</p>
     */
    static int clientParse(int fd) {
        Session s = net.session(fd);
        if (s == null) {
            return 0;
        }
        if (s.eof) {
            handleDisconnect(fd);
            net.sessionEof(fd);
            return 0;
        }
        if (s.rfifoRest() < 1) {
            return 0;
        }
        return org.rtk.map.proto.Wire.isRetroTk(org.rtk.map.proto.Inbound.bytesOf(s))
                ? retroTkParse(fd, s) : rtk2Parse(fd, s);
    }

    /** Satu bingkai RTK2. */
    private static int rtk2Parse(int fd, Session s) {
        int len;
        try {
            len = org.rtk.map.proto.Wire.frameLength(org.rtk.map.proto.Inbound.bytesOf(s));
            if (len == 0) {
                return 0;   // bingkainya belum lengkap
            }
            org.rtk.map.proto.Inbound.dispatch(fd, s, onlineChars.get(fd), len,
                    commands, RTK2_HANDSHAKE);
        } catch (org.rtk.map.proto.Wire.Malformed e) {
            // Setelah satu bingkai rusak, batas bingkai berikutnya sudah
            // tidak diketahui — melanjutkan berarti menafsirkan sampah.
            log.warn("[RTK2] bingkai rusak dari {}: {}", s.clientIpString(), e.getMessage());
            s.eof = true;
            return 0;
        }
        if (net.session(fd) != null && s.rfifoRest() >= len) {
            s.rfifoSkip(len);
        }
        return 0;
    }

    /**
     * Perkenalan RTK2: sama jalurnya dengan 0x10 RetroTK, tanpa byte-nya —
     * plus verifikasi sandi (K3.4). RetroTK memercayai login server untuk
     * itu; RTK2 belum punya login server sendiri, jadi map server yang
     * memeriksa sandi terhadap {@code ChaPassword} sebelum meminta data
     * karakter. Sandi salah = penolakan sambungan, bukan karakter hantu.
     */
    private static boolean rtk2Introduce(int fd, Session s, String name,
                                         String password) {
        String md5 = sql.queryString(
                "SELECT `ChaPassword` FROM `Character` WHERE `ChaName` = ?", name);
        if (md5 == null) {
            log.warn("[MAP] RTK2 '{}' dari {} ditolak: nama tidak ada",
                    name, s.clientIpString());
            return false;
        }
        // K3-lanjutan: sesi yang sudah masuk AKUN boleh memilih karakter
        // miliknya tanpa sandi karakter — itulah arti "pilih karakter".
        // ⚠️ Kepemilikannya diperiksa ULANG di sini terhadap tabel Accounts;
        // klien tidak pernah boleh menyebut karakter mana yang miliknya.
        Integer akun = akunSesi.get(fd);
        boolean lewatAkun = akun != null && akunMemiliki(akun, name);
        if (!lewatAkun
                && !org.rtk.charserver.CharDb.isPass(name, password, md5)) {
            log.warn("[MAP] RTK2 '{}' dari {} ditolak: sandi salah",
                    name, s.clientIpString());
            return false;
        }
        // logindata_add() di logif.c:85: karakter yang MASIH online tidak
        // boleh dipegang dua klien. Di C pendatangnya ditolak (kode 6), sesi
        // lamanya ditendang lewat 0x3804, dan pemain masuk lagi dengan
        // tangan. Di sini SENGAJA MENYIMPANG: sesi lama ditendang, lalu
        // pendatangnya DIMASUKKAN begitu sesi lama tertutup. Alasannya
        // bukan selera: klien yang menutup soketnya lalu menyambung lagi
        // dalam sekejap (livetest, atau pemain yang kliennya baru saja
        // jatuh) tiba SEBELUM utas logika sempat memproses eof sesi
        // lamanya — dengan kontrak C ia ditolak karena "masih online"
        // padahal tidak ada siapa pun di sana (Peringatan #167).
        // ⚠️ Sampai 30 Agu 2026 bendera ChaOnline tidak pernah DISETEL oleh
        // port ini (intif.c:255 terlewat), jadi pemeriksaan apa pun di sini
        // tidak akan pernah berarti tanpa perbaikan di parseCharLoad.
        Integer online = sql.queryInt(
                "SELECT `ChaOnline` FROM `Character` WHERE `ChaName` = ?", name);
        if (online != null && online > 0) {
            Integer chaId = sql.queryInt(
                    "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
            User lama = onlineByName(name);
            if (lama != null) {
                tendang(lama, "Karakter ini masuk dari tempat lain; sambungan diputus.");
            } else if (chaId != null) {
                // Di map server lain (atau bendera basi sesudah server
                // jatuh): char server yang menyebarkan tendangannya dan
                // memadamkan benderanya.
                MapIntif.mintaTendang(chaId);
            }
            log.warn("[MAP] RTK2 '{}' dari {}: masih online ({}); sesi lama ditendang, "
                    + "pendatang dimasukkan sesudahnya",
                    name, s.clientIpString(), lama != null ? "server ini" : "server lain");
            hasilAkun(s, 9, "Karakter masih online; sesi lamanya diputus dulu…");
            rtk2Fds.add(fd);
            // Sesudah sesi lama tertutup (700 ms > 500 ms jeda tendang; 1,2 s
            // bila di server lain), pendatang diperkenalkan seperti biasa —
            // asal slot fd-nya masih miliknya (Peringatan #96/#124).
            timers.insert(lama != null ? 700 : 1200, 0, (a, b) -> {
                if (net.session(fd) == s && !s.eof) {
                    if (!authByName(fd, s, name)) {
                        tutupAktif(s);
                    }
                }
                return 0;
            }, 0, 0);
            return true;
        }
        rtk2Fds.add(fd);
        return authByName(fd, s, name);
    }

    /** Pemain yang sedang online di server ini dengan nama itu, atau null. */
    static User onlineByName(String nama) {
        for (User u : onlineChars.values()) {
            if (u.name().equalsIgnoreCase(nama)) {
                return u;
            }
        }
        return null;
    }

    /**
     * Tendang satu pemain: beri tahu, lalu tutup sambungannya sesudah
     * pesannya sempat terkirim. Penutupan berjalan lewat jalur putus biasa
     * ({@link #handleDisconnect}), jadi karakternya TERSIMPAN dan
     * {@code ChaOnline} kembali 0 lewat 0x3007.
     */
    static void tendang(User sd, String pesan) {
        if (sd == null || sd.ditendang) {
            return;
        }
        sd.ditendang = true;
        clientView.messageToPlayer(sd, 5, pesan);
        final Session s = net.session(sd.fd);
        timers.insert(500, 0, (a, b) -> {
            if (onlineChars.get(sd.fd) == sd) {
                tutupAktif(s);
            }
            return 0;
        }, 0, 0);
        log.info("[MAP] {} ditendang: {}", sd.name(), pesan);
    }

    /**
     * Tutup satu sesi SEKARANG lewat jalur putus biasa — bila slot fd-nya
     * masih milik sesi itu (fd dipakai ulang, Peringatan #96/#124).
     */
    private static int tutupAktif(Session s) {
        if (s != null && net.session(s.fd) == s) {
            handleDisconnect(s.fd);
            net.sessionEof(s);
        }
        return 0;
    }

    /** Akun yang sudah masuk pada tiap sesi RTK2 (K3-lanjutan). */
    private static final java.util.Map<Integer, Integer> akunSesi =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Apakah karakter ini benar-benar milik akun tersebut. */
    private static boolean akunMemiliki(int akunId, String nama) {
        Integer chaId = sql.queryInt(
                "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", nama);
        if (chaId == null) {
            return false;
        }
        Integer ada = sql.queryInt(
                "SELECT `AccountId` FROM `Accounts` WHERE `AccountId` = ?"
                + " AND (`AccountCharId1` = ? OR `AccountCharId2` = ?"
                + " OR `AccountCharId3` = ? OR `AccountCharId4` = ?"
                + " OR `AccountCharId5` = ? OR `AccountCharId6` = ?)",
                akunId, chaId, chaId, chaId, chaId, chaId, chaId);
        return ada != null;
    }

    /** Penangan pra-login & perkenalan RTK2 (K3-lanjutan). */
    private static final org.rtk.map.proto.Inbound.Handshake RTK2_HANDSHAKE =
            new org.rtk.map.proto.Inbound.Handshake() {
        @Override
        public boolean playerIntroduces(int fd, Session s, String nama, String sandi) {
            return rtk2Introduce(fd, s, nama, sandi);
        }

        @Override
        public void accountLogs(int fd, Session s, String email, String sandi) {
            Integer id = sql.queryInt(
                    "SELECT `AccountId` FROM `Accounts`"
                    + " WHERE `AccountEmail` = ? AND `AccountPassword` = MD5(?)",
                    email, sandi);
            if (id == null) {
                akunSesi.remove(fd);
                hasilAkun(s, 1, "Email atau sandi akun salah.");
                return;
            }
            Integer banned = sql.queryInt(
                    "SELECT `AccountBanned` FROM `Accounts` WHERE `AccountId` = ?", id);
            if (banned != null && banned == 1) {
                akunSesi.remove(fd);
                hasilAkun(s, 2, "Akun ini diblokir.");
                return;
            }
            akunSesi.put(fd, id);
            kirimDaftarKarakter(s, id);
        }

        @Override
        public void createsCharacter(int fd, Session s, String nama, String sandi,
                                     int sex, int wajah, int rambut,
                                     int warnaRambut, int warnaWajah, int negara) {
            if (nama.length() < 3 || nama.length() > 12
                    || !nama.matches("[A-Za-z]+")) {
                hasilAkun(s, 3, "Nama harus 3-12 huruf, tanpa angka atau spasi.");
                return;
            }
            if (sandi.length() < 3) {
                hasilAkun(s, 4, "Sandi minimal 3 huruf.");
                return;
            }
            // ⚠️ Slot akun diperiksa SEBELUM karakternya dibuat. Kalau
            // urutannya terbalik, akun yang sudah penuh tetap mendapat baris
            // `Character` baru yang tidak dikaitkan ke mana pun: nama itu
            // terpakai selamanya tanpa ada yang bisa memainkannya.
            Integer akunSesiIni = akunSesi.get(fd);
            if (akunSesiIni != null && slotAkunKosong(akunSesiIni) == 0) {
                hasilAkun(s, 7, "Akun ini sudah punya 6 karakter.");
                return;
            }
            // ⚠️ `sql` DI SINI adalah kolam koneksi map server. Versi tanpa
            // argumen memakai kolam char server, yang di proses ini tidak
            // pernah tersambung — Peringatan #123.
            int r = org.rtk.charserver.CharDb.newChar(sql, nama, sandi, 0, sex,
                    negara, wajah, rambut, warnaRambut, warnaWajah);
            switch (r) {
                case 0 -> {
                    // Kaitkan ke akun bila sesi ini sedang masuk akun, supaya
                    // karakter barunya langsung muncul di daftarnya.
                    Integer akun = akunSesiIni;
                    if (akun != null) {
                        kaitkanKeAkun(akun, nama);
                        hasilAkun(s, 0, "Karakter '" + nama + "' dibuat.");
                        kirimDaftarKarakter(s, akun);
                    } else {
                        hasilAkun(s, 0, "Karakter '" + nama + "' dibuat. "
                                + "Masuk dengan nama dan sandi itu.");
                    }
                }
                case 1 -> hasilAkun(s, 5, "Nama '" + nama + "' sudah dipakai.");
                default -> hasilAkun(s, 6, "Gagal membuat karakter.");
            }
        }
    };

    /**
     * Kirim satu bingkai RTK2 ke sesi yang BELUM punya User.
     *
     * <p>{@code Rtk2ClientView.kirim} bekerja dari objek pemain, dan pada
     * tahap pra-login pemain itu belum ada — daftar karakter justru yang
     * menentukan siapa pemainnya nanti.</p>
     */
    private static void kirimRtk2(Session s, org.rtk.map.proto.Wire.Writer w) {
        if (s == null) {
            return;
        }
        byte[] f = w.frame();
        s.wfifoBytes(0, f, f.length);
        s.wfifoSet(f.length);
    }

    /** Nomor slot akun pertama yang kosong (1..6), atau 0 bila penuh. */
    private static int slotAkunKosong(int akunId) {
        for (int i = 1; i <= 6; i++) {
            Integer isi = sql.queryInt("SELECT `AccountCharId" + i + "`"
                    + " FROM `Accounts` WHERE `AccountId` = ?", akunId);
            if (isi == null || isi == 0) {
                return i;
            }
        }
        return 0;
    }

    /** Pasang karakter ke slot akun pertama yang kosong. */
    private static void kaitkanKeAkun(int akunId, String nama) {
        Integer chaId = sql.queryInt(
                "SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", nama);
        if (chaId == null) {
            return;
        }
        int slot = slotAkunKosong(akunId);
        if (slot == 0) {
            log.warn("[MAP] akun {} sudah punya 6 karakter; '{}' tidak dikaitkan",
                    akunId, nama);
            return;
        }
        sql.update("UPDATE `Accounts` SET `AccountCharId" + slot + "` = ?"
                + " WHERE `AccountId` = ?", chaId, akunId);
    }

    private static void hasilAkun(Session s, int kode, String pesan) {
        kirimRtk2(s, new org.rtk.map.proto.Wire.Writer(
                org.rtk.map.proto.Wire.EV_ACCOUNT_RESULT).u8(kode).str(pesan));
    }

    /** Kirim daftar karakter milik satu akun. */
    private static void kirimDaftarKarakter(Session s, int akunId) {
        java.util.List<Object[]> daftar = new java.util.ArrayList<>();
        sql.forEachRow(
                "SELECT c.`ChaName`, c.`ChaLevel`, c.`ChaSex`, c.`ChaFace`,"
                + " c.`ChaHair`, c.`ChaHairColor`, c.`ChaPthId` FROM `Character` c"
                + " JOIN `Accounts` a ON c.`ChaId` IN (a.`AccountCharId1`,"
                + " a.`AccountCharId2`, a.`AccountCharId3`, a.`AccountCharId4`,"
                + " a.`AccountCharId5`, a.`AccountCharId6`)"
                + " WHERE a.`AccountId` = ? ORDER BY c.`ChaLevel` DESC",
                rs -> daftar.add(new Object[] {
                    rs.getString("ChaName"), rs.getInt("ChaLevel"),
                    rs.getInt("ChaSex"), rs.getInt("ChaFace"),
                    rs.getInt("ChaHair"), rs.getInt("ChaHairColor"),
                    rs.getInt("ChaPthId")}),
                akunId);
        org.rtk.map.proto.Wire.Writer w = new org.rtk.map.proto.Wire.Writer(
                org.rtk.map.proto.Wire.EV_CHAR_LIST).u16(daftar.size());
        for (Object[] c : daftar) {
            w.str((String) c[0]).u16((Integer) c[1]).u8((Integer) c[6])
             .u16((Integer) c[3]).u16((Integer) c[4])
             .u8((Integer) c[5]).u8((Integer) c[2]);
        }
        kirimRtk2(s, w);
    }

    /** Satu paket RetroTK. */
    private static int retroTkParse(int fd, Session s) {
        if (s.rfifoRest() < 3) {
            return 0;
        }
        int len = s.rfifoWBE(1) + 3;
        if (s.rfifoRest() < len) {
            return 0;
        }

        User sd = onlineChars.get(fd);
        int opcode = s.rfifoB(3);

        if (sd == null) {
            // Paket perkenalan TIDAK didekripsi. Di C cabang !sd menangani
            // 0x10 lalu return, sementara decrypt(fd) baru dipanggil setelah
            // nullpo_ret(0, sd) (clif.c:11304-11317 vs 11359). Masuk akal:
            // kunci sesi berasal dari data karakter, yang belum ada saat
            // klien baru memperkenalkan diri. Mendekripsinya di sini membuat
            // byte mulai offset 5 ter-XOR — opcode tetap 0x10 (crypt mulai
            // dari off+5) tapi panjang nama di offset 15 jadi sampah.
            if (opcode == 0x10) {
                clientAuth(fd, s);
            } else {
                log.debug("[MAP] opcode 0x{} diabaikan: klien belum terautentikasi",
                        String.format("%02X", opcode));
            }
        } else {
            Clif.decrypt(s, sd);
            switch (opcode) {
                case 0x06, 0x32 -> Clif.parseWalk(sd);
                case 0x43 -> Clif.parseClick(sd);
                case 0x39 -> Clif.parseMenuInput(sd);
                case 0x3A -> Clif.parseNpcDialog(sd);
                default -> log.debug("[MAP] opcode 0x{} belum diport (dari {})",
                        String.format("%02X", opcode), sd.name());
            }
        }

        if (net.session(fd) != null && s.rfifoRest() >= len) {
            s.rfifoSkip(len);
        }
        return 0;
    }

    /**
     * clif_accept2(): klien memperkenalkan diri setelah dialihkan dari
     * login server. Namanya dicari di database, lalu datanya diminta ke
     * char server lewat 0x3003.
     */
    private static void clientAuth(int fd, Session s) {
        int nameLen = s.rfifoB(15);
        if (nameLen <= 0 || nameLen > 16) {
            log.warn("[MAP] panjang nama tidak masuk akal ({}) dari {}", nameLen, s.clientIpString());
            s.eof = true;
            return;
        }
        if (!authByName(fd, s, s.rfifoString(16, nameLen))) {
            s.eof = true;
        }
    }

    /**
     * Bagian {@code clif_accept2} yang <b>bukan</b> pembacaan byte: cari
     * nama karakternya, lalu minta datanya ke char server lewat 0x3003.
     *
     * <p>Dipakai kedua protokol. Dipisah karena isinya sama persis —
     * yang berbeda hanya dari mana namanya datang.</p>
     */
    static boolean authByName(int fd, Session s, String name) {
        Integer charId = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
        if (charId == null || charId == 0) {
            log.warn("[MAP] karakter '{}' dari {} tidak ada di database", name, s.clientIpString());
            return false;
        }
        s.name = name;
        log.info("[MAP] klien {} memperkenalkan diri sebagai '{}' (id {})",
                s.clientIpString(), name, charId);
        MapIntif.requestChar(fd, charId, name);
        return true;
    }

    /** Detik dinding terakhir yang sudah dijalankan kait cronjob-nya. */
    private static long detikCronTerakhir = -1;

    /**
     * {@code map_cronjob()}: kait skrip berkala.
     *
     * <p>Mengikuti C persis: kaitnya dipilih dari SISA BAGI detik jam
     * dinding, bukan dari penghitung sendiri — {@code cronJobMin} jalan saat
     * detik habis dibagi 60, dan seterusnya sampai harian.</p>
     *
     * <p>⚠️ Detik yang sama tidak boleh dijalankan dua kali. Timernya
     * berjeda 1.000 ms, tetapi tik yang terlambat sedikit lalu terkejar bisa
     * jatuh pada detik dinding yang sama — dan `cronJobMin` yang jalan dua
     * kali berarti mis. donasi diproses ganda.</p>
     */
    private static void cronjobTick() {
        if (scriptEngine == null) {
            return;
        }
        long detik = System.currentTimeMillis() / 1000L;
        if (detik == detikCronTerakhir) {
            return;
        }
        detikCronTerakhir = detik;

        if (detik % 60 == 0) {
            scriptEngine.doScript("cronJobMin", "cronJobMin");
        }
        if (detik % 300 == 0) {
            scriptEngine.doScript("cronJob5Min", "cronJob5Min");
        }
        if (detik % 1800 == 0) {
            scriptEngine.doScript("cronJob30Min", "cronJob30Min");
        }
        if (detik % 3600 == 0) {
            scriptEngine.doScript("cronJobHour", "cronJobHour");
        }
        if (detik % 86400 == 0) {
            scriptEngine.doScript("cronJobDay", "cronJobDay");
        }
        scriptEngine.doScript("cronJobSec", "cronJobSec");
    }

    /**
     * {@code map_loadgameregistry()}: registry sedunia dari basis data.
     *
     * <p>Tanpa ini nilai seperti {@code red_potions_available} atau
     * {@code elixirRound} selalu mulai dari 0 tiap server dinyalakan, dan
     * apa pun yang ditulis skrip hilang saat mati — acara yang berjalan
     * lintas restart tidak pernah bisa selesai.</p>
     */
    private static void muatGameRegistry(org.rtk.map.script.ScriptEngine engine) {
        int[] n = {0};
        int rows = sql.forEachRow(
                "SELECT `GrgIdentifier`,`GrgValue` FROM `GameRegistry" + serverId + "`",
                rs -> {
                    engine.gameRegMuat(rs.getString("GrgIdentifier"),
                            rs.getInt("GrgValue"));
                    n[0]++;
                });
        if (rows < 0) {
            log.warn("[MAP] tabel `GameRegistry{}` tidak terbaca; registry "
                    + "sedunia mulai kosong dan TIDAK akan tersimpan", serverId);
            return;
        }
        log.info("[MAP] {} registry sedunia dimuat dari `GameRegistry{}`",
                n[0], serverId);
    }

    /**
     * {@code map_savegameregistry()}: tulis-terus tiap kali nilainya berubah.
     *
     * <p>⚠️ Nilai 0 MENGHAPUS barisnya, persis seperti C — di sana nol
     * adalah "tidak ada", dan baris bernilai nol yang tertinggal akan
     * dibaca kembali sebagai entri yang menghabiskan slot.</p>
     */
    private static void simpanGameRegistry(String nama, int nilai) {
        String tabel = "`GameRegistry" + serverId + "`";
        if (nilai == 0) {
            sql.update("DELETE FROM " + tabel + " WHERE `GrgIdentifier` = ?", nama);
            return;
        }
        int diubah = sql.update("UPDATE " + tabel + " SET `GrgValue` = ?"
                + " WHERE `GrgIdentifier` = ?", nilai, nama);
        if (diubah <= 0) {
            sql.update("INSERT INTO " + tabel
                    + " (`GrgIdentifier`,`GrgValue`) VALUES (?, ?)", nama, nilai);
        }
    }

    /** Pemain terputus: simpan lalu lepaskan dari dunia. */
    static void handleDisconnect(int fd) {
        rtk2Fds.remove(fd);
        // ⚠️ WAJIB. Nomor fd DIPAKAI ULANG oleh sambungan berikutnya: tanpa
        // baris ini, sambungan baru yang kebetulan mendapat fd bekas sesi
        // yang sudah masuk akun ikut mewarisi akunnya, dan boleh masuk ke
        // karakter akun itu TANPA sandi. Ketahuan oleh kontrol negatif
        // livetest, bukan oleh nalar. Lihat docs/PERINGATAN.md #124.
        akunSesi.remove(fd);
        // Permintaan data karakter yang belum dijawab char server tidak lagi
        // ada tujuannya — lihat catatan `menunggu` di MapIntif.
        MapIntif.lupakanPermintaan(fd);
        User sd = onlineChars.remove(fd);
        if (sd == null) {
            return;
        }
        // clif_leavegroup() di jalur putus sambungan (clif.c:10949).
        // ⚠️ Dipanggil SETELAH onlineChars.remove supaya pemain yang pergi
        // tidak ikut terdaftar saat anggota sisanya disegarkan, dan
        // SEBELUM despawn supaya petanya masih diketahui.
        Groups.keluar(sd);
        Pc.despawn(world, sd);
        // simpan + tandai offline lewat char server (0x3007); penyegaran
        // posisi & samaran dilakukan saveChar sendiri
        MapIntif.saveChar(sd, true);
        // clif.c:10957 — bendera online dipadamkan map server SEKETIKA;
        // 0x3007 di char server memadamkannya lagi, tidak apa-apa.
        sql.update("UPDATE `Character` SET `ChaOnline` = 0 WHERE `ChaId` = ?", sd.id);
        log.info("[MAP] {} keluar dari dunia", sd.name());
    }

    static int clientAccept(int fd) {
        Session s = net.session(fd);
        if (s != null) {
            log.info("[MAP] Client connected from: {}", s.clientIpString());
        }
        return 0;
    }

    public static void main(String[] args) {
        boot(args);
        core = new Core(net, timers);
        core.mainLoop();
    }

    /**
     * Seluruh penyalaan map server KECUALI loop utamanya.
     *
     * <p>Dipisahkan supaya gerbang bisa memakai penyalaan yang SAMA persis
     * dengan server sungguhan — peta, NPC, barang, mantra, skrip, registry
     * sedunia, dan timer. Gerbang yang menyusun dunianya sendiri hanya
     * menguji dunia buatannya sendiri.</p>
     */
    static void boot(String[] args) {
        String confFile = "conf/map.conf";
        String interFile = "conf/inter.conf";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--conf": confFile = args[++i]; break;
                case "--inter": interFile = args[++i]; break;
                default: break;
            }
        }

        ServerLog.setLogfile("logs/map.log");
        ServerLog.setDmpfile("logs/map_dump.log");

        net.doSocket();

        configRead(confFile);
        configRead(interFile);
        configRead("conf/char.conf"); // sql settings
        // lang_read(): pesan penolakan map server. Punya bawaan, jadi
        // berkasnya yang hilang tidak membuat pemain ditolak dengan pesan
        // kosong — lihat MapMsg.
        org.rtk.map.data.MapMsg.load("conf/lang.conf");

        // map.c:1822 — seluruh bendera online dinolkan saat map server
        // menyala: sesi yang tersisa dari proses sebelumnya sudah pasti mati.
        // ⚠️ Cermin C apa adanya: dengan dua map server, boot server kedua
        // menolkan bendera pemain yang sedang online di server pertama.
        if (sql.connect(sqlId, sqlPw, sqlIp, sqlPort, sqlDb)) {
            int n = sql.update("UPDATE `Character` SET `ChaOnline` = 0 WHERE `ChaOnline` = 1");
            if (n > 0) {
                log.info("[MAP] {} bendera ChaOnline basi dinolkan", n);
            }
        }
        if (!sql.connect(sqlId, sqlPw, sqlIp, sqlPort, sqlDb)) {
            log.warn("[MAP] WARNING: no database connection; running with map 0 only.");
        }
        loadMaps();

        // Folder berkas .map. Geometri peta belum dimuat (map server masih
        // skeleton), tapi path-nya sudah dikonfigurasi dan divalidasi di sini
        // supaya salah setel ketahuan sejak start, bukan nanti saat dipakai.
        java.nio.file.Path mapDir = java.nio.file.Paths.get(mapPath);
        if (java.nio.file.Files.isDirectory(mapDir)) {
            log.info("[MAP] Folder peta: {}", mapDir.toAbsolutePath().normalize());
        } else {
            log.warn("[MAP] Folder peta tidak ditemukan: {} (setel map_path di conf/map.conf)",
                    mapDir.toAbsolutePath().normalize());
        }

        // scripting subsystem (sl_init): load the original rtklua content
        if (luaEnable != 0) {
            try {
                scriptEngine = new org.rtk.map.script.ScriptEngine();
                // map_id2sd / map_name2sd untuk konstruktor Player(id).
                scriptEngine.setPlayerLookup(
                        new org.rtk.map.script.ScriptEngine.PlayerLookup() {
                    @Override
                    public org.rtk.map.script.ScriptPlayer byId(long id) {
                        for (User u : onlineChars.values()) {
                            if (u.id == id || u.status.id == id) {
                                return u.scriptPlayer();
                            }
                        }
                        return null;
                    }

                    @Override
                    public org.rtk.map.script.ScriptPlayer byName(String name) {
                        for (User u : onlineChars.values()) {
                            if (u.status.name.equalsIgnoreCase(name)) {
                                return u.scriptPlayer();
                            }
                        }
                        return null;
                    }
                });
                // map_id2npc / map_name2npc untuk konstruktor NPC(id|nama).
                scriptEngine.setNpcLookup(
                        new org.rtk.map.script.ScriptEngine.NpcLookup() {
                    @Override
                    public Object byId(long id) {
                        return npcs == null ? null : npcs.byId(id);
                    }

                    @Override
                    public Object byName(String name) {
                        return npcs == null ? null : npcs.byName(name);
                    }
                });
                // ⚠️ `core = NPC(4294967295)` di sys.lua bergantung pada NPC
                // F1 (`NpcIsF1Npc=1`), dan `core.gameRegistry` dipakai 1.036
                // kali di 27 berkas — termasuk jalur biasa seperti membeli
                // ramuan merah. Kalau barisnya tidak ada, SELURUH pemakaian
                // itu melempar "attempt to index ? (a nil value)" di tempat
                // yang tampak tidak berhubungan. Lebih baik diketahui saat
                // start daripada ditemukan dari satu petak acara.
                if (npcs.byId(org.rtk.map.Npc.F1_NPC) == null) {
                    log.warn("[MAP] NPC F1 (NpcIsF1Npc=1) tidak ada di `NPCs{}` — "
                            + "`core` di skrip akan nil dan setiap "
                            + "`core.gameRegistry` melempar", serverId);
                } else {
                    log.info("[MAP] NPC F1 siap: `core` di skrip terisi");
                }
                // map_loadgameregistry(): registry sedunia dari
                // `GameRegistry<serverid>`. Dimuat SEBELUM skrip berjalan,
                // karena skrip membacanya sejak tik pertama.
                muatGameRegistry(scriptEngine);
                scriptEngine.setGameRegistryWriter(MapServer::simpanGameRegistry);
                int errors = scriptEngine.init(luaPath);
                if (errors < 0) {
                    log.warn("[MAP] Lua scripting disabled: rtklua not found at {} (set lua_path in map.conf)", luaPath);
                    scriptEngine = null;
                } else {
                    log.info("[MAP] Lua scripting ready: {} scripts loaded, {} with errors",
                            scriptEngine.loadedFiles(), scriptEngine.loadErrors());
                }
            } catch (RuntimeException e) {
                log.error("[MAP] Lua scripting failed to start", e);
                scriptEngine = null;
            }
        }

        ServerLog.addLog("");
        ServerLog.addLog("RetroTK Map Server (Java skeleton) Started.%n");

        net.setDefaultAccept(MapServer::clientAccept);
        net.setDefaultParse(MapServer::clientParse);
        mapFd = net.makeListenPort(mapPort);

        // R2: kalender dunia — dibaca dari tabel Time lalu berjalan sendiri.
        WorldTime.load(sql);
        timers.insert(WorldTime.TICK_MS, WorldTime.TICK_MS,
                (a, b) -> WorldTime.tick(), 0, 0);
        timers.insert(1000, RECONNECT_MS, (a, b) -> checkConnectChar(), 0, 0);
        // npc_runtimers(): tik 100 ms untuk kait `move` dan `action` milik NPC
        timers.insert(NpcRegistry.TICK_MS, NpcRegistry.TICK_MS,
                (a, b) -> npcs.runTimers(scriptEngine), 0, 0);
        // mob_timer_spawns(): tik 50 ms untuk AI mob dan kelahiran ulang
        timers.insert(MobRegistry.TICK_MS, MobRegistry.TICK_MS,
                (a, b) -> mobs.runTimers(scriptEngine, world), 0, 0);
        // bl_duratimer(): tik 1 detik untuk durasi & aether mantra, kait
        // `while_passive` / `while_equipped` / `while_cast`, dan `uncast`
        // saat durasinya habis. Di C tiap pemain punya timernya sendiri;
        // di sini satu timer menyapu semua pemain online, karena logika
        // permainan berjalan di satu thread (lihat Peringatan #8).
        timers.insert(1000, 1000, (a, b) -> duraTick(), 0, 0);
        // map_cronjob(): tik 1 detik yang memanggil kait berkala milik
        // skrip. Tanpa ini `minigames.timer()` tidak pernah jalan, jadi
        // TIDAK ADA acara berkala yang pernah dimulai — begitu juga
        // kelahiran bos, penerangan peta, dan pemunculan barang.
        timers.insert(1000, 1000, (a, b) -> {
            cronjobTick();
            return 0;
        }, 0, 0);

        log.info("RetroTK Map Server (Java skeleton) is ready! Listening at {}.", mapPort);
        ServerLog.addLog("Server Ready! Listening at %d.%n", mapPort);
    }
}
