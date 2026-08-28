# RTK Java Version

*Read this in other languages: [Bahasa Indonesia](README.md)*

Java SE port of **RTK-Server** — the RetroTK/NexusTK MMO server originally
written in C (login server, char server, map server) + MySQL + Lua content
scripts. The Lua content is **not converted** — all 907 original scripts
run as-is through LuaJ.

Rewritten on 28 August 2026 after a full audit. The complete history of
the old version lives in `../_backup_docs_2026-08-28/` and in git history.

## Project direction

Since 26 August 2026 this project **no longer pursues byte-per-byte
compatibility** with the RetroTK client. What applies now:

- **Our own protocol (RTK2)** — bidirectional, designed from the real
  needs of the scripts: 46 inbound opcodes, 57 outbound events. Spec:
  [`docs/PROTOKOL-RTK2.md`](docs/PROTOKOL-RTK2.md).
- **Our own client (libGDX)** — developed in the separate repo
  `../RTK-client`, not committed until every asset is replaced with our
  own artwork.
- **Game logic is the most valuable asset** and stays faithful to C:
  907 Lua scripts, 9,850 maps, 4,476 portals, 716 mob types, 2,545 items.
- The old RetroTK protocol still exists side by side (`ProtocolRouter`)
  but at low priority.

Logic and protocol are separated by the `ClientView` (outbound events) and
`ClientCommands` (inbound actions) interfaces — swapping protocols means
writing a new adapter, not touching the logic.

## What already works

- **Login server**: full port — login, character creation, password
  change, meta files, maintenance mode, banned IPs, brute-force lockout.
- **Char server**: login & map handshakes, authentication, character
  load/save (11 tables), mail, cross-server message boards.
- **Map server**: world entry, movement + portals, NPC dialogs & shops
  (coroutines), combat & mob AI, floor items, inventory & equipment,
  use/eat/throw items, player-to-player exchange, groups, ignore list,
  player settings, mounts, spell durations/aether, experience & kill
  counts, bank + clan bank, parcels/mail/gifts, message boards, maps
  mutable at runtime.
- **Scripting**: 906/906 scripts load with 0 errors; binding gaps **0**
  (the only remainder is `testPacket`, deliberately not ported).
- **Testing**: 9 offline regression gates (903 `cliftest` assertions,
  234 `dbtest`) + the real-client gate `livetest` (182 checks; **194** on a
  two-map-server setup, `./tools/uji-dua-server.sh`).
  36 of 46 RTK2 opcodes have now actually been sent by a real client.
- **Indonesian translation**: COMPLETE — 0 of 9,812 dialogue points are
  still English; `livetest` asserts that the dialogue reaching the player
  is Indonesian.

For the remaining work, see the **ROADMAP in [CLAUDE.md](CLAUDE.md)**
(written in Indonesian).

## Prerequisites

- **JDK 25** (project language level = 25); a JRE/JDK 25 is enough on the
  deploy machine.
- **NetBeans** (J2SE "Java with Ant" project) — optional; `build.sh`
  builds with plain `javac`. **No Maven/Gradle** — the 7 external jars
  live in `extLib/` and are committed.
- **MySQL** with the `RTK` database (login/char servers exit without a DB,
  matching C behavior; the map server and most tests still run).

The project is **self-contained** — all game data is inside the repo:
`maps/` (3,544 `.map`), `luascript/` (907 `.lua`), `database/` (schema +
54-table dump). The original C source (`../RTK-Server`) is reference only;
it is never read at runtime.

## Quick start

```bash
# 1. build: NetBeans "Clean and Build", or
./build.sh                        # -> dist/RTK-java.jar

# 2. regression gates — all must be green
./run.sh scripttest maptest       # (run one at a time)
./run.sh chartest
./run.sh worldtest
./run.sh cliftest
./run.sh dbtest                   # needs MySQL
./run.sh luaaudit
./run.sh wiresync                 # needs ../RTK-client (skips itself if absent)

# 3. run all three servers
./run.sh all                      # login (2000) -> char (2005/2006) -> map (2001)
./run.sh status
./run.sh stop

# 4. real-client gate (from the client repo)
(cd ../RTK-client && ./run.sh livetest 127.0.0.1 2001 Adrielle)
```

## Architecture

Three servers in one jar (`Main-Class: org.rtk.RtkLauncher`, selected by
the first argument). Each server gets its own `NetServer` instance
(selector, session table, handler), so all three can live in one JVM.

Threading per server — IO separated from logic:

```
[IO thread]  selector: accept / read / write
     │  append to the session read buffer
     ▼
ArrayBlockingQueue<Session>          (dedup: 1 entry per session)
     │
[Logic thread]  timers → parse packets → build replies
     │  wfifoSet() → outbox (ConcurrentLinkedQueue)
     ▼
[IO thread]  flush outbox to the socket
```

Game logic deliberately stays **single-threaded per server**: packet order
per connection must be preserved, and LuaJ plus the per-player coroutines
are not thread-safe.

