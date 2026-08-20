package org.rtk.common.mmo;

/** Port of struct bank_data (common/mmo.h): satu slot bank pemain. */
public final class BankItem {
    public long itemId;
    public long amount;
    public long owner;
    public long time;
    public long customIcon;
    public long customLook;
    public String realName = "";
    public long customLookColor;
    public long customIconColor;
    public long protectedFlag;
    public String note = "";

    public boolean isEmpty() {
        return itemId == 0 && amount == 0 && (realName == null || realName.isEmpty());
    }
}
