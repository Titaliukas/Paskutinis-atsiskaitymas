package lt.ktu.paskutinisatsiskaitymas.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Flight and collision rules, used exclusively by the authoritative session owner. */
final class Projectiles {
    private final Map<UUID, Projectile> active = new LinkedHashMap<>();

    boolean spawn(NPCState source, NPCTarget target, ProjectileType type) {
        if (active.size() >= GameConstants.PROJECTILE_MAX_ACTIVE) {
            return false;
        }
        Projectile projectile = new Projectile(UUID.randomUUID(), source.id(), target.id(), type,
                new Position(source.preciseX() + source.width() / 2, source.preciseY() + source.height() / 2),
                0, 0, GameConstants.NPC_PROJECTILE_LIFETIME_SECONDS, false);
        projectile.aimAt(target.x() + target.width() / 2, target.y() + target.height() / 2);
        active.put(projectile.state().id(), projectile);
        return true;
    }

    List<ProjectileState> states() { return active.values().stream().map(Projectile::state).toList(); }

    void removeSource(int sourceId) { active.values().removeIf(value -> value.state().sourceNPCId() == sourceId); }

    void removeTarget(int targetId) { active.values().removeIf(value -> value.state().targetId() == targetId); }

    void advance(Arena arena, Map<Integer, NPC> npcs, List<GameCharacter> players, double seconds) {
        // Iterate original identities only; a return is advanced here rather than again in this tick.
        for (Projectile projectile : List.copyOf(active.values())) {
            active.remove(projectile.state().id());
            NPC source = npcs.get(projectile.state().sourceNPCId());
            if (source == null || !source.state().alive()) {
                continue;
            }
            Projectile survivor = advanceOne(projectile, arena, source, players, seconds);
            if (survivor != null) {
                active.put(survivor.state().id(), survivor);
            }
        }
    }

    private Projectile advanceOne(Projectile projectile, Arena arena, NPC source,
            List<GameCharacter> players, double seconds) {
        double budget = seconds;
        // At most one side-wall replacement and one subsequent collision.
        for (int leg = 0; leg < 2; leg++) {
            ProjectileState state = projectile.state();
            double travel = Math.min(budget, state.remainingSeconds());
            Hit hit = firstHit(state, arena, source.state(), players, travel);
            double elapsed = hit == null ? travel : hit.seconds();
            projectile.move(elapsed);
            budget = Math.max(0, budget - elapsed);
            if (projectile.state().remainingSeconds() < 0.000_000_001) {
                return null;
            }
            if (hit == null) {
                return projectile;
            }
            switch (hit.kind()) {
                case SOLID -> { return null; }
                case PLAYER -> {
                    hit.player().applyProjectileSlow();
                    return null;
                }
                case SOURCE -> {
                    source.stun();
                    return null;
                }
                case SIDE -> {
                    if (state.returning()) {
                        return null;
                    }
                    projectile = projectile.returnToward(source.state());
                    if (budget == 0) {
                        return projectile;
                    }
                }
            }
        }
        return null;
    }

    private Hit firstHit(ProjectileState state, Arena arena, NPCState source,
            List<GameCharacter> players, double seconds) {
        double r = GameConstants.NPC_PROJECTILE_RADIUS;
        Hit best = null;
        // Solids win equal-time ties, including ground that meets a side wall.
        for (Platform platform : arena.platforms()) {
            best = earlier(best, new Hit(sweep(state, platform.left(), platform.top(),
                    platform.width(), platform.height()), Kind.SOLID, null), seconds);
        }
        double vertical = boundary(state.position().y(), state.velocityY(), r, arena.height() - r);
        best = earlier(best, new Hit(vertical, Kind.SOLID, null), seconds);
        double horizontal = boundary(state.position().x(), state.velocityX(), r, arena.width() - r);
        best = earlier(best, new Hit(horizontal, Kind.SIDE, null), seconds);
        if (state.returning()) {
            best = earlier(best, new Hit(sweep(state, source.preciseX(), source.preciseY(),
                    source.width(), source.height()), Kind.SOURCE, null), seconds);
        } else {
            for (GameCharacter player : players) {
                GameCharacterState body = player.state();
                best = earlier(best, new Hit(sweep(state, body.x(), body.y(),
                        GameConstants.PLAYER_WIDTH, GameConstants.PLAYER_HEIGHT), Kind.PLAYER, player), seconds);
            }
        }
        return best;
    }

    private static Hit earlier(Hit best, Hit candidate, double seconds) {
        return candidate.seconds() >= 0 && candidate.seconds() <= seconds
                && (best == null || candidate.seconds() < best.seconds()) ? candidate : best;
    }

    private static double boundary(double position, double velocity, double low, double high) {
        if (position < low - 0.000_001 || position > high + 0.000_001) {
            return 0;
        }
        return velocity == 0 ? Double.POSITIVE_INFINITY
                : Math.max(0, ((velocity < 0 ? low : high) - position) / velocity);
    }

    /** Exact swept circle/rectangle: face strips and the four rounded corners. Times are seconds. */
    private static double sweep(ProjectileState state, double x, double y, double width, double height) {
        double px = state.position().x();
        double py = state.position().y();
        double vx = state.velocityX();
        double vy = state.velocityY();
        double r = GameConstants.NPC_PROJECTILE_RADIUS;
        double dx = px - NPCGeometry.clamp(px, x, x + width);
        double dy = py - NPCGeometry.clamp(py, y, y + height);
        if (dx * dx + dy * dy <= r * r) {
            return 0;
        }
        double best = Math.min(strip(px, py, vx, vy, x - r, y, width + 2 * r, height),
                strip(px, py, vx, vy, x, y - r, width, height + 2 * r));
        for (double cx : new double[]{x, x + width}) {
            for (double cy : new double[]{y, y + height}) {
                double a = vx * vx + vy * vy;
                double b = (px - cx) * vx + (py - cy) * vy;
                double c = (px - cx) * (px - cx) + (py - cy) * (py - cy) - r * r;
                double discriminant = b * b - a * c;
                if (a > 0 && discriminant >= 0) {
                    double time = (-b - Math.sqrt(discriminant)) / a;
                    if (time >= 0) {
                        best = Math.min(best, time);
                    }
                }
            }
        }
        return best;
    }

    private static double strip(double px, double py, double vx, double vy,
            double x, double y, double width, double height) {
        double[] horizontal = interval(px, vx, x, x + width);
        double[] vertical = interval(py, vy, y, y + height);
        double entry = Math.max(0, Math.max(horizontal[0], vertical[0]));
        double exit = Math.min(horizontal[1], vertical[1]);
        return entry <= exit ? entry : Double.POSITIVE_INFINITY;
    }

    private static double[] interval(double position, double velocity, double low, double high) {
        if (velocity == 0) {
            return position >= low && position <= high
                    ? new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
                    : new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        }
        double first = (low - position) / velocity;
        double second = (high - position) / velocity;
        return new double[]{Math.min(first, second), Math.max(first, second)};
    }

    private enum Kind { SOLID, SIDE, PLAYER, SOURCE }

    private record Hit(double seconds, Kind kind, GameCharacter player) { }
}
