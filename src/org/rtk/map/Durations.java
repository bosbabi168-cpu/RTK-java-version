package org.rtk.map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.mmo.SkillInfo;
import org.rtk.map.data.BlockList;
import org.rtk.map.script.ScriptEngine;

/**
 * Durasi & aether mantra — port {@code sd->status.dura_aether[]} beserta
 * seluruh keluarga binding yang menyentuhnya, dan tik satu detik
 * {@code bl_duratimer()} (map/pc.c:217).
 *
 * <h2>Kenapa ini penting</h2>
 *
 * <p>{@code setDuration} dipakai <b>423x</b> di korpus — nomor lima
 * terbanyak. Hampir setiap mantra, jebakan, dan barang berwaktu memakainya;
 * selama ia stub, semua efek berwaktu "berhasil" tanpa pernah aktif dan
 * tanpa pernah berakhir. Log pun bersih, karena stub hanya mengembalikan
 * nil.</p>
 *
 * <h2>Satu larik, dua penghitung</h2>
 *
 * <p>Yang mudah salah: {@code duration} dan {@code aether} tinggal di
 * <b>baris yang sama</b> dan berbagi satu {@code id}, tetapi habisnya
 * sendiri-sendiri. Barisnya baru benar-benar kosong ketika keduanya nol —
 * karena itu setiap tempat yang menolkan salah satunya memeriksa dulu apakah
 * yang lain masih berjalan sebelum menghapus {@code id}. Menghapus id lebih
 * cepat membuat aether yang masih hidup kehilangan mantranya.</p>
 *
 * <h2>Perbedaan penyimpanan dari C</h2>
 *
 * <p>C memakai larik tetap {@code dura_aether[200]} dan menandai slot kosong
 * dengan {@code id == 0}; di sini {@link org.rtk.common.mmo.CharStatus#duraAether}
 * adalah daftar yang hanya berisi baris terisi (lihat Peringatan #9 soal blob
 * karakter). Akibatnya "cari slot kosong pertama" menjadi "tambah baris baru",
 * dan batas {@link #MAX_MAGIC_TIMERS} dijaga di sini.</p>
 */
public final class Durations {

    private static final Logger log = LogManager.getLogger(Durations.class);

    /** MAX_MAGIC_TIMERS (common/mmo.h:14). */
    public static final int MAX_MAGIC_TIMERS = 200;

    private Durations() {
    }

    // ------------------------------------------------------------------
    // pencarian
    // ------------------------------------------------------------------

    private static SkillInfo cari(User sd, int id, long casterId, boolean cocokkanCaster) {
        for (SkillInfo s : sd.status.duraAether) {
            if (s.id != id) {
                continue;
            }
            if (cocokkanCaster && s.casterId != casterId) {
                continue;
            }
            return s;
        }
        return null;
    }

    /** pcl_hasduration(): mantra ini sedang berjalan pada pemain. */
    public static boolean hasDuration(User sd, int id) {
        SkillInfo s = cari(sd, id, 0, false);
        return s != null && s.duration > 0;
    }

    /** pcl_hasdurationid(): sedang berjalan DAN dari penyihir tertentu. */
    public static boolean hasDurationFrom(User sd, int id, long casterId) {
        SkillInfo s = cari(sd, id, casterId, true);
        return s != null && s.duration > 0;
    }

    /** pcl_getduration() / pcl_durationamount(): sisa waktu dalam milidetik. */
    public static int duration(User sd, int id) {
        SkillInfo s = cari(sd, id, 0, false);
        return s != null && s.duration > 0 ? s.duration : 0;
    }

    /** pcl_getdurationid(): sisa waktu, tapi hanya dari penyihir tertentu. */
    public static int durationFrom(User sd, int id, long casterId) {
        SkillInfo s = cari(sd, id, casterId, true);
        return s != null && s.duration > 0 ? s.duration : 0;
    }

    /** pcl_hasaether(). */
    public static boolean hasAether(User sd, int id) {
        SkillInfo s = cari(sd, id, 0, false);
        return s != null && s.aether > 0;
    }

    /** pcl_getaether(). */
    public static int aether(User sd, int id) {
        SkillInfo s = cari(sd, id, 0, false);
        return s != null && s.aether > 0 ? s.aether : 0;
    }

    /** pcl_getcasterid(): siapa yang merapal mantra ini pada pemain. */
    public static long casterOf(User sd, int id) {
        SkillInfo s = cari(sd, id, 0, false);
        return s == null ? 0 : s.casterId;
    }

