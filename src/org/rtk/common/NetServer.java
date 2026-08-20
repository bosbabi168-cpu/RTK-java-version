package org.rtk.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Port of common/socket.c on top of java.nio — satu instance per server.
 *
 * Berbeda dengan versi C (dan versi awal port ini) yang memakai state global,
 * kelas ini dibuat per server: login, char, dan map masing-masing punya
 * selector, tabel session, dan handler sendiri. Dengan begitu ketiganya bisa
 * hidup berdampingan dalam satu JVM tanpa saling menimpa.
 *
 * Model threading:
 *
 *   [IO thread]  selector: accept / read / write
 *        |  append ke buffer session (terkunci per session)
 *        v
 *   ArrayBlockingQueue&lt;Session&gt;   <-- serah terima, dengan dedup per session
 *        |
 *   [Logic thread]  timer + parse paket + tulis balasan
 *
 * Satu session hanya boleh antre satu kali (flag {@code queued}), sehingga
 * antrean tidak pernah melebihi jumlah session — queue bounded tapi dijamin
 * tidak pernah penuh, jadi IO thread tidak pernah terblokir.
 *
 * Logika permainan sengaja tetap berjalan di SATU thread per server:
 * urutan paket per koneksi harus terjaga, dan engine skrip (LuaJ) tidak
 * thread-safe. Yang dipisah adalah IO dari logika, bukan logika dari dirinya
 * sendiri.
 */
public final class NetServer {

    private static final Logger log = LogManager.getLogger(NetServer.class);

    public static final int FD_SETSIZE = 4096;
    public static final int FIFOSIZE_SERVER = 128 * 1024;

    private final String name;
    /** Tabel session; ditulis IO thread (accept) & logic thread (close). */
    private final AtomicReferenceArray<Session> sessions = new AtomicReferenceArray<>(FD_SETSIZE);
    private volatile int fdMax = 1; // fd 0 dicadangkan, seperti di kode C

    private Selector selector;
    private Session.ParseFunc defaultParse;
    private Session.ParseFunc defaultAccept;

    /** Serah terima IO -> logika. Kapasitas > FD_SETSIZE agar tak pernah penuh. */
    private final ArrayBlockingQueue<Session> inbound = new ArrayBlockingQueue<>(FD_SETSIZE + 16);

    /** Permintaan OP_WRITE dari logic thread, diterapkan IO thread. */
    private final ConcurrentLinkedQueue<Session> writeInterest = new ConcurrentLinkedQueue<>();

    private final ByteBuffer readBuf =
            ByteBuffer.allocate(Props.getInt("net.read_buffer_bytes", 64 * 1024));

    // brute-force / throttle (socket.c ConnectHistory)
    private final Map<Integer, Integer> throttles = new HashMap<>();
    private final Map<Integer, Boolean> ipLockout = new HashMap<>();

    private Thread ioThread;
    private volatile boolean running;

    public NetServer(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** do_socket(): siapkan selector. */
    public void doSocket() {
        try {
            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException("Unable to open selector for " + name, e);
        }
    }

    public void setDefaultParse(Session.ParseFunc f) {
        defaultParse = f;
    }

    public void setDefaultAccept(Session.ParseFunc f) {
        defaultAccept = f;
    }

    /** Pengganti akses langsung ke tabel session global. */
    public Session session(int fd) {
        if (fd <= 0 || fd >= FD_SETSIZE) {
            return null;
        }
        return sessions.get(fd);
    }

    public int fdMax() {
        return fdMax;
    }

    private synchronized int newFd() {
        for (int i = 1; i < FD_SETSIZE; i++) {
            if (sessions.get(i) == null) {
                if (i >= fdMax) {
                    fdMax = i + 1;
                }
                return i;
            }
        }
        throw new IllegalStateException("Out of session slots on " + name);
    }

    /** make_listen_port() */
    public int makeListenPort(int port) {
        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ssc.socket().setReuseAddress(true);
            ssc.socket().bind(new InetSocketAddress(port));
            int fd = newFd();
            sessions.set(fd, new Session(this, fd, null));
            ssc.register(selector, SelectionKey.OP_ACCEPT, fd);
            return fd;
        } catch (IOException e) {
            throw new RuntimeException("Unable to listen on port " + port + " (" + name + ")", e);
        }
    }

