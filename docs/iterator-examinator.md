# Iterator and Examinator

The frozen `LD2/Iterator/v2/after-fix.svg` defines the unchanged domain roles. Java uses
`java.lang.Iterable` and `java.util.Iterator`, with no replacement iterator interfaces or target adapters.

| GoF role | Production object | Live storage / traversal |
| --- | --- | --- |
| Aggregate | `Iterable<T>` | Standard Java contract |
| Concrete aggregate | `PlayerTargets` | `GameCharacter[4] slots`, traversed in slot order |
| Concrete iterator | `PlayerTargetIterator` | Independent cursor, skips holes; exhausted `next()` throws `NoSuchElementException` |
| Concrete aggregate | `NpcTargets` | `LinkedHashMap<Integer, NPC>`; JDK `values().iterator()` |
| Concrete aggregate | `Projectiles` | `ArrayList<Projectile>`; JDK `iterator()` |
| Client of Iterator | `Examinator` | One shared acquisition method across all three sources |
| Polymorphic element | `AutoTarget` | Directly implemented by `GameCharacter`, `NPC`, `Projectile` |

Each lobby's `GameSession` owns these three aggregates and a retained ability per joined character. The ability
receives `List<Iterable<? extends AutoTarget>>` referencing those exact live aggregates, in player/NPC/projectile
order, plus that session's arena. Targets are never flattened into a master list. `PlayerTargets` retains its
UUID lookup index for existing combat APIs; the slot array remains the targeting traversal.

## Server sequence

All timing uses simulation time, with named defaults:

| Phase / rule | Duration | Behavior |
| --- | --- | --- |
| Cooldown | 30 s from acceptance | Accept only when standing, ready, and inactive |
| FLY | 0.8 s | Enter from `(arena.width()/2, -64)` to `(arena.width()/2, arena.height()/2)` |
| INSPECT | 0.6 s | Look left, then right; no damage |
| ACQUIRE | 0.2 s | Pause, then choose one eligible candidate |
| AIM | 0.5 s | Track the locked candidate with a restrained red cue; no damage |
| FIRE | 0.2 s | Apply ranged effects once at entry, show one laser and impact |

After FIRE, repeat ACQUIRE/AIM/FIRE for the next target. The drone is invisible before activation and hides
immediately after exhaustion or cancellation; there is no return flight or owner-following idle head. Each
accepted activation starts again at the fixed entry point above the arena, with the named 64-unit offscreen
margin keeping the entire head and initial effects outside the visible world. Idle snapshots retain the owner
UUID and cooldown, with a harmless position independent of owner movement. The first shot occurs after 2.1 s;
subsequent uninterrupted shots are 0.9 s apart. Owner movement never affects the flight path or central destination. The central emitter is seven units
right and three units above the head anchor; every shot records that same origin and its exact pre-hit endpoint.
An update consumes its entire elapsed budget across phase boundaries, including feedback aging; no sleeps or
asynchronous domain timers are involved.

Every target is marked **considered**, including ineligible candidates, using identity bookkeeping for this
activation. Acquisition creates a fresh short-lived iterator over the current source, skips considered objects,
and returns at most one eligible target. Exhausted sources advance permanently; later additions to earlier
sources wait for a future activation. Additions to the current or a later source can still be considered. Duplicate
source references do not cause duplicate hits, and an already considered player cannot be struck again after
respawning. No targeting code branches on collection or target classes.

No map/list iterator survives a call or tick. A locked target is revalidated with another fresh iterator, identity
membership, eligibility, and `AutoTarget.targetLifetime()` before firing (and on each update while aiming).
`GameCharacter` exposes its existing respawn counter through that small polymorphic lifetime hook. Removal,
replacement, death, prone, or respawn invalidates the lock and resumes acquisition. A replacement projectile is a
new object; the old aimed object cannot receive a stale hit. Iteration has returned before effects can structurally
change a source; `ConcurrentModificationException` is never normal control flow.

A **120 s maximum active duration** makes passes finite under continuous arrivals. The usual bounded arena has
at most three other players, two NPCs, and eight active projectiles, requiring about 13 s for a fixed set; the limit
leaves ample margin. Entities arriving after the cap or after their source was exhausted wait for the next pass.

The attacker's existing ranged `AttackEffect`s and loadouts remain authoritative. Prone players and the owner
are excluded; shields block the entire player effect list. NPCs receive their existing polymorphic effects.
Active outbound and returning NPC projectiles are neutralized, without spawning damaging laser projectiles.

