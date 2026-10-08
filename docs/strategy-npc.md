# NPC Strategy implementation and report guide

## Sources, scope and actual baseline

The authoritative target is the NPC fragment of the supplied MagicDraw `class_after_strategy.png`. The module
requirements `D02_Reikalavimai-projektui-v2025-v05.2.pdf` (2025-09-08, v5.2), §§2.9, 2.10 and 3.10.d, require
problem/justification, before/after UML, essential code, a working main demonstration, and at least four concrete
Strategy classes. The inspected lecture `04_Strategy_Observer_2026_v10_6.pdf`, slides 9, 11 and 14–17, explains
interchangeable algorithms, runtime replacement and the separate responsibility for selecting a strategy.
All three references were inspected and remain unmodified. The source `output/npc/` directory contained no PNGs;
the four supplied matching poses were already present as untracked files in the repository's asset directory
and have been preserved byte-for-byte. No new artwork was generated.

This implementation supplies **four separate concrete classes** directly implementing `NPCActivityStrategy`:
`PatrolStrategy`, `ChaseStrategy`, `AttackStrategy`, `FleeStrategy`. It addresses these Strategy requirements,
not the entire module assignment, its other patterns or its assessment/report-submission process.

Before this task, `Enemy` was an unused record containing only UUID `id` and `Position position`. No NPCs spawned,
no behaviors existed, and snapshots/rendering omitted them. `GameSession` already handled players, item spawning,
collection and Observer events. There was no giant pre-existing conditional enemy algorithm. A hypothetical
conditional implementation would combine movement algorithms with the entity; that is a design comparison,
not a claim about the baseline.

Strategy fits because the **same NPC** needs four interchangeable movement/attack decisions. The NPC retains
identity, body, precise position and cooldown when the controller replaces its interface reference. This avoids
four NPC subclasses and keeps algorithm changes separate from authoritative integration and attack resolution.
The controller's activity switch decides **when** to install an algorithm; each concrete Strategy decides **how**
to act. The client mapping selects artwork only and is not a Strategy implementation of gameplay.

## Before and after structural sketches

These sketches describe executable repository structure and supplement the supplied MagicDraw image; they do
not replace or revise that authoritative diagram. Use the original image in the report and explain the signature
completions and additions below rather than editing the image to conceal differences.

Before:

```text
Enemy(UUID id, Position position)       [unused, no session/renderer relationships]
GameSession -> GameCharacter           [player movement, item collection and events]
SnapshotMapper -> WorldSnapshot        [players + items]
GamePanel -> AssetManager              [player/item images]
```

After:

```text
GameSession *-- 0..* NPC                     [NPCs, keyed by Integer NPC ID]
GameSession *-- 0..* NPCBehaviorController   [NPCControllers, matched 1:1 by NPC ID]
NPCBehaviorController --> NPC              [selection installs strategy via setter]
NPCBehaviorController --> Map<NPCActivity, NPCActivityStrategy>
NPC --> 1 NPCActivityStrategy              [one active reference, polymorphic decide]
PatrolStrategy ..|> NPCActivityStrategy
ChaseStrategy  ..|> NPCActivityStrategy
AttackStrategy ..|> NPCActivityStrategy
FleeStrategy   ..|> NPCActivityStrategy
NPC/strategies --> NPCState, NPCTarget      [immutable decision inputs]
NPC/strategies --> NPCAction                [immutable velocity and optional request]
GameSession --> GameCharacter.takeDamage   [authoritative revalidation/resolution]
SnapshotMapper --> NPCSnapshot --> GamePanel/NPCPresentation --> AssetManager
```

## Participants and lifecycle

| Participant | Repository responsibility |
| --- | --- |
| `NPC` (Context) | Holds one strategy; cooldown, precise integration, collision, facing and detached state |
| `NPCActivityStrategy` (Strategy) | Shared typed `decide` contract, no mutable world access |
| Four concrete strategies | Patrol reversal, chase motion, attack eligibility, flee motion |
| `NPCBehaviorController` (selection client) | Detection/target selection and simulation-time transitions; installs strategy |
| `GameSession` | Composition owner, stable targets, ordered ticks, authoritative attack resolution |
| `NPCState` / `NPCAction` | Immutable decision snapshot/result |
| `NPCTarget` / `NPCView` | Minimal extra detached target geometry and state/activity export |
| `NPCGeometry` | Finite-value validation, matching, precise rectangle distance and small shared math |

