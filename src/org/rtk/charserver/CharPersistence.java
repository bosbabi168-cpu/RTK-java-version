package org.rtk.charserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Sql;
import org.rtk.common.mmo.BankItem;
import org.rtk.common.mmo.CharStatus;
import org.rtk.common.mmo.Item;
import org.rtk.common.mmo.Legend;
import org.rtk.common.mmo.Point;
import org.rtk.common.mmo.SkillInfo;

/**
 * Port of mmo_char_fromdb() / mmo_char_todb() (char/char_db.c).
 *
 * Memuat dan menyimpan seluruh keadaan karakter dari/ke MySQL. Tabel
 * `Character` menyimpan 67 kolom data inti; sisanya tersebar di tabel
 * anak yang semuanya berkunci `*ChaId`:
 *
 * <pre>
 *   Inventory, Equipment, Banks       barang
 *   SpellBook, Aethers                mantra & timer
 *   Legends, Kills                    riwayat
 *   Registry, RegistryString,
 *   NPCRegistry, QuestRegistry        penyimpanan skrip Lua
 * </pre>
 *
 * Pola simpan mengikuti versi C: hapus seluruh baris milik karakter, lalu
 * tulis ulang dari memori (lihat mem*_todb di char_db.c). Sederhana dan
 * membuat data di memori selalu jadi acuan.
 */
public final class CharPersistence {

    private static final Logger log = LogManager.getLogger(CharPersistence.class);

    private CharPersistence() {
    }

    // ------------------------------------------------------------------
    // MUAT
    // ------------------------------------------------------------------

