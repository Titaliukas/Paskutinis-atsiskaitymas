# Paskutinis atsiskaitymas

A small platform game for up to four players, built for a university Object-Oriented Programming Patterns project.
The Java 25 server owns movement and collision state; Swing clients send keyboard/controller intent and render authoritative
JSON snapshots with Java2D sprites and primitive fallbacks.

Each server runs one selected 960×540 arena: **Combat** (default, six surfaces, two student NPCs and a paired
side passage) or **Training** (stadium background, ground and two moving platforms, no NPCs or passage). Both support up to four players,
character/stat selection, combat, items, stress and respawn. Players create or join independent four-player
lobbies by code after choosing their character. The host starts the game; starting closes entry, including
reconnects. If the host leaves, the lobby closes for everyone. There is no persistence, authentication or match
results. The arena recipe is selected at startup for every lobby; clients cannot switch arenas during play. See [Builder and arenas](docs/builder-arenas.md) for the approved
UML mapping, coordinates and verification.

## Stack and architecture

- Java 25 and Maven Wrapper
- Swing/Java2D desktop client
- Java-WebSocket server and the JDK WebSocket client
- Jackson JSON DTOs and JUnit 5
- Server-only Docker image

```text
game-client ──────────────────────────────> game-protocol
game-server ───> game-protocol
            ├──> game-application ───> game-domain
            └────────────────────────> game-domain
```

| Module             | Responsibility                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------- |
| `game-domain`      | Arena, characters, items/events, NPC strategies/controllers, projectiles, effects, movement and collision |
| `game-application` | Commands, retained input, bounded queue, match controller, and fixed-step loop              |
| `game-protocol`    | Validated transport DTOs and JSON codec; no domain entities                                 |
| `game-server`      | Lobby mediator, connections, per-lobby slots and runtimes, command translation, and snapshots              |
| `game-client`      | Connection form, keyboard and controller input, asynchronous networking, Java2D rendering  |

The server and clients are separate programs because the headless server owns authoritative state while every
player needs an independent desktop UI. They remain in one repository so both processes use the same versioned
protocol and verification build. See [docs/architecture.md](docs/architecture.md) for threading and message flow.

## Prerequisites and Java 25 setup

Source builds and Swing clients require a standalone JDK 25, preferably Eclipse Temurin. Install the JDK variant
for the operating system and CPU architecture, reopen the terminal, and verify:

```sh
java --version
javac --version
```

Both commands must report version 25. On macOS/Linux, the Maven Wrapper also needs `unzip` and either `curl` or
`wget` on its first run. On Windows, use `mvnw.cmd` instead of `./mvnw`. A separate Maven installation is neither
needed nor supported.

In VS Code, install the recommended Microsoft Extension Pack for Java, run **Java: Configure Java Runtime**, and
select the standalone JDK 25 for this workspace. The Docker extension is optional. Shared tasks verify/package the
project and manage Compose; launch configurations start the server, Alice, Bob, or all three processes.

Docker server deployment requires Docker with Compose but does not require Java on the Docker host. Swing clients
always require Java 25 and a desktop environment.

## Fresh clone and build

```sh
git clone <REPOSITORY_URL>
cd <REPOSITORY_DIRECTORY>
java --version
javac --version
./mvnw -version
./mvnw -B -ntp clean verify
```

The build checks module/import boundaries, runs all physics, application, protocol, WebSocket, capacity,
lobby isolation, admission, and shutdown tests, then produces:

- `game-server/target/game-server.jar`
- `game-client/target/game-client.jar`

Client tests run in headless mode and render Swing/Java2D components offscreen; the packaged desktop client
still opens its normal window. The optional particle performance benchmark is enabled with
`-Dparticles.benchmark=true`; all other tests run in the standard build.

## Start a local game directly (macOS)

Install a standalone JDK 25 first. Replace `/path/to/Paskutinis-atsiskaitymas` below with your clone's root
directory (the folder containing `mvnw`, `pom.xml`, and all five modules). Run these steps in order.

