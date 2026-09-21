# Paskutinis atsiskaitymas

Java 25 multiplayer 2D game prototype skeleton for a university Object-Oriented Programming Patterns project.
The repository currently provides module boundaries, a Swing connection window, and a typed JSON WebSocket
handshake. Connecting does not create a game session, player, character, arena, or other gameplay entity.

## Technology and architecture

- Java 25 and Maven Wrapper
- Swing and Java2D for the desktop client
- Java-WebSocket for the server and the JDK WebSocket client for the desktop
- Jackson JSON messages and JUnit 5 tests
- Docker for the headless server only

```text
game-client ──────────────────────────────> game-protocol
game-server ───> game-protocol
            ├──> game-application ───> game-domain
            └────────────────────────> game-domain
```

| Module | Responsibility |
| --- | --- |
| `game-domain` | Immutable game-model declarations with no infrastructure dependencies |
| `game-application` | Future game-loop, command-queue, and match-use-case boundaries |
| `game-protocol` | Transport DTOs and JSON serialization; no game rules or domain entities |
| `game-server` | Headless WebSocket endpoint, connection routing, configuration, and server entry point |
| `game-client` | Swing connection UI, asynchronous networking, and client entry point |

Server and client are separate programs because a headless server owns authoritative state while each player
runs an independent desktop UI. Keeping them in one repository gives both programs one protocol definition and
one verified build without coupling the client to server or domain implementation. See
[docs/architecture.md](docs/architecture.md) for the full boundary and threading rules.

## Prerequisites

For source builds and direct JAR execution:

- A standalone JDK 25, preferably Eclipse Temurin. Confirm both `java --version` and `javac --version` report 25.
- Internet access on the first build so the wrapper can download Maven and project dependencies.
- On macOS/Linux, `unzip` and either `curl` or `wget` for the Maven Wrapper.
- A desktop environment for the Swing client. The direct server is headless.

Docker server deployment requires Docker with Compose but does not require Java on the host. Docker is optional
for developers who run the server JAR directly. The project supports macOS, Windows, ARM64 Raspberry Pi OS, and
ordinary AMD64 Linux/cloud hosts. It does not require a particular IDE, package manager, or system Maven.

### VS Code

Open the repository root and install the recommended Microsoft Extension Pack for Java. The Docker extension is
recommended only for developers using Docker. Run **Java: Configure Java Runtime** from the Command Palette and
select a standalone JDK 25 for this workspace. Do not commit a local JDK path.

Shared tasks provide Maven verification, server packaging, and Docker Compose start/log/stop commands. Launch
configurations run the server, Alice, or Bob; the **Server + Alice + Bob** compound starts all three JVMs. Wait for
the server to report that it is listening before clicking **Connect** in either client window.

## Fresh clone and build

```sh
git clone <REPOSITORY_URL>
cd <REPOSITORY_DIRECTORY>
java --version
javac --version
./mvnw -version
./mvnw -B -ntp clean verify
```

On Windows, replace `./mvnw` with `mvnw.cmd`. The wrapper downloads its pinned Maven version, so do not install or
invoke a separate Maven. A successful verification checks the module dependency/import rules, compiles all five
modules, runs the JSON and WebSocket tests, and creates these self-contained executables:

- `game-server/target/game-server.jar`
- `game-client/target/game-client.jar`

To package only the server and its required modules:

```sh
./mvnw -B -ntp -pl game-server -am package
```

## Run directly with Java 25

Start the server from the repository root:

```sh
java -jar game-server/target/game-server.jar
```

The server binds to `0.0.0.0` and listens on port 8080 by default. Stop it with `Ctrl+C`.

Start one or more clients in separate terminals:

```sh
java -jar game-client/target/game-client.jar ws://localhost:8080/game Alice
java -jar game-client/target/game-client.jar ws://localhost:8080/game Bob
```

The arguments prefill the connection form; click **Connect** in each window. The fields can also be edited before
connecting. Closing a window releases that client's network resources.

## Run the server with Docker Compose

```sh
cp .env.example .env
docker compose up --build -d game-server
docker compose logs -f game-server
```

Press `Ctrl+C` to stop following logs. Run Swing clients on the host using Java 25; the client is not included in
the image. Stop and remove this project's Compose resources with:

```sh
docker compose down
```

Compose uses project `paskutinis-atsiskaitymas`, service `game-server`, and image
`paskutinis-atsiskaitymas-server:local`. It does not use a fixed container name, so Compose manages the instance.
The image runs headlessly as a non-root user and contains only the server JAR and Java 25 runtime.