Spawning creates one NPC, one controller and a complete strategy set before inserting either map entry. Each
controller has its own patrol instance/direction and target/timers. Removing an NPC retires it and removes both
entries. Entities/controllers never escape the session through getters: `npcs()`, `npcTargets()` and
`npcControllerIds()` return immutable detached collections. The owner thread controls all mutations. No NPC
threads, sleeps, wall-clock simulation timers, new modules or infrastructure dependencies were introduced.

## Transition rules and gameplay decisions

Distances are shortest Euclidean gaps between precise axis-aligned body rectangles, in world units. Overlap has
distance zero. Target eligibility means an actual currently connected player character; there is no health/death
model. Ties select the lower numeric target ID. Detection is nearest-player selection within radius; switching
candidate or losing all candidates resets continuous detection progress.

| Activity | Decision and exit rule |
| --- | --- |
| PATROL | Move between configured left-edge boundaries, reverse and cap travel at boundaries. With no players, patrol forever. Continuous detection for `detectionSeconds` installs CHASE. |
| CHASE | Move horizontally toward selected target at `speed × speedMultiplier`. Enter ATTACK at `attackRange`; target loss or `maxChaseSeconds` returns to PATROL. Timeout has precedence at a simultaneous boundary. |
| ATTACK | Stop horizontally, preserve vertical physics, request attack only for matched target in range with cooldown ready. Lost range returns to CHASE; lost target returns to PATROL. |
| FLEE | Move away at flee multiplier, keep facing target. Stop at `safeDistance` or `maxFleeSeconds`; target loss also returns to PATROL. Fresh detection is then required. |

`detectionSeconds` is required **continuous detection duration in simulation seconds**. Extra `detectionElapsed`
accumulates it. Despite its UML name, `timeDistance` is **elapsed simulation seconds in the current activity**,
reset on every replacement; it enforces chase/flee timeouts. Radius/range/safe distance are world units. A 1e-9
second tolerance handles floating-point timer boundaries. At coincident horizontal centers CHASE stops, FLEE
moves opposite retained facing, and facing is retained until a nonzero direction appears. Arena edges clamp motion;
flee timeout prevents an NPC remaining stuck at an edge. After fleeing beyond the patrol interval, PATROL moves
back toward its interval before resuming boundary reversal; it never teleports the body.

Per tick: move players/decay effects, spawn/collect items and publish existing events, update controllers,
delegate NPC decisions and integrate gravity/collision, resolve requests and feed results to controllers. The
resolver checks current target, activity, liveness, precise range and readiness. It calls `recordAttackAttempt()`
exactly once for each accepted in-range request, including shield blocks. That starts cooldown and increments the
monotonic sequence. Its Boolean result means **player state changed**; false includes rejected requests and blocks.
Only true triggers FLEE. A blocked NPC stays in ATTACK and retries after cooldown, never damages every tick.

`GameCharacter.takeDamage(double damage)` increases `stressLevel` on an unshielded hit, returns false without
change when shielded, and rejects nonpositive/nonfinite damage. Success requires an actual representable stress
increase; a sub-precision increment returns false rather than reporting a hit without state change. Stress is an
explicit gameplay interpretation of the diagram, visible in detached player snapshots and corner cards. No health, death, respawn, salary, player combat,
projectiles, score or persistence was added. NPC attacks cannot collect items or alter their strategies on clients.

Default configuration in `GameConstants` and `GameSession.createDefault()`:

| Setting | Value |
| --- | --- |
| NPCs / initial left X | 1 at 360, 2 at 580; upright hitbox 42×64, feet at ground Y=480 |
| Patrol left-edge intervals | NPC 1: [300,420]; NPC 2: [530,650] |
| Speed / chase multiplier / flee multiplier | 95 units/s / 1.5 / 1.8 (player speed is 260) |
| Detection radius / duration | 200 units / 0.6 seconds |
| Attack range / damage / cooldown | 18 units / 10 stress / 1.2 seconds |
| Safe distance / max chase / max flee | 170 units / 4 seconds / 1.5 seconds |

