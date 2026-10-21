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

- `game-domain` owns arena geometry, player/NPC bodies, Strategy selection/algorithms, stress damage, items/events, movement, gravity, and collision rules.
- `game-application` owns validated commands, retained input state, the bounded queue, and fixed-step loop.
- `game-protocol` owns explicit JSON DTOs only. It contains no domain entities or rules.
- `game-server` owns connections, four slot reservations, DTO/command translation, scheduling, and snapshots.
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
                                        │ players, items, NPC decisions/physics, damage
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

The server reserves at most four slots. `HELLO` becomes `JoinPlayerCommand`; accepted clients receive their server
assigned player UUID and slot in `WELCOME`. `INPUT` becomes `InputCommand`; clients cannot send coordinates,
velocity, or collision results. Disconnect becomes `LeavePlayerCommand`, freeing the slot for reconnection.
The first four joins receive unique player UUIDs and slots 0–3; a fifth receives `SERVER_FULL` without affecting
the match. Slot 0/1 spawns remain x=180/738; slots 2/3 spawn at x=60/858, clear of other initial player/NPC bodies.
Disconnect removes the correct character/target and frees its reservation; replacement uses that slot with a
fresh player UUID. `PlayerSlots`, retained input, target selection, queues and tick rates need no new structure.

Snapshots are immutable `ArenaSnapshot`, `PlatformSnapshot`, `PlayerSnapshot`, `ItemSnapshot`, and `NPCSnapshot`
records. Player snapshots include stress; NPC snapshots include precise geometry, validated activity strings,
authoritative facing and a monotonic attack-attempt sequence. The client holds only
the latest snapshot and renders it directly. Networking remains asynchronous, and all Swing changes occur on the
event dispatch thread.

## Corner portrait/stress HUD and diagram gate

The supplied authoritative `class_after_strategy.png` allows `GameSession` composition of `0..*` GameCharacters
and association with `0..*` Players; the Player-to-character identity remains `1` to `1`. Raising the implementation
limit to four fits these unchanged multiplicities. The older generated `full-class-diagram-after-strategy.puml`
and `.mmd` proposals say `0..2` for characters and player snapshot values and use the earlier Enemy proposal.
They conflict with the supplied PNG and cannot accommodate four instances. No diagram was edited or regenerated.

No production model class, public model signature, transport field, inheritance, realization or association was
added by the capacity/HUD change. Existing slot/count validation widens to 0–3/four. Protocol validation uses
local limits and imports no domain constants; application/server reuse the domain limit where permitted.

`GamePanel` privately paints all occupied player cards after world players/NPCs/items, using panel coordinates and
stable slot corners: 0 top left, 1 top right, 2 bottom left, 3 bottom right. Cards have four accent colors, an
aspect-preserving portrait, ellipsized nickname, current numeric stress, and a local white border/`(you)`. Width is
capped at 248 pixels and constrained by panel width; resize recomputes placement. Notifications draw beneath
the top cards. No child controls or input interception were introduced. Departures clear their corner on the next
snapshot; `clearGame` clears snapshot/local state and timers. Stress is absent from world labels and is not clamped
or treated as a percentage/health. Existing damage/shield/NPC behavior remains unchanged.

`AssetManager.cachePortrait` derives `PATASIJUS_PORTRAIT` once into its existing private images map. PATASIJUS is the actual
world sprite for every player. The crop scans transparent margins, retains nontransparent width and the upper
28% of opaque-bounds height, including face and shoulders. The `cachePortrait(..., 0.28)` call controls framing;
replacing `assets/PATASIJUS.png` and rebuilding/restarting changes both world image and portrait. No other character
portrait is silently substituted. The cached alpha image is read-only; Java2D preserves aspect and uses
nearest-neighbor scaling. Missing portraits draw a silhouette.

The four-player/HUD verification completed on standalone Temurin Java 25.0.4.1 with
`./mvnw -B -ntp clean verify`: 79 tests, zero failures/errors/skips, Enforcer and Checkstyle enabled.
Real WebSocket tests cover four independent players, fifth-player rejection and freed-slot replacement; domain
checks confirm NPC targeting of slots 2/3. EDT rendering tests cover fixed corner placement under movement and
snapshot reordering, stress updates, departures and full clearing at 960×540 and 640×360. Their
`game-client/target/player-hud-*.png` renders were visually inspected for portrait framing, long names, local
marking, card spacing and notification placement. The existing NPC main demonstration still produces its original
cycle. Production class/signature/record inventories and NPC report fragments were checked against the preserved
working-tree baseline.

## World and collision model

World origin `(0,0)` is the arena's top-left. X increases rightward and Y downward. Constants live in
`GameConstants`:

- Arena: `960 × 540` world units
- Ground: `(0,480)`, size `960 × 60`
- Raised platforms: `(140,380)`, `(660,380)`, and `(400,280)`, each `160 × 20`
- Player: `42 × 64`
- Fixed simulation: 60 ticks/second; snapshots: 20/second
- Horizontal speed: 260 units/second
- Jump speed: 600 units/second upward
- Gravity: 1500 units/second² downward

