#!/usr/bin/env bash
# ============================================================
# uji-dua-server.sh — menjalankan gerbang livetest untuk R3/C3
# (perpindahan pemain antar MAP SERVER).
#
# Kenapa ada skripnya sendiri: perpindahan antar server tidak bisa
# dibuktikan oleh satu server. Ia butuh DUA map server hidup DAN satu
# peta yang benar-benar dimiliki server kedua — dan pembagian peta itu
# ada di kolom `Maps.MapServer` di database, bukan di berkas conf.
#
# ⚠️ Peta yang dipinjam adalah 330 (Buya) karena peta 362 (Yunsil
# Tavern) punya portal ke sana DAN portal baliknya, jadi ujinya bisa
# pulang-pergi lalu memulihkan keadaan pemain. Buya adalah kota utama:
# skrip ini MEMULANGKANNYA ke server 0 saat selesai, termasuk bila
# ujinya gagal atau ditekan Ctrl-C. Jangan biarkan pinjaman itu
# menetap — di setup satu server, peta milik server lain tidak dimuat
# sama sekali.
#
#   ./tools/uji-dua-server.sh
# ============================================================
set -uo pipefail
cd "$(dirname "$0")/.."

PETA_PINJAM=330
KLIEN=../RTK-client
SQL="mysql -h 127.0.0.1 -u rtk -p50LM8U8Poq5uX2AZJVKs RTK -N"

pulihkan() {
    echo "[uji2] memulangkan peta $PETA_PINJAM ke map server 0"
    $SQL -e "UPDATE \`Maps\` SET \`MapServer\` = 0 WHERE \`MapId\` = $PETA_PINJAM;" 2>/dev/null
    echo "[uji2] membuang tabel milik server 1"
    $SQL -e "DROP TABLE IF EXISTS \`NPCs1\`, \`NPCEquipment1\`, \`GameRegistry1\`;" 2>/dev/null
    ./run.sh stop >/dev/null 2>&1
    pkill -f "map2.conf" 2>/dev/null
}
trap pulihkan EXIT INT TERM

echo "[uji2] meminjamkan peta $PETA_PINJAM ke map server 1"
$SQL -e "UPDATE \`Maps\` SET \`MapServer\` = 1 WHERE \`MapId\` = $PETA_PINJAM;" 2>/dev/null

# ⚠️ Tiap map server membaca tabelnya SENDIRI: NPCs<id>, NPCEquipment<id>,
# GameRegistry<id>. Dump ini hanya punya milik server 0, jadi server kedua
# dulu berjalan tanpa satu pun NPC — termasuk tanpa NPC F1, sehingga `core`
# di skrip nil dan setiap petak acara melempar "attempt to index ?". Itu
# kegagalan FIXTURE yang menyamar sebagai cacat kode; dua `script error`
# arena/elixir di gerbang ini berasal dari sana.
echo "[uji2] menyiapkan tabel milik server 1 (NPC F1 + registry)"
$SQL -e "CREATE TABLE IF NOT EXISTS \`NPCs1\` LIKE \`NPCs0\`;
         CREATE TABLE IF NOT EXISTS \`NPCEquipment1\` LIKE \`NPCEquipment0\`;
         CREATE TABLE IF NOT EXISTS \`GameRegistry1\` LIKE \`GameRegistry0\`;
         DELETE FROM \`NPCs1\`;
         INSERT INTO \`NPCs1\` SELECT * FROM \`NPCs0\` WHERE \`NpcIsF1Npc\` = 1;" 2>/dev/null

./run.sh stop >/dev/null 2>&1
pkill -f "map2.conf" 2>/dev/null
sleep 2

echo "[uji2] menjalankan login + char + map (server 0, port 2001)"
nohup ./run.sh all > logs/uji2-server0.log 2>&1 &
sleep 25

echo "[uji2] menjalankan map server kedua (server 1, port 2002)"
nohup java -jar dist/RTK-java.jar map --conf conf/map2.conf > logs/uji2-server1.log 2>&1 &
sleep 25

echo "[uji2] livetest"
(cd "$KLIEN" && ./run.sh livetest 127.0.0.1 2001 Adrielle)
HASIL=$?
echo "[uji2] livetest selesai dengan kode $HASIL"
exit $HASIL
