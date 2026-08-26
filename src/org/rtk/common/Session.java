package org.rtk.common;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Port of struct socket_data (common/socket.h) together with the
 * RFIFO / WFIFO macro family.
 *
 * The original C server runs on a little-endian host, so the plain
 * RFIFOW/RFIFOL/WFIFOW/WFIFOL macros are little-endian accesses, while
 * SWAP16/SWAP32-wrapped accesses are big-endian. Both variants exist here:
 * rfifoW/wfifoW are little-endian, rfifoWBE/wfifoWBE are big-endian.
 *
 * Threading: buffer diisi oleh IO thread dan dikonsumsi oleh logic thread.
 * Semua mutasi buffer dilakukan di bawah monitor objek Session ini —
 * {@link NetServer} mengunci session selama membaca socket maupun selama
 * memproses paket, jadi pemanggil RFIFO / WFIFO di dalam parser tidak perlu
 * mengunci sendiri.
 */
public class Session {

    public interface ParseFunc {
        /** Returns 0 when a packet was handled (or nothing to do). */
        int parse(int fd);
    }

    private static final int INITIAL_BUFFER =
            Props.getInt("net.session_buffer_bytes", 4096);

    /** Lapisan socket pemilik session ini (satu instance per server). */
    public final NetServer net;

    public final int fd;
    SocketChannel channel;

    byte[] rdata = new byte[INITIAL_BUFFER];
    int rdataSize = 0;
    int rdataPos = 0;

    /**
     * Area penyusunan paket keluar. Hanya disentuh logic thread: wfifoSet()
     * menyalin paket yang sudah jadi ke {@link #outbox}, sehingga IO thread
     * tidak pernah menyentuh array ini. Basis penyusunan selalu 0.
     */
    byte[] wdata = new byte[INITIAL_BUFFER];
    int wdataSize = 0;

    /** Paket siap kirim, serah terima logic thread -> IO thread. */
    private final ConcurrentLinkedQueue<byte[]> outbox = new ConcurrentLinkedQueue<>();

    /** Sisa paket yang belum tertulis penuh; hanya disentuh IO thread. */
    private ByteBuffer pendingWrite;

    public volatile boolean eof = false;
    public Object sessionData;
    public ParseFunc funcParse;
    public InetSocketAddress clientAddr;
    public String name = "";

    /**
     * Nomor urut paket keluar — {@code session[fd]->increment} di C.
     *
     * <p>Dinaikkan oleh {@code WFIFOHEADER()}, yang menaruh hasilnya di byte
     * [4]. <b>Hanya paket yang di C dibangun lewat makro itu</b> yang
     * membawanya (opcode 0x07, 0x0C, 0x0D, 0x13, 0x1F, 0x37, 0x3A, 0x51);
     * paket lain meninggalkan [4] bernilai 0. Jangan diseragamkan — klien
     * membedakan keduanya.</p>
     */
    private int increment;

    /** WFIFOHEADER(): naikkan lalu ambil nomor urut berikutnya (0..255). */
    public int nextIncrement() {
        increment = (increment + 1) & 0xFF;
        return increment;
    }

    /** true bila handler accept belum dijalankan logic thread. */
    volatile boolean pendingAccept = false;

    /** true bila session sudah antre di inbound queue (dedup). */
    final AtomicBoolean queued = new AtomicBoolean(false);

    Session(NetServer net, int fd, SocketChannel channel) {
        this.net = net;
        this.fd = fd;
        this.channel = channel;
    }

    // ------------------------------------------------------------------
    // Dipakai IO thread (selalu di bawah synchronized(session))
    // ------------------------------------------------------------------

    /** Tambahkan n byte dari buffer socket ke belakang rdata. */
    void appendRead(ByteBuffer src, int n) {
        if (rdataSize + n > rdata.length) {
            int cap = rdata.length;
            while (cap < rdataSize + n) {
                cap *= 2;
            }
            byte[] grown = new byte[cap];
            System.arraycopy(rdata, 0, grown, 0, rdataSize);
            rdata = grown;
        }
        src.get(rdata, rdataSize, n);
        rdataSize += n;
    }