    /** mmo_char_fromdb(): null bila karakter tidak ada. */
    public static CharStatus load(Sql sql, long charId) {
        CharStatus c = new CharStatus();
        c.id = charId;

        boolean[] found = {false};
        sql.forEachRow(
                "SELECT `ChaName`,`ChaPassword`,`ChaF1Name`,`ChaTitle`,`ChaClanTitle`,`ChaLastIP`,`ChaAFKMessage`,"
                + "`ChaPartner`,`ChaClnId`,`ChaClnRank`,`ChaPthRank`,"
                + "`ChaLevel`,`ChaPthId`,`ChaTier`,`ChaMark`,`ChaTotem`,`ChaKarma`,`ChaAlignment`,`ChaTutor`,"
                + "`ChaCurrentVita`,`ChaBaseVita`,`ChaCurrentMana`,`ChaBaseMana`,`ChaExperience`,`ChaGold`,`ChaBankGold`,"
                + "`ChaBaseMight`,`ChaBaseWill`,`ChaBaseGrace`,`ChaBaseArmor`,`ChaHeroes`,`ChaMiniMapToggle`,"
                + "`ChaExperienceSoldMagic`,`ChaExperienceSoldHealth`,`ChaExperienceSoldStats`,"
                + "`ChaSex`,`ChaNation`,`ChaFace`,`ChaFaceColor`,`ChaHair`,`ChaHairColor`,`ChaArmorColor`,`ChaSkinColor`,"
                + "`ChaDisguise`,`ChaDisguiseColor`,"
                + "`ChaGMLevel`,`ChaSide`,`ChaState`,`ChaClanChat`,`ChaPathChat`,`ChaNoviceChat`,`ChaMuted`,`ChaSettings`,"
                + "`ChaPK`,`ChaKilledBy`,`ChaKillsPK`,`ChaPKDuration`,`ChaMaximumBankSlots`,`ChaMaximumInventory`,"
                + "`ChaProfileVitaStats`,`ChaProfileEquipList`,`ChaProfileLegends`,`ChaProfileSpells`,"
                + "`ChaProfileInventory`,`ChaProfileBankItems`,"
                + "`ChaMapId`,`ChaX`,`ChaY` "
                + "FROM `Character` WHERE `ChaId` = ? LIMIT 1",
                rs -> {
                    found[0] = true;
                    c.name = s(rs.getString("ChaName"));
                    c.password = s(rs.getString("ChaPassword"));
                    c.f1name = s(rs.getString("ChaF1Name"));
                    c.title = s(rs.getString("ChaTitle"));
                    c.clanTitle = s(rs.getString("ChaClanTitle"));
                    c.lastIp = s(rs.getString("ChaLastIP"));
                    c.afkMessage = s(rs.getString("ChaAFKMessage"));

                    c.partner = rs.getLong("ChaPartner");
                    c.clan = rs.getLong("ChaClnId");
                    c.clanRank = rs.getInt("ChaClnRank");
                    c.classRank = rs.getInt("ChaPthRank");

                    c.level = rs.getInt("ChaLevel");
                    c.charClass = rs.getInt("ChaPthId");
                    c.tier = rs.getInt("ChaTier");
                    c.mark = rs.getInt("ChaMark");
                    c.totem = rs.getInt("ChaTotem");
                    c.karma = rs.getFloat("ChaKarma");
                    c.alignment = rs.getInt("ChaAlignment");
                    c.tutor = rs.getInt("ChaTutor");

                    c.hp = rs.getLong("ChaCurrentVita");
                    c.baseHp = rs.getLong("ChaBaseVita");
                    c.mp = rs.getLong("ChaCurrentMana");
                    c.baseMp = rs.getLong("ChaBaseMana");
                    c.exp = rs.getLong("ChaExperience");
                    c.money = rs.getLong("ChaGold");
                    c.bankMoney = rs.getLong("ChaBankGold");

                    c.baseMight = rs.getLong("ChaBaseMight");
                    c.baseWill = rs.getLong("ChaBaseWill");
                    c.baseGrace = rs.getLong("ChaBaseGrace");
                    c.baseArmor = rs.getInt("ChaBaseArmor");
                    c.heroes = rs.getLong("ChaHeroes");
                    c.miniMapToggle = rs.getLong("ChaMiniMapToggle");

                    c.expSoldMagic = rs.getLong("ChaExperienceSoldMagic");
                    c.expSoldHealth = rs.getLong("ChaExperienceSoldHealth");
                    c.expSoldStats = rs.getLong("ChaExperienceSoldStats");

                    c.sex = rs.getInt("ChaSex");
                    c.country = rs.getInt("ChaNation");
                    c.face = rs.getInt("ChaFace");
                    c.faceColor = rs.getInt("ChaFaceColor");
                    c.hair = rs.getInt("ChaHair");
                    c.hairColor = rs.getInt("ChaHairColor");
                    c.armorColor = rs.getInt("ChaArmorColor");
                    c.skinColor = rs.getInt("ChaSkinColor");
                    c.disguise = rs.getInt("ChaDisguise");
                    c.disguiseColor = rs.getInt("ChaDisguiseColor");

                    c.gmLevel = rs.getInt("ChaGMLevel");
                    c.side = rs.getInt("ChaSide");
                    c.state = rs.getInt("ChaState");
                    c.clanChat = rs.getInt("ChaClanChat");
                    c.subpathChat = rs.getInt("ChaPathChat");
                    c.noviceChat = rs.getInt("ChaNoviceChat");
                    c.mute = rs.getInt("ChaMuted");
                    c.settingFlags = rs.getLong("ChaSettings");
                    c.pk = rs.getInt("ChaPK");
                    c.killedBy = rs.getLong("ChaKilledBy");
                    c.killsPk = rs.getLong("ChaKillsPK");
                    c.pkDuration = rs.getLong("ChaPKDuration");
                    c.maxSlots = rs.getLong("ChaMaximumBankSlots");
                    c.maxInv = rs.getInt("ChaMaximumInventory");

                    c.profileVitaStats = rs.getInt("ChaProfileVitaStats");
                    c.profileEquipList = rs.getInt("ChaProfileEquipList");
                    c.profileLegends = rs.getInt("ChaProfileLegends");
                    c.profileSpells = rs.getInt("ChaProfileSpells");
                    c.profileInventory = rs.getInt("ChaProfileInventory");
                    c.profileBankItems = rs.getInt("ChaProfileBankItems");

                    c.lastPos = new Point(rs.getInt("ChaMapId"), rs.getInt("ChaX"), rs.getInt("ChaY"));
                    c.destPos = new Point(c.lastPos.m, c.lastPos.x, c.lastPos.y);
                }, charId);

        if (!found[0]) {
            return null;
        }

        loadItems(sql, c, charId);
        loadSpellsAndAethers(sql, c, charId);
        loadHistory(sql, c, charId);
        loadRegistries(sql, c, charId);
        return c;
    }