Terminal 1 — select JDK 25, build the whole project, and check both runnable JARs:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export PATH="$JAVA_HOME/bin:$PATH"
java --version
javac --version
cd /path/to/Paskutinis-atsiskaitymas
./mvnw -version
./mvnw -B -ntp clean verify
```

All Java version checks, including the Maven Wrapper's Java version, must report 25. Wait for the build to finish
with **BUILD SUCCESS** before continuing. From the same root, confirm that both files exist:

```sh
ls -lh game-server/target/game-server.jar game-client/target/game-client.jar
```

Both JARs include their runtime dependencies. A working server alone does not prove that the client was packaged:
the reactor builds the server before the client, and `clean` deletes previous build outputs. If the build fails,
fix the reported error and rerun the full command before launching. Test skipping is not required.

Terminal 1 — start the default Combat server and leave it running:

```sh
java -jar game-server/target/game-server.jar
```

Wait for `Listening on 0.0.0.0:8080/game`.

Terminal 2 — open a new terminal, select the same JDK, and enter the project root again before starting Alice:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export PATH="$JAVA_HOME/bin:$PATH"
cd /path/to/Paskutinis-atsiskaitymas
java -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
```

Alice selects a character portrait in **Choose your character**, adjusts its stats, and clicks **Create lobby**.
For a solo game, click **Start game**. For multiplayer, keep the lobby waiting and share its six-character code
before starting.

Terminal 3 — an optional second player, **before Alice clicks Start game**:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 25)"
export PATH="$JAVA_HOME/bin:$PATH"
cd /path/to/Paskutinis-atsiskaitymas
java -jar game-client/target/game-client.jar ws://localhost:8080/game Bob
```

Bob selects a character, enters Alice's code in **Lobby code (to join)**, and clicks **Join lobby**.
Repeat in additional terminals with other nicknames for up to four players total. Once everyone has joined,
Alice clicks **Start game**; admission closes immediately. The waiting room shows each player's character portrait,
nickname, and Host/You indicators. Only the host can start. See [Mediator and lobbies](docs/mediator-lobbies.md).

To use Training, start the server with `java -Dgame.arena=training -jar game-server/target/game-server.jar`
instead. Alternatively use `GAME_ARENA=training java -jar game-server/target/game-server.jar`. The `game.arena` JVM
property overrides `GAME_ARENA`; only `combat` and `training` are accepted. An invalid value fails startup.
Stop the current server before starting the other arena on the same port.

Joining is closed once the game starts, even after a guest disconnects. A guest may leave and rejoin while still
waiting. The **Leave** button returns to character selection; if the host leaves or loses connection, everyone
returns with a closure message and their selected build preserved. Each lobby runs an independent world.
The highlighted card is marked **Selected**; Tab and Space also let you choose a card with the keyboard.
When the first game starts, the window is fitted to a 960×540 (16:9) GamePanel, reduced proportionally if needed
for the current monitor's usable work area. Frame decorations, form, margins and status are measured separately
in logical Swing units. Later snapshots, repeated status messages and reconnects retain your chosen window size;
manual resizing remains available, with letterboxing when you choose a different aspect ratio.
Portraits come from the shared `AssetManager` cache. Below them, sliders adjust maximum health, movement speed,
and jump height from **50–150%** in steps of **5**. Each starts at **100%** and shares a **300-point budget**:
lower one stat to free points before increasing another. Unused points are allowed.
**Undo change** steps back through both character choices and stat adjustments; each slider drag is one step.
Switching characters resets all stats to 100%; undoing the switch restores the previous customized character.
Selection, sliders, and undo are disabled while connecting, waiting in a lobby, or playing.
Failed or cancelled connection attempts retain undo history; a successful join clears it and keeps the chosen
character and stats as the baseline for future changes after disconnecting. Each window has its own history.
Multiple players may choose
the same character. Players appear at four distinct spawn positions; each client marks its own world character
with a white outline and `(you)`. Every client shows the same four slot-based corner cards with each player's
chosen portrait, nickname and authoritative stress. The local card has a white border and `(you)`.
Each character has a different attack role (J: ranged, K: melee):

| Character | Ranged | Melee |
| --- | --- | --- |
| PATASIJUS | 12 damage | 32 damage and strong knockback |
| VACIJUS | 30 damage | 12 damage |
| JURGELINIS | 16 damage and slows to 30% speed for 4 seconds | 16 damage and knockback |
| GOGIS | 20 damage and slows to 65% speed for 2 seconds | 22 damage and knockback |

Combat's two student NPCs show authoritative activity labels and switch among four poses; Training has no NPCs. Stop the direct server
with `Ctrl+C`. One to three clients also work; unused corners remain clear.

## Start the server with Docker Compose

```sh
cp .env.example .env
docker compose up --build -d game-server
docker compose logs -f game-server
# To use Training instead (restart/recreate the server):
GAME_ARENA=training docker compose up -d game-server
```

Press `Ctrl+C` to stop following logs, then start up to four Swing clients with the commands above. Compose uses
project `paskutinis-atsiskaitymas`, service `game-server`, and image
`paskutinis-atsiskaitymas-server:local`. The container runs headlessly as a non-root user and contains only the
server JAR and Java 25 runtime.

Stop and remove this project's server container and network with:

```sh
docker compose down
```

## Facade demonstration without a game window

`GameClientFacade` coordinates three private subsystems: `CharacterSelection`, `CommandHistory`, and
`ClientConnection`. Its two clients are `ConnectionWindow` and `GameClientFacadeDemo`; each owns an independent
facade. The facade executes the existing selection commands, sends the current build on create/join, and delegates
input and observer registration to the connection. The window retains rendering and input-device/SDL lifecycle.

After `./mvnw -B -ntp clean verify` with Java 25, start the server in one terminal:

```sh
java -jar game-server/target/game-server.jar
```

In another terminal, run the second facade client:

```sh
java -Djava.awt.headless=true -cp game-client/target/game-client.jar \
  lt.ktu.paskutinisatsiskaitymas.client.GameClientFacadeDemo ws://localhost:8080/game FacadeDemo
