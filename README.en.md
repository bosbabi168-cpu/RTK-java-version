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

## Project direction (updated 26 August 2026)

**The RetroTK protocol will be replaced with our own design, and the client
will be rebuilt from scratch using libGDX.** Byte-for-byte compatibility with
the original RetroTK client is **no longer a goal**.

What this means for anyone reading this code:

- **Carries over** to the new protocol: 906 Lua scripts, 9,850 maps, 4,476
  warps, 716 mob types, 2,545 items, and the game-logic bindings.
- **Will be rewritten**: the entire `clif_*` packet layer. This is why
  `Clif.sendMyStatus()` is deliberately left unfinished.
- Fidelity to the C source is still enforced for **logic**, not for the wire
  format.

Before this decision, the original RetroTK client did **successfully enter the
world** once four server bugs were fixed (see "Status & roadmap"). All four
had slipped past 294 test assertions — evidence that self-written tests cannot
substitute for a real client.

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
./run.sh dbtest       # needs MySQL (see "Running")
./run.sh luaaudit     # helper: static checker for the Lua scripts

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
java -jar RTK-java-version.jar dbtest      # database layer (needs MySQL)
java -jar RTK-java-version.jar luaaudit    # static checker for Lua scripts
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

1. Prepare the `RTK` MySQL database using this project's `database/` folder.
   `database/2020-09-02-21-55-01_RTK.sql.bak` is a full dump (54 tables)
   containing the content data — among it **9,850 `Maps` rows** and
   **4,476 `Warps` rows** used by the map server. `database/scripts/` holds
   21 migration scripts run in order by `database/migrate.sh`, useful when
   you want to follow the schema history.

   > **Gotcha on Ubuntu/Pop!_OS:** MySQL's `root` uses the `auth_socket`
   > plugin rather than an empty password — it is reachable only through
   > `sudo mysql`. The symptom is `ERROR 1698 (28000)`, not `1045`. Create
   > the `rtk` user that `conf/char.conf` already expects, so no config
   > change is needed:

   ```bash
   sudo mysql -e "CREATE USER IF NOT EXISTS 'rtk'@'localhost' IDENTIFIED BY '50LM8U8Poq5uX2AZJVKs'; \
     GRANT ALL PRIVILEGES ON *.* TO 'rtk'@'localhost' WITH GRANT OPTION; FLUSH PRIVILEGES;"

   mysql -h 127.0.0.1 -u rtk -p < database/2020-09-02-21-55-01_RTK.sql.bak
   ```

   The dump already contains `CREATE DATABASE RTK`, so there is nothing to
   create first. **Note:** its first statement is `DROP DATABASE IF EXISTS
   RTK` — check what an existing `RTK` database holds before re-importing.
   The dump was taken on MySQL 5.7 and imports into 8.0.46 unchanged. Once
   it is in place, `./run.sh dbtest` should be green.
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
`.map` files. Because 9,850 `Maps` rows reference only 2,919 unique files,
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