    private static void loadItems(Sql sql, CharStatus c, long id) {
        sql.forEachRow("SELECT `InvItmId`,`InvAmount`,`InvDurability`,`InvChaIdOwner`,`InvTimer`,`InvPosition`,"
                + "`InvCustom`,`InvCustomLook`,`InvCustomLookColor`,`InvCustomIcon`,`InvCustomIconColor`,"
                + "`InvProtected`,`InvNote`,`InvEngrave` FROM `Inventory` WHERE `InvChaId` = ? ORDER BY `InvPosition`",
                rs -> {
                    Item it = new Item();
                    it.id = rs.getLong("InvItmId");
                    it.amount = rs.getInt("InvAmount");
                    it.dura = rs.getInt("InvDurability");
                    it.owner = rs.getLong("InvChaIdOwner");
                    it.time = rs.getLong("InvTimer");
                    it.pos = rs.getInt("InvPosition");
                    it.custom = rs.getLong("InvCustom");
                    it.customLook = rs.getLong("InvCustomLook");
                    it.customLookColor = rs.getLong("InvCustomLookColor");
                    it.customIcon = rs.getLong("InvCustomIcon");
                    it.customIconColor = rs.getLong("InvCustomIconColor");
                    it.protectedFlag = rs.getLong("InvProtected");
                    it.note = s(rs.getString("InvNote"));
                    it.realName = s(rs.getString("InvEngrave"));
                    c.inventory.add(it);
                }, id);

        sql.forEachRow("SELECT `EqpItmId`,`EqpDurability`,`EqpChaIdOwner`,`EqpTimer`,`EqpSlot`,`EqpCustom`,"
                + "`EqpCustomLook`,`EqpCustomLookColor`,`EqpCustomIcon`,`EqpCustomIconColor`,`EqpProtected`,"
                + "`EqpNote`,`EqpEngrave` FROM `Equipment` WHERE `EqpChaId` = ? ORDER BY `EqpSlot`",
                rs -> {
                    Item it = new Item();
                    it.id = rs.getLong("EqpItmId");
                    it.amount = 1;
                    it.dura = rs.getInt("EqpDurability");
                    it.owner = rs.getLong("EqpChaIdOwner");
                    it.time = rs.getLong("EqpTimer");
                    it.pos = rs.getInt("EqpSlot");
                    it.custom = rs.getLong("EqpCustom");
                    it.customLook = rs.getLong("EqpCustomLook");
                    it.customLookColor = rs.getLong("EqpCustomLookColor");
                    it.customIcon = rs.getLong("EqpCustomIcon");
                    it.customIconColor = rs.getLong("EqpCustomIconColor");
                    it.protectedFlag = rs.getLong("EqpProtected");
                    it.note = s(rs.getString("EqpNote"));
                    it.realName = s(rs.getString("EqpEngrave"));
                    c.equip.add(it);
                }, id);

        sql.forEachRow("SELECT `BnkItmId`,`BnkAmount`,`BnkChaIdOwner`,`BnkTimer`,`BnkCustomIcon`,`BnkCustomLook`,"
                + "`BnkEngrave`,`BnkCustomLookColor`,`BnkCustomIconColor`,`BnkProtected`,`BnkNote` "
                + "FROM `Banks` WHERE `BnkChaId` = ? ORDER BY `BnkPosition`",
                rs -> {
                    BankItem b = new BankItem();
                    b.itemId = rs.getLong("BnkItmId");
                    b.amount = rs.getLong("BnkAmount");
                    b.owner = rs.getLong("BnkChaIdOwner");
                    b.time = rs.getLong("BnkTimer");
                    b.customIcon = rs.getLong("BnkCustomIcon");
                    b.customLook = rs.getLong("BnkCustomLook");
                    b.realName = s(rs.getString("BnkEngrave"));
                    b.customLookColor = rs.getLong("BnkCustomLookColor");
                    b.customIconColor = rs.getLong("BnkCustomIconColor");
                    b.protectedFlag = rs.getLong("BnkProtected");
                    b.note = s(rs.getString("BnkNote"));
                    c.banks.add(b);
                }, id);
    }