Players can run faster than NPCs, jump out of range, and use existing shields. Detection delay and fleeing leave
room to observe the cycle rather than immediately trapping the player. The one existing arena/platform remains
unchanged. A later capacity/HUD task widens the player limit to four under the supplied PNG's unchanged `0..*` multiplicities; it does not alter the NPC algorithms or R1–R5 markers.

## UML-to-code audit: attributes

All named NPC participants are in `lt.ktu.paskutinisatsiskaitymas.domain`. Private fields keep exact diagram names,
including `targetID`, `position_x`, `position_y`, `NPCs` and `NPCControllers`. Record components produce private
final fields and typed accessors. Primitive Java `boolean` implements nonnullable UML Boolean.

| Owner | UML attribute(s) | Actual code and correspondence |
| --- | --- | --- |
| NPC | `id: Integer` | Private final Integer `id`; positive NPC identity |
| NPC | `targetID: Integer` | Private nullable Integer `targetID`; absent means no current player target |
| NPC | `position_x: Integer`, `position_y: Integer` | Exact private Integer fields; rounded precise position |
| NPC | `activityStrategy: NPCActivityStrategy` | Exact private interface reference; one nonnull active algorithm |
| NPC | `attackRange: double`, `attackDamage: double` | Exact private final doubles; world units / stress |
| NPC | `attackCooldownRemaining: double` | Exact private double; seconds remaining, decayed each tick |
| NPCState | `id: Integer`, `x: Integer`, `y: Integer` | Exact immutable components; identity and rounded state |
| NPCState | `width: double`, `height: double`, `speed: double` | Exact components from actual body configuration |
| NPCState | `velocityX: double`, `velocityY: double` | Exact components from integrated velocities |
| NPCState | `alive: Boolean` | Primitive boolean component; live until session removal/retirement |
| NPCState | `targetId: Integer` | Exact nullable Integer component copied from NPC `targetID` |
| NPCState | `attackRange: double`, `attackReady: Boolean` | Actual range and boolean `attackCooldownRemaining == 0` |
| NPCAction | `velocityX: double`, `velocityY: double` | Exact finite intended velocity components |
| NPCAction | `attackTargetId: Integer` | Exact nullable Integer; null means no attack |
| PatrolStrategy | `leftBoundary: double`, `rightBoundary: double` | Exact private final fields; left-edge bounds |
| ChaseStrategy | `speedMultiplier: double` | Exact private final field |
| AttackStrategy | No attributes required | No algorithm state added |
| FleeStrategy | `speedMultiplier: double` | Exact private final field |
| NPCActivity | PATROL, CHASE, ATTACK, FLEE | Exactly these four enum literals |
| NPCBehaviorController | `activity: NPCActivity` | Exact private field; current installed algorithm key |
| NPCBehaviorController | `targetId: Integer` | Exact nullable private field; per-NPC candidate/current target |
| NPCBehaviorController | `detectionRadius: double`, `detectionSeconds: double` | Exact private fields; units and timing defined above |
| NPCBehaviorController | `timeDistance: double` | Exact private field; activity elapsed time in seconds |
| NPCBehaviorController | `safeDistance: double` | Exact private field; gap ending flee |
| NPCBehaviorController | `maxChaseSeconds: double`, `maxFleeSeconds: double` | Exact private fields used in exit conditions |
| NPCBehaviorController | `strategies: Map<NPCActivity, NPCActivityStrategy>` | Exact private map; complete immutable per-NPC set |
| GameSession | `NPCs: Map<Integer, NPC>` | Exact private final map; keys are NPC IDs |
| GameSession | `NPCControllers: Map<Integer, NPCBehaviorController>` | Exact private final map with same key set |

Necessary state within the NPC ellipsis: `preciseX`, `preciseY` are private doubles; `width`, `height`, `speed`,
`velocityX`, `velocityY` support actual integration; `grounded`, `alive` track body/lifecycle; `attackCooldownSeconds`
sets cooldown duration; `facingRight`, `facingTarget` support authoritative orientation; `attackAttemptSequence`
records accepted attempts. Integer views update with `(int) Math.round(preciseCoordinate)` after integration
and at construction; nonnegative coordinates and NPC arena dimensions must fit Integer. Rounding has at most 0.5 unit error and never
feeds back into motion or collision. `NPCState` adds precise coordinates, grounded/facing and sequence for detached
mapping. Validation rejects invalid IDs, nonfinite state/actions, impossible dimensions and invalid delta time.

