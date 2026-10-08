# Paskutinis atsiskaitymas

A small playable four-player platform prototype for a university Object-Oriented Programming Patterns project.
The Java 25 server owns movement and collision state; Swing clients send keyboard intent and render authoritative
JSON snapshots with Java2D sprites and primitive fallbacks.

The playable scope includes one 960×540 arena, one ground platform, at most four players, separate spawn
positions, horizontal movement, jumping, gravity, collision, and safe disconnect/reconnect. Timed speed/jump
boosts and shields spawn as collectible items. Two student NPCs patrol, detect/chase players, make cooldown-limited
attacks that increase visible player stress, and flee after a hit. Shields block stress damage. There is no player
combat, projectile system, scoring, health/death, persistence, authentication, or match result.

The NPC subsystem implements four separate Strategy classes. See [docs/strategy-npc.md](docs/strategy-npc.md)
for UML correspondence, report-code markers, behavior rules, sprite replacement, and demonstration steps.

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

| Module | Responsibility |
| --- | --- |
| `game-domain` | Arena, characters, items/events, NPC strategies/controllers, damage, movement and collision |
| `game-application` | Commands, retained input, bounded queue, match controller, and fixed-step loop |
| `game-protocol` | Validated transport DTOs and JSON codec; no domain entities |
| `game-server` | Connections, four player slots, command translation, scheduling, and snapshots |
| `game-client` | Connection form, keyboard state, asynchronous networking, and Java2D rendering |

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

Click **Connect** in each window. Players appear at four distinct spawn positions; each client marks its own
world character with a white outline and `(you)`. Every client shows the same four slot-based corner cards with
head-and-shoulders portraits, nicknames and authoritative stress. The local card has a white border and `(you)`.
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

| Action | Keys |
| --- | --- |
| Move left | `A` |
| Move right | `D` |
| Jump | `Space` or `W` |

The client tracks both press and release, so movement does not depend on operating-system key repeat. Click the
game area if keys do not respond. Releasing every movement key stops the character. Holding both left and right
produces no horizontal movement.

## Manual playable test

1. Start one server and Alice, Bob, Charlie and Dora clients, then connect them in that order.
2. Confirm all windows display four named players and two NPCs. Slots 0/1/2/3 occupy top-left/top-right/bottom-left/
   bottom-right cards on every client; only the local player's card has a white border and `(you)`.
3. Move and jump each player with `A`, `D`, `Space`/`W`; confirm only that character responds in every window,
   stops on release, lands on the ground and cannot leave the arena. Its corner card must stay fixed.
4. Approach an NPC, observe an attack and check that the same stress value increases in that player's card on
   every client. Stress can exceed 100; it is a number, not a percentage. A shield still blocks damage.
5. Resize to the supported 640×360 panel minimum. Confirm portraits show faces/shoulders, long card nicknames
   are ellipsized, cards do not overlap, and notifications appear below the top cards.
6. Start a fifth client. Confirm `SERVER_FULL` while all four existing players stay connected.
7. Disconnect Charlie. Confirm only its world character and bottom-left card clear; the other three players retain
   their identities and state. Retry the fifth client: it receives the freed slot with a new player UUID and uses
   the bottom-left corner. Further joins are rejected while four slots are occupied.
8. Observe item icons and their temporary speed, jump or shield effects when collected.
9. Disconnect each client and confirm its cards/names/stress clear. Stop the server and confirm responsive
   disconnection rather than freezing or crashing.

## Corner portraits and diagram compatibility

Slot 0 is top left, 1 top right, 2 bottom left and 3 bottom right. `GamePanel` draws cards after the world using
panel dimensions, so position and snapshot order do not move them. Stress remains the unmodified authoritative
value in its existing units, with no maximum, percentage or progress bar. World labels retain nickname/`(you)`;
world outlines, shields, NPC poses/latches and items are preserved.

All players currently render `assets/MARTY.png`; their cards therefore share its portrait. `AssetManager` privately
caches `MARTY_PORTRAIT` once using `cachePortrait`: the nontransparent source bounds' full width and upper 28%
frame the full face and shoulders. Replace `game-client/src/main/resources/assets/MARTY.png`, rebuild and restart
the client to change both world sprite and portrait. Adjust the `0.28` crop fraction in that existing helper call
for different framing. Alpha/aspect ratio are preserved and rendering uses nearest-neighbor scaling. Missing
portrait images use a simple silhouette. No avatar selection, new artwork, UI model class or transport field was added.

The supplied authoritative `class_after_strategy.png` has `0..*` GameSession-to-GameCharacter composition and
GameSession-to-Player association, with each player/character identity remaining `1` to `1`. Four instances fit
these multiplicities. The older generated `full-class-diagram-after-strategy.puml` and `.mmd` proposals explicitly
use `0..2` for GameCharacter and PlayerSnapshot and the earlier Enemy model; four players would violate those
older limits. This task follows the supplied PNG. All diagram files remain unchanged.

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

| Setting | Applies to | Default | Purpose |
| --- | --- | --- | --- |
| `-Dgame.server.port=<PORT>` | Direct server JVM | unset | Highest-precedence listening port |
| `SERVER_PORT` environment variable | Direct server JVM | `8080` | Listening port without the JVM property |
| `SERVER_PORT` in `.env` | Compose host | `8080` | Host port mapped to container port 8080 |
| Client address/first argument | Client | `ws://localhost:8080/game` | Configurable WebSocket endpoint |
| Nickname/second argument | Client | `Player` | Display label, 1–32 characters |

For a direct server on port 9090:

```sh
SERVER_PORT=9090 java -jar game-server/target/game-server.jar
java -jar game-client/target/game-client.jar ws://localhost:9090/game Alice
```

Alternatively use `java -Dgame.server.port=9090 -jar game-server/target/game-server.jar`. Direct Java execution
does not read `.env`; `.env` controls Compose's published host port.

## Protocol summary

The `/game` endpoint accepts JSON text messages with explicit stable types:

- `HELLO`: requests one of four slots with a nickname.
- `WELCOME`: returns connection UUID, server-assigned player UUID, slot, and nickname.
- `INPUT`: sends a monotonically sequenced complete left/right/jump state, never position or velocity.
- `WORLD_SNAPSHOT`: broadcasts arena/platform geometry and authoritative players (including stress), items, and NPC geometry/activity/facing/attack sequences at 20 Hz.
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