Entering prone or respawning cancels **any active phase**, clears locks/feedback, and keeps the remaining
cooldown. Existing posture/respawn counters detect transitions even if the owner stands again or returns to
identical coordinates. Departure removes the ability. Foreign-session character objects are rejected.
`MatchController` applies every player's posture before consuming attack edges. Session ticks update abilities
before projectile flight, remove neutralized projectiles safely, and clean dead NPCs after traversal. The existing
60 Hz simulation, 20 Hz snapshots, lobby Mediator, and queued WebSocket commands remain intact.

## Input and authoritative presentation

Press **L** / controller **Y or Triangle** to activate; holding never queues or repeats an activation. Hold
**S / Down / D-pad Down** to go prone when grounded. Prone blocks voluntary movement, jump and new attacks,
while physics, knockback, ordinary damage and timed effects continue. Releasing stands up; respawn or loss of
support clears prone. Existing input/reset/controller mappings and admission rules are unchanged.

`ExaminatorState`, `ExaminatorSnapshot` and `ExaminatorShotSnapshot` carry detached data: owner, phase, position,
remaining times, per-owner simulation time, emitter/aim point, monotonic shot sequence, and bounded recorded shot
feedback. Every individual shot has its own sequence; feedback contains its pre-hit origin/endpoint and remaining
0.2 s lifetime, even after a killed NPC or intercepted projectile disappears. At the normal 20 Hz cadence a shot
spans four snapshot intervals. Phase-boundary overshoot ages the feedback correctly; a large update can consume
older shots' entire windows, which are then intentionally absent. No unbounded event history is transmitted.
Strict JSON validation checks all fields, phases, finite numbers, timings, feedback bounds/order, and the 30 s
cooldown. Deploy matching client/server builds; this changes the wire shape. Full production snapshots still fit
the existing message-size limit.

The EDT presentation accepts increasing per-owner simulation times and deduplicates by **owner + shot sequence**.
Repeated/stale snapshots never replay bursts or extend an observed beam. A previously unseen late shot uses
only its received remaining lifetime; there is no synchronized wall-clock latency compensation. Older world ticks
are ignored. Cancellation, disconnect, and game reset clear locks/emitters/deduplication; finite emitted particles
expire normally (game reset clears the whole existing particle system). A stalled snapshot stream cannot keep
continuous thrusters or beams alive, and expired phase data never falls back to an idle head. The head, aim cue,
laser and only Examinator particle kinds are clipped to the displayed arena; off-arena entry cannot leak into
letterbox margins. Ordinary particles retain their existing rendering. Rendering never selects targets or applies damage.

Each owning player's existing fixed HUD card displays the ability status immediately below Stress and above
Player N / (you), matched by owner UUID regardless of snapshot order. Text is `EX: Ready`, upward-rounded
cooldown such as `EX: 24s`, or the active phase: Arriving, Scanning (inspection/acquisition), Aiming, Firing.
Active phases take precedence even when cooldown reaches zero; missing ability data shows `EX: --`. This row
uses authoritative snapshots without a separate client countdown. Cards retain their 92-unit height, portraits,
colors, nickname ellipsis and local-player border, with compact text spacing; bottom cards keep `bottomScale()`.
No Examinator status text is drawn in the world.

Flight emits sparse gray/red thruster pixels from the existing presentation timer. AIM emits a small eye charge;
each shot emits a single muzzle/impact burst through the existing `ParticleSystem`, `ParticleKind`, and
`ParticleTypeFactory`. The angle overload preserves ordinary callers, the particle cap, shared flyweights and
allocation accounting. Painting emits no particles.

## Approved artwork

The exact approved idle v7, movement/scan sheet, eight-direction attack sheet and standalone laser exports are
bundled under `game-client/src/main/resources/assets/EXAMINATOR_...`. The originals are retained alongside
prepared frames. `AssetManager` loads the frames/laser strips once from the classpath, including in the runnable
client JAR. There are no runtime absolute paths, image-processing dependencies, crops, or reloads during painting.

Preparation is reproducible with Pillow as an **offline tool only**:

```sh
python3 scripts/prepare-examinator-assets.py /path/to/approved/imagegen
```

