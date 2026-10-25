package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable arena recipe. All coordinates are world units, with positive Y downward. */
public record Arena(String name, ArenaType type, double width, double height, List<Platform> platforms,
        List<Position> playerSpawns, List<NPCSpawn> npcSpawns, double groundY, Optional<SidePassage> sidePassage) {
    public Arena {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sidePassage, "sidePassage");
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0
                || !Double.isFinite(groundY) || groundY < GameConstants.PLAYER_HEIGHT || groundY > height) {
            throw new IllegalArgumentException("Arena bounds and ground must be finite and inside the world");
        }
        platforms = List.copyOf(platforms);
        playerSpawns = List.copyOf(playerSpawns);
        npcSpawns = List.copyOf(npcSpawns);
        if (playerSpawns.size() != GameConstants.MAX_PLAYERS) {
            throw new IllegalArgumentException("Arena requires exactly four player spawns");
        }
        for (Platform platform : platforms) {
            if (platform.left() < 0 || platform.top() < 0 || platform.right() > width
                    || platform.top() + platform.height() > height) {
                throw new IllegalArgumentException("Platform outside arena");
            }
        }
        for (Position spawn : playerSpawns) {
            requireBodyInside(spawn, width, height);
        }
        if (npcSpawns.stream().map(NPCSpawn::id).distinct().count() != npcSpawns.size()) {
            throw new IllegalArgumentException("NPC spawn IDs must be unique");
        }
        for (NPCSpawn spawn : npcSpawns) {
            requireBodyInside(spawn.position(), width, height);
            if (spawn.rightBoundary() > width - GameConstants.PLAYER_WIDTH) {
                throw new IllegalArgumentException("NPC patrol outside arena");
            }
        }
        if (sidePassage.isPresent()) {
            SidePassage passage = sidePassage.orElseThrow();
            if (passage.supportY() > height || !hasEdgeSupport(platforms, passage.supportY(), 0)
                    || !hasEdgeSupport(platforms, passage.supportY(), width - GameConstants.PLAYER_WIDTH)) {
                throw new IllegalArgumentException("Both passage exits require full body support");
            }
        }
    }

    private static void requireBodyInside(Position position, double width, double height) {
        if (position.x() < 0 || position.y() < 0 || position.x() + GameConstants.PLAYER_WIDTH > width
                || position.y() + GameConstants.PLAYER_HEIGHT > height) {
            throw new IllegalArgumentException("Spawn body outside arena");
        }
    }

    private static boolean hasEdgeSupport(List<Platform> platforms, double y, double x) {
        return platforms.stream().anyMatch(p -> p.top() == y && p.left() <= x
                && p.right() >= x + GameConstants.PLAYER_WIDTH);
    }
}
