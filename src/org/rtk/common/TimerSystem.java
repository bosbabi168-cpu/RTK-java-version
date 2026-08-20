package org.rtk.common;

import java.util.PriorityQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Port of common/timer.c (timer_insert / timer_do) — satu instance per server.
 *
 * A timer fires once after {@code startMs} and then repeats every
 * {@code intervalMs}. Passing an interval of 0 makes it one-shot.
 * A callback returning a negative value cancels its own rescheduling.
 *
 * Antrean di dalamnya TIDAK thread-safe secara sengaja: setiap instance hanya
 * disentuh oleh logic thread milik servernya sendiri.
 */
public final class TimerSystem {

    private static final Logger log = LogManager.getLogger(TimerSystem.class);

    public interface Callback {
        int call(int arg1, int arg2);
    }

    private static final class Entry implements Comparable<Entry> {
        long fireAt;
        final long interval;
        final Callback cb;
        final int arg1;
        final int arg2;

        Entry(long fireAt, long interval, Callback cb, int arg1, int arg2) {
            this.fireAt = fireAt;
            this.interval = interval;
            this.cb = cb;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        @Override
        public int compareTo(Entry o) {
            return Long.compare(fireAt, o.fireAt);
        }
    }

    private final PriorityQueue<Entry> queue = new PriorityQueue<>();
    private static final long startTime = System.currentTimeMillis();

    /** gettick(): milliseconds since server start. */
    public static long gettick() {
        return System.currentTimeMillis() - startTime;
    }

    /** timer_insert(start, interval, func, arg1, arg2) */
    public void insert(long startMs, long intervalMs, Callback cb, int arg1, int arg2) {
        queue.add(new Entry(gettick() + startMs, intervalMs, cb, arg1, arg2));
    }

    /** timer_do(tick): run everything that is due. */
    public void doTimer() {
        long now = gettick();
        while (!queue.isEmpty() && queue.peek().fireAt <= now) {
            Entry e = queue.poll();
            int r;
            try {
                r = e.cb.call(e.arg1, e.arg2);
            } catch (RuntimeException ex) {
                log.error("Timer callback error", ex);
                r = -1;
            }
            if (e.interval > 0 && r >= 0) {
                e.fireAt = now + e.interval;
                queue.add(e);
            }
        }
    }

    /** Berapa lama boleh menunggu paket sebelum timer berikutnya jatuh tempo. */
    public long timeUntilNext(long maxWaitMs) {
        if (queue.isEmpty()) {
            return maxWaitMs;
        }
        long delta = queue.peek().fireAt - gettick();
        if (delta < 0) {
            return 0;
        }
        return Math.min(delta, maxWaitMs);
    }

    /** timer_clear() */
    public void clear() {
        queue.clear();
    }
}