    /**
     * Kirim paket-paket di outbox ke socket. Mengembalikan true bila sudah
     * habis. Dipanggil hanya oleh IO thread.
     */
    boolean flushWrite() throws java.io.IOException {
        while (true) {
            if (pendingWrite == null) {
                byte[] next = outbox.poll();
                if (next == null) {
                    return true;
                }
                pendingWrite = ByteBuffer.wrap(next);
            }
            channel.write(pendingWrite);
            if (pendingWrite.hasRemaining()) {
                return false; // socket penuh, lanjut saat OP_WRITE berikutnya
            }
            pendingWrite = null;
        }
    }

    // ------------------------------------------------------------------
    // RFIFO — read buffer
    // ------------------------------------------------------------------

    public int rfifoRest() {
        return rdataSize - rdataPos;
    }

    public int rfifoB(int pos) {
        return rdata[rdataPos + pos] & 0xFF;
    }

    /** Little-endian 16-bit read (RFIFOW on x86). */
    public int rfifoW(int pos) {
        return (rdata[rdataPos + pos] & 0xFF)
                | ((rdata[rdataPos + pos + 1] & 0xFF) << 8);
    }

    /** Big-endian 16-bit read (SWAP16(RFIFOW)). */
    public int rfifoWBE(int pos) {
        return ((rdata[rdataPos + pos] & 0xFF) << 8)
                | (rdata[rdataPos + pos + 1] & 0xFF);
    }

    /** Little-endian 32-bit read (RFIFOL on x86). */
    public int rfifoL(int pos) {
        return (rdata[rdataPos + pos] & 0xFF)
                | ((rdata[rdataPos + pos + 1] & 0xFF) << 8)
                | ((rdata[rdataPos + pos + 2] & 0xFF) << 16)
                | ((rdata[rdataPos + pos + 3] & 0xFF) << 24);
    }

    /** Big-endian 32-bit read (SWAP32(RFIFOL)). */
    public int rfifoLBE(int pos) {
        return ((rdata[rdataPos + pos] & 0xFF) << 24)
                | ((rdata[rdataPos + pos + 1] & 0xFF) << 16)
                | ((rdata[rdataPos + pos + 2] & 0xFF) << 8)
                | (rdata[rdataPos + pos + 3] & 0xFF);
    }

    public byte[] rfifoBytes(int pos, int len) {
        byte[] out = new byte[len];
        System.arraycopy(rdata, rdataPos + pos, out, 0, len);
        return out;
    }

    /** Reads a fixed-length field and stops at the first NUL (like memcpy + C string use). */
    public String rfifoString(int pos, int maxLen) {
        int avail = Math.min(maxLen, rfifoRest() - pos);
        int end = 0;
        while (end < avail && rdata[rdataPos + pos + end] != 0) {
            end++;
        }
        return new String(rdata, rdataPos + pos, end, StandardCharsets.ISO_8859_1);
    }

    public void rfifoSkip(int len) {
        if (rdataSize - rdataPos - len < 0) {
            throw new IllegalStateException("RFIFOSKIP error on fd " + fd);
        }
        rdataPos += len;
    }

    public void rfifoFlush() {
        if (rdataSize == rdataPos) {
            rdataSize = 0;
            rdataPos = 0;
        } else {
            rdataSize -= rdataPos;
            System.arraycopy(rdata, rdataPos, rdata, 0, rdataSize);
            rdataPos = 0;
        }
    }

    /** Direct access for Crypt on inbound packets (RFIFOP(fd,0)). */
    public byte[] rbuf() {
        return rdata;
    }

    public int roff() {
        return rdataPos;
    }

