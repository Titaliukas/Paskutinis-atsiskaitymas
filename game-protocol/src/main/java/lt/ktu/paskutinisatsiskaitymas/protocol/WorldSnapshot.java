package lt.ktu.paskutinisatsiskaitymas.protocol;

import java.util.List;
import java.util.Objects;

/** Authoritative state broadcast. It contains transport DTOs rather than domain entities. */
public record WorldSnapshot(long tick, ArenaSnapshot arena, List<PlayerSnapshot> players, List<ItemSnapshot> items,
        List<NPCSnapshot> npcs, List<AttackSnapshot> attacks,
        List<ProjectileSnapshot> projectiles, List<ExaminatorSnapshot> examinators) implements Message {
    public WorldSnapshot(long tick, ArenaSnapshot arena, List<PlayerSnapshot> players, List<ItemSnapshot> items,
            List<NPCSnapshot> npcs, List<AttackSnapshot> attacks, List<ProjectileSnapshot> projectiles) {
        this(tick, arena, players, items, npcs, attacks, projectiles, List.of());
    }

    public WorldSnapshot {
        if (tick < 0) {
            throw new IllegalArgumentException("Snapshot tick cannot be negative");
        }
        Objects.requireNonNull(arena, "arena");
        players = List.copyOf(players);
        items = List.copyOf(items);
        npcs = List.copyOf(npcs);
        attacks = List.copyOf(attacks);
        projectiles = List.copyOf(projectiles);
        examinators = List.copyOf(examinators);
        List<PlayerSnapshot> roster = players;
        if (examinators.size() > 4 || examinators.stream().map(ExaminatorSnapshot::ownerId).distinct().count()
                != examinators.size() || examinators.stream().anyMatch(ability -> roster.stream()
                .noneMatch(player -> player.playerId().equals(ability.ownerId())))) {
            throw new IllegalArgumentException("Examinator owners must be distinct current players");
        }
        if (projectiles.stream().map(ProjectileSnapshot::id).distinct().count() != projectiles.size()) {
            throw new IllegalArgumentException("Projectile IDs must be unique");
        }
        if (npcs.stream().map(NPCSnapshot::id).distinct().count() != npcs.size()) {
            throw new IllegalArgumentException("NPC IDs must be unique");
        }
        if (players.size() > 4) {
            throw new IllegalArgumentException("A snapshot can contain at most four players");
        }
    }
}