The script uses measured per-frame bounds, transparent 128×128 canvases, a common head anchor and 112-pixel
apparent head height, nearest-neighbor resampling, and retained alpha. The source sheets have unequal head
bounds and low-alpha margin noise, so equal atlas cells are not used as head bounds. Attack discharges cross
faces: preparation removes only exterior beam pixels using the opaque head silhouette and preserves internal
red/white head/flash pixels. AIM uses clean left/right scan heads to avoid premature discharge. FIRE selects the
nearest of eight directional head/flash poses using the exact authoritative shot vector. The original facial
appearance and mechanical side remain intact; short discharge pixels inside the head remain part of the approved
flash. The separate laser rotates to the exact vector; its flare and tip stay fixed in thickness, and only its
middle stretches. Zero-length shots draw no beam. Missing art has procedural cyborg/laser fallbacks.

## Deterministic demonstration

With standalone JDK 25 and the Maven Wrapper:

```sh
./mvnw -B -ntp clean verify
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.IteratorExaminatorDemo
```

The production demo uses the real aggregates, loadout and sequential ability, without sleeps or substitute logic.
Expected output:

```text
Accepted=true, phase=FLY
Arrived=Position[x=480.0, y=270.0], phase=INSPECT
Selected=Position[x=421.0, y=448.0], phase=AIM
Shot=1, endpoint=Position[x=421.0, y=448.0]
Shot=2, endpoint=Position[x=521.0, y=448.0]
Shot=3, endpoint=Position[x=521.0, y=448.0]
PlayerTargets / GameCharacter[4]: owner=100, standing=70, prone=100
NpcTargets / LinkedHashMap<Integer,NPC>: health=20
Projectiles / ArrayList<Projectile>: active=0
Shots=3, feedback=1, phase=FIRE, cooldown=26.1
```

## Verification and two-client scenario

Automated tests cover the slot iterator contract; live additions/removals/replacements between ticks; source
progress and skipped-candidate bookkeeping; respawn lifetime checks; cancellation in every phase; cooldown and
active guards; both arena recipes and a differently sized arena; no premature damage; sequential effects and
all loadouts/shields; multidirectional/zero-length coordinates; detached feedback; large-step overshoot;
input edges/posture ordering; strict protocol round trips/invalid input and full snapshot size; presentation
expiry/deduplication/cancellation/reset; offscreen entry and hidden idle/expired views; owner-matched HUD rows,
missing data and active-phase precedence; time-based particles, scoped clipping, cap and orientation; classpath
frames and fallbacks.
The existing real WebSocket integration scenario connects four peers across two lobbies and checks shared shot
coordinates, prone protection, health changes, held-input suppression and isolation, with all peers/servers closed.

Earlier artwork/sequence verification also ran a live headless smoke check with two production
`GameClientFacade` clients against the packaged server,
rendering both real snapshots through `GamePanel`. Both observed FLY/INSPECT/ACQUIRE/AIM/FIRE, the central
(480,270) position, (487,267) emitter, identical shot coordinates, and guest health 70. Both clients and the server
were closed. This checks networking and Swing rendering without interactive desktop/controller input.

Client tests produce offscreen verification artifacts in `game-client/target/`: `examinator-poses.png`,
`examinator-directions.png`, and `examinator-{fly,inspect,acquire,aim,fire,idle}.png`. These are not bundled assets.
Focused offscreen-entry/HUD tests also save `examinator-hud-{arena}-{size}.png`,
`examinator-idle-{arena}-{size}.png`, and `examinator-arrival-{offscreen,entry}-{arena}-{size}.png` for both
Training and Combat at 960×540, 640×360 and letterboxed 640×400. They check that an initial offscreen launch
paints no head in the world, partial entry is clipped at the arena boundary, and reset/missing data clears status.

For a desktop check:

1. Start Training with `java -Dgame.arena=training -jar game-server/target/game-server.jar`.
2. Open two clients, select characters, create/join the same code, then have the host start.
3. Before activation, confirm no idle heads are visible and each card shows EX status below Stress. Press L
   once: both clients see entry from above the arena to center, inspection, acquisition, half-second aim, then one laser at
   the standing guest. A VACIJUS owner deals 30 damage. Cooldown starts at 30 s on acceptance; holding L does not repeat.
4. On a later activation, make the guest prone during AIM. Its lock is abandoned and it receives no hit. Try
   the owner entering prone during flight, inspection, aim or firing: the sequence cancels without refund.
5. Repeat in Combat with NPCs and projectiles, checking individual sequential shots and detached impacts.
   Check ordinary attacks still damage prone players, shields still work, and another lobby remains isolated.
6. Close clients and stop the server. Host departure still closes only that host's lobby.

Remaining presentation limits: snapshots are rendered directly without movement interpolation or network latency
compensation; directional head poses are quantized to eight sectors, while the laser uses the exact vector.