## LAN and remote deployment

Local clients connect to `ws://localhost:8080/game`. For another computer on the same LAN:

1. Start the server on the host computer.
2. Find that host's LAN address using its operating-system network settings.
3. Allow inbound TCP port 8080 through the host firewall.
4. On a Java 25 desktop computer, run the client with the host address:

   ```sh
   java -jar game-client.jar ws://<SERVER_LAN_IP>:8080/game Bob
   ```

`localhost` always means the computer running the client, so LAN clients must use `<SERVER_LAN_IP>`.

The same server container can run on ARM64 Raspberry Pi OS or an AMD64 Linux/cloud host. Build or pull the image
for the host architecture, publish TCP port 8080, and retain the container's `SERVER_PORT=8080`. Raspberry Pi uses
the ARM64 image variant; typical cloud/Linux hosts use AMD64. No platform is hard-coded in the Dockerfile or
Compose file. Direct JAR deployment instead requires Java 25 on the host.

Public Internet deployment is not production-ready. Add TLS, authentication, firewall policy, and a reverse
proxy before exposing the service outside a trusted network.

## Configuration

| Setting | Applies to | Default | Purpose |
| --- | --- | --- | --- |
| `-Dgame.server.port=<PORT>` | Direct server JVM | unset | Highest-precedence listening port |
| `SERVER_PORT` environment variable | Direct server JVM | `8080` | Listening port when no JVM property is set |
| `SERVER_PORT` in `.env` | Docker Compose host | `8080` | Published host port mapped to container port 8080 |
| Client address field/first argument | Client | `ws://localhost:8080/game` | WebSocket server URL |
| Client nickname field/second argument | Client | `Player` | Nonblank display label, maximum 32 characters |

For a direct server on port 9090:

```sh
SERVER_PORT=9090 java -jar game-server/target/game-server.jar
java -jar game-client/target/game-client.jar ws://localhost:9090/game Alice
```

Alternatively use `java -Dgame.server.port=9090 -jar game-server/target/game-server.jar`. Direct Java execution
does not read `.env`; that file configures Compose's published host port.

## Current protocol

The endpoint is `/game`, and every application message is one JSON WebSocket text message. A client first sends:

```json
{"type":"HELLO","nickname":"Alice"}
```

The server replies with a transport-only connection identifier:

```json
{"type":"WELCOME","connectionId":"4d696088-fdd8-4264-9cbe-ffce4ab038cb","nickname":"Alice"}
```

After the handshake, `PING` receives a matching `PONG`. Malformed or unknown messages receive
`INVALID_MESSAGE`; premature messages receive `HANDSHAKE_REQUIRED`; repeated `HELLO` receives
`ALREADY_CONNECTED`; client-sent server DTOs receive `UNEXPECTED_MESSAGE`. Binary messages and paths other than
`/game` are rejected. Nicknames are not authenticated and do not create domain players.

## Limitations and next stage

There is no arena rendering, movement, physics, collision, combat, spawning, items, NPC behavior, persistence,
authentication, victory/reset logic, or active game loop. The next intended stage is to define arena coordinates
and geometry, then introduce application-owned commands, a bounded queue, and a single-owner authoritative loop
before adding snapshot DTOs and Java2D rendering.

## Troubleshooting

- **Wrong Java version:** if `java --version`, `javac --version`, or `./mvnw -version` does not report Java 25,
  select/install a standalone JDK 25 and update the shell `PATH` or VS Code's configured runtime. Do not use an
  IDE-bundled JRE as the project toolchain.
- **Port 8080 is occupied:** stop the other process or select another host port. For direct Java, set
  `SERVER_PORT`; for Compose, change `SERVER_PORT` in `.env`. Use the same port in the client URL.
- **LAN connection fails:** confirm the client uses the server host's LAN address, both machines can reach each
  other, the server is listening, and the firewall allows inbound TCP on the published port.
- **Docker fails:** confirm the Docker engine is running, then inspect `docker compose ps` and
  `docker compose logs game-server`. Rebuild with `docker compose build --no-cache game-server` if necessary.
- **Client remains disconnected:** confirm the address starts with `ws://` or `wss://`, includes `/game`, and
  points to the configured port. Check server logs for rejected paths or protocol errors.

Dependency and tool references: [Maven Wrapper](https://maven.apache.org/tools/wrapper/),
[Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket), and
[Jackson releases](https://github.com/FasterXML/jackson/wiki/Jackson-Releases).