    // ------------------------------------------------------------------
    // setDuration / setAether
    // ------------------------------------------------------------------

    /**
     * pcl_setduration(mantra, waktu, penyihir, recast).
     *
     * <p>Empat cabang, dan urutannya menentukan hasil:</p>
     * <ol>
     *   <li><b>waktu &lt;= 0 pada mantra yang sedang berjalan</b> — hentikan
     *       sekarang, mainkan animasi penutup, panggil kait {@code uncast}.</li>
     *   <li><b>baris ada tapi durasinya sudah habis, aethernya belum</b> —
     *       nyalakan lagi durasinya di baris yang sama.</li>
     *   <li><b>sudah berjalan dan waktu barunya lebih pendek, atau
     *       {@code recast} diminta</b> — timpa. Perhatikan arahnya: tanpa
     *       {@code recast}, merapal ulang mantra yang sisanya masih lebih
     *       panjang <b>tidak berpengaruh</b>. Itu yang mencegah mantra
     *       diperpanjang tanpa batas dengan merapal berulang.</li>
     *   <li><b>belum ada sama sekali</b> — pakai baris baru.</li>
     * </ol>
     *
     * <p>⚠️ Waktu di bawah 1000 ms (tapi di atas 0) dinaikkan jadi 1000 —
     * tik durasinya per detik, jadi apa pun yang lebih pendek akan hilang di
     * tik pertama.</p>
     */
    public static void setDuration(ScriptEngine engine, User sd, int id, int time,
                                   long casterId, boolean recast) {
        if (id <= 0) {
            return;
        }
        int waktu = (time < 1000 && time > 0) ? 1000 : time;

        SkillInfo sama = cari(sd, id, casterId, true);
        boolean sudahBerjalan = sama != null && sama.duration > 0;

        if (sudahBerjalan && waktu <= 0) {
            akhiri(engine, sd, sama, true);
            return;
        }
        if (sama != null && sama.aether > 0 && sama.duration <= 0) {
            sama.duration = waktu;
            kirimDurasi(sd, id, waktu / 1000, sama.casterId);
            return;
        }
        if (sudahBerjalan && (sama.duration > waktu || recast)) {
            sama.duration = waktu;
            kirimDurasi(sd, id, waktu / 1000, sama.casterId);
            return;
        }
        if (!sudahBerjalan && waktu != 0) {
            if (sd.status.duraAether.size() >= MAX_MAGIC_TIMERS) {
                log.warn("[DURASI] {} sudah memegang {} timer — '{}' dilewati",
                        sd.status.name, MAX_MAGIC_TIMERS, MapServer.spellDb.nameOf(id));
                return;
            }
            SkillInfo baru = new SkillInfo();
            baru.id = id;
            baru.duration = waktu;
            baru.casterId = casterId;
            sd.status.duraAether.add(baru);
            kirimDurasi(sd, id, waktu / 1000, casterId);
        }
    }

    /**
     * pcl_setaether(mantra, waktu): aether menempel pada baris yang sama
     * dengan durasi, jadi mantra yang sudah punya baris memakainya kembali.
     */
    public static void setAether(User sd, int id, int time) {
        if (id <= 0) {
            return;
        }
        int waktu = (time < 1000 && time > 0) ? 1000 : time;

        SkillInfo s = cari(sd, id, 0, false);
        if (s != null) {
            s.aether = waktu;
        } else {
            if (sd.status.duraAether.size() >= MAX_MAGIC_TIMERS) {
                return;
            }
            s = new SkillInfo();
            s.id = id;
            s.aether = waktu;
            sd.status.duraAether.add(s);
        }
        MapServer.clientView.playerAetherChanged(sd, id, waktu / 1000);
    }

    // ------------------------------------------------------------------
    // flush
    // ------------------------------------------------------------------

