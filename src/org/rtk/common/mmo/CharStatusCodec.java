package org.rtk.common.mmo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Serialisasi {@link CharStatus} untuk paket 0x3003/0x3803/0x3004.
 *
 * Menggantikan dump memori {@code struct mmo_charstatus} milik versi C
 * (3.171.352 byte, 82% array registry kosong, ukurannya berbeda antara
 * build 32-bit dan 64-bit). Format di sini:
 *
 * <ul>
 *   <li>big-endian, konsisten dengan protokol klien;</li>
 *   <li>diawali magic {@code "RTKC"} + nomor versi, supaya berkas/paket
 *       lama langsung ketahuan dan bisa ditolak dengan pesan jelas;</li>
 *   <li>string ditulis sebagai panjang uint16 + byte UTF-8;</li>
 *   <li>koleksi ditulis sebagai jumlah uint32 + entri — hanya yang terisi,
 *       jadi karakter baru hanya beberapa ratus byte, bukan 3 MB;</li>
 *   <li>hasil akhirnya tetap dikompresi zlib seperti versi C, sehingga
 *       lapisan transportasinya tidak berubah.</li>
 * </ul>
 */
public final class CharStatusCodec {

    /** Penanda awal blob; membedakan dari dump struct C. */
    public static final int MAGIC = 0x52544B43; // "RTKC"

    /** Naikkan bila tata letak berubah agar versi lama tertolak. */
    public static final int VERSION = 1;