The controller adds only `npc` (owned context reference) and `detectionElapsed`. Patrol adds per-instance
`movingRight`. The session adds `targetCharacters: Map<Integer, UUID>` and `nextTargetId`. Numeric targets map to
actual character UUIDs, not UUID hashes/truncation, and allocate monotonically without disconnect reuse.
A long allocation counter refuses further joins after the positive Integer namespace is exhausted. Removal
clears stale target references immediately. NPC IDs and player-target IDs are separate namespaces.

## UML-to-code audit: operations and relationships

The diagram leaves parameter lists incomplete. The completed public signatures below retain operation names and
return types. No meaningless no-argument overloads were introduced. Primitive return boolean represents UML Boolean.

| Owner / UML operation | Completed Java signature | Why inputs are needed |
| --- | --- | --- |
| NPC.setActivityStrategy : void | `void setActivityStrategy(NPCActivityStrategy strategy)` | Assign the interface implementation |
| NPC.advance : NPCAction | `NPCAction advance(Arena arena, NPCTarget target, double seconds)` | Delegate snapshot decision, then integrate against geometry/time |
| NPC.recordAttackAttempt : void | `void recordAttackAttempt()` | Genuine no-argument operation uses configured cooldown; records one accepted attempt |
| NPC.state : NPCState | `NPCState state()` | Detached immutable actual state |
| NPCAction.hasAttackRequest : Boolean | `boolean hasAttackRequest()` | Tests nullable target representation |
| NPCActivityStrategy.decide : NPCAction | `NPCAction decide(NPCState self, NPCTarget target, double seconds)` | Immutable self/optional target and tick duration |
| PatrolStrategy.decide : NPCAction | Same `decide` signature | Self position, speed and delta determine safe reversal/travel |
| ChaseStrategy.decide : NPCAction | Same `decide` signature | Determine direction from target geometry |
| AttackStrategy.decide : NPCAction | Same `decide` signature | Match target, range and cooldown before request |
| FleeStrategy.decide : NPCAction | Same `decide` signature | Direction away from target, including coincidence fallback |
| NPCBehaviorController.update : void | `void update(List<NPCTarget> targets, double seconds)` | Detect eligible targets and advance transition timers |
| NPCBehaviorController.onAttackResolved : void | `void onAttackResolved(boolean damaged)` | Flee only after actual state change |
| NPCBehaviorController.switchActivity : void | `void switchActivity(NPCActivity next)` | Select and install concrete strategy |
| GameSession.advance : void | `void advance(Map<UUID, MovementInput> inputByPlayer, double seconds)` | Existing signature preserved, extended owner tick |
| GameSession.resolveNPCAction : Boolean | `boolean resolveNPCAction(Integer npcId, NPCAction action)` | Identify owned attacker and revalidate optional request |
| GameCharacter.takeDamage : Boolean | `boolean takeDamage(double damage)` | Minimal actual shield-aware stress damage |

| Target relationship | Implementation evidence |
| --- | --- |
| NPC has one active Strategy through interface | Private nonnull `activityStrategy`; setter and `advance` delegation; no NPC behavior switch/instanceof |
| Four direct interface realizations | Each concrete class declares `implements NPCActivityStrategy` independently |
| NPC depends on state/action/strategy | `state()` creates NPCState; `advance` calls interface `decide` and returns NPCAction |
| Strategies depend on state and produce action | Typed inputs/results; no mutable NPC/character/session access |
| Controller depends on NPC and abstractions | Private context reference and interface-valued map; setter installs map selection |
| Session composition owns zero or more NPCs/controllers | Both exact private maps, atomic pair insertion after validation, matched removal, detached outward views |
| One controller per NPC | Same Integer key set, checked lifecycle tests; controller owns only that context |
| NPC is independent of GameCharacter inheritance | Separate final classes; only session resolution couples an attack to a player |

No unresolved named NPC-fragment field, operation or relationship mismatch remains. Intentional signature
completions, primitive Boolean equivalents and necessary extra state are listed above. The wider supplied diagram
still differs from the pre-existing project: session/player/platform/item identities are UUIDs rather than its
Integers, Arena uses doubles, GameCharacter integration coordinates are doubles rather than integer x/y, and
unrelated lecturer families, salary and player attacks remain unimplemented. These are outside this task; no
repository-wide identity refactor or claim of whole-diagram correspondence is made.