    // ------------------------------------------------------------------
    // WFIFO — write buffer. Writes are staged after wdataSize and are
    // committed by wfifoSet(len), mirroring the C macros.
    // ------------------------------------------------------------------

    private void ensureW(int posEnd) {
        int need = wdataSize + posEnd;
        if (need > wdata.length) {
            int cap = wdata.length;
            while (cap < need) {
                cap *= 2;
            }
            byte[] n = new byte[cap];
            // Salin SELURUH isi lama, bukan hanya `wdataSize`.
            // Byte yang sedang disusun berada DI BELAKANG wdataSize
            // (di `wdataSize + pos`), jadi menyalin sebanyak wdataSize saja
            // membuang semuanya diam-diam. Pernah kejadian: paket daftar
            // peta 19.708 byte terkirim sebagai 19.708 byte NOL, dan
            // tautan map-char putus-nyambung tiap 10 detik.
            System.arraycopy(wdata, 0, n, 0, wdata.length);
            wdata = n;
        }
    }

    public void wfifoB(int pos, int v) {
        ensureW(pos + 1);
        wdata[wdataSize + pos] = (byte) v;
    }

    /** Little-endian 16-bit write (WFIFOW on x86). */
    public void wfifoW(int pos, int v) {
        ensureW(pos + 2);
        wdata[wdataSize + pos] = (byte) (v & 0xFF);
        wdata[wdataSize + pos + 1] = (byte) ((v >> 8) & 0xFF);
    }

    /** Big-endian 16-bit write (WFIFOW = SWAP16(v)). */
    public void wfifoWBE(int pos, int v) {
        ensureW(pos + 2);
        wdata[wdataSize + pos] = (byte) ((v >> 8) & 0xFF);
        wdata[wdataSize + pos + 1] = (byte) (v & 0xFF);
    }

    /** Little-endian 32-bit write (WFIFOL on x86). */
    public void wfifoL(int pos, int v) {
        ensureW(pos + 4);
        wdata[wdataSize + pos] = (byte) (v & 0xFF);
        wdata[wdataSize + pos + 1] = (byte) ((v >> 8) & 0xFF);
        wdata[wdataSize + pos + 2] = (byte) ((v >> 16) & 0xFF);
        wdata[wdataSize + pos + 3] = (byte) ((v >> 24) & 0xFF);
    }

    /** Big-endian 32-bit write (WFIFOL = SWAP32(v)). */
    public void wfifoLBE(int pos, int v) {
        ensureW(pos + 4);
        wdata[wdataSize + pos] = (byte) ((v >> 24) & 0xFF);
        wdata[wdataSize + pos + 1] = (byte) ((v >> 16) & 0xFF);
        wdata[wdataSize + pos + 2] = (byte) ((v >> 8) & 0xFF);
        wdata[wdataSize + pos + 3] = (byte) (v & 0xFF);
    }

    public void wfifoBytes(int pos, byte[] src) {
        wfifoBytes(pos, src, src.length);
    }

    public void wfifoBytes(int pos, byte[] src, int len) {
        ensureW(pos + len);
        System.arraycopy(src, 0, wdata, wdataSize + pos, len);
    }

    /** memset(WFIFOP(fd,pos), 0, len) */
    public void wfifoZero(int pos, int len) {
        ensureW(pos + len);
        java.util.Arrays.fill(wdata, wdataSize + pos, wdataSize + pos + len, (byte) 0);
    }

    /** strcpy semantics: writes the string plus a terminating NUL. */
    public void wfifoString(int pos, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        ensureW(pos + b.length + 1);
        System.arraycopy(b, 0, wdata, wdataSize + pos, b.length);
        wdata[wdataSize + pos + b.length] = 0;
    }

    /** memcpy of a string without the NUL terminator. */
    public void wfifoStringRaw(int pos, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        wfifoBytes(pos, b, b.length);
    }