Each tick derives horizontal velocity from retained left/right state, applies a grounded jump edge, integrates
gravity, clamps horizontal and vertical arena boundaries, and resolves downward crossing of the platform top.
Platforms can be jumped through from below. The default arena lists the center platform first, then the two
lower platforms, then ground, so a downward crossing chooses the highest matching surface.
There is no client prediction or interpolation in this prototype.

`Platform` implements the domain `Prototype<Platform>` interface. Its public `clone()` creates a platform with
the same position and dimensions but a fresh UUID. `copyAt(Position)` clones the platform and changes only the
copy's position, leaving the original in place. Position changes are private to this operation. The default arena
uses one `160 × 20` template to create its three raised platforms with `copyAt(Position)`; the template itself
is not placed in the arena.

## NPC ownership and Strategy

`GameSession` privately owns `NPCs: Map<Integer, NPC>` and `NPCControllers: Map<Integer, NPCBehaviorController>`.
Spawn/removal maintains a matched pair per ID, and outward views are detached immutable records/lists. The default
session spawns two students. Numeric NPC IDs and numeric targets are distinct namespaces. A session-owned map
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

The sealed message vocabulary uses stable names: `HELLO`, `WELCOME`, `INPUT`, `WORLD_SNAPSHOT`, `PING`, `PONG`,
`GAME_EVENT`, and `ERROR`. Jackson uses an explicit subtype allowlist rather than Java class names. The codec
rejects unknown
types/properties, duplicate keys, trailing JSON, malformed records, null messages, and oversized text.

Nicknames are bounded display labels, not authentication. IDs and slots are server assigned. Binary messages and
paths other than `/game` are rejected. Required constructor fields and null primitive values are rejected.
NPC activity names are validated protocol strings, without importing the domain enum. Protocol errors are bounded
DTOs and never expose exceptions or domain objects.

## Lifecycle and deployment

Character and stat selection before joining use a client-local Command pattern. `UndoableCommand` declares
`execute()` and `undo()`; `SelectCharacterCommand` and `ChangeStatCommand` capture an immutable receiver snapshot
at execution time and restore it on undo. `CharacterSelection` is the receiver, initially `Hello.DEFAULT_CHARACTER`
with 100/100/100 stats, and accepts only `Hello.CHARACTERS`. Selecting another character resets all three stats;
undo restores the exact previous character and allocation. See the [UML class diagram](character-selection.md).
`CommandHistory` is the invoker: it pushes successfully executed commands onto a stack and undoes them in reverse
order. Empty-history undo is a no-op; clearing history retains the receiver's current choice.

`CharacterSelectionPanel` displays four Swing toggle cards using cached `<CHARACTER>_PORTRAIT` images from
`AssetManager`. Cards retain standard keyboard activation and show a selection border, a Selected label and a
keyboard-focus outline. Missing portraits retain a named placeholder. Three sliders edit maximum health,
movement speed, and jump height (50–150%, step 5, total at most 300). Unused points are allowed; increases are
clamped to the available budget. Values preview during a drag and commit once on release; keyboard adjustments
also commit. `ConnectionWindow` creates commands for actual changes and provides **Undo change**. The panel
communicates through callbacks and has no dependency on history. Its programmatic setters update cards and
sliders without emitting actions, so undo does not create commands. The receiver, history and
UI are owned by the EDT. Selection, sliders, and undo are disabled while connecting or connected. Receiving `WELCOME`
clears history on the EDT and makes the current choice the baseline; validation failures, connection failures and
cancelled attempts preserve history. Disconnecting allows new changes and undo back to that baseline. History is
per window and lives only for that window's lifetime. Connect reads the receiver's character and stat allocation
for `HELLO`. Its optional `stats` object defaults to 100/100/100 when omitted. The protocol requires exactly three
integer fields; `CharacterBuild` in the domain validates ranges, steps, and budget. The client mirrors these
constraints for feedback but has no domain dependency. The server translates the DTO before reserving a slot;
invalid builds return `INVALID_STATS` without queueing a join or consuming a reservation. `JoinPlayerCommand`
carries the validated build into the single-owner game loop.

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
renderer receives as `facesRight`; sprite art faces right and is mirrored for leftward attacks. Patasijus uses
32x32 sprites for both attacks, Vacijus for both attacks and Gogis for his projectile; the remaining attacks still
use drawn shapes.
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
`paskutinis-atsiskaitymas-server:local`, and host `SERVER_PORT` mapped to container port 8080.

## Extension points

Future slices can add more platform geometry to `Arena`, new application commands, versioned snapshot fields,
and client interpolation without changing transport ownership. Existing Singleton, three-product ItemFactory,
Observer delivery and platform Prototype remain intact. Player combat, projectiles, health/death, scoring,
multiple arenas, persistence, authentication, match results and unrelated patterns remain outside this slice.