    /**
     * pcl_flushduration(dispel, minId, maxId): hapus banyak durasi sekaligus.
     *
     * <p>Aturan rentangnya diambil apa adanya: {@code minId <= 0} berarti
     * <b>semua</b> mantra, {@code minId > 0} dengan {@code maxId <= 0} berarti
     * satu mantra itu saja, sisanya rentang tertutup. Mantra yang
     * {@code SplDispel}-nya lebih besar dari {@code dispel} kebal dan
     * dilewati.</p>
     *
     * @param panggilUncast false = pcl_flushdurationnouncast, yang menghapus
     *                      durasinya <b>tanpa</b> memanggil kait {@code uncast}
     */
    public static int flushDuration(ScriptEngine engine, User sd, int dispel,
                                    int minId, int maxId, boolean panggilUncast) {
        int max = maxId < minId ? minId : maxId;
        int dihapus = 0;
        // Salinan: kait `uncast` boleh menyentuh daftar durasi pemain lagi.
        for (SkillInfo s : new java.util.ArrayList<>(sd.status.duraAether)) {
            int id = s.id;
            if (MapServer.spellDb.dispelOf(id) > dispel) {
                continue;
            }
            boolean cocok;
            if (minId <= 0) {
                cocok = true;
            } else if (max <= 0) {
                cocok = id == minId;
            } else {
                cocok = id >= minId && id <= max;
            }
            if (!cocok) {
                continue;
            }
            akhiri(engine, sd, s, panggilUncast);
            dihapus++;
        }
        return dihapus;
    }

    /**
     * pcl_flushaether(): sama, tapi untuk aether — dan tanpa kait apa pun,
     * karena aether habis bukan peristiwa yang bisa dibatalkan skrip.
     */
    public static int flushAether(User sd, int dispel, int minId, int maxId) {
        int max = maxId < minId ? minId : maxId;
        int dihapus = 0;
        for (SkillInfo s : new java.util.ArrayList<>(sd.status.duraAether)) {
            int id = s.id;
            if (MapServer.spellDb.dispelOf(id) > dispel) {
                continue;
            }
            boolean cocok;
            if (minId <= 0) {
                cocok = true;
            } else if (max <= 0) {
                cocok = id == minId;
            } else {
                cocok = id >= minId && id <= max;
            }
            if (!cocok || s.aether <= 0) {
                continue;
            }
            s.aether = 0;
            MapServer.clientView.playerAetherChanged(sd, id, 0);
            buangBilaKosong(sd, s);
            dihapus++;
        }
        return dihapus;
    }

    /** pcl_refreshdurations(): kirim ulang seluruh bilah durasi ke klien. */
    public static void refresh(User sd) {
        for (SkillInfo s : sd.status.duraAether) {
            if (s.id > 0 && s.duration > 0) {
                kirimDurasi(sd, s.id, s.duration / 1000, s.casterId);
            }
        }
    }

    // ------------------------------------------------------------------
    // tik
    // ------------------------------------------------------------------

    /**
     * bl_duratimer(): satu tik <b>satu detik</b> untuk seorang pemain.
     *
     * <p>Tiga hal terjadi tiap detik, dan ketiganya kait skrip: mantra pasif
     * di buku mantra ({@code while_passive}), perlengkapan yang dikenakan
     * ({@code while_equipped}), lalu tiap durasi yang berjalan
     * ({@code while_cast}).</p>
     *
     * <p>⚠️ {@code while_cast} menerima <b>penyihirnya</b> sebagai argumen
     * kedua bila ia masih ada di dunia. Untuk mob, C hanya memanggilnya bila
     * mob itu masih bernyawa — mantra yang penyihirnya sudah mati berhenti
     * mendapat tik, tapi durasinya tetap berkurang.</p>
     */
    /**
     * pc_disptimertick() (map/pc.c) — satu detik penghitung waktu di layar.
     *
     * <p>Di C ini timer terpisah yang dipasang {@code pcl_settimer}; di sini
     * ia menumpang tik satu detik yang sama, karena keduanya berdenyut pada
     * irama yang sama dan menyapu daftar pemain yang sama.</p>
     *
     * <p>Saat habis, kait {@code pc_timer.display_timer} dipanggil
     * <b>sekali</b> lalu timernya dimatikan — bukan diulang.</p>
     */
    private static void tikTimerTampilan(ScriptEngine engine, User sd) {
        if (sd.disptimerType == 0) {
            return;
        }
        sd.disptimerTick = Math.max(0, sd.disptimerTick - 1);
        if (sd.disptimerTick <= 0) {
            sd.disptimerType = 0;
            kait(engine, sd, "pc_timer", "display_timer", null);
        }
    }