    private static void loadSpellsAndAethers(Sql sql, CharStatus c, long id) {
        List<int[]> slots = new ArrayList<>();
        sql.forEachRow("SELECT `SbkSplId`,`SbkPosition` FROM `SpellBook` WHERE `SbkChaId` = ? ORDER BY `SbkPosition`",
                rs -> slots.add(new int[]{rs.getInt("SbkPosition"), rs.getInt("SbkSplId")}), id);
        int max = 0;
        for (int[] sp : slots) {
            max = Math.max(max, sp[0] + 1);
        }
        c.spells = new int[Math.max(max, 0)];
        for (int[] sp : slots) {
            if (sp[0] >= 0 && sp[0] < c.spells.length) {
                c.spells[sp[0]] = sp[1];
            }
        }

        sql.forEachRow("SELECT `AthSplId`,`AthDuration`,`AthAether` FROM `Aethers` WHERE `AthChaId` = ? "
                + "ORDER BY `AthPosition`",
                rs -> {
                    SkillInfo si = new SkillInfo();
                    si.id = rs.getInt("AthSplId");
                    si.duration = rs.getInt("AthDuration");
                    si.aether = rs.getInt("AthAether");
                    c.duraAether.add(si);
                }, id);
    }

    private static void loadHistory(Sql sql, CharStatus c, long id) {
        sql.forEachRow("SELECT `LegIdentifier`,`LegDescription`,`LegIcon`,`LegColor`,`LegTChaId` "
                + "FROM `Legends` WHERE `LegChaId` = ? ORDER BY `LegPosition`",
                rs -> {
                    Legend l = new Legend();
                    l.name = s(rs.getString("LegIdentifier"));
                    l.text = s(rs.getString("LegDescription"));
                    l.icon = rs.getInt("LegIcon");
                    l.color = rs.getInt("LegColor");
                    l.tchaid = rs.getLong("LegTChaId");
                    c.legends.add(l);
                }, id);

        sql.forEachRow("SELECT `KilMobId`,`KilAmount` FROM `Kills` WHERE `KilChaId` = ? ORDER BY `KilPosition`",
                rs -> c.killReg.put(rs.getLong("KilMobId"), rs.getLong("KilAmount")), id);
    }

    private static void loadRegistries(Sql sql, CharStatus c, long id) {
        sql.forEachRow("SELECT `RegIdentifier`,`RegValue` FROM `Registry` WHERE `RegChaId` = ? ORDER BY `RegPosition`",
                rs -> c.registry.put(s(rs.getString("RegIdentifier")), rs.getInt("RegValue")), id);
        sql.forEachRow("SELECT `RegIdentifier`,`RegValue` FROM `RegistryString` WHERE `RegChaId` = ? "
                + "ORDER BY `RegPosition`",
                rs -> c.registryString.put(s(rs.getString("RegIdentifier")), s(rs.getString("RegValue"))), id);
        sql.forEachRow("SELECT `NrgIdentifier`,`NrgValue` FROM `NPCRegistry` WHERE `NrgChaId` = ? "
                + "ORDER BY `NrgPosition`",
                rs -> c.npcRegistry.put(s(rs.getString("NrgIdentifier")), rs.getInt("NrgValue")), id);
        sql.forEachRow("SELECT `QrgIdentifier`,`QrgValue` FROM `QuestRegistry` WHERE `QrgChaId` = ? "
                + "ORDER BY `QrgPosition`",
                rs -> c.questRegistry.put(s(rs.getString("QrgIdentifier")), rs.getInt("QrgValue")), id);
    }

