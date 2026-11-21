# Architecture

The project is a Java 25 multi-module Maven build with one headless authoritative server and one Swing desktop
process per player. Docker is only a server deployment boundary.

Each server process supports multiple independent four-player lobbies. `ArenaSelection` chooses one arena
recipe, Combat (default) or Training, through `game.arena` / `GAME_ARENA`; every lobby uses that recipe.
`GameWebSocketServer` owns a `ServerLobbyMediator`, which manages membership, host permissions, and message
routing. Starting a lobby creates its own `GameRuntime` and loop-owned `GameSession`; waiting rooms do not
simulate a world. Arena switching remains unsupported. See [Mediator and lobbies](mediator-lobbies.md)
and [Builder and arenas](builder-arenas.md).

## Module boundaries

```text
game-client ──────────────────────────────> game-protocol
game-server ───> game-protocol
            ├──> game-application ───> game-domain
            └────────────────────────> game-domain
```

- `game-domain` owns arena geometry, player/NPC bodies, Strategy selection/algorithms, stress damage, items/events, movement, gravity, and collision rules.
- `game-application` owns validated commands, retained input state, the bounded queue, and fixed-step loop.
- `game-protocol` owns explicit JSON DTOs only. It contains no domain entities or rules.
- `game-server` owns the lobby mediator, connections, four slot reservations per lobby, DTO/command translation, scheduling, and snapshots.
- `game-client` owns connection UI, keyboard/controller state, Java2D rendering, and asynchronous network I/O.

Maven Enforcer checks project dependencies. Checkstyle rejects infrastructure imports in domain/application,
inward game-layer imports from protocol/client, and desktop imports from the server. Live domain entities are never
serialized; the server maps loop-owned state into detached protocol records.

## Authoritative command and snapshot flow

```text
Keyboard/controller input changes
        │
        ▼
complete INPUT state ──WebSocket──> MessageRouter → LobbyMediator
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
                                        │ players, items, NPC decisions/physics, damage
                                        ▼
                     SnapshotMapper → WORLD_SNAPSHOT at 20 Hz
                                        │
                             Lobby-scoped WebSocket delivery
                                        ▼
                       EDT update → GamePanel repaint
```

WebSocket callbacks may reserve/release transport slots and enqueue application commands. They never mutate the
world. `AuthoritativeGameLoop` is scheduled by one `authoritative-game-loop` executor per running lobby. It drains commands before
advancing a fixed `1/60` second simulation step. Client sequence numbers discard delayed input, and a jump occurs
only on a false-to-true input transition while the character is grounded.

Each lobby reserves at most four slots. `LIST_LOBBIES` exposes waiting rooms with space. `CREATE_LOBBY`
and `JOIN_LOBBY` reserve membership and return `WELCOME` followed by `LOBBY_STATE`. Once admitted, a
client sends `SELECT_CHARACTER` to confirm its build. The mediator retains each validated `JoinPlayerCommand`
until the host sends `START_GAME` and every member has confirmed. It queues the joins, publishes `RUNNING`,
and starts the runtime. World initialization, joins, inputs, and removals execute on that runtime's owner thread.

A fifth waiting-room join receives `LOBBY_FULL`. Guest departure frees a waiting slot; replacement gets a fresh
player UUID. Started lobbies reject every join with `LOBBY_STARTED`, including reconnects. A playing guest's
departure queues `LeavePlayerCommand`. Host departure closes the entire lobby and schedules runtime cleanup
outside callbacks and the mediator lock. Server shutdown waits for all cleanup workers. Slot 0/1 spawns remain
x=180/738; slots 2/3 remain x=60/858. No gameplay rules changed.

Snapshots are immutable `ArenaSnapshot`, `PlatformSnapshot`, `PlayerSnapshot`, `ItemSnapshot`, and `NPCSnapshot`
records. Player snapshots include stress; NPC snapshots include precise geometry, validated activity strings,
authoritative facing and a monotonic attack-attempt sequence. The client holds only
the latest snapshot and renders it directly. Networking remains asynchronous, and all Swing changes occur on the
event dispatch thread.

## Corner portrait/stress HUD

`GameConstants.MAX_PLAYERS` is 4. `PlayerSlots` reserves slots 0–3, and `GameSession` enforces the same
capacity. Both arena builders provide four spawn positions. Protocol validation keeps its own transport limits
without importing domain constants; application/server reuse the domain limit where permitted.

