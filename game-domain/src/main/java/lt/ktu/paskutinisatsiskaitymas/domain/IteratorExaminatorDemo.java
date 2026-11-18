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
        Examinator ability = new Examinator(owner, List.of(players, npcs, projectiles));
        System.out.println("Accepted=" + ability.activate() + ", phase=" + ability.state().phase());
        ability.update(Examinator.WINDUP_SECONDS);
        projectiles.removeInactive();
        System.out.printf("PlayerTargets / GameCharacter[4]: owner=%d, standing=%d, prone=%d%n",
                owner.state().health(), standing.state().health(), prone.state().health());
        System.out.printf("NpcTargets / LinkedHashMap<Integer,NPC>: health=%.0f%n", npc.health());
        System.out.printf("Projectiles / ArrayList<Projectile>: active=%d%n", projectiles.states().size());
        System.out.printf("Volley=%d, endpoints=%d, phase=%s, cooldown=%.1f%n", ability.state().volleySequence(),
                ability.state().endpoints().size(), ability.state().phase(), ability.state().cooldownRemaining());
    }

    private static GameCharacter player(int slot, double x, CharacterType type) {
        return new GameCharacter(UUID.randomUUID(), new Player(UUID.randomUUID(), "P" + slot), slot,
                new Position(x, 416), true, type);
    }
}
