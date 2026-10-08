package lt.ktu.paskutinisatsiskaitymas.domain;

/** Shared finite-value validation and precise rectangle distance, without world access. */
final class NPCGeometry {
    private NPCGeometry() { }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    static double positive(double value, String name) {
        if (finite(value, name) <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    static Integer id(Integer value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("Numeric identities must be positive or absent");
        }
        return value;
    }

    static double distance(NPCState self, NPCTarget target) {
        double dx = Math.max(0, Math.max(target.x() - self.preciseX() - self.width(),
                self.preciseX() - target.x() - target.width()));
        double dy = Math.max(0, Math.max(target.y() - self.preciseY() - self.height(),
                self.preciseY() - target.y() - target.height()));
        return Math.hypot(dx, dy);
    }

    static boolean matches(NPCState self, NPCTarget target) {
        return self.alive() && target != null && target.id().equals(self.targetId());
    }

    static double direction(NPCState self, NPCTarget target) {
        return Math.signum(target.x() + target.width() / 2 - self.preciseX() - self.width() / 2);
    }

    static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