    // ------------------------------------------------------------------
    // SIMPAN
    // ------------------------------------------------------------------

    /** mmo_char_todb(): true bila berhasil. */
    public static boolean save(Sql sql, CharStatus c) {
        int r = sql.update(
                "UPDATE `Character` SET `ChaName`=?,`ChaClnId`=?,`ChaClanTitle`=?,`ChaTitle`=?,`ChaLevel`=?,"
                + "`ChaPthId`=?,`ChaMark`=?,`ChaTotem`=?,`ChaKarma`=?,`ChaCurrentVita`=?,`ChaBaseVita`=?,"
                + "`ChaCurrentMana`=?,`ChaBaseMana`=?,`ChaExperience`=?,`ChaGold`=?,`ChaSex`=?,`ChaNation`=?,"
                + "`ChaFace`=?,`ChaHairColor`=?,`ChaArmorColor`=?,`ChaMapId`=?,`ChaX`=?,`ChaY`=?,`ChaSide`=?,"
                + "`ChaState`=?,`ChaHair`=?,`ChaFaceColor`=?,`ChaSkinColor`=?,`ChaPartner`=?,`ChaClanChat`=?,"
                + "`ChaPathChat`=?,`ChaNoviceChat`=?,`ChaSettings`=?,`ChaGMLevel`=?,`ChaDisguise`=?,"
                + "`ChaDisguiseColor`=?,`ChaMaximumBankSlots`=?,`ChaBankGold`=?,`ChaF1Name`=?,"
                + "`ChaMaximumInventory`=?,`ChaPK`=?,`ChaKilledBy`=?,`ChaKillsPK`=?,`ChaPKDuration`=?,"
                + "`ChaMuted`=?,`ChaHeroes`=?,`ChaTier`=?,`ChaExperienceSoldMagic`=?,`ChaExperienceSoldHealth`=?,"
                + "`ChaExperienceSoldStats`=?,`ChaBaseMight`=?,`ChaBaseGrace`=?,`ChaBaseWill`=?,`ChaBaseArmor`=?,"
                + "`ChaMiniMapToggle`=?,`ChaAFKMessage`=?,`ChaTutor`=?,`ChaAlignment`=?,`ChaProfileVitaStats`=?,"
                + "`ChaProfileEquipList`=?,`ChaProfileLegends`=?,`ChaProfileSpells`=?,`ChaProfileInventory`=?,"
                + "`ChaProfileBankItems`=?,`ChaPthRank`=?,`ChaClnRank`=? WHERE `ChaId`=?",
                c.name, c.clan, c.clanTitle, c.title, c.level,
                c.charClass, c.mark, c.totem, c.karma, c.hp, c.baseHp,
                c.mp, c.baseMp, c.exp, c.money, c.sex, c.country,
                c.face, c.hairColor, c.armorColor, c.lastPos.m, c.lastPos.x, c.lastPos.y, c.side,
                c.state, c.hair, c.faceColor, c.skinColor, c.partner, c.clanChat,
                c.subpathChat, c.noviceChat, c.settingFlags, c.gmLevel, c.disguise,
                c.disguiseColor, c.maxSlots, c.bankMoney, c.f1name,
                c.maxInv, c.pk, c.killedBy, c.killsPk, c.pkDuration,
                c.mute, c.heroes, c.tier, c.expSoldMagic, c.expSoldHealth,
                c.expSoldStats, c.baseMight, c.baseGrace, c.baseWill, c.baseArmor,
                c.miniMapToggle, c.afkMessage, c.tutor, c.alignment, c.profileVitaStats,
                c.profileEquipList, c.profileLegends, c.profileSpells, c.profileInventory,
                c.profileBankItems, c.classRank, c.clanRank, c.id);

        if (r < 0) {
            log.error("gagal menyimpan karakter id={} ({})", c.id, c.name);
            return false;
        }

        saveItems(sql, c);
        saveSpellsAndAethers(sql, c);
        saveHistory(sql, c);
        saveRegistries(sql, c);
        return true;
    }