```

Both arguments are optional (defaults: `ws://localhost:8080/game`, `FacadeDemo`). The demo prints character/stat
changes, restores VACIJUS with 75/125/100 via undo, creates a lobby, receives `WELCOME`, starts its game, receives a `WORLD_SNAPSHOT`, then disconnects
and closes its HTTP client. It uses the EDT for facade operations and waits on the main thread for at most 12
seconds; failure prints an error and exits unsuccessfully. Stop the server with Ctrl+C afterwards. The runnable
client JAR's main class remains `GameClientMain`.

## Controls

Character selection uses the Command pattern in `game-client`: `CharacterSelection` is the receiver,
`SelectCharacterCommand` and `ChangeStatCommand` implement `UndoableCommand.execute()` and `undo()`, and
`CommandHistory` is the invoker that stores executed commands in a stack. Commands capture an immutable snapshot
of the previous character and stats. `GameClientFacade` creates commands only for actual changes; restoring
cards and sliders during undo does not create another command. See the [UML and build flow](docs/character-selection.md).
The picker stays visible but disabled while connecting, switches to the waiting room after admission, and shows
the arena after the host starts. Leaving, host departure, or a connection error returns to the picker.
The selected character and stat allocation are sent in `CREATE_LOBBY` or `JOIN_LOBBY` when connecting. The server validates the build,
applies it in the game loop, and keeps it across respawns. Temporary boosts and slows still compose with the build.
All four characters currently share the same health, speed, and jump defaults; attack damage remains character-specific.

Pick the device in the **Input** dropdown of the connection form. It can be changed at any time, also mid-game.

For **8BitDo controller**, the left stick moves, the bottom face button jumps, the left face button or right
shoulder performs a ranged attack, and the right face button or left shoulder performs a melee attack.
These are SDL-normalized positions; printed labels depend on the model and mode. This option matches reported
names containing `8bitdo` (case-insensitive) and requires SDL to detect and map the controller in its current mode.
A mapping for the Ultimate 2C Wireless over Bluetooth on macOS is bundled in `gamecontrollerdb.txt`.

| Action     | Keyboard                 | Xbox controller               | PlayStation controller           |
| ---------- | ------------------------ | ----------------------------- | -------------------------------- |
| Move left  | `A` or `Left`            | Left stick                    | Left stick                       |
| Move right | `D` or `Right`           | Left stick                    | Left stick                       |
| Jump       | `Space`, `W` or `Up`     | `A`                           | `Cross`                          |
| Ranged     | `J`                      | `X` or `RB`                   | `Square` or `R1`                 |
| Examinator | `L` (press) | `Y` | `Triangle` |
| Prone/dodge | Hold `S` or `Down` | Hold D-pad Down | Hold D-pad Down |
| Melee      | `K`                      | `B` or `LB`                   | `Circle` or `L1`                 |

