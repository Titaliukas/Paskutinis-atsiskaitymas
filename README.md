# Paskutinis atsiskaitymas

A small playable four-player platform prototype for a university Object-Oriented Programming Patterns project.
The Java 25 server owns movement and collision state; Swing clients send keyboard intent and render authoritative
JSON snapshots with Java2D sprites and primitive fallbacks.

The implemented scope is intentionally narrow: one 960×540 arena, one ground platform, two colored player
rectangles, separate spawn positions, horizontal movement, jumping, gravity, arena/ground collision, and safe
disconnect/reconnect. There is no combat, scoring, enemies, items, persistence, authentication, or match result.

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
| `game-server`      | Connections, four player slots, command translation, scheduling, and snapshots              |
| `game-client`      | Connection form, keyboard state, asynchronous networking, and Java2D rendering              |

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
reconnection, and shutdown tests, then produces:

- `game-server/target/game-server.jar`
- `game-client/target/game-client.jar`

## Start a local game directly

Open five terminals in the repository root after building.

Terminal 1 — server:

```sh
java -jar game-server/target/game-server.jar
```

Terminal 2 — first client:

```sh
java -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
```

Terminal 3 — second client:

```sh
java -jar game-client/target/game-client.jar ws://localhost:8080/game Bob
```

Terminal 4 — third client:

```sh
java -jar game-client/target/game-client.jar ws://localhost:8080/game Charlie
```

Terminal 5 — fourth client:

```sh
java -jar game-client/target/game-client.jar ws://localhost:8080/game Dora
```

Click a character's portrait card in the **Choose your character** screen, then click **Connect** above.
The highlighted card is marked **Selected**; Tab and Space also let you choose a card with the keyboard.
Portraits come from the shared `AssetManager` cache. Below them, sliders adjust maximum health, movement speed,
and jump height from **50–150%** in steps of **5**. Each starts at **100%** and shares a **300-point budget**:
lower one stat to free points before increasing another. Unused points are allowed.
**Undo change** steps back through both character choices and stat adjustments; each slider drag is one step.
Switching characters resets all stats to 100%; undoing the switch restores the previous customized character.
Selection, sliders, and undo are disabled while connecting or connected.
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

The two green student NPCs show authoritative activity labels and switch among four poses. Stop the direct server
with `Ctrl+C`. One to three clients also work; unused corners remain clear.

## Start the server with Docker Compose

```sh
cp .env.example .env
docker compose up --build -d game-server
docker compose logs -f game-server
```

Press `Ctrl+C` to stop following logs, then start up to four Swing clients with the commands above. Compose uses
project `paskutinis-atsiskaitymas`, service `game-server`, and image
`paskutinis-atsiskaitymas-server:local`. The container runs headlessly as a non-root user and contains only the
server JAR and Java 25 runtime.

Stop and remove this project's server container and network with:

```sh
docker compose down
```

## Controls

Character selection uses the Command pattern in `game-client`: `CharacterSelection` is the receiver,
`SelectCharacterCommand` and `ChangeStatCommand` implement `UndoableCommand.execute()` and `undo()`, and
`CommandHistory` is the invoker that stores executed commands in a stack. Commands capture an immutable snapshot
of the previous character and stats. The connection form creates commands only for actual changes; restoring
cards and sliders during undo does not create another command. See the [UML and build flow](docs/character-selection.md).
The picker stays visible but disabled while connecting, switches to the arena after joining, and returns after
disconnecting or a connection error.
The selected character and stat allocation are sent in `HELLO` when connecting. The server validates the build,
applies it in the game loop, and keeps it across respawns. Temporary boosts and slows still compose with the build.
All four characters currently share the same health, speed, and jump defaults; attack damage remains character-specific.

| Action     | Keys           |
| ---------- | -------------- |
| Move left  | `A`            |
| Move right | `D`            |
| Jump       | `Space` or `W` |

The client tracks both press and release, so movement does not depend on operating-system key repeat. Click the
game area if keys do not respond. Releasing every movement key stops the character. Holding both left and right
produces no horizontal movement.

The default arena has a ground platform at `(0,480)` and three `160 × 20` raised platforms at `(140,380)`,
`(660,380)`, and `(400,280)`. Jump onto either lower platform, then toward the center platform. Players pass
through a platform from below and land when falling onto its top. The raised platforms are copies made with
`Platform.copyAt(Position)` from one template; each copy has its own UUID.

