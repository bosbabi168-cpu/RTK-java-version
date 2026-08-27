package org.rtk.map.proto;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.rtk.common.Session;
import org.rtk.map.ClientCommands;
import org.rtk.map.User;

/**
 * Pembaca bingkai RTK2 — satu-satunya kelas yang boleh menyentuh byte
 * <b>dan</b> memanggil {@link ClientCommands}.
 *
 * <p>Isinya sengaja tidak memutuskan apa pun: tiap cabang membaca ladangnya
 * lalu memanggil satu perintah. Kalau sebuah cabang di sini mulai memeriksa
 * keadaan pemain, memanggil skrip, atau menyentuh peta, baris itu salah
 * tempat — tempatnya di {@code MapCommands}. Batas yang sama, arah
 * berlawanan, dengan aturan "logika jangan memanggil Clif".</p>
 *
 * <p>Perbandingannya dengan {@code Clif.parse*} (RetroTK) menjelaskan kenapa
 * lapisan ini dipisah lebih dulu: keempat method itu dulu berisi ~15 baris
 * pembacaan byte dan ~65 baris logika permainan. Di sini sisi logikanya
 * <b>nol baris</b>, karena tempatnya sudah ada.</p>
 */
public final class Inbound {

    private static final Logger log = LogManager.getLogger(Inbound.class);

    private Inbound() {
    }

    /**
     * Apa yang harus terjadi saat klien memperkenalkan diri.
     *
     * <p>Ada sebagai antarmuka supaya paket ini tidak perlu tahu apa pun
     * tentang {@code MapServer} — autentikasi melibatkan char server,
     * database, dan tabel sesi, semuanya di luar urusan format kabel.</p>
     */
    public interface Handshake {
        /** @return false bila perkenalannya ditolak dan sesinya harus ditutup */
        boolean playerIntroduces(int fd, Session s, String characterName);
    }

    /**
     * Layani satu bingkai yang sudah dipastikan lengkap.
     *
     * @param sd pemain pemilik sesi, atau null bila belum terautentikasi
     * @throws Wire.Malformed bila muatannya tidak cocok dengan opcodenya
     */
    public static void dispatch(int fd, Session s, User sd, int frameLength,
                                ClientCommands cmd, Handshake handshake) {
        int op = Wire.opcode(s);
        Wire.Reader r = new Wire.Reader(s, frameLength);

        if (sd == null) {
            // Sebelum terautentikasi hanya perkenalan yang dilayani. Berbeda
            // dari RetroTK, tidak ada aturan "yang ini jangan didekripsi"
            // yang bisa dilanggar — tidak ada enkripsi sama sekali.
            if (op == Wire.OP_HELLO) {
                hello(fd, s, r, handshake);
            } else {
                log.debug("[RTK2] opcode 0x{} diabaikan: sesi belum terautentikasi",
                        String.format("%04X", op));
            }
            return;
        }

        switch (op) {
            case Wire.OP_HELLO -> log.warn("[RTK2] {} memperkenalkan diri dua kali", sd.name());
            case Wire.OP_PING -> { }

            case Wire.OP_WALK -> {
                int arah = r.u8();
                int x = r.u16();
                int y = r.u16();
                // RTK2 tidak membawa permintaan gambar ulang — server tahu
                // sendiri apa yang baru terlihat. Lihat catatan kebocoran di
                // ClientCommands.playerWalks; null di sini yang benar.
                cmd.playerWalks(sd, arah, x, y, null);
            }
            case Wire.OP_CLICK -> cmd.playerClicks(sd, r.u64());

            case Wire.OP_PICKUP -> cmd.playerPicksUp(sd, r.u8());
            case Wire.OP_DROP_ITEM -> {
                int slot = r.u8();
                boolean semua = r.u8() != 0;
                cmd.playerDropsItem(sd, slot, semua);
            }
            case Wire.OP_DROP_GOLD -> cmd.playerDropsGold(sd, r.u64());
            case Wire.OP_HAND_ITEM -> {
                int slot = r.u8();
                int jumlah = r.u16();
                cmd.playerHandsItem(sd, slot, jumlah);
            }
            case Wire.OP_HAND_GOLD -> cmd.playerHandsGold(sd, r.u64());

            case Wire.OP_WIELD -> cmd.playerWields(sd, r.u8());
            case Wire.OP_UNEQUIP -> cmd.playerUnequips(sd, r.u8());
            case Wire.OP_EAT -> cmd.playerEatsItem(sd, r.u8());
            case Wire.OP_USE -> cmd.playerUsesItem(sd, r.u8());
            case Wire.OP_THROW -> {
                int slot = r.u8();
                cmd.playerThrowsItem(sd, slot, r.u8() != 0);
            }

            case Wire.OP_SAY -> {
                int saluran = r.u8();
                cmd.playerSays(sd, saluran, r.str());
            }
            case Wire.OP_WHISPER -> {
                String tujuan = r.str();
                cmd.playerWhispers(sd, tujuan, r.str());
            }

            case Wire.OP_EXCHANGE_START -> cmd.playerStartsExchange(sd, r.u64());
            case Wire.OP_EXCHANGE_ITEM -> {
                int slot = r.u8();
                int jumlah = r.u16();
                cmd.playerOffersItem(sd, slot, jumlah);
            }
            case Wire.OP_EXCHANGE_GOLD -> cmd.playerOffersGold(sd, r.u64());
            case Wire.OP_EXCHANGE_CANCEL -> cmd.playerCancelsExchange(sd);
            case Wire.OP_EXCHANGE_CONFIRM -> cmd.playerConfirmsExchange(sd, r.u64());

            case Wire.OP_ATTACK -> cmd.playerAttacks(sd);

            case Wire.OP_ANSWER_MENU -> cmd.playerAnswersMenu(sd, jawaban(r));
            case Wire.OP_ANSWER_DIALOG -> cmd.playerAnswersDialog(sd, jawaban(r));

            default -> log.debug("[RTK2] opcode 0x{} tidak dikenal (dari {})",
                    String.format("%04X", op), sd.name());
        }
    }

