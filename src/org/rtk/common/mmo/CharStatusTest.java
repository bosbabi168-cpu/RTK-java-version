package org.rtk.common.mmo;

import java.io.IOException;
import java.util.Random;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Uji round-trip {@link CharStatusCodec}: isi karakter dengan data lengkap,
 * serialisasi, baca ulang, lalu bandingkan seluruh field.
 *
 * Jalankan: {@code ./run.sh chartest}
 */
public final class CharStatusTest {

    private static final Logger log = LogManager.getLogger(CharStatusTest.class);
    private static int failures;

    /** sizeof(struct mmo_charstatus) pada build 64-bit, untuk pembanding. */
    private static final int C_STRUCT_BYTES = 3_171_352;

    private CharStatusTest() {
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            log.info("PASS: {}", what);
        } else {
            log.error("FAIL: {}", what);
            failures++;
        }
    }

    public static void main(String[] args) {
        try {
            run();
        } catch (IOException e) {
            log.error("gagal", e);
            System.exit(1);
        }
        if (failures == 0) {
            log.info("ALL CHARSTATUS TESTS PASSED");
        } else {
            log.error("{} TEST GAGAL", failures);
            System.exit(1);
        }
    }

    private static void run() throws IOException {
        // ---- karakter baru: harus jauh lebih kecil dari struct C ----
        CharStatus fresh = new CharStatus();
        fresh.id = 1;
        fresh.name = "Newbie";
        byte[] freshBlob = CharStatusCodec.encode(fresh);
        log.info("=== ukuran blob ===");
        log.info("struct C (mmo_charstatus)   : {} byte", String.format("%,d", C_STRUCT_BYTES));
        log.info("karakter baru (format Java) : {} byte", String.format("%,d", freshBlob.length));
        check("karakter baru < 1 KB (C: 3 MB)", freshBlob.length < 1024);

        // ---- karakter terisi penuh ----
        CharStatus c = sample();
        byte[] blob = CharStatusCodec.encode(c);
        log.info("karakter terisi penuh       : {} byte ({}x lebih kecil dari struct C)",
                String.format("%,d", blob.length), C_STRUCT_BYTES / Math.max(1, blob.length));

        CharStatus r = CharStatusCodec.decode(blob);

        log.info("=== round-trip ===");
        check("id, nama, password", r.id == c.id && r.name.equals(c.name) && r.password.equals(c.password));
        check("string panjang (afkMessage, title)",
                r.afkMessage.equals(c.afkMessage) && r.title.equals(c.title));
        check("statistik inti (hp/mp/exp/money)",
                r.hp == c.hp && r.baseHp == c.baseHp && r.mp == c.mp && r.exp == c.exp && r.money == c.money);
        check("nilai unsigned 32-bit besar (exp mendekati 4 miliar)", r.exp == c.exp && r.exp > 4_000_000_000L);
        check("karma (float)", Float.compare(r.karma, c.karma) == 0);
        check("expSold* (64-bit)",
                r.expSoldMagic == c.expSoldMagic && r.expSoldHealth == c.expSoldHealth
                        && r.expSoldStats == c.expSoldStats);
        check("nilai bertanda negatif (alignment, baseArmor)",
                r.alignment == c.alignment && r.baseArmor == c.baseArmor);
        check("posisi lastPos & destPos",
                r.lastPos.m == c.lastPos.m && r.lastPos.x == c.lastPos.x && r.lastPos.y == c.lastPos.y
                        && r.destPos.m == c.destPos.m);

        check("inventaris (jumlah & isi)", sameItems(c, r));
        check("perlengkapan terpakai", r.equip.size() == c.equip.size()
                && r.equip.get(0).realName.equals(c.equip.get(0).realName));
        check("legenda (teks panjang)", r.legends.size() == c.legends.size()
                && r.legends.get(0).text.equals(c.legends.get(0).text));
        check("timer spell (dura/aether)", r.duraAether.size() == c.duraAether.size()
                && r.duraAether.get(0).lastTickDura == c.duraAether.get(0).lastTickDura);
        check("bank", r.banks.size() == c.banks.size()
                && r.banks.get(0).realName.equals(c.banks.get(0).realName));
        check("buku mantra (52 slot)", java.util.Arrays.equals(r.spells, c.spells));

        check("registry angka", r.registry.equals(c.registry));
        check("registry string", r.registryString.equals(c.registryString));
        check("registry akun / npc / quest",
                r.accountRegistry.equals(c.accountRegistry)
                        && r.npcRegistry.equals(c.npcRegistry)
                        && r.questRegistry.equals(c.questRegistry));
        check("hitungan bunuh mob", r.killReg.equals(c.killReg));
        check("teks non-ASCII utuh (UTF-8)", r.registryString.get("catatan").equals(c.registryString.get("catatan")));

        // ---- blob rusak harus ditolak dengan jelas, bukan diam-diam salah ----
        log.info("=== penolakan blob tidak valid ===");
        byte[] bogus = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        check("blob acak ditolak", throwsIo(bogus));

        CharStatus v = sample();
        byte[] ok = CharStatusCodec.encode(v);
        byte[] tampered = ok.clone();
        tampered[tampered.length / 2] ^= 0x5A; // rusakkan di tengah
        check("blob yang dirusak ditolak", throwsIo(tampered));

        framingTest(c, blob);
        slotBerlubangTest();
    }

    /**
     * Kantong dengan slot BERLUBANG.
     *
     * <p>Ini yang dulu tidak diuji sama sekali, dan karenanya empat tempat
     * memakai indeks daftar sebagai nomor slot selama berbulan-bulan tanpa
     * ketahuan. Semua uji lama memakai kantong yang rapat 0..n-1, dan pada
     * kantong rapat indeks daftar <b>kebetulan</b> sama dengan nomor slot —
     * jadi kodenya lulus justru karena datanya terlalu jinak.</p>
     *
     * <p>Data nyata tidak jinak: kantong karakter uji terisi di slot
     * <b>0–5 lalu 21–26</b>, dan seluruh karakter yang punya barang berlubang
     * seperti itu.</p>
     */
    private static void slotBerlubangTest() {
        log.info("=== kantong berlubang ===");

        CharStatus c = new CharStatus();
        c.maxInv = 27;
        c.inventory.add(barang(101, 0));
        c.inventory.add(barang(102, 1));
        c.inventory.add(barang(103, 21));
        c.inventory.add(barang(104, 26));

        check("inventoryAt menemukan slot rapat", c.inventoryAt(1).id == 102);
        // ⚠️ Inti persoalannya: slot 21 ada di indeks daftar 2.
        check("inventoryAt menemukan slot JAUH di atas ukuran daftar",
                c.inventoryAt(21).id == 103 && c.inventoryAt(26).id == 104);
        check("slot kosong di tengah lubang mengembalikan null",
                c.inventoryAt(2) == null && c.inventoryAt(20) == null);

        check("slot kosong pertama = 2, bukan ukuran daftar (4)",
                c.firstFreeInventorySlot(c.maxInv) == 2);

        // Tabrakan yang dulu mungkin: barang di 0,1,2,4 → ukuran 4 → slot 4.
        CharStatus t = new CharStatus();
        t.maxInv = 27;
        for (int slot : new int[] {0, 1, 2, 4}) {
            t.inventory.add(barang(200 + slot, slot));
        }
        int bebas = t.firstFreeInventorySlot(t.maxInv);
        check("slot bebas 3 dipilih, bukan 4 yang sudah terisi",
                bebas == 3 && t.inventoryAt(4) != null);

        check("removeInventoryAt membuang slot yang BENAR",
                c.removeInventoryAt(21) && c.inventoryAt(21) == null
                        && c.inventoryAt(26) != null && c.inventoryAt(0) != null);
        check("removeInventoryAt pada slot kosong tidak membuang apa pun",
                !c.removeInventoryAt(20) && c.inventory.size() == 3);

        // Penuh berarti tidak ada slot bebas — bukan "ukuran daftar = maxInv".
        CharStatus penuh = new CharStatus();
        penuh.maxInv = 3;
        for (int slot = 0; slot < 3; slot++) {
            penuh.inventory.add(barang(300 + slot, slot));
        }
        check("kantong penuh mengembalikan -1", penuh.firstFreeInventorySlot(3) == -1);

        // Putar-balik simpan/muat: slot berlubang harus bertahan, termasuk
        // slot 0 yang bukan elemen pertama daftar.
        CharStatus u = new CharStatus();
        u.id = 77;
        u.name = "Berlubang";
        u.maxInv = 27;
        u.inventory.add(barang(401, 5));
        u.inventory.add(barang(402, 0));      // slot 0 SENGAJA bukan yang pertama
        u.inventory.add(barang(403, 26));
        try {
            CharStatus r = CharStatusCodec.decode(CharStatusCodec.encode(u));
            check("putar-balik: slot 0 tetap slot 0 walau bukan elemen pertama",
                    r.inventoryAt(0) != null && r.inventoryAt(0).id == 402);
            check("putar-balik: slot berlubang bertahan",
                    r.inventoryAt(5) != null && r.inventoryAt(5).id == 401
                            && r.inventoryAt(26) != null && r.inventoryAt(26).id == 403);
        } catch (IOException e) {
            check("putar-balik kantong berlubang: " + e.getMessage(), false);
        }
    }

    private static Item barang(int id, int slot) {
        Item it = new Item();
        it.id = id;
        it.amount = 1;
        it.pos = slot;
        return it;
    }

    /**
     * Uji tata letak byte paket 0x3803 (char->map) dan 0x3004 (map->char)
     * persis seperti yang ditulis/dibaca Mapif dan MapIntif. Offset yang
     * meleset satu byte adalah kesalahan paling mudah terjadi di sini —
     * versi C bahkan punya bug itu ({@code RFIFOL(fd,1)-6}).
     */
    private static void framingTest(CharStatus original, byte[] blob) throws IOException {
        log.info("=== framing paket ===");

        // ---- 0x3803: W(0)=opcode, L(2)=panjang total, W(6)=fd klien, blob@8
        final int HDR_LOAD = 8;
        int clientFd = 4321;
        byte[] pkt = new byte[HDR_LOAD + blob.length];
        putU16(pkt, 0, 0x3803);
        putU32(pkt, 2, HDR_LOAD + blob.length);
        putU16(pkt, 6, clientFd);
        System.arraycopy(blob, 0, pkt, HDR_LOAD, blob.length);

        check("0x3803 opcode terbaca", getU16(pkt, 0) == 0x3803);
        check("0x3803 panjang total cocok", getU32(pkt, 2) == pkt.length);
        check("0x3803 fd klien utuh", getU16(pkt, 6) == clientFd);
        int blobLen = getU32(pkt, 2) - HDR_LOAD;
        byte[] back = new byte[blobLen];
        System.arraycopy(pkt, HDR_LOAD, back, 0, blobLen);
        CharStatus loaded = CharStatusCodec.decode(back);
        check("0x3803 karakter utuh setelah framing",
                loaded.name.equals(original.name) && loaded.exp == original.exp
                        && loaded.inventory.size() == original.inventory.size()
                        && loaded.registryString.equals(original.registryString));

        // ---- 0x3004: W(0)=opcode, L(2)=panjang total, blob@6
        final int HDR_SAVE = 6;
        byte[] save = new byte[HDR_SAVE + blob.length];
        putU16(save, 0, 0x3004);
        putU32(save, 2, HDR_SAVE + blob.length);
        System.arraycopy(blob, 0, save, HDR_SAVE, blob.length);

        int saveLen = getU32(save, 2) - HDR_SAVE;
        check("0x3004 panjang blob benar (bukan bug offset-1 versi C)", saveLen == blob.length);
        byte[] sback = new byte[saveLen];
        System.arraycopy(save, HDR_SAVE, sback, 0, saveLen);
        check("0x3004 karakter utuh setelah framing",
                CharStatusCodec.decode(sback).name.equals(original.name));

        // bukti bug versi C: membaca panjang di offset 1 menghasilkan nilai ngawur
        long cBuggy = getU32(save, 1) - 6;
        check("membaca di offset 1 memang menghasilkan panjang salah", cBuggy != blob.length);
    }

    // Protokol antar-server memakai LITTLE-endian (wfifoW/wfifoL), berbeda
    // dengan protokol klien yang big-endian. Helper di bawah harus cocok
    // dengan Mapif/MapIntif, bukan sekadar konsisten dengan dirinya sendiri.

    private static void putU16(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    private static void putU32(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static int getU16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int getU32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static boolean throwsIo(byte[] blob) {
        try {
            CharStatusCodec.decode(blob);
            return false;
        } catch (IOException | RuntimeException e) {
            return true;
        }
    }

    private static boolean sameItems(CharStatus a, CharStatus b) {
        if (a.inventory.size() != b.inventory.size()) {
            return false;
        }
        for (int i = 0; i < a.inventory.size(); i++) {
            Item x = a.inventory.get(i);
            Item y = b.inventory.get(i);
            if (x.id != y.id || x.amount != y.amount || !x.realName.equals(y.realName)
                    || !x.note.equals(y.note) || x.trapsTable.length != y.trapsTable.length) {
                return false;
            }
        }
        return true;
    }

    /** Karakter dengan seluruh jenis data terisi, termasuk kasus tepi. */
    private static CharStatus sample() {
        Random rnd = new Random(42);
        CharStatus c = new CharStatus();
        c.id = 1234;
        c.name = "Tester";
        c.password = "e10adc3949ba59abbe56e057f20f883e";
        c.f1name = "F1";
        c.title = "Sang Penjelajah";
        c.clanTitle = "Ketua";
        c.lastIp = "192.168.56.101";
        c.afkMessage = "sedang pergi sebentar — jangan diganggu ya";

        c.partner = 5678;
        c.clan = 3;
        c.clanRank = 2;
        c.classRank = 7;

        c.level = 99;
        c.charClass = 4;
        c.tier = 2;
        c.mark = 1;
        c.totem = 3;
        c.karma = -12.75f;
        c.alignment = -5;      // bertanda
        c.tutor = 1;

        c.hp = 250_000;
        c.baseHp = 250_000;
        c.mp = 120_000;
        c.baseMp = 120_000;
        c.exp = 4_200_000_000L; // > 2^31, menguji unsigned 32-bit
        c.money = 999_999_999;
        c.bankMoney = 1_500_000;

        c.baseMight = 300;
        c.baseWill = 250;
        c.baseGrace = 275;
        c.baseArmor = -40;     // bertanda
        c.heroes = 12;
        c.miniMapToggle = 1;
        c.expSoldMagic = 9_000_000_000L;
        c.expSoldHealth = 8_000_000_000L;
        c.expSoldStats = 7_000_000_000L;

        c.sex = 1;
        c.country = 2;
        c.face = 10;
        c.faceColor = 3;
        c.hair = 12;
        c.hairColor = 5;
        c.armorColor = 7;
        c.skinColor = 2;
        c.disguise = 128;
        c.disguiseColor = 4;

        c.gmLevel = 99;
        c.side = 1;
        c.state = 0;
        c.clanChat = 1;
        c.subpathChat = 1;
        c.noviceChat = 0;
        c.mute = 0;
        c.settingFlags = 0xDEADBEEFL;
        c.pk = 1;
        c.killedBy = 42;
        c.killsPk = 17;
        c.pkDuration = 3600;
        c.maxSlots = 255;
        c.maxInv = 52;

        c.profileVitaStats = 1;
        c.profileEquipList = 1;
        c.profileLegends = 0;
        c.profileSpells = 1;
        c.profileInventory = 0;
        c.profileBankItems = 1;

        c.lastPos = new Point(1, 25, 30);
        c.destPos = new Point(4714, 8, 1);

        for (int i = 0; i < 15; i++) {
            Item it = new Item();
            it.id = 1000 + i;
            it.amount = 1;
            it.pos = i;
            it.realName = "Perlengkapan " + i;
            it.dura = 10000;
            c.equip.add(it);
        }
        for (int i = 0; i < 52; i++) {
            Item it = new Item();
            it.id = 2000 + i;
            it.amount = rnd.nextInt(1000) + 1;
            it.realName = "Barang " + i;
            it.note = "catatan panjang untuk barang nomor " + i;
            it.trapsTable = new int[]{1, 2, 3};
            c.inventory.add(it);
        }
        for (int i = 0; i < 40; i++) {
            Legend l = new Legend();
            l.icon = i;
            l.color = i % 8;
            l.name = "legenda" + i;
            l.text = "Telah menaklukkan ujian ke-" + i + " pada musim gugur.";
            l.tchaid = 1234;
            c.legends.add(l);
        }
        for (int i = 0; i < 20; i++) {
            SkillInfo s = new SkillInfo();
            s.id = 500 + i;
            s.duration = 60;
            s.aether = 30;
            s.casterId = 1234;
            s.lastTickDura = 1_700_000_000_000L;
            s.lastTickAether = 1_700_000_001_000L;
            c.duraAether.add(s);
        }
        for (int i = 0; i < 30; i++) {
            BankItem b = new BankItem();
            b.itemId = 3000 + i;
            b.amount = 99;
            b.realName = "Simpanan " + i;
            b.note = "disimpan lama";
            c.banks.add(b);
        }

        c.spells = new int[52];
        for (int i = 0; i < 52; i++) {
            c.spells[i] = i * 3;
        }

        for (int i = 0; i < 120; i++) {
            c.registry.put("flag" + i, i * 7);
            c.accountRegistry.put("acct" + i, i);
            c.npcRegistry.put("npc" + i, i * 2);
        }
        for (int i = 0; i < 60; i++) {
            c.questRegistry.put("quest" + i, i % 5);
            c.killReg.put((long) (900 + i), (long) (i * 11));
        }
        c.registryString.put("catatan", "teks dengan aksen: kué, ñ, 漢字, emoji 🎮");
        c.registryString.put("kosong", "");
        for (int i = 0; i < 40; i++) {
            c.registryString.put("str" + i, "nilai ke-" + i);
        }
        return c;
    }
}