Every character has an **Examinator** inspection drone: one accepted press starts a 0.5-second scan and a
10-second cooldown, followed by one instant arena-wide volley using that character's ranged effects. Holding
L/top face never repeats it. Hold S/Down (D-pad Down on all controllers, including 8BitDo) to go prone while
grounded; release to stand. Prone blocks voluntary movement/jump/new attacks and dodges Examinator only.
Ordinary combat and external impulses still work. Respawning or entering prone cancels a pending scan without
refunding cooldown. Both clients show server-recorded shots, drone phases and cooldown. See
[Iterator and Examinator](docs/iterator-examinator.md) for GoF roles, the deterministic domain demonstration,
resource fallbacks, and a two-client verification scenario.

The client tracks both press and release, so movement does not depend on operating-system key repeat. Click the
game area if keys do not respond. Releasing every movement key stops the character. Holding both left and right
produces no horizontal movement.

Controllers are read through [Jamepad](https://github.com/libgdx/Jamepad) (SDL2), whose native libraries for Windows,
macOS and Linux are bundled in the client JAR. Plug the controller in before starting the client. The stick has a
dead zone, so a resting stick does not move the character, and unplugging the controller stops all input. If SDL
cannot load, the dropdown offers only the keyboard.

Combat v2 stationary surfaces are roof `(45,215,870)`, canopy `(333,344,294)`, service platform `(210,280,120)` and
ground `(0,421,960)` (X, top Y, width). Walk under the canopy, jump onto it, jump onto the service platform,
then onto the roof with the default build. The 129-unit canopy-to-roof gap needs the service platform; shared
jump/gravity constants are unchanged. The old side ledges were removed. Platforms catch downward movement and
allow upward passage. At ground level, paired concrete edge openings transfer players to the opposite edge;
the full body must fit inside Y=343–421. Training uses its v2 stadium background and one ground edge at Y=448.
Both arenas also contain one horizontal and one vertical moving platform, described below.
Both original v1 and v2 PNG files are preserved. Subtle platform rims and the service platform/openings are
painted from snapshot geometry. Small-window HUD cards scale down to leave the openings visible.

Enable thin geometry outlines on a client:

```sh
java -Dgame.debug.geometry=true -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
```

## Platform Template Method

`Platform` is abstract. Its final update sequence remembers the previous position, calls
`calculateNextPosition(seconds)`, refreshes collision bounds, then carries assigned standing players and NPCs
by the platform displacement. `StationaryPlatform` returns its current position; `HorizontalPlatform` and
`VerticalPlatform` travel at constant speed and reverse at their endpoints, retaining overshoot.
The game loop assigns supports before moving any platform, then runs character and NPC physics.
Jumping detaches immediately without inheriting platform velocity; walking off and knockback also release support.

| Arena | Movement | Start → end (top-left) | Size | Speed (units/s) |
| --- | --- | --- | --- | ---: |
| Combat | Horizontal | `(360,130)` → `(540,130)` | 120×10 | 60 |
| Combat | Vertical | `(760,350)` → `(760,280)` | 120×10 | 40 |
| Training | Horizontal | `(160,350)` → `(400,350)` | 120×10 | 60 |
| Training | Vertical | `(640,360)` → `(640,250)` | 120×10 | 40 |

Existing surfaces use `StationaryPlatform`. Moving platforms reuse the `SERVICE` appearance and existing
snapshot geometry; clients render the server positions without simulating paths. The Prototype pattern is
preserved: `clone()` keeps subtype and motion phase with a fresh UUID, while `copyAt(Position)` translates
the entire path. Each session clones its platform recipes to own independent movement state.
Circular paths, endpoint pauses, crushing damage and inherited jump momentum are not implemented.

## Manual playable test

1. Start Combat and four clients. Create one lobby and join the other three by code.
2. Try a fifth client while waiting: it receives `LOBBY_FULL`. Have a guest leave, then reuse that slot.
   Start as host and check four distinct spawns and corner HUD cards. New joins now receive `LOBBY_STARTED`.
3. With default stats, walk under the canopy, jump onto it, then the service platform and roof. Repeat from the other side.
4. Walk outward into each ground-level opening. Check opposite-side arrival, continued movement, stable stress/health and no bouncing.
5. Try both edges outside the opening height and at roof height: they remain closed. Check combat, items and respawn.
6. Resize the window, including a non-16:9 size; compare debug outlines against the background.
7. Ride both moving platforms in Combat; check reversals, jumping off and walking off. Restart with Training
   and repeat; check its ground surface, four spawns, closed edges and no NPCs.
8. Create and start a second lobby; verify its players and events never appear in the first.
9. Have the first host leave: its guests return to selection while the second lobby keeps playing.
10. Stop the server and verify clients disconnect without freezing.

Automated offscreen GamePanel previews from actual server snapshots can be reproduced after `clean verify`:

```sh
./scripts/render-arenas.sh
```

42 PNG and seven source JSON files cover ground, canopy, service platform, roof and both openings in
`game-client/target/arena-previews/`, at 960×540, 640×360 and letterboxed
640×400, with normal and debug variants. This verifies rendering without opening a desktop game window.

## Play over a LAN

The server binds to `0.0.0.0`. Local clients use `ws://localhost:8080/game`; a different computer must use:

```text
ws://<SERVER_LAN_IP>:8080/game
```

To set up LAN play:

1. Put the server and client computers on the same network.
2. Find the server host's active LAN address:
   - Windows: run `ipconfig` and locate the active adapter's IPv4 address.
   - macOS: check Network settings or run `ipconfig getifaddr en0` for a typical Wi-Fi interface.
   - Linux/Raspberry Pi OS: run `hostname -I` and use the address for the active LAN interface.
3. Allow inbound TCP port 8080 in the server host's firewall.
4. Start the server directly or with Compose.
5. On the other Java 25 desktop computer, run:

   ```sh
   java -jar game-client.jar ws://<SERVER_LAN_IP>:8080/game Bob
   ```

6. Create a lobby on the first client and join its code on the second. Do not use `localhost` from the second computer because it refers to that computer itself.

The server image works on ARM64 Raspberry Pi OS and AMD64 Linux/cloud hosts without a hard-coded platform.
Raspberry Pi uses the ARM64 image variant; typical cloud hosts use AMD64. Direct JAR deployment needs Java 25 on
the host, while Docker deployment needs Docker but no host Java installation.

Public Internet deployment is not production-ready. TLS, authentication, restrictive firewall rules, and a
reverse proxy are required before exposing the server outside a trusted network.

## Configuration

| Setting                            | Applies to        | Default                    | Purpose                                 |
| ---------------------------------- | ----------------- | -------------------------- | --------------------------------------- |
| `-Dgame.arena=<TYPE>`     | Server JVM        | `combat`                   | Arena; overrides GAME_ARENA             |
| `GAME_ARENA` environment variable  | Server / Compose  | `combat`                   | Arena recipe for this server process    |
| `-Dgame.debug.geometry=true`       | Client JVM        | `false`                    | Thin collision outlines                 |
| `-Dgame.server.port=<PORT>`        | Direct server JVM | unset                      | Highest-precedence listening port       |
| `SERVER_PORT` environment variable | Direct server JVM | `8080`                     | Listening port without the JVM property |
| `SERVER_PORT` in `.env`            | Compose host      | `8080`                     | Host port mapped to container port 8080 |
| Client address/first argument      | Client            | `ws://localhost:8080/game` | Configurable WebSocket endpoint         |
| Nickname/second argument           | Client            | `Player`                   | Display label, 1–32 characters          |

For a direct server on port 9090:

```sh
SERVER_PORT=9090 java -jar game-server/target/game-server.jar
java -jar game-client/target/game-client.jar ws://localhost:9090/game Alice
```

Alternatively use `java -Dgame.server.port=9090 -jar game-server/target/game-server.jar`. Direct Java execution
does not read `.env`; `.env` controls Compose's published host port and arena selection.

## Protocol summary

The `/game` endpoint accepts JSON text messages with explicit stable types:

- `CREATE_LOBBY` / `JOIN_LOBBY`: creates or joins a waiting lobby and requests one of its four slots with a nickname, character, and optional integer `stats` allocation
  (`healthPercent`, `speedPercent`, `jumpHeightPercent`). Omitted stats default to 100/100/100. Malformed fields
  are rejected by the protocol; invalid ranges, steps, or budgets produce `INVALID_STATS` without reserving a slot.
- `JOIN_LOBBY` additionally requires the six-character `code`; whitespace and letter case are normalized.
- `WELCOME`: returns connection UUID, server-assigned player UUID, slot, and nickname after admission.
- `LOBBY_STATE`: publishes code, host player ID, `WAITING`/`RUNNING` phase, and member roster to that lobby.
- `START_GAME`: host-only request to lock entry and start the game.
- `LOBBY_CLOSED`: explains why the lobby closed; clients disconnect and return to selection.
- `INPUT`: sends a monotonically sequenced complete left/right/jump/ranged/melee/examinator/prone state, never position or velocity.
- `WORLD_SNAPSHOT`: broadcasts validated `arenaType` (`combat` or `training`), arena/platform geometry, players (including health, character choice, stress and `slowed`/`prone`), items,
  NPC geometry/activity/facing/attack sequences and `stunned`, player `attacks`, and NPC `projectiles`
  at 20 Hz. Each NPC projectile
  carries its UUID, source NPC ID, explicit `projectileType` (`BOOK` or `DIPLOMA`), center, radius,
  velocity and `returning` flag. Client and server must use this same snapshot format; older snapshots
  missing these fields are rejected.
- `WORLD_SNAPSHOT.examinators`: per-owner drone phase/position/cooldown and sequenced 0.25-second origin/endpoints for instant volley feedback. These are separate from ordinary RANGED/MELEE attacks.
- `PING`/`PONG`: application-level liveness round trip.
- `ERROR`: reports malformed input, wrong direction, missing handshake, capacity, or queue pressure.

Unknown JSON, duplicate fields, trailing values, invalid fields, oversized messages, binary frames, and URL paths
other than `/game` are rejected. A fifth waiting-room join receives `LOBBY_FULL`; started lobbies return `LOBBY_STARTED`.
The former `HELLO` handshake is rejected: update clients and server together. Nicknames are display labels rather than
authenticated identities.

## Troubleshooting

- **Unable to access jarfile:** Java cannot read the JAR at the supplied path. Run `pwd` to check that this terminal
  is in the project root, then `ls -lh game-server/target/game-server.jar game-client/target/game-client.jar`.
  If a file is missing, run `./mvnw -B -ntp clean verify` from that root and wait for **BUILD SUCCESS**. Check both
  files again before launching; compilation or successful server startup alone does not confirm client packaging.
- **Wrong Java:** ensure `java --version`, `javac --version`, and `./mvnw -version` all report Java 25. In VS Code,
  select the same JDK with **Java: Configure Java Runtime**.
- **Port occupied:** stop the other listener or configure another port. Use that same port in every client URL.
- **Connection refused:** confirm the server says it is listening, the URL includes `/game`, and `ws://` is used
  for this local prototype.
- **LAN connection fails:** use the server's LAN address, check both machines can reach each other, and allow the
  published TCP port through the server firewall.
- **Keys do nothing:** wait for the host to start, then click inside the game panel. After disconnecting, join a waiting lobby or create a new one.
- **A character keeps moving:** return focus to the game window and press/release that direction once. Normal
  focus loss sends a neutral input state automatically.
- **Fifth client rejected:** only four slots exist. Disconnect one active client and retry.
- **Docker problem:** run `docker compose ps` and `docker compose logs game-server`; rebuild with
  `docker compose build --no-cache game-server` if necessary.

## NPC projectiles

During CHASE, NPCs keep moving and throw alternating books and diplomas, starting with a book, immediately
when ready and then at most every 1.2 seconds. Shots aim at the player's position at release and travel
straight at 360 units/second without homing or gravity. A player hit reduces horizontal movement speed
by 5% for three seconds. Further hits refresh the timer without stacking. The slowdown multiplies speed
boosts independently and does not change jumping or stress. Shields absorb projectiles and block new slow
applications and refreshes; they do not remove an existing slow. The client displays `−5% speed` below slowed players.

The first left/right arena-wall hit replaces the shot with an independent Prototype clone with a new UUID,
aimed at the thrower's position at that instant. Cyan outlines identify returning shots. These shots pass
through players and other NPCs; hitting the thrower stuns it for one second, with further hits refreshing
the stun. `STUNNED` replaces its displayed activity label while horizontal movement, melee and throwing
are paused. Behavior timers pause too; gravity and attack/throw cooldowns continue, and the same activity
resumes after the stun. Platforms, ground, ceiling and a second side-wall hit absorb shots. Each shot has
an eight-unit collision radius and six seconds total lifetime, including its return. Swept collision checks
resolve the earliest contact and remaining travel after a wall return inside the server tick.

At most eight projectiles are active. A throw skipped at capacity consumes its 1.2-second cooldown but
does not advance the book/diploma alternation. Removing a source NPC or disconnecting the shot's targeted
player removes its associated projectiles. All flight, effects and collision rules run on the authoritative
game-loop thread; clients render snapshots without deciding hits.

## Deterministic Strategy demonstration

After building with Java 25, run from the repository root:

```sh
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.NPCStrategyDemo
```

This drives the production session/controller with an explicit flat demonstration arena, without networking or sleeps. It prints PATROL, CHASE, the
ATTACK attempt resolved within a tick, FLEE, actual player stress, and the return to PATROL on the same NPC.
A successful ATTACK can be shorter than a snapshot interval; the client latches its image for 180 ms using the
monotonic attempt sequence while keeping the server activity label truthful.

Current limitations: direct snapshot rendering may look less smooth on slow networks; there is no prediction,
interpolation, multi-frame animation, sound, menu, match lifecycle, or Internet security layer.

## Builder demonstration

```sh
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.BuilderDemo
```

Uses the real Director with Combat, Training and Combat again, showing that reusing a builder does not accumulate
surfaces or NPC recipes. See [docs/builder-arenas.md](docs/builder-arenas.md) for responsibilities and UML additions.

## Particles (client-only visual effects)

`game-client` renders chunky pixel-art particles for ranged shots, projectile hits, powerup pickups, active speed and jump boosts, item spawn sparkle and idle shimmer, jump puffs, landing dust scaled by fall height, moving-platform dust
and footsteps. They are presentation only: `ParticleTriggers` derives each burst by comparing two consecutive
authoritative `WorldSnapshot`s on the EDT, so the protocol, server and domain are unchanged.

Particles use the **Flyweight** pattern. `ParticleType` is the flyweight: the immutable, shared state of one
`ParticleKind` (colour, size, lifetime, physics constants, the pre-rendered sprite and the fade composites).
`ParticleTypeFactory` hands out exactly one `ParticleType` per kind. Each `Particle` keeps only its extrinsic state
(position, velocity, age) plus a reference to its type, and passes that state to `ParticleType.draw`. The earlier
version gave every particle its own copy and sprite; its measurements were recorded with
`implementation=naive`, the current ones with `implementation=flyweight`. A quick headless check of heap per
particle and median frame time:

```bash
./mvnw -B -ntp -pl game-client -am test -Dtest=ParticleBenchmarkTest -Dparticles.benchmark=true -Dsurefire.failIfNoSpecifiedTests=false
```

Results are written to `game-client/target/particle-benchmark.txt`.

### Particle measurements (Flyweight benchmarks)

Two ways to measure the particle system's speed and memory. Both write a console table and append one row to
`particle-stats.csv` (override the path with `-Dparticles.stats.csv=<file>`), so runs before and after the Flyweight
refactor can be compared row by row. The `implementation` column (`ParticleStats.IMPLEMENTATION`) says which build
produced a row (`naive` before the Flyweight refactor, `flyweight` after).

1. **Gameplay stats** - play normally. The client prints the table and appends a CSV row when the connection ends
   (stop the server, press Disconnect) or the client window is closed. One row per play session, `scenario=gameplay`.
2. **Heavy benchmark** - a visible window with many spawners that floods particles and stops by itself:

```bash
./mvnw -B -ntp -pl game-client -am package -DskipTests
```

```bash
java -cp game-client/target/game-client.jar lt.ktu.paskutinisatsiskaitymas.client.ParticleStressBenchmark --duration=20 --warmup=3 --spawners=40 --rate=1000 --cap=200000
```

`--rate` is particles per second per spawner. The warm-up is not measured. Row has `scenario=stress` and also
`retained_bytes_per_particle`: heap held per live particle, measured after the run with forced GCs.

Recorded columns: run info (timestamp, implementation, scenario, duration, JVM, cores, max heap, spawners, rate, cap);
volume (spawned total, per second and per kind, expired, evicted by the cap, peak and average live); speed (update,
draw and frame time as avg/p50/p95/p99/max in ms, frames, effective FPS, emit ns per particle); memory (bytes
allocated by particle code in total, per spawned particle and split into emit/update/draw, sprites created and their
bytes, heap start/peak/end, GC count and pause time, retained bytes per particle).
