# Architecture

The project is a Java 25 multi-module Maven build with one headless authoritative server and one Swing desktop
process per player. Docker is only a server deployment boundary.

## Module boundaries

```text
game-client ──────────────────────────────> game-protocol
game-server ───> game-protocol
            ├──> game-application ───> game-domain
            └────────────────────────> game-domain
```

- `game-domain` owns arena geometry, player bodies, movement, gravity, and collision rules.
- `game-application` owns validated commands, retained input state, the bounded queue, and fixed-step loop.
- `game-protocol` owns explicit JSON DTOs only. It contains no domain entities or rules.
- `game-server` owns connections, two slot reservations, DTO/command translation, scheduling, and snapshots.
- `game-client` owns connection UI, keyboard state, Java2D rendering, and asynchronous network I/O.

Maven Enforcer checks project dependencies. Checkstyle rejects infrastructure imports in domain/application,
inward game-layer imports from protocol/client, and desktop imports from the server. Live domain entities are never
serialized; the server maps loop-owned state into detached protocol records.

## Authoritative command and snapshot flow

```text
Swing key press/release
        │
        ▼
complete INPUT state ──WebSocket──> MessageRouter
                                        │ validate direction, identity, sequence
                                        ▼
                              BoundedCommandQueue (512)
                                        │
                              one 60 Hz loop thread
                                        │ drain ≤128 commands/tick
                                        ▼
                                MatchController
                                        │ retained left/right + jump edge
                                        ▼
                                  GameSession
                                        │ movement, gravity, collision
                                        ▼
                     SnapshotMapper → WORLD_SNAPSHOT at 20 Hz
                                        │
                             WebSocket broadcast
                                        ▼
                       EDT update → GamePanel repaint
```

WebSocket callbacks may reserve/release transport slots and enqueue application commands. They never mutate the
world. `AuthoritativeGameLoop` is scheduled by one `authoritative-game-loop` executor. It drains commands before
advancing a fixed `1/60` second simulation step. Client sequence numbers discard delayed input, and a jump occurs
only on a false-to-true input transition while the character is grounded.

The server reserves at most two slots. `HELLO` becomes `JoinPlayerCommand`; accepted clients receive their server
assigned player UUID and slot in `WELCOME`. `INPUT` becomes `InputCommand`; clients cannot send coordinates,
velocity, or collision results. Disconnect becomes `LeavePlayerCommand`, freeing the slot for reconnection.
Additional join requests receive `SERVER_FULL` without affecting the match.

Snapshots are immutable `ArenaSnapshot`, `PlatformSnapshot`, and `PlayerSnapshot` records. The client holds only
the latest snapshot and renders it directly. Networking remains asynchronous, and all Swing changes occur on the
event dispatch thread.

## World and collision model

World origin `(0,0)` is the arena's top-left. X increases rightward and Y downward. Constants live in
`GameConstants`:

- Arena: `960 × 540` world units
- Ground: `(0,480)`, size `960 × 60`
- Player: `42 × 64`
- Fixed simulation: 60 ticks/second; snapshots: 20/second
- Horizontal speed: 260 units/second
- Jump speed: 600 units/second upward
- Gravity: 1500 units/second² downward

Each tick derives horizontal velocity from retained left/right state, applies a grounded jump edge, integrates
gravity, clamps horizontal and vertical arena boundaries, and resolves downward crossing of the platform top.
There is no client prediction or interpolation in this prototype.

## Protocol and validation

The sealed message vocabulary uses stable names: `HELLO`, `WELCOME`, `INPUT`, `WORLD_SNAPSHOT`, `PING`, `PONG`,
and `ERROR`. Jackson uses an explicit subtype allowlist rather than Java class names. The codec rejects unknown
types/properties, duplicate keys, trailing JSON, malformed records, null messages, and oversized text.

Nicknames are bounded display labels, not authentication. IDs and slots are server assigned. Binary messages and
paths other than `/game` are rejected. Protocol errors are bounded DTOs and never expose exceptions or domain
objects.

## Lifecycle and deployment

The server binds `0.0.0.0`. Its shutdown hook closes WebSocket connections and then terminates the scheduler;
tests verify the loop worker does not remain alive. Client disconnect cancels pending work, clears input/UI state,
and shuts down its HTTP client when the window closes.

The Maven Wrapper pins Maven 3.9.11. Compiler release, Enforcer, CI, Docker, VS Code, and documentation use Java
25. The multi-stage Dockerfile runs the full reactor verification and copies only the shaded server JAR into a
non-root Java 25 runtime. Compose uses project `paskutinis-atsiskaitymas`, service `game-server`, image
`paskutinis-atsiskaitymas-server:local`, and host `SERVER_PORT` mapped to container port 8080.

## Extension points

Future slices can add more platform geometry to `Arena`, new application commands, versioned snapshot fields,
and client interpolation without changing transport ownership. Combat, enemies, items, health, scoring, multiple
arenas, persistence, authentication, match results, and additional design patterns remain deliberately absent.
