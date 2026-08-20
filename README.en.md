# RTK Java Version

*[Bahasa Indonesia](README.md) · English*

A Java SE port of the **RetroTK** MMO server (NexusTK-style), originally
written in C — upstream source:
[unkmc/RTK-Server](https://github.com/unkmc/RTK-Server).

> A note about the original project: although it is often described as a
> "Lua server", the server core is actually written in **C** (login, char
> and map servers). Lua (907 files) is only the *game content scripting*
> language — quests, spells, NPCs, mobs — executed by the map server
> through the `sl.c` bindings. This project ports that C core to Java and
> runs the Lua content **unmodified** via LuaJ.

## Requirements

- **JDK 25** (the project's language level). That is for the development
  machine; a server only needs a **JRE/JDK 25**, since nothing is built
  there.
- **NetBeans** as IDE and build tool (it ships its own Ant). Optional:
  `build.sh` for a quick `javac`-only build.
- **MySQL** with the `RTK` database (required by the login and char
  servers; the map server can run without it, with reduced functionality).

This project is **self-contained** — all game data lives in the repository,
so nothing extra needs downloading after cloning:

| Folder | Contents |
|---|---|
| `maps/` | 3,544 `.map` map files (~38 MB) |
| `luascript/` | 907 `.lua` scripts (~6.8 MB) |
| `database/` | MySQL schema + dump (~13 MB) |

The map and script folders can be relocated via `map_path` and `lua_path`
in `conf/map.conf`.

## Quick start

On a development machine:

```
# 1. build in NetBeans: Run > Clean and Build Project
#    (or without NetBeans: ./build.sh)

# 2. regression checks — all must pass
./run.sh scripttest
./run.sh maptest
./run.sh chartest
./run.sh worldtest
./run.sh cliftest

# 3. run the three servers
./run.sh all          # login -> char -> map
./run.sh status
./run.sh stop
```

## Architecture

As in the C original, the server is three processes talking over TCP:

```
game client ──► LoginServer (port 2000)
                    │  0x1000..0x1004 / 0x2001..0x2004
                    ▼
                CharServer  (port 2005)  ──► MySQL (RTK database)
                    │  0x3000..0x3007 / 0x3800..0x3804
                    ▼
                MapServer   (port 2001)  ──► game client (after redirect)
```

Those ports are the effective values from `conf/` (`inter.conf` →
`login_port: 2000`, `char_port: 2005`; `map.conf` → `map_port: 2001`),
which override the defaults in `rtk-server.properties`. Only the login and
map ports need to be reachable publicly.

Since the networking layer became per-instance, the three **can technically
run inside a single JVM**; `run.sh` still starts them as separate processes
for restart and crash isolation. The only remaining global state is the
filename held by `ServerLog`.

## Threading model (networking layer)

The C version relies on global state: one session table, one selector, one
handler. This port deliberately does **not** mirror that — each server owns
its own [`NetServer`](src/org/rtk/common/NetServer.java) instance
(selector, session table and handlers), which is what allows them to
coexist in one JVM.

Within a single server, I/O is separated from logic by a queue:

```
   [I/O thread]  selector: accept / read / write
        │  appends to the session read buffer
        ▼
   ArrayBlockingQueue<Session>      (deduplicated: one entry per session)
        │
   [Logic thread]  timers → parse packets → build replies
        │  wfifoSet() → outbox (ConcurrentLinkedQueue<byte[]>)
        ▼
   [I/O thread]  writes the outbox to the socket
```

Concurrency rules that must hold:

- The **read buffer** (`rdata`) is touched by both threads, so it is always
  accessed under the `Session` monitor: the I/O thread locks while
  appending, the logic thread locks while processing a packet.
- The **write buffer** (`wdata`) belongs to the logic thread alone.
  `wfifoSet()` copies the finished packet into a thread-safe `outbox`, so
  the I/O thread never touches the staging area. This matters because
  parsers routinely write into *other* sessions (for example, login
  forwarding a packet to the char-server connection).
- The **session table** is an `AtomicReferenceArray` — written by the I/O
  thread on accept and by the logic thread on close.
- The queue is bounded (`FD_SETSIZE + 16`) yet can never fill, because a
  session may be queued only once; the I/O thread therefore never blocks.
- The logic thread waits only until the next timer is due, so there is no
  busy loop.

**Game logic intentionally stays single-threaded per server.** Not because
it could not be pooled, but for two binding reasons: packet order per
connection must be preserved (a client sends "move" then "attack"), and the
LuaJ script engine with its per-player coroutines is **not thread-safe**.
What was split is I/O from logic — the part that is genuinely safe to split.

## Opening the project in NetBeans

This is a standard **NetBeans J2SE (Java with Ant) project** — the metadata
in `nbproject/` is generated and managed by NetBeans itself. `build.xml` is
deliberately **left in its stock form** (no custom targets), so the IDE can
manage it freely without risk of being overwritten.

| File | In git? | Notes |
|---|---|---|
| `nbproject/project.xml` | yes | project type + source roots |
| `nbproject/project.properties` | yes | `extLib/` classpath, language level, main class |
| `nbproject/build-impl.xml` | yes | **generated by NetBeans — never hand-edit** |
| `nbproject/genfiles.properties` | yes | checksums that trigger regeneration |
| `nbproject/private/` | no | machine-local settings (JDK paths, etc.) |
| `build.xml` | yes | stock NetBeans form; do not add targets here |
| `manifest.mf` | yes | base manifest for the jar |

Use `File > Open Project…` and pick the **`RTK-java-version`** folder (the
one containing `build.xml` and `nbproject/`). Registered source roots are
`src` (Java code) plus `resources`, `extLib` and `conf` so those folders
show up in the project tree.

### Main class

A jar can declare only **one** `Main-Class`, but RTK has three servers.
Hence the dispatcher class
[`org.rtk.RtkLauncher`](src/org/rtk/RtkLauncher.java) — set that as the
main class:

> **Project Properties > Run > Main Class = `org.rtk.RtkLauncher`**

The server is selected by the first argument:

```
java -jar RTK-java-version.jar login       # login server
java -jar RTK-java-version.jar char        # char server
java -jar RTK-java-version.jar map         # map server
java -jar RTK-java-version.jar scripttest  # scripting regression
java -jar RTK-java-version.jar maptest     # map file regression
java -jar RTK-java-version.jar chartest    # character serialisation
java -jar RTK-java-version.jar worldtest   # world / player placement
java -jar RTK-java-version.jar cliftest    # client packets, movement, warps
```

Remaining arguments are passed straight through, so the original options
still work: `java -jar RTK-java-version.jar login --conf conf/login.conf`.
To run a single server from NetBeans' **Run** button, put `login` / `char`
/ `map` in the *Arguments* field.

## Building

**The workflow is: build locally, deploy manually.** The server needs no
JDK and no build step at all.

### 1. Build in NetBeans (primary path)

`Run > Clean and Build Project` produces:

```
dist/
├── RTK-java-version.jar     # RTK classes (+ copies of extLib jars as resources)
└── lib/                     # the dependencies actually used at runtime
    ├── HikariCP-5.1.0.jar
    ├── log4j-api-2.24.3.jar
    └── ... (5 more)
```

> **Important:** the main jar's manifest points at `lib/` through its
> `Class-Path`, so the **`dist/lib/` folder must be copied along** — the
> jar alone is not enough. (The dependency jars also end up bundled inside
> the main jar because `extLib` is registered as a source root, but nested
> jars cannot be loaded by the JVM; what actually gets used is `dist/lib/`.)

### 2. Copy to the server

What the server needs:

```
/opt/rtk-java/
├── dist/          # NetBeans build output (jar + lib/)
├── conf/          # configuration (login/char/inter/map/lang .conf)
├── maps/          # .map map files
├── luascript/     # Lua scripts
├── meta/          # meta files served to the client
├── logs/          # created automatically; output & PID files
└── run.sh
```

### 3. Run it with run.sh

[`run.sh`](run.sh) wraps `java -jar <jar> <server> &`:

```
./run.sh login        # start the login server in the background
./run.sh char
./run.sh map
./run.sh all          # all three in order, with delays
./run.sh status       # show what is running
./run.sh stop         # stop everything
./run.sh scripttest   # foreground regression run
```

Each server writes its PID to `logs/<server>.pid` and its console output to
`logs/<server>.console.log`. JVM options:
`JAVA_OPTS="-Xmx512m" ./run.sh login`.

### Alternative: build.sh (plain javac, no NetBeans)

[`build.sh`](build.sh) compiles with `javac --release 25` and packages
`dist/RTK-java.jar` (same main class, `org.rtk.RtkLauncher`). Useful for
quick checks on machines without NetBeans, such as CI. `run.sh` accepts
either jar name and picks the newer one.

```
./build.sh
./run.sh scripttest
```

- **Core logic is plain Java SE**, with external libraries in
  [`extLib/`](extLib/README.md) (no Maven): the MySQL JDBC driver,
  **HikariCP** (connection pooling), **Log4j2** (logging) and **LuaJ**
  (running the Lua scripts). MD5 (`java.security`), zlib/CRC32
  (`java.util.zip`) and networking (`java.nio`) use the standard Java SE
  APIs.
- Language level: **Java 25** (`javac.source`/`javac.target` in
  `nbproject/project.properties`, `--release 25` in `build.sh`). Modern
  language features are fair game — `RtkLauncher` uses a switch expression.
  **Consequence: the server requires a JRE/JDK 25 or newer.**

## Running

1. Prepare the `RTK` MySQL database using this project's `database/`
   folder: `database/scripts/` holds 21 migration scripts (52 tables) run
   in order by `database/migrate.sh`, and
   `database/2020-09-02-21-55-01_RTK.sql.bak` is a full dump containing the
   content data — among it **7,974 `Maps` rows** and **4,476 `Warps` rows**
   used by the map server.
2. Adjust `conf/char.conf` (SQL credentials), `conf/inter.conf`
   (inter-server id/password) and `conf/map.conf` (the map server's public
   IP). The configuration file format is **identical to the C version** —
   the files in `conf/` were copied straight from the original repository.
3. Start them in order with `./run.sh all` (login → char → map, with delays
   between), or one at a time: `./run.sh login`, wait for the
   "Connected to Login Server" log line, then `./run.sh char` and
   `./run.sh map`.

### Technical configuration (resources/rtk-server.properties)

Values that were hardcoded in the C version — the `ENCKEY` encryption key,
the `KruIn7inc` handshake key, the patch URL, default ports, the
brute-force lockout threshold, the inter-server reconnect interval,
HikariCP pool tuning, socket buffer sizes and the default script path — now
live in
[`resources/rtk-server.properties`](resources/rtk-server.properties). The
file is loaded from the **classpath** by `common/Props.java` (the
`resources` Ant target copies it into `build/classes` and the jar), falling
back to `resources/rtk-server.properties` in the working directory.

Configuration precedence:

1. **`rtk-server.properties`** — technical defaults applied at startup.
2. **`conf/*.conf`** (the original C format) — read afterwards and
   **overriding** any overlapping value (`login_port`, `char_port`,
   `map_port`, `map_path`, `lua_path`, …).

Game data locations follow the same pattern, weakest to strongest:

| Data | properties | conf/map.conf | CLI argument |
|---|---|---|---|
| Maps | `map.path=maps` | `map_path: maps` | `./run.sh maptest <path>` |
| Lua scripts | `lua.path=luascript` | `lua_path: luascript` | `./run.sh scripttest <path>` |

The defaults point at folders inside this project, so the servers run with
no extra configuration.

A missing file or key never stops the server — every lookup has a built-in
fallback identical to the original C behaviour.

> Warning: `crypt.enckey` and `crypt.handshake_key` are part of the RetroTK
> client protocol. Changing them makes the official client unable to
> connect — only change them if you are changing the client too.

Startup order, CLI arguments (`--conf`, `--inter`, `--lang`) and the wire
protocol all follow the C version, so existing RetroTK clients and tools
remain compatible. Diagnostic console output (formerly `printf` /
`System.out`) is now handled by Log4j2 — see below.

## Deploying on CentOS (systemd)

The principle: **no compilation on the server.** Build locally in NetBeans,
copy the result, run it.

1. Install a **JRE/JDK 25**. CentOS' stock repositories generally do not
   carry it yet, so take it from Adoptium/Temurin or Oracle:

   ```
   sudo dnf install -y java-25-openjdk        # if available in the repo
   # otherwise download Temurin 25 and set JAVA_HOME
   java -version                              # confirm 25+
   ```

2. Create the directory and user:

   ```
   sudo useradd -r -s /sbin/nologin rtk
   sudo mkdir -p /opt/rtk-java
   ```

3. From the local machine, copy the build output and its companions:

   ```
   rsync -av dist conf maps luascript meta run.sh rtk@server:/opt/rtk-java/
   sudo chown -R rtk:rtk /opt/rtk-java
   ```

   Make sure **`dist/lib/` comes along** — the jar manifest points at it.

4. Run. The simplest way is `run.sh`:

   ```
   sudo -u rtk /opt/rtk-java/run.sh all
   sudo -u rtk /opt/rtk-java/run.sh status
   ```

   Or as a systemd service, for example
   `/etc/systemd/system/rtk-login.service`:

   ```ini
   [Unit]
   Description=RTK Login Server
   After=network.target mysqld.service

   [Service]
   User=rtk
   WorkingDirectory=/opt/rtk-java
   ExecStart=/usr/bin/java -Xmx256m -jar dist/RTK-java-version.jar login
   Restart=on-failure
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

   Duplicate it for `rtk-char.service` (argument `char`,
   `After=… rtk-login.service`) and `rtk-map.service` (argument `map`,
   `After=… rtk-char.service`). Note that systemd runs processes in the
   foreground, so **omit** the `&` — let systemd manage them.

5. Enable the services and open the ports:

   ```
   sudo systemctl daemon-reload
   sudo systemctl enable --now rtk-login rtk-char rtk-map
   sudo firewall-cmd --permanent --add-port={2000,2001,2005}/tcp && sudo firewall-cmd --reload
   ```

   (Match these to the ports in `conf/`; only the login and map ports need
   to be publicly reachable, the char port is inter-server only.)

6. Monitor with `journalctl -u rtk-login -f`, or the per-component rolling
   files in `/opt/rtk-java/logs/`. When started through `run.sh`, console
   output goes to `logs/<server>.console.log`.

## Connection pooling (HikariCP)

`common/Sql.java` no longer holds a single long-lived JDBC connection the
way `db_mysql.c` did — each `connect()` builds a **HikariCP pool**
(`extLib/HikariCP-5.1.0.jar`), and every query borrows and returns a
connection through try-with-resources. Pool settings per server (in
`Sql.connect()`): maximum 10 connections, minimum 2 idle, 10-second
connection timeout, 10-minute idle timeout, 30-minute max lifetime, plus
prepared-statement caching. The pool name follows the database name
(`RTK-<db>`) so the three servers are easy to tell apart in the logs.

## Logging (Log4j2)

All diagnostic `System.out.println` / `printf` calls were replaced with
Log4j2 loggers (`org.apache.logging.log4j.Logger`, one instance per class).
The configuration lives in [`resources/log4j2.xml`](resources/log4j2.xml)
and is copied into `build/classes` and the jar by the build. Each component
writes to its own rolling file under `logs/` while still appearing in the
NetBeans console:

| Package | Log file | Contents |
|---|---|---|
| `org.rtk.login.*` | `logs/login.log` | Login server (clif, intif) |
| `org.rtk.charserver.*` | `logs/char.log` | Char server (logif, mapif, char_db) |
| `org.rtk.map.*` | `logs/map.log` | Map server (world, client packets, intif, scripting) |
| `org.rtk.common.*` and root (incl. HikariCP's own logging) | `logs/common.log` | Sockets, timers, config, SQL/HikariCP |

Files roll over **daily at midnight**, archived as `login-2026-08-20.log.gz`
and gzipped. Retention is handled by a `Delete` action with
`IfLastModified age="30d"` on each `<RollingFile>`, so archives **older
than 30 days are removed automatically** at every rollover — measured by
file modification time rather than a simple generation count, so it stays
correct even if the server was down for a while. Change the
`RETENTION_DAYS` property in `log4j2.xml` to adjust it. The default level
is `INFO`; change `level="…"` per `<Logger>` to see `DEBUG` output (for
example the unknown-packet hex dump in `LoginClif.clifDebug`).

> Note: the `ServerLog.addLog()` / `ServerLog.logAdd()` mechanism (a port of
> C's `add_log()` / `Log_Add()`, which writes to dynamically named files
> such as `logs/regreject.log`, `logs/validlogin.log`, `logs/BANNED.log`)
> **is kept as-is** — it is not `System.out.println` but a separate
> *game event* logging mechanism whose format is deliberately identical to
> the C version, not server diagnostics.

## Character persistence (mmo_charstatus)

A player's entire state travels between the char server and the map server
via packets 0x3003 (request) → 0x3803 (deliver), then 0x3004 (save) or
0x3007 (save + logout). The actual storage is still MySQL, handled by
[`CharPersistence`](src/org/rtk/charserver/CharPersistence.java), which
loads and saves 11 tables (`Character`, `Inventory`, `Equipment`, `Banks`,
`SpellBook`, `Aethers`, `Legends`, `Kills`, `Registry`, `RegistryString`,
`NPCRegistry`, `QuestRegistry`).

**The format deliberately differs from the C version.** In C, the struct's
contents are shipped as a raw memory dump. After compiling the C header to
be sure, the numbers are: **3,171,352 bytes per character**, of which
**82% is registry arrays that are almost always empty** — and that size
**differs between build targets** (3,171,352 on 64-bit versus 3,168,952 on
the original 32-bit build, because of `unsigned long`). So the C binary
layout is not a stable contract, while both ends of this packet are our own
Java code.

[`CharStatusCodec`](src/org/rtk/common/mmo/CharStatusCodec.java) uses a
compact versioned format: a `"RTKC"` magic plus a version number,
length-prefixed strings, collections that only write populated entries, and
a final zlib compression pass so the transport layer is unchanged.

| | C struct | this format |
|---|---|---|
| fresh character | 3,171,352 bytes | **27 bytes** |
| fully populated character | 3,171,352 bytes | **4,263 bytes** (743× smaller) |

The consequence: a C char server cannot be paired with a Java map server or
vice versa — a trade-off taken deliberately. If the layout changes, bump
`CharStatusCodec.VERSION` so old blobs are rejected with a clear message
instead of being silently misread.

## Map world & players

The map server loads all of its maps at startup: metadata from the `Maps`
table (filtered by `MapServer = ServerId`) combined with geometry from the
`.map` files. Because 7,648 `Maps` rows reference only 2,640 unique files,
geometry is cached per filename — identically shaped maps are not re-read
from disk, yet each still has its own contents (players, mobs).

**Spatial index.** Every map is divided into 8×8-tile blocks, and each
block holds the list of things inside it. "Who is near here?" therefore
only touches a handful of blocks instead of sweeping the whole map (Kugnae
alone is 48,400 tiles). Players and mobs live in separate indexes, matching
`block[]` and `block_mob[]` in the C version.

**The client's view area** is x±9, y±8 (18×16 tiles). When it runs past the
edge of the map the area is **shifted, not clipped**, so a player standing
at the border still sees as far as usual — behaviour copied exactly from
`map_foreachinarea()`.

**Players.** [`User`](src/org/rtk/map/User.java) ports `USER`
(`struct map_sessiondata`): it *is* a thing on the map (a `BlockList`),
holds [`CharStatus`](src/org/rtk/common/mmo/CharStatus.java) as its
persistent data, and keeps derived values (maxHp, might, …) in its own
fields. [`Pc`](src/org/rtk/map/Pc.java) provides `setPos`,
`spawn`/`despawn`, `warp` and `enterWorld`.

> `Pc.setPos()` deliberately does **not** touch the block index — exactly
> like C's `pc_setpos()`, which only changes coordinates. What makes a
> player visible is `Pc.spawn()` (in C: `clif_spawn` → `map_addblock`).

**Warp tiles.** The `Warps` table (4,476 rows) is loaded into a per-block
list on each map. When a player steps onto a warp tile, the destination
map's entry requirements (level, vita/mana, mark, path) are checked first;
if they fail, the player is pushed back two tiles with a rejection message
— that map's `MapRejectMsg` when set, otherwise the stock wording from the
C version. Warps into a map owned by another map server cannot be used yet
(see roadmap C3).

One addition that the C version lacks: if a player's stored position turns
out to be inside a wall (for instance because the map was edited after they
last logged out), `enterWorld` finds the nearest walkable tile instead of
leaving them stuck.

## Port status

| C component | Java files | Status |
|---|---|---|
| `common/crypt.c` | `common/Crypt.java` | ✅ complete (XOR crypt, key table, packet indexes; round-trip tested) |
| `common/md5calc.c` | `common/Md5.java` | ✅ complete (via MessageDigest) |
| `common/socket.c` + RFIFO/WFIFO macros | `common/NetServer.java`, `common/Session.java` | ✅ complete — **per-server instance**, java.nio selector on its own I/O thread, packets handed over through an `ArrayBlockingQueue`; throttle + IP lockout |
| `common/timer.c` | `common/TimerSystem.java` | ✅ complete — **per-server instance**, runs on the logic thread |
| `common/core.c` | `common/Core.java` | ✅ complete — **per-server instance**; the logic thread consuming the packet queue |
| `common/db_mysql.c` | `common/Sql.java` | ✅ via JDBC + PreparedStatement |
| config reader (`config_read`) | `common/Config.java` | ✅ complete |
| **login server** (`login.c`, `clif.c`, `intif.c`) | `login/LoginServer.java`, `LoginClif.java`, `LoginIntif.java` | ✅ **complete** — version check, login, character creation, password change, meta files (zlib+CRC32), maintenance mode, require_reg, banned IPs, brute-force lockout, redirect to the map server |
| **char server** (`char.c`, `logif.c`, `mapif.c`, `char_db.c`) | `charserver/CharServer.java`, `Logif.java`, `Mapif.java`, `CharDb.java`, `CharPersistence.java` | ✅ login & map handshakes, character authentication, creation, password change, map routing, **full character load/save (0x3003/0x3803/0x3004/0x3007)**. ⚠️ boards/mail missing; the database layer has not yet been exercised against a live MySQL |
| `common/mmo.h` (`struct mmo_charstatus`) | `common/mmo/CharStatus.java`, `CharStatusCodec.java`, + `Item`/`Legend`/`SkillInfo`/`BankItem`/`Point` | ✅ 67 `Character` columns + collections, with its own serialisation format (see above) |
| **map server** (`map.c`, `intif.c`) | `map/MapServer.java`, `map/MapIntif.java` | ✅ connects and authenticates to the char server, loads map geometry, accepts routed players, requests and receives character data |
| map world (`map.h` block_list/map_data, `map_read`) | `map/data/BlockList.java`, `MapData.java`, `MapRegistry.java` | ✅ geometry + metadata + 8×8 spatial index, x±9/y±8 view area |
| **gameplay** (`pc.c`, `mob.c`, `npc.c`, `clif.c`, ~22k lines) | `map/User.java`, `map/Pc.java`, `map/Clif.java` | ⚠️ **player placement and movement**: USER, `setPos`/`warp`/`enterWorld`, world-entry packets (0x05/0x15/0x04/0x20/0x1E/0x22), `parseWalk` with collision and broadcast, warp tiles + map entry requirements, 0x0A messages. Combat, mobs, NPCs/dialogs, and redrawing other players (`clif_sendmapdata` / `*look_sub`) are still missing |
| **scripting engine** (`sl.c`, ~11k lines) | `map/script/ScriptEngine.java`, `ScriptClass.java`, `ScriptInstance.java`, `Bindings.java`, `ScriptPlayer.java` | ✅ **core architecture working via LuaJ** — all 906 original Lua scripts load without error; typel object model, `root.method` dispatch, `_async` coroutines + blocking dialog primitives tested end-to-end through the original `Accepted/player.lua`. ⚠️ of the 254 methods scripts call: 110 come from `player.lua` itself, **12 have real Java bindings**, 15 are stubs, and 144 have no implementation yet |
| save server (`saveif.c` — already disabled in C) | — | ❌ not ported (its connection timer is commented out in C) |

## Design notes

- **Endianness** — the C host is little-endian: `RFIFOW/WFIFOW` are
  little-endian accesses while `SWAP16/SWAP32` are big-endian. Both are
  explicit in Java: `rfifoW/wfifoW` (LE, inter-server protocol) and
  `rfifoWBE/wfifoWBE` (BE, client protocol).
- **fd as session identity** — the integer session index is preserved
  because it travels inside the inter-server protocol (login forwards its
  client's fd to the char server and gets it back).
- **SQL injection** — the C version interpolated strings straight into
  queries; this port uses `PreparedStatement` with the same behaviour.
- **Threading** — the C version's global networking state was replaced with
  per-server instances, and I/O was separated from logic by a queue. Game
  logic deliberately stays single-threaded per server; see
  [Threading model](#threading-model-networking-layer).
- A small C bug **deliberately preserved** for compatibility: inter-server
  authentication only rejects when *both* the id and the password are wrong
  (`strcmp(a) && strcmp(b)`), noted in a code comment.
- A C bug that **was fixed** (also noted in comments): `start_money` /
  `start_point` parsing in `char.c` used `strcmpi(...) == 1`, so it never
  took effect; it works normally in this port.

## Scripting engine (LuaJ) — running the original Lua scripts

The `org.rtk.map.script` package ports the `sl.c` architecture on top of
**LuaJ** (`extLib/luaj-jse-3.0.1.jar`, a pure-Java Lua interpreter), so the
game content (900+ files in `luascript/`) runs **unmodified**:

- **Loading** mirrors `sl_init` / `sl_reload`: `Developers/sys.lua` first,
  then every `.lua` under `Accepted/` and `Developers/` (recursively,
  skipping `sys.lua`). Current status: **906/906 files loaded, 0 errors**.
- **The `typel` object model**: players, NPCs, mobs and registries are
  userdata sharing one metatable whose `__index` resolves Java getter →
  prototype → instance data table, exactly the order of `typel_mtindex` in
  C. The `Player` prototype is exposed as a global so the original
  `Accepted/player.lua` can add high-level methods (`menuString`,
  `dialogSeq`, banking, …) from the Lua side — the same layering as the C
  server.
- **Blocking dialogs** use LuaJ coroutines: `_async()` creates a
  per-player coroutine (`sd->coref`), the `menu` / `dialog` / `input` /
  `menuSeq` / `inputSeq` primitives yield, and the engine resumes with the
  client's answer (a numeric index for menus; `"next"` / `"previous"` /
  `"quit"` for dialogs).
- **Event dispatch**: `doScript("blood", "click", playerRef)` is the
  equivalent of `sl_doscript_blargs`, guarded by an error handler.
- Unimplemented bindings are registered as **warn-once stubs** — scripts
  still load and every missing binding shows up in the log rather than
  crashing the loader.

The script path is set by `lua_path` in `conf/map.conf` (default
`luascript`, a folder inside this project); `lua_enable: 0` turns it off.

## Testing

There is no unit-test framework on purpose (to stay pure Java SE). Instead
there are five regression gates, all of which must stay green:

| Command | What it covers |
|---|---|
| `./run.sh scripttest` | 906 Lua scripts load + NPC dialog coroutines |
| `./run.sh maptest` | 3,544 map files parse correctly |
| `./run.sh chartest` | character serialisation (29 assertions) |
| `./run.sh worldtest` | map world + player placement (53 assertions) |
| `./run.sh cliftest` | client packets, movement, warps (87 assertions) |

**`./run.sh scripttest`** (`map/script/ScriptTest.java`):

- **Phase 1** — loads `sys.lua` plus everything under `Accepted/` and
  `Developers/`; **fails if even one file errors** (currently 906/906 OK).
- **Phase 2** — runs a complete NPC interaction against a fake player
  through the original `Accepted/player.lua`: click → yield at the menu →
  resume with a choice → two-page dialog ("next"/"next") → addItem → write
  a registry value → coroutine finishes → a second interaction. 14
  assertions; exit code 1 on any failure.

**`./run.sh chartest`** (`common/mmo/CharStatusTest.java`) round-trips
every character field — including edge cases such as unsigned 32-bit values
above 4 billion, negative signed values, floats and UTF-8 text — verifies
that corrupt blobs are **rejected** with a clear message rather than
silently misread, and checks the byte layout of the 0x3803/0x3004 packets
offset by offset.

**`./run.sh worldtest`** (`map/data/MapWorldTest.java`) exercises the
spatial index and player placement on the **real Kugnae map**, including
boundary cases: something at x+9 is visible while x+10 is not, moving
across block boundaries updates the index, a player stored inside a wall is
relocated to a walkable tile, and a warp to an unloaded map is rejected
without losing the player.

**`./run.sh cliftest`** (`map/ClifTest.java`) covers the packets the
RetroTK client reads. Since the real client is not available here, every
packet is **built, decrypted back, and then checked offset by offset** —
that is what proves the right key path (static vs per-session key) was
chosen, rather than merely that some bytes came out. Beyond layout it
covers movement (the 0x26 confirmation, the 0x0C broadcast to nearby
players, desync snap-back, collision against walls and other players, GM
bypass, invalid directions), map-edge rules, invisible things (ghosts,
stealthed GMs), and warp tiles together with map entry requirements and
their rejection messages.

> **Important:** `ScriptTest` **does not touch the networking layer at
> all**. When changing `NetServer` / `Session` / `Core`, test with a real
> TCP integration test — open a listening port, connect a client socket,
> then verify that the accept handler runs, that several consecutive
> packets are processed **in order**, that timers keep firing, that
> sessions are closed and their slots reused, and that dozens of parallel
> connections all behave. That pattern was used during the threading
> refactor and passed in full.

Other useful manual checks: run the map server without MySQL — it should
stay alive with a warning, and per-component logs should appear under
`logs/`. Verify the thread split with `jcmd <pid> Thread.print` — there
should be an `rtk-io-<name>` thread separate from the logic thread. A full
end-to-end login needs MySQL with the `RTK` database and a RetroTK client.

## Status & roadmap

### Phase 1 — done

The server-side foundation is in place and verified: the login server is
fully ported, the char server's core is done, the map server routes players
end-to-end, the scripting engine loads all 906 original Lua scripts, plus
the infrastructure (HikariCP, Log4j2, properties, NetBeans/`build.sh`
builds, `run.sh` deployment) and the per-server networking architecture
with its dedicated I/O thread.

### What comes next

Reorganised on 20 August 2026 into three tracks, because the items turned
out to depend on each other rather than forming one straight queue. Tracks
A and B can run in parallel.

**Track A — getting to a playable server** (sequential)

1. ~~**Core `clif_*` packets**~~ — **done, 20 August 2026.** The client is
   now told its id, map, position and time on entry
   (0x05/0x15/0x04/0x20/0x1E/0x22). Still missing: `clif_sendstatus`,
   `clif_getchararea`, and redrawing other players (`clif_sendmapdata` +
   `*look_sub`).
2. ~~**Movement**~~ — **done, 20 August 2026.** `clif_parsewalk` with
   desync detection, collision, camera tracking, confirmation to the
   walker, broadcast to nearby players, plus warp tiles and map entry
   requirements.
3. **End-to-end test against a real MySQL** ← start here. It also validates
   `CharPersistence`, never yet exercised against a live database — and now
   the `Warps` loader as well.
4. **NPCs and dialogs** — `clif_parsenpcdialog` connects the Lua engine to
   the client; once it works, the 906 already-loaded scripts come alive.
5. **Mobs and combat** — `mob.c` (2,411 lines).

**Track B — assets and tooling** (parallel, does not block Track A)

1. **EPF decoder** — EPF + PAL → RGB images, plus mapping `tile`/`obj` ids
   to the right frames (`obj` needs `SObj.tbl`, 18,954 entries).
   Prerequisite for B3.
2. **HTML + JavaScript editor** — edit `.map` files and Lua scripts
   straight in a browser. Can start before the EPF decoder lands, using a
   coloured grid derived from ids/`pass`.
3. **Desktop client in Java + libGDX** — needs the EPF decoder first.

**Track C — technical debt** (do it while touching the relevant area)

- Point `ScriptPlayer` at `CharStatus` so registry values written by
  scripts are persisted (the storage side already exists).
- The four missing meta files — `login.conf` asks for 5, `meta/` only has
  `RidableAnimals`. This is why item tooltips do not appear.
- Cross-map-server warping (currently rejected with a clear message).
- Message boards and mail (char server).

**Script-binding priority reference** (re-measured 20 August 2026): of the
**254** methods scripts call, 110 are provided by `player.lua` itself,
**12** have real Java bindings, 15 are still stubs, and **144 have no
implementation at all**. The most-used missing ones: `calcStat` (232×),
`hasLegend` (229×), `sendSide` (183×), `getObjectsInMap` (177×),
`hasDuration` (171×), `getEquippedItem` (166×). That ordering drives which
subsystems get ported first in Track A.

## Folder layout

```
RTK-java-version/
├── build.xml                    # stock NetBeans form (do not add targets)
├── manifest.mf                  # base manifest for the jar
├── build.sh                     # NetBeans-free build (javac --release 25)
├── run.sh                       # start/stop servers: java -jar <jar> <server> &
├── nbproject/                   # NetBeans J2SE project metadata
│   ├── project.xml              #   project type + source roots
│   ├── project.properties       #   extLib classpath, language level 25, main class
│   ├── build-impl.xml           #   GENERATED by NetBeans (never hand-edit)
│   ├── genfiles.properties      #   checksums triggering regeneration
│   └── private/                 #   machine-local settings (.gitignore)
├── CLAUDE.md                    # guide for AI-assisted development
├── src/org/rtk/
│   ├── RtkLauncher.java         # jar main class: login/char/map/test dispatcher
│   ├── common/                  # port of rtk/src/common: Crypt, Session/NetServer,
│   │   │                        #   TimerSystem, Core, Config, Sql (HikariCP),
│   │   │                        #   ServerLog, Props, Md5
│   │   └── mmo/                 #   port of mmo.h: CharStatus + codec
│   ├── login/                   # port of rtk/src/login
│   ├── charserver/              # port of rtk/src/char (+ CharPersistence)
│   └── map/                     # port of rtk/src/map (MapServer, MapIntif, User, Pc,
│       │                        #   Clif = client packets + movement)
│       ├── data/                #   world: MapFile, MapData, BlockList, MapRegistry
│       └── script/              #   port of sl.c on LuaJ
├── resources/
│   ├── log4j2.xml               # logging config (daily rollover, 30-day retention)
│   └── rtk-server.properties    # technical defaults (crypt keys, ports, pool, paths)
├── extLib/                      # 7 external jars (JDBC, HikariCP, Log4j2+SLF4J, LuaJ) — no Maven
├── conf/                        # original C-format configuration (overrides properties)
├── maps/                        # 3,544 .map map files (~38 MB)
├── luascript/                   # 907 .lua scripts (~6.8 MB)
├── database/                    # MySQL schema + dump (~13 MB)
├── meta/                        # meta files served to the client
├── logs/                        # server logs + console output + PID files (.gitignore)
├── build/  dist/                # build output (.gitignore)
└── .gitignore
```
