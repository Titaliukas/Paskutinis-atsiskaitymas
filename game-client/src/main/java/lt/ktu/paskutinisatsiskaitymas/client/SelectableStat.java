package lt.ktu.paskutinisatsiskaitymas.client;

import lt.ktu.paskutinisatsiskaitymas.protocol.StatAllocation;

/** Client labels and accessors for the three editable build percentages. */
enum SelectableStat {
    HEALTH("Maximum health"), SPEED("Movement speed"), JUMP_HEIGHT("Jump height");

    private final String label;

    SelectableStat(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    int value(StatAllocation stats) {
        return switch (this) {
            case HEALTH -> stats.healthPercent();
            case SPEED -> stats.speedPercent();
            case JUMP_HEIGHT -> stats.jumpHeightPercent();
        };
    }

    StatAllocation withValue(StatAllocation stats, int value) {
        return new StatAllocation(this == HEALTH ? value : stats.healthPercent(),
                this == SPEED ? value : stats.speedPercent(),
                this == JUMP_HEIGHT ? value : stats.jumpHeightPercent());
    }
}
