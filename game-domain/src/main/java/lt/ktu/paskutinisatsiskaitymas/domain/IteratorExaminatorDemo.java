package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.UUID;

/** Deterministic Iterator demonstration using the production aggregates, targets, loadout and ability. */
public final class IteratorExaminatorDemo {
    private IteratorExaminatorDemo() { }

    public static void main(String[] args) {
        PlayerTargets players = new PlayerTargets();
        NpcTargets npcs = new NpcTargets();
        Projectiles projectiles = new Projectiles();
        GameCharacter owner = player(0, 100, CharacterType.VACIJUS);
        GameCharacter standing = player(1, 400, CharacterType.PATASIJUS);
        GameCharacter prone = player(3, 700, CharacterType.GOGIS);
        players.add(owner); players.add(standing); players.add(prone);
        prone.setProne(true);
        NPC npc = new NPC(1, new Position(500, 416), 42, 64, 10, 18, 10, 1.2, new PatrolStrategy(0, 900));
        npcs.put(1, npc);
        projectiles.spawn(npc.state(), new NPCTarget(1, 100, 416, 42, 64), ProjectileType.BOOK);
        Examinator ability = new Examinator(owner, List.of(players, npcs, projectiles),
                new ArenaDirector().construct(new TrainingArenaBuilder()));
        System.out.println("Accepted=" + ability.activate() + ", phase=" + ability.state().phase());
        ability.update(Examinator.FLIGHT_SECONDS);
        System.out.println("Arrived=" + ability.state().position() + ", phase=" + ability.state().phase());
        ability.update(Examinator.INSPECTION_SECONDS + Examinator.ACQUIRE_SECONDS);
        System.out.println("Selected=" + ability.state().aim() + ", phase=" + ability.state().phase());
        for (int i = 0; i < 3; i++) {
            ability.update(i == 0 ? Examinator.AIM_SECONDS
                    : Examinator.FIRING_SECONDS + Examinator.ACQUIRE_SECONDS + Examinator.AIM_SECONDS);
            ExaminatorShot shot = ability.state().shots().getFirst();
            System.out.println("Shot=" + shot.sequence() + ", endpoint=" + shot.endpoint());
        }
        projectiles.removeInactive();
        System.out.printf("PlayerTargets / GameCharacter[4]: owner=%d, standing=%d, prone=%d%n",
                owner.state().health(), standing.state().health(), prone.state().health());
        System.out.printf("NpcTargets / LinkedHashMap<Integer,NPC>: health=%.0f%n", npc.health());
        System.out.printf("Projectiles / ArrayList<Projectile>: active=%d%n", projectiles.states().size());
        System.out.printf("Shots=%d, feedback=%d, phase=%s, cooldown=%.1f%n", ability.state().shotSequence(),
                ability.state().shots().size(), ability.state().phase(), ability.state().cooldownRemaining());
    }

    private static GameCharacter player(int slot, double x, CharacterType type) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "P" + slot), slot,
                new Position(x, 416), true, type);
    }
}
