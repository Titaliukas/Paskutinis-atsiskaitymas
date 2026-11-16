package lt.ktu.paskutinisatsiskaitymas.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Flyweight factory: hands out the single shared {@link ParticleType} for each {@link ParticleKind}. */
final class ParticleTypeFactory {
    private static final Map<ParticleKind, ParticleType> TYPES = new ConcurrentHashMap<>();

    private ParticleTypeFactory() { }

    /** The shared type for {@code kind}, created on first use. */
    static ParticleType get(ParticleKind kind) {
        return TYPES.computeIfAbsent(kind, ParticleType::new);
    }

    /** How many distinct flyweights exist right now. */
    static int cachedTypes() {
        return TYPES.size();
    }

    /** Drops the cache so the next requests recreate the types; live particles keep the types they already hold. */
    static void clear() {
        TYPES.clear();
    }
}
