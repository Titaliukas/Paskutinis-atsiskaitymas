package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Map;

/** Single place that maps a wire character name to its concrete factory. */
final class CharacterFactories {
    private static final Map<String, CharacterFactory> FACTORIES = Map.of(
            "PATASIJUS", new PatasijusFactory(),
            "VACIJUS", new VacijusFactory(),
            "JURGELINIS", new JurgelinisFactory(),
            "GOGIS", new GogisFactory());

    private CharacterFactories() { }

    /** Returns the factory for a character name, or null for null or unknown names. */
    static CharacterFactory forCharacter(String name) {
        return name == null ? null : FACTORIES.get(name);
    }
}
