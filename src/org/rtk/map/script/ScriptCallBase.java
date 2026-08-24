package org.rtk.map.script;

/**
 * Benda yang bisa memanggil kait skrip miliknya sendiri
 * ({@code mobl_callbase} di C).
 *
 * <p>Dipakai mob dengan MobAI tipe 4 — yang memakai skripnya sendiri
 * sebagai AI, sehingga perlu memanggil balik kait seperti
 * {@code on_healed} atau {@code on_attacked} dari dalam skrip itu.</p>
 */
public interface ScriptCallBase {

    /**
     * Panggil kait bernama {@code method} pada skrip milik benda ini.
     * @return true bila kaitnya benar-benar ada dan terpanggil
     */
    boolean scriptCallBase(String method);
}
