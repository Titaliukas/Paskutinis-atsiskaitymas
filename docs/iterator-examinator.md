# Iterator and Examinator

The frozen `LD2/Iterator/v2/after-fix.svg` defines the domain roles. Java uses the existing UUID identities and
real `java.lang.Iterable` / `java.util.Iterator`; there are no parallel target interfaces or entity adapters.

| GoF role | Production object | Live storage / traversal |
| --- | --- | --- |
| Aggregate | `Iterable<T>` | Standard Java contract |
| Concrete aggregate | `PlayerTargets` | `GameCharacter[4] slots`, indexed by player slot |
| Concrete iterator | `PlayerTargetIterator` | Independent index, skips null slots; exhausted `next()` throws `NoSuchElementException` |
| Concrete aggregate | `NpcTargets` | `LinkedHashMap<Integer, NPC>`; `values().iterator()` |
| Concrete aggregate | Existing `Projectiles` | `ArrayList<Projectile>`; `active.iterator()` |
| Client of Iterator | `Examinator` | The same loop consumes all three sources through their interfaces |
| Polymorphic element | `AutoTarget` | Implemented directly by `GameCharacter`, `NPC`, and `Projectile` |

Each running lobby's `GameSession` owns these three live aggregates and one retained `Examinator` per joined
character. `PlayerTargets` also owns a UUID lookup index, updated on that same owner thread; the slot array remains
the actual targeting traversal. Read-only map/collection views keep existing combat APIs intact. Joins, leaves,
NPC spawns and projectile replacements update the owning aggregate. Nothing is shared between lobbies.

The ability constructor receives the attacker and `List<Iterable<? extends AutoTarget>>`: references to those
same aggregates, supplied once. It does not flatten or copy their targets. The shared traversal is:

```java
for (Iterable<? extends AutoTarget> source : targetSources) {
    fireAtAll(source.iterator());
}
// fireAtAll:
while (iterator.hasNext()) {
    AutoTarget target = iterator.next();
    if (target.canBeTargetedBy(attacker)) {
        fireAt(target);
    }
}
```

Iterators only traverse. Eligibility, aiming and hit behavior belong to `AutoTarget`; the loop does not inspect
collection or target classes. `fireAt` derives direction independently from each endpoint and records it before
applying effects. An identity set prevents duplicate hits if a source reference is repeated. Every iterator is
created and consumed within the firing tick. The original sealed `Attack` hierarchy, `AttackType`, loadouts,
and Bridge effects remain intact; this separate ability reuses the attacker's ranged effects.

## Rules and tick order

A press edge accepts an activation when ready and standing, starts a **10 s cooldown immediately**, then scans
for **0.5 s** before one instant arena-wide volley. Moving and jumping remain available during the scan.
Holding the activation button never restarts a scan or queues another activation after cooldown. Every character
has the ability. Players other than its owner can be targeted unless prone; shields still block the entire
hit's effect list. Living NPCs receive their existing `AttackEffect` overloads. Active NPC projectiles, including
returning books/diplomas, are neutralized rather than damaged.

Prone is a grounded posture with unchanged 42x64 collision geometry. It blocks voluntary movement, jump and new
normal attacks. It protects specifically against Examinator selection, not ordinary damage. Physics, knockback,
gravity, cooldowns and timed effects continue. Losing support or respawning clears prone. Entering prone or
respawning cancels a pending scan without refunding the cooldown; explicit posture/respawn counters detect the
transition even if the character returns to identical coordinates and health. Leaving removes the ability.
`GameSession.startExaminator(GameCharacter)` rejects a source from another session; application commands route
by the server-assigned player UUID instead of accepting a client-supplied domain object.

`MatchController` first captures retained movement/posture intent for every player and applies **all** postures,
then consumes attack/Examinator press edges. `GameSession` applies posture for direct domain callers too, moves
platforms/characters, updates abilities, and removes neutralized projectiles before further flight/collision.
Dead NPC removal happens after traversal. Projectile flight replaces the current list element via `ListIterator`;
a returned clone consumes only the remaining tick travel and is never advanced again as a new list element.
WebSocket callbacks still only validate and queue commands. The simulation remains 60 Hz and snapshots 20 Hz.

## Input and authoritative presentation

Keyboard: **L** activates on a press edge; hold **S** or **Down** for prone, release to stand. Existing A/D/arrows,
Space/W/Up, J and K remain unchanged. Xbox/PlayStation/8BitDo use SDL's top face button (**Y/Triangle**) and held
**D-pad Down**. `GamepadReading` copies `ControllerState.y` and `dpadDown` from the installed Jamepad API.
Complete input follows the existing client, protocol, mediator, queued `InputCommand` and controller path;
sequence rejection, reset and pre-game suppression still apply.

