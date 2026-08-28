package org.rtk.map.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.ast.Chunk;
import org.luaj.vm2.ast.Exp;
import org.luaj.vm2.ast.FuncBody;
import org.luaj.vm2.ast.Name;
import org.luaj.vm2.ast.NameResolver;
import org.luaj.vm2.ast.Stat;
import org.luaj.vm2.ast.TableConstructor;
import org.luaj.vm2.ast.TableField;
import org.luaj.vm2.ast.Visitor;
import org.luaj.vm2.parser.LuaParser;

/**
 * Pemeriksa statis untuk 907 skrip Lua di {@code luascript/}.
 *
 * <p>{@code scripttest} hanya membuktikan seluruh berkas <b>termuat</b>.
 * Lua tidak memeriksa apa pun sampai baris itu benar-benar dijalankan, jadi
 * salah ketik nama fungsi baru meledak saat pemain menyentuh NPC-nya. Alat
 * ini mencari kesalahan semacam itu tanpa harus menjalankan skripnya.</p>
 *
 * <p>Parsernya <b>parser LuaJ yang sama</b> dengan yang dipakai server, jadi
 * yang diterima di sini persis yang diterima saat runtime.</p>
 *
 * <p>Daftar nama yang dianggap "ada" tidak ditebak, melainkan diambil dari
 * mesin skrip yang sudah hidup: {@link ScriptEngine#init} dijalankan dulu,
 * lalu tabel global dan prototipe {@code Player}/{@code NPC}/{@code Mob}
 * dibaca isinya. Dengan begitu binding Java, pustaka standar Lua, dan apa
 * pun yang didefinisikan skrip lain ikut terhitung.</p>
 *
 * <p>Yang diperiksa:</p>
 * <ol>
 *   <li>berkas gagal diparse;</li>
 *   <li>kunci ganda dalam satu tabel literal (yang belakangan menimpa diam-diam);</li>
 *   <li>fungsi bernama sama didefinisikan dua kali dalam satu berkas;</li>
 *   <li>nama parameter ganda pada satu fungsi;</li>
 *   <li>global dibaca padahal tidak pernah ada — dipisah antara
 *       <b>salah ketik</b> (mirip nama yang ada) dan <b>binding belum
 *       diport</b>;</li>
 *   <li>method dipanggil padahal tidak terdefinisi — dengan pemisahan yang sama.</li>
 * </ol>
 *
 * Jalankan: {@code ./run.sh luaaudit}
 */
public final class LuaAudit {

    private static final Logger log = LogManager.getLogger(LuaAudit.class);

    /**
     * Method milik pustaka standar Lua yang dipanggil dengan {@code :} pada
     * string, file, atau userdata — bukan pada objek permainan. Tanpa daftar
     * ini, {@code ("x"):gsub(...)} dan {@code file:write(...)} salah
     * dilaporkan sebagai method yang tidak terdefinisi.
     */
    private static final Set<String> STDLIB_METHODS = Set.of(
            // string
            "byte", "char", "dump", "find", "format", "gmatch", "gsub", "len",
            "lower", "match", "rep", "reverse", "sub", "upper",
            // file (io.open)
            "close", "flush", "lines", "read", "seek", "setvbuf", "write",
            // dipanggil pada userdata di scripts.lua (dijaga type(t)=='userdata')
            "dim", "size");

    /** Nama global yang memang disediakan runtime tapi tidak muncul di tabel global. */
    private static final Set<String> ALWAYS_PRESENT = Set.of(
            "self", "arg", "_ENV", "_G", "...");

    private LuaAudit() {
    }

    // ---- hasil per berkas ----
    private record Finding(String file, int line, String kind, String detail) {
    }

    private static final List<Finding> findings = new ArrayList<>();

    /** Nama yang terdaftar sebagai binding di `sl.c` versi C, bila sumbernya ada. */
    private static Set<String> cNamesRef = Set.of();