## Essential source fragments for the report

Stable `STRATEGY-REPORT <marker> BEGIN/END` comments enclose actual algorithm/selection/delegation/resolution
fragments. Repeated R2/R4/R5 spans intentionally mark a field plus a method, or the two cooperating methods.
Paths below are relative to the repository root.

| Marker | Actual repository path | Enclosing fragment/method | Report purpose |
| --- | --- | --- | --- |
| R1 | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/NPCActivityStrategy.java` | Interface `decide` declaration | One shared Strategy contract |
| R2 | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/NPC.java` | `activityStrategy` field, `setActivityStrategy`, delegation in `advance` | Context has-a interface, replacement and polymorphism |
| R3a | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/PatrolStrategy.java` | `decide` | Boundary reversal and safe per-tick travel |
| R3b | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/ChaseStrategy.java` | `decide` | Target-directed motion via multiplier |
| R3c | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/AttackStrategy.java` | `decide` | Optional request rather than direct damage |
| R3d | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/FleeStrategy.java` | `decide` | Opposite motion, safe coincidence handling |
| R4 | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/NPCBehaviorController.java` | `strategies` field and `switchActivity` | Selection separated from algorithm; runtime setter installation |
| R5 | `game-domain/src/main/java/lt/ktu/paskutinisatsiskaitymas/domain/GameSession.java` | `advance` and `resolveNPCAction` | Real multiplayer integration, cooldown, stress and result feedback |

Extract reliable exact marked fragments with:

```sh
rg -n -A 18 -B 2 'STRATEGY-REPORT' game-domain/src/main/java
```

Short essential excerpts (the source markers contain the complete surrounding checks):

```java
NPCAction decide(NPCState self, NPCTarget target, double seconds);

activityStrategy = Objects.requireNonNull(strategy, "strategy");
NPCAction action = Objects.requireNonNull(activityStrategy.decide(state(), target, seconds), "decision");

npc.setActivityStrategy(strategies.get(next));

npc.recordAttackAttempt();
return target.takeDamage(npc.attackDamage());
```

A focused report can use R1/R2/R4 to explain structure, R3a–R3d to establish the four meaningful classes, and R5
for the working integration. Include the real unused-Enemy baseline sketch and the supplied MagicDraw after
image, explain the completed signatures and gameplay decisions, then show the demonstration transcript. The
report still needs the student's own exported MagicDraw diagrams and required submission; this Markdown is
implementation/report material, not a claim that the entire module's report has been submitted.

## Four replaceable student poses

All images load through the existing `AssetManager` singleton from `game-client/src/main/resources/assets/`.
The only activity-to-image/style mapping is `NPCPresentation.java` in
`game-client/src/main/java/lt/ktu/paskutinisatsiskaitymas/client/`.

| Server activity | PNG / asset key | Render height vs upright body | Contact fraction of source height |
| --- | --- | --- | --- |
| PATROL | `NPC_PATROL.png` / `NPC_PATROL` | 1.00 | 1927/1930 |
| CHASE | `NPC_CHASE.png` / `NPC_CHASE` | 0.94 | 1512/1536 |
| ATTACK | `NPC_ATTACK.png` / `NPC_ATTACK` | 0.80 | 1320/1330 |
| FLEE | `NPC_FLEE.png` / `NPC_FLEE` | 0.50 | 960/1024 |

Metadata was calibrated against the supplied alpha images and the renderer's generated smoke sheet. The contact
fraction aligns opaque sneakers/hands at authoritative `y + height`; source padding/soft transparency may extend
slightly below it. Full PNG aspect ratio and alpha are preserved, with nearest-neighbor scaling. The pose is centered
at authoritative body center, with height/vertical contact independent of its 42×64 collision rectangle. FLEE is
lower/wider; both threat orientations are rendered in the smoke sheet. NPC labels distinguish students and
always show truthful server activity. PATROL/CHASE face motion and retain direction at rest; ATTACK faces the
target; FLEE faces the threat while moving away, so source right-facing crawl is mirrored for a left-side threat.

Replacing a PNG under the same filename, rebuilding and restarting changes visuals without editing strategies.
For different transparent padding/body proportions, adjust the single `PoseStyle` height/contact mapping. Missing
images fall back to PATROL, then a green primitive body. These are four static poses, not multi-frame animation.

An accepted attempt, including a shield block, increments `NPCSnapshot.attackAttemptSequence`. The first snapshot
initializes it without replay; later increases (even if intermediate values were skipped) latch ATTACK artwork for
180 ms. This survives ATTACK→FLEE inside one 60 Hz tick while snapshots arrive at 20 Hz. The authoritative label
and simulation transition do not wait. `NPCPresentation` is EDT-confined; the existing notification/repaint timer
also expires latches and stops when idle/disconnected/removed. No client damage or AI selection occurs.

The wire schema now includes required `npcs` in WorldSnapshot and `stressLevel` in PlayerSnapshot. Rebuild/restart
both server and clients together; no protocol compatibility fallback for old binaries is claimed.

## Demonstration and verification

Use a standalone Java 25 JDK and the Maven Wrapper, from the repository root:

```sh
./mvnw -B -ntp clean verify
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.NPCStrategyDemo
```

The main uses the production default session, removes NPC 2 only for a readable demonstration, joins a stationary
real player and advances fixed `1/60` second steps. It prints the cycle on NPC 1, records actual attack sequence
and stress, and asserts that all four strategies were exercised before returning to patrol. A successful ATTACK
is resolved within a tick, so the demo explicitly reports that transient operation rather than pretending it
persisted as a post-tick state. It runs without sockets/workers/sleeps and completes before random item spawning.

Expected representative output:

```text
tick=0 NPC=1 PATROL x=360.00 vx=0.00 target=null stress=0
tick=36 NPC=1 CHASE x=413.04 vx=-142.50 target=1 stress=0
tick=110 NPC=1 FLEE x=239.67 vx=0.00 target=1 stress=10
  ATTACK resolved within tick: accepted attempt #1; player stress=10; next=FLEE