    private static void hello(int fd, Session s, Wire.Reader r, Handshake handshake) {
        long magic = r.u32();
        if (magic != Wire.MAGIC) {
            throw new Wire.Malformed("magic 0x" + Long.toHexString(magic)
                    + " bukan RTK2");
        }
        int versi = r.u16();
        if (versi != Wire.VERSION) {
            // Versi dijawab dengan penutupan, bukan usaha menebak. Klien dan
            // server dibuat bersama; versi yang berbeda berarti salah satunya
            // belum di-deploy, dan menerimanya setengah jalan menghasilkan
            // kegagalan yang jauh lebih sulit dibaca daripada penolakan ini.
            throw new Wire.Malformed("versi protokol " + versi
                    + ", server memakai " + Wire.VERSION);
        }
        String nama = r.str();
        if (nama.isEmpty() || nama.length() > 16) {
            throw new Wire.Malformed("nama karakter '" + nama + "' tidak masuk akal");
        }
        if (!handshake.playerIntroduces(fd, s, nama)) {
            s.eof = true;
        }
    }

    /**
     * Satu jawaban pemain, apa pun bentuknya.
     *
     * <p>Keenam ragamnya persis {@code ClientCommands.Answer} — daftar itu
     * yang menentukan bentuk kabelnya, bukan sebaliknya. Ragam 0 (batal)
     * menghasilkan null, dan itu <b>bukan</b> jawaban kosong: skripnya
     * dilepas, tidak dilanjutkan.</p>
     */
    private static ClientCommands.Answer jawaban(Wire.Reader r) {
        int ragam = r.u8();
        return switch (ragam) {
            case 0 -> null;
            case 1 -> ClientCommands.Answer.choice(r.u16());
            case 2 -> ClientCommands.Answer.text(r.str());
            case 3 -> ClientCommands.Answer.itemName(r.str());
            case 4 -> ClientCommands.Answer.slot(r.u8());
            case 5 -> ClientCommands.Answer.step(r.u8());
            default -> throw new Wire.Malformed("ragam jawaban " + ragam + " tidak dikenal");
        };
    }
}
