package org.rtk.charserver;

import org.rtk.common.Md5;

import static org.rtk.charserver.CharServer.*;

/**
 * Port of char/char_db.c — the character database layer.
 *
 * Character loading/saving as a compressed mmo_charstatus blob
 * (mmo_char_fromdb / mmo_char_todb) belongs to the map-server gameplay port
 * and is not included yet; see the project README.
 */
public final class CharDb {

    private CharDb() {
    }

    /** char_db_init() */
    public static void init() {
        if (!sql.connect(sqlId, sqlPw, sqlIp, sqlPort, sqlDb)) {
            System.exit(1);
        }
    }

    /** char_db_term() */
    public static void term() {
        sql.close();
    }

    /** ispass(): does the given password match the stored MD5? */
    static boolean isPass(String name, String pass, String md5) {
        // Legacy format: md5("lowercase_name password"), new format: md5(password).
        String legacy = Md5.hex(name.toLowerCase() + " " + pass);
        String modern = Md5.hex(pass);
        return md5.equalsIgnoreCase(legacy) || md5.equalsIgnoreCase(modern);
    }

    /** ismastpass(): master password with expiration timestamp. */
    static boolean isMastPass(String pass, String mastMd5, long expire) {
        long current = System.currentTimeMillis() / 1000L;
        return mastMd5 != null && mastMd5.equalsIgnoreCase(Md5.hex(pass)) && current <= expire;
    }

    /** find_new_id(): lowest free ChaId, wiping any stale satellite rows. */
    static int findNewId() {
        Integer newId = sql.queryInt(
                "SELECT l.ChaId + 1 AS START FROM `Character` AS l "
                + "LEFT OUTER JOIN `Character` AS r ON l.ChaId + 1 = r.ChaId "
                + "WHERE r.ChaId IS NULL LIMIT 1");
        if (newId == null || newId == 0) {
            return 0;
        }
        int x = newId;
        sql.update("DELETE FROM `Banks` WHERE `BnkChaId` = ?", x);
        sql.update("DELETE FROM `Aethers` WHERE `AthChaId` = ?", x);
        sql.update("DELETE FROM `Equipment` WHERE `EqpChaId` = ?", x);
        sql.update("DELETE FROM `Inventory` WHERE `InvChaId` = ?", x);
        sql.update("DELETE FROM `Kills` WHERE `KilChaId` = ?", x);
        sql.update("DELETE FROM `Legends` WHERE `LegChaId` = ?", x);
        sql.update("DELETE FROM `SpellBook` WHERE `SbkChaId` = ?", x);
        sql.update("DELETE FROM `Registry` WHERE `RegChaId` = ?", x);
        sql.update("DELETE FROM `RegistryString` WHERE `RegChaId` = ?", x);
        sql.update("DELETE FROM `NPCRegistry` WHERE `NrgChaId` = ?", x);
        sql.update("DELETE FROM `QuestRegistry` WHERE `QrgChaId` = ?", x);
        sql.update("DELETE FROM `Parcels` WHERE `ParSender` = ? AND `ParNpc` = '0'", x);
        return x;
    }

    /**
     * char_db_newchar().
     * @return 0 = created, 1 = name taken, 2 = db error
     */
    public static int newChar(String name, String pass, int totem, int sex, int country,
            int face, int hair, int faceColor, int hairColor) {
        int result = isNameUsed(name);
        if (result != 0) {
            return result;
        }
        int newId = findNewId();
        if (newId == 0) {
            return 2;
        }
        int r = sql.update(
                "INSERT INTO `Character` (`ChaId`,`ChaName`, `ChaPassword`, `ChaTotem`, `ChaSex`, `ChaNation`, "
                + "`ChaFace`, `ChaMapID`, `ChaX`, `ChaY`, `ChaHair`, `ChaHairColor`, `ChaFaceColor`) "
                + "VALUES (?, ?, MD5(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newId, name, pass, totem, sex, country, face, startM, startX, startY,
                hair, hairColor, faceColor);
        if (r < 0) {
            return 2; // db error
        }
        return 0; // new character is ready!
    }

