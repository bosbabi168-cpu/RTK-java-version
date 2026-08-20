#!/usr/bin/env bash
# ============================================================
# run.sh - menjalankan server RTK dari jar hasil build NetBeans.
#
# Alur yang dipakai: compile di lokal (NetBeans), lalu salin folder
# dist/ ke server dan jalankan skrip ini. Tidak perlu JDK lengkap /
# compile di server — cukup JRE/JDK 25.
#
#   ./run.sh login        # java -jar <jar> login &
#   ./run.sh char
#   ./run.sh map
#   ./run.sh all          # login -> char -> map (berurutan, ada jeda)
#   ./run.sh scripttest   # foreground, untuk uji regresi
#   ./run.sh status
#   ./run.sh stop         # hentikan semua yang dijalankan skrip ini
#
# Opsi JVM: JAVA_OPTS="-Xmx512m" ./run.sh login
# Semua server butuh Main-Class = org.rtk.RtkLauncher di manifest jar.
# ============================================================
set -uo pipefail
cd "$(dirname "$0")"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
PIDDIR=logs

# Jar dari NetBeans (utama) atau dari build.sh (alternatif); pilih yang terbaru.
JAR=""
for candidate in dist/RTK-java-version.jar dist/RTK-java.jar; do
    if [ -f "$candidate" ] && { [ -z "$JAR" ] || [ "$candidate" -nt "$JAR" ]; }; then
        JAR="$candidate"
    fi
done

need_jar() {
    if [ -z "$JAR" ]; then
        echo "ERROR: jar tidak ditemukan di dist/."
        echo "Build dulu di NetBeans (Clean and Build), lalu salin folder dist/ ke sini."
        exit 1
    fi
}

start_one() {
    local name="$1"
    local pidfile="$PIDDIR/$name.pid"

    if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
        echo "$name sudah jalan (PID $(cat "$pidfile"))"
        return 0
    fi

    mkdir -p "$PIDDIR"
    # inilah perintah intinya: java -jar <jar> <server> &
    nohup $JAVA ${JAVA_OPTS:-} -jar "$JAR" "$name" >> "$PIDDIR/$name.console.log" 2>&1 &
    echo $! > "$pidfile"
    echo "$name dijalankan (PID $!) -> $PIDDIR/$name.console.log"
}

stop_one() {
    local name="$1"
    local pidfile="$PIDDIR/$name.pid"

    if [ ! -f "$pidfile" ]; then
        echo "$name tidak berjalan"
        return 0
    fi
    local pid
    pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then
        kill "$pid"
        echo "$name dihentikan (PID $pid)"
    else
        echo "$name tidak berjalan (PID basi $pid)"
    fi
    rm -f "$pidfile"
}

status_one() {
    local pidfile="$PIDDIR/$1.pid"
    if [ -f "$pidfile" ] && kill -0 "$(cat "$pidfile")" 2>/dev/null; then
        echo "  $1: JALAN (PID $(cat "$pidfile"))"
    else
        echo "  $1: mati"
    fi
}

case "${1:-}" in
    login|char|map)
        need_jar
        start_one "$1"
        ;;
    all)
        need_jar
        start_one login
        sleep 3
        start_one char
        sleep 3
        start_one map
        ;;
    scripttest)
        need_jar
        shift
        exec $JAVA ${JAVA_OPTS:-} -jar "$JAR" scripttest "$@"
        ;;
    stop)
        for s in map char login; do stop_one "$s"; done
        ;;
    status)
        echo "Jar: ${JAR:-<tidak ada>}"
        for s in login char map; do status_one "$s"; done
        ;;
    *)
        echo "Usage: $0 {login|char|map|all|scripttest|status|stop}"
        exit 1
        ;;
esac
