package org.rtk.common.mmo;

/**
 * Port of struct skill_info (common/mmo.h): timer durasi/aether sebuah spell.
 *
 * Catatan: di C, {@code lasttick_dura}/{@code lasttick_aether} bertipe
 * {@code unsigned long} — 4 byte pada build 32-bit asli, 8 byte pada 64-bit.
 * Perbedaan inilah yang membuat ukuran struct C tidak stabil antar target,
 * salah satu alasan port ini memakai formatnya sendiri. Di sini selalu 64-bit.
 */
public final class SkillInfo {
    public int duration;
    public int aether;
    public int time;
    public int id;          // unsigned short
    public int animation;
    public long casterId;   // unsigned int
    public long duraTimer;
    public long aetherTimer;
    public long lastTickDura;
    public long lastTickAether;

    public boolean isEmpty() {
        return id == 0 && duration == 0 && aether == 0;
    }
}