    /** make_connection(): connect keluar, blocking seperti versi C. */
    public int makeConnection(String ip, int port) {
        try {
            SocketChannel sc = SocketChannel.open();
            sc.socket().setTcpNoDelay(true);
            sc.connect(new InetSocketAddress(ip, port));
            sc.configureBlocking(false);
            int fd = newFd();
            Session s = new Session(this, fd, sc);
            s.clientAddr = (InetSocketAddress) sc.getRemoteAddress();
            sessions.set(fd, s);
            sc.register(selector, SelectionKey.OP_READ, fd);
            selector.wakeup();
            return fd;
        } catch (IOException e) {
            log.error("make_connection failed: {}:{} ({})", ip, port, e.getMessage());
            return 0;
        }
    }

    /** session_eof(): tutup socket dan bebaskan slot. */
    public void sessionEof(int fd) {
        Session s = session(fd);
        if (s == null) {
            return;
        }
        try {
            if (s.channel != null) {
                SelectionKey key = s.channel.keyFor(selector);
                if (key != null) {
                    key.cancel();
                }
                s.channel.close();
            }
        } catch (IOException ignored) {
        }
        sessions.set(fd, null);
    }

    /**
     * Buat sesi tanpa socket pada fd tertentu, untuk menyusun paket di luar
     * jaringan — dipakai uji tata letak byte dan perkakas rekam/putar-ulang
     * paket. Sesi lama pada fd itu (bila ada) digantikan.
     */
    public Session openTestSession(int fd) {
        Session s = new Session(this, fd, null);
        sessions.set(fd, s);
        if (fd >= fdMax) {
            fdMax = fd + 1;
        }
        return s;
    }

    /** Dipanggil dari logic thread saat ada data siap kirim. */
    void wantWrite(Session s) {
        writeInterest.add(s);
        if (selector != null) {
            selector.wakeup();
        }
    }

    /** Masukkan session ke antrean logika, maksimal satu entri per session. */
    private void enqueue(Session s) {
        if (s.queued.compareAndSet(false, true)) {
            if (!inbound.offer(s)) {
                // Tidak akan terjadi: kapasitas > FD_SETSIZE dan dedup per session.
                s.queued.set(false);
                log.error("[{}] inbound queue penuh - paket fd {} ditunda", name, s.fd);
            }
        }
    }

