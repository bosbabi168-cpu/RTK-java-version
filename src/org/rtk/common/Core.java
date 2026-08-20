package org.rtk.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Port of common/core.c — loop utama, satu instance per server.
 *
 * Versi C menjalankan timer, send/recv, dan parse paket berurutan dalam satu
 * loop (tiga pthread-nya langsung di-join, jadi efektif sekuensial). Port ini
 * memisahkan IO ke thread sendiri di {@link NetServer}; kelas ini adalah
 * "logic thread": menunggu session dari ArrayBlockingQueue lalu memproses
 * paketnya, sambil menjalankan timer yang jatuh tempo.
 *
 * Satu server = satu logic thread. Urutan paket per koneksi terjaga dan
 * state permainan (termasuk engine skrip LuaJ yang tidak thread-safe) tidak
 * pernah disentuh dua thread sekaligus.
 */
public final class Core {

    private static final Logger log = LogManager.getLogger(Core.class);

    public interface TermFunc {
        void terminate();
    }

    private final NetServer net;
    private final TimerSystem timers;
    private TermFunc termFunc;
    private volatile boolean run = true;

    public Core(NetServer net, TimerSystem timers) {
        this.net = net;
        this.timers = timers;
    }

    /** set_termfunc() */
    public void setTermFunc(TermFunc f) {
        termFunc = f;
    }

    public void stop() {
        run = false;
        net.stopIo();
    }

    /** Jalankan IO thread lalu masuk ke loop logika (blocking). */
    public void mainLoop() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (termFunc != null) {
                termFunc.terminate();
            }
        }, "rtk-shutdown-" + net.name()));

        net.startIo();

        while (run) {
            try {
                timers.doTimer();
                // tidur hanya sampai timer berikutnya jatuh tempo
                Session s = net.pollInbound(timers.timeUntilNext(50));
                if (s != null) {
                    net.handle(s);
                }
            } catch (RuntimeException e) {
                log.error("[{}] logic loop error", net.name(), e);
            }
        }
    }
}