`GamePanel` privately paints all occupied player cards after world players/NPCs/items, using panel coordinates and
stable slot corners: 0 top left, 1 top right, 2 bottom left, 3 bottom right. Cards have four accent colors, an
aspect-preserving portrait, ellipsized nickname, current numeric stress, and a local white border/`(you)`. Width is
capped at 248 pixels and constrained by panel width; resize recomputes placement. Notifications are centered at arena point `(480,480)`,
using the world scale and offsets. No child controls or input interception were introduced. Departures clear their corner on the next
snapshot; `clearGame` clears snapshot/local state and timers. Stress is absent from world labels and is not clamped
or treated as a percentage/health. Existing damage/shield/NPC behavior remains unchanged.

`AssetManager` caches a separate square `<CHARACTER>_PORTRAIT` crop for each of PATASIJUS, VACIJUS,
JURGELINIS and GOGIS. Crop coordinates are configured per source sprite and computed once during cache
construction. `GamePanel` selects the portrait by `PlayerSnapshot.character`; world rendering uses that
character's renderer family through `CharacterFactories` and `CharacterSkin`. Java2D preserves portrait aspect
ratio and uses nearest-neighbor scaling. Missing portraits draw a silhouette.

`GameWebSocketServerTest` covers four independent players, fifth-player rejection and freed-slot replacement.
`ArenaSelectionTest` checks both startup recipes and configuration precedence. Run
`./mvnw -B -ntp clean verify` for the current reactor verification; test counts from earlier feature reports
are historical rather than a fixed project total.

## World and collision model

World origin `(0,0)` is the arena's top-left. X increases rightward and Y downward. Both arenas are 960×540;
players are 42×64; simulation runs at 60 Hz and snapshots at 20 Hz. Shared movement constants remain 260 units/s,
600 units/s upward jump speed and 1500 units/s² gravity. Geometry and four spawns belong to the selected
`Arena`, built by `ArenaDirector` with `CombatArenaBuilder` or `TrainingArenaBuilder`. The old prototype constants
remain for regression fixtures only; gameplay spawning no longer uses global `GROUND_Y`/`spawnX`.

Each tick derives horizontal velocity from input and resolves a grounded jump. Vertical displacement uses
`v*dt + g*dt²/2`, then updates velocity by `g*dt`. The previous semi-implicit formula reached only 115 units at
60 Hz, below the old v1 116-unit first step; constant-acceleration integration restores the intended 120-unit
jump without changing speeds, gravity or builds. Downward crossing selects the smallest matching platform top,
regardless of list order. Upward passage remains open; the building facade is not a wall.

`Arena.sidePassage` describes optional paired openings by top and support height. Player integration checks the
whole body, outward input and boundary crossing before horizontal clamp. Combat exits lead to X=918 or X=0;
normal landing resolves grounded on the opposite ground edge. The next step moves inward, avoiding immediate bounce.
Combat v2 uses a ground-level interval Y=343–421; Training has no passage. NPCs/projectiles/items retain their existing boundary rules. The server performs all transitions and publishes
positions normally. See [Builder and arenas](builder-arenas.md) for coordinates, rules and tests.

`Platform` is an abstract Template Method context. Its final `update(seconds, passengers)` saves
`previousPosition`, invokes protected `calculateNextPosition(seconds)`, refreshes collision bounds, and
moves passengers by the resulting displacement. `update(seconds)` delegates with no passengers. Concrete
`StationaryPlatform`, `HorizontalPlatform`, and `VerticalPlatform` provide their next positions; a private-to-domain
`PlatformMotion` helper shares scalar endpoint reversal arithmetic. Positive finite speed/duration and distinct
finite endpoints are required; `Arena` validates the entire path, not just its initial placement.

Before any platform moves, `GameSession` assigns each grounded body to the first overlapping support in arena
order, using a 1e-6 feet-to-top tolerance. Jump requests are excluded. The domain-only `PlatformPassenger`
interface exposes body geometry and displacement for both `GameCharacter` and `NPC`. Platforms move once on
the owner thread before character/NPC integration. Landing checks compare old and new platform tops;
already-carried bodies instead start against the updated top. One-way upward passage, world clamps and
NPC decision/patrol rules remain intact. No platform velocity is added to a jumping body's own velocity.
Support is recomputed every tick, so walking off, impulses, respawn and removal do not retain old attachments.

