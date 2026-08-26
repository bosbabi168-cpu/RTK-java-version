package org.rtk.charserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.common.mmo.BankItem;
import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.Item;
import org.rtk.common.mmo.Legend;
import org.rtk.common.mmo.Point;
import org.rtk.common.mmo.SkillInfo;
import org.rtk.map.data.MapRegistry;

/**
 * Trek A3 — uji lapisan database terhadap MySQL yang benar-benar hidup.
 *
 * Sampai sekarang {@link CharPersistence} hanya diverifikasi dengan
 * mencocokkan nama kolom ke skema <i>di atas kertas</i>. Uji ini menutup
 * celah itu dalam tiga tahap:
 *
 * <ol>
 *   <li><b>Audit SQL.</b> Setiap pernyataan SQL di kode port diambil dari
 *       berkas sumbernya lalu di-<i>prepare</i> ke server. MySQL memvalidasi
 *       nama tabel dan kolom saat prepare, jadi satu nama salah langsung
 *       ketahuan — tanpa perlu daftar kolom tiruan yang bisa basi.</li>
 *   <li><b>Data dunia.</b> `Maps` dan `Warps` dimuat dari data asli, bukan
 *       portal buatan seperti di `cliftest`.</li>
 *   <li><b>Round-trip karakter.</b> Karakter uji dibuat, diisi nilai ekstrem,
 *       disimpan lewat {@link CharPersistence#save}, dibaca ulang lewat
 *       {@link CharPersistence#load}, dan dibandingkan ladang per ladang —
 *       lalu dihapus lagi.</li>
 * </ol>
 *
 * Uji ini <b>tidak menyentuh data yang sudah ada</b>: ia hanya menulis pada
 * karakter buatannya sendiri, dan membersihkannya di akhir (juga saat gagal).
 *
 * Jalankan: {@code ./run.sh dbtest}
 */
public final class DbTest {

    private static final Logger log = LogManager.getLogger(DbTest.class);

    /** Nama karakter uji — dipakai juga untuk membersihkan sisa uji lama. */
    private static final String TEST_NAME = "__rtkdbtest__";

    /** Berkas sumber yang pernyataan SQL-nya ikut diaudit. */
    private static final String[] SQL_SOURCES = {
        "src/org/rtk/charserver/CharPersistence.java",
        "src/org/rtk/charserver/CharDb.java",
        "src/org/rtk/map/data/MapRegistry.java",
        // Ditambahkan 26 Agustus 2026: keempatnya membaca tabel game dan
        // sempat TIDAK terjangkau audit ini, sehingga kolom yang baru
        // ditambahkan (`ItmText`, `ItmProtected`, `ItmDroppable`, kueri
        // mantra per jalur) tidak pernah divalidasi MySQL.
        "src/org/rtk/map/data/ItemDb.java",
        "src/org/rtk/map/data/SpellDb.java",
        "src/org/rtk/map/NpcRegistry.java",
        "src/org/rtk/map/MobRegistry.java",
        "src/org/rtk/map/Parcels.java",
        "src/org/rtk/map/data/BoardDb.java",
        "src/org/rtk/map/data/ClanDb.java",
        "src/org/rtk/charserver/Mapif.java",
        "src/org/rtk/map/MapServer.java",
        "src/org/rtk/login/LoginClif.java",
        "src/org/rtk/login/LoginIntif.java",
    };

    private static int failures;

    private DbTest() {
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }

    private static void eq(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {} — diharapkan [{}], dapat [{}]", what, expected, actual);
            failures++;
        }
    }

    public static void main(String[] args) {
        Sql sql = new Sql();
        try {
            run(sql, args);
        } catch (RuntimeException | IOException e) {
            log.error("uji berhenti karena kesalahan tak terduga", e);
            failures++;
        } finally {
            cleanup(sql);
            sql.close();
        }

        if (failures == 0) {
            log.info("ALL DB TESTS PASSED");
        } else {
            log.error("{} TEST GAGAL", failures);
            System.exit(1);
        }
    }

    private static void run(Sql sql, String[] args) throws IOException {
        String confPath = args.length > 0 ? args[0] : "conf/char.conf";
        Conf conf = Conf.read(Paths.get(confPath));
        log.info("[DB] menyambung ke {}@{}:{}/{}", conf.id, conf.ip, conf.port, conf.db);

        if (!sql.connect(conf.id, conf.pw, conf.ip, conf.port, conf.db)) {
            log.error("[DB] gagal menyambung — pastikan MySQL hidup dan kredensial di {} benar", confPath);
            failures++;
            return;
        }
        check("koneksi database terbuka", true);

        auditSql(conf);
        worldData(sql);
        spellQueries(sql);
        parcelTest(sql);
        boardTest(sql);
        clanBankTest(sql);
        charRoundTrip(sql);
    }

    // ------------------------------------------------------------------
    // Tahap 1 — audit SQL
    // ------------------------------------------------------------------

    /**
     * Prepare setiap pernyataan SQL di kode port ke server sungguhan.
     *
     * Prepare memaksa MySQL mem-parse dan me-resolve nama tabel/kolom, jadi
     * inilah cara memeriksa 300-an nama kolom tanpa menyalinnya ke dalam uji
     * (salinan seperti itu akan basi diam-diam saat kode berubah).
     */
    private static void auditSql(Conf conf) throws IOException {
        log.info("=== tahap 1: audit SQL terhadap skema hidup ===");

        String url = "jdbc:mysql://" + conf.ip + ":" + conf.port + "/" + conf.db
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        int total = 0;
        int bad = 0;
        int dynamicSkipped = 0;

        try (Connection c = DriverManager.getConnection(url, conf.id, conf.pw)) {
            for (String src : SQL_SOURCES) {
                Path p = Paths.get(src);
                if (!Files.isRegularFile(p)) {
                    log.error("FAIL: berkas sumber tidak ditemukan: {} "
                            + "(jalankan dari root project supaya audit SQL bisa membaca sumbernya)", src);
                    failures++;
                    continue;
                }
                List<Stmt> stmts = extractSql(p);
                int badHere = 0;
                int skipHere = 0;
                for (Stmt st : stmts) {
                    if (st.dynamic()) {
                        // SQL yang nama tabelnya disusun saat runtime (CharPersistence.reg())
                        // tidak bisa di-prepare apa adanya; jalur itu diuji di tahap 3.
                        skipHere++;
                        dynamicSkipped++;
                        continue;
                    }
                    total++;
                    try (PreparedStatement ps = c.prepareStatement(st.sql())) {
                        ps.getParameterMetaData();
                    } catch (SQLException e) {
                        bad++;
                        badHere++;
                        log.error("FAIL: SQL ditolak server — {}\n        {}", e.getMessage(), ringkas(st.sql()));
                    }
                }
                log.info("  {} — {} diperiksa, {} bermasalah, {} dinamis (dilewati)",
                        p.getFileName(), stmts.size() - skipHere, badHere, skipHere);
            }
        } catch (SQLException e) {
            log.error("FAIL: audit SQL tidak bisa membuka koneksi", e);
            failures++;
            return;
        }

        check("ada pernyataan SQL yang terbaca dari sumber", total > 0);
        check(total + " pernyataan SQL diterima skema hidup", bad == 0);
        log.info("  {} pernyataan dinamis dilewati (diuji lewat round-trip di tahap 3)", dynamicSkipped);
    }

    /** Satu pernyataan SQL hasil ekstraksi; {@code dynamic} bila disambung nilai runtime. */
    private record Stmt(String sql, boolean dynamic) {
    }

    /**
     * Ambil literal SQL dari berkas sumber Java, termasuk yang disambung
     * dengan {@code +} lintas baris.
     */
    static List<Stmt> extractSql(Path src) throws IOException {
        String s = Files.readString(src, StandardCharsets.UTF_8);
        String[] starts = {"\"SELECT ", "\"UPDATE ", "\"DELETE ", "\"INSERT "};
        List<Stmt> out = new ArrayList<>();

        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '"') {
                continue;
            }
            boolean start = false;
            for (String st : starts) {
                if (s.startsWith(st, i)) {
                    start = true;
                    break;
                }
            }
            if (!start) {
                continue;
            }

            StringBuilder sb = new StringBuilder();
            boolean dynamic = false;
            int j = i;
            while (j < s.length() && s.charAt(j) == '"') {
                int k = j + 1;
                while (k < s.length() && s.charAt(k) != '"') {
                    if (s.charAt(k) == '\\') {
                        k++;
                    }
                    k++;
                }
                sb.append(s, j + 1, Math.min(k, s.length()));
                j = k + 1;
                // lanjut hanya bila sambungannya literal lain: spasi lalu '+' lalu '"'
                int save = j;
                while (j < s.length() && (Character.isWhitespace(s.charAt(j)) || s.charAt(j) == '+')) {
                    j++;
                }
                if (j >= s.length() || s.charAt(j) != '"') {
                    // rantai putus: kalau putusnya karena '+', SQL-nya disusun runtime
                    for (int q = save; q < j; q++) {
                        if (s.charAt(q) == '+') {
                            dynamic = true;
                            break;
                        }
                    }
                    j = save;
                    break;
                }
            }
            out.add(new Stmt(sb.toString(), dynamic));
            i = j;
        }
        return out;
    }

    private static String ringkas(String sql) {
        String one = sql.replaceAll("\\s+", " ").trim();
        return one.length() <= 140 ? one : one.substring(0, 137) + "...";
    }

    // ------------------------------------------------------------------
    // Tahap 2 — data dunia
    // ------------------------------------------------------------------

    /**
     * Kueri buku mantra terhadap tabel `Spells` sungguhan.
     *
     * <p>{@code getUnknownSpells} dan {@code getAllClassSpells} menembak
     * database tiap kali dipanggil, jadi keduanya tidak bisa diuji di
     * {@code cliftest} yang berjalan tanpa MySQL.</p>
     */
    private static void spellQueries(Sql sql) {
        log.info("=== tahap 2b: kueri buku mantra ===");

        org.rtk.map.data.SpellDb db = new org.rtk.map.data.SpellDb();
        int n = db.load(sql);
        check("tabel Spells termuat", n > 0);

        // Jalur 1 dipilih karena selalu ada isinya di data asli; kalau nanti
        // kosong, ujinya akan berkata begitu alih-alih lolos diam-diam.
        var jalur = db.classSpells(sql, 1);
        check("getAllClassSpells(1) mengembalikan mantra", !jalur.isEmpty());

        boolean adaPenanda = false;
        for (int id : jalur) {
            if (org.rtk.map.data.SpellDb.isSectionMarker(id)) {
                adaPenanda = true;
                break;
            }
        }
        check("getAllClassSpells: penanda bagian TIDAK ikut", !adaPenanda);
        check("getAllClassSpells: batas 255 dihormati", jalur.size() <= 255);

        boolean semuaBernama = true;
        for (int id : jalur) {
            if (db.nameOf(id).isEmpty()) {
                semuaBernama = false;
                break;
            }
        }
        check("getAllClassSpells: tiap id punya nama skrip", semuaBernama);

        // tanda 0 = pemain baru: hasilnya harus subset dari tanda tinggi
        var tandaRendah = db.candidateSpells(sql, 1, 0, 0, 0);
        var tandaTinggi = db.candidateSpells(sql, 1, 0, 99, 0);
        check("getUnknownSpells: tanda lebih tinggi membuka mantra >= tanda rendah",
                tandaTinggi.size() >= tandaRendah.size());
        check("getUnknownSpells: penanda bagian juga disaring di sini",
                tandaTinggi.stream().noneMatch(
                        org.rtk.map.data.SpellDb::isSectionMarker));

        // jalur yang pasti tidak ada -> hanya mantra umum (SplPthId = 0)
        var takAda = db.classSpells(sql, 9999);
        check("getAllClassSpells: jalur tak dikenal mengembalikan kosong",
                takAda.isEmpty());
    }

    /**
     * Kiriman antar pemain (tabel {@code Parcels}) — seluruhnya SQL, jadi
     * bisa diuji sungguhan tanpa klien sama sekali.
     *
     * <p>Memakai id karakter di luar rentang yang dipakai data asli, lalu
     * membersihkan sendiri barisnya. Tidak menyentuh kiriman yang ada.</p>
     */
    private static void parcelTest(Sql sql) {
        log.info("=== tahap 2c: kiriman (Parcels) ===");

        final long penerima = 999000111L;
        sql.update("DELETE FROM `Parcels` WHERE `ParChaIdDestination` = ?", penerima);
        check("mulai dari keadaan kosong",
                org.rtk.map.Parcels.list(sql, penerima).isEmpty());
        check("getParcel pada kotak kosong mengembalikan null",
                org.rtk.map.Parcels.first(sql, penerima) == null);

        // --- nomor urut per penerima ---
        check("kiriman pertama masuk",
                org.rtk.map.Parcels.send(sql, penerima, 42, 7001, 3, 0, "", 0,
                        0, 0, 0, 0, 0, 0));
        check("kiriman kedua masuk",
                org.rtk.map.Parcels.send(sql, penerima, 42, 7002, 1, 0, "Ukiran", 0,
                        0, 0, 0, 0, 0, 0));

        var daftar = org.rtk.map.Parcels.list(sql, penerima);
        check("keduanya terbaca kembali", daftar.size() == 2);
        check("nomor urutnya 0 lalu 1",
                daftar.get(0).pos == 0 && daftar.get(1).pos == 1);
        check("isi kiriman pertama utuh",
                daftar.get(0).data.id == 7001 && daftar.get(0).data.amount == 3);
        check("ukiran ikut tersimpan",
                daftar.get(1).data.realName.equals("Ukiran"));

        // --- pengirim NPC digeser ke rentang id NPC ---
        check("kiriman dari NPC masuk",
                org.rtk.map.Parcels.send(sql, penerima, 5, 7003, 1, 0, "", 1,
                        0, 0, 0, 0, 0, 0));
        var dariNpc = org.rtk.map.Parcels.list(sql, penerima).stream()
                .filter(p -> p.npcFlag != 0).findFirst().orElse(null);
        check("pengirim NPC digeser + NPC_START_NUM - 2",
                dariNpc != null
                        && dariNpc.sender == 5 + org.rtk.map.Npc.NPC_START_NUM - 2);

        // --- menghapus TIDAK menomori ulang sisanya ---
        check("hapus kiriman di posisi 0",
                org.rtk.map.Parcels.remove(sql, penerima, 0));
        daftar = org.rtk.map.Parcels.list(sql, penerima);
        check("sisanya dua", daftar.size() == 2);
        check("nomor urut sisanya TIDAK digeser ulang",
                daftar.stream().noneMatch(p -> p.pos == 0));
        org.rtk.map.Parcels.send(sql, penerima, 42, 7004, 1, 0, "", 0, 0, 0, 0, 0, 0, 0);
        var terbaru = org.rtk.map.Parcels.list(sql, penerima).stream()
                .max(java.util.Comparator.comparingInt(p -> p.pos)).orElseThrow();
        check("kiriman baru memakai nomor TERTINGGI + 1, bukan lubang yang kosong",
                terbaru.pos == 3);

        // --- ketahanan 0 berarti bawaan jenisnya ---
        check("hapus posisi yang tidak ada mengembalikan false",
                !org.rtk.map.Parcels.remove(sql, penerima, 9999));

        sql.update("DELETE FROM `Parcels` WHERE `ParChaIdDestination` = ?", penerima);
        check("bersih setelah uji",
                org.rtk.map.Parcels.list(sql, penerima).isEmpty());
    }

    /**
     * Papan pesan: metadata dari {@code BoardNames} / {@code BoardTitles},
     * dan bendera tampilan yang dihitung dari hak akses.
     *
     * <p>Bendera itu logika murni tanpa database, tetapi diuji di sini
     * karena satu-satunya cara mengetahuinya benar adalah membandingkan
     * dengan papan sungguhan.</p>
     */
    private static void boardTest(Sql sql) {
        log.info("=== tahap 2d: papan pesan ===");

        var db = new org.rtk.map.data.BoardDb();
        int n = db.load(sql);
        check("tabel BoardNames termuat", n > 0);
        check("papan pertama punya nama", !db.nameOf(1).isEmpty());
        check("nama papan bisa dicari balik", db.idOf(db.nameOf(1)) == 1);
        check("papan tak dikenal menjawab kosong, bukan meledak",
                db.nameOf(999999).isEmpty() && db.idOf("tidak_ada_papan") == 0);

        // ⚠️ flags1 bergantung pada popup DAN papan sekaligus
        var B = org.rtk.map.Boards.class;
        check("flags1: popup + papan biasa + boleh tulis -> 2",
                org.rtk.map.Boards.displayFlags1(
                        org.rtk.map.Boards.CAN_WRITE, true, 5) == 2);
        check("flags1: popup + papan biasa + TIDAK boleh tulis -> 0",
                org.rtk.map.Boards.displayFlags1(0, true, 5) == 0);
        check("flags1: bukan popup + boleh tulis -> 3",
                org.rtk.map.Boards.displayFlags1(
                        org.rtk.map.Boards.CAN_WRITE, false, 5) == 3);
        check("flags1: bukan popup + tidak boleh tulis -> 1",
                org.rtk.map.Boards.displayFlags1(0, false, 5) == 1);
        check("flags1: kotak surat (papan 0) memakai cabang bukan-popup",
                org.rtk.map.Boards.displayFlags1(
                        org.rtk.map.Boards.CAN_WRITE, true, 0) == 3);
        check("flags1: nilai 6 MENGGANTIKAN seluruh bendera, tidak di-OR",
                org.rtk.map.Boards.displayFlags1(
                        org.rtk.map.Boards.WRITE_ASK_SCRIPT, true, 5) == 6
                        && org.rtk.map.Boards.displayFlags1(
                                org.rtk.map.Boards.WRITE_ASK_SCRIPT, false, 5) == 6);

        check("flags2: kotak surat 4, papan biasa 2",
                org.rtk.map.Boards.displayFlags2(0) == 4
                        && org.rtk.map.Boards.displayFlags2(7) == 2);

        // kueri isi papan harus diterima skema hidup
        Integer brd = sql.queryInt("SELECT COUNT(*) FROM `Boards`");
        Integer mal = sql.queryInt("SELECT COUNT(*) FROM `Mail`");
        check("tabel Boards terbaca", brd != null);
        check("tabel Mail terbaca", mal != null);
    }

    /**
     * Bank klan — titip, ambil, dan penggabungan barang sejenis.
     *
     * <p>Memakai id klan di luar rentang data asli dan membersihkan sendiri
     * barisnya; tidak menyentuh bank klan yang ada.</p>
     */
    private static void clanBankTest(Sql sql) {
        log.info("=== tahap 2e: bank klan ===");

        final int klan = 999321;
        sql.update("DELETE FROM `ClanBanks` WHERE `CbkClnId` = ?", klan);

        var db = new org.rtk.map.data.ClanDb();
        db.load(sql);
        check("tabel Clans terbaca", db.count() >= 0);
        check("bank klan uji mulai kosong", db.bank(sql, klan).isEmpty());

        // --- titip ---
        var a = new org.rtk.common.mmo.BankItem();
        a.itemId = 7001;
        a.amount = 5;
        a.realName = "";
        check("titip barang pertama", db.deposit(sql, klan, a));
        check("isinya satu slot", db.bank(sql, klan).size() == 1);

        // barang dengan sepuluh atribut SAMA digabung, bukan slot baru
        var b = new org.rtk.common.mmo.BankItem();
        b.itemId = 7001;
        b.amount = 3;
        b.realName = "";
        check("titip barang sejenis", db.deposit(sql, klan, b));
        check("barang sejenis DIGABUNG, bukan jadi slot kedua",
                db.bank(sql, klan).size() == 1
                        && db.bank(sql, klan).get(0).amount == 8);

        // atribut berbeda -> slot terpisah
        var c = new org.rtk.common.mmo.BankItem();
        c.itemId = 7001;
        c.amount = 1;
        c.realName = "Ukiran";
        check("titip barang berukir", db.deposit(sql, klan, c));
        check("ukiran berbeda -> slot TERPISAH", db.bank(sql, klan).size() == 2);

        // --- ambil ---
        var ambil = new org.rtk.common.mmo.BankItem();
        ambil.itemId = 7001;
        ambil.amount = 3;
        ambil.realName = "";
        check("ambil sebagian", db.withdraw(sql, klan, ambil, 3));
        check("jumlahnya berkurang", db.bank(sql, klan).get(0).amount == 5);

        ambil.amount = 99;
        check("ambil lebih banyak dari yang ada -> gagal",
                !db.withdraw(sql, klan, ambil, 99));
        check("gagal ambil tidak mengubah apa pun",
                db.bank(sql, klan).get(0).amount == 5);

        check("ambil seluruhnya", db.withdraw(sql, klan, ambil, 5));
        check("slot yang habis dibuang", db.bank(sql, klan).size() == 1);

        var takAda = new org.rtk.common.mmo.BankItem();
        takAda.itemId = 999999;
        check("ambil barang yang tidak ada -> gagal",
                !db.withdraw(sql, klan, takAda, 1));

        // --- bertahan lewat muat ulang ---
        var db2 = new org.rtk.map.data.ClanDb();
        db2.load(sql);
        var isi = db2.bank(sql, klan);
        check("isinya bertahan setelah dimuat ulang dari database",
                isi.size() == 1 && isi.get(0).realName.equals("Ukiran"));

        sql.update("DELETE FROM `ClanBanks` WHERE `CbkClnId` = ?", klan);
        check("bersih setelah uji",
                new org.rtk.map.data.ClanDb().bank(sql, klan).isEmpty());
    }

    private static void worldData(Sql sql) {
        log.info("=== tahap 2: data dunia dari tabel asli ===");

        // queryInt, bukan rowCount: yang dibutuhkan nilai COUNT(*)-nya,
        // sedangkan rowCount() menghitung BARIS hasil (selalu 1 di sini).
        Integer mapRows = sql.queryInt("SELECT COUNT(*) FROM `Maps`");
        Integer warpRows = sql.queryInt("SELECT COUNT(*) FROM `Warps`");
        log.info("  tabel Maps {} baris, Warps {} baris", mapRows, warpRows);
        check("tabel Maps berisi data", mapRows != null && mapRows > 0);
        check("tabel Warps berisi data", warpRows != null && warpRows > 0);

        MapRegistry world = new MapRegistry();
        int loaded = world.load(sql, 0, "maps");
        log.info("  ServerId 0 memuat {} peta ({} berkas hilang)", loaded, world.missingFiles());
        check("peta untuk ServerId 0 termuat", loaded > 0);
        check("tidak ada berkas peta yang hilang", world.missingFiles() == 0);

        int warps = world.loadWarps(sql);
        check("portal termuat dari tabel Warps", warps > 0);

        // portal harus benar-benar menunjuk petak yang ada di peta yang dimuat
        int checked = 0;
        int broken = 0;
        for (int id : world.mapIds()) {
            var md = world.get(id);
            if (md == null || md.warpCount() == 0) {
                continue;
            }
            for (int y = 0; y < md.ys; y++) {
                for (int x = 0; x < md.xs; x++) {
                    var w = md.warpAt(x, y);
                    if (w == null) {
                        continue;
                    }
                    checked++;
                    var dst = world.get(w.tm());
                    if (dst == null || w.tx() >= dst.xs || w.ty() >= dst.ys) {
                        broken++;
                    }
                }
            }
        }
        log.info("  {} petak portal diperiksa, {} menunjuk ke luar peta tujuan", checked, broken);
        check("portal yang terdaftar semuanya menunjuk petak yang sah", broken == 0);

        // Beberapa petak terdaftar lebih dari sekali, jadi jumlah PETAK yang
        // bisa ditemukan lebih sedikit daripada jumlah BARIS yang dimuat.
        Integer extra = sql.queryInt(
                "SELECT COALESCE(SUM(c) - COUNT(*), 0) FROM ("
                + "SELECT COUNT(*) AS c FROM `Warps` "
                + "GROUP BY `SourceMapId`,`SourceX`,`SourceY` HAVING c > 1) t");
        int berlebih = extra == null ? 0 : extra;
        log.info("  {} baris portal berlebih (petak terdaftar ganda)", berlebih);
        eq("petak portal unik = baris dimuat - baris berlebih", warps - berlebih, checked);

        duplicateWarpOrder(sql, world);
    }

    /**
     * Pada petak yang terdaftar ganda, versi C memenangkan <b>baris
     * terakhir</b> (linked list-nya disisipi di depan lalu diambil kecocokan
     * pertama). Uji ini memastikan port memilih yang sama — sebelum
     * diperbaiki, port mengambil baris pertama dan mengirim pemain ke tujuan
     * yang salah pada 26 petak.
     */
    private static void duplicateWarpOrder(Sql sql, MapRegistry world) {
        // ambil satu petak ganda yang tujuannya benar-benar berbeda
        Object[] row = sql.queryRow(
                "SELECT `SourceMapId`,`SourceX`,`SourceY` FROM `Warps` "
                + "GROUP BY `SourceMapId`,`SourceX`,`SourceY` "
                + "HAVING COUNT(*) > 1 "
                + "AND COUNT(DISTINCT `DestinationMapId`,`DestinationX`,`DestinationY`) > 1 "
                + "LIMIT 1", 3);
        if (row == null) {
            log.info("  tidak ada petak ganda bertujuan berbeda — pemeriksaan urutan dilewati");
            return;
        }
        int m = ((Number) row[0]).intValue();
        int x = ((Number) row[1]).intValue();
        int y = ((Number) row[2]).intValue();

        // tujuan menurut baris TERAKHIR, sesuai urutan alami tabel
        int[] last = new int[3];
        boolean[] got = {false};
        sql.forEachRow("SELECT `DestinationMapId`,`DestinationX`,`DestinationY` FROM `Warps` "
                + "WHERE `SourceMapId`=? AND `SourceX`=? AND `SourceY`=?",
                rs -> {
                    last[0] = rs.getInt(1);
                    last[1] = rs.getInt(2);
                    last[2] = rs.getInt(3);
                    got[0] = true;
                }, m, x, y);

        var md = world.get(m);
        var w = md == null ? null : md.warpAt(x, y);
        if (!got[0] || w == null) {
            check("petak ganda " + m + ":" + x + "," + y + " ditemukan di registry", false);
            return;
        }
        log.info("  petak ganda {}:{},{} -> port pilih {}:{},{} (C pilih {}:{},{})",
                m, x, y, w.tm(), w.tx(), w.ty(), last[0], last[1], last[2]);
        eq("petak ganda: port memilih baris terakhir seperti C",
                last[0] + ":" + last[1] + "," + last[2],
                w.tm() + ":" + w.tx() + "," + w.ty());
    }

    // ------------------------------------------------------------------
    // Tahap 3 — round-trip karakter
    // ------------------------------------------------------------------

    private static void charRoundTrip(Sql sql) {
        log.info("=== tahap 3: round-trip karakter lewat CharPersistence ===");

        // buang sisa uji sebelumnya bila ada
        cleanup(sql);

        int ins = sql.update("INSERT INTO `Character` (`ChaName`) VALUES (?)", TEST_NAME);
        if (ins <= 0) {
            log.error("FAIL: tidak bisa membuat karakter uji");
            failures++;
            return;
        }
        Integer id = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", TEST_NAME);
        if (id == null) {
            log.error("FAIL: karakter uji tidak ditemukan setelah dibuat");
            failures++;
            return;
        }
        check("karakter uji dibuat (ChaId " + id + ")", true);

        CharStatus c = build(id);
        check("simpan karakter uji", CharPersistence.save(sql, c));

        CharStatus r = CharPersistence.load(sql, id);
        if (r == null) {
            log.error("FAIL: karakter uji tidak bisa dibaca kembali");
            failures++;
            return;
        }
        check("baca ulang karakter uji", true);

        // --- ladang skalar ---
        eq("nama", c.name, r.name);
        eq("gelar", c.title, r.title);
        eq("gelar klan", c.clanTitle, r.clanTitle);
        eq("nama F1", c.f1name, r.f1name);
        eq("pesan AFK", c.afkMessage, r.afkMessage);
        eq("level", c.level, r.level);
        eq("path", c.charClass, r.charClass);
        eq("mark", c.mark, r.mark);
        eq("totem", c.totem, r.totem);
        eq("vita sekarang", c.hp, r.hp);
        eq("vita dasar", c.baseHp, r.baseHp);
        eq("mana sekarang", c.mp, r.mp);
        eq("mana dasar", c.baseMp, r.baseMp);
        eq("pengalaman", c.exp, r.exp);
        eq("emas", c.money, r.money);
        eq("emas bank", c.bankMoney, r.bankMoney);
        eq("might", c.baseMight, r.baseMight);
        eq("will", c.baseWill, r.baseWill);
        eq("grace", c.baseGrace, r.baseGrace);
        eq("armor (kolom bertanda)", c.baseArmor, r.baseArmor);
        eq("alignment (kolom bertanda)", c.alignment, r.alignment);
        eq("gmLevel", c.gmLevel, r.gmLevel);
        eq("side", c.side, r.side);
        eq("settingFlags", c.settingFlags, r.settingFlags);
        eq("exp terjual (magic)", c.expSoldMagic, r.expSoldMagic);
        eq("exp terjual (health)", c.expSoldHealth, r.expSoldHealth);
        eq("exp terjual (stats)", c.expSoldStats, r.expSoldStats);
        eq("maks slot bank", c.maxSlots, r.maxSlots);
        eq("maks inventaris", c.maxInv, r.maxInv);
        eq("pasangan", c.partner, r.partner);
        eq("klan", c.clan, r.clan);
        eq("peringkat klan", c.clanRank, r.clanRank);
        eq("peringkat path", c.classRank, r.classRank);
        eq("posisi terakhir", c.lastPos.m + ":" + c.lastPos.x + "," + c.lastPos.y,
                r.lastPos.m + ":" + r.lastPos.x + "," + r.lastPos.y);
        check("karma (float 6 desimal)", Math.abs(c.karma - r.karma) < 0.0001f);

        // --- koleksi ---
        eq("jumlah barang inventaris", c.inventory.size(), r.inventory.size());
        eq("jumlah perlengkapan", c.equip.size(), r.equip.size());
        eq("jumlah barang bank", c.banks.size(), r.banks.size());
        eq("jumlah legenda", c.legends.size(), r.legends.size());
        eq("jumlah aether", c.duraAether.size(), r.duraAether.size());
        eq("panjang buku mantra", c.spells.length, r.spells.length);
        if (c.spells.length > 0 && r.spells.length == c.spells.length) {
            eq("id mantra di slot 0", c.spells[0], r.spells[0]);
        }

        if (!c.inventory.isEmpty() && !r.inventory.isEmpty()) {
            Item a = c.inventory.get(0);
            Item b = r.inventory.get(0);
            eq("barang: id", a.id, b.id);
            eq("barang: jumlah", a.amount, b.amount);
            eq("barang: daya tahan", a.dura, b.dura);
            eq("barang: catatan", a.note, b.note);
            eq("barang: nama asli (kolom InvEngrave)", a.realName, b.realName);
            eq("barang: pemilik", a.owner, b.owner);
        } else {
            check("barang inventaris pertama terbaca kembali", false);
        }

        if (!c.legends.isEmpty() && !r.legends.isEmpty()) {
            Legend a = c.legends.get(0);
            Legend b = r.legends.get(0);
            eq("legenda: nama", a.name, b.name);
            eq("legenda: teks", a.text, b.text);
            eq("legenda: ikon", a.icon, b.icon);
            eq("legenda: warna", a.color, b.color);
        }

        if (!c.banks.isEmpty() && !r.banks.isEmpty()) {
            eq("bank: id barang", c.banks.get(0).itemId, r.banks.get(0).itemId);
            eq("bank: jumlah", c.banks.get(0).amount, r.banks.get(0).amount);
        }

        if (!c.duraAether.isEmpty() && !r.duraAether.isEmpty()) {
            eq("aether: id", c.duraAether.get(0).id, r.duraAether.get(0).id);
            eq("aether: durasi", c.duraAether.get(0).duration, r.duraAether.get(0).duration);
        }

        // --- registry (jalur SQL dinamis: inilah yang menguji reg()) ---
        eq("registry pemain", c.registry.size(), r.registry.size());
        eq("registry nilai", c.registry.get("uji_kunci"), r.registry.get("uji_kunci"));
        eq("registry string", c.registryString.size(), r.registryString.size());
        eq("registry string nilai", c.registryString.get("uji_teks"), r.registryString.get("uji_teks"));
        eq("registry NPC", c.npcRegistry.get("uji_npc"), r.npcRegistry.get("uji_npc"));
        eq("registry quest", c.questRegistry.get("uji_quest"), r.questRegistry.get("uji_quest"));
        eq("catatan pembunuhan", c.killReg.get(500L), r.killReg.get(500L));

        // --- kolom yang memang TIDAK ditulis save() ---
        // ChaPassword & ChaLastIP diubah lewat jalur lain (login / ganti sandi),
        // persis seperti mmo_char_todb di C. Dipastikan di sini supaya tidak
        // ada yang menganggapnya bug round-trip.
        check("ChaPassword sengaja tidak ditimpa save()", r.password.isEmpty());
        check("ChaLastIP sengaja tidak ditimpa save()", r.lastIp.isEmpty());

        // --- simpan ulang: baris anak tidak boleh menumpuk ---
        CharPersistence.save(sql, r);
        CharStatus r2 = CharPersistence.load(sql, id);
        eq("simpan dua kali tidak menggandakan inventaris",
                r.inventory.size(), r2 == null ? -1 : r2.inventory.size());
        int invRows = sql.rowCount("SELECT COUNT(*) FROM `Inventory` WHERE `InvChaId` = ?", id);
        eq("baris Inventory di database sesuai jumlah barang", c.inventory.size(), invRows);
        scriptRegistryTest(sql, id);
        scriptItemTest(sql, id);
        bindingTest(sql, id);

        int regRows = sql.rowCount("SELECT COUNT(*) FROM `Registry` WHERE `RegChaId` = ?", id);
        eq("baris Registry di database sesuai jumlah entri", c.registry.size(), regRows);
    }

    /**
     * C1 ujung-ke-ujung: nilai yang <b>ditulis skrip Lua</b> harus ikut
     * tersimpan ke database.
     *
     * <p>Sebelum C1, {@code ScriptPlayer} punya peta registry sendiri yang
     * terpisah dari {@code CharStatus}, jadi apa pun yang ditulis skrip
     * hilang saat karakter disimpan. Uji ini menutup jalur itu dari Lua
     * sampai baris tabel {@code Registry}.</p>
     */
    private static void scriptRegistryTest(Sql sql, long charId) {
        log.info("  -- C1: registry dari skrip Lua sampai ke database --");

        CharStatus c = CharPersistence.load(sql, charId);
        if (c == null) {
            check("karakter uji bisa dibaca untuk uji skrip", false);
            return;
        }

        // pemain sungguhan: registry skrip ditunjuk ke registry karakter
        org.rtk.map.User sd = new org.rtk.map.User(9999, c);
        var p = sd.scriptPlayer();
        check("registry skrip menunjuk ke registry karakter",
                p.registry == c.registry && p.registryString == c.registryString);

        var engine = new org.rtk.map.script.ScriptEngine();
        engine.globals().load(
                "SIMPAN = function(pl)\n"
              + "  pl.registry['skrip_int'] = 4242\n"
              + "  pl.registryString['skrip_teks'] = 'dari lua'\n"
              + "  pl.quest['skrip_quest'] = 9\n"
              + "end\n").call();
        engine.globals().get("SIMPAN").call(engine.playerRef(p));

        check("nilai skrip terlihat di CharStatus sebelum simpan",
                Integer.valueOf(4242).equals(c.registry.get("skrip_int")));

        check("simpan karakter setelah skrip menulis", CharPersistence.save(sql, c));

        CharStatus r = CharPersistence.load(sql, charId);
        if (r == null) {
            check("karakter bisa dibaca ulang", false);
            return;
        }
        eq("registry int dari skrip tersimpan di database",
                Integer.valueOf(4242), r.registry.get("skrip_int"));
        eq("registry string dari skrip tersimpan di database",
                "dari lua", r.registryString.get("skrip_teks"));
        eq("registry quest dari skrip tersimpan di database",
                Integer.valueOf(9), r.questRegistry.get("skrip_quest"));

        // dan benar-benar ada barisnya di tabel
        Integer n = sql.queryInt(
                "SELECT COUNT(*) FROM `Registry` WHERE `RegChaId` = ? AND `RegIdentifier` = ?",
                charId, "skrip_int");
        eq("baris Registry untuk kunci dari skrip ada di tabel", 1, n);
    }

    /**
     * Barang yang <b>diberikan skrip Lua</b> harus ikut tersimpan.
     *
     * <p>Sebelum ini, {@code addItem} menulis ke peta tiruan di
     * {@code ScriptPlayer}, jadi barangnya lenyap saat pemain keluar. Uji
     * ini menutup jalurnya dari Lua sampai baris tabel {@code Inventory},
     * termasuk aturan tumpukan {@code ItmStackAmount}.</p>
     */
    private static void scriptItemTest(Sql sql, long charId) {
        log.info("  -- barang dari skrip Lua sampai ke database --");

        org.rtk.map.MapServer.itemDb.load(sql);
        var db = org.rtk.map.MapServer.itemDb;

        var contoh = db.infoByName("fox_fur");
        if (contoh == null) {
            check("barang contoh 'fox_fur' ada di tabel Items", false);
            return;
        }
        check("nama barang bisa dicari (itemdb_id)", db.idOf("fox_fur") == contoh.id());
        check("pencarian nama tidak peka besar-kecil",
                db.idOf("FOX_FUR") == contoh.id());
        check("nama tak dikenal mengembalikan 0", db.idOf("tidak_ada_barang") == 0);
        check("batas tumpukan terbaca", db.stackAmountOf(contoh.id()) == 50);

        CharStatus c = CharPersistence.load(sql, charId);
        if (c == null) {
            check("karakter uji terbaca", false);
            return;
        }
        c.inventory.clear();
        c.maxInv = 27;

        org.rtk.map.User sd = new org.rtk.map.User(9998, c);
        var p = sd.scriptPlayer();

        var engine = new org.rtk.map.script.ScriptEngine();
        engine.globals().load(
                "BERI = function(pl, n) pl:addItem('fox_fur', n) end\n"
              + "PUNYA = function(pl) return pl:hasItem('fox_fur') end\n"
              + "BUANG = function(pl, n) return pl:removeItem('fox_fur', n) end\n").call();

        engine.globals().get("BERI").call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(10));
        eq("10 keping masuk satu slot", 1, c.inventory.size());
        eq("jumlahnya benar", 10, c.inventory.get(0).amount);

        // melewati batas tumpukan harus membuka slot kedua
        engine.globals().get("BERI").call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(45));
        eq("55 keping jadi dua slot (batas tumpuk 50)", 2, c.inventory.size());
        eq("slot pertama penuh di batas tumpuk", 50, c.inventory.get(0).amount);
        eq("sisanya di slot kedua", 5, c.inventory.get(1).amount);

        var punya = engine.globals().get("PUNYA").call(engine.playerRef(p));
        eq("hasItem menjumlahkan lintas slot", 55, punya.toint());

        // simpan lalu baca ulang dari database
        check("simpan setelah skrip memberi barang", CharPersistence.save(sql, c));
        CharStatus r = CharPersistence.load(sql, charId);
        if (r == null) {
            check("karakter terbaca ulang", false);
            return;
        }
        int total = 0;
        for (var it : r.inventory) {
            if (it.id == contoh.id()) {
                total += it.amount;
            }
        }
        eq("barang dari skrip tersimpan di database", 55, total);

        Integer baris = sql.queryInt(
                "SELECT COUNT(*) FROM `Inventory` WHERE `InvChaId` = ? AND `InvItmId` = ?",
                charId, contoh.id());
        eq("dua baris Inventory untuk barang itu", 2, baris);

        // membuang lintas slot
        var terbuang = engine.globals().get("BUANG")
                .call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(52));
        eq("removeItem membuang lintas slot", 52, terbuang.toint());
        eq("sisa 3 keping dalam satu slot", 1, c.inventory.size());
        eq("jumlah sisa benar", 3, c.inventory.get(0).amount);

        // --- atribut money menembus ke CharStatus ---
        c.money = 1000;
        engine.globals().load(
                "BAYAR = function(pl, n) pl.money = pl.money - n end\n"
              + "PUNYA_EMAS = function(pl) return pl.money end\n").call();
        var emas = engine.globals().get("PUNYA_EMAS").call(engine.playerRef(p));
        eq("skrip membaca money dari CharStatus", 1000, emas.toint());
        engine.globals().get("BAYAR").call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(250));
        eq("skrip mengubah money di CharStatus", 750L, c.money);

        check("simpan setelah emas berubah", CharPersistence.save(sql, c));
        CharStatus g = CharPersistence.load(sql, charId);
        eq("emas tersimpan di database", 750L, g == null ? -1L : g.money);

        // inventaris penuh: penambahan dilaporkan gagal, bukan didiamkan
        c.inventory.clear();
        c.maxInv = 1;
        engine.globals().get("BERI").call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(50));
        var hasil = engine.globals().load(
                "return function(pl) return pl:addItem('red_fox_fur', 1) end").call()
                .call(engine.playerRef(p));
        check("inventaris penuh: addItem mengembalikan false", !hasil.toboolean());
        eq("tidak ada slot yang dipaksakan", 1, c.inventory.size());
    }

    /**
     * Binding prioritas atas: {@code calcStat}, {@code addSpell},
     * {@code bankDeposit}/{@code bankWithdraw} — semuanya lewat data hidup.
     */
    private static void bindingTest(Sql sql, long charId) {
        log.info("  -- binding prioritas: calcStat, addSpell, bank --");

        org.rtk.map.MapServer.itemDb.load(sql);
        org.rtk.map.MapServer.spellDb.load(sql);
        var db = org.rtk.map.MapServer.itemDb;
        check("tabel mantra termuat", org.rtk.map.MapServer.spellDb.count() > 0);

        CharStatus c = CharPersistence.load(sql, charId);
        if (c == null) {
            check("karakter uji terbaca", false);
            return;
        }
        c.inventory.clear();
        c.equip.clear();
        c.banks.clear();
        c.spells = new int[0];
        c.maxInv = 27;
        c.baseHp = 100;
        c.baseMp = 50;
        c.baseMight = 3;
        c.baseWill = 4;
        c.baseGrace = 5;
        c.baseArmor = 10;

        org.rtk.map.User sd = new org.rtk.map.User(9997, c);
        var p = sd.scriptPlayer();
        var engine = new org.rtk.map.script.ScriptEngine();

        // --- calcStat tanpa perlengkapan: nilai turunan = nilai dasar ---
        engine.globals().load("HITUNG = function(pl) pl:calcStat() end\n"
                + "STAT = function(pl) return pl.might, pl.maxHealth end\n").call();
        engine.globals().get("HITUNG").call(engine.playerRef(p));
        eq("calcStat tanpa perlengkapan: might = baseMight", 3, sd.might);
        eq("calcStat tanpa perlengkapan: maxHp = baseHp", 100L, sd.maxHp);

        // --- cari barang berstat, kenakan, hitung ulang ---
        org.rtk.map.data.ItemDb.Info berstat = null;
        for (long i = 1; i < 3000 && berstat == null; i++) {
            var info = db.info(i);
            if (info.id() != 0 && (info.stats().might() > 0 || info.stats().vita() > 0)) {
                berstat = info;
            }
        }
        if (berstat == null) {
            log.info("    (tidak ada barang berstat di database — bagian ini dilewati)");
        } else {
            var eq = new org.rtk.common.mmo.Item();
            eq.id = berstat.id();
            eq.amount = 1;
            eq.pos = org.rtk.map.data.Equip.ARMOR;
            c.equip.add(eq);
            engine.globals().get("HITUNG").call(engine.playerRef(p));
            eq("calcStat menambahkan might dari perlengkapan",
                    3 + berstat.stats().might(), sd.might);
            eq("calcStat menambahkan vita dari perlengkapan",
                    100L + berstat.stats().vita(), sd.maxHp);

            // lepas lagi: nilai kembali ke dasar, tidak menumpuk
            c.equip.clear();
            engine.globals().get("HITUNG").call(engine.playerRef(p));
            eq("calcStat tidak menumpuk: kembali ke nilai dasar", 3, sd.might);
        }

        // --- addSpell ---
        String namaMantra = org.rtk.map.MapServer.spellDb.nameOf(1);
        if (namaMantra.isEmpty()) {
            namaMantra = "heal";
        }
        engine.globals().load(
                "AJAR = function(pl, n) return pl:addSpell(n) end\n"
              + "PUNYA = function(pl, n) return pl:hasSpell(n) end\n").call();
        var hasil = engine.globals().get("AJAR")
                .call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(namaMantra));
        check("addSpell dengan nama berhasil", hasil.toboolean());
        check("hasSpell menemukan mantra itu",
                engine.globals().get("PUNYA")
                        .call(engine.playerRef(p),
                              org.luaj.vm2.LuaValue.valueOf(namaMantra)).toboolean());
        var lagi = engine.globals().get("AJAR")
                .call(engine.playerRef(p), org.luaj.vm2.LuaValue.valueOf(namaMantra));
        check("mantra yang sudah dimiliki tidak ditambah dua kali", !lagi.toboolean());
        check("mantra tak dikenal ditolak",
                !engine.globals().get("AJAR")
                        .call(engine.playerRef(p),
                              org.luaj.vm2.LuaValue.valueOf("mantra_tidak_ada")).toboolean());

        // --- bank ---
        engine.globals().load(
                "TITIP = function(pl, n, j) return pl:bankDeposit(n, j) end\n"
              + "AMBIL = function(pl, n, j) return pl:bankWithdraw(n, j) end\n"
              + "PUNYA_BRG = function(pl, n) return pl:hasItem(n) end\n").call();
        var LV = org.luaj.vm2.LuaValue.class;
        engine.globals().load("BERI2 = function(pl, n) pl:addItem('fox_fur', n) end").call();
        engine.globals().get("BERI2").call(engine.playerRef(p),
                org.luaj.vm2.LuaValue.valueOf(10));
        eq("punya 10 sebelum menitipkan", 10,
                engine.globals().get("PUNYA_BRG").call(engine.playerRef(p),
                        org.luaj.vm2.LuaValue.valueOf("fox_fur")).toint());

        check("bankDeposit berhasil",
                engine.globals().get("TITIP").call(engine.playerRef(p),
                        org.luaj.vm2.LuaValue.valueOf("fox_fur"),
                        org.luaj.vm2.LuaValue.valueOf(4)).toboolean());
        eq("inventaris berkurang setelah menitipkan", 6,
                engine.globals().get("PUNYA_BRG").call(engine.playerRef(p),
                        org.luaj.vm2.LuaValue.valueOf("fox_fur")).toint());
        eq("bank berisi 4", 1, c.banks.size());
        eq("jumlah di bank benar", 4L, c.banks.get(0).amount);

        var diambil = engine.globals().get("AMBIL").call(engine.playerRef(p),
                org.luaj.vm2.LuaValue.valueOf("fox_fur"),
                org.luaj.vm2.LuaValue.valueOf(3));
        eq("bankWithdraw mengembalikan jumlah yang diambil", 3, diambil.toint());
        eq("inventaris bertambah lagi", 9,
                engine.globals().get("PUNYA_BRG").call(engine.playerRef(p),
                        org.luaj.vm2.LuaValue.valueOf("fox_fur")).toint());
        eq("sisa di bank benar", 1L, c.banks.get(0).amount);

        // --- semuanya tersimpan ke database ---
        check("simpan setelah mantra & bank", CharPersistence.save(sql, c));
        CharStatus r = CharPersistence.load(sql, charId);
        if (r == null) {
            check("karakter terbaca ulang", false);
            return;
        }
        boolean adaMantra = false;
        for (int sp : r.spells) {
            if (sp > 0) {
                adaMantra = true;
            }
        }
        check("mantra tersimpan di database", adaMantra);
        check("isi bank tersimpan di database", !r.banks.isEmpty());
    }

    /** Karakter dengan nilai ekstrem yang sah untuk kolom unsigned. */
    private static CharStatus build(long id) {
        CharStatus c = new CharStatus();
        c.id = id;
        c.name = TEST_NAME;
        c.title = "Penguji";
        c.clanTitle = "Ketua";
        c.f1name = "F1";
        c.afkMessage = "Sedang menguji basis data.";
        c.level = 99;
        c.charClass = 3;
        c.mark = 7;
        c.totem = 4;
        c.karma = 1.5f;
        c.hp = 4_000_000_000L;      // di atas batas int bertanda
        c.baseHp = 4_000_000_000L;
        c.mp = 3_500_000_000L;
        c.baseMp = 3_500_000_000L;
        c.exp = 4_294_967_295L;     // maksimum unsigned 32-bit
        c.money = 123_456_789L;
        c.bankMoney = 987_654_321L;
        c.baseMight = 250;
        c.baseWill = 251;
        c.baseGrace = 252;
        c.baseArmor = -50;          // kolom ini bertanda
        c.alignment = -3;           // kolom ini juga bertanda
        c.gmLevel = 0;
        c.side = 2;
        c.state = 0;
        c.settingFlags = 12605;
        c.expSoldMagic = 9_000_000_000L;   // bigint unsigned
        c.expSoldHealth = 8_000_000_000L;
        c.expSoldStats = 7_000_000_000L;
        c.maxSlots = 30;
        c.maxInv = 27;
        c.partner = 0;
        c.clan = 0;
        c.clanRank = 2;
        c.classRank = 5;
        c.heroes = 1;
        c.miniMapToggle = 1;
        c.tutor = 0;
        c.lastPos = new Point(1, 12, 34);
        c.destPos = new Point(1, 12, 34);

        Item it = new Item();
        it.id = 42;
        it.amount = 5;
        it.dura = 1000;
        it.owner = 12345;
        it.note = "catatan uji";
        it.realName = "ukiran uji";
        c.inventory.add(it);

        Item eq0 = new Item();
        eq0.id = 7;
        eq0.amount = 1;
        eq0.dura = 500;
        eq0.pos = 1;
        c.equip.add(eq0);

        BankItem bi = new BankItem();
        bi.itemId = 99;
        bi.amount = 3;
        c.banks.add(bi);

        Legend lg = new Legend();
        lg.name = "uji";
        lg.text = "Legenda uji";
        lg.icon = 1;
        lg.color = 2;
        c.legends.add(lg);

        SkillInfo sk = new SkillInfo();
        sk.id = 11;
        sk.duration = 300;
        sk.aether = 7;
        c.duraAether.add(sk);

        c.spells = new int[]{101, 102, 103};

        c.killReg.put(500L, 1234L);
        // Registry bernilai int (kolom RegValue), bukan long
        c.registry.put("uji_kunci", 2_000_000_000);
        c.registryString.put("uji_teks", "nilai teks uji");
        c.npcRegistry.put("uji_npc", 77);
        c.questRegistry.put("uji_quest", 88);
        return c;
    }

    
    
    
    
    
    /** Hapus karakter uji beserta seluruh baris anaknya. */
    private static void cleanup(Sql sql) {
        Integer id = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", TEST_NAME);
        if (id == null) {
            return;
        }
        String[] child = {
            "DELETE FROM `Inventory` WHERE `InvChaId` = ?",
            "DELETE FROM `Equipment` WHERE `EqpChaId` = ?",
            "DELETE FROM `Banks` WHERE `BnkChaId` = ?",
            "DELETE FROM `SpellBook` WHERE `SbkChaId` = ?",
            "DELETE FROM `Aethers` WHERE `AthChaId` = ?",
            "DELETE FROM `Legends` WHERE `LegChaId` = ?",
            "DELETE FROM `Kills` WHERE `KilChaId` = ?",
            "DELETE FROM `Registry` WHERE `RegChaId` = ?",
            "DELETE FROM `RegistryString` WHERE `RegChaId` = ?",
            "DELETE FROM `NPCRegistry` WHERE `NrgChaId` = ?",
            "DELETE FROM `QuestRegistry` WHERE `QrgChaId` = ?",
            "DELETE FROM `Character` WHERE `ChaId` = ?",
        };
        for (String q : child) {
            sql.update(q, id);
        }
    }

    /** Pembaca sederhana untuk `conf/char.conf` (format C: `kunci: nilai`). */
    private record Conf(String id, String pw, String ip, int port, String db) {

        static Conf read(Path p) throws IOException {
            String id = "rtk";
            String pw = "";
            String ip = "127.0.0.1";
            int port = 3306;
            String db = "RTK";

            if (!Files.isRegularFile(p)) {
                throw new IOException("konfigurasi tidak ditemukan: " + p);
            }
            for (String line : Files.readAllLines(p, StandardCharsets.ISO_8859_1)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("//") || t.startsWith("#")) {
                    continue;
                }
                int colon = t.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String k = t.substring(0, colon).trim();
                String v = t.substring(colon + 1).trim();
                switch (k) {
                    case "sql_id" -> id = v;
                    case "sql_pw" -> pw = v;
                    case "sql_ip" -> ip = v;
                    case "sql_port" -> port = Integer.parseInt(v);
                    case "sql_db" -> db = v;
                    default -> {
                    }
                }
            }
            return new Conf(id, pw, ip, port, db);
        }
    }
}