    /**
     * char_db_isnameused().
     * @return 0 = free, 1 = taken, 2 = db error
     */
    public static int isNameUsed(String name) {
        int rows = sql.rowCount("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
        if (rows < 0) {
            return 2;
        }
        return rows >= 1 ? 1 : 0;
    }

    /** char_db_idfromname() */
    public static int idFromName(String name) {
        Integer id = sql.queryInt("SELECT `ChaId` FROM `Character` WHERE `ChaName` = ?", name);
        return id == null ? 0 : id;
    }

    /**
     * char_db_setpass().
     * @return 0 = ok, -1 = db error, -2 = unknown name, -3 = wrong password
     */
    public static int setPass(String name, String pass, String newPass) {
        String stored = sql.queryString("SELECT `ChaPassword` FROM `Character` WHERE `ChaName` = ?", name);
        if (stored == null) {
            return -2;
        }
        if (!isPass(name, pass, stored)) {
            return -3;
        }
        int r = sql.update("UPDATE `Character` SET `ChaPassword` = MD5(?) WHERE `ChaName` = ?", newPass, name);
        return r < 0 ? -1 : 0;
    }

    /** checkAccountBan(): non-zero AccountId when the account is banned. */
    static int checkAccountBan(int id) {
        Integer accountId = sql.queryInt(
                "SELECT `AccountId` FROM `Accounts` WHERE `AccountBanned` = '1' AND "
                + "(`AccountCharId1` = ? OR `AccountCharId2` = ? OR `AccountCharId3` = ? "
                + "OR `AccountCharId4` = ? OR `AccountCharId5` = ? OR `AccountCharId6` = ?)",
                id, id, id, id, id, id);
        return accountId == null ? 0 : accountId;
    }

    /**
     * char_db_mapfifofromlogin(): validate credentials and find the map
     * server responsible for the character's current map.
     *
     * @param idOut single-element array that receives the character id
     * @return map server index (&gt;= 0), or a negative error code:
     *         -1 db error, -2 unknown name, -3 wrong password, -4 banned,
     *         0 with idOut untouched means "chaos is rising" (no map server)
     */
    public static int mapFifoFromLogin(String name, String pass, int[] idOut) {
        String md5 = sql.queryString("SELECT `ChaPassword` FROM `Character` WHERE `ChaName` = ?", name);
        if (md5 == null) {
            return -2; // name doesn't exist
        }

        Object[] master = sql.queryRow(
                "SELECT `AdmPassword`,`AdmTimer` FROM `AdminPassword` WHERE `AdmId` = '1'", 2);
        String mastMd5 = master != null ? String.valueOf(master[0]) : "";
        long expiration = master != null && master[1] != null
                ? ((Number) master[1]).longValue() : 0;

        if (!isPass(name, pass, md5) && !isMastPass(pass, mastMd5, expiration)) {
            return -3; // wrong password, try again!
        }

        Object[] row = sql.queryRow(
                "SELECT `ChaId`, `ChaPassword`, `ChaBanned`, `ChaMapId` FROM `Character` WHERE `ChaName` = ?",
                4, name);
        if (row == null) {
            return -2;
        }
        int nId = ((Number) row[0]).intValue();
        int ban = row[2] == null ? 0 : ((Number) row[2]).intValue();
        int map = row[3] == null ? 0 : ((Number) row[3]).intValue();

        int accountBan = checkAccountBan(nId);
        if (ban != 0 || accountBan != 0) {
            return -4; // you are banned, go away
        }

        int result = mapfifoFromMapid(map);
        if (result == -1) {
            return 0; // chaos is rising
        }

        idOut[0] = nId;
        return result;
    }

    /** mmo_setonline() */
    public static void setOnline(int id, int val) {
        sql.update("UPDATE `Character` SET `ChaOnline`= ? WHERE `ChaId`= ?", val, id);
    }

    /** mmo_setallonline() */
    public static void setAllOnline(int val) {
        sql.update("UPDATE `Character` SET `ChaOnline`= ?", val);
    }

    /**
     * logindata_add(): returns the character's current ChaOnline flag
     * (&gt; 0 means already online → double login), -1 on error.
     */
    public static int logindataAdd(int id, int mapServer, String name) {
        Integer online = sql.queryInt("SELECT `ChaOnline` FROM `Character` WHERE `ChaName`= ?", name);
        return online == null ? -1 : online;
    }

    /** logindata_del() */
    public static int logindataDel(int id) {
        sql.update("UPDATE `Character` SET `ChaOnline`=0 WHERE `ChaId`= ?", id);
        return 0;
    }
}