`Platform` also implements `Prototype<Platform>`: `clone()` assigns a fresh UUID and preserves concrete type,
path and motion phase. `copyAt(Position)` translates both path endpoints and retains phase/direction.
Copies reset previous geometry to the copied current position and have no passengers. `GameSession` builds
its own arena container with cloned platforms, avoiding shared motion between sessions using one recipe.
Arena lists and spawn descriptions remain immutable; session-owned platforms are mutable.

Combat appends a 120×10 horizontal platform from (360,130) to (540,130) at 60 units/s and a vertical one
from (760,350) to (760,280) at 40 units/s, for six surfaces total. Training appends the same sizes/speeds,
from (160,350) to (400,350) and (640,360) to (640,250), for three surfaces total. Existing surfaces stay stationary.
Both moving types use the visible SERVICE style. Circular motion, crushing and endpoint pauses are outside this slice.

The client receives validated `ArenaSnapshot.arenaType` (`combat` or `training`) and uses cached v2 background images. `PlatformSnapshot.kind` identifies visible service-style platforms and
`ArenaSnapshot.sidePassage` carries optional transport-only opening geometry.
A single centered arena rectangle and scale map image and world geometry together, preserving black letterboxing.
Nearest-neighbor background scaling retains pixel-art edges. Normal mode omits collision fills when art exists;
`-Dgame.debug.geometry=true` enables thin outlines. Missing art keeps the sky/platform fallback. The four HUD cards
still use panel coordinates and render after the world.

## NPC ownership and Strategy

`GameSession` privately owns `NPCs: Map<Integer, NPC>` and `NPCControllers: Map<Integer, NPCBehaviorController>`.
Spawn/removal maintains a matched pair per ID, and outward views are detached immutable records/lists. Combat spawns one student on its roof and one on ground; Training has no NPCs.
`GameSession` consumes immutable `NPCSpawn` recipes through its existing `spawnNPC`/Strategy construction. Numeric NPC IDs and numeric targets are distinct namespaces. A session-owned map
connects monotonic target IDs to existing character UUIDs; disconnect clears the map entry and stale controller
references. Player/network UUIDs remain unchanged.

After player movement and item collection, each tick calls controller `update`, then NPC `advance`. The controller
selects from a per-NPC map of Patrol/Chase/Attack/Flee strategies and replaces the context's active interface
reference. NPC `advance` delegates to `decide` and integrates the returned velocity with gravity/collision. The
strategies see immutable self/target snapshots, never unrestricted mutable world access. The session revalidates
attack requests against current precise geometry and cooldown, records accepted attempts including shield blocks,
and applies `GameCharacter.takeDamage`. A successful hit increases stress and switches the NPC to FLEE; a shield
block leaves it in ATTACK until cooldown expires or the target leaves range. No NPC threads or client decisions exist.

NPC integer UML coordinates are `Math.round` views over private doubles. Physics and range checks use doubles,
and poses never change the fixed 42×64 hitbox. The client uses `NPCPresentation` as its sole pose/style mapping,
selects the four static student images through `AssetManager`, and mirrors with server facing. FLEE faces the
threat while moving away. A sequence change latches the ATTACK image for 180 ms; the existing Swing notification
repaint timer handles expiry and stops on disconnect/removal. See [strategy-npc.md](strategy-npc.md) for the
complete contract, report markers, transition rules and demo.

## Protocol and validation

The sealed message vocabulary uses stable names: `LIST_LOBBIES`, `LOBBY_LIST`, `CREATE_LOBBY`, `JOIN_LOBBY`, `WELCOME`,
`SELECT_CHARACTER`, `LEAVE_LOBBY`, `LOBBY_STATE`,
`START_GAME`, `LOBBY_CLOSED`, `INPUT`, `WORLD_SNAPSHOT`, `PING`, `PONG`, `GAME_EVENT`, and `ERROR`. Jackson uses an explicit subtype allowlist rather than Java class names. The codec
rejects unknown
types/properties, duplicate keys, trailing JSON, malformed records, null messages, and oversized text.

Nicknames are bounded display labels, not authentication. IDs and slots are server assigned. Binary messages and
paths other than `/game` are rejected. Required constructor fields and null primitive values are rejected.
NPC activity names are validated protocol strings, without importing the domain enum. Protocol errors are bounded
DTOs and never expose exceptions or domain objects.

## Lifecycle and deployment