tick=165 NPC=1 PATROL x=395.15 vx=95.00 target=null stress=10
All four production strategies demonstrated on NPC 1; flee returned to patrol.
```

For the multiplayer demonstration, build, run the server and up to four clients as in README, connect, approach an
NPC, watch labels/poses and stress in the fixed corner cards, then collect a shield or retreat/jump. Disconnect the
target and confirm its NPC returns to PATROL. Reconnect and observe fresh detection. A fifth player is rejected.
Close all clients and stop the server after the demonstration. The deterministic main still uses one player and
keeps its original slot-0 spawn, so its transcript remains unchanged.

Focused deterministic tests cover four algorithms, same-context replacement, boundary reversal, target matching,
missing/coincident targets, nearest tie-breaking, continuous detection/reset, chase/flee timeouts, safe distance,
disconnect/reconnect numeric IDs, stress damage, shield blocks/cooldown/attempt sequence, invalid/out-of-range
requests, fractional motion, arena/platform collision, and matched collection lifecycle. Codec tests cover all
four activities, double render geometry, facing/sequence and invalid fields. Mapper tests simulate 20 Hz snapshots
against 60 Hz production steps; real WebSocket tests deliver NPCs to connected peers alongside independent player
movement and preserve capacity/reconnect/shutdown coverage. Client tests load all four alpha images, check the
pose mapping, aspect/contact geometry and sequence latch with an injected deterministic presentation clock.

`NPCPresentationTest` renders `game-client/target/npc-pose-smoke.png` through the actual GamePanel on the EDT for
visual inspection. It shows all four poses plus mirrored FLEE. Tests clear UI/timers, close socket/HTTP clients and
stop server/loop workers. The required reactor verification retains Java 25, Enforcer and Checkstyle checks.

NPC Strategy baseline validation: the Java 25 standalone JDK (Temurin 25.0.4.1) completed the full five-module
`./mvnw -B -ntp clean verify` reactor successfully: **72 tests, zero failures/errors/skips**, with Enforcer and
Checkstyle enabled. The exact main command above ran successfully and produced the documented cycle. The
GamePanel smoke sheet was visually inspected for alpha, ground alignment, relative pose scale and mirrored FLEE.
The named UML fields/operations, direct interface realizations, ownership and all R1–R5 spans were audited against
the actual source. No commit, push or PR was created.