**NPCs.** 385 NPCs are loaded from the `NPCs<serverId>` table along with
their equipment and placed into the block index. Besides the spatial index
there is an id index (C's `map_id2bl`), because the client sends a **block
id** back when a player clicks something. That id is not `NpcId` as-is but
`NPC_START_NUM + NpcId - 2` — an odd offset that has to be preserved
because it is part of the protocol.

**Warp tiles.** The `Warps` table (4,476 rows) is loaded into a per-block
list on each map. The real data contains **61 tiles listed more than once**,
26 of them with different destinations; the C version lets the **last row**
win, so the lookup here walks its list backwards to match. When a player steps onto a warp tile, the destination
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
| **gameplay** (`pc.c`, `mob.c`, `npc.c`, `clif.c`, ~22k lines) | `map/User.java`, `map/Pc.java`, `map/Clif.java`, `map/Npc*.java`, `map/Mob*.java` | ✅ **Track A complete** — world entry plus rendering of everything nearby (0x33), movement and warps, NPC dialog/menu/input (0x30/0x2F/0x39/0x3A), shops (buy and sell), NPCs and their timers, mobs: 716 types and 1,175 spawns, AI on a 50 ms tick, combat, death and drops. ⚠️ never yet tested against a real RetroTK client |
| **scripting engine** (`sl.c`, ~11k lines) | `map/script/ScriptEngine.java`, `ScriptClass.java`, `ScriptInstance.java`, `Bindings.java`, `ScriptPlayer.java` | ✅ **working via LuaJ** — all 906 original scripts load without error; typel object model, `root.method` dispatch, `_async` coroutines with blocking dialogs, registries and inventory wired through to `CharStatus`. ⚠️ of the ~258 methods scripts call, **50 exist in `sl.c` but are not ported yet**; the most-used ones are now covered (`sendAction` 905×, `talk` 698×, `playSound` 632×, `updateState` 434×, `setDuration` 423×, `spawn` 381×, `calcStat` 249×, `moveGhost` 84×, plus the whole floor-item family). For current numbers: `./run.sh luaaudit` |
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
there are six regression gates, all of which must stay green:

| Command | What it covers |
|---|---|
| `./run.sh scripttest` | 906 Lua scripts load + NPC dialog coroutines |
| `./run.sh maptest` | 3,544 map files parse correctly |
| `./run.sh chartest` | character serialisation (29 assertions) |
| `./run.sh worldtest` | map world + player placement (53 assertions) |
| `./run.sh cliftest` | client packets, movement, warps, rendering, map redraw, NPC dialogs, facing, chat & actions, spell durations, mob movement, floor items, inventory, spell book (481 assertions) |
| `./run.sh dbtest` | database layer against live MySQL (132 assertions) |

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

**`./run.sh dbtest`** (`charserver/DbTest.java`) is the only gate that needs
a live MySQL. Three phases:

1. **SQL audit** — every SQL statement in the ported code is read out of its
   source file and `prepare`d against the server. MySQL resolves table and
   column names at prepare time, so a single wrong name fails immediately.
   This replaces checking hundreds of column names by hand, and cannot go
   stale: the test reads whatever the code currently says. Two statements
   build their table name at runtime and so cannot be prepared as-is; they
   are deliberately skipped here and covered by the round-trip in phase 3 —
   the skipped count is reported so nothing looks checked when it isn't.
2. **World data** — 9,850 maps and 4,476 warps are loaded from the real
   tables, then every warp tile is checked to make sure it points at a tile
   that actually exists on the destination map.
3. **Character round-trip** — a test character is filled with extreme values
   (full unsigned 32-bit, 9-billion bigints, negative signed columns, float
   karma), saved, read back, and compared field by field including
   inventory, bank, legends, aethers, spellbook and every registry.

The test **never touches existing data** — it only writes to the character
it creates, and deletes it again on the way out, including on failure.

### Lua script audit (`./run.sh luaaudit`)

`scripttest` proves the 906 scripts **load**; it cannot prove they are
correct. Lua checks nothing until a line actually runs, so a misspelled
function name only blows up when a player touches that NPC. `luaaudit`
closes the gap without running the scripts: every file is parsed with the
**same LuaJ parser** the runtime uses, then checked for duplicate table
keys, duplicate definitions, and names used but never defined.

The set of "names that exist" is not guessed — the script engine is started
first, then its globals table and the `Player`/`NPC`/`Mob` prototypes are
read.

The output separates two things that are easy to confuse:

- **"AKAN MELEDAK" (will blow up)** — undefined globals that are *called or
  indexed*. Merely reading an undefined global is legal in Lua (it yields
  nil, and plenty of scripts check exactly that), so only uses that really
  do throw are listed here.
- **"not yet ported" vs "does not exist anywhere"** — names are
  cross-referenced against the C `sl.c`. Present there means a known
  porting gap; absent everywhere means a typo or dead code.

The first audit (21 August 2026) found 13 files needing fixes plus two Lua
5.1/5.2 compatibility gaps in the script engine. All of it is recorded in
[`luascript/PERUBAHAN.md`](luascript/PERUBAHAN.md).

> **Important:** because of those fixes, `luascript/` is **no longer
> byte-identical** to upstream's `rtklua/`. Read `PERUBAHAN.md` before
> copying content over from there.

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

### Latest status — 26 August 2026

The starting point for the next session.

| | |
|---|---|
| Regression gates | 6/6 green (`cliftest` **481**, `dbtest` **141** assertions) |
| `logs/map.log` on a live server | **0 ERROR / 0 WARN** |
| All three servers | running side by side (`./run.sh all`), map↔char link stable |
| Script bindings | **56 not ported** (50 in `sl.c` + 6 typos); globals not ported: **1** |
| Bindings still **stubbed** | **none left that are real** — only `sendSound` and `updateStatus`, which do not exist in `sl.c` at all |
| Lua scripts | 906/906 loaded, 0 errors |
| **Real RetroTK client** | **entered the world successfully** — the protocol hunt was then stopped |

The 26 August session closed two blocks: (1) bindings that were still
**stubs** — `talk` (698x), `sendAction` (905x), `playSound` (632x),
`updateState` (434x), `delete`, `refresh`, `sendHealth`, `removeItemSlot`,
`updatePath`; and (2) the **spell duration & aether subsystem**
(`map/Durations.java`): `setDuration` (423x) plus 15 sibling bindings and
the one-second `bl_duratimer()` tick; and (3) `moveGhost` (84x, how
almost every mob AI moves) and `spawn` (381x, traps / event mobs / instance
bosses), which together **emptied the stub list**; and (4) **BL_ITEM**,
floor items — the last large subsystem that was missing entirely, which
alone opened ~95 call sites; and (5) the remaining mob-movement variants
(`moveIntent`, `checkMove`, `moveIgnoreObject`); and (6) the inventory
packets (0x0F / 0x10) with `updateInv`, `hasEquipped`, and the `deduct*`
family; and (7) the spell book (`getSpells`, `getSpellName`,
`getUnknownSpells`, `getAllClassSpells`, `addHealth`).

⚠️ **The `luaaudit` number does not measure this work fairly.** A binding
ported out of a *stub* never counted in the audit to begin with — as far as
the audit is concerned the name was already "defined". See the "still
stubbed" row above.

To confirm this still holds:

```bash
./build.sh
for t in scripttest maptest chartest worldtest cliftest dbtest; do ./run.sh $t; done
./run.sh stop && rm -f logs/map.log && ./run.sh all
sleep 240 && grep -c ERROR logs/map.log     # must be 0
```

> ⚠️ **Do not run the regression gates while the servers are live** —
> `cliftest` and `luaaudit` write to the same `logs/map.log`, so the ERROR
> count gets polluted by lines the tests expect to produce.

#### Follow-up work after the end-to-end test

**1. Test with the real RetroTK client — the only genuine blocker.**
No new code is needed; everything is prepared (`127.0.0.1`, `version: 750`,
Wine 9.0 i386, original `ddraw.dll`). Until a real player enters the world,
**all of Track A stays "verified offline"**.

**2. Bindings that are still stubs — they do not throw, but they do not
work either.** This category is dangerous precisely because `map.log` stays
clean: a stub logs one WARN and returns nil.

| Method | Call sites | State |
|---|---|---|
| `sendAction` | 905x | ✅ ported 26 Aug |
| `talk` | 698x | ✅ ported 26 Aug (`clif_speak` 0x0D) |
| `playSound` | 632x | ✅ ported 26 Aug |
| `updateState` | 434x | ✅ ported 26 Aug |
| `setDuration` | 423x | ✅ ported 26 Aug |
| `spawn` | 381x | **still a stub** — needs `mobspawn_onetime` |
| `setAether` | 225x | ✅ ported 26 Aug |
| `msg` | 133x | ✅ ported 26 Aug |
| `delete` | 109x | ✅ ported 26 Aug |
| `moveGhost` | 84x | ✅ ported 26 Aug |
| `dropItemXY` / `throw` / `dropItem` / `pickUp` | ~90x | ✅ ported 26 Aug (BL_ITEM) |

**The stub list and BL_ITEM are both done.** What remains is inventory &
equipment (~65 call sites, needs an inventory packet), then a long tail of
small groups — see `CLAUDE.md` for the ranked roadmap.

⚠️ Two freshly ported paths have **never run on a live server**: the
duration tick and `moveGhost`. Both only fire for players who are actually
online (the mob AI tick skips maps where `map.users == 0`), so a real
client is needed to exercise them.

~~**3. BL_ITEM (floor items) does not exist yet**~~ — **done 26 August 2026**
(`map/FloorItem`, `map/FloorItemRegistry`). The `...WithTraps` filter now
genuinely differs from the plain variant, and mob drops are visible on the
ground.

**4. Inventory & equipment** — the packets (0x0F / 0x10) and the
standalone bindings are **done**. What remains all depends on the **BOD
subsystem** (`sd->boditems`, items that break or are lost on death), which
does not exist yet: `stripEquip` (9x), `checkInvBod`, `getBODItem`,
`deductDuraEquip`, `expireItem`. Do BOD as one block.

**5. The rest of Track C** — C2 (meta files), C3 (cross-map-server warps),
C4 (boards and mail, the least blocked).

**Bugs found and closed in this round:** the stub list overwriting freshly
ported bindings; LuaJ's `name` field crippling stub reporting so that only
the *first* missing binding was ever reported; `objectRef()` wrapping
players as NPCs; `bladestorm_trap.lua` calling a method on nil.

### What comes next

Split into three tracks because the items depend on each other rather than
forming one straight queue. Tracks B and C can run in parallel.

**Track A — COMPLETE (21 August 2026).** The server is now playable
end-to-end as far as its logic goes:

1. **Core `clif_*` packets** — a player enters the world, receives its
   id/map/position/time/stat panel, then **sees everything around it**:
   other players, NPCs and mobs (packet 0x33). Map tiles are redrawn while
   walking, with a checksum so tiles the client already holds are not resent.
2. **Movement** — desync detection, collision, camera tracking, broadcast to
   nearby players, and warp tiles with map entry requirements.
3. **End-to-end test against a real MySQL** — `./run.sh dbtest`, which also
   validates `CharPersistence` and the `Warps` loader.
4. **NPCs and dialogs** — clicking an NPC (0x43), dialog/menu/input boxes
   (0x30 / 0x2F), replies over 0x39 and 0x3A, the NPC-as-character variant,
   NPC timers, and **complete shops**. The item bindings write to the real
   inventory and are persisted.
5. **Mobs and combat** — 716 mob types and 1,175 spawns from the database,
   AI on a 50 ms tick, script-driven combat, a threat table, and death with
   `on_death` and drops.

**Fix round driven by a live server (24 August 2026).** Running all three
servers and reading `logs/map.log` took **21 unique script errors down to
0 ERROR / 0 WARN**. Every one of them was an NPC timer hook — code that
only runs once the server is actually alive, which is why none of the six
regression gates nor `luaaudit` ever touched it. A clean `map.log` is now
a requirement after touching any binding or script hook.

> ⚠️ **All of it is still verified offline.** Packets are built, decrypted
> back and checked offset by offset — but not one of them has ever been read
> by a real RetroTK client.

**Current priorities**, ordered by what is blocking rather than by size:

1. **Test against a real client.** No new code needed; change `map_ip` in
   `conf/map.conf` and run `./run.sh all`.
2. **The most-used bindings** — `calcStat` (249×), `addNPC`, `addSpell`,
   `bankDeposit`/`bankWithdraw`. Nothing blocks these.
3. **Boards and mail (C4)** — pure protocol, testable offline.
4. **Meta files (C2) and cross-map-server warps (C3)** — buildable, but not
   provable without a client.

**Track B — assets and tooling** (parallel, does not block Track A)

1. **EPF decoder** — EPF + PAL → RGB images, plus mapping `tile`/`obj` ids
   to the right frames (`obj` needs `SObj.tbl`, 18,954 entries).
   Prerequisite for B3.
2. **HTML + JavaScript editor** — edit `.map` files and Lua scripts
   straight in a browser. Can start before the EPF decoder lands, using a
   coloured grid derived from ids/`pass`.
3. **Desktop client in Java + libGDX** — needs the EPF decoder first.

**Track C — technical debt**

- ~~Point `ScriptPlayer` at `CharStatus`~~ — **done, 21 August 2026.** The
  script registries and the character registries are now the same objects,
  so anything a script writes is persisted automatically.
- **The four missing meta files** — `login.conf` asks for 5, `meta/` only
  has `RidableAnimals`. This is why item tooltips are missing. Candidates
  **do exist** in `RTK-Server/rtk/decrypted/` (29 files) in a related
  format, but the names do not line up (`ItemInfo0C.dat` vs the requested
  `ItemInfo0`) and `RideableAnimals.dat` is not the counterpart of the file
  currently in use. A client is needed to tell which is right.
- **Cross-map-server warps** — currently refused with a clear message;
  testing needs two map servers running.
- **Boards and mail** (char server) — the least blocked item in this track:
  protocol and tables already exist, and it can be tested offline.

**Script-binding priority.** Of the ~258 methods scripts call, **50 exist
in `sl.c` but are not ported yet** as of 26 August 2026 (110 → 100 → 50),
and only **1 global** (`lock`) is still missing — down from 6. For current
numbers, always run `./run.sh luaaudit`.

Done on 21 August 2026, most-used first: `calcStat` (**249×**), `addNPC`
(54×), `addSpell` (28×), `callBase` (12×), `hasSpell`, `bankDeposit` /
`bankWithdraw`. Banking now works, and scripts can spawn temporary NPCs
(traps, decorations) that remove themselves when their duration runs out.

**The 24 August 2026 round used a different yardstick: `map.log` from a
server that was actually running**, not usage counts across the corpus.
That produced `sendSide`, `npc:move()` / `warp()`, the whole `getObjects*`
family (`InCell` / `InArea` / `InSameMap` / `InMap`, each with an `Alive`
variant, plus `getBlock` and `getUsers` — 178 combined call sites),
`getMapXMax` / `getMapYMax`,
`getMapRegistry` / `setMapRegistry` (**per map**, not one shared table),
`getPass` / `getObject` / `getTile` / `getWarp`, the `Player(id)` constructor,
`sendAnimation` / `sendAnimationXY`, and the `MOB_*` / `F1_NPC` constants —
plus `player:sendStatus()`, which turned out to still be a stub even though
its packet had existed since A1.

Nothing left stands out as sharply — `hasLegend`, `getEquippedItem` and
`killCount` sit in the 100–200 range. These numbers move every time a
binding is added, so run `./run.sh luaaudit` for the current count rather
than trusting what is written here.

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