The client-side Facade is `GameClientFacade`. Its two clients, `ConnectionWindow` and the windowless
`GameClientFacadeDemo`, each own a separate facade with one `CharacterSelection`, one `CommandHistory`, and one
`ClientConnection`. No subsystem depends on the facade type or is exposed through a getter. The facade executes
the existing commands through history, reads the build for `SELECT_CHARACTER`, and delegates transport/input/observer
registration to the connection. Rendering, input-device choice, SDL, sizing and focus remain in the window.
See the [demo commands](../README.md#facade-demonstration-without-a-game-window).

Facade operations and observer delivery use the EDT, including in the windowless demo. Network callbacks only
enqueue EDT work, without waiting for it. The facade clears history when server confirmation moves the client from the picker to the waiting room. A facade generation guards every queued event; lifecycle operations advance it under the connection
monitor, so an old callback cannot capture a new generation. This supplements the transport's existing generation
checks and prevents cancelled/previous sessions from changing history or UI. Client observers update the UI
directly on the EDT, without a second unguarded dispatch. Repeated identical connection statuses (for example,
closing after disconnecting) are delivered once per observer.

Character and stat selection after admission use a client-local Command pattern. `UndoableCommand` declares
`execute()` and `undo()`; `SelectCharacterCommand` and `ChangeStatCommand` capture an immutable receiver snapshot
at execution time and restore it on undo. `CharacterSelection` is the receiver, initially `SelectCharacter.DEFAULT_CHARACTER`
with 100/100/100 stats, and accepts only `SelectCharacter.CHARACTERS`. Selecting another character resets all three stats;
undo restores the exact previous character and allocation. See the [UML class diagram](character-selection.md).
`CommandHistory` is the invoker: it pushes successfully executed commands onto a stack and undoes them in reverse
order. Empty-history undo is a no-op; clearing history retains the receiver's current choice.

`CharacterSelectionPanel` displays four Swing toggle cards using cached `<CHARACTER>_PORTRAIT` images from
`AssetManager`. Cards retain standard keyboard activation and show a selection border, a Selected label and a
keyboard-focus outline. Missing portraits retain a named placeholder. Three sliders edit maximum health,
movement speed, and jump height (50–150%, step 5, total at most 300). Unused points are allowed; increases are
clamped to the available budget. Values preview during a drag and commit once on release; keyboard adjustments
also commit. `ConnectionWindow` requests changes through its facade and provides **Undo change**. The panel
communicates through callbacks and has no dependency on history. Its programmatic setters update cards and
sliders without emitting actions, so undo does not create commands. The receiver, history and
UI are owned by the EDT. The picker accepts edits after admission until the server confirms the build.
The `SELECT_CHARACTER` payload carries the character and all three stat percentages. `CharacterBuild`
validates ranges, steps, and budget in the domain. An invalid confirmation returns `INVALID_STATS`
and lets the player retry in the same slot. Confirmation keeps the selected build as the local baseline.
A room departure returns to the browser on the same WebSocket.

`GameCharacter` retains the permanent build separately from the timed `CharacterStats` decorator chain. Health
and speed scale directly; jump velocity uses the square root of the height multiplier. Existing defaults are
currently common to all character types. Respawn restores the build's maximum health and clears temporary
effects while preserving permanent allocations. Domain snapshots carry actual maximum health, mapped into the
existing `PlayerSnapshot.maxHealth` field. Attack loadouts are unaffected.

A `CardLayout` switches between the picker and the same `GamePanel` instance, preserving its keyboard adapter.
The picker remains visible and disabled while connecting; a successful connection shows and focuses the game
panel, which displays Waiting for arena until the first snapshot arrives. Disconnects and errors return to the
picker. Connection status callbacks already on the EDT update controls immediately; worker callbacks are
dispatched onto the EDT.

For the Adapter pattern, the client reads every input device through the `InputSource` target (`currentInput`,
`setListener`, `reset`, `close`), which yields the complete `PlayerInput` state. `KeyboardInputAdapter` adapts Swing
`KeyListener` events. `GamepadInputAdapter` adapts Jamepad's polling `ControllerManager`: a 16 ms Swing timer polls
on the EDT and publishes only changes. `XboxInputAdapter`, `PlayStationInputAdapter` and `EightBitDoInputAdapter` choose their controller by
the name SDL reports and map SDL-normalized button positions. Their differences are detection and control hints;
polling and lifecycle are inherited. The bundled `gamecontrollerdb.txt` adds a macOS mapping for the
8BitDo Ultimate 2C Wireless over Bluetooth. `GamepadReading` copies the used Jamepad fields so the
mapping is testable without native SDL. `InputDevice` backs the connection form's Input dropdown and is the single
place that creates an adapter. `ConnectionWindow` swaps adapters by resetting and closing the old one; nothing else
in the client changes. The shared `ControllerManager` is initialised once, guarded so a missing native library
leaves only the keyboard, and shut down when the window closes. The client manifest sets
`Enable-Native-Access: ALL-UNNAMED` for SDL's JNI library. The server does not depend on Jamepad.

The client uses an `AssetManager` singleton to load bundled character sprites and item icons once per client JVM.
Startup initializes it before creating the Swing UI. `GamePanel` uses its images for rendering, while
`ConnectionWindow` uses the same cached `PATASIJUS` image for its window icon. The cache is private and immutable
after construction; callers treat the images as read-only. Missing images retain the renderer's primitive-shape
fallback and the window's default icon.

For the Singleton pattern demonstration, `AssetManager` has a private constructor, one `static final INSTANCE`,
and a public `getInstance()` accessor. Both UI consumers obtain the same manager and reuse the same image objects.
`AssetManagerTest` verifies manager identity, cached image identity, and successful loading of all bundled images.

For the Abstract Factory pattern, the client's `CharacterFactory` creates one character's matching renderer family:
a `BodyRenderer`, a `ProjectileRenderer` and a `MeleeRenderer`. `PatasijusFactory`, `VacijusFactory`,
`JurgelinisFactory` and `GogisFactory` each create their own three products. `CharacterFactories` maps the wire
character name to its factory, and `CharacterSkin.from` builds the whole family at once so a player's body, projectile
and melee swing always match. `GamePanel` depends only on the interfaces. Attack snapshots carry the attacker's
`sourceId`, which `GamePanel` resolves to that player's character; unknown characters and attacks whose player has
left keep the primitive-shape fallback. They also carry the attack's `direction` (-1 left, 1 right), which every
renderer receives as `facesRight`; sprite art faces right and is mirrored for leftward attacks. Every character uses
32x32 sprites (`<CHARACTER>_PROJECTILE`, `<CHARACTER>_MELEE`) for both attacks; the drawn shapes remain only as a
fallback when an image is missing. Jurgelinis's projectile contains text, so it is never mirrored.
Presentation only: no game rule depends on the skin.

For the Decorator pattern, a domain `GameCharacter` keeps its timed effects as one `CharacterStats` chain. The chain
ends in the shared `BaseStats.INSTANCE`, and each effect wraps it in a `StatsDecorator` layer: `SpeedBoostDecorator`,
`SlowDecorator`, `JumpBoostDecorator` or `ShieldDecorator`. A layer changes only its own value and delegates the rest,
so a speed boost and a slow multiply instead of overwriting each other. Every tick, `tick` counts the timers down and
expired layers remove themselves. Re-applying an effect first removes the old layer of that type (`without`), which
refreshes its timer instead of stacking it, and respawn resets the chain to `BaseStats`. Items and `SlowEffect` only
call `applySpeedBoost`, `applySlow`, `applyJumpBoost` or `applyShield`. `CharacterStatsDecoratorTest` covers three-
and four-level chains, for example `Slow(Shield(JumpBoost(SpeedBoost(Base))))`.

The server binds `0.0.0.0`. Its shutdown hook closes WebSocket connections and then terminates the scheduler;
tests verify the loop worker does not remain alive. Client disconnect cancels pending work, clears input/UI state,
and shuts down its HTTP client when the window closes.

The Maven Wrapper pins Maven 3.9.11. Compiler release, Enforcer, CI, Docker, VS Code, and documentation use Java
25. The multi-stage Dockerfile runs the full reactor verification and copies only the shaded server JAR into a
non-root Java 25 runtime. Compose uses project `paskutinis-atsiskaitymas`, service `game-server`, image
`paskutinis-atsiskaitymas-server:local`, host `SERVER_PORT` mapped to container port 8080, and `GAME_ARENA` defaulting
to `combat`. `ArenaSelection` is the single startup switch; its selected product reaches GameRuntime/GameSession
before the owner loop starts. No live world state is changed by WebSocket callbacks.

## Extension points

Future slices can add more platform geometry to `Arena`, new application commands, versioned snapshot fields,
and client interpolation without changing transport ownership. Existing Singleton, ItemFactory, Observer, platform Prototype and Template Method, Strategy, Adapter, Abstract Factory, Decorator and
Command and Facade behavior remain intact. Player combat, projectiles and respawn are already implemented. Scoring,
persistence, authentication, match results, runtime arena switching, lobbies/rooms and unrelated patterns remain outside this slice.