    private CharStatusCodec() {
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /** Serialisasi + kompresi zlib (setara compress2() di C). */
    public static byte[] encode(CharStatus c) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8192);
        try (DataOutputStream out = new DataOutputStream(raw)) {
            write(out, c);
        }
        ByteArrayOutputStream packed = new ByteArrayOutputStream(4096);
        try (DeflaterOutputStream z = new DeflaterOutputStream(packed)) {
            z.write(raw.toByteArray());
        }
        return packed.toByteArray();
    }

    /** Kebalikan {@link #encode}. */
    public static CharStatus decode(byte[] compressed, int off, int len) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream(8192);
        try (InflaterInputStream z =
                     new InflaterInputStream(new ByteArrayInputStream(compressed, off, len))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = z.read(buf)) > 0) {
                raw.write(buf, 0, n);
            }
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw.toByteArray()))) {
            return read(in);
        }
    }

    public static CharStatus decode(byte[] compressed) throws IOException {
        return decode(compressed, 0, compressed.length);
    }

    // ------------------------------------------------------------------
    // tulis
    // ------------------------------------------------------------------

    static void write(DataOutputStream o, CharStatus c) throws IOException {
        o.writeInt(MAGIC);
        o.writeShort(VERSION);

        // identitas
        o.writeInt((int) c.id);
        str(o, c.name);
        str(o, c.password);
        str(o, c.f1name);
        str(o, c.title);
        str(o, c.clanTitle);
        str(o, c.lastIp);
        str(o, c.afkMessage);

        // relasi
        o.writeInt((int) c.partner);
        o.writeInt((int) c.clan);
        o.writeInt(c.clanRank);
        o.writeInt(c.classRank);

        // statistik
        o.writeShort(c.level);
        o.writeShort(c.charClass);
        o.writeShort(c.tier);
        o.writeShort(c.mark);
        o.writeShort(c.totem);
        o.writeFloat(c.karma);
        o.writeByte(c.alignment);
        o.writeByte(c.tutor);

        o.writeInt((int) c.hp);
        o.writeInt((int) c.baseHp);
        o.writeInt((int) c.mp);
        o.writeInt((int) c.baseMp);
        o.writeInt((int) c.exp);
        o.writeInt((int) c.money);
        o.writeInt((int) c.bankMoney);

        o.writeInt((int) c.baseMight);
        o.writeInt((int) c.baseWill);
        o.writeInt((int) c.baseGrace);
        o.writeInt(c.baseArmor);
        o.writeInt((int) c.heroes);
        o.writeInt((int) c.miniMapToggle);

        o.writeLong(c.expSoldMagic);
        o.writeLong(c.expSoldHealth);
        o.writeLong(c.expSoldStats);

        // tampilan
        o.writeByte(c.sex);
        o.writeByte(c.country);
        o.writeShort(c.face);
        o.writeShort(c.faceColor);
        o.writeShort(c.hair);
        o.writeShort(c.hairColor);
        o.writeShort(c.armorColor);
        o.writeShort(c.skinColor);
        o.writeShort(c.disguise);
        o.writeShort(c.disguiseColor);

        // status
        o.writeByte(c.gmLevel);
        o.writeByte(c.side);
        o.writeByte(c.state);
        o.writeByte(c.clanChat);
        o.writeByte(c.subpathChat);
        o.writeByte(c.noviceChat);
        o.writeByte(c.mute);
        o.writeInt((int) c.settingFlags);
        o.writeByte(c.pk);
        o.writeInt((int) c.killedBy);
        o.writeInt((int) c.killsPk);
        o.writeInt((int) c.pkDuration);
        o.writeInt((int) c.maxSlots);
        o.writeShort(c.maxInv);

        // profil
        o.writeByte(c.profileVitaStats);
        o.writeByte(c.profileEquipList);
        o.writeByte(c.profileLegends);
        o.writeByte(c.profileSpells);
        o.writeByte(c.profileInventory);
        o.writeByte(c.profileBankItems);

        // posisi
        point(o, c.lastPos);
        point(o, c.destPos);

        // koleksi
        o.writeInt(c.equip.size());
        for (Item it : c.equip) {
            item(o, it);
        }
        o.writeInt(c.inventory.size());
        for (Item it : c.inventory) {
            item(o, it);
        }
        o.writeInt(c.legends.size());
        for (Legend l : c.legends) {
            o.writeShort(l.icon);
            o.writeShort(l.color);
            str(o, l.text);
            str(o, l.name);
            o.writeInt((int) l.tchaid);
        }
        o.writeInt(c.duraAether.size());
        for (SkillInfo s : c.duraAether) {
            o.writeInt(s.duration);
            o.writeInt(s.aether);
            o.writeInt(s.time);
            o.writeShort(s.id);
            o.writeShort(s.animation);
            o.writeInt((int) s.casterId);
            o.writeInt((int) s.duraTimer);
            o.writeInt((int) s.aetherTimer);
            o.writeLong(s.lastTickDura);
            o.writeLong(s.lastTickAether);
        }
        o.writeInt(c.banks.size());
        for (BankItem b : c.banks) {
            o.writeInt((int) b.itemId);
            o.writeInt((int) b.amount);
            o.writeInt((int) b.owner);
            o.writeInt((int) b.time);
            o.writeInt((int) b.customIcon);
            o.writeInt((int) b.customLook);
            str(o, b.realName);
            o.writeInt((int) b.customLookColor);
            o.writeInt((int) b.customIconColor);
            o.writeInt((int) b.protectedFlag);
            str(o, b.note);
        }

        o.writeInt(c.spells.length);
        for (int s : c.spells) {
            o.writeShort(s);
        }

        intMap(o, c.registry);
        o.writeInt(c.registryString.size());
        for (Map.Entry<String, String> e : c.registryString.entrySet()) {
            str(o, e.getKey());
            str(o, e.getValue());
        }
        intMap(o, c.accountRegistry);
        intMap(o, c.npcRegistry);
        intMap(o, c.questRegistry);

        o.writeInt(c.killReg.size());
        for (Map.Entry<Long, Long> e : c.killReg.entrySet()) {
            o.writeInt(e.getKey().intValue());
            o.writeInt(e.getValue().intValue());
        }
    }

    // ------------------------------------------------------------------
    // baca
    // ------------------------------------------------------------------

    static CharStatus read(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC) {
            throw new IOException(String.format(
                    "blob karakter tidak dikenal: magic 0x%08X (diharapkan 0x%08X 'RTKC')", magic, MAGIC));
        }
        int version = in.readUnsignedShort();
        if (version != VERSION) {
            throw new IOException("versi blob karakter " + version + " tidak didukung (diharapkan " + VERSION + ")");
        }

        CharStatus c = new CharStatus();
        c.id = u32(in);
        c.name = str(in);
        c.password = str(in);
        c.f1name = str(in);
        c.title = str(in);
        c.clanTitle = str(in);
        c.lastIp = str(in);
        c.afkMessage = str(in);

        c.partner = u32(in);
        c.clan = u32(in);
        c.clanRank = in.readInt();
        c.classRank = in.readInt();

        c.level = in.readUnsignedShort();
        c.charClass = in.readUnsignedShort();
        c.tier = in.readUnsignedShort();
        c.mark = in.readUnsignedShort();
        c.totem = in.readUnsignedShort();
        c.karma = in.readFloat();
        c.alignment = in.readByte();
        c.tutor = in.readByte();

        c.hp = u32(in);
        c.baseHp = u32(in);
        c.mp = u32(in);
        c.baseMp = u32(in);
        c.exp = u32(in);
        c.money = u32(in);
        c.bankMoney = u32(in);

        c.baseMight = u32(in);
        c.baseWill = u32(in);
        c.baseGrace = u32(in);
        c.baseArmor = in.readInt();
        c.heroes = u32(in);
        c.miniMapToggle = u32(in);

        c.expSoldMagic = in.readLong();
        c.expSoldHealth = in.readLong();
        c.expSoldStats = in.readLong();

        c.sex = in.readByte();
        c.country = in.readByte();
        c.face = in.readUnsignedShort();
        c.faceColor = in.readUnsignedShort();
        c.hair = in.readUnsignedShort();
        c.hairColor = in.readUnsignedShort();
        c.armorColor = in.readUnsignedShort();
        c.skinColor = in.readUnsignedShort();
        c.disguise = in.readUnsignedShort();
        c.disguiseColor = in.readUnsignedShort();

        c.gmLevel = in.readByte();
        c.side = in.readByte();
        c.state = in.readByte();
        c.clanChat = in.readByte();
        c.subpathChat = in.readByte();
        c.noviceChat = in.readByte();
        c.mute = in.readByte();
        c.settingFlags = u32(in);
        c.pk = in.readByte();
        c.killedBy = u32(in);
        c.killsPk = u32(in);
        c.pkDuration = u32(in);
        c.maxSlots = u32(in);
        c.maxInv = in.readUnsignedShort();

        c.profileVitaStats = in.readByte();
        c.profileEquipList = in.readByte();
        c.profileLegends = in.readByte();
        c.profileSpells = in.readByte();
        c.profileInventory = in.readByte();
        c.profileBankItems = in.readByte();

        c.lastPos = point(in);
        c.destPos = point(in);

        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            c.equip.add(item(in));
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            c.inventory.add(item(in));
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            Legend l = new Legend();
            l.icon = in.readUnsignedShort();
            l.color = in.readUnsignedShort();
            l.text = str(in);
            l.name = str(in);
            l.tchaid = u32(in);
            c.legends.add(l);
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            SkillInfo s = new SkillInfo();
            s.duration = in.readInt();
            s.aether = in.readInt();
            s.time = in.readInt();
            s.id = in.readUnsignedShort();
            s.animation = in.readUnsignedShort();
            s.casterId = u32(in);
            s.duraTimer = u32(in);
            s.aetherTimer = u32(in);
            s.lastTickDura = in.readLong();
            s.lastTickAether = in.readLong();
            c.duraAether.add(s);
        }
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            BankItem b = new BankItem();
            b.itemId = u32(in);
            b.amount = u32(in);
            b.owner = u32(in);
            b.time = u32(in);
            b.customIcon = u32(in);
            b.customLook = u32(in);
            b.realName = str(in);
            b.customLookColor = u32(in);
            b.customIconColor = u32(in);
            b.protectedFlag = u32(in);
            b.note = str(in);
            c.banks.add(b);
        }

        n = in.readInt();
        c.spells = new int[n];
        for (int i = 0; i < n; i++) {
            c.spells[i] = in.readUnsignedShort();
        }

        intMap(in, c.registry);
        n = in.readInt();
        for (int i = 0; i < n; i++) {
            c.registryString.put(str(in), str(in));
        }
        intMap(in, c.accountRegistry);
        intMap(in, c.npcRegistry);
        intMap(in, c.questRegistry);

        n = in.readInt();
        for (int i = 0; i < n; i++) {
            c.killReg.put(u32(in), u32(in));
        }
        return c;
    }

    // ------------------------------------------------------------------
    // primitif
    // ------------------------------------------------------------------

    private static void str(DataOutputStream o, String s) throws IOException {
        byte[] b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        if (b.length > 0xFFFF) {
            throw new IOException("string terlalu panjang: " + b.length + " byte");
        }
        o.writeShort(b.length);
        o.write(b);
    }

    private static String str(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static long u32(DataInputStream in) throws IOException {
        return in.readInt() & 0xFFFFFFFFL;
    }

    private static void point(DataOutputStream o, Point p) throws IOException {
        Point v = p == null ? new Point() : p;
        o.writeShort(v.m);
        o.writeShort(v.x);
        o.writeShort(v.y);
    }

    private static Point point(DataInputStream in) throws IOException {
        return new Point(in.readUnsignedShort(), in.readUnsignedShort(), in.readUnsignedShort());
    }

    private static void item(DataOutputStream o, Item it) throws IOException {
        o.writeInt((int) it.id);
        o.writeInt((int) it.owner);
        o.writeInt((int) it.custom);
        o.writeInt((int) it.time);
        o.writeInt(it.dura);
        o.writeInt(it.amount);
        o.writeByte(it.pos);
        o.writeInt((int) it.customLook);
        o.writeInt((int) it.customIcon);
        o.writeInt((int) it.customLookColor);
        o.writeInt((int) it.customIconColor);
        o.writeInt((int) it.protectedFlag);
        o.writeInt(it.trapsTable.length);
        for (int t : it.trapsTable) {
            o.writeInt(t);
        }
        str(o, it.buytext);
        str(o, it.note);
        o.writeByte(it.repair);
        str(o, it.realName);
    }

    private static Item item(DataInputStream in) throws IOException {
        Item it = new Item();
        it.id = u32(in);
        it.owner = u32(in);
        it.custom = u32(in);
        it.time = u32(in);
        it.dura = in.readInt();
        it.amount = in.readInt();
        it.pos = in.readUnsignedByte();
        it.customLook = u32(in);
        it.customIcon = u32(in);
        it.customLookColor = u32(in);
        it.customIconColor = u32(in);
        it.protectedFlag = u32(in);
        int n = in.readInt();
        it.trapsTable = new int[n];
        for (int i = 0; i < n; i++) {
            it.trapsTable[i] = in.readInt();
        }
        it.buytext = str(in);
        it.note = str(in);
        it.repair = in.readByte();
        it.realName = str(in);
        return it;
    }

    private static void intMap(DataOutputStream o, Map<String, Integer> m) throws IOException {
        o.writeInt(m.size());
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            str(o, e.getKey());
            o.writeInt(e.getValue() == null ? 0 : e.getValue());
        }
    }

    private static void intMap(DataInputStream in, Map<String, Integer> m) throws IOException {
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            m.put(str(in), in.readInt());
        }
    }
}
