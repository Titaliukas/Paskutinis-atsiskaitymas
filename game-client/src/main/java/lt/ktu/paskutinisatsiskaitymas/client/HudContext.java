package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.UUID;
import lt.ktu.paskutinisatsiskaitymas.protocol.WorldSnapshot;

/**
 * Everything a HUD element needs for one paint, so elements never hold a reference to the panel.
 * The layout numbers are computed once here, not separately by each element.
 */
record HudContext(int width, int height, WorldSnapshot snapshot, UUID localPlayerId, String notification,
        AssetManager assets) {

    /** Snapshot order and the local player's identity never determine another owner's ability status. */
    String examinatorStatus(UUID ownerId) {
        var ability = snapshot.examinators().stream().filter(value -> value.ownerId().equals(ownerId))
                .findFirst().orElse(null);
        if (ability == null) {
            return "EX: --";
        }
        return switch (ability.phase()) {
            case "FLY" -> "EX: Arriving";
            case "INSPECT", "ACQUIRE" -> "EX: Scanning";
            case "AIM" -> "EX: Aiming";
            case "FIRE" -> "EX: Firing";
            case "IDLE" -> ability.cooldownRemaining() > 0
                    ? "EX: " + (int) Math.ceil(ability.cooldownRemaining()) + "s" : "EX: Ready";
            default -> "EX: --";
        };
    }

    /** Uniform scale of the arena inside the panel. */
    double worldScale() {
        return Math.min(width / snapshot.arena().width(), height / snapshot.arena().height());
    }

    double offsetX() {
        return (width - snapshot.arena().width() * worldScale()) / 2.0;
    }

    double offsetY() {
        return (height - snapshot.arena().height() * worldScale()) / 2.0;
    }

    /** Top cards never grow past their design size. */
    double hudScale() {
        return Math.min(1, worldScale());
    }

    /** Bottom cards shrink so they fit entirely in the decorative base below the ground, with margins. */
    double bottomScale() {
        double groundY = snapshot.arena().platforms().stream()
                .filter(p -> p.x() == 0 && p.width() == snapshot.arena().width())
                .mapToDouble(p -> p.y()).max().orElse(0);
        double belowGround = height - offsetY() - groundY * worldScale();
        return Math.min(hudScale(), belowGround / (HudStyle.CARD_HEIGHT + 2.0 * HudStyle.CARD_MARGIN));
    }
}