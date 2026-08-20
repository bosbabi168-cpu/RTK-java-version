#!/usr/bin/env bash
# ============================================================
# build.sh - build RTK-java TANPA Ant (javac + jar murni).
#
# Untuk mesin build/server (CentOS/RHEL/Linux mana pun) yang
# hanya punya JDK. Hasilnya identik dengan `ant jar`:
#   dist/RTK-java.jar  (berisi kelas + log4j2.xml + rtk-server.properties)
#
# Pakai:   ./build.sh
# Override JDK bila perlu: JAVA_HOME=/usr/lib/jvm/java-11 ./build.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ -n "${JAVA_HOME:-}" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC=javac
    JAR=jar
fi
RELEASE="${RELEASE:-25}"

command -v "$JAVAC" >/dev/null 2>&1 || { echo "ERROR: javac tidak ditemukan. Install JDK 25 (CentOS: lihat README bagian Deploy)"; exit 1; }

CP=$(echo extLib/*.jar | tr ' ' ':')

rm -rf build dist
mkdir -p build/classes dist logs

echo "[1/3] Compile (release $RELEASE)..."
find src -name "*.java" > build/sources.txt
"$JAVAC" --release "$RELEASE" -encoding UTF-8 -cp "$CP" -d build/classes @build/sources.txt

echo "[2/3] Copy resources (log4j2.xml, rtk-server.properties)..."
cp resources/log4j2.xml resources/rtk-server.properties build/classes/

echo "[3/3] Package dist/RTK-java.jar..."
# Class-Path relatif memungkinkan `java -jar dist/RTK-java.jar` dari root project
MANIFEST_CP=$(for f in extLib/*.jar; do printf "../extLib/%s " "$(basename "$f")"; done)
cat > build/MANIFEST.MF <<EOF
Main-Class: org.rtk.RtkLauncher
Class-Path: $MANIFEST_CP
EOF
"$JAR" cfm dist/RTK-java.jar build/MANIFEST.MF -C build/classes .

echo
echo "Build OK: dist/RTK-java.jar"
echo "Jalankan dengan: ./run.sh {login|char|map|all|scripttest|maptest|chartest|worldtest|cliftest}"