    /**
     * Diambil logic thread. Mengembalikan null bila tak ada pekerjaan sampai
     * timeout habis (supaya timer tetap jalan).
     */
    public Session pollInbound(long timeoutMs) {
        try {
            Session s = inbound.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (s != null) {
                s.queued.set(false);
            }
            return s;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    // ------------------------------------------------------------------
    // IO thread
    // ------------------------------------------------------------------

    /** Jalankan loop selector di thread sendiri. */
    public void startIo() {
        running = true;
        ioThread = new Thread(this::ioLoop, "rtk-io-" + name);
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public void stopIo() {
        running = false;
        if (selector != null) {
            selector.wakeup();
        }
    }

    private void ioLoop() {
        while (running) {
            try {
                applyWriteInterest();
                selector.select(50);
                Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    it.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    int fd = (Integer) key.attachment();
                    try {
                        if (key.isAcceptable()) {
                            acceptClient((ServerSocketChannel) key.channel());
                            continue;
                        }
                        Session s = session(fd);
                        if (s == null) {
                            key.cancel();
                            continue;
                        }
                        if (key.isReadable()) {
                            readSession(s);
                        }
                        if (key.isValid() && key.isWritable()) {
                            writeSession(s, key);
                        }
                    } catch (IOException e) {
                        Session s = session(fd);
                        if (s != null) {
                            s.eof = true;
                            enqueue(s);
                        }
                    }
                }
            } catch (IOException e) {
                if (running) {
                    log.error("[{}] IO loop error: {}", name, e.getMessage());
                }
            } catch (RuntimeException e) {
                log.error("[{}] IO loop error", name, e);
            }
        }
    }

    private void applyWriteInterest() {
        Session s;
        while ((s = writeInterest.poll()) != null) {
            if (s.channel == null) {
                continue;
            }
            SelectionKey key = s.channel.keyFor(selector);
            if (key != null && key.isValid()) {
                key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
            }
        }
    }

    private void acceptClient(ServerSocketChannel ssc) throws IOException {
        SocketChannel sc = ssc.accept();
        if (sc == null) {
            return;
        }
        sc.configureBlocking(false);
        sc.socket().setTcpNoDelay(true);
        InetSocketAddress remote = (InetSocketAddress) sc.getRemoteAddress();
        int ipKey = ipKey(remote);

        synchronized (this) {
            if (throttles.getOrDefault(ipKey, 0) > 0 || ipLockout.getOrDefault(ipKey, false)) {
                sc.close();
                return;
            }
        }

        int fd = newFd();
        Session s = new Session(this, fd, sc);
        s.clientAddr = remote;
        s.funcParse = defaultParse;
        s.pendingAccept = defaultAccept != null;
        sessions.set(fd, s);
        sc.register(selector, SelectionKey.OP_READ, fd);
        // handler accept berisi logika game (mengirim salam), jadi dijalankan
        // di logic thread, bukan di sini
        enqueue(s);
    }

    private void readSession(Session s) throws IOException {
        readBuf.clear();
        int n = s.channel.read(readBuf);
        if (n < 0) {
            s.eof = true;
            enqueue(s);
            return;
        }
        if (n == 0) {
            return;
        }
        readBuf.flip();
        synchronized (s) {
            s.appendRead(readBuf, n);
        }
        enqueue(s);
    }

    private void writeSession(Session s, SelectionKey key) throws IOException {
        // flushWrite hanya menyentuh outbox (thread-safe) dan state milik IO
        // thread, jadi tidak perlu mengunci session di sini
        if (s.flushWrite()) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }

    // ------------------------------------------------------------------
    // Logic thread: penanganan satu session
    // ------------------------------------------------------------------

    /** Jalankan handler accept/parse untuk session yang baru diambil dari antrean. */
    public void handle(Session s) {
        if (s == null || session(s.fd) == null) {
            return;
        }
        synchronized (s) {
            if (s.pendingAccept) {
                s.pendingAccept = false;
                if (defaultAccept != null) {
                    defaultAccept.parse(s.fd);
                }
            }
            if (s.funcParse == null) {
                return;
            }
            while (true) {
                int before = s.roff();
                int r = s.funcParse.parse(s.fd);
                if (session(s.fd) == null) {
                    return; // parser menutup session
                }
                if (s.eof) {
                    s.funcParse.parse(s.fd); // beri kesempatan menangani eof
                    if (session(s.fd) != null) {
                        sessionEof(s.fd);
                    }
                    return;
                }
                if (r != 0 || s.roff() == before || s.rfifoRest() <= 0) {
                    break;
                }
            }
            s.rfifoFlush();
        }
    }

    // ------------------------------------------------------------------
    // Throttle / lockout
    // ------------------------------------------------------------------

    private static int ipKey(InetSocketAddress addr) {
        byte[] a = addr.getAddress().getAddress();
        if (a.length != 4) {
            return 0;
        }
        return (a[0] & 0xFF) | ((a[1] & 0xFF) << 8) | ((a[2] & 0xFF) << 16) | ((a[3] & 0xFF) << 24);
    }

    public synchronized void addThrottle(int sAddr) {
        throttles.merge(sAddr, 1, Integer::sum);
    }

    public synchronized int checkThrottle(int sAddr) {
        return throttles.getOrDefault(sAddr, 0);
    }

    public synchronized int removeThrottle(int none, int noneToo) {
        throttles.clear();
        return 0;
    }

    public synchronized int addIpLockout(int sAddr) {
        ipLockout.put(sAddr, true);
        ServerLog.logAdd("LockOut", "IP locked out for wrong password %s%n", Session.ipToString(sAddr));
        return 0;
    }

    public synchronized void clearIpLockout(int sAddr) {
        ipLockout.remove(sAddr);
    }
}