## Manual playable test

1. Start one server and Alice and Bob clients, then click **Connect** in both.
2. Confirm both windows display the same arena, platform, and two named rectangles.
3. Focus Alice's game panel. Hold `D`, release it, then press `Space`. Confirm only Alice moves and jumps in both
   windows, stops on release, lands on the platform, and cannot leave the arena.
4. Focus Bob's panel and repeat using `A` and `W`. Confirm Bob moves independently in both windows.
5. Start a third client and click **Connect**. Confirm it reports `SERVER_FULL` while Alice and Bob remain active.
6. Disconnect or close Alice. Confirm Alice disappears without stopping Bob.
7. Connect the third client again. Confirm it receives the freed slot, appears at that slot's spawn, and can move.
8. Stop the server. Confirm clients report disconnection instead of freezing or crashing.

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

6. Click **Connect**. Do not use `localhost` from the second computer because it refers to that computer itself.

The server image works on ARM64 Raspberry Pi OS and AMD64 Linux/cloud hosts without a hard-coded platform.
Raspberry Pi uses the ARM64 image variant; typical cloud hosts use AMD64. Direct JAR deployment needs Java 25 on
the host, while Docker deployment needs Docker but no host Java installation.

Public Internet deployment is not production-ready. TLS, authentication, restrictive firewall rules, and a
reverse proxy are required before exposing the server outside a trusted network.

## Configuration

| Setting                            | Applies to        | Default                    | Purpose                                 |
| ---------------------------------- | ----------------- | -------------------------- | --------------------------------------- |
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
does not read `.env`; `.env` controls Compose's published host port.

## Protocol summary

The `/game` endpoint accepts JSON text messages with explicit stable types:

- `HELLO`: requests one of four slots with a nickname, character, and optional integer `stats` allocation
  (`healthPercent`, `speedPercent`, `jumpHeightPercent`). Omitted stats default to 100/100/100. Malformed fields
  are rejected by the protocol; invalid ranges, steps, or budgets produce `INVALID_STATS` without reserving a slot.
- `WELCOME`: returns connection UUID, server-assigned player UUID, slot, and nickname.
- `INPUT`: sends a monotonically sequenced complete left/right/jump state, never position or velocity.
- `WORLD_SNAPSHOT`: broadcasts arena/platform geometry, players (including health, character choice, stress and `slowed`), items,
  NPC geometry/activity/facing/attack sequences and `stunned`, player `attacks`, and NPC `projectiles`
  at 20 Hz. Each NPC projectile
  carries its UUID, source NPC ID, explicit `projectileType` (`BOOK` or `DIPLOMA`), center, radius,
  velocity and `returning` flag. Client and server must use this same snapshot format; older snapshots
  missing these fields are rejected.
- `PING`/`PONG`: application-level liveness round trip.
- `ERROR`: reports malformed input, wrong direction, missing handshake, capacity, or queue pressure.

Unknown JSON, duplicate fields, trailing values, invalid fields, oversized messages, binary frames, and URL paths
other than `/game` are rejected. A fifth join receives `SERVER_FULL`. Nicknames are display labels rather than
authenticated identities.

## Troubleshooting

- **Wrong Java:** ensure `java --version`, `javac --version`, and `./mvnw -version` all report Java 25. In VS Code,
  select the same JDK with **Java: Configure Java Runtime**.
- **Port occupied:** stop the other listener or configure another port. Use that same port in every client URL.
- **Connection refused:** confirm the server says it is listening, the URL includes `/game`, and `ws://` is used
  for this local prototype.
- **LAN connection fails:** use the server's LAN address, check both machines can reach each other, and allow the
  published TCP port through the server firewall.
- **Keys do nothing:** click inside the game panel. Reconnect if the status is not `Connected`.
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

This drives the production session/controller without networking or sleeps. It prints PATROL, CHASE, the
ATTACK attempt resolved within a tick, FLEE, actual player stress, and the return to PATROL on the same NPC.
A successful ATTACK can be shorter than a snapshot interval; the client latches its image for 180 ms using the
monotonic attempt sequence while keeping the server activity label truthful.

Current limitations: direct snapshot rendering may look less smooth on slow networks; there is no prediction,
interpolation, multi-frame animation, sound, menu, match lifecycle, or Internet security layer.