`PlayerSnapshot.prone` and `WorldSnapshot.examinators` carry detached immutable DTOs. Each ability state includes
owner UUID, drone position, explicit IDLE/SCAN/FIRE phase, remaining cooldown/phase time, monotonically increasing
volley sequence, recorded origin and endpoints. FIRE feedback survives **0.25 s**, even when the hit NPC or
projectile has disappeared or a player has respawned. All clients draw those same points; they never infer
hits/damage/targets. Strict JSON requires the new fields, so deploy matching client and server builds.

The EDT-owned presentation deduplicates by owner and volley sequence and never extends/replays a shot for repeated
snapshots. It reuses `GamePanel`'s existing presentation timer; expiry, disconnect and `clearGame` stop animation
work. The drone shows scan/fire poses and cooldown feedback. Cosmetic papers and impact marks do not simulate
new damaging projectiles. One central rotation of existing character sprites preserves their proportions and
feet anchor for all four prone poses. Existing Flyweight particle accounting and HUD Composite remain intact.

Optional resources, loaded once through `AssetManager`, are:
`EXAMINATOR_IDLE.png`, `EXAMINATOR_SCAN.png`, `EXAMINATOR_FIRE.png`, `EXAMINATOR_ICON.png`,
`EXAMINATOR_EXAM_PAPER.png`, `EXAMINATOR_RED_PEN.png`, `EXAMINATOR_IMPACT.png` under
`game-client/src/main/resources/assets/`. Drone canvases are logical 64x64, other canvases 32x32; integer
magnification is allowed. Rendering preserves aspect ratio and uses world-space sizes (44-unit drone, 16-unit
paper), independently of exported pixel dimensions. Missing resources use small coherent procedural pixel-style
fallbacks; no generated PNGs, additional art dependencies, or changes to old assets are needed.

## Deterministic demonstration

With standalone JDK 25, from the repository root:

```sh
./mvnw -B -ntp clean verify
java -cp game-domain/target/game-domain.jar lt.ktu.paskutinisatsiskaitymas.domain.IteratorExaminatorDemo
```

This drives the same production aggregates, character loadout and ability without sleeps, network or substitute
combat logic. Expected output:

```text
Accepted=true, phase=SCAN
PlayerTargets / GameCharacter[4]: owner=100, standing=70, prone=100
NpcTargets / LinkedHashMap<Integer,NPC>: health=20
Projectiles / ArrayList<Projectile>: active=0
Volley=1, endpoints=3, phase=FIRE, cooldown=9.5
```

## Two-client manual scenario

1. Build and start Training: `java -Dgame.arena=training -jar game-server/target/game-server.jar`.
2. In two other terminals at the root, run the client JAR as Alice and Bob. Choose VACIJUS for Alice, create her
   lobby, then have Bob select a character and join its code **before** Alice starts the game.
3. Leave Bob standing and press L once on Alice: scan for 0.5 s, then Bob loses 30 health and both clients see the
   same instant volley. Alice's 10 s cooldown started on the accepted press. Holding L must not fire again.
4. Release L, wait for ready, then hold S/Down on Bob before Alice presses L. Bob's sprite lies down, he cannot
   walk/jump/attack, and the volley has no line to Bob and does not change his health. Release to stand.
5. While Alice scans, enter prone on Alice: the scan cancels and cooldown continues. Check ordinary J/K attacks
   still damage prone Bob; prone is not global immunity. Repeat with top face/D-pad Down on a controller.
6. On Combat, repeat with NPCs and active/returning books or diplomas: eligible projectiles disappear immediately,
   and the recorded endpoint flashes remain visible. A shield blocks player effects. With a second independent
   lobby, verify no drone state, damage or feedback crosses lobby boundaries.
7. Close the clients and stop the server with Ctrl+C. Host departure must still close only its own lobby.

Automated verification covers slot iterator contracts, live sources, duplicate-hit prevention, shields/loadouts,
respawn/posture cancellation, returning-projectile interception, simultaneous prone/attack ordering, held/stale
input, strict DTO validation, full snapshots, real multi-client/lobby WebSocket routing, presentation expiry and
deduplication, controller/reset mappings, all missing-art fallbacks and offscreen prone rendering. The generated
`game-client/target/examinator-assets-prone.png` is a verification artifact, not a bundled game asset.
