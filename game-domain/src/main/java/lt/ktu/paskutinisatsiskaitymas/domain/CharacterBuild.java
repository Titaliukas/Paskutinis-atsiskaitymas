package lt.ktu.paskutinisatsiskaitymas.domain;

/** Permanent pre-join allocation, independent of the temporary stat decorator chain. */
public record CharacterBuild(int healthPercent, int speedPercent, int jumpHeightPercent) {
    public static final CharacterBuild DEFAULT = new CharacterBuild(100, 100, 100);

    public CharacterBuild {
        validate(healthPercent);
        validate(speedPercent);
        validate(jumpHeightPercent);
        if (healthPercent + speedPercent + jumpHeightPercent > 300) {
            throw new IllegalArgumentException("Stats exceed the 300-point budget");
        }
    }

    private static void validate(int percent) {
        if (percent < 50 || percent > 150 || percent % 5 != 0) {
            throw new IllegalArgumentException("Each stat must be 50–150 in steps of 5");
        }
    }

    public int maxHealth() {
        return (int) Math.round(GameConstants.MAX_HEALTH * healthPercent / 100.0);
    }

    public double speedMultiplier() {
        return speedPercent / 100.0;
    }

    public double jumpHeightMultiplier() {
        return jumpHeightPercent / 100.0;
    }
}
