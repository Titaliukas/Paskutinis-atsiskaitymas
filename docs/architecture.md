# Architecture

The project is a Java 25 multi-module Maven build with two independently runnable processes: one headless,
authoritative server and one Swing desktop client per player. Docker is only a server deployment boundary.

## Module boundaries

Arrows represent compile/runtime project dependencies:

```text
game-client ──────────────────────────────> game-protocol
game-server ───> game-protocol
            ├──> game-application ───> game-domain
            └────────────────────────> game-domain
```

- `game-domain` contains immutable model declarations. It has no infrastructure dependency.
- `game-application` contains future use-case boundaries and depends only on domain.
- `game-protocol` contains explicit JSON wire DTOs and serialization, with no rules or domain entities.
- `game-server` owns WebSocket transport, connection state, routing, configuration, and startup.
- `game-client` owns Swing UI and asynchronous JDK WebSocket communication. It never owns authoritative rules.

Maven Enforcer checks project dependencies. Checkstyle checks production imports: domain and application exclude
AWT, networking, SQL, WebSocket, and Jackson; protocol/client cannot import inward game layers; server cannot
import client or desktop UI code. These rules must not be bypassed with reflection or fully qualified references.

## Current domain and protocol

The UML-oriented domain records declare `GameSession`, `Player`, `GameCharacter`, `Arena`, `Platform`, `Enemy`,
`Attack`, `Projectile`, `Item`, and finite immutable `Position` coordinates. Constructors enforce structural
validity and copy collections, but implement no gameplay. Coordinate units, geometry, and lifecycle rules remain
open for the next development stage.

The sealed protocol vocabulary is `HELLO`, `WELCOME`, `PING`, `PONG`, and `ERROR`. DTOs contain no domain types.
`JsonMessageCodec` uses explicit message names and rejects unknown types/properties, duplicate keys, trailing
JSON, nulls, malformed fields, and oversized input. Binary messages and WebSocket paths other than `/game` are
rejected.

`WELCOME` acknowledges a transport connection only. Its UUID is not a player ID, and a nickname is neither an
authenticated identity nor a domain player. Connecting never constructs a world object.

## Runtime and threading

```text
WebSocket callback → JsonMessageCodec → MessageRouter → connection response
                                            │
                                            └─ future: application command → CommandQueue
                                                                                  │
                                                                   GameLoop → MatchController
```

The server binds `0.0.0.0` and keeps only synchronized per-connection handshake state today. It logs connections
and disconnections, waits for binding to complete, and closes clients/workers through its shutdown hook.

Client networking uses the JDK's asynchronous WebSocket client. `ClientMessageRouter` dispatches typed server
messages; Swing state changes are scheduled on the event dispatch thread. Timeouts bound connection and handshake
attempts, fragmented input is size-limited, and late callbacks from prior connections are ignored. Network waits
never run on the Swing event dispatch thread.

When gameplay arrives, WebSocket callbacks may translate DTOs into application-owned commands and offer them to
a bounded, thread-safe `CommandQueue`. Only a single-owner `GameLoop` may drain commands and mutate authoritative
state through `MatchController`. Callbacks must never invoke gameplay behavior directly.

## Planned extension points

1. Define arena geometry and coordinate conventions in the domain.
2. Add concrete application commands and a bounded queue when the first use case requires them.
3. Implement scheduling and `GameLoop.tick()` on one authoritative owner thread.
4. Map application results into versioned snapshot DTOs instead of serializing live domain aggregates.
5. Add a Java2D rendering panel and separate input adapter in the client.
6. Add gameplay rules and design patterns only in response to concrete requirements.

No empty simulation, speculative hierarchy, predefined arena, or world creation is wired into the handshake.

## Build and deployment

The Maven Wrapper pins Maven 3.9.11 and verifies its distribution checksum. Java compiler release and Enforcer,
GitHub Actions, Docker build/runtime images, VS Code instructions, and team documentation all target Java 25.
Executable client and server JARs include their runtime dependencies.

The multi-stage Dockerfile verifies the full reactor, then copies only the server JAR into a non-root,
headless Java 25 runtime image. It remains portable across ARM64 and AMD64 and contains no host paths. Compose uses
the fixed project name `paskutinis-atsiskaitymas`, service `game-server`, image
`paskutinis-atsiskaitymas-server:local`, and a configurable published host port mapped to container port 8080.

`./mvnw -B -ntp clean verify` checks dependency/import boundaries, JSON round trips and invalid input, message
direction, configuration, simultaneous real WebSocket handshakes, fragmentation, reconnection, rejected paths and
binary frames, and clean shutdown. Tests bind ephemeral ports and release their clients and workers.