The full C → Java map is in [CLAUDE.md](CLAUDE.md#peta-arsitektur-c--java--ringkas).

## Configuration

Priority order (weak → strong):

1. `resources/rtk-server.properties` — technical defaults (crypt keys,
   ports, lockout, HikariCP pool, buffers, data paths). Loaded from the
   classpath; every missing key has a fallback.
2. `conf/*.conf` — original C format, overrides matching values
   (`login_port`, `char_port`, `map_port`, `map_path`, `lua_path`, SQL
   credentials).
3. CLI arguments (`--conf`, `--inter`, `--lang`, paths for test modes).

> `crypt.enckey` and `crypt.handshake_key` are part of the RetroTK client
> protocol — do not change them unilaterally.

## Database

`database/2020-09-02-21-55-01_RTK.sql.bak` = full 54-table dump
(9,850 `Maps` rows, 4,476 `Warps`); `database/scripts/` + `migrate.sh` =
21 sequential migrations if you want to walk the schema history.

> **Ubuntu/Pop!_OS trap:** MySQL `root` uses `auth_socket`
> (`ERROR 1698`, not `1045`) — use `sudo mysql`. Create the `rtk` user
> already referenced by `conf/char.conf`:

```bash
sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
  GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; FLUSH PRIVILEGES;"
mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
```

The dump starts with `DROP DATABASE IF EXISTS RTK` — check the existing
database before re-importing. Proven to import from 5.7 format into
MySQL 8.0.

Item/mob/NPC name translations are done in the `*Description` columns
(`database/terjemahan/`) — **not** in the Lua scripts, because names in
scripts are identifiers. The dialogue text itself is **fully translated**
(0 of 9,812 points left); the tooling and catalogue live in
`tools/terjemahan/`, the rules in
[`luascript/GLOSARIUM.md`](luascript/GLOSARIUM.md).

## Testing — nine gates

| Gate | Tests | Notes |
|---|---|---|
| `scripttest` | 906 Lua scripts + dialog coroutines + world calendar | |
| `maptest` | 3,544 map files | |
| `chartest` | character serialization | |
| `worldtest` | map world + player placement | |
| `cliftest` | packets, RTK2 protocol, every subsystem | 903 assertions |
| `dbtest` | database layer against live MySQL | 234 assertions; needs MySQL |
| `luaaudit` | static checker for 907 scripts + binding gaps | `-Drtk.audit.penuh=true` for the full list |
| `wiresync` | `Wire.java` identical to the client repo copy | skips itself if the client repo is absent |
| `elixirtest` | **a full Elixir match** on the real server boot | 34 checks; other map servers must be stopped |
| `livetest` | a **real RTK2 client** enters the world + 182 checks | run from `../RTK-client` |
| `tools/uji-dua-server.sh` | player transfer between map servers (R3/C3) | 194 checks; sets up and restores its own fixture |

The first nine gates are offline — they test the code against itself and
cannot see "something that did not happen". That is why every new
subsystem must get checks in both `cliftest` **and** `livetest`, and then
the code must be deliberately broken once to prove the gate can turn red.

## Scripting engine (LuaJ)

`ScriptEngine` = port of `sl.c`: loads `Developers/sys.lua` → all of
`Accepted/` + `Developers/`. The `typel` object model is preserved
(`__index`: Java getter → prototype → data table). NPC dialogs are
coroutines yielded/resumed by player answers. The `Player` prototype is
extended by `Accepted/player.lua` on top of the Java primitives.

Content changes relative to upstream are recorded in
[`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md); translation style &
terminology in [`luascript/GLOSARIUM.md`](luascript/GLOSARIUM.md).

## Status & roadmap

**Status 29 August 2026:** 9/9 offline gates green, `livetest` 182 checks
green (194 on two map servers), RTK2 protocol symmetric in both directions,
binding gaps 0, `map.log` 0 ERROR/WARN. New players can now **sign
themselves up** through the client (account login, character creation,
character picking) — see K3-lanjutan. Since 29 Aug 2026 the **periodic
event engine runs**: `map_cronjob()` (a 1-second timer driving
`cronJobSec`…`cronJobDay`) and the world registry
`GameRegistry<serverid>` had never been ported, so no event, boss spawn,
map lighting, or item spawner had ever run. Since 29 Aug 2026 **a full
Elixir match is proven to run** (`./run.sh elixirtest`) — and running it
uncovered three silent script-engine defects: a fractional `os.time()`,
`hasItem` returning a count instead of `true`/shortfall (used 419× with
`== true`, so **every item requirement in every quest failed**), and
character appearance attributes that scripts could never read. ⚠️ Sweep `logs/common.log` too: two
real bugs hid there rather than in `map.log` (Peringatan #123, #125).

The roadmap toward "a server that runs normally and smoothly with no
bugs" — including the table of player actions that still lack an RTK2
inbound path — is in
**[CLAUDE.md](CLAUDE.md#roadmap--menuju-server-yang-dipakai-normal--lancar-tanpa-bug)**.
Traps & lessons #1–#137 are in
**[docs/PERINGATAN.md](docs/PERINGATAN.md)** (Indonesian).

## Folder structure

```
RTK-java-version/
├── build.sh  run.sh             # javac build / server start-stop
├── build.xml  manifest.mf  nbproject/   # NetBeans J2SE (Ant) — do not hand-edit
├── CLAUDE.md                    # development guide + roadmap
├── docs/                        # PROTOKOL-RTK2.md, PERINGATAN.md
├── src/org/rtk/
│   ├── RtkLauncher.java         # login/char/map dispatcher + test modes
│   ├── common/  common/mmo/     # socket, crypt, timer, SQL, CharStatus+codec
│   ├── login/  charserver/      # login server, char server
│   └── map/                     # MapServer, User, Clif (RetroTK), Combat, Mob,
│       ├── proto/               #   RTK2: Wire, Inbound (+ WireSyncTest)
│       ├── data/                #   MapData, MapRegistry, ItemDb, SpellDb, ...
│       └── script/              #   ScriptEngine (LuaJ), Bindings, LuaAudit
├── resources/                   # log4j2.xml, rtk-server.properties
├── extLib/                      # 7 external jars (no Maven)
├── conf/                        # original C-format configuration
├── maps/  luascript/  database/  meta/  db/   # game data (self-contained)
└── logs/  build/  dist/         # runtime & build output (.gitignore)
```