    private static void saveItems(Sql sql, CharStatus c) {
        sql.update("DELETE FROM `Inventory` WHERE `InvChaId` = ?", c.id);
        List<Object[]> rows = new ArrayList<>();
        int pos = 0;
        for (Item it : c.inventory) {
            if (it.isEmpty()) {
                pos++;
                continue;
            }
            rows.add(new Object[]{c.id, it.id, it.amount, it.dura, it.owner, it.realName, it.time,
                    it.pos != 0 ? it.pos : pos, it.custom, it.customLook, it.customLookColor,
                    it.customIcon, it.customIconColor, it.protectedFlag, it.note});
            pos++;
        }
        sql.batch("INSERT INTO `Inventory` (`InvChaId`,`InvItmId`,`InvAmount`,`InvDurability`,`InvChaIdOwner`,"
                + "`InvEngrave`,`InvTimer`,`InvPosition`,`InvCustom`,`InvCustomLook`,`InvCustomLookColor`,"
                + "`InvCustomIcon`,`InvCustomIconColor`,`InvProtected`,`InvNote`) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);

        sql.update("DELETE FROM `Equipment` WHERE `EqpChaId` = ?", c.id);
        rows = new ArrayList<>();
        for (Item it : c.equip) {
            if (it.isEmpty()) {
                continue;
            }
            rows.add(new Object[]{c.id, it.id, it.dura, it.owner, it.realName, it.time, it.pos,
                    it.custom, it.customLook, it.customLookColor, it.customIcon, it.customIconColor,
                    it.protectedFlag, it.note});
        }
        sql.batch("INSERT INTO `Equipment` (`EqpChaId`,`EqpItmId`,`EqpDurability`,`EqpChaIdOwner`,`EqpEngrave`,"
                + "`EqpTimer`,`EqpSlot`,`EqpCustom`,`EqpCustomLook`,`EqpCustomLookColor`,`EqpCustomIcon`,"
                + "`EqpCustomIconColor`,`EqpProtected`,`EqpNote`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);

        sql.update("DELETE FROM `Banks` WHERE `BnkChaId` = ?", c.id);
        rows = new ArrayList<>();
        int bpos = 0;
        for (BankItem b : c.banks) {
            if (b.isEmpty()) {
                bpos++;
                continue;
            }
            rows.add(new Object[]{c.id, b.itemId, 0, b.amount, b.owner, b.realName, b.time, bpos++,
                    0, b.customLook, b.customLookColor, b.customIcon, b.customIconColor,
                    b.protectedFlag, b.note});
        }
        sql.batch("INSERT INTO `Banks` (`BnkChaId`,`BnkItmId`,`BnkDura`,`BnkAmount`,`BnkChaIdOwner`,`BnkEngrave`,"
                + "`BnkTimer`,`BnkPosition`,`BnkCustom`,`BnkCustomLook`,`BnkCustomLookColor`,`BnkCustomIcon`,"
                + "`BnkCustomIconColor`,`BnkProtected`,`BnkNote`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows);
    }

    private static void saveSpellsAndAethers(Sql sql, CharStatus c) {
        sql.update("DELETE FROM `SpellBook` WHERE `SbkChaId` = ?", c.id);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < c.spells.length; i++) {
            if (c.spells[i] != 0) {
                rows.add(new Object[]{c.id, c.spells[i], i});
            }
        }
        sql.batch("INSERT INTO `SpellBook` (`SbkChaId`,`SbkSplId`,`SbkPosition`) VALUES (?,?,?)", rows);

        sql.update("DELETE FROM `Aethers` WHERE `AthChaId` = ?", c.id);
        rows = new ArrayList<>();
        int pos = 0;
        for (SkillInfo si : c.duraAether) {
            if (si.isEmpty()) {
                pos++;
                continue;
            }
            rows.add(new Object[]{c.id, si.id, si.duration, si.aether, pos++});
        }
        sql.batch("INSERT INTO `Aethers` (`AthChaId`,`AthSplId`,`AthDuration`,`AthAether`,`AthPosition`) "
                + "VALUES (?,?,?,?,?)", rows);
    }

    private static void saveHistory(Sql sql, CharStatus c) {
        sql.update("DELETE FROM `Legends` WHERE `LegChaId` = ?", c.id);
        List<Object[]> rows = new ArrayList<>();
        int pos = 0;
        for (Legend l : c.legends) {
            if (l.isEmpty()) {
                pos++;
                continue;
            }
            rows.add(new Object[]{c.id, l.name, l.text, l.icon, l.color, pos++, l.tchaid});
        }
        sql.batch("INSERT INTO `Legends` (`LegChaId`,`LegIdentifier`,`LegDescription`,`LegIcon`,`LegColor`,"
                + "`LegPosition`,`LegTChaId`) VALUES (?,?,?,?,?,?,?)", rows);

        sql.update("DELETE FROM `Kills` WHERE `KilChaId` = ?", c.id);
        rows = new ArrayList<>();
        pos = 0;
        for (Map.Entry<Long, Long> e : c.killReg.entrySet()) {
            rows.add(new Object[]{c.id, e.getKey(), e.getValue(), pos++});
        }
        sql.batch("INSERT INTO `Kills` (`KilChaId`,`KilMobId`,`KilAmount`,`KilPosition`) VALUES (?,?,?,?)", rows);
    }

    private static void saveRegistries(Sql sql, CharStatus c) {
        reg(sql, c.id, c.registry, "Registry", "RegChaId", "RegIdentifier", "RegValue", "RegPosition");
        reg(sql, c.id, c.npcRegistry, "NPCRegistry", "NrgChaId", "NrgIdentifier", "NrgValue", "NrgPosition");
        reg(sql, c.id, c.questRegistry, "QuestRegistry", "QrgChaId", "QrgIdentifier", "QrgValue", "QrgPosition");

        sql.update("DELETE FROM `RegistryString` WHERE `RegChaId` = ?", c.id);
        List<Object[]> rows = new ArrayList<>();
        int pos = 0;
        for (Map.Entry<String, String> e : c.registryString.entrySet()) {
            rows.add(new Object[]{c.id, e.getKey(), e.getValue(), pos++});
        }
        sql.batch("INSERT INTO `RegistryString` (`RegChaId`,`RegIdentifier`,`RegValue`,`RegPosition`) "
                + "VALUES (?,?,?,?)", rows);
    }

    private static void reg(Sql sql, long id, Map<String, Integer> map, String table,
                            String chaCol, String idCol, String valCol, String posCol) {
        sql.update("DELETE FROM `" + table + "` WHERE `" + chaCol + "` = ?", id);
        List<Object[]> rows = new ArrayList<>();
        int pos = 0;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            rows.add(new Object[]{id, e.getKey(), e.getValue(), pos++});
        }
        sql.batch("INSERT INTO `" + table + "` (`" + chaCol + "`,`" + idCol + "`,`" + valCol + "`,`" + posCol
                + "`) VALUES (?,?,?,?)", rows);
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }
}