    public static void tick(ScriptEngine engine, User sd) {
        tikTimerTampilan(engine, sd);

        for (int id : sd.status.spells) {
            if (id > 0) {
                kait(engine, sd, MapServer.spellDb.nameOf(id), "while_passive", null);
            }
        }
        for (int i = 0; i < 14; i++) {
            var it = sd.status.equipAt(i);
            if (it != null && it.id > 0) {
                kait(engine, sd, MapServer.itemDb.info(it.id).name(), "while_equipped", null);
            }
        }

        for (SkillInfo s : new java.util.ArrayList<>(sd.status.duraAether)) {
            if (s.id <= 0) {
                continue;
            }
            BlockList caster = s.casterId > 0 ? MapServer.blockById(s.casterId) : null;

            if (s.duration > 0) {
                s.duration -= 1000;

                boolean casterHidup = caster != null
                        && (!(caster instanceof Mob mb) || mb.currentVita > 0);
                if (casterHidup) {
                    kait(engine, sd, MapServer.spellDb.nameOf(s.id), "while_cast", caster);
                } else if (caster == null) {
                    kait(engine, sd, MapServer.spellDb.nameOf(s.id), "while_cast", null);
                }

                if (s.duration <= 0) {
                    s.duration = 0;
                    akhiri(engine, sd, s, true);
                    continue;
                }
            }

            if (s.aether > 0) {
                s.aether -= 1000;
                if (s.aether <= 0) {
                    s.aether = 0;
                    MapServer.clientView.playerAetherChanged(sd, s.id, 0);
                    buangBilaKosong(sd, s);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // bersama
    // ------------------------------------------------------------------

    /**
     * Akhiri satu durasi: beri tahu klien, mainkan animasi penutup, lepas
     * penyihirnya, lalu panggil kait {@code uncast}.
     *
     * <p>Urutannya penting dan diambil dari C: {@code casterId} sudah
     * dinolkan sebelum kait dipanggil, tetapi benda penyihirnya <b>tetap</b>
     * dikirim sebagai argumen — skrip {@code uncast} masih perlu tahu siapa
     * yang merapalnya.</p>
     */
    private static void akhiri(ScriptEngine engine, User sd, SkillInfo s,
                               boolean panggilUncast) {
        int id = s.id;
        BlockList caster = s.casterId > 0 ? MapServer.blockById(s.casterId) : null;

        kirimDurasi(sd, id, 0, s.casterId);
        s.duration = 0;
        s.casterId = 0;

        if (s.animation > 0) {
            // C mengirim animasi -1 kali = penanda "hentikan animasinya"
            MapServer.clientView.objectAnimation(sd, s.animation, -1);
            s.animation = 0;
        }
        buangBilaKosong(sd, s);

        if (!panggilUncast) {
            return;
        }
        kait(engine, sd, MapServer.spellDb.nameOf(id), "uncast", caster);
    }

    /**
     * Baris baru benar-benar dibuang hanya bila durasi DAN aether sama-sama
     * habis. Di C ini muncul sebagai {@code if (aether == 0) id = 0;} di
     * setiap tempat yang menolkan durasi, dan sebaliknya.
     */
    private static void buangBilaKosong(User sd, SkillInfo s) {
        if (s.duration <= 0 && s.aether <= 0) {
            sd.status.duraAether.remove(s);
        }
    }

    private static void kirimDurasi(User sd, int id, int detik, long casterId) {
        String nama = "";
        if (casterId > 0 && casterId != sd.id) {
            BlockList bl = MapServer.blockById(casterId);
            if (bl instanceof User tsd) {
                nama = tsd.status.name;
            }
        }
        MapServer.clientView.playerDurationChanged(sd, id, detik, nama);
    }

    /**
     * Panggil satu kait skrip milik mantra atau barang.
     *
     * <p>{@code caster} null berarti kaitnya dipanggil dengan pemain saja —
     * di C itulah bedanya {@code sl_doscript_blargs(..., 1, &sd->bl)} dan
     * versi dua argumen. Engine null (mis. sebelum skrip dimuat) membuatnya
     * diam saja, bukan meledak.</p>
     */
    private static void kait(ScriptEngine engine, User sd, String root, String method,
                             BlockList caster) {
        if (engine == null || root == null || root.isEmpty()) {
            return;
        }
        try {
            org.luaj.vm2.LuaValue pemain = engine.playerRef(sd.scriptPlayer());
            if (caster != null) {
                engine.doScript(root, method, pemain, engine.objectRef(caster));
            } else {
                engine.doScript(root, method, pemain);
            }
        } catch (RuntimeException e) {
            log.error("[DURASI] kait '{}.{}' gagal", root, method, e);
        }
    }
}