    /**
     * Baca nama binding dari `sl.c` versi C bila repo sumbernya masih ada.
     *
     * Ini yang memisahkan dua hal yang kelihatan sama: nama yang <b>ada di C
     * tapi belum diport</b> (celah yang sudah diketahui, bukan bug skrip) dan
     * nama yang <b>tidak ada di mana pun</b> — itu salah ketik atau kode mati.
     */
    private static Set<String> bacaNamaC() {
        Set<String> out = new TreeSet<>();
        for (String cand : new String[]{
            "../RTK-Server/rtk/src/map/sl.c", "RTK-Server/rtk/src/map/sl.c"}) {
            Path p = Paths.get(cand);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                String src = Files.readString(p, java.nio.charset.StandardCharsets.ISO_8859_1);
                // ⚠️ Buang baris yang DIKOMENTARI lebih dulu. Tanpa ini,
                // pendaftaran yang sengaja dimatikan di C ikut terhitung
                // "ada di sl.c", sehingga binding yang sebenarnya TIDAK ADA
                // di server aslinya pun dilaporkan sebagai celah port.
                // Kejadian pada `addActivationKey`/`checkActivationKey`,
                // yang baris typel_extendproto-nya dikomentari.
                src = buangKomentar(src);
                // PENTING: jangan ambil semua literal string dari sl.c. Di sana
                // nama global, nama method, DAN nama properti objek ("might",
                // "level", ...) semuanya berupa string, sehingga menyamaratakan
                // membuat salah ketik seperti `might.might` terlihat sah.
                // Yang diambil hanya titik pendaftaran yang sebenarnya.
                tarik(src, "lua_register\\(sl_gstate, *\"([A-Za-z0-9_]+)\"", out);
                tarik(src, "typel_extendproto\\([^,]*, *\"([A-Za-z0-9_]+)\"", out);
                tarik(src, "lua_setglobal\\(sl_gstate, *\"([A-Za-z0-9_]+)\"", out);
                // Konstanta enum dipasang lewat makro SL_EXPOSE_ENUM(X) yang
                // memakai operator stringify `#val`, jadi namanya tidak pernah
                // muncul sebagai literal string.
                tarik(src, "SL_EXPOSE_ENUM\\(([A-Za-z_][A-Za-z0-9_]*)\\)", out);
            } catch (IOException e) {
                log.warn("tidak bisa membaca {}: {}", p, e.getMessage());
            }
            break;
        }
        return out;
    }

    /**
     * Hapus komentar C dari sumber sebelum nama binding diambil.
     *
     * <p>Sederhana dengan sengaja: hanya {@code //} sampai akhir baris dan
     * blok {@code /* … *&#47;}. Cukup karena yang dicari hanya baris
     * pendaftaran, dan tidak ada literal string di sl.c yang memuat
     * penanda komentar.</p>
     */
    private static String buangKomentar(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean blok = false;
        for (int i = 0; i < src.length(); i++) {
            if (blok) {
                if (src.startsWith("*/", i)) {
                    blok = false;
                    i++;
                }
                continue;
            }
            if (src.startsWith("/*", i)) {
                blok = true;
                i++;
                continue;
            }
            if (src.startsWith("//", i)) {
                int nl = src.indexOf('\n', i);
                if (nl < 0) {
                    break;
                }
                i = nl;
                out.append('\n');
                continue;
            }
            out.append(src.charAt(i));
        }
        return out.toString();
    }

    private static void report(String file, int line, String kind, String detail) {
        findings.add(new Finding(file, line, kind, detail));
    }

    public static void main(String[] args) {
        String root = args.length > 0 ? args[0] : "luascript";
        try {
            run(root);
        } catch (IOException e) {
            log.error("audit gagal", e);
            System.exit(1);
        }
    }

    private static void run(String root) throws IOException {
        Path base = Paths.get(root);
        if (!Files.isDirectory(base)) {
            log.error("folder skrip tidak ditemukan: {}", base.toAbsolutePath());
            System.exit(1);
        }

        List<Path> files;
        try (var s = Files.walk(base)) {
            files = s.filter(p -> p.toString().endsWith(".lua"))
                    .sorted()
                    .toList();
        }
        log.info("=== audit {} berkas Lua di {} ===", files.size(), base);

        // --- apa saja yang benar-benar ada saat runtime ---
        log.info("menyalakan mesin skrip untuk mendata nama yang tersedia...");
        ScriptEngine engine = new ScriptEngine();
        int loadErrors = engine.init(root);
        if (loadErrors > 0) {
            log.error("{} berkas gagal dimuat — perbaiki itu dulu", loadErrors);
        }
        Set<String> runtimeGlobals = keysOf(engine.globals());
        Set<String> runtimeMethods = new TreeSet<>();
        runtimeMethods.addAll(keysOf(engine.playerClass.proto));
        runtimeMethods.addAll(keysOf(engine.npcClass.proto));
        runtimeMethods.addAll(keysOf(engine.mobClass.proto));
        // ⚠️ Prototipe yang TIDAK terdaftar di sini membuat method miliknya
        // terhitung "belum diport" selamanya. `FloorItem` sempat terlewat,
        // sehingga `addTrapSpotters`/`getTrapSpotters` tetap dilaporkan
        // sebagai celah padahal sudah ada. Setiap kali ScriptEngine dapat
        // prototipe baru, tambahkan di sini juga.
        runtimeMethods.addAll(keysOf(engine.floorItemClass.proto));
        runtimeMethods.addAll(keysOf(engine.boundItemClass.proto));
        log.info("tersedia saat runtime: {} global, {} method prototipe",
                runtimeGlobals.size(), runtimeMethods.size());

        // --- lintasan 1: parse + kumpulkan definisi ---
        Map<Path, Chunk> parsed = new HashMap<>();
        int parseErrors = 0;
        for (Path p : files) {
            try (InputStream in = Files.newInputStream(p)) {
                LuaParser parser = new LuaParser(in, "UTF-8");
                Chunk chunk = parser.Chunk();
                chunk.accept(new NameResolver());
                parsed.put(p, chunk);
            } catch (Exception e) {
                parseErrors++;
                report(rel(base, p), 0, "PARSE", singkat(e.getMessage()));
            }
        }

        Set<String> definedGlobals = new TreeSet<>(runtimeGlobals);
        Set<String> definedMethods = new TreeSet<>(runtimeMethods);
        for (var e : parsed.entrySet()) {
            DefCollector dc = new DefCollector();
            e.getValue().accept(dc);
            definedGlobals.addAll(dc.globals);
            definedMethods.addAll(dc.methods);
        }
        log.info("terdefinisi di seluruh korpus: {} global, {} method",
                definedGlobals.size(), definedMethods.size());

        // --- lintasan 2: cari masalah ---
        Set<String> stubGlobals = new TreeSet<>(engine.stubNames());
        Map<String, Integer> stubUse = new TreeMap<>();
        Map<String, String> stubSite = new HashMap<>();
        Map<String, Integer> missingGlobalUse = new TreeMap<>();
        Map<String, Integer> dangerUse = new TreeMap<>();
        Map<String, Integer> missingMethodUse = new TreeMap<>();
        Map<String, String> firstSite = new HashMap<>();

        for (var e : parsed.entrySet()) {
            String name = rel(base, e.getKey());
            Checker ch = new Checker(name);
            e.getValue().accept(ch);

            for (var u : ch.globalReads.entrySet()) {
                String g = u.getKey();
                // Dipakai laporan stub di bawah: sebuah stub TERDEFINISI,
                // jadi ia tidak akan pernah masuk daftar "tak dikenal" —
                // pemakaiannya harus dihitung terpisah.
                if (stubGlobals.contains(g)) {
                    stubUse.merge(g, u.getValue().count, Integer::sum);
                    stubSite.putIfAbsent(g, name + ":" + u.getValue().line);
                }
                if (definedGlobals.contains(g) || ALWAYS_PRESENT.contains(g)) {
                    continue;
                }
                missingGlobalUse.merge(g, u.getValue().count, Integer::sum);
                firstSite.putIfAbsent("g:" + g, name + ":" + u.getValue().line);
            }
            for (var u : ch.globalDanger.entrySet()) {
                String g = u.getKey();
                if (definedGlobals.contains(g) || ALWAYS_PRESENT.contains(g)) {
                    continue;
                }
                dangerUse.merge(g, u.getValue().count, Integer::sum);
                firstSite.putIfAbsent("d:" + g, name + ":" + u.getValue().line);
            }
            for (var u : ch.methodCalls.entrySet()) {
                String m = u.getKey();
                if (definedMethods.contains(m) || STDLIB_METHODS.contains(m)) {
                    continue;
                }
                missingMethodUse.merge(m, u.getValue().count, Integer::sum);
                firstSite.putIfAbsent("m:" + m, name + ":" + u.getValue().line);
            }
        }

        // --- silang dengan binding versi C ---
        Set<String> cNames = bacaNamaC();
        if (!cNames.isEmpty()) {
            log.info("binding terdaftar di sl.c versi C: {} nama", cNames.size());
        }

        // --- laporan ---
        cNamesRef = cNames;
        cetakTemuan(parseErrors);
        cetakBahaya(dangerUse, firstSite);
        cetakHilang("GLOBAL", missingGlobalUse, definedGlobals, firstSite, "g:");
        cetakHilang("METHOD", missingMethodUse, definedMethods, firstSite, "m:");
        cetakStub(stubGlobals, stubUse, stubSite, cNames);

        int serius = parseErrors
                + (int) findings.stream().filter(f -> !f.kind.equals("PARSE")).count();
        log.info("=== ringkasan ===");
        log.info("berkas diparse      : {} / {}", parsed.size(), files.size());
        log.info("gagal parse         : {}", parseErrors);
        log.info("temuan dalam berkas : {}", serius - parseErrors);
        log.info("global tak dikenal  : {} nama", missingGlobalUse.size());
        log.info("global masih STUB   : {} nama ({} ada di sl.c)",
                stubGlobals.size(),
                stubGlobals.stream().filter(cNames::contains).count());
        log.info("method tak dikenal  : {} nama", missingMethodUse.size());

        if (parseErrors > 0) {
            log.error("ADA BERKAS YANG GAGAL DIPARSE");
            System.exit(1);
        }
        log.info("Semua berkas Lua valid secara sintaks.");
    }

    // ------------------------------------------------------------------
    // laporan
    // ------------------------------------------------------------------

    /**
     * Global yang <b>terpasang sebagai stub</b> — terdefinisi, tetapi tidak
     * melakukan apa pun.
     *
     * <p>⚠️ Ini menutup blind spot yang paling mahal di alat ini. Stub
     * adalah fungsi yang sah: ia tidak melempar error, hanya menulis WARN
     * sekali lalu mengembalikan nil. Karena itu ia <b>tidak pernah</b>
     * muncul di daftar "tak dikenal" maupun "AKAN MELEDAK" — dan sampai
     * 27 Agustus 2026 audit melaporkan "0 celah global" sementara 61
     * binding yang terdaftar di {@code sl.c} masih stub, salah satunya
     * dipakai <b>530x</b>.</p>
     *
     * <p>Diurutkan menurut jumlah pemakaian, karena itulah yang menentukan
     * mana yang paling mahal dibiarkan.</p>
     */
    private static void cetakStub(Set<String> stubs, Map<String, Integer> use,
                                  Map<String, String> site, Set<String> cNames) {
        if (stubs.isEmpty()) {
            log.info("--- tidak ada global yang masih stub ---");
            return;
        }
        java.util.List<String> adaDiC = new java.util.ArrayList<>();
        java.util.List<String> tidakAdaDiC = new java.util.ArrayList<>();
        java.util.List<String> urut = new java.util.ArrayList<>(stubs);
        urut.sort((a, b) -> Integer.compare(use.getOrDefault(b, 0), use.getOrDefault(a, 0)));
        for (String n : urut) {
            String baris = String.format("%-26s %5dx  (mis. %s)",
                    n, use.getOrDefault(n, 0), site.getOrDefault(n, "-"));
            if (cNames.contains(n)) {
                adaDiC.add(baris);
            } else {
                tidakAdaDiC.add(baris);
            }
        }

        log.warn("--- GLOBAL masih STUB dan ADA di sl.c (celah port yang TIDAK "
                + "terlihat di daftar mana pun di atas): {} nama ---", adaDiC.size());
        long batas = Boolean.getBoolean("rtk.audit.penuh") ? Long.MAX_VALUE : 10;
        adaDiC.stream().limit(batas).forEach(a -> log.warn("  {}", a));
        if (adaDiC.size() > batas) {
            log.warn("  ... dan {} lagi (pakai -Drtk.audit.penuh=true)",
                    adaDiC.size() - batas);
        }

        if (!tidakAdaDiC.isEmpty()) {
            log.info("--- GLOBAL masih stub tetapi TIDAK terdaftar di sl.c "
                    + "(kode mati di konten): {} nama ---", tidakAdaDiC.size());
            tidakAdaDiC.stream().limit(batas).forEach(a -> log.info("  {}", a));
        }
    }

    /** Kumpulkan grup 1 dari setiap kecocokan pola ke dalam {@code out}. */
    private static void tarik(String src, String regex, Set<String> out) {
        var m = java.util.regex.Pattern.compile(regex).matcher(src);
        while (m.find()) {
            out.add(m.group(1));
        }
    }

    /**
     * Global yang tidak ada TAPI dipanggil/diindeks — ini yang benar-benar
     * meledak saat dijalankan, bukan sekadar bernilai nil.
     */
    private static void cetakBahaya(Map<String, Integer> danger, Map<String, String> site) {
        List<String> nyata = new ArrayList<>();
        for (var e : danger.entrySet()) {
            if (cNamesRef.contains(e.getKey())) {
                continue; // binding C yang belum diport — celah yang sudah diketahui
            }
            nyata.add(String.format("%-24s dipakai %dx  (mis. %s)",
                    e.getKey(), e.getValue(), site.get("d:" + e.getKey())));
        }
        if (nyata.isEmpty()) {
            log.info("--- tidak ada global tak terdefinisi yang dipanggil/diindeks ---");
            return;
        }
        log.error("--- AKAN MELEDAK SAAT DIJALANKAN: {} global tak terdefinisi "
                + "dipanggil atau diindeks ---", nyata.size());
        nyata.forEach(n -> log.error("  {}", n));
    }

    private static void cetakTemuan(int parseErrors) {
        if (findings.isEmpty()) {
            log.info("--- tidak ada kunci ganda / definisi ganda / parameter ganda ---");
            return;
        }
        log.info("--- temuan dalam berkas ---");
        findings.stream()
                .sorted(Comparator.comparing((Finding f) -> f.kind).thenComparing(f -> f.file))
                .forEach(f -> {
                    String where = f.line > 0 ? f.file + ":" + f.line : f.file;
                    if (f.kind.equals("PARSE")) {
                        log.error("  [{}] {} — {}", f.kind, where, f.detail);
                    } else {
                        log.warn("  [{}] {} — {}", f.kind, where, f.detail);
                    }
                });
        if (parseErrors == 0) {
            log.info("  ({} temuan)", findings.size());
        }
    }

    /**
     * Pisahkan nama tak dikenal menjadi dua: yang <b>mirip</b> nama yang ada
     * (kemungkinan besar salah ketik — bug nyata) dan sisanya (subsistem yang
     * memang belum diport).
     */
    private static void cetakHilang(String label, Map<String, Integer> use,
                                    Set<String> defined, Map<String, String> site, String prefix) {
        if (use.isEmpty()) {
            log.info("--- tidak ada {} tak dikenal ---", label);
            return;
        }

        List<String> typo = new ArrayList<>();
        List<String> takAdaDiManaPun = new ArrayList<>();
        List<String> belumDiport = new ArrayList<>();
        for (String n : use.keySet()) {
            boolean adaDiC = cNamesRef.contains(n);
            String mirip = adaDiC ? null : palingMirip(n, defined);
            String where = site.get(prefix + n);
            if (mirip != null) {
                typo.add(String.format("%-26s -> mungkin '%s'  (%dx, mis. %s)",
                        n, mirip, use.get(n), where));
            } else if (adaDiC) {
                belumDiport.add(String.format("%-26s %dx  (mis. %s)", n, use.get(n), where));
            } else {
                takAdaDiManaPun.add(String.format("%-26s %dx  (mis. %s)", n, use.get(n), where));
            }
        }

        if (!typo.isEmpty()) {
            log.warn("--- {} TAK DIKENAL yang mirip nama lain (kandidat salah ketik) ---", label);
            typo.forEach(t -> log.warn("  {}", t));
        } else {
            log.info("--- tidak ada {} tak dikenal yang mirip nama lain ---", label);
        }

        if (!takAdaDiManaPun.isEmpty()) {
            log.warn("--- {} yang TIDAK ADA di mana pun (bukan di sl.c, bukan di Lua) — "
                    + "salah ketik atau kode mati: {} nama ---", label, takAdaDiManaPun.size());
            takAdaDiManaPun.forEach(a -> log.warn("  {}", a));
        }

        log.info("--- {} ada di sl.c tapi BELUM DIPORT (celah yang sudah diketahui): {} nama ---",
                label, belumDiport.size());
        // -Drtk.audit.penuh=true mencetak daftar utuh; default 10 agar ringkas.
        long batas = Boolean.getBoolean("rtk.audit.penuh") ? Long.MAX_VALUE : 10;
        belumDiport.stream().limit(batas).forEach(a -> log.info("  {}", a));
        // Baris "dan N lagi" hanya benar bila daftarnya MEMANG dipotong.
        // Sebelumnya ia ikut tercetak pada mode penuh dan mengabarkan 73 nama
        // tersembunyi yang sebenarnya sudah tercetak semua.
        if (belumDiport.size() > batas) {
            log.info("  ... dan {} lagi", belumDiport.size() - batas);
        }
    }

    /** Nama terdefinisi yang jaraknya <=1 sunting, atau beda huruf besar-kecil saja. */
    private static String palingMirip(String n, Set<String> defined) {
        String lower = n.toLowerCase();
        for (String d : defined) {
            if (!d.equals(n) && d.toLowerCase().equals(lower)) {
                return d;
            }
        }
        // jarak sunting 1, hanya untuk nama yang cukup panjang agar tidak berisik
        if (n.length() < 5) {
            return null;
        }
        for (String d : defined) {
            if (Math.abs(d.length() - n.length()) <= 1 && d.length() >= 5 && jarak1(n, d)) {
                return d;
            }
        }
        return null;
    }

    /** true bila a dan b berbeda tepat satu sunting (ganti/sisip/hapus). */
    private static boolean jarak1(String a, String b) {
        if (a.equals(b)) {
            return false;
        }
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > 1) {
            return false;
        }
        int i = 0;
        while (i < la && i < lb && a.charAt(i) == b.charAt(i)) {
            i++;
        }
        int j = 0;
        while (j < la - i && j < lb - i && a.charAt(la - 1 - j) == b.charAt(lb - 1 - j)) {
            j++;
        }
        return la - i - j <= 1 && lb - i - j <= 1;
    }

    // ------------------------------------------------------------------
    // pengumpul definisi
    // ------------------------------------------------------------------

    /** Kumpulkan global dan method yang DIDEFINISIKAN sebuah berkas. */
    private static final class DefCollector extends Visitor {
        final Set<String> globals = new LinkedHashSet<>();
        final Set<String> methods = new LinkedHashSet<>();

        @Override
        public void visit(Stat.FuncDef stat) {
            // function a.b.c:d() -> global 'a', method 'd'
            globals.add(stat.name.name.name);
            if (stat.name.method != null) {
                methods.add(stat.name.method);
            } else if (stat.name.dots != null && !stat.name.dots.isEmpty()) {
                methods.add(String.valueOf(stat.name.dots.get(stat.name.dots.size() - 1)));
            }
            super.visit(stat);
        }

        @Override
        public void visit(Stat.Assign stat) {
            for (Object o : stat.vars) {
                if (o instanceof Exp.NameExp ne) {
                    globals.add(ne.name.name);
                } else if (o instanceof Exp.FieldExp fe) {
                    // Player.foo = function() ... end  -> method 'foo'
                    methods.add(fe.name.name);
                }
            }
            super.visit(stat);
        }

        @Override
        public void visit(TableField field) {
            // { foo = function() ... end } juga menyediakan method
            if (field.name != null && field.rhs instanceof Exp.AnonFuncDef) {
                methods.add(field.name);
            }
            super.visit(field);
        }
    }

    // ------------------------------------------------------------------
    // pemeriksa
    // ------------------------------------------------------------------

    private static final class Use {
        int count;
        int line;
    }

    /** Cari pemakaian nama + pola bug dalam satu berkas. */
    private static final class Checker extends Visitor {
        private final String file;
        final Map<String, Use> globalReads = new HashMap<>();
        /** Global yang dipanggil atau diindeks — kalau tidak ada, ini CRASH. */
        final Map<String, Use> globalDanger = new HashMap<>();
        final Map<String, Use> methodCalls = new HashMap<>();
        private final Map<String, Integer> funcDefs = new HashMap<>();

        Checker(String file) {
            this.file = file;
        }

        private static void tally(Map<String, Use> m, String name, int line) {
            Use u = m.computeIfAbsent(name, k -> new Use());
            if (u.count == 0) {
                u.line = line;
            }
            u.count++;
        }

        @Override
        public void visit(Exp.NameExp exp) {
            Name n = exp.name;
            boolean local = n.variable != null && n.variable.isLocal();
            if (!local) {
                tally(globalReads, n.name, exp.beginLine);
            }
            super.visit(exp);
        }

        @Override
        public void visit(Exp.MethodCall exp) {
            tally(methodCalls, exp.name, exp.beginLine);
            bahaya(exp.lhs, exp.beginLine);
            super.visit(exp);
        }

        @Override
        public void visit(Exp.FuncCall exp) {
            bahaya(exp.lhs, exp.beginLine);
            super.visit(exp);
        }

        @Override
        public void visit(Exp.FieldExp exp) {
            bahaya(exp.lhs, exp.beginLine);
            super.visit(exp);
        }

        @Override
        public void visit(Exp.IndexExp exp) {
            bahaya(exp.lhs, exp.beginLine);
            super.visit(exp);
        }

        /**
         * Catat global yang dipakai sebagai penerima: dipanggil, diindeks,
         * atau diambil fieldnya. Membaca global tak terdefinisi di Lua hanya
         * menghasilkan nil dan itu sah (banyak skrip memang mengeceknya
         * dengan {@code ~= nil}); yang meledak adalah ketika nil itu
         * dipanggil atau diindeks.
         */
        private void bahaya(Exp lhs, int line) {
            if (lhs instanceof Exp.NameExp ne) {
                boolean local = ne.name.variable != null && ne.name.variable.isLocal();
                if (!local) {
                    tally(globalDanger, ne.name.name, line);
                }
            }
        }

        @Override
        public void visit(Stat.FuncDef stat) {
            StringBuilder sb = new StringBuilder(stat.name.name.name);
            if (stat.name.dots != null) {
                for (Object d : stat.name.dots) {
                    sb.append('.').append(d);
                }
            }
            if (stat.name.method != null) {
                sb.append(':').append(stat.name.method);
            }
            String full = sb.toString();
            Integer prev = funcDefs.put(full, stat.beginLine);
            if (prev != null) {
                report(file, stat.beginLine, "FUNGSI-GANDA",
                        "'" + full + "' sudah didefinisikan di baris " + prev
                                + "; definisi ini menimpanya");
            }
            super.visit(stat);
        }

        @Override
        public void visit(FuncBody body) {
            if (body.parlist != null && body.parlist.names != null) {
                Set<String> seen = new HashSet<>();
                for (Object o : body.parlist.names) {
                    String pn = o instanceof Name nm ? nm.name : String.valueOf(o);
                    if (!seen.add(pn)) {
                        report(file, body.beginLine, "PARAM-GANDA",
                                "parameter '" + pn + "' muncul dua kali");
                    }
                }
            }
            super.visit(body);
        }

        @Override
        public void visit(TableConstructor tc) {
            if (tc.fields != null) {
                Set<String> seen = new HashSet<>();
                for (Object o : tc.fields) {
                    if (o instanceof TableField tf && tf.name != null && !seen.add(tf.name)) {
                        report(file, tf.beginLine, "KUNCI-GANDA",
                                "kunci '" + tf.name + "' ditulis dua kali dalam tabel yang sama");
                    }
                }
            }
            super.visit(tc);
        }
    }

    // ------------------------------------------------------------------
    // bantu
    // ------------------------------------------------------------------

    private static Set<String> keysOf(LuaTable t) {
        Set<String> out = new TreeSet<>();
        LuaValue k = LuaValue.NIL;
        while (true) {
            Varargs n = t.next(k);
            k = n.arg1();
            if (k.isnil()) {
                break;
            }
            if (k.isstring()) {
                out.add(k.tojstring());
            }
        }
        return out;
    }

    private static String rel(Path base, Path p) {
        return base.relativize(p).toString();
    }

    private static String singkat(String s) {
        if (s == null) {
            return "(tanpa pesan)";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() <= 160 ? one : one.substring(0, 157) + "...";
    }
}