    /**
     * Fixed-width string field: copies at most width bytes, zero-padding the
     * rest (memset + memcpy pattern in the C code).
     */
    public void wfifoStringFixed(int pos, String s, int width) {
        wfifoZero(pos, width);
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        int n = Math.min(b.length, width);
        System.arraycopy(b, 0, wdata, wdataSize + pos, n);
    }

    /**
     * WFIFOSET: paket sepanjang len byte dianggap selesai disusun. Isinya
     * disalin ke outbox agar IO thread bisa mengirimnya, lalu area
     * penyusunan dipakai lagi dari awal untuk paket berikutnya.
     */
    public void wfifoSet(int len) {
        ensureW(len);
        byte[] packet = new byte[len];
        System.arraycopy(wdata, wdataSize, packet, 0, len);
        outbox.add(packet);

        // Bersihkan area penyusunan supaya paket berikutnya mulai dari nol.
        //
        // Di C setiap paket ditulis di offset BARU (`WFIFOP` = wdata +
        // wdata_size + pos, dan WFIFOSET memajukan wdata_size), jadi byte
        // yang tidak ditulis eksplisit selalu nol dari buffer yang di-CALLOC.
        // Port ini memakai ulang offset yang sama, sehingga byte tersebut
        // mewarisi isi paket SEBELUMNYA — dan karena crypt mengenkripsi di
        // tempat, yang diwarisi adalah sampah terenkripsi. Itu ikut terkirim
        // karena beberapa paket mendeklarasikan panjang LEBIH BESAR dari
        // ladang yang benar-benar ditulis (0x1E sisa 2 byte, 0x05 sisa 1,
        // 0x15 sisa 5, 0x04 sisa 2). Terlihat nyata di dump 26 Agu 2026:
        // paket 0x04 berekor "4D 79" (ASCII "My" dari "Mythic Nexus").
        java.util.Arrays.fill(wdata, wdataSize, wdata.length, (byte) 0);

        net.wantWrite(this);
    }

    /**
     * Isi buffer baca seolah byte ini baru tiba dari socket. Pasangan
     * {@link #takeOutbound()} untuk menguji parser tanpa jaringan.
     */
    public void feedInbound(byte[] data) {
        appendRead(java.nio.ByteBuffer.wrap(data), data.length);
    }

    /**
     * Ambil seluruh byte yang menunggu kirim dan kosongkan antreannya.
     * Dipakai untuk memeriksa paket yang dihasilkan tanpa socket sungguhan.
     */
    public byte[] takeOutbound() {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] p;
        while ((p = outbox.poll()) != null) {
            out.write(p, 0, p.length);
        }
        return out.toByteArray();
    }

    /** Direct access for Crypt on outbound packets (WFIFOP(fd,0)). */
    public byte[] wbuf() {
        return wdata;
    }

    public int woff() {
        return wdataSize;
    }

    // ------------------------------------------------------------------
    // Client address helpers
    // ------------------------------------------------------------------

    /**
     * The client IPv4 address in the same integer layout as
     * session[fd]->client_addr.sin_addr.s_addr read on a little-endian host
     * (first octet in the lowest byte).
     */
    public int clientIp() {
        if (clientAddr == null) {
            return 0;
        }
        byte[] a = clientAddr.getAddress().getAddress();
        if (a.length != 4) {
            return 0;
        }
        return (a[0] & 0xFF) | ((a[1] & 0xFF) << 8) | ((a[2] & 0xFF) << 16) | ((a[3] & 0xFF) << 24);
    }

    public String clientIpString() {
        if (clientAddr == null) {
            return "0.0.0.0";
        }
        InetAddress a = clientAddr.getAddress();
        return a.getHostAddress();
    }

    /** CONVIP(ip): "a.b.c.d" from an s_addr-style integer. */
    public static String ipToString(int sAddr) {
        return (sAddr & 0xFF) + "." + ((sAddr >> 8) & 0xFF) + "."
                + ((sAddr >> 16) & 0xFF) + "." + ((sAddr >> 24) & 0xFF);
    }
}
