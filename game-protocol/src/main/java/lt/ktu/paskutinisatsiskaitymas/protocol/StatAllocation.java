package lt.ktu.paskutinisatsiskaitymas.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;

/** Detached build percentages. Gameplay limits are validated by the server's domain model. */
public record StatAllocation(int healthPercent, int speedPercent, int jumpHeightPercent) {
    public static final StatAllocation DEFAULT = new StatAllocation(100, 100, 100);

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StatAllocation fromJson(JsonNode json) {
        if (json == null || !json.isObject() || json.size() != 3) {
            throw new IllegalArgumentException("Stats must contain all three integer percentages");
        }
        return new StatAllocation(integer(json, "healthPercent"), integer(json, "speedPercent"),
                integer(json, "jumpHeightPercent"));
    }

    private static int integer(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.intValue();
    }
}
